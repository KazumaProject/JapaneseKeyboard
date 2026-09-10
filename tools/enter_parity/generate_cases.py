#!/usr/bin/env python3
"""Generate the entire declared matrix; no production policy or expected outcomes."""
import itertools
import json
from pathlib import Path

TEXT_VARIATIONS = list(range(0x00, 0xF0, 0x10))
TYPES = [(f'text-{v:x}', 1 | v) for v in TEXT_VARIATIONS] + [
    ('number', 2), ('number-password', 0x12), ('phone', 3),
    ('datetime', 4), ('date', 0x14), ('time', 0x24), ('null', 0)]
CASES = []

def add(name, input_type, options, label=None, action_id=0, **extra):
    CASES.append(dict(id=name, inputType=input_type, imeOptions=options,
                      actionLabel=label, actionId=action_id, host='raw', **extra))

for (name, typ), action, multiline, no_enter, custom in itertools.product(
        TYPES, range(8), range(4), range(2), range(4)):
    flags = (0x20000 if multiline & 1 else 0) | (0x40000 if multiline & 2 else 0)
    add(f'{name}/a{action}/m{multiline}/n{no_enter}/c{custom}', typ | flags,
        action | (0x40000000 if no_enter else 0),
        'Custom' if custom & 1 else None, 123 if custom & 2 else 0,
        validity='normal' if typ & 15 == 1 or not flags else 'non-text-flags')

# One-factor probes against every standard action and both NO_ENTER_ACTION states.
input_flags = [0x1000, 0x2000, 0x4000, 0x8000, 0x10000, 0x80000, 0x100000]
ime_flags = [0x80000000, 0x20000000, 0x10000000, 0x8000000, 0x4000000,
             0x2000000, 0x1000000, 0x800000, 0x400000, 0x200000, 0x100000]
for action, no_enter in itertools.product(range(8), range(2)):
    options = action | (0x40000000 if no_enter else 0)
    for flag in input_flags:
        add(f'probe/input-{flag:x}/a{action}/n{no_enter}', 1 | flag, options)
    for flag in ime_flags:
        add(f'probe/ime-{flag:x}/a{action}/n{no_enter}', 1, options | flag)
    for typ in [0x1002, 0x2002, 0x3002]:
        add(f'probe/number-{typ:x}/a{action}/n{no_enter}', typ, options)
    for field in ['hintText', 'fieldName', 'privateImeOptions']:
        for value in ['Search', '検索', 'Password', 'ordinary']:
            add(f'probe/{field}-{value}/a{action}/n{no_enter}', 1, options, **{field: value})
add('regression/maps-photo-comment', 0x264001, 0x40000006)
# Real widgets are deliberately not allowed to override the framework EditorInfo.
for host in ['edittext', 'compose', 'webview']:
    for action, multi in itertools.product(range(8), range(2)):
        CASES.append(dict(id=f'{host}/a{action}/m{multi}', host=host, inputType=1 | (0x20000 if multi else 0),
                          imeOptions=action, actionLabel=None, actionId=0))

if __name__ == '__main__':
    assert len({c['id'] for c in CASES}) == len(CASES)
    root = Path(__file__).resolve().parents[2]
    path = root / 'enter-parity-lab/src/main/assets/cases.json'
    path.write_text(json.dumps(CASES, ensure_ascii=False, separators=(',', ':')) + '\n')
    print(f'{len(CASES)} cases -> {path}')
