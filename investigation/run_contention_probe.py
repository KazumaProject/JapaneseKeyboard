#!/usr/bin/env python3
"""Run only isolated emulator probe APKs; collect evidence and restore device settings."""
import argparse
import json
import hashlib
import pathlib
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--edition', choices=['lite', 'full'], default='lite')
parser.add_argument('--test', default='com.kazumaproject.markdownhelperkeyboard.diagnostics.SettingsContentionProbeTest')
parser.add_argument('--home', choices=['legacy', 'new'], default='legacy')
parser.add_argument('--cold-mode', choices=['held', 'drained'], default='held')
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
out = root / 'investigation' / 'results' / f'{args.edition}-{time.strftime("%Y%m%d-%H%M%S")}'
out.mkdir(parents=True)
package = 'com.kazumaproject.markdownhelperkeyboard' + ('.lite' if args.edition == 'lite' else '') + '.freezeprobe'
ime = package + '/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService'

def adb(*command, timeout=30, check=True):
    return subprocess.run(['adb', '-s', args.serial, *command], capture_output=True, timeout=timeout, check=check)

def shell(*command, **kwargs):
    return adb('shell', *command, **kwargs).stdout.decode().strip()

assert shell('getprop', 'ro.kernel.qemu') == '1', 'Emulator only'
old_policy = shell('settings', 'get', 'global', 'hidden_api_policy')
enabled = shell('settings', 'get', 'secure', 'enabled_input_methods')
was_enabled = any(entry.split(';')[0] == ime for entry in enabled.split(':'))
trace_pid = None
trace_path = '/data/misc/perfetto-traces/contention-' + str(time.time_ns()) + '.pftrace'
try:
    installed = []
    for test_apk in [False, True]:
        expected = package + ('.test' if test_apk else '')
        candidates = []
        for base in ['outputs/apk', 'intermediates/apk']:
            for metadata in (root / 'app/build' / base).rglob('output-metadata.json'):
                data = json.loads(metadata.read_text())
                if data.get('applicationId') == expected:
                    for element in data['elements']:
                        apk = metadata.parent / element['outputFile']
                        if apk.exists():
                            candidates.append(apk)
        if not candidates:
            raise RuntimeError(f'Build isolated APK first: {expected}')
        apk = max(candidates, key=lambda path: path.stat().st_mtime_ns)
        adb('install', '-r', '-t', str(apk), timeout=120)
        installed.append(str(apk) + ' sha256=' + hashlib.sha256(apk.read_bytes()).hexdigest())
    (out / 'apks.txt').write_text('\n'.join(installed) + '\n')
    shell('run-as', package, 'rm', '-rf', 'files/contention-probe', check=False)
    # Android internal disk lock access is strictly confined to the emulator test.
    shell('settings', 'put', 'global', 'hidden_api_policy', '1')
    shell('ime', 'enable', ime)
    (out / 'revision.txt').write_bytes(subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root))
    (out / 'source.patch').write_bytes(subprocess.check_output(['git', 'diff', 'HEAD', '--', 'app/src/main', 'app/src/androidTest', 'investigation'], cwd=root))
    (out / 'device.txt').write_text(shell('getprop', 'ro.build.fingerprint') + '\n' + shell('dumpsys', 'package', package))
    trace_start = shell('perfetto', '--background-wait', '-o', trace_path, '-t', '180s', '-b', '8mb',
                        '-a', package, 'sched', 'am', 'wm', 'view', 'gfx', 'binder_driver')
    trace_pid = next((line.strip() for line in trace_start.splitlines() if line.strip().isdigit()), None)
    if not trace_pid:
        raise RuntimeError('No Perfetto PID: ' + trace_start)
    with (out / 'instrumentation.txt').open('wb') as log:
        try:
            subprocess.run(['adb', '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', args.test, '-e', 'contentionProbe', 'true', '-e', 'homeMode', args.home, '-e', 'coldMode', args.cold_mode,
                            package + '.test/androidx.test.runner.AndroidJUnitRunner'], stdout=log, stderr=subprocess.STDOUT,
                           timeout=180, check=True)
        except subprocess.TimeoutExpired:
            log.write(b'\nHOST_TIMEOUT_180_SECONDS\n')
            pid = shell('pidof', package, check=False)
            if pid.isdigit():
                shell('run-as', package, 'kill', '-3', pid, check=False)
                time.sleep(1)
    (out / 'reports.tar').write_bytes(adb('exec-out', 'run-as', package, 'tar', '-cf', '-', 'files/contention-probe', check=False).stdout)
    (out / 'lastanr.txt').write_text(shell('dumpsys', 'activity', 'lastanr'))
    pid = shell('pidof', package, check=False)
    if pid.isdigit():
        (out / 'logcat.txt').write_bytes(adb('logcat', '-d', '--pid', pid).stdout)
    shell('kill', '-TERM', trace_pid, check=False)
    trace_pid = None
    time.sleep(1)
    adb('pull', trace_path, str(out / 'system.pftrace'))
    shell('rm', trace_path, check=False)
    print(out)
    result = (out / 'instrumentation.txt').read_text()
    print(result[-2500:])
    if 'OK (' not in result or 'FAILURES!!!' in result:
        raise SystemExit(1)
finally:
    if trace_pid:
        shell('kill', '-TERM', trace_pid, check=False)
    shell('am', 'force-stop', package, check=False)
    if not was_enabled:
        shell('ime', 'disable', ime, check=False)
    if old_policy == 'null':
        shell('settings', 'delete', 'global', 'hidden_api_policy', check=False)
    else:
        shell('settings', 'put', 'global', 'hidden_api_policy', old_policy, check=False)
