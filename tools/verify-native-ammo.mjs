import fs from 'node:fs';
import assert from 'node:assert/strict';
const r='src/main/resources/assets/apocalypse_firstlight/';
for(const id of ['9x19mm_round','762x51mm_round','9x19mm_casing','762x51mm_casing']){
 const m=JSON.parse(fs.readFileSync(r+'models/item/'+id+'.json'));
 const b=JSON.parse(fs.readFileSync('src/main/blockbench/'+id+'.bbmodel'));
 assert.equal(m.elements.length,b.elements.length);
 assert.equal(m.textures['0'],'apocalypse_firstlight:item/'+id);
 assert.ok(fs.existsSync(r+'textures/item/'+id+'.png'));
 for(const e of m.elements){
  assert.ok(e.from.every((v,i)=>Number.isFinite(v)&&v<e.to[i]&&v>=-16&&e.to[i]<=32));
  if(e.rotation)assert.ok([-45,-22.5,0,22.5,45].includes(e.rotation.angle));
  for(const f of Object.values(e.faces))assert.ok(f.uv.every(v=>Number.isFinite(v)&&v>=0&&v<=16));
 }
 for(const lang of ['zh_cn','en_us'])assert.ok(JSON.parse(fs.readFileSync(r+'lang/'+lang+'.json'))['item.apocalypse_firstlight.'+id]);
 assert.ok(Buffer.from(b.textures[0].source.split(',')[1],'base64').equals(fs.readFileSync(r+'textures/item/'+id+'.png')));
 console.log(id+': model, source, texture, UV, localization OK');
}
