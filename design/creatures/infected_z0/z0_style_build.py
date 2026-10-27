# Z0 r4 review scene: the purchased zombie.png and six variants derived from it (tools/build-infected-z0-variants.mjs).
# Run inside Blender 5.x:
#   exec(open(r"<repo>/design/creatures/infected_z0/z0_style_build.py", encoding="utf-8").read())
# Builds a fresh file, renders z0_style_<ROUND>.png (fronts, backs, heads) and z0_style_faces_<ROUND>.png (heads large)
# next to this script and saves z0_style.blend.
ROUND = "r4"
import os
HERE = r"D:\Minecraft Modding\AFL-vanilla-rework\design\creatures\infected_z0"
exec(open(os.path.join(HERE, "z0_blender_lib.py"), encoding="utf-8").read())

REPO = r"D:\Minecraft Modding\AFL-vanilla-rework"
FRESH = [
    ("s1", os.path.join(REPO, "src/main/resources/assets/minecraft/textures/entity/zombie/zombie.png"), False, "1 原图"),
    ("s2", "z0_s2.png", False, "2 短发 红 T 恤"),
    ("s3", "z0_s3.png", False, "3 金发 灰 T 恤"),
    ("s4", "z0_s4.png", False, "4 寸头 格子衬衫"),
    ("s5", "z0_s5.png", False, "5 秃顶 毛衣"),
    ("s6", "z0_s6.png", False, "6 刷手服"),
    ("s7", "z0_s7.png", False, "7 卫衣"),
]

bpy.ops.wm.read_homefile(use_empty=True)
sc = bpy.context.scene
FONT = None
for f in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf"):
    try:
        FONT = bpy.data.fonts.load(f)
        break
    except Exception:
        pass
MATS = {k: skin_mat(k, os.path.join(HERE, f)) for k, f, _, _ in FRESH}
BRIM = brim_mat()
coll = sc.collection

DX = 1.55
for i, (k, f, slim, label) in enumerate(FRESH):
    x = 0.8 + i * DX
    figure(k, None, slim, coll, (x, 0, 3.0), 0)                                   # front
    figure(k, None, slim, coll, (x, 0, 0.55), 180)                                # back
    figure(k, None, slim, coll, (x, 0, -0.25), -32, scale=1.75, head_only=True)   # head, three-quarter
    text(coll, label, (x - 0.55, -1.5, 5.45), 0.24)

world = bpy.data.worlds.new("bg")
world.use_nodes = True
next(n for n in world.node_tree.nodes if n.type == "BACKGROUND").inputs[0].default_value = (*srgb((0xB4, 0xBB, 0xBF)), 1)
sc.world = world

W, Z0, Z1 = len(FRESH) * DX + 0.1, -1.05, 5.9
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
coll.objects.link(cam)
cam.data.type = "ORTHO"
cam.data.ortho_scale = W
pitch = 6
cam.rotation_euler = (math.radians(90 - pitch), 0, 0)
cam.location = (0.8 + 3 * DX, -20, (Z0 + Z1) / 2 + 20 * math.tan(math.radians(pitch)))
sc.camera = cam
r = sc.render
try:
    r.engine = "BLENDER_EEVEE_NEXT"
except TypeError:
    r.engine = "BLENDER_EEVEE"
r.resolution_x, r.resolution_y, r.resolution_percentage = 2400, round(2400 * (Z1 - Z0) / W), 100
r.filter_size = 0.6
sc.view_settings.view_transform = "Standard"
sc.view_settings.look = "None"
r.image_settings.file_format = "PNG"
r.filepath = os.path.join(HERE, f"z0_style_{ROUND}.png")
bpy.ops.render.render(write_still=True)

# second shot: heads large, front row and three-quarter row
s2 = bpy.data.scenes.new("faces")
s2.world = world
HX = 2.0
for i, (k, f, slim, label) in enumerate(FRESH):
    x = 1.0 + i * HX
    figure(k, None, slim, s2.collection, (x, 0, 2.6), 0, scale=2.8, head_only=True)
    figure(k, None, slim, s2.collection, (x, 0, 0.8), -32, scale=2.3, head_only=True)
    text(s2.collection, label, (x - 0.75, -1.5, 3.75), 0.24)
cam2 = bpy.data.objects.new("cam2", bpy.data.cameras.new("cam2"))
s2.collection.objects.link(cam2)
cam2.data.type = "ORTHO"
cam2.data.ortho_scale = len(FRESH) * HX + 0.1
cam2.rotation_euler = (math.radians(90 - 4), 0, 0)
cam2.location = (1.0 + 3 * HX, -20, 2.0 + 20 * math.tan(math.radians(4)))
s2.camera = cam2
r2 = s2.render
r2.engine = r.engine
r2.resolution_x, r2.resolution_y, r2.resolution_percentage = 2400, round(2400 * 4.6 / (len(FRESH) * HX + 0.1)), 100
r2.filter_size = 0.6
s2.view_settings.view_transform = "Standard"
s2.view_settings.look = "None"
r2.image_settings.file_format = "PNG"
r2.filepath = os.path.join(HERE, f"z0_style_faces_{ROUND}.png")
bpy.ops.render.render(write_still=True, scene=s2.name)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "z0_style.blend"))
print("ok", r.resolution_x, r.resolution_y)
