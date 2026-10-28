# Female body V1: two 128 x 128 skins for the old body (vanilla 64 net doubled, 2 x 2 texels a model px) in the infected
# Z0 r6 style (B, design/creatures/infected_z0: Minecraft Story Mode look): flat painted colour in soft value steps, a
# slightly darker rim on every face, vertical cloth folds, hair as strands; no photo noise. Hair lives on the second
# layer's head (the hat), the future separate hair layer; the rest of the second layer stays empty.
#   python -I tools/characters/old_body_skin_paint_v1.py
# Out: design/characters/female_body_v1/skins/{male,female}_v1.png (= textures/entity/survivor/afl_*_v1.png, in underwear since
# old-body clothes V1) and the garments textures/entity/clothing/{shirt,jacket}[_slim].png, {jeans,shoes}.png
import math, os, struct, sys, zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "design", "characters", "female_body_v1", "skins")
S = 2   # texels per model px

def write_png(p, w, h, px):
    raw = b"".join(b"\0" + bytes(c for t in row for c in t) for row in px)
    def ch(t, c): return struct.pack(">I", len(c)) + t + c + struct.pack(">I", zlib.crc32(t + c) & 0xffffffff)
    open(p, "wb").write(b"\x89PNG\r\n\x1a\n" + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + ch(b"IDAT", zlib.compress(raw, 9)) + ch(b"IEND", b""))

def h32(*a):
    x = 0x9E3779B9
    for v in a:
        x ^= (int(v) & 0xffffffff) + 0x7F4A7C15 + ((x << 6) & 0xffffffff) + (x >> 2)
        x = (x * 0x85EBCA6B) & 0xffffffff
        x ^= x >> 13
    return (x & 0xffff) / 65535.0

def vnoise(x, y, s, seed):
    """Smooth value noise, cell s texels, -1..1."""
    gx, gy = x / s, y / s; ix, iy = math.floor(gx), math.floor(gy); fx, fy = gx - ix, gy - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = h32(ix, iy, seed), h32(ix + 1, iy, seed); c, d = h32(ix, iy + 1, seed), h32(ix + 1, iy + 1, seed)
    return 2 * ((a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy) - 1

def mul(c, k): return tuple(max(0, min(255, round(v * k))) for v in c)
def mix(a, b, t): return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))

class Skin:
    def __init__(self): self.px = [[(0, 0, 0, 0)] * 128 for _ in range(128)]
    def put(self, x, y, c, a=255):
        if 0 <= x < 128 and 0 <= y < 128: self.px[y][x] = (c[0], c[1], c[2], a)

    def box(self, u, v, w, h, d, fn, seed):
        """A vanilla box's six faces; fn(face, tx, ty, W, H, fwd) -> (rgb, alpha) or None. fwd: on the side faces 0 at the
        back, 1 at the front (the same on both sides); on the front and back faces tx runs as the viewer sees it."""
        faces = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                 "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}
        for name, (fu, fv, fw, fh) in faces.items():
            W, H = fw * S, fh * S
            for ty in range(H):
                for tx in range(W):
                    fwd = tx / max(1, W - 1) if name == "right" else 1 - tx / max(1, W - 1) if name == "left" else 0.5
                    r = fn(name, tx, ty, W, H, fwd)
                    if r is None: continue
                    c, a = r
                    # the face's soft rim and texture (the r6 look), on everything painted
                    rim = 1.0
                    if name not in ("top", "bottom"):
                        if tx == 0 or tx == W - 1: rim *= 0.93
                    else:
                        if tx in (0, W - 1) or ty in (0, H - 1): rim *= 0.95
                    k = rim * (1 + 0.035 * vnoise(tx + fu * S, ty + fv * S, 3, seed) + 0.018 * (h32(tx + fu * S, ty + fv * S, seed) - 0.5))
                    self.put(fu * S + tx, fv * S + ty, mul(c, k), a)

# ---------------------------------------------------------------- palette
SKIN = (214, 168, 138)
SKIN_SH = (178, 128, 104)
JEANS = (64, 86, 124)
BOOT = (74, 54, 40)
SOLE = (52, 40, 32)

def skin_at(ty, H, side=False):
    # top a touch lighter, the underside of the part warmer and darker
    t = ty / max(1, H - 1)
    c = mix(SKIN, SKIN_SH, max(0, t - 0.75) * 1.6)
    return mul(c, 1.03 - 0.05 * t)

def cloth(c, tx, ty, W, H, seed, folds=0.07):
    """Vertical folds: long soft streaks, darker toward the hem."""
    f = vnoise(tx * 3.0, ty * 0.35, 4, seed)
    return mul(c, 1 + folds * f - 0.06 * (ty / max(1, H - 1)))

def hair_px(hc, tx, ty, H, seed, tip=1.0):
    """Strands: each texel column its own value, a sheen near the top, darker tips."""
    col = vnoise(tx * 1.0, ty * 0.22, 1.4, seed)
    k = 1 + 0.24 * col + 0.08 * (h32(tx, ty, seed) - 0.5)
    if ty < 3: k *= 1.08 - 0.03 * ty
    elif 4 <= ty <= 5: k *= 1.05   # the sheen band
    k *= 1 - 0.18 * tip * (ty / max(1, H - 1)) ** 2
    return mul(hc, k)

# ---------------------------------------------------------------- one survivor
def paint(female):
    sk = Skin()
    HAIR = (92, 60, 40) if female else (52, 40, 32)
    SHIRT = (148, 92, 80) if female else (66, 88, 112)
    BROW = mul(HAIR, 0.8)
    IRIS = (82, 110, 84) if female else (78, 96, 128)
    LIP_U, LIP_L = (170, 104, 96), (196, 132, 118)

    # ---- head, base layer: face, ears, the scalp under the hair
    def head(face, tx, ty, W, H, fwd):
        c = skin_at(ty, H)
        if face == "top": return hair_px(HAIR, tx, ty, H, 11, 0), 255
        if face == "bottom": return mul(SKIN_SH, 0.95), 255
        if face == "back":
            return hair_px(HAIR, tx, ty, H, 12, 0.6), 255
        if face in ("right", "left"):
            hairline = 3 if not female else 2
            if fwd < 0.55 or ty <= hairline: return hair_px(HAIR, tx, ty, H, 13, 0.6), 255
            # the ear: a darker outline with a lit middle
            ex = round(fwd * (W - 1))
            if 6 <= ty <= 10 and W // 2 - 1 <= ex <= W // 2 + 1:
                return (mul(SKIN_SH, 0.96) if ty in (6, 10) or ex in (W // 2 - 1, W // 2 + 1) else mul(SKIN, 0.97)), 255
            return c, 255
        # front: the face as the viewer sees it, 16 x 16
        x, y = tx, ty
        if y <= 2: return hair_px(HAIR, x, y, H, 14, 0), 255
        if y == 3 and (x < 5 if female else x % 5 != 2): return hair_px(HAIR, x, y, H, 14, 0), 255
        if female:
            if y == 5 and (3 <= x <= 6 or 9 <= x <= 12): return BROW, 255                  # thin brows, a texel of skin under
            if y == 7 and (2 <= x <= 5 or 10 <= x <= 13): return (52, 38, 34), 255       # lash line on the outer corners only
        else:
            if y in (5, 6) and (2 <= x <= 6 or 9 <= x <= 13): return mul(BROW, 1.0 if y == 6 else 0.9), 255
            if y == 7 and (3 <= x <= 6 or 9 <= x <= 12): return mul(SKIN_SH, 1.02), 255  # upper lid shade
        if y in (8, 9):
            for ex in (3, 9):
                if ex <= x <= ex + 3:
                    i = x - ex
                    if i in (1, 2):
                        pupil = (i == 2 if ex == 3 else i == 1) and y == 9
                        return ((28, 26, 30) if pupil else mul(IRIS, 1.08 if y == 8 else 0.92)), 255
                    return (236, 232, 224) if y == 8 else (214, 208, 200), 255
        if y == 10 and x in (7, 8): return mul(SKIN, 1.05), 255                         # nose ridge lit
        if y == 11 and x in (7, 8): return mul(SKIN_SH, 0.98), 255                      # under the nose
        if female:
            if y == 12 and 6 <= x <= 9: return (mix(c, LIP_U, 0.7) if x in (6, 9) else LIP_U), 255
            if y == 13 and 7 <= x <= 8: return mix(c, LIP_L, 0.75), 255
            if y == 11 and x in (2, 3, 12, 13): return mix(c, (212, 140, 128), 0.35), 255
        else:
            if y == 12 and 6 <= x <= 9: return mul(SKIN_SH, 0.85), 255
            if y >= 13 and 3 <= x <= 12: return mix(c, (150, 120, 104), 0.25), 255           # stubble shade
        if y == 7 and (2 <= x <= 6 or 9 <= x <= 13): c = mul(c, 0.94)                     # eye sockets
        if y in (4, 5) and 5 <= x <= 10: c = mul(c, 1.03)                                    # forehead
        if x in (0, 15) and y >= 6: c = mul(c, 0.93)                                         # the face's sides turn away
        if y == 14 and x in (7, 8): c = mul(c, 1.03)                                         # chin
        if y >= 14: c = mul(c, 0.94 if y == 14 else 0.88)                                    # jaw
        return c, 255
    sk.box(0, 0, 8, 8, 8, head, 1)

    # ---- the hair layer (hat, 0.5 px out): the future separate hair layer
    def hat(face, tx, ty, W, H, fwd):
        if face == "top": return hair_px(HAIR, tx, ty, H, 21, 0), 255
        if face == "bottom": return None
        if face == "back":
            if ty <= (15 if female else 10 + jag(tx, 11)): return hair_px(HAIR, tx, ty, H, 22, 0.8), 255
            return None
        if face in ("right", "left"):
            if female:
                # a bob: down to the jaw behind the ear, the front cut back above the cheek
                edge = 15 if fwd < 0.62 else (5 if fwd > 0.86 else 10)
                if ty <= edge + jag(tx, 5 + (face == "left")): return hair_px(HAIR, tx, ty, H, 23, 0.8), 255
                return None
            edge = 7 if fwd < 0.45 else 3
            if ty <= edge + (jag(tx, 8 + (face == "left")) if edge == 3 else 0): return hair_px(HAIR, tx, ty, H, 23, 0.4), 255
            return None
        # front: the fringe (swept to the viewer's right on the female)
        if female:
            edge = 5 - x_sweep(tx) + jag(tx, 9)
        else:
            edge = 1 + (tx % 5 == 0) + (jag(tx, 10) > 0)
        return (hair_px(HAIR, tx, ty, H, 24, 0.2), 255) if ty <= edge else None
    def jag(tx, seed):
        """A strand's tip: -1, 0 or +1 texels round the cut line."""
        r = h32(tx, seed, 77)
        return -1 if r < 0.25 else 1 if r > 0.7 else 0

    def x_sweep(tx): return round(tx * 4 / 15)
    sk.box(32, 0, 8, 8, 8, hat, 2)

    # ---- torso in underwear (old-body clothes V1, user 2026-10-27: the underwear is part of the skin and never comes
    # off; clothes are worn over it as their own shells): bare skin, briefs from 9.5 px, a bra on the woman
    UNDER = (74, 66, 76) if female else (58, 62, 70)
    def under(c, tx, ty, W, H, seed): return cloth(c, tx, ty, W, H, seed, 0.04)
    def chest_depth(x, l):
        """SkinMesh#chestDepth (r4): the deeper of two domes round the nipples, px out of the torso's front."""
        dx, dy = abs(x) - 1.75, l - 3.5
        nx, ny = dx / (1.2 if dx < 0 else 1.35), dy / (2.1 if dy < 0 else 1.15)
        rr = nx * nx + ny * ny
        if rr >= 1: return 0.0
        up = ny * ny / rr if rr > 1e-6 else 0.0
        e = 0.75 + ((1.25 if dy < 0 else 0.5) - 0.75) * up
        return 1.05 * (1 - rr) ** e
    def cup(tx, ty):
        """A front texel (2 to the px, 16 x 24) the chest stands out under anywhere: the bra's cup covers it all."""
        return any(chest_depth((tx + a / 4) / 2 - 4, (ty + b / 4) / 2) > 0 for a in range(5) for b in range(5))
    def torso(face, tx, ty, W, H, fwd):
        if face == "top": return skin_at(0, 1), 255
        if face == "bottom": return mul(UNDER, 0.85), 255
        if ty >= 19:                                                                            # briefs, 9.5 px down
            c = under(UNDER, tx, ty, W, H, 31)
            if ty == 19: c = mul(c, 1.08 if not female else 0.92)                              # the waistband
            if face == "front" and not female and ty >= 21 and abs(tx - (W - 1) / 2) <= 1.5: c = mul(c, 0.92)
            return c, 255
        c = skin_at(ty, H)
        if face == "front":
            mid = (W - 1) / 2
            if female:                                                                          # the bra over SkinMesh's domes
                x, l = (tx + 0.5) / 2 - 4, (ty + 0.5) / 2
                if ty == 10 or ty == 9 and abs(x) <= 3.3:                                       # the band at the fold
                    return mul(under(UNDER, tx, ty, W, H, 35), 0.9), 255
                if cup(tx, ty):
                    c = under(UNDER, tx, ty, W, H, 35)
                    if not (cup(tx - 1, ty) and cup(tx + 1, ty) and cup(tx, ty - 1)): c = mul(c, 0.9)   # the cup's trim
                    return c, 255
                if abs(x) < 0.75 and 3 <= l < 4.5: return mul(under(UNDER, tx, ty, W, H, 35), 0.86), 255  # the gore between the cups
            if female and ty <= 3 and tx in (3, W - 4) and not cup(tx, ty): return under(UNDER, tx, ty, W, H, 36), 255   # the straps
            if not female:
                if 4 <= ty <= 5 and 2 <= tx <= W - 3 and abs(tx - mid) > 1: c = mul(c, 0.95)  # under the chest
                if ty == 14 and abs(tx - mid) < 1: c = mul(c, 0.86)                            # navel
                if 8 <= ty <= 16 and abs(tx - mid) < 0.6: c = mul(c, 0.97)                     # the middle line
            elif ty == 14 and abs(tx - mid) < 1: c = mul(c, 0.88)
            if tx in (0, W - 1): c = mul(c, 0.94)
            return c, 255
        if female and face == "back" and (ty == 10 or ty < 10 and tx in (3, W - 4)): return mul(UNDER, 0.92), 255   # the bra's band and straps
        if female and face in ("right", "left") and ty == 10: return mul(UNDER, 0.92), 255
        if face in ("right", "left"): c = mul(c, 0.92 + 0.06 * fwd)                            # under the arms
        if face == "back" and 2 <= ty <= 14 and tx in (W // 2 - 1, W // 2): c = mul(c, 0.96)   # the spine
        return c, 255
    sk.box(16, 16, 8, 12, 4, torso, 3)

    # ---- arms: bare, the bra straps over the woman's shoulders
    aw = 3 if female else 4
    def arm(seed):
        def fn(face, tx, ty, W, H, fwd):
            if face == "top": return skin_at(0, 1), 255
            if face == "bottom": return mul(SKIN_SH, 0.92), 255
            c = skin_at(ty, H)
            if ty >= 20: c = mul(c, 0.96 if ty < 23 else 0.9)                                  # the hand
            return c, 255
        return fn
    sk.box(40, 16, aw, 12, 4, arm(41), 4)
    sk.box(32, 48, aw, 12, 4, arm(42), 5)

    # ---- legs: briefs at the top, bare feet
    def leg(seed):
        def fn(face, tx, ty, W, H, fwd):
            if face == "top": return mul(UNDER, 0.9), 255
            if face == "bottom": return mul(SKIN_SH, 0.9), 255
            if ty <= (3 if female else 4):
                c = under(UNDER, tx, ty, W, H, seed)
                if ty == (3 if female else 4): c = mul(c, 0.88)                                 # the leg band
                return c, 255
            c = skin_at(ty, H)
            if face == "front" and 9 <= ty <= 10: c = mul(c, 1.03)                              # the knee
            if face == "front" and ty == 23 and tx % 2 == 1: c = mul(c, 0.9)                    # toes
            if ty >= 21: c = mul(c, 0.95)                                                       # the foot
            return c, 255
        return fn
    sk.box(0, 16, 4, 12, 4, leg(51), 6)
    sk.box(16, 48, 4, 12, 4, leg(52), 7)
    return sk


# ---------------------------------------------------------------- old-body clothes V1: garment textures
# Each garment a 128 skin net with only the garment painted (client/skeleton/OldBodyWardrobe draws it as a shell over the
# part, SkinMesh#shell, cut to its levels). Rows are 2 texels a px down the part; the shell's cut end shows the part's top
# or bottom face as the garment's inside. Sleeved garments also get a _slim net (3 px arms sit elsewhere on the net).
DENIM = (104, 132, 166)
LEATHER = (118, 80, 52)
SHOE = (70, 46, 32)
INSIDE = 0.55   # a cut end's face: the garment's inside, in shadow

def shirt(sk, slim):
    def torso(face, tx, ty, W, H, fwd):
        if face == "top": return mul(DENIM, 1.03), 255
        if face == "bottom": return mul(DENIM, INSIDE), 255                                     # inside the hem (10.5 px)
        c = cloth(DENIM, tx, ty, W, H, 61, 0.06)
        if ty >= 19: c = mul(c, 0.9)                                                            # the hem, 10.5 px
        mid = (W - 1) / 2
        if face == "front":
            if ty <= 1 and abs(tx - mid) <= 3: c = mul(DENIM, 0.82)                             # the collar's shadow
            if ty <= 3 and (abs(tx - mid) - 1.5) * 1 <= 3 - ty and abs(tx - mid) >= 0.5: c = mul(DENIM, 1.08) if ty >= 1 else c  # collar points
            if abs(tx - mid) < 1: c = mul(c, 0.9 if tx < mid else 1.06)                         # the placket
            if abs(tx - mid) < 1 and ty in (4, 8, 12, 16): c = (226, 222, 212)                  # buttons
            for px0 in (2, W - 6):                                                              # chest pockets with flaps
                if px0 <= tx <= px0 + 3 and 5 <= ty <= 9:
                    c = mul(c, 1.05 if ty > 6 else 0.88 if ty == 6 else 1.1)
            if tx in (0, W - 1): c = mul(c, 0.92)
        elif face == "back":
            if ty == 4: c = mul(c, 0.9)                                                         # the yoke's seam
            if ty <= 1 and abs(tx - mid) <= 2.5: c = mul(DENIM, 0.86)                           # the collar band
        else:
            c = mul(c, 0.9 + 0.08 * fwd)
            if tx == W // 2: c = mul(c, 0.94)                                                   # side seam
        return c, 255
    sk.box(16, 16, 8, 12, 4, torso, 61)
    aw = 3 if slim else 4
    def arm(seed):
        def fn(face, tx, ty, W, H, fwd):
            if face == "top": return mul(DENIM, 1.03), 255
            if face == "bottom": return mul(DENIM, INSIDE), 255                                 # inside the cuff (9.5 px)
            c = cloth(DENIM, tx, ty, W, H, seed, 0.07)
            if 16 <= ty <= 18: c = mul(c, 0.94 if ty == 16 else 1.02)                           # the cuff
            if ty == 17 and face == "front" and tx == W - 2: c = (226, 222, 212)                 # its button
            if 9 <= ty <= 11 and face == "front": c = mul(c, 0.94 + 0.03 * (ty - 9))             # the elbow's creases
            if face in ("right", "left") and tx == W // 2: c = mul(c, 0.94)
            return c, 255
        return fn
    sk.box(40, 16, aw, 12, 4, arm(62), 62)
    sk.box(32, 48, aw, 12, 4, arm(63), 63)

def jacket(sk, slim):
    def sheen(c, tx, ty, W, H, seed):
        f = vnoise(tx * 1.2, ty * 0.5, 5, seed)
        return mul(c, 1 + 0.09 * f + (0.06 if f > 0.55 else 0) - 0.05 * (ty / max(1, H - 1)))
    def torso(face, tx, ty, W, H, fwd):
        if face == "top": return mul(LEATHER, 1.0), 255
        if face == "bottom": return mul(LEATHER, INSIDE), 255                                   # inside the hem (11.5 px)
        c = sheen(LEATHER, tx, ty, W, H, 71)
        if 20 <= ty <= 22: c = mul(c, 0.86 if ty == 20 else 0.95)                               # the hem band
        mid = (W - 1) / 2
        if face == "front":
            if ty <= 2 and abs(tx - mid) <= 4.5 - ty: c = mul(LEATHER, 0.78)                    # the collar, turned down
            if ty <= 2 and abs(tx - mid) <= 1.5 - ty * 0.5: c = mul(DENIM, 0.8)                 # the shirt's collar in the V
            if 3 <= ty <= 21 and tx == 8: c = (150, 146, 138)                                   # the zip, a texel right of centre
            if 3 <= ty <= 21 and tx == 7: c = mul(c, 0.86)                                      # its placket's shadow
            if 12 <= ty <= 14 and (2 <= tx <= 4 or W - 5 <= tx <= W - 3): c = mul(c, 0.82 if ty == 12 else 1.0)  # slash pockets
            if tx in (0, W - 1): c = mul(c, 0.9)
        elif face == "back":
            if ty <= 1 and abs(tx - mid) <= 4.5: c = mul(LEATHER, 0.8)                          # the collar's back
            if ty == 6: c = mul(c, 0.9)                                                         # the yoke
        else:
            c = mul(c, 0.88 + 0.08 * fwd)
        return c, 255
    sk.box(16, 16, 8, 12, 4, torso, 71)
    aw = 3 if slim else 4
    def arm(seed):
        def fn(face, tx, ty, W, H, fwd):
            if face == "top": return mul(LEATHER, 1.0), 255
            if face == "bottom": return mul(LEATHER, INSIDE), 255                               # inside the cuff (9.75 px)
            c = sheen(LEATHER, tx, ty, W, H, seed)
            if 0 <= ty <= 1: c = mul(c, 0.92)                                                   # the shoulder seam
            if 17 <= ty <= 19: c = mul(c, 0.86 if ty == 17 else 0.96)                           # the cuff
            if face in ("right", "left") and fwd < 0.3 and 15 <= ty <= 18 and tx == 1: c = (150, 146, 138)  # the cuff's zip
            if 9 <= ty <= 11 and face == "front": c = mul(c, 0.9 + 0.04 * (ty - 9))              # the elbow's creases
            return c, 255
        return fn
    sk.box(40, 16, aw, 12, 4, arm(72), 72)
    sk.box(32, 48, aw, 12, 4, arm(73), 73)

def jeans(sk):
    def torso(face, tx, ty, W, H, fwd):
        if face == "top": return mul(JEANS, 0.9), 255                                           # the waistband's top (9 px)
        if face == "bottom": return mul(JEANS, 0.8), 255
        c = cloth(JEANS, tx, ty, W, H, 81, 0.05)
        if 18 <= ty <= 19: c = mul(c, 1.06 if ty == 18 else 0.92)                                # the waistband
        if face == "front":
            if 18 <= ty <= 19 and tx in (2, W - 3): c = mul(c, 0.8)                             # belt loops
            if 18 <= ty <= 19 and tx == W // 2: c = (172, 150, 96)                              # the button
            if ty >= 20 and tx == W // 2 - 1: c = mul(c, 0.86)                                  # the fly's stitching
            if 20 <= ty <= 21 and (tx in (1, 2) or tx in (W - 3, W - 2)): c = mul(c, 1.1)       # the front pockets' tops
        elif face == "back" and 18 <= ty <= 19 and tx in (1, W // 2, W - 2): c = mul(c, 0.8)
        elif face in ("right", "left") and 18 <= ty <= 19 and tx == W // 2: c = mul(c, 0.8)
        return c, 255
    sk.box(16, 16, 8, 12, 4, torso, 81)
    def leg(seed):
        def fn(face, tx, ty, W, H, fwd):
            if face == "top": return mul(JEANS, 0.9), 255
            if face == "bottom": return mul(JEANS, INSIDE), 255                                 # inside the hem (11 px)
            c = cloth(JEANS, tx, ty, W, H, seed, 0.08)
            if face == "front" and ty == 0 and 1 <= tx <= 4: c = mul(c, 1.12)                   # the front pocket's edge
            if face == "back" and 0 <= ty <= 4 and tx in (1, W - 2): c = mul(c, 1.1)            # the back pocket
            if face == "back" and ty == 4 and 1 <= tx <= W - 2: c = mul(c, 1.1)
            if face in ("right", "left") and fwd < 0.2: c = mul(c, 0.92)
            if face == "front" and 9 <= ty <= 12: c = mul(c, 1.06)                              # worn knee
            if face in ("right", "left") and tx == W // 2: c = mul(c, 1.07)                     # side seam
            if 20 <= ty <= 21: c = mul(c, 0.88 if ty == 21 else 0.96)                           # the hem
            return c, 255
        return fn
    sk.box(0, 16, 4, 12, 4, leg(82), 82)
    sk.box(16, 48, 4, 12, 4, leg(83), 83)

def shoes(sk):
    def leg(seed):
        def fn(face, tx, ty, W, H, fwd):
            if face == "top": return mul(SHOE, 0.5), 255                                        # the collar's inside (10.25 px)
            if face == "bottom": return SOLE, 255
            c = mul(SHOE, 1.04 - 0.03 * (ty - 20) + 0.05 * vnoise(tx, ty, 3, seed))
            if ty <= 21: c = mul(c, 0.86 if ty <= 20 else 0.95)                                 # the collar's padding
            if ty == 23: c = mix(SOLE, SHOE, 0.25)                                              # the sole's edge
            if face == "front":
                if ty == 21 and 1 <= tx <= W - 2: c = (128, 110, 88) if tx % 2 else mul(SHOE, 0.8)  # laces across the tongue
                if ty == 22 and 2 <= tx <= W - 3: c = mul(c, 1.08)                              # the toe cap's light
            if face in ("right", "left") and fwd > 0.8 and ty == 22: c = mul(c, 1.06)
            return c, 255
        return fn
    sk.box(0, 16, 4, 12, 4, leg(91), 91)
    sk.box(16, 48, 4, 12, 4, leg(92), 92)

GAME = os.path.join(ROOT, "src", "main", "resources", "assets", "apocalypse_firstlight", "textures", "entity")
os.makedirs(OUT, exist_ok=True)
os.makedirs(os.path.join(GAME, "clothing"), exist_ok=True)
for name, female in (("male_v1", False), ("female_v1", True)):
    s = paint(female)
    write_png(os.path.join(OUT, name + ".png"), 128, 128, s.px)
    write_png(os.path.join(GAME, "survivor", "afl_" + name + ".png"), 128, 128, s.px)   # the starting skins (SurvivorSkins#starter)
    print("wrote", name)
for name, fn, sleeves in (("shirt", shirt, True), ("jacket", jacket, True), ("jeans", lambda sk, slim: jeans(sk), False),
                          ("shoes", lambda sk, slim: shoes(sk), False)):
    for slim in ((False, True) if sleeves else (False,)):
        sk = Skin()
        fn(sk, slim)
        write_png(os.path.join(GAME, "clothing", name + ("_slim" if slim else "") + ".png"), 128, 128, sk.px)
        print("wrote", name + ("_slim" if slim else ""))
