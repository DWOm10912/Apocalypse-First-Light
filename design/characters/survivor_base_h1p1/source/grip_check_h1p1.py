"""P9-01 two-hand grip, H1-P1: apply the final hand-posed grip (grip_pose_h1p1.json) to two H1-P0 B hands on the
P9_ref collection and check / render it. The pose itself is data, set by hand from reference (thumbs-forward grip,
docs/dev/mesh_survivor_base_body_h1p1.md); this script only places it and measures it, it never searches.

Run inside survivor_base_h1p1.blend (needs the P9_ref collection):
    exec(open(r".../source/grip_check_h1p1.py", encoding="utf-8").read())
    rep = check(*load())          # anatomy, bone overlaps, skin vs gun, hand vs hand (mm)
    shots("final_six")            # six views into renders/grip_h1p1/

Gun frame: P9-01 V3 geo px (1 px = 19 mm), +x the gun's right, +y the muzzle, +z up. Left hand = a right hand posed
on the gun mirrored in x (rig matrix S4 @ root @ Mh^-1)."""
import bpy, builtins, json, math, os, sys
import numpy as np
from mathutils import Matrix, Vector, Euler
from mathutils.bvhtree import BVHTree

HERE = r"D:/Minecraft Modding/Apocalypse First Light/design/characters/survivor_base_h1p1/source"
SRC0 = r"D:/Minecraft Modding/Apocalypse First Light/design/items/mesh_hand_p0/source"
P0 = SRC0 + "/mesh_hand_p0.blend"
RV = HERE + "/render_views.py"
POSE = HERE + "/grip_pose_h1p1.json"
OUT = os.path.normpath(HERE + "/../renders/grip_h1p1")
for p in (SRC0, HERE):
    if p not in sys.path:
        sys.path.insert(0, p)
import hand_p0_lib as HL
import grip_h1p1 as G

ORIGIN = Vector((5.0, 0.0, 0.0))
SCALE = 0.019
S4 = Matrix.Diagonal((-1, 1, 1, 1))
BONES = {b.name: b for b in HL.skeleton()}
CHAINS = ('thumb', 'index', 'middle', 'ring', 'pinky')


def state():
    """H1-P0 hand rest (px) and the gun distance field, cached on builtins."""
    st = getattr(builtins, '_h1p1', None)
    if st and 'Mh' in st and 'g' in st:
        return st
    with bpy.data.libraries.load(P0, link=False) as (src, dst):
        dst.objects = ['hand_rig']
    hr = dst.objects[0]
    Mh = hr.data.bones['hand'].matrix_local.copy()
    ad = hr.data
    bpy.data.objects.remove(hr)
    bpy.data.armatures.remove(ad)
    g = G.WideGunField(list(bpy.data.collections['P9_ref'].objects))
    builtins._h1p1 = st = dict(Mh=Mh, g=g)
    return st


def coll(name):
    c = bpy.data.collections.get(name) or bpy.data.collections.new(name)
    if c.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(c)
    return c


def setup():
    """Grip_preview collection: gp_root (gun px), the P9_ref instance and two H1-P0 B hands with their rigs."""
    c = coll('Grip_preview')
    if 'gp_root' in bpy.data.objects:
        return
    root = bpy.data.objects.new('gp_root', None)
    c.objects.link(root)
    root.location = ORIGIN
    root.scale = (SCALE,) * 3
    gun = bpy.data.objects.new('gp_gun', None)
    gun.instance_type = 'COLLECTION'
    gun.instance_collection = bpy.data.collections['P9_ref']
    c.objects.link(gun)
    gun.parent = root
    for side in 'rl':
        with bpy.data.libraries.load(P0, link=False) as (src, dst):
            dst.objects = ['hand_rig', 'hand_B']
        rig, mesh = dst.objects
        rig.name, mesh.name = 'gp_rig_' + side, 'gp_hand_' + side
        c.objects.link(rig)
        c.objects.link(mesh)
        rig.parent = root
        mesh.parent = rig
        mesh.matrix_parent_inverse = Matrix.Identity(4)
        for m in mesh.modifiers:
            if m.type == 'ARMATURE':
                m.object = rig


def load(path=POSE):
    """(right, left) specs: dict(root=4x4 hand H mm -> (mirrored) gun px, e={bone: (x, y, z) deg})."""
    d = json.load(open(path, encoding='utf-8'))
    out = []
    for k in ('firing_right', 'support_left_mirrored'):
        out.append(dict(root=Matrix(d[k]['root']), e={n: tuple(v) for n, v in d[k]['e'].items()}))
    return tuple(out)


def apply(right=None, left=None):
    """Pose the preview rigs; None hides that hand."""
    st = state()
    setup()
    for side, spec in (('r', right), ('l', left)):
        rig = bpy.data.objects['gp_rig_' + side]
        mesh = bpy.data.objects['gp_hand_' + side]
        mesh.hide_render = mesh.hide_viewport = spec is None
        if spec is None:
            continue
        M = spec['root'] @ Matrix.Scale(1 / HL.MM, 4)
        rig.matrix_parent_inverse = Matrix.Identity(4)
        rig.matrix_basis = (M if side == 'r' else S4 @ M) @ st['Mh'].inverted()
        for pb in rig.pose.bones:
            pb.rotation_mode = 'XYZ'
            pb.location = (0, 0, 0)
            pb.rotation_euler = Euler([math.radians(a) for a in spec['e'].get(pb.name, (0, 0, 0))], 'XYZ')
    bpy.context.view_layer.update()


def mesh_px(side):
    """Evaluated skinned vertices, triangles, dominant bone per vertex (gun px) and whether the frame is mirrored."""
    ob = bpy.data.objects['gp_hand_' + side]
    ev = ob.evaluated_get(bpy.context.evaluated_depsgraph_get())
    me = ev.to_mesh()
    M = bpy.data.objects['gp_root'].matrix_world.inverted() @ ob.matrix_world
    v = np.array([tuple(M @ x.co) for x in me.vertices])
    me.calc_loop_triangles()
    tris = [tuple(t.vertices) for t in me.loop_triangles]
    names = [g.name for g in ob.vertex_groups]
    dom = []
    for x in ob.data.vertices:
        gs = sorted(((g.weight, names[g.group]) for g in x.groups), reverse=True)
        dom.append(gs[0][1] if gs else 'hand')
    ev.to_mesh_clear()
    return v, tris, dom, M.determinant() < 0


def penetration(v, tris_other, v_other, flipped):
    """Depth (px) of each vertex inside the other closed mesh (0 outside). The sign comes from the nearest face
    normal, so a vertex sitting in a crease of the other hand can read as a false positive: confirm a hit with the
    unsigned gap before acting on it."""
    t = BVHTree.FromPolygons([tuple(p) for p in v_other], tris_other)
    out = np.zeros(len(v))
    for i, p in enumerate(v):
        loc, nrm, _, dist = t.find_nearest(Vector(p), 0.6)
        if loc is None:
            continue
        s = (Vector(p) - loc).dot(nrm)
        if flipped:
            s = -s
        if s < 0:
            out[i] = dist
    return out


def bone_segments(side):
    """Bone segments (a, b, mean capsule radius) in gun px."""
    rig = bpy.data.objects['gp_rig_' + side]
    W = bpy.data.objects['gp_root'].matrix_world.inverted() @ rig.matrix_world
    seg = {}
    for pb in rig.pose.bones:
        b = BONES[pb.name]
        if b.radii is None:
            continue
        seg[pb.name] = (np.array(W @ pb.head), np.array(W @ pb.tail), 0.5 * (b.radii[0] + b.radii[1]) * HL.MM)
    return seg


def seg_dist(a0, a1, b0, b1):
    ts = np.linspace(0, 1, 21)
    A = a0 + (a1 - a0) * ts[:, None]
    B = b0 + (b1 - b0) * ts[:, None]
    return float(np.sqrt(((A[:, None, :] - B[None, :, :]) ** 2).sum(-1)).min())


def anatomy(e):
    """Joint angles against normal ranges (deg): finger MCP flex -10..90, twist <= 5, abduction <= 25; PIP 0..105;
    DIP 0..80 and DIP <= PIP + 5; PIP / DIP and thumb MCP / IP single-axis; thumb CMC twist <= 35, MCP -10..60,
    IP -15..80. Returns a list of problems."""
    bad = []
    for c in CHAINS[1:]:
        m, p, d = (e.get('%s_%02d' % (c, j), (0, 0, 0)) for j in (1, 2, 3))
        if not -10 <= m[0] <= 90: bad.append('%s MCP flex %.0f' % (c, m[0]))
        if abs(m[1]) > 5: bad.append('%s MCP twist %.0f' % (c, m[1]))
        if abs(m[2]) > 25: bad.append('%s MCP abduction %.0f' % (c, m[2]))
        if not 0 <= p[0] <= 105: bad.append('%s PIP %.0f' % (c, p[0]))
        if not 0 <= d[0] <= 80: bad.append('%s DIP %.0f' % (c, d[0]))
        if d[0] > p[0] + 5: bad.append('%s DIP > PIP' % c)
        if abs(p[1]) + abs(p[2]) + abs(d[1]) + abs(d[2]) > 0.1: bad.append('%s PIP/DIP off-axis' % c)
    t1, t2, t3 = (e.get('thumb_%02d' % j, (0, 0, 0)) for j in (1, 2, 3))
    if abs(t1[1]) > 35: bad.append('thumb CMC twist %.0f' % t1[1])
    if not -10 <= t2[0] <= 60: bad.append('thumb MCP %.0f' % t2[0])
    if not -15 <= t3[0] <= 80: bad.append('thumb IP %.0f' % t3[0])
    if abs(t2[1]) + abs(t2[2]) + abs(t3[1]) + abs(t3[2]) > 0.1: bad.append('thumb MCP/IP off-axis')
    return bad


def check(right, left):
    """Report (mm): anatomy, bone overlaps between chains, skin into the gun per bone, nearest skin-gun gap per chain,
    hand into hand per bone."""
    apply(right, left)
    gun = state()['g']
    rep, meshes = {}, {}
    for side, spec in (('r', right), ('l', left)):
        if spec is None:
            continue
        rep[side + '_anatomy'] = anatomy(spec['e'])
        seg = bone_segments(side)
        worst = []
        names = list(seg)
        for i, a in enumerate(names):
            for b in names[i + 1:]:
                if a[:-3] == b[:-3]:
                    continue
                d = seg_dist(seg[a][0], seg[a][1], seg[b][0], seg[b][1]) - seg[a][2] - seg[b][2]
                if d < -0.05:
                    worst.append((round(d / HL.MM, 1), a, b))
        rep[side + '_bone_overlap_mm'] = sorted(worst)[:6]
        v, tris, dom, flip = mesh_px(side)
        meshes[side] = (v, tris, dom, flip)
        d = gun.sd_many(v)      # mesh_px is already in the real gun frame (left rig carries S4)
        pen = np.maximum(-d, 0)
        by = {}
        for k in np.where(pen > 0.05)[0]:
            by[dom[k]] = max(by.get(dom[k], 0), pen[k])
        rep[side + '_into_gun_mm'] = {n: round(float(x) / HL.MM, 1) for n, x in sorted(by.items(), key=lambda t: -t[1])}
        rep[side + '_gun_gap_min_mm'] = {c: round(float(d[[k for k in range(len(v)) if dom[k].startswith(c)]].min()) / HL.MM, 1)
                                         for c in ('hand',) + CHAINS}
    if 'r' in meshes and 'l' in meshes:
        for a, b in (('r', 'l'), ('l', 'r')):
            v, _, dom, _ = meshes[a]
            vo, to, _, fo = meshes[b]
            pen = penetration(v, to, vo, fo)
            by = {}
            for k in np.where(pen > 0.03)[0]:
                by[dom[k]] = max(by.get(dom[k], 0), pen[k])
            rep[a + '_into_' + b + '_mm'] = {n: round(float(x) / HL.MM, 1) for n, x in sorted(by.items(), key=lambda t: -t[1])}
    return rep


# a view is named by where the camera is (gun frame)
VIEWS = [('gun_left', (-1, 0, 0.05), (0, 0, 1)), ('gun_right', (1, 0, 0.05), (0, 0, 1)),
         ('muzzle', (0.05, 1, 0.08), (0, 0, 1)), ('shooter', (0.05, -1, 0.15), (0, 0, 1)),
         ('top', (0.02, 0.05, 1), (0, 1, 0)), ('below', (0.05, 0.1, -1), (0, 1, 0))]


def shots(prefix, views=None, size=600, span=0.26, focus=None):
    rv = {}
    exec(open(RV, encoding='utf-8').read(), rv)
    os.makedirs(OUT, exist_ok=True)
    objs = [o for o in ('gp_gun', 'gp_hand_r', 'gp_hand_l', 'gp_root') if not bpy.data.objects[o].hide_render]
    f = ORIGIN + Vector(focus or (0, -1.5, 3.2)) * SCALE
    ps = rv['render'](objs, views or VIEWS, OUT, prefix, size=size, focus=f, span=span)
    return rv['sheet'](ps, OUT + '/' + prefix + '_sheet.png', cols=3)
