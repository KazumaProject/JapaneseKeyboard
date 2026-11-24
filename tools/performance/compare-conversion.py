#!/usr/bin/env python3
"""Compare complete conversion snapshots and summarize Android benchmark samples."""

import argparse
import hashlib
import json
import math
import statistics
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def bundled_assets(path):
    with ZipFile(path) as apk:
        names = [entry.filename for entry in apk.infolist()
                 if entry.filename.startswith("assets/") and not entry.is_dir()]
        if not names or len(names) != len(set(names)):
            raise ValueError(f"{path}: missing or duplicate bundled assets")
        fingerprints = {}
        for name in names:
            digest = hashlib.sha256()
            with apk.open(name) as asset:
                for block in iter(lambda: asset.read(256 * 1024), b""):
                    digest.update(block)
            fingerprints[name] = digest.hexdigest()
        return fingerprints


def difference(before, after, path="$"):
    if type(before) is not type(after):
        return f"{path}: expected {type(before).__name__}, got {type(after).__name__}"
    if isinstance(before, dict):
        if before.keys() != after.keys():
            return f"{path}: missing {sorted(before.keys() - after.keys())}, extra {sorted(after.keys() - before.keys())}"
        for key in sorted(before):
            mismatch = difference(before[key], after[key], f"{path}.{key}")
            if mismatch:
                return mismatch
    elif isinstance(before, list):
        if len(before) != len(after):
            return f"{path}: count {len(before)} != {len(after)}"
        for rank, (left, right) in enumerate(zip(before, after), start=1):
            mismatch = difference(left, right, f"{path}[{rank}]")
            if mismatch:
                return mismatch
    elif before != after:
        return f"{path}: {before!r} != {after!r}"
    return None


def case_index(snapshot):
    rows = snapshot["cases"]
    indexed = {row["id"]: row for row in rows}
    if not indexed or len(rows) != len(indexed):
        raise ValueError("snapshot has no cases or contains duplicate case IDs")
    return indexed


def compare_snapshots(before, after):
    a, b = case_index(before), case_index(after)
    conditions = difference(before["environment"], after["environment"], "$.environment")
    if conditions:
        raise ValueError(conditions)
    if a.keys() != b.keys():
        raise ValueError(f"missing cases {sorted(a.keys() - b.keys())}, extra cases {sorted(b.keys() - a.keys())}")
    failures = [mismatch for name in a if (mismatch := difference(a[name], b[name], name))]
    print(f"{len(a) - len(failures)}/{len(a)} cases identical")
    for failure in failures:
        print(failure)
    return int(bool(failures))


def stats(values):
    ordered = sorted(values)
    quantiles = [ordered[math.ceil(len(ordered) * ratio) - 1] for ratio in (0.50, 0.95, 0.99)]
    return statistics.mean(ordered), *quantiles, ordered[-1]


def milliseconds(values):
    return " | ".join(f"{value:.3f}" for value in stats([x / 1_000_000 for x in values]))


def timings(benchmark):
    width = len(benchmark["inputs"])
    first, warm = benchmark["firstNs"], benchmark["warmNs"]
    if width < 1 or not warm or any(len(row) != width for row in [first, *warm]):
        raise ValueError("benchmark has incomplete samples")
    if any(type(value) is not int or value <= 0 for row in [first, *warm] for value in row):
        raise ValueError("benchmark has invalid sample times")
    return first, [value for row in warm for value in row]


def report(benchmark, label):
    first, values = timings(benchmark)
    print(f"\n## {label} · {benchmark['case']} · {benchmark['backend']} · {benchmark['mode']}\n")
    print(f"Engine resolution {benchmark['resolveEngineNs']/1_000_000:.3f} ms")
    print(f"First query {first[0]/1_000_000:.3f} ms")
    print(f"First sequence {sum(first)/1_000_000:.3f} ms")
    print(f"Warm queries {len(values)} (including edits if the case contains a sequence)")
    print("| Mean ms | P50 ms | P95 ms | P99 ms | Max ms |")
    print("|---:|---:|---:|---:|---:|")
    print(f"| {milliseconds(values)} |")
    if len(first) > 1:
        print("\n| Step | First pass ms | Mean ms | P50 ms | P95 ms | P99 ms | Max ms |")
        print("|---:|---:|---:|---:|---:|---:|---:|")
        for step, samples in enumerate(zip(*benchmark["warmNs"])):
            print(f"| {step + 1} | {first[step]/1_000_000:.3f} | {milliseconds(samples)} |")
    before, after = benchmark["before"], benchmark["after"]
    print(f"PSS {before['pssKiB']} → {after['pssKiB']} KiB")
    for key in ("allocatedBytes", "processCpuMs", "gcCount", "blockingGcMs"):
        a, b = before[key], after[key]
        print(f"{key} delta {b - a if a is not None and b is not None else 'unmeasured'}")
    objects = benchmark["allocationObjects"]
    print(f"Allocated objects {objects if objects is not None else 'unmeasured'}")
    stages = [row for row in benchmark["stages"] if row["round"] > 0]
    if stages:
        print("\n| Stage | n | Mean ms | P50 ms | P95 ms | P99 ms | Max ms |")
        print("|---|---:|---:|---:|---:|---:|---:|")
        for key in ("graphNs", "penaltyNs", "forwardDpNs", "backwardSearchNs", "nBestSelectionNs"):
            measured = [row[key] for row in stages if row[key] is not None and row[key] > 0]
            if measured:
                print(f"| {key} | {len(measured)} | {milliseconds(measured)} |")
            else:
                print(f"| {key} | 0 | " + " | ".join(["Unmeasured"] * 5) + " |")


def benchmark_pair(before, after):
    comparable_fields = (
        "environment", "case", "language", "inputs", "backend", "mode", "warmup",
        "queryMode", "bunsetsu", "correction", "prediction", "personalDictionary",
    )
    mismatch = difference(
        {key: before[key] for key in comparable_fields},
        {key: after[key] for key in comparable_fields},
    )
    if mismatch or len(timings(before)[1]) != len(timings(after)[1]):
        raise ValueError(mismatch or "sample counts differ")
    report(before, "Before")
    report(after, "After")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    assets = commands.add_parser("assets", help="verify identical bundled assets in both APKs")
    assets.add_argument("before", type=Path)
    assets.add_argument("after", type=Path)
    snapshot = commands.add_parser("snapshot", help="fail on any candidate or result mismatch")
    snapshot.add_argument("before", type=Path)
    snapshot.add_argument("after", type=Path)
    bench = commands.add_parser("benchmark", help="report one run or compare matching runs")
    bench.add_argument("before", type=Path)
    bench.add_argument("after", type=Path, nargs="?")
    args = parser.parse_args()
    try:
        if args.command == "assets":
            before = bundled_assets(args.before)
            after = bundled_assets(args.after)
            mismatch = difference(before, after, "$.apk.assets")
            if mismatch:
                raise ValueError(mismatch)
            print(f"{len(before)} bundled assets identical")
            return 0
        before = load(args.before)
        after = load(args.after) if args.after else None
        if args.command == "snapshot":
            return compare_snapshots(before, after)
        if after is None:
            report(before, "Baseline")
        else:
            benchmark_pair(before, after)
    except (BadZipFile, KeyError, ValueError, OSError, TypeError) as error:
        print(f"conversion parity: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
