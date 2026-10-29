# Blackridge .50 game version, step 5 (as the BR-51 game_export.py): export the low poly (BR50_lo) for
# tools/build-blackridge-50-v2-native.mjs. Writes EXPORT_PATH (JSON): {texture_size, parts: [{name, bone, vertices:
# [[x, y, z] Blockbench px], faces: [{v: [i...], uv: [[u, v] texture px, top-left]}]}]}.
# Concept mm -> Blockbench px: the P9 V3 scale (16.99015 mm per px, one ruler for the pistols, docs 7) with the grip's
# hand point (y HAND_Y, z HAND_Z mm: the grip's middle under the beavertail) on the P9 rig's hand pivot [0, 3, 2.06]:
#   x / MM_PER_PX, 3 + (z - HAND_Z) / MM_PER_PX, 2.06 - (y - HAND_Y) / MM_PER_PX      (muzzle -Z, +X the right side)
# The same mapping is in the build tool (mm()). Faces stay as Blender has them (triangles, planar quads).
import bpy, json, os

exec(open(os.path.join(BASE, 'game_bones.py')).read())
MM_PER_PX = 16.99015
HAND_Y, HAND_Z = 68.0, 78.0
TEX = 2048


def to_px(co):
    return [co.x / MM_PER_PX, 3.0 + (co.z - HAND_Z) / MM_PER_PX, 2.06 - (co.y - HAND_Y) / MM_PER_PX]


parts = []
skipped = 0
dg = bpy.context.evaluated_depsgraph_get()
for o in sorted(bpy.data.collections['BR50_lo'].objects, key=lambda o: o.name):
    if o.type != 'MESH':
        continue
    me = bpy.data.meshes.new_from_object(o.evaluated_get(dg))
    uv = me.uv_layers.active.data
    mw = o.matrix_world
    verts = [to_px(mw @ v.co) for v in me.vertices]
    faces = []
    for p in me.polygons:
        if p.area < 1e-6:
            skipped += 1
            continue
        faces.append({'v': list(p.vertices), 'uv': [[uv[i].uv.x * TEX, (1.0 - uv[i].uv.y) * TEX] for i in p.loop_indices]})
    parts.append({'name': base_name(o.name), 'bone': bone_of(o.name), 'vertices': verts, 'faces': faces})
    bpy.data.meshes.remove(me)
with open(EXPORT_PATH, 'w') as f:
    json.dump({'texture_size': TEX, 'atlas': 'gun', 'parts': parts}, f)
print('exported', len(parts), 'parts', sum(len(p['faces']) for p in parts), 'faces, skipped', skipped)
for b in sorted(set(p['bone'] for p in parts)):
    print(b, [p['name'] for p in parts if p['bone'] == b])
