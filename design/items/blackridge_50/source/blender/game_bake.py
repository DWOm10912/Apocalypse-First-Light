# Blackridge .50 game version, step 3 (the BR-51 game_bake.py): bake BR50_hi onto the low poly atlas (BR50_lo UVs) at
# BAKE_RES, one group per rig bone (a moving part never picks up its neighbour). Passes (br50_gun_bake_<pass>): color,
# rough, mat (R F0 byte / 255, G metal, B glow), nfull (with the material bumps), ngeo (geometry only), ao (AO_DIST mm).
# The black coating and the machined steel bake as coated dielectrics (F0 24 / 28), as on the P9 and the BR-51.
import bpy, os, time

exec(open(os.path.join(BASE, 'game_bones.py')).read())

RES = globals().get('BAKE_RES', 4096)
AO_DIST = globals().get('AO_DIST', 2.5)
PRE = 'br50_' + ATLAS + '_bake_'
PASSES = globals().get('BAKE_PASSES', ('color', 'rough', 'mat', 'nfull', 'ngeo', 'ao'))
# Parkerized and blackened steel bakes as a coated dielectric, not a LabPBR metal (as the P9: a metal's albedo is its F0,
# which vanilla shows as bare silver). The worn working steel (bolt, trigger, pins) is a metal in the concept; its colour
# is scaled down by COLOR_K so the albedo reads as dark polished steel, not light grey.
STEEL = globals().get('STEEL_MAT', (28, 0, 0))
MATS = {  # material -> (F0 byte, metal, glow)
    'br50_black': (24, 0, 0), 'br50_barrel': (24, 0, 0), 'br50_steel': STEEL, 'br50_bore': STEEL,
    'br50_grip': (10, 0, 0), 'br50_pad': (10, 0, 0), 'br50_white': (10, 0, 0),
}
COLOR_K = globals().get('COLOR_K', {})
GROUPS = sorted({bone_of(o.name) for o in bpy.data.collections['BR50_lo'].objects
                 if o.type == 'MESH' and atlas_of(o.name) == ATLAS})


def group_of(name):
    return bone_of(name)


sc = bpy.context.scene
try:
    sc.render.engine = 'CYCLES'
except TypeError as e:
    print(e)
prefs = bpy.context.preferences.addons['cycles'].preferences
try:
    prefs.compute_device_type = 'OPTIX'
    prefs.get_devices()
    for d in prefs.devices:
        d.use = True
    sc.cycles.device = 'GPU'
except Exception as e:
    print('gpu', e)
if not sc.world:
    sc.world = bpy.data.worlds.new('World')
sc.world.light_settings.distance = AO_DIST
bk = sc.render.bake
bk.use_selected_to_active = False
bk.cage_extrusion = 0.3
bk.max_ray_distance = 0.7
bk.margin = 2
bk.margin_type = 'EXTEND'
bk.use_clear = False
bk.target = 'IMAGE_TEXTURES'
bk.normal_space = 'TANGENT'


def image(name, colorspace, is_float, fill):
    im = bpy.data.images.get(name)
    if im and (im.size[0] != RES or im.is_float != is_float):
        bpy.data.images.remove(im)
        im = None
    if im and not globals().get('BAKE_RESET', True):
        return im   # keep earlier groups of this pass (one group per call)
    if not im:
        im = bpy.data.images.new(name, RES, RES, alpha=False, float_buffer=is_float)
    im.colorspace_settings.name = colorspace
    im.generated_color = fill
    im.source = 'GENERATED'
    im.generated_type = 'BLANK'
    im.generated_width = RES
    im.generated_height = RES
    return im


IMG = {
    'color': ('sRGB', False, (0, 0, 0, 1)), 'rough': ('Non-Color', False, (0.6, 0.6, 0.6, 1)),
    'mat': ('Non-Color', False, (10 / 255, 0, 0, 1)), 'nfull': ('Non-Color', True, (0.5, 0.5, 1, 1)),
    'ngeo': ('Non-Color', True, (0.5, 0.5, 1, 1)), 'ao': ('Non-Color', False, (1, 1, 1, 1)),
}
images = {p: image(PRE + p, *IMG[p]) for p in PASSES}

# bake target material on the joined low groups
tm = bpy.data.materials.get('br50_bake_target') or bpy.data.materials.new('br50_bake_target')
tm.use_nodes = True
tnode = next((n for n in tm.node_tree.nodes if n.type == 'TEX_IMAGE'), None) or tm.node_tree.nodes.new('ShaderNodeTexImage')
tm.node_tree.nodes.active = tnode

bc = bpy.data.collections.get('BR50_bake') or bpy.data.collections.new('BR50_bake')
if bc.name not in sc.collection.children:
    sc.collection.children.link(bc)
for o in list(bc.objects):
    me = o.data
    bpy.data.objects.remove(o)
    if me.users == 0:
        bpy.data.meshes.remove(me)
dg = bpy.context.evaluated_depsgraph_get()
low = {}
for o in bpy.data.collections['BR50_lo'].objects:
    if o.type == 'MESH' and atlas_of(o.name) == ATLAS:
        low.setdefault(group_of(o.name), []).append(o)
bake_obj = {}
for gname, objs in low.items():
    meshes = []
    for o in objs:
        me = bpy.data.meshes.new_from_object(o.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
        meshes.append(me)
    ob = bpy.data.objects.new('bake_' + gname, meshes[0])
    bc.objects.link(ob)
    if len(meshes) > 1:
        tmp = []
        for me in meshes[1:]:
            t = bpy.data.objects.new('tmpjoin', me)
            bc.objects.link(t)
            tmp.append(t)
        with bpy.context.temp_override(active_object=ob, selected_editable_objects=[ob] + tmp, selected_objects=[ob] + tmp):
            bpy.ops.object.join()
    ob.data.materials.clear()
    ob.data.materials.append(tm)
    for p in ob.data.polygons:
        p.material_index = 0
    bake_obj[gname] = ob

high = {}
for o in bpy.data.collections['BR50_hi'].objects:
    if o.type == 'MESH':
        high.setdefault(group_of(o.name), []).append(o)


def bsdf_of(m):
    return next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')


def out_of(m):
    return next(n for n in m.node_tree.nodes if n.type == 'OUTPUT_MATERIAL')


def rewire(pass_name):
    """Point every Blackridge material at what this pass bakes; returns an undo list."""
    undo = []
    for mname, (f0, metal, glow) in MATS.items():
        m = bpy.data.materials.get(mname)
        if not m:   # not used by the gun (dropped when the .blend was saved)
            continue
        nt = m.node_tree
        bs, out = bsdf_of(m), out_of(m)
        if pass_name in ('color', 'rough', 'mat'):
            em = nt.nodes.new('ShaderNodeEmission')
            em.inputs['Strength'].default_value = 1.0
            sock = em.inputs['Color']
            if pass_name == 'mat':
                sock.default_value = (f0 / 255.0, float(metal), float(glow), 1)
            else:
                src = bs.inputs['Base Color' if pass_name == 'color' else 'Roughness']
                k = COLOR_K.get(mname, 1.0) if pass_name == 'color' else 1.0
                if src.links:
                    if k != 1.0:
                        mul = nt.nodes.new('ShaderNodeVectorMath')
                        mul.operation = 'SCALE'
                        mul.inputs['Scale'].default_value = k
                        nt.links.new(src.links[0].from_socket, mul.inputs[0])
                        nt.links.new(mul.outputs[0], sock)
                        undo.append(('node', m, mul))
                    else:
                        nt.links.new(src.links[0].from_socket, sock)
                else:
                    v = src.default_value
                    sock.default_value = tuple(c * k for c in v[:3]) + (1,) if pass_name == 'color' else (v, v, v, 1)
            nt.links.new(em.outputs[0], out.inputs['Surface'])
            undo.append(('emit', m, em))
        elif pass_name == 'ngeo' and bs.inputs['Normal'].links:
            lk = bs.inputs['Normal'].links[0]
            src = lk.from_socket
            nt.links.remove(lk)
            undo.append(('normal', m, src))
    return undo


def restore(undo):
    for kind, m, x in undo:
        nt = m.node_tree
        bs, out = bsdf_of(m), out_of(m)
        if kind == 'node':
            nt.nodes.remove(x)
        elif kind == 'emit':
            nt.nodes.remove(x)
            nt.links.new(bs.outputs[0], out.inputs['Surface'])
        else:
            nt.links.new(x, bs.inputs['Normal'])


def bake(pass_name, gname):
    ob = bake_obj[gname]
    his = high.get(gname, [])
    tnode.image = images[pass_name]
    everything = list(bpy.data.collections['BR50_hi'].objects) + list(bc.objects) + list(bpy.data.collections['BR50_lo'].objects)
    for c in bpy.data.collections:
        c.hide_render = c.name not in ('BR50_hi', 'BR50_bake')
    for o in everything:
        o.hide_render = not (o is ob or (o in his and pass_name != 'ao'))
        o.hide_set(False)
        o.select_set(False)
    for h in his:
        h.select_set(pass_name != 'ao')
    ob.select_set(True)
    bpy.context.view_layer.objects.active = ob
    sel = pass_name != 'ao'
    kw = dict(use_selected_to_active=sel, cage_extrusion=0.3, max_ray_distance=0.7, margin=2, margin_type='EXTEND',
              use_clear=False, target='IMAGE_TEXTURES')
    if pass_name in ('nfull', 'ngeo'):
        sc.cycles.samples = 1
        bpy.ops.object.bake(type='NORMAL', normal_space='TANGENT', **kw)
    elif pass_name == 'ao':
        sc.cycles.samples = globals().get('AO_SAMPLES', 64)
        bpy.ops.object.bake(type='AO', **kw)
    else:
        sc.cycles.samples = 1
        bpy.ops.object.bake(type='EMIT', **kw)


t0 = time.time()
for p in PASSES:
    undo = rewire(p)
    try:
        for gname in GROUPS:
            t1 = time.time()
            bake(p, gname)
            print('baked', p, gname, round(time.time() - t1, 1), 's')
    finally:
        restore(undo)
print('bake total', round(time.time() - t0, 1), 's')
