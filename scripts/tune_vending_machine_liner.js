// Idempotent mechanical update to the sole back_liner cube in both minified bbmodels.
const fs=require('fs'),path=require('path');
for(const variant of ['intact','broken']) {
 const file=path.join(__dirname,'../src/main/blockbench/afl_vending_machine_'+variant+'.bbmodel');
 const model=JSON.parse(fs.readFileSync(file,'utf8'));
 const cubes=model.elements.filter(e=>e.name==='back_liner');
 if(cubes.length!==1)throw new Error('Expected exactly one back_liner in '+variant);
 const cube=cubes[0];
 const old=[33,1,47,15],next=[97,17,111,31];
 for(const face of Object.values(cube.faces)) {
  if(!Array.isArray(face.uv)||face.uv.length!==4)throw new Error('Missing UV in '+variant);
  for(let i=0;i<face.uv.length;i++) {
   const n=old.indexOf(face.uv[i]);
   if(n<0) {
    if(!next.includes(face.uv[i]))throw new Error('Unexpected back_liner UV '+face.uv[i]);
   } else face.uv[i]=next[n];
  }
 }
 fs.writeFileSync(file,JSON.stringify(model));
}
