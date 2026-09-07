// Read-only asset regression against the one-shot authoring baseline.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {read,root,sourcePath,assets,outputs} from './export-native-gun.mjs';
const before=read(path.join(root,'build/p9-style-before',path.basename(sourcePath)));
const after=read(sourcePath);
for(const key of ['groups','animations','display','resolution'])assert.deepEqual(after[key],before[key],key+' must remain unchanged');
assert.deepEqual(after.elements.filter(e=>e.export===false),before.elements.filter(e=>e.export===false));
for(const e of before.elements){
    const actual=after.elements.find(a=>a.uuid===e.uuid);assert(actual,'Existing cube removed: '+e.name);
    if(e.name!=='slide_front_cap')for(const key of ['from','to','rotation','origin'])assert.deepEqual(actual[key],e[key],e.name+'/'+key);
}
for(const e of after.elements.filter(e=>e.export!==false)){
    assert(e.type==='cube'&&e.to.every((v,i)=>v>e.from[i]),'Positive cube dimensions: '+e.name);
    for(const f of Object.values(e.faces))if(f.texture!==null){
        assert.equal(f.texture,0,'Runtime cannot sample arm reference');
        assert(f.uv.every(n=>Number.isFinite(n)&&n>=0&&n<=128),'UV bounds');
        assert(f.uv[0]!==f.uv[2]&&f.uv[1]!==f.uv[3],'UV area');
    }
}
for(const [file,value] of outputs())assert.deepEqual(read(file),value,'Stale runtime '+file);
const png=fs.readFileSync(path.join(assets,'textures/item/p9_01.png'));
assert.equal(png.readUInt32BE(16),256);assert.equal(png.readUInt32BE(20),256);
assert.equal(after.textures[0].uuid,before.textures[0].uuid);
assert.equal(after.textures[0].uv_width,128);assert.equal(after.textures[0].uv_height,128);
const gui=read(path.join(assets,'models/item/p9_01.json'));
assert.equal(gui.perspectives.gui.textures.layer0,'apocalypse_firstlight:item/p9_01_inventory');
const icon=fs.readFileSync(path.join(assets,'textures/item/p9_01_inventory.png'));
assert.equal(icon.readUInt32BE(16),256);assert.equal(icon.readUInt32BE(20),256);
console.log('PASS: bones, anchors, all keys, Display, reference arms, original exterior envelope (except bore opening), UV bounds/binding, atlas, runtime exports, inventory route.');
console.log(`P9: ${before.elements.length} -> ${after.elements.length} source cubes; ${after.elements.filter(e=>e.export!==false).length} runtime cubes.`);
