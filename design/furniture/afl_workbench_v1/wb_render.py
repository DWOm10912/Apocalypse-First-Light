exec(open(r"C:/Users/willi/AppData/Local/Temp/claude/D--Minecraft-Modding-Apocalypse-First-Light/9acf29e6-9726-4be6-9d1a-cac1531ae610/scratchpad/wb_helpers.py").read())
OUT = r"C:/Users/willi/AppData/Local/Temp/claude/D--Minecraft-Modding-Apocalypse-First-Light/9acf29e6-9726-4be6-9d1a-cac1531ae610/scratchpad/wb_renders/"
sc = bpy.context.scene
st = coll("Stage")
for o in list(st.objects): bpy.data.objects.remove(o, do_unlink=True)

def link(o):
    st.objects.link(o); return o

# ground with a 1 m grid, a wall behind (y 1), the 2 x 1 x 2 cell cage
gm = bpy.data.materials.get("st_ground") or bpy.data.materials.new("st_ground")
gm.use_nodes = True; nt = gm.node_tree; b = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
for n in [n for n in nt.nodes if n.type in ("TEX_CHECKER",)]: nt.nodes.remove(n)
ck = nt.nodes.new("ShaderNodeTexChecker"); ck.inputs["Scale"].default_value = 1.0
ck.inputs["Color1"].default_value = (0.36, 0.36, 0.35, 1); ck.inputs["Color2"].default_value = (0.32, 0.32, 0.31, 1)
tc = nt.nodes.new("ShaderNodeTexCoord"); nt.links.new(tc.outputs["Object"], ck.inputs["Vector"])
nt.links.new(ck.outputs["Color"], b.inputs["Base Color"]); b.inputs["Roughness"].default_value = 0.9
me = bpy.data.meshes.new("ground"); bm = bmesh.new()
bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=4); bm.to_mesh(me); bm.free()
g = link(bpy.data.objects.new("ground", me)); g.location = (1, 0, 0); g.data.materials.append(gm)
# checker of 0.5 scale in object space: 1 m squares when size 4 (grid -4..4) -> use scale 4 on an 8 m plane
ck.inputs["Scale"].default_value = 2.0
wm = bpy.data.materials.get("st_wall") or bpy.data.materials.new("st_wall")
wm.use_nodes = True; next(n for n in wm.node_tree.nodes if n.type == "BSDF_PRINCIPLED").inputs["Base Color"].default_value = (0.5, 0.49, 0.47, 1)
me = bpy.data.meshes.new("wall"); bm = bmesh.new()
bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=2.5); bm.to_mesh(me); bm.free()
w = link(bpy.data.objects.new("wall", me)); w.location = (1, 1.0, 1.25); w.rotation_euler = (math.radians(90), 0, 0); w.data.materials.append(wm)
w.hide_render = False
cm = bpy.data.materials.get("st_cage") or bpy.data.materials.new("st_cage")
cm.use_nodes = True; cb = next(n for n in cm.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
cb.inputs["Base Color"].default_value = (0.2, 0.75, 1.0, 1); cb.inputs["Emission Color"].default_value = (0.2, 0.75, 1.0, 1); cb.inputs["Emission Strength"].default_value = 2.0
edges = []
for x in (0, 1, 2):
    for y in (0, 1):
        edges.append(((x, y, 0), (x, y, 2)))
    for z in (0, 1, 2):
        edges.append(((x, 0, z), (x, 1, z)))
for y in (0, 1):
    for z in (0, 1, 2):
        edges.append(((0, y, z), (2, y, z)))
_c = coll
for i, (p, q) in enumerate(edges):
    o = cyl(f"cage_{i}", p, q, 0.004, cm, 6)
    coll().objects.unlink(o); st.objects.link(o)
    o.data.materials.clear(); o.data.materials.append(cm)

# light: soft sun + a big area from the front-left, a grey world
for n in ("key", "fill", "sun"):
    o = bpy.data.objects.get(n)
    if o: bpy.data.objects.remove(o, do_unlink=True)
sun = bpy.data.lights.new("sun", "SUN"); sun.energy = 1.6; sun.angle = math.radians(8)
so = link(bpy.data.objects.new("sun", sun)); so.rotation_euler = (math.radians(50), 0, math.radians(-35))
key = bpy.data.lights.new("key", "AREA"); key.energy = 400; key.size = 2.5
ko = link(bpy.data.objects.new("key", key)); ko.location = (-1.2, -2.6, 2.6)
ko.rotation_euler = (Vector((1.0, 0.5, 1.0)) - ko.location).to_track_quat("-Z", "Y").to_euler()
fill = bpy.data.lights.new("fill", "AREA"); fill.energy = 150; fill.size = 3
fo = link(bpy.data.objects.new("fill", fill)); fo.location = (3.2, -2.0, 1.6)
fo.rotation_euler = (Vector((1.0, 0.5, 1.0)) - fo.location).to_track_quat("-Z", "Y").to_euler()
world = sc.world or bpy.data.worlds.new("World"); sc.world = world; world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
bg.inputs["Color"].default_value = (0.2, 0.21, 0.22, 1); bg.inputs["Strength"].default_value = 0.7

# render settings
sc.render.engine = "BLENDER_EEVEE"
try:
    sc.eevee.use_raytracing = True
except Exception: pass
sc.render.resolution_x = 1600; sc.render.resolution_y = 1200
sc.render.film_transparent = False
sc.view_settings.view_transform = "AgX"; sc.view_settings.exposure = -0.7
try:
    sc.view_settings.look = "AgX - Medium High Contrast"
except Exception: pass
sc.render.image_settings.file_format = "PNG"

def cam(name, loc, target, ortho=None, lens=40):
    o = bpy.data.objects.get(name)
    if o: bpy.data.objects.remove(o, do_unlink=True)
    c = bpy.data.cameras.new(name)
    if ortho:
        c.type = "ORTHO"; c.ortho_scale = ortho
    else:
        c.lens = lens
    o = link(bpy.data.objects.new(name, c)); o.location = loc
    o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    return o

views = {
    "persp": (cam("cam_persp", (-1.6, -3.0, 2.15), (1.0, 0.45, 0.95), lens=38), (1600, 1200)),
    "front": (cam("cam_front", (1.0, -6, 1.0), (1.0, 0, 1.0), ortho=2.7), (1000, 1000)),
    "side": (cam("cam_side", (6, 0.5, 1.0), (0, 0.5, 1.0), ortho=2.7), (700, 1000)),
}
import os
os.makedirs(OUT, exist_ok=True)
for k, (c, (rx, ry)) in views.items():
    sc.camera = c; sc.render.resolution_x = rx; sc.render.resolution_y = ry
    w.hide_render = (k == "side")
    sc.render.filepath = OUT + k + ".png"
    bpy.ops.render.render(write_still=True)
w.hide_render = False
print("rendered", os.listdir(OUT))
