"""Compare physical-device Default captures with the unchanged pre-PR APK.

Requires Pillow. No image tolerance is used. A separately recorded baseline repeat
can explain platform raster differences only with exact crop or keyboard-body
and surrounding-surface matches. Every pixel is checked; no tolerance is used.
"""
import argparse
import json
import re
from pathlib import Path

from PIL import Image, ImageChops

CONFIGURATIONS = {
    "threebutton-portrait": "threebutton-portrait",
    "threebutton-landscape": "threebutton-landscape",
    "threebutton-large": "threebutton-large",
    "threebutton-floating": "threebutton-floating",
    "gesture-portrait": "gesture-portrait",
    "gesture-landscape": "gesture-landscape",
    "cold-light": "threebutton-portrait",
    "cold-dark": "threebutton-portrait",
}
PIXEL_PHASES = {"empty", "composing", "committed", "gesture", "delete", "return"}


def read_rows(directory):
    file = directory / "measurements.json"
    rows = {r["name"]: r for r in json.loads(file.read_text())} if file.exists() else {}
    # Earlier baseline instrumentation omitted some floating roots from its JSON.
    # Recover their original bounds from the accompanying, unmodified node dump.
    for name, row in rows.items():
        dump = directory / (name + "-nodes.txt")
        if not dump.exists():
            continue
        for node_id in ("floating_keyboard_content", "qwerty_view_floating", "keyboard_view_floating"):
            match = re.search(r":id/" + node_id + r" null null Rect\((-?\d+), (-?\d+) - (-?\d+), (-?\d+)\)", dump.read_text())
            if match:
                row[node_id] = list(map(int, match.groups()))
    return rows


def crop_box(row, image):
    if "inputArea" in row:
        left, top, right, _ = row["inputArea"]
        return left, top, right, image.height - row["navigationInsets"][3]
    if "floating_keyboard_content" in row:
        return tuple(row["floating_keyboard_content"])
    body = row.get("keyboard_view_floating") or row.get("qwerty_view_floating")
    candidate = row.get("suggestionView_parent")
    if body and candidate:
        return min(body[0], candidate[0]), min(body[1], candidate[1]), max(body[2], candidate[2]), max(body[3], candidate[3])
    raise ValueError(f"No keyboard bounds for {row['name']}")


def compare(evidence):
    results, missing = [], []
    for configuration, baseline_configuration in CONFIGURATIONS.items():
        baseline = evidence / ("baseline-" + baseline_configuration)
        final = evidence / ("final-" + configuration)
        before, after = read_rows(baseline), read_rows(final)
        if not before or not after:
            missing.append(configuration)
            continue
        phases = ["empty", "composing", "committed", "gesture", "delete", "symbol", "symbol-category", "return"]
        expected_before = {f"{keyboard}-0-default-{phase}" for keyboard in ("TENKEY", "QWERTY") for phase in phases}
        expected_before.add("TENKEY-0-default-longpress")
        indices = [1] if configuration.startswith("cold-") else [0, 3]
        expected_after = {name.replace("-0-default-", f"-{index}-default-") for name in expected_before for index in indices}
        if not expected_before.issubset(before) or not expected_after.issubset(after):
            missing.append(configuration + " (incomplete captures)")
        for name, current in after.items():
            if "-default-" not in name:
                continue
            reference_name = re.sub(r"-\d+-default-", "-0-default-", name)
            old = before[reference_name]
            differences = {k: [v, current.get(k)] for k, v in old.items()
                           if k != "name" and v != current.get(k)}
            row = {"configuration": configuration, "phase": name,
                   "referencePhase": reference_name, "geometryEqual": not differences,
                   "differences": differences}
            if name.rsplit("-", 1)[-1] in PIXEL_PHASES:
                original = Image.open(baseline / (reference_name + ".png")).convert("RGB")
                actual = Image.open(final / (name + ".png")).convert("RGB")
                box = crop_box(old, original)
                difference = ImageChops.difference(original.crop(box), actual.crop(box))
                match = difference.getbbox() is None
                row.update(pixelsEqual=match, primaryPixelDifferenceBounds=difference.getbbox(),
                           matchingPrePrCapture=baseline.name if match else None)
                if not match:
                    for repeat in sorted(evidence.glob("baseline-repeat*-" + baseline_configuration)):
                        repeated = read_rows(repeat).get(reference_name)
                        geometry_keys = [key for key in old if key not in {"name", "navigationInsets"}]
                        if not repeated or not all(repeated.get(key) == old[key] for key in geometry_keys):
                            continue
                        image = repeat / (reference_name + ".png")
                        alternative = Image.open(image).convert("RGB").crop(box)
                        if ImageChops.difference(alternative, actual.crop(box)).getbbox() is None:
                            row.update(pixelsEqual=True, matchingPrePrCapture=repeat.name)
                            break
                        # Android may cache a vector at different raster sizes on separate
                        # surfaces. Require an exact baseline body AND exact primary remainder.
                        body = old.get("qwerty_view") or old.get("keyboard_view")
                        if body:
                            local = (body[0]-box[0], body[1]-box[1], body[2]-box[0], body[3]-box[1])
                            combined = original.crop(box)
                            combined.paste(alternative.crop(local), local[:2])
                            if ImageChops.difference(combined, actual.crop(box)).getbbox() is None:
                                row.update(pixelsEqual=True, matchingPrePrCapture=None,
                                           matchingPrePrRegions={"keyboardBody": repeat.name,
                                                                 "remainder": baseline.name},
                                           keyboardBodyBounds=body)
                                break
            results.append(row)
    return results, missing


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("evidence", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    results, missing = compare(args.evidence)
    args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2))
    failures = [r for r in results if not r["geometryEqual"] or r.get("pixelsEqual") is False]
    print(f"Comparisons: {len(results)}; failures: {len(failures)}; missing: {missing}")
    for row in failures:
        print(row)
    if missing or not results or failures:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
