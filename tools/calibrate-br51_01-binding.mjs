// Calibrate static adapters only. Never rewrite the imported motion tracks.
import fs from 'node:fs';
const root='src/main/resources/assets/apocalypse_firstlight/';
const sourcePath='src/main/blockbench/br51_01.bbmodel';
const b=JSON.parse(fs.readFileSync(sourcePath));
const g=JSON.parse(fs.readFileSync(root+'geo/br51_01.geo.json'));
const itemPath=JSON.parse(fs.readFileSync(root+'models/item/br51_01.json')).loader==='forge:separate_transforms'?root+'models/item/br51_01_in_hand.json':root+'models/item/br51_01.json';
const item=JSON.parse(fs.readFileSync(itemPath));
const originalAnimation=JSON.stringify(b.animations);
const bones=g['minecraft:geometry'][0].bones;
for(const side of ['right','left']) {
  const a=b.groups.find(x=>x.name===side+'_hand_anchor');
  const runtime=bones.find(x=>x.name===a.name);
  a.origin=[side==='right'?-6:6,20,4]; a.rotation=[0,0,0];
  runtime.pivot=[-a.origin[0],20,4]; runtime.rotation=[0,0,0];
  for(const slim of [false,true]) {
    const name=side+'_arm_reference'+(slim?'_slim':'');
    const ref=b.groups.find(x=>x.name===name);
    const cube=b.elements.find(x=>x.name===name+'_cube');
    // Shared runtime presentation divided by the unchanged BR51_01 Display factor.
    const width=(slim?3:4)*.62/.45, length=12*.78/.45, depth=4*.62/.45;
    ref.origin=[...a.origin]; ref.rotation=[0,0,0]; ref.export=false;
    cube.origin=[...a.origin]; cube.from=[a.origin[0]-width/2,20-length,4-depth/2];
    cube.to=[a.origin[0]+width/2,20,4+depth/2]; cube.export=false;
  }
}
// First-person pose is applied below by the dedicated HIP configuration.
// Third-person grip: bring the authored hand marker to the item origin, then
// undo Vanilla's item-in-hand pitch. Independent of the first-person eye.
for(const name of ['thirdperson_righthand','thirdperson_lefthand']) {
  item.display[name]={rotation:[0,0,0],translation:[0,-4.55*.4,-10.6*.4],scale:[.4,.4,.4]};
}
b.display=structuredClone(item.display);
if(JSON.stringify(b.animations)!==originalAnimation)throw Error('Animation changed');
for(const [p,obj]of [[sourcePath,b],[root+'geo/br51_01.geo.json',g],[itemPath,item]])fs.writeFileSync(p,JSON.stringify(obj,null,2));
console.log('Static bindings Y20/R0; FP',item.display.firstperson_righthand,'; animation channels unchanged');
await import('./apply-br51_01-hip-pose.mjs');
