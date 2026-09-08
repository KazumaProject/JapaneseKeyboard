#!/usr/bin/env python3
"""Compare externally observed outcomes. Missing/blocked cases never pass."""
import argparse
import gzip
import collections
import json
from pathlib import Path
from generate_cases import CASES

def read(path):
    text = gzip.open(path, 'rt').read() if str(path).endswith('.gz') else Path(path).read_text()
    rows = [json.loads(line) for line in text.splitlines() if line.strip()]
    assert len({r['id'] for r in rows}) == len(rows), 'duplicate observations'
    return {row['id']: row for row in rows}

def editor_info(row):
    info = row.get('actual')
    if info is None:
        return None
    return {key: info.get(key) for key in ('inputType', 'imeOptions', 'actionLabel', 'actionId',
                                          'hintText', 'fieldName', 'privateImeOptions')}

def effects(row):
    # setComposingText/finishComposingText are recorded for diagnosis, not made
    # part of the editor's Enter policy. Conversion is explicitly out of scope.
    actions = [e['value'] for e in row.get('events', [])
               if e['method'] in ('performEditorAction', 'keyboardAction')]
    return dict(text=row.get('text'), actions=actions, focus=row.get('focus'))

def transport(row):
    return [e for e in row.get('events', []) if e['method'] in
            ('sendKeyEvent', 'commitText', 'performEditorAction', 'keyboardAction')]

def compare(reference, target):
    results = []
    reference_imes = {r.get('ime') for r in reference.values()}
    target_imes = {r.get('ime') for r in target.values()}
    valid_imes = (len(reference_imes) == len(target_imes) == 1 and
                  None not in reference_imes | target_imes and reference_imes != target_imes)
    for case in CASES:
        name = case['id']
        a, b = reference.get(name), target.get(name)
        if a is None or b is None:
            status = 'not-run'
        elif a['status'] != 'observed' or b['status'] != 'observed':
            status = 'blocked'
        elif not valid_imes:
            status = 'ime-mismatch'
        elif editor_info(a) is None or editor_info(a) != editor_info(b):
            status = 'editor-info-mismatch'
        elif case['host'] == 'raw' and any(editor_info(a).get(key) != case.get(key) for key in editor_info(a)):
            status = 'editor-info-mismatch'
        else:
            status = 'match' if effects(a) == effects(b) and transport(a) == transport(b) else 'mismatch'
        results.append(dict(id=name, status=status,
                            reference=effects(a) if a else None,
                            target=effects(b) if b else None,
                            transportEqual=transport(a) == transport(b) if a and b else None))
    return dict(counts=dict(collections.Counter(r['status'] for r in results)), cases=results)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('reference'); parser.add_argument('target'); parser.add_argument('output')
    args = parser.parse_args()
    result = compare(read(args.reference), read(args.target))
    Path(args.output).write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(result['counts']))
    raise SystemExit(0 if result['counts'].get('match') == len(CASES) else 1)
