# Z0 r1 review scene: versions A / B / C next to the current zombie.png. Run inside Blender 5.x:
#   exec(open(r"<repo>/design/creatures/infected_z0/z0_blender_build.py", encoding="utf-8").read())
# Builds a fresh file, renders z0_lineup_r1.png and z0_fronts_r1.png next to this script and saves z0_infected_r1.blend.
import os
HERE = r"D:\Minecraft Modding\AFL-vanilla-rework\design\creatures\infected_z0"
exec(open(os.path.join(HERE, "z0_blender_lib.py"), encoding="utf-8").read())

REPO = r"D:\Minecraft Modding\AFL-vanilla-rework"
SKINS = [
    ("cur", os.path.join(REPO, r"src\main\resources\assets\minecraft\textures\entity\zombie\zombie.png"), False,
     "现版：替换原版的 zombie.png"),
    ("a", os.path.join(HERE, "z0_a.png"), False, "A 刚转化：数小时到数天"),
    ("b", os.path.join(HERE, "z0_b.png"), False, "B 数周：帽檐是小网格"),
    ("c", os.path.join(HERE, "z0_c.png"), True, "C 数月：干尸化，3 px 细手臂"),
]

# ---------------------------------------------------------------- scene

bpy.ops.wm.read_homefile(use_empty=True)
sc = bpy.context.scene
FONT = None
for f in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf", r"C:\Windows\Fonts\simsun.ttc"):
    try:
        FONT = bpy.data.fonts.load(f)
        break
    except Exception:
        pass
MATS = {k: skin_mat(k, p) for k, p, _, _ in SKINS}
BRIM = brim_mat()
coll = sc.collection

# 2 x 2 groups: four turns (front, three-quarter left, three-quarter right, back) and two head close-ups each
GW, GH = 6.4, 5.6
VIEWS = [0, -35, 35, 180]
for gi, (k, p, slim, label) in enumerate(SKINS):
    gx, gz = (gi % 2) * GW, -(gi // 2) * GH
    for vi, yaw in enumerate(VIEWS):
        figure(k, p, slim, coll, (gx + 0.85 + vi * 1.45, 0, gz + 2.0), yaw)
    for hi, yaw in enumerate((0, -35)):
        figure(k, p, slim, coll, (gx + 1.6 + hi * 2.2, 0, gz + 1.0), yaw, scale=2.4, head_only=True)
    text(coll, label, (gx + 0.2, -1.5, gz + 4.45), 0.28)
    panel = cube("panel", ((gx + 0.05) * 16, 40, (gz - 0.3) * 16), ((gx + GW - 0.15) * 16, 41, (gz + GH - 0.6) * 16),
                 flat_mat("panel", (0xC4, 0xCA, 0xCD)))
    coll.objects.link(panel)

world = bpy.data.worlds.new("bg")
world.use_nodes = True
bgn = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
bgn.inputs[0].default_value = (*srgb((0xA9, 0xB1, 0xB5)), 1)
sc.world = world

cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
coll.objects.link(cam)
cam.data.type = "ORTHO"
cam.data.ortho_scale = 2 * GW + 0.2
pitch = 8
cx, cz = GW - 0.05, -GH / 2 + 2.45
cam.rotation_euler = (math.radians(90 - pitch), 0, 0)
cam.location = (cx, -20, cz + 20 * math.tan(math.radians(pitch)))
sc.camera = cam
r = sc.render
try:
    r.engine = "BLENDER_EEVEE_NEXT"
except TypeError:
    r.engine = "BLENDER_EEVEE"
r.resolution_x, r.resolution_y = 2400, round(2400 * (2 * GH - 0.25) / (2 * GW + 0.2))
r.resolution_percentage = 100
r.filter_size = 0.6
sc.view_settings.view_transform = "Standard"
sc.view_settings.look = "None"
r.image_settings.file_format = "PNG"
r.filepath = os.path.join(HERE, "z0_lineup_r1.png")
bpy.ops.render.render(write_still=True)

# second shot: the four fronts large, for detail
s2 = bpy.data.scenes.new("fronts")
s2.world = world
for gi, (k, p, slim, label) in enumerate(SKINS):
    figure(k, p, slim, s2.collection, (0.9 + gi * 2.1, 0, 0.35), 0)
    text(s2.collection, label.split("：")[0], (0.9 + gi * 2.1 - 0.5, -1.5, 2.55), 0.2)
cam2 = bpy.data.objects.new("cam2", bpy.data.cameras.new("cam2"))
s2.collection.objects.link(cam2)
cam2.data.type = "ORTHO"
cam2.data.ortho_scale = 8.6
cam2.rotation_euler = (math.radians(90 - 4), 0, 0)
cam2.location = (4.05, -20, 1.45 + 20 * math.tan(math.radians(4)))
s2.camera = cam2
r2 = s2.render
r2.engine = r.engine
r2.resolution_x, r2.resolution_y, r2.resolution_percentage = 2400, 840, 100
r2.filter_size = 0.6
s2.view_settings.view_transform = "Standard"
s2.view_settings.look = "None"
r2.image_settings.file_format = "PNG"
r2.filepath = os.path.join(HERE, "z0_fronts_r1.png")
bpy.ops.render.render(write_still=True, scene=s2.name)
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "z0_infected_r1.blend"))
print("ok", r.engine, r.resolution_x, r.resolution_y, FONT and FONT.name)
