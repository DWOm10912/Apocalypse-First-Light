// Source-only Blockbench reference presentation. Never changes gun/locator/keys/UV.
// Emits an apply_patch patch instead of writing files; --check is read-only.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
export function presentationScale() {
 const java=fs.readFileSync(path.join(root,'src/main/java/com/antaurora/apofirstlight/weapon/client/NativePlayerArmRenderer.java'),'utf8');
 return ['X','Y','Z'].map(a=>Number(java.match(new RegExp('PRESENTATION_'+a+' = ([.0-9]+)F'))[1]));
}
export function previewFactors(source) {
 const displays=['firstperson_righthand','firstperson_lefthand'].map(n=>source.display?.[n]?.scale||[1,1,1]);
 assert(displays.flat().every(n=>n>0&&Math.abs(n-displays[0][0])<1e-7),'Uniform, equal FP Display scales required for saved reference preview');
 return presentationScale().map(n=>n/displays[0][0]);
}
export function syncReferences(source) {
 const next=structuredClone(source),factor=previewFactors(source);
 for(const side of ['right','left'])for(const[suffix,width]of[['',4],['_slim',3]]) {
  const anchor=next.groups.find(g=>g.name===side+'_hand_anchor');
  const ref=next.groups.find(g=>g.name===side+'_arm_reference'+suffix);
  const cube=next.elements.find(e=>e.name===side+'_arm_reference'+suffix+'_cube');
  assert(anchor&&ref&&cube&&ref.export===false&&cube.export===false,'Canonical source-only reference required');
  assert.deepEqual(ref.origin,anchor.origin);
  assert((ref.rotation||[0,0,0]).every(n=>n===0)&&(cube.rotation||[0,0,0]).every(n=>n===0),'Never redesign reference pose');
  cube.from=[-width/2,-12,-2].map((n,i)=>anchor.origin[i]+n*factor[i]);
  cube.to=[width/2,0,2].map((n,i)=>anchor.origin[i]+n*factor[i]);
 }
 return next;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const args=process.argv.slice(2),mode=args.shift();assert(['--check','--patch'].includes(mode));
 for(const arg of args) {
  const p=path.resolve(root,arg);assert(p.startsWith(path.join(root,'src/main/blockbench')+path.sep)&&p.endsWith('.bbmodel'));
  const text=fs.readFileSync(p,'utf8'),source=JSON.parse(text.replace(/^\uFEFF/,'')),next=syncReferences(source);
  if(mode==='--check')assert.deepEqual(source,next,'Stale source reference presentation: '+arg);
  else {const replacement=JSON.stringify(next)+(text.endsWith('\n')?'\n':'');
   if(text!==replacement)console.log('*** Begin Patch\n*** Update File: '+p.replaceAll('\\','/')+'\n@@\n'+text.trimEnd().split(/\r?\n/).map(l=>'-'+l).join('\n')+'\n'+replacement.trimEnd().split(/\r?\n/).map(l=>'+'+l).join('\n')+'\n*** End Patch');
  }
 }
}
