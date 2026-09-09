from pathlib import Path
import json,argparse
parser=argparse.ArgumentParser(description="Verify independent empty/composing candidate height settings on the physical IME matrix.")
parser.add_argument("--baseline",type=Path,required=True)
parser.add_argument("--changed",type=Path,required=True)
parser.add_argument("--density",type=float,required=True)
args=parser.parse_args()
root=args.changed;rows=[]
runs=json.loads((root/'results.json').read_text())
assert len(runs)==24 and all(r['passed'] for r in runs), 'Incomplete or failed height matrix'
for rotation in ['portrait','landscape']:
 for columns in range(1,4):
  for tabs in ['false','true']:
   stem=f'{rotation}-{columns}-{tabs}'
   baseline={m['name']:m for m in json.loads((args.baseline/f'spacing-{stem}'/'measurements.json').read_text())}
   for change in ['empty','composing']:
    for m in json.loads((root/f'height-{stem}-{change}'/'measurements.json').read_text()):
     old=baseline[m['name']];phase=m['name'].split('-')[-1]
     difference=old['inputArea'][1]-m['inputArea'][1]
     baseHeight=([110,120,160] if rotation=='portrait' else [60,90,120])[columns-1]
     expected=(int(90*args.density)-int(60*args.density)) if change=='empty' else (int((baseHeight+20)*args.density)-int(baseHeight*args.density))
     affected=phase==change
     keys=['keyboard_view','suggestionView_parent','suggestion_recycler_view','candidate_tab_layout','inputArea']
     unchanged=all(m.get(k)==old.get(k) for k in keys)
     passed=(difference==expected and m['keyboard_view']==old['keyboard_view']) if affected else unchanged
     rows.append(dict(configuration=stem,changedSetting=change,phase=m['name'],imeTopChangePx=difference,expectedChangePx=expected if affected else 0,keyboardUnchanged=m['keyboard_view']==old['keyboard_view'],unaffectedGeometryUnchanged=unchanged if not affected else None,passed=passed))
(root/'height-independence.json').write_text(json.dumps(rows,indent=2))
print('Checks',len(rows),'failures',sum(not r['passed'] for r in rows),flush=True)
for r in rows:
 if not r['passed']:print(r,flush=True)

assert all(r["passed"] for r in rows), "Candidate height settings failed independence checks"
