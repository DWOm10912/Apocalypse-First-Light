"""Box Stove V2: the baked meshes as cabin pieces (docs/models/box_stove_v2_plan.md, in-game step 1).

Reads design/furniture/box_stove_v2/source/box_stove_v2.blend (build.py) and writes design/furniture/box_stove_v2/export/
{body,firebox,door}.json in the cabin piece format (CabinMeshModel: {"textures": [ids], "quads": [[tex, (x, y, z, u, v,
nx, ny, nz) x 4]]}, a triangle a quad with its last corner twice), per-corner normals (the Weighted Normal ones), UVs into
the one baked sprite each (top-left origin). tools/build-box-stove-v1.mjs copies them to cabin_mesh/stove/ and makes the
door's animated mesh from door.json. Frame: Blender (x, -z, y) = cabin (x, y, z), see model.py P.
Also prints the ash bed's height under a few points (the splits' places).

  blender -b --factory-startup --python tools/stove/box_stove_v2/export.py
"""
import bpy, json, os
from mathutils import Vector

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
D = os.path.join(ROOT, "design", "furniture", "box_stove_v2")
OUT = os.path.join(D, "export")
NS = "apocalypse_firstlight:block/stove/"
PIECES = {"body": ("box_stove_v2_body", "box_stove_body"), "firebox": ("box_stove_v2_inside", "box_stove_firebox"),
          "door": ("box_stove_v2_door", "box_stove_door")}

bpy.ops.wm.open_mainfile(filepath=os.path.join(D, "source", "box_stove_v2.blend"))
dg = bpy.context.evaluated_depsgraph_get()


def num(v):
    v = round(v, 5) + 0.0
    if v == 0: return "0"
    s = repr(v)
    return s[:-2] if s.endswith(".0") else s


def mc(co):   # Blender -> cabin frame
    return (co.x, co.z, -co.y)


def piece(obname, tex):
    ob = bpy.data.objects[obname].evaluated_get(dg)
    me = ob.to_mesh()
    me.transform(ob.matrix_world)
    uv = me.uv_layers.active.data
    cn = me.corner_normals
    quads, tris, skipped = [], 0, 0
    def corner(li):
        lo = me.loops[li]
        p, n, t = mc(me.vertices[lo.vertex_index].co), mc(cn[li].vector), uv[li].uv
        return [*p, t[0], 1 - t[1], *n]
    def area(ls):
        a, b, c = (me.vertices[me.loops[l].vertex_index].co for l in ls[:3])
        return (b - a).cross(c - a).length
    me.calc_loop_triangles()
    by_poly = {}
    for lt in me.loop_triangles: by_poly.setdefault(lt.polygon_index, []).append(list(lt.loops))
    for p in me.polygons:
        ls = list(p.loop_indices)
        if len(ls) == 4 and area(ls) > 1e-10 and area([ls[0], ls[2], ls[3]]) > 1e-10:
            quads.append(ls); tris += 2; continue
        for t in by_poly.get(p.index, []):
            if area(t) <= 1e-10: skipped += 1; continue
            quads.append(t + [t[2]]); tris += 1
    for q in quads:
        for li in q:
            u, v = uv[li].uv
            assert -1e-6 <= u <= 1 + 1e-6 and -1e-6 <= v <= 1 + 1e-6, (obname, u, v)
    rows = ["[0," + ",".join(num(c) for li in q for c in corner(li)) + "]" for q in quads]
    text = '{"textures":["' + NS + tex + '"],"quads":[' + ",".join(rows) + "]}\n"
    ob.to_mesh_clear()
    return text, len(quads), tris, skipped


os.makedirs(OUT, exist_ok=True)
for name, (obname, tex) in PIECES.items():
    text, nq, nt, sk = piece(obname, tex)
    with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8", newline="\n") as f: f.write(text)
    print(f"EXPORT {name}: {nq} quads, {nt} tris, {sk} zero-area skipped, {len(text) // 1024} KiB")

# the ash bed (and the charcoal on it) under some points: cast down inside the firebox
ins = bpy.data.objects["box_stove_v2_inside"]
inv = ins.matrix_world.inverted()
for z in (0.28, 0.33, 0.38, 0.43, 0.48):
    row = []
    for x in (0.12, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.88):
        o, d = inv @ Vector((x, -z, 0.5)), (inv.to_3x3() @ Vector((0, 0, -1))).normalized()
        hit, loc, *_ = ins.ray_cast(o, d)
        row.append(f"{(ins.matrix_world @ loc).z:.3f}" if hit else "  -  ")
    print(f"ASH z {z:.2f}:", " ".join(row))
