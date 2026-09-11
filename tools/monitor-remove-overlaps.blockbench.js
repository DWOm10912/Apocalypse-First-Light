(() => {
 if(Project.name!=='modern_lcd_monitor'||Cube.all.length!==100)throw Error('Expected monitor');
 const original=Cube.all.slice();Undo.initEdit({elements:original,outliner:true});
 const eps=1e-7,accepted=[];let overlaps=0;
 function subtract(a,b){const lo=a.f.map((v,i)=>Math.max(v,b.f[i])),hi=a.t.map((v,i)=>Math.min(v,b.t[i]));if(lo.some((v,i)=>hi[i]-v<=eps))return [a];overlaps++;const out=[],core={f:a.f.slice(),t:a.t.slice()};for(let i=0;i<3;i++){if(core.f[i]<lo[i]-eps){const q={f:core.f.slice(),t:core.t.slice()};q.t[i]=lo[i];out.push(q);core.f[i]=lo[i];}if(core.t[i]>hi[i]+eps){const q={f:core.f.slice(),t:core.t.slice()};q.f[i]=hi[i];out.push(q);core.t[i]=hi[i];}}return out;}
 for(const cube of original){let parts=[{f:cube.from.slice(),t:cube.to.slice()}];for(const old of accepted)parts=parts.flatMap(p=>subtract(p,old));const faces={};for(const [d,f]of Object.entries(cube.faces))faces[d]={uv:f.uv.slice(),texture:f.texture};const parent=cube.parent;for(let i=0;i<parts.length;i++){const q=parts[i];accepted.push(q);new Cube({name:cube.name+(parts.length>1?'_solid_'+(i+1):''),from:q.f,to:q.t,origin:cube.origin.slice(),faces,box_uv:false,autouv:0}).addTo(parent).init();}cube.remove();}
 Undo.finishEdit('Remove monitor intersecting volumes and coplanar exterior overlap');Canvas.updateAll();return {before:original.length,after:Cube.all.length,overlaps};
})()
