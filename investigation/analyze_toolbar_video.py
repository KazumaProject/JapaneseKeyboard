#!/usr/bin/env python3
"""Decode every recorded frame in the test interval and detect loss of the dark key body."""
import argparse
import csv
import json
from pathlib import Path
import av
import numpy as np
from PIL import Image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("case_directory", type=Path)
    options = parser.parse_args()
    directory = options.case_directory
    metadata = json.loads((directory / "metadata.json").read_text())
    result_path = directory / "toolbar-regression" / metadata["arguments"]["label"] / "result.json"
    result = json.loads(result_path.read_text())
    recording = next((directory / name for name in ("screen.webm", "screen.mp4")
                      if (directory / name).exists()), None)
    if recording is None:
        raise RuntimeError("No video evidence")
    start = (result["events"][0]["uptimeMs"] - metadata["recordStartedUptimeMs"]) / 1000
    end = (result["events"][-1]["uptimeMs"] - metadata["recordStartedUptimeMs"]) / 1000
    bounds = result["initialKeyboardBounds"]
    left, top, right, bottom = bounds
    # Exclude the outer edge, popups and navigation. Use a fixed screen ROI so disappearance
    # cannot make the detector follow another view. Only the key body's interior is sampled.
    roi = [int(left + (right-left)*.12), int(top + (bottom-top)*.15),
           int(right - (right-left)*.12), int(bottom - (bottom-top)*.12)]
    initial = np.asarray(Image.open(result_path.parent / "initial.png").convert("RGB"))

    def metrics(rgb, scaled_roi):
        x0,y0,x1,y1 = scaled_roi
        pixels = rgb[y0:y1:2, x0:x1:2].astype(np.float32)
        luma = pixels[...,0]*.299 + pixels[...,1]*.587 + pixels[...,2]*.114
        return float((luma < 80).mean()), float((luma > 180).mean())

    reference_dark, reference_bright = metrics(initial, roi)
    if reference_dark < .45 or reference_bright < .001:
        raise RuntimeError(f"Unusable visual fixture: dark={reference_dark} ink={reference_bright}")
    rows = []
    with av.open(str(recording)) as container:
        for frame in container.decode(video=0):
            if frame.time < start or frame.time > end:
                continue
            # Decode all frames, then downscale by 4 to keep ROI analysis inexpensive.
            image = frame.reformat(width=frame.width//4, height=frame.height//4, format="rgb24")
            rgb = image.to_ndarray()
            if result["landscape"] and metadata["recordBackend"].startswith("emulator-host"):
                # The host records the physical display buffer, before guest rotation.
                rgb = np.rot90(rgb, 1)
            scale = min(rgb.shape[1]/initial.shape[1], rgb.shape[0]/initial.shape[0])
            offset_x = (rgb.shape[1] - initial.shape[1]*scale)/2
            offset_y = (rgb.shape[0] - initial.shape[0]*scale)/2
            # Device screenrecord fits a rotated display in its initial portrait canvas.
            scaled = [int(roi[0]*scale+offset_x), int(roi[1]*scale+offset_y),
                      int(roi[2]*scale+offset_x), int(roi[3]*scale+offset_y)]
            dark, bright = metrics(rgb, scaled)
            rows.append((frame.time, dark, bright))
    if not rows or rows[-1][0] < end - .5:
        raise RuntimeError("Recording does not cover the complete test interval")
    first_input = (result["events"][1]["uptimeMs"] - metadata["recordStartedUptimeMs"]) / 1000
    calibration = [row for row in rows if row[0] < first_input]
    if len(calibration) < 3 or any(row[1] < .45 or row[2] == 0 for row in calibration):
        raise RuntimeError("Video does not contain a usable visible-keyboard calibration interval")
    # Recompression and portrait letterboxing reduce tiny text's brightness. Calibrate the
    # decoded recording against its own ready interval, whose geometry/screenshot are known.
    video_dark = float(np.median([row[1] for row in calibration]))
    video_bright = float(np.median([row[2] for row in calibration]))
    def missing(dark, bright):
        return dark < video_dark * .5 or bright < video_bright * .15
    assert missing(0, 1) and missing(1, 0) and not missing(video_dark, video_bright)
    rows = [(time, dark, bright, missing(dark, bright)) for time,dark,bright in rows]
    with (directory / "video-frames.csv").open("w") as stream:
        writer = csv.writer(stream)
        writer.writerow(("timeSeconds", "darkFraction", "inkFraction", "missingKeyboard"))
        writer.writerows(rows)
    gaps = [b[0]-a[0] for a,b in zip(rows,rows[1:])]
    summary = {"analysisVersion":3, "frames":len(rows), "startSeconds":start, "endSeconds":end,
               "maximumFrameGapMs":round(max(gaps, default=0)*1000,2),
               "missingKeyboardFrames":sum(row[3] for row in rows),
               "referenceDarkFraction":reference_dark, "referenceInkFraction":reference_bright,
               "videoReferenceDarkFraction":video_dark, "videoReferenceInkFraction":video_bright,
               "roi":roi, "detectorControlsPassed":True,
               "limitation":"Video and in-process geometry do not establish UMIDIGI driver behavior."}
    (directory / "video-analysis.json").write_text(json.dumps(summary,indent=2))
    print(directory.name, summary)
    if summary["missingKeyboardFrames"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
