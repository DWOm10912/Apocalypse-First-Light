"""SUPERSEDED 2026-10-23: the grip is now hand-posed data (grip_pose_h1p1.json), applied and measured by
grip_check_h1p1.py. solve() below (the v7 / v8 optimiser) gave twisted fingers and a hooked thumb and is kept only
for its helpers (Hand, Field, WideGunField, root_rotation) and as a record; do not use it to make a grip.

H1-P1 two-hand P9-01 grip, solved in the gun's own frame (P9-01 V3 geo px, Blender axes: +x the gun's right, +y the
muzzle, +z up; 1 px = 19 mm) with the H1-P0 grip machinery (hand_p0_lib: Rig, GunField, capsules).

The grip follows the USMC pistol manual (Pistol Marksmanship, isosceles two-hand grip) and current thumbs-forward
instruction, not invention:
  firing hand  high on the backstrap, bore near parallel to the forearm, trigger finger straight outside the guard
               (the H1-P0 placement and finger closure, unchanged); the thumb on the left side pointing at the target,
               resting on the support thumb, off the slide stop and the slide
  support hand the heel in the exposed part of the grip, in the pocket between the firing fingertips and the firing
               heel; the fingers wrap over the firing fingers, the index under the trigger guard; the wrist turned
               down about 45 deg; the thumb forward along the frame below the firing thumb, off the slide stop
Solve order: support hand (around the firing hand's fingers and palm), then the firing thumb on top of it. The support
hand is solved as a right hand on the gun mirrored in x, then mirrored back: matrix S M S, finger eulers (x, -y, -z).
"""
import math
import random

import numpy as np
from mathutils import Matrix, Vector

import hand_p0_lib as HL

S = Matrix.Diagonal((-1.0, 1.0, 1.0))
BOX = ((-3.5, -7.5, -2.0), (3.5, 6.0, 7.0))      # px: both hands around the grip, the support thumb up the frame
CONTACT, PENETRATE, STEP = 0.015, -0.004, 0.5      # px, deg per unit: as H1-P0

# closing profiles: H1-P0 PROFILES (preset euler, closing rate per unit (x flex, y twist, z ulnar), max units)
PROFILES = {
    'index':  [((0, 0, 0), (1.0, 0, 0), 90), ((0, 0, 0), (1.2, 0, 0), 100), ((0, 0, 0), (0.8, 0, 0), 75)],
    'middle': [((0, 0, 0), (1.0, 0, 0), 90), ((0, 0, 0), (1.2, 0, 0), 100), ((0, 0, 0), (0.8, 0, 0), 75)],
    'ring':   [((0, 0, 0), (1.0, 0, -HL.FINGERS[2][4] / 70), 90), ((0, 0, 0), (1.2, 0, 0), 100), ((0, 0, 0), (0.8, 0, 0), 75)],
    'pinky':  [((0, 0, 0), (1.0, 0, -HL.FINGERS[3][4] / 70), 90), ((0, 0, 0), (1.2, 0, 0), 100), ((0, 0, 0), (0.8, 0, 0), 75)],
    'thumb':  [((0, 0, 0), (0, 0, 0), 0), ((0, 0, 0), (1.0, 0, 0), 20), ((0, 0, 0), (1.0, 0, 0), 10)],   # after reach
}
# firing index: H1-P0 profile, straight along the frame outside the trigger guard
FIRING_INDEX = [((0, 0, -19), (1.0, 0, 0), 40), ((0, 0, 0), (0.3, 0, 0), 20), ((0, 0, 0), (0.2, 0, 0), 12)]
THUMB_DOFS =[('thumb_01', 0), ('thumb_01', 1), ('thumb_01', 2), ('thumb_02', 0), ('thumb_03', 0)]
THUMB_LIMITS = [(-30, 80), (-60, 60), (-60, 60), (-15, 60), (-5, 60)]

# the thumb metacarpal crosses the top of the backstrap inside the web of the hand: its surface may sink this far into
# the gun (soft tissue of the web, ~3.8 mm), the phalanges may not
WEB_SINK = {'thumb_01': -0.2}

# firing thumb: tip forward on the left of the frame just behind the slide stop (P9 slide stop y -1.21 .. 0.96,
# z 4.47 .. 4.74), resting on the support thumb
RIGHT_THUMB = dict(tip=(-1.3, -1.7, 4.45), gap=0.02, touch=200.0, soft=WEB_SINK)

# support hand (as a right hand on the mirrored gun): root rotation (rake: the fingers turned down from the horizontal,
# ~45 deg; yaw toward the gun's far side; roll about the distal axis) and where its index MCP sits (y, z): at the
# front-left corner of the grip under the trigger guard; the palm slides in along -x until it touches the gun or the
# firing hand, the heel landing in the pocket between the firing fingertips and the firing heel
SUPPORT = dict(rake=45.0, yaw=-15.0, roll=20.0, anchor_h=(18.0, 30.0, 14.0), anchor_yz=(-2.6, 3.2), palm_gap=-0.1,
               seat_groups=('hand',))


def root_rotation(rake, yaw, roll):
    """H1-P0 root_rotation: palm toward -x, fingers forward (+y) and down by the rake, yawed toward -x by -yaw."""
    g, y = math.radians(rake), math.radians(yaw)
    r = Vector((0, math.sin(g), math.cos(g)))
    d0 = Vector((0, math.cos(g), -math.sin(g)))
    d = d0 * math.cos(y) - Vector((1, 0, 0)) * math.sin(y)
    return Matrix.Rotation(-math.radians(roll), 3, d) @ HL.mat3(r, d, r.cross(d))


class WideGunField(HL.GunField):
    """HL.GunField with a wider band (BAND voxels each side), so gaps up to ~9 mm are measured, not clamped at 3 mm."""
    BAND = 20.0

    def __init__(self, objects, voxel=0.025, box=BOX):
        import openvdb
        pts, quads, tris = [], [], []
        for ob in objects:
            base = len(pts)
            pts += [tuple(ob.matrix_world @ v.co) for v in ob.data.vertices]
            for p in ob.data.polygons:
                vs = [base + i for i in p.vertices]
                (quads if len(vs) == 4 else tris).append(vs if len(vs) <= 4 else vs[:3])
        xf = openvdb.createLinearTransform(voxelSize=voxel)
        grid = openvdb.FloatGrid.createLevelSetFromPolygons(
            np.array(pts, np.float32), np.array(tris, np.uint32).reshape(-1, 3), np.array(quads, np.uint32).reshape(-1, 4),
            xf, self.BAND)
        self.voxel, self.lo = voxel, np.floor(np.array(box[0]) / voxel).astype(int)
        hi = np.ceil(np.array(box[1]) / voxel).astype(int)
        self.arr = np.empty(tuple(hi - self.lo + 1), np.float32)
        grid.copyToArray(self.arr, ijk=tuple(int(i) for i in self.lo))
        self.far = float(grid.background)


class Field:
    """Signed distance px: the gun (mirrored in x when asked) united with capsules (a, b, r) in the same frame."""
    def __init__(self, gun, mirror=False, capsules=()):
        self.gun, self.mirror, self.caps = gun, mirror, list(capsules)

    def sd_many(self, p):
        q = np.array(p, np.float64)
        if self.mirror:
            q[:, 0] = -q[:, 0]
        d = self.gun.sd_many(q)
        for a, b, r in self.caps:
            d = np.minimum(d, HL.capsule_sd_many(np.asarray(p, np.float64), a, b, r))
        return d


class Hand:
    """One H1-P0 hand placed in a frame: rig, surface points per bone (bone-local px), finger eulers."""
    def __init__(self, bones, pts_h, rot, origin):
        self.bones, self.by = bones, {b.name: b for b in bones}
        self.rig = HL.Rig(bones, rot, origin)
        self.local = {n: np.array([tuple(self.rig.rest[n].inverted() @ self.rig.world_of_h(p)) for p in ps])
                      for n, ps in pts_h.items()}
        self.eulers = {}

    def world_points(self, world, n):
        m = np.array(world[n])
        return self.local[n] @ m[:3, :3].T + m[:3, 3]

    def capsules(self, names=None, scale=0.4, mirror=False):
        """Bone capsules at the current pose (0.8 of the skin radius by default, so neighbours may touch); the
        palm is three capsules along the metacarpals."""
        world = self.rig.pose(self.eulers)
        out = []
        for b in self.bones:
            if names is not None and b.name not in names:
                continue
            m = world[b.name]
            if b.radii is None:
                for name, mcp, *_ in HL.FINGERS[:4]:
                    a = m @ (Vector((mcp[0] * 0.8, 12.0, 4.0)) * HL.MM)
                    t = m @ (Vector(mcp) * HL.MM)
                    out.append((a, t, 11.0 * HL.MM))
                continue
            a = m.translation.copy()
            t = m @ Vector((0, b.length * HL.MM, 0))
            out.append((a, t, scale * (b.radii[0] + b.radii[1]) * HL.MM))
        if mirror:
            out = [(S @ a, S @ t, r) for a, t, r in out]
        return [(np.array(a), np.array(t), r) for a, t, r in out]


def place(bones, pts_h, spec, field):
    """Slide the hand in along -x (H1-P0 place) until the seat groups touch the field; spec['anchor'] (a bone head)
    or spec['anchor_h'] (H mm) is held at spec['anchor_yz']."""
    R = root_rotation(spec['rake'], spec['yaw'], spec['roll'])
    if 'anchor_h' in spec:      # a point of the hand (H mm) instead of a bone head
        anchor = Vector(spec['anchor_h']) * HL.MM
    else:
        anchor = next(b for b in bones if b.name == spec['anchor']).head * HL.MM
    y, z = spec['anchor_yz']
    palm = np.array([tuple(R @ (p * HL.MM)) for n in spec['seat_groups'] for p in pts_h[n]])

    def clearance(x):
        o = np.array(Vector((x, y, z)) - R @ anchor)
        return float(field.sd_many(palm + o).min())

    lo, hi = -1.0, 5.0
    assert clearance(hi) > 0, 'palm starts inside'
    for _ in range(40):
        mid = (lo + hi) / 2
        if clearance(mid) > spec['palm_gap']:
            hi = mid
        else:
            lo = mid
    return R, Vector((hi, y, z)) - R @ anchor, clearance(hi)


def descend(score, x, limits, steps=(8.0, 4.0, 2.0, 1.0, 0.5, 0.25)):
    """Coordinate descent within box limits; steps are per unit of each variable (scaled by `limits` spans)."""
    s = score(x)
    for st in steps:
        better = True
        while better:
            better = False
            for k, (lo, hi, unit) in enumerate(limits):
                for sgn in (1, -1):
                    y = list(x)
                    y[k] = min(max(y[k] + sgn * st * unit, lo), hi)
                    sy = score(y)
                    if sy < s - 1e-12:
                        s, x, better = sy, y, True
    return s, x


# finger fitting: degrees of freedom per chain (bone, euler axis), their limits (deg) and the step unit
FINGER_DOFS = lambda c: [(c + '_01', 0), (c + '_01', 2), (c + '_02', 0), (c + '_03', 0)]
FINGER_LIMITS = [(-10, 95, 1.0), (-20, 20, 0.5), (0, 105, 1.0), (0, 80, 1.0)]
FIRING_INDEX_LIMITS = [(-10, 30, 1.0), (-25, 5, 0.5), (0, 25, 1.0), (0, 15, 1.0)]   # straight along the frame


def fit_chain(hand, chain, field, obstacles, limits=FINGER_LIMITS, start=None):
    """Wrap one finger: joint angles that bring every phalanx onto the field (gun or other hand) without
    penetrating it or the obstacles (each segment's smallest gap squared, plus a stiff penetration penalty)."""
    dofs = FINGER_DOFS(chain)
    names = finger_names(chain)

    def pose_of(x):
        e = {n: [0.0, 0.0, 0.0] for n in names}
        for (n, axis), v in zip(dofs, x):
            e[n][axis] = v
        return {n: tuple(v) for n, v in e.items()}

    def score(x):
        e = dict(hand.eulers)
        e.update(pose_of(x))
        w = hand.rig.pose(e)
        gap = pen = 0.0
        for j, n in enumerate(names):
            pts = hand.world_points(w, n)
            d = field.sd_many(pts)
            gap += (1.0 + j * 0.5) * max(float(d.min()), 0.0) ** 2      # the field only: a neighbour is not a grip
            for a, b, r in obstacles:
                d = np.minimum(d, HL.capsule_sd_many(pts, a, b, r))
            pen += float(np.square(np.minimum(d - PENETRATE, 0)).sum())
        # mild preference for a natural cascade: the distal joints follow the proximal ones (2/3 rule)
        nat = 1e-6 * ((x[3] - 0.67 * x[2]) ** 2)
        return gap + 2000.0 * pen + nat

    starts = [start or [0.5 * (lo + hi) for lo, hi, _ in limits], [lo for lo, _, _ in limits],
              [0.25 * lo + 0.75 * hi for lo, hi, _ in limits]]
    best = min((descend(score, list(x), limits) for x in starts), key=lambda t: t[0])
    hand.eulers.update(pose_of(best[1]))
    world = hand.rig.pose(hand.eulers)
    cl, nb = [], []
    for n in names:
        pts = hand.world_points(world, n)
        cl.append(round(float(field.sd_many(pts).min()) / HL.MM, 2))
        nb.append(round(min([float(HL.capsule_sd_many(pts, a, b, r).min()) for a, b, r in obstacles] or [9.9]) / HL.MM, 2))
    return dict(deg=[round(v, 1) for v in best[1]], gap_mm=cl, neighbour_mm=nb)


PALM_SINK = 0.3      # px (~5.7 mm): the palm's soft tissue (thenar / hypothenar pads) may press this far in


def fit_root(hand, field, extra, move=(1.5, 1.5, 1.5, 30, 30, 30)):
    """Rigid placement of the hand (6 dof about the palm centre): the palmar surface pressed onto the field (mean gap
    of the closer half of the palmar points), sinking at most PALM_SINK, plus `extra(world_rest_matrix) -> cost`
    for the reference constraints. Returns the moved Hand and the result."""
    rest = hand.rig.rest
    z = hand.local['hand'][:, 2]
    palm_l = hand.local['hand'][z > 0.15]
    palm = np.array([tuple(rest['hand'] @ Vector(p)) for p in palm_l])
    c = Vector(palm.mean(0).tolist())
    half = len(palm) // 2
    root0 = hand.rig.root

    def moved(x):
        R = (Matrix.Rotation(math.radians(x[3]), 3, 'X') @ Matrix.Rotation(math.radians(x[4]), 3, 'Y')
             @ Matrix.Rotation(math.radians(x[5]), 3, 'Z')).to_4x4()
        return Matrix.Translation(c + Vector(x[:3])) @ R @ Matrix.Translation(-c) @ root0

    def score(x):
        m = moved(x)
        mm = np.array(m)
        pts = palm_l @ mm[:3, :3].T + mm[:3, 3]
        d = np.sort(field.sd_many(pts))
        return float(np.maximum(d[:half], 0).mean()) + 200.0 * float(np.square(np.minimum(d + PALM_SINK, 0)).sum()) \
            + extra(m)

    limits = [(-move[k], move[k], 0.05 if k < 3 else 1.0) for k in range(6)]
    s, x = descend(score, [0.0] * 6, limits)
    m = moved(x)
    new = Hand.__new__(Hand)
    new.bones, new.by, new.local, new.eulers = hand.bones, hand.by, hand.local, dict(hand.eulers)
    new.rig = HL.Rig(hand.bones, m.to_3x3(), m.translation)
    return new, dict(move=[round(v, 3) for v in x], score=round(s, 4), extra=round(extra(m), 4))


def reach(hand, spec, field, obstacles):
    """H1-P0 reach: thumb angles that bring its tip to the goal without penetrating, the last segment resting on
    the field (random search, then coordinate descent; seeded)."""
    names = ['thumb_01', 'thumb_02', 'thumb_03']
    target = np.array(spec['tip'])
    tip_len = hand.by['thumb_03'].length * HL.MM

    def pose_of(x):
        e = {n: [0.0, 0.0, 0.0] for n in names}
        for (n, axis), v in zip(THUMB_DOFS, x):
            e[n][axis] = v
        return {n: tuple(v) for n, v in e.items()}

    def score(x):
        e = dict(hand.eulers)
        e.update(pose_of(x))
        w = hand.rig.pose(e)
        pen = 0.0
        for n in names:
            pts = hand.world_points(w, n)
            d = field.sd_many(pts) - spec.get('soft', {}).get(n, spec['gap'])
            for a, b, r in obstacles:
                d = np.minimum(d, HL.capsule_sd_many(pts, a, b, r))
            pen += float(np.square(np.minimum(d, 0)).sum())
        tip = np.array(w['thumb_03'] @ Vector((0, tip_len, 0)))
        d = field.sd_many(hand.world_points(w, 'thumb_03')).min() - spec['gap']
        touch = spec['touch'] * max(float(d), 0.0) ** 2
        return float(np.square(tip - target).sum()) + 2000 * pen + touch

    rnd = random.Random(1)
    starts = sorted(([rnd.uniform(*l) for l in THUMB_LIMITS] for _ in range(3000)), key=score)[:8]

    def descend(x):
        s, step = score(x), 8.0
        while step > 0.1:
            better = False
            for k in range(len(THUMB_LIMITS)):
                for sgn in (1, -1):
                    y = list(x)
                    y[k] = min(max(y[k] + sgn * step, THUMB_LIMITS[k][0]), THUMB_LIMITS[k][1])
                    sy = score(y)
                    if sy < s:
                        s, x, better = sy, y, True
            if not better:
                step /= 2
        return s, x

    s, best = min((descend(x) for x in starts), key=lambda t: t[0])
    return pose_of(best), math.sqrt(max(s, 0.0))


def close(hand, chain, field, obstacles, base=None, prof=None):
    """H1-P0 closure: flex the chain's joints step by step until each touches (contact) or would penetrate."""
    names = ['%s_%02d' % (chain, j + 1) for j in range(3)]
    prof = prof or PROFILES[chain]
    base = base or {n: prof[j][0] for j, n in enumerate(names)}

    def apply(cs):
        for j, n in enumerate(names):
            hand.eulers[n] = tuple(base[n][i] + prof[j][1][i] * cs[j] for i in range(3))

    def clearances():
        world = hand.rig.pose(hand.eulers)
        res = []
        for n in names:
            w = hand.world_points(world, n)
            d = field.sd_many(w)
            for a, b, r in obstacles:
                d = np.minimum(d, HL.capsule_sd_many(w, a, b, r))
            res.append(float(d.min()))
        return res

    c, active, steps = [0.0] * 3, [True] * 3, 0
    apply(c)
    while any(active) and steps < 400:
        steps += 1
        trial = [c[j] + (STEP if active[j] else 0) for j in range(3)]
        apply(trial)
        cl = clearances()
        hit = [k for k in range(3) if cl[k] < PENETRATE]
        if hit:
            apply(c)
            for j in range(hit[-1] + 1):
                active[j] = False
            continue
        c = trial
        for k in range(3):
            if cl[k] < CONTACT:
                for j in range(k + 1):
                    active[j] = False
        for j in range(3):
            if c[j] >= prof[j][2]:
                active[j] = False
    apply(c)
    return c, clearances()


TOUCH = 0.08     # px (~1.5 mm): a surface point this close to the gun or the other hand counts as in contact


def contact(rh, lh, gun):
    """How closely the hands sit: per hand, the palmar half of the palm (H z > 0) and each finger chain, distances
    (px) of their surface points to the gun united with the other hand. Both hands in the gun frame."""
    out = {}
    for name, hand, mirror, other in (('right', rh, False, lh), ('left', lh, True, rh)):
        field = Field(gun, mirror=mirror, capsules=other.capsules(mirror=True))
        world = hand.rig.pose(hand.eulers)
        r = {}
        z = hand.local['hand'][:, 2]            # bone-local px; the hand bone frame is H (z palmar)
        d = field.sd_many(hand.world_points(world, 'hand'))[z > 0.15]
        r['palm_touch'] = round(float((d < TOUCH).mean()), 3)
        r['palm_gap_median_mm'] = round(float(np.median(np.maximum(d, 0))) / HL.MM, 1)
        for chain in ('thumb', 'index', 'middle', 'ring', 'pinky'):
            ds = [float(field.sd_many(hand.world_points(world, n)).min()) for n in finger_names(chain)]
            r[chain + '_mm'] = [round(x / HL.MM, 1) for x in ds]
        out[name] = r
    return out


def finger_names(chain):
    return ['%s_%02d' % (chain, j + 1) for j in range(3)]


# reference points on the hand (H mm): the web between thumb and index (pressed up under the beavertail in a high
# grip), the heel of the palm (hypothenar base, palmar), the index MCP
WEB_H, HEEL_H, INDEX_MCP_H = (36.0, 58.0, 4.0), (-8.0, 12.0, 14.0), HL.FINGERS[0][1]
THENAR_H = (18.0, 30.0, 14.0)    # H mm: the thenar eminence (base of the thumb), palmar surface
BORE_X = 0.0                 # px: the bore centre line (the gun is symmetric in x)
GUARD_BOTTOM_Z = 2.4         # px: underside of the trigger guard


def hand_point(m, p_mm):
    return m @ (Vector(p_mm) * HL.MM)


def wrist_angles(m):
    """Hand distal axis against the bore (+y): sideways (deg, + toward +x) and pitch (deg, - = fingers down)."""
    d = m.to_3x3().col[1]
    return math.degrees(math.atan2(d.x, d.y)), math.degrees(math.atan2(d.z, math.hypot(d.x, d.y)))


def firing_terms(m, gun):
    w, web = m.translation, hand_point(m, WEB_H)
    side, pitch = wrist_angles(m)
    return dict(wrist_x_mm=round((w.x - BORE_X) / HL.MM, 1), web_z_px=round(web.z, 2), web_x_mm=round(web.x / HL.MM, 1),
                web_gap_mm=round(gun.sd(web) / HL.MM, 1), side_deg=round(side, 1), pitch_deg=round(pitch, 1))


def firing_cost(m, gun):
    """USMC: grip as high on the backstrap as possible (the web up under the beavertail, on the backstrap centre),
    the bore centre line near parallel to the forearm (the wrist behind the bore, the hand's distal axis within the
    wrist's deviation range of the bore)."""
    w, web = m.translation, hand_point(m, WEB_H)
    side, pitch = wrist_angles(m)
    c = 20.0 * max(abs(w.x - BORE_X - 0.25) - 0.25, 0.0) ** 2
    c += 20.0 * max(4.15 - web.z, 0.0) ** 2 + 20.0 * max(abs(web.x) - 0.2, 0.0) ** 2 + 20.0 * max(gun.sd(web), 0.0) ** 2
    c += 0.01 * max(abs(side) - 20.0, 0.0) ** 2 + 0.01 * max(-pitch - 35.0, 0.0) ** 2 + 0.01 * max(pitch, 0.0) ** 2
    return c


def support_terms(m, field):
    th, mcp = hand_point(m, THENAR_H), hand_point(m, INDEX_MCP_H)
    side, pitch = wrist_angles(m)
    return dict(rake_deg=round(-pitch, 1), side_deg=round(side, 1),
                thenar_gun_gap_mm=round(float(field.gun.sd_many([tuple(S @ th)])[0]) / HL.MM, 1),
                thenar_gap_mm=round(float(field.sd_many([tuple(th)])[0]) / HL.MM, 1),
                index_mcp=(round(mcp.x, 2), round(mcp.y, 2), round(mcp.z, 2)))


def support_cost(m, field):
    """Thumbs-forward support hand: the wrist turned down about 45 deg, the heel on the exposed grip panel (the gun,
    not the firing hand), the index knuckle below the trigger guard."""
    heel, mcp = hand_point(m, HEEL_H), hand_point(m, INDEX_MCP_H)
    side, pitch = wrist_angles(m)
    c = 0.004 * (-pitch - 45.0) ** 2 + 0.01 * max(abs(side) - 30.0, 0.0) ** 2
    c += 20.0 * max(field.gun.sd(S @ heel), 0.0) ** 2
    c += 20.0 * max(mcp.z - GUARD_BOTTOM_Z, 0.0) ** 2
    return c


def fit_fingers(hand, field, report, prefix, limits):
    done = None
    for chain in ('index', 'middle', 'ring', 'pinky'):
        ob = hand.capsules(finger_names(done)) if done else []
        report[prefix + chain] = fit_chain(hand, chain, field, ob, limits.get(chain, FINGER_LIMITS))
        done = chain


# firing hand placement (palm-only seat, scanned against the USMC references): the wrist 4 mm right of the bore line,
# the web 2.4 mm under the beavertail, the palm pressed 0.2 px (~3.8 mm, soft tissue) into the grip panel
FIRING = dict(rake=20.0, yaw=-20.0, roll=8.0, anchor='middle_01', anchor_yz=(-1.7, 2.3), palm_gap=-0.2,
              seat_groups=('hand',))
PALM_Z = 0.15 / HL.MM        # H mm: palm points on the palmar side of the hand bone (z > 2.85 mm) seat the hand

# slide stop lever and slide (gun px, left side x < 0): the thumbs stay off both (USMC); boxes (lo, hi)
SLIDE_STOP = ((-1.3, -1.4, 4.45), (-0.5, 1.15, 4.85))

# thumbs by direction, not by a goal point: the distal phalanx forward (+y) along the frame's left side, its axis
# midpoint at a height in `band` (gun px): the support thumb against the frame below the slide stop, the firing thumb
# on top of it
FIRING_THUMB = dict(dir=(0.0, 1.0, 0.0), band=(4.4, 4.9), gap=0.02, soft=WEB_SINK)
SUPPORT_THUMB = dict(dir=(0.0, 1.0, 0.0), band=(3.75, 4.15), gap=0.02, soft=WEB_SINK)


def palm_points(pts_h):
    return dict(pts_h, hand=[p for p in pts_h['hand'] if p.z > PALM_Z])


def in_box(p, box, mirror=False):
    q = np.array(p, np.float64)
    if mirror:
        q[:, 0] = -q[:, 0]
    lo, hi = np.array(box[0]), np.array(box[1])
    inside = np.minimum(q - lo, hi - q).min(1)     # > 0 inside, depth to the nearest face
    return np.maximum(inside, 0.0)


def fit_thumb(hand, spec, field, obstacles, mirror=False):
    """Thumb angles (THUMB_DOFS) that lay the distal phalanx along spec['dir'] touching the field, its midpoint in
    spec['band'], no penetration (the metacarpal may sink by spec['soft']), the phalanges off the slide stop. Random
    starts, then coordinate descent; seeded."""
    names = finger_names('thumb')
    want = Vector(spec['dir']).normalized()
    tip_len = hand.by['thumb_03'].length * HL.MM

    def pose_of(x):
        e = {n: [0.0, 0.0, 0.0] for n in names}
        for (n, axis), v in zip(THUMB_DOFS, x):
            e[n][axis] = v
        return {n: tuple(v) for n, v in e.items()}

    def score(x):
        e = dict(hand.eulers)
        e.update(pose_of(x))
        w = hand.rig.pose(e)
        pen = 0.0
        for n in names:
            pts = hand.world_points(w, n)
            d = field.sd_many(pts) - spec['soft'].get(n, spec['gap'])
            for a, b, r in obstacles:
                d = np.minimum(d, HL.capsule_sd_many(pts, a, b, r))
            pen += float(np.square(np.minimum(d, 0)).sum())
            if n != 'thumb_01':
                pen += float(np.square(in_box(pts, SLIDE_STOP, mirror)).sum())
        m = w['thumb_03']
        dist = m.to_3x3().col[1]
        mid = (m @ Vector((0, 0.5 * tip_len, 0))).z
        lo, hi = spec['band']
        tip = hand.world_points(w, 'thumb_03')
        gap = max(float(field.sd_many(tip).min()) - spec['gap'], 0.0)
        return 4.0 * (1.0 - float(dist.dot(want))) + 40.0 * gap ** 2 + 2000.0 * pen             + 40.0 * (max(lo - mid, 0.0) + max(mid - hi, 0.0)) ** 2

    rnd = random.Random(1)
    starts = sorted(([rnd.uniform(*l) for l in THUMB_LIMITS] for _ in range(3000)), key=score)[:8]

    def run(x):
        s, step = score(x), 8.0
        while step > 0.1:
            better = False
            for k in range(len(THUMB_LIMITS)):
                for sgn in (1, -1):
                    y = list(x)
                    y[k] = min(max(y[k] + sgn * step, THUMB_LIMITS[k][0]), THUMB_LIMITS[k][1])
                    sy = score(y)
                    if sy < s:
                        s, x, better = sy, y, True
            if not better:
                step /= 2
        return s, x

    s, best = min((run(x) for x in starts), key=lambda t: t[0])
    hand.eulers.update(pose_of(best))
    w = hand.rig.pose(hand.eulers)
    tip = hand.world_points(w, 'thumb_03')
    m = w['thumb_03']
    mid = m @ Vector((0, 0.5 * tip_len, 0))
    return dict(score=round(s, 4), dir_deg=round(math.degrees(m.to_3x3().col[1].angle(want)), 1),
                tip_gap_mm=round(float(field.sd_many(tip).min()) / HL.MM, 1),
                distal_mid=tuple(round(v, 2) for v in mid))


def close_fingers(hand, field, report, prefix, first_prof=None):
    """H1-P0 closure chain by chain (index to pinky), each against the field and the previous finger's capsules."""
    done = None
    for chain in ('index', 'middle', 'ring', 'pinky'):
        ob = hand.capsules(finger_names(done)) if done else []
        prof = first_prof if (chain == 'index' and first_prof) else None
        units, cl = close(hand, chain, field, ob, prof=prof)
        report[prefix + chain] = dict(units=units, clearance_mm=[round(x / HL.MM, 1) for x in cl])
        done = chain


def solve(gun, pts_h, right_rest=None, right_grip=None, firing=FIRING, support=SUPPORT):
    """gun: WideGunField over BOX; pts_h: coarse surface points per bone (H mm). Returns (right matrix, right eulers,
    left matrix, left eulers, report, rh, lh); matrices are hand -> gun px (left in the gun's own, unmirrored frame).
    The H1-P0 placement (right_rest, right_grip) is no longer used: its wrist sits 28 mm right of the bore."""
    bones = HL.skeleton()
    seat = palm_points(pts_h)
    report = {}
    plain = Field(gun)
    # firing hand: placed by the references, fingers closed (index straight along the frame outside the guard)
    R, origin, first = place(bones, seat, firing, plain)
    rh = Hand(bones, pts_h, R, origin)
    report['right_ref'] = firing_terms(rh.rig.root, gun)
    close_fingers(rh, plain, report, 'right_', FIRING_INDEX)
    # support hand on the mirrored gun, around the mirrored firing fingers and palm
    names = [b.name for b in bones if not b.name.startswith('thumb')]
    caps = rh.capsules(names, mirror=True)
    sink = -support['palm_gap']      # the support palm sinks into the gun, never into the firing hand's skin
    field = Field(gun, mirror=True, capsules=caps)
    seat_field = Field(gun, mirror=True, capsules=[(a, b, r + sink) for a, b, r in rh.capsules(names, 0.5, True)])
    R, origin, first = place(bones, seat, support, seat_field)
    lh = Hand(bones, pts_h, R, origin)
    report['left_ref'] = support_terms(lh.rig.root, field)
    close_fingers(lh, field, report, 'left_')
    report['left_thumb'] = fit_thumb(lh, SUPPORT_THUMB, field, lh.capsules(finger_names('index')), mirror=True)
    # firing thumb over the support thumb (support capsules back in the gun frame)
    over = Field(gun, capsules=lh.capsules(mirror=True))
    report['right_thumb'] = fit_thumb(rh, FIRING_THUMB, over, rh.capsules(finger_names('index')))
    report['contact'] = contact(rh, lh, gun)
    right = rh.rig.root.copy()
    left = S.to_4x4() @ lh.rig.root @ S.to_4x4()
    left_e = {n: (e[0], -e[1], -e[2]) for n, e in lh.eulers.items()}
    return right, dict(rh.eulers), left, left_e, report, rh, lh
