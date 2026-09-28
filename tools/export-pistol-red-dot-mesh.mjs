// Export the saved editable asset, never regenerate its geometry or textures.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {read,convert,serialize,serializeCompact,meshCounts} from './export-afl-mesh.mjs';
const source=read('src/main/blockbench/pistol_red_dot_mesh.bbmodel');
const root='src/main/resources/assets/apocalypse_firstlight';
const groups=new Map(source.groups.map(g=>[g.uuid,g])),bones=[];
function walk(nodes,parent) {
    for(const n of nodes) if(typeof n!=='string') {
        const g=groups.get(n.uuid)??n;
        if(g.export===false) continue;
        bones.push({name:g.name,...(parent?{parent}:{}),pivot:g.origin.map((x,i)=>i===0?-x||0:x),
            ...(g.rotation?.some(x=>x)?{rotation:g.rotation.map((x,i)=>i<2?-x||0:x)}:{})});
        walk(n.children??[],g.name);
    }
}
walk(source.outliner);
const geo={format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.pistol_red_dot',
    texture_width:source.resolution.width,texture_height:source.resolution.height,
    visible_bounds_width:1,visible_bounds_height:1,visible_bounds_offset:[0,0,0]},bones}]};
const mesh=convert(source,geo,{},'pistol_red_dot_mesh',2,null,read('tools/pistol-red-dot.layers.json'));
assert.equal(mesh.parts.filter(p=>p.render_layer==='translucent').length,1);
const previous=read(`${root}/models/item/pistol_red_dot.json`);
const item={parent:'builtin/entity',gui_light:'side',textures:{particle:'apocalypse_firstlight:item/pistol_red_dot'},display:previous.display};
const output=[[`${root}/geo/pistol_red_dot.geo.json`,Buffer.from(serialize(geo))],
    [`${root}/meshes/pistol_red_dot.aflmesh.json`,Buffer.from(serializeCompact(mesh))],
    [`${root}/models/item/pistol_red_dot.json`,Buffer.from(serialize(item))]];
for(const suffix of ['', '_s','_n']) output.push([`${root}/textures/item/pistol_red_dot${suffix}.png`,
    fs.readFileSync(`src/main/blockbench/textures/pistol_red_dot${suffix}.png`)]);
for(const [file,data] of output) {
    if(process.argv.includes('--check')) assert.ok(fs.readFileSync(file).equals(data),`stale ${file}`);
    else fs.writeFileSync(file,data);
}
console.log(JSON.stringify({checked:process.argv.includes('--check'),...meshCounts(mesh),parts:mesh.parts.map(p=>[p.name,p.render_layer??'cutout'])}));
