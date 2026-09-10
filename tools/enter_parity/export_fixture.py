#!/usr/bin/env python3
"""Export complete recorded Gboard decisions, never inferred from the resolver."""
import sys
from pathlib import Path
from compare import read, transport, editor_info
from generate_cases import CASES


def observed_action(row):
    events = transport(row)
    if len(events) == 1 and events[0]['method'] == 'performEditorAction':
        return str(events[0]['value'])
    if events == [dict(method='sendKeyEvent', value='0:66'), dict(method='sendKeyEvent', value='1:66')]:
        return 'enter'
    if events == [dict(method='commitText', value='\n')]:
        return 'newline'
    raise ValueError(f'Unclassified observation: {row["id"]}: {events}')


def export(rows):
    def nullable(value):
        return r"\N" if value is None else value
    lines = ['id\tinputType\timeOptions\tactionLabel\tactionId\texpectedAction\thintText\tfieldName\tprivateImeOptions']
    for case in CASES:
        if case['host'] != 'raw':
            continue
        row = rows.get(case['id'])
        if not row or row['status'] != 'observed':
            raise ValueError(f'Missing successful Gboard observation: {case["id"]}')
        if not row.get('ime', '').startswith('com.google.android.inputmethod.latin/'):
            raise ValueError('Expected a Gboard capture, not the implementation under test')
        info = editor_info(row)
        if info is None or any(info[key] != case.get(key) for key in info):
            raise ValueError(f'Actual EditorInfo differs from declared case: {case["id"]}')
        lines.append('\t'.join(map(str, [row['id'], info['inputType'], info['imeOptions'],
            nullable(info.get('actionLabel')), info['actionId'], observed_action(row), nullable(info.get('hintText')),
            nullable(info.get('fieldName')), nullable(info.get('privateImeOptions'))])))
    return '\n'.join(lines) + '\n'


if __name__ == '__main__':
    result = export(read(sys.argv[1]))
    Path(sys.argv[2]).write_text(result)
    print(f'{len(result.splitlines()) - 1} observed raw cases exported')
