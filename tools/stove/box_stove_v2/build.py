# Box Stove V2, build + bake: model.py's parts -> three meshes (body, the firebox inside: bricks + ash, door), Smart UV, Cycles
# bakes of the procedural materials, composed into AFL LabPBR textures (P9 v3 pattern, design/items/p9_01_v3/source/
# blender/game_bake.py + game_compose.py). Background Blender only:
#   blender -b --factory-startup --python tools/stove/box_stove_v2/build.py -- [--bake 2048] [--quick]
# Writes design/furniture/box_stove_v2/textures/box_stove_v2_{body,inside,door}{,_s,_n}.png and source/box_stove_v2.blend.
#   albedo (sRGB): baked base colour (edge wear, mottle, rust, heat tint come from the materials) x AO
#   _s: R smoothness = 1 - roughness, G F0 byte (iron 10, nickel 255 = metal), B 0, A 255
#   _n: RG tangent normal DirectX (G flipped from Blender), B AO, A 255
import bpy, bmesh, math, os, sys, time
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
OUTD = os.path.join(ROOT, "design", "furniture", "box_stove_v2")
os.makedirs(os.path.join(OUTD, "textures"), exist_ok=True); os.makedirs(os.path.join(OUTD, "source"), exist_ok=True)
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
def arg(k, d):
    return type(d)(argv[argv.index(k) + 1]) if k in argv else d
BAKE = arg("--bake", 2048)                          # body bake; the firebox inside at half, the door at a quarter; all written at half
QUICK = "--quick" in argv
AO_DIST, AO_K = 0.06, 0.55
GROUPS = {"body": ("body",), "inside": ("inside",), "door": ("door",)}
INSIDE_UV = 0.5

bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
G = {"__file__": os.path.join(HERE, "model.py")}
exec(compile(open(G["__file__"], encoding="utf-8").read(), G["__file__"], "exec"), G)
PARTS = G["PARTS"]; P, T, Vector = G["P"], G["T"], G["Vector"]
X0, X1, Y0, Y1, Z0, Z1 = (G["BODY"][k] for k in ("x0", "x1", "y0", "y1", "z0", "z1"))

# ---------------------------------------------------------------- realise and join
dg = bpy.context.evaluated_depsgraph_get()
col = bpy.data.collections.new("stove_v2"); sc.collection.children.link(col)
built = {}
for gname, prefixes in GROUPS.items():
    obs = []
    for o in PARTS:
        if o.name.split(":")[0] not in prefixes: continue
        me = bpy.data.meshes.new_from_object(o.evaluated_get(dg), preserve_all_data_layers=True, depsgraph=dg)
        t = bpy.data.objects.new("tmp_" + o.name, me); col.objects.link(t); obs.append(t)
    ob = obs[0]
    with bpy.context.temp_override(active_object=ob, selected_editable_objects=obs, selected_objects=obs):
        bpy.ops.object.join()
    ob.name = ob.data.name = "box_stove_v2_" + gname
    bm = bmesh.new(); bm.from_mesh(ob.data)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-6)
    bmesh.ops.triangulate(bm, faces=[f for f in bm.faces if len(f.verts) > 4])
    for f in bm.faces: f.smooth = True
    for e in bm.edges:
        e.smooth = len(e.link_faces) == 2 and e.calc_face_angle(0) <= math.radians(35)
    bm.to_mesh(ob.data); bm.free()
    # face-area weighted normals: the big flat plates stay flat next to their bevels (no X shading across long triangles)
    wn = ob.modifiers.new("wn", "WEIGHTED_NORMAL"); wn.mode = "FACE_AREA"; wn.keep_sharp = True; wn.weight = 50
    dg2 = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg2), preserve_all_data_layers=True, depsgraph=dg2)
    old = ob.data; ob.modifiers.clear(); ob.data = me; bpy.data.meshes.remove(old); me.name = ob.name
    built[gname] = ob
for o in PARTS + [o for o in sc.objects if o.name.startswith("cut")]:
    if o.name in bpy.data.objects: bpy.data.objects.remove(o)

def tris(ob):
    return sum(len(p.vertices) - 2 for p in ob.data.polygons)
for g, ob in built.items():
    print("TRIS", g, tris(ob), "polys", len(ob.data.polygons), "mats", [m.name for m in ob.data.materials])

# ---------------------------------------------------------------- UV
def unwrap(ob, thin_inside):
    for o in sc.objects: o.select_set(False)
    bpy.context.view_layer.objects.active = ob; ob.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT"); bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(50), island_margin=0.0, area_weight=0.0, scale_to_bounds=False)
    bpy.ops.object.mode_set(mode="OBJECT")
    # the firebox inside (bricks, ash, the inner faces of the walls) is seen only through the door: half density
    me = ob.data; uv = me.uv_layers.active.data
    lo = P(X0 + T - 0.002, Y0 - 0.002, Z1 - T + 0.002); hi = P(X1 - T + 0.002, Y1 + 0.002, Z0 + T - 0.002)
    lo, hi = Vector(map(min, lo, hi)), Vector(map(max, lo, hi))
    inside = set()
    for p in me.polygons:
        c = p.center
        if thin_inside and all(lo[i] <= c[i] <= hi[i] for i in range(3)):
            inside.add(p.index)
            for i in p.loop_indices: uv[i].uv *= INSIDE_UV
    bpy.ops.object.mode_set(mode="EDIT"); bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.pack_islands(rotate=True, margin=0.004)
    bpy.ops.object.mode_set(mode="OBJECT")
    # texel density report (outside faces): atlas texels per metre
    me = ob.data; uv = me.uv_layers.active.data; area3 = area2 = 0.0
    for p in me.polygons:
        if p.index in inside: continue
        area3 += p.area
        pts = [uv[i].uv for i in p.loop_indices]
        a = 0.0
        for k in range(1, len(pts) - 1):
            a += abs((pts[k][0] - pts[0][0]) * (pts[k + 1][1] - pts[0][1]) - (pts[k + 1][0] - pts[0][0]) * (pts[k][1] - pts[0][1])) / 2
        area2 += a
    return area3, area2
dens = {}
for g, ob in built.items():
    a3, a2 = unwrap(ob, g == "body")
    dens[g] = (a3, a2)
    print("UV", g, "surface m2", round(a3, 3), "uv fill", round(a2, 3))

# ---------------------------------------------------------------- bake
try: sc.render.engine = "CYCLES"
except TypeError as e: print(e)
prefs = bpy.context.preferences.addons["cycles"].preferences
for kind in ("OPTIX", "CUDA"):
    try:
        prefs.compute_device_type = kind; prefs.get_devices()
        for d in prefs.devices: d.use = True
        sc.cycles.device = "GPU"; break
    except Exception as e: print("no", kind, e)
sc.world = bpy.data.worlds.new("w"); sc.world.light_settings.distance = AO_DIST

MATS = sorted({m for ob in built.values() for m in ob.data.materials}, key=lambda m: m.name)
def bsdf(m): return next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
def out(m): return next(n for n in m.node_tree.nodes if n.type == "OUTPUT_MATERIAL")
targets = {}
for m in MATS:
    t = m.node_tree.nodes.new("ShaderNodeTexImage"); t.name = "bake_target"; m.node_tree.nodes.active = t; targets[m] = t

def image(name, res, cs, is_float, fill):
    im = bpy.data.images.new(name, res, res, alpha=False, float_buffer=is_float); im.colorspace_settings.name = cs
    im.generated_color = fill; return im

PASS = {"color": ("sRGB", False, (0, 0, 0, 1)), "rough": ("Non-Color", False, (0.6, 0.6, 0.6, 1)),
        "mat": ("Non-Color", False, (10 / 255, 0, 0, 1)), "normal": ("Non-Color", True, (0.5, 0.5, 1, 1)),
        "ao": ("Non-Color", False, (1, 1, 1, 1))}

def rewire(p):
    undo = []
    for m in MATS:
        if p not in ("color", "rough", "mat"): continue
        nt = m.node_tree; bs = bsdf(m)
        em = nt.nodes.new("ShaderNodeEmission"); sock = em.inputs["Color"]
        if p == "mat":
            sock.default_value = (m.get("afl_f0", 10) / 255.0, float(m.get("afl_metal", 0)), 0, 1)
        else:
            src = bs.inputs["Base Color" if p == "color" else "Roughness"]
            if src.links: nt.links.new(src.links[0].from_socket, sock)
            else:
                v = src.default_value; sock.default_value = tuple(v) if p == "color" else (v, v, v, 1)
        nt.links.new(em.outputs[0], out(m).inputs["Surface"]); undo.append((m, em))
    return undo
def restore(undo):
    for m, em in undo:
        m.node_tree.nodes.remove(em); m.node_tree.links.new(bsdf(m).outputs[0], out(m).inputs["Surface"])

bakes = {}
t0 = time.time()
for g, ob in built.items():
    res = {"body": BAKE, "inside": BAKE // 2, "door": BAKE // 4}[g]
    if QUICK: res //= 2
    bakes[g] = {p: image(f"bake_{g}_{p}", res, *PASS[p]) for p in PASS}
for p in PASS:
    undo = rewire(p)
    try:
        for g, ob in built.items():
            for m in MATS: targets[m].image = bakes[g][p]
            for o in sc.objects:
                o.hide_render = o is not ob; o.select_set(o is ob)
            bpy.context.view_layer.objects.active = ob
            kw = dict(margin=16, margin_type="EXTEND", use_clear=True, target="IMAGE_TEXTURES", use_selected_to_active=False)
            t1 = time.time()
            if p == "normal":
                sc.cycles.samples = 4; bpy.ops.object.bake(type="NORMAL", normal_space="TANGENT", **kw)
            elif p == "ao":
                sc.cycles.samples = 64 if QUICK else 256; bpy.ops.object.bake(type="AO", **kw)
            else:
                sc.cycles.samples = 4 if p == "color" else 1; bpy.ops.object.bake(type="EMIT", **kw)
            print("baked", p, g, round(time.time() - t1, 1), "s")
    finally:
        restore(undo)
print("bake total", round(time.time() - t0, 1), "s")
for o in sc.objects: o.hide_render = False

# ---------------------------------------------------------------- compose
def load(im):
    w = im.size[0]; a = np.empty(w * w * 4, np.float32); im.pixels.foreach_get(a); return a.reshape(w, w, 4)
def s2l(c): return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
def l2s(c):
    c = np.clip(c, 0, 1); return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(c, 1 / 2.4) - 0.055)
def box(a, r):
    if r <= 0: return a
    k = 2 * r + 1
    for ax in (0, 1):
        p = np.pad(a, [(r, r) if i == ax else (0, 0) for i in range(a.ndim)], mode="edge")
        c = np.cumsum(p, axis=ax, dtype=np.float64)
        c = np.concatenate([np.zeros_like(np.take(c, [0], axis=ax)), c], axis=ax)
        a = ((np.take(c, range(k, c.shape[ax]), axis=ax) - np.take(c, range(0, c.shape[ax] - k), axis=ax)) / k).astype(np.float32)
    return a
def coverage(ob, res):
    """Texels inside UV triangles (numpy raster at the output res), for the gutter fill."""
    me = ob.data; uv = me.uv_layers.active.data; m = np.zeros((res, res), bool)
    ys, xs = np.mgrid[0:res, 0:res]; px = (xs + 0.5) / res; py = (ys + 0.5) / res
    for p in me.polygons:
        pts = [uv[i].uv for i in p.loop_indices]
        for k in range(1, len(pts) - 1):
            a, b, c = pts[0], pts[k], pts[k + 1]
            x0 = max(int(min(a[0], b[0], c[0]) * res) - 1, 0); x1 = min(int(max(a[0], b[0], c[0]) * res) + 2, res)
            y0 = max(int(min(a[1], b[1], c[1]) * res) - 1, 0); y1 = min(int(max(a[1], b[1], c[1]) * res) + 2, res)
            if x1 <= x0 or y1 <= y0: continue
            X = px[y0:y1, x0:x1]; Y = py[y0:y1, x0:x1]
            d = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1])
            if abs(d) < 1e-12: continue
            l1 = ((b[0] - X) * (c[1] - Y) - (c[0] - X) * (b[1] - Y)) / d
            l2 = ((c[0] - X) * (a[1] - Y) - (a[0] - X) * (c[1] - Y)) / d
            l3 = 1 - l1 - l2; e = -0.5 / res / max(1e-6, 1)
            m[y0:y1, x0:x1] |= (l1 >= -0.002) & (l2 >= -0.002) & (l3 >= -0.002)
    return m

def write(path, rgba, cs):
    h, w = rgba.shape[:2]
    im = bpy.data.images.new("out_tmp", w, h, alpha=True, float_buffer=False); im.colorspace_settings.name = cs
    im.pixels.foreach_set((np.rint(np.clip(rgba, 0, 1) * 255) / 255.0).astype(np.float32).ravel())
    im.filepath_raw = path; im.file_format = "PNG"; im.save(); bpy.data.images.remove(im); print("wrote", path)

rng = np.random.default_rng(5)
for g, ob in built.items():
    B = bakes[g]
    base = s2l(load(B["color"])[..., :3]); rough = load(B["rough"])[..., 0]; mat = load(B["mat"])
    nrm = load(B["normal"])[..., :3] * 2 - 1; ao = load(B["ao"])[..., 0]
    f = 2; R = base.shape[0] // f
    def down(a):
        s = a.shape; return a.reshape(R, f, R, f, *s[2:]).mean(axis=(1, 3))
    alb = down(base * (1 - AO_K + AO_K * ao)[..., None])
    smooth = down(1 - rough); ao2 = down(ao)
    n2 = down(nrm); n2 /= np.maximum(np.linalg.norm(n2, axis=-1, keepdims=True), 1e-6)
    f0 = np.rint(mat[::f, ::f, 0] * 255)
    # a faint texel-space colour grain so vanilla is not flat (casting texture lives in the normal)
    gr = box(rng.standard_normal((R, R)).astype(np.float32), 1); gr = (gr - gr.mean()) / max(float(gr.std()), 1e-6)
    alb = s2l(np.clip(l2s(alb) * (1 + 0.025 * gr)[..., None], 0, 1))
    # gutters: bake margin already extends islands; clamp what is left far outside to the nearest values via blur fill
    cov = coverage(ob, R)
    fields = [alb, smooth, n2, ao2, f0.astype(np.float32)]
    for it in range(4):
        grown = cov.copy(); cnt = np.zeros(cov.shape, np.float32)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            cnt += (np.roll(cov, (dy, dx), (0, 1)) & ~cov)
        sel = cnt > 0
        for k, a in enumerate(fields):
            acc = np.zeros_like(a)
            for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                s = np.roll(cov, (dy, dx), (0, 1)) & ~cov; acc[s] += np.roll(a, (dy, dx), (0, 1))[s]
            a[sel] = acc[sel] / (cnt[sel][:, None] if a.ndim == 3 else cnt[sel])
        cov = grown | sel
    alb, smooth, n2, ao2, f0 = fields
    n2 /= np.maximum(np.linalg.norm(n2, axis=-1, keepdims=True), 1e-6)
    one = np.ones((R, R), np.float32)
    pre = os.path.join(OUTD, "textures", "box_stove_v2_" + g)
    write(pre + ".png", np.dstack([l2s(alb), one]), "sRGB")
    write(pre + "_s.png", np.dstack([smooth, np.rint(f0) / 255.0, 0 * one, one]), "Non-Color")
    write(pre + "_n.png", np.dstack([n2[..., 0] * 0.5 + 0.5, -n2[..., 1] * 0.5 + 0.5, ao2, one]), "Non-Color")
    a3, a2 = dens[g]
    print("DENSITY", g, "texels/m", round(R * math.sqrt(a2 / a3), 1), "at", R)

for o in list(sc.objects):
    if o.name not in [b.name for b in built.values()]: bpy.data.objects.remove(o)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUTD, "source", "box_stove_v2.blend"))
print("saved")
