# Blender -> GeckoLib + Vextris exporter for a rigged armature (generic: any bone tree, any actions, any meshes).
# Run inside Blender:  ns = {}; exec(open(path).read(), ns); ns['export'](CONFIG)
#
# Writes
#   geo/<id>.geo.json              GeckoLib bones: pivots at the rest heads, zero bind rotation, optional locators
#   animations/<id>.animation.json every clip baked every frame and reduced (linear keys within tolerance)
#   meshes/<id>.aflmesh.json       rigid pieces (A): one part per mesh object parented to a bone, bone-pivot-local
#   skins/<id>.skin.json           skinned mesh (B): model-space vertices, normals, uv, <= 4 bone weights, triangles
#   <ref>                          a Blender reference dump (posed bone matrices and sample vertices) for the offline check
#
# Frames. Blender is Z-up; GeckoLib's render frame (what the PoseStack sees inside a bone walk) is Y-up with
# render = (bx, bz, -by) * unit_px, units px (unit_px: render px per Blender unit, 1 for a scene built in px, 16 for a
# scene in metres at 1 block = 1 m). Geo pivots and positions store x negated, rotations store x and y negated
# (GeckoLib applies Rz Ry Rx). A bone's deformation is D = W(t) W(rest)^-1; with zero bind rotation GeckoLib composes
# the bone as parent * T(pivot + pos) R T(-pivot), so its local transform is D_parent^-1 D.
#
# Config (keys beyond the H1-P0 ones are optional, defaults keep the H1-P0 export byte for byte):
#   bones       None = every bone; 'deform' = bones with use_deform (+ extra_bones); or a list of names. A kept bone's
#               GeckoLib parent is its nearest kept ancestor, so IK controls and other helpers stay out of the game.
#   clips       [{name, action, frames: (f0, f1), loop, ref_frames}] several animations in one file; without it the
#               single clip is action / frames / animation / loop / ref_frames, and the reference keeps the H1-P0 layout
#   skin_extra  dict merged into the skin file (e.g. prop attachment matrices)
import bpy, json, math, os
from mathutils import Matrix, Vector

C = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))    # Blender -> render
CI = C.inverted()


def to_render(v):
    return Vector((v[0], v[2], -v[1]))


def zyx_degrees(m):
    """M = Rz(a) Ry(b) Rx(c) -> (a, b, c) degrees."""
    b = math.asin(max(-1.0, min(1.0, -m[2][0])))
    if abs(m[2][0]) < 0.999999:
        a, c = math.atan2(m[1][0], m[0][0]), math.atan2(m[2][1], m[2][2])
    else:                                   # gimbal: put everything in a
        a, c = math.atan2(-m[0][1], m[1][1]), 0.0
    return tuple(math.degrees(x) for x in (a, b, c))


def unwrap(seq):
    out, prev = [], None
    for v in seq:
        if prev is not None:
            v = [x + 360.0 * round((p - x) / 360.0) for x, p in zip(v, prev)]
        out.append(list(v))
        prev = out[-1]
    return out


def reduce_keys(times, values, tol):
    """Indices of keys to keep so linear interpolation stays within tol of every baked sample."""
    keep, start, n = [0], 0, len(times)
    while start < n - 1:
        end = start + 1
        while end + 1 < n:
            cand = end + 1
            ok = True
            for i in range(start + 1, cand):
                u = (times[i] - times[start]) / (times[cand] - times[start])
                for k in range(3):
                    if abs(values[start][k] + (values[cand][k] - values[start][k]) * u - values[i][k]) > tol:
                        ok = False
                        break
                if not ok:
                    break
            if not ok:
                break
            end = cand
        keep.append(end)
        start = end
    return keep


def r4(x, n=4):
    v = round(float(x), n)
    return 0.0 if v == 0 else v


def bones_in_order(arm, keep=None):
    out = []

    def walk(b):
        if keep is None or keep(b):
            out.append(b)
        for c in b.children:
            walk(c)
    for b in arm.data.bones:
        if b.parent is None:
            walk(b)
    return out


def rest_world(arm, b):
    return arm.matrix_world @ b.matrix_local


def assign_action(arm, act):
    ad = arm.animation_data or arm.animation_data_create()
    ad.action = act
    if hasattr(ad, 'action_slot') and ad.action_slot is None and len(getattr(act, 'slots', ())):
        ad.action_slot = act.slots[0]


def export(cfg):
    arm = bpy.data.objects[cfg['armature']]
    sc = bpy.context.scene
    k = float(cfg.get('unit_px', 1.0))
    Cs = Matrix.Diagonal((k, k, k, 1.0)) @ C                                 # Blender units -> render px
    CsI = Cs.inverted()

    def P(v):
        return to_render(v) * k

    sel = cfg.get('bones')
    if sel is None:
        keep = None
    elif sel == 'deform':
        extra = set(cfg.get('extra_bones', ()))
        keep = lambda b: b.use_deform or b.name in extra
    else:
        keep = lambda b, s=set(sel): b.name in s
    bones = bones_in_order(arm, keep)
    names = [b.name for b in bones]
    kept = set(names)

    def parent_of(b):
        p = b.parent
        while p is not None and p.name not in kept:
            p = p.parent
        return p
    parents = {b.name: parent_of(b) for b in bones}
    rest = {b.name: rest_world(arm, b) for b in bones}
    pivot = {n: P(rest[n].translation) for n in names}                     # render px
    fps = cfg.get('fps', sc.render.fps)
    root = cfg['assets']
    ident = cfg['id']
    f_start = sc.frame_current
    legacy = not cfg.get('clips')
    clips = cfg.get('clips') or [dict(name=cfg['animation'], action=cfg['action'], frames=cfg['frames'],
                                      loop=cfg.get('loop', 'hold_on_last_frame'), ref_frames=cfg.get('ref_frames', []))]

    # ---- geo
    geo_bones = []
    for b in bones:
        e = {'name': b.name, 'pivot': [r4(-pivot[b.name].x), r4(pivot[b.name].y), r4(pivot[b.name].z)]}
        if parents[b.name]:
            e['parent'] = parents[b.name].name
        locs = {kk: v for kk, v in cfg.get('locators', {}).items() if v[0] == b.name}
        if locs:
            e['locators'] = {}
            for kk, (bn, offset) in locs.items():   # offset: Blender units in the bone's rest frame
                p = P(rest[bn] @ Vector(offset))
                e['locators'][kk] = [r4(-p.x), r4(p.y), r4(p.z)]
        geo_bones.append(e)
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.' + ident.split('/')[-1], 'texture_width': cfg['texture_size'][0],
                        'texture_height': cfg['texture_size'][1], 'visible_bounds_width': cfg.get('bounds', (4, 4))[0],
                        'visible_bounds_height': cfg.get('bounds', (4, 4))[1], 'visible_bounds_offset': [0, 1, 0]},
        'bones': geo_bones}]}

    # ---- A: rigid pieces, bone-pivot-local blocks
    # Pieces in a collection hidden in the viewport are not re-evaluated (their matrix_world stays at whatever frame
    # they were last shown), so they are shown for the export, and the rest placement is computed from the bone:
    # world = armature @ bone rest @ T(tail) @ parent inverse @ basis.
    hidden = [c for c in bpy.data.collections if c.hide_viewport and any(o in c.objects.values() for o in cfg.get('rigid', []))]
    for c in hidden:
        c.hide_viewport = False
    bpy.context.view_layer.update()
    parts, rigid_sample = [], {}                       # rigid_sample: part -> mesh vertex indices for the reference
    tw, th = cfg['texture_size']
    for ob in sorted(cfg.get('rigid', []), key=lambda o: names.index(o.parent_bone)):
        if ob.parent != arm or ob.parent_type != 'BONE' or ob.modifiers:
            raise ValueError('rigid piece %s must be bone-parented to %s with no modifiers' % (ob.name, arm.name))
        b = arm.data.bones[ob.parent_bone]
        piece_rest = arm.matrix_world @ b.matrix_local @ Matrix.Translation((0, b.length, 0)) @ ob.matrix_parent_inverse @ ob.matrix_basis
        me = ob.data
        me.calc_loop_triangles()
        uv = me.uv_layers.active.data
        verts, index, faces = [], {}, []
        for tri in me.loop_triangles:
            face = []
            for li in tri.loops:
                vi = me.loops[li].vertex_index
                u, v = uv[li].uv
                key = (vi, round(u, 6), round(v, 6))
                if key not in index:
                    p = P(piece_rest @ me.vertices[vi].co) - pivot[ob.parent_bone]
                    index[key] = len(verts)
                    verts.append([r4(p.x / 16, 6), r4(p.y / 16, 6), r4(p.z / 16, 6), r4(u, 6), r4(1 - v, 6)])
                face.append(index[key])
            faces.append(face)
        slots = sorted(index.items(), key=lambda kv: kv[1])
        rigid_sample[ob.name] = [(si, vi) for (vi, _, _), si in slots[::max(1, len(slots) // 12)]]
        parts.append({'name': ob.name, 'bone': ob.parent_bone, 'render_layer': 'cutout', 'vertices': verts, 'faces': faces})
    aflmesh = {'format_version': 3, 'coordinate_space': 'bone_pivot_local_blocks', 'uv_origin': 'top_left', 'winding': 'ccw',
               'texture_size': [tw, th], 'crease_angle': cfg.get('crease_angle', 70), 'parts': parts}

    # ---- B: skinned mesh, model-space blocks (rest), weights by bone index
    skin, ref_skin, slot_vi = None, None, None
    if cfg.get('skinned'):
        ob = cfg['skinned']
        me = ob.data                                   # the rest mesh (armature modifier not applied)
        me.calc_loop_triangles()
        uv = me.uv_layers.active.data
        groups = {g.index: g.name for g in ob.vertex_groups}
        M = ob.matrix_world
        N = M.to_3x3().inverted().transposed()
        verts, weights, index, tris = [], [], {}, []
        for tri in me.loop_triangles:
            t = []
            for li in tri.loops:
                vi = me.loops[li].vertex_index
                u, v = uv[li].uv
                key = (vi, round(u, 6), round(v, 6))
                if key not in index:
                    p = P(M @ me.vertices[vi].co)
                    nn = to_render((N @ me.loops[li].normal).normalized())
                    index[key] = len(verts)
                    verts.append([r4(p.x / 16, 6), r4(p.y / 16, 6), r4(p.z / 16, 6), r4(u, 6), r4(1 - v, 6),
                                  r4(nn.x, 4), r4(nn.y, 4), r4(nn.z, 4)])
                    w = sorted(((groups[g.group], g.weight) for g in me.vertices[vi].groups
                                if groups.get(g.group) in kept and g.weight > 1e-4), key=lambda x: -x[1])[:4]
                    s = sum(x[1] for x in w) or 1.0
                    weights.append([[names.index(bn), r4(x / s, 4)] for bn, x in w])
                t.append(index[key])
            tris.append(t)
        skin = {'format': 'afl_skin', 'version': 1, 'coordinate_space': 'model_blocks', 'uv_origin': 'top_left',
                'winding': 'ccw', 'texture_size': [tw, th], 'bones': names,
                'vertex_layout': ['x', 'y', 'z', 'u', 'v', 'nx', 'ny', 'nz'],
                'vertices': verts, 'weights': weights, 'triangles': tris}
        skin.update(cfg.get('skin_extra', {}))
        ref_skin = {'sample': list(range(0, len(verts), max(1, len(verts) // cfg.get('ref_samples', 300))))}
        slot_vi = [None] * len(verts)                  # exported vertex slot -> mesh vertex index
        for (vi, _, _), si in index.items():
            slot_vi[si] = vi

    # ---- bake every clip; the reference samples Blender's own deformation at the clip's ref frames
    anim_all, stats, ref_clips = {}, {}, {}
    animated = set()
    for clip in clips:
        act = bpy.data.actions[clip['action']]
        assign_action(arm, act)
        f0, f1 = clip['frames']
        frames = list(range(f0, f1 + 1))
        ref_frames = clip.get('ref_frames', [])
        D = {n: [] for n in names}               # render-frame deformation per frame
        posed, skin_posed, rigid_posed = {}, {}, {}
        for f in frames:
            sc.frame_set(f)
            for n in names:
                W = arm.matrix_world @ arm.pose.bones[n].matrix
                D[n].append(Cs @ (W @ rest[n].inverted()) @ CsI)
            if f not in ref_frames:
                continue
            posed[f] = {n: [list(r) for r in (Cs @ (arm.matrix_world @ arm.pose.bones[n].matrix) @ CsI)] for n in names}
            dg = bpy.context.evaluated_depsgraph_get()
            if ref_skin is not None:
                ev = cfg['skinned'].evaluated_get(dg)
                me2 = ev.to_mesh()
                skin_posed[str(f)] = [[r4(c / 16, 6) for c in P(ev.matrix_world @ me2.vertices[slot_vi[s]].co)] for s in ref_skin['sample']]
                ev.to_mesh_clear()
            for ob in cfg.get('rigid', []):
                ev = ob.evaluated_get(dg)
                me2 = ev.to_mesh()
                rigid_posed.setdefault(ob.name, {})[str(f)] = [[r4(c / 16, 6) for c in P(ev.matrix_world @ me2.vertices[vi].co)]
                                                              for _, vi in rigid_sample[ob.name]]
                ev.to_mesh_clear()
        times = [(f - f0) / fps for f in frames]
        anim_bones, cstats = {}, {}
        for b in bones:
            n = b.name
            par = parents[n]
            rots, poss = [], []
            for i in range(len(frames)):
                Lm = (D[par.name][i].inverted() if par else Matrix.Identity(4)) @ D[n][i]
                a, bb, c = zyx_degrees(Lm.to_3x3())
                rots.append((-c, -bb, a))
                p = Lm @ pivot[n] - pivot[n]
                poss.append((-p.x, p.y, p.z))
            rots = unwrap(rots)
            ch = {}
            if any(abs(v) > 1e-4 for r in rots for v in r):
                keep_i = reduce_keys(times, rots, clip.get('rot_tol', cfg.get('rot_tol', 0.05)))
                ch['rotation'] = {'%.4f' % times[i]: [r4(v, 3) for v in rots[i]] for i in keep_i}
            if any(abs(v) > 1e-5 for p in poss for v in p):
                keep_i = reduce_keys(times, poss, cfg.get('pos_tol', 0.002))
                ch['position'] = {'%.4f' % times[i]: [r4(v, 4) for v in poss[i]] for i in keep_i}
            if ch:
                anim_bones[n] = ch
                cstats[n] = {kk: len(v) for kk, v in ch.items()}
                animated.add(n)
        anim_all[clip['name']] = {'loop': clip.get('loop', 'hold_on_last_frame'), 'animation_length': r4(times[-1]),
                                  'bones': anim_bones}
        stats[clip['name']] = cstats
        ref_clips[clip['name']] = {'frames': ref_frames, 'bone_world_render_px': {str(kk): v for kk, v in posed.items()},
                                   'skin_posed': skin_posed, 'rigid_posed': rigid_posed}
    if not legacy:
        # every clip names every animated bone (a rest key when it does not move), so switching clips in the game
        # never leaves a bone where the previous clip put it
        for a in anim_all.values():
            for n in animated:
                ch = a['bones'].setdefault(n, {})
                ch.setdefault('rotation', {'0.0000': [0.0, 0.0, 0.0]})
                ch.setdefault('position', {'0.0000': [0.0, 0.0, 0.0]})
    anim = {'format_version': '1.8.0', 'animations': anim_all}

    if legacy:
        c0 = ref_clips[clips[0]['name']]
        if ref_skin is not None:
            ref_skin['posed'] = c0['skin_posed']
        ref_rigid = {ob.name: {'slots': [si for si, _ in rigid_sample[ob.name]], 'posed': c0['rigid_posed'].get(ob.name, {})}
                     for ob in cfg.get('rigid', [])}
        ref = {'fps': fps, 'frames': clips[0].get('ref_frames', []), 'bones': names,
               'bone_rest_render_px': {n: [list(r) for r in (C @ rest[n] @ CI)] for n in names},
               'bone_world_render_px': c0['bone_world_render_px'], 'skin': ref_skin, 'rigid': ref_rigid}
    else:
        ref = {'fps': fps, 'unit_px': k, 'bones': names,
               'bone_rest_render_px': {n: [list(r) for r in (Cs @ rest[n] @ CsI)] for n in names},
               'skin_sample': ref_skin['sample'] if ref_skin else [],
               'rigid_slots': {ob.name: [si for si, _ in rigid_sample[ob.name]] for ob in cfg.get('rigid', [])},
               'clips': ref_clips}

    def write(rel, data, compact=False):
        path = os.path.join(root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8', newline='\n') as fh:
            if compact:
                json.dump(data, fh, separators=(',', ':'))
            else:
                json.dump(data, fh, indent=1)
            fh.write('\n')
        return path

    out = [write('geo/%s.geo.json' % ident, geo), write('animations/%s.animation.json' % ident, anim)]
    if parts or legacy:
        out.append(write('meshes/%s.aflmesh.json' % ident, aflmesh, compact=True))
    if skin:
        out.append(write('skins/%s.skin.json' % ident, skin, compact=True))
    if cfg.get('ref'):
        os.makedirs(os.path.dirname(cfg['ref']), exist_ok=True)
        with open(cfg['ref'], 'w', encoding='utf-8', newline='\n') as fh:
            json.dump(ref, fh, separators=(',', ':'))
        out.append(cfg['ref'])
    sc.frame_set(f_start if not legacy else clips[0]['frames'][0])
    for c in hidden:
        c.hide_viewport = True
    summary = dict(bones=len(names), animated=len(animated), keys=stats if not legacy else stats[clips[0]['name']],
                   parts=len(parts), a_tris=sum(len(p['faces']) for p in parts), a_verts=sum(len(p['vertices']) for p in parts),
                   b_verts=len(skin['vertices']) if skin else 0, b_tris=len(skin['triangles']) if skin else 0, files=out)
    print('export', json.dumps(summary))
    return summary
