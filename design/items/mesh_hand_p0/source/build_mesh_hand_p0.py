"""H1-P0 scene builder (run in Blender: exec this file; it reads hand_p0_lib.py beside it).

Stages, all re-runnable and idempotent on the scene:
  place  - the P9-01 V3 reference (collection P9_ref, from the game aflmesh, read only) must already be in the scene;
           the hand root is oriented on the grip rake and slid in until the palm touches the grip's right side
  rig    - armature 'hand_rig' (16 bones) at the bind pose (relaxed open hand on the grip)
  solve  - grip pose: every finger chain closes with contact stops (gun surface + already placed fingers)
  mesh   - B: one blended skin body 'hand_B' (SDF -> OpenVDB iso-surface -> decimate, weights from the bone shapes);
           A: one closed rigid piece per bone 'A_<bone>' parented to its bone
  anim   - action 'open_to_grip_p9': open hand approaches, seats, fingers close one after another, then holds
Results are kept on the armature as custom properties (grip eulers, root approach) for the exporter and checks.
"""
import importlib
import json
import math
import sys

import bpy
import numpy as np
from mathutils import Euler, Matrix, Vector

SRC = r"D:/Minecraft Modding/Apocalypse First Light/design/items/mesh_hand_p0/source"
if SRC not in sys.path:
    sys.path.insert(0, SRC)
import hand_p0_lib as L
importlib.reload(L)

# grip placement: the grip's rake (deg from vertical), the hand's yaw (deg, + = fingers toward the gun's left),
# and where the middle finger's MCP joint sits on the grip (scene y, z; x comes from the palm contact)
GRIP = dict(rake=20.0, yaw=-6.0, roll=15.0,anchor='middle_01', anchor_yz=(-1.55, 1.85), palm_gap=0.01,
            seat_groups=('hand', 'index_01', 'middle_01', 'ring_01', 'pinky_01'))

# closing profiles per chain, per joint: (preset euler deg, closing rate deg per unit (x flex, y twist, z ulnar), max units)
PROFILES = {
    'index':  [((0, 0, -19), (1.0, 0, 0), 40), ((0, 0, 0), (0.3, 0, 0), 20), ((0, 0, 0), (0.2, 0, 0), 12)],
    'middle': [((0, 0, 0), (1.0, 0, 0), 90), ((0, 0, 0), (1.2, 0, 0), 100), ((0, 0, 0), (0.8, 0, 0), 75)],
    'ring':   [((0, 0, 0), (1.0, 0, -L.FINGERS[2][4] / 70), 90), ((0, 0, 0), (1.2, 0, 0), 100), ((0, 0, 0), (0.8, 0, 0), 75)],
    'pinky':  [((0, 0, 0), (1.0, 0, -L.FINGERS[3][4] / 70), 90), ((0, 0, 0), (1.2, 0, 0), 100), ((0, 0, 0), (0.8, 0, 0), 75)],
    'thumb':  [((0, 0, 0), (0, 0, 0), 0), ((0, 0, 0), (1.0, 0, 0), 20), ((0, 0, 0), (1.0, 0, 0), 10)],   # after REACH
}
# target-driven chains: tip goal (scene px), the solved degrees of freedom (bone, euler axis) and their limits deg
REACH = {
    'thumb': dict(tip=(-1.2, -1.6, 3.6), gap=0.02, touch=200.0,   # thumb down along the left panel, over the middle finger
                  dofs=[('thumb_01', 0), ('thumb_01', 1), ('thumb_01', 2), ('thumb_02', 0), ('thumb_03', 0)],
                  limits=[(-30, 80), (-60, 60), (-60, 60), (-15, 60), (-5, 60)]),
}
ORDER = [('index', []), ('middle', ['index']), ('ring', ['middle']), ('pinky', ['ring']), ('thumb', ['index', 'middle'])]
CONTACT, PENETRATE, STEP = 0.015, -0.004, 0.5

STATE = bpy.app.driver_namespace.setdefault('afl_hand_p0', {})


def collection(name):
    c = bpy.data.collections.get(name) or bpy.data.collections.new(name)
    if c.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(c)
    return c


def gun():
    if 'gun' not in STATE:
        STATE['gun'] = L.GunField(list(bpy.data.collections['P9_ref'].objects))
    return STATE['gun']


def root_rotation():
    g, y = math.radians(GRIP['rake']), math.radians(GRIP['yaw'])
    r = Vector((0, math.sin(g), math.cos(g)))
    d0 = Vector((0, math.cos(g), -math.sin(g)))
    d = d0 * math.cos(y) - Vector((1, 0, 0)) * math.sin(y)
    # roll about the distal axis: + tips the radial (thumb) edge over toward the gun, the web onto the backstrap
    return Matrix.Rotation(-math.radians(GRIP['roll']), 3, d) @ L.mat3(r, d, r.cross(d))


def coarse_points(sdf, bones, voxel=2.0):
    """Surface points per bone group, in H mm."""
    out = {}
    for b in bones:
        lo, hi = sdf.extent([b.name])
        v, _ = L.polygonize(lambda p, n=b.name: sdf.group(n, p), lo, hi, voxel)
        out[b.name] = [Vector(p) for p in v]
    return out


def place():
    bones = L.skeleton()
    sdf = L.HandSDF(bones)
    pts = coarse_points(sdf, bones)
    R = root_rotation()
    anchor = next(b for b in bones if b.name == GRIP['anchor']).head * L.MM
    y, z = GRIP['anchor_yz']
    g = gun()

    palm = np.array([tuple(R @ (p * L.MM)) for n in GRIP['seat_groups'] for p in pts[n]])

    def palm_clearance(x):
        o = np.array(Vector((x, y, z)) - R @ anchor)
        return float(g.sd_many(palm + o).min())

    lo, hi = 0.0, 4.0                       # slide in along -x until the palm touches
    assert palm_clearance(hi) > 0
    for _ in range(40):
        mid = (lo + hi) / 2
        if palm_clearance(mid) > GRIP['palm_gap']:
            hi = mid
        else:
            lo = mid
    origin = Vector((hi, y, z)) - R @ anchor
    STATE.update(bones=bones, sdf=sdf, pts=pts, rot=R, origin=origin, rig=L.Rig(bones, R, origin))
    print('placed: wrist', tuple(round(c, 3) for c in origin), 'palm clearance', round(palm_clearance(hi), 4))


def rig():
    for ob in [o for o in bpy.data.objects if o.name == 'hand_rig']:
        bpy.data.objects.remove(ob)
    arm = bpy.data.armatures.get('hand_rig') or bpy.data.armatures.new('hand_rig')
    ob = bpy.data.objects.new('hand_rig', arm)
    collection('Hand_P0').objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.mode_set(mode='EDIT')
    for eb in list(arm.edit_bones):
        arm.edit_bones.remove(eb)
    rg = STATE['rig']
    for b in STATE['bones']:
        m = rg.rest[b.name]
        eb = arm.edit_bones.new(b.name)
        eb.head = m.translation
        eb.tail = m.translation + m.col[1].to_3d() * b.length * L.MM
        eb.align_roll(m.col[2].to_3d())
        if b.parent:
            eb.parent = arm.edit_bones[b.parent]
            eb.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')
    for pb in ob.pose.bones:
        pb.rotation_mode = 'XYZ'
    arm.display_type = 'STICK'
    STATE['arm'] = ob
    # the rest matrices Blender made must equal the lib's (roll from align_roll)
    worst = max(max(abs(a - c) for ra, rc in zip(ob.data.bones[b.name].matrix_local, rg.rest[b.name]) for a, c in zip(ra, rc))
                for b in STATE['bones'])
    print('rig: bones', len(ob.data.bones), 'rest matrix error', worst)


def chain_names(chain):
    return ['%s_%02d' % (chain, j + 1) for j in range(3)]


def capsules(world, names):
    out = []
    by = {b.name: b for b in STATE['bones']}
    for n in names:
        b = by[n]
        m = world[n]
        a = m.translation.copy()
        t = m @ Vector((0, b.length * L.MM, 0))
        out.append((a, t, 0.4 * (b.radii[0] + b.radii[1]) * L.MM))   # 0.8 of the skin radius: neighbours may touch
    return out


def reach(chain, names, eulers, obstacles, local_pts):
    """Target-driven start for a chain (the thumb): joint angles that bring the chain's tip to a point on the gun
    without penetrating anything (random search, then coordinate descent; seeded, so repeatable)."""
    import random
    spec, rg, g = REACH[chain], STATE['rig'], gun()
    by = {b.name: b for b in STATE['bones']}
    target = np.array(spec['tip'])
    tip_bone = names[-1]

    def pose_of(x):
        e = {n: [0.0, 0.0, 0.0] for n in names}
        for (n, axis), v in zip(spec['dofs'], x):
            e[n][axis] = v
        return {n: tuple(v) for n, v in e.items()}

    def score(x):
        e = dict(eulers)
        e.update(pose_of(x))
        w = rg.pose(e)
        pen = 0.0
        for n in names:
            m = np.array(w[n])
            pts = local_pts[n] @ m[:3, :3].T + m[:3, 3]
            d = g.sd_many(pts) - spec['gap']
            for a, b, r in obstacles:
                d = np.minimum(d, L.capsule_sd_many(pts, a, b, r))
            pen += float(np.square(np.minimum(d, 0)).sum())
        tip = np.array(w[tip_bone] @ Vector((0, by[tip_bone].length * L.MM, 0)))
        touch = 0.0
        if spec.get('touch'):            # the last segment must rest on the gun, not hover beside it
            m = np.array(w[tip_bone])
            d = g.sd_many(local_pts[tip_bone] @ m[:3, :3].T + m[:3, 3]).min() - spec['gap']
            touch = spec['touch'] * max(float(d), 0.0) ** 2
        return float(np.square(tip - target).sum()) + 2000 * pen + touch

    lim = spec['limits']
    rnd = random.Random(1)
    starts = sorted(([rnd.uniform(*l) for l in lim] for _ in range(3000)), key=score)[:8]

    def descend(x):
        s, step = score(x), 8.0
        while step > 0.1:
            better = False
            for k in range(len(lim)):
                for sgn in (1, -1):
                    y = list(x)
                    y[k] = min(max(y[k] + sgn * step, lim[k][0]), lim[k][1])
                    sy = score(y)
                    if sy < s:
                        s, x, better = sy, y, True
            if not better:
                step /= 2
        return s, x

    s, best = min((descend(x) for x in starts), key=lambda t: t[0])
    print('reach', chain, 'tip miss px', round(math.sqrt(max(s, 0)), 3), [round(v, 1) for v in best])
    return pose_of(best)


def solve():
    rg, g = STATE['rig'], gun()
    local_pts = {n: np.array([tuple(rg.rest[n].inverted() @ rg.world_of_h(p)) for p in ps]) for n, ps in STATE['pts'].items()}
    eulers, report = {}, {}
    for chain, neighbours in ORDER:
        names, prof = chain_names(chain), PROFILES[chain]
        obstacles = []
        if neighbours:
            world = rg.pose(eulers)
            for nb in neighbours:
                obstacles += capsules(world, chain_names(nb))
        c = [0.0] * 3
        active = [True] * 3
        base = {n: prof[j][0] for j, n in enumerate(names)}
        if chain in REACH:
            base = reach(chain, names, eulers, obstacles, local_pts)

        def apply(cs):
            for j, n in enumerate(names):
                _, rate, _ = prof[j]
                eulers[n] = tuple(base[n][i] + rate[i] * cs[j] for i in range(3))

        def clearances():
            world = rg.pose(eulers)
            res = []
            for n in names:
                m = np.array(world[n])
                w = local_pts[n] @ m[:3, :3].T + m[:3, 3]
                d = g.sd_many(w)
                for a, b, r in obstacles:
                    d = np.minimum(d, L.capsule_sd_many(w, a, b, r))
                res.append(float(d.min()))
            return res

        apply(c)
        start = clearances()
        steps = 0
        while any(active) and steps < 400:
            steps += 1
            trial = [c[j] + (STEP if active[j] else 0) for j in range(3)]
            apply(trial)
            cl = clearances()
            hit = [k for k in range(3) if cl[k] < PENETRATE]
            if hit:
                apply(c)
                for j in range(hit[-1] + 1):
                    active[j] = False
                continue
            c = trial
            for k in range(3):
                if cl[k] < CONTACT:
                    for j in range(k + 1):
                        active[j] = False
            for j in range(3):
                if c[j] >= prof[j][2]:
                    active[j] = False
        apply(c)
        report[chain] = dict(units=c, clearance=[round(x, 4) for x in clearances()], start=[round(x, 3) for x in start])
        print('solve', chain, 'units', c, 'clearance px', report[chain]['clearance'], 'open clearance', report[chain]['start'])
    STATE['grip'] = {n: e for n, e in eulers.items()}
    STATE['solve_report'] = report
    arm = STATE.get('arm') or bpy.data.objects['hand_rig']
    arm['grip_eulers'] = json.dumps(STATE['grip'])


def pose_armature(eulers, root=None):
    arm = bpy.data.objects['hand_rig']
    for pb in arm.pose.bones:
        e = eulers.get(pb.name, (0, 0, 0))
        pb.rotation_euler = Euler([math.radians(a) for a in e], 'XYZ')
        pb.location = (0, 0, 0)
    if root:
        pb = arm.pose.bones['hand']
        pb.location = root[0]
        pb.rotation_euler = Euler([math.radians(a) for a in root[1]], 'XYZ')
    bpy.context.view_layer.update()


def mesh_object(name, verts_px, faces, coll):
    old = bpy.data.objects.get(name)
    if old:
        bpy.data.objects.remove(old)
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(v) for v in verts_px], [], faces)
    me.validate()
    ob = bpy.data.objects.new(name, me)
    collection(coll).objects.link(ob)
    import bmesh
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True
    return ob


def pieces(voxel=0.6, prefix='A_', ratio=None, coll='Hand_P0_A'):
    """A: one closed piece per bone group, vertices in the bone's local px, parented to the bone."""
    rg, sdf, arm = STATE['rig'], STATE['sdf'], bpy.data.objects['hand_rig']
    out = []
    for b in STATE['bones']:
        lo, hi = sdf.extent([b.name])
        v, f = L.polygonize(lambda p, n=b.name: sdf.group(n, p), lo, hi, voxel)
        inv = rg.rest[b.name].inverted()
        ob = mesh_object(prefix + b.name, [inv @ rg.world_of_h(Vector(p)) for p in v], f, coll)
        if ratio:
            mod = ob.modifiers.new('decimate', 'DECIMATE')
            mod.ratio = ratio
            bpy.context.view_layer.objects.active = ob
            bpy.ops.object.modifier_apply(modifier=mod.name)
        ob.parent = arm
        ob.parent_type = 'BONE'
        ob.parent_bone = b.name
        bpy.context.view_layer.update()
        ob.matrix_world = arm.matrix_world @ arm.pose.bones[b.name].matrix
        out.append(ob)
    return out


SKIN = dict(dorsal=(196, 150, 124), palmar=(224, 180, 158), nail=(230, 196, 186))
ASSETS = r"D:/Minecraft Modding/Apocalypse First Light/src/main/resources/assets/apocalypse_firstlight/"
WEIGHT_SIGMA = 3.0    # mm: how far a joint's blend reaches into the neighbouring segment


def skin_texture(path):
    """16 x 16: columns 0-7 dorsal -> palmar tone, column 15 nail (u in the meshes picks the column)."""
    old = bpy.data.images.get('mesh_hand_p0')
    if old:
        old.name = 'mesh_hand_p0_old'
    img = bpy.data.images.new('mesh_hand_p0', 16, 16, alpha=True)
    px = []
    for y in range(16):
        for x in range(16):
            if x >= 14:
                c = SKIN['nail']
            else:
                t = min(x, 7) / 7
                c = tuple(SKIN['dorsal'][i] * (1 - t) + SKIN['palmar'][i] * t for i in range(3))
            px += [c[0] / 255, c[1] / 255, c[2] / 255, 1.0]
    img.pixels.foreach_set(px)    # 8-bit byte buffer: values are stored as given (display sRGB)
    img.update()
    img.filepath_raw = path
    img.file_format = 'PNG'
    img.save()
    if old:
        old.user_remap(img)
        bpy.data.images.remove(old)
    return img


def local_coords(name, v_h):
    b = STATE['sdf'].by[name]
    return b.frame.transposed() @ (Vector(v_h) - b.head)


def uv_of(name, v_h):
    """u: dorsal -> palmar tone from the side of the bone the point is on; the nail on the dorsal fingertip."""
    b = STATE['sdf'].by[name]
    q = local_coords(name, v_h)
    r = 15.0 if b.radii is None else b.radii[0] + (b.radii[1] - b.radii[0]) * max(0.0, min(1.0, q.y / b.length))
    if name.endswith('_03') and q.z < -0.5 * r and q.y > 0.42 * b.length:
        return (15.5 / 16, 0.5)
    s = max(0.0, min(1.0, 0.5 + q.z / (1.6 * r)))
    return ((0.5 + 7 * s) / 16, 0.5)


def group_distances(v_h):
    """mm distance of each point to each bone group's own surface (N x bones)."""
    sdf = STATE['sdf']
    P = np.asarray(v_h, np.float32)
    return np.stack([sdf.group(b.name, P) for b in STATE['bones']], 1)


def skin_weights(v_h):
    """Per vertex up to three (bone index, weight): the nearest bone group, blended only with its parent and children."""
    bones = STATE['bones']
    idx = {b.name: i for i, b in enumerate(bones)}
    family = []
    for b in bones:
        fam = {idx[b.name]} | ({idx[b.parent]} if b.parent else set()) | {idx[c.name] for c in bones if c.parent == b.name}
        family.append(fam)
    D = group_distances(v_h)
    out = []
    for row in D:
        b0 = int(np.argmin(row))
        cand = sorted(((float(np.exp(-(row[j] - row[b0]) / WEIGHT_SIGMA)), j) for j in family[b0]), reverse=True)[:3]
        cand = [(w, j) for w, j in cand if w > 0.02]
        total = sum(w for w, _ in cand)
        out.append([(j, w / total) for w, j in cand])
    return out


def mesh():
    rg, sdf, arm = STATE['rig'], STATE['sdf'], bpy.data.objects['hand_rig']
    pose_armature({})
    import os
    os.makedirs(ASSETS + 'textures/dev', exist_ok=True)
    skin_texture(ASSETS + 'textures/dev/mesh_hand_p0.png')
    mat = bpy.data.materials.get('mesh_hand_p0_skin') or bpy.data.materials.new('mesh_hand_p0_skin')
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    tex = next((n for n in nodes if n.type == 'TEX_IMAGE'), None) or nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images['mesh_hand_p0']
    tex.interpolation = 'Closest'
    bsdf = next(n for n in nodes if n.type == 'BSDF_PRINCIPLED')
    mat.node_tree.links.new(tex.outputs[0], bsdf.inputs[0])
    bsdf.inputs['Roughness'].default_value = 0.6

    # B: one blended body
    lo, hi = sdf.extent([b.name for b in STATE['bones']])
    v, f = L.polygonize(sdf.body, lo, hi, 0.7)
    ob = mesh_object('hand_B', [rg.world_of_h(Vector(p)) for p in v], f, 'Hand_P0_B')
    decimate(ob, 7000)
    finish(ob, mat, skinned=True)
    # A: rigid pieces
    for piece in pieces(0.5, 'A_', None, 'Hand_P0_A'):
        decimate(piece, 900 if piece.name in ('A_hand', 'A_thumb_01') else 420)
        finish(piece, mat, skinned=False)
    print('mesh: B tris', sum(len(p.vertices) - 2 for p in ob.data.polygons), 'A tris',
          sum(sum(len(p.vertices) - 2 for p in o.data.polygons) for o in bpy.data.collections['Hand_P0_A'].objects))


def decimate(ob, tris):
    now = sum(len(p.vertices) - 2 for p in ob.data.polygons)
    mod = ob.modifiers.new('decimate', 'DECIMATE')
    mod.ratio = min(1.0, tris / now)
    mod.use_collapse_triangulate = True
    bpy.context.view_layer.objects.active = ob
    with bpy.context.temp_override(object=ob, active_object=ob):
        bpy.ops.object.modifier_apply(modifier=mod.name)
    for p in ob.data.polygons:
        p.use_smooth = True


def finish(ob, mat, skinned):
    """Material, UVs (u from the owning bone), and for B the vertex groups + armature modifier."""
    rg, arm = STATE['rig'], bpy.data.objects['hand_rig']
    me = ob.data
    me.materials.clear()
    me.materials.append(mat)
    world = [ob.matrix_world @ v.co for v in me.vertices]
    inv_root = rg.root.inverted()
    v_h = [tuple((inv_root @ w) / L.MM) for w in world]
    if skinned:
        weights = skin_weights(v_h)
        for vg in list(ob.vertex_groups):
            ob.vertex_groups.remove(vg)
        groups = [ob.vertex_groups.new(name=b.name) for b in STATE['bones']]
        for i, ws in enumerate(weights):
            for j, w in ws:
                groups[j].add([i], w, 'REPLACE')
        owner = [STATE['bones'][max(ws, key=lambda t: t[1])[0]].name for ws in weights]
        mod = ob.modifiers.get('armature') or ob.modifiers.new('armature', 'ARMATURE')
        mod.object = arm
        ob.parent = arm
    else:
        owner = [ob.parent_bone] * len(me.vertices)
    uv = me.uv_layers.get('uv') or me.uv_layers.new(name='uv')
    for poly in me.polygons:
        for li in poly.loop_indices:
            vi = me.loops[li].vertex_index
            uv.data[li].uv = uv_of(owner[vi], v_h[vi])


FPS, LENGTH = 60, 84          # 1.4 s
# root approach (world px offset at frame 0, local euler deg) and timing in frames: (start, end) per chain, joints of a
# chain start 2 frames apart (MCP leads); auto-clamped Bezier keys, so nothing overshoots and every move stops on its key
APPROACH = dict(offset=(0.4, -2.5, -0.8), euler=(-6, 0, 0), frames=(8, 28))   # from behind and below, along the grip
OPEN_FROM_GRIP = {'thumb': 1}
TIMING = {'thumb': (16, 40), 'index': (20, 36), 'middle': (22, 38), 'ring': (25, 41), 'pinky': (28, 44)}
JOINT_LAG = 2


def open_pose():
    """Frame-0 open hand: bone eulers (deg). Rest everywhere, except the leading joints named by OPEN_FROM_GRIP, which
    already hold their grip angle (the thumb is set on the left of the frame before the hand slides in)."""
    out = {}
    for chain, k in OPEN_FROM_GRIP.items():
        for n in chain_names(chain)[:k]:
            out[n] = STATE['grip'][n]
    return out


def anim():
    scene = bpy.context.scene
    scene.render.fps = FPS
    scene.frame_start, scene.frame_end = 0, LENGTH
    arm = bpy.data.objects['hand_rig']
    rg = STATE['rig']
    arm.animation_data_create()
    old = bpy.data.actions.get('open_to_grip_p9')
    if old:
        bpy.data.actions.remove(old)
    act = bpy.data.actions.new('open_to_grip_p9')
    arm.animation_data.action = act
    pose_armature({})
    R = rg.rest['hand'].to_3x3()
    loc0 = R.transposed() @ Vector(APPROACH['offset'])
    hand = arm.pose.bones['hand']
    f0, f1 = APPROACH['frames']
    for f, loc, e in ((0, loc0, APPROACH['euler']), (f0, loc0, APPROACH['euler']), (f1, Vector(), (0, 0, 0)), (LENGTH, Vector(), (0, 0, 0))):
        hand.location = loc
        hand.rotation_euler = Euler([math.radians(a) for a in e], 'XYZ')
        hand.keyframe_insert('location', frame=f)
        hand.keyframe_insert('rotation_euler', frame=f)
    grip, opened = STATE['grip'], open_pose()
    for chain, (t0, t1) in TIMING.items():
        for j, n in enumerate(chain_names(chain)):
            pb = arm.pose.bones[n]
            o = opened.get(n, (0, 0, 0))
            for f, e in ((0, o), (t0 + j * JOINT_LAG, o), (t1 + j * JOINT_LAG, grip[n]), (LENGTH, grip[n])):
                pb.rotation_euler = Euler([math.radians(a) for a in e], 'XYZ')
                pb.keyframe_insert('rotation_euler', frame=f)
    for fc in fcurves(act):
        for k in fc.keyframe_points:
            k.interpolation = 'BEZIER'
            k.handle_left_type = k.handle_right_type = 'AUTO_CLAMPED'
        fc.update()
    arm['approach'] = json.dumps(APPROACH)
    print('anim: frames', LENGTH, 'curves', len(fcurves(act)))


PATH_MARGIN, PATH_ITER = 0.12, 6   # px a waypoint keeps from the gun (so one key clears a span); waypoints per chain at most


def frame_pose(f):
    """Armature pose at frame f as (bone eulers deg, root offset) for Rig.pose."""
    bpy.context.scene.frame_set(f)
    arm = bpy.data.objects['hand_rig']
    e = {pb.name: tuple(math.degrees(a) for a in pb.rotation_euler) for pb in arm.pose.bones}
    h = arm.pose.bones['hand']
    return e, (Vector(h.location), e['hand'])


def path():
    """Keep every chain out of the gun between its keys: sample the action each frame, and where a chain dips into
    the gun, key that chain at the deepest frame with the nearest joint angles that clear it (coordinate descent on
    all of the chain's euler channels). Repeats until the motion is clear; the same for every chain."""
    rg, g, arm = STATE['rig'], gun(), bpy.data.objects['hand_rig']
    local_pts = {n: np.array([tuple(rg.rest[n].inverted() @ rg.world_of_h(p)) for p in ps]) for n, ps in STATE['pts'].items()}

    def depth(names, eulers, root, gap=0.0):
        w = rg.pose(eulers, root)
        pen, worst = 0.0, 1e9
        for n in names:
            m = np.array(w[n])
            d = g.sd_many(local_pts[n] @ m[:3, :3].T + m[:3, 3]) - gap
            pen += float(np.square(np.minimum(d, 0)).sum())
            worst = min(worst, float(d.min()))
        return pen, worst

    added = {}
    for chain, _ in ORDER:
        names = chain_names(chain)
        added[chain] = []
        for _ in range(PATH_ITER):
            frames = [(f,) + frame_pose(f) for f in range(LENGTH + 1)]
            scored = [(depth(names, e, r)[1], f, e, r) for f, e, r in frames]
            worst, f, e0, root = min(scored, key=lambda t: t[0])
            if worst > PENETRATE:       # the same tolerance as the grip solve: touching is fine
                break
            x0 = [e0[n][i] for n in names for i in range(3)]

            def cost(x):
                e = dict(e0)
                for k, n in enumerate(names):
                    e[n] = tuple(x[3 * k:3 * k + 3])
                return sum((a - b) ** 2 for a, b in zip(x, x0)) * 1e-3 + 1e4 * depth(names, e, root, PATH_MARGIN)[0]

            x, s, step = list(x0), cost(x0), 8.0
            while step > 0.25:
                better = False
                for k in range(len(x)):
                    for sgn in (1, -1):
                        y = list(x)
                        y[k] += sgn * step
                        sy = cost(y)
                        if sy < s:
                            s, x, better = sy, y, True
                if not better:
                    step /= 2
            for k, n in enumerate(names):
                pb = arm.pose.bones[n]
                pb.rotation_euler = Euler([math.radians(a) for a in x[3 * k:3 * k + 3]], 'XYZ')
                pb.keyframe_insert('rotation_euler', frame=f)
            for fc in fcurves(arm.animation_data.action):
                for kp in fc.keyframe_points:
                    kp.interpolation = 'BEZIER'
                    kp.handle_left_type = kp.handle_right_type = 'AUTO_CLAMPED'
                fc.update()
            added[chain].append((f, round(worst, 3)))
        print('path', chain, 'waypoints (frame, depth px before)', added[chain])
    STATE['waypoints'] = added


def fcurves(act):
    """F-curves of an action (Blender 4.4+ layered actions keep them in a channelbag)."""
    if hasattr(act, 'fcurves') and len(act.fcurves):
        return list(act.fcurves)
    out = []
    for layer in act.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                out += list(bag.fcurves)
    return out


def penetration(frame=None):
    """Deepest skin vertex inside the gun (px, positive = inside) for B and for A at the current pose / a frame."""
    for c in ('Hand_P0_A', 'Hand_P0_B'):        # hidden collections are not evaluated
        bpy.data.collections[c].hide_viewport = False
    if frame is not None:
        bpy.context.scene.frame_set(frame)
    dg = bpy.context.evaluated_depsgraph_get()
    g = gun()
    res = {}
    for key, obs in (('B', [bpy.data.objects['hand_B']]), ('A', list(bpy.data.collections['Hand_P0_A'].objects))):
        worst, count, where = 0.0, 0, None
        for ob in obs:
            ev = ob.evaluated_get(dg)
            me = ev.to_mesh()
            P = np.array([tuple(ev.matrix_world @ v.co) for v in me.vertices])
            ev.to_mesh_clear()
            d = g.sd_many(P)
            i = int(np.argmin(d))
            count += int((d < -0.01).sum())
            if -d[i] > worst:
                worst, where = float(-d[i]), (ob.name, tuple(round(c, 2) for c in P[i]))
        res[key] = dict(depth_px=round(worst, 4), depth_mm=round(worst / L.MM, 2), verts_over_0_01px=count, at=where)
    return res


def checks(step=1):
    """Acceptance numbers: gun penetration per frame (A, B), B edge stretch at the grip, how far neighbouring fingers
    are fused along the proximal phalanx at rest, and palm drift during the hold."""
    sc, arm, ob = bpy.context.scene, bpy.data.objects['hand_rig'], bpy.data.objects['hand_B']
    out = {'pen': {'A': 0.0, 'B': 0.0}, 'pen_frames_over': []}
    for f in range(0, LENGTH + 1, step):
        r = penetration(f)
        for k in 'AB':
            out['pen'][k] = max(out['pen'][k], r[k]['depth_mm'])
        if r['A']['depth_mm'] > 0.1 or r['B']['depth_mm'] > 1.0:
            out['pen_frames_over'].append(f)
    me, names = ob.data, [g.name for g in ob.vertex_groups]
    P0 = np.array([tuple(v.co) for v in me.vertices])
    E = np.array([tuple(e.vertices) for e in me.edges])
    L0 = np.linalg.norm(P0[E[:, 0]] - P0[E[:, 1]], axis=1)
    sc.frame_set(LENGTH)
    ev = ob.evaluated_get(bpy.context.evaluated_depsgraph_get())
    m2 = ev.to_mesh()
    P = np.array([tuple(v.co) for v in m2.vertices])
    ev.to_mesh_clear()
    r = np.linalg.norm(P[E[:, 0]] - P[E[:, 1]], axis=1) / np.maximum(L0, 1e-9)
    out['stretch'] = dict(p1=round(float(np.percentile(r, 1)), 3), p99=round(float(np.percentile(r, 99)), 3),
                          max=round(float(r.max()), 2), over_2=int((r > 2).sum()), edges=len(r))
    dom = [names[max(v.groups, key=lambda g: g.weight).group] for v in me.vertices]
    rg, fused = STATE['rig'], {}
    for a, b in E:
        ca, cb = dom[a].split('_')[0], dom[b].split('_')[0]
        if ca != cb and 'hand' not in (ca, cb):
            y = (rg.rest[ca + '_01'].inverted() @ me.vertices[a].co).y / L.MM
            k = '-'.join(sorted((ca, cb)))
            fused[k] = round(max(fused.get(k, 0.0), y), 1)
    out['fused_mm'] = fused
    ref, drift = None, 0.0
    for f in range(APPROACH['frames'][1], LENGTH + 1):
        sc.frame_set(f)
        m = arm.matrix_world @ arm.pose.bones['hand'].matrix
        ref = ref or m.copy()
        drift = max(drift, max(abs(m[i][j] - ref[i][j]) for i in range(3) for j in range(4)))
    out['palm_drift_px'] = round(drift, 6)
    print('checks', out)
    STATE['checks'] = out
    return out


def run(*stages):
    for s in stages:
        globals()[s]()
