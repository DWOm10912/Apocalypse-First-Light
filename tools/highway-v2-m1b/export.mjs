import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {convert,serializeCompact,meshCounts} from '../export-afl-mesh.mjs';

// This adapter consumes triangles from the SAME Java surface kernel. It does not invent another road.
export function exportFixtures(samples,root){
  const files=new Map(),metadata=[];
  const texture=fs.readFileSync(path.join(root,'tools/highway-v2-0/demo/placeholder.png'));
  for(const sample of samples){
    const id=sample.id,group=new Map();
    for(const t of sample.triangles){
      if(!group.has(t.material))group.set(t.material,{name:t.material,uuid:id+'-'+t.material,type:'mesh',origin:[0,0,0],rotation:[0,0,0],shading:'flat',vertices:{},faces:{}});
      const element=group.get(t.material),index=Object.keys(element.faces).length,keys=[],uv={};
      for(const [i,v] of [t.a,t.b,t.c].entries()){
        const key='v'+index+'_'+i;keys.push(key);element.vertices[key]=[v.x*16,v.y*16,v.z*16];uv[key]=[v.u*128,v.v*128];
      }
      element.faces['f'+index]={vertices:keys,uv,texture:0};
    }
    const elements=[...group.values()];
    const source={meta:{format_version:'4.10',model_format:'free'},name:id,resolution:{width:128,height:128},
      textures:[{uuid:id+'-texture',name:'placeholder.png',uv_width:128,uv_height:128,width:128,height:128,source:'data:image/png;base64,'+texture.toString('base64')}],
      elements,outliner:[{name:'road',uuid:id+'-root',origin:[0,0,0],children:elements.map(e=>e.uuid)}]};
    const geo={format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.'+id,texture_width:128,texture_height:128},bones:[{name:'road',pivot:[0,0,0]}]}]};
    const diagnostics={},mesh=convert(source,geo,{},id,2,diagnostics),text=serializeCompact(mesh);
    assert(text.length<4*1024*1024);
    files.set('src/main/blockbench/dev/highway_v2_m1b/'+id+'.bbmodel',serializeCompact(source));
    files.set('tools/highway-v2-m1b/results/'+id+'.aflmesh.json',text);
    files.set('tools/highway-v2-m1b/results/'+id+'.geo.json',serializeCompact(geo));
    metadata.push({id,length:32,section:sample.plan.from,...meshCounts(mesh),diagnostics});
  }
  files.set('tools/highway-v2-m1b/results/export-summary.json',JSON.stringify(metadata,null,2)+'\n');
  return files;
}