"""Cycles reference for the T15 windshield water (docs/vehicles/t15_windshield_rain_design.md).

The bead tile and the drop shapes from gen_textures.py built as real water (IOR 1.333, closed bodies 0.02 mm off the
glass) on a 5 mm pane (IOR 1.52) leaning back like the T15's (53.8 deg), seen from the driver's eye 0.70 m behind it:
an overcast sky (or night), a wet road, a street of grey boxes with windows, street lamps and a car's tail lamps ahead,
a dark cab around the eye. The in-game shaders (afl_windshield_film / afl_windshield_drop) are tuned against it.

  blender --background --factory-startup --python tools/blender/windshield_water/reference_render.py -- <work dir> <out dir> <shot>
shot: close | close_night (10 deg high, 0.14 mm a pixel) | game | game_night (the game's view, the tile at every second
sample) | game_fine (the tile at full resolution, the middle 20 %), each also rendered dry (<shot>_dry.png) for the offline shader
check; <work dir> holds beads_h.npy / drops_h.npy from gen_textures.py.
"""
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix, Vector

argv = sys.argv[sys.argv.index('--') + 1:]
WORK, OUTDIR, SHOT = os.path.abspath(argv[0]), os.path.abspath(argv[1]), argv[2] if len(argv) > 2 else 'close'
NIGHT = SHOT.endswith('night')
os.makedirs(OUTDIR, exist_ok=True)

BEAD_TILE, R_EQ = 0.22, 0.5
S_MID = 0.44                              # the glass s the camera looks at (for the shader check: same tile phase)
UP = Vector((0, -0.591125, 0.806580))
OUT = Vector((0, 0.806580, 0.591125))
ACROSS = Vector((1, 0, 0))
EYE = Vector((0, 0, 1.2))
G0 = EYE + Vector((0, 0.70, 0))          # the glass straight ahead of the eye
GLASS = Matrix(((1, UP.x, OUT.x, G0.x), (0, UP.y, OUT.y, G0.y), (0, UP.z, OUT.z, G0.z), (0, 0, 0, 1)))

scene = bpy.context.scene
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)


# ---------------------------------------------------------------- materials
def nodes_mat(name):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    return m, nt, nt.nodes.new('ShaderNodeOutputMaterial')


def glass_mat(name, ior, color=(1, 1, 1, 1)):
    m, nt, out = nodes_mat(name)
    g = nt.nodes.new('ShaderNodeBsdfGlass')
    g.inputs['IOR'].default_value = ior
    g.inputs['Roughness'].default_value = 0.0
    g.inputs['Color'].default_value = color
    nt.links.new(g.outputs[0], out.inputs['Surface'])
    return m


def solid_mat(name, color, rough=0.6, emit=None, strength=0.0):
    m, nt, out = nodes_mat(name)
    p = nt.nodes.new('ShaderNodeBsdfPrincipled')
    p.inputs['Base Color'].default_value = (*color, 1)
    p.inputs['Roughness'].default_value = rough
    if emit:
        p.inputs['Emission Color'].default_value = (*emit, 1)
        p.inputs['Emission Strength'].default_value = strength
    nt.links.new(p.outputs[0], out.inputs['Surface'])
    return m


WATER = glass_mat('water', 1.333)
PANE = glass_mat('pane', 1.52, (0.96, 0.985, 0.97, 1))
ASPHALT = solid_mat('asphalt', (0.045, 0.045, 0.047), 0.22)
PAINT = solid_mat('line', (0.7, 0.7, 0.68), 0.4)
CAB = solid_mat('cab', (0.035, 0.035, 0.037), 0.8)
HOOD = solid_mat('hood', (0.05, 0.055, 0.06), 0.25)
WINDOW_DAY = (0.10, 0.12, 0.15)


def box(name, mat, lo, hi):
    me = bpy.data.meshes.new(name)
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    v = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    f = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    me.from_pydata(v, [], f)
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    scene.collection.objects.link(ob)
    return ob


def quad(name, mat, pts):
    me = bpy.data.meshes.new(name)
    me.from_pydata(pts, [], [(0, 1, 2, 3)])
    me.materials.append(mat)
    ob = bpy.data.objects.new(name, me)
    scene.collection.objects.link(ob)
    return ob


# ---------------------------------------------------------------- the world
world = bpy.data.worlds.new('sky')
scene.world = world
world.use_nodes = True
wt = world.node_tree
wt.nodes.clear()
bg = wt.nodes.new('ShaderNodeBackground')
wo = wt.nodes.new('ShaderNodeOutputWorld')
tc = wt.nodes.new('ShaderNodeTexCoord')
sep = wt.nodes.new('ShaderNodeSeparateXYZ')
ramp = wt.nodes.new('ShaderNodeValToRGB')
fac = wt.nodes.new('ShaderNodeMath')
fac.operation = 'MULTIPLY_ADD'
fac.inputs[1].default_value = 0.5
fac.inputs[2].default_value = 0.5
wt.links.new(tc.outputs['Generated'], sep.inputs[0])
wt.links.new(sep.outputs['Z'], fac.inputs[0])
wt.links.new(fac.outputs[0], ramp.inputs['Fac'])
wt.links.new(ramp.outputs['Color'], bg.inputs['Color'])
wt.links.new(bg.outputs[0], wo.inputs['Surface'])
cr = ramp.color_ramp
if NIGHT:
    cr.elements[0].position, cr.elements[0].color = 0.0, (0.004, 0.004, 0.005, 1)
    cr.elements[1].position, cr.elements[1].color = 0.6, (0.012, 0.014, 0.022, 1)
    bg.inputs['Strength'].default_value = 1.0
else:
    # 0.5 + 0.5 z of the view direction: ground haze below, a bright horizon, a duller zenith
    cr.elements[0].position, cr.elements[0].color = 0.45, (0.20, 0.205, 0.21, 1)
    e = cr.elements.new(0.52)
    e.color = (0.86, 0.88, 0.90, 1)
    cr.elements[1].position, cr.elements[1].color = 1.0, (0.62, 0.65, 0.70, 1)
    bg.inputs['Strength'].default_value = 1.0

# ground, road lines, the street
quad('ground', ASPHALT, [(-300, -50, 0), (300, -50, 0), (300, 400, 0), (-300, 400, 0)])
for x in (-1.8, 1.8):
    for k in range(40):
        y = 6 + k * 9
        quad(f'line{x}_{k}', PAINT, [(x - 0.06, y, 0.002), (x + 0.06, y, 0.002), (x + 0.06, y + 3, 0.002), (x - 0.06, y + 3, 0.002)])
rng = np.random.default_rng(3)
for side in (-1, 1):
    y = 12.0
    while y < 260:
        w = rng.uniform(10, 26)
        hgt = rng.uniform(7, 30)
        x0 = side * rng.uniform(11, 15)
        grey = rng.uniform(0.18, 0.38)
        b = box(f'bld{side}_{int(y)}', solid_mat(f'wall{side}_{int(y)}', (grey, grey * 0.98, grey * 0.95), 0.85),
                (min(x0, x0 + side * 14), y, 0), (max(x0, x0 + side * 14), y + w, hgt))
        # windows on the street face
        lit = solid_mat(f'win{side}_{int(y)}', WINDOW_DAY, 0.1, (1.0, 0.78, 0.5), 2.5 if NIGHT else 0.0)
        for fl in range(int(hgt // 3.2)):
            for c in range(int(w // 2.6)):
                if rng.random() < (0.45 if NIGHT else 1.0):
                    yy, zz = y + 0.8 + c * 2.6, 1.2 + fl * 3.2
                    xf = x0 - side * 0.02
                    quad(f'w{side}_{int(y)}_{fl}_{c}', lit, [(xf, yy, zz), (xf, yy + 1.4, zz), (xf, yy + 1.4, zz + 1.6), (xf, yy, zz + 1.6)])
        y += w + rng.uniform(2, 8)
# street lamps (on at night; dim heads by day) and a car's tail lamps ahead
lamp = solid_mat('lamp', (0.9, 0.9, 0.85), 0.3, (1.0, 0.72, 0.42), 60.0 if NIGHT else 0.0)
for side in (-1, 1):
    for k in range(10):
        y = 14 + k * 26
        box(f'pole{side}_{k}', solid_mat('pole', (0.2, 0.2, 0.2), 0.5), (side * 7 - 0.08, y - 0.08, 0), (side * 7 + 0.08, y + 0.08, 8))
        box(f'head{side}_{k}', lamp, (side * 6.0 - 0.3, y - 0.15, 7.8), (side * 6.0 + 0.3, y + 0.15, 7.95))
tail = solid_mat('tail', (0.3, 0.02, 0.02), 0.3, (1.0, 0.05, 0.02), 60.0)
box('car', solid_mat('carbody', (0.12, 0.12, 0.13), 0.3), (-0.9, 24, 0.3), (0.9, 28.5, 1.45))
for x in (-0.75, 0.75):
    box(f'tail{x}', tail, (x - 0.12, 23.97, 0.85), (x + 0.12, 24.0, 1.0))
# the cab around the eye (open toward the glass) and the hood ahead
box('cab_floor', CAB, (-0.9, -1.4, 0.3), (0.9, 0.55, 0.32))
box('cab_roof', CAB, (-0.9, -1.4, 1.62), (0.9, 0.42, 1.64))
for x in (-0.9, 0.9):
    box(f'cab_side{x}', CAB, (x - 0.01, -1.4, 0.3), (x + 0.01, 0.6, 1.64))
box('cab_back', CAB, (-0.9, -1.42, 0.3), (0.9, -1.4, 1.64))
box('dash', CAB, (-0.9, 0.2, 0.86), (0.9, 0.62, 0.98))
quad('hood', HOOD, [(-0.95, 0.95, 1.0), (0.95, 0.95, 1.0), (0.95, 2.9, 0.93), (-0.95, 2.9, 0.93)])

# the pane: outer face at the glass plane, 5 mm thick
pane = box('pane', PANE, (-0.9, -0.5, -0.005), (0.9, 0.5, 0.0))
pane.matrix_world = GLASS @ Matrix.Diagonal((-1, 1, 1, 1))   # mirrored in u: a right-handed transform (the box is symmetric)


# ---------------------------------------------------------------- the water
def water_field(u0, u1, s0, s1, step, placed):
    """The bead tile over the window (periodic; every step-th sample), with a few CPU-size drops and a running drop's
    trail stamped in; what was stamped goes into placed (lines for the offline shader check)."""
    H = np.load(os.path.join(WORK, 'beads_h.npy')).astype(np.float64)
    AV = np.load(os.path.join(WORK, 'beads_a.npy'))
    H, AV = H[::step, ::step], AV[::step, ::step]
    px = BEAD_TILE / H.shape[0]
    iu = np.arange(int(math.floor(u0 / px)), int(math.ceil(u1 / px)))
    js = np.arange(int(math.floor(s0 / px)), int(math.ceil(s1 / px)))
    F = H[np.ix_(js % H.shape[0], iu % H.shape[1])]
    U = (iu + 0.5) * px
    S = (js + 0.5) * px
    # the shader's second layer (afl_windshield_film: turned 36.87 deg, 1.37 times larger, the beads with A under
    # 0.55 at full wetness); glass s of the window centre = S_MID (the tile is periodic, the first layer lines up)
    n = H.shape[0]
    gu, gs = U[None, :], S[:, None] + S_MID
    tu = (0.8 * gu - 0.6 * gs) / (BEAD_TILE * 1.37) + 0.37
    ts = (0.6 * gu + 0.8 * gs) / (BEAD_TILE * 1.37) + 0.61
    x, y = tu * n - 0.5, ts * n - 0.5
    x0, y0 = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    fx, fy = x - x0, y - y0
    h2 = (H[y0 % n, x0 % n] * (1 - fx) * (1 - fy) + H[y0 % n, (x0 + 1) % n] * fx * (1 - fy)
          + H[(y0 + 1) % n, x0 % n] * (1 - fx) * fy + H[(y0 + 1) % n, (x0 + 1) % n] * fx * fy) * 1.37
    a2 = AV[np.rint(y).astype(np.int64) % n, np.rint(x).astype(np.int64) % n]
    F = np.maximum(F, np.where(a2 < 0.55, h2, 0))
    D = np.load(os.path.join(WORK, 'drops_h.npy')).astype(np.float64)   # rows, cols, k, k (cell units)
    k = D.shape[-1]

    def stamp(row, col, r, cu, cs, clear=0.0005):
        half = r / R_EQ
        cell = D[row, col]
        a = (U[None, :] - cu) / half
        b = (S[:, None] - cs) / half
        inside = (np.abs(a) < 1) & (np.abs(b) < 1)
        x = np.clip((a + 1) / 2 * k - 0.5, 0, k - 1.001)
        y = np.clip((b + 1) / 2 * k - 0.5, 0, k - 1.001)
        x0, y0 = x.astype(int), y.astype(int)
        fx, fy = x - x0, y - y0
        h = (cell[y0, x0] * (1 - fx) * (1 - fy) + cell[y0, x0 + 1] * fx * (1 - fy)
             + cell[y0 + 1, x0] * (1 - fx) * fy + cell[y0 + 1, x0 + 1] * fx * fy) * half
        h = np.where(inside, h, 0)
        # the drop took in the beads under it and a little around it
        foot = h > 0
        grow = foot.copy()
        n = int(math.ceil(clear / px))
        for _ in range(n):
            grow = grow | np.roll(grow, 1, 0) | np.roll(grow, -1, 0) | np.roll(grow, 1, 1) | np.roll(grow, -1, 1)
        F[grow] = 0
        np.maximum(F, h, out=F)
        placed.append(f'drop {row * 8 + col} {r} {cu} {cs} 0 1')

    span_u, span_s = u1 - u0, s1 - s0
    stamp(0, 3, 0.0016, u0 + 0.22 * span_u, s0 + 0.70 * span_s)
    stamp(1, 1, 0.0022, u0 + 0.62 * span_u, s0 + 0.32 * span_s)
    stamp(2, 5, 0.0028, u0 + 0.80 * span_u, s0 + 0.74 * span_s)
    stamp(0, 6, 0.0012, u0 + 0.40 * span_u, s0 + 0.20 * span_s)
    # a running drop low on the left and its trail up the glass behind it: a thin wet line, beads cleared along it
    ru, rs = u0 + 0.35 * span_u, s0 + 0.45 * span_s
    w, h0 = 0.0008, 0.00005
    a = np.abs(U[None, :] - ru) / w
    trail = (a < 1) & (S[:, None] > rs) & (S[:, None] < s1)
    wide = (np.abs(U[None, :] - ru) < w + 0.0012) & (S[:, None] > rs) & (S[:, None] < s1)
    F[wide] = 0
    F = np.maximum(F, np.where(trail, h0 * (1 - a * a), 0))
    # the running drop: head down (cell +y is its way: turned 180 deg), stamped by hand
    half = 0.0024 / R_EQ
    cell = D[3, 2][::-1, ::-1]
    a = (U[None, :] - ru) / half
    b = (S[:, None] - rs) / half
    inside = (np.abs(a) < 1) & (np.abs(b) < 1)
    xi = np.clip(((a + 1) / 2 * k).astype(int), 0, k - 1)
    yi = np.clip(((b + 1) / 2 * k).astype(int), 0, k - 1)
    h = np.where(inside, cell[yi, xi] * half, 0)
    F[h > 0] = 0
    F = np.maximum(F, h)
    placed.append(f'trail {ru} {rs} {s1} {w} {2 * h0 / w}')
    placed.append(f'drop 26 0.0024 {ru} {rs} 0 -1')
    return F, U, S, px


def water_mesh(F, U, S, px, gap=0.00002):
    """Top and bottom faces of every body of water in F (glass frame: u, s, n), as one mesh."""
    wet = F > 0
    q = wet[:-1, :-1] | wet[1:, :-1] | wet[:-1, 1:] | wet[1:, 1:]
    used = np.zeros_like(wet)
    used[:-1, :-1] |= q
    used[1:, :-1] |= q
    used[:-1, 1:] |= q
    used[1:, 1:] |= q
    idx = -np.ones(F.shape, np.int64)
    n = int(used.sum())
    idx[used] = np.arange(n)
    js, iu = np.nonzero(used)
    uu, ss, hh = U[iu], S[js], F[js, iu]
    top = np.stack([uu, ss, gap + hh], -1)
    bot = np.stack([uu, ss, np.full_like(uu, gap)], -1)
    # normals of the top (from the field), into the world
    hx = (np.roll(F, -1, 1) - np.roll(F, 1, 1)) / (2 * px)
    hy = (np.roll(F, -1, 0) - np.roll(F, 1, 0)) / (2 * px)
    nl = np.stack([-hx[js, iu], -hy[js, iu], np.ones(n)], -1)
    nl /= np.linalg.norm(nl, axis=1, keepdims=True)
    qj, qi = np.nonzero(q)
    a, b, c, d = idx[qj, qi], idx[qj, qi + 1], idx[qj + 1, qi + 1], idx[qj + 1, qi]
    # (across, up, out) is left-handed (right, up, away from the eye): the winding that faces out is clockwise in (u, s)
    faces_top = np.stack([a, d, c, b], -1)
    faces_bot = np.stack([a, b, c, d], -1) + n
    verts = np.concatenate([top, bot])
    faces = np.concatenate([faces_top, faces_bot])
    R = np.array(GLASS)[:3, :3]
    T = np.array(GLASS)[:3, 3]
    world_v = verts @ R.T + T
    world_n = np.concatenate([nl @ R.T, np.tile(-np.array(OUT), (n, 1))])
    me = bpy.data.meshes.new('water')
    me.vertices.add(len(world_v))
    me.vertices.foreach_set('co', world_v.astype(np.float32).ravel())
    nf = len(faces)
    me.loops.add(nf * 4)
    me.loops.foreach_set('vertex_index', faces.astype(np.int32).ravel())
    me.polygons.add(nf)
    me.polygons.foreach_set('loop_start', (np.arange(nf) * 4).astype(np.int32))
    me.update(calc_edges=True)
    me.polygons.foreach_set('use_smooth', np.ones(nf, bool))
    me.normals_split_custom_set_from_vertices(world_n.astype(np.float32).tolist())
    me.materials.append(WATER)
    ob = bpy.data.objects.new('water', me)
    scene.collection.objects.link(ob)
    print(f'water mesh: {len(world_v)} vertices, {nf} quads')
    return ob


# close: 10 deg high, 0.14 mm a pixel, all wet; game: the game's 70 deg at 1920 x 1080 (0.9 mm a pixel at the glass),
# only the middle 30 % rendered wet (border), the water at every second sample of the tile
GAME = SHOT.startswith('game')
FINE = 'fine' in SHOT                      # game_fine: the tile at full resolution, the middle 20 % only
FOV = math.radians(70 if GAME else 10)
RES = (1920, 1080) if GAME else (1600, 900)
BORDER = ((0.4, 0.4, 0.6, 0.6) if FINE else (0.35, 0.35, 0.65, 0.65)) if GAME else (0.0, 0.0, 1.0, 1.0)


def on_glass(nx, ny):
    """Where the eye's ray through (nx, ny) (0..1 across / up the frame) meets the glass: (u, s) about G0."""
    t = math.tan(FOV / 2)
    d = Vector(((nx * 2 - 1) * t * RES[0] / RES[1], 1.0, (ny * 2 - 1) * t))
    p = EYE + d * ((G0 - EYE).dot(OUT) / d.dot(OUT))
    return (p - G0).dot(ACROSS), (p - G0).dot(UP)


corners = [on_glass(x, y) for x in (BORDER[0], BORDER[2]) for y in (BORDER[1], BORDER[3])]
cu0, cu1 = min(c[0] for c in corners), max(c[0] for c in corners)
cs0, cs1 = min(c[1] for c in corners), max(c[1] for c in corners)
mu, ms = (cu1 - cu0) * 0.08, (cs1 - cs0) * 0.08
placed = []
F, U, S, px = water_field(cu0 - mu, cu1 + mu, cs0 - ms, cs1 + ms, 2 if GAME and not FINE else 1, placed)
water = water_mesh(F, U, S, px)
with open(os.path.join(OUTDIR, f'windshield_ref_{SHOT}.txt'), 'w') as f:
    f.write(f'view {math.degrees(FOV)} {RES[0]} {RES[1]} {S_MID} {" ".join(str(b) for b in BORDER)}\n')
    f.write(f'window {cu0 - mu} {cu1 + mu} {cs0 - ms} {cs1 + ms}\n')
    f.write('\n'.join(placed) + '\n')

# ---------------------------------------------------------------- camera, render
cam = bpy.data.cameras.new('eye')
cam.sensor_fit = 'VERTICAL'
cam.angle = FOV
cam.clip_start = 0.05
cam_ob = bpy.data.objects.new('eye', cam)
scene.collection.objects.link(cam_ob)
cam_ob.location = EYE
cam_ob.rotation_euler = (math.pi / 2, 0, 0)
scene.camera = cam_ob

scene.render.engine = 'CYCLES'
prefs = bpy.context.preferences.addons['cycles'].preferences
prefs.compute_device_type = 'OPTIX'
prefs.get_devices()
for dev in prefs.devices:
    dev.use = dev.type == 'OPTIX'
scene.cycles.device = 'GPU'
scene.cycles.samples = 768
scene.cycles.use_denoising = False
scene.cycles.max_bounces = 32
scene.cycles.transmission_bounces = 32
scene.cycles.glossy_bounces = 12
scene.cycles.diffuse_bounces = 3
scene.cycles.transparent_max_bounces = 8
scene.cycles.caustics_refractive = True
scene.cycles.caustics_reflective = True
scene.cycles.blur_glossy = 0.0
scene.cycles.sample_clamp_indirect = 20.0
scene.render.resolution_x, scene.render.resolution_y = RES
scene.render.resolution_percentage = 100
scene.view_settings.view_transform = 'Standard'
scene.view_settings.look = 'None'
scene.view_settings.exposure = 0.0 if NIGHT else -0.4
scene.render.image_settings.file_format = 'PNG'

for dry in (False, True):
    water.hide_render = dry
    scene.cycles.samples = 128 if dry else 768
    # wet: only the window (the rest stays transparent); dry: the whole frame (the shader check's Scene)
    scene.render.use_border = not dry and GAME
    scene.render.use_crop_to_border = False
    scene.render.border_min_x, scene.render.border_min_y, scene.render.border_max_x, scene.render.border_max_y = BORDER
    scene.render.film_transparent = False
    scene.render.filepath = os.path.join(OUTDIR, f'windshield_ref_{SHOT}{"_dry" if dry else ""}.png')
    bpy.ops.render.render(write_still=True)
    print('wrote', scene.render.filepath)
# the scene itself (large: the water is a few million vertices) stays in the work dir
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(WORK, f'windshield_ref_{SHOT}.blend'), compress=True)
