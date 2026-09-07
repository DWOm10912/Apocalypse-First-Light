"""Offline asset QA only: render actual item cubes / atlas colours, not a game screenshot."""
import json, math
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/apocalypse_firstlight'
canvas=Image.new('RGB',(1200,580),(35,39,44)); draw=ImageDraw.Draw(canvas)
faces={'north':[0,1,2,3],'south':[4,7,6,5],'west':[0,3,7,4],'east':[1,5,6,2],'down':[0,4,5,1],'up':[3,2,6,7]}
def rotate(p,axis,a):
    x,y,z=p;c=math.cos(math.radians(a));s=math.sin(math.radians(a))
    return {'x':(x,y*c-z*s,y*s+z*c),'y':(x*c+z*s,y,-x*s+z*c),'z':(x*c-y*s,x*s+y*c,z)}[axis]
for column,name in enumerate(['9x19mm_round','9x19mm_casing','762x51mm_round','762x51mm_casing']):
    m=json.loads((ASSETS/'models/item'/f'{name}.json').read_text())
    tex=Image.open(ASSETS/'textures/item'/f'{name}.png').convert('RGB'); polys=[]
    for e in m['elements']:
        x,y,z=e['from'];X,Y,Z=e['to'];v=[(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
        if 'rotation' in e:
            r=e['rotation'];o=r['origin'];v=[tuple(a+b for a,b in zip(rotate(tuple(a-b for a,b in zip(p,o)),r['axis'],r['angle']),o)) for p in v]
        v=[rotate(rotate(tuple(a-8 for a in p),'y',30),'x',18) for p in v]
        for f,idx in faces.items():
            if f not in e['faces']:continue
            p=[v[i] for i in idx];u=e['faces'][f]['uv']; px=int((u[0]+u[2])/32*tex.width);py=int((u[1]+u[3])/32*tex.height)
            col=tex.getpixel((min(tex.width-1,px),min(tex.height-1,py))); shade={'north':.9,'south':.8,'west':.65,'east':.85,'down':.6,'up':1.1}[f]
            polys.append((sum(q[2] for q in p)/4,[(column*300+150+q[0]*28,275-q[1]*28) for q in p],tuple(min(255,int(c*shade)) for c in col)))
    for _,p,c in sorted(polys,key=lambda p:p[0]):draw.polygon(p,fill=c)
    draw.text((column*300+60,500),name,fill='white')
draw.text((30,550),'OFFLINE GEOMETRY / TEXTURE CHECK - NOT IN-GAME VALIDATION',fill='gray')
out=ROOT/'build/native-ammo-preview.png';out.parent.mkdir(exist_ok=True);canvas.save(out)
print(out)
