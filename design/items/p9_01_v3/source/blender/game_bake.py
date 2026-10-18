# P9 v3 game version, step 3: bake the bake source (P9_hi) onto the low poly atlas (P9_lo UVs) at BAKE_RES.
# Moving parts bake in separate groups (slide, barrel, frame, magazine) so a part never picks up its neighbour: each
# group's low parts are joined into bake_<group> and only that group's high parts are selected (and render-visible for
# the AO pass). Passes, into one image each:
#   color   base colour (Principled Base Color re-wired into an Emission shader), sRGB
#   rough   roughness (same trick), non-colour
#   mat     per-material constants: R = LabPBR F0 byte / 255, G = metal, B = tritium glow
#   nfull   tangent normal with the material bumps (grain, checkering, thread ridges, engraving all come from P9_hi)
#   ngeo    tangent normal of the geometry only (bumps unplugged): the bevels, for the edge-highlight mask
#   ao      ambient occlusion of the low poly group itself (AO_DIST mm)
import bpy, os, time

RES = globals().get('BAKE_RES', 4096)
AO_DIST = globals().get('AO_DIST', 2.5)
GROUPS = globals().get('BAKE_GROUPS', ('slide', 'barrel', 'frame', 'mag'))
PASSES = globals().get('BAKE_PASSES', ('color', 'rough', 'mat', 'nfull', 'ngeo', 'ao'))
# Blackened steel (barrel, pins, controls) bakes as a coated dielectric, not a LabPBR metal: a metal's albedo is its F0,
# which vanilla shows as bare silver, and metal F0 glares under the shader. Bare-metal wear comes with the wear layer.
STEEL = globals().get('STEEL_MAT', (28, 0, 0))
MATS = {  # material -> (F0 byte, metal, glow)
    'p9_polymer': (10, 0, 0), 'p9_grip_texture': (10, 0, 0), 'p9_slide_nitride': (24, 0, 0),
    'p9_barrel_steel': STEEL, 'p9_steel_machined': STEEL, 'p9_sight_white': (10, 0, 0), 'p9_tritium': (10, 0, 1),
}


def group_of(name):
    b = name.split('_', 1)[1]
    if b.startswith('part_mag'):
        return 'mag'
    if b.startswith('slide'):
        return 'slide'
    if b.startswith('barrel'):
        return 'barrel'
    return 'frame'


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
images = {p: image('p9_bake_' + p, *IMG[p]) for p in PASSES}

# bake target material on the joined low groups
tm = bpy.data.materials.get('p9_bake_target') or bpy.data.materials.new('p9_bake_target')
tm.use_nodes = True
tnode = next((n for n in tm.node_tree.nodes if n.type == 'TEX_IMAGE'), None) or tm.node_tree.nodes.new('ShaderNodeTexImage')
tm.node_tree.nodes.active = tnode

bc = bpy.data.collections.get('P9_bake') or bpy.data.collections.new('P9_bake')
if bc.name not in sc.collection.children:
    sc.collection.children.link(bc)
for o in list(bc.objects):
    me = o.data
    bpy.data.objects.remove(o)
    if me.users == 0:
        bpy.data.meshes.remove(me)
dg = bpy.context.evaluated_depsgraph_get()
low = {}
for o in bpy.data.collections['P9_lo'].objects:
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
for o in bpy.data.collections['P9_hi'].objects:
    high.setdefault(group_of(o.name), []).append(o)


def bsdf_of(m):
    return next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')


def out_of(m):
    return next(n for n in m.node_tree.nodes if n.type == 'OUTPUT_MATERIAL')


def rewire(pass_name):
    """Point every P9 material at what this pass bakes; returns an undo list."""
    undo = []
    for mname, (f0, metal, glow) in MATS.items():
        m = bpy.data.materials[mname]
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
                if src.links:
                    nt.links.new(src.links[0].from_socket, sock)
                else:
                    v = src.default_value
                    sock.default_value = tuple(v) if pass_name == 'color' else (v, v, v, 1)
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
        if kind == 'emit':
            nt.nodes.remove(x)
            nt.links.new(bs.outputs[0], out.inputs['Surface'])
        else:
            nt.links.new(x, bs.inputs['Normal'])


def bake(pass_name, gname):
    ob = bake_obj[gname]
    his = high.get(gname, [])
    tnode.image = images[pass_name]
    everything = list(bpy.data.collections['P9_hi'].objects) + list(bc.objects) + list(bpy.data.collections['P9_lo'].objects)
    for c in bpy.data.collections:
        c.hide_render = c.name not in ('P9_hi', 'P9_bake')
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
