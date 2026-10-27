# Female body V1: two 128 x 128 skins for the old body (vanilla 64 net doubled, 2 x 2 texels a model px) in the infected
# Z0 r6 style (B, design/creatures/infected_z0: Minecraft Story Mode look): flat painted colour in soft value steps, a
# slightly darker rim on every face, vertical cloth folds, hair as strands; no photo noise. Hair lives on the second
# layer's head (the hat), the future separate hair layer; the rest of the second layer stays empty.
#   python -I tools/characters/old_body_skin_paint_v1.py
# Out: design/characters/female_body_v1/skins/{male,female}_v1.png
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
SOLE = (34, 30, 28)

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

    # ---- torso: T-shirt, jeans from the waist
    shirt_end = 20 if female else 19
    def torso(face, tx, ty, W, H, fwd):
        if face == "top":
            neck = (W // 2 - 3 <= tx <= W // 2 + 2) and ty >= 2 if female else (W // 2 - 2 <= tx <= W // 2 + 1) and ty >= 3
            return (skin_at(0, 1) if neck else mul(SHIRT, 1.04)), 255
        if face == "bottom": return mul(JEANS, 0.8), 255
        if ty > shirt_end:
            c = cloth(JEANS, tx, ty, W, H, 31, 0.05)
            if ty == shirt_end + 1: c = mul(c, 0.85)                                        # waistband shadow
            return c, 255
        if face == "front":
            mid = (W - 1) / 2
            if female and ty <= 1 and abs(tx - mid) <= 2.5 - ty: return skin_at(ty, H), 255   # scoop neck
            if female and ty <= 2 and abs(tx - mid) <= 3.5 - ty: return mul(SHIRT, 0.86), 255  # its hem
            if not female and ty == 0 and abs(tx - mid) <= 2: return skin_at(ty, H), 255
            c = cloth(SHIRT, tx, ty, W, H, 32)
            if 2 <= ty <= 7 and 2 <= tx <= W - 3: c = mul(c, 1.04)                              # the chest catches the light
            if tx in (0, W - 1): c = mul(c, 0.94)
            if ty >= shirt_end - 1: c = mul(c, 0.9)                                            # hem
            return c, 255
        if face == "back":
            mid = (W - 1) / 2
            if ty == 0 and abs(tx - mid) <= 1.5: return skin_at(ty, H), 255
        c = cloth(SHIRT, tx, ty, W, H, 33 if face == "back" else 34)
        if face in ("right", "left"): c = mul(c, 0.9 + 0.06 * fwd)                            # under the arms
        if face == "back" and 2 <= ty <= 8 and tx in (W // 2 - 1, W // 2): c = mul(c, 0.95)   # the spine's fold
        if ty >= shirt_end - 1: c = mul(c, 0.9)
        return c, 255
    sk.box(16, 16, 8, 12, 4, torso, 3)

    # ---- arms: short sleeves, then skin
    aw = 3 if female else 4
    def arm(seed):
        def fn(face, tx, ty, W, H, fwd):
            if face == "top": return mul(SHIRT, 1.04), 255
            if face == "bottom": return mul(SKIN_SH, 0.92), 255
            if ty <= 8:
                c = cloth(SHIRT, tx, ty, W, H, seed)
                if face in ("right", "left") and fwd < 0.5 and ty in (4, 5) and tx % 3 == 1: c = mul(c, 0.9)
                return (mul(c, 0.86) if ty == 8 else mul(c, 1.05) if ty == 7 else c), 255
            c = skin_at(ty, H)
            if ty == 9: c = mul(c, 0.9)                                                        # under the sleeve
            if ty >= 21: c = mul(c, 0.96 if ty < 23 else 0.9)                                  # the hand
            return c, 255
        return fn
    sk.box(40, 16, aw, 12, 4, arm(41), 4)
    sk.box(32, 48, aw, 12, 4, arm(42), 5)

    # ---- legs: jeans, boots
    def leg(seed):
        def fn(face, tx, ty, W, H, fwd):
            if face == "top": return mul(JEANS, 0.95), 255
            if face == "bottom": return SOLE, 255
            if ty >= 20:
                c = mul(BOOT, 1.05 - 0.04 * (ty - 20)) if ty < 23 else SOLE
                if ty == 20: c = mul(c, 0.8)                                                   # the cuff's shadow on the boot
                if face == "front" and ty == 21 and tx in (3, 4): c = (150, 132, 108)          # laces
                return c, 255
            c = cloth(JEANS, tx, ty, W, H, seed, 0.08)
            if face == "front" and ty == 2 and 1 <= tx <= 4: c = mul(c, 1.12)                     # front pocket's top
            if face == "back" and 1 <= ty <= 5 and tx in (1, W - 2): c = mul(c, 1.1)              # back pocket's sides
            if face == "back" and ty == 5 and 1 <= tx <= W - 2: c = mul(c, 1.1)
            if face in ("right", "left") and fwd < 0.2: c = mul(c, 0.92)
            if face == "front" and 9 <= ty <= 12: c = mul(c, 1.06)                             # worn knee
            if face in ("right", "left") and tx == W // 2: c = mul(c, 1.07)                     # side seam
            if ty == 19: c = mul(c, 0.88)                                                      # cuff
            return c, 255
        return fn
    sk.box(0, 16, 4, 12, 4, leg(51), 6)
    sk.box(16, 48, 4, 12, 4, leg(52), 7)
    return sk

os.makedirs(OUT, exist_ok=True)
for name, female in (("male_v1", False), ("female_v1", True)):
    s = paint(female)
    write_png(os.path.join(OUT, name + ".png"), 128, 128, s.px)
    print("wrote", name)
