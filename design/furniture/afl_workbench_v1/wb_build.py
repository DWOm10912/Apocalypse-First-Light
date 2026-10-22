exec(open(r"C:/Users/willi/AppData/Local/Temp/claude/D--Minecraft-Modding-Apocalypse-First-Light/9acf29e6-9726-4be6-9d1a-cac1531ae610/scratchpad/wb_helpers.py").read())

# clear the old build
c = bpy.data.collections.get("Workbench")
if c:
    for o in list(c.objects): bpy.data.objects.remove(o, do_unlink=True)
M = mats()

# Frame: x 0..2 (2 cells), y 0 front .. 1 wall, z 0..2. Front legs, back posts up to the shelf.
LX = [(0.07, 0.16), (1.84, 1.93)]
for i, (a, b) in enumerate(LX):
    box(f"leg_front_{i}", (a, 0.07, 0), (b, 0.16, 0.83), M["frame"], "z")
    box(f"post_back_{i}", (a, 0.86, 0), (b, 0.95, 1.95), M["frame"], "z")
    box(f"side_low_{i}", (a + 0.015, 0.16, 0.12), (b - 0.015, 0.86, 0.2), M["frame"], "y")
    box(f"side_high_{i}", (a + 0.015, 0.16, 0.70), (b - 0.015, 0.86, 0.83), M["frame"], "y")
# long stretchers and the low shelf
box("rail_low_front", (0.16, 0.085, 0.13), (1.84, 0.145, 0.2), M["frame"], "x")
box("rail_low_back", (0.16, 0.875, 0.13), (1.84, 0.935, 0.2), M["frame"], "x")
ys = [0.15, 0.29, 0.43, 0.57, 0.71]
for i, y in enumerate(ys):
    box(f"shelf_low_{i}", (0.16, y + 0.003, 0.2), (1.84, y + 0.137, 0.222), M["slat"], "x", 0.002)
# front apron (left of the drawers)
box("apron_front", (0.16, 0.075, 0.70), (1.0, 0.1, 0.83), M["frame"], "x")
box("apron_back", (0.16, 0.86, 0.70), (1.84, 0.885, 0.83), M["frame"], "x")

# Top: six laminated maple boards, y 0.04..0.73, 7 cm thick; the front board has a row of dog holes
edges = [0.04 + i * 0.115 for i in range(7)]
for i in range(6):
    box(f"top_{i}", (0.0, edges[i] + 0.001, 0.83), (2.0, edges[i + 1] - 0.001, 0.9), M["top"], "x", 0.002)
top0 = bpy.data.objects["top_0"]
for k in range(8):
    x = 0.8 + k * 0.15
    h = box(f"_hole_{k}", (x - 0.012, 0.085, 0.8), (x + 0.012, 0.115, 0.95), M["dark"], "z", 0)
    h.hide_render = True; h.hide_viewport = True
    bo = top0.modifiers.new(f"hole{k}", "BOOLEAN"); bo.object = h; bo.operation = "DIFFERENCE"
    top0.modifiers.move(len(top0.modifiers) - 1, 0)
# tool well behind the top: a tray between the top and the board
box("well_bottom", (0.16, 0.73, 0.80), (1.84, 0.84, 0.815), M["slat"], "x", 0.002)

# Drawers under the right half of the top
box("drawer_case_l", (1.0, 0.075, 0.56), (1.02, 0.86, 0.83), M["frame"], "y")
box("drawer_case_bottom", (1.0, 0.075, 0.545), (1.84, 0.86, 0.565), M["frame"], "x")
for i, (a, b) in enumerate([(1.025, 1.425), (1.435, 1.835)]):
    box(f"drawer_front_{i}", (a, 0.06, 0.575), (b, 0.085, 0.82), M["flat"], "x")
    cx = (a + b) / 2
    cyl(f"drawer_pull_{i}", (cx - 0.045, 0.05, 0.71), (cx + 0.045, 0.05, 0.71), 0.008, M["iron"], 10)
    box(f"drawer_pull_post_l{i}", (cx - 0.05, 0.048, 0.703), (cx - 0.04, 0.06, 0.717), M["iron"], "z", 0)
    box(f"drawer_pull_post_r{i}", (cx + 0.04, 0.048, 0.703), (cx + 0.05, 0.06, 0.717), M["iron"], "z", 0)

# Front vise, left end: a wooden front jaw on a cast-iron body (two guide rods, a screw, a T bar), open 5 cm
VX0, VX1 = 0.2, 0.6
vc = (VX0 + VX1) / 2
box("vise_jaw", (VX0, -0.02, 0.66), (VX1, 0.01, 0.9), M["top"], "x")
box("vise_jaw_plate", (VX0 + 0.04, 0.01, 0.70), (VX1 - 0.04, 0.018, 0.86), M["vise"], "x", 0.002)
box("vise_rear_plate", (VX0 + 0.04, 0.032, 0.70), (VX1 - 0.04, 0.04, 0.86), M["vise"], "x", 0.002)
box("vise_body", (VX0 + 0.06, 0.04, 0.70), (VX1 - 0.06, 0.6, 0.78), M["vise"], "y")
for x in (VX0 + 0.08, VX1 - 0.08):
    cyl(f"vise_rod_{x:.2f}", (x, -0.025, 0.75), (x, 0.6, 0.75), 0.011, M["steel"], 12)
cyl("vise_screw", (vc, -0.06, 0.75), (vc, 0.6, 0.75), 0.013, M["steel"], 12)
cyl("vise_hub", (vc, -0.07, 0.75), (vc, -0.02, 0.75), 0.028, M["vise"], 16)
cyl("vise_bar", (vc - 0.17, -0.055, 0.75), (vc + 0.17, -0.055, 0.75), 0.009, M["steel"], 10)
for s in (-1, 1):
    cyl(f"vise_bar_knob{s}", (vc + s * 0.165, -0.055, 0.75), (vc + s * 0.185, -0.055, 0.75), 0.016, M["steel"], 12)

# Tool board: pine boards across the back posts, z 0.92..1.80, then a header rail and the top shelf
nb = 13
w = 1.86 / nb
for i in range(nb):
    box(f"board_{i}", (0.07 + i * w + 0.0015, 0.84, 0.92), (0.07 + (i + 1) * w - 0.0015, 0.86, 1.8), M["board"], "z", 0.002)
box("board_rail_low", (0.07, 0.825, 0.9), (1.93, 0.86, 0.95), M["frame"], "x")
box("board_batten", (0.07, 0.825, 1.27), (1.93, 0.84, 1.32), M["frame"], "x")
box("board_header", (0.07, 0.82, 1.78), (1.93, 0.86, 1.86), M["frame"], "x")
box("shelf_top", (0.0, 0.6, 1.86), (2.0, 0.96, 1.89), M["frame"], "x")
box("shelf_lip", (0.0, 0.6, 1.89), (2.0, 0.62, 1.92), M["frame"], "x")
for i, (a, b) in enumerate(LX):
    poly_prism(f"shelf_bracket_{i}", [(a + 0.02, 1.86), (b - 0.02, 1.86), (b - 0.02, 1.8)], 0.62, 0.86, M["frame"], "y")
    bk = bpy.data.objects[f"shelf_bracket_{i}"]
    # a side-view triangle: rebuild it in y/z instead
    bpy.data.objects.remove(bk, do_unlink=True)
    me = bpy.data.meshes.new(f"shelf_bracket_{i}")
    bm = bmesh.new()
    pts = [(0.86, 1.86), (0.62, 1.86), (0.86, 1.62)]
    left = [bm.verts.new((a + 0.025, y, z)) for y, z in pts]
    right = [bm.verts.new((b - 0.025, y, z)) for y, z in pts]
    bm.faces.new(left[::-1]); bm.faces.new(right)
    for k in range(3):
        j = (k + 1) % 3
        bm.faces.new((left[k], left[j], right[j], right[k]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me); bm.free()
    ob = bpy.data.objects.new(f"shelf_bracket_{i}", me); coll().objects.link(ob); ob.data.materials.append(M["frame"]); box_uv(ob, "y")

# Tool slots: five pairs of hickory pegs at z 1.62, the tools hang head up between them (like the tool rack)
SLOT_X = [0.22 + i * 0.27 for i in range(5)]
for i, x in enumerate(SLOT_X):
    for dx in (-0.035, 0.035):
        cyl(f"peg_{i}_{dx:+.3f}", (x + dx, 0.84, 1.62), (x + dx, 0.74, 1.62), 0.009, M["peg"], 12)
# a small-tool strip on the right: a batten with six holes for screwdrivers and chisels
box("chisel_strip", (1.44, 0.76, 1.6), (1.86, 0.84, 1.625), M["frame"], "x")
print("frame ok", len(coll().objects))
