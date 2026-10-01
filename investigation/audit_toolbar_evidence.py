#!/usr/bin/env python3
"""Audit all required fixtures; missing or coarse evidence never counts as a pass."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / 'build/reports/toolbar-flicker'
FIXTURES = runpy.run_path(str(ROOT / 'investigation/run_toolbar_matrix.py'))['fixtures']

def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--pixel-serial', required=True)
    options = parser.parse_args()
    source = options.source_commit
    devices = [(options.pixel_serial,37,True),('emulator-5580',30,True),
               ('emulator-5582',24,False),('emulator-5584',29,False),
               ('emulator-5586',33,False),('emulator-5588',36,False)]
    cases = []
    manifest = {}
    apk_hashes = {name:sha(EVIDENCE/'apks'/name) for name in ('final-app.apk','final-test.apk')}
    for serial,api,extended in devices:
        for label,_ in FIXTURES(extended):
            directory = EVIDENCE / f'{serial}-api{api}' / label
            case = {'device':serial,'api':api,'label':label,'evidence':str(directory.relative_to(EVIDENCE))}
            errors = []
            try:
                metadata = json.loads((directory/'metadata.json').read_text())
                output = directory/'toolbar-regression'/label
                result = json.loads((output/'result.json').read_text())
                video = json.loads((directory/'video-analysis.json').read_text())
                assert metadata['passed'] and result['passed'], 'Instrumentation failed'
                assert metadata['original'] == metadata['restored'], 'Device settings were not restored'
                assert metadata['gitHead'].startswith(source) and not metadata['gitDiff'], 'Source revision mismatch'
                assert set(metadata['apkSha256'].values()) == set(apk_hashes.values()), 'APK hash mismatch'
                assert result['cycles'] == 20 and len(result['phases']) == 60, 'Incomplete input phases'
                assert result['finalText'] == 'か' * 20, 'Missing committed input'
                assert result['events'][0]['phase'] == 'ready' and result['events'][-1]['phase'] == 'finished', 'Incomplete event timeline'
                assert video['analysisVersion'] == 4 and video['detectorControlsPassed'], 'Unvalidated video detector'
                assert video['missingKeyboardFrames'] == result['missingKeyboardSamples'] == 0, 'Keyboard disappeared'
                assert video['maximumFrameGapMs'] <= 100, 'Video sampling gap exceeds 100ms'
                frames = result['frames']
                geometry_gap = max(b['uptimeMs'] - a['uptimeMs'] for a,b in zip(frames,frames[1:]))
                assert geometry_gap <= 100, 'Geometry sampling gap exceeds 100ms'
                with (directory/'video-frames.csv').open() as stream:
                    rows = list(csv.DictReader(stream))
                assert float(rows[0]['timeSeconds']) <= video['startSeconds'] + .1, 'Missing recording start'
                assert float(rows[-1]['timeSeconds']) >= video['endSeconds'] - .1, 'Missing recording end'
                for name in ('initial.png','final.png','initial-window.txt','final-window.txt','initial-input-method.txt','final-input-method.txt'):
                    assert (output/name).stat().st_size > 0, f'Missing evidence: {name}'
                assert (directory/'logcat.txt').stat().st_size > 0, 'Missing logcat'
                case.update(cycles=20,mainHeights=result['observedMainHeights'],surfaceHeights=result['observedSurfaceHeights'],
                            geometrySamples=len(frames),maximumGeometryGapMs=geometry_gap,
                            videoFrames=video['frames'],maximumVideoGapMs=video['maximumFrameGapMs'],missingFrames=0)
            except (AssertionError,FileNotFoundError,KeyError,ValueError) as error:
                errors.append(str(error))
            case.update(status='failed' if errors else 'passed', errors=errors)
            cases.append(case)
            if directory.exists():
                for path in directory.rglob('*'):
                    if path.is_file():
                        manifest[str(path.relative_to(EVIDENCE))] = sha(path)
    summary = dict(sourceCommit=source,apkSha256=apk_hashes,cases=cases,
                   passed=sum(c['status']=='passed' for c in cases),required=len(cases),
                   cycles=sum(c.get('cycles',0) for c in cases if c['status']=='passed'),
                   limitation='Sampling cannot exclude disappearances between recorded frames. UMIDIGI power5 is untested.')
    (EVIDENCE/'final-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
    (EVIDENCE/'evidence-sha256.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(f"Evidence audit: {summary['passed']}/{summary['required']}, {summary['cycles']} cycles")
    for case in cases:
        if case['errors']:
            print(case['device'],case['label'],case['errors'])
    if summary['passed'] != summary['required']:
        raise SystemExit(1)

if __name__ == '__main__':
    main()
