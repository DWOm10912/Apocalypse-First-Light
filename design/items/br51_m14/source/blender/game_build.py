# BR-51 game version, step 1 (as design/items/p9_01_v3/source/blender/game_build.py): build the bake source (BR51_hi:
# concept detail, bevelled, engraved) and the game low poly (BR51_lo: fewer segments, no bevel / text / knurls) from the
# concept scripts. Both with GAMEBUILD (lib.py: no cartridges, the drum window opaque) and INTERIOR (the chamber mouth).
# Run in Blender with BASE = this directory. The concept objects (collection BR51) are renamed cc_* and left alone.
import bpy, bmesh, math, os

G0 = {'BASE': BASE, 'INTERIOR': True, 'GAMEBUILD': True, 'CREASE': 35.0, '__name__': '__main__'}
SCRIPTS = ('receiver.py', 'barrel.py', 'stock.py', 'mags.py')

for o in bpy.data.collections['BR51'].objects:
    if not o.name.startswith('cc_'):
        o.name = 'cc_' + o.name


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
    # the magazine empties sit at the origin: bake their (identity) parenting into the meshes, drop them
    for o in list(bpy.data.collections[colname].objects):
        if o.type == 'EMPTY':
            for ch in list(o.children):
                mw = ch.matrix_world.copy()
                ch.parent = None
                ch.matrix_world = mw
            bpy.data.objects.remove(o)


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


# BUILD_HI False rebuilds only the low poly; LO_COL builds it into another collection (a trial next to the baked one)
LO_COL = globals().get('LO_COL', 'BR51_lo')
if globals().get('BUILD_HI', True):
    build('BR51_hi', 'hi_', 1.0, False)
build(LO_COL, 'lo_', globals().get('LO_Q', 0.38), True)   # 0.38: the rifle fits Vextris' 16384 triangles with its magazine drawn three times
total = 0
rows = []
for o in bpy.data.collections[LO_COL].objects:
    tidy_low(o, G0['CREASE'])
    t = sum(len(p.vertices) - 2 for p in o.data.polygons)
    total += t
    rows.append((o.name, t))
for o in bpy.data.collections['BR51_hi'].objects:
    o.hide_render = False
print('LOW triangles', total, 'objects', len(rows))
print(sorted(rows, key=lambda r: -r[1])[:30])
