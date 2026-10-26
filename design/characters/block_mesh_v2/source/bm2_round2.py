"""AFL Block-Mesh V2 round 2 (user 2026-10-25): the in-game B against B2 in the same scenes.

Both rows are solved by the game's own poser, gun stance and chair path on their game rigs (BlockMeshV2Export):
  poses/poses_b_game.json  the round-1 rig (hand end (0, 10.5), hand half 1.0)
  poses/poses_b2.json      rigs/afl_block_mesh_v2.json (B2)
no prototype target adaptation; the toes clamped on the ground as BlockMeshBody does. The round-1 B is drawn at its
in-game 256 x 256 density (4 texels / px) with its old painter, B2 with bm2_game.py's.

    python -I bm2_round2.py <project> <poses dir> <out dir>
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bm2_build as B  # noqa: E402

B.TEX = 4             # the round-1 game skin: prototype B's painter at 256 x 256
PRJ, POSES, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
sys.argv = [sys.argv[0], PRJ, POSES, OUT]
import bm2_eval as E  # noqa: E402

FBG = E.frames(os.path.join(POSES, 'poses_b_game.json'))
E.frames_of = lambda p: FBG if p == 'B' else E.FB2 if p == 'B2' else E.FV
E.ROW_NAME.update({'B': 'B 第一轮（实机版）', 'B2': 'B2 本轮修正'})
ROWS = ['B', 'B2']


def body(row, name):
    fr = E.frames_of(row)[name]
    M = E.bone_matrices(E.PROTOS[row], fr, None, False)
    if not fr.get('chair_game') and 'seat' not in fr:
        for s in 'rl':
            E.toe_clamp(E.PROTOS[row], M, s, 0.0)
    return fr, M


def scene(row, name):
    fr, M = body(row, name)
    draws = [E.PROTOS[row].draw(M)] + E.props_draws(fr)
    if name in ('chair_wood', 'chair_office'):
        # the game's rider sits at the chair's centre (furniture() puts the cushion 5 cm behind it, for the prototypes)
        draws += [E.Draw(d.P + np.array([0, 0, 0.05]), d.F, d.UV, d.tex, d.cols) for d in E.furniture(name)]
    draws += E.env_ground(name)
    return draws, M


def point(row, M, bone, off=(0, 0, 0)):
    return (M[bone] @ np.append(E.PROTOS[row].pivot[bone] + np.array(off), 1))[:3]


def low_cam(cw, ch):
    tgt = np.array([0, 1.42, 0.0])
    a = math.radians(25)
    eye = tgt + 4.0 * np.array([math.sin(a), -0.62, math.cos(a)])
    return E.Camera(eye, tgt, cw, ch, scale=330)


def hand_cam(row, M, view, cw, ch):
    tgt = point(row, M, 'hand_r', (0, -1.0, 0))
    a = math.radians(view)
    return E.Camera(tgt + 3.0 * np.array([math.sin(a), 0.15, math.cos(a)]), tgt, cw, ch, scale=520)


COLS = [('stand', 0, '正面'), ('stand', 45, '45°'), ('stand', 90, '侧面'), ('low', 0, '仰视近景'),
        ('hand', -20, '右手 正面'), ('hand', -100, '右手 侧面'),
        ('gun_p9', 45, 'P9-01 持枪（待机）'), ('gun_br51', 45, 'BR-51 持枪（待机）'),
        ('chair_wood', 90, '木椅 侧面'), ('chair_office', 55, '办公椅 45°')]


def main():
    cw, ch = 200, 320

    def cell(sub, r, c):
        name, view, _ = c
        if name == 'low':
            draws, _ = scene(r, 'stand')
            cam = low_cam(cw, ch)
        elif name == 'hand':
            draws, M = scene(r, 'stand')
            cam = hand_cam(r, M, view, cw, ch)
        elif name.startswith('chair'):
            draws, _ = scene(r, name)
            cam = E.cell_camera(view, cw, ch, 165, target=(0, 0.72, 0.1))
        else:
            draws, _ = scene(r, name)
            cam = E.cell_camera(view, cw, ch, 150, target=(0, 0.98, 0.05))
        E.render_cell(sub, draws, cam)
    for part, cols in (('a', COLS[:6]), ('b', COLS[6:])):
        img = E.grid(ROWS, cols, cw, ch, cell, left=120)
        E.save_sheet(img, 'round2_compare_%s.png' % part, ROWS, cols, cw, ch,
                     'Block-Mesh V2 第二轮：B（第一轮实机版） 对 B2（头、肩、上躯干、手、皮肤）；同一姿态求解、相机、灯光',
                     left=120, colname=lambda c: c[2])
    measure()


def measure():
    """Head / shoulder / hand numbers of both rows (rest pose, px), for the report."""
    for r in ROWS:
        m = E.PROTOS[r].m
        V = m['V']
        part = E.PROTOS[r].vpart
        out = {}
        for p in ('head', 'torso', 'arm_r'):
            k = np.array([q == p for q in part])
            lo, hi = V[k].min(0), V[k].max(0)
            out[p] = tuple(round(float(x), 2) for x in (hi - lo))
        print(r, out)


if __name__ == '__main__':
    main()
