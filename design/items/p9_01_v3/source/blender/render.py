# P9 v3 concept: dark studio, Cycles, camera shots. Units mm: light power = radiance * area * pi.
import bpy, math
from mathutils import Vector

sc = bpy.context.scene
LC = bpy.data.collections.get('P9_v3_studio') or bpy.data.collections.new('P9_v3_studio')
if LC.name not in sc.collection.children:
    sc.collection.children.link(LC)
TARGET = Vector((0, 26, 64))


def area(name, loc, size, radiance, aim=TARGET, color=(1, 1, 1)):
    ob = bpy.data.objects.get(name)
    if not ob:
        ld = bpy.data.lights.new(name, 'AREA')
        ob = bpy.data.objects.new(name, ld)
        LC.objects.link(ob)
    ld = ob.data
    ld.shape = 'RECTANGLE'
    ld.size, ld.size_y = size
    ld.energy = radiance * size[0] * size[1] * math.pi
    ld.color = color
    ob.location = loc
    ob.rotation_euler = (Vector(aim) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    return ob


def setup(key=60.0, rim=110.0, top=70.0, fill=8.0, world=0.012):
    area('L_key', (-260, 120, 420), (520, 320), key)
    area('L_rim', (260, -320, 230), (900, 70), rim)
    area('L_top', (180, 120, 520), (900, 90), top)
    area('L_fill', (-420, -160, 10), (420, 420), fill)
    w = sc.world or bpy.data.worlds.new('World')
    sc.world = w
    w.use_nodes = True
    nt = w.node_tree
    nt.nodes.clear()
    tc = nt.nodes.new('ShaderNodeTexCoord')
    sp = nt.nodes.new('ShaderNodeSeparateXYZ')
    mr = nt.nodes.new('ShaderNodeMapRange')
    mr.inputs['From Min'].default_value = -1.0
    mr.inputs['From Max'].default_value = 1.0
    cr = nt.nodes.new('ShaderNodeValToRGB')
    el = cr.color_ramp.elements
    el[0].position, el[0].color = 0.40, (world * 0.4, world * 0.4, world * 0.4, 1)
    el[1].position, el[1].color = 0.52, (world, world, world, 1)
    e2 = el.new(0.75)
    e2.color = (world * 6, world * 6, world * 6, 1)
    e3 = el.new(0.97)
    e3.color = (world * 14, world * 14, world * 14, 1)
    bg = nt.nodes.new('ShaderNodeBackground')
    out = nt.nodes.new('ShaderNodeOutputWorld')
    nt.links.new(tc.outputs['Generated'], sp.inputs[0])
    nt.links.new(sp.outputs['Z'], mr.inputs['Value'])
    nt.links.new(mr.outputs['Result'], cr.inputs['Fac'])
    nt.links.new(cr.outputs['Color'], bg.inputs['Color'])
    nt.links.new(bg.outputs[0], out.inputs['Surface'])
    try:
        sc.render.engine = 'CYCLES'
    except TypeError as e:
        print('engine', e)
    cy = sc.cycles
    prefs = bpy.context.preferences.addons['cycles'].preferences
    dev = None
    for kind in ('OPTIX', 'CUDA', 'HIP', 'ONEAPI', 'METAL'):
        try:
            prefs.compute_device_type = kind
            prefs.get_devices()
            if any(d.type == kind for d in prefs.devices):
                dev = kind
                break
        except TypeError:
            continue
    if dev:
        for d in prefs.devices:
            d.use = d.type == dev
        cy.device = 'GPU'
    print('cycles device', dev or 'CPU')
    cy.samples = 256
    cy.use_adaptive_sampling = True
    cy.adaptive_threshold = 0.01
    cy.use_denoising = True
    try:
        cy.denoiser = 'OPENIMAGEDENOISE'
    except TypeError:
        pass
    cy.max_bounces = 8
    cy.glossy_bounces = 6
    sc.render.film_transparent = False
    vs = sc.view_settings
    try:
        vs.view_transform = 'AgX'
        vs.look = 'AgX - Medium High Contrast'
    except TypeError as e:
        print('view', e)
    vs.exposure = 0.0
    cam = bpy.data.objects.get('P9_cam')
    if not cam:
        cd = bpy.data.cameras.new('P9_cam')
        cam = bpy.data.objects.new('P9_cam', cd)
        LC.objects.link(cam)
    cam.data.sensor_width = 36
    cam.data.clip_start = 1
    cam.data.clip_end = 20000
    sc.camera = cam
    # the reference collection never renders
    ref = bpy.data.collections.get('P9_v2_ref')
    if ref:
        ref.hide_render = True
    return cam


def shot(path, eye_dir, dist, lens=85, res=(2400, 1350), target=TARGET, roll=0.0, samples=None, shift=(0, 0), card=1.2, card_up=0.35):
    cam = sc.camera
    d = Vector(eye_dir).normalized()
    cam.location = Vector(target) + d * dist
    # softbox behind and above the camera: the faces that look at the camera show it as a soft sheen
    up = Vector((0, 0, 1))
    cl = Vector(target) + d * (dist * 1.15) + up * (dist * card_up)
    c = area('L_card', cl, (dist * 1.1, dist * 0.55), card, aim=target)
    c.hide_render = card <= 0
    q = (Vector(target) - cam.location).to_track_quat('-Z', 'Y')
    cam.rotation_euler = q.to_euler()
    if roll:
        cam.rotation_euler.rotate_axis('Z', math.radians(roll))
    cam.data.lens = lens
    cam.data.shift_x, cam.data.shift_y = shift
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.resolution_percentage = 100
    if samples:
        sc.cycles.samples = samples
    sc.render.image_settings.file_format = 'PNG'
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    return path
