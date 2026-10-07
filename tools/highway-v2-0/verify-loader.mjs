// Execute the real, unchanged loader against ONLY this demo, using the existing verifier's JShell technique.
// No Gradle, Minecraft startup, dependency downloads, or Java source changes in the repository.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {root,demo} from './generate.mjs';
const metadata=JSON.parse(fs.readFileSync(path.join(root,demo,'metadata.json'),'utf8'));
const gradle=process.env.GRADLE_USER_HOME||path.join(os.homedir(),'.gradle');
const jars=dir=>fs.existsSync(dir)?fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?jars(path.join(dir,e.name)):e.name.endsWith('.jar')&&!e.name.includes('sources')?[path.join(dir,e.name)]:[]):[];
const gson=jars(path.join(gradle,'caches/modules-2/files-2.1/com.google.code.gson/gson')).sort().at(-1);
assert(gson,'Set GRADLE_USER_HOME to an existing cache containing Gson; no download is attempted');
const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
const temporary=fs.mkdtempSync(path.join(os.tmpdir(),'afl-highway-v2-0-loader-')),created=[];
const write=(name,text)=>{const file=path.join(temporary,name);fs.writeFileSync(file,text);created.push(file);return file.replaceAll('\\','/');};
try {
  const commands=['AflMeshPart','AflMeshModel','AflMeshLoader'].map(name=>'/open '+write(name+'.java',fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/client/mesh',name+'.java'),'utf8').replace(/^package .*;\r?\n/m,'')));
  commands.push('{ try {');
  for(const m of metadata.modules)commands.push(`try(var sr=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(${JSON.stringify(path.join(root,m.mesh))}));var gr=java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(${JSON.stringify(path.join(root,m.geometry))}))){var mesh=AflMeshLoader.load("${m.id}",sr,gr);int triangles=0,faces=0;for(var part:mesh.parts("road")){triangles+=part.triangleEquivalent();faces+=part.faceCount();for(int i=0;i<part.cornerCount();i++){double n=0;for(int k=5;k<8;k++)n+=part.value(i,k)*part.value(i,k);if(Math.abs(n-1)>1e-5)throw new AssertionError("normal");}}if(triangles!=${m.triangleEquivalent}||mesh.partCount()!=5)throw new AssertionError("counts");System.out.println("LOADED ${m.id} triangles="+triangles+" bakedFaces="+faces);}`);
  commands.push('System.out.println("HIGHWAY_DEMO_JAVA_LOADER_PASS"); } catch(Throwable error){error.printStackTrace();} }','/exit');
  const entry=write('verify.jsh',commands.join('\n'));
  const runner=write('Run.java','class Run { public static void main(String[] args) throws Exception { jdk.jshell.tool.JavaShellToolBuilder.builder().persistence(new java.util.HashMap<String,String>()).locale(java.util.Locale.ROOT).run(args); } }');
  const run=spawnSync(java,['--add-modules','jdk.jshell','--class-path',gson,runner,'--execution','local','--class-path',gson,'--feedback','concise',entry],{encoding:'utf8',timeout:45000,windowsHide:true});
  const output=(run.stdout??'')+(run.stderr??'');assert.equal(run.status,0,output||String(run.error));assert(output.includes('HIGHWAY_DEMO_JAVA_LOADER_PASS'),output);
  const lines=output.split(/\r?\n/).filter(l=>l.startsWith('LOADED ')||l==='HIGHWAY_DEMO_JAVA_LOADER_PASS');
  fs.writeFileSync(path.join(root,demo,'loader-validation.json'),JSON.stringify({status:'PASS_REAL_JAVA_LOADER_ONLY',lines,clientRun:false,gradleRun:false},null,2)+'\n');
  console.log(lines.join('\n'));
} finally {for(const file of created)fs.unlinkSync(file);fs.rmdirSync(temporary);}
