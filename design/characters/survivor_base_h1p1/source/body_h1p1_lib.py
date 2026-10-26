"""H1-P1 survivor base body: proportions, skeleton, signed-distance body, skin weights (pure data + math).

build_survivor_base_h1p1.py turns it into the Blender scene. Units: millimetres in the Blender world frame (Z up, the
body faces -Y, its right side is -X); the scene itself is in metres (1 block = 1 m). The right side is authored and the
left side is its mirror S = diag(-1, 1, 1): a left bone's head is S h and its frame S F S (so local x flips, flexion about
local X keeps its sign, eulers about Y and Z flip), and a left shape is the right shape evaluated at S p.

The hands are the H1-P0 B hand (design/items/mesh_hand_p0/source/hand_p0_lib.py): its bone table, frames and shapes
are reused unchanged, carried by hand_r (hand frame H: X radial, Y distal, Z palmar, origin at the wrist joint); only the
flat wrist cut is dropped so the palm blends into the forearm.
"""
import math
import sys

import numpy as np
from mathutils import Matrix, Vector

HAND_SRC = r"D:/Minecraft Modding/Apocalypse First Light/design/items/mesh_hand_p0/source"
if HAND_SRC not in sys.path:
    sys.path.insert(0, HAND_SRC)
import hand_p0_lib as HL      # noqa: E402

S = Matrix.Diagonal((-1.0, 1.0, 1.0))
FAR = 1.0e6

# ---- proportions (mm), adult male, stature 1780; joint centres, not surface landmarks
BODY = dict(
    stature=1780.0,
    hips=(0.0, 10.0, 960.0), spine=(0.0, 10.0, 1060.0), chest=(0.0, 18.0, 1230.0), chest_top=(0.0, 12.0, 1460.0),
    neck=(0.0, 22.0, 1465.0), head=(0.0, 2.0, 1585.0), crown=1780.0,
    clavicle=(-22.0, -5.0, 1440.0), shoulder=(-182.0, 15.0, 1428.0),     # ANSUR II biacromial 415 (bone), bideltoid 509
    upper_arm=300.0, forearm=255.0, arm_down=45.0, elbow_prebend=8.0,     # A-pose: arms 45 deg from vertical
    # ANSUR II (2012) men 50th percentile x 1.014 for 1.78 m: crotch 845, knee joint 491, foot 275 (heel 60 behind the
    # ankle -> big toe tip -197, see FOOT_*); chest 289 x 253, waist 325 x 234, hip breadth 344, thigh 624 round, wrist 176 round
    hip=(-92.0, 8.0, 935.0), knee=(-100.0, -2.0, 500.0), ankle=(-108.0, 18.0, 88.0),
    ball=(-116.0, -117.0, 20.0), toe=(-117.0, -187.0, 14.0),
)


def mat3(x, y, z):
    return Matrix((x, y, z)).transposed()


def frame_from(y, z_hint):
    y = Vector(y).normalized()
    z = Vector(z_hint)
    z = (z - z.dot(y) * y).normalized()
    return mat3(y.cross(z), y, z)


def rot_x(deg):
    return Matrix.Rotation(math.radians(deg), 3, 'X')


class Bone:
    """head mm (world), frame 3x3 (columns X, Y = along the bone, Z), length mm, deform flag, H1-P0 hand bone link."""
    def __init__(self, name, parent, head, frame, length, deform=True, hand=None):
        self.name, self.parent, self.head, self.frame, self.length = name, parent, Vector(head), frame, length
        self.deform, self.hand = deform, hand

    @property
    def tail(self):
        return self.head + self.frame.col[1] * self.length


def mirror_name(n):
    return n[:-2] + '_l' if n.endswith('_r') else (n[:-2] + '_r' if n.endswith('_l') else n)


def right_side():
    """Right arm, hand (H1-P0 bones, suffix _r) and leg, parents first."""
    B = BODY
    fwd = Vector((0, -1, 0))
    out = []
    js = Vector(B['shoulder'])
    out.append(Bone('clavicle_r', 'chest', B['clavicle'], frame_from(js - Vector(B['clavicle']), fwd), (js - Vector(B['clavicle'])).length))
    a = math.radians(B['arm_down'])
    d = Vector((-math.sin(a), 0, -math.cos(a)))
    fu = frame_from(d, fwd)
    out.append(Bone('upper_arm_r', 'clavicle_r', js, fu, B['upper_arm']))
    je = js + d * B['upper_arm']
    ff = fu @ rot_x(B['elbow_prebend'])                   # flexion about local X tilts the forearm forward
    out.append(Bone('forearm_r', 'upper_arm_r', je, ff, B['forearm']))
    out.append(Bone('forearm_twist_r', 'forearm_r', je + ff.col[1] * B['forearm'] * 0.5, ff, B['forearm'] * 0.5))
    jw = je + ff.col[1] * B['forearm']
    # hand frame H: radial (thumb) = forearm Z (forward), distal = forearm Y, palmar = -forearm X (toward the body):
    # palms face the thighs, thumbs forward (a neutral forearm)
    fh = mat3(ff.col[2], ff.col[1], -ff.col[0])
    for hb in HL.skeleton():
        name = hb.name + '_r'
        parent = 'forearm_r' if hb.parent is None else hb.parent + '_r'
        out.append(Bone(name, parent, jw + fh @ hb.head, fh @ hb.frame, hb.length, hand=hb.name))
    jh, jk, ja, jb, jt = (Vector(B[k]) for k in ('hip', 'knee', 'ankle', 'ball', 'toe'))
    out.append(Bone('thigh_r', 'hips', jh, frame_from(jk - jh, fwd), (jk - jh).length))
    out.append(Bone('shin_r', 'thigh_r', jk, frame_from(ja - jk, fwd), (ja - jk).length))
    up = Vector((0, 0, 1))
    out.append(Bone('foot_r', 'shin_r', ja, frame_from(jb - ja, up), (jb - ja).length))
    out.append(Bone('toes_r', 'foot_r', jb, frame_from(jt - jb, up), (jt - jb).length))
    return out, fh, jw


def skeleton():
    """All deform bones (world mm, A-pose rest), parents first: centre, right side, mirrored left side."""
    B = BODY
    fwd = Vector((0, -1, 0))
    centre = [Bone('root', None, (0, 0, 0), frame_from((0, -1, 0), (0, 0, 1)), 200.0),
              Bone('hips', 'root', B['hips'], frame_from((0, 0, 1), fwd), B['spine'][2] - B['hips'][2])]
    for name, parent, a, b in (('spine', 'hips', 'spine', 'chest'), ('chest', 'spine', 'chest', 'chest_top'),
                               ('neck', 'chest', 'neck', 'head')):
        h, t = Vector(B[a]), Vector(B[b])
        centre.append(Bone(name, parent, h, frame_from(t - h, fwd), (t - h).length))
    h = Vector(B['head'])
    centre.append(Bone('head', 'neck', h, frame_from((0, 0, 1), fwd), B['crown'] - h.z))
    right, _, _ = right_side()
    left = [Bone(mirror_name(b.name), mirror_name(b.parent), S @ b.head, S @ b.frame @ S, b.length, hand=b.hand) for b in right]
    # parents first: centre, then each side (a side's bones are already ordered)
    return centre + right + left


# IK controls and poles (not deformers, not exported): world mm at the rest pose
def controls(bones):
    by = {b.name: b for b in bones}
    out = []
    for side in 'rl':
        hand = by['hand_' + side]
        out.append(Bone('ik_hand_' + side, 'root', hand.head, hand.frame, 60.0, deform=False))
        el = by['forearm_' + side].head
        out.append(Bone('pole_elbow_' + side, 'chest', el + Vector((0, 380, 0)), frame_from((0, 1, 0), (0, 0, 1)), 50.0, deform=False))
    return out


# ---- shapes (mm, world rest pose). A shape belongs to a right-side or centre bone; left bones use the mirror.

def sd_round_cone(q, h, r0, r1, sx=1.0, sz=1.0):
    """Round cone along local +y from 0 to h, radii r0 -> r1; sx / sz > 1 flatten the cross-section along x / z."""
    b = (r0 - r1) / h
    a = math.sqrt(max(1 - b * b, 1e-9))
    qx = np.sqrt((q[:, 0] * sx) ** 2 + (q[:, 2] * sz) ** 2)
    qy = q[:, 1]
    k = -b * qx + a * qy
    mid = a * qx + b * qy - r0
    bot = np.sqrt(qx * qx + qy * qy) - r0
    top = np.sqrt(qx * qx + (qy - h) ** 2) - r1
    return np.where(k < 0, bot, np.where(k > a * h, top, mid))


# torso cross-sections: height z, half width, half depth, depth centre y (mm); a Catmull-Rom loft between them
TORSO = [(840, 40, 40, 30), (865, 128, 96, 22), (900, 166, 112, 18), (935, 168, 114, 14), (985, 162, 108, 10),
         (1045, 152, 106, 6), (1090, 150, 106, 4), (1150, 148, 108, 2), (1220, 148, 118, 2), (1290, 147, 126, 4),
         (1350, 150, 124, 8), (1405, 150, 112, 12), (1450, 140, 90, 18), (1485, 108, 66, 22), (1515, 66, 54, 24),
         (1540, 40, 40, 24)]
TORSO_N = 2.5           # superellipse exponent of the sections: a torso is boxier than an ellipse (ANSUR II
                        # chest 289 x 253 mm but 1056 mm round)


def _loft_tables(step=2.0):
    k = np.array(TORSO, np.float64)
    z = np.arange(k[0, 0], k[-1, 0] + step, step)
    out = []
    for col in (1, 2, 3):
        pts = k[:, [0, col]]
        vals = np.empty_like(z)
        for i, zz in enumerate(z):
            j = min(max(int(np.searchsorted(pts[:, 0], zz)) - 1, 0), len(pts) - 2)
            p0, p1, p2, p3 = pts[max(j - 1, 0)], pts[j], pts[j + 1], pts[min(j + 2, len(pts) - 1)]
            t = (zz - p1[0]) / (p2[0] - p1[0])
            m1 = (p2[1] - p0[1]) / max(p2[0] - p0[0], 1e-9) * (p2[0] - p1[0])
            m2 = (p3[1] - p1[1]) / max(p3[0] - p1[0], 1e-9) * (p2[0] - p1[0])
            h00, h10, h01, h11 = 2 * t ** 3 - 3 * t ** 2 + 1, t ** 3 - 2 * t ** 2 + t, -2 * t ** 3 + 3 * t ** 2, t ** 3 - t ** 2
            vals[i] = h00 * p1[1] + h10 * m1 + h01 * p2[1] + h11 * m2
        out.append(vals.astype(np.float32))
    return z.astype(np.float32), out[0], out[1], out[2]


TORSO_Z, TORSO_HW, TORSO_HD, TORSO_Y = _loft_tables()


def sd_torso(p):
    """Approximate distance to the lofted torso: the 2D ellipse distance of the section at the point's height,
    closed by the end planes."""
    z = p[:, 2]
    hw = np.interp(z, TORSO_Z, TORSO_HW)
    hd = np.interp(z, TORSO_Z, TORSO_HD)
    yc = np.interp(z, TORSO_Z, TORSO_Y)
    n = TORSO_N
    ax, ay = np.abs(p[:, 0]) / hw + 1e-9, np.abs(p[:, 1] - yc) / hd + 1e-9
    F = (ax ** n + ay ** n) ** (1 / n)
    gx = F ** (1 - n) * ax ** (n - 1) / hw          # |grad F|: F - 1 over it approximates the distance
    gy = F ** (1 - n) * ay ** (n - 1) / hd
    d2 = (F - 1) / np.maximum(np.sqrt(gx * gx + gy * gy), 1e-9)
    dz = np.maximum(TORSO_Z[0] - z, z - TORSO_Z[-1])
    out = np.maximum(d2, dz)
    both = (d2 > 0) & (dz > 0)
    return np.where(both, np.sqrt(d2 * d2 + dz * dz), out)


# ---- foot ----------------------------------------------------------------------------------------------------------
# Right foot in its own plan frame: s forward from the back of the heel, v lateral (world -X), z up from the floor.
# Sizes: ANSUR II (2012) men 50th percentile x 1.014 (1.78 m): foot length 275, foot breadth 103, heel breadth 73,
# heel -> first ball 204, ball-of-foot circumference 256, lateral malleolus height 74, bimalleolar breadth 76.
# The foot is a body (heel to the balls, an explicit lofted mesh turned into an exact distance grid) plus five toes
# (round-cone chains like the H1-P0 fingers). The fifth ball sits at about 0.66 of the foot length, so the front of
# the body slants back toward the little toe.
FOOT_HEEL = 60.0        # heel back behind the ankle joint
# body sections: (s, medial half width, lateral half width, top height on the centre line)
FOOT_RINGS = [(24, 35, 35, 76), (40, 35, 36, 92), (60, 35, 37, 100), (80, 36, 40, 96), (100, 38, 44, 87),
              (125, 40, 48, 75), (150, 42, 52, 63), (175, 44, 56, 52), (195, 46, 57, 44), (212, 45, 54, 38),
              (226, 44, 50, 32), (238, 42, 44, 27)]      # to the toe webs
FOOT_HEEL_CAP = 24.0    # heel rounds over this length (its back is 38 mm up, the bulb behind the tendon)
FOOT_FRONT_CAP = 12.0
FOOT_ARCH = 14.0        # medial arch: the sole lifts this much at the inner edge under the instep
FOOT_LATERAL_FALL = 0.22    # the top falls toward the outer edge (the outer border is low, the instep high inside)


def _front(v):
    """Front of the foot body at lateral offset v: the toe webs, about 238 inside to 213 outside (the toes show
    about 38 mm past the web at the big toe, 30 at the second, 20 at the little toe)."""
    return 238.0 - 0.24 * np.clip(v + 45.0, 0.0, 105.0)


# toes, medial to lateral: joints (s, v, z) from the ball (inside the body) to the tip centre, and radii
FOOT_TOES = [
    ([(204, -29, 22), (238, -31, 19), (262, -32, 13.5)], [14.0, 12.5, 11.0]),                     # hallux
    ([(200, -6, 18), (226, -5, 19.5), (246, -4, 13), (258, -3, 10)], [9.6, 9.0, 8.0, 7.5]),
    ([(194, 10, 17), (219, 11, 17.5), (237, 12.5, 12), (248, 13.5, 9.5)], [9.2, 8.6, 7.6, 7.2]),
    ([(187, 25, 16), (210, 26.5, 15.5), (226, 28, 11), (236, 29, 9)], [8.8, 8.2, 7.4, 7.0]),
    ([(180, 40, 15), (199, 42, 14), (213, 44, 10.5), (222, 45, 9)], [8.6, 8.0, 7.3, 7.0]),
]
FOOT_TOE_SZ = 1.12      # toes are a little wider than tall
FOOT_BALL = 190.0       # toe bone pivot (s) is the ball line: see BODY['ball']


def _smooth(keys, x):
    """Catmull-Rom through (key, value) rows, evaluated at x (clamped to the ends)."""
    k = np.asarray(keys, np.float64)
    x = np.clip(np.asarray(x, np.float64), k[0, 0], k[-1, 0])
    j = np.clip(np.searchsorted(k[:, 0], x) - 1, 0, len(k) - 2)
    out = []
    for col in range(1, k.shape[1]):
        p0, p1 = k[np.maximum(j - 1, 0)], k[j]
        p2, p3 = k[j + 1], k[np.minimum(j + 2, len(k) - 1)]
        h = p2[:, 0] - p1[:, 0]
        t = (x - p1[:, 0]) / h
        m1 = (p2[:, col] - p0[:, col]) / np.maximum(p2[:, 0] - p0[:, 0], 1e-9) * h
        m2 = (p3[:, col] - p1[:, col]) / np.maximum(p3[:, 0] - p1[:, 0], 1e-9) * h
        out.append((2 * t ** 3 - 3 * t ** 2 + 1) * p1[:, col] + (t ** 3 - 2 * t ** 2 + t) * m1
                   + (-2 * t ** 3 + 3 * t ** 2) * p2[:, col] + (t ** 3 - t ** 2) * m2)
    return out


def foot_body_mesh(around=48):
    """Closed mesh of the right foot body in plan coordinates (s, v, z) mm: rings of a section with a flat sole and a
    domed top, the heel and the front capped round, the medial arch lifted, the front slanted to the fifth ball."""
    s0, s1 = FOOT_RINGS[0][0], FOOT_RINGS[-1][0]
    cap_h = np.linspace(0.0, 1.0, 13)[1:]                         # heel cap, fine near the back
    stations = list(s0 * (1 - np.cos(cap_h * np.pi / 2)))
    stations += list(np.arange(s0 + 3.0, s1 - FOOT_FRONT_CAP, 3.0))
    cap_f = np.linspace(0.0, 1.0, 9)[1:]
    stations += list(s1 - FOOT_FRONT_CAP + FOOT_FRONT_CAP * np.sin(cap_f * np.pi / 2))
    st = np.array(stations)
    wm, wl, zt = _smooth(FOOT_RINGS, st)
    # caps: the section shrinks (quarter ellipse) about its middle
    f = np.ones_like(st)
    hb = st < s0
    f[hb] = np.sqrt(np.maximum(1 - ((s0 - st[hb]) / s0) ** 2, 0.0))
    fr = st > s1 - FOOT_FRONT_CAP
    f[fr] = np.sqrt(np.maximum(1 - ((st[fr] - (s1 - FOOT_FRONT_CAP)) / FOOT_FRONT_CAP) ** 2, 0.0))
    th = np.linspace(0.0, 2 * np.pi, around, endpoint=False)
    ct, sn = np.cos(th), np.sin(th)
    rings = []
    for i, s in enumerate(st):
        zb = 0.0
        zc = zb + 0.36 * (zt[i] - zb)
        a_top, a_bot = zt[i] - zc, zc - zb
        w = np.where(ct > 0, wl[i], wm[i])
        x = np.sign(ct) * np.abs(ct) ** (2 / 2.6) * w * f[i]                          # v
        up = sn > 0
        z = np.where(up, np.abs(sn) ** (2 / 2.4) * a_top, -np.abs(sn) ** (2 / 6.0) * a_bot) * f[i] + zc
        # the top falls toward the outer border
        z = np.where(up, z - FOOT_LATERAL_FALL * a_top * f[i] * np.clip(x / max(wl[i], 1.0), 0, 1) * np.abs(sn), z)
        # medial arch: the sole lifts at the inner edge under the instep
        arch = FOOT_ARCH * max(0.0, np.sin(np.pi * (s - 55.0) / 130.0)) if 55.0 < s < 185.0 else 0.0
        z = np.where(~up & (x < 0), z + arch * np.clip(-x / max(wm[i], 1.0), 0, 1) ** 1.6, z)
        ss = np.full(around, s)
        # slant the front back toward the little toe
        front = _front(x)
        k = s1 - 150.0
        ss = np.where(ss > 150.0, 150.0 + (ss - 150.0) * (front - 150.0) / k, ss)
        rings.append(np.stack([ss, x, z], 1))
    pts = np.concatenate(rings + [np.zeros((2, 3))])
    n = len(st)
    quads = []
    for i in range(n - 1):
        for j in range(around):
            a, b = i * around + j, i * around + (j + 1) % around
            quads.append((a, b, b + around, a + around))
    tris = []
    back, tip = n * around, n * around + 1
    for j in range(around):
        tris.append((back, (j + 1) % around, j))
        tris.append((tip, (n - 1) * around + j, (n - 1) * around + (j + 1) % around))
    # the end points: middle of the first / last ring
    pts[back] = rings[0].mean(0)
    pts[tip] = rings[-1].mean(0)
    return pts, np.array(quads, np.int64), np.array(tris, np.int64)


class FootGrid:
    """Exact signed distance of the right foot body, world mm: the mesh above as an OpenVDB level set, read back into a
    dense array and sampled trilinearly; outside the band the distance grows with the distance to the band box."""
    VOXEL, BAND = 1.0, 40.0

    def __init__(self, ankle):
        import openvdb
        self.ankle = np.asarray(ankle, np.float32)
        P, Q, T = foot_body_mesh()
        W = self.to_world(P)
        quads = Q[:, ::-1] if self._flipped() else Q
        tris = T[:, ::-1] if self._flipped() else T
        xf = openvdb.createLinearTransform(voxelSize=self.VOXEL)
        g = openvdb.FloatGrid.createLevelSetFromPolygons(
            W.astype(np.float32), triangles=tris.astype(np.uint32), quads=quads.astype(np.uint32), transform=xf,
            exBandWidth=self.BAND, inBandWidth=self.BAND)
        self.lo = np.floor(W.min(0) - self.BAND)
        self.hi = np.ceil(W.max(0) + self.BAND)
        dims = (self.hi - self.lo).astype(int) + 1
        arr = np.zeros(tuple(dims), np.float32)
        g.copyToArray(arr, ijk=tuple(int(c) for c in self.lo))
        self.arr = arr
        self.mesh_lo, self.mesh_hi = W.min(0), W.max(0)

    def _flipped(self):
        # plan (s, v, z) -> world (ax - v, back - s, z) mirrors (determinant -1): reverse the winding
        return True

    def to_world(self, P):
        back = self.ankle[1] + FOOT_HEEL
        return np.stack([self.ankle[0] - P[:, 1], back - P[:, 0], P[:, 2]], 1)

    def eval(self, p):
        g = (p - self.lo) / self.VOXEL
        dims = np.array(self.arr.shape) - 1
        c = np.clip(g, 0, dims - 1e-4)
        out = np.linalg.norm(g - c, axis=1) * self.VOXEL          # beyond the array: add the distance to it
        i = np.floor(c).astype(int)
        t = c - i
        a = self.arr
        x0, y0, z0 = i[:, 0], i[:, 1], i[:, 2]
        tx, ty, tz = t[:, 0], t[:, 1], t[:, 2]
        def at(dx, dy, dz):
            return a[x0 + dx, y0 + dy, z0 + dz]
        v = ((at(0, 0, 0) * (1 - tx) + at(1, 0, 0) * tx) * (1 - ty) + (at(0, 1, 0) * (1 - tx) + at(1, 1, 0) * tx) * ty) * (1 - tz) \
            + ((at(0, 0, 1) * (1 - tx) + at(1, 0, 1) * tx) * (1 - ty) + (at(0, 1, 1) * (1 - tx) + at(1, 1, 1) * tx) * ty) * tz
        return (v + out).astype(np.float32)


_FOOT_GRIDS = {}


def foot_grid(ankle):
    key = tuple(float(x) for x in ankle)
    if key not in _FOOT_GRIDS:
        _FOOT_GRIDS[key] = FootGrid(ankle)
    return _FOOT_GRIDS[key]


def foot_point(ankle, s, v, z):
    """Plan (s, v, z) of the right foot -> world mm."""
    return Vector((ankle[0] - v, ankle[1] + FOOT_HEEL - s, z))


class Prim:
    """kind 'cone': from a to b (world mm), radii r0 -> r1, cross-section flattened by sx / sz in the frame whose z is
    `flat` (orthogonalised to the axis); kind 'ellip': centre c, radii r along the columns of `frame`; 'loft': the
    torso loft, `cut` in z; 'foot': the right foot body (FootGrid) at `ankle`."""
    def __init__(self, bone, kind, **kw):
        self.bone, self.kind = bone, kind
        if kind == 'cone':
            a, b = Vector(kw['a']), Vector(kw['b'])
            self.o = np.asarray(a, np.float32)
            self.F = np.asarray(frame_from(b - a, kw.get('flat', (0, -1, 0)) if abs((b - a).normalized().dot(Vector(kw.get('flat', (0, -1, 0))))) < 0.98 else (1, 0, 0)), np.float32)
            self.h, self.r0, self.r1 = (b - a).length, kw['r0'], kw['r1']
            self.sx, self.sz = kw.get('sx', 1.0), kw.get('sz', 1.0)
            r = max(self.r0, self.r1)
            self.lo = np.minimum(a, b) - r
            self.hi = np.maximum(a, b) + r
        elif kind == 'loft':
            self.cut = kw.get('cut', (-1e9, 1e9))
            z = TORSO_Z
            self.lo = np.array([-TORSO_HW.max() - 1, (TORSO_Y - TORSO_HD).min() - 1, max(z[0], self.cut[0]) - 1])
            self.hi = np.array([TORSO_HW.max() + 1, (TORSO_Y + TORSO_HD).max() + 1, min(z[-1], self.cut[1]) + 1])
        elif kind == 'foot':
            self.grid = foot_grid(kw['ankle'])
            self.lo, self.hi = self.grid.mesh_lo - 1, self.grid.mesh_hi + 1
        else:
            self.o = np.asarray(Vector(kw['c']), np.float32)
            self.F = np.asarray(kw.get('frame', Matrix.Identity(3)), np.float32)
            self.r = np.asarray(kw['r'], np.float32)
            rr = float(self.r.max())
            self.lo, self.hi = self.o - rr, self.o + rr

    def eval(self, p):
        if self.kind == 'foot':
            return self.grid.eval(p)
        if self.kind == 'loft':
            d = sd_torso(p)
            lo, hi = self.cut
            return np.maximum(d, np.maximum(lo - p[:, 2], p[:, 2] - hi))
        q = (p - self.o) @ self.F
        if self.kind == 'cone':
            return sd_round_cone(q, self.h, self.r0, self.r1, self.sx, self.sz)
        return HL.sd_ellipsoid(q, (0, 0, 0), self.r)


def local_point(bone, v):
    return bone.head + bone.frame @ Vector(v)


def shapes(bones):
    """Primitives per bone (centre and right side) and the blend radius inside each bone's group."""
    by = {b.name: b for b in bones}
    out = {}

    def add(name, k, *prims):
        out[name] = (list(prims), k)
    # torso: one lofted body (TORSO sections) cut into the three spine segments for the weights, plus the buttocks,
    # pectorals and shoulder blades; the shoulder girdle (trapezius) runs neck -> acromion on the clavicle
    zs = (BODY['spine'][2], BODY['chest'][2])
    # (a smooth union bulges by k / 4 even where a shape lies inside the loft, so only the buttocks are added, with a
    # small blend: pectorals and shoulder blades made the chest read as a woman's)
    add('hips', 22.0,
        Prim('hips', 'loft', cut=(-1e9, zs[0])),
        Prim('hips', 'ellip', c=(-70, 66, 895), r=(80, 62, 92)))                              # buttock
    add('spine', 45.0,
        Prim('spine', 'loft', cut=zs))
    add('chest', 40.0,
        Prim('chest', 'loft', cut=(zs[1], 1e9)))
    add('neck', 20.0,
        Prim('neck', 'cone', a=(0, 26, 1440), b=(0, 6, 1600), r0=63, r1=55, flat=(0, -1, 0), sz=1.05))
    add('head', 22.0,
        Prim('head', 'ellip', c=(0, 10, 1682), r=(77, 97, 98)),                               # cranium
        Prim('head', 'ellip', c=(0, -30, 1612), r=(60, 58, 64)))                              # face and jaw (plain)
    add('clavicle_r', 25.0,
        Prim('clavicle_r', 'cone', a=(-34, 26, 1490), b=(-176, 16, 1455), r0=44, r1=38, flat=(0, 0, 1), sz=1.3))  # trapezius
    ua = by['upper_arm_r']
    add('upper_arm_r', 18.0,
        Prim('upper_arm_r', 'ellip', c=local_point(ua, (0, 22, 0)), r=(60, 66, 60), frame=ua.frame),      # deltoid
        Prim('upper_arm_r', 'cone', a=local_point(ua, (0, 10, 0)), b=ua.tail, r0=47, r1=38, flat=ua.frame.col[2]),
        Prim('upper_arm_r', 'ellip', c=local_point(ua, (0, 150, 13)), r=(37, 92, 33), frame=ua.frame),    # biceps
        Prim('upper_arm_r', 'ellip', c=local_point(ua, (0, 125, -15)), r=(39, 100, 33), frame=ua.frame))  # triceps
    fa = by['forearm_r']
    # forearm local x is dorsal / palmar at the wrist (the hand's -Z), z is radial / ulnar: the wrist is 54 x 36
    add('forearm_r', 14.0,
        Prim('forearm_r', 'ellip', c=local_point(fa, (0, 72, 2)), r=(39, 92, 44), frame=fa.frame),        # muscle belly
        Prim('forearm_r', 'cone', a=local_point(fa, (0, 8, 0)), b=local_point(fa, (0, 248, 0)), r0=39, r1=30,
             flat=fa.frame.col[2], sx=1.35),
        Prim('forearm_r', 'ellip', c=local_point(fa, (0, 2, -20)), r=(21, 24, 17), frame=fa.frame))       # olecranon
    th = by['thigh_r']
    add('thigh_r', 25.0,
        Prim('thigh_r', 'cone', a=local_point(th, (0, -10, 0)), b=th.tail, r0=94, r1=55, flat=th.frame.col[2]),
        Prim('thigh_r', 'ellip', c=local_point(th, (6, 170, 18)), r=(72, 170, 64), frame=th.frame),       # quadriceps
        Prim('thigh_r', 'ellip', c=local_point(th, (0, 185, -20)), r=(60, 150, 56), frame=th.frame))      # hamstrings
    sh = by['shin_r']
    add('shin_r', 18.0,
        Prim('shin_r', 'ellip', c=local_point(sh, (0, 0, 38)), r=(30, 34, 18), frame=sh.frame),           # kneecap
        Prim('shin_r', 'cone', a=sh.head, b=local_point(sh, (0, sh.length - 6, 0)), r0=50, r1=33, flat=sh.frame.col[2]),
        Prim('shin_r', 'ellip', c=local_point(sh, (-4, 125, -30)), r=(54, 118, 48), frame=sh.frame))     # calf
    ja, jb, jt = Vector(BODY['ankle']), Vector(BODY['ball']), Vector(BODY['toe'])
    # foot: the body (heel to the balls, exact distance grid) with the ankle bones; toes: five round-cone chains
    add('foot_r', 10.0,
        Prim('foot_r', 'foot', ankle=ja),
        Prim('foot_r', 'ellip', c=ja + Vector((-29, 6, -14)), r=(9, 14, 15)),                             # lateral malleolus
        Prim('foot_r', 'ellip', c=ja + Vector((25, 2, -4)), r=(9, 14, 15)))                               # medial malleolus
    toes = []
    for joints, radii in FOOT_TOES:
        pts = [foot_point(ja, *j) for j in joints]
        for a, b, r0, r1 in zip(pts, pts[1:], radii, radii[1:]):
            toes.append(Prim('toes_r', 'cone', a=a, b=b, r0=r0, r1=r1, flat=(0, 0, 1), sz=FOOT_TOE_SZ))
    add('toes_r', 3.0, *toes)
    return out


# blend radius between a bone's group and its children (keyed by the child), mm
CHILD_BLEND = dict(spine=40.0, chest=40.0, neck=28.0, head=22.0, clavicle_r=30.0, upper_arm_r=26.0, forearm_r=16.0,
                   hand_r=12.0, thigh_r=34.0, shin_r=16.0, foot_r=22.0, toes_r=7.0)


class HandShape(HL.HandSDF):
    """The H1-P0 hand without its flat wrist cut, placed at the wrist of the body (points in world mm)."""
    def __init__(self, frame, wrist):
        super().__init__(HL.skeleton())
        self.Fw = np.asarray(frame, np.float32)
        self.w = np.asarray(wrist, np.float32)

    def to_h(self, p):
        return ((p - self.w) @ self.Fw).astype(np.float32)

    def group_h(self, name, p):
        b = self.by[name]
        q = ((p - np.asarray(b.head, np.float32)) @ np.asarray(b.frame, np.float32)).astype(np.float32)
        prims, k = self.groups[name]
        d = None
        for s in prims:
            e = s.eval(q)
            d = e if d is None else HL.smin(d, e, k)
        if name == 'hand':
            for frame, base, length in self.mcs:
                qq = ((p - np.asarray(base, np.float32)) @ np.asarray(frame, np.float32)).astype(np.float32)
                d = HL.smin(d, HL.sd_round_cone_y(qq, 0.0, length, 9.5, 10.2, 1.05), k)
        return d

    def chain_h(self, root, p):
        d = self.group_h(root, p)
        for c in [b for b in self.bones if b.parent == root]:
            d = HL.smin(d, self.chain_h(c.name, p), 3.0)
        return d

    def body_h(self, p):
        d = HL.smin(self.group_h('hand', p), self.chain_h('thumb_01', p), 6.0)
        for name, *_ in HL.FINGERS:
            d = HL.smin(d, self.chain_h(name + '_01', p), 5.0)
        return d


class BodySDF:
    """Signed distance of the whole body (one blended skin) and of each bone's own group (for weights), world mm."""
    def __init__(self, bones):
        self.bones = bones
        self.by = {b.name: b for b in bones}
        self.groups = shapes(bones)
        _, fh, jw = right_side()
        self.hand = HandShape(fh, jw)
        self.children = {}
        for b in bones:
            if b.parent and not b.name.endswith('_l'):
                self.children.setdefault(b.parent, []).append(b.name)
        self.active = None            # optional set of group names worth evaluating (spatial culling)

    def group(self, name, p):
        """A centre / right bone's own group; left bones: the mirror."""
        if name.endswith('_l'):
            return self.group(mirror_name(name), p * np.array([-1, 1, 1], np.float32))
        b = self.by[name]
        if b.hand:
            return self.hand.group_h(b.hand, self.hand.to_h(p))
        if name not in self.groups:
            return np.full(len(p), FAR, np.float32)
        prims, k = self.groups[name]
        d = None
        centre = not name.endswith('_r')
        m = p * np.array([-1, 1, 1], np.float32) if centre else None
        for s in prims:
            e = s.eval(p)
            if centre and s.kind != 'loft':
                e = np.minimum(e, s.eval(m))
            d = e if d is None else HL.smin(d, e, k)
        return d

    def chain(self, name, p):
        if self.active is not None and name not in self.active:
            return np.full(len(p), FAR, np.float32)
        if name == 'hand_r':
            return self.hand.body_h(self.hand.to_h(p))
        d = self.group(name, p) if name in self.groups else np.full(len(p), FAR, np.float32)
        for c in self.children.get(name, ()):
            if self.by[c].hand and c != 'hand_r' or c == 'forearm_twist_r':
                continue
            d = HL.smin(d, self.chain(c, p), CHILD_BLEND.get(c, 10.0))
        return d

    def body(self, p):
        p = np.asarray(p, np.float32)
        m = p * np.array([-1, 1, 1], np.float32)
        d = self.centre(p)
        for side in (p, m):
            d = HL.smin(d, self.chain('clavicle_r', side), CHILD_BLEND['clavicle_r'])
            d = HL.smin(d, self.chain('thigh_r', side), CHILD_BLEND['thigh_r'])
        return np.maximum(d, 1.0 - p[:, 2])        # flat soles on the floor

    def centre(self, p):
        """The torso loft whole (its bone pieces would bulge where they meet), its extra shapes, neck and head."""
        if self.active is not None and not (self.active & {'hips', 'spine', 'chest', 'neck', 'head'}):
            return np.full(len(p), FAR, np.float32)
        d = sd_torso(p)
        m = p * np.array([-1, 1, 1], np.float32)
        for name in ('hips', 'spine', 'chest'):
            prims, k = self.groups[name]
            for s in prims:
                if s.kind != 'loft':          # paired shapes: authored on the right, mirrored to the left
                    d = HL.smin(d, s.eval(p), k)
                    d = HL.smin(d, s.eval(m), k)
        for name in ('neck', 'head'):
            d = HL.smin(d, self.group(name, p), CHILD_BLEND[name])
        return d

    # region boxes per chain, for culling: (lo, hi) world mm of each top-level piece
    def piece_boxes(self):
        out = {}
        for name, (prims, _) in self.groups.items():
            lo = np.min([p.lo for p in prims], 0)
            hi = np.max([p.hi for p in prims], 0)
            out[name] = (lo, hi)
        hl = [self.by[n] for n in self.by if n.endswith('_r') and self.by[n].hand]
        pts = np.array([tuple(b.head) for b in hl] + [tuple(b.tail) for b in hl])
        out['hand_r'] = (pts.min(0) - 30, pts.max(0) + 30)
        return out


def polygonize_sparse(sdf, lo, hi, voxel, block=8, coarse=4, margin=1.6, log=print):
    """Iso-surface of the body over a box, evaluated only near the surface: blocks of block^3 voxels are kept when the
    distance at their centre is within margin x their half-diagonal (+ 3 voxels), found by a coarse pass over blocks
    of coarse x block voxels; each kept block is evaluated with only the bone groups whose boxes come near it, copied
    into a sparse OpenVDB grid, sign-flood-filled and meshed. Returns (verts world mm, faces)."""
    import openvdb
    import time
    t0 = time.time()
    lo = np.floor((np.array(lo) - 4 * block * voxel) / (block * voxel)) * block * voxel
    hi = np.array(hi) + 4 * block * voxel
    bs = block * voxel
    nb = np.ceil((hi - lo) / bs).astype(int)
    boxes = sdf.piece_boxes()

    def active_near(c, half):
        names = set()
        for name, (a, b) in boxes.items():
            if np.all(c + half + 60 >= a) and np.all(c - half - 60 <= b):
                names.add(name)
                if name.endswith('_r'):
                    names.add(name)
        # left pieces are the mirror: a box near S c
        mc = c * np.array([-1, 1, 1])
        for name, (a, b) in boxes.items():
            if name.endswith('_r') and np.all(mc + half + 60 >= a) and np.all(mc - half - 60 <= b):
                names.add(name)
        return names

    def chains_for(names):
        act = set(names)
        for n in list(act):            # a chain is evaluated through its parents
            b = sdf.by.get(n)
            while b is not None and b.parent:
                act.add(b.parent)
                b = sdf.by.get(b.parent)
        return act

    # coarse pass over super blocks
    cb = coarse * bs
    ns = np.ceil(nb / coarse).astype(int)
    ii = np.stack(np.meshgrid(*[np.arange(n) for n in ns], indexing='ij'), -1).reshape(-1, 3)
    centres = lo + (ii + 0.5) * cb
    sdf.active = None
    d = sdf.body(centres.astype(np.float32))
    keep = np.abs(d) < margin * cb * 0.866 + 3 * voxel
    supers = ii[keep]
    log('coarse', len(ii), 'super blocks, near', len(supers), round(time.time() - t0, 1), 's')
    grid = openvdb.FloatGrid(3.0 * voxel)
    bg = 3.0 * voxel
    kept_blocks, points = 0, 0
    off = np.arange(block) * voxel
    sub = np.stack(np.meshgrid(np.arange(coarse), np.arange(coarse), np.arange(coarse), indexing='ij'), -1).reshape(-1, 3)
    for sidx in supers:
        sc = lo + (sidx + 0.5) * cb
        sdf.active = chains_for(active_near(sc, cb / 2))
        bidx = sidx * coarse + sub
        bc = lo + (bidx + 0.5) * bs
        dd = sdf.body(bc.astype(np.float32))
        near = bidx[np.abs(dd) < margin * bs * 0.866 + 3 * voxel]
        for bi in near:
            o = lo + bi * bs
            P = np.stack(np.meshgrid(o[0] + off, o[1] + off, o[2] + off, indexing='ij'), -1).reshape(-1, 3).astype(np.float32)
            v = np.clip(sdf.body(P), -bg, bg).reshape(block, block, block).astype(np.float32)
            grid.copyFromArray(v, ijk=tuple(int(x) for x in bi * block), tolerance=0.0)
            kept_blocks += 1
            points += len(P)
    sdf.active = None
    grid.signedFloodFill()
    log('fine blocks', kept_blocks, 'points', points, round(time.time() - t0, 1), 's')
    pts, tris, quads = grid.convertToPolygons(0.0, 0.0)
    verts = lo + pts * voxel
    faces = [tuple(int(i) for i in f) for f in quads] + [tuple(int(i) for i in f) for f in tris]
    log('mesh', len(verts), 'verts', len(faces), 'faces', round(time.time() - t0, 1), 's')
    return verts, faces


# ---- skin weights

HAND_BONES = {b.name for b in HL.skeleton()}
# softness (mm) of the joint at a bone's head: how far its blend with the parent reaches
JOINT_SIGMA = dict(hips=40.0, spine=40.0, chest=40.0, neck=22.0, head=16.0, clavicle=24.0, upper_arm=26.0, forearm=18.0,
                   hand=9.0, thigh=30.0, shin=20.0, foot=12.0, toes=7.0)
FINGER_SIGMA = 3.0          # H1-P0


def base(n):
    return n[:-2] if n.endswith(('_r', '_l')) else n


def sigma_of(n):
    b = base(n)
    if b in HAND_BONES and b != 'hand':
        return FINGER_SIGMA
    return JOINT_SIGMA.get(b, 20.0)


def families(bones):
    """Bones a vertex owned by b may blend with: parent and children; outside the hand also grandparent,
    grandchildren and siblings; never across sides."""
    by = {b.name: b for b in bones}
    kids = {}
    for b in bones:
        if b.parent:
            kids.setdefault(b.parent, []).append(b.name)
    out = {}
    for b in bones:
        fam = {b.name}
        if b.parent:
            fam.add(b.parent)
        fam |= set(kids.get(b.name, ()))
        if base(b.name) not in HAND_BONES or base(b.name) == 'hand':
            if b.parent and by[b.parent].parent:
                fam.add(by[b.parent].parent)
            for c in kids.get(b.name, ()):
                fam |= set(kids.get(c, ()))
            if b.parent:
                fam |= set(kids.get(b.parent, ()))
        side = b.name[-2:] if b.name.endswith(('_r', '_l')) else None
        other = {'_r': '_l', '_l': '_r'}.get(side)
        out[b.name] = {f for f in fam if not (other and f.endswith(other)) and not f.startswith('forearm_twist')
                       and f != 'root'}
        if base(b.name) in HAND_BONES and base(b.name) != 'hand':
            out[b.name] = {f for f in out[b.name] if base(f) in HAND_BONES}     # fingers stay in the hand (H1-P0)
    return out


def pair_sigma(by, a, b):
    if by[a].parent == b:
        return sigma_of(a)
    if by[b].parent == a:
        return sigma_of(b)
    return max(sigma_of(a), sigma_of(b))


def skin_weights(sdf, verts, twist=(0.12, 0.92), log=print):
    """Per vertex up to four (bone name, weight): the nearest bone group, blended with its family by the distance
    difference (exp(-dd / sigma of the joint between them)); the forearm's share moves to forearm_twist along the
    forearm (smoothstep over `twist` of the elbow -> wrist parameter)."""
    bones = [b for b in sdf.bones if b.name != 'root' and not b.name.startswith('forearm_twist')]
    names = [b.name for b in bones]
    by = sdf.by
    fam = families(sdf.bones)
    P = np.asarray(verts, np.float32)
    D = np.empty((len(P), len(names)), np.float32)
    for j, n in enumerate(names):
        D[:, j] = sdf.group(n, P)
    col = {n: j for j, n in enumerate(names)}
    out = []
    for i in range(len(P)):
        row = D[i]
        b0 = names[int(np.argmin(row))]
        cand = []
        for f in fam[b0]:
            if f not in col:
                continue
            dd = float(row[col[f]] - row[col[b0]])
            w = math.exp(-max(dd, 0.0) / pair_sigma(by, b0, f)) if f != b0 else 1.0
            if w > 0.02:
                cand.append((w, f))
        cand.sort(reverse=True)
        cand = cand[:4]
        tot = sum(w for w, _ in cand)
        ws = {f: w / tot for w, f in cand}
        for side in 'rl':
            fn = 'forearm_' + side
            if ws.get(fn):
                fa = by[fn]
                t = (Vector(P[i]) - fa.head).dot(fa.frame.col[1]) / fa.length
                u = min(1.0, max(0.0, (t - twist[0]) / (twist[1] - twist[0])))
                s = u * u * (3 - 2 * u)
                ws['forearm_twist_' + side] = ws[fn] * s
                ws[fn] *= 1 - s
        top = sorted(ws.items(), key=lambda kv: -kv[1])[:4]
        tot = sum(w for _, w in top)
        out.append([(n, w / tot) for n, w in top if w / tot > 1e-3])
    log('weights', len(out), 'verts')
    return out, D, names


def tone(sdf, name, p):
    """0 dorsal .. 1 palmar skin tone (the H1-P0 hand palette); the inner forearm lightens toward the wrist."""
    b = sdf.by[name]
    bb = base(name)
    q = Vector(p)
    if name.endswith('_l'):
        q = S @ q
        b = sdf.by[mirror_name(name)]
    if b.hand:
        hb = sdf.hand.by[b.hand]
        lq = Vector(sdf.hand.to_h(np.array([tuple(q)], np.float32))[0])
        loc = hb.frame.transposed() @ (lq - hb.head)
        r = 15.0 if hb.radii is None else hb.radii[0] + (hb.radii[1] - hb.radii[0]) * max(0.0, min(1.0, loc.y / hb.length))
        if b.hand.endswith('_03') and loc.z < -0.5 * r and loc.y > 0.42 * hb.length:
            return 'nail'
        return max(0.0, min(1.0, 0.5 + loc.z / (1.6 * r)))
    if bb in ('forearm', 'forearm_twist'):
        fa = sdf.by['forearm_r']
        loc = fa.frame.transposed() @ (q - fa.head)
        t = max(0.0, min(1.0, loc.y / fa.length))
        return max(0.0, min(0.6, 0.5 - loc.x / 50.0)) * t        # palmar side = -x
    return 0.0
