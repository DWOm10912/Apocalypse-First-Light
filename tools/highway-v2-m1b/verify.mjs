// Targeted real Java kernels, existing Loader and real RouteGraph; no Forge launch or Gradle task.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {exportFixtures} from './export.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const check=process.argv.includes('--check');
assert(process.argv.slice(2).every(a=>a==='--check'));
const jars=d=>fs.existsSync(d)?fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?jars(path.join(d,e.name)):e.name.endsWith('.jar')&&!e.name.includes('sources')?[path.join(d,e.name)]:[]):[];
const gson=jars(path.join(process.env.GRADLE_USER_HOME||path.join(os.homedir(),'.gradle'),'caches/modules-2/files-2.1/com.google.code.gson/gson')).sort().at(-1);
assert(gson,'Existing Gson cache required; no download');
const bin=n=>process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',n+(process.platform==='win32'?'.exe':'')):n;
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'afl-m1b-'));
function run(tool,args){const r=spawnSync(bin(tool),args,{cwd:root,encoding:'utf8',timeout:240000,windowsHide:true,maxBuffer:8*1024*1024});process.stdout.write(r.stdout||'');assert.equal(r.status,0,r.stderr||String(r.error));}
try{
  const classes=path.join(temp,'classes');fs.mkdirSync(classes);
  run('javac',['-encoding','UTF-8','-cp',gson,'-sourcepath',['src/main/java','src/dev/java'].join(path.delimiter),'-d',classes,'tools/highway-v2-m1b/RouteGeometryCheck.java','tools/highway-v2-m1a/GeometryCheck.java']);
  const cp=classes+path.delimiter+gson;
  run('java',['-Xmx1G','-cp',cp,'GeometryCheck','src/dev/highway_mesh_resources/assets/afl_highway_demo/prototype',path.join(temp,'m1a.json')]);
  const old=JSON.parse(fs.readFileSync('tools/highway-v2-m1a/validation.json','utf8')),now=JSON.parse(fs.readFileSync(path.join(temp,'m1a.json'),'utf8'));
  assert.equal(now.modelFingerprint,old.modelFingerprint,'M1-A loader/render geometry regression');
  run('java',['-Xmx1G','-cp',cp,'RouteGeometryCheck',root,path.join(temp,'out')]);
  const output=path.join(root,'tools/highway-v2-m1b/results');
  const files=new Map();
  for(const name of ['validation.json','real-route-plan.json','export-input.json'])files.set('tools/highway-v2-m1b/results/'+name,fs.readFileSync(path.join(temp,'out',name)));
  for(const [name,data] of exportFixtures(JSON.parse(files.get('tools/highway-v2-m1b/results/export-input.json')),root))files.set(name,Buffer.from(data));
  for(const [name,data] of files){const file=path.join(root,name);if(check)assert(fs.existsSync(file)&&fs.readFileSync(file).equals(data),'stale '+name);else{fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,data);}}
  run('java',['-cp',cp,'RouteGeometryCheck','--meshes',path.join(root,'tools/highway-v2-m1b/results')]);
  console.log(check?'M1B_REPRODUCIBLE_OUTPUT_PASS':'M1B_OUTPUT_WRITTEN');
} finally {
  // Only the exact private directory returned by mkdtemp; no repository cleanup.
  const resolved=fs.realpathSync(temp);
  assert.equal(path.dirname(resolved),fs.realpathSync(os.tmpdir()),'temp cleanup escaped expected directory');
  assert(path.basename(resolved).startsWith('afl-m1b-'),'unexpected temp cleanup target');
  fs.rmSync(resolved,{recursive:true});
}