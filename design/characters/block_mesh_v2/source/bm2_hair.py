"""AFL Block-Mesh V2 hair as geometry (user 2026-10-25: "头发能不能不做纯贴图"). Offline proposals only.

The hair is its own boxes on the head bone, a hairstyle layer over the head (the skin's hairstyle, not the body): a top
slab, side panels, a back panel and a fringe, 0.375 px out from the head (bob 0.5, male short cut 0.25; at 0.5 / 0.625
the first sheet's heads read too big over F2's 8 px shoulders), plus what hangs:
  A  long hair over the shoulders: the back panel to the shoulder blades (y 21.75), the sides beside the neck (24.25)
  B  a low ponytail: the sides to the ear (26), the back to the nape (25), a tie and a two-piece tail lying on the back
  C  a jaw-length bob: the sides to the jaw (25.25), the back to the nape (24.75), a little fuller (0.5 px)
  M  the male short cut: 0.25 px over the top, the sides to the temples (28.5) and the back to 26.5
Every box is rigid on the head (no hair bones yet). On F2 (bm2_female.py), B3 for the male rows.

    python -I bm2_hair.py <project> <poses dir> <out dir>
writes hair_compare.png and prints how deep the hanging hair goes into the jacket in each pose.
"""
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bm2_female as FM  # noqa: E402

B3, R3, Q, E, B, G = FM.B3, FM.R3, FM.Q, FM.E, FM.B, FM.G
Ring, Tube = B.Ring, B.Tube

HEAD = G.HEAD
HX, HZ, BASE, TOP = HEAD['w'] / 2, HEAD['d'] / 2, HEAD['base'], HEAD['base'] + HEAD['h']   # 3.75, 3.625, 24.5, 32


def box(name, lo, hi, tag, caps=(True, True)):
    """An axis-aligned box (Bedrock px) on the head bone, as a y tube."""
    c = ((lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2)
    h = ((hi[0] - lo[0]) / 2, (hi[2] - lo[2]) / 2)
    return Tube(name, 'y', [Ring(hi[1], c, h, {'head': 1}), Ring(lo[1], c, h, {'head': 1})], tag, caps=caps)


STYLES = {
    'A': dict(o=0.375, side=24.25, back=21.75, title='A 披肩长发'),
    'B': dict(o=0.375, side=26.0, back=25.0, tail=True, title='B 低马尾'),
    'C': dict(o=0.5, side=25.25, back=24.75, title='C 齐耳短发'),
    'M': dict(o=0.25, side=28.5, back=26.5, male=True, title='男 短发'),
}


def hair_tubes(style):
    S = STYLES[style]
    o, tag = S['o'], 'hair_m' if S.get('male') else 'hair_f'
    X, Z, In = HX + o, HZ + o, 0.25            # outer half sizes; each piece starts 0.25 px inside the head
    out = [box('hair_top', (-X, TOP - In, -Z), (X, TOP + o, Z), tag)]
    for s, sx in (('r', -1), ('l', 1)):
        lo_x, hi_x = sorted((sx * (HX - In), sx * X))
        # the side panel stops 0.5 px behind the face (the face's edge stays skin); a male cut only over the temples
        front = -HZ + (1.5 if S.get('male') else 0.5)
        out.append(box('hair_side_' + s, (lo_x, S['side'], front), (hi_x, TOP, Z), tag, caps=(False, True)))
    out.append(box('hair_back', (-X, S['back'], HZ - In), (X, TOP, Z), tag, caps=(False, True)))
    if S.get('male'):
        out.append(box('hair_fringe', (-X, 30.75, -Z), (X, TOP, -HZ + In), tag, caps=(False, True)))
    else:
        # a side-swept fringe: low over the right brow end, short on the left; the brows stay visible
        out.append(box('hair_fringe', (-X, 29.5, -Z), (0.75, TOP, -HZ + In), tag, caps=(False, True)))
        out.append(box('hair_fringe2', (0.75, 30.5, -Z), (X, TOP, -HZ + In), tag, caps=(False, True)))
    if S.get('tail'):
        out.append(box('hair_tie', (-0.75, 26.25, Z - 0.25), (0.75, 27.25, Z + 0.5), 'tie'))
        # the tail in two pieces, the lower one stepped in to lie on the jacket's back (z 2.0), overlapping the upper
        out.append(box('hair_tail', (-1.0, 24.0, Z - 0.25), (1.0, 26.75, Z + 1.25), tag, caps=(False, True)))
        out.append(box('hair_tail2', (-0.875, 21.25, 2.25), (0.875, 24.25, Z), tag, caps=(False, True)))
    return out


PAL = dict(FM.PAL, tie=(40, 36, 34))
MPAL = dict(G.PAL)
_paint = G.paint                               # bm2_female's (falls through to B3's)


def paint(tube, side, u, v, body):
    tag = tube.paint
    if isinstance(tag, str) and tag.endswith('#'):
        return head_paint(tube, side, u, v, body)
    if tag == 'tie':
        return PAL['tie']
    if tag not in ('hair_f', 'hair_m'):
        return _paint(tube, side, u, v, body)
    P = PAL if tag == 'hair_f' else MPAL
    x, y, z = body
    hair, dk = P['hair'], P['hair_dk']
    lt = P.get('hair_lt', tuple(min(255, c + 14) for c in hair))
    if side in ('cap1', 'bottom'):
        return dk                                           # the ends' undersides
    if side == 'cap0' and tube.name == 'hair_top':
        # the top: a parting over the right eye, lighter on the crown
        if abs(x + 1.25) < 0.125:
            return dk
        return lt if z > 0.5 else hair
    # flat blocks (no strand lines: they read as planks and alias): the last 0.5 px at the ends darker, the top's
    # outer 0.5 px lighter where the light catches it
    lo_y = tube.rings[-1].t
    if y < lo_y + 0.5 and tube.name != 'hair_top':
        return dk
    if tube.name == 'hair_top' or y > TOP - 0.5:
        return lt
    return hair


G.paint = paint


def head_paint(tube, side, u, v, body):
    """Under geometry hair the face's front is skin to its edges (the painted side strands go: the side panels are
    there); behind the fringe it stays hair."""
    proxy = Tube(tube.name, tube.axis, tube.rings, tube.paint[:-1], tube.side, tube.caps)
    col = _paint(proxy, side, u, v, body)
    x, y, z = body
    if side == 'front' and y < 29.5 and col in (PAL['hair'], PAL['hair_lt'], PAL['hair_dk'], MPAL['hair'], MPAL['hair_dk']):
        return PAL['skin']
    return col


def register(name, base, style):
    if base == 'B3':
        body = lambda: [t for t in B3.tubes()]
        hand = B.ARMS['B3']
    else:
        body = lambda: [t for t in FM.tubes_of(base) if t.name != 'hair_back']
        hand = B.ARMS[base]
    def tubes():
        out = body()
        if style:
            for t in out:
                if t.name == 'head':
                    t.paint = t.paint + '#'
            out += hair_tubes(style)
        return out
    B.ARMS[name] = dict(hand)
    B3.R.VARIANTS[name] = G.MAIN
    B.EXTRA[name] = dict(tubes=tubes, tex=G.TEX, align=G.GUTTER, pad=G.GUTTER, texture=G.texture)
    E.PROTOS[name] = E.Proto(name)


ROWS = [('F2', None, None, 'F2 纯贴图（上一版）'), ('F2A', 'F2', 'A', 'F2 + A 披肩长发'), ('F2B', 'F2', 'B', 'F2 + B 低马尾'),
        ('F2C', 'F2', 'C', 'F2 + C 齐耳短发'), ('B3', None, None, 'B3 男 纯贴图（现在）'), ('B3M', 'B3', 'M', 'B3 男 + 短发')]
for name, base, style, title in ROWS:
    if base:
        register(name, base, style)
    E.ROW_NAME[name] = title
_frames_of = E.frames_of
E.frames_of = lambda p: E.FB2 if p in ('F2A', 'F2B', 'F2C', 'B3M') else _frames_of(p)
OUT = Q.OUT


def sheet():
    cw, ch = 200, 300
    rows = [r[0] for r in ROWS]

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
        if kind == 'head':
            draws, M = Q.scene(r, name)
            tgt = Q.point(r, M, 'head', (0, 3.5, 0))
            a = np.radians(view)
            cam = E.Camera(tgt + 3.0 * np.array([np.sin(a), 0.12, np.cos(a)]), tgt, cw, ch, scale=380)
            E.render_cell(sub, draws, cam)
            return
        draws, _ = Q.scene(r, name)
        E.render_cell(sub, draws, E.cell_camera(view, cw, ch, 150, target=(0, 0.98, 0.05)))
    cols = [('stand', 0, 'still', '正面'), ('stand', 45, 'still', '45°'), ('stand', 90, 'still', '侧面'),
            ('stand', 150, 'still', '背后 150°'), ('stand', -30, 'head', '头部近景 正面偏右'), ('stand', 135, 'head', '头部近景 背后'),
            ('stand', 0, 'f5', ((0.0, 1.62, -4.0), (0, 1.1, 0)), 'F5 背后 4 m'), ('gun_p9', 45, 'still', 'P9-01 持枪')]
    img = E.grid(rows, cols, cw, ch, cell, left=150)
    E.save_sheet(img, 'hair_compare.png', rows, cols, cw, ch,
                 '头发做成立体方块（挂在头骨上的发型层，不是身体）：女性三种发型在 F2 上，男性短发在 B3 上',
                 left=150, colname=lambda c: c[-1])


def hair_clip():
    """How deep the hair's lowest pieces go into the jacket (chest box) per pose, px."""
    print('hair inside the jacket / shoulders (px, deepest vertex):')
    for name, base, style, _ in ROWS:
        if not style:
            continue
        P = E.PROTOS[name]
        sh = (4.5, 2.25) if base == 'B3' else FM.VARIANTS[base]['sh']
        worst = []
        for n in E.FB2:
            if n.startswith('t15'):
                continue
            _, M = Q.body(name, n)
            X = P.skin(M)
            vid = np.where([str(p).startswith('hair') for p in P.vpart])[0]
            inv = np.linalg.inv(M['chest'])
            q = (inv @ np.c_[X[vid], np.ones(len(vid))].T).T[:, :3]
            lo, hi = np.array([-sh[0], 16, -sh[1]]), np.array([sh[0], 24, sh[1]])
            d = np.min(np.minimum(q - lo, hi - q), axis=1)
            worst.append((float(max(0.0, d.max())), n))
        worst.sort(reverse=True)
        print('  %-5s %s' % (name, ', '.join('%s %.2f' % (n, d) for d, n in worst[:5])))


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, *_ in ROWS:
        m = E.PROTOS[name].m
        print(name, 'faces', len(m['F']) // 2, 'atlas', m['size'])
    sheet()
    hair_clip()
