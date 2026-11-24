#!/usr/bin/env python3
"""Summarize measurements emitted by FastInputMatrixInstrumentedTest."""

import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path

METRICS = {
    "DOWN → editor text change": ("down_ns", "editor_change_ns"),
    "UP → editor text change": ("up_ns", "editor_change_ns"),
    "DOWN → editor frame submission": ("down_ns", "editor_frame_submit_ns"),
    "DOWN → candidate observation": ("down_ns", "candidates_observed_ns"),
    "UP → candidate observation": ("up_ns", "candidates_observed_ns"),
    "Commit UP → committed text observation": ("commit_up_ns", "commit_observed_ns"),
}


def load_samples(path):
    with path.open(newline="", encoding="utf-8") as source:
        samples = {}
        for row in csv.DictReader(source, delimiter="\t", quoting=csv.QUOTE_NONE):
            key = (row["language"], int(row["sample"]))
            if key in samples:
                raise ValueError(f"{path}: duplicate sample {key}")
            samples[key] = row
    if not samples:
        raise ValueError(f"{path}: no samples")
    return samples


def collect_latencies(samples):
    result = defaultdict(list)
    for (language, _), row in samples.items():
        for name, (start_column, stop_column) in METRICS.items():
            start = int(row[start_column])
            stop = int(row[stop_column])
            if min(start, stop) < 0 or (stop and stop < start):
                raise ValueError(f"{language}: invalid clock interval for {name}")
            latencies = result[language, name]
            if stop:
                latencies.append((stop - start) / 1_000_000)
    return result


def percentile(sorted_values, ratio):
    return sorted_values[math.ceil(len(sorted_values) * ratio) - 1]


def print_stats(name, samples):
    print(f"\n## {name}\n")
    print("| Language | Endpoint | n | Mean ms | P50 ms | P95 ms | P99 ms | Max ms |")
    print("|---|---|---:|---:|---:|---:|---:|---:|")
    for (language, endpoint), values in collect_latencies(samples).items():
        if values:
            ordered = sorted(values)
            stats = [sum(values) / len(values), *(percentile(ordered, p) for p in (0.50, 0.95, 0.99)), ordered[-1]]
            columns = " | ".join(f"{value:.3f}" for value in stats)
        else:
            columns = " | ".join(["Unmeasured"] * 5)
        print(f"| {language} | {endpoint} | {len(values)} | {columns} |")


def compare(before, after):
    if before.keys() != after.keys():
        raise ValueError("Before and after must contain the same language/sample pairs")
    differences = []
    for key, prior in before.items():
        later = after[key]
        if any(
            json.loads(prior[column]) != json.loads(later[column])
            for column in ("ordered_candidates", "committed_text")
        ):
            differences.append(key)
    print("\n## P95 and P99 comparison\n")
    print("| Language | Endpoint | Before P95 | After P95 | P95 improvement | Before P99 | After P99 | P99 improvement |")
    print("|---|---|---:|---:|---:|---:|---:|---:|")
    after_by_endpoint = collect_latencies(after)
    for (language, endpoint), previous in collect_latencies(before).items():
        current = after_by_endpoint[language, endpoint]
        if not previous or not current or len(previous) != len(current):
            print(f"| {language} | {endpoint} | Unmeasured | Unmeasured | — | Unmeasured | Unmeasured | — |")
            continue
        columns = []
        for p in (0.95, 0.99):
            baseline = percentile(sorted(previous), p)
            changed = percentile(sorted(current), p)
            improvement = f"{(baseline - changed) / baseline * 100:.1f}%" if baseline else "—"
            columns.extend((f"{baseline:.3f}", f"{changed:.3f}", improvement))
        print(f"| {language} | {endpoint} | {' | '.join(columns)} |")
    print(f"\nCandidate or commit differences: {len(differences)} of {len(before)} samples.")
    for language, sample in differences[:10]:
        print(f"- {language} sample {sample}")
    return bool(differences)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path, nargs="?")
    args = parser.parse_args()
    try:
        before = load_samples(args.before)
        print_stats("Baseline", before)
        if args.after is None:
            return 0
        after = load_samples(args.after)
        print_stats("After", after)
        return int(compare(before, after))
    except (KeyError, ValueError, OSError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    raise SystemExit(main())
