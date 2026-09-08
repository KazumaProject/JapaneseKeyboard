#!/usr/bin/env python3
"""Fit a smooth degree-six illumination field to opaque, glyph-free reference pixels.

Input images must be lossless and already cropped/aligned. The alpha mask is the
surface silhouette; pixels within three pixels of glyphs or boundaries are excluded.
Every fifth spatial sample is held out. Outputs coefficients and measured residuals,
not image assets. Requires Pillow, numpy and scipy.
"""
import argparse,json
from pathlib import Path
import numpy as np
from PIL import Image
from scipy import ndimage

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--reference',type=Path,required=True)
p.add_argument('--mask',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
p.add_argument('--rgb',action='store_true')
a=p.parse_args()
im=np.array(Image.open(a.reference).convert('RGB'))
mask=np.array(Image.open(a.mask).convert('RGBA'))[:,:,3]>=253
if mask.shape!=im.shape[:2]:raise ValueError('Reference and mask sizes differ')
mask=ndimage.binary_erosion(mask,iterations=3)&~ndimage.binary_dilation(im.min(2)>180,iterations=3)
h,w=mask.shape;yy,xx=np.indices((h,w));x=(xx+.5)/w*2-1;y=(yy+.5)/h*2-1
terms=[(i,j)for i in range(7)for j in range(7-i)]
design=np.stack([x**i*y**j for i,j in terms],-1)
train=mask&((xx+yy)%5!=0);held=mask&~train
target=im if a.rgb else im[:,:,:1]
coefficients=np.linalg.lstsq(design[train],target[train],rcond=None)[0]
error=abs((design@coefficients)[held]-target[held])
result=dict(reference=str(a.reference),mask=str(a.mask),degree=6,terms=terms,
 channels=3 if a.rgb else 1,coefficients=coefficients.flatten().tolist(),
 held_out_samples=int(held.sum()),max_rgb_error=float(error.max()),p99_rgb_error=float(np.percentile(error,99)))
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items()if k not in ['terms','coefficients']},indent=2))
