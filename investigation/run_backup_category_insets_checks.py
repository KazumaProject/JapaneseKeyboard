"""Run isolated APKs built with probe.init.gradle; never change the default IME."""
import argparse
import os
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5580"))
parser.add_argument("--edition", choices=["lite", "full", "both"], default="both")
parser.add_argument("--focused", action="store_true")
parser.add_argument("--label", default="fixed")
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
evidence = root / "investigation/backup-category-insets-evidence"
evidence.mkdir(exist_ok=True)
adb = [os.environ.get("ADB", "adb"), "-s", args.serial]


def run(command, **kwargs):
    return subprocess.run(adb + command, check=True, **kwargs)


def read(command):
    return run(command, stdout=subprocess.PIPE, text=True).stdout.strip()


classes = ["com.kazumaproject.markdownhelperkeyboard.setting_activity.SettingsBackupInsetsInstrumentedTest"]
if not args.focused:
    classes += [
        "com.kazumaproject.markdownhelperkeyboard.setting_activity.SettingsNavigationLayoutInstrumentedTest",
        "com.kazumaproject.markdownhelperkeyboard.setting_activity.SettingsAsyncLoadingInstrumentedTest",
    ]
# PR #1122 documents that this pre-existing continuously drawing preview stalls
# ActivityScenario's global-idle wait on the original dev as well.
excluded = "com.kazumaproject.markdownhelperkeyboard.setting_activity.SettingsNavigationLayoutInstrumentedTest#candidatePreviewsKeepNavigationHiddenAcrossRecreationAndRestoreItOnReturn"
original_enabled = read(["shell", "settings", "get", "secure", "enabled_input_methods"])
original_default = read(["shell", "settings", "get", "secure", "default_input_method"])
added_imes = []
try:
    for edition in (["lite", "full"] if args.edition == "both" else [args.edition]):
        suffix = ".lite" if edition == "lite" else ""
        package = "com.kazumaproject.markdownhelperkeyboard" + suffix + ".freezeprobe"
        other = "com.kazumaproject.markdownhelperkeyboard" + ("" if suffix else ".lite") + ".freezeprobe.test"
        # Test-only providers have fixed authorities; install only one edition's test APK.
        if read(["shell", "pm", "list", "packages", other]):
            run(["uninstall", other])
        for apk in [
            root / f"app/build/outputs/apk/{edition}Standard/debug/app-{edition}-standard-debug.apk",
            root / f"app/build/outputs/apk/androidTest/{edition}Standard/debug/app-{edition}-standard-debug-androidTest.apk",
        ]:
            run(["install", "-r", "-t", str(apk)])
        component = package + "/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService"
        if component not in original_enabled:
            added_imes.append(component)
        run(["shell", "ime", "enable", component])
        logfile = evidence / f"{edition}-{args.label}.log"
        print(f"Running {edition}: {logfile}", flush=True)
        with logfile.open("w") as output:
            try:
                run([
                    "shell", "am", "instrument", "-w", "-r", "-e", "class", ",".join(classes),
                    "-e", "notClass", excluded, package + ".test/androidx.test.runner.AndroidJUnitRunner",
                ], stdout=output, stderr=subprocess.STDOUT, timeout=300)
            except subprocess.TimeoutExpired:
                run(["shell", "am", "force-stop", package])
                raise
        result = logfile.read_text()
        print("\n".join(result.splitlines()[-10:]), flush=True)
        if "OK (" not in result or "FAILURES!!!" in result or "INSTRUMENTATION_FAILED" in result:
            raise RuntimeError(f"Instrumented tests failed: {logfile}")
finally:
    for component in added_imes:
        run(["shell", "ime", "disable", component])
    assert read(["shell", "settings", "get", "secure", "default_input_method"]) == original_default
    assert read(["shell", "settings", "get", "secure", "enabled_input_methods"]) == original_enabled
