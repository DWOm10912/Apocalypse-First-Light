// Read-only V2.1 adversarial checks and HEAD -> working-tree asset benchmark.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import {execFileSync} from 'node:child_process';
import {read,convert,triangulate,classifyQuad,meshCounts,serializeCompact} from './export-afl-mesh.mjs';
const sub=(a,b)=>a.map((x,i)=>x-b[i]);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const unit=a=>a.map(x=>x/Math.hypot(...a));
const normal=(v,ids)=>unit(cross(sub(v[ids[1]].slice(0,3),v[ids[0]].slice(0,3)),sub(v[ids[2]].slice(0,3),v[ids[0]].slice(0,3))));
const basis=(v,ids)=>{
    const [a,b,c]=ids.map(i=>v[i]),e=sub(b.slice(0,3),a.slice(0,3)),f=sub(c.slice(0,3),a.slice(0,3));
    const u=b[3]-a[3],w=b[4]-a[4],s=c[3]-a[3],t=c[4]-a[4],det=u*t-s*w;
    return {hand:Math.sign(det),t:unit(e.map((x,i)=>(x*t-f[i]*w)/det)),b:unit(e.map((x,i)=>(f[i]*u-x*s)/det))};
};
const tris=[[3,0,1],[1,2,3]],round=v=>v.map(x=>x.map(n=>+n.toFixed(10)));
const square=[[0,0,0,0,0],[1,0,0,1,0],[1,1,0,1,1],[0,1,0,0,1]];
const classify=v=>classifyQuad(v,round(v),tris);
assert.equal(classify(square).reason,'QUAD_PRESERVED_DIRECT');
const tiny=[[0,0],[.0001,0],[.0001,.0001],[0,.0001]].map(([x,y])=>[.75+x,.6+y,.7+.3*x+.7*y,x/.0001,y/.0001]);
assert.equal(classify(tiny).reason,'QUAD_RECOVERED_QUANTIZATION');
const warped=structuredClone(square);warped[2][2]=.0001;
assert.equal(classify(warped).order,null,'genuine small warp');
const concave=structuredClone(square);concave[2][0]=.2;concave[2][1]=.2;
assert.equal(classify(concave).order,null,'concavity');
assert.throws(()=>triangulate([square[0],square[2],square[1],square[3]].map(v=>v.slice(0,3))),/zero-area|self-intersect/);
const mirror=structuredClone(square);mirror[2][3]=-1;
assert.equal(classify(mirror).reason,'TRIANGULATED_UV_UNSAFE','opposite UV handedness');
const seam=structuredClone(square);seam[2][3]=.6;
assert.equal(classify(seam).order,null,'UV seam/distortion');
const stretched=structuredClone(square);stretched[2][3]=2;
assert.equal(classify(stretched).order,null,'non-affine tangent derivatives');
const source=read('src/main/blockbench/dev/afl_mesh_core_fixture.bbmodel');
const geo=read('src/dev/resources/afl_mesh_core/fixture.geo.json');
const hard=structuredClone(source);hard.elements.find(e=>e.type==='mesh').faces.surface.normals=[[0,0,1],[1,0,0],[0,0,1]];
assert.throws(()=>convert(hard,geo,{},'hard',2),/unsupported face field/,'per-corner normal contract must not be discarded');
const smooth=structuredClone(source);smooth.elements.find(e=>e.type==='mesh').shading='smooth';
assert.throws(()=>convert(smooth,geo,{},'smooth',2),/smooth shading/);
for(const scale of [[1,1,1],[2,.5,1.3],[-2,.5,1.3],[.3,3,1]]) {
    const n=normal(tiny,tris[0]),posed=tiny.map(v=>v.map((x,i)=>i<3?x*scale[i]:x));
    const transformed=unit(n.map((x,i)=>x/scale[i]));
    const actual=normal(posed,scale.some(x=>x<0)?[3,1,0]:tris[0]);
    assert.ok(Math.hypot(...sub(actual,transformed))<1e-9,'nonuniform inverse-transpose');
}
console.log('ADVERSARIAL_PASS A-J: planar, rounding, warp, concave, bow-tie, mirrored UV, UV seam, tangent, normal semantics, nonuniform scale');
const canonical=t=>[0,1,2].map(i=>JSON.stringify([...t.slice(i),...t.slice(0,i)])).sort()[0];
for(const id of ['p9_01_v2_native','blackridge_50']) {
    const file=`src/main/resources/assets/apocalypse_firstlight/meshes/${id}.aflmesh.json`;
    const beforeText=execFileSync('git',['-c',`safe.directory=${process.cwd().replaceAll('\\','/')}`,'show',`HEAD:${file}`],{maxBuffer:8*1024*1024}).toString();
    const before=JSON.parse(beforeText),s=read(`src/main/blockbench/${id}.bbmodel`),g=read(`src/main/resources/assets/apocalypse_firstlight/geo/${id}.geo.json`);
    const diagnostics={},after=convert(s,g,{},id,2,diagnostics),v1=convert(s,g);
    assert.equal(fs.readFileSync(file,'utf8'),serializeCompact(after),'exporter --check equivalent');
    let maxNormalDelta=0,maxTangentDelta=0,changedToQuad=0,changedToTriangles=0;
    for(let i=0;i<after.parts.length;i++) {
        const a=after.parts[i],b=before.parts[i];
        assert.deepEqual(a.vertices,b.vertices,'no position/UV edits');
        assert.equal(a.name,b.name);assert.equal(a.bone,b.bone);
        const expand=part=>(part.faces??part.triangles).flatMap(f=>f.length===4?[[f[0],f[1],f[2]],[f[2],f[3],f[0]]]:[f]);
        assert.deepEqual(expand(a).map(canonical).sort(),expand(v1.parts[i]).map(canonical).sort(),'exact V1 diagonal/winding/corners');
        const old=new Set(b.faces.filter(f=>f.length===4).map(f=>JSON.stringify(f)));
        const fresh=new Set(a.faces.filter(f=>f.length===4).map(f=>JSON.stringify(f)));
        changedToTriangles += [...old].filter(f=>!fresh.has(f)).length;
        for(const f of a.faces.filter(f=>f.length===4&&!old.has(JSON.stringify(f)))) {
            changedToQuad++;
            const v=a.vertices.map(v=>v.map(Math.fround)),n=normal(v,[f[0],f[1],f[2]]),m=normal(v,[f[2],f[3],f[0]]);
            const delta=Math.hypot(...sub(n,m));maxNormalDelta=Math.max(maxNormalDelta,delta);
            assert.ok(delta<=.002,'bounded float normal equivalence');
            const ta=basis(v,[f[0],f[1],f[2]]),tb=basis(v,[f[2],f[3],f[0]]);
            assert.equal(ta.hand,tb.hand,'historical triangles retain handedness');
            for(const key of ['t','b']) {
                const difference=Math.hypot(...sub(ta[key],tb[key]));
                maxTangentDelta=Math.max(maxTangentDelta,difference);
                assert.ok(difference<.00202,'bounded historical tangent equivalence');
            }
        }
    }
    const visible=m=>({parts:m.parts.filter(p=>!['reload_magazine','empty_old_mag'].includes(p.bone))});
    const bc=meshCounts(before),ac=meshCounts(after);
    assert.equal(ac.triangleEquivalent,bc.triangleEquivalent);
    console.log(JSON.stringify({id,scope:'all sidecar parts including hidden helpers',before:bc,after:ac,
        recovered:changedToQuad,unsafeOldQuadsTriangulated:changedToTriangles,maxNormalDelta,maxTangentDelta,
        vertexReductionPercent:100*(bc.vertices-ac.vertices)/bc.vertices,
        bytesBefore:Buffer.byteLength(beforeText),bytesAfter:fs.statSync(file).size,
        defaultVisibleBefore:meshCounts(visible(before)),defaultVisibleAfter:meshCounts(visible(after)),diagnostics}));
}
