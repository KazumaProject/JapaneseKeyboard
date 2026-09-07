#!/usr/bin/env python3
"""Compare composition traces against dev, never against Gboard."""
import json
import sys
from pathlib import Path

def load(path, expected_package):
    rows = [json.loads(line) for line in Path(path).read_text().splitlines()]
    assert rows, 'Missing composition trace'
    assert {r.get('ime', '').split('/')[0] for r in rows} == {expected_package}, 'Wrong or mixed IME in composition trace'
    for scenario in ['composition', 'conversion']:
        stages = [r['stage'] for r in rows if r['id'] == scenario]
        assert stages[0] == 'typed' and stages[-1] == 'editor-enter', f'Incomplete trace: {scenario}'
        assert any(stage.startswith('confirm-') for stage in stages), 'Missing confirmation'
    return [dict(id=r['id'], stage=r['stage'], text=r['text'], composingStart=r['composingStart'],
                 composingEnd=r['composingEnd'], actions=[e['value'] for e in r['events'] if e['method']=='performEditorAction'])
            for r in rows]

if __name__ == '__main__':
    baseline = load(sys.argv[1], 'com.kazumaproject.markdownhelperkeyboard.lite.enterbaseline')
    target = load(sys.argv[2], 'com.kazumaproject.markdownhelperkeyboard.lite.enterparity')
    assert baseline == target, json.dumps(dict(baseline=baseline,target=target), ensure_ascii=False, indent=2)
    print(f'{len(target)} composition/conversion stages match dev')
