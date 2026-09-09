#!/usr/bin/env python3
"""Diagnostic for corresponding selected-cell fill, with actual source timestamps.

Reference filenames identify the retained captures in ios-contract documentation.
Requires av and numpy. Zero partial frames is an observation at the source capture
cadence, not proof of zero physical latency or full animation acceptance.
"""
import json,gzip,struct
from pathlib import Path
import numpy as np
import av
import argparse
parser = argparse.ArgumentParser(description="Measure selected guide-cell fill transitions; not absolute input latency")
parser.add_argument("--reference-root", type=Path, required=True)
parser.add_argument("--android", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
base = args.reference_root
land = base / "continuous-landscape-timing-v1-evidence"
def pairs(events):
 out=[];active=None
 for e in events:
  if e['phase']==0:active=[e]
  elif active is not None:
   active.append(e)
   if e['phase'] in [3,4]:
    if sum(x['phase']==1 for x in active)>5:out.append(active)
    active=None
 return out

def measurements(name,frames):
 values=np.array([v[1] for v in frames]);times=np.array([v[0] for v in frames]);runs=[]
 for col in range(5):
  partial=(values[:,col]>.1)&(values[:,col]<.9);starts=np.where(partial & ~np.r_[False,partial[:-1]])[0];ends=np.where(partial & ~np.r_[partial[1:],False])[0]
  for a,b in zip(starts,ends):
   runs.append(dict(cell=col,start=float(times[a]),end=float(times[min(b+1,len(times)-1)]),durationMs=float((times[min(b+1,len(times)-1)]-times[a])*1000)))
 return dict(name=name,frames=len(frames),partialRuns=runs,maxPartialDurationMs=max([r['durationMs'] for r in runs],default=0),maxSampleIntervalMs=float(np.diff(times).max()*1000),seenCells=[bool((values[:,i]>.9).any()) for i in range(5)])
reports=[]
for orientation,folder,capture,index in [('portrait',base/'continuous-portrait-v2-evidence',base/'continuous-portrait-v2.frames.gz',9),('landscape',land,base/'continuous-landscape-timing-v1.frames.gz',10)]:
 events=pairs(json.loads((folder/'touch-events.json').read_text()));keys=5 if orientation=='portrait' else 1
 schedule=[(mode,key,trial,hold) for mode in ['Light','Dark'] for key in range(keys) for trial in [-1,0,1,2] for hold in [.06,1.2]]
 assert len(events)==len(schedule),(len(events),len(schedule))
 groups=[]
 for (mode,key,trial,hold),ev in zip(schedule,events):
  if key==0 and trial>=0 and hold>1:groups.append(dict(name=f'ios-{orientation}-{mode}-{trial}',start=ev[0]['time']+.8,end=ev[-1]['time']+.1,frames=[]))
 with gzip.open(capture,'rb') as f:
  meta=json.loads(f.read(struct.unpack('<I',f.read(4))[0]));regions=meta['regions'];starts=np.cumsum([0]+[r['width']*r['height']*3 for r in regions]);region=regions[index];left,top,_,_=region['box']
  cx,cy,w,h=(660,2238,258,168) if orientation=='portrait' else (1907,964.5,244,117)
  points=[]
  for dx,dy in [(0,0),(-1,0),(0,-1),(1,0),(0,1)]:
   x=cx+dx*w-.35*w;y=cy+dy*h
   if orientation=='landscape':x,y=1320-y,x
   points.append((round((x-left)/3),round((y-top)/3)))
  lastCounter=0
  while True:
   header=f.read(32)
   if not header:break
   t,end,*rest=struct.unpack('<ddIIII',header);data=f.read(meta['sampleBytes'])
   assert rest[0]==lastCounter+1, "Missing raw callback record"
   assert len(data)==meta['sampleBytes'], "Truncated raw record"
   lastCounter=rest[0]
   if t>max(g['end'] for g in groups):break
   targets=[g for g in groups if g['start']<=t<=g['end']]
   if not targets:continue
   roi=np.frombuffer(data,dtype=np.uint8,offset=int(starts[index]),count=int(starts[index+1]-starts[index])).reshape(region['height'],region['width'],3).astype(float)
   score=[]
   for x,y in points:
    patch=roi[y-1:y+2,x-1:x+2];score.append(float(np.maximum(0,patch[:,:,2]-patch[:,:,0]).mean()/255))
   for g in targets:g['frames'].append((t,score))
 for g in groups:reports.append(measurements(g['name'],g['frames']))
 print(orientation,[(r['name'],r['maxPartialDurationMs']) for r in reports if r['name'].startswith('ios-'+orientation)],flush=True)
for orientation in ['portrait','landscape']:
 for skin in ['cupertino_light','cupertino_dark']:
  stem=f'final-timed-{orientation}-{skin}';folder=args.android;video=folder/(stem+'.mp4');data=video.read_bytes();magic=b'#VV1NSC0PET1ME2#';s=data.index(magic)+len(magic);version,offset,count=struct.unpack_from('<IQI',data,s);times=np.frombuffer(data,dtype='<u8',offset=s+16,count=count)/1e9
  traces=json.loads(next((folder/stem).glob('*continuous-traces.json')).read_text());groups=[]
  for g in traces:
   if g['key']=='key_5' and g['trial']>=0 and g['hold']>1000:
    l,t,r,b=g['anchor'];w=r-l;h=b-t;cx=(l+r)/2;cy=(t+b)/2
    groups.append(dict(name=f'android-{orientation}-{skin}-{g["trial"]}',start=g['events'][0]['elapsedStartNanos']/1e9+.8,end=g['events'][-1]['elapsedEndNanos']/1e9+.1,points=[(round(cx+dx*w-.35*w),round(cy+dy*h)) for dx,dy in [(0,0),(-1,0),(0,-1),(1,0),(0,1)]],frames=[]))
  for i,f in enumerate(av.open(str(video)).decode(video=0)):
   t=float(times[i]);targets=[g for g in groups if g['start']<=t<=g['end']]
   if not targets:continue
   rgb=f.to_ndarray(format='rgb24').astype(float)
   for g in targets:
    score=[]
    for x,y in g['points']:
     patch=rgb[y-2:y+3,x-2:x+3];score.append(float(np.maximum(0,patch[:,:,2]-patch[:,:,0]).mean()/255))
    g['frames'].append((t,score))
  for g in groups:reports.append(measurements(g['name'],g['frames']))
  print(stem,[(r['name'],r['maxPartialDurationMs']) for r in reports if r['name'].startswith(f'android-{orientation}-{skin}')],flush=True)
args.output.write_text(json.dumps(dict(scope='Selected-cell fill only; excludes recognition latency, glyph fade and normal balloon motion. Video RGB used only as a within-recording fill signal.',results=reports),indent=2))
