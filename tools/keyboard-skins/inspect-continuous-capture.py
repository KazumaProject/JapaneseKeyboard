#!/usr/bin/env python3
"""Export contact sheets from actual-IME continuous recordings and source timestamps.

Usage: python inspect-continuous-capture.py CAPTURE_DIRECTORY [--pattern 'extra-*']
Requires av, numpy and Pillow. This checks recording coverage, not visual acceptance.
Static video frames are held until the next frame; never sample a future frame.
"""
import argparse
import bisect
import json
import struct
from pathlib import Path

import av
import numpy as np
from PIL import Image, ImageDraw


def inspect(video, output):
    files = sorted((video.parent / video.stem).glob('*continuous-traces.json'))
    if not files:
        return
    gestures = [g for path in files for g in json.loads(path.read_text())]
    data = video.read_bytes()
    magic = b'#VV1NSC0PET1ME2#'
    start = data.find(magic)
    if start < 0 or data.find(magic, start + len(magic)) >= 0:
        raise ValueError('Missing or ambiguous source timestamp metadata')
    start += len(magic)
    version, _, count = struct.unpack_from('<IQI', data, start)
    if version != 2 or not count:
        raise ValueError('Unsupported or empty source timestamps')
    times = np.frombuffer(data, dtype='<u8', offset=start + 16, count=count) / 1e9
    if (np.diff(times) <= 0).any():
        raise ValueError('Non-monotonic display timestamps')
    requests, records, cells = {}, [], {}
    for gesture in gestures:
        if gesture['trial'] < 0:
            continue
        down = gesture['events'][0]['elapsedStartNanos'] / 1e9
        up = gesture['events'][-1]['elapsedEndNanos'] / 1e9
        offsets = gesture.get('offsets', [0, gesture['hold'], gesture['hold'] + 150,
            gesture['hold'] + 650, gesture['hold'] + 1150, gesture['hold'] + 1650,
            gesture['hold'] + 2150])
        # Waypoint samples include display delivery time; inspect the original trace
        # for recognition-boundary timing. These labels do not assert selected text.
        samples = [('held', offsets[1] / 1000 - .02)] + [
            (f'waypoint-{i}', offsets[i] / 1000 + .1) for i in range(2, 7)
        ] + [('released', up - down + .15)]
        record = dict(name=gesture['name'], path=gesture.get('path', 'forward'),
            covered=bool(times[0] < down and times[-1] > up + .15), samples=[])
        records.append(record)
        for label, delta in samples:
            requested = down + delta
            index = bisect.bisect_right(times, requested) - 1
            if index < 0:
                raise ValueError('Recording begins after a required sample')
            requests.setdefault(index, []).append((gesture, label))
            record['samples'].append(dict(state=label, frame=index,
                time=float(times[index]), requested=requested))
    decoded = 0
    for index, frame in enumerate(av.open(str(video)).decode(video=0)):
        decoded += 1
        if index not in requests:
            continue
        image = frame.to_image()
        for gesture, label in requests[index]:
            left, top, right, bottom = gesture['anchor']
            width, height = right - left, bottom - top
            # Includes the whole guide even when the bottom-edge cross moves up.
            crop = image.crop((left - width - 12, top - 2 * height - 40,
                right + width + 12, bottom + height + 40))
            crop.thumbnail((300, 280))
            cell = Image.new('RGB', (310, 310), '#777777')
            cell.paste(crop, ((310 - crop.width) // 2, 24))
            ImageDraw.Draw(cell).text((4, 4), label, fill='white')
            cells[gesture['name'], label] = cell
    for trial in range(3):
        chosen = [g for g in gestures if g['trial'] == trial]
        sheet = Image.new('RGB', (2170, 330 * len(chosen)), '#333333')
        draw = ImageDraw.Draw(sheet)
        for row, gesture in enumerate(chosen):
            draw.text((5, row * 330), gesture['name'], fill='white')
            for column, label in enumerate(['held'] + [f'waypoint-{i}' for i in range(2, 7)] + ['released']):
                sheet.paste(cells[gesture['name'], label], (column * 310, row * 330 + 20))
        sheet.save(output / f'{video.stem}-trial{trial}.jpg', quality=92)
    report = dict(video=video.name, decoded=decoded, timestamps=count,
        captureComplete=decoded == count and all(r['covered'] for r in records),
        visualAcceptance='Requires separate review; contact sheets are samples', gestures=records)
    (output / (video.stem + '.json')).write_text(json.dumps(report, indent=2))
    print(video.name, 'captureComplete:', report['captureComplete'], flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--pattern', default='final-timed-*')
    args = parser.parse_args()
    output = args.directory / 'source-inspection'
    output.mkdir(exist_ok=True)
    for video in sorted(args.directory.glob(args.pattern + '.mp4')):
        inspect(video, output)
