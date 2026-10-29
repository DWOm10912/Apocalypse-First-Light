# Blackridge .50 game model next to the P9-01 V3 game model, both from their exported low polys and game textures, in the
# shared Blockbench px space (one ruler, the grips on the same hand point): vanilla emulation (albedo x Minecraft's entity
# diffuse, no specular, no normal map) and a PBR look (LabPBR -> Principled). Headless:
#   blender -b --factory-startup -P game_compare.py -- <out_dir> [samples]
# Shots: side_<mode> (left side, orthographic, P9 above the Blackridge, a 1 px grid = 17 mm), hero_<mode> (Blackridge
# 3/4 front right), slide_<mode> (Blackridge with the slide back and the magazine half out: the insides).
import bpy, json, math, os, sys
from mathutils import Vector

BASE = os.path.dirname(os.path.abspath(__file__)) + os.sep
ROOT = os.path.abspath(os.path.join(BASE, '..', '..', '..', '..', '..'))
argv = sys.argv[sys.argv.index('--') + 1:]
OUT = argv[0]
SAMPLES = int(argv[1]) if len(argv) > 1 else 96
os.makedirs(OUT, exist_ok=True)
TEXD = os.path.join(ROOT, 'src', 'main', 'blockbench', 'textures')
GUNS = {'br50': (os.path.join(ROOT, 'design/items/blackridge_50/source/blackridge_50_v2_game_lowpoly.json'), os.path.join(TEXD, 'blackridge_50_v2_native')),
        'p9': (os.path.join(ROOT, 'design/items/p9_01_v3/source/p9_01_v3_game_lowpoly.json'), os.path.join(TEXD, 'p9_01_v3_native'))}
MM = 16.99015
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o)
sc = bpy.context.scene


def img(path, cs):
    im = bpy.data.images.load(path, check_existing=True)
    im.colorspace_settings.name = cs
    im.alpha_mode = 'NONE'
    return im


def tex(nt, im):
    t = nt.nodes.new('ShaderNodeTexImage')
    t.image = im
    t.interpolation = 'Closest'
    return t


def materials(key, prefix):
    A, S, N = img(prefix + '.png', 'sRGB'), img(prefix + '_s.png', 'Non-Color'), img(prefix + '_n.png', 'Non-Color')
    # vanilla: albedo x (0.4 + 0.6 x two fixed lights), emission
    mv = bpy.data.materials.new(key + '_vanilla')
    mv.use_nodes = True
    nt = mv.node_tree
    nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    ta = tex(nt, A)
    geo = nt.nodes.new('ShaderNodeNewGeometry')
    light = None
    for L in ((0.2, 1.0, -0.7), (-0.2, 1.0, 0.7)):
        l = Vector((L[0], -L[2], L[1])).normalized()       # Minecraft y-up -> Blender
        d = nt.nodes.new('ShaderNodeVectorMath')
        d.operation = 'DOT_PRODUCT'
        nt.links.new(geo.outputs['Normal'], d.inputs[0])
        d.inputs[1].default_value = tuple(l)
        mx = nt.nodes.new('ShaderNodeMath')
        mx.operation = 'MAXIMUM'
        nt.links.new(d.outputs['Value'], mx.inputs[0])
        mx.inputs[1].default_value = 0.0
        if light is None:
            light = mx
        else:
            s = nt.nodes.new('ShaderNodeMath')
            s.operation = 'ADD'
            nt.links.new(light.outputs[0], s.inputs[0])
            nt.links.new(mx.outputs[0], s.inputs[1])
            light = s
    k = nt.nodes.new('ShaderNodeMath')
    k.operation = 'MULTIPLY_ADD'
    k.use_clamp = True
    nt.links.new(light.outputs[0], k.inputs[0])
    k.inputs[1].default_value = 0.6
    k.inputs[2].default_value = 0.4
    mul = nt.nodes.new('ShaderNodeVectorMath')
    mul.operation = 'SCALE'
    nt.links.new(ta.outputs['Color'], mul.inputs[0])
    nt.links.new(k.outputs[0], mul.inputs['Scale'])
    em = nt.nodes.new('ShaderNodeEmission')
    nt.links.new(mul.outputs[0], em.inputs['Color'])
    nt.links.new(em.outputs[0], out.inputs['Surface'])
    # PBR
    mp = bpy.data.materials.new(key + '_pbr')
    mp.use_nodes = True
    nt = mp.node_tree
    nt.nodes.clear()
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    bs = nt.nodes.new('ShaderNodeBsdfPrincipled')
    nt.links.new(bs.outputs[0], out.inputs['Surface'])
    ta, ts, tn = tex(nt, A), tex(nt, S), tex(nt, N)
    nt.links.new(ta.outputs['Color'], bs.inputs['Base Color'])
    sp = nt.nodes.new('ShaderNodeSeparateColor')
    nt.links.new(ts.outputs['Color'], sp.inputs[0])
    r = nt.nodes.new('ShaderNodeMath')
    r.operation = 'SUBTRACT'
    r.inputs[0].default_value = 1.0
    nt.links.new(sp.outputs[0], r.inputs[1])
    nt.links.new(r.outputs[0], bs.inputs['Roughness'])
    lv = nt.nodes.new('ShaderNodeMath')
    lv.operation = 'MULTIPLY'
    nt.links.new(sp.outputs[1], lv.inputs[0])
    lv.inputs[1].default_value = 255.0 / 10.0 * 0.5          # F0 byte -> specular level (0.5 = F0 0.04)
    nt.links.new(lv.outputs[0], bs.inputs['Specular IOR Level'])
    spn = nt.nodes.new('ShaderNodeSeparateColor')
    nt.links.new(tn.outputs['Color'], spn.inputs[0])
    inv = nt.nodes.new('ShaderNodeMath')
    inv.operation = 'SUBTRACT'
    inv.inputs[0].default_value = 1.0
    nt.links.new(spn.outputs[1], inv.inputs[1])
    cc = nt.nodes.new('ShaderNodeCombineColor')
    nt.links.new(spn.outputs[0], cc.inputs[0])
    nt.links.new(inv.outputs[0], cc.inputs[1])
    cc.inputs[2].default_value = 1.0
    nm = nt.nodes.new('ShaderNodeNormalMap')
    nt.links.new(cc.outputs[0], nm.inputs['Color'])
    nt.links.new(nm.outputs[0], bs.inputs['Normal'])
    return mv, mp


def load(key, path, prefix):
    low = json.load(open(path))
    T = low['texture_size']
    objs = []
    for p in low['parts']:
        me = bpy.data.meshes.new(key + '_' + p['name'])
        me.from_pydata([(v[0], -v[2], v[1]) for v in p['vertices']], [], [f['v'] for f in p['faces']])
        uv = me.uv_layers.new(name='UVMap').data
        for poly, f in zip(me.polygons, p['faces']):
            for li, (u, v) in zip(poly.loop_indices, f['uv']):
                uv[li].uv = (u / T, 1.0 - v / T)
        me.validate()
        ob = bpy.data.objects.new(me.name, me)
        sc.collection.objects.link(ob)
        ob['bone'] = p['bone']
        for poly in me.polygons:
            poly.use_smooth = True
        me.set_sharp_from_angle(angle=math.radians(35))   # Vextris rebuilds smooth normals below 35 deg
        objs.append(ob)
    return objs, materials(key, prefix)


guns = {k: load(k, *v) for k, v in GUNS.items()}
# The P9 sits above the Blackridge for the side shot (same scale).
P9_LIFT = 10.0

try:
    sc.render.engine = 'CYCLES'
    prefs = bpy.context.preferences.addons['cycles'].preferences
    prefs.compute_device_type = 'OPTIX'
    prefs.get_devices()
    for d in prefs.devices:
        d.use = True
    sc.cycles.device = 'GPU'
except Exception as e:
    print('gpu', e)
sc.render.film_transparent = False
w = bpy.data.worlds.new('w')
sc.world = w
w.use_nodes = True
bg = next(n for n in w.node_tree.nodes if n.type == 'BACKGROUND')
lights = []
for name, rot, energy in (('key', (math.radians(55), 0, math.radians(-35)), 3.2), ('rim', (math.radians(-60), 0, math.radians(160)), 2.0),
                          ('top', (0, 0, 0), 1.0)):
    ld = bpy.data.lights.new(name, 'SUN')
    ld.energy = energy
    ld.angle = math.radians(6)
    lo = bpy.data.objects.new(name, ld)
    lo.rotation_euler = rot
    sc.collection.objects.link(lo)
    lights.append(lo)
cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam'))
sc.collection.objects.link(cam)
sc.camera = cam
cam.data.clip_start = 0.05
cam.data.clip_end = 1000


def mode(m):
    vs = sc.view_settings
    if m == 'vanilla':
        vs.view_transform, vs.look = 'Standard', 'None'
        bg.inputs['Color'].default_value = (0.16, 0.17, 0.19, 1)
        bg.inputs['Strength'].default_value = 1.0
        sc.cycles.samples = 16
        for l in lights:
            l.hide_render = True
    else:
        vs.view_transform, vs.look = 'AgX', 'AgX - Medium High Contrast'
        bg.inputs['Color'].default_value = (0.5, 0.55, 0.62, 1)
        bg.inputs['Strength'].default_value = 0.35
        sc.cycles.samples = SAMPLES
        for l in lights:
            l.hide_render = False
    for k, (objs, (mv, mp)) in guns.items():
        for o in objs:
            o.data.materials.clear()
            o.data.materials.append(mv if m == 'vanilla' else mp)


def pose(slide=0.0, mag=0.0, p9=True):
    ax = Vector((0, 0.179, -1)).normalized()     # down the magazine's rake, Blender frame (y = muzzle)
    for o in guns['br50'][0]:
        b = o['bone']
        o.location = (0, 0, 0)
        if b in ('slide', 'bolt', 'safety'):
            o.location = (0, -slide, 0)
        if b in ('magazine', 'follower'):
            o.location = ax * mag
    for o in guns['p9'][0]:
        o.hide_render = not p9
        o.location = (0, 0, P9_LIFT)


def shot(name, eye, target, ortho=None, lens=70, res=(1800, 1100)):
    cam.data.type = 'ORTHO' if ortho else 'PERSP'
    if ortho:
        cam.data.ortho_scale = ortho
    cam.data.lens = lens
    cam.location = Vector(target) + Vector(eye)
    cam.rotation_euler = (Vector(target) - cam.location).to_track_quat('-Z', 'Y').to_euler()
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.filepath = os.path.join(OUT, name + '.png')
    bpy.ops.render.render(write_still=True)


for m in ('vanilla', 'pbr'):
    mode(m)
    pose(p9=True)
    shot('side_' + m, (-60, 0, 0), (0, 2.0, 7.35), ortho=30.0)
    pose(p9=False)
    shot('hero_' + m, (24, 26, 13), (0, 2.0, 3.5), lens=60)
    pose(slide=75 / MM, mag=40 / MM, p9=False)
    shot('slide_' + m, (20, 12, 17), (0, 0.5, 4.0), lens=60)
print('compare done')
