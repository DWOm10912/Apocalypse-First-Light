// AFL Mesh V1: strict offline Free Model converter. Never rewrites source, Cube geo or animations.
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const fail = message => { throw new Error(message); };
const need = (condition, message) => { if (!condition) fail(message); };
const finite = n => typeof n === 'number' && Number.isFinite(n);
const vec = (v, length, where) => {
    need(Array.isArray(v) && v.length === length && v.every(finite), `${where}: expected ${length} finite numbers`);
    return v;
};
const clean = n => Number(n.toFixed(10)) || 0;
const add = (a,b) => a.map((n,i)=>n+b[i]);
const sub = (a,b) => a.map((n,i)=>n-b[i]);
const cross = (a,b) => [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const dot = (a,b) => a.reduce((sum,n,i)=>sum+n*b[i],0);
const length = a => Math.hypot(...a);
const compare = (a,b) => a<b?-1:a>b?1:0;
const same = (a,b) => a.every((v,i)=>Math.abs(v-b[i])<1e-6);

// THREE Euler XYZ means Rx*Ry*Rz; Group ZYX means Rz*Ry*Rx (column vectors).
// Mesh vertices are relative to element.origin, NOT absolute Blockbench coordinates.
export function rotate(v, degrees, order) {
    let p=v.slice();
    for (const axis of [...order].reverse()) {
        const a=degrees['XYZ'.indexOf(axis)]*Math.PI/180,c=Math.cos(a),s=Math.sin(a),[x,y,z]=p;
        p=axis==='X'?[x,c*y-s*z,s*y+c*z]:axis==='Y'?[c*x+s*z,y,-s*x+c*z]:[c*x-s*y,s*x+c*y,z];
    }
    return p;
}

/** Deterministic ear clipping of a simple planar polygon; caller supplies boundary order. */
export function triangulate(points, where='polygon') {
    need(points.length>=3 && points.length<=64, `${where}: face requires 3..64 vertices`);
    let n=[0,0,0];
    for(let i=0;i<points.length;i++) n=add(n,cross(points[i],points[(i+1)%points.length]));
    const magnitude=length(n);
    need(magnitude>1e-8,`${where}: zero-area or self-intersecting polygon`);
    n=n.map(v=>v/magnitude);
    const extent=Math.max(...[0,1,2].map(k=>Math.max(...points.map(p=>p[k]))-Math.min(...points.map(p=>p[k]))));
    const tolerance=Math.max(1e-5,extent*1e-5);
    need(points.every(p=>Math.abs(dot(sub(p,points[0]),n))<=tolerance),`${where}: non-planar polygon (tolerance ${tolerance})`);
    const drop=n.map(Math.abs).indexOf(Math.max(...n.map(Math.abs)));
    const p=points.map(v=>v.filter((_,i)=>i!==drop));
    const turn=(a,b,c)=>(b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);
    const eps=Math.max(1e-10,extent*extent*1e-10);
    const on=(a,b,c)=>Math.abs(turn(a,b,c))<=eps && c[0]>=Math.min(a[0],b[0])-eps && c[0]<=Math.max(a[0],b[0])+eps
        && c[1]>=Math.min(a[1],b[1])-eps && c[1]<=Math.max(a[1],b[1])+eps;
    const intersects=(a,b,c,d)=>{
        const ab1=turn(a,b,c),ab2=turn(a,b,d),cd1=turn(c,d,a),cd2=turn(c,d,b);
        return ((ab1>eps&&ab2< -eps||ab1< -eps&&ab2>eps)&&(cd1>eps&&cd2< -eps||cd1< -eps&&cd2>eps))
            || on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b);
    };
    for(let i=0;i<p.length;i++) for(let j=i+1;j<p.length;j++) {
        need(length(sub(points[i],points[j]))>1e-8,`${where}: duplicate polygon positions`);
        if(j===i+1 || i===0&&j===p.length-1) continue;
        need(!intersects(p[i],p[(i+1)%p.length],p[j],p[(j+1)%p.length]),`${where}: self-intersecting polygon`);
    }
    const area=p.reduce((s,a,i)=>s+a[0]*p[(i+1)%p.length][1]-a[1]*p[(i+1)%p.length][0],0);
    need(Math.abs(area)>eps,`${where}: zero-area projected polygon`);
    const direction=Math.sign(area),remaining=p.map((_,i)=>i),triangles=[];
    while(remaining.length>3) {
        let cut=false;
        for(let i=0;i<remaining.length;i++) {
            const a=remaining[(i+remaining.length-1)%remaining.length],b=remaining[i],c=remaining[(i+1)%remaining.length];
            if(direction*turn(p[a],p[b],p[c])<=eps) continue;
            const occupied=remaining.some(k=>k!==a&&k!==b&&k!==c
                && direction*turn(p[a],p[b],p[k])>=-eps && direction*turn(p[b],p[c],p[k])>=-eps && direction*turn(p[c],p[a],p[k])>=-eps);
            if(occupied) continue;
            triangles.push([a,b,c]);remaining.splice(i,1);cut=true;break;
        }
        need(cut,`${where}: cannot triangulate without degenerate/overlapping triangles`);
    }
    triangles.push(remaining.slice());
    for(const [a,b,c] of triangles) need(length(cross(sub(points[b],points[a]),sub(points[c],points[a])))>1e-8,`${where}: zero-area triangle`);
    return triangles;
}

const unsupported = new Set(['weights','vertex_weights','skinning','skin','armatures','morph','morph_targets','shape_keys',
    'subdivision','subdivisions','modifiers','vertex_colors','dynamic_topology','deformation']);
function rejectFeatures(value, where) {
    if(!value || typeof value!=='object') return;
    for(const [key,child] of Object.entries(value)) {
        if(unsupported.has(key)) need(child===false || child===0 || child===null || Array.isArray(child)&&!child.length,
            `${where}.${key}: unsupported feature`);
        if(key!=='_static') rejectFeatures(child,`${where}.${key}`);
    }
}

export function convert(source, geometry, mapping={}, sourceName='bbmodel') {
    need(source?.meta?.model_format==='free',`${sourceName}: V1 requires Free Model`);
    rejectFeatures(source,sourceName);
    need(Array.isArray(source.textures)&&source.textures.length===1,`${sourceName}: exactly one texture atlas required`);
    const texture=source.textures[0];
    need(!texture.frameCount || texture.frameCount===1,`${sourceName}: animated textures unsupported`);
    need(!texture.scope && (!texture.render_mode||texture.render_mode==='default')
        && (!texture.pbr_channel||texture.pbr_channel==='color'),`${sourceName}: texture scope/material mode unsupported`);
    const size=[texture.uv_width??source.resolution?.width,texture.uv_height??source.resolution?.height];
    vec(size,2,'texture_size');need(size.every(n=>Number.isInteger(n)&&n>0&&n<=4096),'invalid texture_size');
    if(texture.width!==undefined || texture.height!==undefined) {
        vec([texture.width,texture.height],2,'texture pixel dimensions');
        need(texture.width>0&&texture.height>0&&Math.abs(texture.width*size[1]-texture.height*size[0])<1e-6,
            `${sourceName}: animated strip/different texture aspect unsupported`);
    }
    const geos=geometry?.['minecraft:geometry'];
    need(Array.isArray(geos)&&geos.length===1,'target geometry must have exactly one minecraft:geometry');
    const geo=geos[0];
    need(geo.description.texture_width===size[0]&&geo.description.texture_height===size[1],'target geometry texture_size mismatch');
    const bones=new Map();
    for(const b of geo.bones) {
        need(typeof b.name==='string'&&!bones.has(b.name),`duplicate/invalid target bone ${b.name}`);
        bones.set(b.name,b);
    }
    for(const b of bones.values()) {
        const seen=new Set([b.name]);let parent=b.parent;
        while(parent) { need(bones.has(parent)&&!seen.has(parent),`target bone ${b.name}: unknown/cyclic parent ${parent}`);seen.add(parent);parent=bones.get(parent).parent; }
    }
    need(mapping && typeof mapping==='object'&&!Array.isArray(mapping),'mapping must be group-name -> bone-name object');
    const groups=new Map((source.groups??[]).map(g=>[g.uuid,g]));
    need(groups.size===(source.groups??[]).length,'duplicate group UUID');
    const elements=new Map((source.elements??[]).map(e=>[e.uuid,e]));
    need(elements.size===source.elements?.length,'duplicate element UUID');
    const visited=new Set(),groupNames=new Set(),usedMapping=new Set(),partNames=new Set(),parts=[];
    const knownElementKeys=new Set(['name','color','origin','rotation','shading','export','visibility','locked','render_order','scope',
        'allow_mirror_modeling','vertices','faces','type','uuid']);
    const knownGroupKeys=new Set(['name','uuid','export','locked','scope','selected','visibility','_static','origin',
        'rotation','color','children','reset','shade','mirror_uv','autouv','isOpen','primary_selected','scale']);
    function walk(nodes,parentBone=null,enabled=true) {
        need(Array.isArray(nodes),'invalid outliner children');
        for(const node of nodes) {
            if(typeof node==='string') {
                const element=elements.get(node);need(element,`unknown element UUID ${node}`);
                need(!visited.has(node),`duplicate element parent ${element.name}`);visited.add(node);
                if(!enabled || element.export===false) continue;
                need(parentBone,`${element.name}: mesh/cube requires parent bone group`);
                if(element.type==='cube') continue; // Cube geo remains the existing separate artifact.
                need(element.type==='mesh',`${element.name}: unsupported element type ${element.type}`);
                const where=`${sourceName} bone=${parentBone.name} mesh=${element.name}`;
                for(const key of Object.keys(element)) need(knownElementKeys.has(key),`${where}: unsupported field ${key}`);
                need(!element.scope,`${where}: multi-file scope unsupported`);
                need(!element.shading||element.shading==='flat',`${where}: smooth shading unsupported; author flat shading`);
                need(!element.render_order||element.render_order==='default',`${where}: custom render_order unsupported`);
                need(typeof element.name==='string'&&element.name.trim()&&element.name.length<=128&&!partNames.has(element.name),`${where}: invalid/duplicate stable part name`);
                partNames.add(element.name);
                const origin=vec(element.origin??[0,0,0],3,`${where} origin`),rotation=vec(element.rotation??[0,0,0],3,`${where} rotation`);
                // Target .geo pivot/rotation have the native exporter X and X/Y sign conversions.
                const bp=parentBone.pivot??[0,0,0],pivot=[-bp[0],bp[1],bp[2]];
                const vertices=[],triangles=[];
                need(element.faces&&element.vertices,`${where}: missing mesh data`);
                for(const faceKey of Object.keys(element.faces).sort(compare)) {
                    const face=element.faces[faceKey],label=`${where} face=${faceKey}`;
                    need(Object.keys(face).every(k=>['vertices','uv','texture'].includes(k)),`${label}: unsupported face field`);
                    if(face.texture===null) continue; // Explicitly disabled surface.
                    need(face.texture===0 || typeof texture.uuid==='string'&&face.texture===texture.uuid,`${label}: unknown/multiple texture`);
                    need(Array.isArray(face.vertices)&&new Set(face.vertices).size===face.vertices.length,`${label}: duplicate/invalid vertex keys`);
                    const points=face.vertices.map(key=>vec(element.vertices[key],3,`${label} invalid vertex ${key}`));
                    const uv=face.vertices.map(key=>vec(face.uv?.[key],2,`${label} missing UV ${key}`));
                    const tris=triangulate(points,label),base=vertices.length;
                    points.forEach((p,i)=>{
                        const local=sub(add(origin,rotate(p,rotation,'XYZ')),pivot).map(n=>clean(n/16));
                        const tex=uv[i].map((n,j)=>clean(n/size[j]));
                        need(local.every(n=>Math.abs(n)<=256)&&tex.every(n=>n>=0&&n<=1),`${label}: position/UV outside V1 range`);
                        vertices.push([...local,...tex]);
                    });
                    for(const t of tris) {
                        const ids=t.map(i=>base+i),[a,b,c]=ids.map(i=>vertices[i].slice(0,3));
                        need(length(cross(sub(b,a),sub(c,a)))>1e-10,`${label}: zero-area triangle after coordinate bake`);
                        triangles.push(ids);
                    }
                }
                need(triangles.length,`${where}: no enabled triangles`);
                parts.push({name:element.name,bone:parentBone.name,vertices,triangles});
                continue;
            }
            need(node&&typeof node==='object', 'invalid outliner node');
            const group=groups.get(node.uuid)??node;
            need(group.uuid&&!visited.has(group.uuid),`duplicate/cyclic group ${group.name}`);visited.add(group.uuid);
            const active=enabled&&group.export!==false;
            if(!active) { walk(node.children??[],parentBone,false);continue; }
            for(const key of Object.keys(group)) need(knownGroupKeys.has(key),`group ${group.name}: unsupported field ${key}`);
            need(typeof group.name==='string'&&!groupNames.has(group.name),`invalid/duplicate group name ${group.name}`);groupNames.add(group.name);
            const name=Object.hasOwn(mapping,group.name)?mapping[group.name]:group.name;
            if(Object.hasOwn(mapping,group.name)) usedMapping.add(group.name);
            const bone=bones.get(name);need(bone,`${sourceName} group=${group.name}: unknown parent bone ${name}`);
            need(!group.scope&&!group.mirror_uv&&!group.reset,`group ${group.name}: unsupported scope/mirror/reset`);
            need(!group.scale||same(vec(group.scale,3,'group scale'),[1,1,1]),`group ${group.name}: scale unsupported`);
            const p=vec(group.origin??[0,0,0],3,`group ${group.name} origin`),r=vec(group.rotation??[0,0,0],3,`group ${group.name} rotation`);
            need(same(vec(bone.pivot??[0,0,0],3,'target pivot'),[-p[0],p[1],p[2]]),`group ${group.name}: target bone pivot mismatch; rebind in authoring first`);
            need(same(vec(bone.rotation??[0,0,0],3,'target rotation'),[-r[0],-r[1],r[2]]),`group ${group.name}: target bone rotation mismatch`);
            need((bone.parent??null)===(parentBone?.name??null),`group ${group.name}: target bone hierarchy mismatch`);
            walk(node.children??[],bone,active);
        }
    }
    walk(source.outliner);
    for(const e of elements.values()) need(visited.has(e.uuid)||e.export===false,`${e.name}: orphan element/unknown parent bone`);
    for(const key of Object.keys(mapping)) need(usedMapping.has(key),`unused group mapping ${key}`);
    for(const animation of source.animations??[]) for(const [id,animator] of Object.entries(animation.animators??{})) {
        need(!elements.has(id)&&(!animator.type||['bone','effect'].includes(animator.type)),'mesh/topology animation unsupported');
        if(animator.type!=='effect') for(const key of animator.keyframes??[])
            need(['rotation','position','scale'].includes(key.channel),`unsupported animation channel ${key.channel}`);
    }
    parts.sort((a,b)=>compare(a.bone,b.bone)||compare(a.name,b.name));
    need(parts.length>0&&parts.length<=128,'parts count must be 1..128');
    need(parts.reduce((s,p)=>s+p.triangles.length,0)<=16384,'triangle hard limit 16384 exceeded');
    need(parts.reduce((s,p)=>s+p.vertices.length,0)<=65536,'vertex hard limit 65536 exceeded');
    return {format_version:1,coordinate_space:'bone_pivot_local_blocks',uv_origin:'top_left',winding:'ccw',texture_size:size,parts};
}

export const read = file => JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));
export const serialize = data => JSON.stringify(data,null,2)+'\n';
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
    try {
        const args=process.argv.slice(2),options={};
        for(let i=0;i<args.length;i++) {
            const key=args[i];need(['--input','--geometry','--mapping','--output','--check'].includes(key)&&!(key in options),`unknown/duplicate argument ${key}`);
            options[key]=key==='--check'?true:args[++i];
            need(options[key] && (options[key]===true || !options[key].startsWith('--')),`missing value for ${key}`);
        }
        need(options['--input']&&options['--geometry']&&options['--output'],
            'Usage: node tools/export-afl-mesh.mjs --input source.bbmodel --geometry target.geo.json --output target.aflmesh.json [--mapping groups.json] [--check]');
        const input=path.resolve(options['--input']),geometry=path.resolve(options['--geometry']),output=path.resolve(options['--output']);
        need(output.endsWith('.aflmesh.json')&&output!==input&&output!==geometry,'output must be a separate .aflmesh.json');
        const model=convert(read(input),read(geometry),options['--mapping']?read(options['--mapping']):{},input),text=serialize(model);
        need(text.length<=4*1024*1024,'sidecar exceeds runtime 4 MiB limit');
        if(options['--check']) need(fs.readFileSync(output,'utf8')===text,`stale sidecar ${output}`);
        else fs.writeFileSync(output,text);
        console.log(`${options['--check']?'CHECKED':'EXPORTED'} ${model.parts.length} parts, ${model.parts.reduce((s,p)=>s+p.triangles.length,0)} triangles: ${output}`);
    } catch(e) { console.error(e.message);process.exitCode=1; }
}
