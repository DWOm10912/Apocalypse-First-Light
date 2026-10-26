"""AFL Mesh survivor V1 third-person locomotion A1/A2 (2026-10-25, docs/dev/mesh_survivor_v1_tp_locomotion_a1.md):
CMU motion capture -> the frozen 54-bone survivor_rig, offline (numpy, no Blender). The skeleton is only read (its rest
frames dumped from Blender as JSON); names, tree, rest pose and lengths never change.

  python -I anim_tp_v1.py <rig_rest.json> <cmu_dir> <out_dir> [clip ...]

Per clip it writes <out_dir>/<name>.blend.json (per frame each pose bone's basis: quaternion w x y z, the hips location,
for anim_tp_blender_v1.py to key as a separate action) and <name>.clip.json (the game clip: per bone its rotation about
its rest pivot in the render model frame x y z w, the hips offset in blocks) and prints the checks.

Retarget:
- The source joint rotations and root path are low-passed first (zero-phase Gaussian, CUTOFF per kind): the raw CMU
  data jitters frame to frame, which the foot hold and leg IK would carry into the hips and legs.
- CMU is Y-up with the rest pose facing +Z; A turns it to Blender Z-up facing -Y, a yaw Psi turns the travel to -Y.
- Trunk, neck, head, clavicles, feet, toes: the source segment's world turn from its rest (every CMU rest frame is I) on
  the AFL rest frame, W = Psi A G A^T R_rest. Both rests stand upright with the feet flat.
- Thigh, shin, upper arm, forearm: the bone aimed along the source bone, its hinge carried by the source turn (legs: the
  lateral X; arms: direction x forward), so the AFL A-pose arms take the T-pose source rightly.
- Legs: the source ankle relative to its hip joint, scaled by AFL leg / source leg, reached by a 2-bone IK in the source
  knee plane; the clip is then set so the lowest sole point of the cycle touches z = 0.
- Hands follow the forearm, forearm_twist stays (its constraint copies the hand's twist, which is zero), fingers take
  the relaxed curl of skin_male_v1.RELAXED.
- In place: the root's travel over the cycle is taken off as a straight line, so a stance foot slides back at the
  reference speed; the game plays the clip at (actual speed / reference speed), which holds that foot on the ground.
- Loop: the cycle (start, length) best matching pose and velocity at its two ends; what is left is ramped out linearly.
- Batch 2 (2026-10-25): kinds move (any gait: the root travels), idle and sit (a seated hold, the hip joints put a set
  height over a cushion and the feet held on the floor). STYLE per clip name; stride scales the ankle path fore-aft
  about its cycle mean (and the reference speed with it), so jog / run / sprint come from one source in phase. Every
  move clip is turned so that its first sample is the left foot's touchdown: the game blends gaits at one phase.
"""
import sys, os, json, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
import mocap_cmu as cmu

A = np.array([[1., 0, 0], [0, 0, -1], [0, 1, 0]])
C3 = np.array([[1., 0, 0], [0, 0, 1], [0, -1, 0]])            # Blender (x, y, z) -> render (x, z, -y)
FWD = np.array([0., -1, 0])
X = np.array([1., 0, 0])
# CMU capture rate per subject, 120 Hz unless listed. The index is not to be trusted: subjects 77 and 139 are the same
# files and 143 is listed 120 Hz, yet a run's flight phase falls at 9.8 m/s^2 only at 60 Hz (scratchpad gravity.py)
FPS_SRC = {'77': 60, '139': 60, '143': 60, '75': 60}
FPS_OUT = 60
# lowest boot points at rest (right side, Blender m, from male_v1_boots); the left side mirrors x
SOLE = (('foot', (-0.107, 0.053, -0.003)), ('foot', (-0.116, -0.117, -0.003)), ('toes', (-0.114, -0.191, -0.001)))
RELAXED = (14, 18, 8, (6, -8, -8))
# A relaxed hand (batch 2 fix, 2026-10-25): fingers together, flexed more toward the little finger (MCP / PIP / DIP),
# the thumb in by the index. RELAXED and the run's fist stay for the clips already seen (idle, run).
HAND_RELAXED = dict(close=0.85, thumb=(20.0, 10.0, 15.0),
                    curl={'index': (22, 32, 18), 'middle': (26, 38, 20), 'ring': (30, 42, 22), 'pinky': (34, 46, 24)})
FINGERS = ('thumb', 'index', 'middle', 'ring', 'pinky')
TRUNK = (('hips', 'root'), ('spine', 'upperback'), ('chest', 'thorax'), ('head', 'head'))
CONTACT_M = 0.005
KNEE_MIN = 4.0          # the knee never locks fully straight (deg)
KNEE_STAND = 8.0        # idle: the hips are raised until the median knee bends this much (CMU stands read 15-30 deg)
CUTOFF = {'idle': 3.0, 'move': 10.0, 'sit': 3.0}   # source low-pass (Hz): a stand sways under 1 Hz, a run's foot
                                                   # strikes need more
BREATH = {'spine': 0.5, 'chest': 0.8}   # sit 'still': a breath's pitch on these (deg about the bone's X)
HIP_OVER_SEAT = 0.10    # sit: the hip joints over the cushion (m): the jeans reach 0.10-0.115 behind the thigh axis, less
                        # what the cushion gives
# Run style on top of the source (user 2026-10-25: 16_46 read "a bit girlish" in game): measured against CMU subject 9's
# runs (09_01-11) 16_46 holds the hands high and tight (elbow 61 deg, flare 1 cm, swing 15 cm), puts the feet on one
# line (width 0.7 cm) and twists the pelvis 19 / shoulders 28 deg with a 4.8 deg lean; subject 9: elbow ~73, flare 3-5,
# swing 22-25 cm, width 1-5 cm, pelvis ~10, shoulders ~20, lean 7-9. Applied per frame before the leg IK.
RUN = dict(elbow_open=14.0,      # elbow flexion reduced by this (deg; negative: bent more), never under ELBOW_MIN
           arm_out=6.0,          # both arm bones turned out about the forward axis (deg)
           arm_swing=1.2,        # upper arm fore-aft angle about its cycle mean, scaled (the opened elbow already swings
                                 # the hand further)
           step_width=0.05,      # ankle targets moved apart sideways (m, in all)
           hips_yaw=0.6,         # pelvis twist about the vertical, scaled
           chest_yaw=0.75,       # upper back / thorax twist about the vertical, scaled (the clavicles turn with the thorax)
           lean=3.0,             # spine and chest pitched forward (deg each)
           fingers=(40, 50, 30, (14, -14, -18)))   # a loose fist instead of RELAXED
NEUTRAL = dict(elbow_open=0.0, arm_out=0.0, arm_swing=1.0, step_width=0.0, hips_yaw=1.0, chest_yaw=1.0, lean=0.0,
               hips_pitch=0.0,   # pelvis pitched forward (deg; the hip joints are kept where the source has them)
               hips_drop=0.0,    # the hips lowered after the stand height is set (m), the legs bend to the feet
               stride=1.0,       # move: the ankle path fore-aft about its cycle mean, scaled; the reference speed too
               lift=1.0,         # move: the ankle height over its cycle low, scaled
               fingers=RELAXED)
# Jog and sprint: the run's source in step (a vanilla walk is 4.32 m/s, its sprint 5.61; CMU has no straight run over
# 4.45 m/s). Stride 0.6 -> 2.51 m/s, 1.3 -> 5.44 m/s at the run's 178 steps a minute; a sprint leans more, swings
# the arms further and lifts the heel higher. AFL's load and injury slow-downs hold any speed from 1.3 to 4.32 m/s, so
# each held speed has a clip of its own near it: the fast walk is 07_12 "brisk walk" (1.96 m/s, 138 steps a minute;
# the walk's source at a longer stride either could not reach the ground or, lowered, dragged the swing foot). Crouch: 136_09 "walk crouched" (0.8 m/s, hips at 0.75 of the leg, trunk
# 33 deg) taken to the 1.3 m/s sneak by a longer stride; the crouched idle is the stand (77_02) brought down to it.
STYLE = {
    'run': dict(NEUTRAL, **RUN),
    'jog': dict(NEUTRAL, **dict(RUN, lean=2.0, arm_swing=1.0), stride=0.6, lift=0.8),
    'sprint': dict(NEUTRAL, **dict(RUN, lean=7.0, arm_swing=1.45, elbow_open=8.0), stride=1.3, lift=1.25),
    'crouch_walk': dict(NEUTRAL, stride=1.25, lift=1.3),
    # the boots reach 0.19 m ahead of the ankle, longer than the actors' feet: the swing foot is lifted more not to drag
    'walk_fast': dict(NEUTRAL, lift=1.45),
    'crouch_idle': dict(NEUTRAL, hips_drop=0.22, hips_pitch=18.0, lean=26.0, elbow_open=-14.0),
}
ELBOW_MIN = 20.0

# name: CMU clip (subject_trial), kind, options (style: STYLE key; seat: sit cushion height m; window: the seconds of
# the source a cycle is looked for in; ahead: sit, the hip joints ahead of the seat centre m). 16_46 run/jog 4.1 m/s;
# 77_02 "standing" (careful actions, 60 Hz); 35_01 walk 1.29 m/s (subject 35: steps 12 cm apart, pelvis turn 7 deg;
# subject 16 walks sway the pelvis 14-15 deg); 136_09 walk crouched; 75_18 "high sit", seated 1.6-3.3 s (hip joints
# 0.43 m up). Seats: the wooden chair 0.465 m (its back 0.16 m behind the centre: the hip joints 0.05 m behind), the
# office chair 0.547 m.
CLIPS = {
    'tp_run_v1': ('16_46', 'move', {'style': 'run'}),
    'tp_jog_v1': ('16_46', 'move', {'style': 'jog'}),
    'tp_sprint_v1': ('16_46', 'move', {'style': 'sprint', 'slow_stance': True}),
    'tp_walk_v1': ('35_01', 'move', {'cutoff': 8.0, 'slow_stance': True, 'hand': HAND_RELAXED}),
    'tp_walk_fast_v1': ('07_12', 'move', {'style': 'walk_fast', 'cutoff': 8.0, 'slow_stance': True, 'hand': HAND_RELAXED}),
    'tp_crouch_walk_v1': ('136_09', 'move', {'style': 'crouch_walk', 'cutoff': 6.0, 'slow_stance': True, 'hand': HAND_RELAXED}),
    'tp_idle_v1': ('77_02', 'idle', {}),
    'tp_crouch_idle_v1': ('77_02', 'idle', {'style': 'crouch_idle', 'hand': HAND_RELAXED}),
    'tp_sit_chair_v1': ('75_18', 'sit', {'window': (1.6, 3.3), 'seat': 0.465, 'ahead': -0.05, 'still': 4.0, 'hand': HAND_RELAXED}),
    'tp_sit_office_v1': ('75_18', 'sit', {'window': (1.6, 3.3), 'seat': 0.547, 'ahead': -0.05, 'still': 4.0, 'hand': HAND_RELAXED}),
}


def unit(v):
    return v / np.linalg.norm(v)


def frame(d, h):
    d = unit(d)
    h = unit(h - np.dot(h, d) * d)
    return np.column_stack([d, h, np.cross(d, h)])


def yaw(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1.]])


def q_from_m(R):
    t = np.trace(R)
    if t > 0:
        s = math.sqrt(t + 1) * 2
        q = np.array([s / 4, (R[2, 1] - R[1, 2]) / s, (R[0, 2] - R[2, 0]) / s, (R[1, 0] - R[0, 1]) / s])
    else:
        i = int(np.argmax(np.diag(R)))
        j, k = (i + 1) % 3, (i + 2) % 3
        s = math.sqrt(1 + R[i, i] - R[j, j] - R[k, k]) * 2
        q = np.zeros(4)
        q[0] = (R[k, j] - R[j, k]) / s
        q[1 + i] = s / 4
        q[1 + j] = (R[j, i] + R[i, j]) / s
        q[1 + k] = (R[k, i] + R[i, k]) / s
    return q / np.linalg.norm(q)


def m_from_q(q):
    w, x, y, z = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def q_mul(a, b):
    w1, x1, y1, z1 = a
    w2, x2, y2, z2 = b
    return np.array([w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2, w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
                     w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2, w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2])


def q_inv(q):
    return np.array([q[0], -q[1], -q[2], -q[3]])


def q_slerp(a, b, t):
    if np.dot(a, b) < 0:
        b = -b
    d = min(1.0, float(np.dot(a, b)))
    if d > 0.9995:
        return unit(a + (b - a) * t)
    th = math.acos(d)
    return (math.sin((1 - t) * th) * a + math.sin(t * th) * b) / math.sin(th)


def q_angle(q):
    return math.degrees(2 * math.acos(min(1.0, abs(q[0]))))


def euler_xyz(deg):
    x, y, z = (math.radians(v) for v in deg)
    rx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    rz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return rz @ ry @ rx


def m4(R=None, t=None):
    M = np.eye(4)
    if R is not None:
        M[:3, :3] = R
    if t is not None:
        M[:3, 3] = t
    return M


class Rig:
    def __init__(self, path):
        data = json.load(open(path))
        self.order = [b['name'] for b in data]
        self.parent = {b['name']: b['parent'] for b in data}
        self.rest = {b['name']: np.array(b['m']) for b in data}
        self.tail = {b['name']: np.array(b['tail']) for b in data}
        self.deform = [b['name'] for b in data if b['deform']]
        self.inv = {n: np.linalg.inv(m) for n, m in self.rest.items()}
        self.link = {n: (self.inv[p] @ self.rest[n] if p else self.rest[n]) for n, p in self.parent.items()}

    def head(self, n):
        return self.rest[n][:3, 3]

    def R(self, n):
        return self.rest[n][:3, :3]

    def dir(self, n):
        return unit(self.tail[n] - self.head(n))

    def length(self, n):
        return float(np.linalg.norm(self.tail[n] - self.head(n)))

    def world(self, rot=None, hips=None, basis=None):
        """Pose frames: world rotations where given (rot), the hips head (hips), local bases (basis), else follow."""
        rot, basis = rot or {}, basis or {}
        W = {}
        for n in self.order:
            p = self.parent[n]
            M = (W[p] @ self.link[n]) if p else self.rest[n].copy()
            if n in basis:
                M = M @ basis[n]
            if n in rot:
                M[:3, :3] = rot[n]
            if n == 'hips' and hips is not None:
                M[:3, 3] = hips
            W[n] = M
        return W

    def basis(self, W, n):
        p = self.parent[n]
        return self.inv[n] @ (self.rest[p] @ np.linalg.inv(W[p]) if p else np.eye(4)) @ W[n]


def gauss(x, sigma):
    """Zero-phase Gaussian low-pass along axis 0 (sigma in samples), the ends held."""
    r = int(math.ceil(3 * sigma))
    k = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    p = np.concatenate([np.repeat(x[:1], r, 0), x, np.repeat(x[-1:], r, 0)])
    return np.stack([np.convolve(p[:, i], k, 'valid') for i in range(x.shape[1])], axis=1)


def smooth_rot(Rs, sigma):
    q = np.array([q_from_m(R) for R in Rs])
    for i in range(1, len(q)):                       # one hemisphere, so the components filter linearly
        if np.dot(q[i], q[i - 1]) < 0:
            q[i] = -q[i]
    q = gauss(q, sigma)
    return np.array([m_from_q(unit(v)) for v in q])


class Source:
    def __init__(self, cmu_dir, clip, cutoff=None):
        """cutoff: the -3 dB frequency (Hz) of a low-pass on the joint rotations and the root path before FK; the CMU
        captures are unfiltered (77_02 jitters 1-2 deg a frame at the root, near its Euler gimbal lock)."""
        subj = clip.split('_')[0]
        skel = cmu.Skeleton(os.path.join(cmu_dir, subj + '.asf'))
        frames = cmu.read_amc(os.path.join(cmu_dir, clip + '.amc'))
        self.fps = FPS_SRC.get(subj, 120)
        loc = [skel.local(f) for f in frames]
        if cutoff:
            sigma = 0.1325 * self.fps / cutoff          # Gaussian: |H(f)| = 0.707 at f = 0.1325 / sigma
            t = gauss(np.array([l[0] for l in loc]), sigma)
            rots = {b: smooth_rot([l[1][b] for l in loc], sigma) for b in loc[0][1]}
            loc = [(t[i], {b: rots[b][i] for b in rots}) for i in range(len(loc))]
        fk = [skel.chain(*l) for l in loc]
        self.n = len(fk)
        self.start = {b: np.array([A @ f[b][0] for f in fk]) for b in fk[0]}
        self.end = {b: np.array([A @ f[b][1] for f in fk]) for b in fk[0]}
        self.G = {b: np.array([A @ f[b][2] @ A.T for f in fk]) for b in fk[0]}
        self.rest_dir = {b.name: A @ b.direction for b in skel.order if b.name != 'root'}
        self.leg = (skel.bones['lfemur'].length + skel.bones['ltibia'].length
                    + skel.bones['rfemur'].length + skel.bones['rtibia'].length) / 2
        self.floor = min(self.end[b][:, 2].min() for b in ('ltoes', 'rtoes', 'lfoot', 'rfoot'))
        self.root = self.start['root']


def period(src):
    h = src.end['ltibia'][:, 2] - src.end['ltibia'][:, 2].mean()
    n = len(h)
    ac = np.correlate(h, h, 'full')[n - 1:] / np.arange(n, 0, -1)
    lo, hi = int(0.3 * src.fps), min(int(1.6 * src.fps), n - 10)
    return lo + int(np.argmax(ac[lo:hi]))


JOINTS = ('lfemur', 'ltibia', 'lfoot', 'ltoes', 'rfemur', 'rtibia', 'rfoot', 'rtoes', 'thorax', 'head',
          'lhumerus', 'lradius', 'lhand', 'rhumerus', 'rradius', 'rhand')


def rel(src):
    """Joint ends relative to the root (horizontal), the root height kept: (frames, joints, 3)."""
    r = src.root.copy()
    r[:, 2] = 0
    return np.stack([src.end[j] - r for j in JOINTS] + [src.root - r], axis=1)


def best_cycle(src, lengths, lo, hi):
    P = rel(src)
    V = np.diff(P, axis=0)
    best = None
    for N in lengths:
        for a in range(lo, hi - N - 1):
            c = np.sum((P[a] - P[a + N]) ** 2) + 100 * np.sum((V[a] - V[a + N]) ** 2)
            if best is None or c < best[0]:
                best = (c, a, N)
    return best


def ik2(H, T, n, L1, L2):
    v = T - H
    D = float(np.linalg.norm(v))
    u = v / D
    D = min(max(D, abs(L1 - L2) + 1e-4), math.sqrt(L1 * L1 + L2 * L2 + 2 * L1 * L2 * math.cos(math.radians(KNEE_MIN))))
    n = unit(n - np.dot(n, u) * u)
    w = np.cross(u, n)                       # the knee bends forward (straight leg: u = -Z, n = +X -> w = -Y)
    ca = (L1 * L1 + D * D - L2 * L2) / (2 * L1 * D)
    a = math.acos(max(-1.0, min(1.0, ca)))
    knee = H + L1 * (math.cos(a) * u + math.sin(a) * w)
    return knee, H + u * D, n


def hinge_leg(d):
    return unit(X - np.dot(X, d) * d)


def hinge_arm(d):
    return unit(np.cross(d, FWD))


def finger_basis(rig, curl=RELAXED):
    if isinstance(curl, dict):
        return hand_basis(rig, curl)
    out = {}
    k, m, t, th = curl
    for n in rig.order:
        part, _, rest = n.partition('_')
        if part not in FINGERS:
            continue
        seg, s = int(rest.split('_')[0]), rest.split('_')[1]
        if part == 'thumb':
            x, y, z = th if seg == 1 else (th[0] * 1.6, 0, 0)
        else:
            x, y, z = ((k, m, t)[seg - 1], 0, 0)
        if s == 'l':
            y, z = -y, -z
        out[n] = m4(euler_xyz((x, y, z)))
    return out


def hand_basis(rig, hand):
    """A hand shape (HAND_RELAXED): the rest fan of the fingers closed by hand['close'] (the rig's rest hand spreads
    index +8, ring -7, pinky -15 deg about the middle finger, the fingers nearly straight: user 2026-10-25 "five fingers
    spread" read wrong), each finger's three joints flexed as given, the thumb brought hand['thumb'][0] deg toward the
    index and its two outer joints flexed. Built on the right hand, mirrored (conjugated by diag(-1, 1, 1) in the bone
    frames, as finger_basis negates y and z) on the left."""
    D = np.diag([-1., 1, 1])
    Rh = rig.R('hand_r')
    out = {}
    for f in FINGERS:
        for seg in (1, 2, 3):
            n = '%s_%02d_r' % (f, seg)
            if f == 'thumb':
                if seg == 1:
                    Rt = Rh.T @ rig.R(n)                              # the thumb's rest frame in the hand's
                    d, di = Rh.T @ rig.dir(n), Rh.T @ rig.dir('index_01_r')
                    G = axis_angle(np.cross(d, di), hand['thumb'][0])  # toward the index, in the hand frame
                    B = Rt.T @ G @ Rt
                else:
                    B = euler_xyz((hand['thumb'][seg - 1], 0, 0))
            else:
                B = euler_xyz((hand['curl'][f][seg - 1], 0, 0))
                if seg == 1:                                          # the spread taken out about the bone's Z
                    d = Rh.T @ rig.dir(n)
                    B = axis_angle([0, 0, 1], hand['close'] * math.degrees(math.atan2(d[0], d[1]))) @ B
            out[n] = m4(B)
            out[n.replace('_r', '_l')] = m4(D @ B @ D)
    return out


def axis_angle(a, deg):
    a = unit(a)
    K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    r = math.radians(deg)
    return np.eye(3) + math.sin(r) * K + (1 - math.cos(r)) * K @ K


def z_twist(R):
    """The angle (deg) of R's twist about the world vertical."""
    q = q_from_m(R)
    return math.degrees(2 * math.atan2(q[3], q[0]))


def arm_pitch(d):
    """Upper arm fore-aft angle (deg, forward +; the rig faces -Y)."""
    return math.degrees(math.atan2(-d[1], -d[2]))


def arm_style(dirs, side, st):
    """Opened elbow, scaled swing, arm turned out: on (direction, hinge) of the upper arm and forearm."""
    (du, hu), (df, hf) = dirs
    ax = np.cross(du, df)
    if np.linalg.norm(ax) > 1e-6:
        flex = math.degrees(math.acos(np.clip(np.dot(unit(du), unit(df)), -1, 1)))
        R = axis_angle(ax, -(flex - max(flex - st['elbow_open'], ELBOW_MIN)))   # +angle turns du toward df
        df, hf = R @ df, R @ hf
    swing = (st['arm_swing'] - 1) * (arm_pitch(du) - st['swing_mean'][side])
    out = -st['arm_out'] if side == 'l' else st['arm_out']                       # the left side is +X
    R = axis_angle(Y_AXIS, out) @ axis_angle(X, -swing)
    return (R @ du, R @ hu), (R @ df, R @ hf)


Y_AXIS = np.array([0., 1, 0])


def retarget_frame(rig, src, t, Psi, base, s, fingers, shift=None, lift=0.0, fix=None, level=None, style=None):
    """One source frame on the AFL rig. fix (the loop seam): world turns put before the free bones' rotations, and
    the hips, ankle targets and knee hinges given outright. style: STYLE[kind] plus swing_mean. Returns the pose
    frames and what the legs were given."""
    def G(b):
        return Psi @ src.G[b][t]

    def P(arr):
        return Psi @ (arr[t] - base)

    rot = {}
    for afl, cm in TRUNK:
        rot[afl] = G(cm) @ rig.R(afl)
    trunk_style = {}
    if style is not None:
        for afl, cm, k in (('hips', 'root', style['hips_yaw']), ('spine', 'upperback', style['chest_yaw']),
                           ('chest', 'thorax', style['chest_yaw'])):
            R = axis_angle([0, 0, 1], (k - 1) * z_twist(G(cm)))
            # +X pitch takes up toward -Y, forward
            R = axis_angle(X, style['hips_pitch'] if afl == 'hips' else style['lean']) @ R
            trunk_style[afl] = R
            rot[afl] = R @ rot[afl]
    rot['neck'] = neck_turn(G) @ rig.R('neck')
    if level is not None:                     # the head and neck upright on average; the game turns the head to the look
        for b in ('neck', 'head'):
            rot[b] = level[b] @ rot[b]
    for side, cs in (('l', 'l'), ('r', 'r')):
        rot['clavicle_' + side] = trunk_style.get('chest', np.eye(3)) @ G(cs + 'clavicle') @ rig.R('clavicle_' + side)
        rot['foot_' + side] = G(cs + 'foot') @ rig.R('foot_' + side)
        rot['toes_' + side] = G(cs + 'toes') @ rig.R('toes_' + side)
        dirs = [(G(cs + cm) @ src.rest_dir[cs + cm], G(cs + cm) @ hinge_arm(src.rest_dir[cs + cm])) for cm in ('humerus', 'radius')]
        if style is not None:
            dirs = arm_style(dirs, side, style)
        for afl, (ds, hs) in zip(('upper_arm_', 'forearm_'), dirs):
            da = rig.dir(afl + side)
            rot[afl + side] = frame(ds, hs) @ frame(da, hinge_arm(da)).T @ rig.R(afl + side)
    if fix is not None:
        for b, c in fix['rot'].items():
            rot[b] = c @ rot[b]
    # the hips: the AFL hip-joint midpoint on the scaled source one
    mid_src = s * (P(src.start['lfemur']) + P(src.start['rfemur'])) / 2
    mid_afl = (rig.head('thigh_l') + rig.head('thigh_r')) / 2
    hips = mid_src - rot['hips'] @ rig.R('hips').T @ (mid_afl - rig.head('hips')) + np.array([0, 0, lift])
    if fix is not None:
        hips = fix['hips']
    W0 = rig.world(rot, hips, fingers)
    info = {'rot': {b: r.copy() for b, r in rot.items()}, 'hips': hips.copy(), 'T': {}, 'n': {}, 'miss': {}}
    for side, cs in (('l', 'l'), ('r', 'r')):
        H = W0['thigh_' + side][:3, 3]
        ank = s * (P(src.end[cs + 'tibia']) - P(src.start[cs + 'femur']))
        if style is not None:
            if 'ankle_mean' in style:      # stride: the fore-aft path about its mean, the height over its low
                m_y, lo_z = style['ankle_mean'][side]
                ank = np.array([ank[0], m_y + style['stride'] * (ank[1] - m_y), lo_z + style['lift'] * (ank[2] - lo_z)])
            ank = ank + np.array([style['step_width'] / 2 if side == 'l' else -style['step_width'] / 2, 0, 0])
        T = H + ank
        if shift is not None:
            T = T + shift[side]
        n = G(cs + 'femur') @ hinge_leg(src.rest_dir[cs + 'femur'])
        if fix is not None:
            T, n = fix['T'][side] + (shift[side] if shift is not None else 0), fix['n'][side]
        info['T'][side], info['n'][side] = T.copy(), n.copy()
        knee, T2, n = ik2(H, T, n, rig.length('thigh_' + side), rig.length('shin_' + side))
        for b, d in (('thigh_', knee - H), ('shin_', T2 - knee)):
            da = rig.dir(b + side)
            rot[b + side] = frame(d, n) @ frame(da, hinge_leg(da)).T @ rig.R(b + side)
        info['miss'][side] = float(np.linalg.norm(T - T2))
    return rig.world(rot, hips, fingers), info


def neck_turn(G):
    return m_from_q(q_slerp(q_from_m(G('lowerneck')), q_from_m(G('upperneck')), 0.5))


def mean_turn(Rs):
    qs = [q_from_m(R) for R in Rs]
    acc = np.zeros(4)
    for q in qs:
        acc += q if np.dot(q, qs[0]) >= 0 else -q
    return m_from_q(unit(acc))


def head_level(src, Psi, ts):
    """Per bone the world turn that takes the cycle's mean neck / head turn off (CMU stands often look down)."""
    return {'neck': mean_turn([neck_turn(lambda b: Psi @ src.G[b][t]) for t in ts]).T,
            'head': mean_turn([Psi @ src.G['head'][t] for t in ts]).T}


def seam_fixes(infos):
    """The loop seam taken out over the cycle: per frame (phase i / N) the free bones' world turns slerped toward
    frame 0's, the hips, ankle targets and knee hinges moved linearly. Frame N is then frame 0."""
    N = len(infos) - 1
    first, last = infos[0], infos[-1]
    corr = {b: q_from_m(first['rot'][b] @ last['rot'][b].T) for b in first['rot']}
    out = []
    for i, f in enumerate(infos):
        phi = i / N
        out.append({'rot': {b: m_from_q(q_slerp(np.array([1., 0, 0, 0]), c, phi)) for b, c in corr.items()},
                    'hips': f['hips'] - phi * (last['hips'] - first['hips']),
                    'T': {sd: f['T'][sd] - phi * (last['T'][sd] - first['T'][sd]) for sd in 'lr'},
                    'n': {sd: unit(f['n'][sd] - phi * (last['n'][sd] - first['n'][sd])) for sd in 'lr'}})
    seam = {b: q_angle(c) for b, c in corr.items()}
    return out, seam, float(np.linalg.norm(last['hips'] - first['hips']))


def soles(rig, W):
    """World sole points per side: heel, ball, toe."""
    out = {}
    for side in 'lr':
        pts = []
        for b, p in SOLE:
            p = np.array(p)
            if side == 'l':
                p = p * np.array([-1, 1, 1])
            n = b + '_' + side
            local = rig.inv[n] @ np.r_[p, 1]
            pts.append((W[n] @ local)[:3])
        out[side] = np.array(pts)
    return out


def lowest(rig, Ws, side):
    """Per frame: the ball of the foot in x, y (it barely moves while the foot rolls) and the lowest sole point's z."""
    out = []
    for W in Ws:
        p = soles(rig, W)[side]
        out.append(np.array([p[1, 0], p[1, 1], p[:, 2].min()]))
    return np.array(out)


def stance_runs(z, whole, slow=None):
    """The longest cyclic run of frames within 3.5 cm of the lowest (whole: every frame) and, given slow, among those
    with the foot slow over the ground (a walk's swing foot clears the ground by only 1-2 cm)."""
    n = len(z)
    on = np.ones(n, bool) if whole else z < z.min() + 0.035
    if slow is not None and not whole:
        on &= slow
    if on.all():
        return list(range(n))
    start = int(np.argmin(on))
    runs, cur = [], []
    for k in range(1, n + 1):
        i = (start + k) % n
        if on[i]:
            cur.append(i)
        elif cur:
            runs.append(cur)
            cur = []
    if cur:
        runs.append(cur)
    return max(runs, key=len)


def stance_height(rig, Ws, whole):
    hs = []
    for side in 'lr':
        z = lowest(rig, Ws[:-1], side)[:, 2]
        hs.append(float(np.median(z[stance_runs(z, whole)])))
    return sum(hs) / 2


def foot_lock(rig, Ws, ref, dt, whole=False, blend=8, slow_test=False, floor_clamp=False):
    """Per side the ankle shift that holds the stance foot: its lowest sole point on z = 0, its ball still in x, moving +Y at
    ref (the ground under an in-place body). Stance is the longest cyclic run within 3.5 cm of the foot's lowest point
    (whole: every frame; slow_test: and slow over the ground, for gaits that barely lift the swing foot; the run keeps
    its height-only stance, PASS 2026-10-25); the shift fades out over blend frames each side of it."""
    n = len(Ws) - 1                                   # frame n is frame 0 again
    out = [{'l': np.zeros(3), 'r': np.zeros(3)} for _ in Ws]
    info = {}
    for side in 'lr':
        low = lowest(rig, Ws[:n], side)
        # the ball's speed over the ground (the ground moves +Y at ref under the in-place body)
        v = (np.roll(low[:, :2], -1, 0) - np.roll(low[:, :2], 1, 0)) / (2 * dt) - np.array([0, ref])
        slow = np.hypot(v[:, 0], v[:, 1]) < max(0.25, 0.35 * ref)
        run = stance_runs(low[:, 2], whole, slow if slow_test else None)
        tau = np.arange(len(run)) * dt
        y0 = float(np.mean(low[run, 1] - ref * tau))
        x0 = float(np.mean(low[run, 0]))
        shift = {}
        for k, i in enumerate(run):
            shift[i] = np.array([x0 - low[i, 0], y0 + ref * tau[k] - low[i, 1], -low[i, 2]])
        if len(run) < n:
            first, last = shift[run[0]], shift[run[-1]]
            for j in range(1, blend + 1):
                w = 0.5 + 0.5 * math.cos(math.pi * j / (blend + 1))
                for i, o in (((run[-1] + j) % n, last), ((run[0] - j) % n, first)):
                    if i not in shift:
                        o = o * w
                        o[2] = max(o[2], -low[i, 2])
                        shift[i] = o
        if floor_clamp:                       # no sole under the ground anywhere in the cycle
            for i in range(n):
                if i not in shift and low[i, 2] < 0:
                    shift[i] = np.array([0, 0, -low[i, 2]])
        for i, o in shift.items():
            out[i][side] = o
        info[side] = (len(run), float(np.abs(np.array(list(shift.values()))).max()))
    out[n] = out[0]
    return out, info


def bases_of(rig, W):
    out = {}
    for n in rig.order:
        B = rig.basis(W, n)
        out[n] = (q_from_m(B[:3, :3]), B[:3, 3].copy())
    return out


def build(rig, src, kind, name, opts=None):
    opts = opts or {}
    s = 0.5 * (rig.length('thigh_l') + rig.length('shin_l') + rig.length('thigh_r') + rig.length('shin_r')) / src.leg
    n = src.n
    move = kind == 'move'
    if move:
        P0 = period(src)
        c, a, N = best_cycle(src, range(P0 - 4, P0 + 5), int(n * .15), int(n * .85))
        d = src.root[a + N] - src.root[a]
        travel = math.hypot(d[0], d[1])
        Psi = yaw(-math.pi / 2 - math.atan2(d[1], d[0]))
        speed_src = travel / (N / src.fps)

        def base(t):
            b = src.root[a] + d * (t - a) / N
            return np.array([b[0], b[1], src.floor])
    else:
        if kind == 'sit':                     # a cycle over most of the seated hold
            w0, w1 = (int(x * src.fps) for x in opts['window'])
            c, a, N = best_cycle(src, range(int(0.75 * (w1 - w0)), w1 - w0 - 2, 2), w0, w1)
        else:
            c, a, N = best_cycle(src, range(int(4 * src.fps), int(8 * src.fps), 6), int(n * .05), int(n * .95))
        lat = src.start['lfemur'][a:a + N] - src.start['rfemur'][a:a + N]
        f = np.cross(lat.mean(axis=0), [0, 0, 1])
        Psi = yaw(-math.pi / 2 - math.atan2(f[1], f[0]))
        m = src.root[a:a + N].mean(axis=0)
        if kind == 'sit':                     # the hip joints (scaled) over the seat point, opts['ahead'] in front of it
            hm = ((src.start['lfemur'] + src.start['rfemur']) / 2)[a:a + N].mean(axis=0)
            m = hm - Psi.T @ np.array([0, -opts.get('ahead', 0.0), 0]) / s
        speed_src = 0.0

        def base(t):
            return np.array([m[0], m[1], src.floor])
    st = None
    if opts.get('style'):
        st = dict(STYLE[opts['style']])
        st['swing_mean'] = {sd: float(np.mean([arm_pitch(Psi @ src.G[sd + 'humerus'][t] @ src.rest_dir[sd + 'humerus'])
                                               for t in range(a, a + N)])) for sd in 'lr'}
        if move and (st['stride'] != 1 or st['lift'] != 1):
            st['ankle_mean'] = {}
            for sd in 'lr':
                rel_ank = np.array([s * Psi @ (src.end[sd + 'tibia'][t] - src.start[sd + 'femur'][t]) for t in range(a, a + N)])
                st['ankle_mean'][sd] = (float(rel_ank[:, 1].mean()), float(rel_ank[:, 2].min()))

    def rf(*args, **kw):
        return retarget_frame(*args, style=st, **kw)

    fingers = finger_basis(rig, opts.get('hand') or (st['fingers'] if st else RELAXED))
    duration = N / src.fps
    ref = s * speed_src * (st['stride'] if st else 1.0)
    # the source floor is the joint centres of its toes, not a sole: first lift the whole body so the stance feet
    # stand on z = 0 (the swing foot keeps its clearance; seated: the hip joints over the cushion), then hold each
    # stance foot by a small ankle shift
    ts = range(a, a + N + 1)
    lv = head_level(src, Psi, ts)
    first = [rf(rig, src, t, Psi, base(t), s, fingers, level=lv)[0] for t in ts]
    if kind == 'sit':
        lift = opts['seat'] + HIP_OVER_SEAT - float(np.median([(W['thigh_l'][2, 3] + W['thigh_r'][2, 3]) / 2 for W in first]))
    else:                                     # (a longer stride needs the hips a little lower to reach the ground)
        lift = -stance_height(rig, first, not move) - (st['hips_drop'] if st and move else 0.0)
    first = [rf(rig, src, t, Psi, base(t), s, fingers, lift=lift, level=lv)[0] for t in ts]
    shifts, lock = foot_lock(rig, first, ref, 1 / src.fps, whole=not move, slow_test=opts.get("slow_stance", False))
    infos = [rf(rig, src, t, Psi, base(t), s, fingers, shifts[t - a], lift, level=lv)[1] for t in ts]
    if kind == 'idle':
        L1, L2 = rig.length('thigh_l'), rig.length('shin_l')
        want = math.sqrt(L1 * L1 + L2 * L2 + 2 * L1 * L2 * math.cos(math.radians(KNEE_STAND)))
        for k in range(4):
            if k < 3:
                reach = [float(np.linalg.norm(f['T'][sd] - W['thigh_' + sd][:3, 3])) for f, W in
                         zip(infos, [rf(rig, src, t, Psi, base(t), s, fingers, shifts[t - a], lift, level=lv)[0] for t in ts])
                         for sd in 'lr']
                lift += want - float(np.median(reach))
            elif st and st['hips_drop']:      # crouched: brought down from the stand, the feet where they stood
                lift -= st['hips_drop']
            else:
                break
            first = [rf(rig, src, t, Psi, base(t), s, fingers, lift=lift, level=lv)[0] for t in ts]
            shifts, lock = foot_lock(rig, first, ref, 1 / src.fps, whole=True)
            infos = [rf(rig, src, t, Psi, base(t), s, fingers, shifts[t - a], lift, level=lv)[1] for t in ts]
    fixes, seam, seam_loc = seam_fixes(infos)
    late = [None] * len(ts)
    if opts.get('slow_stance'):               # walks: the seam ramp moves the held feet, so they are held again after it
        fixed = [rf(rig, src, t, Psi, base(t), s, fingers, fix=fixes[t - a], level=lv)[0] for t in ts]
        late, lock = foot_lock(rig, fixed, ref, 1 / src.fps, slow_test=True, floor_clamp=True)
    frames, ik_miss = [], []
    for t in ts:
        W, info = rf(rig, src, t, Psi, base(t), s, fingers, late[t - a], fix=fixes[t - a], level=lv)
        frames.append(bases_of(rig, W))
        ik_miss.append(max(info['miss'].values()))
    for b in rig.order:                                   # quaternion signs kept continuous for the sampling
        for i in range(1, len(frames)):
            if np.dot(frames[i][b][0], frames[i - 1][b][0]) < 0:
                frames[i][b] = (-frames[i][b][0], frames[i][b][1])
    # sampled at 60 fps over one cycle (the last sample is the first again, so it is left out)
    K = max(2, round(N * FPS_OUT / src.fps))
    samples = []
    for k in range(K):
        x = k * N / K
        i = int(math.floor(x))
        f = x - i
        j = min(i + 1, N)
        samples.append({b: (q_slerp(frames[i][b][0], frames[j][b][0], f), frames[i][b][1] * (1 - f) + frames[j][b][1] * f)
                        for b in rig.order})
    if opts.get('still'):
        # seated (user 2026-10-25: "一直抖来抖去"): the hold's arms still settle 20 deg in 1.27 s, which a short loop
        # repeats as a twitch. The hold's mean pose instead, breathing on the spine and chest (deg, over opts['still'] s)
        mean = {}
        for b in rig.order:
            acc = np.zeros(4)
            for smp in samples:
                q = smp[b][0]
                acc += q if np.dot(q, samples[0][b][0]) >= 0 else -q
            mean[b] = (unit(acc), np.mean([smp[b][1] for smp in samples], axis=0))
        duration = float(opts['still'])
        K = round(duration * FPS_OUT)
        samples = []
        for k in range(K):
            s_k = math.sin(2 * math.pi * k / K)
            smp = dict(mean)
            for b, amp in BREATH.items():
                smp[b] = (q_mul(mean[b][0], q_from_m(euler_xyz((amp * s_k, 0, 0)))), mean[b][1])
            samples.append(smp)
    Ws = [rig.world(basis={b: m4(m_from_q(q), l) for b, (q, l) in smp.items()}) for smp in samples]
    roll = 0
    if move:                                              # the first sample: the left foot's touchdown (down and slow)
        sl = [soles(rig, W)['l'] for W in Ws]
        on = [sl[k][:, 2].min() < CONTACT_M and math.hypot(*((sl[(k + 1) % K][1, :2] - sl[k][1, :2]) * K / duration
                                                              - np.array([0, ref]))) < max(0.3, 0.4 * ref) for k in range(K)]
        starts = [k for k in range(K) if on[k] and not on[k - 1]]
        if starts:
            roll = starts[0]
            samples, Ws = samples[roll:] + samples[:roll], Ws[roll:] + Ws[:roll]
    report = checks(rig, Ws, duration, ref)
    report['phase_roll_samples'] = roll
    report['stance_frames_120'] = {k: v[0] for k, v in lock.items()}
    report['lift_cm'] = round(lift * 100, 1)
    if st:
        report['style'] = dict({k: v for k, v in st.items() if k not in ('swing_mean', 'ankle_mean')}, name=opts['style'])
    if kind == 'sit':
        report.update(seat_m=opts['seat'], hips_ahead_m=opts.get('ahead', 0.0))
    report['head_level_deg'] = {b: round(q_angle(q_from_m(m)), 1) for b, m in lv.items()}
    report['lock_shift_max_cm'] = {k: round(v[1] * 100, 1) for k, v in lock.items()}
    report.update(clip=name, source='CMU ' + src_name(name), kind=kind, start_frame=a, frames=N, duration_s=round(duration, 4),
                  ref_speed=round(ref, 4), source_speed=round(speed_src, 4), scale=round(s, 4), samples=K,
                  cadence_spm=round(120 / duration, 1) if move else None,
                  seam_max_deg=round(max(seam.values()), 2), seam_worst=max(seam, key=seam.get), sole_min_cm=round(min(min(soles(rig, W)[sd][:, 2].min() for sd in 'lr') for W in Ws) * 100, 2), seam_hips_cm=round(seam_loc * 100, 2),
                  ik_reach_miss_cm=round(max(ik_miss) * 100, 2))
    return samples, Ws, report


def src_name(name):
    return CLIPS[name][0]


def checks(rig, Ws, duration, ref):
    K = len(Ws)
    dt = duration / K
    out = {}
    pts = [soles(rig, W) for W in Ws]
    for side in 'lr':
        slips, contact, drag, clear = [], 0, 0, []
        for k in range(K):
            p, q = pts[k][side], pts[(k + 1) % K][side]
            i = int(np.argmin(p[:, 2]))
            v = (q[1] - p[1]) / dt + np.array([0, -ref, 0])      # the ball, in the world: the body moves -Y at ref
            sp = math.hypot(v[0], v[1])
            if p[i, 2] < CONTACT_M and q[:, 2].min() < CONTACT_M:
                contact += 1
                slips.append(sp)
            elif sp > 1.0:
                clear.append(p[i, 2])                             # a swinging foot: its height over the ground
            if sp > 1.0 and p[i, 2] < CONTACT_M:
                drag += 1
        out['contact_' + side] = round(contact / K, 3)
        out['drag_samples_' + side] = drag
        out['swing_min_clear_cm_' + side] = round(min(clear) * 100, 1) if clear else None
        out['slip_median_cm_s_' + side] = round(float(np.median(slips)) * 100, 1) if slips else None
        out['slip_max_cm_s_' + side] = round(float(np.max(slips)) * 100, 1) if slips else None
    knee, elbow = [], []
    for W in Ws:
        for side in 'lr':
            a = unit(W['thigh_' + side][:3, 1])
            b = unit(W['shin_' + side][:3, 1])
            knee.append(math.degrees(math.acos(np.clip(np.dot(a, b), -1, 1))))
            a = unit(W['upper_arm_' + side][:3, 1])
            b = unit(W['forearm_' + side][:3, 1])
            elbow.append(math.degrees(math.acos(np.clip(np.dot(a, b), -1, 1))))
    hz = [W['hips'][:3, 3][2] for W in Ws]
    out.update(knee_flex_deg=[round(min(knee), 1), round(max(knee), 1)], elbow_flex_deg=[round(min(elbow), 1), round(max(elbow), 1)],
               hips_z_m=[round(min(hz), 3), round(max(hz), 3)])
    return out


def write(rig, name, samples, Ws, report, out_dir):
    bones = [b for b in rig.deform]
    blend = {'format': 'afl_tp_clip_blender', 'version': 1, 'name': name, 'fps': FPS_OUT, 'report': report,
             'frames': [{b: [round(float(v), 6) for v in list(smp[b][0]) + (list(smp[b][1]) if b == 'hips' else [])]
                         for b in bones} for smp in samples],
             # numpy pose frames of a few bones (head and +Y tip, Blender m) for the Blender read-back
             'check': {str(k): {b: [round(float(v), 6) for v in list(Ws[k][b][:3, 3]) + list(Ws[k][b][:3, 3] + Ws[k][b][:3, 1] * 0.1)]
                                for b in ('head', 'hand_r', 'hand_l', 'foot_l', 'toes_r', 'index_03_r', 'shin_r')}
                       for k in (0, len(Ws) // 3, 2 * len(Ws) // 3)}}
    with open(os.path.join(out_dir, name + '.blend.json'), 'w') as fh:
        json.dump(blend, fh, separators=(',', ':'))
    tracks = {}
    for b in bones:
        R = rig.R(b)
        qs = []
        for smp in samples:
            Q = C3 @ R @ m_from_q(smp[b][0]) @ R.T @ C3.T
            q = q_from_m(Q)
            if qs and np.dot(q, qs[-1]) < 0:
                q = -q
            qs.append(q)
        same = all(q_angle(q_mul(q, q_inv(qs[0]))) < 0.01 for q in qs)
        keep = qs[:1] if same else qs
        if same and q_angle(qs[0]) < 0.01:
            continue                                              # rest: not stored
        tracks[b] = [round(float(v), 6) for q in keep for v in (q[1], q[2], q[3], q[0])]
    offset = [round(float(v), 6) for smp in samples for v in C3 @ rig.R('hips') @ smp['hips'][1]]
    clip = {'format': 'afl_skin_clip', 'version': 1, 'name': name, 'space': 'model_blocks', 'loop': True,
            'duration': report['duration_s'], 'ref_speed': report['ref_speed'], 'samples': len(samples),
            'rotation': 'xyzw_about_rest_pivot', 'bones': bones,
            'parents': [bones.index(rig.parent[b]) if rig.parent[b] in bones else -1 for b in bones],
            'pivots': [[round(float(v), 6) for v in C3 @ rig.head(b)] for b in bones],
            'tracks': tracks, 'hips_offset': offset,
            'source': report['source'] + ' (Carnegie Mellon University Motion Capture Database, mocap.cs.cmu.edu), retargeted by anim_tp_v1.py'}
    with open(os.path.join(out_dir, name + '.clip.json'), 'w') as fh:
        json.dump(clip, fh, separators=(',', ':'))


def main():
    rig_path, cmu_dir, out_dir = sys.argv[1:4]
    names = sys.argv[4:] or list(CLIPS)
    rig = Rig(rig_path)
    for name in names:
        if ':' in name:                                   # a trial: name:cmu_clip:kind[:style]
            name, clip, kind, *style = name.split(':')
            CLIPS[name] = (clip, kind, {'style': style[0]} if style else {})
        clip, kind, opts = CLIPS[name]
        src = Source(cmu_dir, clip, opts.get('cutoff', CUTOFF[kind]))
        samples, Ws, report = build(rig, src, kind, name, opts)
        write(rig, name, samples, Ws, report, out_dir)
        print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    main()
