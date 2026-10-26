# Male survivor V1 -> game assets for the M2.5 in-game static test (docs/dev/mesh_survivor_v1_m25_ingame.md).
# Run inside survivor_male_v1.blend (Blender 5.2):
#   import sys, importlib; sys.path.insert(0, SRC); import export_game_male_v1 as X; importlib.reload(X); X.export()
#
# Writes (resource id apocalypse_firstlight:dev/survivor_male_v1; nothing of dev/survivor_base_h1p1 or P9 is touched)
#   meshes/dev/survivor_male_v1.vxmesh.json   Vextris format 4: the 54-bone tree (bones block, zero bind rotation) and
#                                             one skin per object, model-space blocks, faces the outfit masks hide dropped
#   textures/dev/survivor_male_v1.png         the eight base-colour maps packed into one atlas (one renderSkins call)
#   skins/dev/survivor_male_v1.poses.json     static test poses 0 + A-H: per bone the model-space deformation (blocks,
#                                             column-major), the P9-01 on hand_r (the H1-P1 grip matrix) and the head pivot
#
# Frames: Blender Z-up metres -> render (x, z, -y) blocks (1 m = 1 block), as export_geckolib with unit_px 16. The rig has
# no bind rotation in the game, so a bone's frame at rest is the identity and its frame in a pose is
# D = C (W_pose W_rest^-1) C^-1.
import bpy, json, math, os, sys, importlib
import numpy as np
from mathutils import Matrix, Vector

SRC = os.path.dirname(os.path.abspath(__file__)) if '__file__' in globals() else \
    r"D:/Minecraft Modding/Apocalypse First Light/design/characters/survivor_male_v1/source"
if SRC not in sys.path:
    sys.path.insert(0, SRC)
import skin_male_v1 as K

ROOT = os.path.normpath(os.path.join(SRC, '..', '..', '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'apocalypse_firstlight')
ID = 'dev/survivor_male_v1'
H1P1_SKIN = os.path.join(ASSETS, 'skins', 'dev', 'survivor_base_h1p1.skin.json')
C = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))
CI = C.inverted()

# atlas, top-left pixels: x, y, horizontal copies (the head's u runs to 1.47: its map is laid twice side by side)
ATLAS = (1024, 1280)
REGIONS = {
    'male_v1_head': (0, 0, 2),
    'male_v1_jacket': (0, 512, 1), 'male_v1_jeans': (512, 512, 1),
    'male_v1_body': (0, 1024, 1), 'male_v1_tee': (256, 1024, 1), 'male_v1_boots': (512, 1024, 1),
    'male_v1_hair': (768, 1024, 1), 'male_v1_belt': (768, 1152, 1),
}


# a corner whose smooth normal is within this of facing away from its triangle takes the face normal (thick garment rims)
FLIP_DOT = 0.02


def r6(x):
    v = round(float(x), 6)
    return 0.0 if v == 0 else v


def to_render(v):
    return Vector((v[0], v[2], -v[1]))


def image_of(ob):
    for n in ob.data.materials[0].node_tree.nodes:
        if n.type == 'TEX_IMAGE' and n.image:
            return n.image
    raise ValueError('no image on ' + ob.name)


def deform_bones():
    """The 54 deform bones, parents before children (the H1-P1 export order)."""
    arm = K.rig()
    out = []

    def walk(b):
        if b.use_deform:
            out.append(b)
        for c in b.children:
            walk(c)
    for b in arm.data.bones:
        if b.parent is None:
            walk(b)
    return out


def kept_parent(b, kept):
    p = b.parent
    while p is not None and p.name not in kept:
        p = p.parent
    return p


def atlas():
    W, H = ATLAS
    out = np.zeros((H, W, 4), np.float32)
    sizes = {}
    for name, (x, y, copies) in REGIONS.items():
        img = image_of(bpy.data.objects[name])
        w, h = img.size
        px = np.array(img.pixels[:], np.float32).reshape(h, w, 4)[::-1]    # top row first
        for k in range(copies):
            if x + (k + 1) * w > W or y + h > H:
                raise ValueError('atlas region of %s does not fit' % name)
            out[y:y + h, x + k * w:x + (k + 1) * w] = px
        sizes[name] = (w, h)
    img = bpy.data.images.get('survivor_male_v1_atlas') or bpy.data.images.new('survivor_male_v1_atlas', W, H, alpha=True)
    if tuple(img.size) != (W, H):
        img.scale(W, H)
    img.pixels[:] = out[::-1].ravel()
    path = os.path.join(ASSETS, 'textures', 'dev', 'survivor_male_v1.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.filepath_raw = path
    img.file_format = 'PNG'
    img.save()
    return path, sizes, float(out[..., 3][out[..., 3] > 0].min()) if (out[..., 3] > 0).any() else 0.0


def atlas_uv(name, size, u, v):
    """Blender uv -> atlas uv (top left), clamped half a texel inside the object's region (nearest sampling)."""
    W, H = ATLAS
    x, y, copies = REGIONS[name]
    w, h = size
    pu = min(max(u * w, 0.5), copies * w - 0.5)
    pv = min(max((1 - v) * h, 0.5), h - 0.5)
    return (x + pu) / W, (y + pv) / H


def rest_mesh(ob):
    """The rest shape as the approved look has it: shape keys applied (the head and hair 'stylise' key), no armature
    and no mask (vertex order of the original mesh). Free it with ob.evaluated_get(dg).to_mesh_clear()."""
    mods = [m for m in ob.modifiers if m.type in ('ARMATURE', 'MASK')]
    shown = [m.show_viewport for m in mods]
    try:
        for m in mods:
            m.show_viewport = False
        dg = bpy.context.evaluated_depsgraph_get()
        dg.update()
        me = ob.evaluated_get(dg).to_mesh()
        if len(me.vertices) != len(ob.data.vertices):
            raise ValueError('%s rest evaluation changed the vertex count' % ob.name)
        return me
    finally:
        for m, v in zip(mods, shown):
            m.show_viewport = v


def skin_of(ob, size, index_of, kept):
    me = rest_mesh(ob)
    me.calc_loop_triangles()
    uv = me.uv_layers.active.data
    normals = me.corner_normals
    show = K.visible(ob.name)                           # vertices the outfit mask keeps
    groups = {g.index: g.name for g in ob.vertex_groups}
    Mw = ob.matrix_world
    N = Mw.to_3x3().inverted().transposed()
    verts, norms, weights, faces, slot, source = [], [], [], [], {}, []
    joints, joint_at = [], {}
    dropped = flipped = 0
    for tri in me.loop_triangles:
        if not all(show[vi] for vi in tri.vertices):
            dropped += 1
            continue
        face = []
        fn = to_render((N @ tri.normal).normalized())
        for li in tri.loops:
            vi = me.loops[li].vertex_index
            n = to_render((N @ normals[li].vector).normalized())
            if n.dot(fn) < FLIP_DOT:                   # Vextris rejects a corner normal facing away from its face
                n = fn
                flipped += 1
            u, v = uv[li].uv
            key = (vi, round(u, 6), round(v, 6), round(n.x, 3), round(n.y, 3), round(n.z, 3))
            if key not in slot:
                p = to_render(Mw @ me.vertices[vi].co)
                au, av = atlas_uv(ob.name, size, u, v)
                verts.append([r6(p.x), r6(p.y), r6(p.z), r6(au), r6(av)])
                source.append(vi)
                norms.append([round(n.x, 4) + 0.0, round(n.y, 4) + 0.0, round(n.z, 4) + 0.0])
                w = sorted(((groups[g.group], g.weight) for g in ob.data.vertices[vi].groups
                            if groups.get(g.group) in kept and g.weight > 1e-4), key=lambda t: -t[1])[:4]
                if not w:
                    raise ValueError('%s vertex %d has no deform weight' % (ob.name, vi))
                s = sum(t[1] for t in w)
                ws = [round(t[1] / s, 4) for t in w]
                ws[0] = round(ws[0] + 1 - sum(ws), 4)      # the sum stays within the loader's 1e-3
                pairs = []
                for (bn, _), x in zip(w, ws):
                    if x <= 0:
                        continue
                    if bn not in joint_at:
                        joint_at[bn] = len(joints)
                        joints.append(bn)
                    pairs.append([joint_at[bn], x])
                weights.append(pairs)
                slot[key] = len(verts) - 1
            face.append(slot[key])
        faces.append(face)
    ob.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh_clear()
    # joints in skeleton order (stable diffs): remap the indices
    order = sorted(joints, key=lambda j: index_of[j])
    remap = {joints.index(j): i for i, j in enumerate(order)}
    for pairs in weights:
        for p in pairs:
            p[0] = remap[p[0]]
    return dict(name=ob.name.replace('male_v1_', ''), render_layer='cutout', joints=order, vertices=verts,
                normals=norms, faces=faces, weights=weights), dropped, source, flipped


def frames(bones, kept, sources=None):
    """Every bone's model-space deformation (blocks, column-major) in each test pose; with {object: slot -> vertex},
    also Blender's own deformed position of every REF_STEP-th slot (blocks) for the offline check."""
    arm = K.rig()
    rest = {b.name: arm.matrix_world @ b.matrix_local for b in bones}
    act = arm.animation_data.action if arm.animation_data else None
    pose_position = arm.data.pose_position
    gun = bpy.data.objects.get('fp_gun')
    gun_matrix = gun.matrix_world.copy() if gun else None
    out = {}
    try:
        if arm.animation_data:
            arm.animation_data.action = None
        arm.data.pose_position = 'POSE'
        for name, fn in K.POSES:
            K.reset()
            K.ik(name.startswith('H_'))
            fn()
            K.update()
            pose = {}
            for b in bones:
                W = arm.matrix_world @ arm.pose.bones[b.name].matrix
                D = C @ (W @ rest[b.name].inverted()) @ CI
                pose[b.name] = [r6(D[r][c]) for c in range(4) for r in range(4)]
            out[name] = pose
            if sources:
                dg = bpy.context.evaluated_depsgraph_get()
                K.masks(False)                          # same vertex order as the rest mesh
                dg.update()
                for n, src in sources.items():
                    ev = bpy.data.objects[n].evaluated_get(dg)
                    me = ev.to_mesh()
                    REF.setdefault(n, {})[name] = [[r6(c) for c in to_render(ev.matrix_world @ me.vertices[src[i]].co)]
                                                   for i in range(0, len(src), REF_STEP)]
                    ev.to_mesh_clear()
                K.masks(True)
    finally:
        K.reset()
        K.ik(True)
        arm.data.pose_position = pose_position
        if arm.animation_data:
            arm.animation_data.action = act
        if gun:
            gun.matrix_world = gun_matrix
        K.update()
    return out


REF_STEP = 7
REF = {}
REF_PATH = os.path.join(os.environ.get('TEMP', SRC), 'claude', 'D--Minecraft-Modding-Apocalypse-First-Light',
                        '691c66af-2c9d-4aff-8c65-9100f7c7271f', 'scratchpad', 'vx', 'male_ref.json')


def write(rel, data, compact=True):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        if compact:
            json.dump(data, fh, separators=(',', ':'))
        else:
            json.dump(data, fh, indent=1)
        fh.write('\n')
    return path


def export():
    before = K.fingerprint()
    REF.clear()
    bones = deform_bones()
    kept = {b.name for b in bones}
    index_of = {b.name: i for i, b in enumerate(bones)}
    arm = K.rig()
    bone_block = []
    for b in bones:
        p = to_render(arm.matrix_world @ b.head_local)
        e = {'name': b.name, 'pivot': [r6(p.x), r6(p.y), r6(p.z)]}
        par = kept_parent(b, kept)
        if par:
            e['parent'] = par.name
        bone_block.append(e)
    tex, sizes, min_alpha = atlas()
    skins, dropped, sources, flips = [], {}, {}, {}
    for n in K.NAMES:
        s, d, src, f = skin_of(bpy.data.objects[n], sizes[n], index_of, kept)
        flips[n] = f
        skins.append(s)
        dropped[n] = d
        sources[n] = src
    mesh = {'format_version': 4, 'coordinate_space': 'bone_pivot_local_blocks', 'uv_origin': 'top_left', 'winding': 'ccw',
            'texture_size': list(ATLAS), 'bones': bone_block, 'skins': skins}
    files = [write('meshes/%s.vxmesh.json' % ID, mesh), tex]
    with open(H1P1_SKIN, encoding='utf-8') as fh:
        grip = json.load(fh)['props']['p9_01']
    head = arm.data.bones['head']
    hp = to_render(arm.matrix_world @ head.head_local)
    poses = {'format': 'afl_skin_poses', 'version': 1, 'space': 'model_blocks', 'matrix_order': 'column_major',
             'bones': [b.name for b in bones], 'head_pivot': [r6(hp.x), r6(hp.y), r6(hp.z)],
             'props': {'p9_01': grip}, 'poses': frames(bones, kept, sources)}
    files.append(write('skins/%s.poses.json' % ID, poses))
    with open(REF_PATH, 'w', encoding='utf-8') as fh:
        json.dump(dict(step=REF_STEP, skins=[s['name'] for s in skins], objects=list(K.NAMES), posed=REF), fh)
    after = K.fingerprint()
    summary = dict(files=files, bones=len(bones), skins=len(skins),
                   tris={s['name']: len(s['faces']) for s in skins}, total_tris=sum(len(s['faces']) for s in skins),
                   slots=sum(len(s['vertices']) for s in skins), dropped=dropped, face_normal_corners=flips,
                   joints={s['name']: len(s['joints']) for s in skins}, min_alpha=round(min_alpha, 3),
                   max_influences=max(len(w) for s in skins for w in s['weights']),
                   rig_unchanged=before == after, size_kb=round(os.path.getsize(files[0]) / 1024))
    print('export', json.dumps(summary))
    return summary
