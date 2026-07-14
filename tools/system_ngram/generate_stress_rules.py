#!/usr/bin/env python3
"""Writes deterministic temporary rules consumed by the Kotlin version 3 builder."""

import argparse
import random
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=99_997)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    random_source = random.Random(0x4A4B4E47)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="\n") as output:
        output.write("# Temporary deterministic rules for the 100,000-rule performance test.\n")
        for _ in range(args.count):
            words = [f"{random_source.getrandbits(64):016x}" for _ in range(3)]
            output.write(" + ".join(f'"{word}"' for word in words))
            output.write("\n")


if __name__ == "__main__":
    main()
