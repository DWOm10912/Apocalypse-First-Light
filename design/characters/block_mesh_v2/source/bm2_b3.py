"""AFL Block-Mesh V2 B3 (user 2026-10-25 round 3, offline only): B2 with Minecraft-style continuous box arms.

The user's direction (after looking at Minecraft: Story Mode's people): no separate palm. The arm reads as one square
column from the shoulder to the hand; the clothing's sleeve is its own layer over it, and only the last short piece of
the column shows as skin. Bones, joints, hand ends and every action stay B2's (rigs/afl_block_mesh_v2.json unchanged).

B3 against B2 (the arms only; head, neck, torso, legs, feet, skin palette are B2's):
  sleeve    its own tube (part sleeve_r / sleeve_l), 3.25 x 3.25 px from the shoulder (y 24) to the cuff (y 14.25), the
            same cross-section all the way (B2 narrowed 3.25 -> 2.75 to its cuff); the elbow's two joint rings as B2's;
            the cuff end closed (inside the hand it is hidden: from outside it reads as a 1-texel ring round the wrist)
  hand      the body's own arm (part hand_r / hand_l), 3 x 3 px, the same cross-section from inside the sleeve (y 15) to
            the end (y 11.5): 2.75 px of skin below the cuff, a square end, no wrist narrowing, no palm, no thumb.
            The wrist bone (14.5) bends it through two joint rings (15.0 / 14.0), the bend inside the cuff
  masking   the body's arm exists only below the sleeve (tucked 0.75 px into it): the clothing hides the body surface
            it covers. SLEEVE_END is the one number a shorter sleeve changes (more skin column below it)
  sizes     multiples of 0.25 px (4 texels), the atlas on 4-texel blocks with filled gutters (bm2_game.py)

Writes design/characters/block_mesh_v2/b3/: b3_body.json (the game's mesh format, parts list with the new parts) and
b3_sample.png; with --game also skins/block_mesh_v2/b3_body.json, textures/entity/block_mesh_v2/b3_sample.png and points
rigs/afl_block_mesh_v2.json's block_mesh mesh / texture at them (B2's files stay). The game's BlockMeshBody derives the
clavicles as clavicle_hold does (BlockMeshBody.CLAVICLE_DEAD_DEG, the same 8 deg).

Usage: python -I bm2_b3.py <repo root> [--game]
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bm2_build as B  # noqa: E402
import bm2_game as G  # noqa: E402
import bm2_rig as R  # noqa: E402

SLEEVE = (1.625, 1.625)      # half x, half z (px)
HAND = (1.5, 1.5)
SLEEVE_END = 14.25           # the cuff (y px); the wrist joint is 14.5
TUCK = 0.75                  # the body's arm reaches this far up into the sleeve
# round 3b (user 2026-10-25): the idle clip turns the upper arms 4-5 deg out (locomotion.idle, z 4..5); the arm box's top is
# 1.5 px over the shoulder pivot, so it tilts with them and its top face shows as a slope. Not the mesh (a straight box in
# the bind pose), not the weights (the top ring rigid on the upper arm), not the shoulder joint: the rest pose's turn.
# The animation stays; the top ring goes on the clavicle, which follows the upper arm only past CLAVICLE_DEAD_DEG.
SHOULDER_HOLD = True
# round 3c: the slanted sides the user marked were the torso's taper, not the shoulder: a straight jacket over the waist
STRAIGHT_JACKET = True
HEM = 16.0
CLAVICLE_DEAD_DEG = 8.0
PARTS = ['head', 'neck', 'torso', 'waist', 'sleeve_r', 'sleeve_l', 'hand_r', 'hand_l', 'leg_r', 'leg_l', 'foot_r', 'foot_l']

GAME_MESH = 'skins/block_mesh_v2/b3_body.json'
GAME_TEXTURE = 'textures/entity/block_mesh_v2/b3_sample.png'

R.VARIANTS['B3'] = G.MAIN
B.ARMS['B3'] = dict(B.ARMS['B2'], hand=HAND, tip=HAND)


def tubes():
    Ring, Tube = B.Ring, B.Tube
    A = G.ARM
    out = [t for t in G.tubes() if not t.name.startswith('arm_') and not (STRAIGHT_JACKET and t.name == 'torso')]
    if STRAIGHT_JACKET:
        # the jacket a straight 9 x 4.5 box from the shoulders to its hem (B2's chest-to-waist taper, 4.5 -> 4.0 over y
        # 21.5 -> 17.5, showed as slanted sides between the arms and the body); under the hem the 8 x 4 waist and belt
        J, W2 = (4.5, 2.25), (4.0, 2.0)
        out.append(Tube('torso', 'y', [Ring(24, (0, 0), J, {'chest': 1}), Ring(21.5, (0, 0), J, {'chest': 0.85, 'spine': 0.15}),
                                       Ring(20.0, (0, 0), J, {'chest': 0.3, 'spine': 0.7}), Ring(18.5, (0, 0), J, {'spine': 0.8, 'pelvis': 0.2}),
                                       Ring(17.5, (0, 0), J, {'spine': 0.25, 'pelvis': 0.75}), Ring(HEM, (0, 0), J, {'spine': 0.1, 'pelvis': 0.9})], 'b3'))
        out.append(Tube('waist', 'y', [Ring(HEM + TUCK, (0, 0), W2, {'spine': 0.2, 'pelvis': 0.8}), Ring(15, (0, 0), W2, {'pelvis': 1})],
                        'b3', caps=(False, True)))
    for s, sx in (('r', -1), ('l', 1)):
        u, f, hd = 'upper_arm_' + s, 'forearm_' + s, 'hand_' + s
        c = (sx * A['x'], 0)
        if SHOULDER_HOLD:
            # the sleeve's top on the clavicle (clavicle_hold: the chest's within the dead zone), its side from the shoulder
            # pivot down on the upper arm: a small swing turns the arm under a level, square shoulder top
            rings = [Ring(A['top'], c, SLEEVE, {'clavicle_' + s: 1}), Ring(A['shoulder'], c, SLEEVE, {u: 1})]
        else:
            rings = [Ring(A['top'], c, SLEEVE, {u: 1})]
        rings += B.joint_rings(A['elbow'], c, SLEEVE, c, SLEEVE, u, f, 2, 0.5)
        rings += [Ring(SLEEVE_END, c, SLEEVE, {f: 1})]
        out.append(Tube('sleeve_' + s, 'y', rings, 'b3', s))
        rings = B.joint_rings(A['wrist'], c, HAND, c, HAND, f, hd, 2, 0.5)       # 15.0 (inside the sleeve) / 14.0
        rings += [Ring(A['tip'], c, HAND, {hd: 1})]
        assert rings[0].t >= SLEEVE_END + TUCK - 1e-6
        out.append(Tube('hand_' + s, 'y', rings, 'b3', s, caps=(False, True)))
    return out


def clavicle_hold(M, pivot):
    """The clavicles (bm2_eval: the chest's) turned by the upper arm's turn off the chest less CLAVICLE_DEAD_DEG about the
    shoulder pivot (rest Bedrock px). For BlockMeshBody when B3 goes in: the same, in place of clavicle = chest."""
    for s in 'rl':
        Mc, Mu = M['chest'], M['upper_arm_' + s]
        rel = np.linalg.inv(Mc) @ Mu
        Rm = rel[:3, :3]
        ang = math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(Rm) - 1) / 2))))
        if ang <= CLAVICLE_DEAD_DEG:
            M['clavicle_' + s] = Mc
            continue
        axis = np.array([Rm[2, 1] - Rm[1, 2], Rm[0, 2] - Rm[2, 0], Rm[1, 0] - Rm[0, 1]])
        axis = axis / np.linalg.norm(axis)
        a = math.radians(ang - CLAVICLE_DEAD_DEG)
        K = np.array([[0, -axis[2], axis[1]], [axis[2], 0, -axis[0]], [-axis[1], axis[0], 0]])
        Rp = np.eye(4)
        Rp[:3, :3] = np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * K @ K
        p = np.asarray(pivot['upper_arm_' + s], float)
        T, Ti = np.eye(4), np.eye(4)
        T[:3, 3], Ti[:3, 3] = p, -p
        M['clavicle_' + s] = Mc @ T @ Rp @ Ti
    return M


PAL = G.PAL
_paint = G.paint


def paint(tube, side, u, v, body):
    x, y, z = body
    name = tube.name
    out_side = (side == 'xneg') if tube.side == 'r' else (side == 'xpos')
    if name.startswith('sleeve'):
        if side in ('cap0', 'cap1'):
            return PAL['jacket_dk']
        if y < SLEEVE_END + 0.5:
            return PAL['jacket_dk']                     # the cuff band, two texels
        if out_side and 23.25 <= y < 23.5:
            return PAL['jacket_dk']                     # the shoulder seam
        return PAL['jacket']
    if name == 'torso' and STRAIGHT_JACKET and side == 'cap1':
        return PAL['jacket_dk']                         # the hem's underside
    if name == 'waist':
        if side == 'cap1':
            return PAL['jeans_dk']
        if side == 'front' and abs(x) < 0.75 and y < 15.75:
            return PAL['buckle']
        return PAL['belt']
    if name.startswith('hand'):
        return PAL['skin_dk'] if side == 'cap1' else PAL['skin']
    return _paint(tube, side, u, v, body)


G.paint = paint                       # bm2_game.texture paints through its module's paint
B.EXTRA['B3'] = dict(tubes=tubes, tex=G.TEX, align=G.GUTTER, pad=G.GUTTER, texture=G.texture)


def main():
    root = sys.argv[1]
    out = os.path.join(root, 'design/characters/block_mesh_v2/b3')
    os.makedirs(out, exist_ok=True)
    m = B.fix_winding(B.build('B3'))
    if m['size'] != 256:
        raise ValueError('atlas %d' % m['size'])
    open(os.path.join(out, 'b3_sample.png'), 'wb').write(B.png_bytes(B.texture(m)))
    G.PARTS[:] = PARTS
    mj = G.mesh_json(m)
    mj['about'] = ('AFL Block-Mesh V2 B3 (user 2026-10-25 round 3, offline): B2 with continuous box arms, the sleeve its own '
                   'part. Generated by design/characters/block_mesh_v2/source/bm2_b3.py; the game mesh format.')
    json.dump(mj, open(os.path.join(out, 'b3_body.json'), 'w'), separators=(',', ':'))
    if '--game' in sys.argv:
        # into the dev switch (user 2026-10-25 "将第三行的 B3 网格和皮肤接入开发测试模式"): its own files beside B2's (kept),
        # the rig (bm2_game.py's, B2's bones and hand ends) pointed at them; run after bm2_game.py, which writes B2's paths
        assets = os.path.join(root, G.ASSETS)
        open(os.path.join(assets, GAME_TEXTURE), 'wb').write(B.png_bytes(B.texture(m)))
        json.dump(mj, open(os.path.join(assets, GAME_MESH), 'w'), separators=(',', ':'))
        rig_path = os.path.join(assets, 'rigs/afl_block_mesh_v2.json')
        rig = json.load(open(rig_path, encoding='utf-8'))
        rig['about'] = ('AFL Block-Mesh V2 B3 (user 2026-10-25, docs/dev/block_mesh_v2_b_ingame.md 9): B2 16 poser bones and '
                        'hand ends (bm2_game.py), the mesh and skin of B3 (bm2_b3.py --game). Dev test only '
                        '(client/skeleton/BlockMeshBody); afl_humanoid_v1.json stays the players rig.')
        rig['block_mesh']['mesh'], rig['block_mesh']['texture'] = GAME_MESH, GAME_TEXTURE
        json.dump(rig, open(rig_path, 'w', encoding='utf-8'), indent=1)
        print('game files', GAME_MESH, GAME_TEXTURE, 'rig pointed at them')
    uv = np.array(mj['vertices']).reshape(-1, 13)[:, 3:5] * m['size']
    off = np.abs(uv - np.round(uv)).max()
    print('quads', len(mj['quad_parts']), 'vertices', len(mj['vertices']) // 13, 'atlas', m['size'],
          'worst uv off the texel grid', round(float(off), 5))


if __name__ == '__main__':
    main()
