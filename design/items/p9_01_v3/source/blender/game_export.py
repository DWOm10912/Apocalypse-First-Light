# P9 v3 game version, step 5: export the low poly (P9_lo) for tools/build-p9-01-v3-native.mjs.
# Writes EXPORT_PATH (JSON): {texture_size, parts: [{name, bone, vertices: [[x, y, z] Blockbench px], faces: [{v: [i...],
# uv: [[u, v] texture px, top-left]}]}]}. Concept mm -> Blockbench px: x / MM_PER_PX, z / MM_PER_PX - Y0, -y / MM_PER_PX
# (muzzle -Z, the same bind space as the v2 native rig). Faces stay as Blender has them (triangles, planar quads).
import bpy, json, math

MM_PER_PX = 16.99015189435616
Y0 = 1.048045            # v2 rig: the concept's ground line sits 1.048 px under the Blockbench origin
TEX = globals().get('TEX_SIZE', 2048)


def bone_of(name):
    b = name.split('_', 1)[1]
    if b.startswith('slide_rsight') or b.startswith('slide_rdot'):
        return 'rear_sight'
    if b.startswith('slide_fsight') or b.startswith('slide_fdot'):
        return 'front_sight'
    if b.startswith('slide'):
        return 'slide'
    if b.startswith('barrel'):
        return 'barrel'
    if b == 'part_trigger':
        return 'trigger'
    if b == 'part_mag_follower':
        return 'follower'
    if b.startswith('part_mag') and not b.startswith('part_magrel'):
        return 'magazine'
    return 'gun_body'


def to_px(co):
    return [co.x / MM_PER_PX, co.z / MM_PER_PX - Y0, -co.y / MM_PER_PX]


parts = []
skipped = 0
for o in sorted(bpy.data.collections['P9_lo'].objects, key=lambda o: o.name):
    me = o.data
    uv = me.uv_layers.active.data
    mw = o.matrix_world
    verts = [to_px(mw @ v.co) for v in me.vertices]
    faces = []
    for p in me.polygons:
        if p.area < 1e-6:
            skipped += 1
            continue
        faces.append({'v': list(p.vertices), 'uv': [[uv[i].uv.x * TEX, (1.0 - uv[i].uv.y) * TEX] for i in p.loop_indices]})
    parts.append({'name': o.name[3:], 'bone': bone_of(o.name), 'vertices': verts, 'faces': faces})
with open(EXPORT_PATH, 'w') as f:
    json.dump({'texture_size': TEX, 'parts': parts}, f)
print('exported', len(parts), 'parts', sum(len(p['faces']) for p in parts), 'faces, skipped', skipped)
for b in sorted(set(p['bone'] for p in parts)):
    print(b, [p['name'] for p in parts if p['bone'] == b])
