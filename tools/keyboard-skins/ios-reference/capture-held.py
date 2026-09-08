#!/usr/bin/env python3
"""Capture lossless stills during the long holds marked by ReferenceUITests."""
from pathlib import Path
import re
import subprocess
import sys
import threading

reference_dir = Path(__file__).resolve().parent
root = reference_dir.parents[2]
device, run_name = sys.argv[1:3]
out = root / 'build' / 'keyboard-skins' / (run_name + '-held')
out.mkdir(parents=True, exist_ok=True)
timers = []
failures = []

def capture(name):
    try:
        subprocess.run(['xcrun', 'simctl', 'io', device, 'screenshot', str(out / (name + '.png'))],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except subprocess.CalledProcessError:
        failures.append(name)

proc = subprocess.Popen([str(reference_dir / 'run-tests.sh'), device, run_name, sys.argv[3] if len(sys.argv) > 3 else 'testReferenceMatrix'],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
for line in proc.stdout:
    print(line, end='', flush=True)
    match = re.match(r'CAPTURE_(BEGIN|FAST) (\S+) ', line)
    if match:
        timer = threading.Timer(0.15 if match.group(1) == 'FAST' else 1.0, capture, args=(match.group(2),))
        timer.start()
        timers.append(timer)
code = proc.wait()
for timer in timers:
    timer.join()
if failures:
    print("Failed still captures: " + ", ".join(failures), file=sys.stderr)


expected = {"testControlledReference": 26, "testCenterPreviews": 6}.get(sys.argv[3] if len(sys.argv) > 3 else "")
if expected is not None and len(list(out.glob("*.png"))) != expected:
    raise SystemExit(f"Invalid capture: expected {expected} held images; zero/discovered-no-tests runs are failures")

sys.exit(code or bool(failures))
