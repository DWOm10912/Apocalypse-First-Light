"""AFL Block-Mesh V2: the four glTF prototypes as editable .blend files (Blender background).

Usage: blender -b --factory-startup -P bm2_blend.py -- <models dir>
Each file: the armature (22 bones, D 52) with the skinned mesh and its 128 x 128 skin (closest filtering), nothing else.
"""
import os
import sys

import bpy

models = sys.argv[sys.argv.index('--') + 1]
for proto in 'ABCD':
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=os.path.join(models, 'block_mesh_v2_%s.gltf' % proto))
    for mat in bpy.data.materials:
        if mat.node_tree:
            for n in mat.node_tree.nodes:
                if n.type == 'TEX_IMAGE':
                    n.interpolation = 'Closest'
    for img in bpy.data.images:
        img.pack()
    arm = [o for o in bpy.data.objects if o.type == 'ARMATURE']
    mesh = [o for o in bpy.data.objects if o.type == 'MESH']
    if arm:
        arm[0].name = 'AFL_BlockMesh_V2_%s_rig' % proto
    if mesh:
        mesh[0].name = 'AFL_BlockMesh_V2_%s' % proto
    out = os.path.join(models, 'block_mesh_v2_%s.blend' % proto)
    bpy.ops.wm.save_as_mainfile(filepath=out, compress=True)
    print('saved', out, 'bones', len(arm[0].data.bones) if arm else 0, 'verts', len(mesh[0].data.vertices) if mesh else 0)
