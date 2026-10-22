# BR-51 game version, step 1b: delete low poly faces that are buried in another part that never moves against them
# (game_bones.CULL_SETS: the static rifle, each magazine on its own). A face goes when its centre and every corner, each
# pushed CULL_PUSH mm out along the face normal, lie inside another closed part of the same set (ray parity, three
# directions, majority). Saves atlas space (the drum halves' inner discs, the receiver's floor in the stock inletting).
import bpy, bmesh, os
from mathutils import Vector
from mathutils.bvhtree import BVHTree

exec(open(os.path.join(BASE, 'game_bones.py')).read())
PUSH = globals().get('CULL_PUSH', 0.03)
DIRS = (Vector((0.31, 0.77, 0.56)).normalized(), Vector((-0.62, 0.21, 0.75)).normalized(), Vector((0.55, -0.66, -0.51)).normalized())

lo = [o for o in bpy.data.collections['BR51_lo'].objects if o.type == 'MESH']
trees, boxes = {}, {}
for o in lo:
    bm = bmesh.new()
    bm.from_mesh(o.data)
    bm.transform(o.matrix_world)
    trees[o.name] = BVHTree.FromBMesh(bm)
    cs = [v.co for v in bm.verts]
    boxes[o.name] = (Vector([min(c[i] for c in cs) for i in range(3)]), Vector([max(c[i] for c in cs) for i in range(3)]))
    bm.free()


def inside(name, p):
    lo_, hi_ = boxes[name]
    if any(p[i] < lo_[i] or p[i] > hi_[i] for i in range(3)):
        return False
    tree = trees[name]
    votes = 0
    for d in DIRS:
        n, q = 0, p.copy()
        for _ in range(64):
            hit = tree.ray_cast(q, d)
            if hit[0] is None:
                break
            n += 1
            q = hit[0] + d * 1e-4
        votes += n % 2
    return votes >= 2


total = 0
for cset in CULL_SETS:
    objs = [o for o in lo if bone_of(o.name) == cset]
    for o in objs:
        others = [x.name for x in objs if x is not o]
        if not others:
            continue
        mw = o.matrix_world
        bm = bmesh.new()
        bm.from_mesh(o.data)
        dead = []
        for f in bm.faces:
            n = (mw.to_3x3() @ f.normal).normalized()
            pts = [mw @ f.calc_center_median()] + [mw @ v.co for v in f.verts]
            if all(any(inside(t, p + n * PUSH) for t in others) for p in pts):
                dead.append(f)
        if dead:
            bmesh.ops.delete(bm, geom=dead, context='FACES')
            loose = [v for v in bm.verts if not v.link_faces]
            bmesh.ops.delete(bm, geom=loose, context='VERTS')
            bm.to_mesh(o.data)
            total += len(dead)
            print('culled', o.name, len(dead))
        bm.free()
print('culled faces', total)
