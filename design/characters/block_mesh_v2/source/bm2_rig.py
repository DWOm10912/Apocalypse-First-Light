"""AFL Block-Mesh V2 prototypes (2026-10-25): the shared body. Units are model px (1 px = 0.9375 / 16 m, the vanilla
player's), Bedrock axes: +y up from the ground, front -z, the body's right at -x.

Common base for all four prototypes (docs/dev/block_mesh_v2_prototypes.md 2): head 8 / torso 9 / legs 15 px, total 32 px
as vanilla, so the eye (1.62 m), the camera and the hitbox stay where they are. Above the waist (y 18) everything is where
vanilla has it; the pelvis band is 3 px (vanilla 6) and the legs 15 px (vanilla 12) with the knee at 9 px, and a foot.

Main skeleton: 22 bones, shared by A-D. Prototype D adds 15 finger bones per hand (52 in all).
The 16-bone rig the existing poser solves (rigs/afl_humanoid_v1.json format) is the main skeleton less neck, spine,
clavicles and toes; poser_rig() writes it, warp() the block -> V2 rest-space map of each body part for prop keys.
"""
import json
import math

PX = 0.9375 / 16

# ---------------------------------------------------------------- proportions (px)
HEAD = dict(w=8, h=8, d=8, base=24)
TORSO = dict(w=8, d=4, top=24, waist=18, chest=20.5, hip=15)
ARM = dict(top=24, shoulder=22.5, elbow=18, wrist=14.5, tip=12, x=5.5)   # joints on the arm's axis
LEG = dict(w=4, d=4, hip=15, knee=9, ankle=1.5, x=2)
FOOT = dict(w=4, h=1.5, front=-3.5, back=2, ball=-1.5)    # the sole at y 0; the toes bend at the ball

# ---------------------------------------------------------------- main skeleton: name, parent, pivot
MAIN = [
    ('root', None, (0, 0, 0)),
    ('pelvis', 'root', (0, TORSO['hip'], 0)),
    ('spine', 'pelvis', (0, TORSO['waist'], 0)),
    ('chest', 'spine', (0, TORSO['chest'], 0)),
    ('neck', 'chest', (0, 23.5, 0)),
    ('head', 'neck', (0, HEAD['base'], 0)),
]
for s, sx in (('r', -1), ('l', 1)):
    MAIN += [
        ('clavicle_' + s, 'chest', (sx * 1.0, 23.0, 0)),
        ('upper_arm_' + s, 'clavicle_' + s, (sx * ARM['x'], ARM['shoulder'], 0)),
        ('forearm_' + s, 'upper_arm_' + s, (sx * ARM['x'], ARM['elbow'], 0)),
        ('hand_' + s, 'forearm_' + s, (sx * ARM['x'], ARM['wrist'], 0)),
    ]
for s, sx in (('r', -1), ('l', 1)):
    MAIN += [
        ('thigh_' + s, 'pelvis', (sx * LEG['x'], LEG['hip'], 0)),
        ('shin_' + s, 'thigh_' + s, (sx * LEG['x'], LEG['knee'], 0)),
        ('foot_' + s, 'shin_' + s, (sx * LEG['x'], LEG['ankle'], 0)),
        ('toes_' + s, 'foot_' + s, (sx * LEG['x'], 0.6, FOOT['ball'])),
    ]

# Prototype D: the hand (palm 1.7 px, fingers 1.5 px) in the hand bone's rest frame, which hangs down (-y). The palm faces
# the body (+x on the right hand), the thumb is ahead (-z). Finger roots across the knuckle line (z), segment lengths.
FINGERS = {
    'thumb': dict(root=(0.35, -0.45, -0.95), seg=(0.75, 0.6, 0.5), w=0.6),
    'index': dict(root=(0.05, -1.7, -0.62), seg=(0.62, 0.45, 0.4), w=0.5),
    'middle': dict(root=(0.05, -1.75, -0.12), seg=(0.66, 0.48, 0.42), w=0.5),
    'ring': dict(root=(0.05, -1.7, 0.36), seg=(0.6, 0.45, 0.4), w=0.48),
    'pinky': dict(root=(0.05, -1.6, 0.8), seg=(0.48, 0.36, 0.34), w=0.42),
}


def finger_bones():
    """Prototype D's finger chains: (name, parent, rest pivot). The right palm faces +x; the left mirrors x."""
    out = []
    for s, sx in (('r', 1), ('l', -1)):
        for f, d in FINGERS.items():
            rx, ry, rz = d['root']
            x, y, z = -ARM['x'] * sx + rx * sx, ARM['wrist'] + ry, rz
            parent = 'hand_' + s
            for i, length in enumerate(d['seg']):
                name = '%s_%02d_%s' % (f, i + 1, s)
                out.append((name, parent, (round(x, 4), round(y, 4), round(z, 4))))
                if f == 'thumb':           # down and forward from the heel of the hand
                    y -= length * 0.7
                    z -= length * 0.7
                else:
                    y -= length
                parent = name
    return out


def skeleton(fingers=False, variant=None):
    if variant in VARIANTS:
        return VARIANTS[variant]
    return MAIN + (finger_bones() if fingers else [])


# Later variants' main skeletons (bm2_game.py registers B2: the same bones, other pivots).
VARIANTS = {}


# ---------------------------------------------------------------- the 16-bone rig for the existing poser
POSER_BONES = ['root', 'pelvis', 'chest', 'head', 'upper_arm_r', 'forearm_r', 'hand_r', 'upper_arm_l', 'forearm_l', 'hand_l',
               'thigh_r', 'shin_r', 'foot_r', 'thigh_l', 'shin_l', 'foot_l']


def poser_rig(vanilla_rig, main=None):
    """rigs/afl_humanoid_v1.json at the V2 proportions: the poser's chest turns at the waist (the spine's pivot)."""
    piv = {n: p for n, _, p in (main or MAIN)}
    piv['chest'] = piv['spine']
    r = json.loads(json.dumps(vanilla_rig))
    r['about'] = 'SCRATCH 2026-10-25: afl_humanoid_v1 at the AFL Block-Mesh V2 prototype proportions (offline checks only).'
    for b in r['bones']:
        b['pivot'] = list(piv[b['name']])
    skin = {
        'body': ([-4, 15, -2], [8, 9, 4], [6]),
        'right_arm': ([-8, 12, -2], [4, 12, 4], [6, 10]),
        'left_arm': ([4, 12, -2], [4, 12, 4], [6, 10]),
        'right_leg': ([-4, 0, -2], [4, 15, 4], [6, 14]),
        'left_leg': ([0, 0, -2], [4, 15, 4], [6, 14]),
    }
    for s in r['skin']:
        f, size, cuts = skin[s['part']]
        s['from'], s['size'], s['cuts'] = f, size, cuts
        s.pop('slim_from', None)
        s.pop('slim_size', None)
    for name, sock in r['sockets'].items():
        if name == 'holster':      # on the thigh: vanilla's 12 -> 6 is V2's 15 -> 9
            x, y, z = sock['pos']
            sock['pos'] = [x, round(LEG['knee'] + (y - 6) / 6 * (LEG['hip'] - LEG['knee']), 4), z]
    return r


def warp():
    """Block (vanilla) rest model px -> V2 rest model px per body part, Java axes (y down: y_java = 24 - y), as two boxes per
    part; MeshMapExport maps a prop key per axis between them. Above the waist nothing moves."""
    j = lambda y: 24 - y
    out = {'pelvis': {'block': [[-4, j(18), -2], [4, j(12), 2]], 'mesh': [[-4, j(18), -2], [4, j(TORSO['hip']), 2]]}}
    for s, (bx, vx) in (('r', ((-3.9, 0.1), (-4, 0))), ('l', ((-0.1, 3.9), (0, 4)))):
        for bone, (b0, b1), (m0, m1) in (('thigh', (12, 6), (LEG['hip'], LEG['knee'])), ('shin', (6, 2), (LEG['knee'], LEG['ankle'])),
                                          ('foot', (2, 0), (LEG['ankle'], 0))):
            out[bone + '_' + s] = {'block': [[bx[0], j(b0), -2], [bx[1], j(b1), 2]], 'mesh': [[vx[0], j(m0), -2], [vx[1], j(m1), 2]]}
    return out


if __name__ == '__main__':
    import sys
    van = json.load(open(sys.argv[1]))
    json.dump(poser_rig(van), open(sys.argv[2], 'w'), indent=1)
    json.dump(warp(), open(sys.argv[3], 'w'), indent=1)
    sk = skeleton(True)
    print('main bones', len(MAIN), 'with fingers', len(sk))
