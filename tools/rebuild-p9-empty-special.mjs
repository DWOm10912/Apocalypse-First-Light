// One-shot original emergency reload choreography, preserving every other clip.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {root,sourcePath,assets,read,compile} from './export-native-gun.mjs';
import {I,chain,R,T,pt,sample} from './check-native-gun-aimline.mjs';
import {oldMagazinePose} from './p9-empty-flight.mjs';
assert(process.argv.includes('--write'),'Pass --write');
const backup=path.join(root,'build/p9-empty-special-before');
const refining=fs.existsSync(backup);
assert(!refining||process.argv.includes('--refine'),'Pass --refine for a scoped revision; baseline is never overwritten');
fs.mkdirSync(backup,{recursive:true});
const current=read(sourcePath),before=refining?read(path.join(backup,path.basename(sourcePath))):current;
if(refining){
    for(const b of before.groups)assert.deepEqual(current.groups.find(g=>g.name===b.name),b,'Stop: existing bone edited');
    for(const c of before.elements)assert.deepEqual(current.elements.find(e=>e.uuid===c.uuid),c,'Stop: existing geometry edited');
    for(const a of before.animations)if(!a.name.endsWith('.reload_empty'))assert.deepEqual(current.animations.find(b=>b.name===a.name),a,'Stop: other animation edited');
    for(const field of ['display','textures','resolution'])assert.deepEqual(current[field],before[field],'Stop: source changed '+field);
}
const s=structuredClone(before),g=n=>s.groups.find(g=>g.name===n),zero=[0,0,0];
if(!refining)for(const f of [sourcePath,assets+'/geo/p9_01.geo.json',assets+'/animations/p9_01.animation.json',assets+'/animations/p9_01.equip.json'])fs.copyFileSync(f,path.join(backup,path.basename(f)));
const definition=read(path.join(root,'src/main/resources/data/apocalypse_firstlight/native_guns/p9_01.json'));
const id=n=>{const h=createHash('sha256').update('afl/p9/emergency-reload-v1/'+n).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-8${h.slice(17,20)}-${h.slice(20,32)}`};
const add=(a,b)=>a.map((n,i)=>n+b[i]),sub=(a,b)=>a.map((n,i)=>n-b[i]),scale=(v,k)=>v.map(n=>n*k);
const rot=r=>chain(R(2,r[2]),R(1,r[1]),R(0,r[0]));
function scalar(k,t){
    if(t<=k[0][0])return k[0][1];if(t>=k.at(-1)[0])return k.at(-1)[1];
    const h=k.slice(1).map((p,i)=>p[0]-k[i][0]),d=h.map((x,i)=>(k[i+1][1]-k[i][1])/x),m=k.map(()=>0);
    for(let i=1;i<k.length-1;i++)if(d[i-1]*d[i]>0){const a=2*h[i]+h[i-1],b=h[i]+2*h[i-1];m[i]=(a+b)/(a/d[i-1]+b/d[i])}
    const j=k.findIndex(p=>p[0]>t)-1,u=(t-k[j][0])/h[j];
    return (2*u**3-3*u*u+1)*k[j][1]+(u**3-2*u*u+u)*h[j]*m[j]+(-2*u**3+3*u*u)*k[j+1][1]+(u**3-u*u)*h[j]*m[j+1];
}
const curve=(k,t)=>[0,1,2].map(i=>scalar(k.map(([t,v])=>[t,v[i]]),t));
const nodes=new Map();function walk(ns){for(const n of ns)if(typeof n!=='string'){nodes.set(n.uuid,n);walk(n.children)}}walk(s.outliner);
const magazine=g('magazine'),old=structuredClone(magazine);
Object.assign(old,{name:'empty_old_magazine',uuid:id('old-magazine'),visibility:false,children:[]});
const oldNode={uuid:old.uuid,children:[]};
for(const uuid of nodes.get(magazine.uuid).children){
    assert.equal(typeof uuid,'string','Magazine clone only supports direct cube children');
    const source=s.elements.find(e=>e.uuid===uuid),cube=structuredClone(source);
    cube.uuid=id(source.name);cube.name='empty_old_'+source.name;
    oldNode.children.push(cube.uuid);s.elements.push(cube);
}
nodes.get(g('gun').uuid).children.push(oldNode);s.groups.push(old);
const original=before.animations.find(a=>a.name==='animation.p9_01.reload_empty');
assert.equal(original.length,1.65);
const a={uuid:id('clip'),name:original.name,loop:'once',override:false,length:1.65,snapping:200,anim_time_update:'',blend_weight:'',start_delay:'',loop_delay:'',animators:{}};
s.animations[s.animations.findIndex(a=>a.name===original.name)]=a;
function key(b,ch,t,v,interpolation='linear'){
    const bone=g(b),track=a.animators[bone.uuid]??={name:b,type:'bone',rotation_global:false,quaternion_interpolation:false,keyframes:[]};
    track.keyframes.push({channel:ch,time:t,data_points:[Object.fromEntries(['x','y','z'].map((axis,i)=>[axis,String(+v[i].toFixed(8))]))],uuid:id(`${b}/${ch}/${t}`),color:-1,interpolation});
}
function bake(b,ch,fn){for(let i=0;i<=330;i++){const t=+(i*.005).toFixed(4);key(b,ch,t,fn(t))}}
// Expose the magwell before the snap. Release at .48s, near maximum leftward
// velocity in the .40-.56s sweep, not at its stopped endpoint or rebound.
const hr=[[0,zero],[.06,[-1,0,-2]],[.30,[-12,42,-65]],[.40,[-12,45,-70]],[.56,[-8,68,-92]],[.63,[-10,42,-60]],[.78,[-11,-4,-33]],[.93,[-11,-4,-32]],[.965,[-12.2,-4,-33]],[1.03,[-10.8,-4,-32]],[1.20,[-8,-3,-26]],[1.25,[-8,-3,-26]],[1.31,[-9.3,-3,-28]],[1.39,[-7,-2,-23]],[1.54,[.45,.1,.9]],[1.65,zero]];
const hp=[[0,zero],[.10,zero],[.30,[.25,4.8,-.05]],[.40,[.35,4.8,-.05]],[.56,[-1.6,4.95,-.25]],[.65,[-.9,2.5,-.1]],[.80,[.08,.07,.12]],[.95,[.08,.05,.10]],[.98,[.06,.01,.14]],[1.04,[.08,.06,.10]],[1.25,[.08,.06,.10]],[1.31,[.07,.04,.16]],[1.41,[.05,.04,.06]],[1.58,[0,.005,-.01]],[1.65,zero]];
bake('p9_handling','rotation',t=>curve(hr,t));bake('p9_handling','position',t=>curve(hp,t));
const pivot=g('p9_handling').origin;
const handlingKeys=a.animators[g('p9_handling').uuid].keyframes;
const handling=t=>chain(T(sample(handlingKeys,'position',t)),T(pivot),rot(sample(handlingKeys,'rotation',t)),T(scale(pivot,-1)));
const axis=pt(rot(magazine.rotation),[0,-1,0]);
// Old magazine travels in the RELEASE-TIME gun basis. Compensate subsequent
// wrist movement in its baked local tracks so the free magazine doesn't follow it.
function oldPose(t){
    return oldMagazinePose(handling,magazine,t);
}
bake(old.name,'position',t=>oldPose(t).p);bake(old.name,'rotation',t=>oldPose(t).r);
for(const [t,v] of [[0,0],[.40,1],[.84,0],[1.65,0]])key(old.name,'scale',t,[v,v,v],'step');
const d=[[.41,9],[.61,9],[.70,7.8],[.80,5.2],[.87,2.9],[.91,1.8],[.95,0],[1.65,0]];
const lateral=[[.41,[-3.5,-.4,1.2]],[.61,[-3.5,-.4,1.2]],[.70,[-1.7,-.15,.8]],[.80,[-.45,0,.2]],[.86,zero],[1.65,zero]];
const nr=[[.41,[7,-10,-8]],[.61,[7,-10,-8]],[.73,[3,-5,-3]],[.84,zero],[1.65,zero]];
const magPosition=t=>t<=.4?zero:add(scale(axis,scalar(d,t)),curve(lateral,t));
const magRotation=t=>t<=.4?zero:curve(nr,t);
for(const name of ['magazine','reload_magazine']){bake(name,'position',magPosition);bake(name,'rotation',magRotation)}
for(const [t,v] of [[0,1],[.4,0],[.61,1],[1.65,1]])key('magazine','scale',t,[v,v,v],'step');
const baseplate=s.elements.find(e=>e.name==='magazine_baseplate');
const cap=[-3.62,baseplate.to[1]-.05,(baseplate.from[2]+baseplate.to[2])/2];
const contact=t=>add(add(magazine.origin,magPosition(t)),pt(rot(add(magazine.rotation,magRotation(t))),sub(cap,magazine.origin)));
const hand=g('left_hand_anchor').origin,handPivot=g('left_hand_motion').origin;
const approach=[[0,hand],[.055,hand],[.20,add(hand,[-1,-1.4,1])],[.34,[-9.2,-3.4,10]],[.48,add(contact(.61),[-.8,-.4,.5])],[.56,contact(.61)],[.61,contact(.61)]];
const wrist=[[0,zero],[.09,zero],[.23,[12,-4,10]],[.43,[29,-12,4]],[.61,[27,-9,10]],[.80,[23,-6,12]],[.95,[20,-5,13]],[1.06,[18,-4,10]],[1.16,[5,-7,7]],[1.24,[-4,-8,3.5]],[1.31,[-5,-8,3]],[1.37,[-2,-6,4]],[1.52,[1,-.5,.7]],[1.62,zero],[1.65,zero]];
const slide=t=>scalar([[0,1.04],[1.25,1.04],[1.30,1.27],[1.32,1.27],[1.39,0],[1.65,0]],t);
const rear=t=>[-4.19,10.45,8.1+slide(t)];
const gripPath=[[1.04,contact(1.04)],[1.14,[-4.2,6.4,8.8]],[1.23,rear(1.23)]];
const backPath=[[1.35,rear(1.35)],[1.42,[-4.65,8.8,8.5]],[1.53,add(hand,[-.3,-.18,.3])],[1.62,hand],[1.65,hand]];
const target=t=>t<.61?curve(approach,t):t<=1.04?contact(t):t<1.23?curve(gripPath,t):t<=1.35?rear(t):curve(backPath,t);
bake('left_hand_motion','rotation',t=>curve(wrist,t));
bake('left_hand_motion','position',t=>sub(sub(target(t),handPivot),pt(rot(curve(wrist,t)),sub(hand,handPivot))));
for(const name of ['slide','front_sight','rear_sight','sight_anchor'])bake(name,'position',t=>[0,0,slide(t)]);
a.animators.effects={name:'Effects',type:'effect',keyframes:[{channel:'sound',data_points:[{effect:'slide_action',file:'E:/Download/slide_action.ogg'}],uuid:id('slide-sound'),time:1.25,color:-1,interpolation:'linear'}]};
for(const tr of Object.values(a.animators))tr.keyframes.sort((x,y)=>x.time-y.time||x.channel.localeCompare(y.channel));
for(const clip of before.animations)if(clip.name!==a.name)assert.deepEqual(s.animations.find(a=>a.name===clip.name),clip,'Other clips frozen');
for(const b of before.groups)assert.deepEqual(g(b.name),b,'Existing bone frozen');
for(const c of before.elements)assert.deepEqual(s.elements.find(e=>e.uuid===c.uuid),c,'Existing geometry frozen');
assert.deepEqual(s.display,before.display);assert.deepEqual(s.textures,before.textures);
const result=compile(s,read(assets+'/geo/p9_01.geo.json'),read(assets+'/models/item/p9_01_in_hand.json'));
fs.writeFileSync(sourcePath,JSON.stringify(s,null,2)+'\n');
fs.writeFileSync(assets+'/geo/p9_01.geo.json',JSON.stringify(result.geometry,null,2)+'\n');
fs.writeFileSync(assets+'/animations/p9_01.animation.json',JSON.stringify(result.animations,null,2)+'\n');
if(!refining)fs.writeFileSync(path.join(backup,'gameplay.json'),JSON.stringify(definition,null,2)+'\n');
else assert.deepEqual(definition,read(path.join(backup,'gameplay.json')),'Gameplay drift');
console.log(JSON.stringify({clip:a.name,length:a.length,newOldMagazineCubes:oldNode.children.length,otherClipsChanged:false,oldNewOverlap:[.61,.84],release:.48,magIn:.95,slideSound:1.25}));
