import bpy, math, os
from mathutils import Vector
OUT = r"C:/Users/willi/AppData/Local/Temp/afl_rods_renders/"  # scratch folder that rods_sheet.cjs reads
os.makedirs(OUT, exist_ok=True)
sc = bpy.context.scene
try:
    sc.render.engine = "CYCLES"
except TypeError as e:
    print(e)
sc.cycles.samples = SAMPLES if "SAMPLES" in globals() else 96
sc.cycles.use_denoising = True
try:
    prefs = bpy.context.preferences.addons["cycles"].preferences
    prefs.get_devices()
    for t in ("OPTIX", "CUDA", "HIP", "ONEAPI", "METAL"):
        try:
            prefs.compute_device_type = t
            if any(d.type == t for d in prefs.devices):
                for d in prefs.devices: d.use = d.type == t
                sc.cycles.device = "GPU"; break
        except TypeError:
            pass
except Exception as e:
    print("device", e)
sc.render.film_transparent = True
sc.view_settings.view_transform = "AgX" if "AgX" in [i.identifier for i in bpy.types.ColorManagedViewSettings.bl_rna.properties["view_transform"].enum_items] else sc.view_settings.view_transform

cam_data = bpy.data.cameras.get("cam") or bpy.data.cameras.new("cam")
cam = bpy.data.objects.get("cam") or bpy.data.objects.new("cam", cam_data)
if cam.name not in sc.collection.objects: sc.collection.objects.link(cam)
sc.camera = cam

def show(names):
    for c in bpy.data.collections:
        if c.name in ("Lights",): continue
        c.hide_render = c.name not in names

def shoot(file, names, loc, look, res, lens=50, ortho=None):
    show(names)
    cam.location = loc
    cam.rotation_euler = (Vector(look) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    if ortho:
        cam_data.type = "ORTHO"; cam_data.ortho_scale = ortho
    else:
        cam_data.type = "PERSP"; cam_data.lens = lens
    cam_data.clip_start = 0.01
    sc.render.resolution_x, sc.render.resolution_y = res
    sc.render.filepath = OUT + file
    bpy.ops.render.render(write_still=True)
    print("wrote", file)

def orbit(target, dist, yaw, pitch):
    t = Vector(target); a, p = math.radians(yaw), math.radians(pitch)
    return tuple(t + Vector((math.cos(p) * math.sin(a), -math.cos(p) * math.cos(a), math.sin(p))) * dist)

SHOTS = globals().get("SHOTS", "all")
OX = 4.0
jobs = {
    "A_hero": lambda: shoot("A_hero.png", ("A", "Stage_A"), orbit((0, 0, 0.42), 2.3, 35, 18), (0, 0, 0.42), (1000, 1300), 50),
    "A_front": lambda: shoot("A_front.png", ("A", "Stage_A"), (0, -5, 0.45), (0, 0, 0.45), (600, 1100), ortho=1.25),
    "A_base": lambda: shoot("A_base.png", ("A", "Stage_A"), orbit((0.03, 0, 0.03), 0.42, 30, 28), (0.03, 0, 0.03), (800, 640), 50),
    "B_hero": lambda: shoot("B_hero.png", ("B", "Stage_B"), orbit((OX, 0, 0.85), 3.7, 32, 14), (OX, 0, 0.85), (1000, 1300), 50),
    "B_front": lambda: shoot("B_front.png", ("B", "Stage_B"), (OX, -6, 0.95), (OX, 0, 0.95), (600, 1100), ortho=2.15),
    "B_top": lambda: shoot("B_top.png", ("B", "Stage_B"), orbit((OX + 0.02, 0, 1.47), 1.15, 25, 10), (OX + 0.02, 0, 1.47), (800, 640), 50),
    "B_stand": lambda: shoot("B_stand.png", ("B", "Stage_B"), orbit((OX + 0.1, 0, 0.12), 0.8, -20, 24), (OX + 0.1, 0, 0.12), (800, 640), 50),
}
for k, f in jobs.items():
    if SHOTS == "all" or k in SHOTS: f()
