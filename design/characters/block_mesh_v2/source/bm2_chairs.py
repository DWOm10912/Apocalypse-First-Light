"""AFL Block-Mesh V2 chairs (user 2026-10-25, docs/dev/block_mesh_v2_b_ingame.md 11): B3 on the wood and office chair
before (the round-1 chair path: ankles 7 px ahead of the model origin, the eye's root shift) and after ChairLegs, the
same renders and the contact numbers against the chairs' measured boxes (metres, floor y 0, the seat's middle x = z = 0,
the sitter facing +z):
  wood    seat x +-0.29, z -0.24..0.21, 0.43..0.465 (hard; 58 x 45 cm since 2026-10-25, was 42 x 42); back -0.23..-0.20
  office  seat x +-0.394, z -0.28..0.352, 0.42..0.547 (padded); back -0.28..-0.20; star base radius 0.41, up to 0.15

    python -I bm2_chairs.py <project> <poses dir> <out dir> <before poses json>
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
BEFORE = sys.argv.pop(4)
import bm2_round3b as R3  # noqa: E402  (B3 with the clavicle hold, its sheets' helpers)

T, E, Q = R3.T, R3.E, R3.Q
FBEFORE = E.frames(BEFORE)
E.PROTOS['B3b'] = E.PROTOS['B3']
E.frames_of = lambda p: FBEFORE if p == 'B3b' else E.FB2
E.ROW_NAME.update({'B3b': '修正前', 'B3': '修正后'})
ROWS = ['B3b', 'B3']
_body = R3.body


def body(row, name):
    fr = E.frames_of(row)[name]
    M = E.bone_matrices(E.PROTOS[row], fr, None, False)
    R3.B3.clavicle_hold(M, E.PROTOS[row].pivot)
    return fr, M


Q.body = body
SEAT = {'chair_wood': dict(x=0.29, z=(-0.24, 0.21), top=0.465, back=-0.198),
        'chair_office': dict(x=0.394, z=(-0.28, 0.352), top=0.547, back=-0.20)}


def contacts(row, name):
    fr, M = body(row, name)
    P = E.PROTOS[row]
    X = P.skin(M)
    s = SEAT[name]
    part = P.vpart
    legs = np.array([p.startswith(('leg_', 'foot_')) for p in part])
    feet = np.array([p.startswith('foot_') for p in part])
    torso = np.array([p in ('torso', 'waist') for p in part])
    inside = legs & (np.abs(X[:, 0]) < s['x']) & (X[:, 2] > s['z'][0]) & (X[:, 2] < s['z'][1]) & (X[:, 1] < s['top'] - 0.005) & (X[:, 1] > 0.42)
    # how deep below the seat top (thigh into the cushion) and how far behind the front edge (shin into its front)
    depth = float((s['top'] - X[inside, 1]).max()) if inside.any() else 0.0
    edge = float((s['z'][1] - X[inside & (X[:, 1] < s['top'] - 0.03), 2]).max()) if (inside & (X[:, 1] < s['top'] - 0.03)).any() else 0.0
    sole = float(X[feet, 1].min())
    toe_floor = sole
    star = 0.0
    if name == 'chair_office':
        k = feet & (X[:, 1] < 0.15)
        r = np.hypot(X[k, 0], X[k, 2])
        star = float((0.41 - r).max()) if k.any() else -1.0
    back = float(s['back'] - X[torso, 2].min())     # > 0: the torso's back into the backrest
    # the office chair's armrest pads (x 0.328..0.416, y 0.626..0.678, z -0.12..0.28): the arm's lowest point over a pad's
    # footprint (< 0.678 m: through it; the arm is a box, so its vertices alone miss a pad between them)
    arm = np.array([p.startswith(('sleeve_', 'hand_', 'arm_')) for p in part])
    if name == 'chair_office':
        A = densify(P, M, arm)
        k = (np.abs(A[:, 0]) > 0.328) & (np.abs(A[:, 0]) < 0.416) & (A[:, 2] > -0.12) & (A[:, 2] < 0.28)
        armrest = float(A[k, 1].min()) if k.any() else 9.99
    else:
        armrest = 9.99
    hip = (M['thigh_r'] @ np.append(P.pivot['thigh_r'], 1))[:3]
    knee = (M['shin_r'] @ np.append(P.pivot['shin_r'], 1))[:3]
    return dict(armrest=armrest, depth=depth * 100, edge=edge * 100, sole=toe_floor * 100, star=star * 100, back=back * 100,
                hip=hip, knee=knee)


def densify(P, M, keep, n=6):
    """Points over the faces of the kept parts (bilinear in each quad's rest corners, then skinned)."""
    V = P.m['V']
    pts = []
    for qi in range(0, len(P.m['F']), 2):
        f = P.m['F'][qi]
        if not keep[f[0]]:
            continue
        base = min(f.min(), P.m['F'][qi + 1].min())
        c = V[base:base + 4]
        for a in np.linspace(0, 1, n):
            for b in np.linspace(0, 1, n):
                pts.append((1 - a) * (1 - b) * c[0] + a * (1 - b) * c[1] + a * b * c[2] + (1 - a) * b * c[3])
    pts = np.array(pts)
    # skin each point with the nearest rest vertex's weights
    idx = np.argmin(((pts[:, None, :] - V[None, keep, :]) ** 2).sum(2), axis=1)
    src = np.where(keep)[0][idx]
    out = np.zeros((len(pts), 3))
    K = np.array([M[nm] for nm in P.names])
    ph = np.c_[pts, np.ones(len(pts))]
    for k in range(4):
        out += P.W4[src, k:k + 1] * np.einsum('nij,nj->ni', K[P.J4[src, k]], ph)[:, :3]
    return out


def main():
    cw, ch = 260, 320

    def cell(sub, r, c):
        name, view = c[:2]
        draws, _ = Q.scene(r, name)
        E.render_cell(sub, draws, E.cell_camera(view, cw, ch, 175, target=(0, 0.7, 0.12)))
    cols = [(n, v, '%s %s' % ('木椅' if n == 'chair_wood' else '办公椅', t)) for n in ('chair_wood', 'chair_office')
            for v, t in ((90, '侧面'), (30, '30°'), (0, '正面'))]
    img = E.grid(ROWS, cols, cw, ch, cell, left=80)
    E.save_sheet(img, 'chairs_b3.png', ROWS, cols, cw, ch, 'B3 坐椅子：髋和脚按椅子尺寸放（ChairLegs），修正前 / 后同一相机',
                 left=80, colname=lambda c: c[2])
    print('cm: legs below the seat top / behind its front edge, lowest sole over the floor, foot inside the star base, '
          'torso into the backrest (negative = gap); hip and knee (y, z) m; arm low over the armrest pad (m, office; < 0.678 through it)')
    for name in ('chair_wood', 'chair_office'):
        for r in ROWS:
            c = contacts(r, name)
            print('  %-13s %-4s seat %5.1f  edge %5.1f  sole %5.1f  star %5.1f  back %5.1f  hip (%.3f, %.3f) knee (%.3f, %.3f)  armrest %.3f' % (
                name, r, c['depth'], c['edge'], c['sole'], c['star'], c['back'], c['hip'][1], c['hip'][2], c['knee'][1], c['knee'][2], c['armrest']))


if __name__ == '__main__':
    main()
