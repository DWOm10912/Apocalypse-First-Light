"""Male V1 head, preset face 'm1' (round 2: stylised low poly, flat shaded), world mm, rest pose, front = -Y.

A ring loft whose rings may tilt: each ring has its own height at the front (zF), the side (zS) and the back (zB),
so the jaw line runs as one edge loop from the chin up to below the ear, and the brow / eye / nose / mouth bands sit
on loops. The cross-section is a superellipse (half width W, front depth F, back depth B about y0, squareness n),
narrowed at the front (ft) and back (bt). FEATURES are small radial offsets per ring and column (simple blocks for the
nose, brow, sockets, cheekbones, lips and chin; the face itself is painted). The ears are separate low-poly pieces.
The bottom ring is the body's neck ring (+ chord midpoints), so head and body meet without a gap.
Round 1 (ray-sampled distance field, smooth shaded, 1.05 scale) was rejected 2026-10-24: skull too large, mid face
flat, mask-like profile. Round 3 (user notes 2026-10-24, 'blank template face'): two more face columns and a ring under
the nose, wider skull, square jaw edge, broad chin, nose wings and philtrum, a brow ridge over deeper sockets.
"""
import math

import numpy as np

# column angles (deg from the front, +x side), mirrored to -x: 20 columns (7 and 24 carry the nose wings, the
# philtrum, the lips and the eye patch, face_male_v1)
COLS = [0, 7, 15, 24, 32, 52, 75, 100, 130, 155, 180]

#     zF    zS    zB     W   F   B   y0   n    ft    bt
RINGS = [
    (1541, 1574, 1557, 55, 70, 54, 14, 2.6, 0.32, 0.10),   # 0 under the jaw
    (1551, 1592, 1566, 64, 90, 55, 12, 3.0, 0.40, 0.16),   # 1 jaw line: chin bottom -> jaw angle (defined, not boxy)
    (1566, 1601, 1576, 68, 94, 57, 12, 2.9, 0.33, 0.18),   # 2 chin (broad)
    (1585, 1608, 1587, 69, 97, 60, 12, 2.9, 0.28, 0.18),   # 3 lower lip border / mouth corners
    (1601, 1616, 1599, 70, 98, 64, 12, 2.8, 0.25, 0.15),   # 4 upper lip border
    (1610, 1622, 1605, 71, 98, 67, 12, 2.7, 0.22, 0.12),   # 5 under the nose: philtrum top, nostrils
    (1621, 1630, 1614, 72, 97, 72, 12, 2.6, 0.19, 0.10),   # 6 nose tip and wings
    (1637, 1642, 1630, 75, 97, 82, 12, 2.6, 0.12, 0.05),   # 7 nose, cheekbones
    (1650, 1653, 1648, 76, 96, 89, 12, 2.5, 0.09, 0.02),   # 8 under the eyes
    (1662, 1662, 1662, 77, 96, 94, 12, 2.4, 0.07, 0.00),   # 9 eyes
    (1673, 1674, 1675, 78, 98, 97, 12, 2.4, 0.06, 0.00),   # 10 brow
    (1697, 1697, 1697, 77, 92, 98, 12, 2.3, 0.04, 0.00),   # 11 forehead
    (1723, 1723, 1723, 74, 81, 93, 12, 2.3, 0.02, 0.00),
    (1745, 1745, 1745, 66, 66, 80, 12, 2.2, 0.00, 0.00),
    (1761, 1761, 1761, 48, 47, 58, 12, 2.1, 0.00, 0.00),
]
TOP = (0.0, 16.0, 1770.0)

# radial offsets (mm) per ring index and column angle (both sides)
FEATURES = {
    0: {32: -1, 52: -2, 75: -2},                                 # tucked under the jaw: a clear jaw edge
    1: {0: 2, 7: 2, 15: 1.5, 52: 1, 75: 1.5, 100: 1},            # jaw line, the angle only a little out
    2: {0: 3.5, 7: 3.5, 15: 2.5, 24: 1.5, 32: 0.5},              # broad, rounded chin, not pointed
    3: {0: 1, 7: 1},                                             # lower lip border
    4: {0: 1.8, 7: 2.2, 15: 1.2},                                # upper lip border, the bow at 7
    5: {0: 2.5, 7: 3.5, 15: 1.8, 24: 0.5},                       # philtrum groove between the ridges
    6: {0: 22, 7: 13, 15: 6, 24: 1, 32: 1},                      # nose tip, wings
    7: {0: 15, 7: 7, 15: 2, 32: 2, 52: 5, 75: 2},                # nose, cheekbones
    8: {0: 10, 7: 5, 15: 1, 24: -1, 32: -1, 52: 3, 75: 1},       # bridge, under the eye, cheekbones
    9: {0: 6, 7: 3, 24: -2, 32: -2.5, 52: 2},                       # bridge, sockets; 52 level with 8 / 10 (round 5)
    10: {0: 3, 7: 3, 15: 3, 24: 3.5, 32: 3.5, 52: 1.5},                # brow ridge over a deeper socket
}


def ring_point(r, i, a_deg):
    zF, zS, zB, W, F, B, y0, n, ft, bt = r
    a = math.radians(a_deg)
    s, c = math.sin(a), math.cos(a)
    e = 2.0 / n
    x = W * math.copysign(abs(s) ** e, s)
    y = math.copysign(abs(c) ** e, c)
    x *= 1 - ft * max(0.0, c) ** 2 - bt * max(0.0, -c) ** 2
    y = y0 - (F if c > 0 else B) * y
    z = zS + ((zF - zS) * c ** 1.5 if c > 0 else (zB - zS) * (-c) ** 1.5)
    off = FEATURES.get(i, {}).get(abs(round(a_deg)), 0.0)
    return np.array([x + s * off, y - c * off, z], float)


def angles():
    """Signed column angles, ordered around (+x side first from the front)."""
    a = COLS + [-c for c in reversed(COLS[1:-1])]
    return sorted(a, key=lambda x: x % 360)


def grid():
    """Rows of points (bottom up) over angles(); the last row is the crown point."""
    A = angles()
    rows = [[ring_point(r, i, a) for a in A] for i, r in enumerate(RINGS)]
    rows.append([np.array(TOP, float)])
    return rows


# ear: outline in the ear plane (u back, v up, mm from the ear centre), a rim ring and a flatter inner ring
EAR_C = (76.0, 14.0, 1646.0)
EAR = [(-5, 25), (6, 27), (13, 19), (15, 6), (11, -9), (5, -22), (-4, -25), (-8, -14), (-9, 4), (-8, 17)]


def ears():
    """Each ear outline point: (rim, inner, root) for +x; mirrored by the caller."""
    tilt = math.radians(12)          # the top leans back
    out = []
    for u, v in EAR:
        uu = u * math.cos(tilt) + v * math.sin(tilt)
        vv = -u * math.sin(tilt) + v * math.cos(tilt)
        base = np.array([EAR_C[0], EAR_C[1] + uu, EAR_C[2] + vv])
        rim = base + np.array([8.0 + 0.12 * u, 0, 0])
        inner = np.asarray(EAR_C) + 0.55 * (base - EAR_C) + np.array([4.0, 2.0, 0])
        root = np.asarray(EAR_C) + 0.7 * (base - EAR_C) + np.array([-7.0, 0, 0])
        out.append((rim, inner, root))
    return out
