# Female body V1 on the old body (SkinMesh C-min, rigs/afl_humanoid_v1.json): the male old body as the game builds it
# (vanilla boxes, vanilla 64 x 64 UV net, the second layer 0.25 / 0.5 px out), and female variants built on it by
# torso ring profiles only (bones, lengths, pivots and every animation unchanged). Offline review sheet, background
# Blender only:
#   blender -b --factory-startup --python tools/characters/old_body_female_v1.py -- [--quick]
# Out: design/characters/female_body_v1/ (sheet + the .blend).
import bpy, math, os, sys
import numpy as np
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "design", "characters", "female_body_v1"); os.makedirs(OUT, exist_ok=True)
# 128 skins in B's infected r6 style (tools/characters/old_body_skin_paint_v1.py); the UV is in 64-net px / 64, any size fits
SKINS = os.path.join(OUT, "skins")
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
QUICK = "--quick" in argv
TEX = 64.0

# ---------------------------------------------------------------- variants
# A torso profile: rings (level px down from the shoulders 0 .. 12, half width x, front z (negative), back z), Java model
# px. The male torso is the vanilla box: x +-4, z -2 .. 2. The chest / pelvis joint ring sits at level 6 (cut [6]).
MALE = [(0, 4, -2, 2), (6, 4, -2, 2), (12, 4, -2, 2)]
WAIST = [(0, 4, -2, 2), (4, 4, -2, 2), (6, 3.75, -1.875, 1.875), (7.5, 3.5, -1.75, 1.75), (9.5, 4, -2, 2), (12, 4, -2, 2)]
# The chest (C): its own small part on the chest, not a torso-wide wedge (r1 read as a bolster, user 2026-10-27). Under a
# T-shirt the cloth slopes out below the collarbone to the fullest point and then hangs from it, easing back into the
# chest above the waist: no tuck underneath (a tucked underside is what made the roll). About 6 px across, narrower at the front.
# Rings (level px down from the shoulders, half width at the chest, depth out of the torso's front); the texture is the
# T-shirt's front, projected straight on, so the shirt runs over it.
BUST = [(1.0, 2.7, 0.03), (2.0, 2.95, 0.38), (2.9, 3.05, 0.62), (3.6, 3.05, 0.7), (4.3, 3.0, 0.62), (5.0, 2.9, 0.38),
        (5.6, 2.75, 0.12), (5.9, 2.6, 0.03)]
# r2 (user 2026-10-27: "胸的位置是不是有点不对", marking 2-5.5 px down): higher, fullest at 3-4 px, back in by 6 px, so all
# of it is above the chest / pelvis joint ring (level 6) and follows the chest. The underside still slopes (0.67 px over
# 2.3 px), no tuck.
FRONT_IN = 0.45   # the front face this much narrower each side than the back

# r3 (user 2026-10-27: "侧面看还行，正面看感觉有点奇怪，还是有点像枕头"): one block reads as a cushion from the front
# (straight sides, a straight underside, the shade under it one level band). Two forms instead, with r2's side profile
# through each form's middle: the shirt bridges the shallow valley between them and the lower edge is two arcs, lowest
# under each form, higher in the middle and at the sides. D smooth (0.2 px grid), E faceted (0.5 px, closer to the boxes).
LOBE_X, LOBE_HALF = 1.55, 3.05   # a form's middle (px from the centre line); where the chest ends at the sides
APEX, LOW = 3.6, 5.9             # the fullest level (px down); where a form's middle meets the torso again
BRIDGE = 0.8                     # the shirt across the valley: this share of the forms' depth
RISE_IN, RISE_OUT = 1.0, 1.3     # how much higher the lower edge ends in the middle / at the sides

def side_profile(l):
    """r2's depth along a form's middle (BUST)."""
    if l <= BUST[0][0] or l >= BUST[-1][0]: return 0.0
    for (l0, _, d0), (l1, _, d1) in zip(BUST, BUST[1:]):
        if l0 <= l <= l1: return d0 + (d1 - d0) * (l - l0) / (l1 - l0)
    return 0.0

def chest_depth(x, l):
    ax = abs(x)
    if ax >= LOBE_HALF: return 0.0
    if ax >= LOBE_X:
        u = (ax - LOBE_X) / (LOBE_HALF - LOBE_X)
        across, low = math.sqrt(max(0.0, 1 - u * u)), LOW - RISE_OUT * u * u
    else:
        u = 1 - ax / LOBE_X
        across, low = 1 - (1 - BRIDGE) * u * u, LOW - RISE_IN * u ** 1.5
    if l > APEX:   # below the fullest point the form ends at `low`: squeeze r2's lower profile into it
        l = APEX + (l - APEX) * (LOW - APEX) / max(0.2, low - APEX)
    return side_profile(l) * across

VARIANTS = [
    # (label, skin, slim arms, torso profile, chest: None / "block" (r2) / grid step px)
    ("B", "female_v1", True, WAIST, None),
    ("C", "female_v1", True, WAIST, "block"),
    ("D", "female_v1", True, WAIST, 0.2),
    ("E", "female_v1", True, WAIST, 0.5),
]

# ---------------------------------------------------------------- the old body's mesh (skeleton/SkinMesh at rest)
def j2b(x, y, z):
    """Java model px (y down, front -z, ground at y 24) -> Blender (z up, front -y)."""
    return Vector((x, z, 24 - y))

def part(verts, faces, uvs, u, v, w, h, d, rings, g):
    """One vanilla box as SkinMesh emits it: rings (level 0..h, x0, x1, z0, z1, top y), UV after ModelPart.Cube."""
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    f10, f11 = v, v + d
    R = []
    for (lvl, x0, x1, z0, z1, y) in rings:
        R.append((lvl, [j2b(x0, y, z0), j2b(x1, y, z0), j2b(x1, y, z1), j2b(x0, y, z1)]))
    centre = sum((p for _, r in R for p in r), Vector()) / (4 * len(R))
    def quad(p0, a0, p1, a1, p2, a2, p3, a3):
        base = len(verts)
        verts.extend([p0, p1, p2, p3])
        uvs.extend([a0, a1, a2, a3])
        # wound to face out of the part (checked against its middle; the bust's slopes included)
        n = (p2 - p0).cross(p3 - p1)
        mid = (p0 + p1 + p2 + p3) / 4
        faces.append((base, base + 1, base + 2, base + 3) if n.dot(mid - centre) >= 0 else (base + 3, base + 2, base + 1, base))
    def uvp(uu, vv): return (uu / TEX, 1 - vv / TEX)
    t, e = R[0][1], R[-1][1]
    quad(t[2], uvp(f6, f10), t[3], uvp(f5, f10), t[0], uvp(f5, f11), t[1], uvp(f6, f11))
    quad(e[1], uvp(f7, f11), e[0], uvp(f6, f11), e[3], uvp(f6, f10), e[2], uvp(f7, f10))
    for s in range(len(R) - 1):
        (la, a), (lc, c) = R[s], R[s + 1]
        va, vb = f11 + la, f11 + lc
        for (ca, ua, cb, ub) in ((0, f5, 3, f4), (1, f6, 0, f5), (2, f8, 1, f6), (3, f9, 2, f8)):
            quad(a[ca], uvp(ua, va), a[cb], uvp(ub, va), c[cb], uvp(ub, vb), c[ca], uvp(ua, vb))

def box_rings(x0, x1, y0, y1, z0, z1, g, levels=None):
    h = y1 - y0
    stretch = (h + 2 * g) / h
    return [(l, x0 - g, x1 + g, z0 - g, z1 + g, y0 - g + l * stretch) for l in (levels or [0, h])]

def torso_rings(profile, g):
    stretch = (12 + 2 * g) / 12
    return [(l, -hw - g, hw + g, zf - g, zb + g, 0 - g + l * stretch) for (l, hw, zf, zb) in profile]

def torso_front_z(profile, level):
    for (l0, _, z0, _), (l1, _, z1, _) in zip(profile, profile[1:]):
        if l0 <= level <= l1: return z0 + (z1 - z0) * (level - l0) / (l1 - l0)
    return profile[-1][2]

def bust_part(verts, faces, uvs, profile):
    """The chest part: a loft through BUST, closed at both ends; UV = the torso's front face straight on (64-net px)."""
    def uv(x, lvl): return ((20 + (x + 4)) / TEX, 1 - (20 + lvl) / TEX)
    R = []
    for (lvl, hw, dep) in BUST:
        z = torso_front_z(profile, lvl) + 0.01   # just inside the shirt's front: no gap where it starts and ends
        fw = max(0.3, hw - FRONT_IN * dep / 0.7)
        R.append((lvl, [(-hw, z), (hw, z), (fw, z - dep), (-fw, z - dep)]))
    centre = Vector((0, -2.3, 24 - 3.4))
    def quad(ps, ts):
        base = len(verts)
        verts.extend(ps); uvs.extend(ts)
        n = (ps[2] - ps[0]).cross(ps[3] - ps[1]); mid = sum(ps, Vector()) / 4
        faces.append((base, base + 1, base + 2, base + 3) if n.dot(mid - centre) >= 0 else (base + 3, base + 2, base + 1, base))
    for (la, a), (lb, b) in zip(R, R[1:]):
        for i in range(4):
            j = (i + 1) % 4
            if i == 0: continue   # the back (on the chest) is never seen
            ps = [j2b(a[i][0], la, a[i][1]), j2b(a[j][0], la, a[j][1]), j2b(b[j][0], lb, b[j][1]), j2b(b[i][0], lb, b[i][1])]
            quad(ps, [uv(a[i][0], la), uv(a[j][0], la), uv(b[j][0], lb), uv(b[i][0], lb)])
    for lvl, ring in (R[0], R[-1]):
        quad([j2b(x, lvl, z) for x, z in ring], [uv(x, lvl) for x, _ in ring])

def chest_grid(verts, faces, uvs, profile, step):
    """D / E: the chest as a height field over the torso's front (chest_depth), quads facing front; UV straight on."""
    def uv(x, lvl): return ((20 + (x + 4)) / TEX, 1 - (20 + lvl) / TEX)
    n = round(2 * LOBE_HALF / step)
    xs = [-LOBE_HALF + i * 2 * LOBE_HALF / n for i in range(n + 1)]
    m = round((BUST[-1][0] - BUST[0][0]) / step)
    ls = [BUST[0][0] + j * (BUST[-1][0] - BUST[0][0]) / m for j in range(m + 1)]
    D = [[chest_depth(x, l) for l in ls] for x in xs]
    P = [[j2b(x, l, torso_front_z(profile, l) + 0.01 - D[i][j]) for j, l in enumerate(ls)] for i, x in enumerate(xs)]
    for i in range(n):
        for j in range(m):
            if max(D[i][j], D[i + 1][j], D[i + 1][j + 1], D[i][j + 1]) < 0.004: continue
            ps = [P[i][j], P[i + 1][j], P[i + 1][j + 1], P[i][j + 1]]
            ts = [uv(xs[i], ls[j]), uv(xs[i + 1], ls[j]), uv(xs[i + 1], ls[j + 1]), uv(xs[i], ls[j + 1])]
            base = len(verts)
            verts.extend(ps); uvs.extend(ts)
            nrm = (ps[2] - ps[0]).cross(ps[3] - ps[1])
            faces.append((base, base + 1, base + 2, base + 3) if nrm.y < 0 else (base + 3, base + 2, base + 1, base))

def body_mesh(name, slim, profile, overlay, chest=None):
    verts, faces, uvs = [], [], []
    g = 0.25 if overlay else 0.0
    gh = 0.5 if overlay else 0.0
    # head (hat 0.5 out)
    part(verts, faces, uvs, 32 if overlay else 0, 0, 8, 8, 8, box_rings(-4, 4, -8, 0, -4, 4, gh), gh)
    # torso (jacket)
    part(verts, faces, uvs, 16, 32 if overlay else 16, 8, 12, 4, torso_rings(profile, g), g)
    if chest == "block" and not overlay: bust_part(verts, faces, uvs, profile)
    elif chest and not overlay: chest_grid(verts, faces, uvs, profile, chest)
    aw = 3 if slim else 4
    # right arm (x -), left arm (x +)
    part(verts, faces, uvs, 40, 32 if overlay else 16, aw, 12, 4, box_rings(-4 - aw, -4, 0, 12, -2, 2, g), g)
    part(verts, faces, uvs, 48 if overlay else 32, 48, aw, 12, 4, box_rings(4, 4 + aw, 0, 12, -2, 2, g), g)
    # legs
    part(verts, faces, uvs, 0, 32 if overlay else 16, 4, 12, 4, box_rings(-3.9, 0.1, 12, 24, -2, 2, g), g)
    part(verts, faces, uvs, 0 if overlay else 16, 48, 4, 12, 4, box_rings(-0.1, 3.9, 12, 24, -2, 2, g), g)
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(p) for p in verts], [], faces)
    uvl = me.uv_layers.new(name="UV")
    for poly in me.polygons:
        for li, vi in zip(poly.loop_indices, poly.vertices):
            uvl.data[li].uv = uvs[vi]
    me.update()
    return me

# ---------------------------------------------------------------- scene
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
for eng in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
    try:
        sc.render.engine = eng
        break
    except TypeError:
        pass
sc.render.film_transparent = False
sc.view_settings.view_transform = "Standard"
world = bpy.data.worlds.new("W"); sc.world = world; world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
bg.inputs[0].default_value = (0.36, 0.38, 0.41, 1); bg.inputs[1].default_value = 0.55   # B r6 stage()

MATS = {}
def skin_mat(skin):
    if skin in MATS: return MATS[skin]
    m = bpy.data.materials.new(skin); m.use_nodes = True; nt = m.node_tree; N = nt.nodes; L = nt.links
    b = next(n for n in N if n.type == "BSDF_PRINCIPLED")
    t = N.new("ShaderNodeTexImage"); t.image = bpy.data.images.load(os.path.join(SKINS, skin + ".png")); t.interpolation = "Closest"
    L.new(t.outputs["Color"], b.inputs["Base Color"]); L.new(t.outputs["Alpha"], b.inputs["Alpha"])
    b.inputs["Roughness"].default_value = 0.85
    try: m.surface_render_method = "DITHERED"
    except Exception: pass
    try: m.blend_method = "HASHED"
    except Exception: pass
    m.use_backface_culling = False
    MATS[skin] = m
    return m

SPACING = 16
FIG = []   # each variant's objects (one shown per shot)
for i, (label, skin, slim, profile, chest) in enumerate(VARIANTS):
    FIG.append([])
    x = (i - (len(VARIANTS) - 1) / 2) * SPACING
    for overlay in (False, True):
        me = body_mesh(f"{label}_{'layer2' if overlay else 'base'}", slim, profile, overlay, chest)
        ob = bpy.data.objects.new(me.name, me); sc.collection.objects.link(ob)
        ob.location.x = x
        me.materials.append(skin_mat(skin))
        FIG[-1].append(ob)

floor = bpy.data.meshes.new("floor"); floor.from_pydata([(-80, -60, 0), (80, -60, 0), (80, 60, 0), (-80, 60, 0)], [], [(0, 1, 2, 3)])
fo = bpy.data.objects.new("floor", floor); sc.collection.objects.link(fo)
fm = bpy.data.materials.new("floor"); fm.use_nodes = True
next(n for n in fm.node_tree.nodes if n.type == "BSDF_PRINCIPLED").inputs["Base Color"].default_value = (0.32, 0.33, 0.35, 1)
floor.materials.append(fm)

sun = bpy.data.lights.new("key", "SUN"); sun.energy = 3.2; sun.angle = math.radians(6)
so = bpy.data.objects.new("key", sun); sc.collection.objects.link(so); so.rotation_euler = (math.radians(48), 0, math.radians(-35))
fill = bpy.data.lights.new("fill", "SUN"); fill.energy = 1.2
fl = bpy.data.objects.new("fill", fill); sc.collection.objects.link(fl); fl.rotation_euler = (math.radians(70), 0, math.radians(150))

cam = bpy.data.cameras.new("cam"); cam.lens = 70; cam.sensor_fit = "HORIZONTAL"
co = bpy.data.objects.new("cam", cam); sc.collection.objects.link(co); sc.camera = co
RES = (1000, 560) if QUICK else (2000, 1120)

def shoot(path, direction, target, width, res=RES, lens=70):
    """A perspective camera from a direction, framing `width` px across at the target (B's r6 shoot())."""
    cam.lens = lens
    fov = 2 * math.atan(cam.sensor_width / 2 / lens)
    d = Vector(direction).normalized()
    co.location = Vector(target) + d * (width / 2 / math.tan(fov / 2))
    co.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    return path

def solo(i):
    for k, objs in enumerate(FIG):
        for ob in objs: ob.hide_render = k != i

VIEWS = [("three_quarter", (-0.75, -1, 0.22)), ("front", (0, -1, 0.06)), ("side", (-1, 0, 0.06)), ("back", (0.2, 1, 0.1))]
TILE = (RES[0] // 4, RES[1] * 2 // 3)
rows_p = []
for n, d in VIEWS:
    row = []
    for i in range(len(VARIANTS)):
        solo(i)
        x = (i - (len(VARIANTS) - 1) / 2) * SPACING
        row.append(shoot(os.path.join(OUT, f"_{n}{i}.png"), d, (x, 0, 16), 24, res=TILE, lens=70))
    rows_p.append(row)
# the chests close up: C (r2), D and E, straight on and three-quarter
heads = []
for idx in (1, 2, 3):
    solo(idx)
    x = (idx - (len(VARIANTS) - 1) / 2) * SPACING
    for d in ((0, -1, 0.25), (-0.75, -1, 0.15)):
        heads.append(shoot(os.path.join(OUT, f"_chest{idx}{len(heads)}.png"), d, (x, -2, 20.6), 11, res=(TILE[0] * 2 // 3, TILE[0] * 2 // 3), lens=85))

# stack the views into one sheet: the four lineup views, then the heads in a row
def load(p):
    im = bpy.data.images.load(p)
    return np.array(im.pixels[:], dtype=np.float32).reshape(im.size[1], im.size[0], 4)
rows = [np.concatenate([load(p) for p in r], axis=1) for r in rows_p] + [np.concatenate([load(p) for p in heads], axis=1)]
W = max(r.shape[1] for r in rows)
rows = [np.pad(r, ((0, 0), (0, W - r.shape[1]), (0, 0)), mode="edge") for r in rows]
sheet = np.concatenate(rows[::-1], axis=0)   # Blender pixels run bottom-up: the first view on top
out = bpy.data.images.new("sheet", sheet.shape[1], sheet.shape[0], alpha=True)
out.pixels = sheet.ravel().tolist()
out.filepath_raw = os.path.join(OUT, "female_body_v1_r3.png"); out.file_format = "PNG"; out.save()
shots = [p for r in rows_p for p in r] + heads
for p in shots: os.remove(p)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "old_body_female_v1.blend"))
print("SHEET", out.filepath_raw)
