import fs from 'node:fs';
import assert from 'node:assert/strict';
const base='src/main/resources/assets/apocalypse_firstlight/';
const source=JSON.parse(fs.readFileSync('src/main/blockbench/thermal_generator_3d.bbmodel'));
const model=JSON.parse(fs.readFileSync(base+'models/block/thermal_generator_3d.json'));
const defs=new Map(source.groups.map(g=>[g.uuid,g])), elements=new Map(source.elements.map(e=>[e.uuid,e]));
const ordered=[], groups=new Map();
function walk(nodes,owner='') { for(const n of nodes) {
  if(typeof n==='string') {const e=elements.get(n);if(e.export!==false)ordered.push({e,owner});}
  else {const g={...defs.get(n.uuid),...n};if(g.export===false)continue;groups.set(g.name,g);walk(n.children,g.name);}
}}
walk(source.outliner);assert.equal(ordered.length,model.elements.length);
const parts={rotor:[],status_green:[],status_yellow:[],status_red:[]}, opaque=[],glass=[];
ordered.forEach(({e,owner},i)=>{
 const cube=model.elements[i];assert.equal(cube.name,e.name);
 if(owner==='generator_rotor')parts.rotor.push(cube);
 else if(owner.startsWith('status_')&&e.name==='lamp_lens')parts[owner].push(cube);
 else if(['recessed_smoked_glass','liquid_recessed_heat_glass'].includes(e.name))glass.push(cube);
 else opaque.push(cube);
});
const child=(elements,render_type)=>({textures:model.textures,render_type,elements});
for(const[name,cubes]of Object.entries(parts)) {
 if(name.startsWith('status_'))for(const cube of cubes)for(const face of Object.values(cube.faces))face.tintindex=0;
 assert(cubes.length>0);fs.writeFileSync(base+`models/block/thermal_generator_dynamic_${name}.json`,JSON.stringify(child(cubes,'minecraft:solid'),null,2)+'\n');
}
fs.writeFileSync(base+'models/block/thermal_generator_3d_world.json',JSON.stringify({loader:'forge:composite',textures:model.textures,
 children:{opaque:child(opaque,'minecraft:solid')}},null,2)+'\n');
fs.writeFileSync(base+'models/block/thermal_generator_dynamic_glass.json',JSON.stringify(child(glass,'minecraft:translucent'),null,2)+'\n');
const constants={ROTOR_PIVOT:groups.get('generator_rotor').origin,SOLID_ANCHOR:groups.get('solid_fuel_display_anchor').origin,
 LIQUID_ANCHOR:groups.get('liquid_render_anchor').origin,LIQUID_SURFACE:groups.get('liquid_surface_anchor').origin};
for(const [key,name]of [['SOLID','solid_fuel_display_volume'],['LIQUID','liquid_display_volume']]) {
 const e=source.elements.find(e=>e.name===name);assert(e.export===false);constants[key+'_MIN']=e.from;constants[key+'_MAX']=e.to;
}
fs.writeFileSync('src/main/java/com/antaurora/apofirstlight/client/ThermalGeneratorRenderGeometry.java',
 'package com.antaurora.apofirstlight.client;\n\n// Generated from the approved bbmodel by tools/export-thermal-generator-dynamic.mjs.\npublic final class ThermalGeneratorRenderGeometry {\n    private ThermalGeneratorRenderGeometry() {}\n'+
 Object.entries(constants).map(([k,v])=>`    public static final double[] ${k} = {${v.map(n=>n/16).join(', ')}};`).join('\n')+'\n}\n');
console.log(JSON.stringify({opaque:opaque.length,glass:glass.length,parts:Object.fromEntries(Object.entries(parts).map(([k,v])=>[k,v.length])),sourceGeometryUnchanged:true}));
const fx = Object.fromEntries(['solid_flame_fx_anchor','solid_ember_fx_anchor','liquid_fx_anchor']
 .map(name => [name.toUpperCase(), groups.get(name).origin]));
const cap = source.elements.find(e => e.name === 'stack_cap');
fx.EXHAUST = [(cap.from[0]+cap.to[0])/2, cap.to[1]+.08, (cap.from[2]+cap.to[2])/2];
fs.writeFileSync('src/main/java/com/antaurora/apofirstlight/energy/ThermalParticleAnchors.java',
 'package com.antaurora.apofirstlight.energy;\n\n// Generated from approved source anchors and stack_cap; no source geometry changes.\npublic final class ThermalParticleAnchors {\n    private ThermalParticleAnchors() {}\n'+
 Object.entries(fx).map(([k,v])=>`    public static final double[] ${k} = {${v.map(n=>n/16).join(', ')}};`).join('\n')+'\n}\n');
