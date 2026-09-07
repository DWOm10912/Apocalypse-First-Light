// Original pistol choreography. Semantic poses below are authored anew, not sampled from old clips.
// Curves are baked at 100 Hz for identical numeric playback in Blockbench and Gecko.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {root,sourcePath,assets,read,compile} from './export-native-gun.mjs';
import {chain,R,pt} from './check-native-gun-aimline.mjs';
const baseDir=path.join(root,'build/p9-animation-v1-before');
assert(process.argv.includes('--write'),'One-shot authoring: pass --write');
fs.mkdirSync(baseDir,{recursive:true});
const source=read(sourcePath),s=structuredClone(source),g=n=>s.groups.find(g=>g.name===n),zero=[0,0,0];
for(const f of [sourcePath,assets+'/animations/p9_01.animation.json',assets+'/geo/p9_01.geo.json',assets+'/textures/item/p9_01_inventory.png']){
    const backup=path.join(baseDir,path.basename(f));
    if(fs.existsSync(backup))assert(fs.readFileSync(f).equals(fs.readFileSync(backup)),'Only retry against unchanged authoring baseline: '+f);
    else fs.copyFileSync(f,backup);
}
const uid=n=>{let h=createHash('sha256').update('afl/p9/new-animation-v1/'+n).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-4${h.slice(13,16)}-8${h.slice(17,20)}-${h.slice(20,32)}`};
const add=(a,b)=>a.map((x,i)=>x+b[i]),sub=(a,b)=>a.map((x,i)=>x-b[i]),mul=(a,k)=>a.map(x=>x*k);
const rot=v=>chain(R(2,v[2]),R(1,v[1]),R(0,v[0]));
// Shape-preserving Hermite: nonzero velocity through waypoints, zero at deliberate dwells.
function scalar(k,t){
    if(t<=k[0][0])return k[0][1];if(t>=k.at(-1)[0])return k.at(-1)[1];
    const h=k.slice(1).map((p,i)=>p[0]-k[i][0]),d=h.map((x,i)=>(k[i+1][1]-k[i][1])/x),m=k.map(()=>0);
    for(let i=1;i<k.length-1;i++)if(d[i-1]*d[i]>0){let a=2*h[i]+h[i-1],b=h[i]+2*h[i-1];m[i]=(a+b)/(a/d[i-1]+b/d[i])}
    const j=k.findIndex(p=>p[0]>t)-1,u=(t-k[j][0])/h[j];
    return (2*u**3-3*u*u+1)*k[j][1]+(u**3-2*u*u+u)*h[j]*m[j]+(-2*u**3+3*u*u)*k[j+1][1]+(u**3-u*u)*h[j]*m[j+1];
}
const curve=(k,t)=>[0,1,2].map(i=>scalar(k.map(([t,v])=>[t,v[i]]),t));
const nodes=new Map();function walk(ns){for(const n of ns)if(typeof n!=='string'){nodes.set(n.uuid,n);walk(n.children)}}walk(s.outliner);
// One common handling carrier keeps the right hand rigidly seated without corrective tracks.
const carrier=structuredClone(g('weapon_root'));
Object.assign(carrier,{name:'p9_handling',uuid:uid('p9_handling'),origin:[...g('right_hand_anchor').origin],rotation:zero,children:[]});
const fp=nodes.get(g('fp_root').uuid),weapon=nodes.get(g('weapon_root').uuid);
for(const name of ['right_hand_motion','left_hand_motion'])assert(weapon.children.some(n=>n.uuid===g(name).uuid),'Hands must remain beneath weapon_root');
const children=fp.children.filter(n=>n.uuid===weapon.uuid);assert.equal(children.length,1);
fp.children=fp.children.filter(n=>!children.includes(n));fp.children.push({uuid:carrier.uuid,children});s.groups.push(carrier);
s.animations=[];
function clip(name,length,loop='once'){
    const a={uuid:uid(name),name:'animation.p9_01.'+name,loop,override:false,length,snapping:100,anim_time_update:'',blend_weight:'',start_delay:'',loop_delay:'',animators:{}};
    s.animations.push(a);return a;
}
function key(a,b,ch,t,v,interpolation='linear'){
    const bone=g(b);assert(bone,b);
    const track=a.animators[bone.uuid]??={name:b,type:'bone',rotation_global:false,quaternion_interpolation:false,keyframes:[]};
    track.keyframes.push({channel:ch,data_points:[Object.fromEntries(['x','y','z'].map((axis,i)=>[axis,String(+v[i].toFixed(8))]))],uuid:uid(`${a.name}/${b}/${ch}/${t}`),time:t,color:-1,interpolation});
}
function bake(a,b,ch,fn,step=.01){for(let i=0;i<=Math.round(a.length/step);i++){const t=+(i*step).toFixed(4);key(a,b,ch,t,fn(t))}}
function track(a,b,ch,knots,step=.01){bake(a,b,ch,t=>curve(knots,t),step)}
const hand=g('left_hand_anchor').origin,pivot=g('left_hand_motion').origin;
function left(a,target,wrist){
    bake(a,'left_hand_motion','rotation',wrist);
    bake(a,'left_hand_motion','position',t=>sub(sub(target(t),pivot),pt(rot(wrist(t)),sub(hand,pivot))));
}
const idle=clip('static_idle',2.4,'loop');
// Support-hand micro flex only; do not move gun/sight line or change the approved Ready pose.
left(idle,()=>hand,t=>[.11*Math.sin(t*Math.PI/1.2),0,.06*Math.sin(t*Math.PI/1.2)]);
for(const name of ['draw','put_away']){
    const a=clip(name,.15);
    const entering=name==='draw';
    track(a,'p9_handling','position',entering?[[0,[.7,-17,3.2]],[.04,[.42,-6.2,1.7]],[.085,[.09,-.7,.35]],[.115,[0,.08,-.07]],[.15,zero]]:[[0,zero],[.025,[.06,-.1,.07]],[.08,[.4,-4.6,1.5]],[.15,[1,-17,3.4]]],.005);
    track(a,'p9_handling','rotation',entering?[[0,[-24,12,-20]],[.045,[-12,5,-9]],[.095,[1.1,-.35,.65]],[.15,zero]]:[[0,zero],[.025,[-2,3,-2]],[.08,[-16,11,-9]],[.15,[-28,16,-22]]],.005);
    left(a,t=>add(hand,curve(entering?[[0,[-1.3,-2,1.4]],[.035,[-.8,-1.2,.8]],[.10,[-.08,-.12,.05]],[.15,zero]]:[[0,zero],[.025,zero],[.10,[-.8,-1.6,1]],[.15,[-1.4,-2.2,1.6]]],t)),t=>curve(entering?[[0,[8,-4,8]],[.045,[6,-2,6]],[.12,[-.3,0,-.2]],[.15,zero]]:[[0,zero],[.025,zero],[.15,[9,-4,9]]],t));
}
const slideBones=['slide','front_sight','rear_sight','sight_anchor'];
const lock=1.04;
for(const last of [false,true]){
    const a=clip(last?'fire_last_round':'fire',.14);
    for(const b of slideBones)track(a,b,'position',[[0,zero],[.015,[0,0,1.22]],[.035,[0,0,1.24]],[.07,[0,0,last?lock:.14]],[.10,[0,0,last?lock:0]],[.14,[0,0,last?lock:0]]],.005);
    track(a,'barrel','rotation',[[0,zero],[.02,[1.25,0,0]],[.065,[.5,0,0]],[.10,zero],[.14,zero]],.005);
    track(a,'barrel','position',[[0,zero],[.02,[0,-.045,.11]],[.08,zero],[.14,zero]],.005);
    track(a,'trigger','rotation',[[0,zero],[.01,[-5,0,0]],[.045,[-5,0,0]],[.105,zero],[.14,zero]],.005);
    track(a,'p9_handling','rotation',[[0,zero],[.02,[.32,.025,-.09]],[.055,[.12,0,.035]],[.095,[-.035,0,-.015]],[.14,zero]],.005);
    track(a,'p9_handling','position',[[0,zero],[.025,[0,-.012,.09]],[.06,[0,0,.028]],[.10,[0,0,-.008]],[.14,zero]],.005);
}
const emptyIdle=clip('empty_idle',1,'loop');
for(const b of slideBones){key(emptyIdle,b,'position',0,[0,0,lock]);key(emptyIdle,b,'position',1,[0,0,lock])}
const mag=g('magazine'),plate=s.elements.find(e=>e.name==='magazine_baseplate');
const cap=[-3.62,plate.to[1]-.05,(plate.from[2]+plate.to[2])/2];
const seated=add(mag.origin,pt(rot(mag.rotation),sub(cap,mag.origin)));
const axis=pt(rot(mag.rotation),[0,-1,0]);
for(const empty of [false,true]){
    const a=clip(empty?'reload_empty':'reload',empty?1.65:1.3),end=a.length;
    const rootR=[[0,zero],[.06,[-1,1,-2]],[.24,[-12,-6,-32]],[.40,[-14,-6,-34]],[.56,[-15,-8,-39]],[.72,[-12,-7,-36]],[.90,[-11,-5,-33]],[.96,[-13,-5,-35]],[1.03,[-11.5,-5,-33.5]],...(empty?[[1.14,[-9,-4,-28]],[1.27,[-9,-4,-28]],[1.33,[-10.2,-4,-29.4]],[1.42,[-8.5,-3,-26]],[1.58,[.35,.1,.7]],[1.65,zero]]:[[1.10,[-9,-4,-26]],[1.23,[.3,.1,.65]],[1.3,zero]])];
    track(a,'p9_handling','rotation',rootR);
    track(a,'p9_handling','position',[[0,zero],[.09,[0,0,0]],[.28,[.12,.1,.08]],[.6,[.05,.12,.25]],[.90,[.08,.10,.10]],[.96,[.07,.045,.14]],[1.03,[.08,.1,.1]],...(empty?[[1.40,[.08,.1,.1]],[1.65,zero]]:[[1.3,zero]])]);
    const dist=[[0,0],[.4,0],[.46,2.3],[.54,6.7],[.63,9],[.69,9],[.76,7.3],[.82,5.9],[.90,1.6],[.95,0],[end,0]];
    const sweep=[[0,zero],[.53,zero],[.63,[-2,-.5,.9]],[.69,[-2,-.5,.9]],[.77,[-.65,-.08,.3]],[.83,zero],[end,zero]];
    const mr=[[0,zero],[.54,zero],[.63,[7,-9,-10]],[.69,[7,-9,-10]],[.77,[2,-3,-3]],[.83,zero],[end,zero]];
    const mp=t=>add(mul(axis,scalar(dist,t)),curve(sweep,t)),magRot=t=>curve(mr,t);
    const contact=t=>add(add(mag.origin,mp(t)),pt(rot(add(mag.rotation,magRot(t))),sub(cap,mag.origin)));
    for(const b of ['magazine','reload_magazine']){bake(a,b,'position',mp);bake(a,b,'rotation',magRot)}
    for(const [t,v] of [[0,1],[.63,0],[.70,1],[end,1]])key(a,'magazine','scale',t,[v,v,v],'step');
    const approach=[[0,hand],[.045,hand],[.16,add(hand,[-.55,-.35,.55])],[.27,add(seated,[-.8,.65,.45])],[.34,seated]];
    const wrist=[[0,zero],[.07,zero],[.23,[12,-4,8]],[.34,[22,-7,14]],[.54,[22,-7,14]],[.63,[27,-10,8]],[.72,[26,-9,10]],[.85,[22,-7,14]],[1.02,[22,-7,14]],...(empty?[[1.12,[18,-7,12]],[1.24,[-3,-8,4]],[1.32,[-3,-8,4]],[1.40,[1,-6,6]],[1.57,[.4,0,.3]],[1.65,zero]]:[[1.10,[14,-5,10]],[1.23,[-.45,.1,-.25]],[1.3,zero]])];
    const slide=t=>scalar([[0,lock],[1.25,lock],[1.30,1.20],[1.33,1.20],[1.39,0],[end,0]],t);
    const rear=[-4.19,10.45,8.1];
    const rearContact=t=>add(rear,[0,0,slide(t)]);
    const returnPath=empty?[[1.37,rearContact(1.37)],[1.44,[-4.65,8.5,8.55]],[1.54,add(hand,[-.35,-.2,.4])],[1.65,hand]]:[[1.04,seated],[1.13,add(hand,[-.65,-.6,.8])],[1.24,add(hand,[-.04,-.03,.05])],[1.3,hand]];
    const contactPath=[[1.04,seated],[1.12,[-4.14,5.7,8.5]],[1.20,[-4.30,9.0,9.1]],[1.24,rearContact(1.24)]];
    const target=t=>t<.34?curve(approach,t):t<=1.04?contact(t):empty?(t<1.24?curve(contactPath,t):t<=1.37?rearContact(t):curve(returnPath,t)):curve(returnPath,t);
    left(a,target,t=>curve(wrist,t));
    if(empty){
        for(const b of slideBones)bake(a,b,'position',t=>[0,0,slide(t)]);
        a.animators.effects={name:'Effects',type:'effect',keyframes:[{channel:'sound',data_points:[{effect:'slide_action',file:'E:/Download/slide_action.ogg'}],uuid:uid('slide_action'),time:1.25,color:-1,interpolation:'linear'}]};
    }
}
for(const a of s.animations)for(const tr of Object.values(a.animators))tr.keyframes.sort((x,y)=>x.time-y.time||x.channel.localeCompare(y.channel));
assert.deepEqual(s.elements,source.elements,'Geometry/UV frozen');assert.deepEqual(s.textures,source.textures,'Texture frozen');assert.deepEqual(s.display,source.display,'Display frozen');
for(const old of source.groups)assert.deepEqual(g(old.name),old,'Existing bones and anchors frozen');
// User's follow-up alignment request: reduce the static right forearm/gun-axis
// angle from 11.2067 to 3 degrees, without moving the cap or changing action keys.
g('right_hand_anchor').rotation=[-87,0,7.7351212345];
const result=compile(s,read(assets+'/geo/p9_01.geo.json'),read(assets+'/models/item/p9_01_in_hand.json'));
fs.writeFileSync(sourcePath,JSON.stringify(s,null,2)+'\n');
fs.writeFileSync(assets+'/geo/p9_01.geo.json',JSON.stringify(result.geometry,null,2)+'\n');
fs.writeFileSync(assets+'/animations/p9_01.animation.json',JSON.stringify(result.animations,null,2)+'\n');
// Local equip consumer reads the SAME exported draw/put-away tracks; no duplicated poses.
const equip={format_version:1,animations:{draw:result.animations.animations['animation.p9_01.draw'],put_away:result.animations.animations['animation.p9_01.put_away']}};
fs.writeFileSync(assets+'/animations/p9_01.equip.json',JSON.stringify(equip,null,2)+'\n');
console.log(JSON.stringify({clips:s.animations.map(a=>[a.name,a.length]),keys:s.animations.reduce((n,a)=>n+Object.values(a.animators).reduce((m,b)=>m+b.keyframes.length,0),0),geometryChanged:false,gameplayTimingChanged:false}));
