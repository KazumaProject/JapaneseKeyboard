#!/usr/bin/env python3
"""Sequential controlled conditions, not probabilistic repetition."""
import pathlib
import subprocess
import sys
root = pathlib.Path(__file__).resolve().parent
cases = []
for edition in ['lite', 'full']:
    cases.append(['--edition', edition])
    for home in ['legacy', 'new']:
        for mode in ['held', 'drained']:
            cases.append(['--edition', edition, '--test',
                          'com.kazumaproject.markdownhelperkeyboard.diagnostics.ColdSettingsReadProbeTest',
                          '--home', home, '--cold-mode', mode])
failed = []
for args in cases:
    print('CASE', ' '.join(args), flush=True)
    result = subprocess.run([sys.executable, str(root / 'run_contention_probe.py'), *args])
    if result.returncode:
        failed.append(args)
print(f'MATRIX completed={len(cases)} failed={len(failed)}', flush=True)
raise SystemExit(bool(failed))
