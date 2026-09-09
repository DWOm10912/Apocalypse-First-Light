// Regenerate targeted magazine geometry only; preserve all other bones/animations.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert');
const root=path.resolve(__dirname,'../../..');
const read=p=>JSON.parse(fs.readFileSync(path.join(root,p),'utf8'));
const write=(p,x)=>fs.writeFileSync(path.join(root,p),JSON.stringify(x,null,2)+'\n');
const clone=x=>structuredClone(x);
function polish(cubes){
 const out=clone(cubes),body=out[0],o=body.origin,s=body.size;
 const x=o[0],y=o[1],z=o[2],w=s[0],h=s[1],d=s[2],top=y+h;
 const metal=clone(out[2].uv),dark=clone(body.uv),base=clone(out[1].uv);
 const add=(origin,size,uv)=>out.push({origin,size,uv:clone(uv)});
 // Recess the solid body top; four solid lips stay within its former envelope.
 body.size[1]-=.32;
 add([x,top-.32,z],[.14,.32,d],metal);
 add([x+w-.14,top-.32,z],[.14,.32,d],metal);
 add([x+.14,top-.32,z],[w-.28,.32,.13],metal);
 add([x+.14,top-.32,z+d-.13],[w-.28,.32,.13],metal);
 add([x+.19,top-.30,z+.18],[w-.38,.10,d-.36],dark);
 // Restrained basal shoulder and two shallow raised side panels.
 add([x-.07,y+.02,z-.06],[w+.14,.16,d+.12],base);
 for(const side of [-1,1]){
  const sx=side<0?x-.055:x+w-.005;
  add([sx,y+.28,z+.29],[.06,.95,d-.58],dark);
  for(const dy of [.35,.94])add([sx-.012,y+dy,z+.35],[.084,.09,d-.70],metal);
 }
 return out;
}
function nodeById(nodes,id){for(const n of nodes){if(n.uuid===id)return n;const k=n.children&&nodeById(n.children,id);if(k)return k;}}
function syncSource(bb,name,before,after,pivot){
 const group=bb.groups?.find(g=>g.name===name);
 const findName=ns=>{for(const n of ns){if(n.name===name)return n;const v=n.children&&findName(n.children);if(v)return v;}};
 const node=group?nodeById(bb.outliner,group.uuid):findName(bb.outliner);
 assert(node,`missing ${name}`);const ids=node.children.filter(x=>typeof x==='string');
 const elems=ids.map(id=>bb.elements.find(e=>e.uuid===id));assert.equal(elems.length,before.length);
 const toElement=(cube,template)=>{
  const e=clone(template);e.uuid=crypto.randomUUID();e.name=name+'_relief_'+bb.elements.length;
  e.from=[-cube.origin[0]-cube.size[0],cube.origin[1],cube.origin[2]];
  e.to=e.from.map((v,i)=>v+cube.size[i]);e.origin=clone(pivot);e.rotation=[0,0,0];e.box_uv=false;e.autouv=0;
  for(const [dir,f] of Object.entries(cube.uv))e.faces[dir]={uv:[...f.uv,f.uv[0]+f.uv_size[0],f.uv[1]+f.uv_size[1]],texture:template.faces[dir].texture};
  return e;
 };
 elems[0].to[1]-=.32;
 for(const c of after.slice(before.length)){const e=toElement(c,elems[0]);bb.elements.push(e);node.children.push(e.uuid);}
}
const gp='src/main/resources/assets/apocalypse_firstlight/geo/';
const sp='src/main/blockbench/';
const ext=read(gp+'p9_01_extended_magazine.geo.json'),gun=read(gp+'p9_01.geo.json');
const eb=ext['minecraft:geometry'][0].bones[0],oldExt=clone(eb.cubes);
assert.equal(oldExt.length,11,'already polished or edited; inspect before regenerating');eb.cubes=polish(oldExt);
const es=read(sp+'p9_01_extended_magazine.bbmodel');syncSource(es,'magazine_root',oldExt,eb.cubes,[0,0,0]);
const gs=read(sp+'p9_01.bbmodel');
for(const b of gun['minecraft:geometry'][0].bones.filter(b=>['magazine','empty_old_magazine'].includes(b.name))){
 assert.equal(b.cubes.length,11);const old=clone(b.cubes);b.cubes=polish(old);syncSource(gs,b.name,old,b.cubes,[-b.pivot[0],b.pivot[1],b.pivot[2]]);
}
// Mounted reference has one fixed-pose magazine translated from the independent part.
const preview=read(sp+'p9_01_extended_magazine_fit_preview.bbmodel');
const shift=arr=>arr.map((v,i)=>v+[2.98,7.95,7.28][i]);
const translated=cs=>cs.map(c=>({...clone(c),origin:shift(c.origin)}));
syncSource(preview,'magazine',translated(oldExt),translated(eb.cubes),[-2.98,7.95,7.28]);
write(gp+'p9_01_extended_magazine.geo.json',ext);write(gp+'p9_01.geo.json',gun);
write(sp+'p9_01_extended_magazine.bbmodel',es);write(sp+'p9_01.bbmodel',gs);write(sp+'p9_01_extended_magazine_fit_preview.bbmodel',preview);
console.log('Polished standard, reload duplicate, extended and mounted reference: 23 cubes each; pivots/textures/animations preserved.');
