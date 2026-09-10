const str={type:'string'},int={type:'integer'},bool={type:'boolean'};
const vec={type:'array',items:int,minItems:3,maxItems:3};
const target={type:'string',enum:['REFERENCE_SELECTION','AUTHORING_SESSION','REFERENCE_AREA'],default:'REFERENCE_SELECTION'};
const crop={min:vec,max:vec};
const inspect={target,...crop};
const slice={...inspect,coordinate:int,relative:bool,encoding:{type:'string',enum:['category','palette']},downsample:{type:'integer',minimum:1,maximum:32}};
const edit={target:{type:'string',enum:['AUTHORING_SESSION']},...crop,dry_run:bool};
const tool=(name,description,properties={},required=[],write=false)=>({name,description,inputSchema:{type:'object',properties,required,additionalProperties:false},annotations:{readOnlyHint:!write,destructiveHint:write,idempotentHint:!write,openWorldHint:false}});
export const tools=[
  tool('minecraft_status','Call first. Refreshes world/dimension binding; inspect this before each authoring round.'),
  tool('get_player_state','Read position, direction, dimension and camera.'),
  tool('get_worldedit_selection','Read exact current CuboidRegion selection. Reference is read-only.'),
  tool('inspect_selection','Bounded palette/density/floor heuristic. Crop large selections.',inspect),
  tool('get_horizontal_slice','Y slice. Category or exact blockstate palette; max 4096 samples.',slice,['coordinate']),
  tool('get_vertical_slice','X or Z section, rows top to bottom.',{...slice,axis:{type:'string',enum:['X','Z']}},['axis','coordinate']),
  tool('inspect_facade','First occupied depth and facade sample analysis.',{...inspect,side:{type:'string',enum:['NORTH','SOUTH','EAST','WEST']},depth:{type:'integer',minimum:1,maximum:16},downsample:{type:'integer',minimum:1,maximum:32}},['side']),
  tool('capture_current_view','Capture Minecraft framebuffer on render thread. Does not move camera. Returns local PNG.'),
  tool('camera_move','Creative first-person inspection only. Same private world, loaded collision-free destination within plot +64 horizontal/-16 below/+48 above, travel <=256. Saves first return point and enables flight; no blocks or gamemode changed. Wait for camera_status.client_frame_ready before capture.',{position:{type:'array',minItems:3,maxItems:3,items:{type:'number'}},yaw:{type:'number',minimum:-180,maximum:180},pitch:{type:'number',minimum:-90,maximum:90},dry_run:bool},['position','yaw','pitch'],true),
  tool('camera_restore','Restore first camera_move position, angles and original flying flag in same authoring session. Rechecks destination safety; no forced restoration through obstacles.',{dry_run:bool},[],true),
  tool('camera_status','Read position, flight, return availability and client_frame_ready after a move.'),
  tool('authoring_create','Reserve vacant plot. No clearing or automatic export.',{building_id:str,width:int,height:int,depth:int,surface_offset_y:int},['building_id','width','height','depth'],true),
  tool('authoring_info','Read active authoring session.'),
  tool('authoring_validate','Validate only; never export.',{},[],true),
  tool('authoring_cancel','Cancel reservation; preserve blocks. Bridge history/clipboard invalidated at next session.',{},[],true),
  tool('authoring_clear','Undoable WorldEdit clear within active plot only. Use dry_run first.',{dry_run:bool},[],true),
  ...['set','walls','faces'].map(n=>tool(`we_${n}`,'Exact namespaced blockstate. Source/target must remain within active authoring plot.',{...edit,block:str},['block'],true)),
  tool('we_replace','Replace exact blockstate, no arbitrary patterns.',{...edit,block:str,from:str},['block','from'],true),
  tool('we_batch_set','Ordered exact-state cuboids, preflight all, one undo entry. Max 128 operations and 250000 summed cells including overlaps.',{target:{type:'string',enum:['AUTHORING_SESSION']},dry_run:bool,operations:{type:'array',minItems:1,maxItems:128,items:{type:'object',properties:{min:vec,max:vec,block:str},required:['min','max','block'],additionalProperties:false}}},['operations'],true),
  tool('we_copy','Copy only own authoring region to isolated bridge clipboard. No reference copying.',edit,[],true),
  tool('we_rotate_clipboard','Rotate own bridge clipboard around Y and normalize origin; no world edits.',{rotation:{type:'integer',enum:[0,90,180,270]},dry_run:bool},['rotation'],true),
  tool('we_paste','Paste bridge clipboard; to is target minimum. No entities or biomes.',{...edit,to:vec,ignore_air:bool},['to'],true),
  tool('we_stack','Repeat source by explicit block offset, bounded preflight.',{...edit,offset:vec,count:{type:'integer',minimum:1,maximum:128}},['offset','count'],true),
  tool('we_move','Move source by block offset. Use dry_run first.',{...edit,offset:vec},['offset'],true),
  tool('we_undo','Undo last bridge edit only. Manual conflicts rejected.',{dry_run:bool},[],true),
  tool('we_redo','Redo last bridge edit only. Manual conflicts rejected.',{dry_run:bool},[],true),
  tool('export_target_registry','Export actual running block registry/properties for offline reference compatibility. No world edits.'),
  tool('reference_import_status','List only explicitly allowlisted reference archives.'),
  tool('reference_map_scan','Offline scan of a launch-allowlisted source. No paste.',{source_id:int,radius:int,y_min:int,y_max:int},['source_id']),
  tool('reference_candidates','Read last scan candidates. User must choose before extract.'),
  tool('reference_extract','Offline reference extraction with explicitly reviewed absolute bounds. No paste.',{source_id:int,reference_id:str,bounds:{type:'array',items:int,minItems:6,maxItems:6},ground_policy:{type:'string',enum:['BUILDING_ONLY','BUILDING_PLUS_PAD','FULL_SELECTION']}},['source_id','reference_id','bounds'],true),
  tool('reference_compatibility_report','Audit against exported actual game registry. Unknown never becomes air.',{reference_id:str},['reference_id']),
  tool('reference_prepare_paste','Prepare exact-compatible reference and stage to dedicated reference directory; no world change. Mapping review is CLI-only in V1.',{reference_id:str},['reference_id'],true),
  tool('reference_write_schematic','Write prepared reference via actual WorldEdit Sponge serializer. No automatic world paste.',{reference_id:str},['reference_id'],true),
  tool('reference_paste','Explicit paste into empty reference area set by user in-game. Never authoring draft. Use dry_run first.',{reference_id:str,dry_run:bool,rotation:{type:'integer',enum:[0,90,180,270]}},['reference_id'],true),
  tool('reference_remove','Undo last reference paste in same reference area/world only. Manual changes reject removal.',{},[],true)
];
