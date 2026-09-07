// Saved-source exporter for AFL's audited GeckoLib cube/bone subset.
// No Blockbench connection, source writes, texture writes, or non-FP Display overwrite.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {previewFactors} from './native-arm-presentation.mjs';
export const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const sourcePath=path.join(root,'src/main/blockbench/service_pistol_v03_8_fire_slide_cleanup.bbmodel');
export const assets=path.join(root,'src/main/resources/assets/apocalypse_firstlight');
export const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const clean=n=>Math.round(n*1e10)/1e10 || 0;
const vector=(a,sign=[1,1,1])=>a.map((n,i)=>clean(n*sign[i]));
export function compile(source, priorGeo, priorDisplay) {
    const groups=new Map(source.groups.map(g=>[g.uuid,g]));
    const elements=new Map(source.elements.map(e=>[e.uuid,e]));
    const bones=[];
    const parents=new Map();
    const byName=new Map(source.groups.map(g=>[g.name,g]));
    function walk(nodes,parent,exported=true) {
        for(const node of nodes) {
            if(typeof node==='string') continue;
            const g=groups.get(node.uuid); assert(g,'Missing group');
            parents.set(g.name,parent);
            const enabled=exported && g.export!==false;
            if(enabled) {
                const b={name:g.name,pivot:vector(g.origin,[-1,1,1])};
                if(parent)b.parent=parent;
                if(g.rotation?.some(Boolean))b.rotation=vector(g.rotation,[-1,-1,1]);
                const cubes=[];
                for(const id of node.children.filter(n=>typeof n==='string')) {
                    const c=elements.get(id); assert(c,'Missing element');
                    if(c.export===false)continue;
                    assert.equal(c.type,'cube','Only cube source is supported');
                    assert.equal(c.box_uv,false,'Bake explicit UV before changing physical size');
                    const size=c.to.map((n,i)=>clean(n-c.from[i]));
                    const cube={origin:[clean(-c.to[0]),c.from[1],c.from[2]],size,uv:{}};
                    for(const [face,f] of Object.entries(c.faces)) {
                        if(f.texture===null)continue;
                        assert(!f.rotation,'Rotated UV requires explicit exporter support');
                        assert.equal(f.texture,0,'Gun must use the gun texture, never reference texture');
                        const [u,v,s,t]=f.uv;
                        cube.uv[face]=face==='up'||face==='down'
                            ? {uv:[s,t],uv_size:[clean(u-s),clean(v-t)]}
                            : {uv:[u,v],uv_size:[clean(s-u),clean(t-v)]};
                    }
                    if(c.rotation?.some(Boolean)) {
                        cube.pivot=vector(c.origin,[-1,1,1]);
                        cube.rotation=vector(c.rotation,[-1,-1,1]);
                    }
                    if(c.inflate)cube.inflate=c.inflate;
                    cubes.push(cube);
                }
                if(cubes.length)b.cubes=cubes;
                bones.push(b);
            }
            walk(node.children,g.name,enabled);
        }
    }
    walk(source.outliner,null);
    const geometry=structuredClone(priorGeo);
    geometry.format_version='1.12.0';
    geometry['minecraft:geometry'][0].bones=bones;
    const animations={format_version:'1.8.0',animations:{}};
    for(const a of source.animations) {
        assert(['fire','reload','empty_idle','fire_last_round','reload_empty'].some(n=>a.name==='animation.service_pistol.'+n));
        const out={animation_length:a.length,bones:{}};
        if(a.loop==='loop')out.loop=true;
        animations.animations[a.name]=out;
        for(const [id,animator] of Object.entries(a.animators)) {
            if(!animator.keyframes?.length)continue;
            if(id==='effects') {
                assert.equal(a.name,'animation.service_pistol.reload_empty');
                assert(animator.keyframes.every(k=>k.channel==='sound' && k.time===1.25
                    && k.data_points.every(p=>p.effect==='slide_action')),'Unknown source-only effect');
                continue; // Blockbench preview only; authoritative server timing plays the sound once.
            }
            const g=groups.get(id); assert(g && g.export,'Unsupported effects/reference animation');
            assert(!animator.rotation_global && !animator.quaternion_interpolation,'Unsupported animation mode');
            const channels={};out.bones[g.name]=channels;
            for(const ch of ['rotation','position','scale']) {
                const keys=animator.keyframes.filter(k=>k.channel===ch).sort((a,b)=>a.time-b.time);
                if(!keys.length)continue;
                const track={};channels[ch]=track;
                keys.forEach((k,i)=>{
                    assert(['linear','step'].includes(k.interpolation));
                    assert(k.time>=0 && k.time<=a.length);
                    assert.equal(k.data_points.length,1);
                    const v=['x','y','z'].map(ax=>Number(k.data_points[0][ax]));
                    assert(v.every(Number.isFinite),'Only numeric authoring channels are supported');
                    const value={vector:vector(v,ch==='rotation'?[-1,-1,1]:ch==='position'?[-1,1,1]:[1,1,1])};
                    if(i && keys[i-1].interpolation==='step')value.easing='afl_hold';
                    // JS enumerates positive integer properties before fractional keys.
                    // Gecko consumes JSON entry order: "1" ahead of ".02" corrupts durations.
                    // Keep zero first, but serialize other whole seconds as decimal strings.
                    const time=k.time>0 && Number.isInteger(k.time)?k.time.toFixed(1):String(k.time);
                    assert(!(time in track),'Duplicate channel time');
                    track[time]=value;
                });
                const times=Object.keys(track).map(Number);
                assert(times.every((t,i)=>i===0||t>times[i-1]),'Serialized keyframes must stay chronological');
            }
        }
    }
    const display=structuredClone(priorDisplay);
    for(const c of ['firstperson_righthand','firstperson_lefthand']) {
        const d=source.display[c]; assert(d);
        const s=d.scale||[1,1,1];
        assert(s.every(v=>v>0 && Math.abs(v-s[0])<1e-7),'Whole rig requires uniform Display');
        display.display[c]=structuredClone(d);
    }
    const referencePresentation=previewFactors(source);
    for(const side of ['right','left']) {
        const anchor=byName.get(side+'_hand_anchor');
        assert(anchor?.export);
        for(const [suffix,width] of [['',4],['_slim',3]]) {
            const ref=byName.get(side+'_arm_reference'+suffix);
            const cube=source.elements.find(e=>e.name===side+'_arm_reference'+suffix+'_cube');
            assert(ref && cube,'Both Classic and Slim references required');
            assert.equal(ref.export,false);assert.equal(cube.export,false);
            assert.equal(parents.get(ref.name),anchor.name);
            assert.deepEqual(ref.origin,anchor.origin);
            assert((ref.rotation||[0,0,0]).every(n=>n===0));
            assert((cube.rotation||[0,0,0]).every(n=>n===0),'Move the locator, not the reference cube');
            const lo=[-width/2,-12,-2].map((n,i)=>n*referencePresentation[i]),hi=[width/2,0,2].map((n,i)=>n*referencePresentation[i]);
            for(let i=0;i<3;i++) {
                assert(Math.abs(cube.from[i]-anchor.origin[i]-lo[i])<1e-7,'Noncanonical reference from');
                assert(Math.abs(cube.to[i]-anchor.origin[i]-hi[i])<1e-7,'Noncanonical reference to');
            }
        }
        for(let name=anchor.name;name;name=parents.get(name)) {
            assert.notEqual(name,'gun_model_root','Hand must not be in gun subtree');
            for(const a of Object.values(animations.animations))
                assert(!a.bones[name]?.scale,'Do not animate hand/ancestor scale');
        }
    }
    assert.equal(bones.reduce((n,b)=>n+(b.cubes||[]).length,0),77);
    assert(!bones.some(b=>b.name.includes('arm_reference')));
    assert.equal(animations.animations['animation.service_pistol.fire'].animation_length,.14);
    assert.equal(animations.animations['animation.service_pistol.reload'].animation_length,1.3);
    const png=source.textures.find(t=>t.name==='service_pistol.png');
    assert(Buffer.from(png.source.split(',')[1],'base64').equals(fs.readFileSync(path.join(assets,'textures/item/service_pistol.png'))),'Texture changed');
    return {geometry,animations,display};
}
export function outputs() {
    const geo=path.join(assets,'geo/service_pistol.geo.json'),anim=path.join(assets,'animations/service_pistol.animation.json'),display=path.join(assets,'models/item/service_pistol_in_hand.json');
    const result=compile(read(sourcePath),read(geo),read(display));
    return new Map([[geo,result.geometry],[anim,result.animations],[display,result.display]]);
}
if(process.argv[1] && path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
    const write=process.argv.includes('--write'),animationsOnly=process.argv.includes('--animations-only');
    assert(process.argv.slice(2).every(a=>['--write','--check','--animations-only'].includes(a)),'Invalid export flag');
    for(const [p,value] of outputs()) {
        if(animationsOnly && !p.endsWith('service_pistol.animation.json'))continue;
        if(write)fs.writeFileSync(p,JSON.stringify(value,null,2)+'\n');
        else assert.deepEqual(read(p),value,'Stale export: '+p);
    }
    console.log(animationsOnly?'PASS: saved-source animations only; geo/Display untouched.':write?'Exported saved bbmodel: geo + animations + FP Display only.':'PASS: exact source/export geometry, keys, canonical Classic/Slim references and FP Display.');
}
