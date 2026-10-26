"""H1-P2 (2026-10-24, docs/dev/mesh_body_p9_fp_clips_h1p2.md): P9-01 first-person action clips on the H1-P1 body.

Run inside survivor_base_h1p1.blend after fp_pose_h1p1.py, in the same namespace (it registers into ACTIONS /
AFTER_EXPORT):
    exec(open(r".../source/fp_reload_h1p1.py", encoding="utf-8").read())
    rep = check('reload_tactical_experienced')      # per-frame IK, wrists, skin into gun / magazines / other hand
    action_shots('reload_tactical_experienced')     # first-person frames into renders/fp_h1p1/reload_*
    export_fp()                                     # bakes CLIPS + ACTIONS, writes the GeckoLib files + props sidecar

The gun keeps the formal P9-01 V3 clip's motion (user PASS 2026-10-22): its handling transform relative to the gun's
root, sampled by extract_p9_formal_fp.mjs into p9_formal_fp_channels.json, is put on the frozen hip pose
(G = G_hip . D_handling), so the beats, the sounds, the magazine-well path, the old magazine's fall and the head-down
camera are the formal ones. The right hand stays on the grip through the arm IK (the game carries the gun on hand_r,
so its IK has to be exact every frame); its thumb presses the left magazine release around the release sound. The
support hand is new, the real-world speed / tactical reload: it peels off the grip, goes to the magazine pouch on the
left hip (the magazine upside down, front forward, flat side on the body, as belt pouches carry it), takes the new
magazine with the base plate in the palm and the index finger along the front (mag_grip_h1p2.json 'carry'), turns it
up into the formal magazine path, rolls under the base plate (palm flat, 'push') while the magazine goes in, pushes it
home and turns from the base plate straight back onto the grip. While the hand carries the magazine freely (pouch to
well) the magazine rides on the posed hand; from the well mouth on it is on the formal path and the hand follows it.
R3 (user 2026-10-24): the game's hand pass now turns with the formal camera bone (MeshBodyFpTest), and the gun, its
magazines and the hands are lifted LIFT while the head looks down, so the well and the new magazine are on screen.

Frames: Blockbench px (+x right, +y up, -z muzzle) -> Blender gun px (+x right, +y muzzle, +z up) is (x, -z, y), the
same origin and scale (checked on the magazine and slide bounds). The props sidecar gives each gun part a local
transform at its pivot in the mesh's own model blocks (Blockbench axes, px / 16), column-major, which the game's
BonePoser multiplies in (MeshBodyFpTest); mag_out carries the visible new magazine (reload_magazine)."""
import json
import math
import sys
import numpy as np
from mathutils import Euler, Matrix, Vector

CHANNELS = HERE + '/p9_formal_fp_channels.json'
MAG_GRIP = HERE + '/mag_grip_h1p2.json'
PROPS = ASSETS + '/skins/dev/survivor_base_h1p1.props.json'
BB = Matrix(((1, 0, 0, 0), (0, 0, -1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))     # Blockbench px -> Blender gun px
BBI = BB.inverted()
MAG_PIVOT = Vector((0, 3.5992, 1.647))      # mag_out / empty_old_mag pivot, Blockbench px (the gun's geo)
# the magazine's own frame (gun px): base bottom centre, the axis tilted 22 deg forward with the grip; body and base
# plate as boxes in that frame (mm), since the game mesh is split per face and has no inside
MAG_TILT = math.radians(22.0)
MAG_M0 = (Matrix.Translation((0.0, -3.466, -0.505)) @
          Matrix(((1, 0, 0), (0, math.cos(MAG_TILT), math.sin(MAG_TILT)),
                  (0, -math.sin(MAG_TILT), math.cos(MAG_TILT)))).to_4x4())
MAG_BOXES = (((-10.8, -19.6, 4.3), (10.8, 18.7, 88.9)), ((-13.8, -24.3, -0.6), (13.8, 23.8, 4.4)))
# the pouch on the belt, front left (about half past ten): the waist as an ellipse in the hip stance (m: half width,
# half depth, centre y, height of the base plate's bottom), the pouch's angle from the left side toward the front,
# the magazine's flat side 4 mm off the body and its top (the lower end) canted back (deg). Picked from a search over
# angle, cant and the arm below (scratch R2): the place where the carry grip is reachable with the wrist nearest
# neutral (about 60 deg extension, 52 deg pronation, 26 deg ulnar; elbow 100 deg)
POUCH = dict(a=0.165, b=0.105, cy=0.045, z=1.02, theta=55.0, gap=0.0148, cant=-20.0)
# the support arm while the hand is down at the pouch (blended in from the grip and back out by the well): elbow
# swivel, clavicle elevate and protract (deg, the left values of the pose spec)
ARM_POUCH = dict(swivel=0.0, elevate=10.0, protract=8.0)
# the support hand clear of the gun on the way off: offset from its grip target in the gun's axes (m: gun-left,
# back, down); on the way back from the base plate it only passes REGRIP_VIA off the straight midpoint
AWAY = (-0.075, -0.02, -0.06)
REGRIP_VIA = (-0.025, -0.005, -0.02)
# R3: the gun (with its magazines and both hands) lifted while the formal camera looks down, armature m, scaled by
# the camera bone's pitch over its largest (0 -> 1 -> 0 on the formal beats); with the hand pass turning by the camera
# bone this puts the well about 90 px above the bottom of a 540 px screen at the seat (scratch measure_r3.py: 1 cm
# up = 23 px; 5 cm would push the slide out of the top)
LIFT = (0.0, 0.0, 0.035)
FINGERS = ('thumb', 'index', 'middle', 'ring', 'pinky')


def _fingers(vals, thumb):
    """vals: (finger, (curl _01, _02, _03), spread on _01); spread is the grip files' z, which closes the rest
    pose's fan (about 7 deg between neighbours)."""
    out = {}
    for f, vs, z in vals:
        for j, v in zip((1, 2, 3), vs):
            out[f + '_0%d' % j] = (v, 0.0, z if j == 1 else 0.0)
    out.update(thumb)
    return out


# support-hand finger sets (grip-file convention; set_fingers mirrors them for the left): off the gun, softly bent;
# reaching for the magazine, open; pushing it home, flat. R3 (user: the push hand looked fanned out): the fingers
# closed to the grip's PIP spacing (about 22 mm; the pinky as far as it goes, its tip about 33 mm from the ring's;
# scratch measure_r3b / r3c.py), relaxed a little apart; the push curled 1.6x round the front of the grip (2.2x
# put them 1.5 mm into the magazine while it rolls under; scratch push_r3_bg.py)
RELAXED = _fingers((('index', (18, 22, 12), 4.5), ('middle', (22, 26, 14), 0.0), ('ring', (26, 28, 15), -4.5),
                    ('pinky', (30, 30, 16), -10.0)),
                   dict(thumb_01=(-9.5, -34.5, 8.0), thumb_02=(10.0, 0, 0), thumb_03=(8.0, 0, 0)))
REACH = _fingers((('index', (6, 6, 3), 7.0), ('middle', (8, 8, 4), 0.0), ('ring', (10, 10, 5), -6.5),
                  ('pinky', (12, 12, 6), -15.0)),
                 dict(thumb_01=(-9.5, -34.5, 8.0), thumb_02=(4.0, 0, 0), thumb_03=(2.0, 0, 0)))
PUSH = _fingers((('index', (24, 18, 10), 7.0), ('middle', (27, 21, 11), 0.0), ('ring', (30, 24, 13), -6.0),
                 ('pinky', (34, 27, 16), -18.0)),
                dict(thumb_01=(-9.5, -34.5, 8.0), thumb_02=(6.0, 0, 0), thumb_03=(4.0, 0, 0)))


# ---- small helpers

def rigid(M):
    """M without its scale: a gun px map (G, a magazine placement) becomes metres in the gun's axes -> armature."""
    loc, rot, _ = M.decompose()
    return Matrix.Translation(loc) @ rot.to_matrix().to_4x4()


def scaled(R):
    """A rigid placement back to a gun px map."""
    return R @ Matrix.Scale(PX, 4)


def smooth(u):
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def ramp(t, a, b):
    return smooth((t - a) / (b - a))


def lerp_m(a, b, u, via=None):
    """Rigid blend a -> b: translation straight or along a quadratic arc through via (its middle point), rotation
    slerped."""
    la, ra, _ = a.decompose()
    lb, rb, _ = b.decompose()
    if via is None:
        loc = la.lerp(lb, u)
    else:
        c = 2 * via - (la + lb) / 2                     # control point so that the arc passes through via at u = 0.5
        loc = la * (1 - u) ** 2 + c * 2 * u * (1 - u) + lb * u * u
    return Matrix.Translation(loc) @ ra.slerp(rb, u).to_matrix().to_4x4()


def arc(a, b, via, u):
    """Point on the quadratic arc a -> b that passes through via at u = 0.5."""
    c = 2 * via - (a + b) / 2
    return a * (1 - u) ** 2 + c * 2 * u * (1 - u) + b * u * u


def spline(keys, u):
    """Point (and its wrist angles) at u on the smooth path through keys [(point, angles)]: cubic Hermite with
    Catmull-Rom tangents, keys spaced by their chord lengths so the speed stays even between them."""
    P = [Vector(k[0]) for k in keys]
    A = [Vector(k[1]) for k in keys]
    s = [0.0]
    for a, b in zip(P, P[1:]):
        s.append(s[-1] + (b - a).length)
    s = [x / s[-1] for x in s]
    n = len(P) - 1

    def tan(X, i):
        lo, hi = max(0, i - 1), min(n, i + 1)
        return (X[hi] - X[lo]) / (s[hi] - s[lo])
    u = min(1.0, max(0.0, u))
    i = next((j for j in range(n) if u <= s[j + 1]), n - 1)
    h = s[i + 1] - s[i]
    x = (u - s[i]) / h
    h00, h10, h01, h11 = 2 * x ** 3 - 3 * x ** 2 + 1, x ** 3 - 2 * x ** 2 + x, -2 * x ** 3 + 3 * x ** 2, x ** 3 - x ** 2
    return tuple(h00 * X[i] + h10 * h * tan(X, i) + h01 * X[i + 1] + h11 * h * tan(X, i + 1) for X in (P, A))


def wrist_raw(s):
    """wrist(s) unrounded, radians (the Euler that rebuilds the hand from its forearm)."""
    pb = rig().pose.bones['hand_' + s]
    par = pb.parent
    rest = par.bone.matrix_local.inverted() @ pb.bone.matrix_local
    return ((par.matrix @ rest).inverted() @ pb.matrix).to_euler('YXZ')


def peel_fingers(u):
    """How far the support fingers have opened off the grip at peel progress u: loosened first, open by halfway
    (R3, scratch peel_r3_bg.py: opening with the hand put a fingertip 1.4 mm into the grip front, after it 2.5 mm)."""
    return smooth(u / 0.5)


def blend_e(a, b, u):
    return {k: tuple(x + (y - x) * u for x, y in zip(a.get(k, (0, 0, 0)), b.get(k, (0, 0, 0)))) for k in set(a) | set(b)}


_formal = {}


def formal(clip):
    """The formal clip's frames (60 fps): parts as Blender gun px 4x4 relative to the gun root, shown flags, camera."""
    if clip not in _formal:
        c = json.load(open(CHANNELS, encoding='utf-8'))['clips'][clip]
        frames = []
        for fr in c['frames']:
            parts = {n: BB @ Matrix([m[i * 4:i * 4 + 4] for i in range(4)]) @ BBI for n, m in fr['parts'].items()}
            frames.append(dict(t=fr['t'], parts=parts, shown=fr['shown'], camera=fr['camera']))
        _formal[clip] = dict(length=c['length'], frames=frames, sounds=c['sounds'], origins=c.get('origins', {}))
    return _formal[clip]


def grips():
    """mag_grip_h1p2.json: carry / push (K: support hand_l local (m) -> metres in the magazine placement's frame, and
    the finger eulers), slide (the same for the overhand grip on the slide, R4), pinch (the inspect's ring grip round the
    magazine's lower half) and the right thumb on the release."""
    d = json.load(open(MAG_GRIP, encoding='utf-8'))
    out = {k: (Matrix(d[k]['K']), {n: tuple(v) for n, v in d[k]['e'].items()}) for k in ('carry', 'push', 'slide', 'pinch') if k in d}
    out['press'] = {n: tuple(v) for n, v in d['press_r'].items()}
    out['stop'] = {n: tuple(v) for n, v in d.get('stop_r', {}).items()}
    return out


def pouch_W():
    """The new magazine in its pouch (rigid placement, armature): upside down (top down, base plate up), its right
    side facing out from the body, front forward along the waist, top canted by POUCH['cant']."""
    P = POUCH
    th = math.radians(P['theta'])
    surf = Vector((P['a'] * math.cos(th), P['cy'] - P['b'] * math.sin(th), P['z']))
    n = Vector((math.cos(th) / P['a'], -math.sin(th) / P['b'], 0)).normalized()
    zm = Vector((0, 0, -1))
    R = Matrix((n, zm.cross(n), zm)).transposed()
    R = Matrix.Rotation(math.radians(P['cant']), 3, n) @ R
    return rigid(Matrix.Translation(surf + n * P['gap']) @ R.to_4x4() @ Matrix.Scale(PX, 4) @ MAG_M0.inverted())


def local(R, pivot=MAG_PIVOT):
    """Gun px transform R (relative to the gun root) -> the mesh bone's local transform at its pivot: Blockbench
    axes, model blocks, column-major (16 floats)."""
    Rb = BBI @ R @ BB
    L = Matrix.Translation(-pivot) @ Rb @ Matrix.Translation(pivot)
    L.translation = L.translation / 16.0
    return [round(L[r][c], 6) for c in range(4) for r in range(4)]


# ---- the experienced tactical reload (formal reload_tactical_experienced, 2.3 s; p9_reload_inspect_v1.md 5.1)
# seconds: the support hand off the grip, clear of it (away: where the peel would end on its own; R3 runs the peel
# and the way down as one move, release..pouch, so the hand never stops beside the gun), at the pouch, closed on the
# magazine, out of the pouch, into the formal magazine path (join0..join1), rolled under the base plate
# (flip0..flip1), the magazine seated (formal), off the base plate and straight back onto the grip (leave..regrip;
# R3: no loop out to AWAY); the right thumb on the release (down, held, back up). The
# formal clip cants the gun so the well faces the support hand, and the old magazine slides out of it down to the
# left (0.35..0.85) right across the way up from the pouch, so the hand comes up behind and under the gun and only
# takes the formal path at 0.70, when the old one has fallen past (the formal sounds and the seat stay where they
# are). On the two long moves (down to the pouch, up to the well) the wrist runs along an arc and the hand turns so
# that its wrist angles go straight from the start to the end values (wrist space: both ends are inside the limits,
# so is every frame between; a plain slerp of the hand turns the short way and overtwists the forearm). down_via /
# lift_via: where the wrist passes halfway, as an offset from the straight midpoint (down) or from the wrist at join1
# (up), armature m
TAC_EXP = dict(clip='reload_tactical_experienced', release=0.06, away=0.11, pouch=0.34, grab=0.39, out=0.46,
               join0=0.62, join1=0.70, flip0=0.86, flip1=1.04, seat=1.20, leave=1.26, regrip=1.50,
               press=(0.18, 0.25, 0.33, 0.42), down_via=(0.05, 0.03, 0.0), lift_via=(-0.02, 0.13, -0.10))


class Reload:
    """One reload clip. plan(f) gives the gun, the support hand's target and fingers and the new magazine for frame f;
    frame(f) poses it (pose() report) and caches what the props sidecar needs."""

    def __init__(self, s):
        self.s = s
        self.F = formal(s['clip'])
        self.last = len(self.F['frames']) - 1
        self.cache = {}
        self.ready = False

    def setup(self):
        """What needs the posed stance (hip, or 'ads' for the ADS reloads): its gun, the support grip relative to the
        gun, the pouch."""
        self.p = spec(self.s.get('stance', 'hip'))
        pose(self.p)
        g = grips()
        self.Kc, self.ec = g['carry']
        self.Kp, self.ep = g['push']
        self.press_e = g['press']
        self.stop_e = g['stop']
        self.G_hip = gun_matrix(self.p)
        self.gfp = grip(POSE_FP)
        self.e_grip = dict(self.gfp['l'][1])
        self.e_thumb = {k: v for k, v in self.gfp['r'][1].items() if k.startswith('thumb')}
        Rg = rigid(self.G_hip)
        self.A_grip = Rg.inverted() @ hand_targets(self.G_hip, self.gfp)['l'][0]
        self.A_away = Matrix.Translation(AWAY) @ self.A_grip
        self.W_pouch = pouch_W()
        self.W_out = Matrix.Translation((0, 0, 0.09)) @ self.W_pouch       # pulled straight up, clear of the pouch
        self.cam_max = max(1e-6, max(-fr['camera'][0] for fr in self.F['frames']))
        self._ends = None
        self.t_away, self.tau_a = self.s['away'], None
        self.peel()
        self.ready = True

    def peel(self):
        """R3: the peel and the way down to the pouch as one smoothstep from release to pouch, split where the two
        paths' lengths say, so the hand's speed carries through the corner instead of stopping beside the gun (it
        jerked off the grip at 127 m/s2). t_away is where the peel ends; ends() poses the hand there."""
        s = self.s
        L1 = Vector(AWAY).length
        for _ in range(2):
            (P0, _), (P1, _) = self.ends()['down']
            via = (P0 + P1) / 2 + Vector(s['down_via'])
            pts = [arc(P0, P1, via, k / 20) for k in range(21)]
            L2 = sum((pts[k + 1] - pts[k]).length for k in range(20))
            self.tau_a = L1 / (L1 + L2)
            lo, hi = 0.0, 1.0                                       # smoothstep inverse
            for _ in range(40):
                m = (lo + hi) / 2
                lo, hi = (m, hi) if smooth(m) < self.tau_a else (lo, m)
            self.t_away = s['release'] + (lo + hi) / 2 * (s['pouch'] - s['release'])
            self._ends = None

    def root(self, f):
        """The gun's root this frame: the stance's gun lifted by LIFT as the formal camera looks down (R3; the ADS
        reloads keep the head up, lift (0, 0, 0)). With lift_env (up a..b, down c..d, s) the lift follows that
        smooth envelope instead: a clip that hardly looks down would turn every camera bump into a jump of the gun."""
        if 'lift_env' in self.s:
            a, b, c, d = self.s['lift_env']; t = f / FPS
            w = ramp(t, a, b) * (1 - ramp(t, c, d))
        else:
            w = max(0.0, -self.fr(f)['camera'][0]) / self.cam_max
        return Matrix.Translation(Vector(self.s.get('lift', LIFT)) * w) @ self.G_hip

    def spec_at(self, t):
        """The stance's spec with the support arm blended toward ARM_POUCH while the hand is down at the pouch, and
        toward arm_seat (when the clip has one) from the way up until it is back on the grip."""
        s = self.s
        u = ramp(t, s['release'], s['pouch']) * (1 - ramp(t, s['out'], s['join1']))
        w = ramp(t, s['out'], s['join1']) * (1 - ramp(t, s['leave'], s['regrip'])) if 'arm_seat' in s else 0.0
        if u <= 0 and w <= 0:
            return self.p
        p = dict(self.p)
        for arm, k_u in ((ARM_POUCH, u), (s.get('arm_seat', {}), w)):
            if k_u <= 0:
                continue
            for k, v in arm.items():
                r0, l0 = self.p[k]
                p[k] = (r0, l0 + (v - l0) * k_u)
        return p

    def fr(self, f):
        return self.F['frames'][max(0, min(f, self.last))]

    def G(self, f):
        return self.root(f) @ self.fr(f)['parts']['handling']

    def W_formal(self, f):
        """The formal new magazine (mag_out, which ends on the seated magazine), gun px -> armature."""
        return self.root(f) @ self.fr(f)['parts']['mag_out']

    def ends(self):
        """Wrist point and wrist angles where the two long moves start and end (posed once, cached)."""
        if getattr(self, '_ends', None):
            return self._ends
        s = self.s

        def at(t, H, e):
            pose(self.spec_at(t), G=self.G(round(t * FPS)), left=(H, e))
            return rig().pose.bones['hand_l'].head.copy(), wrist_raw('l')
        H_away = rigid(self.G(round(self.t_away * FPS))) @ self.A_away
        H_join = rigid(self.W_formal(round(s['join1'] * FPS))) @ self.Kc
        pouch = at(s['pouch'], self.W_pouch @ self.Kc, REACH)
        out = (pouch[0] + (self.W_out.translation - self.W_pouch.translation), pouch[1])  # straight up, same wrist
        self._ends = dict(down=(at(self.t_away, H_away, RELAXED), pouch), out=(pouch, out),
                          lift=(out, at(s['join1'], H_join, self.ec)))
        return self._ends

    def wrist_hand(self, spec_t, G, e, seg, u):
        """The support hand's target on a long move: the wrist on its arc, the hand turned from the forearm the arm
        IK gives there by wrist angles straight between the ends."""
        (P0, e0), (P1, e1) = self.ends()[seg]
        via = dict(down=(P0 + P1) / 2 + Vector(self.s['down_via']), out=(P0 + P1) / 2,
                   lift=P1 + Vector(self.s['lift_via']))[seg]
        pos = arc(P0, P1, via, u)
        pose(spec_t, G=G, left=(Matrix.Translation(pos), e))          # the arm IK depends on the wrist point only
        hb = rig().pose.bones['hand_l']
        rest = hb.parent.bone.matrix_local.inverted() @ hb.bone.matrix_local
        ang = Euler(tuple(a + (b - a) * u for a, b in zip(e0, e1)), 'YXZ')
        R = (hb.parent.matrix @ rest).to_3x3().normalized() @ ang.to_matrix()
        return Matrix.Translation(pos) @ R.to_4x4()

    def press(self, t):
        """The shooting thumb: on the magazine release (press), or on the slide stop (stop, the ADS empty reload)."""
        for key, e in (('press', self.press_e), ('stop', self.stop_e)):
            if key not in self.s:
                continue
            a, b, c, d = self.s[key]
            u = ramp(t, a, b) * (1 - ramp(t, c, d))
            if u > 0:
                return blend_e(self.e_thumb, e, u)
        return None

    def slip(self, t):
        """Fumbles: the carrying hand slid down the magazine (its axis, toward the base plate) by slip[4] m between
        slip[0..1] and slip[2..3], so it holds it low for the shove that sticks and stays clear of the shooting hand."""
        if 'slip' not in self.s:
            return Matrix.Identity(4)
        a, b, c, d, m = self.s['slip']
        w = ramp(t, a, b) * (1 - ramp(t, c, d))
        return Matrix.Translation(Vector((0, -math.sin(MAG_TILT), -math.cos(MAG_TILT))) * m * w)

    def plan(self, f):
        s, t = self.s, f / FPS
        G = self.G(f)
        Rg = rigid(G)
        out = dict(t=t, G=G, fr=self.fr(f), left=None, e=None, W=None, free=False, right=self.press(t))
        if t <= s['release'] or t >= s['regrip']:
            return out
        if t < s['pouch']:                              # off the grip and down to the pouch, one move (peel())
            tau, a = ramp(t, s['release'], s['pouch']), self.tau_a
            if tau < a:                                 # the peel, gun-relative
                u = tau / a
                out.update(left=Rg @ lerp_m(self.A_grip, self.A_away, u),
                           e=blend_e(self.e_grip, RELAXED, peel_fingers(u)))
            else:                                       # the wrist on its arc down
                u = (tau - a) / (1 - a)
                out.update(wrist=('down', u), e=blend_e(RELAXED, REACH, u))
        elif t < s['grab']:                             # closing on the magazine
            out.update(left=self.W_pouch @ self.Kc, e=blend_e(REACH, self.ec, ramp(t, s['pouch'], s['grab'])))
        elif t < s['out']:                              # pulled out of the pouch: the magazine rides from here
            out.update(wrist=('out', ramp(t, s['grab'], s['out'])), e=self.ec, free=True)
        elif t < s['join1']:                            # up to the well, onto the formal path from join0
            out.update(wrist=('lift', ramp(t, s['out'], s['join1'])), e=self.ec, free=True,
                       to=(rigid(self.W_formal(f)) @ self.Kc, ramp(t, s['join0'], s['join1'])))
        elif t < s['seat']:                             # in the formal path; under the base plate for the push
            W = self.W_formal(f)
            u = ramp(t, s['flip0'], s['flip1'])
            out.update(left=rigid(W) @ self.slip(t) @ lerp_m(self.Kc, self.Kp, u), e=blend_e(self.ec, PUSH, u), W=W)
        elif t < s['leave']:                            # seated: the hand stays on the base plate
            W = self.root(f) @ self.fr(f)['parts']['magazine']
            out.update(left=rigid(W) @ self.Kp, e=PUSH, W=W)
        else:                                           # off the base plate straight onto the grip (gun-relative)
            u = ramp(t, s['leave'], s['regrip'])
            A_seat = Rg.inverted() @ rigid(self.root(f) @ self.fr(f)['parts']['magazine']) @ self.Kp
            via = (A_seat.translation + self.A_grip.translation) / 2 + Vector(REGRIP_VIA)
            mid = 0.55                                  # fingers ease open, then close round the grip on arrival
            e = blend_e(blend_e(PUSH, RELAXED, smooth(u / mid)), self.e_grip, smooth((u - mid) / (1 - mid)))
            out.update(left=Rg @ lerp_m(A_seat, self.A_grip, u, via), e=e)
        return out

    def frame(self, f):
        if not self.ready:
            self.setup()
        pl = self.plan(f)
        spec_t = self.spec_at(pl['t'])
        H = pl['left']
        if pl.get('wrist'):
            H = self.wrist_hand(spec_t, pl['G'], pl['e'], *pl['wrist'])
            if pl.get('to'):
                H = lerp_m(H, *pl['to'])
        elif pl.get('rel'):
            H = self.rel_hand(spec_t, pl['G'], pl['e'], *pl['rel'])
        left = (H, pl['e']) if H is not None else None
        rep = pose(spec_t, G=pl['G'], left=left, right=pl['right'])
        W = pl['W']
        if pl['free']:                                  # carried by the hand (K: a grip changing in the hand)
            W = scaled(rig().pose.bones['hand_l'].matrix @ pl.get('K', self.Kc).inverted())
        self.cache[f] = dict(t=pl['t'], G=pl['G'].copy(), W=None if W is None else W.copy(), fr=pl['fr'])
        return rep

    def shown_new(self, t):
        return 1 if self.s['grab'] <= t < self.s['seat'] else 0

    def props(self):
        """The clip's props track: mag_out (the new magazine) and empty_old_mag local transforms, shown flags,
        camera. A hidden new magazine holds its nearest shown place, so the frame blend never streaks."""
        frames = range(self.last + 1)
        Ws = [self.cache[f]['W'] for f in frames]
        first = next(i for i, w in enumerate(Ws) if w is not None)
        for i in range(len(Ws)):
            if Ws[i] is None:
                Ws[i] = Ws[first] if i < first else Ws[i - 1]
        parts = {'mag_out': [], 'empty_old_mag': []}
        parts.update({n: [] for n in self.extra_parts})
        shown = {'magazine': [], 'reload_magazine': [], 'empty_old_mag': []}
        camera = []
        follower = self.follower_locals() if 'follower' in self.extra_parts else None
        for f in frames:
            c = self.cache[f]
            Gi = c['G'].inverted()
            parts['mag_out'] += local(Gi @ Ws[f])
            parts['empty_old_mag'] += local(Gi @ self.root(f) @ c['fr']['parts']['empty_old_mag'])
            if 'slide' in self.extra_parts:                 # the slide on the gun (locked back, racked: formal)
                hp = c['fr']['parts']
                parts['slide'] += local(hp['handling'].inverted() @ hp['slide'], self.pivot('slide'))
            if follower:
                parts['follower'] += follower[f]
            shown['magazine'].append(int(c['fr']['shown']['magazine']))
            shown['empty_old_mag'].append(int(c['fr']['shown']['empty_old_mag']))
            shown['reload_magazine'].append(self.shown_new(c['t']))
            camera += [round(x, 4) for x in c['fr']['camera']]
        return dict(frames=self.last + 1, length=self.F['length'], parts=parts, shown=shown, camera=camera)

    extra_parts = ()

    def pivot(self, name):
        return Vector(self.F['origins'].get(name, (0, 0, 0)))

    def follower_locals(self):
        """The follower in its magazine (formal; its parent is scaled to 0 while hidden, so those frames hold the
        nearest shown one)."""
        out = []
        for f in range(self.last + 1):
            hp = self.fr(f)['parts']
            out.append(local(hp['magazine'].inverted() @ hp['follower'], self.pivot('follower'))
                       if self.fr(f)['shown']['magazine'] else None)
        first = next(i for i, x in enumerate(out) if x is not None)
        for i in range(len(out)):
            if out[i] is None:
                out[i] = out[first] if i < first else out[i - 1]
        return out


# ---- the experienced empty reload (formal reload_empty_experienced, 2.8 s; R4): the tactical reload's magazine
# change (the formal magazine pauses at the well mouth, so the roll under the base plate and the seat come later),
# then the support hand goes round the left side of the gun onto the locked-back slide - overhand: palm over the rear
# of the slide, fingers curled onto its right side, heel and thumb on the left (mag_grip_h1p2.json 'slide', found by
# the scratch search r4_slide.py: the slide placed on the still hand, fingers and thumb closed until they touch, then
# the reach and wrist at the formal rack frames; the elbow swung out 40 deg, the shoulder forward 30) - rides it back
# off the slide stop with the formal slide (1.77..1.87), lets go at the release sound (the slide runs home in two
# frames under the opening fingers) and comes back round to the grip. Seconds: slide0 on the slide (the formal clip
# rolls the gun about 80 deg toward the hand on the way), close the fingers shut, let released, regrip on the grip.
EMPTY_EXP = dict(TAC_EXP, clip='reload_empty_experienced', flip0=0.90, flip1=1.08, seat=1.25, leave=1.30,
                 slide0=1.65, close=1.73, let=1.935, regrip=2.35)
ARM_SLIDE = dict(swivel=40.0, elevate=0.0, protract=30.0)
# R4b: the two moves round the gun follow the formal hand's way (formal_ref lefthand, gun-relative): to the slide,
# down off the base plate, out along the left side, up past the shooting thumb and over the top onto the slide;
# home, up and forward off the slide, out and down the left side, in to the grip from the left at grip height (one
# arc through the straight midpoint went through the grip and the shooting hand). Wrist keys between the ends, gun
# axes (mm: gun-right, muzzle, up), ('start' / 'end': offset from that end, None: on the gun), each with its hand's
# turn: a share of the way from the start's wrist angles to the end's, or (fingers, palm) in the gun's axes - up the
# left side the hand stands on its edge, palm to the gun and fingers up (turned flat, its little finger's edge swept
# the shooting thumb), and folds over the top onto the slide (and back the same way).
# The fingers lean back toward the thumb (straight up or forward the wrist went past its 20 deg ulnar limit), and a
# turned key's wrist angles are held inside 85% of the wrist_limit range.
RACK_KEYS = (((-10, -5, -30), 'start', 0.0), ((-80, -118, 25), None, ((0, 0.7, 0.7), (0.7, 0, 0.7))),
             ((-90, -100, 105), None, ((0, -0.05, 1), (1, 0, 0))), ((-25, 0, 30), 'end', 1.0))
HOME_KEYS = (((-12, 8, 36), 'start', 0.0), ((-100, -100, 100), None, ((0, 0.1, 1), (1, 0, 0))),
             ((-40, 0, -12), 'end', 1.0))       # in from a little low: the thumb slides in under the shooting thumb
WRIST_KEEP = ((-70, 80), (-90, 90), (-45, 20))
# home, the open hand's thumb lies along the index (the rest thumb stood 23 mm off the palm and swept the shooting
# thumb on the way down; thumb_01 scanned at f131-134)
TUCKED = dict(REACH, thumb_01=(-10.0, -35.0, 30.0))
SLIDE_OPEN = _fingers((('index', (20, 15, 8), 7.0), ('middle', (20, 15, 8), 0.0), ('ring', (20, 15, 8), -6.0),
                       ('pinky', (20, 15, 8), -18.0)),
                      dict(thumb_01=(-9.5, -34.5, 8.0), thumb_02=(4.0, 0, 0), thumb_03=(2.0, 0, 0)))


class RackReload(Reload):
    """A reload that ends by racking the locked-back slide overhand (empty reloads)."""
    extra_parts = ('slide', 'follower')

    def setup(self):
        self._rack = None
        super().setup()
        self.Ks, self.es = grips()['slide']

    def spec_at(self, t):
        p = super().spec_at(t)
        s = self.s
        u = ramp(t, s['leave'], s['slide0']) * (1 - ramp(t, s['let'], s['regrip']))
        if u <= 0:
            return p
        p = dict(p)
        for k, v in ARM_SLIDE.items():
            r0, l0 = p[k]
            p[k] = (r0, l0 + (v - l0) * u)
        return p

    def W_slide(self, f):
        """The slide (formal: locked back, pulled off the stop, run home), gun px -> armature."""
        return self.root(f) @ self.fr(f)['parts']['slide']

    def rack_ends(self):
        """The two moves round the gun as wrist keys [(point in the gun's rigid axes, wrist angles)]: the posed ends
        and RACK_KEYS / HOME_KEYS between them."""
        if self._rack:
            return self._rack
        s = self.s

        def at(t, H, e):
            f = round(t * FPS)
            pose(self.spec_at(t), G=self.G(f), left=(H, e))
            return rigid(self.G(f)).inverted() @ rig().pose.bones['hand_l'].head, wrist_raw('l')
        f0, fg = round(s['slide0'] * FPS), round(s['let'] * FPS)
        seat = at(s['leave'], *self.rack_start())
        on = at(s['slide0'], rigid(self.W_slide(f0)) @ self.Ks, SLIDE_OPEN)
        let = at(s['let'], rigid(self.W_slide(fg)) @ self.Ks, self.es)
        grip = at(s['regrip'], rigid(self.G(round(s['regrip'] * FPS))) @ self.A_grip, self.e_grip)

        def turned(t, q, fingers, palm):
            """Wrist angles that turn the hand to (fingers, palm) (gun axes) with the wrist at q, about time t."""
            f = round(t * FPS)
            Rg = rigid(self.G(f))
            pose(self.spec_at(t), G=self.G(f), left=(Matrix.Translation(Rg @ q), RELAXED))
            y = Vector(fingers).normalized()
            z = Vector(palm) - Vector(palm).dot(y) * y
            z.normalize()
            R = Rg.to_3x3() @ Matrix((y.cross(z), y, z)).transposed()
            hb = rig().pose.bones['hand_l']
            rest = hb.parent.bone.matrix_local.inverted() @ hb.bone.matrix_local
            P = (hb.parent.matrix @ rest).to_3x3().normalized()
            ang = (P.inverted() @ R).to_euler('YXZ')
            return Vector(min(math.radians(hi * 0.85), max(math.radians(lo * 0.85), a))
                          for a, (lo, hi) in zip(ang, WRIST_KEEP))

        def keys(a, b, mid, t0, t1):
            pts = [a[0]] + [Vector(off) / 1000.0 + {'start': a[0], 'end': b[0], None: Vector()}[base]
                            for off, base, _ in mid] + [b[0]]
            L = [0.0]
            for p, q in zip(pts, pts[1:]):
                L.append(L[-1] + (q - p).length)
            out = [(a[0], Vector(a[1]))]
            for i, (_, _, w) in enumerate(mid, 1):
                if isinstance(w, tuple):                # about when the move gets there (u = smooth(time share))
                    x = 0.5 - math.sin(math.asin(1 - 2 * L[i] / L[-1]) / 3)
                    out.append((pts[i], turned(t0 + (t1 - t0) * x, pts[i], *w)))
                else:
                    out.append((pts[i], Vector(a[1]).lerp(Vector(b[1]), w)))
            return out + [(b[0], Vector(b[1]))]
        self._rack = dict(to_slide=keys(seat, on, self.rack_keys, s['leave'], s['slide0']),
                          home=keys(let, grip, HOME_KEYS, s['let'], s['regrip']))
        return self._rack

    rack_keys = RACK_KEYS
    rack_from = PUSH                                    # the fingers the move to the slide starts with

    def rack_start(self):
        """Where the move to the slide starts (hand target, fingers): on the seated magazine's base plate."""
        fl = round(self.s['leave'] * FPS)
        return rigid(self.root(fl) @ self.fr(fl)['parts']['magazine']) @ self.Kp, PUSH

    def rel_hand(self, spec_t, G, e, seg, u):
        """As wrist_hand, but the wrist's path rides on the gun (the formal clip turns the gun toward the hand)."""
        q, a = spline(self.rack_ends()[seg], u)
        pos = rigid(G) @ q
        pose(spec_t, G=G, left=(Matrix.Translation(pos), e))
        hb = rig().pose.bones['hand_l']
        rest = hb.parent.bone.matrix_local.inverted() @ hb.bone.matrix_local
        ang = Euler(tuple(a), 'YXZ')
        R = (hb.parent.matrix @ rest).to_3x3().normalized() @ ang.to_matrix()
        return Matrix.Translation(pos) @ R.to_4x4()

    def plan(self, f):
        s, t = self.s, f / FPS
        if t < s['leave'] or t >= s['regrip']:
            return super().plan(f)
        out = dict(t=t, G=self.G(f), fr=self.fr(f), left=None, e=None, W=None, free=False, right=self.press(t))
        if t < s['slide0']:                             # off the base plate, round the left side onto the slide
            u = ramp(t, s['leave'], s['slide0'])
            # the fingers open off the base plate first (curled, they swept the shooting fingers), shape for the slide late
            e = blend_e(blend_e(self.rack_from, REACH, smooth(u / 0.25)), SLIDE_OPEN, smooth((u - 0.6) / 0.4))
            out.update(rel=('to_slide', u), e=e)
        elif t < s['let']:                              # on the slide: shut the fingers, ride it back off the stop
            out.update(left=rigid(self.W_slide(f)) @ self.Ks, e=blend_e(SLIDE_OPEN, self.es, ramp(t, s['slide0'], s['close'])))
        else:                                           # let go (fingers flicked open), back round to the grip
            u = ramp(t, s['let'], s['regrip'])
            e = blend_e(self.es, TUCKED, ramp(t, s['let'], s['let'] + 0.06))
            mid = 0.8                                   # open past the shooting thumb, shut on the grip on arrival
            out.update(rel=('home', u), e=blend_e(e, self.e_grip, smooth((u - mid) / (1 - mid))))
        return out


# ---- the novice tactical reload (formal reload_tactical_novice, 3.1 s; Lv 0-3, the start of every ordinary survivor):
# the same hands as the experienced one, done one thing after another - the formal clip tilts the gun and looks down
# about 13 deg before anything else, and the support hand stays on the grip through it; it only loosens off the grip as
# the shooting thumb goes to the release (0.46; on the grip the thumb went 6-9 mm into it) and heads down while the
# release is pressed and held (0.50..0.76, sound 0.61), not ahead of it as the experienced hand does; a longer hold at
# the pouch; it joins the formal path later (the old magazine has long fallen), holds the magazine at the well mouth
# while the formal clip lines it up (1.47..1.60), rolls under the base plate while it goes in slowly, and stays on it
# through the formal second look (2.13..2.40) before going back to the grip. Slower moves than the experienced ones
# (left wrist up to about 3 m/s against 3.75), not clumsy ones. down_via / lift_via (scratch diag_nov_bg.py): the
# experienced ways put the elbow past its bend (13 mm short) with this clip's gun, so both pass further out and lower.
TAC_NOV = dict(TAC_EXP, clip='reload_tactical_novice', release=0.46, away=0.52, pouch=0.93, grab=1.02, out=1.08,
               join0=1.30, join1=1.40, flip0=1.58, flip1=1.78, seat=2.00, leave=2.38, regrip=2.70,
               press=(0.50, 0.58, 0.66, 0.76), down_via=(0.08, -0.04, -0.08), lift_via=(0.0, 0.08, -0.12))
# ---- the novice empty reload (formal reload_empty_novice, 3.8 s): the novice tactical reload's magazine change (the
# formal clip is the same frame for frame up to the seat), then the experienced empty reload's overhand rack on the
# formal novice beats - the hand stays on the base plate through the second look and leaves as the formal clip starts
# turning the gun to it (2.40..2.67; leaving at 2.34 swept the shooting hand 8.5 mm, scratch diag_nov_empty_bg.py),
# gets onto the slide just after the gun is there, holds it closed through the formal pause before the pull (slide back
# 2.87..3.07, sound 2.83), lets go at the release (3.135) and comes home while the gun goes back (3.33..3.60).
EMPTY_NOV = dict(TAC_NOV, clip='reload_empty_novice', leave=2.44, slide0=2.74, close=2.80, let=3.135, regrip=3.60)
# ---- the novice fumbles (formal reload_tactical_novice_fumble 4.0 s / reload_empty_novice_fumble 4.7 s): the formal
# clips are the novice ones frame for frame up to 1.0 s, carry the new magazine within 13 mm of them to the well, then
# put 0.9 s in (1.60..2.50: pushed in crooked, stuck 51 mm short, pulled back out and lined up again); from 2.50 they
# are the novice clips from 1.60 frame for frame (scratch cmp_shift2.py). The hand keeps the carrying grip on the
# formal magazine through the fumble, so everything from the flip on is the novice beat plus 0.9 s. slip: holding it
# where the novice clip does, the stuck magazine (51 mm short) put the fingers 5-7 mm into the shooting hand for half a
# second, so the hand slides 22 mm down it during the line-up pause at the well (1.48..1.60) and back up while it is
# lined up again (2.36..2.48), before the flip.
FUMBLE = 0.90
SLIP = (1.48, 1.60, 2.36, 2.48, 0.022)
TAC_NOV_FUMBLE = dict(TAC_NOV, clip='reload_tactical_novice_fumble', slip=SLIP,
                      **{k: round(TAC_NOV[k] + FUMBLE, 3) for k in ('flip0', 'flip1', 'seat', 'leave', 'regrip')})
EMPTY_NOV_FUMBLE = dict(EMPTY_NOV, clip='reload_empty_novice_fumble', slip=SLIP,
                        **{k: round(EMPTY_NOV[k] + FUMBLE, 3) for k in ('flip0', 'flip1', 'seat', 'leave', 'slide0',
                                                                        'close', 'let', 'regrip')})
# ---- the experienced ADS reloads (formal reload_tactical_experienced_ads 2.3 s / reload_empty_experienced_ads 2.7 s;
# Lv 5-6 while aiming): the formal clips keep the gun up at the eye (rolled about 10 deg, not 34) and the camera level,
# with the experienced sounds and magazine beats (the tactical one's shown flags are the hip clip's frame for frame), so
# the hands are the experienced ones from the ADS stance and the gun is not lifted (camera pitch under 0.3 deg). The
# empty one does not rack: the shooting thumb drops the slide off the stop (stop: onto the lever 1.28..1.34, held down
# through the slide release sound 1.375, off by 1.48; mag_grip_h1p2.json 'stop_r'), the support hand leaves the base
# plate as the thumb goes and is back on the grip as the formal gun comes home (1.45..1.75).
TAC_EXP_ADS = dict(TAC_EXP, clip='reload_tactical_experienced_ads', stance='ads', lift=(0.0, 0.0, 0.0))
EMPTY_EXP_ADS = dict(TAC_EXP_ADS, clip='reload_empty_experienced_ads', flip0=0.90, flip1=1.08, seat=1.25, leave=1.30,
                     regrip=1.72, stop=(1.28, 1.34, 1.40, 1.48))


# ---- the practised tactical reload (formal reload_tactical, 1.7 s; Lv 7-10): the experienced hands on the COD-style
# formal beats (MWII X12 reference) - the support hand is off the grip and on its way down before the thumb reaches the
# release (sound 0.21), closes on the magazine as the old one drops (0.26), comes up straight into the formal path
# (at the well mouth 0.60, 16 mm short 0.75, seated 0.883), and is off the base plate at once and back on the grip as
# the formal gun comes home (1.05..1.30). The formal clip looks down only 1.25 deg (the experienced one 6.9), so the
# lift (scaled by the clip's own deepest look) is the same 35 mm. Scratch diag_prac_bg.py: the formal gun turns
# further toward the hand (40 deg against 30), and with the hip arm the carrying and pushing wrist sat on its 20 deg
# ulnar limit (0.45..0.57), so the elbow drops 10 deg from the way up until it is back on the grip (arm_seat); the old
# magazine falls slowly under the well, so the hand comes up 6 cm further out and takes the formal path at 0.48..0.54
# (on the experienced way and times it went 10.5 mm into it at 0.43..0.47). The formal camera looks down only 1.25 deg
# and bumps 1 deg at the seat, which the camera-scaled lift turned into a 26 mm drop and back over f51..56 (user
# 2026-10-24: too much shake), so the lift is a smooth envelope over the formal look (up 0.02..0.30, down 1.00..1.35).
TAC_PRAC = dict(TAC_EXP, clip='reload_tactical', release=0.02, away=0.06, pouch=0.22, grab=0.26, out=0.36,
                join0=0.48, join1=0.54, flip0=0.58, flip1=0.70, seat=0.883, leave=0.92, regrip=1.18,
                press=(0.12, 0.17, 0.24, 0.31), lift_via=(-0.06, 0.10, -0.12),
                arm_seat=dict(elevate=10.0), lift_env=(0.02, 0.30, 1.00, 1.35))
# ---- the practised empty reload (formal reload_empty, 2.0 s): no rack - the formal clip is the practised tactical one
# about 2 frames later up to the seat (seated f54 = 0.90, sounds +0.04 s), then the shooting thumb drops the slide off
# the stop (slide release sound 1.015, the slide runs home at f61..62) as the experienced ADS empty reload does, and the
# gun comes home 8 frames later than the tactical one (1.13..1.40). The support hand leaves the base plate as the thumb
# goes and is back on the grip as the gun gets home; the lift goes down with it.
EMPTY_PRAC = dict(TAC_PRAC, clip='reload_empty', out=0.37, join0=0.50, join1=0.56, flip0=0.61, flip1=0.73, seat=0.90,
                  leave=0.94, regrip=1.31, stop=(0.93, 0.99, 1.04, 1.12), lift_env=(0.02, 0.30, 1.13, 1.48))
# ---- the practised ADS reloads (formal reload_tactical_ads 1.7 s / reload_empty_ads 2.0 s): the hip clips' sounds and
# shown flags frame for frame, the gun up at the eye (rolled 11..14 deg, not 55) and the camera level, home a little
# earlier (the empty one by about 0.1 s), so the practised hands from the ADS stance, not lifted. Scratch
# diag_prac_ads_bg.py: the gun hardly turns, so the old magazine falls straight under the well and the hip way up went
# 10.7 mm into it (f29) - the hand comes up 4 cm further out; no lowered elbow (arm_seat set the ADS arm's 4 deg to 10
# or -2 and put the reach 2 mm off with a worse wrist); the empty one's hand leaves the base plate 0.03 s later (left
# with the thumb, the wrist went onto its limit at f62; now 0.982, as the experienced ADS empty reload).
_PRAC_ADS = dict(stance='ads', lift=(0.0, 0.0, 0.0), lift_via=(-0.10, 0.12, -0.06))
TAC_PRAC_ADS = dict({k: v for k, v in TAC_PRAC.items() if k != 'arm_seat'}, clip='reload_tactical_ads', **_PRAC_ADS)
EMPTY_PRAC_ADS = dict({k: v for k, v in EMPTY_PRAC.items() if k != 'arm_seat'}, clip='reload_empty_ads', **_PRAC_ADS,
                      leave=0.97, regrip=1.24)


class StopReload(Reload):
    """An empty reload that ends with the shooting thumb on the slide stop (the slide and follower are formal)."""
    extra_parts = ('slide', 'follower')


RELOADS = {}


def register(s, kind=Reload):
    r = kind(s)
    RELOADS[s['clip']] = r
    ACTIONS['fp_' + s['clip']] = (r.last, r.frame)
    return r


def write_props():
    out = dict(_doc='H1-P2 props sidecar (fp_reload_h1p1.py): per action clip (the formal P9-01 clip name; the body '
                    'clip is fp_<name>), 60 fps: parts = local transform at the bone pivot, mesh model blocks, '
                    'column-major; shown = 1 / 0 (a hidden part hides its children); camera = the formal camera '
                    'bone, author-space deg.',
               fps=FPS, clips={k: r.props() for k, r in RELOADS.items() if r.cache})
    with open(PROPS, 'w', encoding='utf-8') as fh:
        json.dump(out, fh, separators=(',', ':'))
    return PROPS


# ---- checks and first-person frames

def _field(with_mag, part='core'):
    """The P9's distance fields (gun px), cached on builtins: part 'core' is everything but the slide, with or
    without its seated magazine; 'slide' the slide at rest (R4: the empty reload moves it)."""
    key = '_p9_field_%s_%s' % (part, 'mag' if with_mag else 'frame')
    fld = getattr(builtins, key, None)
    if fld is None:
        if part == 'slide':
            obs = [o for o in bpy.data.collections['P9_ref'].objects if o.name.startswith('p9_slide')]
        else:
            obs = [o for o in bpy.data.collections['P9_ref'].objects if not o.name.startswith('p9_slide')
                   and (with_mag or not o.name.startswith('p9_part_mag_'))]
        fld = sys.modules['grip_h1p1'].WideGunField(obs)
        setattr(builtins, key, fld)
    return fld


def gun_sd_mm(gpx, fr):
    """Signed distance (mm) of points in the gun's px frame to the gun this formal frame: the core (with the seated
    magazine while it shows) united with the slide where the formal clip puts it."""
    D = fr['parts']['handling'].inverted() @ fr['parts']['slide']
    Di = np.array(D.inverted())
    q = gpx @ Di[:3, :3].T + Di[:3, 3]
    return np.minimum(_field(bool(fr['shown']['magazine'])).sd_many(gpx), _field(True, 'slide').sd_many(q)) * 19.0


def mag_sd_mm(P, W):
    """Signed distance (mm, negative inside) of armature points P to the magazine placed by W (gun px -> armature)."""
    A = np.array((MAG_M0.inverted() @ W.inverted()))
    q = (P @ A[:3, :3].T + A[:3, 3]) * PX * 1000.0
    best = np.full(len(P), 1e9)
    for lo, hi in MAG_BOXES:
        c, h = (np.array(lo) + hi) / 2, (np.array(hi) - lo) / 2
        d = np.abs(q - c) - h
        best = np.minimum(best, np.linalg.norm(np.maximum(d, 0), axis=-1) + np.minimum(d.max(-1), 0))
    return best


def _skin():
    """Armature-space skin verts, the dominant deform bone per vertex (fp_arms, the mask group, does not count) and
    the triangles."""
    body = bpy.data.objects['survivor_body']
    ev = body.evaluated_get(bpy.context.evaluated_depsgraph_get())
    me = ev.to_mesh()
    M = rig().matrix_world.inverted() @ body.matrix_world
    V = np.array([tuple(M @ v.co) for v in me.vertices])
    tris = getattr(builtins, '_h1p2_tris', None)
    if tris is None:
        me.calc_loop_triangles()
        tris = builtins._h1p2_tris = [tuple(t.vertices) for t in me.loop_triangles]
    ev.to_mesh_clear()
    dom = getattr(builtins, '_h1p2_dom', None)
    if dom is None:
        names = [g.name for g in body.vertex_groups]
        bones = set(rig().data.bones.keys())
        dom = []
        for x in body.data.vertices:
            gs = sorted(((g.weight, names[g.group]) for g in x.groups if names[g.group] in bones), reverse=True)
            dom.append(gs[0][1] if gs else 'root')
        builtins._h1p2_dom = dom = np.array(dom)
    return V, dom, tris


def _hand(dom, s):
    keep = {'hand_' + s} | {f + '_0%d_' % j + s for f in FINGERS for j in (1, 2, 3)}
    return np.array([d in keep for d in dom])


def _forearm(dom, s):
    return np.array([d in ('forearm_' + s, 'forearm_twist_' + s) for d in dom])


def wrist(s):
    """Hand relative to its forearm in the wrist_limit constraint's own terms (LOCAL, YXZ euler, deg): x flexion /
    extension, y twist, z ulnar (+ right, - left) / radial."""
    pb = rig().pose.bones['hand_' + s]
    par = pb.parent
    rest = par.bone.matrix_local.inverted() @ pb.bone.matrix_local
    e = ((par.matrix @ rest).inverted() @ pb.matrix).to_euler('YXZ')
    return tuple(round(math.degrees(a), 1) for a in e)


def strain(w):
    """Left wrist angles (wrist('l')) as the largest share of the wrist_limit range in use (1 = at a limit)."""
    x, y, z = w
    return max(x / 80 if x > 0 else -x / 70, abs(y) / 90, -z / 45 if z < 0 else z / 20)


def check(clip, frames=None, hands_every=2):
    """Per frame: IK error both arms, wrists, left-hand skin into the gun / the new magazine / the falling old one,
    the left hand's contact gap to the new magazine while it holds it, hand into hand, the left wrist's speed. Returns
    (rows, summary)."""
    r = RELOADS[clip]
    r.setup()
    s = r.s
    rows, prev, fprev = [], None, None
    for f in (frames or range(r.last + 1)):
        rep = r.frame(f)
        c = r.cache[f]
        t, G, fr = c['t'], c['G'], c['fr']
        V, dom, tris = _skin()
        hl, hr = _hand(dom, 'l'), _hand(dom, 'r')
        al, ar = hl | _forearm(dom, 'l'), hr | _forearm(dom, 'r')
        Gi = np.array(G.inverted())
        gpx = V @ Gi[:3, :3].T + Gi[:3, 3]
        row = dict(f=f, t=round(t, 3), pos_l=rep['l']['pos_err_mm'], rot_l=rep['l']['rot_err_deg'],
                   pos_r=rep['r']['pos_err_mm'], rot_r=rep['r']['rot_err_deg'], wrist_l=wrist('l'), wrist_r=wrist('r'),
                   elbow_l=rep['l']['elbow_flex'], strain_l=round(strain(wrist('l')), 3))
        dl = gun_sd_mm(gpx[al], fr)
        row['l_into_gun'] = round(float(max(0.0, -dl.min())), 2)
        dr = gun_sd_mm(gpx[hr], fr)
        row['r_into_gun'] = round(float(max(0.0, -dr.min())), 2)
        if c['W'] is not None and r.shown_new(t):
            d = mag_sd_mm(V[hl], c['W'])
            row['l_into_mag'], row['l_mag_gap'] = round(float(max(0.0, -d.min())), 2), round(float(max(0.0, d.min())), 2)
            dr2 = mag_sd_mm(V[hr], c['W'])
            row['r_into_mag'] = round(float(max(0.0, -dr2.min())), 2)
        if fr['shown']['empty_old_mag']:
            d = mag_sd_mm(V[al], r.root(f) @ fr['parts']['empty_old_mag'])
            row['l_into_old'] = round(float(max(0.0, -d.min())), 2)
        if f % hands_every == 0:
            # hand and forearm into the other hand and forearm (the forearm tubes are open only at their ends)
            rt = [tt for tt in tris if all(ar[i] for i in tt)]
            pen = penetration(gpx[al], rt, gpx, False) * 19.0
            row['l_into_r'] = round(float(pen.max()), 2)
            lt = [tt for tt in tris if all(al[i] for i in tt)]
            pen = penetration(gpx[ar], lt, gpx, False) * 19.0
            row['r_into_l'] = round(float(pen.max()), 2)
        w = Vector(rig().pose.bones['hand_l'].head)
        if prev is not None and f > fprev:
            row['v_l'] = round((w - prev).length * FPS / (f - fprev), 2)
        prev, fprev = w, f
        rows.append(row)
    keys = sorted({k for row in rows for k in row if k not in ('f', 't', 'wrist_l', 'wrist_r')})
    summary = {k: max(((row[k], row['f']) for row in rows if k in row), default=None) for k in keys}
    summary['wrist_l_x'] = (min(row['wrist_l'][1] for row in rows), max(row['wrist_l'][1] for row in rows))
    summary['wrist_l_z'] = (min(row['wrist_l'][2] for row in rows), max(row['wrist_l'][2] for row in rows))
    summary['wrist_r_z'] = (min(row['wrist_r'][2] for row in rows), max(row['wrist_r'][2] for row in rows))
    return rows, summary


def _instance(name, coll):
    ob = bpy.data.objects.get(name)
    if ob is None:
        ob = bpy.data.objects.new(name, None)
        ob.instance_type = 'COLLECTION'
        bpy.data.collections['Body_H1P1'].objects.link(ob)
    ob.instance_collection = bpy.data.collections[coll]
    return ob


def _collections():
    """Shared-object views of P9_ref: the gun without its slide and follower, with (P9_core_mag_ref) or without
    (P9_core_ref) its seated magazine, the slide (P9_slide_ref) and the follower (P9_follower_ref) to place where the
    formal clip puts them (R4), and one whole magazine at rest (P9_mag_ref)."""
    core = lambda n: not n.startswith('p9_slide') and n != 'p9_part_mag_follower'
    for name, pick in (('P9_core_mag_ref', core),
                       ('P9_core_ref', lambda n: core(n) and not n.startswith('p9_part_mag_')),
                       ('P9_slide_ref', lambda n: n.startswith('p9_slide')),
                       ('P9_follower_ref', lambda n: n == 'p9_part_mag_follower'),
                       ('P9_mag_ref', lambda n: n.startswith('p9_part_mag_'))):
        c = bpy.data.collections.get(name) or bpy.data.collections.new(name)
        for o in bpy.data.collections['P9_ref'].objects:
            if pick(o.name) and o.name not in c.objects:
                c.objects.link(o)


def action_shots(clip, times=(0.0, 0.12, 0.24, 0.30, 0.50, 0.58, 0.62, 0.75, 0.92, 1.00, 1.08, 1.20, 1.30, 1.40, 1.50,
                              1.62, 2.30), w=960, h=540, side=False):
    """First-person frames of an action clip (renders/fp_h1p1/<clip>_<frame>.png; side=True adds a third-person
    side view): the gun without its magazine while the formal one is out, the new and the old magazine where the
    clip puts them."""
    import importlib.util
    ms = importlib.util.spec_from_file_location('rv', HERE + '/render_views.py')
    rv = importlib.util.module_from_spec(ms)
    ms.loader.exec_module(rv)
    r = RELOADS[clip]
    r.setup()
    _collections()
    sc = bpy.context.scene
    os.makedirs(OUT, exist_ok=True)
    mask = arm_mask()
    gun = bpy.data.objects.get('fp_gun')
    new = _instance('fp_mag_new', 'P9_mag_ref')
    old = _instance('fp_mag_old', 'P9_mag_ref')
    slide = _instance('fp_slide', 'P9_slide_ref')
    follower = _instance('fp_follower', 'P9_follower_ref')
    keep = {'survivor_body', 'fp_gun', 'fp_mag_new', 'fp_mag_old', 'fp_slide', 'fp_follower'}
    hidden = {ob.name: ob.hide_render for ob in sc.objects}
    ad = rig().animation_data
    action = ad.action if ad else None
    if ad:
        ad.action = None
    out = []
    try:
        for ob in sc.objects:
            ob.hide_render = ob.name not in keep and ob.type != 'CAMERA'
        for t in times:
            f = min(r.last, round(t * FPS))
            r.frame(f)
            c = r.cache[f]
            gun = bpy.data.objects['fp_gun']
            seated = bool(c['fr']['shown']['magazine'])
            gun.instance_collection = bpy.data.collections['P9_core_mag_ref' if seated else 'P9_core_ref']
            M = rig().matrix_world
            # on the gun's handling as the props track has them (the inspect's right side turns the gun off the formal)
            Gh = c['G'] @ c['fr']['parts']['handling'].inverted()
            slide.matrix_world = M @ Gh @ c['fr']['parts']['slide']
            follower.hide_render = not seated
            if seated:
                follower.matrix_world = M @ Gh @ c['fr']['parts']['follower']
            new.hide_render = not (c['W'] is not None and r.shown_new(c['t']))
            if c['W'] is not None:
                new.matrix_world = M @ c['W']
            old.hide_render = not c['fr']['shown']['empty_old_mag']
            old.matrix_world = M @ r.root(f) @ c['fr']['parts']['empty_old_mag']
            rv.setup(w)
            sc.render.resolution_x, sc.render.resolution_y = w, h
            sc.display.shading.background_type = 'WORLD'
            sc.world.color = (0.42, 0.47, 0.52)
            mask.show_render = True
            sc.camera = cam = fp_camera(r.p)
            # the view turns by the formal camera bone like the game's (MeshBodyFpTest, R3: the hand pass too):
            # pitch = -x looks down, yaw = -y turns right, roll = -z
            x, y, z = c['fr']['camera']
            cam.rotation_euler = (Matrix.Rotation(math.radians(y), 3, 'Z') @ cam.rotation_euler.to_matrix()
                                  @ Matrix.Rotation(math.radians(x), 3, 'X')
                                  @ Matrix.Rotation(math.radians(z), 3, 'Z')).to_euler('XYZ')
            sc.render.filepath = os.path.join(OUT, '%s_%03d.png' % (clip, f))
            bpy.ops.render.render(write_still=True)
            out.append(sc.render.filepath)
            if side:
                mask.show_render = False
                # rv.render unhides every name it is given, so pass only the magazines this frame shows
                out += rv.render(['survivor_body', 'fp_gun', 'fp_slide'] +
                                 [o.name for o in (new, old, follower) if not o.hide_render],
                                 [('side', (1, -0.35, 0.15), (0, 0, 1))], OUT, '%s_%03d' % (clip, f), size=720,
                                 focus=(0.05, -0.3, 1.2), span=1.1)
    finally:
        bpy.data.objects['fp_gun'].instance_collection = bpy.data.collections['P9_ref']
        for k, v in hidden.items():
            if k in sc.objects:
                sc.objects[k].hide_render = v
        new.hide_render = old.hide_render = slide.hide_render = follower.hide_render = True
        mask.show_render = False
        mask.show_viewport = False
        if ad:
            ad.action = action
    return out


register(TAC_EXP)
register(EMPTY_EXP, RackReload)
register(TAC_NOV)
register(EMPTY_NOV, RackReload)
register(TAC_NOV_FUMBLE)
register(EMPTY_NOV_FUMBLE, RackReload)
register(TAC_EXP_ADS)
register(EMPTY_EXP_ADS, StopReload)
register(TAC_PRAC)
register(EMPTY_PRAC, StopReload)
register(TAC_PRAC_ADS)
register(EMPTY_PRAC_ADS, StopReload)


# ---- the no-magazine reloads (formal reload_<tactical|empty>_no_mag[_<tier>][_fumble][_ads], 12 clips): each formal
# clip is its magazine-fitted one without the release and the old magazine falling, frame for frame 6 frames (0.1 s)
# earlier from f12..f41 on (well before the formal magazine path; scratch cmp in the H1-P2 doc section 18). So each body
# clip is its PASS fitted one with no thumb press, every beat from join0 on 0.1 s earlier, and the hand's way from the
# grip to the formal path (release..join0) squeezed into the time left: the novice one just starts 0.1 s earlier, the
# experienced and practised ones leave the grip as early as the practised one does (0.02 s) and go a little faster.
NO_MAG_SHIFT = 0.10


def no_mag(s, kind=Reload):
    clip = s['clip'].replace('reload_tactical', 'reload_tactical_no_mag', 1).replace('reload_empty', 'reload_empty_no_mag', 1)
    r0, j0 = s['release'], s['join0']
    r1 = max(r0 - NO_MAG_SHIFT, 0.02)

    def at(t):
        if t >= j0:
            return round(t - NO_MAG_SHIFT, 4)
        return round(r1 + (t - r0) * (j0 - NO_MAG_SHIFT - r1) / (j0 - r0), 4) if t > r0 else r1
    out = {k: v for k, v in s.items() if k != 'press'}
    out['clip'] = clip
    for k in ('release', 'away', 'pouch', 'grab', 'out', 'join0', 'join1', 'flip0', 'flip1', 'seat', 'leave', 'regrip',
              'slide0', 'close', 'let'):
        if k in s:
            out[k] = at(s[k])
    if 'stop' in s:
        out['stop'] = tuple(at(t) for t in s['stop'])
    if 'slip' in s:
        out['slip'] = tuple(at(t) for t in s['slip'][:4]) + (s['slip'][4],)
    if 'lift_env' in s:
        out['lift_env'] = tuple(at(t) for t in s['lift_env'])
    return register(out, kind)


for _s, _kind in ((TAC_EXP, Reload), (EMPTY_EXP, RackReload), (TAC_NOV, Reload), (EMPTY_NOV, RackReload),
                  (TAC_NOV_FUMBLE, Reload), (EMPTY_NOV_FUMBLE, RackReload), (TAC_EXP_ADS, Reload),
                  (EMPTY_EXP_ADS, StopReload), (TAC_PRAC, Reload), (EMPTY_PRAC, StopReload),
                  (TAC_PRAC_ADS, Reload), (EMPTY_PRAC_ADS, StopReload)):
    no_mag(_s, _kind)


# ---- draw / put_away / first_draw (formal 0.5 / 0.5 / 1.2 s; H1-P2 doc section 19). The formal gun comes up from
# 214 mm below the hip stance, turned 33 deg (0.05..0.25, 9 mm past at 0.25, settled 0.30); put_away is that backwards
# after a 9 mm bump at 0.05 (down 0.10..0.45); the first draw comes up the same way, turns the gun 46 deg and lifts it
# 6 cm toward the support hand (0.25..0.75), racks the slide 37 mm (0.45..0.57, sound 0.45, released 0.615) and is home
# by 1.0. The formal gun starts out of the holster already (the thigh holster is off screen and the third person keeps
# its skeleton draw), so the body brings the gun up with the shooting hand on the grip and the support hand from LOW -
# in front of the belly, off screen, where put_away leaves it - onto the grip (draw), off the grip down to LOW
# (put_away; the formal support hand lets go first), or up the gun's left side onto the slide, the R4 overhand rack on
# the formal slide and round to the grip (first_draw). LOW: the support hand target off the hip stance's support grip,
# in the gun's axes (m: gun-right, muzzle, up). Seconds: rise off LOW, join0..join1 onto the grip (draw); release,
# away, pouch = at LOW (put_away, the reloads' peel and way down); rise off LOW, leave beside the gun .. slide0 on the
# slide, close, let, regrip (first_draw).
LOW = (-0.03, -0.20, -0.25)
# scratch diag: the draw's hand on the grip by 0.28 swept the shooting fingers 4 mm while the formal gun goes 9 mm past
# (0.25), by 0.30 1.8 mm one frame; the first draw's hand beside the slide by 0.28 went 4.4 m/s, by 0.32 3.7
DRAW = dict(clip='draw', lift=(0.0, 0.0, 0.0), rise=0.06, join0=0.22, join1=0.30, lift_via=(0.05, 0.08, -0.08),
            down_via=(0.0, 0.0, 0.0))
PUT_AWAY = dict(clip='put_away', lift=(0.0, 0.0, 0.0), release=0.02, away=0.08, pouch=0.32, down_via=(0.03, 0.03, 0.0),
                lift_via=(0.0, 0.0, 0.0))
FIRST_DRAW = dict(clip='first_draw', lift=(0.0, 0.0, 0.0), rise=0.06, leave=0.32, slide0=0.40, close=0.44,
                  let=0.615, regrip=0.98, lift_via=(0.06, 0.06, -0.06), down_via=(0.0, 0.0, 0.0), settle=(1.10, 1.20))


class Draw(Reload):
    """draw / put_away: the shooting hand on the grip throughout, the support hand between LOW and the grip."""

    def setup(self):
        s = self.s
        self.p = spec('hip')
        pose(self.p)
        self.G_hip = gun_matrix(self.p)
        self.gfp = grip(POSE_FP)
        self.e_grip = dict(self.gfp['l'][1])
        self.e_thumb = {k: v for k, v in self.gfp['r'][1].items() if k.startswith('thumb')}
        self.press_e = self.stop_e = {}
        Rg = rigid(self.G_hip)
        self.A_grip = Rg.inverted() @ hand_targets(self.G_hip, self.gfp)['l'][0]
        self.A_away = Matrix.Translation(AWAY) @ self.A_grip
        self.H_low = Rg @ Matrix.Translation(LOW) @ self.A_grip
        self.cam_max = 1.0
        self._ends = None
        self.t_away, self.tau_a = s.get('away', 0.0), 0.0
        if 'pouch' in s and not self.late_peel:
            self.peel()
        self.ready = True

    late_peel = False                                   # Inspect peels once its gun path is set up

    def spec_at(self, t):
        return self.p

    def ends(self):
        if self._ends:
            return self._ends
        s = self.s

        def at(t, H, e):
            pose(self.spec_at(t), G=self.G(round(t * FPS)), left=(H, e))
            return rig().pose.bones['hand_l'].head.copy(), wrist_raw('l')
        out = {}
        if 'pouch' in s:
            H_away = rigid(self.G(round(self.t_away * FPS))) @ self.A_away
            out['down'] = (at(self.t_away, H_away, RELAXED), at(s['pouch'], self.H_low, RELAXED))
        if 'join1' in s:
            fj = round(s['join1'] * FPS)
            out['lift'] = (at(s['rise'], self.H_low, RELAXED), at(s['join1'], rigid(self.G(fj)) @ self.A_grip, self.e_grip))
        self._ends = out
        return out

    def plan(self, f):
        s, t = self.s, f / FPS
        G = self.G(f)
        Rg = rigid(G)
        H_grip = Rg @ self.A_grip
        out = dict(t=t, G=G, fr=self.fr(f), left=None, e=None, W=self.W_formal(f), free=False, right=None)
        if 'join1' in s:                                # draw: LOW, then up onto the grip as the gun gets there
            if t <= s['rise']:
                out.update(left=self.H_low, e=RELAXED)
            elif t < s['join1']:
                u = ramp(t, s['rise'], s['join1'])
                e = blend_e(blend_e(RELAXED, REACH, smooth(u / 0.5)), self.e_grip, smooth((u - 0.6) / 0.4))
                out.update(wrist=('lift', u), e=e, to=(H_grip, ramp(t, s['join0'], s['join1'])))
            else:
                out.update(left=H_grip, e=self.e_grip)
        elif t <= s['release']:                         # put_away: off the grip and down to LOW in one move (peel())
            out.update(left=H_grip, e=self.e_grip)
        elif t < s['pouch']:
            tau, a = ramp(t, s['release'], s['pouch']), self.tau_a
            if tau < a:
                u = tau / a
                out.update(left=Rg @ lerp_m(self.A_grip, self.A_away, u), e=blend_e(self.e_grip, RELAXED, peel_fingers(u)))
            else:
                out.update(wrist=('down', (tau - a) / (1 - a)), e=RELAXED)
        else:
            out.update(left=self.H_low, e=RELAXED)
        return out

    def shown_new(self, t):
        return 0


class FirstDraw(RackReload):
    """first_draw: from LOW up the gun's left side to just above the slide while it comes up (armature arc, the
    wrist angles straight from LOW's to the slide grip's, as the reloads' way up), then, with the gun turned and still,
    the empty reloads' overhand rack: down onto the slide (forward here, pulled back off the round), the formal slide,
    round to the grip. Riding on the gun from LOW (R4's way) turned the wrist 85 deg in two frames while the formal gun
    turns 40 deg at 0.20..0.25 and swept the shooting hand 6 mm; R4's key beside the gun (set for its gun rolled 80
    deg) put this wrist on its extension and ulnar limits."""
    extra_parts = ('slide',)
    rack_from = SLIDE_OPEN
    rack_keys = ()
    ABOVE = (-0.025, 0.0, 0.03)                         # RACK_KEYS' last key: off the slide grip, gun axes (m)

    def setup(self):
        self._rack = None
        Draw.setup(self)
        self.Ks, self.es = grips()['slide']

    def spec_at(self, t):
        s = self.s
        u = ramp(t, s['rise'], s['slide0']) * (1 - ramp(t, s['let'], s['regrip']))
        if u <= 0:
            return self.p
        p = dict(self.p)
        for k, v in ARM_SLIDE.items():
            r0, l0 = p[k]
            p[k] = (r0, l0 + (v - l0) * u)
        return p

    def G(self, f):
        """The formal gun, its last 0.4 deg still settling at 1.1 s (0.1 at the end) taken out over settle, so the clip
        ends on the hip pose (the seam check)."""
        D = self.fr(f)['parts']['handling']
        return self.root(f) @ lerp_m(D, Matrix.Identity(4), ramp(f / FPS, *self.s['settle']))

    def rack_start(self):
        """Just above the slide grip at leave, open."""
        f = round(self.s['leave'] * FPS)
        return Matrix.Translation(rigid(self.G(f)).to_3x3() @ Vector(self.ABOVE)) @ rigid(self.W_slide(f)) @ self.Ks, SLIDE_OPEN

    def ends(self):
        if self._ends:
            return self._ends
        s = self.s

        def at(t, H, e):
            pose(self.spec_at(t), G=self.G(round(t * FPS)), left=(H, e))
            return rig().pose.bones['hand_l'].head.copy(), wrist_raw('l')
        self._ends = dict(lift=(at(s['rise'], self.H_low, RELAXED), at(s['leave'], *self.rack_start())))
        return self._ends

    def plan(self, f):
        s, t = self.s, f / FPS
        G = self.G(f)
        out = dict(t=t, G=G, fr=self.fr(f), left=None, e=None, free=False, right=None)
        if t <= s['rise']:
            out.update(left=self.H_low, e=RELAXED)
        elif t < s['leave']:                            # up beside the gun with it
            u = ramp(t, s['rise'], s['leave'])
            out.update(wrist=('lift', u), e=blend_e(RELAXED, SLIDE_OPEN, u))
        elif t < s['regrip']:
            out = RackReload.plan(self, f)
        else:
            out.update(left=rigid(G) @ self.A_grip, e=self.e_grip)
        out['W'] = self.W_formal(f)
        return out

    def shown_new(self, t):
        return 0


register(DRAW, Draw)
register(PUT_AWAY, Draw)
register(FIRST_DRAW, FirstDraw)


# ---- inspect (formal inspect / inspect_empty 11.1 s, inspect_no_mag / inspect_empty_no_mag 6.75 s; H1-P2 doc section
# 20). The formal gun shows its left side (1.05..2.0), turns to the right side (2.0..3.05, held to 3.8: muzzle 62 deg to
# the right with a bent wrist, which the block arm could do and a real wrist cannot: extension and twist at their
# limits, the hand 16 deg off the grip), turns the well to the support hand (3.8..4.5), the magazine comes out
# (4.79 / 4.85), is shown at the screen's left and goes back in (seated 9.445); the gun is home by 10.5. User
# 2026-10-24 (the COD MW X16 inspect): the right side as COD shows it - the forearm turned in, the back of the hand up,
# the muzzle to the left, the right side toward the eye, the support hand under the front of the frame. So the body's
# gun is the formal one except 2.0..4.6, where it goes to RS and back to the formal well-to-hand pose; while it is
# there the formal sway rides on it.
# Round 3 (2026-10-24, user on the beat sheet: the hands twisted at the joints and the fingers, not a move a person can
# make - research first, the recordings are reference only): every key held to real joint ranges, measured
# geometrically (scratch anat_lib.py: forearm rotation from full supination - neutral about 90, full pronation about
# 170 -, wrist flexion / extension and radial / ulnar deviation of the hand's axis against the forearm's; the rig's YXZ
# wrist Euler hides the deviation under large flexion) against the ranges most daily tasks stay inside (Ryu et al.
# 1991: about 40 flexion / extension, 40 radial + ulnar together, radial at most about 20; the forearm 25..155 for a
# held, looked-at hand). The old keys sat 23-45 deg radial at the well, 30 ulnar with 35 flexion and 43 extension in
# view, the right wrist 42 radial on RS; the fingers went straight in the change of grip, and the fist's thumb was
# twisted 40 deg at its base (about 35 at most).
# RS: the formal 3.4 s gun turned about the shooting hand's grip 105 deg about the vertical, -45 about the body's x,
# -135 about the muzzle, then 18 cm to the right, the shooting elbow out 28 deg and its shoulder 9 deg forward
# (scratch rs_anat2_bg.py: forearm 156 - turned in, the back of the hand up -, wrist 19 extension, 4 ulnar; was 161 and
# 42 radial; the right side faces the eye 0.54, was 0.53).
# The support hand lets go and goes down to LOW, comes up under the front of the frame (cup, gun-relative) for the
# right side, slides to under the base plate (wait), takes the magazine on the palm as the release is pressed and rides
# it out on the formal path: the palm turned 50 deg about the magazine's axis and the shoulder lowered (scratch
# well2_bg.py: wrist 27-36 extension, deviation under 4, the forearm turned out, palm up, 25-28). Clear of the gun the
# hand rolls round the upright magazine from under it to its left side, the fingers curling round the front as the palm
# comes round (never straighter than the push), into the fist (the pinch grip, user 2026-10-24: a closed fist, the top
# 35 mm out past the index, the palm on the left broad side). The magazine check (refs MW2 X12 / .50 GS and Tarkov
# P226R magazine checks, the motion AFL's own) keeps the wrist straight: the top round and the spine toward the eye
# (forearm 104, flexion and deviation under 1), then the forearm turns 48 deg in for the left side (wrist 17 extension,
# 24 ulnar; scratch view_search_bg.py); each hold drifts a little. Back: the reverse roll onto the palm, onto the formal
# path, the push home, the reloads' way to the grip.
RS_TURN = (105.0, -45.0, -135.0)                    # deg: about armature z, armature x, the muzzle
RS_MOVE = (-0.18, 0.0, 0.0)                         # m, armature (+x the body's left, +y back)
RS_ARM = dict(swivel=30.0, protract=15.0)           # the shooting arm on RS (deg)
RS_SWAY = 0.5                                       # share of the formal sway kept on RS (the whole of it bends the wrist to 1.0)
WELL_PHI = -50.0                                    # the push at the well turned about the magazine's axis (deg)
ARM_WELL = dict(swivel=0.0, elevate=-10.0, protract=0.0)    # the support arm at the well (deg)
# the magazine in view, placed in the formal camera of SHOW_CAM s: its middle at screen x, y (of the half sizes), the
# distance from the eye (m), its roll about the view ray, the top tipped toward the eye, turned about the screen's up
# (deg); the support arm there; the change over the hold
SHOW_CAM = 6.5
SHOW = (((-0.32, 0.1, 0.37, -15.0, 40.0, -20.0), dict(swivel=0.0, elevate=20.0, protract=15.0), (0, 0, -0.015, 0, 6, 0)),
        ((-0.32, 0.1, 0.44, -30.0, 40.0, 20.0), dict(swivel=0.0, elevate=-10.0, protract=30.0), (0, 0, -0.01, 0, 0, -6)))
# the change of grip in the hand, under the base plate <-> fist: the magazine's middle along an arc through this point
# (hand local m off the straight middle), the fingers at the push's curl until ROLL_CLOSE of the way, then closing;
# scratch roll_search_bg.py (81 pairs): the skin at most 0.99 mm into the magazine over both rolls (was 4.2 mm)
TIP_VIA, ROLL_CLOSE = (-0.01, 0.0, 0.012), 0.7
# the shooting arm while the support hand shows the magazine (absolute; the stance's otherwise): the gun held one-
# handed on the stance's elbow put the wrist in about 38 deg ulnar for 3 s; the shoulder a little back and up, the
# elbow out: at most 26 ulnar, the elbow 55 mm off the stance's (scratch show_arm_search_bg.py, 240 triples; less
# ulnar only with a 20-30 deg shrug)
SHOW_ARM_R = dict(swivel=10.0, elevate=10.0, protract=-10.0)
# the cup: wrist point off the lowest verts of the RS gun's front half (armature m) and wrist angles (deg) - scratch
# insp_cup2_bg.py: 31 skin verts on the gun's down side, none in the shooting hand, fingers 58 mm above the hand's middle, strain 0.27
CUP_AT = ((0.09, 0.06, -0.075), (-16.0, 0.0, -12.0))
INSPECT = dict(clip='inspect', lift=(0.0, 0.0, 0.0), release=0.05, away=0.14, pouch=0.60, down_via=(0.03, 0.03, 0.0),
               rs=(2.0, 3.05, 3.8, 4.6), rs_at=3.4, rise=2.45, cup0=2.90, cup1=3.05, wait=4.45, grab=4.72,
               free=5.20, keys=(6.0, 6.75, 7.25, 7.95), join0=8.80, join1=9.02,
               base_via=(0.0, 0.0, -0.04), show_via=(0.03, 0.0, -0.04), back_via=(0.0, 0.0, -0.08),
               seat=9.445, leave=9.52, regrip=10.30, press=(4.68, 4.76, 4.86, 4.95),
               lift_via=(0.0, 0.0, 0.0))


def gun_front_low(G):
    """Armature point: the mean of the P9's verts within 1 cm of the lowest in its front half, the gun placed by G."""
    gv = getattr(builtins, '_p9_verts', None)
    if gv is None:
        gv = builtins._p9_verts = np.array([tuple(o.matrix_world @ v.co) for o in bpy.data.collections['P9_ref'].objects
                                            if o.type == 'MESH' for v in o.data.vertices])
    A = np.array(G)
    fa = gv[gv[:, 1] > np.median(gv[:, 1])] @ A[:3, :3].T + A[:3, 3]
    return Vector(tuple(fa[fa[:, 2] < fa[:, 2].min() + 0.01].mean(0)))


def mag_turn(deg):
    """K pre-factor for a magazine grip: the hand carried deg round the magazine's long axis (through its section's
    middle)."""
    c = Vector((0.0, -0.45 / (PX * 1000), 0.0))
    R = MAG_M0 @ Matrix.Translation(c) @ Matrix.Rotation(math.radians(deg), 4, 'Z') @ Matrix.Translation(-c) @ MAG_M0.inverted()
    S = Matrix.Scale(PX, 4)
    return S @ R @ S.inverted()


def key_blend(keys, t):
    """Values (dicts) at t through (time, dict) keys: held outside them, smoothstep between neighbours."""
    if t <= keys[0][0]:
        return keys[0][1]
    for (ta, va), (tb, vb) in zip(keys, keys[1:]):
        if t <= tb:
            u = ramp(t, ta, tb) if tb > ta else 1.0
            return {n: va[n] + (vb[n] - va[n]) * u for n in va}
    return keys[-1][1]


class Inspect(Draw):
    """inspect / inspect_empty (and, with no_mag, the two without a magazine): see above."""

    def setup(self):
        Draw.setup(self)
        s = self.s
        g = grips()
        self.Kc, self.ec = g['pinch']                  # shown in the fist (user 2026-10-24), not the reloads' carry
        Kp, self.ep = g['push']
        self.Kp = mag_turn(WELL_PHI) @ Kp                # the push, turned at the well
        self.press_e = g['press']
        self.e_cup = blend_e(self.e_grip, RELAXED, 0.5)
        self.A_wait = None
        f = round(s['rs_at'] * FPS)
        # the empty clips turn the right side 25 deg further (the block arm again); RS is one pose for all four
        D_base = formal(s.get('rs_from', s['clip']))['frames'][f]['parts']['handling']
        G0 = self.root(f) @ D_base
        pose(self.p, G=G0, left=(self.H_low, RELAXED))
        piv = rig().pose.bones['hand_r'].head.copy()
        ax = rigid(G0).to_3x3().col[1]
        yz, xx, mz = (math.radians(a) for a in RS_TURN)
        C = (Matrix.Rotation(yz, 3, 'Z') @ Matrix.Rotation(xx, 3, 'X') @ Matrix.Rotation(mz, 3, ax)).to_4x4()
        M = Matrix.Translation(piv + Vector(RS_MOVE)) @ C @ Matrix.Translation(-piv) @ G0
        self.D_rs = self.G_hip.inverted() @ M
        self.D_ref = self.fr(f)['parts']['handling']
        Grs = self.G(f)
        P = gun_front_low(Grs) + Vector(CUP_AT[0])
        pose(self.spec_at(s['rs_at']), G=Grs, left=(Matrix.Translation(P), RELAXED))
        hb = rig().pose.bones['hand_l']
        rest = hb.parent.bone.matrix_local.inverted() @ hb.bone.matrix_local
        R = (hb.parent.matrix @ rest).to_3x3().normalized() @             Euler(tuple(math.radians(a) for a in CUP_AT[1]), 'YXZ').to_matrix()
        self.A_cup = rigid(Grs).inverted() @ Matrix.Translation(P) @ R.to_4x4()
        if not s.get('no_mag'):
            self.show_keys = self.show_places()
            fw = round(s['wait'] * FPS)
            Rg = rigid(self.G(fw))
            # under the base plate, 2 cm down its axis, open
            self.A_wait = Rg.inverted() @ rigid(self.root(fw) @ self.fr(fw)['parts']['magazine']) @ \
                Matrix.Translation(Vector((0, -math.sin(MAG_TILT), -math.cos(MAG_TILT))) * 0.02) @ self.Kp
        self._ends = None
        self.peel()

    late_peel = True

    def spec_at(self, t):
        """The stance's spec with the shooting arm toward RS_ARM on RS, and the support arm through its keys: the stance,
        ARM_WELL at the well (out), each SHOW view, ARM_WELL (back in, seated), the stance on the grip."""
        s = self.s
        p = dict(self.p)
        a0, a1, b0, b1 = s['rs']
        w = ramp(t, a0, a1) * (1 - ramp(t, b0, b1))
        for k, v in RS_ARM.items():
            r0, l0 = side(p.get(k, 0.0), 'r'), side(p.get(k, 0.0), 'l')
            p[k] = (r0 + (v - r0) * w, l0)
        if s.get('no_mag') or getattr(self, 'show_keys', None) is None:     # (setup poses before the keys are made)
            return p
        k = s['keys']
        w = ramp(t, s['free'], k[0]) * (1 - ramp(t, k[3], s['join0']))      # the gun one-handed while the views
        for n, v in SHOW_ARM_R.items():
            r0, l0 = side(p.get(n, 0.0), 'r'), side(p.get(n, 0.0), 'l')
            p[n] = (r0 + (v - r0) * w, l0)
        st = {k: side(self.p.get(k, 0.0), 'l') for k in ARM_WELL}
        v1, v2 = self.show_keys[0][1], self.show_keys[2][1]
        left = key_blend(((s['rs'][2], st), (s['wait'], ARM_WELL), (s['free'], ARM_WELL), (k[0], v1), (k[1], v1),
                          (k[2], v2), (k[3], v2), (s['join0'], ARM_WELL), (s['leave'], ARM_WELL), (s['regrip'], st)), t)
        for n, v in left.items():
            p[n] = (side(p.get(n, 0.0), 'r'), v)
        return p

    def show_places(self):
        """The SHOW keys as (magazine placement gun px -> armature, support arm): each view, then it drifted."""
        f = round(SHOW_CAM * FPS)
        pose(self.p, G=self.G(f), left=(self.H_low, RELAXED))
        x, y, z = self.fr(f)['camera']
        Rc = (Matrix.Rotation(math.radians(y), 3, 'Z') @ Euler((math.pi / 2, 0, math.pi), 'XYZ').to_matrix()
              @ Matrix.Rotation(math.radians(x), 3, 'X') @ Matrix.Rotation(math.radians(z), 3, 'Z'))
        back, fov = self.p.get('view', (0.0, 42.0))
        C = eye() + Vector((0, back, 0))
        th = math.tan(math.radians(fov) / 2)
        Mc = MAG_M0 @ Matrix.Translation(Vector((0, -0.45, 44.0)) / (PX * 1000))
        Mc_m = Matrix.Translation(Mc.translation * PX) @ Mc.to_3x3().to_4x4()      # its middle, the offset in metres

        def place(sx, sy, d, psi, beta, gam):
            ray = (-Rc.col[2] + Rc.col[0] * sx * th * 16 / 9 + Rc.col[1] * sy * th).normalized()
            up = (Rc.col[1] - Rc.col[1].dot(ray) * ray).normalized()
            rt = up.cross(-ray)
            B = Matrix((rt, ray, up)).transposed()      # upright, the spine (-y) toward the eye
            R = (Matrix.Rotation(math.radians(gam), 3, up) @ Matrix.Rotation(math.radians(beta), 3, rt)
                 @ Matrix.Rotation(math.radians(psi), 3, -ray) @ B)
            return scaled(Matrix.Translation(C + ray * d) @ R.to_4x4() @ Mc_m.inverted())
        out = []
        for prm, arm, drift in SHOW:
            out += [(place(*prm), arm), (place(*(a + b for a, b in zip(prm, drift))), arm)]
        return out

    def D(self, f):
        """The gun's handling: formal, except the right side (rs = a0, a1, b0, b1)."""
        a0, a1, b0, b1 = self.s['rs']
        t = f / FPS
        Df = self.fr(f)['parts']['handling']
        if t <= a0 or t >= b1:
            return Df
        sway = lambda g: self.D_rs @ lerp_m(Matrix.Identity(4), self.D_ref.inverted() @ self.fr(g)['parts']['handling'], RS_SWAY)
        if t < a1:
            return lerp_m(self.fr(round(a0 * FPS))['parts']['handling'], sway(round(a1 * FPS)), ramp(t, a0, a1))
        if t <= b0:
            return sway(f)
        return lerp_m(sway(round(b0 * FPS)), self.fr(round(b1 * FPS))['parts']['handling'], ramp(t, b0, b1))

    def G(self, f):
        return self.root(f) @ self.D(f)

    def ends(self):
        if self._ends:
            return self._ends
        s = self.s

        def at(t, H, e):
            pose(self.spec_at(t), G=self.G(round(t * FPS)), left=(H, e))
            return rig().pose.bones['hand_l'].head.copy(), wrist_raw('l')
        out = {}
        H_away = rigid(self.G(round(self.t_away * FPS))) @ self.A_away
        out['down'] = (at(self.t_away, H_away, RELAXED), at(s['pouch'], self.H_low, RELAXED))
        fc = round(s['cup1'] * FPS)
        cup = at(s['cup1'], rigid(self.G(fc)) @ self.A_cup, self.e_cup)
        out['lift'] = (at(s['rise'], self.H_low, RELAXED), cup)
        if s.get('no_mag'):
            fb = round(s['rs'][2] * FPS)
            out['down2'] = (at(s['rs'][2], rigid(self.G(fb)) @ self.A_cup, self.e_cup), at(s['low2'], self.H_low, RELAXED))
            fj = round(s['join0'] * FPS)              # to just off the grip (put_away's peel, backwards)
            out['lift2'] = (at(s['rise2'], self.H_low, RELAXED), at(s['join0'], rigid(self.G(fj)) @ self.A_away, RELAXED))
        else:
            fb, fw = round(s['rs'][2] * FPS), round(s['wait'] * FPS)
            out['base'] = (at(s['rs'][2], rigid(self.G(fb)) @ self.A_cup, self.e_cup),
                           at(s['wait'], rigid(self.G(fw)) @ self.A_wait, REACH))
            ff, fj = round(s['free'] * FPS), round(s['join1'] * FPS)
            pts = [at(s['free'], rigid(self.W_formal(ff)) @ self.Kp, PUSH)]
            pts += [at(t, rigid(W) @ self.Kc, self.ec) for (W, _), t in zip(self.show_keys, s['keys'])]
            pts.append(at(s['join1'], rigid(self.W_formal(fj)) @ self.Kp, PUSH))
            out.update({n: (a, b) for n, a, b in zip(('show0', 'hold1', 'show1', 'hold2', 'back'), pts, pts[1:])})
        self._ends = out
        return out

    def roll(self, u):
        """The grip (K) and fingers u of the way from the palm under the base plate to the fist: the magazine's middle
        along an arc in the hand, the fingers at the push's curl until ROLL_CLOSE, then closing round it (round 2 opened
        them straight first)."""
        C = Matrix.Translation((MAG_M0 @ Vector((0.0, 0.0, 45.0 / (PX * 1000.0)))) * PX)
        Qp, Qc = self.Kp.inverted() @ C, self.Kc.inverted() @ C
        via = (Qp.translation + Qc.translation) / 2 + Vector(TIP_VIA)
        e = blend_e(PUSH, self.ec, smooth((u - ROLL_CLOSE) / (1 - ROLL_CLOSE)))
        return (lerp_m(Qp, Qc, u, via) @ C.inverted()).inverted(), e

    def wrist_hand(self, spec_t, G, e, seg, u):
        (P0, e0), (P1, e1) = self.ends()[seg]
        via = (P0 + P1) / 2 + Vector({'down': self.s['down_via'], 'base': self.s.get('base_via', (0, 0, 0)),
                                      'lift2': self.s.get('lift2_via', (0, 0, 0)), 'show0': self.s.get('show_via', (0, 0, 0)),
                                      'back': self.s.get('back_via', (0, 0, 0))}.get(seg, (0, 0, 0)))
        pos = arc(P0, P1, via, u)
        pose(spec_t, G=G, left=(Matrix.Translation(pos), e))
        hb = rig().pose.bones['hand_l']
        rest = hb.parent.bone.matrix_local.inverted() @ hb.bone.matrix_local
        ang = Euler(tuple(a + (b - a) * u for a, b in zip(e0, e1)), 'YXZ')
        R = (hb.parent.matrix @ rest).to_3x3().normalized() @ ang.to_matrix()
        return Matrix.Translation(pos) @ R.to_4x4()

    def plan(self, f):
        s, t = self.s, f / FPS
        G = self.G(f)
        Rg = rigid(G)
        out = dict(t=t, G=G, fr=self.fr(f), left=None, e=None, W=self.W_formal(f), free=False, right=self.press(t))
        if t <= s['release']:
            out.update(left=Rg @ self.A_grip, e=self.e_grip)
        elif t < s['pouch']:                            # off the grip and down to LOW (put_away's way)
            tau, a = ramp(t, s['release'], s['pouch']), self.tau_a
            if tau < a:
                u = tau / a
                out.update(left=Rg @ lerp_m(self.A_grip, self.A_away, u), e=blend_e(self.e_grip, RELAXED, peel_fingers(u)))
            else:
                out.update(wrist=('down', (tau - a) / (1 - a)), e=RELAXED)
        elif t <= s['rise']:
            out.update(left=self.H_low, e=RELAXED)
        elif t < s['cup1']:                             # up under the front of the frame
            u = ramp(t, s['rise'], s['cup1'])
            out.update(wrist=('lift', u), e=blend_e(RELAXED, self.e_cup, u),
                       to=(Rg @ self.A_cup, ramp(t, s['cup0'], s['cup1'])))
        elif t <= s['rs'][2]:
            out.update(left=Rg @ self.A_cup, e=self.e_cup)
        elif s.get('no_mag'):
            if t < s['low2']:
                out.update(wrist=('down2', ramp(t, s['rs'][2], s['low2'])), e=blend_e(self.e_cup, RELAXED, ramp(t, s['rs'][2], s['low2'])))
            elif t <= s['rise2']:
                out.update(left=self.H_low, e=RELAXED)
            elif t < s['join0']:
                u = ramp(t, s['rise2'], s['join0'])
                out.update(wrist=('lift2', u), e=RELAXED, to=(Rg @ self.A_away, smooth((u - 0.7) / 0.3)))
            elif t < s['join1']:
                u = ramp(t, s['join0'], s['join1'])
                out.update(left=Rg @ lerp_m(self.A_away, self.A_grip, u), e=blend_e(self.e_grip, RELAXED, peel_fingers(1 - u)))
            else:
                out.update(left=Rg @ self.A_grip, e=self.e_grip)
        elif t < s['wait']:                             # down off the frame and round to under the base plate, opening
            u = ramp(t, s['rs'][2], s['wait'])
            out.update(wrist=('base', u), e=blend_e(self.e_cup, REACH, u), to=(Rg @ self.A_wait, smooth((u - 0.6) / 0.4)))
        elif t < s['grab']:                             # up onto the base plate, the palm under it (the reloads' push)
            u = ramp(t, s['wait'], s['grab'])
            A_mag = Rg.inverted() @ rigid(self.root(f) @ self.fr(f)['parts']['magazine']) @ self.Kp
            out.update(left=Rg @ lerp_m(self.A_wait, A_mag, u), e=blend_e(REACH, PUSH, u))
        elif t < s['free']:                             # released and drawn out on the formal path on the palm
            out.update(left=rigid(self.W_formal(f)) @ self.Kp, e=PUSH)
        elif t < s['join1']:                            # shown at the screen's left, then back to the well
            k = s['keys']
            seg, a, b = next(x for x in (('show0', s['free'], k[0]), ('hold1', k[0], k[1]), ('show1', k[1], k[2]),
                                         ('hold2', k[2], k[3]), ('back', k[3], s['join1'])) if t < x[2])
            u = ramp(t, a, b)
            out.update(wrist=(seg, u), e=self.ec, free=True)
            if seg == 'show0':                          # clear of the gun the hand rolls round it into the fist
                out.update(zip(('K', 'e'), self.roll(smooth(u / 0.85))))
            if seg == 'back':                           # the reverse roll onto the palm, then onto the formal path
                out.update(zip(('K', 'e'), self.roll(1 - smooth(u / 0.6))),
                           to=(rigid(self.W_formal(f)) @ self.Kp, ramp(t, s['join0'], s['join1'])))
        elif t < s['seat']:                             # in: pushed home from under the base plate
            W = self.W_formal(f)
            out.update(left=rigid(W) @ self.Kp, e=PUSH, W=W)
        elif t < s['leave']:
            W = self.root(f) @ self.fr(f)['parts']['magazine']
            out.update(left=rigid(W) @ self.Kp, e=PUSH, W=W)
        elif t < s['regrip']:                           # off the base plate onto the grip (the reloads' way)
            u = ramp(t, s['leave'], s['regrip'])
            A_seat = Rg.inverted() @ rigid(self.root(f) @ self.fr(f)['parts']['magazine']) @ self.Kp
            via = (A_seat.translation + self.A_grip.translation) / 2 + Vector(REGRIP_VIA)
            mid = 0.55
            e = blend_e(blend_e(PUSH, RELAXED, smooth(u / mid)), self.e_grip, smooth((u - mid) / (1 - mid)))
            out.update(left=Rg @ lerp_m(A_seat, self.A_grip, u, via), e=e)
        else:
            out.update(left=Rg @ self.A_grip, e=self.e_grip)
        return out

    def shown_new(self, t):
        f = min(self.last, round(t * FPS))
        return 0 if self.s.get('no_mag') else int(not self.fr(f)['shown']['magazine'])


# the same beats without the magazine (inspect_no_mag / inspect_empty_no_mag, 6.75 s): the empty well shown 4.5..5.5,
# the gun home by 6.25; the support hand leaves the cup for LOW and comes back to the grip with the gun
INSPECT_NO_MAG = dict({k: v for k, v in INSPECT.items() if k != 'press'}, clip='inspect_no_mag', no_mag=True,
                      low2=4.35, rise2=5.45, join0=6.08, join1=6.25, lift2_via=(0.05, 0.08, -0.08))


class InspectEmpty(Inspect):
    """inspect_empty / inspect_empty_no_mag: the slide locked back on the gun."""
    extra_parts = ('slide',)


register(INSPECT, Inspect)
register(dict(INSPECT, clip='inspect_empty', rs_from='inspect'), InspectEmpty)
register(INSPECT_NO_MAG, Inspect)
register(dict(INSPECT_NO_MAG, clip='inspect_empty_no_mag', rs_from='inspect'), InspectEmpty)


# ---- unload (formal unload / unload_empty 1.45 s; magazine_system_v1.md 3.1, H1-P2 doc section 21): the formal gun
# lifts about 12 cm and cants the well 51 deg toward the support hand (0.05..0.30), the thumb presses the release
# (sound 0.35), the magazine drops out along the well (0.40..0.55, clear of it by 0.55), the gun comes back to the hip
# one-handed (0.62..1.0, still from there). The support hand leaves the grip and goes, gun-relative, round under the
# base plate (open, the inspect's well grip: the palm turned WELL_PHI about the magazine's axis), takes it on the palm
# as it is released, rides it out on the formal path until it is clear of the well, then carries it down and in to the
# rig at the belly (off screen) while closing round it into the fist (the inspect's roll). The game stows it at 0.90
# (user 2026-10-24: rig, pockets, backpack, else the ground; the formal reload_magazine hides then); the hand lets go
# (to let), then comes back up onto the grip on an arc (the draw's way up, back..regrip, docking from join0).
# Seconds: release off the grip, wait under the base plate, grab on it, free clear of the well, low at the rig; low_at:
# the end off LOW (hip gun axes, m); low_e: the wrist there (deg, the hand bone's YXZ Euler); lift_via: the way up's
# halfway point off the straight midpoint (m). Scratch unload_search2_bg.py: under the base plate by 0.26 the hand
# turned with the gun still rolling into the formal lift (to 0.30) and the forearm went to 13 (near full supination);
# under it once the gun has stopped (0.32, on it 0.37, the release 0.35, the magazine drops from 0.40): at least 25.
# At the belly the free pose's wrist (forearm 25, 27 extension) was held; turned toward neutral: forearm 64, 6
# extension, 6 ulnar (18 tries). The way up (scratch lift_search_bg.py, 16 tries): straight up and docking from 1.18
# the support hand went 5 mm into the shooting hand at 1.20 (the grip's own contact is 1.5 / 2.5 mm); out 4 cm to the
# left halfway and docking from 1.10 it comes onto the grip from the side, no deeper than the grip. The rig: LOW itself
# stopped the fist 29 cm off the reloads' belt pouch, in the air before the belly (side view); low_at puts it 8 cm over
# the pouch (pouch_W, the reloads' own), same wrist, 2.65 m/s at most on the way down
UNLOAD = dict(clip='unload', lift=(0.0, 0.0, 0.0), release=0.04, wait=0.32, grab=0.37, free=0.55, low=0.90, let=0.95,
              back=0.95, join0=1.10, regrip=1.38,
              press=(0.24, 0.31, 0.39, 0.48), wait_via=(-0.03, 0.0, -0.03), low_via=(0.0, 0.0, 0.0),
              low_at=(-0.145, -0.141, -0.119), low_e=(-3.1, -27.0, -8.4), lift_via=(0.04, 0.0, 0.0))


class Unload(Draw):
    """unload / unload_empty: see above."""

    def setup(self):
        Draw.setup(self)
        s = self.s
        g = grips()
        self.Kc, self.ec = g['pinch']
        Kp, self.ep = g['push']
        self.Kp = mag_turn(WELL_PHI) @ Kp
        self.press_e = g['press']
        fw = round(s['wait'] * FPS)
        # under the base plate, 2 cm down its axis, open (the inspect's wait)
        self.A_wait = rigid(self.G(fw)).inverted() @ rigid(self.root(fw) @ self.fr(fw)['parts']['magazine']) @ \
            Matrix.Translation(Vector((0, -math.sin(MAG_TILT), -math.cos(MAG_TILT))) * 0.02) @ self.Kp
        self._ends = None

    def G(self, f):
        """The formal handling eased in over the first 0.05 s: its first frame is 1.1 deg off the hip gun (the next
        ones 0.2), a 0.6 px jump of the shooting hand at the switch from hip."""
        return self.root(f) @ lerp_m(Matrix.Identity(4), self.fr(f)['parts']['handling'], ramp(f / FPS, 0.0, 0.05))

    def spec_at(self, t):
        """The stance's spec with the support arm toward ARM_WELL from the grip to under the well, back on the way down."""
        s = self.s
        w = ramp(t, s['release'], s['wait']) * (1 - ramp(t, s['free'], s['low']))
        if w <= 0:
            return self.p
        p = dict(self.p)
        for k, v in ARM_WELL.items():
            r0, l0 = side(p.get(k, 0.0), 'r'), side(p.get(k, 0.0), 'l')
            p[k] = (r0, l0 + (v - l0) * w)
        return p

    def ends(self):
        if self._ends:
            return self._ends
        s = self.s
        ff = round(s['free'] * FPS)
        pose(self.spec_at(s['free']), G=self.G(ff), left=(rigid(self.W_formal(ff)) @ self.Kp, PUSH))
        P0, e0 = rig().pose.bones['hand_l'].head.copy(), wrist_raw('l')
        P1 = self.H_low.translation + rigid(self.G_hip).to_3x3() @ Vector(s['low_at'])
        e1 = Euler(tuple(math.radians(a) for a in s['low_e']), 'YXZ') if 'low_e' in s else e0
        fr = round(s['regrip'] * FPS)
        pose(self.spec_at(s['regrip']), G=self.G(fr), left=(rigid(self.G(fr)) @ self.A_grip, self.e_grip))
        P2, e2 = rig().pose.bones['hand_l'].head.copy(), wrist_raw('l')
        self._ends = dict(low=((P0, e0), (P1, e1)), lift=((P1, e1), (P2, e2)))
        return self._ends

    def wrist_hand(self, spec_t, G, e, seg, u):
        (P0, e0), (P1, e1) = self.ends()[seg]
        pos = arc(P0, P1, (P0 + P1) / 2 + Vector(self.s[seg + '_via']), u)
        pose(spec_t, G=G, left=(Matrix.Translation(pos), e))
        hb = rig().pose.bones['hand_l']
        rest = hb.parent.bone.matrix_local.inverted() @ hb.bone.matrix_local
        ang = Euler(tuple(a + (b - a) * u for a, b in zip(e0, e1)), 'YXZ')
        R = (hb.parent.matrix @ rest).to_3x3().normalized() @ ang.to_matrix()
        return Matrix.Translation(pos) @ R.to_4x4()

    roll = Inspect.roll

    def plan(self, f):
        s, t = self.s, f / FPS
        G = self.G(f)
        Rg = rigid(G)
        out = dict(t=t, G=G, fr=self.fr(f), left=None, e=None, W=self.W_formal(f), free=False, right=self.press(t))
        if t <= s['release']:
            out.update(left=Rg @ self.A_grip, e=self.e_grip)
        elif t < s['wait']:                             # off the grip, round under the base plate (gun-relative), opening
            u = ramp(t, s['release'], s['wait'])
            via = (self.A_grip.translation + self.A_wait.translation) / 2 + Vector(s['wait_via'])
            e = blend_e(blend_e(self.e_grip, RELAXED, peel_fingers(u)), REACH, smooth((u - 0.5) / 0.5))
            out.update(left=Rg @ lerp_m(self.A_grip, self.A_wait, u, via), e=e)
        elif t < s['grab']:                             # up onto the base plate, the palm under it
            u = ramp(t, s['wait'], s['grab'])
            A_mag = Rg.inverted() @ rigid(self.W_formal(f)) @ self.Kp
            out.update(left=Rg @ lerp_m(self.A_wait, A_mag, u), e=blend_e(REACH, PUSH, u))
        elif t < s['free']:                             # released and drawn out on the formal path on the palm
            out.update(left=rigid(self.W_formal(f)) @ self.Kp, e=PUSH)
        elif t < s['low']:                              # down to the rig at the belly, closing into the fist
            u = ramp(t, s['free'], s['low'])
            out.update(wrist=('low', u), free=True)
            out.update(zip(('K', 'e'), self.roll(smooth(u / 0.85))))
        elif t < s['back']:                             # stowed: the fist opens
            out.update(wrist=('low', 1.0), e=blend_e(self.roll(1.0)[1], RELAXED, smooth(ramp(t, s['low'], s['let']))))
        elif t < s['regrip']:                           # back up onto the grip (the draw's way up)
            u = ramp(t, s['back'], s['regrip'])
            e = blend_e(blend_e(RELAXED, REACH, smooth(u / 0.5)), self.e_grip, smooth((u - 0.6) / 0.4))
            out.update(wrist=('lift', u), e=e, to=(Rg @ self.A_grip, ramp(t, s['join0'], s['regrip'])))
        else:
            out.update(left=Rg @ self.A_grip, e=self.e_grip)
        return out

    def shown_new(self, t):
        """The magazine in the hand from the drop to the stow (the formal reload_magazine)."""
        f = min(self.last, round(t * FPS))
        return int(self.fr(f)['shown']['reload_magazine'])


class UnloadEmpty(Unload):
    """unload_empty: the slide locked back on the gun."""
    extra_parts = ('slide',)


register(UNLOAD, Unload)
register(dict(UNLOAD, clip='unload_empty'), UnloadEmpty)
if write_props not in AFTER_EXPORT:
    AFTER_EXPORT.append(write_props)
