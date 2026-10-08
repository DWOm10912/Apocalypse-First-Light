// Java source-file launcher for isolated geometry checks; no Gradle, game bootstrap or network dependency.
// --compiled checks the same methods from build/classes/java/main after the one requested compileJava.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
assert(process.argv.slice(2).every(x=>x==='--compiled'));
const jars=d=>fs.existsSync(d)?fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?jars(path.join(d,e.name)):e.name.endsWith('.jar')&&!e.name.includes('sources')?[path.join(d,e.name)]:[]):[];
const gson=jars(path.join(process.env.GRADLE_USER_HOME||path.join(os.homedir(),'.gradle'),'caches/modules-2/files-2.1/com.google.code.gson/gson')).sort().at(-1);
assert(gson,'Existing Gson cache required; no download attempted');
const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'afl-m1a-check-')),created=[];
try {
  let sources=[fs.readFileSync(path.join(root,'tools/highway-v2-m1a/GeometryCheck.java'),'utf8')];
  let cp=gson;
  if(process.argv.includes('--compiled'))cp+=path.delimiter+path.join(root,'build/classes/java/main');
  else {
    for(const name of ['AflMeshPart','AflMeshModel','AflMeshLoader'])sources.push(fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/client/mesh',name+'.java'),'utf8'));
    for(const name of ['ChunkMeshGeometry','RoadMeshAsset'])sources.push(fs.readFileSync(path.join(root,'src/dev/java/com/antaurora/apofirstlight/dev/highwaymesh',name+'.java'),'utf8'));
    sources=sources.map(s=>{
      const isConsumer=s.includes('class RoadMeshAsset')||s.includes('class GeometryCheck');
      s=s.replace(/^package .*;\r?\n/gm,'').replace(/^import (static )?com\.antaurora\..*;\r?\n/gm,'').replace(/public final class /g,'final class ');
      if(isConsumer){for(const n of ['Vertex','Triangle','Tile','Normal'])s=s.replace(new RegExp('\\b'+n+'\\b','g'),'ChunkMeshGeometry.'+n);for(const n of ['clip','triangulate'])s=s.replace(new RegExp('(?<![.\\w])'+n+'\\(','g'),'ChunkMeshGeometry.'+n+'(');}
      return s;
    });
  }
  // Collect imports at the start of the temporary source compilation unit. Class bodies are otherwise unchanged.
  const imports=new Set();sources=sources.map(s=>s.replace(/^import .*;\r?\n/gm,m=>{imports.add(m.trim());return '';}));
  const file=path.join(temp,'GeometryCheck.java');created.push(file);fs.writeFileSync(file,[...imports].join('\n')+'\n'+sources.join('\n'));
  const out=path.join(root,'tools/highway-v2-m1a/validation.json');
  const r=spawnSync(java,['--class-path',cp,file,path.join(root,'src/dev/highway_mesh_resources/assets/afl_highway_demo/prototype'),out],{encoding:'utf8',timeout:90000,windowsHide:true});
  const output=(r.stdout??'')+(r.stderr??'');assert.equal(r.status,0,output||String(r.error));assert(output.includes('M1A_OFFLINE_PASS'),output);console.log(output.trim());
} finally {for(const f of created)fs.unlinkSync(f);fs.rmdirSync(temp);}
