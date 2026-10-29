# White-tailed deer D0 concept: buck and doe (autumn coat, hard antlers), the player for scale, poses, renders.
#   blender -b --factory-startup --python deer_build.py -- preview|final
# Writes design/creatures/whitetail_deer_d0/r1/*.png (renders, atlases) and deer_d0_concept.blend.
import bpy, math, os, sys
import numpy as np
from mathutils import Vector

HERE = "D:/Minecraft Modding/AFL-vanilla-rework/design/creatures/whitetail_deer_d0"
sys.path.insert(0, HERE)
import deer_lib as L
from deer_lib import Tube, Fig, rgb, mix, smooth, bands

OUT = HERE + "/r1"
PLAYER_SKIN = "D:/Minecraft Modding/AFL-vanilla-rework/src/main/resources/assets/apocalypse_firstlight/textures/entity/survivor/m01.png"


def V(*a):
    return Vector(a)


# ------------------------------------------------------------------------------------------------- autumn palette
BACK = rgb("#5A4C3E")      # dark saddle down the spine
MID = rgb("#7B6B57")       # grey-brown winter coat
MIDD = rgb("#6A5C4B")
SIDE = rgb("#988A77")      # lower flanks
GREY = rgb("#86796A")      # neck and shoulders, greyer
GREYD = rgb("#6E6355")
CHEST = rgb("#5E5246")     # brisket
WHITE = rgb("#E3DED3")
CREAM = rgb("#CBC1AF")
EYE_RING = rgb("#E2DCCF")
FORE = rgb("#4A3D31")      # buck's forehead
HEADM = rgb("#7E6D5A")
BRIDGE = rgb("#6C5B49")
NOSE = rgb("#1E1B19")
LEG = rgb("#8A7560")       # legs, warmer
LEGL = rgb("#A6937C")
LEGD = rgb("#5E4C3C")
HOOF = rgb("#2B2623")
GLAND = rgb("#3A2C21")
TAILT = rgb("#7C5E42")
TAILD = rgb("#4C3828")
EARIN = rgb("#B7A497")
EYE = rgb("#120F0E")
ANT0, ANT1, ANT2 = rgb("#3D2E21"), rgb("#705B44"), rgb("#D9CDAF")


def jit(a, b, seed=0.0):
    """Per-block hash in 0..1 (a, b already quantized to blocks)."""
    return np.modf(np.abs(np.sin(a * 12.9898 + b * 78.233 + seed) * 43758.5453))[0]


def fill(shape, c):
    return np.broadcast_to(c, shape + (3,)).copy()


# ------------------------------------------------------------------------------------------------------- painters
def p_body(xx, nn, tt, face):
    v = (nn + 1) / 2                                  # 0 spine .. 1 belly
    c = bands(v, [0.14, 0.76], [BACK, MID, SIDE], 1.0)
    c[tt < 0.04] = mix(c[tt < 0.04], CHEST, 0.75)
    j = jit(np.floor(tt * 34), np.floor(xx * 5 + 5), 1.0)
    c[(v > 0.86) & (v < 0.93) & (tt > 0.3) & (tt < 0.8)] *= 0.86                  # darker flank line over the belly
    edge = 0.94 + 0.1 * (1 - smooth(0.25, 0.35, tt)) + 0.1 * smooth(0.75, 0.85, tt) + 0.03 * (j - 0.5)
    c[v > edge] = WHITE
    rump = (tt > 0.965) & (v > 0.32 + 0.08 * j) & (np.abs(xx) < 0.6)
    c[rump] = WHITE
    return c


def p_neck(xx, nn, tt, face):
    v = (nn + 1) / 2                                  # 0 nape .. 1 throat
    c = bands(v, [0.22, 0.8], [GREYD, GREY, SIDE], 2.0)
    low = (tt < 0.3) & (v > 0.55)
    c[low] = mix(c[low], CHEST, 0.55)                               # dark brisket below
    j = jit(np.floor(tt * 16), np.floor(xx * 4 + 4), 2.0)
    patch = (tt > 0.8 + 0.04 * j) & (tt < 0.96) & (v > 0.62 + 0.08 * (j - 0.5))
    c[patch] = WHITE
    return c


def p_headbox(fore_k):
    def p(xx, nn, tt, face):
        v = (nn + 1) / 2                              # 0 crown .. 1 jaw
        c = bands(v, [0.6], [HEADM, SIDE], 3.0) if face not in ("front", "back") else mix(HEADM, SIDE, v)
        if face == "front":                           # under the jaw: white chin, grey throat end
            c[:] = np.where((tt > 0.35)[..., None], WHITE, GREY)
        if face == "back":                            # crown: forehead dark toward the front
            c = mix(c, FORE, fore_k * smooth(0.2, 0.55, tt))
        if face in ("left", "right"):
            c = mix(c, FORE, fore_k * 0.55 * smooth(0.55, 0.9, tt) * (1 - smooth(0.0, 0.45, v)))
        if face == "end":                             # the bridge showing above the muzzle
            c = mix(BRIDGE, FORE, fore_k)
        return c
    return p


def p_muzzle(xx, nn, tt, face):
    v = (nn + 1) / 2                                  # 0 top .. 1 chin
    c = mix(BRIDGE, SIDE, smooth(0.3, 0.8, v))
    band = (tt > 0.46) & (tt < 0.7)
    c[band] = WHITE
    nose = (tt >= 0.7) & (v < 0.56)
    c[nose] = NOSE
    c[(tt >= 0.7) & (v >= 0.56)] = WHITE
    if face == "front":                               # chin
        c[:] = WHITE
    if face == "end":
        c[:] = np.where((v < 0.62)[..., None], NOSE, WHITE)
    if face == "start":
        c[:] = BRIDGE
    return c


def p_ear(xx, nn, tt, face):
    sh = xx.shape
    if face == "back":
        c = fill(sh, MID)
        c[(np.abs(xx) > 0.68) | (tt > 0.86)] = BACK
        return c
    if face == "front":
        c = fill(sh, WHITE)
        c[(np.abs(xx) < 0.32) & (tt > 0.18) & (tt < 0.78)] = EARIN
        c[(np.abs(xx) > 0.66) | (tt > 0.88)] = MID
        return c
    return fill(sh, BACK)


def p_tail(xx, nn, tt, face):
    sh = xx.shape
    if face == "back":
        c = fill(sh, TAILT)
        c[np.abs(xx) < 0.34] = TAILD
        c[np.abs(xx) > 0.7] = WHITE
        c[tt > 0.86] = TAILD
        return c
    if face == "start":
        return fill(sh, TAILT)
    return fill(sh, WHITE)


def p_antler(t0, t1):
    def p(xx, nn, tt, face):
        u = t0 + (t1 - t0) * tt                       # 0 burr .. 1 tip along the whole antler
        c = mix(ANT0, ANT1, smooth(0.0, 0.4, u))
        c = mix(c, ANT2, smooth(0.62, 0.96, u))
        k = xx if face in ("front", "back") else nn
        groove = (np.floor((k + 1) * 1.4) % 2) == 1
        c[groove & (u < 0.8)] *= 0.86
        return c
    return p


def p_leg(sx, hind, tube):
    tr = tube.tr
    tj = [(tr[2 * i - 1] + tr[2 * i]) / 2 for i in (1, 2, 3)]   # knee/elbow, hock/carpus, fetlock
    t_hoof = tr[6]

    def p(xx, nn, tt, face):
        outer = xx * sx                               # +1 outer side .. -1 inner side
        c = fill(xx.shape, LEG)
        upper = tt < tj[0]
        c[upper] = mix(MID, LEG, 0.35)
        inner = outer < -0.3
        c[inner & (tt < tj[0] + 0.12)] = WHITE        # armpit / inner thigh
        c[inner & (tt >= tj[0] + 0.12)] = mix(LEG, CREAM, 0.6)
        if hind:
            c[(nn < -0.45) & (tt > tj[0] + 0.06) & (tt < tj[1] - 0.04)] = WHITE   # behind the shin
            c[(outer < -0.35) & (np.abs(tt - tj[1]) < 0.05) & (nn < 0.4)] = GLAND  # tarsal gland, inner hock
        else:
            c[(nn > 0.4) & (tt > tj[0] + 0.05) & (tt < tj[2])] = LEGD          # dark front of the foreleg
        c[(nn < -0.4) & (tt > tj[1]) & (tt < tj[2])] = LEGL                   # back of the cannon
        c[(tt > tj[2] - 0.02) & (tt < t_hoof)] = LEGL                         # pastern
        c[tt >= t_hoof] = HOOF
        if face == "end":
            c[:] = HOOF * 0.8
        return c
    return p


# ---------------------------------------------------------------------------------------------------------- deer
KINDS = {
    "buck": dict(scale=1.0, neck=1.0, muzzle=2.3, head_w=3.0, antlers=True, fore=1.0, belly_w=5.2),
    "doe": dict(scale=0.92, neck=0.8, muzzle=2.5, head_w=2.8, antlers=False, fore=0.35, belly_w=5.0),
}


def leg(f, side, sx, J, sizes, names, parent, hind):
    names = [n + side for n in names]
    rings = [(J[0], *sizes[0], {parent: 0.4, names[0]: 0.6})]
    for i in range(1, len(J) - 1):
        dp, dn = (J[i] - J[i - 1]).normalized(), (J[i + 1] - J[i]).normalized()
        rings.append((J[i] - dp * 0.45, *sizes[i], {names[i - 1]: 1}))
        rings.append((J[i] + dn * 0.45, *sizes[i], {names[i]: 1}))
    rings.append((J[-1], *sizes[-1], {names[-1]: 1}))
    tb = Tube("leg" + ("H" if hind else "F") + side, rings, (1, 0, 0), (0, -1, 0), None, hair=(0, 0, -1), strokes=0.05)
    tb.paint = p_leg(sx, hind, tb)
    f.add(tb)
    for i, n in enumerate(names):
        f.bones[n] = (tuple(J[i]), tuple(J[i + 1]), parent if i == 0 else names[i - 1])


def build_deer(kind, coll):
    k = KINDS[kind]
    f = Fig("deer_" + kind)
    B = f.bones
    B["pelvis"] = ((0, 5.5, 11), (0, 2, 11), None)
    B["lumbar"] = ((0, 2, 11), (0, -2, 11), "pelvis")
    B["chest"] = ((0, -2, 11), (0, -6.5, 11), "lumbar")
    B["neck_1"] = ((0, -6.3, 11.9), (0, -8.8, 15.6), "chest")     # pivot low in the chest, so the head reaches the grass
    B["neck_2"] = ((0, -8.8, 15.6), (0, -10.6, 18.4), "neck_1")
    bw = k["belly_w"]
    # brisket lowest behind the forelegs, the belly tucked up toward the flank, the rump rounded off
    f.add(Tube("body", [(V(0, -8, 11.05), 4.7, 5.9, {"chest": 1}), (V(0, -4.5, 10.95), 5.0, 7.1, {"chest": 1}),
                        (V(0, -1.8, 10.95), bw, 6.7, {"chest": .5, "lumbar": .5}),
                        (V(0, 2.2, 11.25), bw - 0.1, 5.7, {"lumbar": .5, "pelvis": .5}),
                        (V(0, 5.0, 11.55), 4.9, 5.3, {"pelvis": 1}), (V(0, 8.0, 11.7), 4.6, 4.2, {"pelvis": 1})],
               (1, 0, 0), (0, 0, -1), p_body, hair=(0, 0.7, -0.7)))
    nk = k["neck"]
    f.add(Tube("neck", [(V(0, -5.8, 12.2), 2.9 * nk, 3.8 * nk, {"chest": 1}),
                        (V(0, -7.6, 14.1), 2.8 * nk, 3.6 * nk, {"neck_1": 1}),
                        (V(0, -8.5, 15.1), 2.65 * nk, 3.35 * nk, {"neck_1": 1}),
                        (V(0, -9.6, 16.6), 2.5 * nk, 3.1 * nk, {"neck_1": .5, "neck_2": .5}),
                        (V(0, -10.25, 17.6), 2.4 * nk, 2.85 * nk, {"neck_2": 1}),
                        (V(0, -10.9, 18.9), 2.3 * nk, 2.6 * nk, {"neck_2": .4, "head": .6})],
               (1, 0, 0), (0, -1, -1), p_neck, hair=(0, 0.55, -0.83)))
    a = math.radians(25)
    dr = V(0, -math.cos(a), -math.sin(a))
    dn = V(0, math.sin(a), -math.cos(a))
    up = -dn
    hb = V(0, -10.3, 19.4)
    B["head"] = ((0, -10.6, 18.4), tuple(V(0, -10.6, 18.4) + dr * 3), "neck_2")
    hw = k["head_w"]
    f.add(Tube("head", [(hb, hw, 2.8, {"head": 1}), (hb + dr * 2.8, hw - 0.1, 2.7, {"head": 1})],
               (1, 0, 0), dn, p_headbox(k["fore"]), hair=-dr))
    m0 = hb + dr * 2.5 + dn * 0.45
    f.add(Tube("muzzle", [(m0, 2.0, 1.9, {"head": 1}), (m0 + dr * k["muzzle"], 1.8, 1.65, {"head": 1})],
               (1, 0, 0), dn, p_muzzle, hair=-dr, strokes=0.03))
    for side, sx in ((".L", 1), (".R", -1)):
        base = hb + dr * 0.6 + up * 1.15 + V(sx * 1.15, 0, 0)
        ax = V(sx * 0.8, 0.3, 0.55).normalized()
        f.add(Tube("ear" + side, [(base, 1.1, 0.45, {"ear" + side: 1}), (base + ax * 1.0, 1.95, 0.45, {"ear" + side: 1}),
                                  (base + ax * 2.3, 1.8, 0.4, {"ear" + side: 1}), (base + ax * 3.2, 0.8, 0.35, {"ear" + side: 1})],
                   ax.cross(dr), dr, p_ear, hair=ax, strokes=0.03))
        B["ear" + side] = (tuple(base), tuple(base + ax * 3.2), "head")
        if k["antlers"]:
            ab = hb + dr * 1.45 + up * 1.4 + V(sx * 0.85, 0, 0)
            main = [(0, 0, -0.15), (0, 0, 0), (0.6, 0.5, 0.9), (1.8, 0.8, 1.6), (2.8, 0.2, 2.1), (3.2, -1.2, 2.4),
                    (2.7, -2.6, 2.6), (1.7, -3.5, 2.6)]
            th = (0.95, 0.85, 0.78, 0.72, 0.68, 0.62, 0.52, 0.38)
            pts = [ab + V(sx * o[0], o[1], o[2]) for o in main]
            f.add(Tube("antler" + side, [(p, t, t, {"head": 1}) for p, t in zip(pts, th)], (0, 0, 1), (0, -1, 0),
                       p_antler(0, 1), hair=None))
            for n, (o, d, ln, u0) in enumerate((((0.6, 0.5, 0.9), (0.1, 0.15, 1), 1.3, 0.3),
                                                ((2.8, 0.2, 2.1), (0.05, 0.15, 1), 3.4, 0.55),
                                                ((3.05, -1.85, 2.5), (0, 0.12, 1), 2.7, 0.65))):
                s0 = ab + V(sx * o[0], o[1], o[2])
                dd = V(sx * d[0], d[1], d[2]).normalized()
                s0 = s0 - dd * 0.25
                f.add(Tube(f"tine{n}{side}", [(s0, 0.55, 0.55, {"head": 1}), (s0 + dd * ln * 0.55, 0.48, 0.48, {"head": 1}),
                                              (s0 + dd * ln, 0.34, 0.34, {"head": 1})],
                           (1, 0, 0), (0, -1, 0), p_antler(u0, 1), hair=None))
    tb = V(0, 7.75, 13.2)
    td = V(0, 0.3, -1).normalized()
    f.add(Tube("tail", [(tb, 1.5, 0.8, {"pelvis": .3, "tail": .7}), (tb + td * 1.0, 2.0, 0.8, {"tail": 1}),
                        (tb + td * 2.3, 1.85, 0.7, {"tail": 1}), (tb + td * 3.2, 1.0, 0.5, {"tail": 1})],
               (1, 0, 0), V(0, -1, 0.3), p_tail, hair=td, strokes=0.05))
    B["tail"] = (tuple(tb), tuple(tb + td * 3.2), "pelvis")
    for side, sx in ((".L", 1), (".R", -1)):
        x = sx * 1.4                                   # roots hidden inside the body (outer face <= its side)
        leg(f, side, sx, [V(x, -5.0, 12.0), V(x, -4.3, 7.4), V(x, -4.6, 3.9), V(x, -4.7, 1.0), V(x, -4.95, 0)],
            [(1.8, 2.6), (1.45, 1.8), (1.0, 1.15), (0.95, 1.05), (1.1, 1.45)],
            ["fl_upper", "fl_lower", "fl_cannon", "fl_hoof"], "chest", False)
        x = sx * 1.45
        leg(f, side, sx, [V(x, 5.4, 11.4), V(x, 3.6, 7.3), V(x, 6.9, 4.3), V(x, 6.5, 1.0), V(x, 6.3, 0)],
            [(1.85, 4.6), (1.7, 2.4), (1.15, 1.45), (0.95, 1.05), (1.1, 1.45)],
            ["hl_thigh", "hl_shin", "hl_cannon", "hl_hoof"], "pelvis", True)

    def post(f):
        A = f.A
        for fn, flip in (("left", False), ("right", True)):
            F = A.face("head", fn)
            H, W = F.shape[:2]
            col = (lambda c: W - 1 - c) if flip else (lambda c: c)      # col counted from the jaw side
            # a whole pale eye ring round the dark eye (user 2026-10-28, in game: "貌似没有眼睛"; a half ring was lost in
            # the dark face); row 2 is above the eye, 5 below
            for r in range(2, 6):
                for c in range(2, 6):
                    if r < H and c < W:
                        F[r, col(c), :3] = EYE_RING
            for r in (3, 4):
                for c in (3, 4):
                    F[r, col(c), :3] = EYE
            F[3, col(4), :3] = rgb("#A39D95")                          # wet highlight
            if W > 1 and H > 5:
                F[5, col(1), :3] = GLAND                               # preorbital gland, before and under the ring
        E = A.face("muzzle", "end")
        H, W = E.shape[:2]
        E[0, 1, :3] = rgb("#4B4643")                                   # wet shine on the nose
        if H >= 2:
            E[1, 0, :3] = E[1, W - 1, :3] = rgb("#0A0909")              # nostrils
        Fm = A.face("muzzle", "front")
        Fm[-1, Fm.shape[1] // 2 - 1:Fm.shape[1] // 2 + 1, :3] = NOSE * 1.4   # dark lower lip
        for key in [t.key for t in f.tubes if t.key.startswith("leg")]:
            tb = next(t for t in f.tubes if t.key == key)
            Bk = A.face(key, "back")
            r = int(tb.tr[5] * Bk.shape[0]) - 1                       # just above the fetlock: dewclaws
            if 0 <= r < Bk.shape[0]:
                Bk[r, :, :3] = LEGD
    f.finish(coll, f"{OUT}/deer_{kind}_atlas.png", seed=3 if kind == "buck" else 7, post=post)
    s = k["scale"]
    f.ao.scale = (s, s, s)
    f.kind = kind
    return f


# --------------------------------------------------------------------------------------------------------- player
def face_rect(face, u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}[face]


def build_player(coll):
    im = bpy.data.images.load(PLAYER_SKIN, check_existing=True)
    w, h = im.size
    S = np.array(im.pixels[:], np.float32).reshape(h, w, 4)[::-1].copy()
    k = S.shape[0] // 64
    f = Fig("player")
    parts = [("head", (8, 8, 8), (0, 0, 32), (0, 0), (32, 0), 0.5),
             ("torso", (8, 12, 4), (0, 0, 24), (16, 16), (16, 32), 0.25),
             ("armR", (4, 12, 4), (-6, 0, 24), (40, 16), (40, 32), 0.25),
             ("armL", (4, 12, 4), (6, 0, 24), (32, 48), (48, 48), 0.25),
             ("legR", (4, 12, 4), (-2, 0, 12), (0, 16), (0, 32), 0.25),
             ("legL", (4, 12, 4), (2, 0, 12), (16, 48), (0, 48), 0.25)]
    copies = []
    for key, (pw, ph, pd), (cx, cy, top), uv, uv2, inf in parts:
        for kk, uvo, e in ((key, uv, 0.0), (key + "_o", uv2, inf)):
            tb = Tube(kk, [(V(cx, cy, top + e), pw + 2 * e, pd + 2 * e, {}), (V(cx, cy, top - ph - e), pw + 2 * e, pd + 2 * e, {})],
                      (1, 0, 0), (0, -1, 0), lambda xx, nn, tt, face: np.zeros(xx.shape + (3,), np.float32), hair=None)
            tb.alpha = (lambda xx, nn, tt, face: 1.0)
            f.add(tb)
            copies.append((kk, uvo, (pw, ph, pd)))

    def post(f):
        for kk, (u, v), (pw, ph, pd) in copies:
            for mine, theirs in (("front", "front"), ("left", "left"), ("back", "back"), ("right", "right"),
                                 ("start", "top"), ("end", "bottom")):
                fx, fy, fw, fh = face_rect(theirs, u, v, pw, ph, pd)
                blk = S[fy * k:(fy + fh) * k, fx * k:(fx + fw) * k]
                F = f.A.face(kk, mine)
                ys = np.minimum((np.arange(F.shape[0]) + 0.5) * blk.shape[0] / F.shape[0], blk.shape[0] - 1e-3).astype(int)
                xs = np.minimum((np.arange(F.shape[1]) + 0.5) * blk.shape[1] / F.shape[1], blk.shape[1] - 1e-3).astype(int)
                R = blk[ys][:, xs]
                if mine == "end":
                    R = R[::-1]
                F[...] = R
    f.finish(coll, f"{OUT}/player_atlas.png", post=post, rig=False)
    return f


# ---------------------------------------------------------------------------------------------------------- poses
POSES = {
    "stand": {},
    # head down to the grass, ears turned, tail loose; forelegs a little apart
    "graze": {"neck_1": (108, 0, 4), "neck_2": (8, 0, 0), "head": (-46, 0, 0),
              "ear.L": (-10, 0, 15), "ear.R": (10, 0, -10),
              "fl_upper.L": (-7, 0, 0), "fl_lower.L": (4, 0, 0), "fl_upper.R": (4, 0, 0),
              "hl_thigh.L": (4, 0, 0), "hl_thigh.R": (-3, 0, 0), "tail": (8, 0, 0)},
    # alert: neck straight up, head level, ears forward, tail half up, a forefoot raised to stamp
    "alert": {"neck_1": (-24, 0, 0), "neck_2": (-14, 0, 6), "head": (8, 0, 4), "ear.L": (-18, 0, 30), "ear.R": (-18, 0, -30),
              "tail": (70, 0, 0), "fl_upper.R": (-16, 0, 0), "fl_lower.R": (56, 0, 0), "fl_cannon.R": (12, 0, 0)},
    # bounding away, mid-air: forelegs reaching, hind legs pushed back, neck out, tail up and flagging
    "flee": {"pelvis": (-6, 0, 0), "neck_1": (22, 0, 0), "neck_2": (6, 0, 0), "head": (-6, 0, 0),
             "ear.L": (30, 0, 10), "ear.R": (30, 0, -10), "tail": (165, 0, 0),
             "fl_upper.L": (-56, 0, 0), "fl_lower.L": (-4, 0, 0), "fl_cannon.L": (12, 0, 0),
             "fl_upper.R": (-42, 0, 0), "fl_lower.R": (26, 0, 0), "fl_cannon.R": (36, 0, 0),
             "hl_thigh.L": (42, 0, 0), "hl_shin.L": (-24, 0, 0), "hl_cannon.L": (18, 0, 0),
             "hl_thigh.R": (34, 0, 0), "hl_shin.R": (-14, 0, 0), "hl_cannon.R": (22, 0, 0)},
}


# --------------------------------------------------------------------------------------------------------- render
def stage():
    sc = bpy.context.scene
    for eng in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT"):
        try:
            sc.render.engine = eng
            break
        except TypeError:
            pass
    try:
        sc.view_settings.view_transform = "Standard"
    except TypeError:
        pass
    if "deer_sun" in bpy.data.objects:
        return sc
    w = bpy.data.worlds.new("deer_world")
    sc.world = w
    w.use_nodes = True
    bg = next(n for n in w.node_tree.nodes if n.type == "BACKGROUND")
    bg.inputs[0].default_value = (0.36, 0.38, 0.41, 1)
    bg.inputs[1].default_value = 0.6
    sd = bpy.data.lights.new("deer_sun", "SUN")
    sd.energy = 3.2
    sd.angle = math.radians(6)
    so = bpy.data.objects.new("deer_sun", sd)
    sc.collection.objects.link(so)
    so.rotation_euler = (math.radians(48), 0, math.radians(-35))
    fd = bpy.data.lights.new("deer_rim", "SUN")
    fd.energy = 1.2
    fo = bpy.data.objects.new("deer_rim", fd)
    sc.collection.objects.link(fo)
    fo.rotation_euler = (math.radians(70), 0, math.radians(150))
    bpy.ops.mesh.primitive_plane_add(size=30)
    g = bpy.context.active_object
    g.name = "deer_ground"
    m = bpy.data.materials.new("deer_ground")
    m.use_nodes = True
    b = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    b.inputs["Base Color"].default_value = (0.25, 0.26, 0.27, 1)
    b.inputs["Roughness"].default_value = 1
    g.data.materials.append(m)
    cd = bpy.data.cameras.new("deer_cam")
    co = bpy.data.objects.new("deer_cam", cd)
    sc.collection.objects.link(co)
    sc.camera = co
    sc.render.film_transparent = False
    return sc


def show_only(figs, all_figs):
    for g in all_figs:
        hide = g not in figs
        g.ob.hide_render = hide
        if g.ao is not g.ob:
            g.ao.hide_render = hide


def shoot(figs, all_figs, direction, out, res=(1200, 900), lens=60, pad=1.12, ortho=None, target=None, size=None):
    sc = stage()
    show_only(figs, all_figs)
    lo = Vector((1e9,) * 3)
    hi = Vector((-1e9,) * 3)
    for g in figs:
        a, b = L.bounds(g.ob)
        lo = Vector([min(lo[i], a[i]) for i in range(3)])
        hi = Vector([max(hi[i], b[i]) for i in range(3)])
    c = (lo + hi) / 2 if target is None else Vector(target)
    d = Vector(direction).normalized()
    cam = sc.camera
    aspect = res[0] / res[1]
    # extent of the box seen from d: project the 8 corners onto the camera plane
    right = d.cross(Vector((0, 0, 1))).normalized() * -1
    upv = right.cross(-d).normalized()
    pts = [Vector((x, y, z)) for x in (lo.x, hi.x) for y in (lo.y, hi.y) for z in (lo.z, hi.z)]
    ex = max(abs((p - c).dot(right)) for p in pts) * 2
    ey = max(abs((p - c).dot(upv)) for p in pts) * 2
    sz = max(ey, ex / aspect) * pad if size is None else size
    if ortho:
        cam.data.type = "ORTHO"
        cam.data.sensor_fit = "VERTICAL"
        cam.data.ortho_scale = sz
        cam.location = c + d * 20
    else:
        cam.data.type = "PERSP"
        cam.data.lens = lens
        cam.data.sensor_fit = "VERTICAL"
        fov = 2 * math.atan(cam.data.sensor_height / 2 / lens)
        cam.location = c + d * (sz / 2 / math.tan(fov / 2))
    cam.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.resolution_percentage = 100
    sc.render.filepath = out
    sc.render.image_settings.file_format = "PNG"
    bpy.ops.render.render(write_still=True)
    print("rendered", out)
    if ortho:
        return dict(cz=c.z, cy=c.y, height=sz, res=res)


def build_all():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o)
    coll = bpy.context.scene.collection
    stage()
    buck = build_deer("buck", coll)
    doe = build_deer("doe", coll)
    player = build_player(coll)
    return buck, doe, player


def lineup(buck, doe, player):
    buck.pose({})
    doe.pose({})
    buck.ao.location = (0, 0, 0)
    doe.ao.location = (2.6, 0, 0)
    player.ob.location = (-1.8, 0, 0)
    for g in (buck, doe):
        g.pose({})


def pose_check(g, name):
    g.pose(POSES[name], lift=POSE_LIFT.get(name, 0))
    lo, hi = L.bounds(g.ob)
    hooves = [g.point(b).z / PXs(g) for b in g.bones if b.endswith("hoof.L") or b.endswith("hoof.R")]
    nose = g.point("head", 5.0 / 3.0).z / PXs(g)
    print(f"POSE {g.kind} {name}: hooves z px {[round(h, 2) for h in hooves]} nose z {nose:.2f}")


def PXs(g):
    return L.PX * g.ao.scale.x


POSE_LIFT = {"flee": 5.0}


def preview_poses():
    buck, doe, player = build_all()
    figs = [buck, doe, player]
    for name, g in (("graze", doe), ("alert", buck), ("flee", buck)):
        pose_check(g, name)
        shoot([g], figs, (0.85, -1, 0.25), f"{OUT}/prev_pose_{name}.png", res=(900, 900))
        shoot([g], figs, (1, 0, 0.04), f"{OUT}/prev_pose_{name}_side.png", res=(900, 700))
        g.pose({})


def silhouettes(on, figs):
    sc = bpy.context.scene
    bg = next(n for n in sc.world.node_tree.nodes if n.type == "BACKGROUND")
    if on:
        black = bpy.data.materials.get("deer_black") or bpy.data.materials.new("deer_black")
        black.use_nodes = True
        nt = black.node_tree
        nt.nodes.clear()
        e = nt.nodes.new("ShaderNodeEmission")
        e.inputs[0].default_value = (0, 0, 0, 1)
        o = nt.nodes.new("ShaderNodeOutputMaterial")
        nt.links.new(e.outputs[0], o.inputs[0])
        for g in figs:
            g.saved = list(g.ob.data.materials)
            g.ob.data.materials.clear()
            g.ob.data.materials.append(black)
        bg.inputs[0].default_value = (1, 1, 1, 1)
        bg.inputs[1].default_value = 1
        bpy.data.objects["deer_ground"].hide_render = True
    else:
        for g in figs:
            g.ob.data.materials.clear()
            for m in g.saved:
                g.ob.data.materials.append(m)
        bg.inputs[0].default_value = (0.36, 0.38, 0.41, 1)
        bg.inputs[1].default_value = 0.6
        bpy.data.objects["deer_ground"].hide_render = False


def final():
    import json
    buck, doe, player = build_all()
    figs = [buck, doe, player]
    views = {"side": (1, 0, 0.05), "front34": (0.85, -1, 0.25), "front": (0, -1, 0.12), "back34": (-0.85, 1, 0.3)}
    for g in (buck, doe):
        g.pose({})
        lo, hi = L.bounds(g.ob)
        c = (lo + hi) / 2
        for vn, d in views.items():
            shoot([g], figs, d, f"{OUT}/{g.kind}_{vn}.png", res=(800, 800), target=c, size=2.25, lens=50)
        hp = g.point("head", 0.6)
        shoot([g], figs, (0.75, -1, 0.18), f"{OUT}/{g.kind}_head.png", res=(700, 700), target=hp, size=0.62, lens=85)
    # lineup, side on: player, buck facing him, doe behind
    buck.ao.location = (0, 0, 0)
    doe.ao.location = (0, 2.0, 0)
    player.ob.location = (0, -1.8, 0)
    bpy.context.view_layer.update()
    tgt = (0, 0.25, 1.12)
    meta = shoot(figs, figs, (1, 0, 0), f"{OUT}/lineup.png", res=(2200, 900), ortho=True, target=tgt, size=2.4)
    silhouettes(True, figs)
    shoot(figs, figs, (1, 0, 0), f"{OUT}/lineup_sil.png", res=(2200, 900), ortho=True, target=tgt, size=2.4)
    silhouettes(False, figs)
    meta["heights"] = {"buck": 14.5 * L.PX, "doe": 14.5 * L.PX * KINDS["doe"]["scale"], "player": 32 * L.PX}
    meta["y"] = {"player": -1.8, "buck": 0.0, "doe": 2.0}
    json.dump(meta, open(f"{OUT}/lineup.json", "w"))
    buck.ao.location = doe.ao.location = (0, 0, 0)
    for name, g in (("graze", doe), ("alert", buck), ("flee", buck)):
        pose_check(g, name)
        shoot([g], figs, (0.85, -1, 0.22), f"{OUT}/pose_{name}.png", res=(1000, 900), pad=1.1)
        g.pose({})
    bpy.ops.wm.save_as_mainfile(filepath=HERE + "/deer_d0_concept.blend")


def preview():
    buck, doe, player = build_all()
    figs = [buck, doe, player]
    for g, x in ((buck, 0), (doe, 3), (player, -2)):
        g.ao.location.x = x
    buck.pose({})
    doe.pose({})
    shoot([buck], figs, (1, 0, 0.06), f"{OUT}/prev_buck_side.png", res=(1200, 900))
    shoot([buck], figs, (0.8, -1, 0.3), f"{OUT}/prev_buck_q.png", res=(1000, 1000))
    shoot([doe], figs, (0.8, -1, 0.3), f"{OUT}/prev_doe_q.png", res=(1000, 1000))
    bpy.ops.wm.save_as_mainfile(filepath=HERE + "/deer_d0_concept.blend")


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    os.makedirs(OUT, exist_ok=True)
    {"preview": preview, "poses": preview_poses, "final": final}.get(argv[0] if argv else "preview")()
