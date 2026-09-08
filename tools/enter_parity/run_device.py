#!/usr/bin/env python3
"""Capture a real IME, restoring the selected IME and animation settings in finally."""
import argparse
import json
import subprocess
import shlex
import uuid
from pathlib import Path
from datetime import datetime, timezone

parser = argparse.ArgumentParser()
parser.add_argument('--ime', required=True)
parser.add_argument('--runner', choices=['MatrixRunner', 'CompositionRegressionRunner'], default='MatrixRunner')
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--serial', required=True)
parser.add_argument('--start', type=int, default=0)
parser.add_argument('--end', type=int, default=999999)
parser.add_argument('--settle', type=int, default=120)
parser.add_argument('--filter', default='')
parser.add_argument('--layout-note', default='not recorded')
parser.add_argument('--keep-animations', action='store_true')
parser.add_argument('--screenshots', action='store_true')
parser.add_argument('--verified-enter', nargs=2, type=int, metavar=('X', 'Y'), help='Single-case only: coordinates verified by the operator from the current IME screenshot')
args = parser.parse_args()
adb = ['adb', '-s', args.serial]
def call(*command):
    command = list(command)
    if command[0] == 'shell':
        command = ['shell', shlex.join(command[1:])]
    return subprocess.check_output(adb + command, text=True)
old_ime = call('shell', 'settings', 'get', 'secure', 'default_input_method').strip()
scales = {key: call('shell', 'settings', 'get', 'global', key).strip() for key in
          ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']}
filename = f'capture-{uuid.uuid4().hex}.jsonl'
args.output.parent.mkdir(parents=True, exist_ok=True)
metadata = dict(utc=datetime.now(timezone.utc).isoformat(),
                model=call('shell', 'getprop', 'ro.product.model').strip(),
                android=call('shell', 'getprop', 'ro.build.version.release').strip(),
                fingerprint=call('shell', 'getprop', 'ro.build.fingerprint').strip(),
                locale=call('shell', 'getprop', 'persist.sys.locale').strip(), ime=args.ime,
                layoutNote=args.layout_note, start=args.start, end=args.end, settle=args.settle,
                filter=args.filter, runner=args.runner, originalAnimationScales=scales, animationsDisabled=not args.keep_animations,
                version=[s.strip() for s in call('shell', 'dumpsys', 'package', args.ime.split('/')[0]).splitlines()
                         if 'versionName=' in s or 'versionCode=' in s])
process = None
try:
    if not args.keep_animations:
        for key in scales:
            call('shell', 'settings', 'put', 'global', key, '0')
    print(call('shell', 'ime', 'set', args.ime), end='')
    assert call('shell', 'settings', 'get', 'secure', 'default_input_method').strip() == args.ime
    # The shell's am instrument can exit 0 even when instrumentation failed.
    remote = ['am', 'instrument', '-w', '-e', 'start', str(args.start),
        '-e', 'end', str(args.end), '-e', 'settle', str(args.settle), '-e', 'filter', args.filter,
        '-e', 'screenshots', str(args.screenshots).lower(), '-e', 'output', filename, 'com.kazumaproject.enterparity/.' + args.runner]
    if args.verified_enter:
        if args.end - args.start != 1 or args.filter or not args.screenshots:
            raise ValueError('Verified coordinates require one case and --screenshots')
        remote[-1:-1] = ['-e', 'verifiedEnterX', str(args.verified_enter[0]), '-e', 'verifiedEnterY', str(args.verified_enter[1])]
    process = subprocess.Popen(adb + ['shell', shlex.join(remote)],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    completed = False
    for line in process.stdout:
        print(line, end='', flush=True)
        completed |= line.startswith('Results: ')
    if process.wait() or not completed:
        raise RuntimeError('Instrumentation did not complete; any partial capture is not a full run')
    metadata['completed'] = True
finally:
    if process is not None and process.poll() is None:
        call('shell', 'am', 'force-stop', 'com.kazumaproject.enterparity')
        process.wait(timeout=20)
    try:
        capture = call('shell', 'run-as', 'com.kazumaproject.enterparity', 'cat', 'files/' + filename)
        args.output.write_text(capture)
        metadata['capturedRows'] = len(capture.splitlines())
        if args.screenshots:
            folder = args.output.with_suffix('.screenshots')
            folder.mkdir(exist_ok=True)
            for line in capture.splitlines():
                row = json.loads(line)
                if 'screenshot' in row:
                    png = subprocess.check_output(adb + ['exec-out', 'run-as', 'com.kazumaproject.enterparity',
                                                        'cat', 'files/' + row['screenshot']])
                    (folder / f"{row['index']}.png").write_bytes(png)
    finally:
        args.output.with_suffix('.metadata.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
        try:
            if call('shell', 'settings', 'get', 'secure', 'default_input_method').strip() == args.ime:
                print(call('shell', 'ime', 'set', old_ime), end='')
            else:
                print('Selected IME changed externally; leaving that selection in place.')
        finally:
            for key, value in scales.items():
                if args.keep_animations or call('shell', 'settings', 'get', 'global', key).strip() != '0':
                    continue
                if value == 'null':
                    call('shell', 'settings', 'delete', 'global', key)
                else:
                    call('shell', 'settings', 'put', 'global', key, value)
