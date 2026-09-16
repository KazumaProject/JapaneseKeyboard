#!/usr/bin/env python3
"""Compare captures from QuantityPerformanceInstrumentedTest, excluding diagnostic measurements."""
import argparse
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--allow-right-id-fix", action="store_true",
                        help="Allow only the documented first-node to last-node right context correction")
    args = parser.parse_args()
    before = json.loads(args.before.read_text())
    after = json.loads(args.after.read_text())
    if len(before) != len(after):
        raise SystemExit(f"Query count changed: {len(before)} -> {len(after)}")
    fields = ("backend", "bunsetsu", "phrase", "index", "input", "segments", "splits", "splitByCandidate", "systemMatches")
    corrected_ids = 0
    for old, new in zip(before, after):
        label = f"{old['backend']}/{old['bunsetsu']}/{old['input']}/{old['index']}"
        for field in fields:
            if old.get(field) != new.get(field):
                raise SystemExit(f"{label}: {field} changed")
        if len(old["candidates"]) != len(new["candidates"]):
            raise SystemExit(f"{label}: candidate count changed")
        for a, b in zip(old["candidates"], new["candidates"]):
            a, b = dict(a), dict(b)
            if args.allow_right_id_fix:
                corrected_ids += a.pop("right", None) != b.pop("right", None)
            if a != b:
                raise SystemExit(f"{label}: candidate changed: {a} -> {b}")
    print(f"PASS: {len(before)} queries; {corrected_ids} corrected right context IDs")
    for phrase in dict.fromkeys(row["phrase"] for row in before):
        groups = [[r for r in rows if r["phrase"] == phrase and r["backend"] == "INCREMENTAL_SESSION" and r["bunsetsu"]]
                  for rows in (before, after)]
        summary = {key: [max(r[key] for r in rows) for rows in groups]
                   for key in ("quantityStates", "quantityEdges")}
        print(phrase, json.dumps(summary))


if __name__ == "__main__":
    main()
