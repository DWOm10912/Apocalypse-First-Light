"""H1-P0 five-finger right hand: anatomy table, signed-distance shapes, forward kinematics, grip solver.

Pure data + math; build_mesh_hand_p0.py turns it into the Blender scene. Units: the anatomy is in millimetres in the
hand frame H (X radial = thumb side, Y distal = toward the fingertips, Z palmar = out of the palm, origin at the wrist
joint); the scene is in P9-01 V3 geo pixels (MM px per mm), Blender axes = (rx, -rz, ry) of the GeckoLib render frame.
"""
import math

import numpy as np
from mathutils import Matrix, Vector, Euler

MM = 1 / 19.0          # px per mm: the P9 grip is 1.5 px wide (~29 mm) and 2.4 px deep (~46 mm)

# name, MCP joint (radial, distal, palmar) mm, phalanx lengths mm, skin radius (base, tip) per phalanx mm,
# rest abduction deg (+ = toward the little finger), rest flexion per joint deg (a relaxed open hand). The rest hand is
# spread so neighbouring fingers do not touch: skinned fingers fused at rest tear into a membrane when they bend apart
FINGERS = [
    ('index',  (28.0, 92.0, 3.0),  (46, 26, 18), ((9.2, 8.3), (8.3, 7.5), (7.5, 6.4)), -8.0, (5, 9, 5)),
    ('middle', (8.0, 97.0, 0.0),   (50, 30, 19), ((9.5, 8.6), (8.6, 7.8), (7.8, 6.6)),  0.0, (5, 9, 5)),
    ('ring',   (-12.0, 93.0, 2.0), (47, 29, 19), ((8.8, 8.1), (8.1, 7.4), (7.4, 6.3)),  7.0, (6, 10, 5)),
    ('pinky',  (-30.0, 82.0, 6.0), (37, 21, 17), ((7.9, 7.2), (7.2, 6.5), (6.5, 5.7)), 15.0, (7, 11, 6)),
]
# thumb: CMC joint mm, lengths (metacarpal, proximal, distal), radii, metacarpal direction (radial abduction from the
# distal axis, palmar lift) deg, pronation of the pad toward the fingers deg, rest flexion (CMC, MCP, IP)
THUMB = dict(cmc=(19.0, 20.0, 8.0), lengths=(46, 32, 26), radii=((12.0, 10.6), (10.6, 9.8), (9.8, 8.4)),
             abduct=44.0, palmar=14.0, pronate=60.0, flex=(0, 8, 10))
WRIST_CUT = -6.0       # forearm interface: the hand is cut flat at this distal coordinate (mm)


def mat3(x, y, z):
    return Matrix((x, y, z)).transposed()


def rot_x(deg):
    return Matrix.Rotation(math.radians(deg), 3, 'X')


def rot_z(deg):
    return Matrix.Rotation(math.radians(deg), 3, 'Z')


class Bone:
    def __init__(self, name, parent, head, frame, length, radii=None):
        self.name, self.parent, self.head, self.frame, self.length = name, parent, Vector(head), frame, length
        self.radii = radii      # (base, tip) skin radius mm, phalanges only

    @property
    def tail(self):
        return self.head + self.frame.col[1] * self.length


def skeleton():
    """Bones in H at the bind (relaxed open) pose, parents first."""
    bones = [Bone('hand', None, (0, 0, 0), Matrix.Identity(3), 60.0)]
    t = THUMB
    a, p = math.radians(t['abduct']), math.radians(t['palmar'])
    d = Vector((math.sin(a) * math.cos(p), math.cos(a) * math.cos(p), math.sin(p)))
    pr = math.radians(t['pronate'])
    z0 = Vector((-math.sin(pr), 0, math.cos(pr)))
    z = (z0 - z0.dot(d) * d).normalized()
    frame = mat3(d.cross(z), d, z)
    head, parent = Vector(t['cmc']), 'hand'
    for j in range(3):
        frame = frame @ rot_x(t['flex'][j])
        b = Bone('thumb_%02d' % (j + 1), parent, head, frame, t['lengths'][j], t['radii'][j])
        bones.append(b)
        head, parent = b.tail, b.name
    for name, mcp, lengths, radii, abd, flex in FINGERS:
        frame, head, parent = rot_z(abd), Vector(mcp), 'hand'
        for j in range(3):
            frame = frame @ rot_x(flex[j])
            b = Bone('%s_%02d' % (name, j + 1), parent, head, frame, lengths[j], radii[j])
            bones.append(b)
            head, parent = b.tail, b.name
    return bones


# ---- signed-distance shapes (mm, in each bone's own rest frame: Y along the bone, Z palmar) ----

def sd_round_cone_y(q, y0, y1, r0, r1, zs=1.0):
    h = y1 - y0
    b = (r0 - r1) / h
    a = math.sqrt(max(1 - b * b, 1e-9))
    qx = np.sqrt(q[:, 0] ** 2 + (q[:, 2] * zs) ** 2)
    qy = q[:, 1] - y0
    k = -b * qx + a * qy
    mid = a * qx + b * qy - r0
    bot = np.sqrt(qx * qx + qy * qy) - r0
    top = np.sqrt(qx * qx + (qy - h) ** 2) - r1
    return np.where(k < 0, bot, np.where(k > a * h, top, mid))


def sd_ellipsoid(q, c, r):
    p = (q - np.asarray(c, np.float32)) / np.asarray(r, np.float32)
    k0 = np.sqrt((p * p).sum(1))
    k1 = np.sqrt(((p / np.asarray(r, np.float32)) ** 2).sum(1))
    return k0 * (k0 - 1) / np.maximum(k1, 1e-6)


def smin(a, b, k):
    if k <= 0:
        return np.minimum(a, b)
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0, 1)
    return b * (1 - h) + a * h - k * h * (1 - h)


class Shape:
    """One primitive on a bone: kind 'cone' (y0, y1, r0, r1, zs) or 'ellip' (centre, radii), in the bone frame."""
    def __init__(self, kind, *args):
        self.kind, self.args = kind, args

    def eval(self, q):
        return sd_round_cone_y(q, *self.args) if self.kind == 'cone' else sd_ellipsoid(q, *self.args)

    def extent(self):
        if self.kind == 'cone':
            y0, y1, r0, r1, _ = self.args
            r = max(r0, r1)
            return (-r, y0 - r0, -r), (r, y1 + r1, r)
        c, r = self.args
        return tuple(c[i] - r[i] for i in range(3)), tuple(c[i] + r[i] for i in range(3))


def to_local(b, v):
    return b.frame.transposed() @ (Vector(v) - b.head)


def shapes(bones):
    """Primitives per bone (the bone's own group) and the blend radius inside the group."""
    by = {b.name: b for b in bones}
    out = {}
    hand = by['hand']
    palm = [Shape('cone', -10.0, 30.0, 23.0, 26.0, 1.6),            # carpus / wrist, flattened dorsal-palmar
            Shape('ellip', (0.0, 60.0, 2.5), (36.0, 36.0, 12.0)),     # palm body
            Shape('ellip', (-22.0, 44.0, 6.0), (14.0, 30.0, 10.0))]   # hypothenar pad
    out['hand'] = (palm, 14.0)       # plus the metacarpals (metacarpal_shapes), whose heads make the knuckles
    t1 = by['thumb_01']
    out['thumb_01'] = ([Shape('cone', 0.0, t1.length, *t1.radii, 1.1),
                        Shape('ellip', (-4.0, 18.0, 6.0), (13.0, 22.0, 11.0))], 10.0)   # thenar eminence
    for b in bones:
        if b.radii is None or b.name == 'thumb_01':
            continue
        r0, r1 = b.radii
        distal = b.name.endswith('_03')
        y1 = b.length - (0.55 * r1 if distal else 0.0)
        group = [Shape('cone', 0.0, y1, r0, r1, 1.12)]
        if not b.name.startswith('thumb'):
            group.append(Shape('ellip', (0.0, 0.0, -0.3), (r0 * 0.95, r0 * 0.85, r0 * 0.93)))   # joint knuckle (slight, no beads)
        out[b.name] = (group, 3.0)
    return out


def metacarpal_shapes(bones):
    """The four metacarpals in H (they belong to the hand bone; frames are not the hand's)."""
    res = []
    for name, mcp, *_ in FINGERS:
        base = Vector((mcp[0] * 0.5, 26.0, 1.0))
        tip = Vector(mcp)
        axis = (tip - base).normalized()
        z = (Vector((0, 0, 1)) - axis.z * axis).normalized()
        x = axis.cross(z)
        res.append((mat3(x, axis, z), base, (tip - base).length))
    return res


class HandSDF:
    """Signed distance of the hand (B: one blended body) or of one bone group (A: a rigid piece), points in H mm."""
    def __init__(self, bones):
        self.bones = bones
        self.by = {b.name: b for b in bones}
        self.groups = shapes(bones)
        self.mcs = metacarpal_shapes(bones)

    def group(self, name, p):
        b = self.by[name]
        q = ((p - np.asarray(b.head, np.float32)) @ np.asarray(b.frame, np.float32)).astype(np.float32)
        prims, k = self.groups[name]
        d = None
        for s in prims:
            e = s.eval(q)
            d = e if d is None else smin(d, e, k)
        if name == 'hand':
            for frame, base, length in self.mcs:
                qq = ((p - np.asarray(base, np.float32)) @ np.asarray(frame, np.float32)).astype(np.float32)
                d = smin(d, sd_round_cone_y(qq, 0.0, length, 9.5, 10.2, 1.05), k)
            d = np.maximum(d, WRIST_CUT - p[:, 1])
        return d

    def chain(self, root, p):
        """Root bone's group blended with its descendants (k 3)."""
        d = self.group(root, p)
        kids = [b for b in self.bones if b.parent == root]
        for c in kids:
            d = smin(d, self.chain(c.name, p), 3.0)
        return d

    def body(self, p):
        d = smin(self.group('hand', p), self.chain('thumb_01', p), 6.0)
        for name, *_ in FINGERS:
            d = smin(d, self.chain(name + '_01', p), 5.0)
        return np.maximum(d, WRIST_CUT - p[:, 1])

    def extent(self, names):
        lo, hi = Vector((1e9,) * 3), Vector((-1e9,) * 3)
        for n in names:
            b = self.by[n]
            prims, k = self.groups[n]
            ext = [s.extent() for s in prims]
            if n == 'hand':
                ext.append(((-40, WRIST_CUT, -20), (40, 100, 25)))
            for a, c in ext:
                for corner in [(x, y, z) for x in (a[0], c[0]) for y in (a[1], c[1]) for z in (a[2], c[2])]:
                    w = b.head + b.frame @ Vector(corner)
                    lo = Vector(map(min, lo, w))
                    hi = Vector(map(max, hi, w))
        return lo, hi


def polygonize(fn, lo, hi, voxel):
    """Iso-surface of fn (numpy points -> distance) over the box, via OpenVDB. Returns (verts H mm, faces)."""
    import openvdb
    pad = 3 * voxel
    lo = np.array(lo) - pad
    n = np.ceil((np.array(hi) + pad - lo) / voxel).astype(int) + 1
    arr = np.empty(tuple(n), np.float32)
    jj, kk = np.meshgrid(np.arange(n[1]), np.arange(n[2]), indexing='ij')
    for i in range(n[0]):
        pts = np.stack([np.full(jj.size, lo[0] + i * voxel), lo[1] + jj.ravel() * voxel, lo[2] + kk.ravel() * voxel], 1).astype(np.float32)
        arr[i] = fn(pts).reshape(n[1], n[2])
    grid = openvdb.FloatGrid()
    grid.copyFromArray(arr)
    pts, tris, quads = grid.convertToPolygons(0.0, 0.0)
    verts = lo + pts * voxel
    faces = [tuple(int(i) for i in f) for f in quads] + [tuple(int(i) for i in f) for f in tris]
    return verts, faces


# ---- placement and kinematics (scene px) ----

class Rig:
    """World (scene px) rest matrices of the bones for a root placement, and forward kinematics of a pose."""
    def __init__(self, bones, root_rot, root_origin):
        self.bones, self.by = bones, {b.name: b for b in bones}
        self.root = Matrix.Translation(root_origin) @ root_rot.to_4x4()
        self.rest = {}
        for b in bones:
            m = b.frame.to_4x4()
            m.translation = b.head * MM
            self.rest[b.name] = self.root @ m
        self.local = {b.name: (self.rest[b.parent].inverted() @ self.rest[b.name]) if b.parent else self.rest[b.name] for b in bones}

    def world_of_h(self, v_mm):
        return self.root @ (Vector(v_mm) * MM)

    def pose(self, eulers, root_offset=None):
        """eulers: name -> (x, y, z) deg (Blender XYZ); root_offset: (location in hand-local px, euler deg)."""
        out = {}
        for b in self.bones:
            e = eulers.get(b.name, (0, 0, 0))
            basis = Euler([math.radians(a) for a in e], 'XYZ').to_matrix().to_4x4()
            if b.parent is None:
                if root_offset:
                    basis = Matrix.Translation(root_offset[0]) @ Euler([math.radians(a) for a in root_offset[1]], 'XYZ').to_matrix().to_4x4()
                out[b.name] = self.local[b.name] @ basis
            else:
                out[b.name] = out[b.parent] @ self.local[b.name] @ basis
        return out


class GunField:
    """Signed distance (px, negative inside) to the union of the P9 reference meshes: an OpenVDB level set (sign by
    flood fill, so the hollow magazine well with the magazine in it is solid), copied dense over the box the hand can
    reach and sampled trilinearly. Nearest-face signs are wrong at edges and inside the hollow grip."""
    def __init__(self, objects, voxel=0.025, box=((-2.5, -7.5, -2.0), (3.5, 4.0, 7.0))):
        import openvdb
        pts, quads, tris = [], [], []
        for ob in objects:
            base = len(pts)
            pts += [tuple(ob.matrix_world @ v.co) for v in ob.data.vertices]
            for p in ob.data.polygons:
                vs = [base + i for i in p.vertices]
                (quads if len(vs) == 4 else tris).append(vs if len(vs) <= 4 else vs[:3])
        xf = openvdb.createLinearTransform(voxelSize=voxel)
        P = np.array(pts, np.float32)
        grid = openvdb.FloatGrid.createLevelSetFromPolygons(P, np.array(tris, np.uint32).reshape(-1, 3),
                                                             np.array(quads, np.uint32).reshape(-1, 4), xf, 6.0)
        self.voxel, self.lo = voxel, np.floor(np.array(box[0]) / voxel).astype(int)
        hi = np.ceil(np.array(box[1]) / voxel).astype(int)
        self.arr = np.empty(tuple(hi - self.lo + 1), np.float32)
        grid.copyToArray(self.arr, ijk=tuple(int(i) for i in self.lo))
        self.far = float(grid.background)

    def sd_many(self, p):
        """p: (N, 3) px -> signed distance px (clamped to the narrow band outside it)."""
        g = np.asarray(p, np.float64) / self.voxel - self.lo
        i0 = np.floor(g).astype(int)
        f = g - i0
        n = np.array(self.arr.shape)
        inside = np.all((i0 >= 0) & (i0 < n - 1), axis=1)
        i0 = np.clip(i0, 0, n - 2)
        out = np.zeros(len(g))
        for dx in (0, 1):
            for dy in (0, 1):
                for dz in (0, 1):
                    w = (f[:, 0] if dx else 1 - f[:, 0]) * (f[:, 1] if dy else 1 - f[:, 1]) * (f[:, 2] if dz else 1 - f[:, 2])
                    out += w * self.arr[i0[:, 0] + dx, i0[:, 1] + dy, i0[:, 2] + dz]
        return np.where(inside, out, self.far)

    def sd(self, p):
        return float(self.sd_many([tuple(p)])[0])


def capsule_sd_many(p, a, b, r):
    a, b = np.array(a), np.array(b)
    ab = b - a
    t = np.clip(((p - a) @ ab) / max(ab @ ab, 1e-12), 0, 1)
    return np.linalg.norm(p - (a + t[:, None] * ab), axis=1) - r


def capsule_sd(p, a, b, r):
    ab = b - a
    t = max(0.0, min(1.0, (p - a).dot(ab) / max(ab.length_squared, 1e-12)))
    return (p - (a + ab * t)).length - r
