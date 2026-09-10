import argparse
from collections import Counter
from datetime import datetime,timezone
import json
from pathlib import Path
import re
import uuid
from .safe_source import open_source
from .anvil_reader import level_metadata,chunks,blocks
from .structure_detector import discover
from .block_compat import audit

ROOT=Path(__file__).resolve().parents[2]
DEFAULT_OUT=ROOT/'build/reference_imports'

def write(path,data):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    # No silently overwriting prior extraction/mapping reports.
    with path.open('x',encoding='utf8') as f:json.dump(data,f,ensure_ascii=False,separators=(',',':'))
    return str(path.resolve())

def extract(world,metadata,bounds,dimension,ground_policy):
    if len(bounds)!=6 or any(bounds[i]>bounds[i+3] for i in range(3)):raise ValueError('INVALID_BOUNDS')
    size=[bounds[i+3]-bounds[i]+1 for i in range(3)]
    if size[0]*size[1]*size[2]>2_000_000 or bounds[1]<-64 or bounds[4]>319:raise ValueError('EXTRACTION_LIMIT')
    palette=[];lookup={};result=[];seen=set();be_count=0;author_text=[]
    for cx,cz,root in chunks(world,dimension,bounds):
        seen.add((cx,cz))
        for x,y,z,state in blocks(cx,cz,root,bounds):
            if state['Name'] in ('minecraft:air','minecraft:cave_air','minecraft:void_air'):continue
            clean={'Name':state['Name'],'Properties':state.get('Properties',{})};key=json.dumps(clean,sort_keys=True)
            if key not in lookup:lookup[key]=len(palette);palette.append(clean)
            result.append([x-bounds[0],y-bounds[1],z-bounds[2],lookup[key]])
        for be in root.get('block_entities',[]):
            if not all(bounds[i]<=be.get(k,-10**9)<=bounds[i+3] for i,k in enumerate(('x','y','z'))):continue
            be_count+=1
            if 'sign' in be.get('id',''):
                # Preserve provenance as untrusted report data only, never game text or instructions.
                if len(author_text)<128:author_text.append({k:be[k] for k in ('id','front_text','back_text','Text1','Text2','Text3','Text4') if k in be})
    required={(x,z) for x in range(bounds[0]//16,bounds[3]//16+1) for z in range(bounds[2]//16,bounds[5]//16+1)}
    if required-seen:raise ValueError(f'CHUNK_DECODE_FAILED: missing chunks in explicit bounds: {sorted(required-seen)[:12]}')
    return {'format':'AFL_REFERENCE_STRUCTURE_V1','reference_only':True,'source_version':metadata['source_version'],'source_data_version':metadata['data_version'],'source_world':metadata['world_name'],'dimension':dimension,'bounds':bounds,'size':size,'origin':bounds[:3],'palette':palette,'blocks':result,'block_entities':[],'entities':[],'metadata':{'ground_policy':ground_policy,'bounds_confirmed':True,'source_block_entities_stripped':be_count,'source_sign_provenance_untrusted':author_text,'original_orientation':'UNKNOWN','redistribution_allowed':'unknown'}}

def main(argv=None):
    parser=argparse.ArgumentParser(description='Offline reference-only extraction; no save edits or automatic paste')
    sub=parser.add_subparsers(dest='command',required=True)
    for name in ('scan','extract'):
        p=sub.add_parser(name);p.add_argument('source');p.add_argument('--output',type=Path,default=DEFAULT_OUT);p.add_argument('--dimension',choices=['overworld','the_nether','the_end'],default='overworld')
        if name=='scan':p.add_argument('--radius',type=int,default=128);p.add_argument('--y-min',type=int,default=0);p.add_argument('--y-max',type=int,default=192)
        else:
            p.add_argument('--bounds',nargs=6,type=int,required=True);p.add_argument('--reference-id',required=True);p.add_argument('--ground-policy',choices=['BUILDING_ONLY','BUILDING_PLUS_PAD','FULL_SELECTION'],default='BUILDING_PLUS_PAD');p.add_argument('--source-url');p.add_argument('--source-author')
    p=sub.add_parser('audit');p.add_argument('structure',type=Path);p.add_argument('--registry',type=Path,required=True);p.add_argument('--mapping',type=Path);p.add_argument('--output',type=Path,required=True)
    p=sub.add_parser('prepare');p.add_argument('structure',type=Path);p.add_argument('--registry',type=Path,required=True);p.add_argument('--mapping',type=Path);p.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(argv)
    if args.command in ('scan','extract'):
        world,source=open_source(args.source,args.output);metadata=level_metadata(world)
        if args.command=='scan':
            report={'stage':'CANDIDATES','source':source,'metadata':metadata,'region_files':len(list((world/({'overworld':'region','the_nether':'DIM-1/region','the_end':'DIM1/region'}[args.dimension])).glob('*.mca'))),**discover(world,metadata,args.radius,args.y_min,args.y_max,args.dimension)}
            path=write(args.output/f'scan-{uuid.uuid4()}.json',report);print(json.dumps({'report':path,'metadata':metadata,'candidates':[{k:v for k,v in c.items() if k not in ('artificial_y_histogram','palette_top_30')} for c in report['candidates']]},ensure_ascii=False))
        else:
            if not re.fullmatch('[a-z][a-z0-9_]{0,63}',args.reference_id):raise ValueError('INVALID_REFERENCE_ID')
            folder=args.output/args.reference_id
            if folder.exists():raise ValueError('OUTPUT_EXISTS: choose new reference id/output directory')
            data=extract(world,metadata,args.bounds,args.dimension,args.ground_policy);data['reference_id']=args.reference_id
            manifest={'reference_id':args.reference_id,**source,'source_url':args.source_url,'source_author':args.source_author,'source_version':metadata['source_version'],'source_data_version':metadata['data_version'],'import_timestamp':datetime.now(timezone.utc).isoformat(),'extracted_bounds':args.bounds,'reference_only':True,'redistribution_allowed':'unknown','compatibility_replacements':[],'notes':'No entities, source BE NBT, inventories, loot or game sign text imported. Bounds chosen explicitly; no material-based terrain deletion.','source_sign_provenance_untrusted':data['metadata']['source_sign_provenance_untrusted']}
            print(json.dumps({'structure':write(folder/'reference_structure.json',data),'manifest':write(folder/'reference_manifest.json',manifest),'size':data['size'],'non_air':len(data['blocks']),'palette':len(data['palette'])}))
    else:
        data=json.loads(args.structure.read_text(encoding='utf8'));registry=json.loads(args.registry.read_text(encoding='utf8'));mapping=json.loads(args.mapping.read_text(encoding='utf8')) if args.mapping else {}
        report=audit(data,registry,mapping)
        if args.command=='audit':print(json.dumps({'report':write(args.output,report),'ready':report['ready'],'totals':report['totals']}))
        else:
            if not report['ready']:raise ValueError('REPLACEMENT_REVIEW_REQUIRED: audit unresolved states first')
            prepared={**data,'palette':report['target_palette'],'target_data_version':registry['data_version'],'target_version':'1.20.1','compatibility':report,'prepared':True}
            artifact=write(args.output,prepared)
            manifest=json.loads(args.structure.with_name('reference_manifest.json').read_text(encoding='utf8'))
            manifest['compatibility_replacements']=[e for e in report['entries'] if e['status']!='COMPATIBLE_EXACT']
            manifest['target_data_version']=registry['data_version'];manifest['target_registry_source']=str(args.registry.resolve())
            print(json.dumps({'prepared':artifact,'manifest':write(args.output.with_suffix('.manifest.json'),manifest),'stage':'READY_FOR_PASTE','reference_only':True,'auto_paste':False}))

if __name__=='__main__':
    try:main()
    except (ValueError,OSError,KeyError) as e:raise SystemExit(str(e))
