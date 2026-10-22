# BR-51 game version, step 5 (as the P9 v3 game_export.py): export the low poly (BR51_lo) of atlas ATLAS for
# tools/build-br51-01-v3-native.mjs. Writes EXPORT_PATH (JSON): {texture_size, atlas, parts: [{name, bone, vertices:
# [[x, y, z] Blockbench px], faces: [{v: [i...], uv: [[u, v] texture px, top-left]}]}]}.
# Concept mm -> Blockbench px (the old BR-51 rig's space: bore axis at y 11.4375, muzzle crown at z -28, muzzle -Z):
#   x / MM_PER_PX, Y_BORE + z / MM_PER_PX, Z_MUZZLE + (Y_MUZZLE - y) / MM_PER_PX
# Faces stay as Blender has them (triangles, planar quads).
import bpy, json, os

exec(open(os.path.join(BASE, 'game_bones.py')).read())
MM_PER_PX = 20.603
Y_BORE, Z_MUZZLE, Y_MUZZLE = 11.4375, -28.0, 1126.0
TEX = globals().get('TEX_SIZE', 2048)


def to_px(co):
    return [co.x / MM_PER_PX, Y_BORE + co.z / MM_PER_PX, Z_MUZZLE + (Y_MUZZLE - co.y) / MM_PER_PX]


parts = []
skipped = 0
for o in sorted(bpy.data.collections['BR51_lo'].objects, key=lambda o: o.name):
    if o.type != 'MESH' or atlas_of(o.name) != ATLAS:
        continue
    me = bpy.data.meshes.new_from_object(o.evaluated_get(bpy.context.evaluated_depsgraph_get()))
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
    json.dump({'texture_size': TEX, 'atlas': ATLAS, 'parts': parts}, f)
print('exported', ATLAS, len(parts), 'parts', sum(len(p['faces']) for p in parts), 'faces, skipped', skipped)
for b in sorted(set(p['bone'] for p in parts)):
    print(b, [p['name'] for p in parts if p['bone'] == b])
