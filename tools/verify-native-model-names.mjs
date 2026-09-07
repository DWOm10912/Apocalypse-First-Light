import fs from 'node:fs';
import assert from 'node:assert/strict';
const base='src/main/resources/assets/apocalypse_firstlight/';
const sound=JSON.parse(fs.readFileSync(base+'sounds.json'));
for(const id of ['p9_01','br51_01']){
 for(const p of [`geo/${id}.geo.json`,`animations/${id}.animation.json`,`models/item/${id}.json`,`textures/item/${id}.png`])assert(fs.existsSync(base+p),p);
 const a=JSON.parse(fs.readFileSync(base+`animations/${id}.animation.json`));
 for(const clip of Object.values(a.animations))for(const s of Object.values(clip.sound_effects||{}))assert(sound[s.effect.replace('apocalypse_firstlight:','')],s.effect);
 const b=JSON.parse(fs.readFileSync('src/main/blockbench/'+(id==='p9_01'?'p9_01_v03_8_fire_slide_cleanup':id)+'.bbmodel'));
 assert(!JSON.stringify(b.animations).includes('service_pistol'));
 if(id==='br51_01'){
  assert.equal(Object.keys(a.animations).length,8);
  const bones=JSON.parse(fs.readFileSync(base+'geo/br51_01.geo.json'))['minecraft:geometry'][0].bones;
  for(const n of ['mag_extended_1','mag_extended_2','mag_extended_3','sight','grip_default'])assert(!bones.some(b=>b.name===n));
  assert(bones.some(b=>b.name==='empty_old_mag_standard'));
 }
 for(const lang of ['zh_cn','en_us'])assert(JSON.parse(fs.readFileSync(base+`lang/${lang}.json`))[`item.apocalypse_firstlight.${id}`]);
}
for(const [key,event]of Object.entries(sound))for(const value of event.sounds||[]){const s=typeof value==='string'?value:value.name;if(typeof value==='object'&&value.type==='event')continue;if(s.startsWith('apocalypse_firstlight:'))assert(fs.existsSync(base+'sounds/'+s.split(':')[1]+'.ogg'),key+'/'+s);}
console.log('Native names/resources/sounds/8 rifle clips/white-gun selection: PASS');
