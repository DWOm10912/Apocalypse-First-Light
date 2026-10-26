"""B3 on the wooden chair: still, the foot fidgets (the right heel at the top of its bounce; either foot shifted) and the look at its edge (90 deg, the chest twisted 45); front, 30 deg, side, back 150.
    python -I chair_idles.py <project> <poses dir> <out dir> <before json> <source dir> <idles json>"""
import os
import sys

import numpy as np

sys.path.insert(0, sys.argv[5])
idles_json = sys.argv[6]
before = sys.argv[4]
sys.argv = sys.argv[:4] + [before]
import bm2_chairs as C  # noqa: E402

E = C.E
FR = E.frames(idles_json)
E.frames_of = lambda p: FR
out = sys.argv[3]
NAMES = ['none', 'bounce_r', 'shift_r', 'shift_l', 'look_r', 'look_l']
views = [0, 35, 90, 150]
cw, ch = 220, 280
img = np.full((ch * len(views), cw * len(NAMES), 3), 236, np.uint8)
chair = [E.Draw(d.P + np.array([0, 0, 0.05]), d.F, d.UV, d.tex, d.cols) for d in E.furniture('chair_wood')]
for i, n in enumerate(NAMES):
    fr, M = C.body('B3', 'chair_wood_' + n)
    draws = [E.PROTOS['B3'].draw(M)] + chair + E.env_ground('chair_wood')
    for j, v in enumerate(views):
        sub = img[j * ch:(j + 1) * ch, i * cw:(i + 1) * cw]
        E.render_cell(sub, draws, E.cell_camera(v, cw, ch, 165, target=(0, 0.72, 0.05)))
E.save_png(os.path.join(out, 'chair_idles.png'), img)
P = E.PROTOS['B3']
for n in NAMES:
    _, M = C.body('B3', 'chair_wood_' + n)
    X = P.skin(M)
    part = P.vpart
    legs = np.array([p.startswith(('leg_', 'foot_')) for p in part])
    rl = np.array([p in ('leg_r', 'foot_r') for p in part])
    ll = np.array([p in ('leg_l', 'foot_l') for p in part])
    torso = np.array([p in ('torso', 'waist') for p in part])
    arm_r = np.array([p.endswith('_r') and p.startswith(('sleeve', 'hand', 'arm')) for p in part])
    back = float((-0.198 - X[torso, 2]).max() * 100)   # + : the torso into the slats (cm)
    seat_in = legs & (np.abs(X[:, 0]) < 0.29) & (X[:, 2] > -0.24) & (X[:, 2] < 0.21) & (X[:, 1] < 0.46) & (X[:, 1] > 0.43)
    print('%-12s torso into back %.1f cm | legs in seat %d verts | feet lowest r %.3f l %.3f | right foot back z %.3f | right arm lowest z %.3f y %.3f' % (
        n, back, int(seat_in.sum()), X[rl, 1].min(), X[ll, 1].min(), X[rl, 2].max(), X[arm_r, 2].min(), X[arm_r, 1].min()))
print('ok')
