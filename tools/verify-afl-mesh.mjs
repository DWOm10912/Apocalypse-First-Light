// Bounded offline checks. --java-loader evaluates the pure loader sources in JShell;
// --java-renderer also uses cached Minecraft/GeckoLib math classes, without starting a client.
// Neither mode invokes Gradle or emits project class files.
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import assert from 'node:assert/strict';
import {convert,read,serialize,triangulate} from './export-afl-mesh.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const fixture=path.join(root,'src/dev/resources/afl_mesh_core');
const source=read(path.join(root,'src/main/blockbench/dev/afl_mesh_core_fixture.bbmodel'));
const geo=read(path.join(fixture,'fixture.geo.json'));
const sidecar=convert(source,geo);
assert.equal(fs.readFileSync(path.join(fixture,'fixture.aflmesh.json'),'utf8'),serialize(sidecar),'stale fixture');
assert.equal(serialize(convert(source,geo)),serialize(sidecar),'nondeterministic export');
assert.equal(sidecar.parts.length,3);
assert.equal(geo['minecraft:geometry'][0].bones[0].cubes.length,1);
assert.equal(sidecar.parts.reduce((s,p)=>s+p.triangles.length,0),4);
let rejected=0;
function bad(change,pattern){const s=structuredClone(source),g=structuredClone(geo);change(s,g);assert.throws(()=>convert(s,g),pattern);rejected++;}
bad(s=>s.textures.push({...s.textures[0]}),/one texture/);
bad(s=>s.textures[0].render_mode='emissive',/material mode unsupported/);
bad(s=>{s.textures[0].width=16;s.textures[0].height=32;},/animated strip/);
bad(s=>delete s.elements[1].faces.surface.uv.v0,/missing UV/);
bad(s=>s.elements[1].faces.surface.vertices[0]='unknown',/invalid vertex/);
bad(s=>s.elements[1].faces.surface.texture=7,/unknown\/multiple texture/);
bad(s=>s.elements[1].vertices.v1=s.elements[1].vertices.v0.slice(),/zero-area|duplicate/);
bad(s=>s.elements[2].vertices.v3[2]=1,/non-planar/);
bad(s=>s.elements[2].faces.surface.vertices=['v0','v2','v1','v3'],/self-intersecting|zero-area/);
bad(s=>s.elements[1].weights={v0:{fixture_root:1}},/unsupported/);
bad(s=>s.elements[1].morph_targets=[{}],/unsupported/);
bad(s=>s.elements[1].subdivision=2,/unsupported/);
bad(s=>s.elements[1].dynamic_topology=true,/unsupported/);
bad(s=>s.elements[1].scale=[-1,1,1],/unsupported field/);
bad(s=>s.elements[1].shading='smooth',/smooth shading unsupported/);
bad(s=>s.animations=[{animators:{[s.elements[1].uuid]:{type:'bone',keyframes:[]}}}],/mesh\/topology/);
bad(s=>s.groups[1].bedrock_binding='query.item_slot',/unsupported field/);
bad(s=>s.groups[1].name='unknown',/unknown parent bone/);
bad(s=>s.groups[1].origin[0]+=1,/pivot mismatch/);
bad(s=>s.groups[1].rotation[1]+=1,/rotation mismatch/);
bad((s,g)=>g['minecraft:geometry'][0].bones[1].parent='visibility_test',/cyclic/);
bad(s=>s.elements.push({...s.elements[1],uuid:'orphan',name:'orphan'}),/orphan/);
const renamed=structuredClone(source);renamed.groups[1].name='author_child';
assert.deepEqual(convert(renamed,geo,{author_child:'fixture_child'}),sidecar,'explicit group mapping');
const concave=[[0,0,0],[3,0,0],[3,3,0],[1.5,1,0],[0,3,0]];
const tris=triangulate(concave);assert.equal(tris.length,3);
const area=tris.reduce((sum,[a,b,c])=>sum+Math.abs((concave[b][0]-concave[a][0])*(concave[c][1]-concave[a][1])-(concave[b][1]-concave[a][1])*(concave[c][0]-concave[a][0]))/2,0);
assert.equal(area,6,'concave triangulation must preserve area');

// Independent stored THREE reference vs the documented GeckoLib 4.7.4 traversal.
// Apply about absolute pivots, child first, with Gecko's loaded (-rx,-ry,rz).
const bones=new Map(geo['minecraft:geometry'][0].bones.map(b=>[b.name,b]));
function posed(vertex,bone){
    let p=vertex.slice(0,3).map(v=>v*16);
    const pivot=bones.get(bone).pivot;p=p.map((v,i)=>v+(i===0?-pivot[i]:pivot[i]));
    for(let b=bones.get(bone);b;b=bones.get(b.parent)){
        const center=[-b.pivot[0],b.pivot[1],b.pivot[2]],r=(b.rotation??[0,0,0]).map((v,i)=>(i<2?-v:v)*Math.PI/180);
        let [x,y,z]=p.map((v,i)=>v-center[i]);
        [y,z]=[Math.cos(r[0])*y-Math.sin(r[0])*z,Math.sin(r[0])*y+Math.cos(r[0])*z];
        [x,z]=[Math.cos(r[1])*x+Math.sin(r[1])*z,-Math.sin(r[1])*x+Math.cos(r[1])*z];
        [x,y]=[Math.cos(r[2])*x-Math.sin(r[2])*y,Math.sin(r[2])*x+Math.cos(r[2])*y];
        p=[x,y,z].map((v,i)=>v+center[i]);
    }
    return p.map(v=>v/16);
}
const expected=read(path.join(fixture,'fixture.expected.json')).points;
let maxError=0;
for(const part of sidecar.parts)part.vertices.forEach((v,i)=>posed(v,part.bone).forEach((n,j)=>{
    const error=Math.abs(n-expected[part.name][i][j]);maxError=Math.max(maxError,error);assert(error<1e-8,`${part.name}: coordinate/pivot mismatch`);
}));
// The distant visibility facet must enlarge framing beyond the Cube-only envelope.
const cube=geo['minecraft:geometry'][0].bones[0].cubes[0],cubePositions=[];
for(let i=0;i<8;i++){
    const p=[-(cube.origin[0]+((i&1)?cube.size[0]:0)),cube.origin[1]+((i&2)?cube.size[1]:0),cube.origin[2]+((i&4)?cube.size[2]:0)];
    const pivot=source.groups[0].origin;
    cubePositions.push(posed(p.map((v,k)=>(v-pivot[k])/16),'fixture_root'));
}
assert(Math.min(...Object.values(expected).flat().map(v=>v[2]))<Math.min(...cubePositions.map(v=>v[2]))-0.5,'Mesh must contribute maintenance bounds');
const native=fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/weapon/client/NativeGunContextRenderer.java'),'utf8');
const maintenance=fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/client/MaintenanceGunRendering.java'),'utf8');
const cache=fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/client/mesh/AflMeshCache.java'),'utf8');
assert.match(native,/super\.renderCubesOfBone[\s\S]*if \(mesh != null\) AflMeshRenderer\.render/);
assert.match(native,/frozen\.topLevelBones\(\)/);
assert.match(maintenance,/AflMeshRenderer\.collectBounds/);
assert.match(maintenance,/generation[\s\S]*BOUNDS\.clear\(\); LONGITUDINAL_CENTERS\.clear/);
assert.match(cache,/volatile Snapshot/);
assert.match(cache,/current = new Snapshot\(current\.generation\(\) \+ 1, models\)/);
const assets=path.join(root,'src/main/resources/assets/apocalypse_firstlight/meshes');
for(const id of ['br51_01','hr55','p9_01','silverwood_12'])assert(!fs.existsSync(path.join(assets,id+'.aflmesh.json')),'Round 1 must not opt in official weapons');
console.log(`PASS: deterministic fixture; ${rejected} invalid inputs rejected; concave triangulation; non-zero element/group pivots and rotations (max error ${maxError}); Mesh bounds; adapter/no-sidecar/reload static checks.`);

if(process.argv.includes('--java-loader')||process.argv.includes('--java-renderer')) {
    const gradle=process.env.GRADLE_USER_HOME??path.join(os.homedir(),'.gradle');
    const gsonRoot=path.join(gradle,'caches/modules-2/files-2.1/com.google.code.gson/gson');
    const findJars=dir=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?findJars(path.join(dir,e.name)):e.name.endsWith('.jar')&&!e.name.includes('sources')?[path.join(dir,e.name)]:[]);
    const gson=findJars(gsonRoot).sort().at(-1);assert(gson,'cached Gson required; no download');
    const temp=fs.mkdtempSync(path.join(os.tmpdir(),'afl-mesh-core-check-'));
    const files=[];
    const write=(name,text)=>{const f=path.join(temp,name);fs.writeFileSync(f,text);files.push(f);return f.replaceAll('\\','/');};
    try {
        const javaRoot=path.join(root,'src/main/java/com/antaurora/apofirstlight/client/mesh');
        const renderCheck=process.argv.includes('--java-renderer');
        const opens=['AflMeshPart','AflMeshModel','AflMeshLoader',...(renderCheck?['AflMeshRenderer']:[])].map(name=>{
            const text=fs.readFileSync(path.join(javaRoot,name+'.java'),'utf8').replace(/^package .*;\r?\n/m,'');
            return '/open '+write(name+'.java',text);
        });
        const json=JSON.stringify(fs.readFileSync(path.join(fixture,'fixture.aflmesh.json'),'utf8'));
        const geometry=JSON.stringify(fs.readFileSync(path.join(fixture,'fixture.geo.json'),'utf8'));
        const reference=JSON.stringify(fs.readFileSync(path.join(fixture,'fixture.expected.json'),'utf8'));
        const script=[...opens,`String geometryText = ${geometry};`,`String meshText = ${json};`,
            'void require(boolean b, String m) { if (!b) throw new AssertionError(m); }',
            'void rejected(com.google.gson.JsonObject d, String fragment) throws Exception { try { AflMeshLoader.load("fixture:meshes/fixture.aflmesh.json",new java.io.StringReader(d.toString()),new java.io.StringReader(geometryText)); throw new AssertionError("accepted invalid mesh"); } catch (IllegalArgumentException e) { require(e.getMessage().contains("fixture:meshes/fixture.aflmesh.json") && e.getMessage().contains(fragment),e.getMessage()); } }',
            '{ try {',
            'var m=AflMeshLoader.load("fixture",new java.io.StringReader(meshText),new java.io.StringReader(geometryText)); require(m.partCount()==3,"part count"); require(m.parts("missing").isEmpty(),"unknown lookup");',
            'var quad=m.parts("fixture_child").get(0); require(quad.cornerCount()==6,"quad bake");',
            'for(var b:java.util.List.of("fixture_root","fixture_child","visibility_test")) for(var p:m.parts(b)) for(int i=0;i<p.cornerCount();i++){ double n=0;for(int j=5;j<8;j++)n+=p.value(i,j)*p.value(i,j);require(Math.abs(n-1)<1e-5,"unit normal");var a=p.bounds();require(p.value(i,0)>=a.minX()&&p.value(i,0)<=a.maxX()&&p.value(i,2)>=a.minZ()&&p.value(i,2)<=a.maxZ(),"bounds contain vertices"); }',
            'var original=com.google.gson.JsonParser.parseString(meshText).getAsJsonObject();',
            'var d=original.deepCopy();d.addProperty("format_version",2);rejected(d,"format_version");',
            'd=original.deepCopy();d.getAsJsonArray("parts").get(0).getAsJsonObject().addProperty("bone","not_a_bone");rejected(d,"bone=not_a_bone part=rotated_child_quad");',
            'd=original.deepCopy();d.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("triangles").get(0).getAsJsonArray().set(0,new com.google.gson.JsonPrimitive(999));rejected(d,"invalid index");',
            'd=original.deepCopy();d.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("triangles").set(0,com.google.gson.JsonParser.parseString("[0,0,0]"));rejected(d,"zero-area");',
            'd=original.deepCopy();d.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("vertices").get(0).getAsJsonArray().remove(4);rejected(d,"x,y,z,u,v");',
            'd=original.deepCopy();d.addProperty("skinning",true);rejected(d,"requires exactly");',
            'd=original.deepCopy();d.getAsJsonArray("texture_size").set(0,new com.google.gson.JsonPrimitive(32));rejected(d,"texture_size mismatch");',
            'd=original.deepCopy();d.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonArray("vertices").get(0).getAsJsonArray().set(3,new com.google.gson.JsonPrimitive(2));rejected(d,"UV out");',
            'System.out.println("JAVA_LOADER_CHECKS_PASS: actual V1 parser, flat-normal bake, local bounds, eight invalid-sidecar diagnostics");',
            '} catch(Throwable e) { e.printStackTrace(); } }'];
        if(renderCheck)script.push(
            // Real Minecraft PoseStack, GeckoLib GeoBone/RenderUtils, and a recording VertexConsumer.
            'class Capture implements com.mojang.blaze3d.vertex.VertexConsumer { java.util.List<float[]> rows=new java.util.ArrayList<>(); public void vertex(float x,float y,float z,float r,float g,float b,float a,float u,float v,int o,int l,float nx,float ny,float nz){rows.add(new float[]{x,y,z,r,g,b,a,u,v,o,l,nx,ny,nz});} public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x,double y,double z){return this;} public com.mojang.blaze3d.vertex.VertexConsumer color(int r,int g,int b,int a){return this;} public com.mojang.blaze3d.vertex.VertexConsumer uv(float u,float v){return this;} public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u,int v){return this;} public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u,int v){return this;} public com.mojang.blaze3d.vertex.VertexConsumer normal(float x,float y,float z){return this;} public void endVertex(){} public void defaultColor(int r,int g,int b,int a){} public void unsetDefaultColor(){} }',
            '{ try {',
            'var model=AflMeshLoader.load("fixture",new java.io.StringReader(meshText),new java.io.StringReader(geometryText));',
            `var reference=com.google.gson.JsonParser.parseString(${reference}).getAsJsonObject().getAsJsonObject("points");`,
            'var hierarchy=new java.util.LinkedHashMap<String,software.bernie.geckolib.cache.object.GeoBone>();for(var entry:com.google.gson.JsonParser.parseString(geometryText).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")){var j=entry.getAsJsonObject();var b=new software.bernie.geckolib.cache.object.GeoBone(j.has("parent")?hierarchy.get(j.get("parent").getAsString()):null,j.get("name").getAsString(),false,0.0,false,false);var p=j.getAsJsonArray("pivot");var r=j.getAsJsonArray("rotation");b.setPivotX(-p.get(0).getAsFloat());b.setPivotY(p.get(1).getAsFloat());b.setPivotZ(p.get(2).getAsFloat());if(r!=null){b.setRotX((float)Math.toRadians(-r.get(0).getAsFloat()));b.setRotY((float)Math.toRadians(-r.get(1).getAsFloat()));b.setRotZ((float)Math.toRadians(r.get(2).getAsFloat()));}hierarchy.put(b.getName(),b);}',
            'for(var leaf:hierarchy.values()){var chain=new java.util.ArrayList<software.bernie.geckolib.cache.object.GeoBone>();for(var b=leaf;b!=null;b=b.getParent())chain.add(0,b);var p=new com.mojang.blaze3d.vertex.PoseStack();for(var b:chain)software.bernie.geckolib.util.RenderUtils.prepMatrixForBone(p,b);var out=new Capture();AflMeshRenderer.render(model,leaf,p,out,0,0,1,1,1,1);for(var part:model.parts(leaf.getName()))for(var point:reference.getAsJsonArray(part.name())){var xyz=point.getAsJsonArray();boolean found=false;for(var row:out.rows)if(Math.abs(row[0]-xyz.get(0).getAsDouble())<2e-6&&Math.abs(row[1]-xyz.get(1).getAsDouble())<2e-6&&Math.abs(row[2]-xyz.get(2).getAsDouble())<2e-6)found=true;require(found,"real Gecko traversal/source reference mismatch");}}',
            'var bone=new software.bernie.geckolib.cache.object.GeoBone(null,"fixture_child",false,0.0,false,false);bone.setPivotX(4);bone.setPivotY(8);bone.setPivotZ(-2);bone.setRotX(0.4f);bone.setRotY(0.6f);bone.setRotZ(-0.2f);bone.setPosX(2);bone.setPosY(3);bone.setPosZ(-1);',
            'for(float[] scale:java.util.List.of(new float[]{1,1,1},new float[]{-1,1,1},new float[]{-2,-2,-2},new float[]{-2,3,0.5f},new float[]{-2,-3,0.5f})){',
            'var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.mulPose(new org.joml.Quaternionf().rotationXYZ(0.2f,-0.3f,0.4f));pose.scale(scale[0],scale[1],scale[2]);software.bernie.geckolib.util.RenderUtils.prepMatrixForBone(pose,bone);var before=new org.joml.Matrix4f(pose.last().pose());var capture=new Capture();AflMeshRenderer.render(model,bone,pose,capture,1234,5678,0.2f,0.4f,0.6f,0.8f);require(capture.rows.size()==8,"quad submission count");require(before.equals(pose.last().pose()),"pose leak");',
            'var part=model.parts("fixture_child").get(0);for(int i=0;i<8;i+=4){var a=capture.rows.get(i);var b=capture.rows.get(i+1);var c=capture.rows.get(i+2);require(java.util.Arrays.equals(c,capture.rows.get(i+3)),"degenerate corner");var n=new org.joml.Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2]).cross(c[0]-a[0],c[1]-a[1],c[2]-a[2]).normalize();require(n.dot(a[11],a[12],a[13])>0.9999,"winding/normal mismatch");require(a[9]==5678&&a[10]==1234&&a[3]==0.2f&&a[6]==0.8f,"light/overlay/color");require(a[7]==part.value(i/4*3,3)&&a[8]==part.value(i/4*3,4),"UV");}',
            'var boxes=new java.util.ArrayList<net.minecraft.world.phys.AABB>();AflMeshRenderer.collectBounds(model,bone,pose,boxes);require(boxes.size()==1,"mesh bounds");var bounds=boxes.get(0).inflate(1e-6);for(var v:capture.rows)require(bounds.contains(v[0],v[1],v[2]),"transformed bounds miss vertex");',
            'bone.setHidden(true);capture.rows.clear();AflMeshRenderer.render(model,bone,pose,capture,0,0,1,1,1,1);require(capture.rows.isEmpty(),"hidden bone");bone.setHidden(false);',
            'capture.rows.clear();AflMeshRenderer.render(null,bone,pose,capture,0,0,1,1,1,1);require(capture.rows.isEmpty(),"no sidecar");',
            'pose.scale(0,1,1);AflMeshRenderer.render(model,bone,pose,capture,0,0,1,1,1,1);require(capture.rows.isEmpty(),"singular transform");}',
            'System.out.println("JAVA_RENDERER_CHECKS_PASS: actual renderer, real PoseStack/GeoBone, reflected/nonuniform transforms, winding/normals, UV/color/light/overlay, hidden/null, bounds");',
            '} catch(Throwable e) { e.printStackTrace(); } }');
        script.push('/exit');
        const entry=write('checks.jsh',script.join('\n'));
        // JShell's CLI writes Windows registry preferences even for batch input. An in-memory
        // persistence map avoids touching user preferences and works in the restricted workspace.
        const runner=write('RunMeshChecks.java','class RunMeshChecks { public static void main(String[] args) throws Exception { jdk.jshell.tool.JavaShellToolBuilder.builder().persistence(new java.util.HashMap<String,String>()).locale(java.util.Locale.ROOT).run(args); } }');
        const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
        const classpath=[gson];
        if(renderCheck){
            classpath.push(path.join(gradle,'caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.22_mapped_official_1.20.1/forge-1.20.1-47.4.22_mapped_official_1.20.1-recomp.jar'));
            classpath.push(...findJars(path.join(gradle,'caches/forge_gradle/deobf_dependencies/software/bernie/geckolib')));
            for(const dep of ['org.joml/joml','com.google.guava/guava','it.unimi.dsi/fastutil','org.slf4j/slf4j-api','com.mojang/logging','com.mojang/datafixerupper','org.apache.commons/commons-lang3'])
                classpath.push(...findJars(path.join(gradle,'caches/modules-2/files-2.1',dep)));
        }
        const run=spawnSync(java,['--add-modules','jdk.jshell',runner,'--class-path',classpath.join(path.delimiter),'--feedback','concise',entry],{encoding:'utf8',timeout:45000,windowsHide:true});
        const out=(run.stdout??'')+(run.stderr??'');
        assert.equal(run.status,0,out||String(run.error));assert(out.includes('JAVA_LOADER_CHECKS_PASS'),out);
        if(renderCheck)assert(out.includes('JAVA_RENDERER_CHECKS_PASS'),out);
        console.log(out.split(/\r?\n/).filter(s=>/JAVA_(LOADER|RENDERER)_CHECKS_PASS/.test(s)).join('\n'));
    } finally { for(const file of files)fs.unlinkSync(file);fs.rmdirSync(temp); }
}
