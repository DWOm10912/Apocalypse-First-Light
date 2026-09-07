// Numerical/resource checks only; manual gameplay/visual verification is separate.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {root,sourcePath,assets,read,outputs} from './export-native-gun.mjs';
import {scene,sample,pt,chain,R,T} from './check-native-gun-aimline.mjs';
import {releaseTime,releaseMotion} from './p9-empty-flight.mjs';
const baseline=path.join(root,'build/p9-empty-special-before');
const s=read(sourcePath),old=read(path.join(baseline,path.basename(sourcePath)));
const g=n=>s.groups.find(g=>g.name===n),a=s.animations.find(a=>a.name.endsWith('.reload_empty'));
const keys=n=>a.animators[g(n).uuid].keyframes;
const sub=(a,b)=>a.map((n,i)=>n-b[i]),distance=(a,b)=>Math.hypot(...sub(a,b));
for(const before of old.animations)if(before.name!==a.name)assert.deepEqual(s.animations.find(a=>a.name===before.name),before,'Other animation modified');
for(const before of old.groups)assert.deepEqual(g(before.name),before,'Existing bone modified');
for(const before of old.elements)assert.deepEqual(s.elements.find(e=>e.uuid===before.uuid),before,'Existing geometry modified');
assert.equal(s.groups.length,old.groups.length+1);
assert.equal(s.elements.length,old.elements.length+11);
for(const cube of s.elements.filter(e=>e.name.startsWith('empty_old_'))){
    const expected=structuredClone(old.elements.find(e=>e.name===cube.name.slice('empty_old_'.length)));
    expected.name=cube.name;expected.uuid=cube.uuid;assert.deepEqual(cube,expected,'Temporary magazine not an exact material/geometry clone');
}
for(const field of ['display','textures','resolution'])assert.deepEqual(s[field],old[field]);
assert.equal(a.length,1.65);assert.equal(a.loop,'once');
assert.deepEqual(read(path.join(root,'src/main/resources/data/apocalypse_firstlight/native_guns/p9_01.json')),read(path.join(baseline,'gameplay.json')));
assert.equal(read(path.join(baseline,'gameplay.json')).reload.empty_seconds,a.length);
assert.equal(a.animators.effects.keyframes[0].time,1.25);
assert.equal(a.animators.effects.keyframes[0].data_points[0].effect,'slide_action');
const priorKeys=new Set(old.animations.find(b=>b.name===a.name).animators ? Object.values(old.animations.find(b=>b.name===a.name).animators).flatMap(t=>(t.keyframes??[]).map(k=>k.uuid)):[]);
assert(Object.values(a.animators).every(t=>t.keyframes.every(k=>!priorKeys.has(k.uuid))),'Old empty keys reused');
const baseplate=s.elements.find(e=>e.name==='magazine_baseplate');
const cap=[-3.62,baseplate.to[1]-.05,(baseplate.from[2]+baseplate.to[2])/2].map(n=>n/16);
let maxContact=0,maxDetached=0;
const base=scene(s).matrix('gun'),driver=g('p9_handling');
const handling=t=>{const r=sample(keys('p9_handling'),'rotation',t);return chain(T(sample(keys('p9_handling'),'position',t)),T(driver.origin),R(2,r[2]),R(1,r[1]),R(0,r[0]),T(driver.origin.map(v=>-v)))};
const flight=releaseMotion(handling,g('magazine'));
assert(flight.v[0]<-10,'Actual release velocity must carry the old magazine left');
const handAt=t=>scene(s,'firstperson_righthand','reload_empty',t).point('right_hand_anchor');
const speed=t=>(handAt(t+.001)[0]-handAt(t-.001)[0])/.002;
let peak=0;for(let n=401;n<560;n++)peak=Math.max(peak,-speed(n/1000));
assert(-speed(releaseTime)>peak*.9,'Release must coincide with near-peak leftward wrist speed');
assert(sample(keys('p9_handling'),'rotation',releaseTime)[2]<-50,'Side turn must expose the magwell');
const mag=g('magazine'),axis=pt(chain(R(2,mag.rotation[2]),R(1,mag.rotation[1]),R(0,mag.rotation[0])),[0,-1,0]);
for(let n=0;n<=1650;n++){
    const t=n/1000,p=scene(s,'firstperson_righthand','reload_empty',t);
    if(t>=.61&&t<=1.04){
        maxContact=Math.max(maxContact,distance(p.point('left_hand_anchor'),pt(p.matrix('magazine'),cap)));
        assert(maxContact<.0002,'New magazine not hand coupled');
    }
    if(t>=releaseTime&&t<.84){
        const u=t-releaseTime;
        const expected=pt(base,flight.p.map((v,i)=>(v+flight.v[i]*u+[0,-22*u*u,0][i])/16));
        maxDetached=Math.max(maxDetached,distance(p.point('empty_old_magazine'),expected));
        assert(maxDetached<.0003,'Old magazine still follows current wrist instead of release basis');
    }
}
for(const [time,formal,temp] of [[0,1,0],[.399,1,0],[.4,0,1],[.6,0,1],[.61,1,1],[.839,1,1],[.84,1,0],[.95,1,0],[1.65,1,0]]){
    assert.equal(sample(keys('magazine'),'scale',time)[0],formal,'Formal visibility '+time);
    assert.equal(sample(keys('empty_old_magazine'),'scale',time)[0],temp,'Temporary visibility '+time);
}
assert.deepEqual(sample(keys('magazine'),'position',.95),[0,0,0]);
assert.deepEqual(sample(keys('empty_old_magazine'),'position',1.65),[0,0,0]);
assert.deepEqual(sample(keys('empty_old_magazine'),'rotation',1.65),[0,0,0]);
for(const name of ['gun','muzzle_anchor','sight_anchor','right_hand_anchor','left_hand_anchor','magazine'])
    assert(distance(scene(s,'firstperson_righthand','reload_empty',1.65).point(name),scene(s).point(name))<1e-7,'Ready endpoint '+name);
const hand=g('left_hand_anchor'),motion=g('left_hand_motion');
let slideError=0;
for(let n=1040;n<=1650;n++){
    const t=n/1000,r=sample(keys('left_hand_motion'),'rotation',t),p=sample(keys('left_hand_motion'),'position',t);
    const v=pt(chain(R(2,r[2]),R(1,r[1]),R(0,r[0])),sub(hand.origin,motion.origin)).map((v,i)=>v+motion.origin[i]+p[i]);
    assert(v[0]<-2.98,'Left hand crosses gun center plane');
    if(t>=1.23&&t<=1.35){
        slideError=Math.max(slideError,distance(v,[-4.19,10.45,8.1+sample(keys('slide'),'position',t)[2]]));
        assert(slideError<.0001,'Slide-local contact');
    }
}
const currentRuntime=read(assets+'/animations/p9_01.animation.json'),previousRuntime=read(path.join(baseline,'p9_01.animation.json'));
for(const [name,value] of Object.entries(previousRuntime.animations))if(name!==a.name)assert.deepEqual(currentRuntime.animations[name],value);
assert(!currentRuntime.animations[a.name].sound_effects,'Server sounds must not double-play');
assert(fs.readFileSync(assets+'/animations/p9_01.equip.json').equals(fs.readFileSync(path.join(baseline,'p9_01.equip.json'))));
for(const [file,value] of outputs())assert.deepEqual(read(file),value,'Stale export '+file);
const renderer=fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/weapon/client/P901Renderer.java'),'utf8');
assert(renderer.includes('bone.getName().equals("empty_old_magazine")')&&renderer.includes('pistol.isEmptyReloadPlaying()'),'Temporary render gate missing');
console.log(JSON.stringify({pass:true,clip:a.name,length:a.length,maxContact,maxDetached,slideError,releaseTime,releaseVelocity:flight.v,wristPeakRatio:-speed(releaseTime)/peak,overlapSeconds:.23,newKeys:Object.values(a.animators).reduce((n,t)=>n+t.keyframes.length,0),otherClips:'UNCHANGED',gameplay:'UNCHANGED',runtimeVisual:'NOT_TESTED'},null,2));
