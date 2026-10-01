#!/usr/bin/env python3
"""Run 20 actual IME cycles per fixture; retain failures and continue independent cases."""
import argparse
import itertools
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def fixtures(extended):
    yield "normal-on", []
    yield "normal-off", ["--mode", "off"]
    if not extended:
        return
    for tab, height, landscape in itertools.product((False, True), (32, 36, 72), (False, True)):
        if (tab, height, landscape) == (False, 36, False):
            continue
        name = f"on-tab{int(tab)}-height{height}-{'landscape' if landscape else 'portrait'}"
        yield name, ["--height", str(height)] + (["--tab"] if tab else []) + (["--landscape"] if landscape else [])
    for mode, tab, landscape in itertools.product(("off", "integrated"), (False, True), (False, True)):
        if mode == "off" and not tab and not landscape:
            continue
        yield f"{mode}-tab{int(tab)}-{'landscape' if landscape else 'portrait'}", ["--mode", mode] + (["--tab"] if tab else []) + (["--landscape"] if landscape else [])
    for name, landscape in itertools.product(("floating", "asymmetric"), (False, True)):
        yield f"{name}-{'landscape' if landscape else 'portrait'}", (["--floating"] if name == "floating" else ["--candidate-height", "80"]) + (["--landscape"] if landscape else [])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--extended", action="store_true")
    parser.add_argument("--app-apk", type=Path)
    parser.add_argument("--test-apk", type=Path)
    options = parser.parse_args()
    results = []
    for index, (label, parameters) in enumerate(fixtures(options.extended)):
        command = [sys.executable, str(ROOT / "investigation/run_toolbar_regression.py"), options.serial,
                   label, "--cycles", "20"] + parameters
        if index:
            command += ["--no-install"]
        for flag, value in (("--app-apk", options.app_apk), ("--test-apk", options.test_apk)):
            if value:
                command += [flag, str(value)]
        print(f"START {options.serial} {label}", flush=True)
        result = subprocess.run(command, cwd=ROOT)
        api = subprocess.check_output(["adb", "-s", options.serial, "shell", "getprop", "ro.build.version.sdk"], text=True).strip()
        evidence = ROOT / "build/reports/toolbar-flicker" / f"{options.serial}-api{api}" / label
        video = subprocess.run([sys.executable, str(ROOT / "investigation/analyze_toolbar_video.py"), str(evidence)], cwd=ROOT)
        results.append({"label":label,"exitCode":result.returncode,"videoExitCode":video.returncode})
    directory = ROOT / "build/reports/toolbar-flicker"
    (directory / f"matrix-{options.serial}.json").write_text(json.dumps(results, indent=2))
    print(f"MATRIX {options.serial}: {sum(r['exitCode']==0 and r['videoExitCode']==0 for r in results)}/{len(results)} passed", flush=True)
    if any(r["exitCode"] or r["videoExitCode"] for r in results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
