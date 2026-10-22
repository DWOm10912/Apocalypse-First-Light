# BR-51 game version, step 2 (as the P9 v3 game_uv.py): one UV atlas for the low poly parts of ATLAS (game_bones.py:
# 'gun' = the rifle and its 20-round magazine, 'mags' = the 30-round magazine and the drum). Smart UV Project splits at
# sharp edges (the hard-edge + UV-seam layout keeps the baked normal map independent of the tangent basis), then one pack
# at one texel density. ATLAS_MARGIN is a fraction of the atlas (0.002 = 4 px at 2048).
import bpy, math, os

exec(open(os.path.join(BASE, 'game_bones.py')).read())
lo = [o for o in bpy.data.collections['BR51_lo'].objects if o.type == 'MESH' and atlas_of(o.name) == ATLAS]
# long parts get their UV islands cut across their length at existing section rings (the stock's side silhouette as one
# island packs badly): after the projection each band's UVs move apart, so the pack sees separate islands (mesh unchanged)
SPLIT = globals().get('UV_SPLIT', {'stock': (230.0, 330.0, 560.0), 'hg_wood': (692.0,), 'brl': (680.0,)})
view = next(a for a in bpy.context.screen.areas if a.type == 'VIEW_3D')
region = next(r for r in view.regions if r.type == 'WINDOW')
for o in bpy.context.view_layer.objects:
    o.select_set(False)
for o in lo:
    o.hide_set(False)
    o.select_set(True)
    if not o.data.uv_layers:
        o.data.uv_layers.new(name='UVMap')
bpy.context.view_layer.objects.active = lo[0]
with bpy.context.temp_override(area=view, region=region, active_object=lo[0], selected_objects=lo, selected_editable_objects=lo):
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(globals().get('UV_ANGLE', 33.0)), margin_method='SCALED',
                             island_margin=0.0, area_weight=0.0, correct_aspect=True, scale_to_bounds=False)
    bpy.ops.object.mode_set(mode='OBJECT')
    for o in lo:
        ys = SPLIT.get(base_name(o.name))
        if not ys:
            continue
        uv = o.data.uv_layers.active.data
        for p in o.data.polygons:
            band = sum(1 for y in ys if p.center.y > y)
            for i in p.loop_indices:
                uv[i].uv.x += 0.01 * band   # inside the 0-1 tile (the pack keeps islands in their closest UDIM)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.select_all(action='SELECT')
    # axis-aligned islands keep straight bevels on texel rows (no staircase rims along the edge highlights)
    bpy.ops.uv.pack_islands(udim_source='CLOSEST_UDIM', rotate=True,
                            rotate_method=globals().get('PACK_ROTATE', 'AXIS_ALIGNED'), scale=True,
                            merge_overlap=False, margin_method='FRACTION', margin=globals().get('ATLAS_MARGIN', 0.002),
                            shape_method=globals().get('PACK_SHAPE', 'CONCAVE'))
    bpy.ops.object.mode_set(mode='OBJECT')

# texel density report (texels per mm at 2048) and fill ratio
area3 = 0.0
area2 = 0.0
for o in lo:
    me = o.data
    uv = me.uv_layers.active.data
    for p in me.polygons:
        area3 += p.area
        pts = [uv[i].uv for i in p.loop_indices]
        a = 0.0
        for i in range(1, len(pts) - 1):
            a += abs((pts[i] - pts[0]).cross(pts[i + 1] - pts[0])) / 2
        area2 += a
print('uv fill', round(area2, 3), 'texels/mm at 2048', round(math.sqrt(area2 * 2048 * 2048 / area3), 2))
