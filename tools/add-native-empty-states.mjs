// Mechanical derived-clip generation. Never edits the approved base animations or rig.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {sourcePath,read,outputs,assets} from './export-native-gun.mjs';
const s=read(sourcePath), before=structuredClone(s);
const names=['slide','front_sight','rear_sight','sight_anchor'];
const base=n=>s.animations.find(a=>a.name==='animation.service_pistol.'+n);
const fire=base('fire'), reload=base('reload');
assert(fire && reload);
const clone=(a,n)=>{const c=structuredClone(a); c.name='animation.service_pistol.'+n;c.uuid=randomUUID();c.selected=false;
 for(const b of Object.values(c.animators))for(const k of b.keyframes||[])k.uuid=randomUUID();return c;};
const last=clone(fire,'fire_last_round');
for(const b of Object.values(last.animators))if(names.includes(b.name))
 for(const k of b.keyframes||[])if(k.channel==='position'&&k.time>=.04)k.data_points=[{x:'0',y:'0',z:'1.28'}];
const idle=clone(fire,'empty_idle');idle.loop='loop';idle.length=1;
idle.animators={};
const emptyReload=clone(reload,'reload_empty');
function key(time,z){return {channel:'position',data_points:[{x:'0',y:'0',z:String(z)}],uuid:randomUUID(),time,color:-1,interpolation:'linear'};}
for(const name of names){
 const g=s.groups.find(g=>g.name===name);assert(g);
 idle.animators[g.uuid]={name,type:'bone',keyframes:[key(0,1.28),key(1,1.28)]};
 const keys=[key(0,1.28),key(1.1,1.28)];
 // Last four ticks, sampled smoothstep: preserve mag-in at .95 and total 1.30.
 for(let i=1;i<=20;i++){const t=i/20;keys.push(key(Number((1.1+i*.01).toFixed(2)),1.28*(1-t*t*(3-2*t))));}
 emptyReload.animators[g.uuid]={name,type:'bone',keyframes:keys};
}
s.animations=s.animations.filter(a=>!['empty_idle','fire_last_round','reload_empty'].some(n=>a.name.endsWith('.'+n)));
s.animations.push(last,idle,emptyReload);
for(const field of Object.keys(before).filter(k=>k!=='animations'))assert.deepEqual(s[field],before[field]);
assert.deepEqual(base('fire'),before.animations.find(a=>a.name.endsWith('.fire')));
assert.deepEqual(base('reload'),before.animations.find(a=>a.name.endsWith('.reload')));
fs.writeFileSync(sourcePath,JSON.stringify(s)+'\n');
// Export only animation: geo, Display and texture remain byte-for-byte untouched.
for(const [p,value]of outputs())if(p.endsWith('service_pistol.animation.json'))fs.writeFileSync(p,JSON.stringify(value,null,2)+'\n');
console.log('Added 3 mechanical empty clips; base Fire/Reload and all rig fields preserved.');
