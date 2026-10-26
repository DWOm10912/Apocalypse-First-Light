"""Male V1 face geometry (world mm, basis pose, front = -Y): real eye openings and a mouth slit on the head, plus a
separate 'male_v1_face' part (eyeballs, mouth bag, teeth). The 54-bone rig is frozen (no face bones), so the face
moves by shape keys: blink_l, blink_r, mouth_open (jaw about a hinge below the ears), mouth_pucker (sipping).
Chewing = mouth_open driven 0 .. 0.35 in a loop; drinking = mouth_pucker + a little mouth_open.

Head patches (per eye): the loft quads rings 8-10 x columns 0-52 are replaced by
    boundary (14) -> crease / under-eye ring (14) -> lid edge (14) -> inner lid wall touching the eyeball (14).
Mouth: the quads rings 3-4 x columns -32..32 become
    boundary (18) -> lip ring (18, the lips' fullest line) -> slit (16, shared corners) -> inner lips (16).
Round 3 (user notes 2026-10-24): smaller eyes under a lower upper lid in deeper sockets, a clearer slit with full lips,
corners set in a little, neutral.
Inner walls get their own UV strip at the bottom of the head texture (uv_inner), so they never share texels with
the skin in front of them.
"""
import math

import bmesh
import bpy
import numpy as np
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree
from mathutils.kdtree import KDTree

import head_male_v1 as H

M = 0.001

EYE_X, EYE_Z, EYE_HALF = 29.0, 1660.0, 11.0
EYE_UP, EYE_DOWN = 4.3, 3.1         # lid edge heights above / below EYE_Z at the middle (mm)
BALL = (13.0, 11.0, 11.0)          # eyeball half axes (mm), a slightly wide stylised ball
BALL_YAW = 18.0                    # deg, turned outward so the ball follows the face plane
MOUTH_Z = 1594.0
HINGE = np.array([0.0, 8.0, 1635.0])
JAW_OPEN = 0.17                    # rad at mouth_open = 1 (about 18 mm at the lips)
STRIP_V = (0.004, 0.066)           # head texture: inner walls; the skin starts at V_SKIN
V_SKIN = 0.075
STRIP_U = {1: (0.02, 0.30), 2: (0.36, 0.64), 3: (0.70, 0.98)}   # inner: 1 eye +x, 2 eye -x, 3 lips

# eye patch: rings 8 (under the eye), 9 (eye), 10 (brow); columns 0..52. Boundary order: along ring 8 outwards,
# up the outer column, back along ring 10, down column 0. Lid edge parameter t: -1 inner corner .. 1 outer corner.
EYE_RINGS = (8, 9, 10)
EYE_COLS = (0, 7, 15, 24, 32, 52)
EYE_LOOP = [(8, c) for c in EYE_COLS] + [(9, 52)] + [(10, c) for c in reversed(EYE_COLS)] + [(9, 0)]
EDGE_T = [-0.82, -0.5, -0.15, 0.2, 0.52, 0.82]
EYE_T = [(t, 'b') for t in EDGE_T] + [(1.0, 'c')] + [(t, 't') for t in reversed(EDGE_T)] + [(-1.0, 'c')]
MOUTH_COLS = (-32, -24, -15, -7, 0, 7, 15, 24, 32)
MOUTH_LOOP = [(3, c) for c in MOUTH_COLS] + [(4, c) for c in reversed(MOUTH_COLS)]
CORNER_X = 19.5
SLIT_IN = [float(x) for x in np.linspace(-CORNER_X, CORNER_X, 9)[1:-1]]
SLIT_X = [-CORNER_X] + SLIT_IN + [CORNER_X] + SLIT_IN[::-1]        # corner, lower x7, corner, upper x7

# vertex roles (attribute 'role')
R_OPEN = {'t': 1, 'b': 2, 'c': 3}
R_MID = {'t': 4, 'b': 5, 'c': 6}
R_WALL = {'t': 7, 'b': 8, 'c': 9}
R_SLIT_UP, R_SLIT_LOW, R_SLIT_C, R_IN_UP, R_IN_LOW, R_IN_C = 20, 21, 22, 23, 24, 25
R_LIP_UP, R_LIP_LOW = 26, 27


def eye_top(t):
    """Upper lid edge: low and heavy, highest a little inside the middle."""
    return EYE_Z + EYE_UP * np.sqrt(np.clip(1 - t * t, 0, 1)) * (1 - 0.12 * t) + 0.6 * t


def eye_bot(t):
    return EYE_Z - EYE_DOWN * np.sqrt(np.clip(1 - t * t, 0, 1)) + 0.4 * t


def mouth_line(x):
    return MOUTH_Z - 0.0008 * x * x


def smooth(x, a, b):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def rot_z(deg):
    return Matrix.Rotation(math.radians(deg), 3, 'Z')


# ---------------------------------------------------------------- build

class Face:
    """Builds the head patches in a bmesh; keeps the eyeball frames for the face part and the painter."""

    def __init__(self, bm):
        self.bm = bm
        bm.verts.ensure_lookup_table()
        self.bvh = BVHTree.FromBMesh(bm)
        self.L = {k: bm.verts.layers.int.new(k) for k in ('role', 'ri', 'side', 'gring', 'gcol', 'dep')}
        self.inner = bm.faces.layers.int.new('inner')
        for v in bm.verts:
            for k in self.L:
                v[self.L[k]] = -1
        kd = KDTree(len(bm.verts))
        for v in bm.verts:
            kd.insert(v.co, v.index)
        kd.balance()
        self.grid = {}
        for i, row in enumerate(H.grid()[:-1]):
            for a, p in zip(H.angles(), row):
                co, idx, d = kd.find(Vector(p) * M)
                if d < 1e-6:
                    v = bm.verts[idx]
                    self.grid[(i, a)] = v
                    v[self.L['gring']], v[self.L['gcol']] = i, a
        self.balls = {}

    def surf_y(self, x, z):
        """Front surface depth (mm) of the uncut head at (x, z)."""
        hit = self.bvh.ray_cast(Vector((x * M, -0.3, z * M)), Vector((0, 1, 0)), 1.0)
        return hit[0].y / M

    def vert(self, p, role, ri=-1, side=0, dep=-1):
        v = self.bm.verts.new(Vector(p) * M)
        for k in self.L:
            v[self.L[k]] = -1
        v[self.L['role']], v[self.L['ri']], v[self.L['side']], v[self.L['dep']] = role, ri, side, dep
        return v

    def quad_face(self, ring, a, b):
        vs = {self.grid[(ring, a)], self.grid[(ring, b)], self.grid[(ring + 1, a)], self.grid[(ring + 1, b)]}
        for f in self.grid[(ring, a)].link_faces:
            if set(f.verts) == vs:
                return f
        raise RuntimeError('no loft quad at ring %d cols %d..%d' % (ring, a, b))

    def bridge(self, A, B, inner=0):
        out = []
        n = len(A)
        for i in range(n):
            j = (i + 1) % n
            vs = [A[i], A[j], B[j], B[i]]
            if B[i] is B[j]:
                vs = [A[i], A[j], B[i]]
            out.append(self.bm.faces.new(vs))
            out[-1][self.inner] = inner
        return out

    def orient(self, faces, outer):
        """Flip the whole patch if its outer ring faces point into the head (they face -Y)."""
        for f in outer:
            f.normal_update()
        if sum(f.normal.y for f in outer) > 0:
            for f in faces:
                f.normal_flip()

    def cut(self):
        bm = self.bm
        dead, interior = [], []
        for s in (1, -1):
            for ring in EYE_RINGS[:2]:
                for a, b in zip(EYE_COLS, EYE_COLS[1:]):
                    dead.append(self.quad_face(ring, a * s if a else 0, b * s))
            interior += [self.grid[(EYE_RINGS[1], c * s)] for c in EYE_COLS[1:-1]]
        for a, b in zip(MOUTH_COLS, MOUTH_COLS[1:]):
            dead.append(self.quad_face(3, a, b))
        bmesh.ops.delete(bm, geom=list(set(dead)), context='FACES_ONLY')
        bmesh.ops.delete(bm, geom=interior, context='VERTS')
        # the loft edges that crossed the mouth (the inner columns between rings 3 and 4) are loose now
        # (the column 0 edges between the eyes are loose too, but the eye patches use them again)
        loose = [e for e in bm.edges if e.is_wire and max(v.co.z for v in e.verts) < 1610 * M]
        bmesh.ops.delete(bm, geom=loose, context='EDGES')

    def eye(self, s):
        side = 1 if s > 0 else 2
        ex = EYE_X * s
        B = [self.grid[(r, c * s if c else 0)] for r, c in EYE_LOOP]
        O2, M2 = [], []
        for (t, e), bv in zip(EYE_T, B):
            x = ex + s * t * EYE_HALF
            z = {'t': eye_top(t), 'b': eye_bot(t), 'c': eye_bot(t) + 0.5 * (eye_top(t) - eye_bot(t))}[e]
            O2.append((x, z, e))
            b = np.array(bv.co) / M
            f = 0.3 if e == 't' else 0.4
            mx, mz = x + f * (b[0] - x), z + f * (b[2] - z)
            M2.append((mx, mz, e))
        # sockets: the lid edge set back 2 mm, the crease under the brow 3.2 mm
        O = [np.array((x, self.surf_y(x, z) + 2.0, z)) for x, z, e in O2]
        Mid = [np.array((x, self.surf_y(x, z) + {'t': 3.2, 'b': 0.6, 'c': 1.4}[e], z)) for x, z, e in M2]
        # eyeball: a yawed ellipsoid just behind the lid edge (every edge point at least 8% outside it)
        R = rot_z(BALL_YAW * s)
        c = np.array((ex, self.surf_y(ex, EYE_Z) + 4.0 + 11.2, EYE_Z - 0.5))
        rad = np.array(BALL)

        def q(p, c):
            return np.linalg.norm(np.array(R.transposed() @ Vector(p - c)) / rad)
        while min(q(p, c) for p in O) < 1.08:
            c[1] += 0.25
        W = [c + (p - c) / q(p, c) * 1.03 for p in O]
        self.balls[s] = (c, R)
        mid = [self.vert(p, R_MID[e], i, side) for i, (p, (_, _, e)) in enumerate(zip(Mid, M2))]
        opn = [self.vert(p, R_OPEN[e], i, side, 0) for i, (p, (_, _, e)) in enumerate(zip(O, O2))]
        wal = [self.vert(p, R_WALL[e], i, side, 1) for i, (p, (_, _, e)) in enumerate(zip(W, O2))]
        f1 = self.bridge(B, mid)
        f2 = self.bridge(mid, opn)
        f3 = self.bridge(opn, wal, inner=side)
        self.orient(f1 + f2 + f3, f1)

    def mouth(self):
        B = [self.grid[k] for k in MOUTH_LOOP]
        n = len(MOUTH_COLS)
        S, I = [], []
        for i, x in enumerate(SLIT_X):
            z = mouth_line(x)
            corner = i in (0, n - 1)
            upper = i > n - 1
            y = self.surf_y(x, z) + (0.4 if corner else -0.8)          # corners set in a little, the slit a crease
            role = R_SLIT_C if corner else (R_SLIT_UP if upper else R_SLIT_LOW)
            S.append(self.vert((x, y, z), role, i, 0, 0))
            if corner:
                ip = (x * 0.9, y + 4.0, z)
                irole = R_IN_C
            else:
                ip = (x * 0.9, y + 6.0, z + (1.2 if upper else -1.2))
                irole = R_IN_UP if upper else R_IN_LOW
            I.append(self.vert(ip, irole, i, 0, 1))
        # boundary (18) -> slit (16): corners are shared by the lower and the upper lip
        sl = S[:n] + [S[n - 1]] + S[n:] + [S[0]]
        # lip ring halfway: the fullest line of each lip (the lower lip fuller)
        Lp = []
        for bv, sv in zip(B, sl):
            b, q = np.array(bv.co) / M, np.array(sv.co) / M
            x, z = b[0] + 0.5 * (q[0] - b[0]), b[2] + 0.5 * (q[2] - b[2])
            upper = bv[self.L['gring']] == 4
            Lp.append(self.vert((x, self.surf_y(x, z) - (0.9 if upper else 1.4), z),
                                R_LIP_UP if upper else R_LIP_LOW, -1, 0))
        f1 = self.bridge(B, Lp)
        f2 = []
        for i in range(len(B)):
            j = (i + 1) % len(B)
            vs = [Lp[i], Lp[j], sl[j], sl[i]]
            if sl[i] is sl[j]:
                vs = [Lp[i], Lp[j], sl[i]]
            f2.append(self.bm.faces.new(vs))
            f2[-1][self.inner] = 0
        f3 = self.bridge(S, I, inner=3)
        self.orient(f1 + f2 + f3, f1)
        self.inner_lips = [np.array(v.co) / M for v in I]


def stage_face():
    """Cut the head (fresh from build_survivor_male_v1.head) and build the face part. Returns the face object."""
    ob = bpy.data.objects['male_v1_head']
    if ob.data.shape_keys:
        raise RuntimeError('rebuild the head first (it has shape keys)')
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    F = Face(bm)
    F.cut()
    for s in (1, -1):
        F.eye(s)
    F.mouth()
    bm.normal_update()
    bm.to_mesh(ob.data)
    bm.free()
    ob.data.update()
    face = build_part(F)
    print('head tris', sum(len(p.vertices) - 2 for p in ob.data.polygons),
          'face tris', sum(len(p.vertices) - 2 for p in face.data.polygons))
    return face


# ---------------------------------------------------------------- the face part: eyeballs, mouth bag, teeth

def build_part(F):
    bm = bmesh.new()
    part = bm.verts.layers.int.new('part')        # 1 eyeball, 2 mouth bag, 3 upper teeth, 4 lower teeth
    balls = {}
    for s, (c, R) in F.balls.items():
        geo = bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=8, radius=1.0)
        T = (Matrix.Translation(Vector(c) * M) @ R.to_4x4()
             @ Matrix.Diagonal((BALL[0] * M, BALL[1] * M, BALL[2] * M, 1)) @ Matrix.Rotation(math.radians(90), 4, 'X'))
        bmesh.ops.transform(bm, matrix=T, verts=geo['verts'])
        for v in geo['verts']:
            v[part] = 1
        balls[s] = c
    # mouth bag: rings behind the inner lips, open to the front, faces turned inwards
    il = np.array(F.inner_lips)
    order = np.argsort(il[:, 0])
    ix, iy = il[order, 0], il[order, 1]
    rings = [(0.8, 23.0, 7.5), (10.0, 21.0, 13.0), (22.0, 15.0, 10.0)]
    N = 12
    vr = []
    for dy, rx, rz in rings:
        row = []
        for k in range(N):
            a = 2 * math.pi * k / N
            x, z = rx * math.cos(a), MOUTH_Z + rz * math.sin(a)
            y = float(np.interp(x * 0.9, ix, iy)) + dy
            v = bm.verts.new(Vector((x, y, z)) * M)
            v[part] = 2
            row.append(v)
        vr.append(row)
    cap = bm.verts.new(Vector((0, float(np.interp(0, ix, iy)) + 26.0, MOUTH_Z)) * M)
    cap[part] = 2
    bag = []
    for A, Bv in zip(vr, vr[1:]):
        for k in range(N):
            bag.append(bm.faces.new((A[k], A[(k + 1) % N], Bv[(k + 1) % N], Bv[k])))
    for k in range(N):
        bag.append(bm.faces.new((vr[-1][k], vr[-1][(k + 1) % N], cap)))
    centre = Vector((0, (float(np.interp(0, ix, iy)) + 12.0), MOUTH_Z)) * M
    for f in bag:
        f.normal_update()
        if f.normal.dot(centre - f.calc_center_median()) < 0:
            f.normal_flip()
    # teeth: one strip each, just behind the inner lips (hidden while the mouth is closed)
    xs = np.linspace(-16, 16, 7)
    for tag, z0, z1 in ((3, 1593.6, 1600.5), (4, 1586.5, 1593.2)):
        row0 = [bm.verts.new(Vector((x, float(np.interp(x, ix, iy)) + 1.5 + 0.02 * x * x, z0)) * M) for x in xs]
        row1 = [bm.verts.new(Vector((x, float(np.interp(x, ix, iy)) + 1.5 + 0.02 * x * x, z1)) * M) for x in xs]
        for v in row0 + row1:
            v[part] = tag
        for k in range(len(xs) - 1):
            f = bm.faces.new((row0[k], row0[k + 1], row1[k + 1], row1[k]))
            f.normal_update()
            if f.normal.y > 0:
                f.normal_flip()
    me = bpy.data.meshes.new('male_v1_face')
    bm.to_mesh(me)
    bm.free()
    from dress_male_v1 import replace_object
    ob = replace_object('male_v1_face', me)
    ob['ball_centres'] = [list(balls[1]), list(balls[-1])]
    return ob


# ---------------------------------------------------------------- UVs of the inner walls

def uv_inner(ob):
    """Inner lid walls and inner lips: a strip at the bottom of the head texture (u along the ring, v = depth)."""
    me = ob.data
    uvl = me.uv_layers.active.data
    inner = me.attributes['inner'].data
    ri = me.attributes['ri'].data
    dep = me.attributes['dep'].data
    for p in me.polygons:
        k = inner[p.index].value
        if k <= 0:
            continue
        n = len(EYE_LOOP) if k in (1, 2) else len(SLIT_X)
        u0, u1 = STRIP_U[k]
        ids = [ri[me.loops[li].vertex_index].value for li in p.loop_indices]
        wrap = 0 in ids and n - 1 in ids
        for li, i in zip(p.loop_indices, ids):
            vi = me.loops[li].vertex_index
            i = n if (wrap and i == 0) else i
            uvl[li].uv = (u0 + (u1 - u0) * i / n, STRIP_V[0] + (STRIP_V[1] - STRIP_V[0]) * dep[vi].value)


def inner_faces(ob):
    return {p.index for p in ob.data.polygons if ob.data.attributes['inner'].data[p.index].value > 0}


# ---------------------------------------------------------------- shape keys

def _attr(me, name):
    return np.array([d.value for d in me.attributes[name].data])


def jaw(p, w):
    """Rotate points p (mm, n x 3) about the jaw hinge by JAW_OPEN * w."""
    a = JAW_OPEN * w
    d = p - HINGE
    c, s = np.cos(a), np.sin(a)
    out = p.copy()
    out[:, 1] = HINGE[1] + d[:, 1] * c - d[:, 2] * s
    out[:, 2] = HINGE[2] + d[:, 1] * s + d[:, 2] * c
    return out


def head_keys(ob):
    me = ob.data
    P = np.array([v.co[:] for v in me.vertices]) / M
    role, ri, side = _attr(me, 'role'), _attr(me, 'ri'), _attr(me, 'side')
    gring, gcol = _attr(me, 'gring'), _attr(me, 'gcol')
    keys = {}
    # blink: the upper lid comes down over the ball (in front of it) to just below the lower lid edge
    for sd, name in ((1, 'blink_l'), (2, 'blink_r')):
        D = np.zeros_like(P)
        for i in np.nonzero(side == sd)[0]:
            if ri[i] < 0:
                continue
            t = EYE_T[ri[i]][0]
            h = eye_top(t) - eye_bot(t)
            close = eye_bot(t) + 0.12 * h - eye_top(t)
            r = role[i]
            if r in (R_OPEN['t'], R_WALL['t']):
                D[i] = (0, -1.5 if r == R_OPEN['t'] else -1.0, close)
            elif r == R_MID['t']:
                D[i] = (0, -0.8, 0.2 * close)
            elif r in (R_OPEN['b'], R_WALL['b']):
                D[i] = (0, 0, 0.15 * h)
        keys[name] = D
    # mouth_open: the jaw (chin rings, lower lip, lower inner lip) about the hinge; corners half
    w = np.zeros(len(P))
    grid = gring >= 0
    ringw = {0: 0.85, 1: 1.0, 2: 1.0, 3: 1.0}
    for i in np.nonzero(grid)[0]:
        if gring[i] in ringw:
            w[i] = ringw[gring[i]] * (1 - smooth(abs(gcol[i]), 55, 120))
    w[np.isin(role, (R_SLIT_LOW, R_IN_LOW, R_LIP_LOW))] = 1.0
    w[np.isin(role, (R_SLIT_C, R_IN_C))] = 0.5
    near = np.abs(P[:, 0]) > CORNER_X - 1
    w[(role == R_LIP_LOW) & near] = 0.6
    w[(role == R_LIP_UP) & near] = 0.4
    keys['mouth_open'] = jaw(P, w) - P
    # mouth_pucker: lips narrow and push forward, a small opening (sipping, a straw, blowing)
    D = np.zeros_like(P)
    lip = np.isin(role, (R_SLIT_UP, R_SLIT_LOW, R_SLIT_C, R_IN_UP, R_IN_LOW, R_IN_C, R_LIP_UP, R_LIP_LOW))
    D[lip, 0] = -0.36 * P[lip, 0]
    D[lip, 1] = -4.5
    D[np.isin(role, (R_SLIT_UP, R_IN_UP)), 2] = 0.8
    D[np.isin(role, (R_SLIT_LOW, R_IN_LOW)), 2] = -0.8
    rim = grid & np.isin(gring, (3, 4)) & (np.abs(gcol) <= 24)
    D[rim, 0] = -0.18 * P[rim, 0]
    D[rim, 1] = np.where(np.abs(gcol[rim]) <= 7, -3.0, -2.0)
    keys['mouth_pucker'] = D
    return keys


def part_keys(ob):
    me = ob.data
    P = np.array([v.co[:] for v in me.vertices]) / M
    part = _attr(me, 'part')
    w = np.zeros(len(P))
    w[part == 2] = np.clip((MOUTH_Z + 2 - P[part == 2, 2]) / 5.0, 0, 1)
    w[part == 4] = 1.0
    D = np.zeros_like(P)
    return {'mouth_open': jaw(P, w) - P, 'mouth_pucker': D}


def stage_face_keys():
    """Add the face keys (after the 'stylise' key, so Basis exists); deltas are in basis space."""
    for name, fn in (('male_v1_head', head_keys), ('male_v1_face', part_keys)):
        ob = bpy.data.objects[name]
        if not ob.data.shape_keys:
            ob.shape_key_add(name='Basis')
        basis = np.array([v.co[:] for v in ob.data.vertices])
        for kname, D in fn(ob).items():
            kb = ob.data.shape_keys.key_blocks.get(kname) or ob.shape_key_add(name=kname, from_mix=False)
            kb.data.foreach_set('co', (basis + D * M).astype(np.float32).ravel())
            kb.value = 0.0


def set_keys(**vals):
    for name in ('male_v1_head', 'male_v1_face'):
        sk = bpy.data.objects[name].data.shape_keys
        for k, v in vals.items():
            if sk and k in sk.key_blocks:
                sk.key_blocks[k].value = v


def rebuild_and_preview(prefix='m1_face'):
    """Head + face + hair rebuilt, painted, scaled, keyed; one sheet per view for the four face states."""
    import build_survivor_male_v1 as B
    import dress_male_v1 as D
    import render_views as R
    B.head()
    stage_face()
    D.stage_hair()
    D.stage_paint(only=['male_v1_head', 'male_v1_hair', 'male_v1_face'])
    D.stage_head_scale()
    stage_face_keys()
    D.stage_smooth_skin()
    states = (('neutral', {}), ('blink', dict(blink_l=1, blink_r=1)), ('open', dict(mouth_open=1)),
              ('pucker', dict(mouth_pucker=1, mouth_open=0.15)))
    off = dict(blink_l=0, blink_r=0, mouth_open=0, mouth_pucker=0)
    sheets = []
    for view in ('front', ('q45', (-0.72, -0.69, 0.08), (0, 0, 1)), 'right'):
        paths = []
        for tag, vals in states:
            set_keys(**off)
            set_keys(**vals)
            paths += D.stage_render('%s_%s' % (prefix, tag), size=600, span=0.30, focus=(0, 0, 1.66), views=[view])
        vname = view if isinstance(view, str) else view[0]
        sheets.append(D.RENDERS + '/%s_states_%s.png' % (prefix, vname))
        R.sheet(paths, sheets[-1])
    set_keys(**off)
    return sheets
