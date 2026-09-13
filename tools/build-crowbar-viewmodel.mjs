// Derive an independent single-hand rig; never overwrite the approved world/item source.
import fs from 'node:fs';
import crypto from 'node:crypto';
const base='src/main/resources/assets/apocalypse_firstlight/';
const original=JSON.parse(fs.readFileSync('src/main/blockbench/crowbar.bbmodel','utf8'));
const template=JSON.parse(fs.readFileSync('src/main/blockbench/templates/afl_first_person_player_arm_rig.bbmodel','utf8'));
const id=name=>{const h=crypto.createHash('md5').update('crowbar_fp/'+name).digest('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;};
const zero=[0,0,0];
const groups=[],elements=[];
const group=(name,extra={})=>{const g={name,uuid:id(name),origin:[...zero],rotation:[...zero],export:true,...extra};groups.push(g);return {uuid:g.uuid,children:[]};};
const root=group('crowbar_fp_root'),motion=group('smash_motion'),tool=group('crowbar_model',{rotation:[0,-110,0]}),hand=group('right_hand_anchor');
root.children.push(motion);motion.children.push(tool,hand);
const convert=v=>v.map((n,i)=>Number(((n-[9,3,8][i])*.65).toFixed(6)));
for(const e of original.elements){const c=structuredClone(e);c.uuid=id(e.name);c.from=convert(c.from);c.to=convert(c.to);c.origin=convert(c.origin);c.rotation=c.rotation||[...zero];c.box_uv=false;elements.push(c);tool.children.push(c.uuid);}
// Extract the canonical right arm references, at the exact NativePlayerArmRenderer presentation scale.
for(const [name,width] of [['classic',4],['slim',3]]){
 const ref=structuredClone(template.elements.find(e=>e.name===name+'_right_reference_only'));
 ref.name='right_arm_reference_'+name;ref.uuid=id(ref.name);ref.export=false;ref.box_uv=false;
 ref.from=[-width/2,-12,-2];ref.to=[width/2,0,2];ref.origin=[...zero];
 ref.visibility=name==='classic';ref.faces=Object.fromEntries(['north','south','east','west','up','down'].map(f=>[f,{uv:[0,0,4,12],texture:0}]));
 elements.push(ref);hand.children.push(ref.uuid);
}
const keys=[
 [0,[6,-6,-17],[8,-8,12],[-73,0,0]],
 [.15,[6.4,-5.7,-16.8],[12,-9,14],[-77,0,0]],
 [.55,[7,-3,-16.5],[38,-12,23],[-103,0,0]],
 [.9,[6.5,-1.5,-16],[55,-15,25],[-120,0,0]],
 [1.0,[4,-2,-18],[8,-6,12],[-73,0,0]],
 [1.05,[1.9,-4.5,-19],[-65,0,-4],[0,0,0]],
 [1.10,[1.9,-4.5,-19],[-65,0,-4],[0,0,0]],
 [1.20,[2.4,-4.8,-18],[-53,0,-1],[-12,0,0]],
 [1.55,[4.5,-6,-17],[-8,-5,7],[-57,0,0]],
 [2.0,[6,-6,-17],[8,-8,12],[-73,0,0]],
 [2.45,[6,-6,-17],[8,-8,12],[-73,0,0]]
];
// Revised user cue contains only fracture: 0.60s silent windup, 1.05s recovery/tail.
for(const k of keys)k[0]=Number((k[0]<=1.05?k[0]*.6/1.05:.6+(k[0]-1.05)*1.05/1.4).toFixed(4));
const frames=(index,channel)=>keys.map(k=>({uuid:id(channel+index+k[0]),channel,time:k[0],interpolation:'linear',data_points:[{x:String(k[index][0]),y:String(k[index][1]),z:String(k[index][2])}]}));
const animators={
 [id('smash_motion')]:{name:'smash_motion',type:'bone',keyframes:[...frames(1,'position'),...frames(2,'rotation')]},
 [id('right_hand_anchor')]:{name:'right_hand_anchor',type:'bone',keyframes:frames(3,'rotation')}
};
const source={meta:{format_version:'5.0',model_format:'geckolib_model',box_uv:false},name:'crowbar_first_person',resolution:original.resolution,
 textures:original.textures,elements,groups,outliner:[root],animations:[{uuid:id('smash_glass'),name:'smash_glass',loop:'once',length:1.65,animators}]};
const bones=groups.map(g=>({name:g.name,pivot:[0,0,0],...(g.name==='crowbar_fp_root'?{}:{parent:g.name==='smash_motion'?'crowbar_fp_root':'smash_motion'})}));
bones.find(b=>b.name==='crowbar_model').rotation=[0,110,0]; // Gecko bind rotation reverses X/Y on load.
bones.find(b=>b.name==='crowbar_model').cubes=elements.filter(e=>e.export!==false).map(e=>({origin:[-e.to[0],e.from[1],e.from[2]],size:e.to.map((v,i)=>v-e.from[i]),pivot:[-e.origin[0],e.origin[1],e.origin[2]],rotation:e.rotation.map((v,i)=>v*(i<2?-1:1)),uv:Object.fromEntries(Object.entries(e.faces).filter(([,f])=>f.texture!==null).map(([n,f])=>[n,{uv:f.uv.slice(0,2),uv_size:[f.uv[2]-f.uv[0],f.uv[3]-f.uv[1]]}]))}));
const track=(index,rotation)=>Object.fromEntries(keys.map(k=>[k[0].toFixed(2),{vector:k[index].map((v,i)=>v*(rotation?(i<2?-1:1):(i===0?-1:1)))}]));
const animation={format_version:'1.8.0',afl_timing:{duration_ticks:33,impact_tick:12,sound_seconds:1.027483},animations:{smash_glass:{animation_length:1.65,bones:{smash_motion:{position:track(1,false),rotation:track(2,true)},right_hand_anchor:{rotation:track(3,true)}}}}};
const geo={format_version:'1.12.0','minecraft:geometry':[{description:{identifier:'geometry.crowbar_first_person',texture_width:original.resolution.width,texture_height:original.resolution.height,visible_bounds_width:4,visible_bounds_height:4},bones}]};
for(const [file,value] of [['src/main/blockbench/crowbar_first_person.bbmodel',source],[base+'geo/crowbar_first_person.geo.json',geo],[base+'animations/crowbar_first_person.animation.json',animation]])fs.writeFileSync(file,JSON.stringify(value,null,2)+'\n');
console.log('Generated independent smash_glass rig: '+(elements.length-2)+' tool cubes; canonical Classic/Slim reference arms; 33 ticks / impact 12.');
