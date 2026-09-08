#!/usr/bin/env python3
"""Compare lossless, density-matched production fixtures; never resize or register outputs.

Requires Pillow, numpy, scipy. Reference is capture-held.py/testControlledReference.
Geometry acceptance: symmetric full-contour distance <=1 pt (@3). Surface: max RGB <=2
on common opaque interiors, with explicit 3-pixel boundary and glyph-AA exclusions.
Typography includes placed ink bounds AND pixel-mask IoU; bounds alone are not parity.
"""
import argparse,json
from pathlib import Path
import numpy as np
from PIL import Image
from scipy import ndimage

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--reference',type=Path,required=True)
p.add_argument('--android',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True);report=[]

def rgb(path,box=None):
 im=Image.open(path).convert('RGB')
 return np.array(im.crop(box)if box else im)

def placed(path,size,offset=(0,0)):
 im=Image.new('RGBA',size);im.paste(Image.open(path).convert('RGBA'),offset);return np.array(im)

def bbox(mask):
 y,x=np.where(mask)
 return [int(x.min()),int(y.min()),int(x.max()+1),int(y.max()+1)]if len(x)else None

def evaluate(name,reference,mask,android,surface,glyph_light,regions=None):
 actual=surface[:,:,3]>=253
 edge=lambda m:m^ndimage.binary_erosion(m)
 r_edge,n_edge=edge(mask),edge(actual)
 if not r_edge.any()or not n_edge.any():raise ValueError(f'{name}: empty silhouette')
 distance=max(ndimage.distance_transform_edt(~r_edge)[n_edge].max(),ndimage.distance_transform_edt(~n_edge)[r_edge].max())/3
 opaque=ndimage.binary_erosion(mask&actual,iterations=3)
 glyph_ref=((reference.max(2)<100)if glyph_light else(reference.min(2)>180))&opaque
 selected=(surface[:,:,0]<60)&(surface[:,:,1]>100)&(surface[:,:,2]>200)
 glyph_ref |= (reference.min(2)>180)&selected&opaque
 glyph_native=(np.max(abs(android[:,:,:3].astype(int)-surface[:,:,:3].astype(int)),2)>80)&opaque
 color_area=opaque&~ndimage.binary_dilation(glyph_ref,iterations=3)
 error=np.max(abs(surface[:,:,:3].astype(int)-reference.astype(int)),2)[color_area]
 item=dict(name=name,outline_max_error_pt=float(distance),silhouette_iou=float((mask&actual).sum()/(mask|actual).sum()),
  opaque_rgb_max_error=int(error.max()),opaque_rgb_p99_error=float(np.percentile(error,99)),
  geometry_pass=bool(distance<=1),surface_pass=bool(error.max()<=2))
 def ink(r,n):
  rb,nb=bbox(r),bbox(n);union=r|n
  return dict(reference_bounds=rb,android_bounds=nb,
   bounds_max_error_pt=float(np.max(abs(np.array(rb)-nb))/3)if rb and nb else None,
   glyph_mask_exact_match=bool(np.array_equal(r,n)),
   placed_mask_iou=float((r&n).sum()/union.sum())if union.any()else None)
 item['ink']=ink(glyph_ref,glyph_native)
 if regions:
  item['cells']={label:ink(glyph_ref[box],glyph_native[box])for label,box in regions.items()}
 report.append(item)
 Image.fromarray(reference).save(a.output/f'{name}-ios.png')
 Image.fromarray(android).save(a.output/f'{name}-android.png')
 # Composite against a neutral background; differences must include missing surface pixels.
 bg=np.full_like(reference,128);r=np.where(mask[:,:,None],reference,bg)
 alpha=android[:,:,3:4]/255;n=android[:,:,:3]*alpha+bg*(1-alpha)
 difference=np.max(abs(r.astype(float)-n),2)
 heat=np.zeros_like(reference);heat[:,:,0]=np.minimum(difference*3,255)
 Image.fromarray(heat).save(a.output/f'{name}-difference.png')
 print(name,'outline',round(distance,3),'pt','RGB',int(error.max()),'ink',item['ink'])

boxes={'left':(272,2154,592,2322),'right':(729,2154,1048,2322),
 'top':(531,1956,789,2194),'bottom':(531,2283,789,2521),'center':(531,1986,789,2154)}
for v in json.loads((a.android/'layout.json').read_text()):
 name=v['name'];mode='Light'if'light'in name else'Dark';dr=name.split('-')[-1];box=boxes[dr]
 suffix='か-hold'if dr=='center'else'flick-'+{'top':'up','bottom':'down'}.get(dr,dr)
 reference=rgb(a.reference/f'{mode}-kana-{suffix}.png',box)
 light=rgb(a.reference/f'Light-kana-{suffix}.png',box)
 mask=((light[:,:,2]>240)&(light[:,:,1]>100))if dr=='center'else(light.min(2)>=253)
 if dr=='bottom':
  dark=rgb(a.reference/f'Dark-kana-{suffix}.png',box)
  before=rgb(a.reference/f'Dark-kana-{suffix}-before.png',box)
  changed=np.max(abs(dark.astype(int)-before.astype(int)),2)>2
  # The light popup overlaps white keys at its bottom corners. Use its before/held
  # dark pair there, rather than mistaking the unchanged underlying key for popup.
  mask[-30:,:30]=changed[-30:,:30];mask[-30:,-30:]=changed[-30:,-30:]
 mask=ndimage.binary_fill_holes(mask)
 offset=(531+v['offset_x']-box[0],(1986 if dr=='center'else 2154)+v['offset_y']-box[1])
 size=(box[2]-box[0],box[3]-box[1])
 evaluate(name,reference,mask,placed(a.android/f'{name}.png',size,offset),
  placed(a.android/f'{name}-surface.png',size,offset),mode=='Light'and dr!='center')

for mode in ['Light','Dark']:
 name='cupertino_'+mode.lower()+'-guide';box=(273,1818,1047,2322)
 reference=rgb(a.reference/f'{mode}-kana-か-hold.png',box);light=rgb(a.reference/'Light-kana-か-hold.png',box)
 area=np.zeros((504,774),bool);area[:,258:516]=True;area[168:336,:]=True
 dark=rgb(a.reference/'Dark-kana-か-hold.png',box)
 # Light's white app background is indistinguishable from the popup corners.
 # Use the paired dark before/held capture to isolate the cross over that background.
 before=rgb(a.reference/'Dark-kana-か-before.png',box)
 mask=ndimage.binary_fill_holes((np.max(abs(dark.astype(int)-before.astype(int)),2)>3)&area)
 # Selected center has white ink in both appearances, so report it separately above.
 evaluate(name,reference,mask,placed(a.android/f'{name}.png',(774,504)),
  placed(a.android/f'{name}-surface.png',(774,504)),mode=='Light',
  {'left':np.s_[168:336,0:258],'top':np.s_[0:168,258:516],
   'right':np.s_[168:336,516:774],'bottom':np.s_[336:504,258:516]})

for v in json.loads((a.android/'preview-layout.json').read_text()):
 name=v['name'];mode='Light'if'light'in name else'Dark';key=name[-1]
 rx,ry,rw={'q':(20,1782,178),'p':(1129,1782,172),'e':(242,1782,186),'a':(47,1950,186),'l':(1087,1950,186)}[key]
 suffix='hold' if key in ['q','p'] else 'preview'
 nx=v['key_left']+v['x_offset'];ny=v['key_top']+v['key_height']+v['y_offset']
 box=(min(rx,nx),min(ry,ny),max(rx+rw,nx+v['width']),max(ry+347,ny+v['height']))
 reference=rgb(a.reference/f'{mode}-qwerty-{key}-{suffix}.png',box)
 dark=rgb(a.reference/f'Dark-qwerty-{key}-{suffix}.png',box)
 yy,xx=np.indices(dark.shape[:2]);xx+=box[0];yy+=box[1]
 candidate=(dark.min(2)>40)&(xx>=rx)&(xx<rx+rw)&(yy>=ry)&(yy<ry+347)
 if suffix=='preview':
  before=rgb(a.reference/f'Dark-qwerty-{key}-preview-before.png',box)
  candidate &= np.max(abs(dark.astype(int)-before.astype(int)),2)>3
  candidate=ndimage.binary_closing(candidate,iterations=1)
 labels,_=ndimage.label(candidate)
 seed=(int(v['key_top']+v['key_height']/2-box[1]),int(v['key_left']+v['key_width']/2-box[0]))
 component=labels[seed]
 if component==0:raise ValueError(f'{name}: stem seed missing')
 mask=ndimage.binary_fill_holes(labels==component)
 size=(box[2]-box[0],box[3]-box[1]);offset=(nx-box[0],ny-box[1])
 evaluate(name,reference,mask,placed(a.android/f'{name}.png',size,offset),
  placed(a.android/f'{name}-surface.png',size,offset),mode=='Light')

(a.output/'measurements.json').write_text(json.dumps({'scale':3,'registration':'anchor coordinates only; no scaling',
 'acceptance':{'outline_tolerance_pt':1,'opaque_rgb_tolerance':2,'glyph_mask':'diagnostic only; glyph identity excluded by requester on 2026-09-08', 'font_policy':'retain existing platform fonts; no font assets; text size and placement remain in scope'},
 'reference':str(a.reference),'android':str(a.android),'results':report},ensure_ascii=False,indent=2))
