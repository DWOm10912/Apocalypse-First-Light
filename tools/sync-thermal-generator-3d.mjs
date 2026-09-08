import fs from 'node:fs';
import assert from 'node:assert/strict';
import zlib from 'node:zlib';
const base='src/main/resources/assets/apocalypse_firstlight/',id='thermal_generator_3d';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const s=read(`src/main/blockbench/${id}.bbmodel`),jp=base+`models/block/${id}.json`,j=read(jp),gp=base+`geo/${id}.geo.json`,g=read(gp),geo=g['minecraft:geometry'][0];
const near=(a,b)=>Math.abs(a-b)<1e-4,vec=(a,b)=>assert(a.length===b.length&&a.every((v,i)=>near(v,b[i])),`${a} != ${b}`);
const defs=new Map(s.groups.map(x=>[x.uuid,x])),es=new Map(s.elements.map(e=>[e.uuid,e])),groups=new Map(),ordered=[];
function walk(nodes,parent=null){for(const n of nodes){if(typeof n==='string'){if(es.get(n).export!==false)ordered.push(es.get(n));continue;}const d={...defs.get(n.uuid),...n,parent};if(d.export===false)continue;groups.set(d.name,d);walk(n.children,d.name);}}
walk(s.outliner);assert.equal(ordered.length,j.elements.length);assert.equal(geo.bones.length,groups.size);assert.equal(s.textures.length,1);assert(!s.animations?.length);
assert.equal(j.textures['0'],`apocalypse_firstlight:block/${id}`);
for(const slot of ['gui','ground','fixed','firstperson_righthand','thirdperson_righthand']){
 for(const key of ['rotation','translation','scale'])if(s.display?.[slot]?.[key])vec(s.display[slot][key],j.display[slot][key]??(key==='scale'?[1,1,1]:[0,0,0]));
}
vec(j.display.gui.scale,[.624,.624,.624]);
const variants=read(base+'blockstates/thermal_generator.json').variants;
for(const [facing,y]of Object.entries({north:0,east:90,south:180,west:270}))for(const lit of [false,true]){
 const v=variants[`facing=${facing},lit=${lit}`];assert.equal(v.model,`apocalypse_firstlight:block/${id}_render`);assert.equal(v.y??0,y);
}
assert.equal(Object.keys(variants).length,8);
assert.equal(read(base+'models/item/thermal_generator.json').parent,`apocalypse_firstlight:block/${id}_render`);
for(let i=0;i<ordered.length;i++){const c=ordered[i],e=j.elements[i];assert.equal(c.name,e.name);vec(c.from,e.from);vec(c.to,e.to);const rot=c.rotation??[0,0,0];
 if(rot.some(v=>v)){assert(e.rotation);vec(c.origin,e.rotation.origin);assert([-45,-22.5,0,22.5,45].includes(e.rotation.angle));vec(rot,['x','y','z'].map(a=>e.rotation.axis===a?e.rotation.angle:0));}
 for(const [name,f]of Object.entries(c.faces)){assert.equal(s.textures[f.texture]?.uuid??f.texture,s.textures[0].uuid);assert.equal(e.faces[name].texture,'#0');vec(f.uv.map(v=>v/16),e.faces[name].uv);assert(f.uv.every(v=>v>=0&&v<=256));}}
for(const bone of geo.bones){const d=groups.get(bone.name);assert(d);assert.equal(bone.parent??null,d.parent);vec(bone.pivot,[-d.origin[0],d.origin[1],d.origin[2]]);
 const cs=d.children.filter(n=>typeof n==='string').map(n=>es.get(n)).filter(c=>c.export!==false);assert.equal(cs.length,(bone.cubes??[]).length);
 cs.forEach((c,i)=>{const b=bone.cubes[i];vec(b.origin,[-c.to[0],c.from[1],c.from[2]]);vec(b.size,c.to.map((v,k)=>v-c.from[k]));if(c.rotation?.some(v=>v)){vec(b.pivot,[-c.origin[0],c.origin[1],c.origin[2]]);vec(b.rotation,[-c.rotation[0],-c.rotation[1],c.rotation[2]]);}for(const [side,f]of Object.entries(c.faces)){const[u,v,x,y]=f.uv,flip=['up','down'].includes(side);vec(b.uv[side].uv,flip?[x,y]:[u,v]);vec(b.uv[side].uv_size,flip?[u-x,v-y]:[x-u,y-v]);}});
}
const ready=['generator_rotor','status_green','status_yellow','status_red','heat_window_glass','exhaust_stack','fe_output_port','solid_fuel_display_anchor','solid_flame_fx_anchor','solid_ember_fx_anchor','liquid_render_anchor','liquid_surface_anchor','liquid_fx_anchor','fluid_input_anchor','fluid_output_anchor'];ready.forEach(n=>assert(groups.has(n)));
const anchorDefs={solid_fuel_display_anchor:['solid_combustion_chamber',[10.325,4.55,3.825]],solid_flame_fx_anchor:['solid_combustion_chamber',[10.325,4.8,3.825]],solid_ember_fx_anchor:['solid_combustion_chamber',[10.325,6,3.825]],liquid_render_anchor:['liquid_heat_window',[10.375,2.5,2.9]],liquid_surface_anchor:['liquid_heat_window',[10.375,3.32,2.9]],liquid_fx_anchor:['liquid_heat_window',[10.375,3.34,2.9]],fluid_input_anchor:['left_fluid_port',[16,8,8]],fluid_output_anchor:['right_fluid_port',[0,8,8]]};
for(const[n,[parent,pivot]]of Object.entries(anchorDefs)){const a=groups.get(n);assert.equal(a.parent,parent);vec(a.origin,pivot);assert.equal(a.children.length,0);assert(!geo.bones.find(b=>b.name===n).cubes?.length);}
assert(!ordered.some(c=>/static_.*flame|ember_bed|display_volume/.test(c.name)),'Source-only previews leaked into runtime');
assert(!geo.bones.some(b=>/source_only|flame_core|flame_mid|flame_outer/.test(b.name)));
const rightVent=groups.get('right_lower_vent').children.map(id=>es.get(id));assert.equal(rightVent.length,5);
vec(rightVent.find(c=>c.name==='lower_right_slits').from,[2.15,2.45,.55]);vec(rightVent.find(c=>c.name==='lower_right_slits').to,[6,3.4,.95]);
assert(!ordered.some(c=>c.name==='lower_left_slits'));
const overlap=(a,b)=>a.from.every((v,t)=>Math.min(a.to[t],b.to[t])-Math.max(v,b.from[t])>1e-5);
for(const [n,f,t]of [['solid_fuel_display_volume',[7.65,4.55,2.1],[13,6.3,5.55]],['liquid_display_volume',[7.35,2.5,1.45],[13.4,3.32,4.35]]]){
 const volume=s.elements.find(c=>c.name===n);assert(volume&&volume.export===false);vec(volume.from,f);vec(volume.to,t);
 assert(!ordered.some(c=>!c.rotation?.some(v=>v)&&overlap(volume,c)),n+' intersects static geometry');
}
const liquidGlass=ordered.find(c=>c.name==='liquid_recessed_heat_glass');vec(liquidGlass.from,[6.95,2.45,1.06]);vec(liquidGlass.to,[13.8,3.4,1.1]);
assert(ordered.find(c=>c.name==='liquid_tank_back').from[2]-liquidGlass.to[2]>=3.49);
const rotorCenter=[4.1,8.125,4.8];
vec(groups.get('generator_rotor').origin,rotorCenter);
const rotor=groups.get('generator_rotor').children.filter(n=>typeof n==='string').map(n=>es.get(n));
const rim=rotor.filter(c=>c.name==='octagonal_rotor_segment');assert.equal(rim.length,24);
assert.equal(rotor.filter(c=>c.name.startsWith('rotor_spoke_')).length,8);
vec(rotor.find(c=>c.name==='rotor_hub').from.map((v,i)=>(v+rotor.find(c=>c.name==='rotor_hub').to[i])/2),rotorCenter);
assert(near((Math.min(...rim.map(c=>c.from[0]))+Math.max(...rim.map(c=>c.to[0])))/2,rotorCenter[0]));
for(const a of rim){const ac=a.from.map((v,i)=>(v+a.to[i])/2);assert(rim.some(b=>{const bc=b.from.map((v,i)=>(v+b.to[i])/2);return near(ac[0],bc[0])&&near(ac[1]+bc[1],2*rotorCenter[1])&&near(ac[2]+bc[2],2*rotorCenter[2])&&a.from.every((v,i)=>near(a.to[i]-v,b.to[i]-b.from[i]));}),'Rim symmetry');}
const png=Buffer.from(s.textures[0].source.split(',')[1],'base64');
// Reuse the project's lossless PNG decoder; no image transformation here.
const code=fs.readFileSync('tools/sync-precision-fabrication-station.mjs','utf8');
// Some existing source PNGs have padding after IEND; stop at the PNG terminator.
const decoder=code.slice(code.indexOf('function pixels('),code.indexOf('const atlas=Buffer')).replace("if(tag==='IDAT')","if(tag==='IEND')break;if(tag==='IDAT')");
const pixels=new Function('zlib','assert',decoder+'return pixels;')(zlib,assert);
const p=pixels(png),back=pixels(fs.readFileSync(base+'textures/block/machine_back.png'));
assert.equal(p.width,256);assert.equal(p.height,256);
for(let y=0;y<10;y++)for(let x=0;x<10;x++){const a=((75+y)*256+43+x)*4,b=((11+y)*32+11+x)*4;assert(p.rgba.subarray(a,a+4).equals(back.rgba.subarray(b,b+4)));}
assert.equal(p.rgba[((48*256)+176)*4+3],0,'Glass centre must remain clear');
for(const [side,u]of [['left',96],['right',128]]){
 const reference=pixels(fs.readFileSync(base+'textures/block/machine_side_'+side+'_fluid.png'));
 for(let y=9;y<=22;y++)for(let x=9;x<=22;x++){const a=((96+y)*256+u+x)*4,b=(y*32+x)*4;assert(p.rgba.subarray(a,a+4).equals(reference.rgba.subarray(b,b+4)),side+' fluid core changed');}
 const c=ordered.find(c=>c.name===side+'_fluid_interface');vec(c.from,side==='left'?[15.9,6,6]:[0,6,6]);vec(c.to,side==='left'?[16,10,10]:[.1,10,10]);
}
let intersections=0,preservedLowerPanelIntersections=0;const axis=ordered.filter(c=>!c.rotation?.some(v=>v));
// The live source already had its two side backplates extended to Y1.1 before
// this edit. Preserve that user geometry; do not permit overlaps in new work.
for(let i=0;i<axis.length;i++)for(let k=i+1;k<axis.length;k++){
 const a=axis[i],b=axis[k];if(!overlap(a,b))continue;
 const panel=[a,b].find(c=>c.name==='side_backplate'&&near(c.from[1],1.1)&&near(c.to[1],2.65));
 const other=panel===a?b:a;
 if(panel&&['corner_column','sealed_base'].includes(other.name))preservedLowerPanelIntersections++;
 else intersections++;
}
assert.equal(intersections,0,'New or unexpected axis-aligned solid overlap');
assert([0,10].includes(preservedLowerPanelIntersections),'Unexpected lower-panel baseline');
fs.writeFileSync(base+`textures/block/${id}.png`,png);
j.render_type='minecraft:translucent';fs.writeFileSync(jp,JSON.stringify(j,null,2)+'\n');
// Keep the direct Blockbench export as an auditable intermediate. The live model
// uses Forge's built-in composite loader to write opaque depth before glass.
const glassNames=new Set(['recessed_smoked_glass','liquid_recessed_heat_glass']);
const opaque=j.elements.filter(e=>!glassNames.has(e.name)),glass=j.elements.filter(e=>glassNames.has(e.name));
assert.equal(glass.length,2);assert.equal(opaque.length+glass.length,j.elements.length);
const child=(elements,render_type)=>({textures:j.textures,display:j.display,render_type,elements});
const render={loader:'forge:composite',textures:j.textures,display:j.display,
 children:{opaque:child(opaque,'minecraft:solid'),glass:child(glass,'minecraft:translucent')},item_render_order:['opaque','glass']};
fs.writeFileSync(base+`models/block/${id}_render.json`,JSON.stringify(render,null,2)+'\n');
g.format_version='1.12.0';delete geo.item_display_transforms;fs.writeFileSync(gp,JSON.stringify(g,null,2)+'\n');
console.log(JSON.stringify({sourceCubes:s.elements.length,runtimeCubes:ordered.length,runtimeGroups:groups.size,sourceRuntimeSync:true,standardSocketPixels:true,standardFluidPortPixels:true,glassClear:true,newAxisAlignedOverlap:intersections,preservedLowerPanelIntersections,displayVolumesClear:true,anchors:Object.keys(anchorDefs),sourceOnlyPreviewExcluded:true,liveBlockReplaced:true,allFacingLitVariants:true,itemParentUpdated:true,guiScale:.624},null,2));
