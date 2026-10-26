"""Male V1 direction A (user 2026-10-24): one-piece head, the face painted into the texture, chunky hair clumps.

Keeps the round 4 head shape (head_male_v1, uncut), the 54-bone body and the outfit. The cut eyes / mouth, the face
part (eyeballs, mouth bag, teeth) and the face shape keys of face_male_v1 are archived: not called here, the old face
object is hidden in the collection 'Male_V1_archive'. Features, eye sockets, the plane under the cheekbones, the
shadow under the lip and the jaw are painted. Expressions later: eye 3 frames, mouth 4 frames as texture swaps.

Head UV: a cylinder, the front 150 deg get 60 % of the width (512 x 512, about 1.5 px/mm on the face).
Hair: 10 clumps following the hair flow from a crown whorl, each a ridged strip ending in a tip at the hairline;
the scalp under and between them is painted dark (hairline_z).

Styles for the round 5 face samples: 'clean' (smooth gradients), 'medium' (4 value planes, brushy edges), 'heavy'
(hard planes, visible strokes and hatching, stronger warm / cool shifts).
Run in survivor_male_v1.blend: import painted_face_v1 as A; A.samples().
"""
import math
import os
import sys

import bpy
import bmesh
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

SRC = os.path.dirname(os.path.abspath(__file__))
if SRC not in sys.path:
    sys.path.insert(0, SRC)
import dress_male_v1 as D      # noqa: E402
import head_male_v1 as H       # noqa: E402

M = 0.001
smooth, mix, vnoise, fbm, line_mask = D.smooth, D.mix, D.vnoise, D.fbm, D.line_mask

HEAD_TEX = (512, 512)
U_KNOTS = ([-180.0, -75.0, 75.0, 180.0], [0.0, 0.2, 0.8, 1.0])
Z0, Z1, Y0 = 1515.0, 1780.0, 12.0
NAMES = ['male_v1_head', 'male_v1_hair', 'male_v1_body', 'male_v1_tee', 'male_v1_jacket', 'male_v1_jeans',
         'male_v1_belt', 'male_v1_boots']

# painted face layout (mm, basis head, front = -Y)
EYE_X, EYE_Z, EYE_HALF = 28.5, 1661.0, 10.5
MOUTH_Z, MOUTH_HALF = 1594.0, 19.0


# ---------------------------------------------------------------- head UV

def u_of(a):
    """Column angle (deg, -180..180, or past 180 for faces across the back seam) -> u."""
    a = np.asarray(a, float)
    w = np.where(a > 180, a - 360, a)
    return np.interp(w, *U_KNOTS) + (a > 180)


def uv_head(ob):
    me = ob.data
    uvl = me.uv_layers.get('UVMap') or me.uv_layers.new(name='UVMap')
    for p in me.polygons:
        cos = [me.vertices[me.loops[li].vertex_index].co / M for li in p.loop_indices]
        a = [math.degrees(math.atan2(c.x, -(c.y - Y0))) for c in cos]
        if max(a) - min(a) > 180:
            a = [x + 360 if x < 0 else x for x in a]
        for li, ai, c in zip(p.loop_indices, a, cos):
            uvl.data[li].uv = (float(u_of(ai)), (c.z - Z0) / (Z1 - Z0))


# ---------------------------------------------------------------- hair: clumps

def hairline_z(a_deg):
    """Hairline height (mm) at a column angle: nearly level across the forehead, turning down at the temples into
    the sideburns (round 4 arched towards the temples, which read as a bowl)."""
    A = [0, 20, 35, 50, 60, 68, 74, 79, 88, 97, 106, 118, 132, 148, 164, 180]
    Z = [1711, 1711, 1710, 1707, 1701, 1690, 1668, 1652, 1674, 1673, 1666, 1652, 1642, 1636, 1633, 1632]
    return np.interp(np.abs(a_deg), A, Z)


# Hair flows from a crown whorl at the back of the top: the top forward and over to +x (parting at -x), the sides
# down and back, the back down. Each clump: control points from root to tip, a point is (azimuth, elevation) in deg
# about CENTRE or ('h', azimuth, mm below the hairline); half width (mm), thickness (mm), tip end, tip lift (mm).
CLUMPS = [
    ([(170, 72), (180, 86), (0, 74)], 34, 13.0, 0.6, 0.0),                  # crown lock forward over the top
    ([(-10, 80), (6, 62), ('h', 8, 9)], 32, 14.0, 0.35, 3.0),              # front, forward and over to +x
    ([(22, 78), (40, 56), ('h', 44, 5)], 30, 12.0, 0.3, 2.0),              # front +x, to the temple
    ([(-42, 74), (-36, 56), ('h', -26, 7)], 20, 9.0, 0.3, 2.0),            # the parting side, front
    ([(70, 72), (88, 46), ('h', 98, 3)], 36, 8.0, 0.2, 0.0),              # side +x, down over the ear
    ([(-62, 70), (-88, 46), ('h', -104, 3)], 42, 7.5, 0.2, 0.0),          # side -x
    ([(118, 66), (124, 40), ('h', 122, 3)], 32, 7.0, 0.2, 0.0),           # behind the ear +x
    ([(178, 64), (152, 32), ('h', 147, 4)], 36, 8.0, 0.2, 0.0),           # back +x
    ([(182, 64), (-148, 34), ('h', -138, 4)], 40, 8.0, 0.2, 0.0),         # back -x
    ([(180, 68), (180, 30), ('h', 180, 5)], 30, 8.0, 0.25, 0.0),          # back, down the middle
]
ROWS = 7                     # along a clump, root to tip (the tip row is one point)
ACROSS = (-1.0, -0.5, 0.0, 0.5, 1.0)
CENTRE = D.CENTRE
_SCALP = None


def scalp_bvh():
    """The head loft without the ears (rays from CENTRE must not stop on an ear root)."""
    global _SCALP
    if _SCALP is None:
        rows = H.grid()
        verts, polys = [], []
        for r in rows[:-1]:
            verts += [Vector(p) for p in r]
        n = len(rows[0])
        for k in range(len(rows) - 2):
            for i in range(n):
                polys.append([k * n + i, k * n + (i + 1) % n, (k + 1) * n + (i + 1) % n, (k + 1) * n + i])
        verts.append(Vector(rows[-1][0]))
        top = len(verts) - 1
        k = len(rows) - 2
        polys += [[k * n + i, k * n + (i + 1) % n, top] for i in range(n)]
        _SCALP = BVHTree.FromPolygons(verts, polys)
    return _SCALP


def direction(pt):
    if pt[0] == 'h':
        _, a, drop = pt
        p = D.along(D.column_curve(a), float(hairline_z(a)) - drop)[0]
        d = p - CENTRE
    else:
        az, el = np.radians(pt[0]), np.radians(pt[1])
        d = np.array([math.sin(az) * math.cos(el), -math.cos(az) * math.cos(el), math.sin(el)])
    return d / np.linalg.norm(d)


def surf(d):
    """Scalp point along a direction from CENTRE, and its outward direction for offsets."""
    hit = scalp_bvh().ray_cast(Vector(CENTRE), Vector(d), 400.0)
    p = np.array(hit[0]) if hit[0] is not None else CENTRE + d * 80
    n = p - CENTRE
    n[2] *= 0.8
    return p, n / np.linalg.norm(n)


def path(ctrl, n):
    """n directions evenly along the great-circle polyline through the control directions."""
    D_ = [direction(c) for c in ctrl]
    dense = []
    for a, b in zip(D_[:-1], D_[1:]):
        for t in np.linspace(0, 1, 24, endpoint=False):
            v = a * (1 - t) + b * t
            dense.append(v / np.linalg.norm(v))
    dense.append(D_[-1])
    dense = np.array(dense)
    seg = np.arccos(np.clip((dense[:-1] * dense[1:]).sum(1), -1, 1))
    s = np.concatenate([[0], np.cumsum(seg)])
    ts = np.linspace(0, s[-1], n)
    out = np.array([[np.interp(t, s, dense[:, k]) for k in range(3)] for t in ts])
    return out / np.linalg.norm(out, axis=1, keepdims=True)


def stage_hair():
    rng = np.random.default_rng(5)
    bm = bmesh.new()
    uvf = []
    slots = len(CLUMPS)
    for ci, (ctrl, half, h, tip_end, lift) in enumerate(CLUMPS):
        dirs = path(ctrl, ROWS)
        cen = [surf(d)[0] for d in dirs]
        rows = []
        for r in range(ROWS):
            t = r / (ROWS - 1)                        # 0 root .. 1 tip
            p, n = surf(dirs[r])
            T = cen[min(r + 1, ROWS - 1)] - cen[max(r - 1, 0)]
            side = np.cross(T, n)
            side /= np.linalg.norm(side)
            g = np.interp(t, [0, 0.25, 0.7, 1.0], [0.35, 1.0, 0.8, tip_end])
            w = half * np.interp(t, [0, 0.3, 0.8, 1.0], [0.8, 1.0, 0.95, 0.2])
            ks = (0.0,) if r == ROWS - 1 else ACROSS
            row = []
            for k in ks:
                q, nq = surf(p + side * k * w - CENTRE)
                q = q + nq * (-0.6 + h * g * (1 - k * k) ** 0.7)
                if 0 < r < ROWS - 1:
                    q += rng.uniform(-0.7, 0.7, 3)
                if r == ROWS - 1:
                    q = q + nq * lift
                row.append(bm.verts.new(Vector(q * M)))
            rows.append(row)
        u0 = ci / slots
        for r in range(ROWS - 1):
            v0, v1 = 1 - r / (ROWS - 1), 1 - (r + 1) / (ROWS - 1)
            A, B = rows[r], rows[r + 1]
            for j in range(len(ACROSS) - 1):
                ua, ub = u0 + (j / 4) / slots, u0 + ((j + 1) / 4) / slots
                if len(B) == 1:
                    f = bm.faces.new((A[j], A[j + 1], B[0]))
                    uvf.append((f, [(ua, v0), (ub, v0), (u0 + 0.5 / slots, v1)]))
                else:
                    f = bm.faces.new((A[j], A[j + 1], B[j + 1], B[j]))
                    uvf.append((f, [(ua, v0), (ub, v0), (ub, v1), (ua, v1)]))
    uvl = bm.loops.layers.uv.new('UVMap')
    for f, uvs in uvf:
        for lp, uv in zip(f.loops, uvs):
            lp[uvl].uv = uv
    bm.normal_update()
    for f in bm.faces:
        c = np.array(f.calc_center_median()) / M
        if f.normal.dot(Vector(c - CENTRE)) < 0:
            f.normal_flip()
    me = bpy.data.meshes.new('male_v1_hair')
    bm.to_mesh(me)
    bm.free()
    return D.replace_object('male_v1_hair', me)


def paint_hair(P, N, A, UV):
    slots = len(CLUMPS)
    q = UV[:, 0] * slots
    k = (q - np.floor(q)) * 2 - 1                 # across a clump, -1 .. 1
    t = 1 - UV[:, 1]                              # root 0 .. tip 1
    streak = 0.6 * vnoise(np.stack([k * 9, t * 2.2, np.floor(q)], 1), 1.0, 31) + \
        0.4 * vnoise(np.stack([k * 22, t * 4, np.floor(q)], 1), 1.0, 32)
    c = np.tile(D.HAIR, (len(P), 1))
    ridge = (1 - smooth(np.abs(k), 0.15, 0.6)) * smooth(t, 0.1, 0.35) * (1 - smooth(t, 0.75, 0.97))
    c = mix(c, D.HAIR_LIGHT, np.clip(ridge * (0.3 + 0.55 * streak), 0, 0.8))
    c = mix(c, D.HAIR * 0.6, smooth(np.abs(k), 0.65, 1.0) * 0.6)      # the grooves between clumps
    c = mix(c, D.HAIR * 0.6, (1 - smooth(t, 0.0, 0.2)) * 0.5)         # roots tucked under the clump above
    c = mix(c, D.HAIR * 0.75, smooth(streak, 0.55, 0.8) * 0.3)
    c = mix(c, D.HAIR * 0.5, np.clip(A * 1.2, 0, 0.6))
    return c


# ---------------------------------------------------------------- painted face

STYLES = {
    'clean':  dict(bands=0, edge=0.0, jitter=0.0, stroke=0.0, hatch=0.0, dab=(14.0, 5.0),
                   line=1.0, chroma=0.85, mottle=0.03, catch=False),
    'medium': dict(bands=4, edge=0.05, jitter=0.08, stroke=0.04, hatch=0.0, dab=(13.0, 4.5),
                   line=1.15, chroma=1.0, mottle=0.05, catch=True),
    'heavy':  dict(bands=4, edge=0.012, jitter=0.12, stroke=0.07, hatch=0.10, dab=(10.0, 3.2),
                   line=1.35, chroma=1.25, mottle=0.06, catch=True),
    # V1 final (round 6): the user's pick 'clean', with the eyes and brows of eyes_v1
    'v1':     dict(bands=0, edge=0.0, jitter=0.0, stroke=0.0, hatch=0.0, dab=(14.0, 5.0),
                   line=1.0, chroma=0.85, mottle=0.03, catch=True),
}

# value ramp: lit -> mid -> warm turning edge -> shadow -> deep, sRGB
RAMP_V = [0.0, 0.3, 0.55, 0.8, 1.05]
RAMP_C = np.array([[0.88, 0.72, 0.59], [0.79, 0.61, 0.49], [0.72, 0.52, 0.42], [0.56, 0.40, 0.34], [0.41, 0.30, 0.29]])
BANDS = [0.17, 0.4, 0.62, 0.84]
SCLERA = np.array([0.76, 0.70, 0.63])
IRIS = np.array([0.30, 0.21, 0.14])
LINE = D.LINE
LIP_TINT = np.array([0.96, 0.82, 0.84])
FLUSH_TINT = np.array([1.02, 0.90, 0.88])
STUB_TINT = np.array([0.62, 0.60, 0.60])


def dabs(a, b, seed):
    """Brush dabs: a staggered grid of cells in (along, across) stroke units, one random value 0..1 per cell."""
    rb = np.floor(b)
    ra = np.floor(a + 0.5 * (rb % 2))
    n = ra.astype(np.int64) * 73856093 ^ rb.astype(np.int64) * 19349663 ^ seed * 83492791
    n = (n ^ (n >> 13)) * 1274126177
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def ramp(v):
    v = np.clip(v, 0, RAMP_V[-1])
    return np.stack([np.interp(v, RAMP_V, RAMP_C[:, k]) for k in range(3)], 1)


def blob(Pm, cx, cz, rx, rz, soft=0.7):
    return 1 - smooth(np.sqrt(((Pm[:, 0] - cx) / rx) ** 2 + ((Pm[:, 2] - cz) / rz) ** 2), 1 - soft, 1.0)


def seg(ax, z, p0, p1, width, soft):
    """1 within width/2 of the segment p0-p1 in the (|x|, z) plane (mm), soft edge."""
    p0, p1 = np.array(p0, float), np.array(p1, float)
    d = p1 - p0
    t = np.clip(((ax - p0[0]) * d[0] + (z - p0[1]) * d[1]) / (d @ d), 0, 1)
    return 1 - smooth(np.hypot(ax - p0[0] - t * d[0], z - p0[1] - t * d[1]), width / 2, width / 2 + soft)


def eye_lids(t):
    """Upper and lower lid heights (mm above EYE_Z) along the eye, t -1 inner corner .. 1 outer corner."""
    top = np.interp(t, [-1, -0.6, -0.2, 0.3, 0.7, 1], [0.0, 2.2, 3.1, 2.9, 1.8, -0.2])
    bot = np.interp(t, [-1, -0.5, 0.0, 0.5, 1], [0.0, -1.7, -2.3, -1.9, -0.3])
    return EYE_Z + top, EYE_Z + bot


def painter(style):
    S = STYLES[style]

    def fn(P, N, A, UV):
        x, y, z = P[:, 0], P[:, 1], P[:, 2]
        ax = np.abs(x)
        Pm = np.stack([ax, y, z], 1)
        ang = np.degrees(np.arctan2(x, -(y - Y0)))
        front = smooth(-y, 30, 70)
        face = smooth(-y, 50, 80)
        # brush direction: across the forehead, down and in over the cheeks
        su = np.radians(ang) * 80.0
        th = np.radians(np.interp(z, [1590, 1640, 1680, 1720], [-55, -40, -12, 4])) * np.sign(su) * smooth(np.abs(su), 4, 25)
        zc = z - 1640.0                                                         # rotate about the face, not z = 0
        al = su * np.cos(th) + zc * np.sin(th)
        ac = -su * np.sin(th) + zc * np.cos(th)
        wob = (vnoise(P, 6.0, 45) - 0.5) * 0.7                                 # dab edges not ruler-straight
        stroke = 0.6 * dabs(al / S['dab'][0] + wob, ac / S['dab'][1] - wob, 41) + \
            0.4 * dabs(al / (S['dab'][0] * 0.55) - wob, ac / (S['dab'][1] * 0.6) + wob, 42)
        # ---- form: painted planes only (the low-poly normals would bake their ring bands into the skin)
        wf = front * smooth(z, 1545, 1565) * (1 - smooth(z, 1700, 1712))      # brushwork on the face only
        jl = np.interp(np.abs(ang), [0, 40, 80, 100], [1553, 1560, 1585, 1595])
        v = 0.24 + 0.30 * smooth(np.abs(ang), 55, 100) + 0.06 * smooth(1660 - z, 0, 90) + 0.3 * A
        v = v + 0.40 * smooth(jl - z, -2, 14) * (1 - smooth(np.abs(ang), 100, 140))   # under the jaw
        # front plane / side plane: the turn runs down the temple, round the cheekbone, in under it to the chin
        xb = np.interp(z, [1560, 1585, 1605, 1625, 1645, 1675, 1705], [30, 38, 44, 50, 54, 57, 58])
        v = v + 0.18 * smooth(ax - xb, -4, 10) * front
        v = v + 0.10 * seg(ax, z, (52, 1628), (40, 1600), 14, 10) * front          # hollow under the cheekbone
        v = v - 0.07 * seg(ax, z, (32, 1646), (50, 1641), 10, 8) * front          # cheekbone light
        # eye sockets: a rounded box from the bridge to the outer corner, darkest under the inner brow
        sock = 1 - smooth((np.abs(ax - 27) / 19) ** 2.5 + (np.abs(z - 1662.5) / 11) ** 2.5, 0.3, 1.0)
        v = v + front * (0.32 * sock + 0.20 * blob(Pm, 15, 1667, 7, 7, 0.9))
        # nose: side planes graded from the ridge out to the cheek (a narrow dark band on each side read as a bar);
        # the shadow under it, the wing creases; the flat bridge facet catches the light, so the ridge is toned down
        nw = np.interp(z, [1612, 1618, 1632, 1656], [12, 13, 9, 6])
        v = v + 0.07 * smooth(ax, nw * 0.3, nw * 0.9) * (1 - smooth(ax, nw + 1, nw + 6)) * smooth(z, 1614, 1622) * \
            (1 - smooth(z, 1646, 1658)) * face
        v = v + front * (0.38 * blob(Pm, 0, 1611, 12, 3.2, 0.7) + 0.25 * blob(Pm, 11.5, 1617, 3.2, 6, 0.7))
        v = v + 0.05 * (1 - smooth(ax, 2.0, 5.5)) * smooth(z, 1630, 1638) * (1 - smooth(z, 1648, 1656)) * face
        # mouth: upper lip in shade, corners, the shadow under the lower lip; nasolabial lines faint
        v = v + front * (0.22 * blob(Pm, 0, 1584, 12, 3.6, 0.85) + 0.20 * blob(Pm, 19.5, 1593.5, 2.4, 2.4, 0.7)
                         + 0.06 * (ax < 20) * smooth(z, MOUTH_Z, MOUTH_Z + 1) * (1 - smooth(z, 1599, 1602)))
        tq = np.clip((1619 - z) / 29.0, 0, 1)
        nl = smooth(z, 1592, 1600) * (1 - smooth(z, 1612, 1619))
        v = v + face * nl * (0.12 * line_mask(ax - (16 + 9 * tq), 2.6, 2.0) - 0.04 * line_mask(ax - (19.5 + 9 * tq), 3, 2))
        v = v - front * (0.12 * blob(Pm, 0, 1698, 30, 14, 0.95)                  # forehead
                         + 0.14 * blob(Pm, 0, 1623, 4.5, 3.5, 0.7)               # nose tip
                         + 0.10 * blob(Pm, 0, 1568, 11, 6, 0.8)                  # chin
                         + 0.04 * blob(Pm, 0, 1589, 9, 2.0, 0.6))                # lower lip
        ear = (ax > 79.5) & (np.abs(y - 14) < 20) & (z > 1615) & (z < 1678)
        ei = (1 - smooth(np.sqrt(((y - 15) / 9) ** 2 + ((z - 1646) / 16) ** 2), 0.6, 1.0)) * (ax > 79.5)
        v = v + 0.30 * ei
        # ---- brush: value planes, stroke-broken edges, hatching in the shadows
        j = (stroke - 0.5) * 2 * wf
        if S['hatch']:
            hatch = (dabs(al / 18.0 + wob, ac / 2.2, 43) > 0.6) * 1.0
            v = v + S['hatch'] * hatch * smooth(v, 0.4, 0.7) * wf
        if S['bands']:
            e = max(S['edge'], 1e-3)
            vq = sum(smooth(v + j * S['jitter'], b - e, b + e) for b in BANDS)
            vq = np.interp(vq, [0, 1, 2, 3, 4], [0.05, 0.3, 0.55, 0.8, 1.02])
            v = mix(v[:, None], vq[:, None], 0.85)[:, 0]
        v = v + j * S['stroke']
        c = ramp(v)
        grey = c.mean(1, keepdims=True)
        c = grey + (c - grey) * S['chroma']
        c = c * (1 + S['mottle'] * (fbm(P, 25, 3) - 0.5) * 2)[:, None]
        # warm nose, cheeks and ears
        flush = np.maximum((1 - smooth(D.ellipse(Pm, 38, 1630, 20, 15), 0.3, 1.0)) * (ax > 18) * front,
                           (1 - smooth(D.ellipse(Pm, 0, 1623, 9, 10), 0.3, 1.0)) * front)
        c = c * mix(np.ones(3), FLUSH_TINT ** S['chroma'], np.clip(flush * 0.45 + ear * 0.5, 0, 1))
        # stubble: chin, upper lip, along the jaw; cheeks faint
        cheek_top = np.interp(ax, [0, 22, 40, 58, 72, 80], [1606, 1598, 1588, 1590, 1608, 1625])
        beard = smooth(cheek_top - z, -2, 6) * (1 - smooth(np.abs(ang), 95, 110))
        chin = (1 - smooth(D.ellipse(Pm, 0, 1570, 32, 20), 0.5, 1.1)) * front
        jaw = (1 - smooth(np.abs(z - jl), 4, 12)) * (1 - smooth(np.abs(ang), 95, 110))
        moust = (1 - smooth(D.ellipse(Pm, 0, 1605, 21, 4.4), 0.5, 1.0)) * front
        speck = 0.8 + 0.4 * vnoise(P, 1.4, 9) if style != 'heavy' else 0.5 + 0.9 * smooth(vnoise(P, 1.1, 9), 0.35, 0.7)
        b = np.maximum((beard * 0.45 + chin * 0.45 + jaw * 0.3) * smooth(z, 1532, 1556), moust * 0.45)
        lipzone = (1 - smooth(D.ellipse(Pm, 0, 1593, 20, 6.5), 0.7, 1.0)) * front
        b = np.clip(b * speck * (1 - lipzone), 0, 1)
        c = c * mix(np.ones(3), STUB_TINT, b * 0.55)
        # lips: the upper in shade, the lower lighter; the line between them
        mline = MOUTH_Z - 0.0008 * x * x
        ends = 1 - smooth(ax, MOUTH_HALF - 3, MOUTH_HALF + 0.5)
        up_top = 1600.5 - 0.006 * x * x + 0.8 * smooth(ax, 0, 4) * (1 - smooth(ax, 4, 9))
        upper = smooth(z, mline, mline + 0.6) * (1 - smooth(z, up_top - 0.6, up_top + 0.4)) * ends
        lower = (1 - smooth(D.ellipse(Pm, 0, 1590, 12.5, 3.6), 0.6, 1.0)) * (z < mline)
        c = c * mix(np.ones(3), LIP_TINT, np.clip((upper * 0.5 + lower * 0.35) * front, 0, 1))
        c = mix(c, LINE * 1.5, line_mask(z - mline, 0.9 * S['line'], 0.5) * ends * (1 - 0.4 * smooth(ax, 6, 17)) * front * 0.8)
        # nostrils
        c = mix(c, LINE * 1.5, blob(Pm, 6.5, 1614, 3.6, 1.9, 0.6) * front * 0.65)
        c = eyes_v1(c, x, y, z, ax) if style == 'v1' else eyes_r5(c, S, style, P, y, z, ax)
        return scalp(c, P, y, z, ax, ang)
    return fn


# V1 final eyes and brows (round 6). Rounds 2-5 drew the eye as a thin slit (5.4 x 21 mm, 4:1) outlined by a lash
# line, the iris 40% under the lids, dim whites, no light in the eye and a brow band parallel to the lid: every round
# moved those lines by a millimetre, so the faces stayed alike and the eyes stayed dead. Here the eye is an almond
# shape at adult proportions (23.5 x 7.5 mm, about 3:1), drawn as filled shapes that still read at game distance:
# a dark upper lid mass, the iris 40% of the width with a dark rim and a pupil, whites lit at the centre, one
# catchlight from the same world direction in both eyes. The brow is its own shape: heavy inner head, rising to an
# arch past the iris, a short tail; it does not follow the lid.
EYE_IN, EYE_OUT = 17.0, 40.5            # |x| of the inner / outer corner (mm)
EYE_TILT = 1.5                          # outer corner this much above the inner
GAZE = (0.0, 0.0)                       # iris offset (x, z) in mm, the same for both eyes; straight ahead
SCLERA_V1 = np.array([0.80, 0.76, 0.70])
IRIS_V1 = np.array([0.38, 0.27, 0.17])
IRIS_RIM = np.array([0.15, 0.11, 0.08])
CATCH = np.array([0.95, 0.93, 0.88])


def lids_v1(ax):
    """t (-1 inner .. 1 outer), the corner line and the upper / lower lid heights at |x|."""
    half = (EYE_OUT - EYE_IN) / 2
    t = np.clip((ax - EYE_IN) / half - 1, -1, 1)
    zc = EYE_Z - 1.0 + EYE_TILT * (t + 1) / 2
    top = zc + np.interp(t, [-1, -0.75, -0.4, -0.1, 0.25, 0.6, 0.85, 1], [0, 2.3, 3.8, 4.2, 3.9, 2.8, 1.4, 0])
    bot = zc + np.interp(t, [-1, -0.7, -0.3, 0.1, 0.45, 0.75, 1], [0, -1.6, -2.9, -3.4, -3.1, -2.0, 0])
    return t, zc, top, bot


def eyes_v1(c, x, y, z, ax):
    t, zc, top, bot = lids_v1(ax)
    fr = y < -45
    span = (ax > EYE_IN - 0.3) & (ax < EYE_OUT + 0.3) & fr
    inside = span & (z < top) & (z > bot)
    # the white: lit in the middle, grey into the corners, the lid's shadow along the top
    ce = mix(np.tile(SCLERA_V1, (len(c), 1)), SCLERA_V1 * 0.74, smooth(np.abs(t), 0.35, 0.95))
    ce = mix(ce, ce * 0.72, smooth(z, top - 1.8, top - 0.2))
    # iris + pupil; the same offset for both eyes so they look at one point
    xi = np.sign(x) * (EYE_IN + EYE_OUT) / 2 + GAZE[0]
    zi = EYE_Z - 0.4 + GAZE[1]
    r = np.hypot(x - xi, z - zi)
    ci = mix(IRIS_V1 * 1.15, IRIS_V1 * 0.85, smooth(z - zi, -3, 2))           # lit from above: lower iris lighter
    ci = mix(ci, IRIS_RIM, smooth(r, 3.4, 4.5))
    ci = mix(ci, LINE * 0.5, 1 - smooth(r, 1.5, 2.1))
    ci = mix(ci, ci * 0.7, smooth(z, top - 1.6, top - 0.2))
    ce = mix(ce, ci, 1 - smooth(r, 4.5, 4.9))
    hl = 1 - smooth(np.hypot(x - (xi - 1.5), z - (zi + 1.7)), 0.55, 1.0)  # x - 1.5 in both eyes: one light
    ce = mix(ce, CATCH, hl * 0.9)
    c = np.where(inside[:, None], ce, c)
    # upper lid: a dark mass, thin at the inner corner, heavier over the outer half, a short flick past the corner
    thick = np.interp(t, [-1, -0.5, 0.3, 0.9, 1], [0.4, 0.9, 1.5, 1.3, 0.9])
    d = z - top
    lash = smooth(d, -0.7, -0.15) * (1 - smooth(d, thick - 0.3, thick + 0.3)) * span
    fl = (ax >= EYE_OUT) & (ax < EYE_OUT + 2.6) & fr
    flick = fl * (1 - smooth(np.abs(z - (zc + 0.5 + 0.25 * (ax - EYE_OUT))), 0.2, 0.7)) * (1 - smooth(ax, EYE_OUT + 1.2, EYE_OUT + 2.6))
    c = mix(c, LINE * 1.05, np.clip(lash + flick, 0, 1) * 0.92)
    # the fold above the lid: a soft shadow, not a line
    crease = (1 - smooth(np.abs(d - 3.6 - 0.6 * (t > 0) * t), 0.4, 1.4)) * smooth(t, -0.85, -0.5) * (1 - smooth(t, 0.75, 1.0)) * fr
    crease = crease * (ax > EYE_IN - 1) * (ax < EYE_OUT + 1)
    c = mix(c, c * 0.8, crease * 0.55)
    # lower lid: a soft rim and a faint shadow below it; no line
    db = z - bot
    rim = (1 - smooth(np.abs(db + 0.5), 0.3, 0.9)) * (1 - smooth(np.abs(t), 0.6, 0.95)) * span
    c = mix(c, c * 0.84, rim * 0.6)
    under = (1 - smooth(np.abs(db + 3.6), 0.6, 2.2)) * (1 - smooth(np.abs(t), 0.3, 0.9)) * span
    c = mix(c, c * 0.92, under * 0.5)
    # brows: heavy inner head, the arch past the outer edge of the iris, a short tail
    s = np.clip((ax - 10.5) / 34.0, 0, 1)
    bb = np.interp(s, [0, 0.25, 0.62, 1], [1668.6, 1670.4, 1672.6, 1670.2])
    bt = np.interp(s, [0, 0.2, 0.62, 0.85, 1], [1674.6, 1675.4, 1676.6, 1674.4, 1671.0])
    dens = np.interp(s, [0, 0.12, 0.3, 1], [0.45, 0.8, 0.92, 0.78])
    brow = smooth(z, bb - 0.5, bb + 0.5) * (1 - smooth(z, bt - 0.6, bt + 0.4)) * smooth(ax, 10, 12) * \
        (1 - smooth(ax, 43.5, 45)) * (y < -40)
    return mix(c, D.HAIR * 1.05, brow * dens)


def eyes_r5(c, S, style, P, y, z, ax):
    """Round 5 eyes and brows, kept for the r5 sample sheet."""
    u = ax - EYE_X
    tt = np.clip(u / EYE_HALF, -1, 1)
    top, bot = eye_lids(tt)
    eyezone = (np.abs(u) < EYE_HALF) & (y < -50)
    inside = eyezone & (z < top) & (z > bot)
    ce = mix(np.tile(SCLERA, (len(P), 1)), SCLERA * 0.7, smooth(np.abs(tt), 0.4, 1.0))
    ce = mix(ce, ce * 0.72, smooth(z, top - 1.4, top))                        # the lid's shadow on the ball
    r = np.sqrt((u + 0.4) ** 2 + (z - EYE_Z - 0.2) ** 2)
    ci = mix(IRIS * 1.25, IRIS * 0.8, smooth(r, 1.5, 4.2))
    ci = mix(ci, LINE * 0.7, 1 - smooth(r, 1.4, 1.9))
    ci = mix(ci, ci * 0.7, smooth(z, top - 1.4, top))
    ce = np.where((r < 4.4)[:, None], ci, ce)
    if S['catch']:
        hl = 1 - smooth(np.sqrt((u + 1.6) ** 2 + (z - EYE_Z - 1.2) ** 2), 0.5, 0.9)
        ce = mix(ce, np.array([0.86, 0.84, 0.80]), hl * 0.8)
    c = np.where(inside[:, None], ce, c)
    lw = 1.25 * S['line']
    tail = (u > EYE_HALF - 1) * (u < EYE_HALF + 2.0)
    lid = line_mask(z - top - lw * 0.3, lw, 0.5) * ((np.abs(u) < EYE_HALF + 0.5) | tail) * (y < -50)
    c = mix(c, LINE * 1.15, lid * 0.85)
    c = mix(c, c * 0.8, line_mask(z - top - 3.4, 1.2, 0.9) * (np.abs(u + 1) < EYE_HALF - 1) * (y < -50) * 0.6)
    lowl = line_mask(z - bot + 0.4, 0.8, 0.5) * (np.abs(u) < EYE_HALF - 1) * (y < -50)
    c = mix(c, c * 0.78, lowl * (0.35 if style == 'clean' else 0.5))
    # brows: a resting brow, thick at the inner end, tapering out
    bu = np.clip((ax - 11) / 32.0, 0, 1)
    bc = 1672.0 + 2.4 * np.sin(bu * math.pi * 0.9) - 0.4 * bu
    bw = 5.2 - 2.4 * bu
    bend = smooth(ax, 10, 12.5) * (1 - smooth(ax, 41, 44)) * (y < -40)
    bdir = 0.7 * vnoise(np.stack([(ax * 0.5 + z * 0.87) / 0.9, (ax * 0.87 - z * 0.5) / 4.0, y / 30], 1), 1.0, 44)
    soft = {'clean': 0.9, 'medium': 0.7, 'heavy': 0.45}[style]
    brow = (1 - smooth(np.abs(z - bc), bw / 2 - soft * 0.5, bw / 2 + soft)) * bend
    if style != 'clean':
        brow = brow * np.clip(0.75 + (bdir - 0.35) * (0.8 if style == 'medium' else 1.6), 0, 1)
    return mix(c, D.HAIR * 1.1, np.clip(brow * 0.9, 0, 1))


def scalp(c, P, y, z, ax, ang):
    """Scalp under the hair, the clippered taper and the sideburns (as round 4)."""
    hz = hairline_z(ang)
    by_ear = (ax > 79.5) * (np.abs(y - 14) < 18) * (z < 1676)
    c = mix(c, D.HAIR * 0.6, smooth(z - hz, -4, 2) * (1 - by_ear))       # dark under the clumps: gaps read as shadow
    taper = smooth(z - (hz - 22), 0, 20) * (1 - smooth(z - hz, -2, 2)) * smooth(np.abs(ang), 55, 80) * (1 - by_ear)
    c = mix(c, mix(D.SKIN_SHADE, D.HAIR, 0.75), np.clip(taper * 0.85 * (0.75 + 0.5 * vnoise(P, 1.2, 13)), 0, 1))
    sbz = np.interp(np.abs(ang), [70, 74, 80, 84, 88], [1700, 1648, 1640, 1652, 1700])
    sb = smooth(z - sbz, -2, 3) * smooth(np.abs(ang), 69, 73) * (1 - smooth(np.abs(ang), 84, 88)) * (z < 1690)
    return mix(c, D.HAIR * 0.95, sb * 0.7 * (1 - by_ear))


# ---------------------------------------------------------------- build, paint, render

def archive_face():
    """The round 2-4 face part stays in the file, hidden, out of the build."""
    ob = bpy.data.objects.get('male_v1_face')
    if ob is None:
        return
    coll = bpy.data.collections.get('Male_V1_archive')
    if coll is None:
        coll = bpy.data.collections.new('Male_V1_archive')
        bpy.context.scene.collection.children.link(coll)
    for c in list(ob.users_collection):
        c.objects.unlink(ob)
    coll.objects.link(ob)
    ob.hide_render = ob.hide_viewport = True


def head_scale(k=D.HEAD_SCALE):
    c = np.array([0.0, 12.0, 1550.0])
    for name in ('male_v1_head', 'male_v1_hair'):
        ob = bpy.data.objects[name]
        if not ob.data.shape_keys:
            ob.shape_key_add(name='Basis')
        key = ob.data.shape_keys.key_blocks.get('stylise') or ob.shape_key_add(name='stylise')
        for i, v in enumerate(ob.data.vertices):
            p = np.array(v.co) / M
            w = float(smooth(p[2], 1550, 1600))
            key.data[i].co = Vector((c + (p - c) * (1 + (k - 1) * w)) * M)
        key.value = 1.0


def soften_brow():
    """No hard edge along the brow ring: across the whole face it read as a band at eye height; jaw and nose keep
    theirs (36 deg)."""
    me = bpy.data.objects['male_v1_head'].data
    sharp = me.attributes.get('sharp_edge')
    if sharp is None:
        return
    for e in me.edges:
        a, b = (me.vertices[i].co / M for i in e.vertices)
        mid = (a + b) / 2
        if 1656 < mid.z < 1702 and abs(mid.x) > 6 and mid.y < 0:
            sharp.data[e.index].value = False


def build():
    """Uncut round 4 head + clump hair; head UV; hair painted. Returns the BVH for painting the head."""
    import build_survivor_male_v1 as B
    archive_face()
    B.head()
    stage_hair()
    uv_head(bpy.data.objects['male_v1_head'])
    bvh = D.character_bvh(NAMES)
    D.paint(bpy.data.objects['male_v1_hair'], 256, 128, paint_hair, bvh, 'm1_hair')
    head_scale()
    for name in ('male_v1_head', 'male_v1_body'):
        me = bpy.data.objects[name].data
        me.shade_smooth()
        me.set_sharp_from_angle(angle=math.radians(36))
    soften_brow()
    for p in bpy.data.objects['male_v1_hair'].data.polygons:
        p.use_smooth = False
    return bvh


def paint_style(style, bvh):
    return D.paint(bpy.data.objects['male_v1_head'], *HEAD_TEX, painter(style), bvh, 'm1_head_%s' % style)


def render(prefix, views, size=600, span=0.30, focus=(0, 0, 1.665), out_dir=None):
    sys.path.insert(0, D.H1)
    import render_views as R
    sc = bpy.context.scene
    sc.display.shading.background_color = (0.30, 0.31, 0.32)
    sc.display.shading.show_specular_highlight = False
    out = R.render(NAMES, views, out_dir or D.RENDERS + '/r5', prefix, size=size, focus=focus, span=span)
    sc.display.shading.color_type = 'TEXTURE'
    return out


VIEWS = ['front', ('q45', (-0.72, -0.69, 0.08), (0, 0, 1)), 'right']


def game_view(prefix, shots, out_dir=None):
    """Whole character as it shows in game: a 480 x 540 window cut from a 1080p screen at Minecraft's 70 deg vertical
    FOV, camera at eye height. shots: (name, direction from the character, distance m)."""
    sys.path.insert(0, D.H1)
    import render_views as R
    sc = bpy.context.scene
    R.setup(540)
    sc.render.resolution_x, sc.render.resolution_y = 480, 540
    sc.display.shading.background_color = (0.30, 0.31, 0.32)
    sc.display.shading.show_specular_highlight = False
    keep = set(NAMES)
    hidden = {}
    for ob in sc.objects:
        if ob.type in ('MESH', 'ARMATURE', 'EMPTY'):
            hidden[ob.name] = ob.hide_render
            ob.hide_render = ob.name not in keep
    cam = R.camera()
    cam.data.type = 'PERSP'
    cam.data.sensor_fit = 'VERTICAL'
    cam.data.angle_y = 2 * math.atan(math.tan(math.radians(35)) * 540 / 1080)
    sc.camera = cam
    out_dir = out_dir or D.RENDERS + '/r6'
    os.makedirs(out_dir, exist_ok=True)
    out = []
    aim = Vector((0, 0, 0.95))
    for name, d, dist in shots:
        d = Vector(d).normalized()
        cam.location = Vector((d.x * dist, d.y * dist, 1.62))
        cam.rotation_mode = 'QUATERNION'
        cam.rotation_quaternion = (aim - cam.location).to_track_quat('-Z', 'Y')
        sc.render.filepath = os.path.join(out_dir, '%s_%s.png' % (prefix, name))
        bpy.ops.render.render(write_still=True)
        out.append(sc.render.filepath)
    cam.data.type = 'ORTHO'
    for n, h in hidden.items():
        sc.objects[n].hide_render = h
    return out


GAME_SHOTS = [('front_4m', (0, -1, 0), 4.0), ('q45_4m', (-0.7, -0.7, 0), 4.0),
              ('back_4m', (0, 1, 0), 4.0), ('front_10m', (0, -1, 0), 10.0)]


def final_v1():
    """Round 6: the one V1 face (clean + eyes_v1), close-ups and game-distance previews in renders/r6."""
    bvh = build()
    paint_style('v1', bvh)
    out = {'face': render('m1_face_v1', VIEWS, out_dir=D.RENDERS + '/r6')}
    out['game'] = game_view('m1_v1_game', GAME_SHOTS)
    return out


def samples(styles=('clean', 'medium', 'heavy')):
    bvh = build()
    out = {}
    for st in styles:
        paint_style(st, bvh)
        out[st] = render('m1_face_%s' % st, VIEWS)
    out['hair'] = render('m1_hair_clumps', ['back', ('top', (0.0, -0.35, 1.0), (0, 1, 0))])
    return out
