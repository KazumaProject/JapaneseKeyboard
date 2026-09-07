#!/usr/bin/env python3
"""Run the Enter probe with shell-safe selectors and a reliable failure exit status."""
import argparse
import os
import re
import shlex
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
parser.add_argument("--package", default="com.kazumaproject.markdownhelperkeyboard.lite")
parser.add_argument("--mode", choices=("record", "compare", "inventory"), default="record")
parser.add_argument("--ime")
parser.add_argument("--cases")
parser.add_argument("--surface")
parser.add_argument("--keyboard", choices=("TENKEY", "SUMIRE", "QWERTY", "ROMAJI", "GOJUON"))
parser.add_argument("--setup")
parser.add_argument("--before-text")
parser.add_argument("--state-marker")
parser.add_argument("--enter")
parser.add_argument("--bunsetsu", action="store_true", help="Temporarily enable own IME clause conversion")
args = parser.parse_args()
remote = ["am", "instrument", "-w", "-r", "-e", "class",
          "com.kazumaproject.markdownhelperkeyboard.enter.EnterProbeInstrumentedTest"]
for key, value in {
    "probeBunsetsu": "true" if args.bunsetsu else None,
    "probeMode": args.mode, "probeIme": args.ime, "probeCases": args.cases,
    "probeSurface": args.surface, "probeKeyboard": args.keyboard, "probeSetup": args.setup,
    "probeBeforeText": args.before_text, "probeStateMarker": args.state_marker, "probeEnter": args.enter,
}.items():
    if value is not None:
        remote += ["-e", key, value]
remote += [args.package + ".test/androidx.test.runner.AndroidJUnitRunner"]
command = ["adb"] + (["-s", args.serial] if args.serial else []) + ["shell", shlex.join(remote)]
process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
lines = []
for line in process.stdout:
    lines.append(line)
    print(line, end="", flush=True)
code = process.wait()
output = "".join(lines)
# adb itself often exits 0 even when instrumentation has failed.
passed = code == 0 and re.search(r"OK \(\d+ tests?\)", output) is not None
raise SystemExit(0 if passed else 1)
