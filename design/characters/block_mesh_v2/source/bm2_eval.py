"""AFL Block-Mesh V2 prototypes: pose A-D with the game's own poser output, measure contacts, render the comparison sheets.

Input: poses_v2.json / poses_vanilla.json written by the scratch exporter BlockMeshV2Export (the real HumanoidPoser /
ActionPose / SeatPose, offline). The prototypes' 22 main bones follow the poser's 16 (the same pivots, bm2_rig.py); spine
and neck take half / 40 % of the chest's and head's turns; D's fingers are posed here (relaxed, or curled until they
touch the held object).

Usage: python -I bm2_eval.py <project> <poses dir> <out dir> [sheet ...]
"""
import json
import math
import os
import subprocess
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bm2_rig as R  # noqa: E402
import bm2_build as B  # noqa: E402
import bm2_game  # noqa: E402,F401  (registers B2)

PRJ, POSES, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
SHEETS = sys.argv[4:]
ASSETS = os.path.join(PRJ, 'src/main/resources/assets/apocalypse_firstlight')
os.makedirs(OUT, exist_ok=True)
J = np.array([[1., 0, 0, 0], [0, -1, 0, 24], [0, 0, 1, 0], [0, 0, 0, 1]])      # Bedrock px -> Java model px
S16 = np.diag([1 / 16., 1 / 16, 1 / 16, 1])


def m44(a):
    return np.array(a, float).reshape(4, 4).T


def unit(v):
    return v / np.linalg.norm(v)


def axis_rot(a, rad):
    a = unit(np.asarray(a, float))
    K = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    M = np.eye(4)
    M[:3, :3] = np.eye(3) + math.sin(rad) * K + (1 - math.cos(rad)) * K @ K
    return M


def T(p):
    M = np.eye(4)
    M[:3, 3] = p
    return M


def q_from_m(Rm):
    t = np.trace(Rm)
    if t > 0:
        s = math.sqrt(t + 1) * 2
        return np.array([0.25 * s, (Rm[2, 1] - Rm[1, 2]) / s, (Rm[0, 2] - Rm[2, 0]) / s, (Rm[1, 0] - Rm[0, 1]) / s])
    i = int(np.argmax(np.diag(Rm)))
    j, k = (i + 1) % 3, (i + 2) % 3
    s = math.sqrt(1 + Rm[i, i] - Rm[j, j] - Rm[k, k]) * 2
    q = np.zeros(4)
    q[0] = (Rm[k, j] - Rm[j, k]) / s
    q[1 + i] = 0.25 * s
    q[1 + j] = (Rm[j, i] + Rm[i, j]) / s
    q[1 + k] = (Rm[k, i] + Rm[i, k]) / s
    return q


def m_from_q(q):
    w, x, y, z = q / np.linalg.norm(q)
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def part_turn(M, share):
    """The rotation part of M (a rigid transform) scaled to a share of its angle."""
    q = q_from_m(M[:3, :3])
    if q[0] < 0:
        q = -q
    ang = 2 * math.acos(min(1, q[0]))
    if ang < 1e-9:
        return np.eye(3)
    ax = q[1:] / math.sin(ang / 2)
    return axis_rot(ax, ang * share)[:3, :3]


# ---------------------------------------------------------------- images
def load_png(path, alpha=False):
    w, h = map(int, subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=width,height', '-of', 'csv=p=0', path],
                                   capture_output=True, text=True).stdout.strip().split(','))
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-f', 'rawvideo', '-pix_fmt', 'rgba', '-'], capture_output=True).stdout
    img = np.frombuffer(raw, np.uint8).reshape(h, w, 4)
    return img if alpha else img[:, :, :3]


def save_png(path, img):
    open(path, 'wb').write(B.png_bytes(np.ascontiguousarray(img[:, :, :3])))


# ---------------------------------------------------------------- a drawable: world triangles with uv + texture or colours
class Draw:
    def __init__(self, P, F, UV=None, tex=None, cols=None, alpha=None, lit=True):
        self.P, self.F, self.UV, self.tex, self.cols, self.alpha, self.lit = P, F, UV, tex, cols, alpha, lit


class Camera:
    """Orthographic (scale px per m) or perspective (fov deg) looking from eye at target."""

    def __init__(self, eye, target, w, h, scale=None, fov=None, up=(0, 1, 0)):
        self.eye, self.w, self.h, self.scale, self.fov = np.asarray(eye, float), w, h, scale, fov
        f = unit(np.asarray(target, float) - self.eye)
        r = unit(np.cross(f, up))
        u = np.cross(r, f)
        self.R = np.array([r, u, -f])          # rows: right, up, back
        self.target = np.asarray(target, float)

    def project(self, P):
        v = (P - self.eye) @ self.R.T          # camera space, -z ahead
        if self.scale:
            d = np.linalg.norm(self.target - self.eye)
            x = self.w / 2 + v[:, 0] * self.scale
            y = self.h / 2 - v[:, 1] * self.scale
            z = v[:, 2]                         # larger = nearer
            return x, y, z, np.ones(len(P))
        f = self.h / 2 / math.tan(math.radians(self.fov) / 2)
        depth = -v[:, 2]
        depth = np.where(depth < 1e-3, 1e-3, depth)
        x = self.w / 2 + v[:, 0] / depth * f
        y = self.h / 2 - v[:, 1] / depth * f
        return x, y, -depth, 1 / depth


LIGHT = unit(np.array([0.35, 0.85, 0.55]))


def shade_of(n):
    """Minecraft-like: brighter on top, dimmer underneath and on the shadow side."""
    up = n[1]
    s = 0.62 + 0.3 * max(0.0, float(np.dot(n, LIGHT))) + (0.12 * up if up > 0 else 0.18 * up)
    return min(1.0, max(0.3, s))


def render(img, zb, draws, cam, sky=None):
    H, W = zb.shape
    for d in draws:
        x, y, z, iw = cam.project(d.P)
        for fi, tri in enumerate(d.F):
            X, Y, Z = x[tri], y[tri], z[tri]
            x0, x1 = int(max(0, math.floor(X.min()))), int(min(W - 1, math.ceil(X.max())))
            y0, y1 = int(max(0, math.floor(Y.min()))), int(min(H - 1, math.ceil(Y.max())))
            if x1 < x0 or y1 < y0 or (x1 - x0) * (y1 - y0) > W * H:
                continue
            den = (Y[1] - Y[2]) * (X[0] - X[2]) + (X[2] - X[1]) * (Y[0] - Y[2])
            if abs(den) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            l0 = ((Y[1] - Y[2]) * (gx - X[2]) + (X[2] - X[1]) * (gy - Y[2])) / den
            l1 = ((Y[2] - Y[0]) * (gx - X[2]) + (X[0] - X[2]) * (gy - Y[2])) / den
            l2 = 1 - l0 - l1
            m = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
            if not m.any():
                continue
            zz = l0 * Z[0] + l1 * Z[1] + l2 * Z[2]
            sub = zb[y0:y1 + 1, x0:x1 + 1]
            m &= zz > sub
            if not m.any():
                continue
            p = d.P[tri]
            n = np.cross(p[1] - p[0], p[2] - p[0])
            nl = np.linalg.norm(n)
            if nl < 1e-12:
                continue
            n /= nl
            if np.dot(n, cam.eye - p.mean(0)) < 0 and cam.scale is None:
                n = -n
            elif cam.scale is not None and np.dot(n, cam.R[2]) < 0:
                n = -n
            sh = shade_of(n) if d.lit else 1.0
            if d.tex is not None:
                w0, w1, w2 = l0 * iw[tri[0]], l1 * iw[tri[1]], l2 * iw[tri[2]]
                ws = w0 + w1 + w2
                uv = d.UV[tri]
                u = (w0 * uv[0, 0] + w1 * uv[1, 0] + w2 * uv[2, 0]) / ws
                v = (w0 * uv[0, 1] + w1 * uv[1, 1] + w2 * uv[2, 1]) / ws
                th, tw = d.tex.shape[:2]
                ti = np.clip((v * th).astype(int), 0, th - 1)
                tj = np.clip((u * tw).astype(int), 0, tw - 1)
                col = d.tex[ti, tj][..., :3].astype(float)
                if d.tex.shape[2] == 4:
                    m &= d.tex[ti, tj][..., 3] > 100
                col = col * sh
            else:
                col = np.broadcast_to(np.array(d.cols[fi], float) * sh, gx.shape + (3,))
            sub[m] = zz[m]
            img[y0:y1 + 1, x0:x1 + 1][m] = np.clip(col[m], 0, 255).astype(np.uint8)


# ---------------------------------------------------------------- prototypes
class Proto:
    def __init__(self, name):
        self.name = name
        self.m = B.fix_winding(B.build(name))
        self.tex = B.texture(self.m)
        self.bones = R.skeleton(name == 'D', name)
        self.names = [b[0] for b in self.bones]
        self.parent = {n: p for n, p, _ in self.bones}
        self.pivot = {n: np.array(p, float) for n, _, p in self.bones}
        self.index = {n: i for i, n in enumerate(self.names)}
        V = self.m['V']
        self.Vh = np.c_[V, np.ones(len(V))]
        J4 = np.zeros((len(V), 4), int)
        W4 = np.zeros((len(V), 4))
        for i, w in enumerate(self.m['W']):
            items = sorted(w.items(), key=lambda kv: -kv[1])[:4]
            t = sum(v for _, v in items)
            for k, (b, v) in enumerate(items):
                J4[i, k] = self.index[b]
                W4[i, k] = v / t
        self.J4, self.W4 = J4, W4
        self.part = np.array([self.m['FN'][f // 1][0].name for f in range(len(self.m['F']))])
        # vertices by part (for the contact checks)
        self.vpart = np.empty(len(V), object)
        for fi, tri in enumerate(self.m['F']):
            self.vpart[tri] = self.m['FN'][fi][0].name

    def skin(self, M):
        K = np.array([M[n] for n in self.names])
        out = np.zeros((len(self.Vh), 3))
        for k in range(4):
            out += self.W4[:, k:k + 1] * np.einsum('nij,nj->ni', K[self.J4[:, k]], self.Vh)[:, :3]
        return out

    def draw(self, M, keep=None):
        F = self.m['F']
        if keep is not None:
            F = F[[keep(self.vpart[t[0]]) for t in F]]
        return Draw(self.skin(M), F, self.m['UV'], self.tex)


POSER = R.POSER_BONES


def bone_matrices(proto, fr, fingers=None, adapt=False):
    """World (metres, relative to the render origin) matrices of every prototype bone, acting on Bedrock rest px.
    adapt: the limited target adaptation (docs 6.2): hands back where the current block person's are (its accepted
    poses), feet on the current person's footprints / the floor at the V2 ankle height."""
    Tinv = np.linalg.inv(m44(fr['to_model']))
    Wj = {n: m44(w) for n, w in zip(POSER, fr['world'])}
    to_world = lambda W: Tinv @ S16 @ W @ J
    M = {}
    for n in POSER:
        M[n] = to_world(Wj[n])
    # spine: half of the chest's turn about the waist; neck: 40 % of the head's about its pivot
    jp = lambda n: (J @ np.append(proto.pivot[n], 1))[:3]
    rel = np.linalg.inv(Wj['pelvis']) @ Wj['chest']
    Rh = np.eye(4)
    Rh[:3, :3] = part_turn(rel, 0.5)
    M['spine'] = to_world(Wj['pelvis'] @ T(jp('spine')) @ Rh @ T(-jp('spine')))
    rel = np.linalg.inv(Wj['chest']) @ Wj['head']
    Rh = np.eye(4)
    Rh[:3, :3] = part_turn(rel, 0.4)
    M['neck'] = to_world(Wj['chest'] @ T(jp('neck')) @ Rh @ T(-jp('neck')))
    for s in 'rl':
        M['clavicle_' + s] = M['chest']
        M['toes_' + s] = M['foot_' + s]
    if adapt:
        adapt_targets(proto, fr, M)
    if proto.name == 'D':
        finger_pose(proto, M, fingers or {})
    return M


VAN_RIG = None


def vanilla_matrix(name, bone):
    """The current block person's bone (world, on its own Bedrock rest px) in the same test."""
    fr = FV[name]
    return np.linalg.inv(m44(fr['to_model'])) @ S16 @ m44(fr['world'][POSER.index(bone)]) @ J


def palm_rest(proto, s):
    """Where a held item's grip sits in the hand (rest Bedrock px): the hand's middle 1.5 px above its end, as the
    current block hand holds it; D's palm face (the fingers close round the grip in front of it)."""
    sx = -1 if s == 'r' else 1
    if proto.name == 'D':
        return np.array([sx * (B.ARMS['D']['cx'] - 0.75 - 0.55), 12.9, -0.1])
    return np.array([sx * B.ARMS[proto.name]['cx'], 12.5, 0])


def two_bone(proto, M, a, b, c, target, end=None):
    """Turn bones a (and below) and b (and below) so c's pivot reaches target (world), the bend in the plane it has
    now; end: c's wanted world matrix, set when the target is reached. Returns the miss (m)."""
    kids = lambda n: [k for k in M if k == n or is_below(proto, k, n)]
    S = (M[a] @ np.append(proto.pivot[a], 1))[:3]
    E = (M[b] @ np.append(proto.pivot[b], 1))[:3]
    Wp = (M[c] @ np.append(proto.pivot[c], 1))[:3]
    L1, L2 = np.linalg.norm(E - S), np.linalg.norm(Wp - E)
    d = np.linalg.norm(target - S)
    dc = min(max(d, abs(L1 - L2) + 1e-6), L1 + L2 - 1e-6)
    u = unit(target - S)
    v = (E - S) - np.dot(E - S, u) * u
    if np.linalg.norm(v) < 1e-9:
        v = np.cross(u, [1, 0, 0])
    v = unit(v)
    ca = (L1 * L1 + dc * dc - L2 * L2) / (2 * L1 * dc)
    nE = S + L1 * (ca * u + math.sqrt(max(0, 1 - ca * ca)) * v)
    G = T(S) @ align(E - S, nE - S) @ T(-S)
    for k in kids(a):
        M[k] = G @ M[k]
    Wp = (M[c] @ np.append(proto.pivot[c], 1))[:3]
    G = T(nE) @ align(Wp - nE, target - nE) @ T(-nE)
    for k in kids(b):
        M[k] = G @ M[k]
    Wp = (M[c] @ np.append(proto.pivot[c], 1))[:3]
    miss = float(np.linalg.norm(Wp - target))
    if end is not None:
        G = end @ np.linalg.inv(M[c])
        for k in kids(c):
            M[k] = G @ M[k]
        if miss > 1e-4:          # not reachable: the hand / foot keeps its turn, at the chain's end
            off = Wp - (M[c] @ np.append(proto.pivot[c], 1))[:3]
            for k in kids(c):
                M[k] = T(off) @ M[k]
    return miss


def is_below(proto, k, n):
    p = proto.parent.get(k)
    while p:
        if p == n:
            return True
        p = proto.parent.get(p)
    return False


def align(a, b):
    """The smallest turn taking direction a to direction b (4x4)."""
    a, b = unit(a), unit(b)
    ax = np.cross(a, b)
    s = np.linalg.norm(ax)
    if s < 1e-12:
        return np.eye(4)
    return axis_rot(ax, math.atan2(s, float(np.dot(a, b))))


ADAPT_LOG = {}


def adapt_targets(proto, fr, M):
    name = fr['name']
    log = ADAPT_LOG.setdefault((proto.name, name), {})
    seat = 'seat' in fr
    px = np.linalg.norm(M['root'][:3, 0])           # one rest px in world metres
    rj = vanilla_rig()
    vp = {b['name']: np.array(b['pivot'], float) for b in rj['bones']}
    for s in 'rl':
        sx = -1 if s == 'r' else 1
        if not seat and fr['modes'][0 if s == 'r' else 1] == 'IK':
            Mv = vanilla_matrix(name, 'hand_' + s)
            want = Mv @ T(np.array([sx * 6, 12.5, 0]) - palm_rest(proto, s))
            log['hand_' + s] = two_bone(proto, M, 'upper_arm_' + s, 'forearm_' + s, 'hand_' + s,
                                        (want @ np.append(proto.pivot['hand_' + s], 1))[:3], want) / px
        if seat:
            want = T((0, -0.5 * px, 0)) @ M['foot_' + s]
        else:
            Mv = vanilla_matrix(name, 'foot_' + s)
            want = Mv @ T(np.array([vp['foot_' + s][0] - proto.pivot['foot_' + s][0], 0, 0]))
        log['foot_' + s] = two_bone(proto, M, 'thigh_' + s, 'shin_' + s, 'foot_' + s,
                                    (want @ np.append(proto.pivot['foot_' + s], 1))[:3], want) / px
        log['toes_' + s] = toe_clamp(proto, M, s, seat_levels(fr)[0] if seat else 0.0)


def toe_clamp(proto, M, s, floor):
    """The toes bend up at the ball until the toe box is out of the floor (deg turned)."""
    sx = -1 if s == 'r' else 1
    tips = [np.array([sx * 2 + dx, y, R.FOOT['front'], 1]) for dx in (-2, 2) for y in (0, 1.1)]
    low = lambda: min((M['toes_' + s] @ p)[1] for p in tips)
    if low() >= floor - 1e-4:
        return 0.0
    piv = (M['toes_' + s] @ np.append(proto.pivot['toes_' + s], 1))[:3]
    ax = unit(M['foot_' + s][:3, 0])
    base = M['toes_' + s].copy()
    for deg in range(1, 61):
        for sg in (1, -1):
            M['toes_' + s] = T(piv) @ axis_rot(ax, math.radians(sg * deg)) @ T(-piv) @ base
            if low() >= floor - 1e-4:
                return float(sg * deg)
    return 60.0


def vanilla_rig():
    global VAN_RIG
    if VAN_RIG is None:
        VAN_RIG = json.load(open(os.path.join(ASSETS, 'rigs/afl_humanoid_v1.json')))
    return VAN_RIG


RELAXED = dict(mcp=12, pip=18, dip=10, thumb=(10, 8, 6))


def finger_pose(proto, M, curls):
    """Fingers by curl per side: {'r': c or dict per finger 0..1} over the full grip angles; RELAXED otherwise."""
    for s in 'rl':
        sign = 1 if s == 'r' else -1
        for f, d in R.FINGERS.items():
            c = curls.get(s, None)
            cf = c.get(f) if isinstance(c, dict) else c
            names = ['%s_%02d_%s' % (f, i + 1, s) for i in range(3)]
            if f == 'thumb':
                full = (35, 40, 45)
                rel = RELAXED['thumb']
                dvec = unit(np.array([0, -0.7, -0.7]))
                n = np.array([sign * 1.0, 0, 0])
                ax = np.cross(dvec, n)
            else:
                full = (80, 95, 65)
                rel = (RELAXED['mcp'], RELAXED['pip'], RELAXED['dip'])
                ax = np.array([0, 0, sign * 1.0])
            prev = M['hand_' + s]
            for i, nme in enumerate(names):
                ang = rel[i] if cf is None else full[i] * cf
                p = proto.pivot[nme]
                prev = prev @ T(p) @ axis_rot(ax, math.radians(ang)) @ T(-p)
                M[nme] = prev


def finger_points(proto, M, s):
    """Sample points along each finger (world) with the finger's name, for the contact search."""
    out = []
    for f, d in R.FINGERS.items():
        names = ['%s_%02d_%s' % (f, i + 1, s) for i in range(3)]
        for i, nme in enumerate(names):
            p0 = proto.pivot[nme]
            p1 = proto.pivot[names[i + 1]] if i < 2 else p0 + (p0 - proto.pivot[names[1]]) / np.linalg.norm(p0 - proto.pivot[names[1]]) * d['seg'][2]
            for t in (0.5, 1.0):
                q = p0 + (p1 - p0) * t
                out.append((f, (M[nme] @ np.append(q, 1))[:3]))
    return out


def grip(proto, M, s, obj, fingers=('thumb', 'index', 'middle', 'ring', 'pinky')):
    """Curl each finger until it touches the object's points (world): the first curl (0..1) with a finger sample point
    within the finger's half width + 0.15 px of the object; returns the curls and each finger's gap at that curl."""
    curls, gaps = {}, {}
    tree = obj
    for f in fingers:
        best = 1.0
        for c in np.linspace(0, 1, 41):
            trial = dict(curls)
            trial[f] = c
            finger_pose(proto, M, {s: trial})
            pts = [p for ff, p in finger_points(proto, M, s) if ff == f]
            dmin = min(float(np.min(np.linalg.norm(tree - p, axis=1))) for p in pts)
            if dmin < (R.FINGERS[f]['w'] / 2 + 0.15) * R.PX:
                best = c
                gaps[f] = dmin / R.PX
                break
        else:
            gaps[f] = dmin / R.PX
        curls[f] = best
    finger_pose(proto, M, {s: curls})
    return curls, gaps


# ---------------------------------------------------------------- scene meshes
def aflmesh(mesh, geo, tex, parts=None, skip=(), transform=None, keep=None):
    g = json.load(open(os.path.join(ASSETS, geo)))['minecraft:geometry'][0]
    piv = {b['name']: np.array(b.get('pivot', [0, 0, 0])) / 16 for b in g['bones']}
    d = json.load(open(os.path.join(ASSETS, mesh)))
    P, UV, F = [], [], []
    for p in d['parts']:
        if parts and p['name'] not in parts:
            continue
        if any(k in p['name'] for k in skip):
            continue
        if keep is not None and not keep(p):
            continue
        v = np.array(p['vertices'])
        base = len(P)
        P += list(v[:, :3] + piv.get(p.get('bone'), 0))
        UV += list(v[:, 3:5])
        for f in p['faces']:
            f = list(f) if isinstance(f[0], int) else list(f[0])
            for k in range(1, len(f) - 1):
                F.append((base + f[0], base + f[k], base + f[k + 1]))
    P = np.array(P)
    if transform is not None:
        P = transform(P)
    return Draw(P, np.array(F), np.array(UV), load_png(os.path.join(ASSETS, tex), alpha=True))


def cabin_quads(path, texs, transform):
    """A cabin_mesh furniture file: quads of (texture index, then four (x y z u v nx ny nz))."""
    d = json.load(open(os.path.join(ASSETS, path)))
    imgs = [load_png(os.path.join(ASSETS, 'textures', t.split(':')[1] + '.png'), alpha=True) for t in d['textures']]
    out = []
    for ti, img in enumerate(imgs):
        P, UV, F = [], [], []
        for q in d['quads']:
            if q[0] != ti:
                continue
            base = len(P)
            for k in range(4):
                a = q[1 + k * 8: 1 + k * 8 + 8]
                P.append(a[0:3])
                UV.append(a[3:5])
            F += [(base, base + 1, base + 2), (base, base + 2, base + 3)]
        if P:
            out.append(Draw(transform(np.array(P, float)), np.array(F), np.array(UV), img))
    return out


def ground(size, y=0.0, tex=None, cols=None, tile=1.0, center=(0, 0)):
    n = int(size / tile)
    P, F, UV = [], [], []
    for i in range(-n, n):
        for k in range(-n, n):
            base = len(P)
            x0, z0 = center[0] + i * tile, center[1] + k * tile
            P += [(x0, y, z0), (x0 + tile, y, z0), (x0 + tile, y, z0 + tile), (x0, y, z0 + tile)]
            UV += [(0, 0), (1, 0), (1, 1), (0, 1)]
            F += [(base, base + 1, base + 2), (base, base + 2, base + 3)]
    return Draw(np.array(P, float), np.array(F), np.array(UV, float), tex, cols)


def grass_tex(n=16, seed=3):
    rng = np.random.default_rng(seed)
    base = np.array([92, 140, 62], float)
    img = base * (0.86 + 0.22 * rng.random((n, n, 1)))
    return np.clip(img, 0, 255).astype(np.uint8)


def box_draw(lo, hi, col):
    lo, hi = np.asarray(lo, float), np.asarray(hi, float)
    c = [np.where([(k >> i) & 1 for i in range(3)], hi, lo) for k in range(8)]
    quads = [(0, 2, 3, 1), (4, 5, 7, 6), (0, 1, 5, 4), (2, 6, 7, 3), (0, 4, 6, 2), (1, 3, 7, 5)]
    F = []
    for q in quads:
        F += [(q[0], q[1], q[2]), (q[0], q[2], q[3])]
    return Draw(np.array(c), np.array(F), cols=[col] * len(F))


# ---------------------------------------------------------------- the current (vanilla) block person, flat colours
VAN_SEGS = None


def vanilla_draw(fr):
    global VAN_SEGS
    rj = json.load(open(os.path.join(ASSETS, 'rigs/afl_humanoid_v1.json')))
    names = [b['name'] for b in rj['bones']]
    if VAN_SEGS is None:
        VAN_SEGS = []
        for s in rj['skin']:
            f, sz = s['from'], s['size']
            x0, y0, z0 = f[0], 24 - (f[1] + sz[1]), f[2]
            lv = [0] + s['cuts'] + [sz[1]]
            for i, b in enumerate(s['bones']):
                VAN_SEGS.append((names.index(b), (x0, y0 + lv[i], z0), (x0 + sz[0], y0 + lv[i + 1], z0 + sz[2]), s['part'], lv[i]))
        VAN_SEGS.append((names.index('head'), (-4, -8, -4), (4, 0, 4), 'head', 0))
    Tinv = np.linalg.inv(m44(fr['to_model']))
    P, F, C = [], [], []
    for b, lo, hi, part, top in VAN_SEGS:
        W = Tinv @ S16 @ m44(fr['world'][b])
        d = box_draw(lo, hi, (0, 0, 0))
        base = len(P)
        P += list((W @ np.c_[d.P, np.ones(8)].T).T[:, :3])
        F += list(d.F + base)
        col = {'head': B.PAL['skin'], 'body': B.PAL['jacket'], 'right_arm': B.PAL['jacket'], 'left_arm': B.PAL['jacket'],
               'right_leg': B.PAL['jeans'], 'left_leg': B.PAL['jeans']}[part]
        if part.endswith('arm') and top >= 10:
            col = B.PAL['skin']
        if part.endswith('leg') and top >= 10:
            col = B.PAL['boot']
        C += [col] * len(d.F)
    return Draw(np.array(P), np.array(F), cols=C)


# ---------------------------------------------------------------- props
PROP_MESH = {
    'p9_01': ('meshes/p9_01_v3_native.aflmesh.json', 'geo/p9_01_v3_native.geo.json', 'textures/item/p9_01_v3_native.png'),
    'br51_01': ('meshes/br51_01_v3_native.aflmesh.json', 'geo/br51_01_v3_native.geo.json', 'textures/item/br51_01_v3_native.png'),
    'round_point_shovel': ('meshes/round_point_shovel.aflmesh.json', 'geo/round_point_shovel.geo.json', 'textures/item/round_point_shovel_mesh.png'),
    'crowbar': ('meshes/crowbar.aflmesh.json', 'geo/crowbar.geo.json', 'textures/item/crowbar_mesh.png'),
}
PROP_CACHE = {}


def prop_parts(pid):
    """(mesh, geo, texture, part filter) of a player prop; a gun part (magazine) is the gun's mesh, only that bone."""
    pj = json.load(open(os.path.join(ASSETS, 'player_props/%s.json' % pid)))
    if 'gun_part' in pj:
        gun, bone = pj['gun_part']['gun'], pj['gun_part']['bone']
        g = json.load(open(os.path.join(ASSETS, PROP_MESH[gun][1])))['minecraft:geometry'][0]
        sub = {bone}
        for _ in range(4):
            sub |= {x['name'] for x in g['bones'] if x.get('parent') in sub}
        return PROP_MESH[gun], pj['scale'], lambda p: p.get('bone') in sub
    if pj.get('gun'):
        skip = ('empty_old_mag', 'mag_out', 'reload_magazine', 'glass')
        return PROP_MESH[pj['gun']], pj['scale'], lambda p: not any(k in p['name'] for k in skip) and p.get('bone') not in ('mag_out', 'empty_old_mag')
    return PROP_MESH[pid], pj.get('scale', 1), lambda p: True


def prop_draw(pid, prop_m, Tinv):
    if pid not in PROP_MESH and not pid.startswith('p9_01_mag'):
        return None
    if pid not in PROP_CACHE:
        (mesh, geo, tex), scale, keep = prop_parts(pid)
        PROP_CACHE[pid] = (aflmesh(mesh, geo, tex, keep=keep), scale)
    d, scale = PROP_CACHE[pid]
    k = 16 * scale
    W = Tinv @ S16 @ m44(prop_m) @ np.diag([k, -k, -k, 1])
    return Draw((W @ np.c_[d.P, np.ones(len(d.P))].T).T[:, :3], d.F, d.UV, d.tex)


def p9_grip_points(prop_m, Tinv, scale=0.49467):
    pj = json.load(open(os.path.join(ASSETS, 'player_props/p9_01.json')))
    k = 16 * scale
    W = Tinv @ S16 @ m44(prop_m) @ np.diag([k, -k, -k, 1])
    return {n: (W @ np.append(v['pos'], 1))[:3] for n, v in pj['grips'].items()}


# ---------------------------------------------------------------- measurements
def torso_frames(proto, M):
    """Boxes (rest Bedrock px) carried by bones, for the clipping check: torso halves, thighs, head."""
    return [('chest', (-4, 18, -2), (4, 24, 2)), ('pelvis', (-4, 15, -2), (4, 18, 2)),
            ('thigh_r', (-4, 9.5, -2), (0, 15, 2)), ('thigh_l', (0, 9.5, -2), (4, 15, 2)), ('head', (-4, 24, -4), (4, 32, 4))]


def penetration(proto, M, P, s):
    """Deepest a forearm / hand vertex of side s sits inside the torso, thighs or head (px)."""
    vid = np.where([(p == 'arm_' + s) or p.endswith('_' + s) and p.split('_')[0] in R.FINGERS for p in proto.vpart])[0]
    rest = proto.m['V'][vid]
    vid = vid[rest[:, 1] < R.ARM['elbow'] + 0.5]       # below the elbow: the shoulder sits against the torso by design
    worst, where = 0.0, ''
    for bone, lo, hi in torso_frames(proto, M):
        inv = np.linalg.inv(M[bone])
        q = (inv @ np.c_[P[vid], np.ones(len(vid))].T).T[:, :3]
        lo, hi = np.array(lo), np.array(hi)
        inside = np.min(np.minimum(q - lo, hi - q), axis=1)
        d = float(inside.max()) if len(inside) else 0
        if d > worst:
            worst, where = d, bone
    return worst, where


def joint_angle(M, a, b, proto):
    """The bend (deg) between two bones' rest-down axes."""
    da = M[a][:3, :3] @ np.array([0, -1., 0])
    db = M[b][:3, :3] @ np.array([0, -1., 0])
    return math.degrees(math.acos(max(-1, min(1, float(np.dot(unit(da), unit(db)))))))


def elbow_squeeze(proto, M, P, s):
    """The arm's thinnest posed cross-section around the elbow over its rest thickness (1 = keeps its volume)."""
    arm = [t for t in proto.m['tubes'] if t.name == 'arm_' + s][0]
    ratios = []
    vid = np.where(proto.vpart == 'arm_' + s)[0]
    rest = proto.m['V'][vid]
    for r in arm.rings:
        if abs(r.t - R.ARM['elbow']) > 1.0:
            continue
        sel = vid[np.abs(rest[:, 1] - r.t) < 1e-6]
        if len(sel) < 4:
            continue
        q = P[sel]
        # the ring's posed extent across the arm: the smallest distance between opposite corners' pairs
        d = [np.linalg.norm(q[i] - q[j]) for i in range(len(q)) for j in range(i + 1, len(q))]
        ratios.append(min(x for x in d if x > 1e-6) / (2 * min(r.h) * R.PX))
    return min(ratios) if ratios else 1.0


def lowest_sole(proto, P, s):
    vid = np.where(proto.vpart == 'foot_' + s)[0]
    return float(P[vid, 1].min())


def seat_levels(fr):
    """(floor, cushion, roof) world heights of a seated frame (anchors are in the frame's world metres)."""
    seat = fr['seat']
    fl = seat['anchors']['floor_r'][1]
    roof = fl + (1.79 - 0.535) if fr['name'].startswith('t15') else None
    return fl, fl + seat['cushion_y'] - seat['floor_y'], roof


def palm_point(M, s):
    sx = -1 if s == 'r' else 1
    return (M['hand_' + s] @ np.array([sx * R.ARM['x'], R.ARM['wrist'] - 1.1, 0, 1]))[:3]


def measure(proto, fr, M, P):
    res = {}
    seat = fr.get('seat')
    floor = seat_levels(fr)[0] if seat else 0.0
    for s in 'rl':
        res['sole_' + s] = (lowest_sole(proto, P, s) - floor) * 100          # cm over the floor
        pen, where = penetration(proto, M, P, s)
        res['clip_' + s] = round(pen, 2)
        res['clip_where_' + s] = where
        res['elbow_' + s] = joint_angle(M, 'upper_arm_' + s, 'forearm_' + s, proto)
        res['knee_' + s] = joint_angle(M, 'thigh_' + s, 'shin_' + s, proto)
        res['elbow_keep_' + s] = elbow_squeeze(proto, M, P, s)
        if not seat and fr['modes'][0 if s == 'r' else 1] in ('IK', 'VANILLA'):
            sx = -1 if s == 'r' else 1
            pv = (vanilla_matrix(fr['name'], 'hand_' + s) @ np.array([sx * 6, 12.5, 0, 1]))[:3]
            res['hand_shift_' + s] = float(np.linalg.norm((M['hand_' + s] @ np.append(palm_rest(proto, s), 1))[:3] - pv)) * 100
    if not seat:
        Mv = {s: vanilla_matrix(fr['name'], 'foot_' + s) for s in 'rl'}
        for s in 'rl':
            sx = -1 if s == 'r' else 1
            pv = (Mv[s] @ np.array([sx * 1.9, 0, 0, 1]))[:3]
            res['foot_shift_' + s] = float(np.linalg.norm((M['foot_' + s] @ np.array([sx * 2, 0, 0, 1]))[:3] - pv)) * 100
    if seat:
        fl, cushion, roof = seat_levels(fr)
        vid = np.where([p == 'torso' or p.startswith('leg_') for p in proto.vpart])[0]
        rest = proto.m['V'][vid]
        near = vid[(rest[:, 1] > R.LEG['hip'] - 3) & (rest[:, 1] < R.LEG['hip'] + 1.5)]
        res['seat_gap'] = (float(P[near, 1].min()) - cushion) * 100
        eye = (M['head'] @ np.array([0, 27.63, -2.5, 1]))[:3]
        res['eye_over_cushion'] = (float(eye[1]) - cushion) * 100
        if roof is not None:
            res['roof_clear'] = (roof - float(P[proto.vpart == 'head', 1].max())) * 100
        for s in 'rl':
            g = seat['anchors'].get('grip_' + s) or seat['anchors'].get('wheel_' + s)
            if g is not None:
                res['wheel_' + s] = float(np.linalg.norm(palm_point(M, s) - np.array(g))) * 100
    return res


# ---------------------------------------------------------------- sheets
def frames(path):
    return {f['name']: f for f in json.load(open(path))['frames']}


FR = frames(os.path.join(POSES, 'poses_v2.json'))
FV = frames(os.path.join(POSES, 'poses_vanilla.json'))
# B2 (round 2): its own poses, solved on its own rig (rigs/afl_block_mesh_v2.json), when exported
FB2 = frames(os.path.join(POSES, 'poses_b2.json')) if os.path.exists(os.path.join(POSES, 'poses_b2.json')) else None
PROTOS = {p: Proto(p) for p in ['A', 'B', 'C', 'D'] + (['B2'] if FB2 else [])}


def frames_of(p):
    return FV if p == 'V' else FB2 if p == 'B2' else FR


def posed(p, name, fingers=None, adapt=True):
    fr = frames_of(p)[name]
    M = bone_matrices(PROTOS[p], fr, fingers, adapt)
    return fr, M


FONT = 'C\\:/Windows/Fonts/simhei.ttf'


def cell_camera(view, w, h, scale, target=(0, 0.95, 0), dist=6.0):
    a = math.radians(view)
    eye = np.array(target) + dist * np.array([math.sin(a), 0.12, math.cos(a)])
    return Camera(eye, target, w, h, scale=scale)


def annotate(path, labels):
    """Chinese labels via ffmpeg drawtext (each text through its own file, no escaping): (x, y, text, size)."""
    tmp = os.path.join(OUT, '_labels')
    os.makedirs(tmp, exist_ok=True)
    parts = []
    for i, (x, y, text, size) in enumerate(labels):
        f = os.path.join(tmp, '%d.txt' % i)
        open(f, 'w', encoding='utf-8').write(text)
        parts.append("drawtext=fontfile='%s':textfile='%s':x=%d:y=%d:fontsize=%d:fontcolor=0x202020:expansion=none" %
                     (FONT, os.path.abspath(f).replace('\\', '/').replace(':', '\\:'), x, y, size))
    script = os.path.join(tmp, 'filter.txt')
    open(script, 'w', encoding='utf-8').write(',\n'.join(parts))
    out = path[:-4] + '_t.png'
    r = subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', path, '-filter_script:v', script, out], capture_output=True, text=True)
    if r.returncode:
        print(r.stderr[-800:])
        return
    os.replace(out, path)


def props_draws(fr, use_vanilla=False):
    src = FV[fr['name']] if use_vanilla and fr['name'] in FV else fr
    Tinv = np.linalg.inv(m44(src['to_model']))
    out = []
    for pr in src.get('props', []):
        d = prop_draw(pr['id'], pr['m'], Tinv)
        if d is not None:
            out.append(d)
    return out


CHAIRS = {}


def furniture(name):
    """The test's chair, its cushion on the seat the poser sat on ((0, h, -0.05) world), its back behind the hips."""
    if name not in CHAIRS:
        if name == 'chair_wood':
            ds = cabin_quads('cabin_mesh/furniture/chair.json', None, lambda P: P)
        else:
            ds = [aflmesh('meshes/modern_office_chair.aflmesh.json', 'geo/modern_office_chair.geo.json',
                          'textures/block/modern_office_chair.png')]
        P = np.concatenate([d.P for d in ds])
        h = 0.465 if name == 'chair_wood' else 0.547
        cx, cz = (P[:, 0].min() + P[:, 0].max()) / 2, (P[:, 2].min() + P[:, 2].max()) / 2
        back = P[P[:, 1] > h + 0.15]
        flip = (back[:, 2].mean() - cz) > 0          # the back on +z: turn half round (the body sits facing +z)
        out = []
        for d in ds:
            Q = d.P - np.array([cx, 0, cz])
            if flip:
                Q = Q * np.array([-1, 1, -1])
            out.append(Draw(Q + np.array([0, 0, -0.05]), d.F, d.UV, d.tex, d.cols))
        CHAIRS[name] = out
    return CHAIRS[name]


CAB = None


def t15_interior(cut_left=True):
    """pickup_t15_cab + _seat in the driver frame's world: geo blocks (x left, -z ahead) -> (x - 0.369, y + 0.133, -z - 0.0455)."""
    global CAB
    if CAB is None:
        tf = lambda P: np.c_[P[:, 0] - 0.369, P[:, 1] + 0.133, -P[:, 2] - 0.0455]
        keep = lambda p: p.get('bone') != 'door_l' and 'glass' not in p['name']
        CAB = [aflmesh('meshes/pickup_t15_cab.aflmesh.json', 'geo/pickup_t15_cab.geo.json', 'textures/entity/vehicle/pickup_t15_cab.png', transform=tf, keep=keep),
               aflmesh('meshes/pickup_t15_seat.aflmesh.json', 'geo/pickup_t15_seat.geo.json', 'textures/entity/vehicle/pickup_t15_cab.png', transform=tf)]
    if not cut_left:
        return CAB
    out = []
    for d in CAB:   # cut away the driver's door side and the roof, so the side view sees in
        c = d.P[d.F].mean(1)
        k = ~((c[:, 0] > 0.26) | (c[:, 1] > 1.93))
        out.append(Draw(d.P, d.F[k], d.UV, d.tex))
    return out


GRASS = None


def env_ground(name):
    global GRASS
    if name.startswith('t15'):
        return []
    if GRASS is None:
        GRASS = grass_tex()
    return [ground(1.6, 0, GRASS, tile=0.5)]


def body_draw(row, name, adapt=True, fingers=None):
    if row == 'V':
        return vanilla_draw(FV[name]), None
    _, M = posed(row, name, fingers, adapt)
    if row == 'D' and fingers is None:
        grip_fingers(M, name)
    return PROTOS[row].draw(M), M


def scene(row, name, adapt=True, fingers=None, cut=True):
    fr = frames_of(row)[name]
    body, M = body_draw(row, name, adapt, fingers)
    draws = [body] + props_draws(fr, use_vanilla=adapt and row != 'V')
    if name in ('chair_wood', 'chair_office'):
        draws += furniture(name)
    if name.startswith('t15'):
        draws += t15_interior(cut)
    draws += env_ground(name)
    return draws, M


GRIP_LOG = {}


def grip_fingers(M, name):
    """D: curl the fingers onto what the hand holds (the prop's mesh near the palm, the steering wheel), else relaxed."""
    proto = PROTOS['D']
    fr = FR[name]
    if name.startswith('t15'):
        objs = [d.P for d in t15_interior(False)]
    else:
        objs = [d.P for d in props_draws(fr, use_vanilla=name in FV)]
    if not objs:
        return
    cloud = np.concatenate(objs)
    curls = {}
    for s in 'rl':
        palm = (M['hand_' + s] @ np.append(palm_rest(proto, s), 1))[:3]
        near = cloud[np.linalg.norm(cloud - palm, axis=1) < 3.2 * R.PX]
        if len(near) < 3:
            continue
        c, gaps = grip(proto, M, s, near)
        curls[s] = c
        GRIP_LOG[name + '_' + s] = dict(curl={k: round(v, 2) for k, v in c.items()}, gap_px={k: round(v, 2) for k, v in gaps.items()})
    finger_pose(proto, M, curls)


def render_cell(img, draws, cam):
    zb = np.full(img.shape[:2], -1e9)
    render(img, zb, draws, cam)


def grid(rows, cols, cw, ch, cell, left=70, top=34, bg=236):
    img = np.full((top + len(rows) * ch, left + len(cols) * cw, 3), bg, np.uint8)
    for ri, r in enumerate(rows):
        for ci, c in enumerate(cols):
            sub = img[top + ri * ch: top + (ri + 1) * ch, left + ci * cw: left + (ci + 1) * cw]
            cell(sub, r, c)
            sub[:, -1] = 175
            sub[-1, :] = 175
    return img


ROW_NAME = {'V': '现有方块人', 'A': 'A 原版粗', 'B': 'B 细方臂', 'C': 'C 四棱台', 'D': 'D 五指'}


def save_sheet(img, fname, rows, cols, cw, ch, title, left=70, top=34, colname=lambda c: c):
    path = os.path.join(OUT, fname)
    save_png(path, img)
    labels = [(8, 4, title, 15)]
    for ci, c in enumerate(cols):
        labels.append((left + ci * cw + 6, top + 6, colname(c), 13))
    for ri, r in enumerate(rows):
        labels.append((4, top + ri * ch + ch // 2 - 8, ROW_NAME.get(r, r), 13))
    annotate(path, labels)
    print('wrote', path)


def sheet_compare():
    cols = [('stand', 0, '正面'), ('stand', 90, '侧面'), ('stand', 180, '背面'), ('stand', 45, '45°'), ('crouch', 45, '蹲下'),
            ('chair_wood', 60, '木椅'), ('chair_office', 60, '办公椅'), ('gun_p9', 45, 'P9-01 持枪')]
    rows = ['V'] + list('ABCD')
    cw, ch = 180, 300

    def cell(sub, r, c):
        name, view, _ = c
        draws, _ = scene(r, name)
        render_cell(sub, draws, cell_camera(view, cw, ch, 138, target=(0, 0.98, 0.05)))
    img = grid(rows, cols, cw, ch, cell)
    save_sheet(img, 'compare_all.png', rows, cols, cw, ch, 'Block-Mesh V2 原型对比：同一身高、站姿、相机、灯光、皮肤颜色（第一行是现在的方块人）',
               colname=lambda c: c[2])


def sheet_arms():
    cols = [('stand', '站立', False), ('run', '跑（肘 70°）', False), ('gun_p9', '持枪', False), ('chair_wood', '木椅（肘 68°）', False),
            ('run', '跑 单独看手臂（弯曲平面正侧）', True), ('chair_wood', '木椅 单独看手臂', True), ('bend_generator', '弯腰 单独看手臂', True)]
    rows = list('ABCD')
    cw, ch = 210, 250

    def cell(sub, r, c):
        name, iso = c[0], c[2]
        draws, M = scene(r, name)
        if iso:           # the right arm alone, from its outer side: the elbow's and the wrist's profile
            if r == 'D':
                grip_fingers(M, name)
            draws = [PROTOS[r].draw(M, keep=lambda p: p == 'arm_r' or (p.endswith('_r') and p.split('_')[0] in R.FINGERS))]
        el = (M['forearm_r'] @ np.append(PROTOS[r].pivot['forearm_r'], 1))[:3]
        wr = (M['hand_r'] @ np.append(PROTOS[r].pivot['hand_r'], 1))[:3]
        tgt = el * 0.6 + wr * 0.4
        sh = (M['upper_arm_r'] @ np.append(PROTOS[r].pivot['upper_arm_r'], 1))[:3]
        n = unit(np.cross(el - sh, wr - el)) if np.linalg.norm(np.cross(el - sh, wr - el)) > 1e-6 else np.array([-1.0, 0, 0])
        if n[0] > 0:
            n = -n
        if not iso:
            a = math.radians(-75)
            n = np.array([math.sin(a), 0.1, math.cos(a)])
        cam = Camera(tgt + 3 * n, tgt, cw, ch, scale=420)
        render_cell(sub, draws, cam)
    img = grid(rows, cols, cw, ch, cell)
    save_sheet(img, 'arms_closeup.png', rows, cols, cw, ch, '右臂肘、腕特写（从右前外侧看）', colname=lambda c: c[1])


def seat_cam(name, view, cw, ch):
    if name.startswith('t15'):
        return cell_camera(view, cw, ch, 150, target=(0, 1.3, 0.18))
    return cell_camera(view, cw, ch, 150, target=(0, 0.8, 0.12))


def sheet_seats():
    cols = [('crouch', 45, '蹲下'), ('crouch_walk', 45, '蹲走'), ('chair_wood', 90, '木椅 侧'), ('chair_office', 90, '办公椅 侧'),
            ('t15_driver', 90, 'T15 驾驶座 1.0'), ('t15_driver_085', 90, 'T15 驾驶座 0.85')]
    rows = list('ABCD')
    cw, ch = 210, 290

    def cell(sub, r, c):
        name, view, _ = c
        draws, _ = scene(r, name)
        render_cell(sub, draws, seat_cam(name, view, cw, ch))
    img = grid(rows, cols, cw, ch, cell)
    save_sheet(img, 'tests_seat_crouch.png', rows, cols, cw, ch, '坐、蹲测试（现有动作数据 + 有限目标适配：手回到现在方块人的位置，脚踩原脚印或地板）',
               colname=lambda c: c[2])
    cols2 = [c for c in cols if c[0] != 't15_driver_085']
    img = grid(['V'], cols2, cw, ch, lambda sub, r, c: render_cell(sub, scene('V', c[0])[0], seat_cam(c[0], c[1], cw, ch)))
    save_sheet(img, 'tests_seat_crouch_current.png', ['V'], cols2, cw, ch, '对照：现在的方块人（座位里拉长腿；T15 里整体缩到 0.82、躯干压到 0.68）',
               colname=lambda c: c[2])


def sheet_actions():
    cols = [('gun_p9', 45, 'P9 持枪'), ('gun_br51', 50, 'BR-51 持枪'), ('p9_reload_20', 35, '换弹 20%'), ('p9_reload_40', 35, '换弹 40%'),
            ('p9_reload_60', 35, '换弹 60%'), ('p9_draw_out', 45, '拔枪'), ('bend_generator', 60, '弯腰'), ('bend_shovel', 60, '铲土'),
            ('arms_up', 20, '举手'), ('turn', 45, '转身')]
    rows = ['V'] + list('ABCD')
    cw, ch = 170, 290

    def cell(sub, r, c):
        name, view, _ = c
        draws, _ = scene(r, name)
        render_cell(sub, draws, cell_camera(view, cw, ch, 132, target=(0, 0.98, 0.1)))
    img = grid(rows, cols, cw, ch, cell)
    save_sheet(img, 'tests_actions.png', rows, cols, cw, ch, '持枪、换弹关键帧、工具动作（现有动作数据 + 有限目标适配）', colname=lambda c: c[2])


def sheet_grips():
    cols = [('p9_draw_out', 'r', -60, 0.25, '单手持 P9（拔枪）'), ('gun_p9', 'r', -40, 0.25, '双手持枪 右手'), ('gun_p9', 'l', 40, 0.25, '双手持枪 左手'),
            ('gun_p9', 'r', -110, -0.6, '手指包住握把（下外侧）'), ('p9_reload_40', 'l', 50, 0.25, '左手拿弹匣'), ('t15_driver_085', 'r', 0, 0, '握方向盘')]
    rows = ['B', 'D']
    cw, ch = 240, 260

    def cell(sub, r, c):
        name, s, view, el, _ = c
        draws, M = scene(r, name, cut=False)
        tgt = (M['hand_' + s] @ np.append(palm_rest(PROTOS[r], s), 1))[:3]
        if name.startswith('t15'):    # from ahead of the wheel (the dash and everything beyond the rim cut away), both hands
            hub = np.array(FR[name]['seat']['anchors']['wheel_hub'])
            cut = []
            for d in t15_interior(False):
                c = d.P[d.F].mean(1)
                cut.append(Draw(d.P, d.F[(c[:, 2] < hub[2] + 0.05) & (c[:, 1] < 1.6)], d.UV, d.tex))
            draws = draws[:1] + cut
            cam = Camera(hub + np.array([0.0, 0.9, 2.2]), hub, cw, ch, scale=520)
        else:
            a = math.radians(view)
            cam = Camera(tgt + 2 * np.array([math.sin(a), el, math.cos(a)]), tgt, cw, ch, scale=760)
        render_cell(sub, draws, cam)
    img = grid(rows, cols, cw, ch, cell)
    save_sheet(img, 'd_finger_grips.png', rows, cols, cw, ch, 'D 五指握持（手指逐根弯到碰到物体为止）；上排 B 方手作对照', colname=lambda c: c[4])


def tree_trunks(spots):
    bark = load_png(os.path.join(ASSETS, 'textures/block/tree/red_oak_bark.png'))
    out = []
    for x, z, r in spots:
        P, F, UV = [], [], []
        for k in range(10):
            a0, a1 = 2 * math.pi * k / 10, 2 * math.pi * (k + 1) / 10
            for j in range(7):
                base = len(P)
                P += [(x + r * math.cos(a0), j, z + r * math.sin(a0)), (x + r * math.cos(a1), j, z + r * math.sin(a1)),
                      (x + r * math.cos(a1), j + 1, z + r * math.sin(a1)), (x + r * math.cos(a0), j + 1, z + r * math.sin(a0))]
                UV += [(0, 1), (1, 1), (1, 0), (0, 0)]
                F += [(base, base + 1, base + 2), (base, base + 2, base + 3)]
        out.append(Draw(np.array(P, float), np.array(F), np.array(UV, float), bark))
    return out


def wall(x0, x1, z, h, tex, tile=1.0):
    P, F, UV = [], [], []
    for i in range(int(round((x1 - x0) / tile))):
        for j in range(int(round(h / tile))):
            base = len(P)
            xa, ya = x0 + i * tile, j * tile
            P += [(xa, ya, z), (xa + tile, ya, z), (xa + tile, ya + tile, z), (xa, ya + tile, z)]
            UV += [(0, 1), (1, 1), (1, 0), (0, 0)]
            F += [(base, base + 1, base + 2), (base, base + 2, base + 3)]
    return Draw(np.array(P, float), np.array(F), np.array(UV, float), tex)


def pickup_exterior(offset):
    """The T15 (body, front, cab, seat, four wheels at the renderer's hubs) side-on to the camera (offline preview)."""
    parts = []
    tf = lambda P: np.c_[-P[:, 2], P[:, 1], P[:, 0]] + np.array(offset)
    for m in ('pickup_t15', 'pickup_t15_front', 'pickup_t15_cab', 'pickup_t15_seat'):
        tex = 'textures/entity/vehicle/%s.png' % ('pickup_t15_cab' if m in ('pickup_t15_cab', 'pickup_t15_seat') else 'pickup_t15')
        parts.append(aflmesh('meshes/%s.aflmesh.json' % m, 'geo/%s.geo.json' % m, tex, transform=tf, keep=lambda p: 'glass' not in p['name']))
    for x, y, z, turned in ((-0.712, 0.36, -1.60, 1), (0.712, 0.36, -1.60, 0), (-0.712, 0.36, 1.25, 1), (0.712, 0.36, 1.25, 0)):
        k = -1 if turned else 1
        parts.append(aflmesh('meshes/pickup_t15_wheel.aflmesh.json', 'geo/pickup_t15_wheel.geo.json', 'textures/entity/vehicle/pickup_t15.png',
                             transform=lambda P, x=x, y=y, z=z, k=k: tf(np.c_[k * P[:, 0] + x, P[:, 1] + y, k * P[:, 2] + z])))
    return parts


def figures(pose='stand', spacing=1.15, z=0.0):
    out = []
    for i, r in enumerate(['V'] + list('ABCD')):
        body, _ = body_draw(r, pose)
        P = body.P + np.array([(i - 2) * spacing, 0, z])
        out.append(Draw(P, body.F, body.UV, body.tex, body.cols))
    return out


def sheet_env():
    W, H = 1280, 640
    sky = np.array([186, 205, 222], np.uint8)
    envs = []
    envs.append(('env_forest.png', '林间（草地、树干为离线占位）', [ground(9, 0, grass_tex(32, 5), tile=1)] +
                 tree_trunks([(-3.4, -3.0, 0.22), (2.9, -4.2, 0.3), (4.6, -1.5, 0.18), (-5.2, -5.5, 0.26), (0.6, -7, 0.3)])))
    log = load_png(os.path.join(ASSETS, 'textures/block/cabin/white_oak_log_wall.png'))
    plank = load_png(os.path.join(ASSETS, 'textures/block/furniture/wood.png'))
    envs.append(('env_cabin.png', '木屋（白栎原木墙）', [ground(6, 0, plank, tile=1), wall(-6, 6, -1.6, 3, log)]))
    asphalt = load_png(os.path.join(ASSETS, 'textures/block/asphalt.png'))
    envs.append(('env_road.png', '沥青路面', [ground(9, 0, asphalt, tile=1)]))
    envs.append(('env_t15.png', 'T15 旁边', [ground(9, 0, asphalt, tile=1)] + pickup_exterior((0.3, 0.0, -2.6))))
    for fname, title, scenery in envs:
        img = np.empty((H, W, 3), np.uint8)
        img[:] = sky
        cam = Camera((1.2, 1.7, 7.4), (0, 1.0, -0.6), W, H, fov=40)
        render_cell(img, scenery + figures(), cam)
        path = os.path.join(OUT, fname)
        save_png(path, img)
        annotate(path, [(10, 10, title + '：从左到右 现有方块人、A、B、C、D（离线合成，未进游戏）', 20)])
        print('wrote', path)


def run_measure():
    rows = []
    for p in 'ABCD':
        for name, fr in FR.items():
            for adapt in (False, True):
                M = bone_matrices(PROTOS[p], fr, None, adapt)
                P = PROTOS[p].skin(M)
                r = measure(PROTOS[p], fr, M, P)
                r.update(proto=p, test=name, adapt=adapt, ik_miss_px=ADAPT_LOG.get((p, name), {}) if adapt else {})
                rows.append(r)
    json.dump(rows, open(os.path.join(OUT, 'measure.json'), 'w'), indent=1)
    return rows


if __name__ == '__main__':
    if 'measure' in SHEETS:
        rows = run_measure()
        for r in rows:
            keys = [k for k in r if k not in ('proto', 'test', 'adapt', 'ik_miss_px') and not k.startswith(('clip_where', 'elbow_keep'))]
            print(r['proto'], r['test'], 'ADAPT' if r['adapt'] else 'raw', ' '.join('%s=%.1f' % (k, r[k]) for k in keys),
                  'clipat', r['clip_where_r'], r['clip_where_l'], 'keep %.2f %.2f' % (r['elbow_keep_r'], r['elbow_keep_l']),
                  'miss', {k: round(v, 2) for k, v in r['ik_miss_px'].items() if v > 0.01})
    for name in SHEETS:
        if name != 'measure':
            globals()['sheet_' + name]()
    if GRIP_LOG:
        json.dump(GRIP_LOG, open(os.path.join(OUT, 'd_grip_log.json'), 'w'), indent=1)
