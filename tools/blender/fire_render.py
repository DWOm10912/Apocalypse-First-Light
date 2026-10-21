# Shade and render a baked fuel_flame*.blend fire (docs/gameplay/fuel_fire_v1.md "火焰贴图 V3"): emission only, blackbody
# colour k0..k1 K from the "flame" grid, strength ((flame - lo) / (1 - lo))^gamma x strength (no glow below lo: no fog).
#   blender -b fuel_flame_v2.blend --python fire_render.py -- <outDir> <frames: a-b or a,b,c> <w> <h> [lo] [gamma] [k0] [k1] [strength] [view]
# Env: AFL_MAT material (default FireMat; the body fire is BodyFireMat), AFL_BASE_FADE darken the fuel-rich layer above the pool
# (that height as a fraction of the domain, generated z; pool fire 0.08), AFL_SAVE=1 save the shading into the .blend. V3 renders:
#   AFL_BASE_FADE=0.08 ... fuel_flame_v2.blend ... 60-119 512 704 0.2 2.5 900 2100 7 Standard
#   AFL_MAT=BodyFireMat ... fuel_flame_body_v2.blend ... 60-119 256 640 0.2 2.5 900 2100 7 Standard
import bpy, sys, os
argv = sys.argv[sys.argv.index('--') + 1:]
out, spec, w, h = argv[0], argv[1], int(argv[2]), int(argv[3])
lo = float(argv[4]) if len(argv) > 4 else 0.12      # flame below this emits nothing (no fog)
gamma = float(argv[5]) if len(argv) > 5 else 2.0     # strength = ((f - lo) / (1 - lo)) ^ gamma
k0 = float(argv[6]) if len(argv) > 6 else 1000.0     # blackbody K at the faintest flame
k1 = float(argv[7]) if len(argv) > 7 else 2900.0     # ... and at the hottest
strength = float(argv[8]) if len(argv) > 8 else 60.0
view = argv[9] if len(argv) > 9 else 'AgX'

mat = bpy.data.materials[os.environ.get('AFL_MAT', 'FireMat')]
nt = mat.node_tree
for n in list(nt.nodes):
    if n.type != 'OUTPUT_MATERIAL': nt.nodes.remove(n)
outn = next(n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL')
attr = nt.nodes.new('ShaderNodeAttribute'); attr.attribute_name = 'flame'
def math(op, a, b=None):
    m = nt.nodes.new('ShaderNodeMath'); m.operation = op; m.use_clamp = op in ('SUBTRACT',)
    for i, v in enumerate((a, b)):
        if v is None: continue
        if isinstance(v, (int, float)): m.inputs[i].default_value = v
        else: nt.links.new(v, m.inputs[i])
    return m.outputs[0]
f = attr.outputs['Fac']
t = math('DIVIDE', math('SUBTRACT', f, lo), 1.0 - lo)            # 0..1 above the cut
s = math('MULTIPLY', math('POWER', t, gamma), strength)
base = float(__import__('os').environ.get('AFL_BASE_FADE', '0'))   # darker fuel-rich layer just above the pool (domain-generated z)
if base > 0:
    tc = nt.nodes.new('ShaderNodeTexCoord'); sep = nt.nodes.new('ShaderNodeSeparateXYZ')
    nt.links.new(tc.outputs['Generated'], sep.inputs[0])
    ramp = math('POWER', math('MINIMUM', math('DIVIDE', math('SUBTRACT', sep.outputs['Z'], 0.03), base), 1.0), 0.7)
    s = math('MULTIPLY', s, math('ADD', math('MULTIPLY', ramp, 0.75), 0.25))
temp = math('ADD', math('MULTIPLY', f, k1 - k0), k0)
bb = nt.nodes.new('ShaderNodeBlackbody'); nt.links.new(temp, bb.inputs['Temperature'])
em = nt.nodes.new('ShaderNodeEmission')
nt.links.new(bb.outputs['Color'], em.inputs['Color']); nt.links.new(s, em.inputs['Strength'])
nt.links.new(em.outputs['Emission'], outn.inputs['Volume'])

sc = bpy.context.scene
sc.render.resolution_x, sc.render.resolution_y, sc.render.resolution_percentage = w, h, 100
sc.view_settings.view_transform = view
sc.view_settings.look = 'None'
sc.cycles.samples = 48
sc.render.image_settings.file_format = 'PNG'
sc.render.image_settings.color_mode = 'RGB'
os.makedirs(out, exist_ok=True)
frames = []
for part in spec.split(','):
    if '-' in part: a, b = part.split('-'); frames += list(range(int(a), int(b) + 1))
    else: frames.append(int(part))
for fr in frames:
    sc.frame_set(fr)
    sc.render.filepath = os.path.join(out, 'f%03d.png' % fr)
    bpy.ops.render.render(write_still=True)
print('RENDER DONE', len(frames), flush=True)
if os.environ.get('AFL_SAVE'):
    sc.render.filepath = ''
    bpy.ops.wm.save_mainfile()
    print('SAVED', bpy.data.filepath, flush=True)
