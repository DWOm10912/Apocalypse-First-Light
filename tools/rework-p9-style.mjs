// One-shot, hand-authored P9 exterior/UV revision. No generated imagery or foreign assets.
// --write applies it once; ongoing edits belong in the bbmodel, not repeated regeneration.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {root,sourcePath,assets,read,compile} from './export-native-gun.mjs';
assert(process.argv.includes('--write'),'Use --write for the one-shot asset revision');
const s=read(sourcePath), original=structuredClone(s);
assert(!s.elements.some(e=>e.name==='slide_relief_left'),'Already revised; edit the source directly');
const backup=path.join(root,'build/p9-style-before');
assert(!fs.existsSync(backup),'Existing baseline: do not overwrite it');
fs.mkdirSync(backup,{recursive:true});
for(const file of [sourcePath,path.join(assets,'textures/item/p9_01.png'),path.join(assets,'geo/p9_01.geo.json')])
    fs.copyFileSync(file,path.join(backup,path.basename(file)));
const groups=new Map(s.groups.map(g=>[g.name,g]));
const nodes=new Map();
function walk(ns){for(const n of ns)if(typeof n!=='string'){nodes.set(n.uuid,n);walk(n.children)}}
walk(s.outliner);
const el=name=>{const e=s.elements.find(e=>e.name===name);assert(e,name);return e};
const mats=new Map();
const uuid=name=>{const h=crypto.createHash('md5').update('afl/p9-style-v1/'+name).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`};
function box(name,group,from,to,material='frame',rotation=[0,0,0],origin=from.map((v,i)=>(v+to[i])/2)){
    const e=structuredClone(el('frame_primary'));
    Object.assign(e,{name,uuid:uuid(name),from,to,origin,rotation,inflate:0,export:true,visibility:true});
    for(const f of Object.values(e.faces)){f.texture=0;delete f.rotation}
    s.elements.push(e);nodes.get(groups.get(group).uuid).children.push(e.uuid);mats.set(name,material);return e;
}
// Split front face around the bore, leaving the barrel crown mechanically separate.
const cap=el('slide_front_cap');
cap.from=[-3.86,10.88,-4.56];cap.to=[-2.1,10.95,-4.32];
box('slide_front_lower_bridge','slide',[-3.86,9.71,-4.56],[-2.1,9.82,-4.32],'slide');
box('slide_front_left_cheek','slide',[-3.86,9.82,-4.56],[-3.50,10.88,-4.32],'slide');
box('slide_front_right_cheek','slide',[-2.46,9.82,-4.56],[-2.1,10.88,-4.32],'slide');
// Narrow side flats and bevel transitions: no accessory rails on the slide.
box('slide_relief_left','slide',[-4.128,10.08,-3.52],[-4.099,10.55,5.98],'slide');
box('slide_relief_right','slide',[-1.861,10.08,-3.52],[-1.832,10.55,2.67],'slide');
for(const [side,x] of [['left',-4.14],['right',-1.89]]){
    for(let i=0;i<4;i++)box(`front_serration_${side}_${i}`,'slide',[x,9.89,-2.97+i*.43],[x+.07,10.63,-2.86+i*.43],'recess',[-9,0,0]);
    box(`slide_running_seam_${side}`,'slide',[x,9.61,-3.70],[x+.05,9.69,9.18],'recess');
}
box('slide_rear_service_plate','slide',[-3.55,9.90,9.841],[-2.41,10.62,9.881],'darkMetal');
box('slide_rear_plate_bevel','slide',[-3.47,10.60,9.842],[-2.49,10.69,9.887],'metal');
box('extractor_recess','slide',[-1.792,10.12,5.63],[-1.766,10.24,6.18],'recess');
box('chamber_hood_edge','barrel',[-3.64,10.99,3.10],[-2.28,11.028,3.22],'metal');
// Crown highlights belong to the barrel; maintain the original muzzle axis and length.
for(let i=0;i<8;i++){
    const e=el(`muzzle_facet_${i}`);mats.set(e.name,i%2?'darkMetal':'metal');
}
mats.set('bore_recess','bore');
// Shaped dust-cover panels and two small controls/pins; all within the old envelope.
for(const [side,x] of [['left',-4.006],['right',-1.988]]){
    box(`frame_dust_inset_${side}`,'frame',[x,8.73,-3.18],[x+.034,9.15,.96],'polymer');
    box(`frame_dust_upper_bevel_${side}`,'frame',[x,9.17,-3.21],[x+.035,9.24,1.07],'frame');
    box(`frame_takedown_${side}`,'frame',[x-.022,8.91,2.69],[x+.058,9.19,3.29],'darkMetal');
    for(const [j,z] of [[0,4.48],[1,7.67]]){
        box(`frame_pin_${side}_${j}`,'frame',[x-.025,8.70,z],[x+.06,8.96,z+.26],'metal',[45,0,0]);
        box(`frame_pin_core_${side}_${j}`,'frame',[x-.027,8.785,z+.085],[x+.062,8.875,z+.175],'recess');
    }
}
for(let i=0;i<3;i++)box(`slide_stop_ridge_${i}`,'frame',[-4.179,8.96,5.37+i*.23],[-4.163,9.18,5.44+i*.23],'metal');
for(let i=0;i<3;i++)box(`mag_release_ridge_${i}`,'frame',[-4.116,7.97+i*.085,5.94],[-4.099,8.006+i*.085,6.30],'frame');
// Grip panels use the exact original grip pivot/rake, not a new animated group.
for(const side of [-1,1]){
    const panel=el(`grip_panel_${side}`),p=panel.origin,rot=panel.rotation;
    mats.set(panel.name,'grip');
    const x=side<0?-4.011:-1.973;
    box(`grip_panel_front_border_${side}`,'frame',[x,2.73,6.27],[x+.025,6.80,6.36],'polymer',rot,p);
    box(`grip_panel_rear_border_${side}`,'frame',[x,2.73,8.49],[x+.025,6.80,8.60],'polymer',rot,p);
    box(`grip_panel_lower_border_${side}`,'frame',[x,2.61,6.38],[x+.025,2.75,8.48],'frame',rot,p);
    box(`grip_panel_upper_border_${side}`,'frame',[x,6.78,6.38],[x+.025,6.92,8.48],'frame',rot,p);
    box(`grip_thumb_recess_${side}`,'frame',[x,6.10,6.55],[x+.03,6.59,7.89],'polymer',rot,p);
}
for(const name of ['frontstrap_pad','backstrap_crown']){
    const e=el(name);mats.set(name,'grip');
    for(let i=0;i<7;i++){
        const z=name==='frontstrap_pad'?e.from[2]-.014:e.to[2]-.012;
        box(`${name}_traction_${i}`,'frame',[-3.62,2.98+i*.46,z],[-2.34,3.045+i*.46,z+.026],'polymer',e.rotation,e.origin);
    }
}
// Guard inner chamfers keep its opening, the trigger, and the gripping hand clear.
for(let i of [1,2,3]){
    const e=el(`guard_segment_${i}`);
    for(const side of [-1,1]){
        const from=[...e.from],to=[...e.to];
        from[0]=side<0?e.from[0]-.016:e.to[0]-.024;to[0]=from[0]+.04;
        from[1]+=.05;to[1]-=.05;
        box(`guard_edge_${i}_${side}`,'frame',from,to,'frame',e.rotation,e.origin);
    }
}
// Pressed magazine ribs/floorplate details move with the original magazine bone.
for(const side of [-1,1]){
    const x=side<0?-3.755:-2.222;
    for(let i=0;i<2;i++)box(`magazine_pressed_rib_${side}_${i}`,'magazine',[x,2.38,6.73+i*1.12],[x+.035,7.30,6.83+i*1.12],'darkMetal');
    box(`magazine_floor_grip_${side}`,'magazine',[side<0?-3.995:-1.987,1.86,6.31],[side<0?-3.978:-1.969,2.03,8.58],'polymer');
}
box('magazine_floor_seam','magazine',[-3.83,2.14,6.23],[-2.13,2.21,8.72],'recess');
box('magazine_floor_insert','magazine',[-3.59,1.724,6.65],[-2.37,1.752,8.18],'frame');
box('magazine_floor_lock','magazine',[-3.11,1.709,7.33],[-2.85,1.729,7.59],'darkMetal');
// Dovetail feet remain BELOW the current sighting surface; notch/post untouched.
box('front_sight_dovetail','front_sight',[-3.37,11.147,-3.67],[-2.59,11.205,-2.98],'darkMetal');
box('rear_sight_dovetail','rear_sight',[-3.88,11.147,8.71],[-2.08,11.217,9.61],'darkMetal');
// Purpose-designed face atlas, 128 logical UV units with 256px physical resolution.
// Shared UV islands only for identical small surfaces; large surfaces retain correct density.
const size=256,pixels=Buffer.alloc(size*size*4),palette={
    slide:[61,64,66],frame:[38,41,43],polymer:[29,32,33],grip:[26,29,30],
    metal:[77,81,83],darkMetal:[47,51,53],recess:[19,22,24],bore:[8,10,12],dot:[169,181,173]
};
for(let i=0;i<size*size;i++)pixels.set([19,22,24,255],i*4);
function material(e){
    if(mats.has(e.name))return mats.get(e.name);
    const n=e.name;
    if(n.includes('dot'))return 'dot';
    if(/serration|shadow|inner_dark/.test(n))return 'recess';
    if(/sight/.test(n))return 'darkMetal';
    if(/slide|bevel/.test(n))return 'slide';
    if(/chamber|barrel|guide_rod/.test(n))return 'metal';
    if(/magazine_body|extractor|trigger/.test(n))return 'darkMetal';
    if(/grip|strap/.test(n))return 'polymer';
    return 'frame';
}
const cache=new Map(),islands=[];
const light={up:6,down:-5,north:-2,south:0,east:0,west:0};
for(const e of s.elements.filter(e=>e.export!==false)){
    const dims=e.to.map((v,i)=>v-e.from[i]);
    for(const [face,f] of Object.entries(e.faces)){
        if(f.texture===null)continue;
        const axis=face==='up'||face==='down'?[0,2]:face==='east'||face==='west'?[2,1]:[0,1];
        const w=Math.max(2,Math.ceil(dims[axis[0]]*4)),h=Math.max(2,Math.ceil(dims[axis[1]]*4));
        const m=material(e),pattern=m==='grip'?'stipple':e.name==='magazine_body'&&(face==='east'||face==='west')?'witness':'plain';
        const key=[m,light[face],w,h,pattern].join('/');
        if(!cache.has(key)){const island={w,h,m,light:light[face],pattern,faces:[]};cache.set(key,island);islands.push(island)}
        cache.get(key).faces.push(f);
    }
}
// Shelf pack tall islands first. One pixel of extrusion prevents sampling other surfaces.
islands.sort((a,b)=>b.h-a.h||b.w-a.w);
let px=1,py=1,rowH=0;
for(const island of islands){
    const {w,h,m,pattern}=island;
    if(px+w+1>=size){px=1;py+=rowH+2;rowH=0}
    assert(py+h+1<size,'UV atlas full');rowH=Math.max(rowH,h);
    for(let y=-1;y<=h;y++)for(let x=-1;x<=w;x++){
        const u=Math.max(0,Math.min(w-1,x)),v=Math.max(0,Math.min(h-1,y));
        let delta=island.light;
        if(m!=='bore'&&m!=='dot'){
            delta+=(v===0?6:v===h-1?-5:0)+(u===0?2:u===w-1?-2:0);
            if(w>6&&h>4)delta+=((u*7+v*11)%13===0?1:0);
            if(pattern==='stipple'&&u>1&&u<w-2&&v>1&&v<h-2)delta+=((u+2*(v%2))%4===0&&v%3===0)?7:-1;
            if(pattern==='witness'&&u===Math.floor(w/2)&&v>3&&v<h-3&&v%4<2)delta=-23;
            if(m==='slide'&&h>5&&v===2&&u>2&&u<w-3)delta+=2;
        }
        pixels.set([...palette[m].map(c=>Math.max(0,Math.min(255,c+delta))),255],((py+y)*size+px+x)*4);
    }
    for(const f of island.faces){f.uv=[px/2,py/2,(px+w)/2,(py+h)/2];f.texture=0}
    px+=w+2;
}
const crc=b=>{let c=0xffffffff;for(const v of b){c^=v;for(let i=0;i<8;i++)c=(c>>>1)^((c&1)?0xedb88320:0)}return(c^0xffffffff)>>>0};
const chunk=(name,b)=>{const t=Buffer.from(name),n=Buffer.alloc(4),c=Buffer.alloc(4);n.writeUInt32BE(b.length);c.writeUInt32BE(crc(Buffer.concat([t,b])));return Buffer.concat([n,t,b,c])};
const header=Buffer.alloc(13);header.writeUInt32BE(size);header.writeUInt32BE(size,4);header[8]=8;header[9]=6;
const raw=Buffer.alloc(size*(size*4+1));for(let y=0;y<size;y++)pixels.copy(raw,y*(size*4+1)+1,y*size*4,(y+1)*size*4);
const png=Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',header),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);
Object.assign(s.textures[0],{width:size,height:size,source:'data:image/png;base64,'+png.toString('base64')});
assert.deepEqual(s.groups,original.groups,'All bones/pivots/anchors frozen');
assert.deepEqual(s.animations,original.animations,'Animation keys frozen');
assert.deepEqual(s.display,original.display,'All Display transforms frozen');
assert.deepEqual(s.elements.filter(e=>e.export===false),original.elements.filter(e=>e.export===false),'Reference arms frozen');
const geoPath=path.join(assets,'geo/p9_01.geo.json'),animPath=path.join(assets,'animations/p9_01.animation.json'),displayPath=path.join(assets,'models/item/p9_01_in_hand.json');
fs.writeFileSync(path.join(assets,'textures/item/p9_01.png'),png);
const out=compile(s,read(geoPath),read(displayPath));
assert.deepEqual(out.animations,read(animPath),'Runtime animation cannot change');
assert.deepEqual(out.display,read(displayPath),'Runtime Display cannot change');
fs.writeFileSync(sourcePath,JSON.stringify(s,null,2)+'\n');
fs.writeFileSync(geoPath,JSON.stringify(out.geometry,null,2)+'\n');
console.log(JSON.stringify({before:original.elements.length,after:s.elements.length,runtime:s.elements.filter(e=>e.export!==false).length,texture:size,atlasUsedHeight:py+rowH+1,islands:islands.length,anchorsChanged:0,animationKeysChanged:0}));
