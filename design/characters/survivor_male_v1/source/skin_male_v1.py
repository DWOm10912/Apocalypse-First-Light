"""AFL default male survivor V1, M2 (2026-10-25): skin the eight V1 meshes to the frozen H1-P1 rig and check them in
test poses before any locomotion clip is made.

Run inside survivor_male_v1.blend:
    import skin_male_v1 as K; importlib.reload(K)
    K.weights()          # vertex groups + Armature modifiers on the eight meshes, the hidden-under-outfit mask
    rep = K.check()      # test poses A-H: numbers (renders/m2/m2_check.json) + renders/m2/*.png

The rig is read only: no bone is added, moved, renamed or re-parented, the rest pose and the actions are untouched.
Test poses are set on pose bones and cleared afterwards; fingerprint() hashes the bones and every action before and
after, and check() compares the bones with the H1-P1 source blend.

Weights: body and head come from body_h1p1_lib.skin_weights (the H1-P1 bone-group distance field the PASS H1-P1 body
used), so the neck ring the two meshes share gets the same weights on both. Hair is rigid on the head. Each garment
vertex takes the weights of the nearest body surface point of its own limb (barycentric on the body triangle), so the
cloth and the skin under it move together; the limb comes from the garment vertex's nearest bone group, which keeps
a jacket side from taking upper-arm weights across the armpit. Body faces wholly under the outfit go to the vertex
group m1_hidden_under_outfit and an inverted Mask modifier hides them: the body mesh itself stays complete, another
outfit gets its own group.
"""
import hashlib
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Matrix, Quaternion, Vector
from mathutils.bvhtree import BVHTree

SRC = r"D:/Minecraft Modding/Apocalypse First Light/design/characters/survivor_male_v1/source"
H1 = r"D:/Minecraft Modding/Apocalypse First Light/design/characters/survivor_base_h1p1/source"
H1_BLEND = H1 + "/survivor_base_h1p1.blend"
for p in (SRC, H1):
    if p not in sys.path:
        sys.path.insert(0, p)
import body_h1p1_lib as L     # noqa: E402

M = 0.001
STATE = bpy.app.driver_namespace.setdefault('afl_male_v1', {})
RIG = 'survivor_rig'
BODY, HEAD, HAIR = 'male_v1_body', 'male_v1_head', 'male_v1_hair'
CLOTHES = ('male_v1_tee', 'male_v1_jacket', 'male_v1_jeans', 'male_v1_belt', 'male_v1_boots')
NAMES = (BODY, HEAD, HAIR) + CLOTHES
HIDDEN = 'm1_hidden_under_outfit'
TEE = 'male_v1_tee'
MASKS = {BODY: HIDDEN, TEE: 'm1_hidden_under_jacket'}     # per layer: its faces wholly under the layers outside it
COVER_MM = 24.0         # a body vertex is under the outfit when every ray of a 35 deg cone meets a garment this close
CONE_DEG = 35.0
LAYER_MM = 30.0         # layer checks look this far for the outer surface
POKE_MM = 1.0           # a covered vertex that ends this far outside its cover pokes through
RENDERS = os.path.normpath(os.path.join(SRC, '..', 'renders', 'm2'))
FINGERS = ('thumb', 'index', 'middle', 'ring', 'pinky')


def rig():
    return bpy.data.objects[RIG]


def update():
    bpy.context.view_layer.update()


def sdf():
    if 'sdf' not in STATE:
        STATE['sdf'] = L.BodySDF(L.skeleton())
    return STATE['sdf']


def basis_mm(ob):
    return np.array([v.co[:] for v in ob.data.vertices], np.float64) / M


def tris_of(me):
    me.calc_loop_triangles()
    return np.array([t.vertices[:] for t in me.loop_triangles], np.int64)


# ---- weights

def limb(n):
    """Coarse region of a bone: torso (with the neck, head and clavicles), arm_<s> or leg_<s>."""
    if n.endswith(('_r', '_l')):
        b, s = n[:-2], n[-1]
        if b in ('thigh', 'shin', 'foot', 'toes'):
            return 'leg_' + s
        if b != 'clavicle':
            return 'arm_' + s
    return 'torso'


def nearest_group(P):
    """Nearest H1-P1 bone group per point (mm), the same field skin_weights starts from."""
    s = sdf()
    names = [b.name for b in s.bones if b.name != 'root' and not b.name.startswith('forearm_twist')]
    D = np.stack([s.group(n, P.astype(np.float32)) for n in names], 1)
    return [names[j] for j in D.argmin(1)]


def dense(rows, names):
    col = {n: j for j, n in enumerate(names)}
    W = np.zeros((len(rows), len(names)))
    for i, row in enumerate(rows):
        for n, w in row:
            W[i, col[n]] = w
    return W


def sparse(W, names, top=4):
    out = []
    for w in W:
        idx = np.argsort(-w)[:top]
        idx = [j for j in idx if w[j] > 1e-3]
        tot = sum(w[j] for j in idx)
        out.append([(names[j], float(w[j] / tot)) for j in idx])
    return out


def assign(ob, rows):
    ob.vertex_groups.clear()
    groups = {}
    for i, row in enumerate(rows):
        for n, w in row:
            g = groups.get(n)
            if g is None:
                g = groups[n] = ob.vertex_groups.new(name=n)
            g.add([i], w, 'REPLACE')


def armature(ob):
    for m in list(ob.modifiers):
        if m.type in ('ARMATURE', 'MASK'):
            ob.modifiers.remove(m)
    m = ob.modifiers.new('rig', 'ARMATURE')
    m.object = rig()
    m.use_vertex_groups = True
    m.use_bone_envelopes = False
    ob.parent = rig()
    ob.matrix_parent_inverse = Matrix.Identity(4)
    return m


def transfer(body_rows, body_P, body_T, P):
    """Garment points P (mm) take the barycentric weights of the nearest body point on a triangle of their limb."""
    names = sorted({n for row in body_rows for n, _ in row})
    W = dense(body_rows, names)
    dom = [limb(row[0][0]) for row in body_rows]
    trees = {}
    for c in sorted(set(dom)):
        sel = [k for k, t in enumerate(body_T) if c in (dom[t[0]], dom[t[1]], dom[t[2]])]
        trees[c] = (BVHTree.FromPolygons([Vector(p) for p in body_P], [tuple(body_T[k]) for k in sel]), sel)
    alltree = BVHTree.FromPolygons([Vector(p) for p in body_P], [tuple(t) for t in body_T])
    out = np.zeros((len(P), len(names)))
    far = 0
    for i, (p, g) in enumerate(zip(P, nearest_group(P))):
        tree, sel = trees.get(limb(g), (alltree, None))
        loc, _, k, dist = tree.find_nearest(Vector(p))
        if loc is None:
            loc, _, k, dist = alltree.find_nearest(Vector(p))
            sel = None
        far += dist > 60
        t = body_T[sel[k] if sel else k]
        a, b, c = (Vector(body_P[j]) for j in t)
        u = barycentric(loc, a, b, c)
        out[i] = u[0] * W[t[0]] + u[1] * W[t[1]] + u[2] * W[t[2]]
    return sparse(out, names), far


# A jacket hangs from the hips: its hem over the thighs keeps this share of the thigh weights, the rest goes to the
# hips (with the full thigh share the back hem was dragged down into a sack in the squat; with none the thigh cuts
# through the front hem when the leg comes up)
LEG_SHARE = {'male_v1_jacket': 0.35}


def hang(rows, share):
    out = []
    for row in rows:
        w = {}
        for n, x in row:
            if limb(n).startswith('leg_'):
                w[n] = w.get(n, 0.0) + x * share
                w['hips'] = w.get('hips', 0.0) + x * (1 - share)
            else:
                w[n] = w.get(n, 0.0) + x
        top = sorted(w.items(), key=lambda kv: -kv[1])[:4]
        tot = sum(x for _, x in top)
        out.append([(n, x / tot) for n, x in top])
    return out


def barycentric(p, a, b, c):
    v0, v1, v2 = b - a, c - a, p - a
    d00, d01, d11 = v0.dot(v0), v0.dot(v1), v1.dot(v1)
    d20, d21 = v2.dot(v0), v2.dot(v1)
    den = d00 * d11 - d01 * d01
    if abs(den) < 1e-12:
        return (1.0, 0.0, 0.0)
    v = (d11 * d20 - d01 * d21) / den
    w = (d00 * d21 - d01 * d20) / den
    u = 1 - v - w
    q = np.clip([u, v, w], 0, None)
    return q / q.sum()


def cone(n, deg=CONE_DEG):
    n = Vector(n).normalized()
    a = n.orthogonal().normalized()
    b = n.cross(a)
    t = math.tan(math.radians(deg))
    return [n] + [(n + t * (math.cos(k * math.pi / 2) * a + math.sin(k * math.pi / 2) * b)).normalized()
                  for k in range(4)]


def garment_tree(names=CLOTHES, posed=False):
    verts, polys = [], []
    for n in names:
        ob = bpy.data.objects[n]
        if posed:
            dg = bpy.context.evaluated_depsgraph_get()
            me = ob.evaluated_get(dg).to_mesh()
            P = [ob.matrix_world @ v.co for v in me.vertices]
            T = tris_of(me)
            ob.evaluated_get(dg).to_mesh_clear()
        else:
            P = [Vector(v.co) for v in ob.data.vertices]
            T = tris_of(ob.data)
        o = len(verts)
        verts += P
        polys += [tuple(int(j) + o for j in t) for t in T]
    return BVHTree.FromPolygons(verts, polys)


def covered(P, N, tree, reach=COVER_MM * M):
    """Per point (m): every ray of the cone along its normal meets the outfit within reach."""
    out = np.zeros(len(P), bool)
    for i, (p, n) in enumerate(zip(P, N)):
        ok = True
        for d in cone(n):
            hit = tree.ray_cast(Vector(p) + d * 0.0005, d, reach)
            if hit[0] is None:
                ok = False
                break
        out[i] = ok
    return out


def hidden_group(ob, outer=CLOTHES):
    """Vertex group of the vertices whose every face is under the outer layers (the faces on a visible edge keep
    their vertices, so the hidden region stops one ring inside the garment openings) and an inverted Mask modifier
    on it, first in the stack (the armature then deforms fewer vertices)."""
    me = ob.data
    P = [v.co.copy() for v in me.vertices]
    N = [v.normal.copy() for v in me.vertices]
    cov = covered(P, N, garment_tree(outer))
    hide_face = np.array([all(cov[j] for j in f.vertices) for f in me.polygons])
    vis_v = np.zeros(len(P), bool)
    for f, h in zip(me.polygons, hide_face):
        if not h:
            vis_v[list(f.vertices)] = True
    keep = [i for i in range(len(P)) if not vis_v[i]]
    name = MASKS[ob.name]
    g = ob.vertex_groups.get(name) or ob.vertex_groups.new(name=name)
    g.add(keep, 1.0, 'REPLACE')
    m = ob.modifiers.new('outfit_mask', 'MASK')
    m.vertex_group = name
    m.invert_vertex_group = True
    ob.modifiers.move(ob.modifiers.find('outfit_mask'), 0)
    return dict(group=name, hidden_verts=len(keep), verts=len(P),
                faces_removed=int(sum(1 for f in me.polygons if any(not vis_v[j] for j in f.vertices))),
                faces_total=len(me.polygons))


def weights():
    """Weights, Armature modifiers and the outfit mask on the eight V1 meshes. Returns counts per object."""
    s = sdf()
    rep = {}
    body = bpy.data.objects[BODY]
    bP = basis_mm(body)
    brows, _, _ = L.skin_weights(s, bP)
    assign(body, brows)
    armature(body)
    rep[BODY] = dict(verts=len(bP), groups=len(body.vertex_groups))
    head = bpy.data.objects[HEAD]
    hrows, _, _ = L.skin_weights(s, basis_mm(head))
    assign(head, hrows)
    armature(head)
    rep[HEAD] = dict(verts=len(hrows), groups=len(head.vertex_groups))
    # hair has no bones of its own: it takes the scalp under it (all head, the nape blends into the neck like the
    # skin there; rigid hair sank into the nape when the neck bent)
    hair = bpy.data.objects[HAIR]
    rows, _ = transfer(hrows, basis_mm(head), tris_of(head.data), basis_mm(hair))
    assign(hair, rows)
    armature(hair)
    rep[HAIR] = dict(verts=len(rows), groups=len(hair.vertex_groups),
                     not_all_head=sum(1 for r in rows if r[0][0] != 'head' or r[0][1] < 0.999))
    bT = tris_of(body.data)
    for n in CLOTHES:
        ob = bpy.data.objects[n]
        rows, far = transfer(brows, bP, bT, basis_mm(ob))
        if n in LEG_SHARE:
            rows = hang(rows, LEG_SHARE[n])
        assign(ob, rows)
        armature(ob)
        rep[n] = dict(verts=len(rows), groups=len(ob.vertex_groups), far_from_body=int(far))
    rep['mask'] = hidden_group(body)
    rep['mask_tee'] = hidden_group(bpy.data.objects[TEE], ('male_v1_jacket',))
    rep['mask_refine'] = refine_masks()
    rep['seam'] = neck_seam(brows, hrows, bP, basis_mm(head))
    STATE['weights'] = rep
    return rep


def neck_seam(brows, hrows, bP, hP):
    """Largest weight difference between body and head vertices at the same place (the neck ring)."""
    names = sorted({n for r in brows + hrows for n, _ in r})
    Wb, Wh = dense(brows, names), dense(hrows, names)
    worst, pairs = 0.0, 0
    for i, p in enumerate(hP):
        d = np.linalg.norm(bP - p, axis=1)
        j = int(d.argmin())
        if d[j] < 0.5:
            pairs += 1
            worst = max(worst, float(np.abs(Wb[j] - Wh[i]).sum()))
    return dict(shared_verts=pairs, max_weight_diff=round(worst, 4))


# ---- test poses (pose bones only; reset() clears them)

def pb():
    return rig().pose.bones


ARM_CONSTRAINTS = (('forearm_r', 'arm_ik'), ('forearm_l', 'arm_ik'), ('hand_r', 'hand_rot'), ('hand_l', 'hand_rot'))


def ik(on):
    for b, c in ARM_CONSTRAINTS:
        pb()[b].constraints[c].mute = not on


def reset():
    for b in pb():
        b.matrix_basis = Matrix.Identity(4)
    update()


def rot(name, q):
    """Rotate a pose bone by quaternion q (armature space) about its posed head; its children follow."""
    b = pb()[name]
    update()
    h = b.head.copy()
    b.matrix = Matrix.Translation(h) @ q.to_matrix().to_4x4() @ Matrix.Translation(-h) @ b.matrix
    update()


def turn(name, deg, axis):
    rot(name, Quaternion(Vector(axis).normalized(), math.radians(deg)))


def direction(name):
    b = pb()[name]
    update()
    return (b.tail - b.head).normalized()


def aim(name, d):
    """Point a bone (head -> tail) along d with the smallest turn."""
    rot(name, direction(name).rotation_difference(Vector(d).normalized()))


def tilt(name, toward, deg):
    """Turn a bone's direction deg toward a vector."""
    d = direction(name)
    ax = d.cross(Vector(toward))
    if ax.length > 1e-6:
        turn(name, deg, ax)


def sv(s, out, fwd, up):
    """Body vector from (outward, forward, up) for side s (right is -X, forward -Y)."""
    return Vector((-out if s == 'r' else out, -fwd, up))


def fingers(s, curl):
    """curl: (knuckle, middle, tip) deg for the four fingers and a thumb (x, y, z) triple, grip-file convention."""
    k, m, t, th = curl
    for b in pb():
        if not b.name.endswith('_' + s):
            continue
        part = b.name.split('_')[0]
        if part not in FINGERS:
            continue
        seg = int(b.name.split('_')[1])
        b.rotation_mode = 'XYZ'
        if part == 'thumb':
            x, y, z = th if seg == 1 else (th[0] * 1.6, 0, 0)
        else:
            x, y, z = ((k, m, t)[seg - 1], 0, 0)
        if s == 'l':
            y, z = -y, -z
        b.rotation_euler = tuple(math.radians(v) for v in (x, y, z))
    update()


RELAXED = (14, 18, 8, (6, -8, -8))
FIST = (88, 96, 58, (14, -38, -36))
OPEN = (0, 0, 0, (0, 0, 0))


def arms(up, fore, curl=RELAXED, sides='rl'):
    for s in sides:
        aim('upper_arm_' + s, sv(s, *up))
        aim('forearm_' + s, sv(s, *fore))
        fingers(s, curl)


def legs_flat(s, thigh, shin):
    """Sagittal hip flexion (thigh < 0 = forward) and knee flexion (shin > 0), the foot back to its rest angle."""
    turn('thigh_' + s, thigh, (1, 0, 0))
    turn('shin_' + s, shin, (1, 0, 0))
    turn('foot_' + s, -(thigh + shin), (1, 0, 0))


def ground(sides='rl'):
    """Move the hips so the given feet are back where they stand at rest (y and z)."""
    bones = rig().data.bones
    d = Vector()
    for s in sides:
        d += pb()['foot_' + s].head - bones['foot_' + s].head_local
    d /= len(sides)
    h = pb()['hips']
    h.matrix = Matrix.Translation((0, -d.y, -d.z)) @ h.matrix
    update()


def pose_a():
    """A standing: arms down by the sides, elbows soft, hands relaxed."""
    arms((0.17, 0.02, -1), (0.12, 0.17, -1))


def pose_b():
    """B both arms forward at shoulder height, shoulders a little protracted."""
    for s in 'rl':
        tilt('clavicle_' + s, (0, -1, 0), 10)
    arms((0.15, 1, 0.0), (0.08, 1, 0.02))


def pose_c():
    """C both arms raised: clavicles elevated 22 deg, arms about 160 deg up."""
    for s in 'rl':
        tilt('clavicle_' + s, (0, 0, 1), 22)
    arms((0.38, 0.05, 1), (0.30, 0.05, 1), OPEN)


def pose_d():
    """D elbows bent 125 deg; right forearm turned 80 deg with a fist, left wrist flexed 60 deg with an open hand."""
    arms((0.10, 0.12, -1), (0.05, 0.82, 0.57), OPEN)
    turn('hand_r', 80, direction('forearm_r'))
    fingers('r', FIST)
    b = pb()['hand_l']
    update()
    turn('hand_l', 60, b.matrix.to_3x3() @ Vector((1, 0, 0)))


def pose_e():
    """E squat: hips 100 deg, knees 130 deg, feet flat (ankles 30 deg dorsiflexed), knees out 15 deg, trunk forward,
    arms forward for balance."""
    for s in 'rl':
        legs_flat(s, -100, 130)
    ground()
    for s in 'rl':
        turn('thigh_' + s, 15 if s == 'r' else -15, (0, 0, 1))
    ground()
    turn('spine', 14, (1, 0, 0))
    turn('chest', 10, (1, 0, 0))
    turn('neck', -14, (1, 0, 0))
    arms((0.12, 1, -0.25), (0.05, 1, -0.10))


def pose_f():
    """F right leg raised: hip and knee 90 deg, foot pointed 20 deg; standing on the left."""
    legs_flat('r', -90, 90)
    turn('foot_r', 20, (1, 0, 0))
    arms((0.17, 0.02, -1), (0.12, 0.17, -1))


def pose_g():
    """G head turned 65 deg to the left (neck 25 + head 40) and the chin up 15 deg."""
    turn('neck', 25, (0, 0, 1))
    turn('head', 40, (0, 0, 1))
    face = Quaternion((0, 0, 1), math.radians(65)) @ Vector((0, -1, 0))
    turn('head', 15, face.cross(Vector((0, 0, 1))))
    arms((0.17, 0.02, -1), (0.12, 0.17, -1))


def fp_module():
    if 'F' not in STATE:
        F = {'__name__': 'fp_pose_h1p1'}
        exec(open(H1 + '/fp_pose_h1p1.py', encoding='utf-8').read(), F)
        STATE['F'] = F
    return STATE['F']


def pose_h():
    """H P9-01 two-hand hold: the third-person press-out (fp_pose_h1p1 'tp_ads', the arm IK on the shared grip)."""
    ik(True)
    return fp_module()['pose']('tp_ads')


def pose_rest():
    """0 the rest pose (A pose): the baseline the other counts compare with."""


POSES = [('0_rest', pose_rest), ('A_stand', pose_a), ('B_arms_forward', pose_b), ('C_arms_up', pose_c), ('D_elbows', pose_d),
         ('E_squat', pose_e), ('F_leg_raise', pose_f), ('G_head_turn', pose_g), ('H_p9_two_hand', pose_h)]


# ---- measurements

def masks(on):
    for n in MASKS:
        m = bpy.data.objects[n].modifiers.get('outfit_mask')
        if m:
            m.show_viewport = on


def evaluated(names, mask=False):
    """World vertex positions (m) per object with the rig pose; the outfit masks off unless asked."""
    masks(mask)
    update()
    dg = bpy.context.evaluated_depsgraph_get()
    out = {}
    for n in names:
        ob = bpy.data.objects[n]
        e = ob.evaluated_get(dg)
        me = e.to_mesh()
        out[n] = np.array([ob.matrix_world @ v.co for v in me.vertices])
        e.to_mesh_clear()
    masks(True)
    return out


def tri_areas(P, T):
    a, b, c = P[T[:, 0]], P[T[:, 1]], P[T[:, 2]]
    return 0.5 * np.linalg.norm(np.cross(b - a, c - a), axis=1)


def dominant(ob):
    out = [''] * len(ob.data.vertices)
    best = np.zeros(len(out))
    names = {g.index: g.name for g in ob.vertex_groups if g.name not in MASKS.values()}
    for v in ob.data.vertices:
        for g in v.groups:
            if g.group in names and g.weight > best[v.index]:
                best[v.index] = g.weight
                out[v.index] = names[g.group]
    return out


def visible(n):
    """Vertices of object n outside its outfit mask group (all of them for an object without one)."""
    ob = bpy.data.objects[n]
    g = ob.vertex_groups.get(MASKS.get(n, ''))
    hid = np.zeros(len(ob.data.vertices), bool)
    if g:
        for v in ob.data.vertices:
            for e in v.groups:
                if e.group == g.index:
                    hid[v.index] = True
    return ~hid


def stretch(rest, posed):
    """Triangle area ratio posed / rest per object: collapse (< 0.35) and stretch (> 2.0) counts, the extremes and
    the bones they sit on (masked layers count visible triangles only)."""
    out = {}
    for n in NAMES:
        ob = bpy.data.objects[n]
        T = tris_of(ob.data)
        if n in MASKS:
            T = T[visible(n)[T].all(1)]
        r = tri_areas(posed[n], T) / np.maximum(tri_areas(rest[n], T), 1e-12)
        dom = dominant(ob)
        lo, hi = int(r.argmin()), int(r.argmax())
        out[n] = dict(min=round(float(r.min()), 3), max=round(float(r.max()), 3), collapse=int((r < 0.35).sum()),
                      stretched=int((r > 2.0).sum()), min_at=dom[T[lo][0]], max_at=dom[T[hi][0]],
                      p1=round(float(np.percentile(r, 1)), 3), p99=round(float(np.percentile(r, 99)), 3))
    return out


JOINTS = [('shoulder_r', 'upper_arm_r', 'male_v1_jacket'), ('elbow_r', 'forearm_r', 'male_v1_jacket'),
          ('wrist_r', 'hand_r', BODY), ('elbow_l', 'forearm_l', 'male_v1_jacket'), ('wrist_l', 'hand_l', BODY),
          ('hip_r', 'thigh_r', 'male_v1_jeans'), ('knee_r', 'shin_r', 'male_v1_jeans'),
          ('ankle_r', 'foot_r', 'male_v1_boots'), ('knee_l', 'shin_l', 'male_v1_jeans'), ('neck', 'head', HEAD)]
JOINT_R = 0.065


def joint_volume(rest, posed):
    """Per joint: verts of the outer layer within 65 mm of the joint centre; their distance to the posed joint centre
    over the rest one (mean and 5th percentile). Below about 0.8 the joint is collapsing."""
    bones = rig().data.bones
    out = {}
    for name, bone, obn in JOINTS:
        c0 = np.array(bones[bone].head_local)
        c1 = np.array(pb()[bone].head)
        R, Q = rest[obn], posed[obn]
        d0 = np.linalg.norm(R - c0, axis=1)
        sel = d0 < JOINT_R
        if sel.sum() < 4:
            continue
        k = np.linalg.norm(Q[sel] - c1, axis=1) / np.maximum(d0[sel], 1e-4)
        out[name] = dict(mesh=obn.replace('male_v1_', ''), verts=int(sel.sum()), mean=round(float(k.mean()), 3),
                         p5=round(float(np.percentile(k, 5)), 3))
    return out


def tree_of(P, T):
    return BVHTree.FromPolygons([Vector(p) for p in P], [tuple(map(int, t)) for t in T])


def signed(tree, P, reach):
    """Signed distance along the nearest face normal (+ outside), NaN past reach."""
    out = np.full(len(P), np.nan)
    for i, p in enumerate(P):
        loc, nrm, _, dist = tree.find_nearest(Vector(p), reach)
        if loc is not None:
            out[i] = (Vector(p) - loc).dot(nrm)
    return out


LAYERS = [(BODY, 'male_v1_tee'), (BODY, 'male_v1_jacket'), (BODY, 'male_v1_jeans'), (BODY, 'male_v1_boots'),
          ('male_v1_tee', 'male_v1_jacket'), ('male_v1_jeans', 'male_v1_jacket'), ('male_v1_belt', 'male_v1_jacket'),
          ('male_v1_jeans', 'male_v1_boots'), ('male_v1_boots', 'male_v1_jeans'), ('male_v1_jeans', 'male_v1_belt'),
          ('male_v1_tee', 'male_v1_jeans'), (HAIR, 'male_v1_jacket'), (HEAD, 'male_v1_jacket')]


def normals(P, T):
    N = np.zeros_like(P)
    a, b, c = P[T[:, 0]], P[T[:, 1]], P[T[:, 2]]
    fn = np.cross(b - a, c - a)
    for k in range(3):
        np.add.at(N, T[:, k], fn)
    return N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-12)


def in_shell(tree, P, dirs=((0.13, 0.21, 0.97), (0.95, -0.17, 0.26), (-0.31, 0.93, -0.19))):
    """Inside the material of a closed shell (the garments are closed: outer surface, inner surface and rims): odd
    ray crossings, majority of three directions."""
    out = np.zeros(len(P), bool)
    D = [Vector(d).normalized() for d in dirs]
    for i, p in enumerate(P):
        odd = 0
        for d in D:
            o, n = Vector(p), 0
            for _ in range(32):
                hit = tree.ray_cast(o, d, 3.0)
                if hit[0] is None:
                    break
                n += 1
                o = hit[0] + d * 1e-5
            odd += n % 2
        out[i] = odd >= 2
    return out


def through(tree, P, N, reach=0.015):
    """Points outside the shell's outer surface: back along the normal the first face met faces the point."""
    out = np.full(len(P), np.nan)
    for i, (p, n) in enumerate(zip(P, N)):
        d = -Vector(n)
        hit = tree.ray_cast(Vector(p) - d * 1e-5, d, reach)
        if hit[0] is not None and hit[1].dot(d) < 0:
            out[i] = hit[3]
    return out


def under(tree, P, N, reach=LAYER_MM * M):
    """Points under a shell: along the normal the first face met is its inner surface (facing the point)."""
    out = np.zeros(len(P), bool)
    for i, (p, n) in enumerate(zip(P, N)):
        d = Vector(n)
        hit = tree.ray_cast(Vector(p) + d * 1e-5, d, reach)
        out[i] = hit[0] is not None and hit[1].dot(d) < 0
    return out


def layer_setup(rest):
    """At rest, per (inner, outer) pair: the inner vertices under the outer shell (they must stay under it) and the
    ones already in its material (left out of the posed counts)."""
    out = {}
    for a, b in LAYERS:
        Tb = tris_of(bpy.data.objects[b].data)
        tree = tree_of(rest[b], Tb)
        Na = normals(rest[a], tris_of(bpy.data.objects[a].data))
        ok = visible(a)
        cov = ok & under(tree, rest[a], Na)
        pre = in_shell(tree, rest[a])
        out[(a, b)] = (np.where(cov & ~pre)[0], np.where(ok & ~pre)[0], int(pre.sum()))
    return out


def layers(setup, posed):
    """Per pair: inner vertices now in the outer shell's material (in_fabric) and the covered ones now outside its
    outer surface (through, with the depth); the body counts visible vertices only."""
    out = {}
    for (a, b), (cov, free, pre) in setup.items():
        Tb = tris_of(bpy.data.objects[b].data)
        tree = tree_of(posed[b], Tb)
        dom = dominant(bpy.data.objects[a])
        Na = normals(posed[a], tris_of(bpy.data.objects[a].data))
        res = {}
        inside = free[in_shell(tree, posed[a][free])] if len(free) else free
        if len(cov):
            t = through(tree, posed[a][cov], Na[cov])
            thr = cov[np.isfinite(t)]
            if len(thr):
                res['through'] = dict(n=int(len(thr)), of=int(len(cov)), worst_mm=round(float(np.nanmax(t)) / M, 1),
                                      at=tally(dom, thr))
        if len(inside):
            res['in_fabric'] = dict(n=int(len(inside)), at=tally(dom, inside))
        if res:
            res['rest_in_fabric'] = pre
            out['%s>%s' % (a.replace('male_v1_', ''), b.replace('male_v1_', ''))] = res
    return out


def tally(dom, idx, top=3):
    where = {}
    for i in idx:
        where[dom[i]] = where.get(dom[i], 0) + 1
    return dict(sorted(where.items(), key=lambda kv: -kv[1])[:top])


def fib_hemisphere(n, k=14):
    """k directions spread over the hemisphere around n."""
    n = Vector(n).normalized()
    a = n.orthogonal().normalized()
    b = n.cross(a)
    out = []
    for i in range(k):
        z = 1 - (i + 0.5) / k
        r = math.sqrt(max(0.0, 1 - z * z))
        t = i * 2.39996
        out.append((z * n + r * math.cos(t) * a + r * math.sin(t) * b).normalized())
    return out


def visible_tree():
    """Everything that draws in the posed frame (outfit masks on) as one BVH."""
    masks(True)
    update()
    dg = bpy.context.evaluated_depsgraph_get()
    verts, polys = [], []
    for n in NAMES:
        ob = bpy.data.objects[n]
        e = ob.evaluated_get(dg)
        me = e.to_mesh()
        T = tris_of(me)
        o = len(verts)
        verts += [ob.matrix_world @ v.co for v in me.vertices]
        polys += [tuple(int(j) + o for j in t) for t in T]
        e.to_mesh_clear()
    return BVHTree.FromPolygons(verts, polys)


def holes(posed):
    """Masked-away vertices that can be seen from outside in this pose: there a hole in the masked layer shows."""
    tree = visible_tree()
    out = {}
    for n in MASKS:
        seen = hole_verts(n, posed[n], tree)
        out[n.replace('male_v1_', '')] = dict(n=len(seen), of=int((~visible(n)).sum()),
                                              at=tally(dominant(bpy.data.objects[n]), seen, 4))
    return out


def refine_masks(rounds=4):
    """Take back into view every masked vertex that can be seen in a test pose (into the sleeve cuffs, past the
    collar) plus its ring of neighbours, until no pose shows a hole. Returns the vertices freed per round."""
    ob = rig()
    act = ob.animation_data.action if ob.animation_data else None
    pose_position = ob.data.pose_position
    log = []
    try:
        if ob.animation_data:
            ob.animation_data.action = None
        ob.data.pose_position = 'POSE'
        for _ in range(rounds):
            seen = {n: set() for n in MASKS}
            for name, fn in POSES:
                reset()
                ik(name.startswith('H_'))
                fn()
                posed = evaluated(MASKS)
                tree = visible_tree()
                for n in MASKS:
                    seen[n] |= set(hole_verts(n, posed[n], tree))
            freed = {}
            for n, vs in seen.items():
                if vs:
                    freed[n.replace('male_v1_', '')] = unmask(n, vs)
            log.append(freed)
            if not freed:
                break
    finally:
        reset()
        ik(True)
        ob.data.pose_position = pose_position
        if ob.animation_data:
            ob.animation_data.action = act
        update()
    return log


def unmask(n, vs):
    """Remove vertices vs and their neighbours from object n's mask group."""
    ob = bpy.data.objects[n]
    me = ob.data
    ring = set(vs)
    for f in me.polygons:
        if any(j in vs for j in f.vertices):
            ring |= set(f.vertices)
    ob.vertex_groups[MASKS[n]].remove(list(ring))
    return len(ring)


def hole_verts(n, P, tree):
    """Masked-away vertices of object n (posed positions P) that a ray of their normal hemisphere sees from outside
    the character (tree: everything that draws)."""
    hid = np.where(~visible(n))[0]
    N = normals(P, tris_of(bpy.data.objects[n].data))
    out = []
    for i in hid:
        p = Vector(P[i]) + Vector(N[i]) * 0.001
        if any(tree.ray_cast(p, d, 3.0)[0] is None for d in fib_hemisphere(N[i])):
            out.append(int(i))
    return out


def hair_follow(rest, posed):
    """Hair against its head transform (rigid hair: 0), the change of its gap to the head mesh, and hair vertices
    inside the head (closed) beyond the ones there at rest."""
    bones = rig().data.bones
    Mh = pb()['head'].matrix @ bones['head'].matrix_local.inverted()
    R = rest[HAIR]
    rigid = np.array([Mh @ Vector(p) for p in R])
    dev = float(np.linalg.norm(rigid - posed[HAIR], axis=1).max())
    Th = tris_of(bpy.data.objects[HEAD].data)
    t0 = tree_of(rest[HEAD], Th)
    d0 = np.array([t0.find_nearest(Vector(p))[3] for p in R])
    t1 = tree_of(posed[HEAD], Th)
    d1 = np.array([t1.find_nearest(Vector(p))[3] for p in posed[HAIR]])
    sunk = int((in_shell(t1, posed[HAIR]) & ~in_shell(t0, R)).sum())
    return dict(rigid_dev_mm=round(dev / M, 2), gap_change_max_mm=round(float(np.abs(d1 - d0).max()) / M, 2),
                into_head=sunk)


def gun_tree():
    gun = bpy.data.objects.get('fp_gun')
    if gun is None:
        return None
    verts, polys = [], []
    for ob in bpy.data.collections['P9_ref'].all_objects:
        if ob.type != 'MESH':
            continue
        W = gun.matrix_world @ ob.matrix_world
        T = tris_of(ob.data)
        o = len(verts)
        verts += [W @ v.co for v in ob.data.vertices]
        polys += [tuple(int(j) + o for j in t) for t in T]
    return BVHTree.FromPolygons(verts, polys)


def gun_contact(posed):
    """Hand verts against the P9 surface: inside by more than 1.5 mm (penetration) and within 3 mm (contact)."""
    tree = gun_tree()
    dom = dominant(bpy.data.objects[BODY])
    P = posed[BODY]
    out = {}
    for s in 'rl':
        idx = [i for i, n in enumerate(dom) if n.endswith('_' + s) and (n.split('_')[0] in FINGERS or
                                                                         n.startswith('hand'))]
        sd = signed(tree, P[idx], 0.05)
        out['hand_' + s] = dict(verts=len(idx), contact=int(np.sum(np.abs(np.nan_to_num(sd, nan=1)) < 0.003)),
                                inside=int(np.sum(np.nan_to_num(sd, nan=1) < -0.0015)),
                                deepest_mm=round(float(-np.nanmin(sd)) / M, 1) if np.isfinite(sd).any() else None)
    return out


# ---- the rig is not changed: fingerprints

def fingerprint():
    arm = rig().data
    h = hashlib.sha1()
    for b in arm.bones:
        h.update(b.name.encode())
        h.update((b.parent.name if b.parent else '-').encode())
        h.update(np.round(np.array(b.matrix_local), 6).tobytes())
        h.update(np.round([b.length], 6).tobytes())
        h.update(bytes([b.use_deform]))
    bones = h.hexdigest()
    acts = {}
    for a in bpy.data.actions:
        ha = hashlib.sha1()
        curves = [fc for ly in a.layers for st in ly.strips for cb in st.channelbags for fc in cb.fcurves] \
            if hasattr(a, 'layers') else list(a.fcurves)          # Blender 5 layered actions
        for fc in curves:
            ha.update(('%s[%d]' % (fc.data_path, fc.array_index)).encode())
            ha.update(np.round(np.array([k.co[:] for k in fc.keyframe_points]), 6).tobytes())
        acts[a.name] = ha.hexdigest()[:12]
    return dict(bones=bones, n_bones=len(arm.bones), deform=sum(b.use_deform for b in arm.bones), actions=acts)


def against_h1p1():
    """Bone names, parents, rest matrices and lengths against the H1-P1 source blend (read only)."""
    with bpy.data.libraries.load(H1_BLEND, link=False) as (src, dst):
        dst.armatures = [n for n in src.armatures if n == rig().data.name] or src.armatures[:1]
    ref = dst.armatures[0]
    mine = rig().data
    diff = []
    for b in ref.bones:
        o = mine.bones.get(b.name)
        if o is None:
            diff.append((b.name, 'missing'))
            continue
        if (o.parent.name if o.parent else None) != (b.parent.name if b.parent else None):
            diff.append((b.name, 'parent'))
        e = float(np.abs(np.array(o.matrix_local) - np.array(b.matrix_local)).max())
        if e > 1e-5 or abs(o.length - b.length) > 1e-6:
            diff.append((b.name, round(e, 7)))
    extra = [b.name for b in mine.bones if b.name not in ref.bones]
    n = len(ref.bones)
    bpy.data.armatures.remove(ref)
    return dict(ref_bones=n, differences=diff, extra=extra)


# ---- renders

def render(prefix, views, focus=(0, 0, 0.92), span=2.05, size=420, extra=()):
    import render_views as R
    sc = bpy.context.scene
    sc.display.shading.background_color = (0.30, 0.31, 0.32)
    sc.display.shading.show_specular_highlight = False
    return R.render(list(NAMES) + list(extra), views, RENDERS, prefix, size=size, focus=focus, span=span)


def compose(panels, out, cols, cw, ch, gap=6):
    """Contact sheet with Blender's image API: panels = image paths, each fitted into a cw x ch cell."""
    rows = (len(panels) + cols - 1) // cols
    W, H = cols * cw + (cols + 1) * gap, rows * ch + (rows + 1) * gap
    sheet = np.zeros((H, W, 4), np.float32)
    sheet[..., :3] = 0.08
    sheet[..., 3] = 1
    for i, path in enumerate(panels):
        im = bpy.data.images.load(path, check_existing=False)
        if tuple(im.size) != (cw, ch):
            im.scale(cw, ch)
        w, h = im.size
        a = np.array(im.pixels[:], np.float32).reshape(h, w, 4)[::-1]
        bpy.data.images.remove(im)
        r, c = divmod(i, cols)
        x, y = gap + c * (cw + gap), gap + r * (ch + gap)
        sheet[y:y + h, x:x + w] = a
    im = bpy.data.images.new('_m2_sheet', W, H, alpha=True)
    im.pixels[:] = sheet[::-1].ravel()
    im.filepath_raw = out
    im.file_format = 'PNG'
    im.save()
    bpy.data.images.remove(im)
    return out


VIEW_Q = ('q45', (-0.72, -0.69, 0.10), (0, 0, 1))
CLOSE = {'C_arms_up': ('front', (0, 0, 1.45), 0.75), 'D_elbows': (VIEW_Q, (0, -0.1, 1.25), 0.75),
         'E_squat': ('right', (0, -0.15, 0.45), 1.0), 'F_leg_raise': (VIEW_Q, (-0.1, -0.15, 0.75), 0.9),
         'G_head_turn': (VIEW_Q, (0, 0, 1.55), 0.5), 'H_p9_two_hand': ('right', (0, -0.35, 1.35), 0.75)}


# ---- the check

def check(render_images=True):
    ob = rig()
    before = fingerprint()
    act = ob.animation_data.action if ob.animation_data else None
    pose_position = ob.data.pose_position
    gun = bpy.data.objects.get('fp_gun')
    gun_matrix = gun.matrix_world.copy() if gun else None
    gun_hidden = gun.hide_render if gun else None
    rep = dict(rig=against_h1p1())
    try:
        if ob.animation_data:
            ob.animation_data.action = None
        ob.data.pose_position = 'REST'
        reset()
        rest = evaluated(NAMES)
        setup = layer_setup(rest)
        ob.data.pose_position = 'POSE'
        sheet, close = [], []
        for name, fn in POSES:
            reset()
            ik(name.startswith('H_'))
            info = fn()
            posed = evaluated(NAMES)
            r = dict(stretch=stretch(rest, posed), joints=joint_volume(rest, posed), layers=layers(setup, posed),
                     holes=holes(posed), hair=hair_follow(rest, posed))
            if name.startswith('H_'):
                r['gun'] = gun_contact(posed)
                r['ik'] = {s: info[s] for s in 'rl'}
            rep[name] = r
            if render_images:
                extra = ('fp_gun',) if name.startswith('H_') else ()
                if gun:
                    gun.hide_render = not extra
                fr = render('m2_' + name, ['front', VIEW_Q], extra=extra)
                sheet += fr
                if name in CLOSE:
                    v, f, sp = CLOSE[name]
                    close += render('m2_' + name + '_close', [v], focus=f, span=sp, extra=extra)
        if render_images:
            rep['sheets'] = [compose(sheet, os.path.join(RENDERS, 'm2_poses_sheet.png'), 6, 320, 320),
                             compose(close, os.path.join(RENDERS, 'm2_joints_sheet.png'), 3, 400, 400)]
    finally:
        reset()
        ik(True)
        ob.data.pose_position = pose_position
        if ob.animation_data:
            ob.animation_data.action = act
        if gun:
            gun.matrix_world = gun_matrix
            gun.hide_render = gun_hidden
        update()
    after = fingerprint()
    rep['fingerprint'] = dict(unchanged=before == after, bones=before['n_bones'], deform=before['deform'],
                              actions=len(before['actions']))
    rep['counts'] = counts()
    os.makedirs(RENDERS, exist_ok=True)
    with open(os.path.join(RENDERS, 'm2_check.json'), 'w', encoding='utf-8') as f:
        json.dump(rep, f, indent=1, ensure_ascii=False, default=str)
    return rep


def counts():
    """Triangles, vertices, influences, materials and the export vertex slots (unique position + uv) per object."""
    out, tot = {}, dict(tris=0, verts=0, slots=0, visible_tris=0)
    for n in NAMES:
        ob = bpy.data.objects[n]
        me = ob.data
        T = tris_of(me)
        inf = [sum(1 for g in v.groups if ob.vertex_groups[g.group].name not in MASKS.values() and g.weight > 0)
               for v in me.vertices]
        uv = me.uv_layers.active
        slots = len({(l.vertex_index, round(uv.data[l.index].uv[0], 5), round(uv.data[l.index].uv[1], 5))
                     for l in me.loops}) if uv else len(me.vertices)
        vt = int(visible(n)[T].all(1).sum())
        out[n] = dict(tris=len(T), visible_tris=vt, verts=len(me.vertices), slots=slots, max_influences=max(inf),
                      mean_influences=round(float(np.mean(inf)), 2), material=me.materials[0].name if me.materials else None)
        for k in tot:
            tot[k] += out[n][k]
    out['total'] = tot
    out['materials'] = len({o['material'] for k, o in out.items() if k != 'total' and isinstance(o, dict)
                            and 'material' in o})
    return out
