// Source-preserving BR51_01 visual migration. Never modify the external authoring directory.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const input=process.argv.find(x=>x.startsWith('--source='))?.slice(9)||'E:/Download/合集/M14';
const out='src/main/resources/assets/apocalypse_firstlight/';
// External originals keep their historical filenames; normalize only loaded metadata.
const read=n=>JSON.parse(fs.readFileSync(path.join(input,n.replaceAll('br51_01','m14')),'utf8'),(k,v)=>typeof v==='string'&&!v.startsWith('data:')?v.replaceAll('m14','br51_01'):v);
const source=read('br51_01_1_geo.bbmodel'), geo=read('br51_01_1_geo.json'), anim=read('br51_01_1.animation.json');
const names=['static_idle','reload_empty','reload_tactical','static_bolt_caught','inspect','shoot','put_away','draw'];
if(Object.keys(anim.animations).sort().join()!=[...names].sort().join())throw Error('Source must contain exactly the approved eight animations');
const originals=structuredClone(anim.animations);
// Preserve motion, not legacy runtime loop/hold semantics for discrete actions.
const looping = new Set(['static_idle', 'static_bolt_caught']);
for (const [name, clip] of Object.entries(anim.animations)) clip.loop = looping.has(name);
for (const clip of source.animations) clip.loop = looping.has(clip.name) ? 'loop' : 'once';
const soundFiles={fire:'br51_01_shoot',reload_empty_1:'reload_empty_1',reload_empty_2:'reload_empty_2',reload_empty_3:'reload_empty_3',reload_empty_4:'reload_empty_4',reload_tactical_1:'reload_tactical_1',reload_tactical_2:'reload_tactical_2',reload_tactical_3:'reload_tactical_3',draw:'br51_01_draw',put_away:'br51_01_draw_1'};
function soundId(effect){
 if(effect==='shoot')return 'apocalypse_firstlight:br51_01_fire';
 if(effect.startsWith('wemql_r:br51_01_1/'))return 'apocalypse_firstlight:br51_01_'+effect.split('/').pop();
 if(effect.includes('magout_1_'))return 'apocalypse_firstlight:br51_01_reload_tactical_1';
 if(effect.includes('magout_2_'))return 'apocalypse_firstlight:br51_01_reload_tactical_2';
 if(effect.endsWith('/br51_01_draw')||effect.endsWith('reload_raise'))return 'apocalypse_firstlight:br51_01_draw';
 if(effect.endsWith('drop_rattle'))return 'apocalypse_firstlight:br51_01_put_away';
 throw Error('Unmapped event: '+effect);
}
const id=n=>{let h=crypto.createHash('md5').update('afl-br51_01/'+n).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;};
const bones=geo['minecraft:geometry'][0].bones;
for(const side of ['right','left']){
 const driver=side+'hand',name=side+'_hand_anchor',x=side==='right'?6:-6;
 const old=bones.find(b=>b.name===driver+'_pos');
 // Preserve old driver transforms, replace only its visible arm cube.
 delete old.cubes;
 bones.push({name,parent:driver+'_pos',pivot:[x,8,4],rotation:[0,0,180]});
 const parentGroup=source.groups.find(g=>g.name===driver+'_pos');
 function find(nodes){for(const n of nodes){if(typeof n==='string')continue;if(n.uuid===parentGroup.uuid)return n;let r=find(n.children||[]);if(r)return r;}}
 const node=find(source.outliner);
 for(const child of node.children){const e=source.elements.find(e=>e.uuid===child);if(e){e.export=false;e.visibility=false;}}
 // Blockbench authoring X is the opposite of Bedrock JSON X.
 source.groups.push({name,uuid:id(name),origin:[-x,8,4],rotation:[0,0,180],export:true});
 node.children.push({uuid:id(name),children:[]});
 // Constant compensation cancels the source's hand-only 1.6 stretch, at the distal cap.
 for(const a of Object.values(anim.animations))a.bones[name]={scale:[1,0.625,1]};
 for(const a of source.animations){a.animators[id(name)]={name,type:'bone',keyframes:[{channel:'scale',time:0,data_points:[{x:1,y:.625,z:1}],interpolation:'linear',uuid:id(a.name+name)}]};}
}
for(const a of Object.values(anim.animations))for(const e of Object.values(a.sound_effects||{}))e.effect=soundId(e.effect);
for(const a of source.animations)for(const animator of Object.values(a.animators))for(const k of animator.keyframes||[])if(k.channel==='sound')for(const p of k.data_points||[])if(p.effect)p.effect=soundId(p.effect);
// Motion contract: original channels, times, interpolation, pivots and hierarchy remain intact.
for(const name of names){for(const [bone,track] of Object.entries(originals[name].bones))if(JSON.stringify(anim.animations[name].bones[bone])!==JSON.stringify(track))throw Error('Motion changed '+name+'/'+bone);}
source.name='br51_01';source.model_identifier='br51_01';
const display={firstperson_righthand:{rotation:[0,0,0],translation:[1,-5,-10],scale:[.45,.45,.45]},firstperson_lefthand:{rotation:[0,0,0],translation:[1,-5,-10],scale:[.45,.45,.45]},thirdperson_righthand:{rotation:[0,0,0],translation:[0,1,0],scale:[.4,.4,.4]},thirdperson_lefthand:{rotation:[0,0,0],translation:[0,1,0],scale:[.4,.4,.4]},gui:{rotation:[20,135,0],translation:[0,-3,0],scale:[.24,.24,.24]},ground:{rotation:[0,0,90],translation:[0,1,0],scale:[.3,.3,.3]},fixed:{rotation:[0,90,0],scale:[.25,.25,.25]}};
source.display=display;
const png=Buffer.from(source.textures[0].source.split(',')[1],'base64');
source.textures[0].name='br51_01.png';source.textures[0].relative_path='../resources/assets/apocalypse_firstlight/textures/item/br51_01.png';source.textures[0].namespace='apocalypse_firstlight';source.textures[0].folder='item';
geo['minecraft:geometry'][0].description.identifier='geometry.br51_01';
const assets=[['src/main/blockbench/br51_01.bbmodel',JSON.stringify(source,null,2)], [out+'geo/br51_01.geo.json',JSON.stringify(geo,null,2)], [out+'animations/br51_01.animation.json',JSON.stringify(anim,null,2)], [out+'textures/item/br51_01.png',png], [out+'models/item/br51_01.json',JSON.stringify({parent:'builtin/entity',textures:{particle:'apocalypse_firstlight:item/br51_01'},display},null,2)]];
for(const [event,file]of Object.entries(soundFiles))assets.push([out+'sounds/br51_01/'+event+'.ogg',fs.readFileSync(path.join(input,'m14_1',file.replaceAll('br51_01','m14')+'.ogg'))]);
const sounds=JSON.parse(fs.readFileSync(out+'sounds.json'));
for(const event of Object.keys(soundFiles))sounds['br51_01_'+event]={sounds:[{name:'apocalypse_firstlight:br51_01/'+event,stream:false}]};
assets.push([out+'sounds.json',JSON.stringify(sounds,null,2)+'\n']);
for(const [p,data]of assets){fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,data);}
console.log('8 animations, 10 sounds; all original motion tracks unchanged; two absent source targets bolt2/charger retained.');
// Reattach source-only previews when regenerating from the external source.
await import('./add-br51_01-reference-arms.mjs');
await import('./calibrate-br51_01-binding.mjs');
await import('./restore-br51_01-empty-magazine.mjs');
await import('./prepare-br51_01-white-gun.mjs');
await import('./apply-br51_01-world-icons.mjs');
