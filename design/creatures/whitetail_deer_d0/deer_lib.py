# White-tailed deer D0 concept (docs/gameplay/afl_whitetail_deer_d0_design.md): the player's art language — boxes with
# shared joint rings (SkinMesh-style weights), 2 texels per model px, painted textures — on a quadruped rig.
# Sizes are the real deer's at the player's scale (1 px = 0.9375 / 16 m = 5.86 cm): buck shoulder 14.5 px = 85 cm.
# Reference photos: NPS "White Tailed Deer Buck at Attention" (Shenandoah, October), Wikipedia infobox buck (profile).
#
# A part is a tube: a row of rectangular rings along a polyline, each ring square to the local direction (a joint ring is
# square to the bisector), so a leg is one piece through its joints, like the player's arms. Faces: front (+N), left
# (+X world side), back (-N), right (-X), caps start / end. The texture is painted in the part's own coordinates
# xx (-1 right .. +1 left), nn (-1 back .. +1 front), tt (0 start .. 1 end), then brush strokes drag colour along the hair.
import bpy, bmesh, math, os
import numpy as np
from mathutils import Vector, Euler

PX = 0.9375 / 16          # m per model px (the player's render scale)
T = 2                     # texels per model px (128 skins)
ATLAS = 256


# --------------------------------------------------------------------------------------------------------- colours
def rgb(h):
    return np.array([int(h[i:i + 2], 16) / 255 for i in (1, 3, 5)], np.float32)


def mix(a, b, k):
    k = np.asarray(k, np.float32)[..., None] if np.ndim(k) else k
    return a * (1 - k) + b * k


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------------------------------------------------- atlas
class Atlas:
    def __init__(self, name):
        self.name = name
        self.px = np.zeros((ATLAS, ATLAS, 4), np.float32)
        self.x = self.y = self.row = 0
        self.faces = {}

    def alloc(self, w, h):
        if self.x + w > ATLAS:
            self.x, self.y, self.row = 0, self.y + self.row + 1, 0
        o = (self.x, self.y)
        self.x += w + 1
        self.row = max(self.row, h)
        assert self.y + h <= ATLAS, "atlas full"
        return o

    def uv(self, key, f, s, t):
        fx, fy, fw, fh = self.faces[key][f]
        return ((fx + s * fw) / ATLAS, 1 - (fy + t * fh) / ATLAS)

    def face(self, key, f):
        fx, fy, fw, fh = self.faces[key][f]
        return self.px[fy:fy + fh, fx:fx + fw]

    def image(self, path):
        name = os.path.splitext(os.path.basename(path))[0]
        if name in bpy.data.images:
            bpy.data.images.remove(bpy.data.images[name])
        im = bpy.data.images.new(name, ATLAS, ATLAS, alpha=True)
        im.pixels[:] = self.px[::-1].ravel()
        im.filepath_raw = path
        im.file_format = "PNG"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        im.save()
        return im


RC = None


def bands(v, stops, cols, seed=0.0):
    """A few colour steps instead of a smooth ramp (a smooth ramp across 14 texels reads as wood grain), each border
    broken in one-px blocks (2 texels) along the hair, about a texel either way. v must run along the face's columns
    (side faces) or rows (caps)."""
    face, R, C = RC
    cap = face in ("start", "end")
    n = v.shape[0] if cap else v.shape[1]
    blk = C // 4 if cap else R // 4                  # two-px runs, a texel either way: a fringe, not camouflage
    off = (np.round(np.modf(np.abs(np.sin(blk * 12.9898 + seed * 78.233) * 43758.5453))[0] * 2) - 1) / n
    idx = np.digitize(v + off, stops)
    return np.asarray(cols, np.float32)[idx]


# ----------------------------------------------------------------------------------------------------------- tube
class Tube:
    """rings: list of (pos px Vector, width px, depth px, {bone: weight}). side: the width direction hint; hint: the
    +N (front face) direction hint. paint(xx, nn, tt, face) -> rgb (H, W, 3); hair: world direction strokes follow."""

    def __init__(self, key, rings, side, hint, paint, hair=None, strokes=0.045, alpha=None):
        self.key, self.rings, self.paint = key, rings, paint
        self.side, self.hint = Vector(side).normalized(), Vector(hint).normalized()
        self.hair = Vector(hair).normalized() if hair is not None else None
        self.density = strokes
        self.alpha = alpha
        P = [r[0] for r in rings]
        self.frames = []
        Nprev = None
        for i, c in enumerate(P):
            if i == 0:
                t = (P[1] - c).normalized()
            elif i == len(P) - 1:
                t = (c - P[-2]).normalized()
            else:
                t = ((P[i + 1] - c).normalized() + (c - P[i - 1]).normalized()).normalized()
            X = (self.side - t * self.side.dot(t)).normalized()
            N = t.cross(X)
            if (Nprev is None and N.dot(self.hint) < 0) or (Nprev is not None and N.dot(Nprev) < 0):
                N = -N
            Nprev = N
            self.frames.append((c, t, X, N))
        L = [0.0]
        for i in range(1, len(P)):
            L.append(L[-1] + (P[i] - P[i - 1]).length)
        self.cum = L
        self.length = L[-1]
        self.tr = [l / self.length for l in L]
        self.wmax = max(r[1] for r in rings)
        self.dmax = max(r[2] for r in rings)
        m = len(P) // 2
        self.tmid, self.Xmid, self.Nmid = self.frames[m][1], self.frames[m][2], self.frames[m][3]

    # texel sizes
    def dims(self):
        H = max(1, round(self.length * T))
        Wf = max(1, round(self.wmax * T))
        Wl = max(1, round(self.dmax * T))
        r0, r1 = self.rings[0], self.rings[-1]
        cs = (max(1, round(r0[1] * T)), max(1, round(r0[2] * T)))
        ce = (max(1, round(r1[1] * T)), max(1, round(r1[2] * T)))
        return H, Wf, Wl, cs, ce

    def place(self, A):
        H, Wf, Wl, cs, ce = self.dims()
        W = 2 * Wf + 2 * Wl + 1 + max(cs[0], ce[0])
        Hh = max(H, cs[1] + ce[1] + 1)
        ox, oy = A.alloc(W, Hh)
        f = {"front": (ox, oy, Wf, H), "left": (ox + Wf, oy, Wl, H), "back": (ox + Wf + Wl, oy, Wf, H),
             "right": (ox + 2 * Wf + Wl, oy, Wl, H)}
        cx = ox + 2 * Wf + 2 * Wl + 1
        f["start"] = (cx, oy, cs[0], cs[1])
        f["end"] = (cx, oy + cs[1] + 1, ce[0], ce[1])
        A.faces[self.key] = f

    def coords(self, A, face):
        """Per-texel xx, nn, tt for one face (texel centres)."""
        fx, fy, fw, fh = A.faces[self.key][face]
        s = (np.arange(fw) + 0.5) / fw
        t = (np.arange(fh) + 0.5) / fh
        S, Tt = np.meshgrid(s, t)
        w = np.interp(Tt, self.tr, [r[1] for r in self.rings]) / self.wmax
        d = np.interp(Tt, self.tr, [r[2] for r in self.rings]) / self.dmax
        one = np.ones_like(S)
        if face == "front":
            return (2 * S - 1) / w, one, Tt
        if face == "left":
            return one, (1 - 2 * S) / d, Tt
        if face == "back":
            return (1 - 2 * S) / w, -one, Tt
        if face == "right":
            return -one, (2 * S - 1) / d, Tt
        tt = np.zeros_like(S) if face == "start" else np.ones_like(S)
        return 2 * S - 1, 2 * Tt - 1, tt     # caps: columns -X..+X, rows -N..+N

    def axes(self, face):
        """World directions of a face's (row, col) axes, for brush strokes."""
        t, X, N = self.tmid, self.Xmid, self.Nmid
        col = {"front": X, "left": -N, "back": -X, "right": N, "start": X, "end": X}[face]
        row = N if face in ("start", "end") else t
        return row, col

    def texture(self, A, seed):
        rng = np.random.default_rng(seed)
        for face in ("front", "left", "back", "right", "start", "end"):
            F = A.face(self.key, face)
            xx, nn, tt = self.coords(A, face)
            global RC
            RC = (face, *np.indices(xx.shape))      # painters read the face's texel rows / columns from here
            F[..., :3] = np.clip(self.paint(np.clip(xx, -1, 1), np.clip(nn, -1, 1), tt, face), 0, 1)
            F[..., 3] = 1 if self.alpha is None else self.alpha(xx, nn, tt, face)
            if self.hair is not None and self.density > 0:
                row, col = self.axes(face)
                dr, dc = self.hair.dot(row), self.hair.dot(col)
                n = math.hypot(dr, dc)
                if n < 0.35:
                    dr, dc = 1.0, 0.0
                else:
                    dr, dc = dr / n, dc / n
                brush(F, dr, dc, self.density, rng)

    def build(self, bm, uvl, dl, gi, A):
        rings = []
        for (c, t, X, N), r in zip(self.frames, self.rings):
            w, d = r[1] / 2, r[2] / 2
            vs = [bm.verts.new((c - X * w + N * d) * PX), bm.verts.new((c + X * w + N * d) * PX),
                  bm.verts.new((c + X * w - N * d) * PX), bm.verts.new((c - X * w - N * d) * PX)]
            for v in vs:
                for bone, wt in r[3].items():
                    if wt > 0:
                        v[dl][gi(bone)] = wt
            rings.append(vs)
        sides = (("front", 0, 1, lambda w, d: (0.5 - w / 2, 0.5 + w / 2)),
                 ("left", 1, 2, lambda w, d: (0.5 - d / 2, 0.5 + d / 2)),
                 ("back", 2, 3, lambda w, d: (0.5 - w / 2, 0.5 + w / 2)),
                 ("right", 3, 0, lambda w, d: (0.5 - d / 2, 0.5 + d / 2)))
        faces = []
        for i in range(len(rings) - 1):
            ra, rb = rings[i], rings[i + 1]
            ta, tb = self.tr[i], self.tr[i + 1]
            wa, da = self.rings[i][1] / self.wmax, self.rings[i][2] / self.dmax
            wb, db = self.rings[i + 1][1] / self.wmax, self.rings[i + 1][2] / self.dmax
            for fn, a, b, span in sides:
                f = bm.faces.new((ra[a], ra[b], rb[b], rb[a]))
                sa, sb = span(wa, da), span(wb, db)
                for loop, st in zip(f.loops, ((sa[0], ta), (sa[1], ta), (sb[1], tb), (sb[0], tb))):
                    loop[uvl].uv = A.uv(self.key, fn, *st)
                faces.append(f)
        for fn, ring in (("start", rings[0]), ("end", rings[-1])):
            f = bm.faces.new(ring)
            for loop, st in zip(f.loops, ((0, 1), (1, 1), (1, 0), (0, 0))):
                loop[uvl].uv = A.uv(self.key, fn, *st)
            faces.append(f)
        bmesh.ops.recalc_face_normals(bm, faces=faces)


def brush(F, dr, dc, density, rng, length=(2, 5)):
    """Short strokes along the hair: each picks up the colour where it starts and drags it on, a little darker or
    lighter, fading out, so colour edges get a hairy fringe and flat areas get a painted grain (not per-texel noise)."""
    H, W = F.shape[:2]
    n = int(H * W * density)
    for _ in range(n):
        r0, c0 = rng.uniform(0, H), rng.uniform(0, W)
        L = int(rng.integers(length[0], length[1] + 1))
        k = 0.93 if rng.random() < 0.6 else 1.06
        src = F[int(r0), int(c0), :3].copy() * k
        for i in range(L):
            r, c = int(r0 + i * dr), int(c0 + i * dc)
            if not (0 <= r < H and 0 <= c < W):
                break
            a = 0.65 * (1 - i / (L + 1))
            F[r, c, :3] = F[r, c, :3] * (1 - a) + src * a


# ------------------------------------------------------------------------------------------------------------ figure
class Fig:
    def __init__(self, name):
        self.name = name
        self.A = Atlas(name)
        self.tubes = []
        self.bones = {}
        self.groups = []

    def gi(self, bone):
        if bone not in self.groups:
            self.groups.append(bone)
        return self.groups.index(bone)

    def add(self, tube):
        self.tubes.append(tube)
        tube.place(self.A)
        return tube

    def finish(self, coll, tex_path, seed=1, post=None, rig=True):
        for i, tb in enumerate(self.tubes):
            tb.texture(self.A, seed * 100 + i)
        if post:
            post(self)               # small painted details at exact texels (eyes, nose, dewclaws)
        img = self.A.image(tex_path)
        bm = bmesh.new()
        uvl = bm.loops.layers.uv.new("UVMap")
        dl = bm.verts.layers.deform.verify()
        for tb in self.tubes:
            tb.build(bm, uvl, dl, self.gi, self.A)
        if not rig:
            me = bpy.data.meshes.new(self.name)
            bm.to_mesh(me)
            bm.free()
            ob = bpy.data.objects.new(self.name, me)
            coll.objects.link(ob)
            ob.data.materials.append(tex_mat(self.name, img))
            self.ob, self.ao = ob, ob
            return self
        arm = bpy.data.armatures.new(self.name + "_rig")
        ao = bpy.data.objects.new(self.name + "_rig", arm)
        coll.objects.link(ao)
        bpy.context.view_layer.objects.active = ao
        bpy.ops.object.mode_set(mode="EDIT")
        for n, (hd, tl, par) in self.bones.items():
            eb = arm.edit_bones.new(n)
            eb.head = Vector(hd) * PX
            eb.tail = Vector(tl) * PX
            eb.roll = 0
        for n, (hd, tl, par) in self.bones.items():
            if par:
                arm.edit_bones[n].parent = arm.edit_bones[par]
        bpy.ops.object.mode_set(mode="OBJECT")
        me = bpy.data.meshes.new(self.name)
        bm.to_mesh(me)
        bm.free()
        ob = bpy.data.objects.new(self.name, me)
        coll.objects.link(ob)
        for g in self.groups:
            ob.vertex_groups.new(name=g)
        ob.data.materials.append(tex_mat(self.name, img))
        ob.parent = ao
        mod = ob.modifiers.new("rig", "ARMATURE")
        mod.object = ao
        self.ob, self.ao = ob, ao
        return self

    def pose(self, rots, lift=0.0, ground=True):
        """rots: bone -> (x, y, z) degrees about the armature axes, relative to the parent (see design doc):
        +x tips a forward bone's end down and swings a down-hanging bone's end back (+Y); +z turns to the left."""
        ao = self.ao
        for pb in ao.pose.bones:
            pb.rotation_mode = "QUATERNION"
            pb.rotation_quaternion = (1, 0, 0, 0)
        for n, (x, y, z) in rots.items():
            pb = ao.pose.bones[n]
            rest = pb.bone.matrix_local.to_3x3()
            R = Euler((math.radians(x), math.radians(y), math.radians(z)), "YXZ").to_matrix()
            pb.rotation_quaternion = (rest.inverted() @ R @ rest).to_quaternion()
        bpy.context.view_layer.update()
        if ground:
            ao.location.z = 0
            bpy.context.view_layer.update()
            lo, hi = bounds(self.ob)
            ao.location.z += -lo.z + lift * PX
            bpy.context.view_layer.update()

    def point(self, bone, at=1.0):
        pb = self.ao.pose.bones[bone]
        return self.ao.matrix_world @ (pb.head + (pb.tail - pb.head) * at)


def bounds(ob):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = ob.evaluated_get(dg)
    P = [ev.matrix_world @ v.co for v in ev.data.vertices]
    lo = Vector([min(p[i] for p in P) for i in range(3)])
    hi = Vector([max(p[i] for p in P) for i in range(3)])
    return lo, hi


def tex_mat(name, img, rough=0.88):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    tx = nt.nodes.new("ShaderNodeTexImage")
    tx.image = img
    tx.interpolation = "Closest"
    bs = nt.nodes.new("ShaderNodeBsdfPrincipled")
    nt.links.new(tx.outputs["Color"], bs.inputs["Base Color"])
    nt.links.new(tx.outputs["Alpha"], bs.inputs["Alpha"])
    bs.inputs["Roughness"].default_value = rough
    for k in ("Specular IOR Level", "Specular"):
        if k in bs.inputs:
            bs.inputs[k].default_value = 0.25
            break
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(bs.outputs[0], o.inputs[0])
    for attr, val in (("surface_render_method", "DITHERED"), ("blend_method", "CLIP")):
        try:
            setattr(m, attr, val)
        except Exception:
            pass
    return m
