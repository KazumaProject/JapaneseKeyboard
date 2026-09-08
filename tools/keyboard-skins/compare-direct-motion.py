#!/usr/bin/env python3
"""Compare every declared gesture trial; fail on missing cases or out-of-budget motion."""

import argparse
import json
from pathlib import Path

FRAME_MS = 1000 / 60


def key(gesture):
    return tuple(gesture[field] for field in ("kind", "mode", "label", "trial"))


def indexed(report):
    result = {}
    for gesture in report["gestures"]:
        identity = key(gesture)
        if identity in result:
            raise ValueError(f"Duplicate trial: {identity}")
        result[identity] = gesture
    if not result:
        raise ValueError("No measured trials")
    return result


def observed(transition):
    interval = transition["bracket_ms"]
    if len(interval) != 2 or interval[1] < interval[0]:
        raise ValueError(f"Invalid transition interval: {interval}")
    return interval[1]


def compare(reference, android):
    ios = indexed(reference)
    actual = indexed(android)
    if ios.keys() != actual.keys():
        raise ValueError(
            f"Trial mismatch: missing={ios.keys()-actual.keys()}, extra={actual.keys()-ios.keys()}"
        )
    rows = []
    for identity, sample in actual.items():
        target = ios[identity]
        differences = {
            "dismissal_ms": observed(sample["dismissal"])
            - observed(target["dismissal"])
        }
        if sample["label"] == "hold":
            for fraction in ("0.1", "0.5", "0.9"):
                # Input recognition thresholds are preserved. Compare fade presentation
                # relative to the first visible guide, and restoration to dispatched UP.
                differences[f"dim_{fraction}_ms"] = (
                    observed(sample["label_dim"][fraction])
                    - observed(sample["appearance"])
                    - observed(target["label_dim"][fraction])
                    + observed(target["appearance"])
                )
                differences[f"restore_{fraction}_ms"] = observed(
                    sample["label_restore"][fraction]
                ) - observed(target["label_restore"][fraction])
        rows.append(
            {
                "trial": list(identity),
                "differences": differences,
                "passed": all(abs(value) <= FRAME_MS for value in differences.values()),
            }
        )
    return {
        "criterion": "Difference between first observed transition frames, at most one 60 Hz frame. Source frame intervals remain in the input reports.",
        "tolerance_ms": FRAME_MS,
        "trials": rows,
        "passed": all(row["passed"] for row in rows),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ios", type=Path, required=True)
    parser.add_argument("--android", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = compare(
        json.loads(args.ios.read_text()), json.loads(args.android.read_text())
    )
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(
        f"{sum(row['passed'] for row in result['trials'])}/{len(result['trials'])} trials passed"
    )
    raise SystemExit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
