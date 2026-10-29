# Blackridge .50 concept round 3 (user 2026-10-28: "我想让别人一眼就认出来这个是沙漠之鹰，但是我们可以改细节").
# The silhouette and sections follow a Desert Eagle reference measured part by part (REF_JSON, written by
# D:/Minecraft Modding/AFL-reference/blackridge_50/measure.py from "Desert Eagle" by ELIZION, Sketchfab, CC BY; the
# reference stays outside the repo): side profiles from the 0.5 mm columns, cross-sections from the convex hulls.
# Ours: the slide serrations, the barrel's top rail and muzzle, the stippled grip and badge, the ring hammer, the safety
# paddles, the markings. Units mm; +y muzzle, +z up, +x right; y = 0 at the beavertail, z = 0 at the magazine base.
import os, json
exec(open(os.path.join(BASE, 'lib.py')).read())

REF = json.load(open(globals().get('REF_JSON', 'D:/Minecraft Modding/AFL-reference/blackridge_50/measure_elizion.json')))
clear('br50')
# ---- game version (INTERIOR): the insides the animations show (docs/native_guns/blackridge_50_remake_v1.md 11). The
# slide runs back far enough to put the bolt face behind the top round's base (about 75 mm): the gap behind the barrel
# opens on the bolt recess and chamber, the magazine mouth and the top of the frame; the slide's nose uncovers the frame's
# dust cover (the gas piston and recoil springs stay inside the frame there, unseen). The magazine (the reference's, hollowed at the
# top for the round the game draws) slides out along its rake through a well in the frame and grip; the follower rests
# empty at the lips (the animations push it down when there are rounds).
MAG_RAKE = 0.179          # magazine axis: dy / dz (10.1 deg back from vertical)
rk = lambda y, z: (y + MAG_RAKE * (z - 120.0), z)      # (y at z 120, z) along the magazine's rake


def mag_well():
    """The well the magazine slides through (frame and grip; 0.4 clearance), open into the slide's channel at the top."""
    return solid('br50_c_well', [rk(43.0 + MAG_RAKE * 90.0, -10.0), rk(87.9 + MAG_RAKE * 90.0, -10.0),
                                 rk(87.9 + MAG_RAKE * 90.0, 126.0), rk(43.0 + MAG_RAKE * 90.0, 126.0)], 'yz', -10.4, 10.4)


MB = mat('br50_black')
MBR = mat('br50_barrel')
MST = mat('br50_steel')
MP = mat('br50_grip')
MPD = mat('br50_pad')
MW = mat('br50_white')


def rdp(pts, eps=0.25):
    """Douglas-Peucker on an open polyline."""
    if len(pts) < 3:
        return pts
    a, b = Vector(pts[0]), Vector(pts[-1])
    ab = b - a
    best, idx = -1.0, 0
    for i in range(1, len(pts) - 1):
        p = Vector(pts[i])
        if ab.length < 1e-9:
            d = (p - a).length
        else:
            d = abs(ab.x * (p - a).y - ab.y * (p - a).x) / ab.length
        if d > best:
            best, idx = d, i
    if best <= eps:
        return [pts[0], pts[-1]]
    return rdp(pts[:idx + 1], eps)[:-1] + rdp(pts[idx:], eps)


def side_poly(name, y0=-1e9, y1=1e9, zmin=-1e9, zmax=1e9):
    """The part's side outline (y, z) between y0 and y1: the top of its highest run forward, the bottom of its lowest back."""
    cols = [(y, r) for y, r in REF[name]['side'] if y0 <= y <= y1]
    top = [(y, min(r[-1][1], zmax)) for y, r in cols]
    bot = [(y, max(r[0][0], zmin)) for y, r in cols]
    poly = rdp(top) + rdp(bot[::-1])
    clean = []
    for p in poly:
        if not clean or (Vector(p) - Vector(clean[-1])).length > 0.05:
            clean.append(p)
    return clean


def traced(name, res=0.25, eps=0.6, min_area=20.0):
    """The part's side silhouette traced from its columns: a raster of the runs, its boundary edges chained into loops
    (outer and holes), simplified. Returns [(loop, area)], positive area = outer (counter-clockwise in y, z)."""
    import numpy as np
    side = REF[name]['side']
    y0, y1 = side[0][0], side[-1][0]
    zs = [z for _, r in side for run in r for z in run]
    z0 = min(zs)
    ny, nz = int((y1 - y0) / res) + 8, int((max(zs) - z0) / res) + 4
    g = np.zeros((ny, nz), bool)
    step = side[1][0] - side[0][0]
    for y, r in side:
        i0 = int((y - y0) / res) + 1
        for a, b in r:
            g[i0:i0 + int(step / res), int((a - z0) / res) + 1:int((b - z0) / res) + 1] = True
    # directed boundary edges, material on the left (counter-clockwise outer loops)
    nxt = {}
    I, J = np.nonzero(g)
    for i, j in zip(I.tolist(), J.tolist()):
        if not g[i, j - 1]:
            nxt[(i, j)] = (i + 1, j)          # bottom side, going +y
        if not g[i + 1, j]:
            nxt[(i + 1, j)] = (i + 1, j + 1)  # right side, going +z
        if not g[i, j + 1]:
            nxt[(i + 1, j + 1)] = (i, j + 1)  # top side, going -y
        if not g[i - 1, j]:
            nxt[(i, j + 1)] = (i, j)          # left side, going -z
    loops = []
    while nxt:
        start, cur = next(iter(nxt)), None
        loop = [start]
        cur = nxt.pop(start)
        while cur != start and cur in nxt:
            loop.append(cur)
            cur = nxt.pop(cur)
        pts = [(y0 + (i - 1) * res, z0 + (j - 1) * res) for i, j in loop]
        area = 0.5 * sum(pts[k][0] * pts[k - 1][1] - pts[k - 1][0] * pts[k][1] for k in range(len(pts)))
        if abs(area) < min_area:
            continue
        # simplify the closed loop: split at the farthest pair, Douglas-Peucker each half
        far = max(range(len(pts)), key=lambda k: (Vector(pts[k]) - Vector(pts[0])).length)
        simp = rdp(pts[:far + 1], eps)[:-1] + rdp(pts[far:] + [pts[0]], eps)[:-1]
        loops.append((simp, -area))
    return loops


def section(name, key):
    return [tuple(p) for p in REF[name]['sections'][key]]


def plan(name, key):
    return [tuple(p) for p in REF[name]['plans'][key]]


def part(oname, ref, m, x0, x1, y0=-1e9, y1=1e9, sec=None, sec_y=(-10, 300), zmin=-1e9, zmax=1e9):
    ob = solid(oname, side_poly(ref, y0, y1, zmin, zmax), 'yz', x0, x1, m)
    if sec:
        boolean(ob, solid(oname + '_c_sec', sec, 'xz', sec_y[0], sec_y[1]), 'INTERSECT')
    return ob


# ---- frame: side outline with the trigger guard opening; the dust cover's under-rail slots ----
loops = sorted(traced('Frame'), key=lambda l: -abs(l[1]))
print('frame loops', [(len(l), round(a)) for l, a in loops])
frame = solid('br50_frame', loops[0][0], 'yz', -14.8, 14.8, MB)
holes = [solid('br50_c_fhole%d' % k, l, 'yz', -20, 20) for k, (l, a) in enumerate(loops[1:]) if a < 0]
if holes:
    boolean(frame, holes)
if INTERIOR:
    boolean(frame, mag_well())
finish(frame, 0.3, 3, 30)

# ---- grip: the reference's outline and plan (sheared along the rake), our stippled wrap and badge ----
grip = part('br50_grip', 'Grip', MP, -19.4, 19.4)
pk = sorted(REF['Grip']['plans'], key=float)
lo_k, hi_k = pk[0], pk[-2]
cy = lambda k: sum(p[1] for p in plan('Grip', k)) / len(plan('Grip', k))
shear = (cy(hi_k) - cy(lo_k)) / (float(hi_k) - float(lo_k))
mid = pk[1]
pl = solid('br50_c_gplan', [(x, y - shear * float(mid)) for x, y in plan('Grip', mid)], 'xy', -10.0, 130.0)
transform(pl, Matrix(((1, 0, 0, 0), (0, 1, shear, 0), (0, 0, 1, 0), (0, 0, 0, 1))))
boolean(grip, pl, 'INTERSECT')
if INTERIOR:
    boolean(grip, mag_well())
finish(grip, 1.2, 3, 28)
# our badge: a recessed oval with a ridge-line mark, both sides (where the reference has its medallion)
for sx in (1, -1):
    badge = solid('br50_c_badge%d' % (sx > 0), fillet([(44.0, 62.0), (64.0, 62.0), (64.0, 76.0), (44.0, 76.0)], 7.0), 'yz',
                  sx * 18.2 if sx > 0 else -25, 25 if sx > 0 else -18.2)
    transform(badge, Matrix(((1, 0, 0, 0), (0, 1, shear, -shear * 69.0), (0, 0, 1, 0), (0, 0, 0, 1))))
    boolean(grip, badge)
    bx = sx * 18.4
    b = solid('br50_badge%d' % (sx > 0), fillet([(44.6, 62.6), (63.4, 62.6), (63.4, 75.4), (44.6, 75.4)], 6.4), 'yz', min(bx, bx + sx * 0.6), max(bx, bx + sx * 0.6), MST)
    transform(b, Matrix(((1, 0, 0, 0), (0, 1, shear, -shear * 69.0), (0, 0, 1, 0), (0, 0, 0, 1))))
    face = bx + sx * 0.6
    ridge = solid('br50_c_ridge%d' % (sx > 0), [(46.5, 66.0), (51.0, 71.5), (54.0, 68.5), (57.5, 73.0), (61.5, 66.0), (61.5, 65.2), (57.5, 71.6), (54.0, 67.2), (51.0, 70.2), (46.5, 65.2)],
                  'yz', min(face - sx * 0.25, face + sx * 2), max(face - sx * 0.25, face + sx * 2))
    transform(ridge, Matrix(((1, 0, 0, 0), (0, 1, shear, -shear * 69.0), (0, 0, 1, 0), (0, 0, 0, 1))))
    boolean(b, ridge)
    finish(b, 0.2, 2, 30)
for sx in (1, -1):
    lathe('br50_gripscrew%d' % (sx > 0), [(0.0, sx * 19.6), (2.4, sx * 19.6), (2.6, sx * 19.2), (2.6, sx * 18.0)], 'x', 32, MST, center=(38.6, 34.6))
pad = part('br50_magbase', 'MagazineBase', MPD, -12.8, 12.8)
finish(pad, 0.4, 2, 30)

# ---- slide: full length; the tall rear block (section at 61) and the lower front under the barrel (section at 169) ----
s_rear = part('br50_slide', 'Slide', MB, -16.3, 16.3, y1=118.0, sec=section('Slide', '61.3'))
s_front = part('br50_slide_front', 'Slide', MB, -16.3, 16.3, y0=112.0, sec=section('Slide', '168.9'))
boolean(s_rear, s_front, 'UNION')
slide = s_rear
cuts = []
# ours: 7 wide grooves leaning forward 15 deg (the reference has narrow vertical ones)
for i in range(7):
    yc = 72.0 + i * 5.6
    for sx, (x0, x1) in (('r', (15.0, 22)), ('l', (-22, -15.0))):
        g = solid('br50_c_ser%s%d' % (sx, i), fillet([(yc - 1.4, 112.5), (yc + 1.4, 112.5), (yc + 1.4, 141.5), (yc - 1.4, 141.5)], 1.4, 6), 'yz', x0, x1)
        transform(g, rot_x(-15, (0, yc, 127.0)))
        cuts.append(g)
if INTERIOR:   # the slide's lower half is a channel round the frame (open on top behind the nose bridge), as on the real gun
    cuts.append(solid('br50_c_channel', [(40.0, 98.0), (236.0, 98.0), (236.0, 129.7), (40.0, 129.7)], 'yz', -14.3, 14.3))
boolean(slide, cuts)
if not GAME:
    boolean(slide, [text_solid('br50_c_m1', 'BLACKRIDGE  .50 AE', 6.2, (-16.28, 148.0, 117.2), '-x', 0.2, align='RIGHT', spacing=1.06),
                    text_solid('br50_c_m2', 'BLACKRIDGE FIREARMS', 3.4, (16.28, 150.0, 120.0), '+x', 0.15, spacing=1.06),
                    text_solid('br50_c_m3', 'MESA ROJA  S6', 3.0, (16.28, 150.0, 114.6), '+x', 0.15, spacing=1.06)])
finish(slide, 0.3, 3, 30)

# ---- barrel: the reference's round-bottomed triangular section; ours on top (rail) and at the muzzle ----
b_main = part('br50_barrel', 'Barrel', MBR, -16.3, 16.3, y1=250.0, sec=section('Barrel', '193.2'))
b_front = part('br50_barrel_front', 'Barrel', MBR, -16.3, 16.3, y0=246.0, sec=section('Barrel', '249.0'))
boolean(b_main, b_front, 'UNION')
barrel = b_main
BORE_Z = 134.0
cuts = [solid('br50_c_bore', circle(0, BORE_Z, 6.35, 64), 'xz', 240, 280),
        lathe('br50_c_crown', [(0.0, 271.4), (8.0, 271.4), (9.4, 274)], 'y', 64, center=(0, BORE_Z))]
# ours: two slanted vents on each side of the muzzle block instead of the reference's top ports
for sx in (1, -1):
    for j in range(2):
        v = solid('br50_c_vent%d%d' % (sx > 0, j), fillet([(254.0 + j * 7.0, 136.0), (258.0 + j * 7.0, 136.0), (258.0 + j * 7.0, 146.0), (254.0 + j * 7.0, 146.0)], 1.6),
                  'yz', sx * 4.0 if sx > 0 else -20, 20 if sx > 0 else -4.0)
        transform(v, rot_x(-20, (0, 256.0 + j * 7.0, 141.0)))
        cuts.append(v)
if INTERIOR:   # bolt recess and chamber (seen through the gap behind the barrel with the slide back)
    cuts += [lathe('br50_c_recess', [(8.6, 105.0), (8.6, 133.2)], 'y', 40, center=(0, BORE_Z + 0.7)),
             lathe('br50_c_chamber', [(6.9, 133.0), (6.9, 160.0)], 'y', 40, center=(0, BORE_Z))]
boolean(barrel, cuts)
finish(barrel, 0.3, 3, 30)
top = max(z for y, r in REF['Barrel']['side'] if 160 < y < 240 for z in (r[-1][1],))
rx = [(-5.6, top - 0.6), (5.6, top - 0.6), (5.6, top + 1.4), (8.0, top + 2.4), (8.0, top + 5.0), (-8.0, top + 5.0), (-8.0, top + 2.4), (-5.6, top + 1.4)]
rail = solid('br50_rail', fillet(rx, [0, 0, 0.3, 0.5, 0.6, 0.6, 0.5, 0.3]), 'xz', 140.0, 246.0, MBR)
boolean(rail, [solid('br50_c_rs%d' % i, [(146.0 + i * 12.0, top + 2.2), (152.0 + i * 12.0, top + 2.2), (152.0 + i * 12.0, top + 10), (146.0 + i * 12.0, top + 10)], 'yz', -12, 12) for i in range(8)])
finish(rail, 0.18, 2, 30)

# ---- sights, hammer, back part, bolt in the port ----
fs = part('br50_fsight', 'FrontSight', MBR, -5.4, 5.4, sec=section('FrontSight', list(REF['FrontSight']['sections'])[2]))
finish(fs, 0.15, 2, 30)
rs = part('br50_rsight', 'RearSight', MB, -12.9, 12.9, sec=section('RearSight', list(REF['RearSight']['sections'])[2]))
boolean(rs, solid('br50_c_notch', fillet([(-1.9, 150.5), (1.9, 150.5), (1.9, 170), (-1.9, 170)], [0.6, 0.6, 0, 0]), 'xz', 0, 100))
finish(rs, 0.15, 2, 30)
for k, sx in enumerate((-5.4, 5.4)):
    lathe('br50_rdot%d' % k, [(0.0, 50.4), (0.95, 50.4), (0.95, 51.4)], 'y', 32, MW, center=(sx, 152.4))
back = part('br50_backpart', 'BackPart', MB, -11.0, 11.0)
finish(back, 0.25, 2, 30)
ham = part('br50_hammer', 'Hammer', MST, -3.7, 3.7)
# ours: a ring hammer (lightening hole through the spur)
hz = REF['Hammer']['z']
hy = REF['Hammer']['y']
boolean(ham, solid('br50_c_hring', circle(hy[0] + 7.0, hz[1] - 8.0, 3.0, 32), 'yz', -6, 6))
finish(ham, 0.2, 2, 30)
bolt = part('br50_bolt', 'BoltMain', MST, -13.4, 12.0, sec=section('BoltMain', '118.2') if INTERIOR else None, sec_y=(100, 140))
finish(bolt, 0.2, 2, 30)
trig = part('br50_trigger', 'Trigger', MST, -3.5, 3.5)
finish(trig, 0.2, 2, 30)

# ---- controls ----
for sx, nm in ((1, 'r'), (-1, 'l')):
    x0, x1 = (16.0, 19.2) if sx > 0 else (-19.2, -16.0)
    sf = part('br50_safety_' + nm, 'Safety', MST, x0, x1)
    # ours: three grip grooves across the paddle
    sy, sz = REF['Safety']['y'], REF['Safety']['z']
    boolean(sf, [solid('br50_c_sg%s%d' % (nm, i), [(sy[0] + 3.0 + i * 2.2, sz[0] - 1), (sy[0] + 3.9 + i * 2.2, sz[0] - 1), (sy[0] + 3.9 + i * 2.2, sz[1] + 1), (sy[0] + 3.0 + i * 2.2, sz[1] + 1)],
                        'yz', x1 - 0.4 if sx > 0 else x0 - 1, x1 + 1 if sx > 0 else x0 + 0.4) for i in range(3)])
    finish(sf, 0.2, 2, 30)
ss = part('br50_slidestop', 'SlideHolder', MST, -16.6, -14.6)
finish(ss, 0.15, 2, 30)
lk = part('br50_locklever', 'SideSwitch', MST, 14.6, 16.5)
finish(lk, 0.15, 2, 30)
lp = part('br50_lockpin', 'Button_02', MST, -15.6, -14.4)
finish(lp, 0.15, 2, 30)
mr = part('br50_magrel', 'Button_03', MST, -15.8, -14.4)
finish(mr, 0.15, 2, 30)

if INTERIOR:
    # magazine: the reference's outline and section, hollow at the top (inside 14.4 wide, 1.3 walls front and back)
    mag = part('br50_mag', 'Magazine', MB, -10.0, 10.0, sec=section('Magazine', '70.4'))
    boolean(mag, solid('br50_c_magcav', [rk(60.6, 96.0), rk(100.6, 96.0), rk(100.6, 140.0), rk(60.6, 140.0)], 'yz', -7.2, 7.2))
    finish(mag, 0.2, 2, 30)
    fol = solid('br50_follower', fillet([rk(61.2, 116.5), rk(100.0, 116.5), rk(100.0, 121.5), rk(61.2, 121.5)], [0, 0, 1.2, 1.2]), 'yz', -6.9, 6.9, MPD)
    finish(fol, 0.15, 2, 30)
print('br50 r3 ok', len(bpy.data.collections[COL].objects))
