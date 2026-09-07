// Keep the 3D hand model separate from the supplied inventory illustration.
import fs from 'node:fs';
const base='src/main/resources/assets/apocalypse_firstlight/models/item/';
const sp='src/main/blockbench/br51_01.bbmodel';
const source=JSON.parse(fs.readFileSync(sp));
const main=JSON.parse(fs.readFileSync(base+'br51_01.json'));
const hand=main.loader==='forge:separate_transforms'?JSON.parse(fs.readFileSync(base+'br51_01_in_hand.json')):main;
// Vanilla hand pose and item-in-hand already supply the aiming pitch.
// Centre the authored thirdperson_hand [0,4.55,10.6] with no extra pitch.
for(const k of ['thirdperson_righthand','thirdperson_lefthand']){
  hand.display[k]={rotation:[0,0,0],translation:[0,-1.82,-4.24],scale:[.4,.4,.4]};
  source.display[k]=structuredClone(hand.display[k]);
}
const wrapper={loader:'forge:separate_transforms',gui_light:'front',textures:{particle:'apocalypse_firstlight:item/br51_01_inventory'},base:{parent:'apocalypse_firstlight:item/br51_01_in_hand'},perspectives:{gui:{parent:'minecraft:item/generated',loader:'forge:item_layers',textures:{layer0:'apocalypse_firstlight:item/br51_01_inventory'},display:{gui:{rotation:[0,0,0],translation:[0,0,0],scale:[.95,.95*574/1165,1]}}}}};
for(const [p,o]of [[base+'br51_01_in_hand.json',hand],[base+'br51_01.json',wrapper],[sp,source]])fs.writeFileSync(p,JSON.stringify(o,null,2));
