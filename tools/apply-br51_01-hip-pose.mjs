// One per-gun HIP baseline, in Minecraft item-display units/degrees.
// Shared by every clip; does not edit geometry, hand contacts or animation.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const sourcePath='src/main/blockbench/br51_01.bbmodel';
const base='src/main/resources/assets/apocalypse_firstlight/models/item/';
const itemPath=JSON.parse(fs.readFileSync(base+'br51_01.json')).loader==='forge:separate_transforms'?base+'br51_01_in_hand.json':base+'br51_01.json';
export const BR51_01_HIP={translation:[3.8,-7.2,-11.5],rotation:[0,4,0],scale:[.45,.45,.45]};
const source=JSON.parse(fs.readFileSync(sourcePath));
const item=JSON.parse(fs.readFileSync(itemPath));
const original=structuredClone(source);
for(const key of ['firstperson_righthand','firstperson_lefthand']){
  item.display[key]=structuredClone(BR51_01_HIP);
  source.display[key]=structuredClone(BR51_01_HIP);
}
const check=structuredClone(source);check.display=original.display;
assert.deepEqual(check,original,'Only Display may change');
fs.writeFileSync(sourcePath,JSON.stringify(source,null,2));
fs.writeFileSync(itemPath,JSON.stringify(item,null,2));
console.log('BR51_01 HIP',JSON.stringify(BR51_01_HIP),'source geometry/anchors/animations unchanged');
