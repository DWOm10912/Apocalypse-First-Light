# Z0 r6 concept figures (docs/gameplay/afl_infected_reference_study_v1.md section 3).
# Same art language as the player's old cube body: boxes with shared joint rings (SkinMesh-style weights), 2 texels
# per model px, textures cut from the purchased zombie.png / r4-style skins so the brushwork is the bought artist's.
# The infected rig differs from the player's: two spine bones, a neck, a hinged jaw, and per-type limb sizes.
# Run inside Blender: exec(open(path).read()); build_all() then the render helpers at the bottom.
import bpy, bmesh, math, os
import numpy as np
from mathutils import Vector, Matrix, Euler

HERE = "D:/Minecraft Modding/AFL-vanilla-rework/design/creatures/infected_z0"
SKINS = {
    "s1": "D:/Minecraft Modding/AFL-vanilla-rework/src/main/resources/assets/minecraft/textures/entity/zombie/zombie.png",
    "s3": HERE + "/z0_s3.png",
    "c3": HERE + "/z0_c3.png",
}
PX = 1 / 16
T = 2            # texels per model px
ATLAS = 512

# ---------------------------------------------------------------------------------------------------------- textures
_skin_cache = {}
def skin(key):
    if key not in _skin_cache:
        im = bpy.data.images.load(SKINS[key], check_existing=True)
        w, h = im.size
        a = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1].copy()   # row 0 = image top
        _skin_cache[key] = a
    return _skin_cache[key]

def face_rect(face, u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}[face]

def resample(src, th, tw):
    """Nearest resample in whole texels: extra rows / columns are duplicates spread evenly, so the brushwork keeps
    its texel size instead of being smeared."""
    sh, sw = src.shape[:2]
    ys = np.minimum((np.arange(th) + 0.5) * sh / th, sh - 1e-3).astype(int)
    xs = np.minimum((np.arange(tw) + 0.5) * sw / tw, sw - 1e-3).astype(int)
    return src[ys][:, xs].copy()

class Atlas:
    def __init__(self, name):
        self.name = name
        self.px = np.zeros((ATLAS, ATLAS, 4), np.float32)
        self.x = self.y = self.row = 0
        self.parts = {}

    def alloc(self, w, h):           # texels
        if self.x + w > ATLAS:
            self.x, self.y, self.row = 0, self.y + self.row + 1, 0
        o = (self.x, self.y)
        self.x += w + 1
        self.row = max(self.row, h)
        assert self.y + h <= ATLAS, "atlas full"
        return o

    def add(self, key, size, src=None, rows=None):
        """size (w,h,d) px. src = (skin key, (u,v) on the 64 net, (sw,sh,sd)); rows = (r0, r1) takes only those
        px rows of the source's side faces (used to split the head at the mouth)."""
        w, h, d = size
        W, H = round(2 * (w + d) * T), round((d + h) * T)
        ox, oy = self.alloc(W, H)
        rects = {f: tuple(round(c * T) for c in face_rect(f, 0, 0, w, h, d)) for f in
                 ("top", "bottom", "right", "front", "left", "back")}
        if src:
            sk, (u, v), (sw, sh, sd) = src
            S = skin(sk)
            k = S.shape[0] // 64
            for f, (fx, fy, fw, fh) in rects.items():
                if fw == 0 or fh == 0:
                    continue
                su, sv, sfw, sfh = face_rect(f, u, v, sw, sh, sd)
                if rows and f not in ("top", "bottom"):
                    sv, sfh = sv + rows[0], rows[1] - rows[0]
                block = S[round(sv * k):round((sv + sfh) * k), round(su * k):round((su + sfw) * k)]
                self.px[oy + fy:oy + fy + fh, ox + fx:ox + fx + fw] = resample(block, fh, fw)
        self.parts[key] = (ox, oy, rects, size)
        return key

    def face(self, key, f):
        """Writable view of one face, row 0 = the face's image top (for sides: the top of the part)."""
        ox, oy, rects, _ = self.parts[key]
        fx, fy, fw, fh = rects[f]
        return self.px[oy + fy:oy + fy + fh, ox + fx:ox + fx + fw]

    def uv(self, key, f, s, t):
        """Face-local (s right, t down) in 0..1 -> Blender UV."""
        ox, oy, rects, _ = self.parts[key]
        fx, fy, fw, fh = rects[f]
        return ((ox + fx + s * fw) / ATLAS, 1 - (oy + fy + t * fh) / ATLAS)

    def image(self):
        name = "z0r6_" + self.name
        if name in bpy.data.images:
            bpy.data.images.remove(bpy.data.images[name])
        im = bpy.data.images.new(name, ATLAS, ATLAS, alpha=True)
        im.pixels[:] = self.px[::-1].ravel()
        im.filepath_raw = f"{HERE}/r6/z0r6_{self.name}_atlas.png"
        im.file_format = "PNG"
        os.makedirs(f"{HERE}/r6", exist_ok=True)
        im.save()
        return im

# ---- painting (colours in linear, as Blender stores pixels) -----------------------------------------------------------
def lin(hexs):
    c = [int(hexs[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    return np.array([((v + 0.055) / 1.055) ** 2.4 if v > 0.04045 else v / 12.92 for v in c], np.float32)

BLOOD_OLD = lin("#4B302B")
BLOOD_DRY = lin("#3A1C19")
BLOOD_WET = lin("#6E1A16")
TORN = lin("#6E4C43")
MOUTH = lin("#1A0E0D")
TOOTH = lin("#8C836C")

def rng(seed):
    return np.random.default_rng(seed)

def dab(F, x, y, col, k=1.0):
    h, w = F.shape[:2]
    if 0 <= x < w and 0 <= y < h and F[y, x, 3] > 0.5:
        F[y, x, :3] = F[y, x, :3] * (1 - k) + col * k

def drip(F, x, y, length, seed, col=BLOOD_WET, core=BLOOD_DRY, width=2):
    """A run of blood straight down: widest at the start, thinning, darker core, broken tail."""
    r = rng(seed)
    for i in range(length):
        wv = width if i < length * 0.45 else 1
        for j in range(wv):
            dab(F, x + j, y + i, core if (j == 0 and i > 1) else col, 0.92 if i < length - 1 else 0.6)
        if r.random() < 0.15:
            x += r.choice([-1, 1]) if i > 2 else 0

def soak(F, cx, cy, rx, ry, seed, col=BLOOD_OLD, k=0.85):
    """A soaked patch with a ragged edge (fabric wicking), dark in the middle."""
    r = rng(seed)
    h, w = F.shape[:2]
    for y in range(max(0, cy - ry - 2), min(h, cy + ry + 3)):
        for x in range(max(0, cx - rx - 2), min(w, cx + rx + 3)):
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            edge = 1 + r.normal(0, 0.18)
            if d < edge:
                dab(F, x, y, BLOOD_DRY if d < 0.3 else col, k * (1.0 if d < 0.7 else 0.7))

def tear(F, x, y, w, h, seed, inside=TORN):
    """A hole in cloth showing the wound beneath: torn rim one texel lighter, raw inside, blood under it."""
    r = rng(seed)
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            ex = min(xx - x, x + w - 1 - xx, yy - y, y + h - 1 - yy)
            if ex == 0 and r.random() < 0.45:
                continue
            dab(F, xx, yy, BLOOD_DRY if (ex > 0 and r.random() < 0.35) else inside, 1)
    for i in range(min(2, max(1, w // 3))):
        drip(F, x + 1 + int(r.integers(0, max(1, w - 2))), y + h - 1, int(r.integers(2, 5)), seed + i, col=BLOOD_OLD, width=1)

def tint(F, col, gain=1.0, dirt=0.0, seed=0, clamp=None):
    """Recolour cloth keeping its painted light/dark: target colour x (luma / mean luma) ^ gain."""
    m = F[..., 3] > 0.5
    if not m.any():
        return
    lum = F[..., :3] @ np.array([0.2126, 0.7152, 0.0722], np.float32)
    rel = (lum / max(lum[m].mean(), 1e-4)) ** gain
    if clamp:
        rel = np.clip(rel, *clamp)
    out = col[None, None, :] * rel[..., None]
    if dirt:
        r = rng(seed)
        n = r.random(F.shape[:2]).astype(np.float32) * dirt
        rows = np.linspace(0, 1, F.shape[0], dtype=np.float32)[:, None] * dirt
        out = out * (1 - n[..., None] * 0.5 - rows[..., None] * 0.6) + lin("#2E2418") * (n[..., None] * 0.5 + rows[..., None] * 0.6)
    F[m, :3] = np.clip(out[m], 0, 1)

RAW = lin("#8A4A40")

def bite(F, cx, cy, rx, ry, seed, runs=2):
    r = rng(seed)
    h, w = F.shape[:2]
    for y in range(max(0, cy - ry - 1), min(h, cy + ry + 2)):
        for x in range(max(0, cx - rx - 1), min(w, cx + rx + 2)):
            d = ((x - cx + 0.5) / (rx + 0.5)) ** 2 + ((y - cy + 0.5) / (ry + 0.5)) ** 2
            if d < 0.45:
                dab(F, x, y, RAW if r.random() < 0.6 else TORN, 1)
            elif d < 1.0:
                dab(F, x, y, BLOOD_DRY, 1)
            elif d < 1.5 and r.random() < 0.4:
                dab(F, x, y, BLOOD_OLD, 0.7)
    for i in range(runs):
        drip(F, cx - rx // 2 + i * max(1, rx), cy + ry, int(r.integers(3, 7)), seed + 9 + i, col=BLOOD_OLD, width=1)
    if not runs:
        for y in range(cy + ry + 1, min(h, cy + ry + 6)):
            for x in range(cx - rx, cx + rx + 1):
                if r.random() < 0.5 - 0.09 * (y - cy - ry):
                    dab(F, x + int(r.integers(-1, 2)), y, BLOOD_OLD, 0.6)

def gloves(F, rows, seed):
    """Hands soaked from the fingers up: solid at the ends, breaking up higher, in 2-texel blocks (one model px)."""
    r = rng(seed)
    h, w = F.shape[:2]
    for by in range(0, rows, 2):
        for bx in range(0, w, 2):
            p = 1.0 if by < rows - 4 else 0.55
            if r.random() < p:
                c = BLOOD_DRY if r.random() < 0.45 else BLOOD_WET
                for y in range(h - 1 - by, max(h - 3 - by, -1), -1):
                    for x in range(bx, min(bx + 2, w)):
                        dab(F, x, y, c, 0.9)

def fill(F, col, alpha=1.0):
    F[..., :3] = col
    F[..., 3] = alpha

# ---------------------------------------------------------------------------------------------------------- geometry
def column(bm, uvl, A, key, x0, x1, y0, y1, zs, ts, mat_idx=0, caps=True):
    """A box as one column of rings (shared vertices). zs top->bottom heights (m), ts the matching 0..1 down the
    side faces. Blender X = character's left, -Y = front."""
    rings = [[bm.verts.new(c) for c in ((x0, y0, z), (x1, y0, z), (x1, y1, z), (x0, y1, z))] for z in zs]
    sides = [(0, 1, "front"), (2, 3, "back"), (3, 0, "right"), (1, 2, "left")]
    for i in range(len(zs) - 1):
        for a, b, fn in sides:
            ra, rb = rings[i], rings[i + 1]
            f = bm.faces.new((ra[a], ra[b], rb[b], rb[a]))
            f.material_index = mat_idx
            for loop, (s, t) in zip(f.loops, ((0, ts[i]), (1, ts[i]), (1, ts[i + 1]), (0, ts[i + 1]))):
                loop[uvl].uv = A.uv(key, fn, s, t)
    if caps:
        t, b_ = rings[0], rings[-1]
        f = bm.faces.new((t[3], t[2], t[1], t[0]))      # top: image top = back
        f.material_index = mat_idx
        for loop, st in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
            loop[uvl].uv = A.uv(key, "top", *st)
        f = bm.faces.new((b_[0], b_[1], b_[2], b_[3]))  # bottom: image top = front
        f.material_index = mat_idx
        for loop, st in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
            loop[uvl].uv = A.uv(key, "bottom", *st)
    return rings

class Fig:
    """One infected: armature + one skinned mesh. Units px (converted to m on output); feet at z 0 at rest."""
    def __init__(self, name, spec, coll):
        self.name, self.s, self.coll = name, spec, coll
        self.A = Atlas(name)
        self.bm = bmesh.new()
        self.uvl = self.bm.loops.layers.uv.new("UVMap")
        self.dl = self.bm.verts.layers.deform.verify()
        self.groups = []
        self.bones = {}

    # -- rig ------------------------------------------------------------------------------------------------------
    def rig(self):
        s = self.s
        L = s["thigh"] + s["shin"]                 # hip height
        tw, th, td = s["torso"]
        top = L + th                               # neck base
        aw = s["arm_w"]
        ax = tw / 2 + aw / 2                        # arm centre x
        sh = top - 2                                # shoulder pivot (vanilla: 2 px under the torso top)
        b = self.bones
        b["pelvis"] = ((0, 0, L), (0, 0, L + 2), None)
        b["belly"] = ((0, 0, L), (0, 0, L + th / 2), "pelvis")
        b["chest"] = ((0, 0, L + th / 2), (0, 0, top), "belly")
        hz = top - s["neck_drop"]                  # head base
        b["neck"] = ((0, 0, hz - 1.5), (0, 0, hz), "chest")
        b["head"] = ((0, 0, hz), (0, 0, hz + s["head"][1]), "neck")
        hd = s["head"][2]
        b["jaw"] = ((0, -hd / 2 + s["jaw_d"], hz + s["jaw"]), (0, -hd / 2, hz + s["jaw"]), "head")
        for side, sx in (("R", -1), ("L", 1)):
            x = sx * ax
            b["upper." + side] = ((x, 0, sh), (x, 0, sh - s["upper"]), "chest")
            b["fore." + side] = ((x, 0, sh - s["upper"]), (x, 0, sh - s["upper"] - s["fore"]), "upper." + side)
            hx = sx * s["hip_x"]
            b["thigh." + side] = ((hx, 0, L), (hx, 0, L - s["thigh"]), "pelvis")
            b["shin." + side] = ((hx, 0, L - s["thigh"]), (hx, 0, 0), "thigh." + side)
        self.L, self.top, self.ax, self.sh, self.hz = L, top, ax, sh, hz

    def gi(self, bone):
        if bone not in self.groups:
            self.groups.append(bone)
        return self.groups.index(bone)

    def weigh(self, rings, weights):
        for ring, wd in zip(rings, weights):
            for v in ring:
                for bone, w in wd.items():
                    if w > 0:
                        v[self.dl][self.gi(bone)] = w

    def part(self, key, size, centre_xy, ztop, levels, weights, src=None, rows=None, inflate=0.0, mat=0):
        """A box part: size (w,h,d) px, centre (x,y) px, top z px; levels px down from the top (0 .. h) with one
        weight dict per level."""
        w, h, d = size
        if key not in self.A.parts:
            self.A.add(key, size, src, rows)
        cx, cy = centre_xy
        x0, x1 = (cx - w / 2 - inflate) * PX, (cx + w / 2 + inflate) * PX
        y0, y1 = (cy - d / 2 - inflate) * PX, (cy + d / 2 + inflate) * PX
        zs = [((ztop + inflate) if l == 0 else (ztop - h - inflate) if l == h else (ztop - l)) * PX for l in levels]
        rings = column(self.bm, self.uvl, self.A, key, x0, x1, y0, y1, zs, [l / h for l in levels], mat)
        self.weigh(rings, weights)
        return rings

    # -- standard body ------------------------------------------------------------------------------------------------
    def body(self, src_key, layers=("hat",)):
        s, L, top = self.s, self.L, self.top
        tw, th, td = s["torso"]
        hw, hh, hd = s["head"]
        jaw = s["jaw"]                               # jaw height px (bottom of the head)
        sk = src_key
        # head: the bought head as one box; the jaw is a chin block (narrower than the face, so the cheeks stay)
        # standing 0.25 px proud of the face and hinged inside the head; under it the face is painted as an open
        # mouth, so opening the jaw shows a mouth, not a slot across the whole head
        hz = self.hz
        jw, jh, jd = s["jaw_w"], jaw, s["jaw_d"]
        self.part("head", (hw, hh, hd), (0, 0), hz + hh, [0, hh], [{"head": 1}] * 2, (sk, (0, 0), (8, 8, 8)))
        if "hat" in layers:
            self.part("hat", (hw, hh, hd), (0, 0), hz + hh, [0, hh], [{"head": 1}] * 2,
                      (sk, (32, 0), (8, 8, 8)), inflate=0.5)
            self.A.face("hat", "bottom")[..., 3] = 0
        F = self.A.face
        hf = F("head", "front")
        r0, c0, c1 = round((hh - jh) * T), round((hw - jw) / 2 * T), round((hw + jw) / 2 * T)
        self.A.add("jaw", (jw, jh, jd))
        jf = F("jaw", "front")
        jf[...] = hf[r0:, c0:c1]
        for fn in ("left", "right"):
            J = F("jaw", fn)
            src = hf[r0:, c0:c0 + 2] if fn == "right" else hf[r0:, c1 - 2:c1]
            J[...] = resample(src, J.shape[0], J.shape[1])
        jb = F("jaw", "bottom")
        hb = F("head", "bottom")
        jb[...] = resample(hb[:2, c0:c1], jb.shape[0], jb.shape[1])
        fill(F("jaw", "back"), MOUTH)
        jt = F("jaw", "top")                       # image top = back; lower teeth along the front edge
        fill(jt, MOUTH * 1.5)
        r = rng(len(self.name))
        for x in range(1, jt.shape[1] - 1):
            if r.random() > 0.3:
                jt[-1, x, :3] = TOOTH * (0.7 + 0.3 * r.random())
        hole = hf[r0 - 1:, c0:c1]                  # the open mouth painted on the face under the chin block
        fill(hole, MOUTH)
        for x in range(1, hole.shape[1] - 1):      # upper teeth, uneven, one or two gone
            if r.random() > 0.25:
                hole[0, x, :3] = TOOTH * (0.75 + 0.3 * r.random())
                if r.random() < 0.35:
                    hole[1, x, :3] = TOOTH * 0.65
        hole[:, 0, :3] = hole[:, -1, :3] = BLOOD_DRY
        sk_c = hf[r0 - 2, c0 + 1, :3].copy()
        for yy, xx in ((-1, 0), (-1, -1), (0, 0), (0, -1)):
            hole[yy, xx, :3] = sk_c * 0.8
        self.part("jaw", (jw, jh, jd), (0, -hd / 2 + jd / 2 - 0.25), hz + jh, [0, jh], [{"jaw": 1}] * 2)
        # torso: chest / belly split at the middle ring, bottom ring on the pelvis
        self.part("torso", (tw, th, td), (0, 0), top, [0, th * 0.25, th * 0.5, th * 0.75, th],
                  [{"chest": 1}, {"chest": 1}, {"chest": 0.5, "belly": 0.5}, {"belly": 1}, {"pelvis": 1}],
                  (sk, (16, 16), (8, 12, 4)))
        # arms: top ring half on the chest (the old body's shoulder link), elbow ring shared
        up, fo = s["upper"], s["fore"]
        aw, ad = s["arm_w"], s["arm_d"]
        alen = up + fo + 2
        for side, sx, uvo in (("R", -1, (40, 16)), ("L", 1, (32, 48))):
            self.part("arm." + side, (aw, alen, ad), (sx * self.ax, 0), self.top,
                      [0, 2, 2 + up - 0.5, 2 + up + 0.5, alen],
                      [{"chest": 0.5, "upper." + side: 0.5}, {"upper." + side: 1}, {"upper." + side: 1},
                       {"fore." + side: 1}, {"fore." + side: 1}],
                      (sk, uvo, (s["src_arm_w"], 12, 4)))
        # legs: four hip rings 0/1/2/3 px blending pelvis -> thigh (SkinMesh.HIP), knee ring shared
        lw, ld = s["leg_w"], s["leg_d"]
        tl, sl = s["thigh"], s["shin"]
        for side, sx, uvo in (("R", -1, (0, 16)), ("L", 1, (16, 48))):
            th_, sh_ = "thigh." + side, "shin." + side
            self.part("leg." + side, (lw, L, ld), (sx * s["hip_x"], 0), L,
                      [0, 1, 2, 3, tl - 0.5, tl + 0.5, L],
                      [{"pelvis": 1}, {"pelvis": 2 / 3, th_: 1 / 3}, {"pelvis": 1 / 3, th_: 2 / 3}, {th_: 1},
                       {th_: 1}, {sh_: 1}, {sh_: 1}],
                      (sk, uvo, (4, 12, 4)))

    # -- output -------------------------------------------------------------------------------------------------------
    def finish(self, mat):
        arm = bpy.data.armatures.new(self.name + "_rig")
        ao = bpy.data.objects.new(self.name + "_rig", arm)
        self.coll.objects.link(ao)
        bpy.context.view_layer.objects.active = ao
        win = bpy.context.window_manager.windows[0]
        with bpy.context.temp_override(window=win, active_object=ao, object=ao, selected_objects=[ao]):
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
        self.bm.to_mesh(me)
        self.bm.free()
        ob = bpy.data.objects.new(self.name, me)
        self.coll.objects.link(ob)
        for g in self.groups:
            ob.vertex_groups.new(name=g)
        # bmesh deform layer indices follow self.groups order, which is the vertex group order
        for m in mat:
            ob.data.materials.append(m)
        ob.parent = ao
        mod = ob.modifiers.new("rig", "ARMATURE")
        mod.object = ao
        self.ob, self.ao = ob, ao
        return ao

    def pose(self, rots, lift=None):
        """rots: bone -> (x, y, z) degrees about the parent-relative armature axes.
        +x tips an up-pointing bone forward (to -Y) and swings a down-pointing one back;
        +y rolls toward the character's left; +z turns to the character's left."""
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
        dg = bpy.context.evaluated_depsgraph_get()
        ev = self.ob.evaluated_get(dg)
        zs = [(ev.matrix_world @ v.co).z for v in ev.data.vertices]
        ao.location.z += -min(zs) + (lift or 0)
        bpy.context.view_layer.update()

def tex_mat(name, img):
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
    bs.inputs["Roughness"].default_value = 0.92
    for k in ("Specular IOR Level", "Specular"):
        if k in bs.inputs:
            bs.inputs[k].default_value = 0.2
            break
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(bs.outputs[0], o.inputs[0])
    for attr, val in (("surface_render_method", "DITHERED"), ("blend_method", "CLIP")):
        try:
            setattr(m, attr, val)
        except Exception:
            pass
    return m

# ------------------------------------------------------------------------------------------------------------ types
VAN = dict(head=(8, 8, 8), jaw=2, jaw_w=4, jaw_d=4, neck_drop=0, torso=(8, 12, 4), arm_w=4, arm_d=4, src_arm_w=4, upper=4, fore=6,
           leg_w=4, leg_d=4, thigh=6, shin=6, hip_x=1.9)

def spec(**kw):
    s = dict(VAN)
    s.update(kw)
    return s

TYPES = {
    # common: the player's proportions, the bought skin as-is; it is the posture that makes it read
    "shambler": spec(),
    # runner: lanky, slim 3 px limbs, forearms +1.5 px, a little taller; fresh, a backpack still on
    "runner": spec(head=(8, 8, 7.5), torso=(7.5, 12, 3.5), arm_w=3, arm_d=3, upper=4.5, fore=7.5,
                   leg_w=3.5, leg_d=3.5, thigh=6.5, shin=6.5, hip_x=1.75),
    # heavy: wide and deep, thick limbs, short legs, small head for the body; road crew in a hi-vis vest
    "heavy": spec(neck_drop=1.5, torso=(11, 12, 6), arm_w=5, arm_d=5, upper=4, fore=6.5, leg_w=5, leg_d=5,
                  thigh=5.5, shin=5.5, hip_x=2.6),
}
SOURCE = {"shambler": "s1", "runner": "s3", "heavy": "c3"}

def dress_shambler(f):
    F = f.A.face
    jf = F("jaw", "front")
    for x, n in ((6, 4), (9, 3), (11, 2)):
        drip(jf, x, 1, n, 11 + x, col=BLOOD_OLD, width=1)
    tf = F("torso", "front")
    soak(tf, 9, 4, 4, 3, 21)
    drip(tf, 7, 6, 9, 22, col=BLOOD_OLD)
    drip(tf, 11, 5, 6, 23, col=BLOOD_OLD, width=1)
    for side in ("R", "L"):          # dried blood at the fingers
        a = F("arm." + side, "front")
        for x in range(a.shape[1]):
            for y in range(a.shape[0] - 3, a.shape[0]):
                dab(a, x, y, BLOOD_DRY, 0.35 + 0.15 * ((x + y) % 2))

def dress_runner(f):
    F = f.A.face
    # fresh: blood from the mouth down the chin, a soaked chest, a bite on the left shoulder
    jf = F("jaw", "front")
    for x, n in ((4, 4), (6, 4), (8, 4), (10, 3)):
        drip(jf, x, 0, n, 31 + x, width=1)
    tf = F("torso", "front")
    soak(tf, 7, 3, 5, 4, 32, col=BLOOD_WET, k=0.9)
    soak(tf, 8, 9, 3, 4, 33, col=BLOOD_OLD, k=0.75)
    for x, n in ((4, 12), (7, 15), (10, 10), (12, 7)):
        drip(tf, x, 6, n, 40 + x)
    tt = F("torso", "top")
    bite(tt, 12, 3, 2, 1, 34, runs=0)
    tl = F("torso", "left")
    bite(tl, 3, 2, 2, 2, 35, runs=1)
    al = F("arm.L", "front")
    for x, n in ((0, 9), (3, 6)):
        drip(al, x, 2, n, 50 + x, width=1)
    for side in ("R", "L"):          # bloody hands
        for fn in ("front", "back", "left", "right", "bottom"):
            a = F("arm." + side, fn)
            if fn == "bottom":
                fill(a, BLOOD_DRY)
            else:
                gloves(a, 8, 60 + len(fn) + len(side))
    # backpack straps painted on the shirt, front and over the shoulders
    for x0 in (2, tf.shape[1] - 4):
        for y in range(0, 15):
            for x in range(x0, x0 + 2):
                tf[y, x, :3] = lin("#2B2F22") * (0.85 + 0.25 * ((y // 2 + x) % 2))
        tf[14, x0:x0 + 2, :3] = lin("#77705C")      # buckle
    for x0 in (2, tt.shape[1] - 4):
        tt[:, x0:x0 + 2, :3] = lin("#2B2F22")

def add_runner_pack(f):
    s = f.s
    tw, th, td = s["torso"]
    key = "pack"
    f.A.add(key, (7, 8, 3), ("s1", (0, 16), (4, 12, 4)))
    for fn in ("top", "bottom", "front", "back", "left", "right"):
        tint(f.A.face(key, fn), lin("#66724C"), gain=1.6, dirt=0.35, seed=7, clamp=(0.72, 1.22))
    bk = f.A.face(key, "back")       # the side away from the body: a pocket and its seam
    bk[8:16, 2:12, :3] *= 0.82
    bk[8, 2:12, :3] = lin("#2A3020")
    bk[9, 6:8, :3] = lin("#77705C")
    soak(bk, 3, 12, 2, 3, 71, col=BLOOD_OLD, k=0.6)
    f.part(key, (7, 8, 3), (0, td / 2 + 1.5), f.top - 0.5, [0, 4, 8],
           [{"chest": 1}, {"chest": 1}, {"chest": 0.6, "belly": 0.4}])

def dress_heavy(f):
    F = f.A.face
    hf, ht = F("head", "front"), F("head", "top")
    ht[...] = resample(hf[2:8, 2:-2], ht.shape[0], ht.shape[1])
    for fn in ("left", "right", "back"):
        h_ = F("head", fn)
        h_[:3] = resample(hf[2:5, 2:-2], 3, h_.shape[1])
    jf = F("jaw", "front")
    for x, n in ((5, 3), (10, 4)):
        drip(jf, x, 1, n, 81 + x, col=BLOOD_OLD, width=1)
    # a chunk bitten out of the right forearm
    a = F("arm.R", "front")
    bite(a, 6, 14, 3, 2, 82, runs=0)
    # work boots: the last 2 px of each leg
    for side in ("R", "L"):
        for fn in ("front", "back", "left", "right", "bottom"):
            g = F("leg." + side, fn)
            n = g.shape[0] if fn == "bottom" else 4
            r = rng(90 + len(fn))
            for y in range(g.shape[0] - n, g.shape[0]):
                for x in range(g.shape[1]):
                    c = lin("#2C2119") * (0.8 + 0.4 * r.random())
                    if fn != "bottom" and y == g.shape[0] - n:
                        c = lin("#4A3626")
                    if fn != "bottom" and y == g.shape[0] - 1:
                        c = lin("#16110D")
                    g[y, x, :3] = c

def add_heavy_vest_and_belly(f):
    s = f.s
    tw, th, td = s["torso"]
    sk = SOURCE["heavy"]
    # belly: pushes out of the front, lower half, on the belly/pelvis bones
    f.part("belly", (tw - 2, 6.5, 3.5), (0, -td / 2 - 1.25), f.L + 6.5, [0, 3, 6.5],
           [{"belly": 1}, {"belly": 1}, {"belly": 0.5, "pelvis": 0.5}], (sk, (16, 16), (8, 12, 4)), rows=(6, 12))
    bf = f.A.face("belly", "front")
    soak(bf, 12, 4, 2, 2, 101, col=BLOOD_OLD, k=0.45)
    # vest: an outer box over the torso, hi-vis yellow-green, open down the front, two bands around and two up over
    # the shoulders front and back (ANSI class 2 pattern)
    f.A.add("vest", (tw, th, td), (sk, (16, 16), (8, 12, 4)))
    HV, REF = lin("#B6CC34"), lin("#A9ABA4")
    for fn in ("front", "back", "left", "right", "top"):
        tint(f.A.face("vest", fn), HV, gain=1.8, dirt=0.25, seed=110 + len(fn), clamp=(0.7, 1.2))
    f.A.face("vest", "bottom")[..., 3] = 0
    h = int(th * T)
    bands = (int(h * 0.52), int(h * 0.78))
    for fn in ("front", "back", "left", "right"):
        V = f.A.face("vest", fn)
        for b in bands:
            V[b:b + 2, :, :3] = REF * (0.8 + 0.2 * rng(b).random((2, V.shape[1], 1)).astype(np.float32))
            V[b - 1, :, :3] *= 0.75
    W = int(tw * T)
    for fn in ("front", "back"):
        V = f.A.face("vest", fn)
        for x0 in (int(W * 0.2), int(W * 0.8) - 2):
            V[:bands[0], x0:x0 + 2, :3] = REF * 0.9
    V = f.A.face("vest", "front")    # open front: a gap that widens over the belly
    for y in range(V.shape[0]):
        half = 2 if y < h * 0.45 else 5
        V[y, W // 2 - half:W // 2 + half, 3] = 0
    soak(V, 5, 18, 3, 4, 120, col=BLOOD_OLD, k=0.8)
    Vt = f.A.face("vest", "top")      # neck hole
    Vt[2:-2, int(W * 0.3):int(W * 0.7), 3] = 0
    f.part("vest", (tw, th, td), (0, 0), f.top, [0, th * 0.25, th * 0.5, th * 0.75, th],
           [{"chest": 1}, {"chest": 1}, {"chest": 0.5, "belly": 0.5}, {"belly": 1}, {"pelvis": 1}], inflate=0.4)

POSES = {
    # head pushed forward and tilted, one arm hanging, the other half-raised, feet dragging
    "shambler": {"pelvis": (10, 0, 0), "belly": (12, 0, -5), "chest": (18, 6, -8), "neck": (26, 0, 0),
                 "head": (-38, 16, 8), "jaw": (20, 0, 0),
                 "upper.R": (-14, 8, 0), "fore.R": (-10, 0, 0), "upper.L": (-66, -8, 12), "fore.L": (-30, 0, 0),
                 "thigh.L": (-36, 0, -4), "shin.L": (30, 0, 0), "thigh.R": (2, 0, 4), "shin.R": (30, 0, 0)},
    # mid-stride lunge: torso near 50 deg forward, face up at the target, jaw wide, one arm reaching
    "runner": {"pelvis": (20, 0, 6), "belly": (14, 0, 0), "chest": (14, 0, -6), "neck": (12, 0, 0),
               "head": (-64, -6, 0), "jaw": (28, 0, 0),
               "upper.R": (-72, 10, 0), "fore.R": (-14, 0, 0), "upper.L": (28, -10, 0), "fore.L": (-42, 0, 0),
               "thigh.L": (-68, 0, 0), "shin.L": (58, 0, 0), "thigh.R": (12, 0, 0), "shin.R": (62, 0, 0)},
    # heavy: belly forward, chest back, head sunk into the shoulders, arms hanging wide, wide stance
    "heavy": {"pelvis": (0, 0, 0), "belly": (-4, 0, 6), "chest": (14, -9, 6), "neck": (20, 0, 0),
              "head": (-20, -12, -6), "jaw": (16, 0, 0),
              "upper.R": (-6, 24, 0), "fore.R": (-14, 0, 0), "upper.L": (-30, -16, 0), "fore.L": (-36, 0, 0),
              "thigh.R": (-10, 8, 0), "shin.R": (10, 0, 0), "thigh.L": (6, -8, 0), "shin.L": (12, 0, 0)},
}

def build_type(key, coll, x):
    f = Fig("z0r6_" + key, TYPES[key], coll)
    f.rig()
    f.body(SOURCE[key])
    if key == "shambler":
        dress_shambler(f)
    elif key == "runner":
        dress_runner(f)
        add_runner_pack(f)
    else:
        dress_heavy(f)
        add_heavy_vest_and_belly(f)
    img = f.A.image()
    f.finish([tex_mat("z0r6_" + key, img)])
    f.ao.location.x = x
    return f

FIGS = {}
def build_all():
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o)
    coll = bpy.context.scene.collection
    for i, k in enumerate(TYPES):
        FIGS[k] = build_type(k, coll, (i - 1) * 1.6)
    return FIGS

def pose_all(rest=False):
    for k, f in FIGS.items():
        f.pose({} if rest else POSES[k])

# ------------------------------------------------------------------------------------------------------------ render
def stage():
    sc = bpy.context.scene
    for eng in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
        try:
            sc.render.engine = eng
            break
        except TypeError:
            pass
    try:
        sc.view_settings.view_transform = "Standard"
    except TypeError:
        pass
    sc.render.film_transparent = False
    w = bpy.data.worlds.new("z0r6_world")
    sc.world = w
    w.use_nodes = True
    bg = next(n for n in w.node_tree.nodes if n.type == "BACKGROUND")
    bg.inputs[0].default_value = (0.36, 0.38, 0.41, 1)
    bg.inputs[1].default_value = 0.55
    if "z0r6_sun" not in bpy.data.objects:
        sd = bpy.data.lights.new("z0r6_sun", "SUN")
        sd.energy = 3.2
        sd.angle = math.radians(6)
        so = bpy.data.objects.new("z0r6_sun", sd)
        sc.collection.objects.link(so)
        so.rotation_euler = (math.radians(48), 0, math.radians(-35))
        fd = bpy.data.lights.new("z0r6_rim", "SUN")
        fd.energy = 1.2
        fo = bpy.data.objects.new("z0r6_rim", fd)
        sc.collection.objects.link(fo)
        fo.rotation_euler = (math.radians(70), 0, math.radians(150))
        gm = bpy.data.meshes.new("z0r6_ground")
        bm = bmesh.new()
        bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=12)
        bm.to_mesh(gm)
        bm.free()
        go = bpy.data.objects.new("z0r6_ground", gm)
        sc.collection.objects.link(go)
        m = bpy.data.materials.new("z0r6_ground")
        m.use_nodes = True
        b = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
        b.inputs["Base Color"].default_value = (0.25, 0.26, 0.27, 1)
        b.inputs["Roughness"].default_value = 1
        gm.materials.append(m)
        cd = bpy.data.cameras.new("z0r6_cam")
        co = bpy.data.objects.new("z0r6_cam", cd)
        sc.collection.objects.link(co)
        sc.camera = co
    return sc

def bounds(f):
    dg = bpy.context.evaluated_depsgraph_get()
    ev = f.ob.evaluated_get(dg)
    P = [ev.matrix_world @ v.co for v in ev.data.vertices]
    lo = Vector([min(p[i] for p in P) for i in range(3)])
    hi = Vector([max(p[i] for p in P) for i in range(3)])
    return lo, hi

def shoot(f, direction, out, res=(900, 1200), lens=70, pad=1.12, target=None, height=None):
    """Frame the figure (or a point/height) from a direction with a perspective camera."""
    sc = stage()
    for k, g_ in FIGS.items():
        hide = g_ is not f
        g_.ob.hide_render = hide
        g_.ao.hide_render = hide
    lo, hi = bounds(f)
    c = (lo + hi) / 2 if target is None else Vector(target)
    size = max(hi.z - lo.z, (hi.x - lo.x) * res[1] / res[0], (hi.y - lo.y) * res[1] / res[0]) if height is None else height
    cam = sc.camera
    cam.data.lens = lens
    cam.data.sensor_fit = "VERTICAL"
    fov = 2 * math.atan(cam.data.sensor_height / 2 / lens)
    dist = size * pad / 2 / math.tan(fov / 2)
    d = Vector(direction).normalized()
    cam.location = c + d * dist
    cam.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.resolution_percentage = 100
    sc.render.filepath = out
    sc.render.image_settings.file_format = "PNG"
    bpy.ops.render.render(write_still=True)
    return out


def render_final():
    out = HERE + "/r6/"
    for k, f in FIGS.items():
        shoot(f, (-0.75, -1, 0.22), out + f"{k}_hero.png", res=(900, 1200))
        shoot(f, (-1, 0, 0.08), out + f"{k}_side.png", res=(600, 800))
        pb = f.ao.pose.bones["head"]
        c = f.ao.matrix_world @ ((pb.head + pb.tail) / 2)
        shoot(f, (-0.45, -1, 0.1), out + f"{k}_head.png", res=(600, 600), target=c, height=0.85, lens=85)
    # silhouettes: black on white, one fixed scale
    sc = bpy.context.scene
    black = bpy.data.materials.new("z0r6_black")
    black.use_nodes = True
    nt = black.node_tree
    nt.nodes.clear()
    e = nt.nodes.new("ShaderNodeEmission")
    e.inputs[0].default_value = (0, 0, 0, 1)
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    nt.links.new(e.outputs[0], o.inputs[0])
    old = {k: list(f.ob.data.materials) for k, f in FIGS.items()}
    for f in FIGS.values():
        f.ob.data.materials.clear()
        f.ob.data.materials.append(black)
    bg = next(n for n in sc.world.node_tree.nodes if n.type == "BACKGROUND")
    bgc, bgs = tuple(bg.inputs[0].default_value), bg.inputs[1].default_value
    bg.inputs[0].default_value = (1, 1, 1, 1)
    bg.inputs[1].default_value = 1
    vt = sc.view_settings.view_transform
    bpy.data.objects["z0r6_ground"].hide_render = True
    def sil(f, name):
        lo, hi = bounds(f)
        c = ((lo.x + hi.x) / 2, (lo.y + hi.y) / 2, 1.05)
        shoot(f, (-0.75, -1, 0.05), out + f"sil_{name}.png", res=(500, 700), target=c, height=2.3, lens=200, pad=1.0)
    sh = FIGS["shambler"]
    sh.pose({})
    sil(sh, "player_rest")
    sh.pose(POSES["shambler"])
    for k, f in FIGS.items():
        sil(f, k)
    for k, f in FIGS.items():
        f.ob.data.materials.clear()
        for m in old[k]:
            f.ob.data.materials.append(m)
    bg.inputs[0].default_value = bgc
    bg.inputs[1].default_value = bgs
    bpy.data.objects["z0r6_ground"].hide_render = False
    bpy.ops.wm.save_as_mainfile(filepath=HERE + "/z0_concept_r6.blend")
