// Only source-only arm references change; preserve all tool geometry and animation data.
import fs from 'node:fs';
const path='src/main/blockbench/crowbar_first_person.bbmodel';
const source=JSON.parse(fs.readFileSync(path,'utf8'));
let count=0;
for(const e of source.elements){
 if(!['right_arm_reference_classic','right_arm_reference_slim'].includes(e.name))continue;
 if(e.export!==false)throw new Error('Reference unexpectedly exported');
 const width=e.name.endsWith('slim')?3:4;
 e.from=[-width/2,-12,-2];e.to=[width/2,0,2];count++;
}
if(count!==2)throw new Error('Expected two reference arms');
fs.writeFileSync(path,JSON.stringify(source,null,2)+'\n');
