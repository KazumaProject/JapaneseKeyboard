#!/usr/bin/env python3
"""Measure label-transition timing from a glyph ROI and an event-calibrated recording.

Video percentiles are normalized to their endpoints for timing only, never used as
lossless color calibration. The ROI must contain an unchanged background and the same
label throughout the interval. Use a low percentile for dark ink, high for light ink.
"""
import argparse,json
from pathlib import Path
import av,numpy as np
from scipy.optimize import least_squares
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--video',type=Path,required=True)
p.add_argument('--report',type=Path,required=True)
p.add_argument('--gesture',type=int,required=True)
p.add_argument('--phase',choices=['down','up'],required=True)
p.add_argument('--roi',type=int,nargs=4,required=True)
p.add_argument('--percentile',type=float,default=1)
p.add_argument('--start',type=float,required=True)
p.add_argument('--end',type=float,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args();report=json.loads(a.report.read_text());g=report['gestures'][a.gesture]
origin=g[a.phase];offset=report['clock']['offset_seconds'];samples=[]
x0,y0,x1,y1=a.roi
for frame in av.open(str(a.video)).decode(video=0):
 t=float(frame.pts*frame.time_base)+offset-origin
 if t>a.end:break
 if t<a.start:continue
 im=frame.to_ndarray(format='rgb24')[y0:y1,x0:x1]
 if not im.size:raise ValueError('Empty ROI')
 samples.append([t,float(np.percentile(im,a.percentile))])
v=np.array(samples)
if len(v)<10:raise ValueError('Insufficient samples')
lo=float(np.median(v[:3,1]));hi=float(np.median(v[-3:,1]))
if abs(hi-lo)<10:raise ValueError('Insufficient label contrast')
t=v[:,0];values=(v[:,1]-lo)/(hi-lo)
def response(parameters):
 elapsed=np.maximum(0,t-parameters[0]);rate=parameters[1]
 return 1-(1+rate*elapsed)*np.exp(-rate*elapsed)
fit=least_squares(lambda parameters:response(parameters)-values,[a.start,26],bounds=([a.start-.2,1],[a.end,200]))
residual=abs(response(fit.x)-values)
result=dict(video=str(a.video),gesture=a.gesture,phase=a.phase,roi=a.roi,
 note='Normalized compressed-video intensity for timing only; no absolute RGB claim',
 clock_uncertainty_ms=report['clock']['uncertainty_ms'],
 fitted_onset_ms=float(fit.x[0]*1000),fitted_rate_per_second=float(fit.x[1]),
 max_normalized_residual=float(residual.max()),mean_normalized_residual=float(residual.mean()),
 samples=[dict(since_event_ms=float(tt*1000),normalized_intensity=float(value))for tt,value in zip(t,values)])
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items()if k!='samples'},indent=2))
