#!/usr/bin/env python3
"""Run an isolated toolbar case, retain evidence, and restore device state on failure."""
import argparse
import hashlib
import io
import json
import os
import shlex
from pathlib import Path
import subprocess
import tarfile
import time

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.kazumaproject.toolbarqa"
TEST = "com.kazumaproject.markdownhelperkeyboard.FastInputMatrixInstrumentedTest#independentShortcutToolbarKeepsImeBoundsAcrossKanaConversionAndCommit"


def latest_apk(package, android_test=False):
    candidates = []
    for path in (ROOT / "app/build").rglob("output-metadata.json"):
        if "/apk/" not in str(path):
            continue
        data = json.loads(path.read_text())
        if data.get("applicationId") != package:
            continue
        for element in data.get("elements", []):
            apk = path.parent / element["outputFile"]
            if apk.exists():
                candidates.append(apk)
    if not candidates:
        raise RuntimeError(f"No APK metadata matches isolated package {package}")
    return max(candidates, key=lambda path: path.stat().st_mtime)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("label")
    parser.add_argument("--mode", choices=("on", "off", "integrated"), default="on")
    parser.add_argument("--tab", action="store_true")
    parser.add_argument("--height", type=int, default=36)
    parser.add_argument("--floating", action="store_true")
    parser.add_argument("--landscape", action="store_true")
    parser.add_argument("--candidate-height", type=int, default=60)
    parser.add_argument("--cycles", type=int, default=20)
    parser.add_argument("--expect-height-change", action="store_true")
    parser.add_argument("--app-apk", type=Path)
    parser.add_argument("--test-apk", type=Path)
    parser.add_argument("--no-install", action="store_true")
    options = parser.parse_args()
    if not options.label.replace("-", "").replace("_", "").isalnum():
        parser.error("label must contain only letters, digits, '-' and '_'")
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    adb = [str(sdk / "platform-tools/adb"), "-s", options.serial]

    def run(arguments, timeout=60, check=True):
        process = subprocess.run(adb + arguments, capture_output=True, text=True, timeout=timeout)
        if check and process.returncode:
            raise RuntimeError(f"adb {arguments}: {process.stdout} {process.stderr}")
        return process.stdout.strip()

    def shell(command, **kwargs):
        return run(["shell", command], **kwargs)

    api = shell("getprop ro.build.version.sdk")
    out = ROOT / "build/reports/toolbar-flicker" / f"{options.serial}-api{api}" / options.label
    out.mkdir(parents=True, exist_ok=True)
    original = {
        "ime": shell("settings get secure default_input_method"),
        "enabled": shell("settings get secure enabled_input_methods"),
        "user_rotation": shell("settings get system user_rotation"),
        "accelerometer_rotation": shell("settings get system accelerometer_rotation"),
    }
    app = options.app_apk or latest_apk(PACKAGE)
    test = options.test_apk or latest_apk(PACKAGE + ".test", android_test=True)
    aapt = sdk / "build-tools/36.0.0/aapt"
    for apk, package in ((app, PACKAGE), (test, PACKAGE + ".test")):
        badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
        if not badging.startswith(f"package: name='{package}'"):
            raise RuntimeError(f"Refusing to install {apk}: applicationId is not {package}")
    metadata = {
        "gitHead": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
        "gitDiff": subprocess.check_output(["git", "diff", "--stat"], cwd=ROOT, text=True),
        "arguments": vars(options) | {"app_apk": str(app), "test_apk": str(test)},
        "device": shell("getprop ro.build.fingerprint"), "api": api, "original": original,
        "apkSha256": {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in (app, test)},
    }
    record_pid = None
    host_recording = False
    remote = f"/data/local/tmp/toolbar-{options.label}.mp4"
    logcat = None
    passed = False
    start = time.monotonic()
    try:
        if not options.no_install:
            for apk in (app, test):
                print(run(["install", "-r", "-t", str(apk)], timeout=180), flush=True)
        shell("input keyevent WAKEUP")
        shell("wm dismiss-keyguard")
        shell("settings put system accelerometer_rotation 0")
        shell(f"settings put system user_rotation {1 if options.landscape else 0}")
        time.sleep(1)
        log_stream = (out / "logcat.txt").open("w")
        logcat = subprocess.Popen(adb + ["logcat", "-v", "threadtime", "-T", "1"], stdout=log_stream, stderr=subprocess.STDOUT)
        # Host recording works on API 24/29 too, independently of guest AVC support.
        if options.serial.startswith("emulator-"):
            recording = run(["emu", "screenrecord", "start", "--fps", "60", "--bit-rate", "8M",
                             "--time-limit", "170", str(out / "screen.webm")])
            (out / "screenrecord.log").write_text(recording)
            host_recording = "OK" in recording and "KO" not in recording
            metadata["recordBackend"] = "emulator-host-60fps"
        else:
            record_pid = shell(f"screenrecord --time-limit 170 {remote} >{remote}.log 2>&1 & echo $!")
            metadata["recordBackend"] = "device-screenrecord"
        metadata["recordStartedUptimeMs"] = int(float(shell("cat /proc/uptime").split()[0]) * 1000)
        command = ["shell", "am", "instrument", "-w", "-e", "class", TEST]
        for key, value in {
            "label": options.label, "toolbarMode": options.mode,
            "candidateTab": str(options.tab).lower(), "toolbarHeight": str(options.height),
            "floating": str(options.floating).lower(),
            "rotation": "landscape" if options.landscape else "portrait",
            "candidateHeight": str(options.candidate_height), "cycles": str(options.cycles),
            "expectHeightChange": str(options.expect_height_change).lower(),
        }.items():
            command += ["-e", key, value]
        command += [f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"]
        metadata["command"] = command
        try:
            process = subprocess.run(adb + command, capture_output=True, text=True, timeout=160)
            result = process.stdout + process.stderr
            (out / "instrumentation.log").write_text(result)
            passed = "OK (1 test)" in result and "FAILURES" not in result
        except subprocess.TimeoutExpired as error:
            (out / "instrumentation.log").write_text(str(error))
            metadata["timeout"] = True
            (out / "timeout-window.txt").write_text(shell("dumpsys window windows"))
            shell(f"am force-stop {PACKAGE}")
    finally:
        if host_recording:
            run(["emu", "screenrecord", "stop"], check=False)
        if record_pid and record_pid.isdigit():
            shell(f"kill -2 {record_pid}", check=False)
            time.sleep(2)
            run(["pull", remote, str(out / "screen.mp4")], check=False)
            (out / "screenrecord.log").write_text(shell(f"cat {remote}.log", check=False))
            shell(f"rm -f {remote} {remote}.log", check=False)
        if logcat:
            logcat.terminate()
            logcat.wait(timeout=10)
            log_stream.close()
        # Outer restoration also runs when instrumentation cannot execute its finally block.
        if original["enabled"] != "null":
            shell(f"settings put secure enabled_input_methods {shlex.quote(original['enabled'])}")
        if original["ime"] and original["ime"] != "null":
            for attempt in range(5):
                if shell("settings get secure default_input_method") == original["ime"]:
                    break
                shell(f"ime set {original['ime']}", check=False)
                time.sleep(0.5)
        for key in ("user_rotation", "accelerometer_rotation"):
            value = original[key]
            shell(f"settings delete system {key}" if value == "null" else f"settings put system {key} {value}")
        archive = subprocess.run(adb + ["exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "-C", "files", f"toolbar-regression/{options.label}"], capture_output=True)
        if archive.returncode == 0 and len(archive.stdout) >= 512:
            with tarfile.open(fileobj=io.BytesIO(archive.stdout)) as source:
                for member in source.getmembers():
                    path = Path(member.name)
                    if member.isfile() and not path.is_absolute() and ".." not in path.parts:
                        destination = out / path
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        destination.write_bytes(source.extractfile(member).read())
        else:
            (out / "evidence-error.txt").write_bytes(archive.stdout + archive.stderr)
            passed = False
        metadata["elapsedSeconds"] = round(time.monotonic() - start, 2)
        metadata["passed"] = passed
        metadata["restored"] = {
            "ime": shell("settings get secure default_input_method"),
            "enabled": shell("settings get secure enabled_input_methods"),
            "user_rotation": shell("settings get system user_rotation"),
            "accelerometer_rotation": shell("settings get system accelerometer_rotation"),
        }
        if metadata["restored"] != original:
            metadata["passed"] = passed = False
            metadata["restorationFailure"] = True
        (out / "metadata.json").write_text(json.dumps(metadata, indent=2))
    print(f"{'PASS' if passed else 'FAIL'} {options.serial} {options.label}: {out}", flush=True)
    if not passed:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
