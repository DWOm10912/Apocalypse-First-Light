// Read-only V0.5.1 before/after projection and saved-source checks. No images.
import assert from 'node:assert/strict';
import {aflScene,analyze,chain,S} from './audit-player-arm-presentation.mjs';
import {presentationScale,syncReferences} from './native-arm-presentation.mjs';
import {read,sourcePath,assets,compile} from './export-native-gun.mjs';
const source=read(sourcePath),target=presentationScale();
assert.deepEqual(source,syncReferences(source),'Saved reference presentation must match framework');
const outputs=compile(source,read(assets+'/geo/service_pistol.geo.json'),read(assets+'/models/item/service_pistol_in_hand.json'));
assert.deepEqual(outputs.animations,read(assets+'/animations/service_pistol.animation.json'),'Animation unchanged');
// Existing geo-format/guard-rounding and Display rounding mismatches are not fixed here.
function capWidth(m,width) {
 const xs=[];for(const x of[-width/32,width/32])for(const z of[-.125,.125]) {
  const px=m[0]*x+m[2]*z+m[3],pz=m[8]*x+m[10]*z+m[11];
  assert(pz<-.05);xs.push(px/-pz/Math.tan(35*Math.PI/180)*540);
 }return Math.max(...xs)-Math.min(...xs);
}
const rows=[];
for(const state of ['ready','fire','reload']) {
 const t=state==='ready'?0:state==='fire'?.04:.5,scene=aflScene(state,t);
 const parent=source.display.firstperson_righthand.scale[0];
 const after={...scene,arm:(s,w)=>chain(scene.arm(s,w),S(target.map(n=>n/parent)))};
 for(const side of ['right','left'])for(const width of[3,4])for(const [version,s]of[['before',scene],['after',after]]) {
  const a=analyze(s,side,width);rows.push({state,t,side,width,version,axes:a.axis_scale,
   cap:a.positions.hand_cap,visible_length_px:a.whole_arm.visible_px,
   cap_horizontal_width_px:capWidth(s.arm(side,width),width),gun_occlusion:a.surface.gun_occlusion_ratio});
 }
}
for(let i=0;i<rows.length;i+=2)assert.deepEqual(rows[i].cap,rows[i+1].cap,'Contact drift');
console.log(JSON.stringify({frameworkScale:target,projection:'70deg vertical / 16:9 / 1080 high; width=cap cross-section horizontal span; no pixel/GPU claim',rows},null,2));
