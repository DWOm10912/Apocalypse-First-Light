"""AFL Block-Mesh V2 round 3b (user 2026-10-25): B3's shoulder before / after the clavicle hold (bm2_b3.SHOULDER_HOLD).
Same poses (poses_b2.json), cameras and light; only the sleeve's top ring and the clavicle bone differ.

    python -I bm2_round3b.py <project> <poses dir> <out dir>
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bm2_b3 as B3  # noqa: E402
import bm2_round3 as T  # noqa: E402

E, Q, B = T.E, T.Q, T.Q.B
OUT = Q.OUT

# before: the round-3 sleeve (its top ring on the upper arm), built with the flag off
def variant(name, hold, jacket):
    B3.SHOULDER_HOLD, B3.STRAIGHT_JACKET = hold, jacket
    B.EXTRA[name] = dict(B.EXTRA['B3'])
    E.R.VARIANTS[name] = E.R.VARIANTS['B3']
    B.ARMS[name] = B.ARMS['B3']
    E.PROTOS[name] = E.Proto(name)


variant('B3_0', False, False)       # round 3 as first sent
variant('B3_1', True, False)        # 3b: the shoulder top only
variant('B3', True, True)           # 3c: + the straight jacket
E.frames_of = lambda p: E.FB2
E.ROW_NAME.update({'B3_0': '第三轮原版', 'B3_1': '上次：只改肩顶', 'B3': '本次：直筒外套'})
ROWS = ['B3_0', 'B3_1', 'B3']
_body = Q.body


def body(row, name):
    fr, M = _body(row, name)
    if row in ('B3', 'B3_1'):
        B3.clavicle_hold(M, E.PROTOS[row].pivot)
    return fr, M


Q.body = body          # bm2_round2.scene / bm2_round3 sheets pose through it
T.arm_parts = lambda row, s: ('sleeve_' + s, 'hand_' + s)


def arm_parts(row, s):
    return 'sleeve_' + s, 'hand_' + s


def main():
    cw, ch = 220, 300

    def cell(sub, r, c):
        name, view, kind = c[:3]
        fr, M = Q.body(r, name)
        draws = [E.PROTOS[r].draw(M)] + E.props_draws(fr) + E.env_ground(name)
        if kind == 'arm':
            draws = [E.PROTOS[r].draw(M, keep=lambda p: p in arm_parts(r, 'r'))]
            cam = T.arm_cam(r, M, cw, ch)
        elif kind == 'close':
            tgt = (M['chest'] @ np.array([0, 21.0, 0, 1]))[:3]
            a = math.radians(view)
            cam = E.Camera(tgt + 3 * np.array([math.sin(a), 0.2, math.cos(a)]), tgt, cw, ch, scale=215)
        elif kind == 'up':
            a = math.radians(view)
            tgt = np.array([0, 1.35, 0])
            cam = E.Camera(tgt + 6 * np.array([math.sin(a), 0.12, math.cos(a)]), tgt, cw, ch, scale=150)
        else:
            cam = E.cell_camera(view, cw, ch, 150, target=(0, 0.98, 0.05))
        E.render_cell(sub, draws, cam)
    cols = [('stand', 0, 'body', '正面'), ('stand', 45, 'body', '45°'), ('stand', 0, 'close', '肩部 正面'), ('stand', 40, 'close', '肩部 45°'),
            ('walk', 45, 'body', '走（手臂摆动）'), ('arms_up', 0, 'up', '手臂抬起 正面'), ('arms_up', 45, 'up', '手臂抬起 45°'),
            ('run', 0, 'arm', '弯肘（跑，单看右臂）'), ('gun_p9', 45, 'body', 'P9-01 持枪')]
    img = E.grid(ROWS, cols, cw, ch, cell, left=120)
    E.save_sheet(img, 'round3c_torso_sides.png', ROWS, cols, cw, ch,
                 'B3 躯干两侧：去掉胸到腰的收腰（9 → 8 px），外套改成 9 × 4.5 直筒到下摆，下摆下面是 8 px 的腰带；动画、骨骼、肘腕没改',
                 left=120, colname=lambda c: c[3])
    gaps()


def gaps():
    """Stand, front view: the torso's side (its widest vertex at that height) and the sleeve's inner edge, px from the middle."""
    print('stand, right side, |x| px: torso side / sleeve inner edge / visible gap')
    for r in ROWS:
        P = E.PROTOS[r]
        _, M = Q.body(r, 'stand')
        X = P.skin(M)
        inv = np.linalg.inv(M['chest'])
        L = (inv @ np.c_[X, np.ones(len(X))].T).T[:, :3]          # in the chest's rest px
        line = []
        for y in (22.0, 20.0, 18.0, 16.5):
            tor = [L[i] for i in range(len(L)) if P.vpart[i] in ('torso',) and abs(L[i][1] - y) < 0.8 and L[i][0] < 0]
            slv = [L[i] for i in range(len(L)) if P.vpart[i] == 'sleeve_r' and abs(L[i][1] - y) < 2.0]
            ts = max(-p[0] for p in tor) if tor else float('nan')
            si = min(-p[0] for p in slv) if slv else float('nan')
            line.append('y %.1f: %.2f / %.2f / %.2f' % (y, ts, si, si - ts))
        print('  %-6s %s' % (r, ' | '.join(line)))


def numbers():
    """The shoulder top's tilt off the chest (deg) and the clavicle's turn, per frame; the arm-into-head check."""
    P = E.PROTOS['B3']
    print('frame: upper-arm turn off the chest / sleeve-top tilt off the chest, before -> after (deg, right arm)')
    for n in [k for k in E.FB2 if not k.startswith('t15')]:
        res = []
        for r in ROWS:
            _, M = Q.body(r, n)
            Rm = (np.linalg.inv(M['chest']) @ M['upper_arm_r'])[:3, :3]
            arm = math.degrees(math.acos(max(-1, min(1, (np.trace(Rm) - 1) / 2))))
            top = 'upper_arm_r' if r == 'B3_0' else 'clavicle_r'
            Rt = (np.linalg.inv(M['chest']) @ M[top])[:3, :3]
            res.append((arm, math.degrees(math.acos(max(-1, min(1, (np.trace(Rt) - 1) / 2))))))
        print('  %-15s arm %6.1f   top %5.1f -> %5.1f' % (n, res[0][0], res[0][1], res[1][1]))
    for r in ROWS:
        _, M = Q.body(r, 'arms_up')
        print('arms_up', r, 'arm below the elbow in the head / torso:', T.clip(r, M, 'r'))


if __name__ == '__main__':
    main()
