"""AFL Block-Mesh V2 female body, proportion proposals F1-F3 (user 2026-10-25: "先出方案图再说"; height exactly B3's,
sex and skin chosen by the player when making the character). Offline only: nothing here goes into the game.

Every proposal is B3 (bm2_b3.py) with the same 22 bones, joints, hand ends and solved poses (poses_b2.json): only the
box sizes and the sample skin differ, so every action, seat and gun stance stays the male body's. The arm boxes move in
with the narrower shoulders the way B2 moved them out (the joint stays at x 5.5, the box centre is free).

            shoulders  depth  arms (sleeve / hand)  bust (deep)  waist (from y)  belt / hips
  B3 male   9          4.5    3.25 / 3              -            -               8 / legs 8
  F1 light  8.5        4.25   3 / 2.75              0.5          -               7.5 / legs 8
  F2 mid    8          4      2.75 / 2.5            0.75         7.5 (19)        7.5 / legs 8
  F3 clear  8          4      2.75 / 2.5            1.0          7 (19.5)        7.5 / legs 8

Same height, eye height, head (7.5 x 7.5 x 7.25), legs and feet as B3. The sample skin is the same for F1-F3 (long
hair painted on the head and down the neck, the face's details a little finer), B3's jacket, tee and jeans colours.

    python -I bm2_female.py <project> <poses dir> <out dir>
writes female_compare_a.png (still: front, 45, side, back, F5 4 m behind / in front), female_compare_b.png (walk, P9-01,
BR-51, wooden chair, crouch, arms up) and prints the arms' clipping into the body against B3.
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bm2_b3 as B3  # noqa: E402
import bm2_round3 as R3  # noqa: E402  (sets up bm2_round2 / bm2_eval and B3's proto)

Q, E = R3.Q, R3.E
B, G = B3.B, B3.G
Ring, Tube = B.Ring, B.Tube

# ---------------------------------------------------------------- the proposals (Bedrock px; sizes on 0.25 px)
VARIANTS = {
    'F1': dict(sh=(4.25, 2.125), lower=None, step=None, arm_x=5.75, sleeve=(1.5, 1.5), hand=(1.375, 1.375), bust=0.5,
               belt=(3.75, 1.875), title='F1 轻微'),
    'F2': dict(sh=(4.0, 2.0), lower=(3.75, 2.0), step=19.0, arm_x=5.5, sleeve=(1.375, 1.375), hand=(1.25, 1.25), bust=0.75,
               belt=(3.75, 1.875), title='F2 适中'),
    'F3': dict(sh=(4.0, 2.0), lower=(3.5, 2.0), step=19.5, arm_x=5.5, sleeve=(1.375, 1.375), hand=(1.25, 1.25), bust=1.0,
               belt=(3.75, 1.875), title='F3 明显'),
}
BUST = dict(top=23.0, bottom=20.5, half_w=3.25)

# B3's torso weights by height, for the female rings at the same heights
TORSO_W = [(24, {'chest': 1}), (21.5, {'chest': 0.85, 'spine': 0.15}), (20.0, {'chest': 0.3, 'spine': 0.7}),
           (18.5, {'spine': 0.8, 'pelvis': 0.2}), (17.5, {'spine': 0.25, 'pelvis': 0.75}), (B3.HEM, {'spine': 0.1, 'pelvis': 0.9})]


def weight_at(y):
    for (y0, w0), (y1, w1) in zip(TORSO_W, TORSO_W[1:]):
        if y1 <= y <= y0:
            return B.lerp_w(w0, w1, (y0 - y) / (y0 - y1))
    return dict(TORSO_W[-1][1])


def tubes_of(v):
    P = VARIANTS[v]
    tag = v.lower()
    keep = [t for t in B3.tubes() if t.name in ('head', 'neck') or t.name.startswith(('leg_', 'foot_'))]
    for t in keep:
        t.paint = tag                                     # the female sample skin paints head and neck (hair)
    out = list(keep)
    # the jacket: straight from the shoulders, a square step in at the waist (F2 / F3) rather than B2's slanted taper. The
    # step is two boxes (the chest's bottom cap is the ledge), not two rings at one height: those make a face with no
    # texture area
    rings = []
    for y, w in TORSO_W:
        if P['step'] is not None and y < P['step']:
            break
        rings.append(Ring(y, (0, 0), P['sh'], w))
    if P['step'] is not None:
        rings.append(Ring(P['step'], (0, 0), P['sh'], weight_at(P['step'])))
        # the same depth as the chest (front and back flush: a 1/8 px ledge there aliased), only the sides step in; it
        # starts at the step itself, so nothing of it overlaps the chest's faces
        lower = [Ring(P['step'], (0, 0), P['lower'], weight_at(P['step']))]
        lower += [Ring(y, (0, 0), P['lower'], w) for y, w in TORSO_W if y < P['step']]
        out.append(Tube('torso_lower', 'y', lower, tag, caps=(False, True)))
    out.append(Tube('torso', 'y', rings, tag))
    # hair (a hairstyle part of the sample skin, not the body): long hair down the back of the neck to the shoulder
    # blades, on the head; inside the head and the jacket where it starts
    out.append(Tube('hair_back', 'y', [Ring(25.5, (0, 2.75), (3.5, 1.125), {'head': 1}),
                                       Ring(22.25, (0, 2.75), (3.5, 1.125), {'head': 1})], tag, caps=(False, True)))
    out.append(Tube('waist', 'y', [Ring(B3.HEM + B3.TUCK, (0, 0), P['belt'], {'spine': 0.2, 'pelvis': 0.8}),
                                   Ring(15, (0, 0), P['belt'], {'pelvis': 1})], tag, caps=(False, True)))
    # the bust: one box on the chest, its back 0.25 px inside the jacket
    z0 = -P['sh'][1] + 0.25
    yc, hh = (BUST['top'] + BUST['bottom']) / 2, (BUST['top'] - BUST['bottom']) / 2
    out.append(Tube('bust', 'z', [Ring(z0, (0, yc), (BUST['half_w'], hh), {'chest': 1}),
                                  Ring(-P['sh'][1] - P['bust'], (0, yc), (BUST['half_w'], hh), {'chest': 1})], tag, caps=(False, True)))
    A = G.ARM
    for s, sx in (('r', -1), ('l', 1)):
        u, f, hd = 'upper_arm_' + s, 'forearm_' + s, 'hand_' + s
        c = (sx * P['arm_x'], 0)
        rings = [Ring(A['top'], c, P['sleeve'], {'clavicle_' + s: 1}), Ring(A['shoulder'], c, P['sleeve'], {u: 1})]
        rings += B.joint_rings(A['elbow'], c, P['sleeve'], c, P['sleeve'], u, f, 2, 0.5)
        rings += [Ring(B3.SLEEVE_END, c, P['sleeve'], {f: 1})]
        out.append(Tube('sleeve_' + s, 'y', rings, tag, s))
        rings = B.joint_rings(A['wrist'], c, P['hand'], c, P['hand'], f, hd, 2, 0.5)
        rings += [Ring(A['tip'], c, P['hand'], {hd: 1})]
        out.append(Tube('hand_' + s, 'y', rings, tag, s, caps=(False, True)))
    return out


# ---------------------------------------------------------------- the female sample skin (shared by F1-F3)
PAL = dict(G.PAL, hair=(96, 58, 38), hair_dk=(74, 44, 30), hair_lt=(112, 70, 46), lash=(48, 34, 28), mouth=(162, 92, 84),
           blush=(200, 136, 110))
_b3_paint = B3.paint


def inside(v, lo, hi):
    return lo <= v < hi


def paint(tube, side, u, v, body):
    if not (isinstance(tube.paint, str) and tube.paint in ('f1', 'f2', 'f3')):
        return _b3_paint(tube, side, u, v, body)
    x, y, z = body
    ax = abs(x)
    name = tube.name
    if name == 'head':
        H = G.HEAD
        top = H['base'] + H['h']
        if side == 'cap0':
            return PAL['hair']
        if side == 'cap1':
            return PAL['skin_dk']
        if side == 'front':
            # a side parting: the fringe sweeps lower on the right, the hair down both sides of the face
            if y >= top - 1.0 or (y >= top - 1.75 and x < -0.5) or (y >= top - 1.5 and ax >= 2.5):
                return PAL['hair_lt'] if inside(y, top - 0.75, top - 0.5) and x < 0 else PAL['hair']
            if ax >= 3.25 and y >= 25.0:
                return PAL['hair']
            if inside(y, 28.75, 29.0) and inside(ax, 1.0, 2.75):
                return PAL['brow']
            if inside(y, 28.25, 28.5) and inside(ax, 0.75, 2.75):
                return PAL['lash']                          # the lash line over the eye
            if inside(y, 27.5, 28.25) and inside(ax, 0.75, 2.5):
                return PAL['iris'] if ax < 1.5 else PAL['eye_w']
            if inside(y, 26.75, 27.25) and ax < 0.5:
                return PAL['skin_dk']
            if inside(y, 26.25, 26.75) and inside(ax, 2.0, 2.75):
                return PAL['blush']
            if inside(y, 25.5, 25.75) and ax < 1.0:
                return PAL['mouth']
            return PAL['skin']
        if side == 'back':
            return PAL['hair_dk'] if y < 25.0 else PAL['hair']
        # the sides: hair over the ear, down to the jaw
        if y >= 25.0 and z >= -1.0:
            return PAL['hair_dk'] if y < 25.25 else PAL['hair']
        if y >= top - 1.5:
            return PAL['hair']
        return PAL['skin']
    if name == 'hair_back':
        if side == 'cap1':
            return PAL['hair_dk']
        if y < 22.75:
            return PAL['hair_dk']                           # the ends
        if side == 'back' and abs(x) < 0.25:
            return PAL['hair_dk']                           # a parting line down the back
        return PAL['hair']
    if name == 'torso_lower':
        proxy = Tube('torso', 'y', tube.rings, 'b3', tube.side)
        return _b3_paint(proxy, side, u, v, body)
    if name == 'neck':
        return PAL['hair_dk'] if side == 'back' or (side in ('xpos', 'xneg') and z > 0.25) else PAL['skin_dk']
    if name == 'bust':
        if side == 'cap1':                                  # its front, under the open jacket
            if ax < 1.0:
                return PAL['tee']
            if ax < 1.25:
                return PAL['jacket_dk']
            return PAL['jacket']
        if side == 'bottom':
            return PAL['jacket_dk']
        return PAL['jacket']
    if name == 'torso' and side == 'cap1':
        return PAL['jacket_dk']
    if name == 'torso' and side == 'cap0':
        return PAL['jacket_dk'] if ax >= 1.5 else PAL['skin_dk']
    return _b3_paint(tube, side, u, v, body)


G.paint = paint
for v in VARIANTS:
    B.ARMS[v] = dict(B.ARMS['B3'], hand=VARIANTS[v]['hand'], tip=VARIANTS[v]['hand'])
    B3.R.VARIANTS[v] = G.MAIN
    B.EXTRA[v] = dict(tubes=(lambda v=v: tubes_of(v)), tex=G.TEX, align=G.GUTTER, pad=G.GUTTER, texture=G.texture)
    E.PROTOS[v] = E.Proto(v)
E.frames_of = lambda p: Q.FBG if p == 'B' else E.FB2 if p in ('B2', 'B3') or p in VARIANTS else E.FV
E.ROW_NAME.update({'B3': 'B3 男性（现在）'})
E.ROW_NAME.update({v: VARIANTS[v]['title'] for v in VARIANTS})
ROWS = ['B3', 'F1', 'F2', 'F3']
OUT = Q.OUT


def sheet_a():
    cw, ch = 200, 320

    def cell(sub, r, c):
        name, view, kind = c[0], c[1], c[2]
        if kind == 'f5':
            eye, tgt = c[3]

            def draw(img, w, h):
                draws, _ = Q.scene(r, name)
                draws = [d for d in draws if d not in E.env_ground(name)] + [R3.wide_ground()]
                zb = np.full(img.shape[:2], -1e9)
                E.render(img, zb, draws, E.Camera(np.array(eye), np.array(tgt), w, h, fov=40), sky=None)
            R3.supersample(draw, sub)
            return
        draws, _ = Q.scene(r, name)
        E.render_cell(sub, draws, E.cell_camera(view, cw, ch, 150, target=(0, 0.98, 0.05)))
    cols = [('stand', 0, 'still', '正面'), ('stand', 45, 'still', '45°'), ('stand', 90, 'still', '侧面'),
            ('stand', 150, 'still', '背后 150°'), ('stand', 180, 'still', '背面'),
            ('stand', 0, 'f5', ((0.0, 1.62, -4.0), (0, 1.1, 0)), 'F5 背后 4 m'),
            ('stand', 0, 'f5', ((0.0, 1.62, 4.0), (0, 1.1, 0)), 'F5 正面 4 m')]
    img = E.grid(ROWS, cols, cw, ch, cell, left=120)
    E.save_sheet(img, 'female_compare_a.png', ROWS, cols, cw, ch,
                 '女性身体比例方案 F1–F3（身高、头、腿、骨骼和 B3 相同，只改肩宽、厚度、手臂、胸、腰、胯）',
                 left=120, colname=lambda c: c[-1])


def sheet_b():
    cw, ch = 210, 300

    def cell(sub, r, c):
        name, view = c[0], c[1]
        draws, _ = Q.scene(r, name)
        if name.startswith('chair'):
            cam = E.cell_camera(view, cw, ch, 165, target=(0, 0.72, 0.1))
        else:
            cam = E.cell_camera(view, cw, ch, 150, target=(0, 0.98, 0.05))
        E.render_cell(sub, draws, cam)
    cols = [('walk', 45, '走'), ('run', 90, '跑 侧面'), ('gun_p9', 45, 'P9-01 持枪'), ('gun_br51', 45, 'BR-51 持枪'),
            ('p9_reload_40', 30, 'P9-01 换弹'), ('chair_wood', 60, '木椅'), ('crouch', 45, '蹲'), ('arms_up', 20, '双手举起')]
    img = E.grid(ROWS, cols, cw, ch, cell, left=120)
    E.save_sheet(img, 'female_compare_b.png', ROWS, cols, cw, ch,
                 '同一套动作和握枪目标（没改动作），看手臂、胸和腰在动作里的样子',
                 left=120, colname=lambda c: c[-1])


def boxes(row):
    """Torso, waist, thighs and head boxes of a row (its own shoulders, waist and belt)."""
    if row == 'B3':
        sh, lower, step, belt, bust = (4.5, 2.25), None, None, (4.0, 2.0), 0
    else:
        P = VARIANTS[row]
        sh, lower, step, belt, bust = P['sh'], P['lower'], P['step'], P['belt'], P['bust']
    out = [('chest', (-sh[0], step or B3.HEM, -sh[1] - (bust if bust else 0)), (sh[0], 24, sh[1]))]
    if lower:
        out.append(('spine', (-lower[0], B3.HEM, -lower[1]), (lower[0], step, lower[1])))
    out += [('pelvis', (-belt[0], 15, -belt[1]), (belt[0], B3.HEM, belt[1])),
            ('thigh_r', (-4, 9.5, -2), (0, 15, 2)), ('thigh_l', (0, 9.5, -2), (4, 15, 2)),
            ('head', (-3.75, 24.5, -3.625), (3.75, 32, 3.625))]
    return out


def clip(row, M, s):
    P = E.PROTOS[row]
    X = P.skin(M)
    vid = np.where([p in ('sleeve_' + s, 'hand_' + s) for p in P.vpart])[0]
    vid = vid[P.m['V'][vid, 1] < R3.G_ELBOW + 0.5]
    worst, where = 0.0, ''
    for bone, lo, hi in boxes(row):
        inv = np.linalg.inv(M[bone])
        q = (inv @ np.c_[X[vid], np.ones(len(vid))].T).T[:, :3]
        d = np.min(np.minimum(q - np.array(lo), np.array(hi) - q), axis=1)
        if len(d) and d.max() > worst:
            worst, where = float(d.max()), bone
    return worst, where


def run_clip():
    names = [n for n in E.FB2 if not n.startswith('t15')]
    print('arm below the elbow inside torso / bust / waist / thighs / head (px, deepest vertex):')
    for n in names:
        line = []
        for r in ROWS:
            _, M = Q.body(r, n)
            for s in 'rl':
                d, w = clip(r, M, s)
                line.append('%s%s %.2f%s' % (r, s, d, (' ' + w) if d > 0.05 else ''))
        print('  %-16s %s' % (n, ' | '.join(line)))


def stats():
    for r in ROWS:
        m = E.PROTOS[r].m
        print(r, 'faces', len(m['F']) // 2, 'vertices', len(m['V']), 'atlas', m['size'])


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    stats()
    sheet_a()
    sheet_b()
    run_clip()
