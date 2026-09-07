#!/usr/bin/env python3
"""Export checked-in reviewed observation arrays for the device comparison runner."""
import argparse
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("sources", type=Path, nargs="+")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
records = {}
for source in args.sources:
    for item in json.loads(source.read_text()):
        if item["status"] != "reviewed" or not item["ime"].startswith("com.google.android.inputmethod.latin/"):
            raise ValueError(f"Unreviewed/non-Gboard source: {source}")
        if item["operation"].startswith("FAILED:"):
            raise ValueError("Cannot export a failed recording")
        case = item["caseId"]
        if not case.replace("-", "").replace("_", "").isalnum() or case in records:
            raise ValueError(f"Invalid/duplicate case ID: {case}")
        records[case] = item
args.output.mkdir(parents=True, exist_ok=True)
for case, item in records.items():
    path = args.output / f"{case}.json"
    if path.exists() and json.loads(path.read_text()) != item:
        raise ValueError(f"Refusing to overwrite a different baseline: {path}")
    path.write_text(json.dumps(item, ensure_ascii=False, indent=2) + "\n")
print(f"Exported {len(records)} reviewed observations to {args.output}")
