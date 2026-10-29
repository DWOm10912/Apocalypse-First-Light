# Blackridge .50 game version, step 2 (as the BR-51 / P9 v3 game_uv.py): one 2048 UV atlas for the low poly. Smart UV
# Project splits at sharp edges, then one axis-aligned pack at one texel density (straight bevels land on texel rows).
# Runs headless (game_run.py): the operators get an object-mode / edit-mode context without a 3D view.
import bpy, math, os

exec(open(os.path.join(BASE, 'game_bones.py')).read())
lo = [o for o in bpy.data.collections['BR50_lo'].objects if o.type == 'MESH']
for o in bpy.context.view_layer.objects:
    o.select_set(False)
for o in lo:
    o.hide_set(False)
    o.select_set(True)
    if not o.data.uv_layers:
        o.data.uv_layers.new(name='UVMap')
bpy.context.view_layer.objects.active = lo[0]
bpy.ops.object.mode_set(mode='EDIT')
bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=math.radians(globals().get('UV_ANGLE', 33.0)), margin_method='SCALED',
                         island_margin=0.0, area_weight=0.0, correct_aspect=True, scale_to_bounds=False)
bpy.ops.uv.select_all(action='SELECT')
bpy.ops.uv.pack_islands(udim_source='CLOSEST_UDIM', rotate=True, rotate_method='AXIS_ALIGNED', scale=True,
                        merge_overlap=False, margin_method='FRACTION', margin=globals().get('ATLAS_MARGIN', 0.002),
                        shape_method='CONCAVE')
bpy.ops.object.mode_set(mode='OBJECT')

area3 = area2 = 0.0
for o in lo:
    uv = o.data.uv_layers.active.data
    for p in o.data.polygons:
        area3 += p.area
        pts = [uv[i].uv for i in p.loop_indices]
        area2 += sum(abs((pts[i] - pts[0]).cross(pts[i + 1] - pts[0])) / 2 for i in range(1, len(pts) - 1))
print('uv fill', round(area2, 3), 'texels/mm at 2048', round(math.sqrt(area2 * 2048 * 2048 / area3), 2))
