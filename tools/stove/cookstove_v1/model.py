# Wood Cookstove V1 (docs/gameplay/cooking_v1_plan.md 6): the forest home's old enamelled wood cookstove, where the
# cooking is done (user 2026-10-27: the box stove only heats water; "老宅只放老式搪瓷柴灶"). One cell, a farmhouse range of
# the 1930s-50s the house has had for decades: cream porcelain enamel panels framed in dark green, nickel trim, a black
# cast-iron cooktop with ONE cook lid over the firebox where the pot or the skillet stands, the firebox on the left as you
# face it (its loading door, the ash door below), the oven on the right (thermometer, nickel latch bar; not used in V1), a
# high back with a warming closet and a shelf, the flue going up behind it, four short cast legs. Worn: the enamel
# chipped here and there, the cooktop polished where pots slide.
# exec()'d by a build or render script in a scene (tools/stove/stove_lib.py: Box Stove V2's helpers and materials).
#
# Frame: the AFL cabin piece frame, facing north: x along the stove (seen from the front +x is on the left, so the firebox
# is at the x 1 end), y up, z from the room (front, z 0) to the wall (z 1); block units (1 = 1 m). Parts are named
# <group>:<what>: body (one baked piece for now; the doors open in a later step).
import os

STOVE_DIR = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
exec(compile(open(os.path.join(STOVE_DIR, "stove_lib.py"), encoding="utf-8").read(), "stove_lib.py", "exec"))

porcelain("cs_cream", (0.74, 0.69, 0.58), chip=1.0)          # the panels: old cream enamel
porcelain("cs_green", (0.045, 0.11, 0.075), chip=0.6)        # the frames and bands: dark green enamel
plain("cs_dial", (0.80, 0.77, 0.68), 0.25)                   # the thermometer's plain cream face

CS = dict(x0=0.06, x1=0.94, z0=0.15, z1=0.74, y0=0.14, y1=0.745)
TOP0, TOP1 = 0.745, 0.77                      # the cooktop plate; a pot stands at TOP1
COOK = (0.76, 0.38); COOK_R = 0.12            # the one cook lid (x, z): over the firebox, where the pot goes
FIRE_X0 = 0.60                                # the oven / firebox partition
FLUE_CS = (0.24, 0.675); CS_PIPE_R = 0.065    # behind the high back, over the oven
BACK_Z = 0.60                                 # the high back's front face
X0, X1, Y0, Y1, Z0, Z1 = (CS[k] for k in ("x0", "x1", "y0", "y1", "z0", "z1"))
TB = 0.01

# ---------------------------------------------------------------- body: enamel panels on an iron carcass
box("body:carcass", (X0 + 0.004, Y0, Z0 + 0.004), (X1 - 0.004, Y1, Z1), "st_iron", 0.002)
box("body:side_oven", (X0, Y0 + 0.01, Z0 + 0.03), (X0 + 0.006, Y1 - 0.02, Z1 - 0.03), "cs_cream", 0.003)
box("body:side_fire", (X1 - 0.006, Y0 + 0.01, Z0 + 0.03), (X1, Y1 - 0.02, Z1 - 0.03), "cs_cream", 0.003)
box("body:base_band", (X0 - 0.004, Y0 - 0.006, Z0 - 0.004), (X1 + 0.004, Y0 + 0.05, Z1 + 0.002), "cs_green", 0.004, 3)
# the cooktop: black cast iron, overhanging, a nickel edge band under its lip
box("body:cooktop", (X0 - 0.018, TOP0, Z0 - 0.018), (X1 + 0.018, TOP1, Z1 + 0.004), "st_top", 0.004, 3)
for nm, lo, hi in (("front", (X0 - 0.010, TOP0 - 0.018, Z0 - 0.010), (X1 + 0.010, TOP0, Z0 + 0.004)),
                   ("oven", (X0 - 0.010, TOP0 - 0.018, Z0 - 0.010), (X0 + 0.004, TOP0, Z1 - 0.02)),
                   ("fire", (X1 - 0.004, TOP0 - 0.018, Z0 - 0.010), (X1 + 0.010, TOP0, Z1 - 0.02))):
    box("body:trim_" + nm, lo, hi, "st_nickel", 0.003)
cyl("body:lid_ring", "y", COOK, TOP1 - 0.001, TOP1 + 0.0015, COOK_R + 0.012, "st_top", 64)
cyl("body:lid", "y", COOK, TOP1, TOP1 + 0.003, COOK_R, "st_top", 64, bev=0.0015)
box("body:lid_slot", (COOK[0] - 0.012, TOP1 + 0.0028, COOK[1] - COOK_R + 0.018), (COOK[0] + 0.012, TOP1 + 0.0032, COOK[1] - COOK_R + 0.03), "st_inner", 0)
box("body:seam", (FIRE_X0 - 0.024, TOP1 - 0.0005, Z0 - 0.012), (FIRE_X0 - 0.020, TOP1 + 0.0005, BACK_Z), "st_inner", 0)

# ---------------------------------------------------------------- the high back: a panel, the warming closet, a shelf
box("body:back_panel", (X0, TOP1, BACK_Z), (X1, 1.42, BACK_Z + 0.02), "cs_cream", 0.003)
box("body:back_frame_l", (X1 - 0.03, TOP1, BACK_Z - 0.004), (X1, 1.12, BACK_Z + 0.02), "cs_green", 0.003)
box("body:back_frame_r", (X0, TOP1, BACK_Z - 0.004), (X0 + 0.03, 1.12, BACK_Z + 0.02), "cs_green", 0.003)
WC = dict(y0=1.12, y1=1.40, z0=0.43)          # the warming closet over the back of the cooktop
box("body:closet", (X0, WC["y0"], WC["z0"]), (X1, WC["y1"], BACK_Z + 0.02), "cs_cream", 0.004, 3)
box("body:closet_floor", (X0 - 0.006, WC["y0"] - 0.012, WC["z0"] - 0.006), (X1 + 0.006, WC["y0"], BACK_Z + 0.02), "cs_green", 0.003)
for k, (x0, x1) in enumerate(((X0 + 0.035, 0.49), (0.51, X1 - 0.035))):
    box(f"body:closet_door_{k}", (x0, WC["y0"] + 0.03, WC["z0"] - 0.008), (x1, WC["y1"] - 0.03, WC["z0"]), "cs_cream", 0.004, 3)
    for nm, lo, hi in (("b", (x0, WC["y0"] + 0.03, WC["z0"] - 0.011), (x1, WC["y0"] + 0.05, WC["z0"] - 0.008)),
                       ("t", (x0, WC["y1"] - 0.05, WC["z0"] - 0.011), (x1, WC["y1"] - 0.03, WC["z0"] - 0.008))):
        box(f"body:closet_door_{k}_{nm}", lo, hi, "cs_green", 0.002)
    cyl(f"body:closet_knob_{k}", "z", ((x0 + x1) / 2, (WC["y0"] + WC["y1"]) / 2), WC["z0"] - 0.03, WC["z0"] - 0.008, 0.011, "st_nickel", 16, bev=0.002, r1=0.009)
box("body:shelf", (X0 - 0.02, WC["y1"], WC["z0"] - 0.03), (X1 + 0.02, WC["y1"] + 0.022, BACK_Z + 0.03), "cs_cream", 0.004, 3)
box("body:shelf_edge", (X0 - 0.024, WC["y1"] + 0.004, WC["z0"] - 0.034), (X1 + 0.024, WC["y1"] + 0.018, WC["z0"] - 0.026), "st_nickel", 0.003)
for x in (X0 + 0.01, X1 - 0.03):   # the cast brackets under the closet, down to the cooktop
    box(f"body:bracket_{x:.2f}", (x, TOP1, WC["z0"] + 0.02), (x + 0.02, WC["y0"] - 0.012, BACK_Z), "st_iron", 0.003)
# the flue: a collar on the cooktop behind the high back, the pipe up past the shelf
cyl("body:collar", "y", FLUE_CS, TOP1, TOP1 + 0.05, CS_PIPE_R + 0.01, "st_iron", 32, bev=0.002)
cyl("body:pipe", "y", FLUE_CS, TOP1 + 0.05, 2.4, CS_PIPE_R, "st_pipe", 32)

# ---------------------------------------------------------------- legs
def cs_leg(tag, x, z):
    rings = []
    for y, w in ((Y0 - 0.006, 0.03), (0.08, 0.022), (0.03, 0.019), (0.012, 0.026), (0.0, 0.026)):
        rings.append([(x + w * a, y, z + w * b) for a, b in ((-1, -1), (1, -1), (1, 1), (-1, 1))])
    bevel(loft("body:leg_" + tag, rings, "st_leg"), 0.003, 2, 30)
for tag, x, z in (("fl", X0 + 0.04, Z0 + 0.04), ("fr", X1 - 0.04, Z0 + 0.04), ("bl", X0 + 0.04, Z1 - 0.04), ("br", X1 - 0.04, Z1 - 0.04)):
    cs_leg(tag, x, z)

# ---------------------------------------------------------------- the front: green frames, cream doors, nickel
def frame(tag, x0, x1, y0, y1, w=0.024):
    zf = Z0 - TB * 0.6
    for nm, lo, hi in (("b", (x0 - w, y0 - w, zf), (x1 + w, y0, Z0)), ("t", (x0 - w, y1, zf), (x1 + w, y1 + w, Z0)),
                       ("l", (x0 - w, y0, zf), (x0, y1, Z0)), ("r", (x1, y0, zf), (x1 + w, y1, Z0))):
        box(f"body:frame_{tag}_{nm}", lo, hi, "cs_green", 0.002)

def door(tag, x0, x1, y0, y1):
    box(f"body:{tag}_plate", (x0 + 0.002, y0 + 0.002, Z0 - TB), (x1 - 0.002, y1 - 0.002, Z0), "cs_cream", 0.004, 3)
    for nm, lo, hi in (("b", (x0 + 0.02, y0 + 0.02, Z0 - TB - 0.003), (x1 - 0.02, y0 + 0.028, Z0 - TB)),
                       ("t", (x0 + 0.02, y1 - 0.028, Z0 - TB - 0.003), (x1 - 0.02, y1 - 0.02, Z0 - TB)),
                       ("l", (x0 + 0.02, y0 + 0.028, Z0 - TB - 0.003), (x0 + 0.028, y1 - 0.028, Z0 - TB)),
                       ("r", (x1 - 0.028, y0 + 0.028, Z0 - TB - 0.003), (x1 - 0.02, y1 - 0.028, Z0 - TB))):
        box(f"body:{tag}_bead_{nm}", lo, hi, "st_nickel", 0.0015)

box("body:front_panel", (X0, Y0 + 0.05, Z0 - 0.004), (X1, Y1 - 0.02, Z0), "cs_cream", 0.003)
FD = dict(x0=0.65, x1=0.89, y0=0.49, y1=0.67)     # firebox loading door
AD = dict(x0=0.67, x1=0.87, y0=0.23, y1=0.36)     # ash door
OD = dict(x0=0.11, x1=0.54, y0=0.24, y1=0.67)     # oven door
for tag, d in (("fire", FD), ("ash", AD), ("oven", OD)):
    frame(tag, d["x0"], d["x1"], d["y0"], d["y1"]); door(tag, d["x0"], d["x1"], d["y0"], d["y1"])
box("body:pilaster", (FIRE_X0 - 0.045, Y0 + 0.05, Z0 - 0.012), (FIRE_X0 - 0.005, Y1 - 0.02, Z0), "cs_green", 0.003)
fz = Z0 - TB - 0.003
cx = (FD["x0"] + FD["x1"]) / 2
cyl("body:draft_plate", "z", (cx, 0.575), fz - 0.004, fz, 0.04, "st_nickel", 32, bev=0.0015)
for k in range(6):
    a = math.pi * 2 * k / 6
    box(f"body:draft_slot_{k}", (cx + 0.024 * math.cos(a) - 0.005, 0.575 + 0.024 * math.sin(a) - 0.005, fz - 0.0045),
        (cx + 0.024 * math.cos(a) + 0.005, 0.575 + 0.024 * math.sin(a) + 0.005, fz - 0.004), "st_inner", 0)
cyl("body:draft_knob", "z", (cx, 0.575), fz - 0.016, fz - 0.004, 0.008, "st_nickel", 16, bev=0.002)
cyl("body:fire_latch", "z", (FD["x0"] + 0.03, 0.64), fz - 0.03, fz, 0.010, "st_nickel", 16, bev=0.002, r1=0.008)
cyl("body:ash_knob", "z", ((AD["x0"] + AD["x1"]) / 2, 0.295), fz - 0.03, fz, 0.011, "st_nickel", 16, bev=0.002, r1=0.009)
oz = Z0 - TB - 0.003
cyl("body:oven_bar", "x", (0.635, oz - 0.03), OD["x0"] + 0.05, OD["x1"] - 0.05, 0.008, "st_nickel", 16)
for x in (OD["x0"] + 0.05, OD["x1"] - 0.05):
    box(f"body:oven_bar_post_{x:.2f}", (x - 0.008, 0.627, oz - 0.03), (x + 0.008, 0.643, oz), "st_nickel", 0.002)
cyl("body:thermo_bezel", "z", ((OD["x0"] + OD["x1"]) / 2, 0.50), oz - 0.008, oz, 0.05, "st_nickel", 48, bev=0.002)
cyl("body:thermo_face", "z", ((OD["x0"] + OD["x1"]) / 2, 0.50), oz - 0.0085, oz - 0.0075, 0.042, "cs_dial", 48)
box("body:thermo_needle", ((OD["x0"] + OD["x1"]) / 2 - 0.0015, 0.50, oz - 0.0095), ((OD["x0"] + OD["x1"]) / 2 + 0.0015, 0.535, oz - 0.0088), "st_inner", 0)
TBAR_Y, TBAR_Z = 0.705, Z0 - 0.05
cyl("body:towel_bar", "x", (TBAR_Y, TBAR_Z), X0 + 0.01, X1 - 0.01, 0.007, "st_nickel", 16)
for x in (X0 + 0.03, X1 - 0.03):
    box(f"body:towel_bracket_{x:.2f}", (x - 0.007, TBAR_Y - 0.012, TBAR_Z - 0.004), (x + 0.007, TBAR_Y + 0.012, Z0), "st_nickel", 0.002)

print("cookstove v1 parts", len(PARTS))
