#!/usr/bin/env python3
"""Generate Mozc segmenter assets for the Android converter.

The inputs are Mozc's rule files and the app's bundled id.def.  The output
format is intentionally small and JVM-friendly:

* prefix_penalty.dat: little-endian uint16 prefix penalties by POS id.
* suffix_penalty.dat: little-endian uint16 suffix penalties by POS id.
* boundary_rule.dat: custom binary bit matrix with a small header.
* pos_group.dat: text POS matcher groups and ranges.
"""

from __future__ import annotations

import argparse
import re
import struct
import zipfile
from dataclasses import dataclass
from pathlib import Path


SPECIAL_POS = (
    "特殊,郵便番号",
    "特殊,短縮よみ",
    "特殊,サジェストのみ",
)


@dataclass(frozen=True)
class PosEntry:
    pos_id: int
    feature: str


@dataclass(frozen=True)
class BoundaryRule:
    left_pattern: str
    right_pattern: str
    result: bool


@dataclass(frozen=True)
class PosMatcherRule:
    name: str
    pattern: str


def read_zip_text(zip_path: Path, name: str) -> str:
    with zipfile.ZipFile(zip_path) as zf:
        with zf.open(name) as fp:
            return fp.read().decode("utf-8")


def parse_id_def(path: Path) -> list[PosEntry]:
    entries: list[PosEntry] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        fields = stripped.split(maxsplit=1)
        entries.append(PosEntry(int(fields[0]), fields[1]))
    next_id = max(entry.pos_id for entry in entries) + 1
    for feature in SPECIAL_POS:
        entries.append(PosEntry(next_id, feature))
        next_id += 1
    return entries


def boundary_pattern_to_regex(pattern: str) -> re.Pattern[str]:
    return re.compile("^" + pattern.replace("*", "[^,]+"))


def segmenter_pattern_to_regex(pattern: str) -> re.Pattern[str]:
    return re.compile(pattern.replace("*", "[^,]+"))


def parse_boundary_def(text: str) -> tuple[list[tuple[re.Pattern[str], int]], list[tuple[re.Pattern[str], int]]]:
    prefix: list[tuple[re.Pattern[str], int]] = []
    suffix: list[tuple[re.Pattern[str], int]] = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        label, feature, cost_text = stripped.split()[:3]
        cost = int(cost_text)
        if not 0 <= cost <= 0xFFFF:
            raise ValueError(f"Penalty out of uint16 range: {line}")
        target = prefix if label == "PREFIX" else suffix if label == "SUFFIX" else None
        if target is None:
            raise ValueError(f"Unknown boundary label: {line}")
        target.append((boundary_pattern_to_regex(feature), cost))
    return prefix, suffix


def penalty_for(patterns: list[tuple[re.Pattern[str], int]], feature: str) -> int:
    for pattern, cost in patterns:
        if pattern.match(feature):
            return cost
    return 0


def parse_segmenter_def(text: str) -> list[BoundaryRule]:
    rules: list[BoundaryRule] = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        left, right, result = stripped.split()[:3]
        rules.append(BoundaryRule(left, right, result.lower() == "true"))
    return rules


def parse_pos_matcher_rules(text: str) -> list[PosMatcherRule]:
    rules: list[PosMatcherRule] = []
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        name, pattern = stripped.split(maxsplit=1)
        rules.append(PosMatcherRule(name, pattern))
    return rules


def matching_ids(entries: list[PosEntry], pattern: str, *, boundary: bool) -> list[int]:
    if pattern == "*":
        return [entry.pos_id for entry in entries]
    regex = boundary_pattern_to_regex(pattern) if boundary else segmenter_pattern_to_regex(pattern)
    ids = [entry.pos_id for entry in entries if regex.match(entry.feature)]
    if not ids:
        raise ValueError(f"No POS id matched pattern: {pattern}")
    return ids


def ranges_from_ids(ids: list[int]) -> list[tuple[int, int]]:
    if not ids:
        return []
    sorted_ids = sorted(set(ids))
    ranges: list[tuple[int, int]] = []
    start = prev = sorted_ids[0]
    for pos_id in sorted_ids[1:]:
        if pos_id == prev + 1:
            prev = pos_id
            continue
        ranges.append((start, prev))
        start = prev = pos_id
    ranges.append((start, prev))
    return ranges


def write_uint16_array(path: Path, values: list[int]) -> None:
    path.write_bytes(b"".join(struct.pack("<H", value) for value in values))


def write_boundary_matrix(path: Path, entries: list[PosEntry], rules: list[BoundaryRule]) -> None:
    size = max(entry.pos_id for entry in entries) + 1
    matrix = bytearray(b"\x01" * (size * size))

    compiled = [
        (
            matching_ids(entries, rule.left_pattern, boundary=True),
            matching_ids(entries, rule.right_pattern, boundary=True),
            1 if rule.result else 0,
        )
        for rule in rules
    ]

    # Later rules are defaults for earlier rules.  Iterate backwards so earlier
    # Mozc rules overwrite later ones without evaluating every pattern per cell.
    for left_ids, right_ids, value in reversed(compiled):
        value_byte = bytes([value])
        for rid in left_ids:
            for lid in right_ids:
                matrix[rid + size * lid] = value_byte[0]

    # BOS/EOS always form boundaries.
    for i in range(size):
        matrix[0 + size * i] = 1
        matrix[i + size * 0] = 1

    bits = bytearray((len(matrix) + 7) // 8)
    for index, value in enumerate(matrix):
        if value:
            bits[index // 8] |= 1 << (index % 8)

    l_table = list(range(size))
    r_table = list(range(size))
    payload = bytearray()
    payload += b"MZBD1"
    payload += struct.pack("<IIII", size, size, len(l_table), len(r_table))
    payload += b"".join(struct.pack("<H", value) for value in l_table)
    payload += b"".join(struct.pack("<H", value) for value in r_table)
    payload += struct.pack("<I", len(bits))
    payload += bits
    path.write_bytes(payload)


def write_pos_group(path: Path, entries: list[PosEntry], rules: list[PosMatcherRule]) -> None:
    lines: list[str] = []
    for rule in rules:
        ids = matching_ids(entries, rule.pattern, boundary=False)
        group_id = min(ids)
        ranges = ",".join(f"{start}-{end}" for start, end in ranges_from_ids(ids))
        lines.append(f"{rule.name}\t{group_id}\t{ranges}\t{rule.pattern}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mozc-zip", type=Path, required=True)
    parser.add_argument("--id-def", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    entries = parse_id_def(args.id_def)
    boundary_text = read_zip_text(args.mozc_zip, "mozc-master/src/data/rules/boundary.def")
    segmenter_text = read_zip_text(args.mozc_zip, "mozc-master/src/data/rules/segmenter.def")
    pos_matcher_text = read_zip_text(args.mozc_zip, "mozc-master/src/data/rules/pos_matcher_rule.def")

    prefix_rules, suffix_rules = parse_boundary_def(boundary_text)
    prefix = [penalty_for(prefix_rules, entry.feature) for entry in entries]
    suffix = [penalty_for(suffix_rules, entry.feature) for entry in entries]

    args.output_dir.mkdir(parents=True, exist_ok=True)
    write_uint16_array(args.output_dir / "prefix_penalty.dat", prefix)
    write_uint16_array(args.output_dir / "suffix_penalty.dat", suffix)
    write_boundary_matrix(args.output_dir / "boundary_rule.dat", entries, parse_segmenter_def(segmenter_text))
    write_pos_group(args.output_dir / "pos_group.dat", entries, parse_pos_matcher_rules(pos_matcher_text))


if __name__ == "__main__":
    main()
