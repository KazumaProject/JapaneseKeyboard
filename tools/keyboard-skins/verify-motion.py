#!/usr/bin/env python3
"""Measure popup transitions against input events using PTS and a visible frame clock.

Requires PyAV and numpy. Clock residuals and transition brackets are retained; a
sample gap is never silently counted as a matching frame. No video colors calibrate skins.
"""
import argparse,json
from pathlib import Path
import av,numpy as np
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--platform',choices=['android','ios'],required=True)
p.add_argument('--video',type=Path,required=True)
p.add_argument('--gestures',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args();raw=json.loads(a.gestures.read_text());gestures=[]
if a.platform=='android':
 queues={}
 for g in raw:
  name=g['name']
  if name not in queues:
   events=json.loads((a.gestures.parent.parent/f'{name}-events.json').read_text());pairs=[];active=None
   for e in events:
    if e['phase']==0:active=[e]
    elif active:
     active.append(e)
     if e['phase']==1:pairs.append(active);active=None
   queues[name]=iter(pairs)
  events=next(queues[name]);g={**g,'down':events[0]['time']/1000,'up':events[-1]['time']/1000,'events':events,'down_serial':events[0]['serial'],'up_serial':events[-1]['serial'],'frames':[]}
  x,y,w,h=[g[k]for k in ['x','y','width','height']];left=x-w/2;top=y-h/2
  if g['label']=='な':box=(x-w*.4,y-h*.4,x+w*.4,y+h*.4)
  elif ':'in g['label']:
   direction=g['label'].split(':')[1];shift=h*10/56
   box={'left':(left-w,top,left,top+h),'right':(left+w,top,left+2*w,top+h),
    'top':(left,top-h-shift,left+w,top-shift),'bottom':(left,top+h+shift,left+w,top+2*h+shift)}[direction]
  elif g['label']=='e':box=(x-200,top-600,x+200,top-4)
  else:box=(x-w,top-h*1.56,x+w,top-4)
  g['box']=box;gestures.append(g)
else:
 for old in raw:
  label=old['label'];box=old['box']
  if old.get('contrast'):
   pass # Explicit cap-only ROI above the keyboard; excludes changing candidate text.
  elif label=='hold':box=(531,2154,789,2322)
  elif label in ['left','right','up','down']:
   box={'left':(272,2154,530,2322),'right':(789,2154,1048,2322),
    'up':(531,1956,789,2124),'down':(531,2353,789,2521)}[label]
  elif label=='q':box=(15,1780,205,1985)
  elif label=='p':box=(1115,1780,1305,1985)
  gestures.append(dict(name=f'ios-{old["index"]}',label=label,down=old['down']['time'],up=old['up']['time'],down_serial=old['down']['serial'],up_serial=old['up']['serial'],box=box,frames=[]))
low=min(g['down']for g in gestures)-1;high=max(g['up']for g in gestures)+1;clock=[]
for f in av.open(str(a.video)).decode(video=0):
 im=f.to_ndarray(format='rgb24');height,width=im.shape[:2]
 def bits(row):
  y,x,step=(975+30*row,75,30)if a.platform=='ios'else(410+20*row,30,20)
  return sum(int(v)<<i for i,v in enumerate(im[y,x+np.arange(32)*step].mean(1)>128))
 tick=bits(1)/1000;serial=bits(0);event_serial=bits(2)
 if not low<=tick<=high:continue
 pts=float(f.pts*f.time_base);clock.append((pts,serial,tick))
 for g in gestures:
  if g['down']-.25<=tick<=g['up']+.4:
   x0,y0,x1,y1=g['box'];x0=max(0,int(x0));x1=min(width,int(x1));y0=max(0,int(y0));y1=min(height,int(y1))
   if x1<=x0 or y1<=y0:raise ValueError('Empty popup ROI')
   g['frames'].append((pts,im[y0:y1:3,x0:x1:3].astype(np.float32),event_serial))
clock=np.array(clock)
if len(clock)<10:raise ValueError('No valid visible clock; capture is invalid')
advance=np.r_[True,np.diff(clock[:,1])>0];offsets=clock[:,2]-clock[:,0];offset=float(np.median(offsets[advance]))
residual=(offsets[advance]-offset)*1000;calibration=dict(offset_seconds=offset,
 residual_percentiles_ms=dict(zip(['min','p1','p5','median','p95','p99','max'],[float(x)for x in np.percentile(residual,[0,1,5,50,95,99,100])])),
 uncertainty_ms=float(max(abs(np.percentile(residual,1)),abs(np.percentile(residual,99)))))
report=[]
for g in gestures:
 frames=g.pop('frames');t=np.array([f[0]+offset for f in frames]);ims=np.stack([f[1]for f in frames]);down=g['down'];up=g['up']
 # Stay inside the 350 ms inter-gesture pause: later frames can belong to the next
 # keyboard/activity and are not a valid dismissed-window baseline.
 pre=(t<down);post=(t>up+.15)&(t<up+.28);held=(t>max(down+.035,up-.25))&(t<up-.03)
 if not pre.any()or not post.any()or not held.any():raise ValueError(f'Missing baseline: {g["name"]}/{g["label"]}')
 before=ims[pre][-1];after=ims[post].mean(0);plateau=ims[held].mean(0)
 change=abs(ims-before).mean((1,2,3));off=abs(ims-after).mean((1,2,3));stable=abs(ims-plateau).mean((1,2,3))
 amplitude=max(1,float(abs(plateau-before).mean()));off_amp=max(1,float(abs(plateau-after).mean()))
 if g['label'] in ['な','hold']:
  # The pressed key appears before the long-press guide. Detect its selected blue
  # cell explicitly, excluding the independent surrounding-label fade.
  blue=((ims[:,:,:,2]-ims[:,:,:,0]>100)&(ims[:,:,:,1]>70)).mean((1,2))*100
  change=blue;off=blue;stable=abs(blue-blue[held].mean())
  amplitude=off_amp=max(1,float(blue[held].mean()))
  g['metric']='selected guide blue area; excludes pressed origin and label fade'
 def transition(metric,predicate,origin):
  indices=np.flatnonzero(predicate)
  if not len(indices):return None
  i=indices[0];interval=[float((t[max(0,i-1)]-origin)*1000),float((t[i]-origin)*1000)]
  return dict(bracket_ms=interval,capture_gap_ms=interval[1]-interval[0],
   uncertainty_expanded_ms=[interval[0]-calibration['uncertainty_ms'],interval[1]+calibration['uncertainty_ms']])
 g.update(appearance=transition(change,(t>=down)&(change>max(1,.05*amplitude)),down),
  settled=transition(stable,(t>=down)&(t<up)&(stable<max(1,.02*amplitude)),down),
  dismissal=transition(off,(t>=up)&(off<max(1,.05*off_amp)),up),
  max_sample_gap_ms=float(np.diff(t).max()*1000),
  timeline=[dict(since_down_ms=float((tt-down)*1000),since_up_ms=float((tt-up)*1000),
   appearance_fraction=float(c/amplitude),dismissal_fraction=float(o/off_amp),held_difference=float(s))for tt,c,o,s in zip(t,change,off,stable)])
 marker=np.flatnonzero(np.array([f[2]for f in frames])>=g['up_serial'])
 if len(marker) and g['dismissal'] is not None:
  marker_up=t[marker[0]]
  g['marker_relative_dismissal_ms']=[v+(up-marker_up)*1000 for v in g['dismissal']['bracket_ms']]
  g['up_marker_after_event_ms']=float((marker_up-up)*1000)
 down_marker=np.flatnonzero(np.array([f[2]for f in frames])>=g['down_serial'])
 if len(down_marker) and g['appearance'] is not None:
  marker_down=t[down_marker[0]]
  g['marker_relative_appearance_ms']=[v+(down-marker_down)*1000 for v in g['appearance']['bracket_ms']]
  g['down_marker_after_event_ms']=float((marker_down-down)*1000)
 if a.platform=='android'and g['label']in ['q','p']and(up-down)>.3:g['invalid_preview_trial']='hold crossed the existing variation threshold'
 report.append(g)
 print(g['name'],g['label'],g.get('trial'),g['appearance'],g['dismissal'],flush=True)
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps(dict(platform=a.platform,video=str(a.video),clock=calibration,gestures=report),ensure_ascii=False,indent=2))
