# BR-51 concept: magazines 20 (straight box), 30 (curved box), 50 (drum), each with the top rounds showing. They work as
# the M14's: the hook at the top front catches the op rod spring guide, the magazine rocks back and up until the latch
# behind it snaps under the rear ledge; double stack, staggered rounds under two feed lips. Shapes (pressed side ribs,
# flared floorplate, drum faces) are our own. Built in the gun (inserted) position, parented to empties MAG20/MAG30/MAG50.
import os
exec(open(os.path.join(BASE, 'geo.py')).read())
for p in ('mag', 'MAG', 'rnd'):
    clear(p)

MAGM = mat('br51_mag')
BRASS = mat('br51_brass')
COPPER = mat('br51_copper')
FOLL = mat('br51_follower')

YF, YB = 516.0, 438.0            # front / back face (outside)
YM = (YF + YB) / 2
HX = 12.4                        # half width
ZT = -8.5                        # top of the feed lips (top round just under the closed bolt)


def empty(name):
    e = bpy.data.objects.get(name)
    if not e:
        e = bpy.data.objects.new(name, None)
        col().objects.link(e)
    e.location = (0, 0, 0)
    e.empty_display_size = 30
    return e


def parent(obs, e):
    for o in obs:
        o.parent = e


def cartridge(name, x, z, y0=441.5):
    """7.62x51: base at y0, bullet forward. Case and bullet are separate objects (brass / copper)."""
    if GAMEBUILD:
        return []
    case = lathe(name + '_case', [(0.0, 0), (5.95, 0), (5.95, 1.3), (5.0, 1.7), (5.0, 3.0), (5.95, 3.7), (5.75, 39.6),
                                  (4.45, 43.6), (4.45, 51.2), (0.0, 51.2)], 'y', 40, BRASS, center=(x, z))
    case.data.transform(Matrix.Translation((0, y0, 0)))
    pr = [(3.92, 51.0), (3.92, 57.5)]
    for k in range(1, 11):
        t = k / 10
        pr.append((3.92 * math.sqrt(max(0.0, 1 - t ** 1.6)) + 0.55 * t, 57.5 + 13.5 * t))
    pr.append((0.0, 71.0))
    bul = lathe(name + '_bullet', [(0.0, 51.0)] + pr, 'y', 40, COPPER, center=(x, z))
    bul.data.transform(Matrix.Translation((0, y0, 0)))
    finish(case, 0.15, 2, 35)
    return [case, bul]


def rib(name, yc, z0, z1, side, half=7.0, rise=1.3):
    """Pressed rib on a side wall: a round-ended bar flattened to `rise` mm, standing on the wall at x = side * HX."""
    m = 4 if GAME else 8   # quarter-round steps (game: the 20-round magazine is drawn three times, Vextris 16384 triangles)
    prof = [(0.0, z0)]
    for k in range(1, m + 1):
        a = math.pi / 2 * k / m
        prof.append((half * math.sin(a), z0 + half - half * math.cos(a)))
    for k in range(0, m + 1):
        a = math.pi / 2 * k / m
        prof.append((half * math.cos(a), z1 - half + half * math.sin(a)))
    prof.append((0.0, z1))
    o = lathe(name, prof, 'z', 16 if GAME else 48, MAGM, center=(0, yc))
    o.data.transform(Matrix.Scale(rise / half, 4, (1, 0, 0)))
    o.data.transform(Matrix.Translation((side * (HX - 0.3), 0, 0)))
    return o


def box_mag(tag, depth, bend_r=None):
    """Straight box from ZT down to ZT - depth, optionally bent forward on radius bend_r (curved magazine)."""
    zb = ZT - depth
    parts = []
    body = box(tag + '_body', -HX, HX, YB, YF, zb + 3.0, ZT - 1.5, MAGM, 2.2)
    # pressed ribs: two a side, running down the body; a spine rib on the back
    ribs = []
    for s in (-1, 1):
        for yc in (YM - 17, YM + 17):
            r = rib(tag + '_rib', yc, zb + 16, ZT - 24, s)
            ribs.append(r)
    ribs.append(box(tag + '_spine', -5.0, 5.0, YB - 1.0, YB + 2.0, zb + 12, ZT - 24, MAGM, 0.95))
    # front catch hook (catches the op rod spring guide) and rear latch ledge
    ribs.append(solid(tag + '_hook', fillet([(YF - 2, ZT - 14), (YF + 3.2, ZT - 14), (YF + 3.2, ZT - 9.5), (YF + 1.2, ZT - 8),
                                              (YF - 2, ZT - 8)], [0, 0.8, 0.8, 0.8, 0], 3), 'yz', -6.5, 6.5, MAGM))
    ribs.append(solid(tag + '_ledge', fillet([(YB + 2, ZT - 39), (YB - 2.6, ZT - 39), (YB - 2.6, ZT - 34), (YB + 2, ZT - 32)],
                                             [0, 0.6, 0.6, 0], 3), 'yz', -7.5, 7.5, MAGM))
    boolean(body, ribs, 'UNION')
    # open top: pocket for the rounds, feed lips curled over the rear two thirds
    boolean(body, box('c_mag_pocket', -HX + 1.0, HX - 1.0, YB + 1.0, YF - 1.0, ZT - 14, ZT + 5, r=1.0))
    lips = []
    for s in (-1, 1):
        lp = box(tag + '_lip', -1.2, 1.2, YB + 1, YB + 52, ZT - 2.5, ZT + 0.5, MAGM, 1.0)
        lp.data.transform(Matrix.Translation((s * (HX - 3.4), 0, 0)))
        lips.append(lp)
        wall = box(tag + '_lipwall', -1.0, 1.0, YB + 1, YF - 1, ZT - 14.5, ZT - 1.5, MAGM)
        wall.data.transform(Matrix.Translation((s * (HX - 1.0), 0, 0)))
        lips.append(wall)
    boolean(body, lips, 'UNION')
    finish(body, 0.3, 2, 35)
    parts.append(body)
    fp = box(tag + '_floor', -HX - 1.4, HX + 1.4, YB - 1.6, YF + 1.6, zb, zb + 3.2, MAGM, 3.0)
    boolean(fp, box('c_fp_tab', -4.0, 4.0, YB - 3, YB + 4, zb - 1, zb + 1.2, r=1.0))   # release tab notch
    finish(fp, 0.4, 3, 35)
    parts.append(fp)
    rounds = cartridge(tag + '_rnd1', -4.6, ZT - 7.6) + cartridge(tag + '_rnd2', 4.6, ZT - 13.6)
    if bend_r:
        for o in [body, fp]:
            # cut the long flat walls into 5 mm bands first, so they bend instead of shearing
            bm = bmesh.new()
            bm.from_mesh(o.data)
            zc = ZT - 15.0
            while zc > zb:
                geom = bm.verts[:] + bm.edges[:] + bm.faces[:]
                bmesh.ops.bisect_plane(bm, geom=geom, plane_co=(0, 0, zc), plane_no=(0, 0, 1))
                zc -= 10.0 if GAME else 5.0
            bm.to_mesh(o.data)
            bm.free()
            for v in o.data.vertices:
                s = ZT - 10 - v.co.z
                if s <= 0:
                    continue
                th = s / bend_r
                dy = YM + bend_r - v.co.y
                v.co.y = YM + bend_r - dy * math.cos(th)
                v.co.z = ZT - 10 - dy * math.sin(th)
            o.data.update()
    return parts + rounds


e20 = empty('MAG20')
parent(box_mag('mag20', 145.0), e20)
e30 = empty('MAG30')
parent(box_mag('mag30', 205.0, bend_r=520.0), e30)

# 50-round drum. Inside the drum the rounds lie across, parallel to the drum axis (x), on a spiral; the feed neck
# twists each round a quarter turn into line with the bore before it reaches the straight tower (the way the Soviet
# 75-round drums feed). So the drum is a round's length plus walls wide (84 mm), and sits below a widening neck.
# Faces (our own): pressed centre cap with four rivets, two pressed rings with eight radial ribs between them; left
# face: a round-count window (clear cover, the case heads of the outer row show through); right face: winding key and
# the spring release button behind it.
e50 = empty('MAG50')
DC = (YM + 14.0, -134.0)           # drum centre (y, z)
DR = 60.0
W = 42.0                           # half width
WIN = mat('br51_poly')
tower = box('mag50_tower', -HX, HX, YB, YF, -56, ZT - 1.5, MAGM, 2.2)
boolean(tower, [solid('mag50_hook', fillet([(YF - 2, ZT - 14), (YF + 3.2, ZT - 14), (YF + 3.2, ZT - 9.5), (YF + 1.2, ZT - 8),
                                           (YF - 2, ZT - 8)], [0, 0.8, 0.8, 0.8, 0], 3), 'yz', -6.5, 6.5, MAGM),
                solid('mag50_ledge', fillet([(YB + 2, ZT - 39), (YB - 2.6, ZT - 39), (YB - 2.6, ZT - 34), (YB + 2, ZT - 32)],
                                            [0, 0.6, 0.6, 0], 3), 'yz', -7.5, 7.5, MAGM)], 'UNION')
# twist neck: from the tower section out to the drum width
nsec = []
for k in range(13):
    t = k / 12
    f = t * t * (3 - 2 * t)
    hx = HX + (W - 1.0 - HX) * f
    nsec.append((rrect(-hx, hx, YB + 6.0 * f, YF - 8.0 * f, 2.2 + 5.0 * f, 6), -54.0 - 34.0 * t))
neck = loft('mag50_neck', nsec[::-1], 'xy', MAGM)
boolean(tower, neck, 'UNION')
boolean(tower, box('c_mag_pocket', -HX + 1.0, HX - 1.0, YB + 1.0, YF - 1.0, ZT - 14, ZT + 5, r=1.0))
lips = []
for s in (-1, 1):
    lp = box('mag50_lip', -1.2, 1.2, YB + 1, YB + 52, ZT - 2.5, ZT + 0.5, MAGM, 1.0)
    lp.data.transform(Matrix.Translation((s * (HX - 3.4), 0, 0)))
    lips.append(lp)
    wall = box('mag50_lipwall', -1.0, 1.0, YB + 1, YF - 1, ZT - 14.5, ZT - 1.5, MAGM)
    wall.data.transform(Matrix.Translation((s * (HX - 1.0), 0, 0)))
    lips.append(wall)
boolean(tower, lips, 'UNION')
finish(tower, 0.3, 2, 35)
dparts = [tower]


def side(s, x0, x1):
    """x range (x0, x1) measured outward from the face at x = W, mirrored to the left face for s < 0."""
    return (W + x0, W + x1) if s > 0 else (-W - x1, -W - x0)


def arc_band(r0, r1, a0, a1, n=24):
    """Window outline in (y, z): a ring sector around the drum centre, round ends."""
    pts = []
    for k in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * k / n)
        pts.append((DC[0] + r1 * math.cos(a), DC[1] + r1 * math.sin(a)))
    for k in range(n + 1):
        a = math.radians(a1 - (a1 - a0) * k / n)
        pts.append((DC[0] + r0 * math.cos(a), DC[1] + r0 * math.sin(a)))
    rad = [0.0] * len(pts)
    for i in (0, n, n + 1, 2 * n + 1):
        rad[i] = (r1 - r0) * 0.45
    return fillet(pts, rad, 4)


WA0, WA1 = 196.0, 300.0            # window arc (0 deg = forward, 90 = up), low at the back of the drum
for s in (-1, 1):
    prof = [(0.0, 0.0), (DR - 4, 0.0), (DR, 2.5), (DR, W - 4.0), (DR - 4.0, W), (0.0, W)]
    sh = lathe('mag50_shell', prof, 'x', 128, MAGM, center=DC)
    if s < 0:
        sh.data.transform(Matrix.Scale(-1, 4, (1, 0, 0)))
        sh.data.flip_normals()
    add = []
    for rr in (38.0, 22.0):
        # a real ring: disc out to the outer flank, minus a cone up the inner flank (a capped lathe of the trapezoid
        # alone comes out as a solid disc)
        g = lathe('c_ring', [(0.0, 0.0), (rr + 3.0, 0.0), (rr + 1.6, 1.8), (0.0, 1.8)], 'x', 128, center=DC)
        boolean(g, lathe('c_ring_in', [(0.0, -1.0), (rr - 3.0, -1.0), (rr - 3.0, 0.0), (rr - 1.6, 1.8), (rr - 1.6, 3.0),
                                       (0.0, 3.0)], 'x', 128, center=DC))
        if s < 0:
            g.data.transform(Matrix.Scale(-1, 4, (1, 0, 0)))
            g.data.flip_normals()
        g.data.transform(Matrix.Translation((s * W, 0, 0)))
        add.append(g)
    add.append(cyl_x('c_cap', 14.0, *side(s, -0.5, 1.6), DC[0], DC[1], 64))
    for o in add:                  # one at a time: overlapping operands in one exact pass drop out
        boolean(sh, o, 'UNION')
    if s < 0:
        # round-count window: outer opening with a lip, deeper slot where the outer row of rounds runs
        boolean(sh, solid('c_win', arc_band(47.0, 57.0, WA0, WA1), 'yz', -W - 2, -W + 3))
        if not GAMEBUILD:
            boolean(sh, solid('c_win_slot', arc_band(45.6, 58.4, WA0 - 2, WA1 + 2), 'yz', -W + 2.99, -1.0))
    finish(sh, 0.6, 3, 35)
    dparts.append(sh)
    # eight radial ribs between the rings: own pieces seated in the face (unioned, the ones on ring vertices break)
    for k in range(8):
        rb = solid('mag50_rib', rrect(DC[0] - 2.5, DC[0] + 2.5, DC[1] + 23.5, DC[1] + 36.5, 1.5, 4), 'yz', *side(s, -0.5, 1.2), MAGM)
        rb.data.transform(rot_x(45.0 * k + 22.5, (0, DC[0], DC[1])))
        finish(rb, 0.4, 2, 35)
        dparts.append(rb)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        rv = cyl_x('mag50_rivet', 1.4, *side(s, 1.2, 2.3), DC[0] + 10.0 * math.cos(a), DC[1] + 10.0 * math.sin(a), 20, MAGM)
        finish(rv, 0.35, 2, 35)
        dparts.append(rv)
# game version: no rounds behind it (the drum's count is not modelled), so the cover is opaque smoked plastic set in
# the window lip, flush instead of over a deep slot
cover = solid('mag50_window', arc_band(46.0, 58.0, WA0 - 1, WA1 + 1), 'yz', -W + 1.0, -W + 1.8,
              mat('br51_poly_smoke') if GAMEBUILD else WIN)
dparts.append(cover)
# outer row of rounds behind the window: case heads against the left wall, bullets to the right
for k in range(0 if GAMEBUILD else 9):
    ad = WA0 + 4.0 + 12.4 * k
    if ad > WA1 - 4.0:
        break
    a = math.radians(ad)
    for o in cartridge('mag50_wrnd%d' % k, 0.0, 0.0, 0.0):
        o.data.transform(Matrix.Translation((-38.6, DC[0] + 52.0 * math.cos(a), DC[1] + 52.0 * math.sin(a))) @
                         Matrix.Rotation(math.radians(-90), 4, 'Z'))
        dparts.append(o)
# left centre: drum axle end with a screwdriver slot
cap_l = cyl_x('mag50_axle', 4.5, -W - 3.6, -W - 1.0, DC[0], DC[1], 32, MAGM)
boolean(cap_l, solid('c_axle_slot', rrect(DC[0] - 0.6, DC[0] + 0.6, DC[1] - 5.0, DC[1] + 5.0, 0.0), 'yz', -W - 4.5, -W - 2.8))
finish(cap_l, 0.3, 2, 35)
dparts.append(cap_l)
band = lathe('mag50_band', [(DR + 1.6, -2.2), (DR + 1.6, 2.2)], 'x', 128, MAGM, center=DC)
finish(band, 0.4, 2, 35)
dparts.append(band)
# winding key on the right face: wind the spring after loading
hub = cyl_x('mag50_key', 10.0, W + 1.4, W + 6.0, DC[0], DC[1], 48, MAGM)
boolean(hub, box('mag50_key_wing', W + 3.0, W + 10.5, DC[0] - 17, DC[0] + 17, DC[1] - 3.2, DC[1] + 3.2, MAGM, 2.4), 'UNION')
for k in range(0 if GAME else 24):
    o = box('c_key_knurl', W, W + 7, DC[0] - 0.6, DC[0] + 0.6, DC[1] + 9.2, DC[1] + 11)
    o.data.transform(rot_x(15 * k, (0, DC[0], DC[1])))
    boolean(hub, o)
finish(hub, 0.3, 2, 35)
dparts.append(hub)
# spring release (unloading): button on the right face, up at the back
a = math.radians(150.0)
by, bz = DC[0] + 48.0 * math.cos(a), DC[1] + 48.0 * math.sin(a)
btn = solid('mag50_release', rrect(by - 5.0, by + 5.0, bz - 3.5, bz + 3.5, 1.5, 4), 'yz', W - 0.5, W + 2.6, MAGM)
for k in range(0 if GAME else 3):
    boolean(btn, solid('c_rel_line', rrect(by - 3.6 + 3.0 * k, by - 2.8 + 3.0 * k, bz - 2.6, bz + 2.6, 0.0), 'yz', W + 2.0, W + 3.0))
btn.data.transform(rot_x(-30.0, (0, by, bz)))
finish(btn, 0.2, 2, 35)
dparts.append(btn)
dparts += cartridge('mag50_rnd1', -4.6, ZT - 7.6) + cartridge('mag50_rnd2', 4.6, ZT - 13.6)
parent(dparts, e50)
print('mags ok')
