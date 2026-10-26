"""AFL default male survivor V1 (stylised low poly) on the frozen H1-P1 skeleton. Run inside survivor_male_v1.blend.

The H1-P1 rig (54 deform bones + 4 IK helpers) is used as is: no bone is added, moved, renamed or re-parented.
The H1-P1 sources are imported read only (body_h1p1_lib for the body surface and the skin weights).

Stages (re-runnable): body, head, hair, outfit, weights, uv, textures, check.
"""
import importlib
import math
import sys

import bpy
import bmesh
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

SRC = r"D:/Minecraft Modding/Apocalypse First Light/design/characters/survivor_male_v1/source"
H1 = r"D:/Minecraft Modding/Apocalypse First Light/design/characters/survivor_base_h1p1/source"
for p in (SRC, H1):
    if p not in sys.path:
        sys.path.insert(0, p)
import body_h1p1_lib as L     # noqa: E402
import cage_male_v1 as C      # noqa: E402
importlib.reload(C)

M = 0.001
STATE = bpy.app.driver_namespace.setdefault('afl_male_v1', {})
COLL = 'Male_V1'


def rig():
    return bpy.data.objects['survivor_rig']


def sdf():
    if 'sdf' not in STATE:
        STATE['sdf'] = L.BodySDF(L.skeleton())
    return STATE['sdf']


def collection(name=COLL):
    c = bpy.data.collections.get(name) or bpy.data.collections.new(name)
    if c.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(c)
    return c


def replace_object(name, me):
    old = bpy.data.objects.get(name)
    if old:
        oldme = old.data
        bpy.data.objects.remove(old)
        if oldme.users == 0:
            bpy.data.meshes.remove(oldme)
    ob = bpy.data.objects.new(name, me)
    collection().objects.link(ob)
    return ob


def tri_count(ob):
    return sum(len(p.vertices) - 2 for p in ob.data.polygons)


def grad(f, P, h=0.5):
    g = np.zeros_like(P)
    for k in range(3):
        d = np.zeros(3, np.float32)
        d[k] = h
        g[:, k] = (f(P + d) - f(P - d)) / (2 * h)
    n = np.linalg.norm(g, axis=1, keepdims=True)
    return g / np.maximum(n, 1e-6)


def project(f, P, iso=0.0, steps=6):
    """Newton steps of the points onto f == iso (f: signed distance in mm)."""
    P = np.asarray(P, np.float32).copy()
    for _ in range(steps):
        d = f(P) - iso
        P -= grad(f, P) * d[:, None]
    return P


def relax(bm, f, iso=0.0, rounds=4, amount=0.5, fixed=None):
    """Tangential Laplacian smoothing + re-projection: evens the quads that the projection bunched up."""
    fixed = fixed or set()
    for _ in range(rounds):
        P = np.array([tuple(v.co / M) for v in bm.verts], np.float32)
        Q = P.copy()
        for v in bm.verts:
            if v.index in fixed or not v.link_edges:
                continue
            c = sum((e.other_vert(v).co for e in v.link_edges), Vector()) / len(v.link_edges) / M
            Q[v.index] = P[v.index] + (np.array(c, np.float32) - P[v.index]) * amount
        Q = project(f, Q, iso)
        for v in bm.verts:
            if v.index not in fixed:
                v.co = Vector(Q[v.index]) * M


NECK_CUT = 1530.0        # mm: the body ends here; the head module starts with the same ring
HAND_TRIS = 700          # per hand (the H1-P1 hand, 3400, collapse-decimated)
WRIST_T = (0.80, 0.86)   # forearm parameter: the low-poly arm ends at [0], the decimated hand starts at [1]
WRIST_RING = 12          # vertices on the hand's cut ring (the arm ring has 8)


def forearm_plane(side, t):
    b = rig().data.bones['forearm_' + side]
    a = Vector(b.head_local)
    d = (Vector(b.tail_local) - a).normalized()
    return a + d * (b.length * t), d


def boundary_loops(bm):
    """Every closed boundary loop of the mesh, each ordered."""
    edges = [e for e in bm.edges if e.is_boundary]
    seen, out = set(), []
    for e in edges:
        if e.verts[0] in seen:
            continue
        loop = boundary_loop(bm, None, start=e.verts[0])
        seen.update(loop)
        out.append(loop)
    return out


def nearest_loop(bm, point):
    return min(boundary_loops(bm), key=lambda lp: (sum((v.co for v in lp), Vector()) / len(lp) - point).length)


def boundary_loop(bm, verts, start=None):
    """The boundary edges among `verts` (None: all), ordered into one loop (from `start`)."""
    vs = set(verts) if verts is not None else None
    edges = [e for e in bm.edges if e.is_boundary and (vs is None or (e.verts[0] in vs and e.verts[1] in vs))]
    nxt = {}
    for e in edges:
        a, b = e.verts
        nxt.setdefault(a, []).append(b)
        nxt.setdefault(b, []).append(a)
    start = start or edges[0].verts[0]
    loop, prev = [start], None
    while True:
        cur = loop[-1]
        n = [v for v in nxt[cur] if v is not prev]
        if not n or n[0] is start:
            break
        prev = cur
        loop.append(n[0])
    return loop


def zipper(bm, A, B, centre, axis):
    """Triangle strip between two closed loops of any counts, both sorted by angle around `axis`."""
    ref = axis.orthogonal().normalized()
    ref2 = axis.cross(ref)

    def ang(v):
        q = v.co - centre
        return math.atan2(q.dot(ref2), q.dot(ref))
    A = sorted(A, key=ang)
    B = sorted(B, key=ang)
    i = j = 0
    na, nb = len(A), len(B)
    while i < na or j < nb:
        a0, a1 = A[i % na], A[(i + 1) % na]
        b0, b1 = B[j % nb], B[(j + 1) % nb]
        # advance the loop whose next vertex is angularly closer
        ta = ang(a1) + (2 * math.pi if (i + 1) >= na else 0)
        tb = ang(b1) + (2 * math.pi if (j + 1) >= nb else 0)
        if j >= nb or (i < na and ta <= tb):
            bm.faces.new((a0, a1, b0))
            i += 1
        else:
            bm.faces.new((a0, b1, b0))
            j += 1


def quad_ring(bm, A, B, centre):
    """Quads between two loops of equal count (zipper otherwise), matched by angle around Z."""
    if len(A) != len(B):
        return zipper(bm, A, B, centre, Vector((0, 0, 1)))
    ang = lambda v: math.atan2(v.co.x - centre.x, -(v.co.y - centre.y)) % (2 * math.pi)
    A = sorted(A, key=ang)
    B = sorted(B, key=ang)
    n = len(A)
    k = min(range(n), key=lambda s: sum(abs(math.remainder(ang(A[i]) - ang(B[(i + s) % n]), 2 * math.pi)) for i in range(n)))
    for i in range(n):
        bm.faces.new((A[i], A[(i + 1) % n], B[(i + 1 + k) % n], B[(i + k) % n]))


def hand_mesh():
    """Both hands from the H1-P1 body: everything distal of WRIST_T[1], collapse-decimated, open at the cut."""
    src = bpy.data.objects['h1p1_body_ref'].data
    bm = bmesh.new()
    bm.from_mesh(src)
    for side in 'rl':
        c, d = forearm_plane(side, WRIST_T[1] - 0.12)
        sel = [f for f in bm.faces if all((v.co - c).dot(d) > 0 and abs(v.co.x) > 0.3 for v in f.verts)]
        # decimate the hand plus a margin up the forearm, then cut the margin away (a clean low-count ring)
        tmp = bpy.data.meshes.new('tmp_hand')
        sub = bmesh.new()
        vmap = {}
        for f in sel:
            for v in f.verts:
                if v not in vmap:
                    vmap[v] = sub.verts.new(v.co)
        for f in sel:
            sub.faces.new([vmap[v] for v in f.verts])
        sub.to_mesh(tmp)
        sub.free()
        ob = bpy.data.objects.new('tmp_hand', tmp)
        bpy.context.scene.collection.objects.link(ob)
        dec = ob.modifiers.new('dec', 'DECIMATE')
        dec.ratio = HAND_TRIS * 1.25 / max(1, sum(len(f.verts) - 2 for f in sel))
        dg = bpy.context.evaluated_depsgraph_get()
        me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
        bpy.data.objects.remove(ob)
        bpy.data.meshes.remove(tmp)
        hb = bmesh.new()
        hb.from_mesh(me)
        bpy.data.meshes.remove(me)
        c1, _ = forearm_plane(side, WRIST_T[1])
        bmesh.ops.bisect_plane(hb, geom=hb.verts[:] + hb.edges[:] + hb.faces[:], plane_co=c1, plane_no=d, clear_inner=True)
        # thin the cut ring to WRIST_RING vertices (dissolving the others merges their faces), then re-triangulate
        ring = boundary_loop(hb, None)
        keep = {ring[int(round(i * len(ring) / WRIST_RING)) % len(ring)] for i in range(WRIST_RING)}
        bmesh.ops.dissolve_verts(hb, verts=[v for v in ring if v not in keep])
        bmesh.ops.triangulate(hb, faces=[f for f in hb.faces if len(f.verts) > 3], quad_method='BEAUTY', ngon_method='BEAUTY')
        STATE['hand_' + side] = hb
    bm.free()


def body():
    """Low-poly body: skin cage -> 1 Catmull-Clark level -> projected onto the H1-P1 surface -> neck opened."""
    r = rig()
    r.data.pose_position = 'REST'
    cage = C.build(r, 'male_v1_cage')
    sub = cage.modifiers.new('sub', 'SUBSURF')
    sub.levels = sub.render_levels = 1
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(cage.evaluated_get(dg))
    bpy.data.objects.remove(cage)
    bm = bmesh.new()
    bm.from_mesh(me)
    # open the neck: drop everything above the cut, keep the ring
    geom = [v for v in bm.verts if v.co.z / M > NECK_CUT + 1]
    bmesh.ops.delete(bm, geom=geom, context='VERTS')
    bm.verts.ensure_lookup_table()
    s = sdf()
    P = np.array([tuple(v.co / M) for v in bm.verts], np.float32)
    P = project(s.body, P)
    for v, p in zip(bm.verts, P):
        v.co = Vector(p) * M
    relax(bm, s.body, rounds=5)
    # cut the arms at WRIST_T[0], add the decimated hands, stitch the gap
    hand_mesh()
    for side in 'rl':
        c, d = forearm_plane(side, WRIST_T[0])
        arm = [f for f in bm.faces if all(abs(v.co.x) > 0.28 for v in f.verts)]
        geom = arm + list({e for f in arm for e in f.edges}) + list({v for f in arm for v in f.verts})
        bmesh.ops.bisect_plane(bm, geom=geom, plane_co=c, plane_no=d, clear_outer=True)
        arm_ring = nearest_loop(bm, c)
        hb = STATE.pop('hand_' + side)
        vmap = {v: bm.verts.new(v.co) for v in hb.verts}
        for f in hb.faces:
            bm.faces.new([vmap[v] for v in f.verts])
        hb.free()
        c1, _ = forearm_plane(side, WRIST_T[1])
        hand_ring = nearest_loop(bm, c1)
        zipper(bm, arm_ring, hand_ring, (c + c1) / 2, d)
        print('wrist', side, 'arm ring', len(arm_ring), 'hand ring', len(hand_ring))
    bm.to_mesh(me)
    bm.free()
    for p in me.polygons:
        p.use_smooth = False
    ob = replace_object('male_v1_body', me)
    print('body: verts', len(me.vertices), 'tris', tri_count(ob))
    return ob


def neck_ring():
    """The body's open neck ring (world m), ordered by angle, with the chord midpoints inserted."""
    me = bpy.data.objects['male_v1_body'].data
    bm = bmesh.new()
    bm.from_mesh(me)
    lp = nearest_loop(bm, Vector((0, 0.015, NECK_CUT * M)))
    pts = [v.co.copy() for v in lp]
    bm.free()
    c = sum(pts, Vector()) / len(pts)
    pts.sort(key=lambda p: math.atan2(p.x - c.x, -(p.y - c.y)) % (2 * math.pi))
    out = []
    for a, b in zip(pts, pts[1:] + pts[:1]):
        out += [a, (a + b) / 2]
    return out, c


def head():
    """The preset head 'm1' with its neck, from head_male_v1 (separate module, the face presets swap it)."""
    import head_male_v1 as H
    importlib.reload(H)
    rows = H.grid()
    ring0, c = neck_ring()
    bm = bmesh.new()
    vrows = [[bm.verts.new(p) for p in ring0]]
    for r in rows:
        vrows.append([bm.verts.new(Vector(p) * M) for p in r])
    # ring0 -> first ray ring: zipper (16 -> 20); then quads; the crown pole: a fan
    quad_ring(bm, vrows[0], vrows[1], c)
    for a, b in zip(vrows[1:-2], vrows[2:-1]):
        n = len(a)
        for i in range(n):
            bm.faces.new((a[i], a[(i + 1) % n], b[(i + 1) % n], b[i]))
    top, pole = vrows[-2], vrows[-1][0]
    for i in range(len(top)):
        bm.faces.new((top[i], top[(i + 1) % len(top)], pole))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    # ears: root ring (inside the head) -> rim -> inner ring -> centre
    for sg in (1, -1):
        mir = Vector((sg, 1, 1))
        pts = H.ears()
        rim = [bm.verts.new(Vector(p[0] * np.array([sg, 1, 1])) * M) for p in pts]
        inn = [bm.verts.new(Vector(p[1] * np.array([sg, 1, 1])) * M) for p in pts]
        root = [bm.verts.new(Vector(p[2] * np.array([sg, 1, 1])) * M) for p in pts]
        cen = bm.verts.new(sum((v.co for v in inn), Vector()) / len(inn) + Vector((-0.003 * sg, 0, 0)))
        n = len(pts)
        ear_faces = []
        for i in range(n):
            j = (i + 1) % n
            ear_faces.append(bm.faces.new((root[i], root[j], rim[j], rim[i])))
            ear_faces.append(bm.faces.new((rim[i], rim[j], inn[j], inn[i])))
            ear_faces.append(bm.faces.new((inn[i], inn[j], cen)))
        # outward: the rim faces point away from the head centre
        for f in ear_faces:
            f.normal_update()
        ec = Vector(H.EAR_C) * M
        ec.x *= sg
        sgn = sum((f.normal.dot(f.calc_center_median() - Vector((0, ec.y, ec.z)))) for f in ear_faces)
        if sgn < 0:
            for f in ear_faces:
                f.normal_flip()
    me = bpy.data.meshes.new('male_v1_head')
    bm.to_mesh(me)
    bm.free()
    ob = replace_object('male_v1_head', me)
    print('head: verts', len(me.vertices), 'tris', tri_count(ob))
    return ob


def run(*stages):
    for st in stages:
        globals()[st]()
