"""AFL Block-Mesh V2 round 3 (user 2026-10-25): B (round 1, in game), B2 and B3 (continuous box arms, bm2_b3.py) in the
same scenes, plus the arms' clipping numbers. B2 and B3 share one rig, so B3 is drawn on B2's solved poses (poses_b2.json):
the hand targets, joints and actions are the same; only the arm's surface differs.

    python -I bm2_round3.py <project> <poses dir> <out dir>
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bm2_b3  # noqa: E402,F401  (registers B3; before bm2_eval builds B2's texture through bm2_game.paint)
import bm2_round2 as Q  # noqa: E402

E = Q.E
E.PROTOS['B3'] = E.Proto('B3')
E.frames_of = lambda p: Q.FBG if p == 'B' else E.FB2 if p in ('B2', 'B3') else E.FV
E.ROW_NAME.update({'B2': 'B2（第二轮）', 'B3': 'B3 连续方柱臂'})
ROWS = ['B', 'B2', 'B3']
OUT = Q.OUT


def arm_parts(row, s):
    return ('sleeve_' + s, 'hand_' + s) if row == 'B3' else ('arm_' + s,)


def arm_cam(row, M, cw, ch, scale=420):
    """The right arm from its outer side, square to its bend (sheet_arms' camera)."""
    P = E.PROTOS[row]
    p = lambda b: (M[b] @ np.append(P.pivot[b], 1))[:3]
    sh, el, wr = p('upper_arm_r'), p('forearm_r'), p('hand_r')
    tgt = el * 0.6 + wr * 0.4
    n = np.cross(el - sh, wr - el)
    n = E.unit(n) if np.linalg.norm(n) > 1e-6 else np.array([-1.0, 0, 0])
    if n[0] > 0:
        n = -n
    return E.Camera(tgt + 3 * n, tgt, cw, ch, scale=scale)


def supersample(cell_fn, sub, k=2):
    """Render k x larger and box-filter down (stands in for the game's mip levels at a distance)."""
    h, w = sub.shape[:2]
    big = np.full((h * k, w * k, 3), sub[0, 0], np.uint8)
    cell_fn(big, w * k, h * k)
    sub[:] = big.reshape(h, k, w, k, 3).mean((1, 3)).astype(np.uint8)


GROUND = None


def wide_ground():
    global GROUND
    if GROUND is None:
        GROUND = E.ground(14, 0, E.grass_tex(), tile=0.5)
    return GROUND


def sheet_a():
    cw, ch = 200, 320

    def cell(sub, r, c):
        name, view, _ = c
        if name == 'low':
            draws, _ = Q.scene(r, 'stand')
            cam = Q.low_cam(cw, ch)
        elif name == 'hand':
            draws, M = Q.scene(r, 'stand')
            tgt = Q.point(r, M, 'hand_r', (0, -1.6, 0))      # the hand's middle (wrist 14.5, end 11.5 / 12)
            a = math.radians(view)
            cam = E.Camera(tgt + 3.0 * np.array([math.sin(a), 0.15, math.cos(a)]), tgt, cw, ch, scale=600)
        else:
            draws, _ = Q.scene(r, name)
            cam = E.cell_camera(view, cw, ch, 150, target=(0, 0.98, 0.05))
        E.render_cell(sub, draws, cam)
    cols = [('stand', 0, '正面'), ('stand', 45, '45°'), ('stand', 90, '侧面'), ('low', 0, '仰视近景'),
            ('hand', -20, '右手 正面'), ('hand', -100, '右手 侧面')]
    img = E.grid(ROWS, cols, cw, ch, cell, left=120)
    E.save_sheet(img, 'round3_outline.png', ROWS, cols, cw, ch, 'Block-Mesh V2 第三轮：手臂改成连续方柱 + 独立袖子（B3），对比 B、B2',
                 left=120, colname=lambda c: c[2])


def sheet_b():
    cw, ch = 210, 300

    def cell(sub, r, c):
        name, view, kind = c
        if kind == 'arm':
            _, M = Q.body(r, name)
            draws = [E.PROTOS[r].draw(M, keep=lambda p: p in arm_parts(r, 'r'))]
            cam = arm_cam(r, M, cw, ch)
        elif kind == 'seat':
            draws, _ = Q.scene(r, name) if not name.startswith('t15') else scene_t15(r, name)
            cam = E.cell_camera(view, cw, ch, 165, target=(0, 0.72, 0.1)) if not name.startswith('t15') else \
                E.cell_camera(view, cw, ch, 150, target=(0, 1.3, 0.18))
        else:
            draws, _ = Q.scene(r, name)
            cam = E.cell_camera(view, cw, ch, 150, target=(0, 0.98, 0.05))
        E.render_cell(sub, draws, cam)
    cols = [('run', 0, 'arm', '弯肘（跑，单看右臂）'), ('p9_reload_40', 0, 'arm', '弯肘（P9 换弹，单看右臂）'),
            ('gun_p9', 45, 'body', 'P9-01 持枪'), ('gun_br51', 45, 'body', 'BR-51 持枪'), ('p9_reload_40', 30, 'body', 'P9-01 换弹'),
            ('crowbar', 60, 'body', '撬棍'), ('chair_wood', 90, 'seat', '木椅'), ('t15_driver_085', 90, 'seat', 'T15 驾驶 0.85')]
    cols = [(a, b, k, t) for a, b, k, t in cols]
    img = E.grid(ROWS, cols, cw, ch, lambda sub, r, c: cell(sub, r, c[:3]), left=120)
    E.save_sheet(img, 'round3_actions.png', ROWS, cols, cw, ch, '弯肘、持枪、换弹、工具、座位（同一套动作求解，没改动作和握枪目标）',
                 left=120, colname=lambda c: c[3])


def scene_t15(row, name):
    fr, M = Q.body(row, name)
    draws = [E.PROTOS[row].draw(M)] + E.props_draws(fr) + E.t15_interior(True)
    return draws, M


def sheet_c():
    """F5 distances: the game's third-person cameras 4 m from the eye (70 deg), a passer-by at 8 m."""
    cw, ch = 560, 315

    def cell(sub, r, c):
        name, eye, tgt, fov = c[0], c[1], c[2], c[3]

        def draw(img, w, h):
            draws, _ = Q.scene(r, name)
            draws = [d for d in draws if d not in E.env_ground(name)] + [wide_ground()]
            zb = np.full(img.shape[:2], -1e9)
            E.render(img, zb, draws, E.Camera(eye, tgt, w, h, fov=fov), sky=None)
        supersample(draw, sub)
    cols = [('stand', (0.0, 1.62, -4.0), (0, 1.62, 0), 70, 'F5 背后 4 m'), ('stand', (0.0, 1.62, 4.0), (0, 1.62, 0), 70, 'F5 正面 4 m'),
            ('stand', (2.0, 1.6, 7.7), (0, 1.0, 0), 70, '8 m 外的路人'), ('gun_p9', (1.0, 1.62, -4.0), (0, 1.4, 0), 70, 'F5 背后 持 P9')]
    img = E.grid(ROWS, cols, cw, ch, cell, left=120)
    E.save_sheet(img, 'round3_game_distance.png', ROWS, cols, cw, ch, '正常游戏距离（透视 70°，2 倍超采样代替 mip）',
                 left=120, colname=lambda c: c[4])


# ---------------------------------------------------------------- clipping of the arm below the elbow
BOXES = [('chest', (-4.5, 18, -2.25), (4.5, 24, 2.25)), ('pelvis', (-4.0, 15, -2.0), (4.0, 18, 2.0)),
         ('thigh_r', (-4, 9.5, -2), (0, 15, 2)), ('thigh_l', (0, 9.5, -2), (4, 15, 2)), ('head', (-3.75, 24.5, -3.625), (3.75, 32, 3.625))]


def clip(row, M, s):
    P = E.PROTOS[row]
    X = P.skin(M)
    vid = np.where([p in arm_parts(row, s) for p in P.vpart])[0]
    vid = vid[P.m['V'][vid, 1] < G_ELBOW + 0.5]
    worst, where = 0.0, ''
    for bone, lo, hi in BOXES:
        inv = np.linalg.inv(M[bone])
        q = (inv @ np.c_[X[vid], np.ones(len(vid))].T).T[:, :3]
        d = np.min(np.minimum(q - np.array(lo), np.array(hi) - q), axis=1)
        if len(d) and d.max() > worst:
            worst, where = float(d.max()), bone
    return worst, where


G_ELBOW = 18.0


def run_clip():
    names = [n for n in E.FB2 if not n.startswith('t15')]
    print('arm below the elbow inside torso / thighs / head (px, deepest vertex), B2 against B3:')
    for n in names:
        line = []
        for r in ('B2', 'B3'):
            _, M = Q.body(r, n)
            for s in 'rl':
                d, w = clip(r, M, s)
                line.append('%s %s %.2f%s' % (r, s, d, (' ' + w) if d > 0.05 else ''))
        print('  %-16s %s' % (n, ' | '.join(line)))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    sheet_a()
    sheet_b()
    sheet_c()
    run_clip()
