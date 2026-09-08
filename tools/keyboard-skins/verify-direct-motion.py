#!/usr/bin/env python3
"""Compare display transitions using source timestamps, without video-clock regression.

iOS: raw CoreSimulator frame callbacks, same mach clock as UITouch.timestamp.
Android: screenrecord Winscope elapsed-realtime timestamps, converted using the
per-event elapsed-minus-monotonic offset logged by the isolated test host.
"""

import argparse, gzip, json, struct
from pathlib import Path
import av, numpy as np


def pairs(events, up):
    result = []
    active = None
    for event in events:
        if event["phase"] == 0:
            active = [event]
        elif active is not None:
            active.append(event)
            if event["phase"] == up:
                result.append(active)
                active = None
    return result


def ios_gestures(folder):
    out = []
    for kind in ["kana", "qwerty"]:
        labels = (
            ["hold", "left", "up", "right", "down"]
            if kind == "kana"
            else ["q", "e", "p"]
        )
        for mode in ["Light", "Dark"]:
            events = json.loads(
                (folder / f"touch-events-{kind}-{mode}.json").read_text()
            )
            sequence = pairs(events, 3)
            assert len(sequence) == 3 + len(labels) * 4, (kind, mode, len(sequence))
            sequence = sequence[3:]
            schedule = [(label, -1) for label in labels] + [
                (label, trial) for label in labels for trial in range(3)
            ]
            for (label, trial), ev in zip(schedule, sequence):
                if trial < 0:
                    continue
                out.append(
                    dict(
                        kind=kind,
                        mode=mode.lower(),
                        label=label,
                        trial=trial,
                        down=ev[0].get("dispatchTime", ev[0]["time"]),
                        up=ev[-1].get("dispatchTime", ev[-1]["time"]),
                        input_down=ev[0]["time"],
                        input_up=ev[-1]["time"],
                        events=ev,
                        down_serial=ev[0]["serial"],
                        up_serial=ev[-1]["serial"],
                        frames=[],
                    )
                )
    return out


def android_gestures(folder):
    raw = json.loads((folder / "motion/gestures.json").read_text())
    queues = {}
    out = []
    for g in raw:
        name = g["name"]
        if name not in queues:
            queues[name] = iter(
                pairs(json.loads((folder / f"{name}-events.json").read_text()), 1)
            )
        ev = next(queues[name])
        offset = np.median([x["elapsedMinusMonotonicNanos"] for x in ev]) / 1e9
        if g["trial"] < 0:
            continue
        label = {
            "な": "hold",
            "な:left": "left",
            "な:top": "up",
            "な:right": "right",
            "な:bottom": "down",
        }.get(g["label"], g["label"])
        kind, mode = name.split("-")
        x, y, w, h = [g[k] for k in ["x", "y", "width", "height"]]
        left = x - w / 2
        top = y - h / 2
        if label == "hold":
            box = (x - w * 0.4, y - h * 0.4, x + w * 0.4, y + h * 0.4)
        elif label in ["left", "right", "up", "down"]:
            shift = h * 10 / 56
            box = {
                "left": (left - w, top, left, top + h),
                "right": (left + w, top, left + 2 * w, top + h),
                "up": (left, top - h - shift, left + w, top - shift),
                "down": (left, top + h + shift, left + w, top + 2 * h + shift),
            }[label]
        elif label == "e":
            box = (x - 200, top - 600, x + 200, top - 4)
        else:
            box = (x - w, top - h * 1.56, x + w, top - 4)
        out.append(
            dict(
                kind=kind,
                mode=mode,
                label=label,
                trial=g["trial"],
                down=ev[0]["dispatchMonotonicNanos"] / 1e9 + offset,
                up=ev[-1]["dispatchMonotonicNanos"] / 1e9 + offset,
                input_down=ev[0]["time"] / 1000 + offset,
                input_up=ev[-1]["time"] / 1000 + offset,
                events=ev,
                down_serial=ev[0]["serial"],
                up_serial=ev[-1]["serial"],
                elapsed_minus_monotonic_seconds=float(offset),
                box=box,
                label_box=(x - w - 60, y - h - 50, x - w + 60, y - h + 50),
                frames=[],
            )
        )
    return out


def read_ios(path, gestures):
    counter = []
    cost = []
    with gzip.open(path, "rb") as f:
        n = struct.unpack("<I", f.read(4))[0]
        meta = json.loads(f.read(n))
        size = 32 + meta["sampleBytes"]
        starts = np.cumsum(
            [0] + [r["width"] * r["height"] * 3 for r in meta["regions"]]
        )
        while data := f.read(size):
            if len(data) != size:
                raise ValueError("Truncated raw frame")
            t, end, count, serial, tick, event = struct.unpack_from("<ddIIII", data)
            counter.append(count)
            cost.append((end - t) * 1000)
            for g in gestures:
                if g["down"] - 0.25 <= t <= g["up"] + 0.4:
                    index = {
                        "q": 0,
                        "e": 1,
                        "p": 2,
                        "hold": 3,
                        "left": 4,
                        "right": 5,
                        "up": 6,
                        "down": 7,
                    }[g["label"]]

                    def roi(i):
                        return (
                            np.frombuffer(
                                data,
                                np.uint8,
                                offset=32 + int(starts[i]),
                                count=int(starts[i + 1] - starts[i]),
                            )
                            .reshape(-1, 3)
                            .astype(np.float32)
                        )

                    g["frames"].append((t, roi(index), roi(8), serial, tick, event))
    if not counter:
        raise ValueError("No raw display records")
    if counter != list(range(1, len(counter) + 1)):
        raise ValueError("Missing raw callback records")
    return dict(
        source="CoreSimulator display callback, mach monotonic seconds; no codec or PTS fitting",
        callback_count=len(counter),
        sample_cost_max_ms=max(cost),
        sample_cost_p99_ms=float(np.percentile(cost, 99)),
        missing_callback_records=0,
    )


def winscope_times(path):
    data = path.read_bytes()
    magic = b"#VV1NSC0PET1ME2#"
    start = data.find(magic)
    if start < 0 or data.find(magic, start + len(magic)) >= 0:
        raise ValueError("Missing/ambiguous Winscope metadata")
    version, offset, count = struct.unpack_from("<IQI", data, start + len(magic))
    if count == 0:
        raise ValueError("Empty source timestamp table")
    if version != 2:
        raise ValueError("Unknown Winscope metadata version")
    times = (
        np.frombuffer(
            data, dtype="<u8", offset=start + len(magic) + 16, count=count
        ).astype(np.float64)
        / 1e9
    )
    if (np.diff(times) <= 0).any():
        raise ValueError("Non-monotonic display timestamps")
    return times


def read_android(path, gestures):
    times = winscope_times(path)
    count = 0
    for index, f in enumerate(av.open(str(path)).decode(video=0)):
        if index >= len(times):
            raise ValueError("More decoded frames than source timestamps")
        count += 1
        t = times[index]
        active = [g for g in gestures if g["down"] - 0.25 <= t <= g["up"] + 0.4]
        if not active:
            continue
        im = f.to_ndarray(format="rgb24")

        def bits(row):
            return sum(
                int(v) << i
                for i, v in enumerate(
                    im[410 + 20 * row, 30 + np.arange(32) * 20].mean(1) > 128
                )
            )

        serial, tick, event = [bits(row) for row in range(3)]
        for g in active:
            x0, y0, x1, y1 = map(int, g["box"])
            roi = (
                im[max(0, y0) : y1 : 3, max(0, x0) : x1 : 3]
                .reshape(-1, 3)
                .astype(np.float32)
            )
            lx0, ly0, lx1, ly1 = map(int, g["label_box"])
            label = im[ly0:ly1:3, lx0:lx1:3].reshape(-1, 3).astype(np.float32)
            g["frames"].append((t, roi, label, serial, tick, event))
    if count != len(times):
        raise ValueError(f"Decoded/source frame count mismatch {count}/{len(times)}")
    return dict(
        source="screenrecord Winscope v2 display timestamps; per-event clock-domain conversion",
        source_frame_count=len(times),
        decoded_frame_count=count,
    )


def bracket(t, metric, predicate, origin):
    indices = np.flatnonzero(predicate)
    if not len(indices):
        return None
    i = int(indices[0])
    if i == 0:
        return None
    return dict(
        bracket_ms=[float((t[i - 1] - origin) * 1000), float((t[i] - origin) * 1000)],
        gap_ms=float((t[i] - t[i - 1]) * 1000),
        index=i,
    )


def measure(gestures):
    out = []
    for g in gestures:
        frames = g.pop("frames")
        if len(frames) < 10:
            raise ValueError(f"Insufficient frames for {g}")
        t = np.array([f[0] for f in frames])
        ims = np.stack([f[1] for f in frames])
        down, up = g["down"], g["up"]
        pre = t < down
        post = (t > up + 0.15) & (t < up + 0.3)
        held = (t > max(down + 0.035, up - 0.1)) & (t < up - 0.03)
        if not pre.any() or not post.any() or not held.any():
            raise ValueError("Missing baselines")
        before = ims[pre][-1]
        after = ims[post].mean(0)
        plateau = ims[held].mean(0)
        # Keep unchanged opaque surface pixels, excluding both endpoint glyph masks.
        # Otherwise a returning neighboring label can be misclassified as a retained flick bubble.
        surface = (
            ((plateau.min(1) > 150) & (after.min(1) > 100))
            if g["mode"] == "light"
            else ((plateau.max(1) < 150) & (after.max(1) < 150))
        )
        surface &= abs(plateau - after).mean(1) > 3
        if g["label"] == "hold":
            surface = np.ones(len(plateau), dtype=bool)
        if not surface.any():
            raise ValueError(f'No surface contrast: {g["kind"]}/{g["label"]}')
        change = abs(ims[:, surface] - before[surface]).mean((1, 2))
        off = abs(ims[:, surface] - after[surface]).mean((1, 2))
        stable = abs(ims[:, surface] - plateau[surface]).mean((1, 2))
        amp = max(1, float(abs(plateau[surface] - before[surface]).mean()))
        off_amp = max(1, float(abs(plateau[surface] - after[surface]).mean()))
        g["surface_pixel_count"] = int(surface.sum())
        if g["label"] == "hold":
            blue = ((ims[:, :, 2] - ims[:, :, 0] > 100) & (ims[:, :, 1] > 70)).mean(
                1
            ) * 100
            change = off = blue
            stable = abs(blue - blue[held].mean())
            amp = off_amp = max(1, float(blue[held].mean()))
        appearance = bracket(
            t, change, (t >= down) & (change > max(1, 0.05 * amp)), down
        )
        settled = bracket(
            t, stable, (t >= down) & (t < up) & (stable < max(1, 0.02 * amp)), down
        )
        dismissal = bracket(t, off, (t >= up) & (off < max(1, 0.05 * off_amp)), up)
        g.update(
            appearance=appearance,
            settled=settled,
            dismissal=dismissal,
            release_samples=[
                dict(
                    since_up_ms=float((tt - up) * 1000),
                    visible_fraction=float(v / off_amp),
                    frame_serial=int(fr[3]),
                    clock_ms=int(fr[4]),
                    event_serial=int(fr[5]),
                )
                for tt, v, fr in zip(t, off, frames)
                if up - 0.06 < tt < up + 0.15
            ],
        )
        if g["label"] == "hold":
            labels = np.stack([f[2] for f in frames])
            values = np.percentile(
                labels, 1 if g["mode"] == "light" else 99, axis=(1, 2)
            )
            original = float(np.median(values[pre][-3:]))
            dim = float(np.median(values[held]))
            if abs(original - dim) < 10:
                raise ValueError("Insufficient label contrast")
            dim_fraction = (values - original) / (dim - original)
            restore_fraction = 1 - dim_fraction
            g["label_dim"] = {
                str(level): bracket(
                    t,
                    dim_fraction,
                    (t >= down) & (t < up) & (dim_fraction >= level),
                    down,
                )
                for level in [0.1, 0.5, 0.9]
            }
            g["label_restore"] = {
                str(level): bracket(
                    t, restore_fraction, (t >= up) & (restore_fraction >= level), up
                )
                for level in [0.1, 0.5, 0.9]
            }
            g["label_samples"] = [
                dict(
                    since_down_ms=float((tt - down) * 1000),
                    since_up_ms=float((tt - up) * 1000),
                    dim_fraction=float(value),
                )
                for tt, value in zip(t, dim_fraction)
            ]
        out.append(g)
    return out


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--platform", choices=["ios", "android"], required=True)
    p.add_argument("--capture", type=Path, required=True)
    p.add_argument("--events", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    a = p.parse_args()
    gestures = (
        ios_gestures(a.events) if a.platform == "ios" else android_gestures(a.events)
    )
    calibration = (
        read_ios(a.capture, gestures)
        if a.platform == "ios"
        else read_android(a.capture, gestures)
    )
    report = dict(
        platform=a.platform,
        capture=str(a.capture),
        timing=calibration,
        gestures=measure(gestures),
    )
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    for g in report["gestures"]:
        print(g["kind"], g["mode"], g["label"], g["trial"], g["dismissal"])
    print(calibration)


if __name__ == "__main__":
    main()
