exec(open(r"C:/Users/willi/AppData/Local/Temp/claude/D--Minecraft-Modding-Apocalypse-First-Light/9acf29e6-9726-4be6-9d1a-cac1531ae610/scratchpad/wb_helpers.py").read())
M = mats()

for o in [o for o in coll().objects if o.name.startswith("t_")]: bpy.data.objects.remove(o, do_unlink=True)
SLOT_X = [0.22 + i * 0.27 for i in range(5)]
PY = 0.79          # the plane tools hang in, between the board and the peg tips
PEG_TOP = 1.629

# slot 0: hand saw, its handle on the pegs, blade down
x = SLOT_X[0]
h = poly_prism("t_saw_handle", [(x - 0.075, PEG_TOP + 0.02), (x + 0.055, PEG_TOP + 0.02), (x + 0.065, PEG_TOP + 0.06),
    (x + 0.045, PEG_TOP + 0.115), (x - 0.02, PEG_TOP + 0.125), (x - 0.06, PEG_TOP + 0.09), (x - 0.075, PEG_TOP + 0.05)],
    PY - 0.011, PY + 0.011, M["handle"], "z")
h.location.z -= 0.0
bv = h.modifiers.new("bevel", "BEVEL"); bv.width = 0.004; bv.segments = 2
hole = box("t_saw_hole", (x - 0.035, PY - 0.05, PEG_TOP + 0.025), (x + 0.035, PY + 0.05, PEG_TOP + 0.075), M["dark"], "z", 0)
hole.hide_render = hole.hide_viewport = True
bo = h.modifiers.new("hole", "BOOLEAN"); bo.object = hole; bo.operation = "DIFFERENCE"; h.modifiers.move(1, 0)
me = bpy.data.meshes.new("t_saw_blade"); bm = bmesh.new()
pts = [(x - 0.07, PEG_TOP + 0.02), (x + 0.05, PEG_TOP + 0.02), (x + 0.03, PEG_TOP - 0.58), (x - 0.05, PEG_TOP - 0.58)]
f = [bm.verts.new((px, PY - 0.0008, pz)) for px, pz in pts]; bk = [bm.verts.new((px, PY + 0.0008, pz)) for px, pz in pts]
bm.faces.new(f); bm.faces.new(bk[::-1])
for k in range(4):
    j = (k + 1) % 4; bm.faces.new((f[j], f[k], bk[k], bk[j]))
bmesh.ops.recalc_face_normals(bm, faces=bm.faces); bm.to_mesh(me); bm.free()
ob = bpy.data.objects.new("t_saw_blade", me); coll().objects.link(ob); ob.data.materials.append(M["steel"])
for k in range(3):
    cyl(f"t_saw_screw{k}", (x - 0.03 + k * 0.03, PY - 0.014, PEG_TOP + 0.012 + (k % 2) * 0.03), (x - 0.03 + k * 0.03, PY + 0.014, PEG_TOP + 0.012 + (k % 2) * 0.03), 0.006, M["brass"], 10)

# slot 1: claw hammer, head on the pegs, handle down
x = SLOT_X[1]
box("t_hammer_head", (x - 0.055, PY - 0.013, PEG_TOP), (x + 0.03, PY + 0.013, PEG_TOP + 0.03), M["iron"], "x", 0.004)
cyl("t_hammer_face", (x - 0.075, PY, PEG_TOP + 0.015), (x - 0.055, PY, PEG_TOP + 0.015), 0.016, M["steel"], 14)
poly_prism("t_hammer_claw", [(x + 0.03, PEG_TOP), (x + 0.03, PEG_TOP + 0.03), (x + 0.085, PEG_TOP + 0.005), (x + 0.1, PEG_TOP - 0.03), (x + 0.075, PEG_TOP - 0.012)], PY - 0.011, PY + 0.011, M["iron"], "x")
cyl("t_hammer_handle", (x, PY, PEG_TOP - 0.3), (x, PY, PEG_TOP + 0.01), 0.014, M["handle"], 14)

# slot 3: a brace (crank drill), its head pad on the pegs
x = SLOT_X[3]
cyl("t_brace_pad", (x, PY, PEG_TOP), (x, PY, PEG_TOP + 0.03), 0.032, M["handle"], 18)
z = PEG_TOP
cyl("t_brace_s1", (x, PY, z - 0.08), (x, PY, z), 0.006, M["steel"], 10)
cyl("t_brace_s2", (x, PY, z - 0.08), (x + 0.1, PY, z - 0.08), 0.006, M["steel"], 10)
cyl("t_brace_s3", (x + 0.1, PY, z - 0.22), (x + 0.1, PY, z - 0.08), 0.006, M["steel"], 10)
cyl("t_brace_grip", (x + 0.1, PY, z - 0.2), (x + 0.1, PY, z - 0.1), 0.017, M["handle"], 14)
cyl("t_brace_s4", (x, PY, z - 0.22), (x + 0.1, PY, z - 0.22), 0.006, M["steel"], 10)
cyl("t_brace_s5", (x, PY, z - 0.3), (x, PY, z - 0.22), 0.006, M["steel"], 10)
cyl("t_brace_chuck", (x, PY, z - 0.36), (x, PY, z - 0.29), 0.014, M["steel"], 14)
cyl("t_brace_bit", (x, PY, z - 0.46), (x, PY, z - 0.36), 0.004, M["steel"], 8)

# small-tool strip: six holes, two screwdrivers and two chisels in four of them (handles up)
for k in range(6):
    hx = 1.48 + k * 0.068
    cyl(f"t_strip_hole{k}", (hx, 0.795, 1.6255), (hx, 0.795, 1.6256), 0.009, M["dark"], 12)
for k, kind in [(0, "sd"), (1, "sd"), (3, "ch"), (4, "ch")]:
    hx = 1.48 + k * 0.068
    if kind == "sd":
        cyl(f"t_sd_blade{k}", (hx, 0.795, 1.47), (hx, 0.795, 1.64), 0.0035, M["steel"], 8)
        cyl(f"t_sd_handle{k}", (hx, 0.795, 1.64), (hx, 0.795, 1.74), 0.014, M["red"] if k == 0 else M["handle"], 12)
    else:
        box(f"t_ch_blade{k}", (hx - 0.009, 0.792, 1.45), (hx + 0.009, 0.798, 1.6), M["steel"], "z", 0)
        cyl(f"t_ch_ferrule{k}", (hx, 0.795, 1.6), (hx, 0.795, 1.65), 0.008, M["brass"], 10)
        cyl(f"t_ch_handle{k}", (hx, 0.795, 1.65), (hx, 0.795, 1.75), 0.013, M["handle"], 12)

# on the top: a pine board and a hand plane on it
box("t_board_on_top", (0.75, 0.25, 0.9), (1.6, 0.4, 0.92), M["pine"], "x", 0.002)
box("t_plane_body", (1.12, 0.3, 0.92), (1.36, 0.355, 0.975), M["vise"], "x", 0.004)
box("t_plane_iron", (1.2, 0.315, 0.975), (1.23, 0.34, 1.02), M["steel"], "z", 0.002)
cyl("t_plane_knob", (1.32, 0.3275, 0.975), (1.32, 0.3275, 1.02), 0.013, M["handle"], 12)
poly_prism("t_plane_tote", [(1.14, 0.975), (1.18, 0.975), (1.17, 1.04), (1.14, 1.04)], 0.32, 0.335, M["handle"], "z")
# shelf: a coffee can of nails, tins, a jar
cyl("t_can_coffee", (0.3, 0.75, 1.89), (0.3, 0.75, 2.0 - 0.02), 0.055, M["red"], 20)
for k, (cx, r, hgt) in enumerate([(0.48, 0.04, 0.08), (0.58, 0.035, 0.06), (1.6, 0.04, 0.1)]):
    cyl(f"t_tin{k}", (cx, 0.78, 1.89), (cx, 0.78, 1.89 + hgt), r, M["tin"], 18)
box("t_box_shelf", (1.0, 0.68, 1.89), (1.35, 0.9, 1.97), M["frame"], "x")
# lower shelf: offcuts and a small crate
for k in range(3):
    box(f"t_offcut{k}", (0.25, 0.25 + k * 0.005, 0.222 + k * 0.025), (1.2 - k * 0.15, 0.4 + k * 0.005, 0.245 + k * 0.025), M["pine"], "x", 0.002)
for k, (a, b) in enumerate([((1.35, 0.3, 0.222), (1.75, 0.315, 0.42)), ((1.35, 0.685, 0.222), (1.75, 0.7, 0.42)),
                             ((1.35, 0.315, 0.222), (1.365, 0.685, 0.42)), ((1.735, 0.315, 0.222), (1.75, 0.685, 0.42)),
                             ((1.365, 0.315, 0.222), (1.735, 0.685, 0.235))]):
    box(f"t_crate{k}", a, b, M["pine"], "x" if k < 2 or k == 4 else "y", 0.002)
for k in range(5):
    box(f"t_crate_scrap{k}", (1.38 + k * 0.065, 0.34 + (k % 2) * 0.05, 0.235), (1.42 + k * 0.065, 0.6, 0.31 + (k % 3) * 0.03), M["frame"], "y", 0.002)
