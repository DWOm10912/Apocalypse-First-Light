"""Kitchen stoves (Cooking V1 K1c): the baked mesh as cabin pieces (tools/stove/box_stove_v2/export.py's format).

Reads design/furniture/<name>/source/<name>.blend (kitchen_build.py) and writes design/furniture/<name>/export/body.json
(and for the wood cookstove, two cells high, top.json: the faces above y 1, moved down a cell, drawn by the upper half),
per-corner normals, UVs into the one baked sprite (top-left origin). tools/build-kitchen-stoves-v1.mjs copies them in.
Frame: Blender (x, -z, y) = cabin (x, y, z).

  blender -b --factory-startup --python tools/stove/kitchen_export.py -- --model cookstove_v1|propane_range_v1
"""
import bpy, os, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
MODEL = argv[argv.index("--model") + 1] if "--model" in argv else "cookstove_v1"
NAME = {"cookstove_v1": "wood_cookstove", "propane_range_v1": "propane_range"}[MODEL]
D = os.path.join(ROOT, "design", "furniture", NAME)
OUT = os.path.join(D, "export")
TEX = "apocalypse_firstlight:block/kitchen/" + NAME + "_body"

bpy.ops.wm.open_mainfile(filepath=os.path.join(D, "source", NAME + ".blend"))
dg = bpy.context.evaluated_depsgraph_get()


def num(v):
    v = round(v, 5) + 0.0
    if v == 0: return "0"
    s = repr(v)
    return s[:-2] if s.endswith(".0") else s


ob = bpy.data.objects[NAME + "_body"].evaluated_get(dg)
me = ob.to_mesh()
me.transform(ob.matrix_world)
uv = me.uv_layers.active.data
cn = me.corner_normals
me.calc_loop_triangles()
by_poly = {}
for lt in me.loop_triangles: by_poly.setdefault(lt.polygon_index, []).append(list(lt.loops))


def area(ls):
    a, b, c = (me.vertices[me.loops[l].vertex_index].co for l in ls[:3])
    return (b - a).cross(c - a).length


cells = {"body": [], "top": []}
skipped = 0
for p in me.polygons:
    cell = "top" if p.center.z > 1 else "body"
    ls = list(p.loop_indices)
    if len(ls) == 4 and area(ls) > 1e-10 and area([ls[0], ls[2], ls[3]]) > 1e-10:
        cells[cell].append(ls); continue
    for t in by_poly.get(p.index, []):
        if area(t) <= 1e-10: skipped += 1; continue
        cells[cell].append(t + [t[2]])

os.makedirs(OUT, exist_ok=True)
for cell, quads in cells.items():
    if not quads: continue
    dy = -1.0 if cell == "top" else 0.0
    rows = []
    for q in quads:
        cs = []
        for li in q:
            co, n, t = me.vertices[me.loops[li].vertex_index].co, cn[li].vector, uv[li].uv
            assert -1e-6 <= t[0] <= 1 + 1e-6 and -1e-6 <= t[1] <= 1 + 1e-6, (t[0], t[1])
            cs += [co.x, co.z + dy, -co.y, t[0], 1 - t[1], n.x, n.z, -n.y]
        rows.append("[0," + ",".join(num(c) for c in cs) + "]")
    text = '{"textures":["' + TEX + '"],"quads":[' + ",".join(rows) + "]}\n"
    with open(os.path.join(OUT, cell + ".json"), "w", encoding="utf-8", newline="\n") as f: f.write(text)
    ys = [me.vertices[me.loops[li].vertex_index].co.z + dy for q in quads for li in q]
    print(f"EXPORT {NAME} {cell}: {len(quads)} quads, y {min(ys):.3f}..{max(ys):.3f}, {len(text) // 1024} KiB")
print("zero-area skipped", skipped)
ob.to_mesh_clear()
