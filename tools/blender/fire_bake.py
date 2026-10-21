# Bake a fuel_flame*.blend Mantaflow fire (docs/gameplay/fuel_fire_v1.md "火焰贴图 V3"). Settings as one JSON argument:
#   blender -b fuel_flame_v2.blend --python fire_bake.py -- <cacheDir> '{"end":120,"res":160,"vort":1.0,"fvort":3.0,"burn":0.82,"noise":0.7,"tex":0.35,"save":true}'
#   blender -b fuel_flame_body_v2.blend --python fire_bake.py -- <cacheDir> '{"end":120,"res":160,"vort":1.0,"fvort":3.0,"burn":0.9,"noise":0.7,"tex":0.18,"domain":"FireDomain.001","flow":"AflBodyFire_Body","save":true}'
# The cache is not kept in the repository (a few GB): bake again before rendering. "save" stores the settings and cache path.
argv = sys.argv[sys.argv.index('--') + 1:]
cache, p = argv[0], json.loads(argv[1])
dom = bpy.data.objects[p.get('domain', 'FireDomain')]; d = dom.modifiers['Fluid'].domain_settings
f = bpy.data.objects[p.get('flow', 'FuelPool')].modifiers['Fluid'].flow_settings
d.cache_directory = cache; d.cache_type = 'ALL'; d.cache_frame_start = 1; d.cache_frame_end = p['end']
d.resolution_max = p['res']; d.vorticity = p['vort']; d.flame_vorticity = p['fvort']; d.burning_rate = p['burn']
d.noise_strength = p['noise']; d.noise_scale = p.get('nscale', 2)
f.noise_texture.noise_scale = p['tex']
bpy.context.scene.frame_end = p['end']
print('noise attrs', d.noise_strength, d.noise_pos_scale, d.noise_time_anim)
os.makedirs(cache, exist_ok=True); t = time.time()
with bpy.context.temp_override(object=dom, active_object=dom, selected_objects=[dom]):
    bpy.ops.fluid.bake_all()
print('BAKE DONE', p, round(time.time() - t, 1), 's', flush=True)
if p.get('save'): bpy.ops.wm.save_mainfile()
