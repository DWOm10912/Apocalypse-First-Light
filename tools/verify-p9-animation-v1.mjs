// Asset/math regression. This is not a GPU, sound or subjective style pass.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {root,sourcePath,assets,read,outputs} from './export-native-gun.mjs';
import {scene,pt,sample,chain,R} from './check-native-gun-aimline.mjs';
const s=read(sourcePath),before=read(path.join(root,'build/p9-animation-v1-before',path.basename(sourcePath)));
const bone=n=>s.groups.find(g=>g.name===n),vsub=(a,b)=>a.map((n,i)=>n-b[i]);
const distance=(a,b)=>Math.hypot(...vsub(a,b));
assert.deepEqual(s.elements,before.elements,'No geometry, UV or reference edits');
assert.deepEqual(s.textures,before.textures);assert.deepEqual(s.display,before.display);
for(const g of before.groups){
    const expected=structuredClone(g);
    if(g.name==='right_hand_anchor')expected.rotation=[-87,0,7.7351212345];
    assert.deepEqual(bone(g.name),expected,'Only approved right wrist direction may change: '+g.name);
}
assert.equal(s.groups.length,before.groups.length+1);
const lengths={static_idle:2.4,draw:.15,put_away:.15,fire:.14,fire_last_round:.14,empty_idle:1,reload:1.3,reload_empty:1.65};
for(const [name,length] of Object.entries(lengths)){
    const a=s.animations.find(a=>a.name==='animation.p9_01.'+name);assert(a,name);assert.equal(a.length,length);
    assert.equal(a.loop,['static_idle','empty_idle'].includes(name)?'loop':'once');
    for(const tr of Object.values(a.animators))if(tr.type==='bone')for(const k of tr.keyframes){
        assert(k.time>=0&&k.time<=length);
        assert(['linear','step'].includes(k.interpolation));
        assert(['x','y','z'].every(axis=>Number.isFinite(+k.data_points[0][axis])));
    }
}
for(const name of ['fire','reload','reload_empty','draw'])for(const b of ['muzzle_anchor','right_hand_anchor','left_hand_anchor'])
    assert(distance(scene(s,'firstperson_righthand',name,lengths[name]).point(b),scene(s).point(b))<1e-7,name+' ready endpoint '+b);
const mag=bone('magazine'),baseplate=s.elements.find(e=>e.name==='magazine_baseplate');
assert(baseplate,'Magazine contact geometry');
const cap=[-3.62,baseplate.to[1]-.05,(baseplate.from[2]+baseplate.to[2])/2].map(n=>n/16);
let maxContactError=0;
for(const name of ['reload','reload_empty']){
    for(let tick=340;tick<=1040;tick++){
        const posed=scene(s,'firstperson_righthand',name,tick/1000);
        const error=distance(posed.point('left_hand_anchor'),pt(posed.matrix('magazine'),cap));
        maxContactError=Math.max(error,maxContactError);
        assert(error<.001,'Hand detached from magazine '+name+'@'+tick+': '+error);
    }
    const a=s.animations.find(a=>a.name.endsWith('.'+name));
    const keys=a.animators[mag.uuid].keyframes;
    assert.deepEqual(sample(keys,'position',.4),[0,0,0]);
    assert.deepEqual(sample(keys,'position',.95),[0,0,0]);
    assert.equal(sample(keys,'scale',.62)[0],1);
    assert.equal(sample(keys,'scale',.65)[0],0);
    assert.equal(sample(keys,'scale',.71)[0],1);
}
const empty=s.animations.find(a=>a.name.endsWith('.reload_empty'));
const hand=bone('left_hand_anchor'),motion=bone('left_hand_motion');
let nearestPlane=-Infinity;
for(let tick=1040;tick<=1650;tick++){
    const t=tick/1000,keys=empty.animators[motion.uuid].keyframes;
    const rotation=sample(keys,'rotation',t),position=sample(keys,'position',t);
    const rotated=pt(chain(R(2,rotation[2]),R(1,rotation[1]),R(0,rotation[0])),vsub(hand.origin,motion.origin));
    const target=rotated.map((n,i)=>n+motion.origin[i]+position[i]);
    nearestPlane=Math.max(nearestPlane,target[0]);
    assert(target[0]<-2.98,'Crossed gun longitudinal center plane');
    if(t>=1.24&&t<=1.37){
        const slideZ=sample(empty.animators[bone('slide').uuid].keyframes,'position',t)[2];
        // Between baked keys, independently interpolated Euler/position channels
        // have a tiny second-order error; keep it below 0.0001 model units.
        assert(distance(target,[-4.19,10.45,8.1+slideZ])<.0001,'Slide contact not local-axis coupled');
    }
}
assert.equal(empty.animators.effects.keyframes[0].time,1.25);
for(const [file,value] of outputs())assert.deepEqual(read(file),value,'Stale export '+file);
const oldKeys=new Set(before.animations.flatMap(a=>Object.values(a.animators).flatMap(t=>(t.keyframes??[]).map(k=>k.uuid))));
assert(s.animations.every(a=>Object.values(a.animators).every(t=>t.keyframes.every(k=>!oldKeys.has(k.uuid)))),'Old animation keys reused');
const icon=fs.readFileSync(assets+'/textures/item/p9_01_inventory.png'),oldIcon=fs.readFileSync(path.join(root,'build/p9-animation-v1-before/p9_01_inventory.png'));
assert(icon.subarray(16,24).equals(oldIcon.subarray(16,24)),'Inventory canvas dimensions changed');
console.log(JSON.stringify({pass:true,clips:s.animations.length,newKeys:s.animations.reduce((n,a)=>n+Object.values(a.animators).reduce((m,t)=>m+t.keyframes.length,0),0),maxContactError,nearestPlane,geometry:'UNCHANGED',anchors:'RIGHT_WRIST_ROTATION_ONLY',timing:'UNCHANGED',export:'EXACT',runtime:'NOT_TESTED'},null,2));
