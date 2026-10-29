# Blackridge .50 game version, step 1 (as design/items/br51_m14/source/blender/game_build.py): build the bake source
# (BR50_hi: the approved round-3 concept, bevelled, engraved) and the game low poly (BR50_lo: fewer segments, no bevel or
# text) from gun_r3.py, both with INTERIOR (bolt recess, chamber, gas cylinder, piston, guide rods, magazine and well).
# Run with BASE = this directory, after materials.py (game_run.py does both).
import bpy, bmesh, math, os

G0 = {'BASE': BASE, 'INTERIOR': True, 'CREASE': 35.0, '__name__': '__main__'}
SCRIPTS = ('gun_r3.py',)


def wipe(colname):
    c = bpy.data.collections.get(colname)
    if not c:
        return
    for o in list(c.objects):
        me = o.data if o.type == 'MESH' else None
        bpy.data.objects.remove(o)
        if me and me.users == 0:
            bpy.data.meshes.remove(me)


def build(colname, prefix, q, game):
    wipe(colname)
    for s in SCRIPTS:
        g = dict(G0, COLNAME=colname, Q=q, GAME=game)
        exec(open(os.path.join(BASE, s)).read(), g)
    for o in list(bpy.data.collections[colname].objects):
        o.name = prefix + o.name.split('.')[0]


def tidy_low(ob, crease):
    """Planar faces only (Vextris: planar convex quads or triangles), no slivers, sharp edges = crease rule."""
    for m in list(ob.modifiers):
        ob.modifiers.remove(m)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bmesh.ops.dissolve_degenerate(bm, edges=bm.edges, dist=1e-4)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-5)
    bad = []
    for f in bm.faces:
        if len(f.verts) > 4:
            bad.append(f)
        elif len(f.verts) == 4:
            n = f.normal
            p0 = f.verts[0].co
            ext = max((v.co - p0).length for v in f.verts)
            dev = max(abs((v.co - p0).dot(n)) for v in f.verts)
            if dev > max(1e-5, ext * 1e-4):
                bad.append(f)
    if bad:
        bmesh.ops.triangulate(bm, faces=bad, quad_method='BEAUTY', ngon_method='BEAUTY')
    bmesh.ops.dissolve_degenerate(bm, edges=bm.edges, dist=1e-4)
    lim = math.radians(crease)
    for e in bm.edges:
        e.smooth = len(e.link_faces) == 2 and e.calc_face_angle(0) < lim
    bm.to_mesh(ob.data)
    bm.free()
    for p in ob.data.polygons:
        p.use_smooth = True
    w = ob.modifiers.new('wnormal', 'WEIGHTED_NORMAL')
    w.mode = 'FACE_AREA'
    w.keep_sharp = True
    w.weight = 50


if globals().get('BUILD_HI', True):
    build('BR50_hi', 'hi_', 1.0, False)
build('BR50_lo', 'lo_', globals().get('LO_Q', 0.6), True)
total = 0
rows = []
for o in bpy.data.collections['BR50_lo'].objects:
    tidy_low(o, G0['CREASE'])
    t = sum(len(p.vertices) - 2 for p in o.data.polygons)
    total += t
    rows.append((o.name, t))
for o in bpy.data.collections['BR50_hi'].objects:
    o.hide_render = False
print('LOW triangles', total, 'objects', len(rows))
print(sorted(rows, key=lambda r: -r[1]))
