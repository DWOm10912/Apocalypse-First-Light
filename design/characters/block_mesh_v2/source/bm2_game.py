"""AFL Block-Mesh V2 in game (docs/dev/block_mesh_v2_b_ingame.md): prototype B, revision 2 ("B2", user 2026-10-25 round 2),
as the game's dev-test mesh, its 256 x 256 sample skin and the poser rig at B2's proportions.

B2 against B (only these, the B style kept, no fingers):
  head      8 x 8 x 8 -> 7.5 x 7.5 x 7.25 px (-6 % across, -9 % front to back), on a 0.5 px neck: the top stays at 32 px
  torso     8 x 4 -> 9 x 4.5 at the chest, tapering to 8 x 4 at the hips; the arm boxes out to x 6 (centre) for the wider
            shoulders, their joints where B has them (x 5.5), 0.5 px inside the box as vanilla's are 1 px
  arm       3 x 3 -> 3.25 at the shoulder, 3 at the elbow, 2.75 at the cuff
  hand      2 x 2.6, 2.5 px long, rounded into the wrist -> a flat palm 1.75 thick x 3 wide, 3 px long, square at the end, a
            narrower wrist (2 x 2.5) between cuff and palm
  sizes     every size and length a multiple of 0.25 px, so faces start and end on whole texels (4 texels per px)
  skin      flat colour blocks, details on whole pixels (4 texels), no per-texel noise (B's sample skin had a per-texel hash
            that read as diagonal "fish scales"); atlas regions on 4-texel blocks with 4-texel gutters filled with their
            edge colour, so the game's two mip levels (BlockMeshBody) do not bleed

Mesh JSON (client/skeleton/BlockMeshBody reads it): Java model px (x, 24 - y, z; y down, the feet at 24), the 22 main bones
with Java pivots, and per quad its part and four vertices, each x y z u v j0 j1 j2 j3 w0 w1 w2 w3 (uv 0..1, joints index the
bone list). The four are in Minecraft's order: (p2 - p0) x (p3 - p1) points out of the body.

Usage: python -I bm2_game.py <repo root>
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bm2_build as B  # noqa: E402
import bm2_rig as R  # noqa: E402

PARTS = ['head', 'neck', 'torso', 'arm_r', 'arm_l', 'leg_r', 'leg_l', 'foot_r', 'foot_l']
ASSETS = 'src/main/resources/assets/apocalypse_firstlight'
MESH = 'skins/block_mesh_v2/b_body.json'
TEXTURE = 'textures/entity/block_mesh_v2/b_sample.png'
TEX = 4        # texels per px: 256 x 256 (a 64 x 64 vanilla skin is 1)
GUTTER = 4     # texels: one block of the second mip level

# ---------------------------------------------------------------- B2 proportions (Bedrock px)
HEAD = dict(w=7.5, h=7.5, d=7.25, base=24.5)
NECK = dict(top=24.75, bottom=23.75, h=(1.5, 1.375), z=0.25)
ARM = dict(x=6.0, joint=5.5, top=24, shoulder=22.5, elbow=18, cuff=15.0, wrist=14.5, palm=14.0, tip=11.5)
ARM_W = dict(top=(1.625, 1.625), elbow=(1.5, 1.5), cuff=(1.375, 1.375), wrist=(1.0, 1.25), palm=(0.875, 1.5))
# the hand end the hand targets use (GunStance): 1 px under the palm's middle, as vanilla's 2 px hand has it at its end
HAND_CAP_Y = ARM['shoulder'] - (ARM['tip'] + 0.5)

# the joints stay where B has them (x 5.5: the hands reach as far); the arm boxes sit 0.5 px out from them, as vanilla's do
# (joint 5, box centre 6), so the shoulders read wider
MAIN = list(R.MAIN)
R.VARIANTS['B2'] = MAIN
B.ARMS['B2'] = dict(B.ARMS['B'], cx=ARM['x'], hand=ARM_W['palm'], tip=ARM_W['palm'])


# ---------------------------------------------------------------- tubes
def tubes():
    Ring, Tube = B.Ring, B.Tube
    H = HEAD
    out = [Tube('head', 'y', [Ring(H['base'] + H['h'], (0, 0), (H['w'] / 2, H['d'] / 2), {'head': 1}),
                              Ring(H['base'], (0, 0), (H['w'] / 2, H['d'] / 2), {'head': 1})], 'b2'),
           Tube('neck', 'y', [Ring(NECK['top'], (0, NECK['z']), NECK['h'], {'head': 1}),
                              Ring(NECK['bottom'], (0, NECK['z']), NECK['h'], {'chest': 1})], 'b2', caps=(False, False)),
           Tube('torso', 'y', [Ring(24, (0, 0), (4.5, 2.25), {'chest': 1}), Ring(21.5, (0, 0), (4.5, 2.25), {'chest': 0.85, 'spine': 0.15}),
                               Ring(20.0, (0, 0), (4.375, 2.125), {'chest': 0.3, 'spine': 0.7}),
                               Ring(18.5, (0, 0), (4.125, 2.0), {'spine': 0.8, 'pelvis': 0.2}),
                               Ring(17.5, (0, 0), (4.0, 2.0), {'spine': 0.25, 'pelvis': 0.75}), Ring(15, (0, 0), (4.0, 2.0), {'pelvis': 1})], 'b2')]
    L, F = R.LEG, R.FOOT
    for s, sx in (('r', -1), ('l', 1)):
        c, h = (sx * L['x'], 0), (2, 2)
        th, sh, ft = 'thigh_' + s, 'shin_' + s, 'foot_' + s
        rings = [Ring(L['hip'], c, h, {'pelvis': 0.35, th: 0.65}), Ring(L['hip'] - 0.75, c, h, {th: 1})]
        rings += B.joint_rings(L['knee'], c, h, c, h, th, sh, 2, 0.5)
        rings += [Ring(L['ankle'] + 0.5, c, h, {sh: 1}), Ring(L['ankle'], c, h, {sh: 0.45, ft: 0.55})]
        out.append(Tube('leg_' + s, 'y', rings, 'b2', s, caps=(False, True)))
        x = sx * L['x']
        out.append(Tube('foot_' + s, 'z', [Ring(F['back'], (x, 0.75), (2, 0.75), {ft: 1}), Ring(F['ball'] + 0.25, (x, 0.75), (2, 0.75), {ft: 1}),
                                           Ring(F['ball'] - 0.25, (x, 0.625), (2, 0.625), {ft: 0.3, 'toes_' + s: 0.7}),
                                           Ring(F['front'], (x, 0.5), (2, 0.5), {'toes_' + s: 1})], 'b2', s))
    A, W = ARM, ARM_W
    for s, sx in (('r', -1), ('l', 1)):
        u, f, hd = 'upper_arm_' + s, 'forearm_' + s, 'hand_' + s
        c = (sx * A['x'], 0)
        rings = [Ring(A['top'], c, W['top'], {u: 1})]
        rings += B.joint_rings(A['elbow'], c, W['elbow'], c, W['elbow'], u, f, 2, 0.5)
        rings += [Ring(A['cuff'], c, W['cuff'], {f: 1}), Ring(A['wrist'], c, W['wrist'], {f: 0.5, hd: 0.5}),
                  Ring(A['palm'], c, W['palm'], {hd: 1}), Ring(A['tip'], c, W['palm'], {hd: 1})]
        out.append(Tube('arm_' + s, 'y', rings, 'b2', s))
    return out


# ---------------------------------------------------------------- the sample skin: flat blocks, details on whole px
PAL = dict(skin=(194, 140, 106), skin_dk=(172, 120, 90), skin_lt=(206, 154, 120), hair=(58, 41, 31), hair_dk=(44, 31, 24),
           eye_w=(232, 230, 224), iris=(62, 84, 110), brow=(54, 39, 30), mouth=(150, 92, 78), tee=(66, 69, 74),
           tee_dk=(54, 57, 62), jacket=(122, 90, 60), jacket_dk=(98, 72, 48), jacket_lt=(140, 105, 72), jeans=(58, 78, 118),
           jeans_dk=(46, 62, 96), boot=(84, 60, 42), boot_dk=(60, 42, 30), sole=(40, 33, 29), belt=(44, 33, 26),
           buckle=(164, 154, 120), lace=(146, 128, 104))


def inside(v, lo, hi):
    return lo <= v < hi


def paint(tube, side, u, v, body):
    """Colour at a texel centre: body = its Bedrock position (px). Every edge below is a multiple of 0.25 px (one texel)."""
    x, y, z = body
    ax = abs(x)
    s = tube.side
    out_side = (side == 'xneg') if s == 'r' else (side == 'xpos')
    name = tube.name
    if name == 'head':
        top = HEAD['base'] + HEAD['h']
        if side == 'cap0':
            return PAL['hair']
        if side == 'cap1':
            return PAL['skin_dk']
        if side == 'front':
            if y >= top - 1.25 or (y >= top - 2.25 and ax >= 2.75):
                return PAL['hair']
            if inside(y, 28.75, 29.0) and inside(ax, 0.75, 2.75):
                return PAL['brow']
            if inside(y, 27.5, 28.25) and inside(ax, 0.75, 2.5):
                return PAL['iris'] if ax < 1.5 else PAL['eye_w']
            if inside(y, 26.5, 27.25) and ax < 0.5:
                return PAL['skin_dk']
            if inside(y, 25.5, 25.75) and ax < 1.0:
                return PAL['mouth']
            return PAL['skin']
        if side == 'back':
            return PAL['hair'] if y >= 26.0 else PAL['hair_dk'] if y >= 25.5 else PAL['skin']
        if y >= top - 2.25 or (y >= 26.0 and z >= 1.25):
            return PAL['hair']
        if inside(y, 26.25, 27.75) and inside(z, -0.25, 0.5):
            return PAL['skin_dk']
        return PAL['skin']
    if name == 'neck':
        return PAL['skin_dk']
    if name == 'torso':
        if side == 'cap0':
            return PAL['jacket_dk'] if ax >= 1.5 else PAL['skin_dk']
        if side == 'cap1':
            return PAL['jeans_dk']
        if y < 15.75:
            if side == 'front' and y >= 15.0 and ax < 0.75:
                return PAL['buckle']
            return PAL['belt']
        if side == 'front':
            if ax < 1.0:
                if y >= 23.5 and ax < 0.75:
                    return PAL['skin_dk']
                return PAL['tee']
            if ax < 1.25 or (y >= 23.25 and ax < 2.25):
                return PAL['jacket_dk']                     # the open front's edges, the collar
            if inside(y, 20.25, 21.5) and inside(ax, 1.75, 3.5):
                return PAL['jacket_dk'] if y >= 21.25 else PAL['jacket_lt']
            if inside(y, 16.75, 18.25) and inside(ax, 1.75, 3.5):
                return PAL['jacket_dk'] if y >= 18.0 else PAL['jacket_lt']
            if y < 16.25:
                return PAL['jacket_dk']                     # the hem
            return PAL['jacket']
        if y < 16.25 or (side == 'back' and inside(y, 22.75, 23.0)):
            return PAL['jacket_dk']
        return PAL['jacket']
    if name.startswith('arm'):
        if side == 'cap1':
            return PAL['skin_dk']
        if y < ARM['cuff']:
            # the hand: a fingers line across the outside and the back of the hand, the thumb's edge at the front
            if inside(y, 12.75, 13.0) and side != 'front':
                return PAL['skin_dk']
            if side == 'front' and inside(y, 12.75, 14.0):
                return PAL['skin_dk']
            return PAL['skin']
        if y < ARM['cuff'] + 0.5 or side == 'cap0':
            return PAL['jacket_dk']
        if out_side and inside(y, 23.25, 23.5):
            return PAL['jacket_dk']                         # the shoulder seam
        return PAL['jacket']
    if name.startswith('leg'):
        if y < 3.25:
            if y >= 2.75:
                return PAL['boot_dk']
            if side == 'front' and abs(ax - 2) < 0.5 and int(y * 2) % 2 == 0:
                return PAL['lace']
            return PAL['boot']
        if out_side and inside(u, 1.875, 2.125):
            return PAL['jeans_dk']                          # the outside seam, one texel
        return PAL['jeans']
    if name.startswith('foot'):
        if side == 'bottom' or y < 0.375:
            return PAL['sole']
        if z < -2.75:
            return PAL['boot_dk']
        return PAL['boot']
    return (255, 0, 255)


def texture(m):
    """Each texel of each face from the face's corners (bilinear), no noise; then the gutters take their edge colour."""
    size, tex = m['size'], m['tex']
    img = np.zeros((size, size, 3), np.uint8)
    have = np.zeros((size, size), bool)
    V, UV = m['V'], m['UV']
    for qi in range(0, len(m['F']), 2):
        tube, side = m['FN'][qi]
        base = min(m['F'][qi].min(), m['F'][qi + 1].min())
        p = V[base:base + 4]
        uv = UV[base:base + 4] * size
        u0, v0 = uv.min(0)
        u1, v1 = uv.max(0)
        c = B.corner_order(uv, u0, v0, u1, v1)
        for ty in range(int(round(v0)), int(round(v1))):
            for tx in range(int(round(u0)), int(round(u1))):
                fu = (tx + 0.5 - u0) / max(u1 - u0, 1e-6)
                fv = (ty + 0.5 - v0) / max(v1 - v0, 1e-6)
                q = p[c[0]] * (1 - fu) * (1 - fv) + p[c[1]] * fu * (1 - fv) + p[c[2]] * fu * fv + p[c[3]] * (1 - fu) * fv
                img[ty, tx] = paint(tube, side, fu * (u1 - u0) / tex, fv * (v1 - v0) / tex, q)
                have[ty, tx] = True
    for _ in range(GUTTER):
        grow = have.copy()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            src = np.roll(np.roll(have, dy, 0), dx, 1) & ~grow
            img[src] = np.roll(np.roll(img, dy, 0), dx, 1)[src]
            grow |= src
        have = grow
    return img


B.EXTRA['B2'] = dict(tubes=tubes, tex=TEX, align=GUTTER, pad=GUTTER, texture=texture)


# ---------------------------------------------------------------- game files
def java(p):
    return [float(p[0]), float(24 - p[1]), float(p[2])]


def mesh_json(m):
    names = [b[0] for b in MAIN]
    index = {n: i for i, n in enumerate(names)}
    bones = [{'name': n, 'parent': par, 'pivot': java(piv)} for n, par, piv in MAIN]
    quads, verts = [], []
    for qi in range(0, len(m['F']), 2):
        tube, side = m['FN'][qi]
        tri = m['F'][qi]
        base = int(min(tri.min(), m['F'][qi + 1].min()))
        # bm2_build's quad: 0-1-2 / 0-2-3, flipped by fix_winding when it faced in; out = counter-clockwise in Bedrock axes
        order = [base, base + 1, base + 2, base + 3] if tri[1] == base + 1 else [base, base + 3, base + 2, base + 1]
        p = m['V'][order]
        out_b = np.cross(p[2] - p[0], p[3] - p[1])
        order = order[::-1]   # Java px mirror y: the same corners the other way round keep the normal outward
        pj = np.array([java(m['V'][i]) for i in order])
        n = np.cross(pj[2] - pj[0], pj[3] - pj[1])
        if np.dot(n, out_b * np.array([1, -1, 1])) <= 0:
            raise ValueError('winding %s %s' % (tube.name, side))
        quads.append(PARTS.index(tube.name))
        for i in order:
            w = sorted(m['W'][i].items(), key=lambda kv: -kv[1])[:4]
            tot = sum(v for _, v in w)
            js = [index[b] for b, _ in w] + [0] * (4 - len(w))
            ws = [round(v / tot, 5) for _, v in w] + [0.0] * (4 - len(w))
            u, v = m['UV'][i]
            verts += [round(c, 5) for c in java(m['V'][i])] + [round(float(u), 6), round(float(v), 6)] + js + ws
    return {
        'format_version': 1,
        'about': 'AFL Block-Mesh V2 B2 (user 2026-10-25 round 2), generated by design/characters/block_mesh_v2/source/bm2_game.py. '
                 'Java model px; per quad 4 vertices of x y z u v j0..j3 w0..w3; quad order (p2-p0)x(p3-p1) outward.',
        'texture_size': m['size'],
        'bones': bones,
        'parts': PARTS,
        'quad_parts': quads,
        'vertices': verts,
    }


def rig_json(vanilla):
    r = R.poser_rig(vanilla, MAIN)
    r['about'] = ('AFL Block-Mesh V2 B2 (user 2026-10-25, docs/dev/block_mesh_v2_b_ingame.md): the poser\'s 16 bones at '
                  'B2\'s proportions, generated by design/characters/block_mesh_v2/source/bm2_game.py. Dev test only '
                  '(client/skeleton/BlockMeshBody); afl_humanoid_v1.json stays the players\' rig.')
    # the boxes the gauze (BandageWrapRenderer) and the offline checks read: B2's wider chest, slimmer arms
    for s in r['skin']:
        if s['part'] == 'body':
            s['from'], s['size'] = [-4.5, 15, -2], [9, 9, 4]
        if s['part'] == 'right_arm':
            s['from'], s['size'] = [-7.5, 12, -1.5], [3, 12, 3]
        if s['part'] == 'left_arm':
            s['from'], s['size'] = [4.5, 12, -1.5], [3, 12, 3]
    r['block_mesh'] = {
        'about': 'SkeletonRig.Profile: hand_cap = the hand end off the shoulder pivot (Java px, right arm); hip_height / foot_x / '
                 'ankle Bedrock px; reference_leg = vanilla\'s right leg the clips were made for; key_warp Java px boxes.',
        'hand_cap': [-(ARM['x'] - ARM['joint']), HAND_CAP_Y],
        'hand_half': ARM_W['palm'][0],
        'hip_height': R.LEG['hip'],
        'foot_x': R.LEG['x'],
        'ankle': R.LEG['ankle'],
        'seat_scale': 0.85,
        'seat_leg_stretch': False,
        'reference_leg': {'hip': [1.9, 12], 'thigh': 6, 'shin': 4, 'ankle': 2},
        'key_warp': R.warp(),
        'leg_part_pivot': [R.LEG['x'], R.LEG['hip']],
        'mesh': MESH,
        'texture': TEXTURE,
    }
    return r


def main():
    root = sys.argv[1]
    assets = os.path.join(root, ASSETS)
    m = B.fix_winding(B.build('B2'))
    if m['size'] != 256:
        raise ValueError('atlas %d' % m['size'])
    img = B.texture(m)
    for rel in (MESH, TEXTURE):
        os.makedirs(os.path.dirname(os.path.join(assets, rel)), exist_ok=True)
    open(os.path.join(assets, TEXTURE), 'wb').write(B.png_bytes(img))
    mj = mesh_json(m)
    json.dump(mj, open(os.path.join(assets, MESH), 'w'), separators=(',', ':'))
    vanilla = json.load(open(os.path.join(assets, 'rigs/afl_humanoid_v1.json')))
    json.dump(rig_json(vanilla), open(os.path.join(assets, 'rigs/afl_block_mesh_v2.json'), 'w'), indent=1)
    # UV on whole texels: every corner (texels) within 1e-4 of an integer
    uv = np.array(mj['vertices']).reshape(-1, 13)[:, 3:5] * m['size']
    off = np.abs(uv - np.round(uv)).max()
    print('quads', len(mj['quad_parts']), 'vertices', len(mj['vertices']) // 13, 'atlas', m['size'], 'worst uv off the texel grid', round(float(off), 5))


if __name__ == '__main__':
    main()
