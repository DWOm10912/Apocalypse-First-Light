import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {readFile,copyFile,mkdir,readdir,realpath} from 'node:fs/promises';
import {constants} from 'node:fs';
import path from 'node:path';
const exec=promisify(execFile);
export class ReferenceClient {
  constructor(root,gameDir,python,sources){this.root=root;this.gameDir=gameDir;this.python=python;this.sources=sources;this.output=path.join(root,'build/reference_imports');this.lastScan=null;}
  id(value){if(typeof value!=='string'||!value.match(/^[a-z][a-z0-9_]{0,63}$/))throw new Error('INVALID_REFERENCE_ID');return value;}
  async invoke(args){const {stdout}=await exec(this.python,['-m','tools.afl_reference_map_importer',...args],{cwd:this.root,timeout:120000,maxBuffer:1024*1024,windowsHide:true});return JSON.parse(stdout);}
  async call(tool,a,bridge){
    if(tool==='reference_import_status')return {sources:this.sources.map((p,i)=>({source_id:i,filename:path.basename(p)})),reference_only:true,python_configured:!!this.python};
    if(tool==='reference_map_scan'||tool==='reference_extract'){
      if(!Number.isInteger(a.source_id)||a.source_id<0||a.source_id>=this.sources.length)throw new Error('SOURCE_NOT_ALLOWLISTED: add --reference-source at server launch');
      const source=this.sources[a.source_id];
      if(tool==='reference_map_scan'){const r=await this.invoke(['scan',source,'--radius',String(a.radius??128),'--y-min',String(a.y_min??0),'--y-max',String(a.y_max??192)]);this.lastScan=r.report;return r;}
      if(!Array.isArray(a.bounds)||a.bounds.length!==6||!a.bounds.every(Number.isInteger))throw new Error('EXPLICIT_BOUNDS_REQUIRED');
      return this.invoke(['extract',source,'--bounds',...a.bounds.map(String),'--reference-id',this.id(a.reference_id),'--ground-policy',a.ground_policy??'BUILDING_PLUS_PAD']);
    }
    if(tool==='reference_candidates'){
      if(!this.lastScan)throw new Error('SCAN_FIRST');return JSON.parse(await readFile(this.lastScan,'utf8'));
    }
    const id=this.id(a.reference_id),folder=path.join(this.output,id),structure=path.join(folder,'reference_structure.json'),registry=path.join(this.gameDir,'afl_authoring_bridge/target_registry_1_20_1.json');
    const realFolder=await realpath(folder);if(!realFolder.startsWith((await realpath(this.output))+path.sep))throw new Error('REFERENCE_PATH_REJECTED');
    // Only named artifact directories created by importer; no arbitrary filesystem read tool.
    const mapping=path.join(this.root,'tools/afl_reference_map_importer/mappings/mc_1_21_4_to_1_20_1.json');
    if(tool==='reference_compatibility_report')return this.invoke(['audit',structure,'--registry',registry,'--mapping',mapping,'--output',path.join(folder,`audit-${Date.now()}.json`)]);
    if(tool==='reference_prepare_paste'){
      const artifact=path.join(folder,`prepared-${Date.now()}.json`);const result=await this.invoke(['prepare',structure,'--registry',registry,'--mapping',mapping,'--output',artifact]);
      const dest=path.join(this.gameDir,'afl_reference_import/prepared');await mkdir(dest,{recursive:true});await copyFile(artifact,path.join(dest,id+'.json'),constants.COPYFILE_EXCL);return {...result,staged_path:path.join(dest,id+'.json'),auto_paste:false};
    }
    throw new Error('UNKNOWN_REFERENCE_TOOL');
  }
}
