#!/usr/bin/env python3
"""Run serialized, fresh-process probes against an already installed isolated test package.

Install the dev application APK for --label baseline, then the final APK for --label integrated.
The same instrumentation APK/harness must be used for both. No normal IME package is touched.
"""
import argparse
import json
import pathlib
import subprocess
import gzip
import re

parser = argparse.ArgumentParser()
parser.add_argument("--label", required=True)
parser.add_argument("--output", required=True, type=pathlib.Path)
parser.add_argument("--rules", action="store_true")
parser.add_argument("--rounds", type=int, default=3)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
package = "com.kazumaproject.markdownhelperkeyboard.lite.counterprobe"
class_name = "CounterDictionaryPerformanceInstrumentedTest" if args.rules else "CounterConversionPerformanceInstrumentedTest"

def shell(*parts):
    return subprocess.check_output(["adb", "shell", *parts], text=True)

for index in range(1, args.rounds + 1):
    label = f"{args.label}-{index}"
    shell("am", "force-stop", package)
    battery = shell("dumpsys", "battery")
    thermal = shell("dumpsys", "thermalservice")
    def field(text, pattern):
        match = re.search(pattern, text)
        return match.group(1) if match else None
    environment = {"fingerprint": shell("getprop", "ro.build.fingerprint").strip(), "batteryLevelPercent": field(battery, r"level: (\d+)"), "batteryTemperatureTenthsC": field(battery, r"temperature: (\d+)"), "thermalStatus": field(thermal, r"Thermal Status: (\d+)")}
    args.output.joinpath(label + "-environment.json").write_text(json.dumps(environment, ensure_ascii=False, indent=2))
    command = ["adb", "shell", "am", "instrument", "-w", "-r", "-e", "class", "com.kazumaproject.markdownhelperkeyboard.converter." + class_name, "-e", "counterPerf", "true", "-e", "counterPerfLabel", label, package + ".test/androidx.test.runner.AndroidJUnitRunner"]
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, check=True)
    args.output.joinpath(label + ".log").write_text(result.stdout)
    if "OK (1 test)" not in result.stdout:
        raise SystemExit(f"{label} did not pass; inspect the saved instrumentation log")
    data = shell("run-as", package, "cat", "files/counter-perf/" + label + ".txt").encode()
    with gzip.GzipFile(filename=str(args.output.joinpath(label + ".txt.gz")), mode="wb", mtime=0) as output:
        output.write(data)
    print(label + ": PASS", flush=True)
