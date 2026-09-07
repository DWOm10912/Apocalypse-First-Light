// Release-time local-basis motion, shared by the one-shot author and scoped refinement.
import {I,chain,R,T,pt,sample} from './check-native-gun-aimline.mjs';
import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
import {sourcePath,read} from './export-native-gun.mjs';
const zero=[0,0,0],add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]),scale=(v,k)=>v.map(n=>n*k);
const rot=r=>chain(R(2,r[2]),R(1,r[1]),R(0,r[0]));
function inverse(m){const out=I();for(let i=0;i<3;i++)for(let j=0;j<3;j++)out[4*i+j]=m[4*j+i];for(let i=0;i<3;i++)out[4*i+3]=-out[4*i]*m[3]-out[4*i+1]*m[7]-out[4*i+2]*m[11];return out}
const euler=m=>[Math.atan2(m[9],m[10]),Math.asin(Math.max(-1,Math.min(1,-m[8]))),Math.atan2(m[4],m[0])].map(n=>n*180/Math.PI);
export const releaseTime=.48;
// Fully clear before independent spin; include wrist and exit-axis velocity.
function attachedPosition(handling,mag,t){
    const axis=pt(rot(mag.rotation),[0,-1,0]),q=(t-.4)/(releaseTime-.4);
    const d=6.3*(-2*q**3+3*q*q)+16*(releaseTime-.4)*(q**3-q*q);
    return pt(handling(t),add(mag.origin,scale(axis,d)));
}
export function releaseMotion(handling,mag){
    const p=attachedPosition(handling,mag,releaseTime),dt=.00001;
    return {p,v:scale(sub(p,attachedPosition(handling,mag,releaseTime-dt)),1/dt)};
}
export function oldMagazinePose(handling,mag,t){
    if(t<=.4||t>=.85)return {p:zero,r:zero};
    if(t<=releaseTime)return {p:sub(pt(inverse(handling(t)),attachedPosition(handling,mag,t)),mag.origin),r:zero};
    const u=t-releaseTime,{p,v}=releaseMotion(handling,mag);
    // Inherit real release velocity, not an independent lateral launch speed.
    // Acceleration stays in common rig space; no fixed world-axis shortcut.
    const position=add(add(p,scale(v,u)),[0,-22*u*u,0]);
    const detached=chain(inverse(handling(t)),handling(releaseTime));
    const orientation=chain(detached,rot(add(mag.rotation,[48*u,-70*u,-150*u])));
    return {p:sub(pt(inverse(handling(t)),position),mag.origin),r:sub(euler(orientation),mag.rotation)};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
    if(!process.argv.includes('--write'))throw Error('Pass --write for old-magazine tracks only');
    const s=read(sourcePath),a=s.animations.find(a=>a.name==='animation.p9_01.reload_empty');
    const g=n=>s.groups.find(g=>g.name===n),driver=g('p9_handling'),keys=a.animators[driver.uuid].keyframes;
    const handling=t=>chain(T(sample(keys,'position',t)),T(driver.origin),rot(sample(keys,'rotation',t)),T(scale(driver.origin,-1)));
    for(const k of a.animators[g('empty_old_magazine').uuid].keyframes)if(k.channel!=='scale'){
        const p=oldMagazinePose(handling,g('magazine'),k.time),v=k.channel==='position'?p.p:p.r;
        for(let i=0;i<3;i++)k.data_points[0][['x','y','z'][i]]=String(+v[i].toFixed(8));
    }
    fs.writeFileSync(sourcePath,JSON.stringify(s,null,2)+'\n');
}
