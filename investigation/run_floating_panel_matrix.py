#!/usr/bin/env python3
"""Run the isolated IME's real touch tests. Build with floating-panel.init.gradle first."""
import argparse
import io
import os
from pathlib import Path
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.kazumaproject.floatingqa.lite"
DEFAULT_CASES = [f"{surface}:{rotation}:TENKEY:default" for surface in ("split", "floating") for rotation in ("portrait", "landscape")]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial", help="adb device serial")
    parser.add_argument("cases", nargs="*", help="surface:rotation:keyboard:skin[:media]")
    args = parser.parse_args()
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        props = (ROOT / "local.properties").read_text().splitlines()
        sdk = next(line.split("=", 1)[1] for line in props if line.startswith("sdk.dir="))
    adb = [str(Path(sdk) / "platform-tools/adb"), "-s", args.serial]

    def run(command, timeout=180):
        return subprocess.run(adb + command, capture_output=True, text=True, timeout=timeout, check=True).stdout.strip()

    api = run(["shell", "getprop", "ro.build.version.sdk"])
    out = ROOT / "build/reports/floating-panel" / f"{args.serial}-api{api}"
    out.mkdir(parents=True, exist_ok=True)
    original_ime = run(["shell", "settings", "get", "secure", "default_input_method"])
    original_rotation = run(["shell", "settings", "get", "system", "user_rotation"])
    original_auto = run(["shell", "settings", "get", "system", "accelerometer_rotation"])
    failures = []
    try:
        for apk in ("liteStandard/debug/app-lite-standard-debug.apk", "androidTest/liteStandard/debug/app-lite-standard-debug-androidTest.apk"):
            print(run(["install", "-r", str(ROOT / "app/build/outputs/apk" / apk)]), flush=True)
        for case in args.cases or DEFAULT_CASES:
            parts = case.split(":")
            if len(parts) not in (4, 5):
                raise ValueError(f"Invalid case: {case}")
            surface, rotation, keyboard, skin = parts[:4]
            media = parts[4] if len(parts) == 5 else "none"
            run(["shell", "am", "force-stop", PACKAGE])
            command = ["shell", "am", "instrument", "-w", "-e", "class", "com.kazumaproject.markdownhelperkeyboard.FloatingPanelDeviceTest"]
            for name, value in (("surface", surface), ("rotation", rotation), ("keyboard", keyboard), ("skin", skin), ("media", media)):
                command += ["-e", name, value]
            command += [f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"]
            result = run(command, timeout=240)
            (out / (case.replace(":", "-") + ".log")).write_text(result)
            passed = "OK (1 test)" in result
            print(f"{case}: {'PASS' if passed else 'FAIL'}", flush=True)
            if not passed:
                failures.append(case)
            archive = subprocess.run(adb + ["exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "-C", "files", "floating-panel"], capture_output=True, check=True).stdout
            with tarfile.open(fileobj=io.BytesIO(archive)) as source:
                for member in source.getmembers():
                    relative = Path(member.name)
                    if member.isfile() and not relative.is_absolute() and ".." not in relative.parts:
                        destination = out / relative
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        destination.write_bytes(source.extractfile(member).read())
    finally:
        # Also restore if instrumentation crashes or times out before its own cleanup.
        if original_ime and original_ime != "null":
            run(["shell", "ime", "set", original_ime])
        for key, value in (("user_rotation", original_rotation), ("accelerometer_rotation", original_auto)):
            if value != "null":
                run(["shell", "settings", "put", "system", key, value])
    print(f"Evidence: {out}")
    if failures:
        raise SystemExit("Failed: " + ", ".join(failures))


if __name__ == "__main__":
    main()
