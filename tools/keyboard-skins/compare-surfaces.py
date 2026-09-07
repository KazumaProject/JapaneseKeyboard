#!/usr/bin/env python3
"""Compare reference/device key corners without rescaling or font masking tricks.
Requires Pillow and numpy. Reports geometric discrepancy, not whole-keyboard parity.
"""
from pathlib import Path
import json
import numpy as np
from PIL import Image
root = Path(__file__).resolve().parents[2]
evidence = root / 'docs/keyboard-skins/evidence'
report = {}
for mode, color in [('light', 255), ('dark', 61)]:
    ref = np.array(Image.open(evidence/f'ios-{mode}-kana-key.png').convert('RGB'))
    actual = np.array(Image.open(evidence/f'android-{mode}-kana-key.png').convert('RGBA'))
    ref_mask = np.max(np.abs(ref.astype(int) - color), axis=2) <= 2
    actual_mask = actual[:, :, 3] >= 253
    # Upper-left 12 pt corner, excluding text well inside the key.
    contour = lambda mask: [int(np.flatnonzero(row[:80])[0]) for row in mask[:36]]
    ref_edge, actual_edge = contour(ref_mask), contour(actual_mask)
    error = np.abs(np.array(ref_edge) - actual_edge)
    report[mode] = dict(pixels_per_point=3, reference_key_pixels=[241,147],
        opaque_rgb_max_error=int(np.max(np.abs(actual[40,40,:3].astype(int) - ref[40,40].astype(int)))),
        corner_max_error_points=float(error.max()/3),
        corner_mean_error_points=float(error.mean()/3),
        corner_within_one_point=bool(error.max() <= 3),
        reference_left_edge_pixels=ref_edge, android_left_edge_pixels=actual_edge)
print(json.dumps(report, indent=2))
