#!/usr/bin/env python3
"""Generates Mozc segmenter assets for the Kotlin converter runtime."""

from __future__ import annotations

import argparse
import filecmp
import re
import struct
from pathlib import Path


def _iter_data_lines(path: Path):
    with path.open("r", encoding="utf-8") as source:
        for line in source:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            yield stripped


def _boundary_pattern_to_regex(pattern: str) -> re.Pattern[str]:
    regex = "^" + pattern.replace("*", "[^,]+")
    if not regex.endswith(","):
        regex += "(?:,|$)"
    return re.compile(regex)


def _segmenter_pattern_to_regex(pattern: str) -> re.Pattern[str] | None:
    if pattern == "*":
        return None
    return re.compile(pattern.replace("*", "[^,]+"))


def _load_pos(app_id_def: Path, special_pos: Path) -> list[tuple[int, str]]:
    rows: list[tuple[int, str]] = []
    max_id = -1
    for line in _iter_data_lines(app_id_def):
        fields = line.split()
        pos_id = int(fields[0])
        rows.append((pos_id, fields[1]))
        max_id = max(max_id, pos_id)

    next_id = max_id + 1
    for line in _iter_data_lines(special_pos):
        feature = line.split()[0]
        rows.append((next_id, feature))
        next_id += 1

    rows.sort(key=lambda item: item[0])
    expected = list(range(len(rows)))
    actual = [pos_id for pos_id, _ in rows]
    if actual != expected:
        raise ValueError("id.def POS IDs must be contiguous from 0")
    return rows


def _generate_boundary(boundary_def: Path, pos_rows: list[tuple[int, str]]) -> bytes:
    prefix: list[tuple[re.Pattern[str], int]] = []
    suffix: list[tuple[re.Pattern[str], int]] = []
    for line in _iter_data_lines(boundary_def):
        label, feature, cost_text = line.split()
        cost = int(cost_text)
        if cost < 0 or cost > 0xFFFF:
            raise ValueError(f"Boundary penalty out of uint16 range: {line}")
        item = (_boundary_pattern_to_regex(feature), cost)
        if label == "PREFIX":
            prefix.append(item)
        elif label == "SUFFIX":
            suffix.append(item)
        else:
            raise ValueError(f"Unknown boundary label: {line}")

    data = bytearray()
    for _, feature in pos_rows:
        prefix_cost = next((cost for regex, cost in prefix if regex.match(feature)), 0)
        suffix_cost = next((cost for regex, cost in suffix if regex.match(feature)), 0)
        data += struct.pack("<H", prefix_cost)
        data += struct.pack("<H", suffix_cost)
    return bytes(data)


def _load_segmenter_rules(segmenter_def: Path):
    rules = []
    for line in _iter_data_lines(segmenter_def):
        left, right, result = line.split()
        rules.append((
            _segmenter_pattern_to_regex(left),
            _segmenter_pattern_to_regex(right),
            result.lower() == "true",
        ))
    return rules


def _matches(regex: re.Pattern[str] | None, feature: str) -> bool:
    return True if regex is None else regex.match(feature) is not None


def _generate_segmenter(segmenter_def: Path, pos_rows: list[tuple[int, str]]):
    rules = _load_segmenter_rules(segmenter_def)
    pos_size = len(pos_rows)
    features = [feature for _, feature in pos_rows]

    matrix = [[True for _ in range(pos_size)] for _ in range(pos_size)]
    for rid, left_feature in enumerate(features):
        for lid, right_feature in enumerate(features):
            if rid == 0 or lid == 0:
                matrix[rid][lid] = True
                continue
            for left_regex, right_regex, result in rules:
                if _matches(left_regex, left_feature) and _matches(right_regex, right_feature):
                    matrix[rid][lid] = result
                    break

    compressed_rid_table, compressed_l_size = _compress_states(
        tuple(tuple(row) for row in matrix)
    )
    columns = tuple(tuple(matrix[rid][lid] for rid in range(pos_size)) for lid in range(pos_size))
    compressed_lid_table, compressed_r_size = _compress_states(columns)

    bit_count = compressed_l_size * compressed_r_size
    bitarray = bytearray((bit_count + 7) // 8)
    for rid in range(pos_size):
        for lid in range(pos_size):
            if matrix[rid][lid]:
                bit_index = compressed_rid_table[rid] + compressed_l_size * compressed_lid_table[lid]
                bitarray[bit_index >> 3] |= 1 << (bit_index & 7)

    return compressed_l_size, compressed_r_size, compressed_rid_table, compressed_lid_table, bytes(bitarray)


def _compress_states(states: tuple[tuple[bool, ...], ...]) -> tuple[list[int], int]:
    seen: dict[tuple[bool, ...], int] = {}
    table: list[int] = []
    for state in states:
        compressed = seen.get(state)
        if compressed is None:
            compressed = len(seen)
            seen[state] = compressed
        table.append(compressed)
    return table, len(seen)


def _write_uint16_array(path: Path, values: list[int]) -> None:
    path.write_bytes(b"".join(struct.pack("<H", value) for value in values))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mozc_dir", required=True, type=Path)
    parser.add_argument("--app_id_def", required=True, type=Path)
    parser.add_argument("--output_dir", required=True, type=Path)
    args = parser.parse_args()

    rules_dir = args.mozc_dir / "src" / "data" / "rules"
    mozc_id_def = args.mozc_dir / "src" / "data" / "dictionary_oss" / "id.def"
    boundary_def = rules_dir / "boundary.def"
    segmenter_def = rules_dir / "segmenter.def"
    special_pos = rules_dir / "special_pos.def"

    if not filecmp.cmp(args.app_id_def, mozc_id_def, shallow=False):
        raise SystemExit(
            f"id.def mismatch: {args.app_id_def} and {mozc_id_def} must be byte-identical"
        )

    pos_rows = _load_pos(args.app_id_def, special_pos)
    boundary = _generate_boundary(boundary_def, pos_rows)
    compressed_l_size, compressed_r_size, l_table, r_table, bitarray = _generate_segmenter(
        segmenter_def, pos_rows
    )

    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / "segmenter_meta.dat").write_bytes(
        struct.pack("<III", compressed_l_size, compressed_r_size, len(pos_rows))
    )
    _write_uint16_array(args.output_dir / "segmenter_ltable.dat", l_table)
    _write_uint16_array(args.output_dir / "segmenter_rtable.dat", r_table)
    (args.output_dir / "segmenter_bitarray.dat").write_bytes(bitarray)
    (args.output_dir / "boundary.dat").write_bytes(boundary)


if __name__ == "__main__":
    main()
