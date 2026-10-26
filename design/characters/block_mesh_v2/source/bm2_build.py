"""AFL Block-Mesh V2 prototypes A-D (2026-10-25, docs/dev/block_mesh_v2_prototypes.md): geometry, UV, sample skin, glTF.

Every body part is a "tube": rectangular rings along an axis (y down for the body and limbs, z for the foot), each ring with
its bone weights; neighbouring rings make the side faces, the end rings the caps. A joint is one ring (A, the C-min way the
current skeleton draws) or several rings with stepped weights (B, C, D): the bend is spread over them.

Only the arms differ between the prototypes; head, torso, legs and feet are the same (bm2_rig.py proportions):
  A  vanilla-thick arms 4 x 4 px, one ring per joint.
  B  slimmer box arms: upper 3 x 3, forearm 2.6, hand 2 x 2.6; two rings per joint.
  C  square frustums: 3.4 at the shoulder to 2.2 at the wrist, the hand a tapered wedge; three rings at the elbow.
  D  B's arms with a palm and five three-segment fingers (52 bones).

UV: each tube unwrapped as one strip (front | +x side | back | -x side, the length down) plus two caps, shelf-packed;
2 texels per model px (a 64 x 64 vanilla skin is 1). The sample skin is painted per texel from the body coordinates, so
the four prototypes wear the same outfit.

Usage: python -I bm2_build.py <out dir>   (models/<proto>.gltf + .bin + _skin.png, stats.json)
"""
import json
import math
import os
import struct
import sys
import zlib

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bm2_rig as R  # noqa: E402

TEX = 2           # texels per model px
# Later variants (bm2_game.py: B2, user 2026-10-25 round 2) register their own tubes, texel density, atlas packing and painter
# here; A-D keep the defaults above.
EXTRA = {}        # proto -> dict(tubes=fn, tex=texels per px, align=, pad=, texture=fn(m))


# ---------------------------------------------------------------- tubes
class Ring:
    def __init__(self, t, c, h, w):
        self.t, self.c, self.h, self.w = t, c, h, w     # position on the axis, centre (2), half sizes (2), {bone: weight}


class Tube:
    def __init__(self, name, axis, rings, paint, side=None, caps=(True, True)):
        self.name, self.axis, self.rings, self.paint, self.side, self.caps = name, axis, rings, paint, side, caps

    def point(self, ring, a, b):
        """A ring corner (a, b = -1 / +1) in px, Bedrock axes."""
        if self.axis == 'y':
            return np.array([ring.c[0] + a * ring.h[0], ring.t, ring.c[1] + b * ring.h[1]])
        return np.array([ring.c[0] + a * ring.h[0], ring.c[1] + b * ring.h[1], ring.t])


CORNERS = [(-1, -1), (1, -1), (1, 1), (-1, 1)]
# y tube sides: c0-c1 at -z (front), c1-c2 at +x, c2-c3 at +z (back), c3-c0 at -x
SIDES_Y = ['front', 'xpos', 'back', 'xneg']
# z tube (the foot, its rings across x / y): c0-c1 at -y (sole), c1-c2 at +x, c2-c3 at +y (top), c3-c0 at -x
SIDES_Z = ['bottom', 'xpos', 'top', 'xneg']


def lerp_w(a, b, t):
    out = {}
    for k in set(a) | set(b):
        out[k] = a.get(k, 0) * (1 - t) + b.get(k, 0) * t
    return {k: v for k, v in out.items() if v > 1e-6}


def joint_rings(t, c0, h0, c1, h1, upper, lower, rings, span):
    """Rings across a joint at t: rings = 1 (one ring at the joint, half and half) or more, spread over +-span px."""
    out = []
    for i in range(rings):
        f = 0.5 if rings == 1 else i / (rings - 1)
        tt = t + span - 2 * span * f if rings > 1 else t
        w = 0.5 if rings == 1 else (f * (rings - 1) + 0.5) / rings
        c = tuple(c0[k] + (c1[k] - c0[k]) * f for k in range(2))
        h = tuple(h0[k] + (h1[k] - h0[k]) * f for k in range(2))
        out.append(Ring(tt, c, h, {upper: 1 - w, lower: w}))
    return out


# ---------------------------------------------------------------- the shared body
def head_tube():
    H = R.HEAD
    return Tube('head', 'y', [Ring(H['base'] + H['h'], (0, 0), (4, 4), {'head': 1}), Ring(H['base'], (0, 0), (4, 4), {'head': 1})], 'head')


def torso_tube():
    T = R.TORSO
    h = (4, 2)
    rings = [Ring(24, (0, 0), h, {'chest': 1}), Ring(21.0, (0, 0), h, {'chest': 0.8, 'spine': 0.2}),
             Ring(20.0, (0, 0), h, {'chest': 0.3, 'spine': 0.7}), Ring(18.6, (0, 0), h, {'spine': 0.8, 'pelvis': 0.2}),
             Ring(17.4, (0, 0), h, {'spine': 0.25, 'pelvis': 0.75}), Ring(T['hip'], (0, 0), h, {'pelvis': 1})]
    return Tube('torso', 'y', rings, 'torso')


def leg_tubes():
    L = R.LEG
    out = []
    for s, sx in (('r', -1), ('l', 1)):
        c = (sx * L['x'], 0)
        h = (2, 2)
        th, sh, ft = 'thigh_' + s, 'shin_' + s, 'foot_' + s
        rings = [Ring(L['hip'], c, h, {'pelvis': 0.35, th: 0.65}), Ring(L['hip'] - 0.8, c, h, {th: 1})]
        rings += joint_rings(L['knee'], c, h, c, h, th, sh, 2, 0.55)
        rings += [Ring(L['ankle'] + 0.6, c, h, {sh: 1}), Ring(L['ankle'], c, h, {sh: 0.45, ft: 0.55})]
        out.append(Tube('leg_' + s, 'y', rings, 'leg', s, caps=(False, True)))
        # the foot: heel / instep on the foot bone, the toe box on the toes (z tube, front at -z)
        F = R.FOOT
        fh = F['h']
        out.append(Tube('foot_' + s, 'z', [Ring(F['back'], (sx * L['x'], fh / 2), (2, fh / 2), {ft: 1}),
                                           Ring(F['ball'] + 0.25, (sx * L['x'], fh / 2), (2, fh / 2), {ft: 1}),
                                           Ring(F['ball'] - 0.25, (sx * L['x'], 0.6), (2, 0.6), {ft: 0.3, 'toes_' + s: 0.7}),
                                           Ring(F['front'], (sx * L['x'], 0.55), (2, 0.55), {'toes_' + s: 1})], 'foot', s))
    return out


# ---------------------------------------------------------------- arms
ARMS = {
    # widths (x, z) at shoulder, elbow, wrist, hand tip; rings per joint; the arm's centre x
    'A': dict(sh=(2, 2), el=(2, 2), wr=(2, 2), tip=(2, 2), elbow_rings=1, wrist_rings=1, cx=6.0, span=0.0),
    'B': dict(sh=(1.5, 1.5), el=(1.4, 1.4), wr=(1.3, 1.3), hand=(1.0, 1.3), tip=(1.0, 1.3), elbow_rings=2, wrist_rings=2, cx=5.5, span=0.5),
    'C': dict(sh=(1.7, 1.7), el=(1.45, 1.45), wr=(1.1, 1.1), hand=(0.8, 1.3), tip=(0.65, 1.1), elbow_rings=3, wrist_rings=2, cx=5.5, span=0.7),
    'D': dict(sh=(1.5, 1.5), el=(1.4, 1.4), wr=(1.3, 1.3), hand=(0.75, 1.3), tip=(0.65, 1.25), elbow_rings=2, wrist_rings=2, cx=5.5, span=0.5),
}


def arm_tubes(proto):
    a = ARMS[proto]
    A = R.ARM
    out = []
    for s, sx in (('r', -1), ('l', 1)):
        u, f, h = 'upper_arm_' + s, 'forearm_' + s, 'hand_' + s
        c = (sx * a['cx'], 0)
        rings = [Ring(A['top'], c, a['sh'], {u: 1})]
        if a['elbow_rings'] == 1:
            rings += [Ring(A['elbow'], c, a['el'], {u: 0.5, f: 0.5})]
        else:
            rings += joint_rings(A['elbow'], c, a['el'], c, a['el'], u, f, a['elbow_rings'], a['span'])
        hand = a.get('hand', a['wr'])
        if a['wrist_rings'] == 1:
            rings += [Ring(A['wrist'], c, a['wr'], {f: 0.5, h: 0.5})]
        else:
            rings += joint_rings(A['wrist'], c, a['wr'], c, hand, f, h, a['wrist_rings'], 0.35)
        if proto == 'D':
            rings += [Ring(A['wrist'] - 1.7, c, a['tip'], {h: 1})]          # the palm to the knuckles
        else:
            rings += [Ring(A['tip'], c, a['tip'], {h: 1})]
        out.append(Tube('arm_' + s, 'y', rings, 'arm', s))
    return out


def finger_tubes():
    """D: each finger a tube through its three bones (a ring at each knuckle), the thumb from the palm's heel."""
    out = []
    bones = {n: p for n, _, p in R.finger_bones()}
    for s, sx in (('r', 1), ('l', -1)):
        for f, d in R.FINGERS.items():
            names = ['%s_%02d_%s' % (f, i + 1, s) for i in range(3)]
            pts = [np.array(bones[n]) for n in names]
            last = pts[2] + (pts[2] - pts[1]) / np.linalg.norm(pts[2] - pts[1]) * d['seg'][2]
            hw = d['w'] / 2
            rings = []
            if f == 'thumb':
                # along its own line (down and forward): rings in y with a z offset following the bones
                chain = pts + [last]
                for i, p in enumerate(chain):
                    w = {names[min(i, 2)]: 1.0} if i == 0 else ({names[i - 1]: 0.5, names[i]: 0.5} if i < 3 else {names[2]: 1.0})
                    rings.append(Ring(float(p[1]), (float(p[0]), float(p[2])), (hw * 0.95, hw), w))
            else:
                chain = pts + [last]
                for i, p in enumerate(chain):
                    w = {names[0]: 0.6, 'hand_' + s: 0.4} if i == 0 else ({names[i - 1]: 0.5, names[i]: 0.5} if i < 3 else {names[2]: 1.0})
                    rings.append(Ring(float(p[1]), (float(p[0]), float(p[2])), (hw * 0.9, hw), w))
            out.append(Tube(f + '_' + s, 'y', rings, 'finger', s))
    return out


def prototype(proto):
    if proto in EXTRA:
        return EXTRA[proto]['tubes']()
    tubes = [head_tube(), torso_tube()] + leg_tubes() + arm_tubes(proto)
    if proto == 'D':
        tubes += finger_tubes()
    return tubes


# ---------------------------------------------------------------- mesh, UV
def strip_size(tube):
    """The unwrapped strip: widths of the four sides (px, their widest ring), the length (px), the two caps."""
    ws = [0, 0, 0, 0]
    for r in tube.rings:
        ws = [max(ws[0], 2 * r.h[0]), max(ws[1], 2 * r.h[1]), max(ws[2], 2 * r.h[0]), max(ws[3], 2 * r.h[1])]
    length = sum(abs(tube.rings[i + 1].t - tube.rings[i].t) for i in range(len(tube.rings) - 1))
    if tube.name.startswith(('thumb', 'index', 'middle', 'ring', 'pinky')):
        length = sum(np.linalg.norm(np.subtract(ringpos(tube, i + 1), ringpos(tube, i))) for i in range(len(tube.rings) - 1))
    first, last = tube.rings[0], tube.rings[-1]
    return ws, length, (2 * first.h[0], 2 * first.h[1]), (2 * last.h[0], 2 * last.h[1])


def ringpos(tube, i):
    r = tube.rings[i]
    return (r.c[0], r.t, r.c[1]) if tube.axis == 'y' else (r.c[0], r.c[1], r.t)


def pack(rects, align=1, pad=1):
    """Shelf packing of (key, w, h) in texels; returns {key: (x, y)}, atlas size (power of two, square). align: every
    region starts on a multiple of it (a mip level's block), pad: the gutter after each."""
    up = lambda n: int(math.ceil(n / align)) * align
    for size in (64, 128, 256, 512):
        x = y = shelf = 0
        out = {}
        ok = True
        for key, w, h in sorted(rects, key=lambda r: -r[2]):
            w, h = up(int(math.ceil(w - 1e-6)) + pad), up(int(math.ceil(h - 1e-6)) + pad)
            if x + w > size:
                x, y, shelf = 0, y + shelf, 0
            if y + h > size or w > size:
                ok = False
                break
            out[key] = (x, y)
            x += w
            shelf = max(shelf, h)
        if ok:
            return out, size
    raise ValueError('atlas too big')


def build(proto):
    TEX = EXTRA.get(proto, {}).get('tex', globals()['TEX'])
    tubes = prototype(proto)
    rects = []
    for t in tubes:
        ws, length, c0, c1 = strip_size(t)
        rects.append(((t.name, 'strip'), sum(ws) * TEX, length * TEX))
        if t.caps[0]:
            rects.append(((t.name, 'cap0'), c0[0] * TEX, c0[1] * TEX))
        if t.caps[1]:
            rects.append(((t.name, 'cap1'), c1[0] * TEX, c1[1] * TEX))
    place, size = pack(rects, EXTRA.get(proto, {}).get('align', 1), EXTRA.get(proto, {}).get('pad', 1))
    V, UV, W, F, FN, REG = [], [], [], [], [], []   # vertices (px), uv (0..1), weights, triangles, face names, uv regions
    regions = []
    for t in tubes:
        ws, length, c0, c1 = strip_size(t)
        sides = SIDES_Y if t.axis == 'y' else SIDES_Z
        sx, sy = place[(t.name, 'strip')]
        offs = np.concatenate([[0], np.cumsum(ws)])
        vpos = 0.0
        for i in range(len(t.rings) - 1):
            r0, r1 = t.rings[i], t.rings[i + 1]
            seg = abs(r1.t - r0.t) if not t.name.startswith(('thumb', 'index', 'middle', 'ring', 'pinky')) else \
                float(np.linalg.norm(np.subtract(ringpos(t, i + 1), ringpos(t, i))))
            for k in range(4):
                a0, a1 = CORNERS[k], CORNERS[(k + 1) % 4]
                p = [t.point(r0, *a0), t.point(r0, *a1), t.point(r1, *a1), t.point(r1, *a0)]
                wt = [r0.w, r0.w, r1.w, r1.w]
                u0, u1 = sx + offs[k] * TEX, sx + offs[k + 1] * TEX
                v0, v1 = sy + vpos * TEX, sy + (vpos + seg) * TEX
                uv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
                quad(V, UV, W, F, FN, p, uv, wt, size, (t, sides[k]))
            vpos += seg
        regions.append((t, place, ws, length))
        for ci, (cap, ring) in enumerate(((c0, t.rings[0]), (c1, t.rings[-1]))):
            if not t.caps[ci]:
                continue
            cx, cy = place[(t.name, 'cap%d' % ci)]
            p = [t.point(ring, *c) for c in CORNERS]
            if ci == 0:
                p = p[::-1]
            uv = [(cx + (0 if c[0] < 0 else cap[0] * TEX), cy + (0 if c[1] < 0 else cap[1] * TEX)) for c in CORNERS]
            if ci == 0:
                uv = uv[::-1]
            quad(V, UV, W, F, FN, p, uv, [ring.w] * 4, size, (t, 'cap0' if ci == 0 else 'cap1'))
    return dict(proto=proto, tubes=tubes, V=np.array(V), UV=np.array(UV), W=W, F=np.array(F), FN=FN, size=size, place=place, tex=TEX)


def quad(V, UV, W, F, FN, p, uv, wt, size, tag):
    """One flat quad (its own four vertices), wound so the normal points out of the tube."""
    n = np.cross(p[1] - p[0], p[3] - p[0])
    base = len(V)
    for q, t, w in zip(p, uv, wt):
        V.append(q)
        UV.append((t[0] / size, t[1] / size))
        W.append(w)
    F.append((base, base + 1, base + 2))
    F.append((base, base + 2, base + 3))
    FN.append(tag)
    FN.append(tag)


def fix_winding(m):
    """Every triangle's normal out of its tube (away from the tube's axis)."""
    F = m['F']
    for i, (tri, (tube, side)) in enumerate(zip(F, m['FN'])):
        p = m['V'][tri]
        n = np.cross(p[1] - p[0], p[2] - p[0])
        c = p.mean(0)
        ax = np.array(ringpos(tube, 0)) + np.array(ringpos(tube, len(tube.rings) - 1))
        ax = ax / 2
        if side in ('cap0', 'cap1'):
            out = c - (np.array(ringpos(tube, 1)) if side == 'cap0' else np.array(ringpos(tube, len(tube.rings) - 2)))
        else:
            # the nearest point of the tube's centre line
            pts = np.array([ringpos(tube, k) for k in range(len(tube.rings))])
            out = c - pts[np.argmin(np.linalg.norm(pts - c, axis=1))]
        if np.dot(n, out) < 0:
            F[i] = tri[[0, 2, 1]]
    return m


# ---------------------------------------------------------------- the sample skin (one outfit for A-D)
PAL = dict(skin=(196, 140, 104), skin_dk=(170, 116, 86), hair=(60, 42, 31), hair_dk=(44, 31, 24), eye_w=(236, 234, 228),
           iris=(64, 86, 112), brow=(56, 40, 30), mouth=(146, 88, 74), tee=(64, 67, 72), jacket=(124, 92, 60),
           jacket_dk=(96, 70, 46), jacket_lt=(146, 110, 74), jeans=(56, 76, 118), jeans_dk=(42, 58, 92), boot=(82, 58, 41),
           boot_dk=(54, 38, 28), sole=(38, 32, 29), belt=(42, 32, 26), buckle=(168, 158, 122), lace=(150, 132, 108))


def noise(x, y, k):
    h = (int(x) * 73856093) ^ (int(y) * 19349663) ^ (k * 83492791)
    return ((h & 0xffff) / 65535.0 - 0.5) * 0.09


def paint(tube, side, u, v, body):
    """Colour at a point of a tube's surface. u px across the face (from its first corner), v px down the tube, body = the
    point's Bedrock position (px)."""
    x, y, z = body
    kind = tube.paint
    s = tube.side
    out_side = (side == 'xneg') if s == 'r' else (side == 'xpos')
    if kind == 'head':
        if side == 'cap0':
            return PAL['hair']
        if side == 'cap1':
            return PAL['skin_dk']
        if side == 'front':
            if y > 30.5 or (y > 29.6 and abs(x) > 1.2) or (y > 29 and abs(x) > 3.3):
                return PAL['hair'] if y > 30.5 else PAL['hair_dk']
            if 28.4 < y < 28.9 and 0.9 < abs(x) < 3.1:
                return PAL['brow']
            if 27.1 < y < 28.0 and 1.0 < abs(x) < 3.0:
                return PAL['iris'] if 1.0 < abs(x) < 2.0 else PAL['eye_w']
            if 25.4 < y < 25.9 and abs(x) < 1.1:
                return PAL['mouth']
            if 26.2 < y < 27.0 and abs(x) < 0.6:
                return PAL['skin_dk']
            return PAL['skin']
        if side == 'back':
            return PAL['hair'] if y > 25.6 else PAL['hair_dk'] if y > 25.0 else PAL['skin']
        # the sides: hair above the ear, the ear
        if y > 29.0 or (y > 26.0 and z > 1.0):
            return PAL['hair']
        if 26.0 < y < 28.0 and -0.5 < z < 0.6:
            return PAL['skin_dk']
        return PAL['skin']
    if kind == 'torso':
        if side == 'cap0':
            return PAL['jacket_dk'] if abs(x) > 1.4 else PAL['skin']
        if side == 'cap1':
            return PAL['jeans_dk']
        if y < 15.7:
            if 15.05 < y and side == 'front' and abs(x) < 0.7:
                return PAL['buckle']
            return PAL['belt'] if y > 15.05 else PAL['jeans']
        if side == 'front':
            if abs(x) < 1.0:
                return PAL['skin'] if y > 23.2 and abs(x) < 0.7 else PAL['tee']
            if y > 23.2 and abs(x) < 2.2:
                return PAL['jacket_dk']
            if 20.2 < y < 21.6 and 1.8 < abs(x) < 3.5:
                return PAL['jacket_dk'] if y > 21.3 else PAL['jacket_lt']
            if 16.6 < y < 18.4 and 1.6 < abs(x) < 3.6:
                return PAL['jacket_dk'] if y > 18.1 else PAL['jacket_lt']
            if 15.7 < y < 16.2:
                return PAL['jacket_dk']
            return PAL['jacket']
        if 15.7 < y < 16.2:
            return PAL['jacket_dk']
        if side == 'back' and 23.0 < y < 23.4:
            return PAL['jacket_dk']
        return PAL['jacket']
    if kind == 'arm':
        A = R.ARM
        cuff = A['wrist'] + 0.25
        if y < cuff - 0.6 or side == 'cap1':
            return PAL['skin_dk'] if side == 'cap1' else PAL['skin']
        if y < cuff:
            return PAL['jacket_dk']
        if side == 'cap0':
            return PAL['jacket_dk']
        if out_side and 20.0 < y < 23.0 and 0.25 < (u % 10) < 0.6:
            return PAL['jacket_dk']
        return PAL['jacket']
    if kind == 'finger':
        return PAL['skin_dk'] if side in ('cap1',) else PAL['skin']
    if kind == 'leg':
        if y < 3.2:
            if y > 2.8:
                return PAL['boot_dk']
            if side == 'front' and abs((x - (-2 if s == 'r' else 2))) < 0.6 and int(y * 2) % 2 == 0:
                return PAL['lace']
            return PAL['boot']
        if y > 14.2 and side == 'front' and 0.4 < abs(abs(x) - 2) < 1.6:
            return PAL['jeans_dk']
        if out_side and 0.4 < (u % 4) < 0.6:
            return PAL['jeans_dk']
        return PAL['jeans']
    if kind == 'foot':
        if side == 'bottom':
            return PAL['sole']
        if y < 0.35:
            return PAL['sole']
        if z < -2.6:
            return PAL['boot_dk']
        return PAL['boot']
    return (255, 0, 255)


def texture(m):
    """Paint the atlas: each texel of each face region, from the face's corner positions (bilinear)."""
    if m['proto'] in EXTRA:
        return EXTRA[m['proto']]['texture'](m)
    size = m['size']
    img = np.full((size, size, 3), 0, np.uint8)
    V, UV = m['V'], m['UV']
    for qi in range(0, len(m['F']), 2):
        tube, side = m['FN'][qi]
        tri = m['F'][qi]
        base = min(m['F'][qi].min(), m['F'][qi + 1].min())
        p = V[base:base + 4]
        uv = UV[base:base + 4] * size
        u0, v0 = uv.min(0)
        u1, v1 = uv.max(0)
        for ty in range(int(math.floor(v0)), int(math.ceil(v1))):
            for tx in range(int(math.floor(u0)), int(math.ceil(u1))):
                fu = (tx + 0.5 - u0) / max(u1 - u0, 1e-6)
                fv = (ty + 0.5 - v0) / max(v1 - v0, 1e-6)
                # the quad's corners in uv order: (u0,v0) (u1,v0) (u1,v1) (u0,v1) for the sides; caps alike
                c = corner_order(uv, u0, v0, u1, v1)
                q = (p[c[0]] * (1 - fu) * (1 - fv) + p[c[1]] * fu * (1 - fv) + p[c[2]] * fu * fv + p[c[3]] * (1 - fu) * fv)
                col = np.array(paint(tube, side, fu * (u1 - u0) / TEX, fv * (v1 - v0) / TEX, q), float)
                col *= 1 + noise(tx, ty, len(tube.name))
                if 0 <= ty < size and 0 <= tx < size:
                    img[ty, tx] = np.clip(col, 0, 255)
    return img


def corner_order(uv, u0, v0, u1, v1):
    want = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    return [int(np.argmin([abs(a[0] - w[0]) + abs(a[1] - w[1]) for a in uv])) for w in want]


# ---------------------------------------------------------------- glTF
def png_bytes(img):
    h, w, _ = img.shape
    raw = b''.join(b'\x00' + img[y].tobytes() for y in range(h))

    def chunk(t, d):
        return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + \
        chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')


def to_gltf(p):
    """Bedrock px (front -z) -> glTF metres (front +z): turned half round y."""
    return np.array([-p[0], p[1], -p[2]]) * R.PX


def write_gltf(m, out_dir):
    proto = m['proto']
    bones = R.skeleton(proto == 'D')
    names = [b[0] for b in bones]
    index = {n: i for i, n in enumerate(names)}
    V = np.array([to_gltf(v) for v in m['V']], np.float32)
    UV = m['UV'].astype(np.float32)
    F = m['F'].astype(np.uint32)
    # flat normals per vertex (each quad owns its vertices)
    N = np.zeros_like(V)
    for tri in F:
        n = np.cross(V[tri[1]] - V[tri[0]], V[tri[2]] - V[tri[0]])
        N[tri] += n
    N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
    J = np.zeros((len(V), 4), np.uint16)
    Wt = np.zeros((len(V), 4), np.float32)
    for i, w in enumerate(m['W']):
        items = sorted(w.items(), key=lambda kv: -kv[1])[:4]
        tot = sum(v for _, v in items)
        for k, (b, v) in enumerate(items):
            J[i, k] = index[b]
            Wt[i, k] = v / tot
    pivots = {n: np.array(p, float) for n, _, p in bones}
    parents = {n: par for n, par, _ in bones}
    ibm = np.zeros((len(names), 16), np.float32)
    for i, n in enumerate(names):
        g = to_gltf(pivots[n])
        mtx = np.eye(4)
        mtx[:3, 3] = -g
        ibm[i] = mtx.T.reshape(-1)   # column-major
    blobs = [V.tobytes(), N.astype(np.float32).tobytes(), UV.tobytes(), J.tobytes(), Wt.tobytes(), F.tobytes(), ibm.tobytes()]
    offsets, buf = [], b''
    for b in blobs:
        while len(buf) % 4:
            buf += b'\x00'
        offsets.append(len(buf))
        buf += b
    nodes = []
    for n in names:
        par = parents[n]
        local = to_gltf(pivots[n]) - (to_gltf(pivots[par]) if par else 0)
        nodes.append({'name': n, 'translation': [float(x) for x in local]})
    for i, n in enumerate(names):
        kids = [index[c] for c in names if parents[c] == n]
        if kids:
            nodes[i]['children'] = kids
    mesh_node = len(nodes)
    nodes.append({'name': 'AFL_BlockMesh_V2_' + proto, 'mesh': 0, 'skin': 0})
    root = len(nodes)
    nodes.append({'name': 'AFL_BlockMesh_V2_%s_rig' % proto, 'children': [index['root'], mesh_node]})
    acc = lambda bv, ctype, count, typ, extra=None: dict({'bufferView': bv, 'componentType': ctype, 'count': count, 'type': typ}, **(extra or {}))
    gl = {
        'asset': {'version': '2.0', 'generator': 'AFL bm2_build.py'},
        'scene': 0, 'scenes': [{'nodes': [root]}], 'nodes': nodes,
        'meshes': [{'name': 'body_' + proto, 'primitives': [{'attributes': {'POSITION': 0, 'NORMAL': 1, 'TEXCOORD_0': 2, 'JOINTS_0': 3, 'WEIGHTS_0': 4},
                                                              'indices': 5, 'material': 0}]}],
        'skins': [{'joints': list(range(len(names))), 'inverseBindMatrices': 6, 'skeleton': index['root']}],
        'materials': [{'name': 'skin_' + proto, 'pbrMetallicRoughness': {'baseColorTexture': {'index': 0}, 'metallicFactor': 0, 'roughnessFactor': 1}}],
        'textures': [{'sampler': 0, 'source': 0}],
        'samplers': [{'magFilter': 9728, 'minFilter': 9728}],
        'images': [{'uri': 'block_mesh_v2_%s_skin.png' % proto}],
        'buffers': [{'uri': 'block_mesh_v2_%s.bin' % proto, 'byteLength': len(buf)}],
        'bufferViews': [{'buffer': 0, 'byteOffset': o, 'byteLength': len(b)} for o, b in zip(offsets, blobs)],
        'accessors': [acc(0, 5126, len(V), 'VEC3', {'min': V.min(0).tolist(), 'max': V.max(0).tolist()}), acc(1, 5126, len(V), 'VEC3'),
                      acc(2, 5126, len(V), 'VEC2'), acc(3, 5123, len(V), 'VEC4'), acc(4, 5126, len(V), 'VEC4'),
                      acc(5, 5125, F.size, 'SCALAR'), acc(6, 5126, len(names), 'MAT4')],
    }
    for k in range(3, 5):
        gl['bufferViews'][k]['target'] = 34962
    stem = os.path.join(out_dir, 'block_mesh_v2_%s' % proto)
    json.dump(gl, open(stem + '.gltf', 'w'), indent=1)
    open(stem + '.bin', 'wb').write(buf)
    return dict(proto=proto, bones=len(names), vertices=len(V), triangles=len(F), atlas=m['size'],
                )


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    stats = {}
    for proto in 'ABCD':
        m = fix_winding(build(proto))
        img = texture(m)
        open(os.path.join(out, 'block_mesh_v2_%s_skin.png' % proto), 'wb').write(png_bytes(img))
        s = write_gltf(m, out)
        s['tubes'] = len(m['tubes'])
        stats[proto] = s
        print(proto, s)
    json.dump(stats, open(os.path.join(out, 'stats.json'), 'w'), indent=1)


if __name__ == '__main__':
    main()
