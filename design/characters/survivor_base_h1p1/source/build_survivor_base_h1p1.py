"""H1-P1 scene builder (run in Blender: exec this file; it reads body_h1p1_lib.py beside it).

Stages, all re-runnable on the scene:
  rig    - armature 'survivor_rig' (metres): deform bones from the lib (A-pose rest) + IK controls / poles
  mesh   - 'survivor_body': one blended skin (SDF -> sparse OpenVDB iso-surface -> decimate hands and body separately),
           weights from the bone groups (<= 4 per vertex), tone UVs, armature modifier
  ik     - two-bone IK on both arms (no stretch, elbow pole, forearm hinge), hand rotation from its control,
           forearm twist from the hand's roll
  prop   - the P9-01 V3 reference (from the H1-P0 scene, read only) attached to hand_r at the H1-P0 grip, real size
  anim   - the test clips (one action each)
"""
import importlib
import json
import math
import os
import sys
import time

import bpy
import numpy as np
from mathutils import Euler, Matrix, Vector

SRC = r"D:/Minecraft Modding/Apocalypse First Light/design/characters/survivor_base_h1p1/source"
if SRC not in sys.path:
    sys.path.insert(0, SRC)
import body_h1p1_lib as L     # noqa: E402
importlib.reload(L.HL)
importlib.reload(L)

ASSETS = r"D:/Minecraft Modding/Apocalypse First Light/src/main/resources/assets/apocalypse_firstlight/"
TEXTURE = 'survivor_base_h1p1'
M = 0.001                       # scene metres per mm
VOXEL = 1.6                     # mm, iso-surface voxel before decimation
TRIS = dict(hand=3400, body=9000)     # per hand, rest of the body (+ the shorts rings; total <= 16384, the Vextris part budget)
STATE = bpy.app.driver_namespace.setdefault('afl_body_h1p1', {})


def collection(name):
    c = bpy.data.collections.get(name) or bpy.data.collections.new(name)
    if c.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(c)
    return c


def lib_bones():
    if 'bones' not in STATE:
        STATE['bones'] = L.skeleton()
        STATE['controls'] = L.controls(STATE['bones'])
    return STATE['bones']


def rest_matrix(b):
    m = b.frame.to_4x4()
    m.translation = b.head * M
    return m


def rig():
    bones = lib_bones()
    for ob in [o for o in bpy.data.objects if o.name == 'survivor_rig']:
        bpy.data.objects.remove(ob)
    arm = bpy.data.armatures.get('survivor_rig') or bpy.data.armatures.new('survivor_rig')
    ob = bpy.data.objects.new('survivor_rig', arm)
    collection('Body_H1P1').objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.mode_set(mode='EDIT')
    for eb in list(arm.edit_bones):
        arm.edit_bones.remove(eb)
    for b in bones + STATE['controls']:
        eb = arm.edit_bones.new(b.name)
        eb.head = b.head * M
        eb.tail = (b.head + b.frame.col[1] * b.length) * M
        eb.align_roll(b.frame.col[2])
        eb.use_deform = b.deform
        if b.parent:
            eb.parent = arm.edit_bones[b.parent]
            eb.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')
    for pb in ob.pose.bones:
        pb.rotation_mode = 'XYZ'
    arm.display_type = 'STICK'
    worst = max(max(abs(a - c) for ra, rc in zip(ob.data.bones[b.name].matrix_local, rest_matrix(b)) for a, c in zip(ra, rc))
                for b in bones + STATE['controls'])
    print('rig: bones', len(ob.data.bones), 'deform', sum(b.use_deform for b in ob.data.bones), 'rest matrix error', worst)


def sdf():
    if 'sdf' not in STATE:
        STATE['sdf'] = L.BodySDF(lib_bones())
    return STATE['sdf']


def mesh_object(name, verts_m, faces, coll):
    old = bpy.data.objects.get(name)
    if old:
        bpy.data.objects.remove(old)
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(v) for v in verts_m], [], faces)
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


def tri_count(ob):
    return sum(len(p.vertices) - 2 for p in ob.data.polygons)


def is_hand(points_mm):
    """Distal of 18 mm before the wrist joint, either hand."""
    s = sdf()
    P = np.asarray(points_mm, np.float32)
    out = np.zeros(len(P), bool)
    for Q in (P, P * np.array([-1, 1, 1], np.float32)):
        h = s.hand.to_h(Q)
        out |= (h[:, 1] > -18.0) & (np.linalg.norm(h, axis=1) < 260.0)
    return out


def decimate_regions(ob):
    """Collapse-decimate the hands and the rest separately (edit-mode selection), so the fingers keep their budget."""
    me = ob.data
    hand = is_hand([tuple(v.co / M) for v in me.vertices])
    bpy.context.view_layer.objects.active = ob
    for region, target in (('hand', 2 * TRIS['hand']), ('body', TRIS['body'])):
        me = ob.data
        hand = is_hand([tuple(v.co / M) for v in me.vertices])
        sel = hand if region == 'hand' else ~hand
        faces = [p for p in me.polygons if all(sel[i] for i in p.vertices)]
        now = sum(len(p.vertices) - 2 for p in faces)
        for p in me.polygons:
            p.select = all(sel[i] for i in p.vertices)
        for v in me.vertices:
            v.select = bool(sel[v.index])
        for e in me.edges:
            e.select = all(sel[i] for i in e.vertices)
        with bpy.context.temp_override(object=ob, active_object=ob, edit_object=ob):
            bpy.ops.object.mode_set(mode='EDIT')
            bpy.ops.mesh.select_mode(type='FACE')
            bpy.ops.mesh.decimate(ratio=min(1.0, target / max(now, 1)))
            bpy.ops.mesh.select_all(action='SELECT')
            bpy.ops.mesh.quads_convert_to_tris(quad_method='BEAUTY', ngon_method='BEAUTY')
            bpy.ops.mesh.select_all(action='DESELECT')
            bpy.ops.object.mode_set(mode='OBJECT')
        print('decimate', region, now, '->', target, 'total now', tri_count(ob))
    for p in ob.data.polygons:
        p.use_smooth = True


def skin_texture(path):
    """16 x 16: columns 0-7 dorsal -> palmar skin (the H1-P0 palette), 8-9 base shorts, 14-15 nail."""
    skin = dict(dorsal=(196, 150, 124), palmar=(224, 180, 158), nail=(230, 196, 186), shorts=(62, 64, 68))
    old = bpy.data.images.get(TEXTURE)
    if old:
        old.name = TEXTURE + '_old'
    img = bpy.data.images.new(TEXTURE, 16, 16, alpha=True)
    px = []
    for y in range(16):
        for x in range(16):
            if x >= 14:
                c = skin['nail']
            elif x in (8, 9):
                c = skin['shorts']
            else:
                t = min(x, 7) / 7
                c = tuple(skin['dorsal'][i] * (1 - t) + skin['palmar'][i] * t for i in range(3))
            px += [c[0] / 255, c[1] / 255, c[2] / 255, 1.0]
    img.pixels.foreach_set(px)
    img.update()
    img.filepath_raw = path
    img.file_format = 'PNG'
    img.save()
    if old:
        old.user_remap(img)
        bpy.data.images.remove(old)
    return img


def material():
    os.makedirs(ASSETS + 'textures/dev', exist_ok=True)
    skin_texture(ASSETS + 'textures/dev/%s.png' % TEXTURE)
    mat = bpy.data.materials.get(TEXTURE) or bpy.data.materials.new(TEXTURE)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    tex = next((n for n in nodes if n.type == 'TEX_IMAGE'), None) or nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images[TEXTURE]
    tex.interpolation = 'Closest'
    bsdf = next(n for n in nodes if n.type == 'BSDF_PRINCIPLED')
    mat.node_tree.links.new(tex.outputs[0], bsdf.inputs[0])
    bsdf.inputs['Roughness'].default_value = 0.6
    return mat


SHORTS = (775.0, 1040.0)        # mm: base shorts from mid thigh to the waist
SHORTS_X = 260.0                # mm: only the trunk and thighs (the A-pose hands hang outside this)


def shorts_rings(ob):
    """Cut edge rings at the shorts' hems (trunk and thighs only), so the shorts get straight edges."""
    import bmesh
    me = ob.data
    bm = bmesh.new()
    bm.from_mesh(me)
    for z in SHORTS:
        faces = [f for f in bm.faces if abs(f.calc_center_median().x / M) < SHORTS_X]
        edges = list({e for f in faces for e in f.edges})
        res = bmesh.ops.bisect_plane(bm, geom=faces + edges + list({v for f in faces for v in f.verts}),
                                     plane_co=(0, 0, z * M), plane_no=(0, 0, 1))
        cut = [f for f in bm.faces if len(f.verts) > 3]
        bmesh.ops.triangulate(bm, faces=cut, quad_method='BEAUTY', ngon_method='BEAUTY')
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = True


def mesh():
    s = sdf()
    t0 = time.time()
    bones = lib_bones()
    lo = np.min([tuple(b.head) for b in bones] + [tuple(b.tail) for b in bones], 0) - 120
    hi = np.max([tuple(b.head) for b in bones] + [tuple(b.tail) for b in bones], 0) + 120
    lo[2] = -10.0
    v, f = L.polygonize_sparse(s, lo, hi, VOXEL)
    ob = mesh_object('survivor_body', [Vector(p) * M for p in v], f, 'Body_H1P1')
    print('iso-surface tris', tri_count(ob), round(time.time() - t0, 1), 's')
    decimate_regions(ob)
    shorts_rings(ob)
    finish(ob)
    print('mesh: tris', tri_count(ob), 'verts', len(ob.data.vertices), round(time.time() - t0, 1), 's')


def finish(ob):
    s, arm = sdf(), bpy.data.objects['survivor_rig']
    me = ob.data
    mat = material()
    me.materials.clear()
    me.materials.append(mat)
    vmm = [tuple(v.co / M) for v in me.vertices]
    weights, D, names = L.skin_weights(s, vmm)
    for vg in list(ob.vertex_groups):
        ob.vertex_groups.remove(vg)
    deform = [b.name for b in arm.data.bones if b.use_deform and b.name != 'root']
    groups = {n: ob.vertex_groups.new(name=n) for n in deform}
    for i, ws in enumerate(weights):
        for n, w in ws:
            groups[n].add([i], w, 'REPLACE')
    owner = [max(ws, key=lambda t: t[1])[0] for ws in weights]
    # twist bones own no tone of their own
    owner = [o.replace('forearm_twist', 'forearm') for o in owner]
    mod = ob.modifiers.get('armature') or ob.modifiers.new('armature', 'ARMATURE')
    mod.object = arm
    ob.parent = arm
    tones = [L.tone(s, owner[i], vmm[i]) for i in range(len(vmm))]
    uv = me.uv_layers.get('uv') or me.uv_layers.new(name='uv')
    # per face: shorts between the cut rings (shorts_rings), nails where most corners are nail; skin tones per corner
    # (a nail corner on a skin face takes the dorsal tone, so no UV runs across the shorts / nail columns)
    for poly in me.polygons:
        c = sum((Vector(vmm[i]) for i in poly.vertices), Vector()) / len(poly.vertices)
        shorts = SHORTS[0] < c.z < SHORTS[1] and abs(c.x) < SHORTS_X
        nail = sum(tones[i] == 'nail' for i in poly.vertices) * 2 > len(poly.vertices)
        for li in poly.loop_indices:
            t = tones[me.loops[li].vertex_index]
            if shorts:
                u = 8.5 / 16
            elif nail:
                u = 15.5 / 16
            else:
                u = (0.5 + 7 * (0.15 if t == 'nail' else t)) / 16
            uv.data[li].uv = (u, 0.5)
    STATE['weights_names'] = names
    print('finish: groups', len(groups), 'max influences', max(len(w) for w in weights))


# joint ranges (degrees, bone local axes; see docs/dev/mesh_survivor_base_body_h1p1.md section 6)
#   upper arm from the A-pose: X + = forward flexion, Z - = abduction (up and out), Y = twist
#   elbow: X + = flexion from the 8 deg rest bend; wrist (hand frame): X + = palmar flexion, Z + = ulnar deviation,
#   Y = pronation / supination (the forearm twist bone takes TWIST_SHARE of it)
SHOULDER = dict(x=(-50, 150), y=(-70, 70), z=(-135, 60))
ELBOW = (-8, 142)
WRIST = dict(x=(-70, 80), y=(-90, 90), z=(-20, 45))   # ulnar 45: the cammed support wrist of the P9 grip (FP ADS)
TWIST_SHARE = 0.65


def ik():
    """Two-bone IK on both arms; the hand follows its control's rotation and stays on the wrist when the control is
    out of reach (no stretch)."""
    arm = bpy.data.objects['survivor_rig']
    pb = arm.pose.bones
    rad = math.radians
    for side in 'rl':
        ua, fa, tw, hd = (pb[n + '_' + side] for n in ('upper_arm', 'forearm', 'forearm_twist', 'hand'))
        for b in (ua, fa, tw, hd):
            for c in list(b.constraints):
                b.constraints.remove(c)
        c = fa.constraints.new('IK')
        c.name = 'arm_ik'
        c.target, c.subtarget = arm, 'ik_hand_' + side
        c.pole_target, c.pole_subtarget = arm, 'pole_elbow_' + side
        c.chain_count, c.use_stretch, c.use_tail = 2, False, True
        fa.ik_stretch = ua.ik_stretch = 0.0
        fa.lock_ik_y = fa.lock_ik_z = True
        fa.use_ik_limit_x, fa.ik_min_x, fa.ik_max_x = True, rad(ELBOW[0]), rad(ELBOW[1])
        # a mirrored side mirrors the Y / Z senses (X flexion keeps its sign)
        sg = 1 if side == 'r' else -1
        for ax in 'xyz':
            lo, hi = SHOULDER[ax]
            if ax != 'x' and sg < 0:
                lo, hi = -hi, -lo
            setattr(ua, 'use_ik_limit_' + ax, True)
            setattr(ua, 'ik_min_' + ax, rad(lo))
            setattr(ua, 'ik_max_' + ax, rad(hi))
        c = hd.constraints.new('COPY_ROTATION')
        c.name = 'hand_rot'
        c.target, c.subtarget = arm, 'ik_hand_' + side
        c.target_space = c.owner_space = 'WORLD'
        c = hd.constraints.new('LIMIT_ROTATION')
        c.name = 'wrist_limit'
        c.owner_space = 'LOCAL'
        c.euler_order = 'YXZ'
        for ax in 'xyz':
            lo, hi = WRIST[ax]
            if ax != 'x' and sg < 0:
                lo, hi = -hi, -lo
            setattr(c, 'use_limit_' + ax, True)
            setattr(c, 'min_' + ax, rad(lo))
            setattr(c, 'max_' + ax, rad(hi))
        c = tw.constraints.new('COPY_ROTATION')
        c.name = 'forearm_twist'
        c.target, c.subtarget = arm, 'hand_' + side
        c.target_space = c.owner_space = 'LOCAL'
        c.use_x = c.use_z = False
        c.euler_order = 'YXZ'
        c.influence = TWIST_SHARE
        solve_pole(arm, side)
    print('ik: both arms')


def rest_error(arm, side):
    bpy.context.view_layer.update()
    worst = 0.0
    for n in ('upper_arm_', 'forearm_', 'hand_'):
        b = arm.pose.bones[n + side]
        worst = max(worst, max(abs(a - c) for ra, rc in zip(b.matrix, arm.data.bones[n + side].matrix_local)
                               for a, c in zip(ra, rc)))
    return worst


def solve_pole(arm, side):
    """The pole angle that leaves the rest pose unchanged (controls at rest)."""
    c = arm.pose.bones['forearm_' + side].constraints['arm_ik']
    for pb in arm.pose.bones:
        pb.location = (0, 0, 0)
        pb.rotation_euler = (0, 0, 0)
    best = None
    for step in (10.0, 1.0, 0.1, 0.01):
        centre = 0.0 if best is None else best[1]
        span = 180.0 if best is None else step * 10
        a = centre - span
        while a <= centre + span + 1e-9:
            c.pole_angle = math.radians(a)
            e = rest_error(arm, side)
            if best is None or e < best[0]:
                best = (e, a)
            a += step
    c.pole_angle = math.radians(best[1])
    print('ik %s: pole angle %.2f deg, rest error %.2e' % (side, best[1], rest_error(arm, side)))


def run(*stages):
    for st in stages:
        globals()[st]()
