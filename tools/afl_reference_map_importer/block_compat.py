import json
import re
from collections import Counter

def audit(structure,registry,mappings=None):
    if registry.get('minecraft_version')!='1.20.1' or not registry.get('blocks'): raise ValueError('TARGET_REGISTRY_UNAVAILABLE: require actual 1.20.1 registry snapshot')
    counts=Counter(b[3] for b in structure['blocks']);entries=[];target_palette=[];totals=Counter();mappings=mappings or {}
    for index,state in enumerate(structure['palette']):
        name=state['Name'];props=state.get('Properties',{});mapping=mappings.get(name);target=dict(state);status='COMPATIBLE_EXACT';reason='Exact registry and property match'
        if mapping:
            if not mapping.get('approved') or not mapping.get('reason'): raise ValueError(f'REPLACEMENT_REVIEW_REQUIRED: {name}')
            target={'Name':mapping['target'],'Properties':dict(props)}
            for key in mapping.get('drop_properties',[]):target['Properties'].pop(key,None)
            target['Properties'].update(mapping.get('properties',{}));status='COMPATIBLE_PROPERTY_ADJUST' if target['Name']==name else 'REPLACEMENT_APPROVED';reason=mapping['reason']
        definition=registry['blocks'].get(target['Name'])
        if not definition:status='REPLACEMENT_REQUIRED';reason='Target block does not exist'
        else:
            invalid={k:v for k,v in target.get('Properties',{}).items() if k not in definition['properties'] or v not in definition['properties'][k]}
            if invalid:status='UNKNOWN';reason=f'Explicit property adjustment required: {invalid}'
        if re.search(r'(command_block|structure_block|structure_void|jigsaw|barrier|tnt|fire$|portal|piston|sculk_sensor|sculk_shrieker|spawner)',target['Name']):
            status='REPLACEMENT_REQUIRED';reason='Unsafe reference geometry, even though target registry may contain it; explicit mapping required'
        # Geometry can retain containers/signs; all original NBT/text/inventory is omitted.
        entries.append({'source':state,'target':target,'count':counts[index],'status':status,'reason':reason,'block_entity_policy':'STRIP_ALL_SOURCE_NBT' if definition and definition.get('has_block_entity') else 'NONE'})
        target_palette.append(target);totals[status]+=counts[index]
    unresolved=any(e['status'] in ('UNKNOWN','REPLACEMENT_REQUIRED') for e in entries)
    return {'target_version':'1.20.1','target_data_version':registry['data_version'],'entries':entries,'totals':dict(totals),'unknown_to_air':False,'ready':not unresolved,'target_palette':target_palette,'block_entity_policy':'SAFE_ONLY_EMPTY_GEOMETRY_NO_SOURCE_NBT'}
