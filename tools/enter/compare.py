#!/usr/bin/env python3
"""Compare reviewed Gboard observations with actual Enter probe recordings."""
import argparse
import json
from pathlib import Path

FIELDS = ("text", "selectionStart", "selectionEnd", "composingStart", "composingEnd", "actions")


def outcome(snapshot):
    return {key: snapshot[key] for key in FIELDS}


def differences(reference, actual):
    errors = []
    if reference.get("status") != "reviewed" or not reference["ime"].startswith("com.google.android.inputmethod.latin/"):
        return ["reference is not a reviewed Gboard observation"]
    if actual["operation"].startswith("FAILED:"):
        return ["actual recording failed"]
    for key in ("caseId", "editorInfo", "operation", "androidSdk"):
        if reference[key] != actual[key]:
            errors.append(f"incompatible {key}: {reference[key]!r} != {actual[key]!r}")
    if outcome(reference["before"]) != outcome(actual["before"]):
        errors.append("incompatible initial state")
    if errors:
        return errors
    if len(reference["after"]) != len(actual["after"]):
        errors.append("number of recorded Enter steps differs")
    for step, (expected, observed) in enumerate(zip(reference["after"], actual["after"]), 1):
        for key in FIELDS:
            if expected[key] != observed[key]:
                errors.append(f"Enter {step}: {key}: expected {expected[key]!r}, actual {observed[key]!r}")
    return errors


def load_directory(directory):
    cases = {}
    for path in sorted(directory.glob("*.json")):
        data = json.loads(path.read_text())
        key = data["caseId"]
        if key in cases:
            raise ValueError(f"Duplicate case {key}; use one run per directory")
        cases[key] = data
    if not cases:
        raise ValueError(f"No observations in {directory}")
    return cases


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("actual", type=Path)
    parser.add_argument("--inventory", type=Path, help="enter-cases.json from the device, to report the whole case set")
    args = parser.parse_args()
    expected, actual = load_directory(args.reference), load_directory(args.actual)
    all_cases = expected.keys() | actual.keys()
    if args.inventory:
        all_cases |= {item["caseId"] for item in json.loads(args.inventory.read_text())}
    failed = 0
    for case in sorted(all_cases):
        if case not in expected:
            errors = ["UNREVIEWED: no Gboard reference"]
        elif case not in actual:
            errors = ["NOT RUN: no actual recording"]
        else:
            errors = differences(expected[case], actual[case])
        if errors:
            failed += 1
            print(case + ": " + "; ".join(errors))
    print(f"{len(all_cases) - failed} matched; {failed} failed/unverified")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
