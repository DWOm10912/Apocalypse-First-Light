# Propane Range V1 (docs/gameplay/cooking_v1_plan.md 6): the town houses' kitchen range (user 2026-10-27: "两种都做，不过
# 老宅只放老式搪瓷柴灶"). A plain white 30 inch freestanding gas range of the 1990s on bottled propane: four sealed burners
# under two continuous cast-iron grates, the control panel with four knobs and a clock window above the oven door, the
# oven door with a black glass window and a steel handle bar, a storage drawer, a low backguard, a recessed toe kick.
# exec()'d by a build or render script in a scene (tools/stove/stove_lib.py: Box Stove V2's helpers and materials).
#
# Frame: the AFL cabin piece frame, facing north: x along the range (seen from the front +x is on the left), y up, z from
# the room (front, z 0) to the wall (z 1); block units (1 = 1 m). Parts are named <group>:<what>: body.
import os

STOVE_DIR = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
exec(compile(open(os.path.join(STOVE_DIR, "stove_lib.py"), encoding="utf-8").read(), "stove_lib.py", "exec"))

porcelain("rg_white", (0.80, 0.80, 0.78), rough=0.18)           # white porcelain enamel, the top and the sides
plain("rg_panel", (0.78, 0.78, 0.76), 0.3, scale=400, tint=0.01)   # the painted steel panels
plain("rg_glass", (0.008, 0.008, 0.009), 0.04)                   # black glass: the oven window, the clock
plain("rg_knob", (0.03, 0.03, 0.032), 0.35, scale=400)           # black plastic knobs
plain("rg_steel", (0.55, 0.55, 0.53), 0.28, metal=1, f0=255, bump=0.02, scale=400, tint=0.02)   # brushed steel handle
plain("rg_cap", (0.012, 0.012, 0.012), 0.45)                     # black enamel burner caps
plain("rg_burner", (0.42, 0.42, 0.42), 0.45, metal=1, f0=255, scale=400)   # cast aluminium burner bases
iron("rg_grate", rough=0.55, rough_edge=0.45)                    # porcelain-coated cast-iron grates, matte black

RG = dict(x0=0.12, x1=0.88, z0=0.08, z1=0.72, y1=0.91)
TOP = RG["y1"]
GRATE_TOP = TOP + 0.028                       # a pan stands on the grates
BURNERS = [(0.69, 0.27), (0.69, 0.53), (0.31, 0.27), (0.31, 0.53)]   # front left (as you face it), back left, front right, back right
X0, X1, Z0, Z1 = RG["x0"], RG["x1"], RG["z0"], RG["z1"]

# ---------------------------------------------------------------- the cabinet
box("body:carcass", (X0, 0.10, Z0 + 0.02), (X1, TOP - 0.004, Z1), "rg_white", 0.004, 3)
box("body:toe_kick", (X0 + 0.01, 0.0, Z0 + 0.06), (X1 - 0.01, 0.10, Z1 - 0.01), "rg_knob", 0.002)
box("body:cooktop", (X0 - 0.004, TOP - 0.006, Z0), (X1 + 0.004, TOP, Z1), "rg_white", 0.005, 3)
box("body:backguard", (X0 - 0.004, TOP, Z1 - 0.05), (X1 + 0.004, TOP + 0.11, Z1), "rg_white", 0.006, 3)
# the burners: an aluminium base, a black cap, a shallow well round each in the top
for k, (x, z) in enumerate(BURNERS):
    big = k in (0, 3)
    cyl(f"body:well_{k}", "y", (x, z), TOP - 0.0005, TOP + 0.001, 0.085 if big else 0.075, "rg_white", 48)
    cyl(f"body:burner_{k}", "y", (x, z), TOP, TOP + 0.016, 0.042 if big else 0.035, "rg_burner", 32, bev=0.002)
    cyl(f"body:cap_{k}", "y", (x, z), TOP + 0.016, TOP + 0.021, 0.040 if big else 0.033, "rg_cap", 32, bev=0.0015)
# two continuous grates (left and right pairs): a frame of bars, cross bars, fingers toward each burner
def grate(tag, xa, xb):
    za, zb, w, h = Z0 + 0.03, Z1 - 0.06, 0.009, 0.028
    for nm, lo, hi in (("f", (xa, TOP, za), (xb, TOP + h, za + w)), ("b", (xa, TOP, zb - w), (xb, TOP + h, zb)),
                       ("l", (xa, TOP, za), (xa + w, TOP + h, zb)), ("r", (xb - w, TOP, za), (xb, TOP + h, zb)),
                       ("m", (xa, TOP, (za + zb) / 2 - w / 2), (xb, TOP + h, (za + zb) / 2 + w / 2)),
                       ("c", ((xa + xb) / 2 - w / 2, TOP, za), ((xa + xb) / 2 + w / 2, TOP + h, zb))):
        box(f"body:grate_{tag}_{nm}", lo, hi, "rg_grate", 0.002)
    for k in range(4):   # short feet at the corners
        x = xa + 0.01 if k % 2 == 0 else xb - 0.01; z = za + 0.01 if k < 2 else zb - 0.01
        cyl(f"body:grate_{tag}_foot_{k}", "y", (x, z), TOP - 0.002, TOP + 0.004, 0.006, "rg_knob", 12)
grate("left", 0.505, X1 - 0.012)
grate("right", X0 + 0.012, 0.495)
# the control panel above the oven door: four knobs, the clock window
box("body:panel", (X0, 0.785, Z0 - 0.006), (X1, TOP - 0.006, Z0 + 0.02), "rg_panel", 0.004, 3)
for k, x in enumerate((0.78, 0.66, 0.34, 0.22)):
    cyl(f"body:knob_{k}", "z", (x, 0.847), Z0 - 0.04, Z0 - 0.006, 0.021, "rg_knob", 32, bev=0.003, r1=0.019)
    box(f"body:knob_{k}_grip", (x - 0.004, 0.83, Z0 - 0.046), (x + 0.004, 0.864, Z0 - 0.038), "rg_knob", 0.002)
box("body:clock", (0.44, 0.83, Z0 - 0.009), (0.56, 0.865, Z0 - 0.006), "rg_glass", 0.002)
# the oven door: a white frame, a black glass window, a steel handle bar on two standoffs
box("body:oven_door", (X0 + 0.01, 0.22, Z0 - 0.03), (X1 - 0.01, 0.775, Z0 + 0.004), "rg_white", 0.008, 3)
box("body:oven_window", (0.26, 0.38, Z0 - 0.0315), (0.74, 0.64, Z0 - 0.029), "rg_glass", 0.004)
cyl("body:oven_handle", "x", (0.735, Z0 - 0.075), X0 + 0.05, X1 - 0.05, 0.011, "rg_steel", 24)
for x in (X0 + 0.07, X1 - 0.07):
    box(f"body:oven_handle_post_{x:.2f}", (x - 0.01, 0.725, Z0 - 0.075), (x + 0.01, 0.745, Z0 - 0.03), "rg_steel", 0.003)
# the storage drawer
box("body:drawer", (X0 + 0.01, 0.105, Z0 - 0.02), (X1 - 0.01, 0.21, Z0 + 0.004), "rg_white", 0.006, 3)
box("body:drawer_pull", (0.40, 0.175, Z0 - 0.034), (0.60, 0.19, Z0 - 0.02), "rg_steel", 0.003)

print("propane range v1 parts", len(PARTS))
