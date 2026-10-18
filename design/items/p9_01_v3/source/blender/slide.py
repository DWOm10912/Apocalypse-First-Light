# P9 v3 slide, extractor, rear plate and sights.
import os
exec(open(os.path.join(BASE, 'lib.py')).read())

clear('slide')
MS = mat('p9_slide_nitride')
MST = mat('p9_steel_machined')
MP = mat('p9_polymer')
MW = mat('p9_sight_white')
MG = mat('p9_tritium')

W, ZB, ZT, ZC, WT = 13.2, 99.4, 118.9, 114.6, 9.6
YR, YF = -61.0, 121.0
XS = [(-W, ZB), (W, ZB), (W, ZC), (WT, ZT), (-WT, ZT), (-W, ZC)]

# outer shape = cross-section along y  AND  side profile along x  AND  top-view nose taper along z
s = solid('slide', fillet(XS, [0.5, 0.5, 2.5, 1.8, 1.8, 2.5]), 'xz', YR - 5, YF + 5, MS)
side = fillet([(YR, ZB), (YF, ZB), (YF, 115.6), (YF - 3.4, ZT), (YR, ZT)], [0.5, 0.5, 1.2, 3.0, 1.0])
boolean(s, solid('slide_c_side', side, 'yz', -20, 20), 'INTERSECT')
nose = fillet([(-W - 2, YR - 5), (W + 2, YR - 5), (W + 2, YF - 16), (W, YF - 7), (W - 0.9, YF + 0.2),
               (-W + 0.9, YF + 0.2), (-W, YF - 7), (-W - 2, YF - 16)], [0, 0, 0, 6, 0, 0, 6, 0])
nose_cut = solid('slide_c_nose', nose, 'xy', ZB - 5, ZT + 5)
boolean(s, nose_cut, 'INTERSECT')

cuts = []
# barrel bore opening
cuts.append(solid('slide_c_bore', circle(0, 109.1, 6.35, 64), 'xz', 110, 130))
# ejection port on the right (+x), open through the top
port = fillet([(-5.4, 0.0), (W + 3, 0.0), (W + 3, 27.6), (-5.4, 27.6)], [2.2, 0, 0, 2.2])
cuts.append(solid('slide_c_port', port, 'xy', 105.6, 125))
# rear serration panels (stepped 0.7 mm) both sides, front edge slanted
panel = fillet([(YR - 5, 101.6), (-13.0, 101.6), (-7.6, 114.2), (YR - 5, 114.2)], [0, 1.0, 1.0, 0])
cuts.append(solid('slide_c_panel_r', panel, 'yz', W - 0.7, W + 5))
cuts.append(solid('slide_c_panel_l', panel, 'yz', -W - 5, -W + 0.7))
# rear sight and front sight dovetails across the top
def dovetail(y0, y1, depth):
    zb = ZT - depth
    k = 0.577
    return [(y0, zb), (y1, zb), (y1 - k * (depth + 4), ZT + 4), (y0 + k * (depth + 4), ZT + 4)]
cuts.append(solid('slide_c_rdt', dovetail(-56.0, -46.2, 2.5), 'yz', -20, 20))
cuts.append(solid('slide_c_fdt', dovetail(101.6, 108.4, 2.0), 'yz', -20, 20))
# rear face pocket for the back plate
cuts.append(solid('slide_c_rpocket', fillet([(-6.0, 101.0), (6.0, 101.0), (6.0, 111.4), (-6.0, 111.4)], 1.2), 'xz', YR - 5, YR + 1.2))
# extractor slot on the right
ext = fillet([(-16.8, 107.7), (1.5, 107.7), (1.5, 111.9), (-16.8, 111.9)], [1.0, 0, 0, 1.0])
cuts.append(solid('slide_c_ext', ext, 'yz', W - 1.0, W + 5))
boolean(s, cuts)

# serrations: stadium grooves leaning back 14 deg
grooves = []
def groove(yc, z0, z1, x0, x1, name):
    g = solid(name, fillet([(yc - 0.78, z0), (yc + 0.78, z0), (yc + 0.78, z1), (yc - 0.78, z1)], 0.78, 6), 'yz', x0, x1)
    transform(g, rot_x(14, (0, yc, (z0 + z1) / 2)))
    return g
for i in range(12):
    yc = -56.0 + i * 3.1
    grooves.append(groove(yc, 103.2, 112.8, W - 1.5, W + 5, 'slide_c_gr%d' % i))
    grooves.append(groove(yc, 103.2, 112.8, -W - 5, -W + 1.5, 'slide_c_gl%d' % i))
for i in range(9):
    yc = 76.5 + i * 3.25
    grooves.append(groove(yc, 103.4, 113.2, W - 0.8, W + 5, 'slide_c_fr%d' % i))
    grooves.append(groove(yc, 103.4, 113.2, -W - 5, -W + 0.8, 'slide_c_fl%d' % i))
boolean(s, grooves)
if INTERIOR:
    # what the animations open up: the channel from the breech face to the muzzle bore (open at the bottom, the frame
    # rails close it) and the firing pin hole in the breech face
    boolean(s, [solid('slide_c_chan', rrect(-8.3, 8.3, ZB - 2.0, 117.0, [0, 0, 1.2, 1.2]), 'xz', 0.0, 112.0),
                solid('slide_c_fph', circle(0, 109.13, 0.95, 24), 'xz', -2.0, 0.5)])
# markings (engraved 0.15 mm): model on the right behind the port, calibre under it (the game P9 fires 9x19)
if not GAME:
    marks = [text_solid('slide_c_m1', 'P9-01', 3.4, (W, 31.5, 106.2), '+x', 0.15, spacing=1.05),
             text_solid('slide_c_m2', 'CAL 9x19mm' if INTERIOR else 'CAL 5.7x28', 2.1, (W, 50.5, 106.4), '+x', 0.15, spacing=1.05)]
    boolean(s, marks)
finish(s, 0.22, 3, 30)

# extractor (steel), proud of the slide by 0.3 mm
ex = solid('slide_extractor', fillet([(-16.5, 108.0), (0.4, 108.0), (1.2, 109.0), (1.2, 111.6), (-16.5, 111.6)], [0.9, 0.2, 0.3, 0.2, 0.9]), 'yz', W - 1.0, W + 0.3, MST)
boolean(ex, solid('slide_c_expin', circle(-13.4, 109.8, 0.75, 24), 'yz', W, W + 1), 'DIFFERENCE')
finish(ex, 0.12, 2, 30)

# back plate (polymer) recessed 0.15 mm in the pocket, with the striker indicator hole
bp = solid('slide_backplate', fillet([(-5.88, 101.12), (5.88, 101.12), (5.88, 111.28), (-5.88, 111.28)], 1.1), 'xz', YR - 0.15 + 0.0, YR + 1.2, MP)
transform(bp, Matrix.Translation((0, 0.15, 0)))
boolean(bp, solid('slide_c_bphole', circle(0, 107.6, 1.1, 32), 'xz', YR - 1, YR + 0.9), 'DIFFERENCE')
finish(bp, 0.15, 2, 30)
pin = solid('slide_striker_pin', circle(0, 107.6, 0.95, 32), 'xz', YR + 0.35, YR + 1.0, MST)
finish(pin, 0.1, 2, 30)

# sights: dovetail base cut to the slide section, then the body on top
def sight_base(name, y0, y1, depth):
    zb = ZT - depth
    k = 0.577
    g = 0.05
    tr = [(y0 + g, zb + g), (y1 - g, zb + g), (y1 - g - k * (depth + 1), ZT + 1), (y0 + g + k * (depth + 1), ZT + 1)]
    b = solid(name, tr, 'yz', -W - 1, W + 1, MS)
    boolean(b, solid(name + '_c', fillet(XS, [0.5, 0.5, 2.5, 1.8, 1.8, 2.5]), 'xz', -100, 200), 'INTERSECT')
    transform(b, Matrix.Translation((0, 0, -0.02)))
    return b

rs = solid('slide_rsight', fillet([(-57.2, ZT - 0.3), (-45.6, ZT - 0.3), (-45.6, 123.4), (-46.6, 124.7), (-55.9, 124.7), (-57.2, 122.4)],
                                  [0, 0, 0.6, 0.5, 0.6, 0.5]), 'yz', -12, 12, MS)
front = fillet([(-9.4, 118.2), (9.4, 118.2), (9.4, 121.2), (8.7, 124.7), (1.7, 124.7), (1.7, 121.9), (-1.7, 121.9), (-1.7, 124.7), (-8.7, 124.7), (-9.4, 121.2)],
               [0, 0, 0.6, 0.4, 0.25, 0.3, 0.3, 0.25, 0.4, 0.6])
boolean(rs, solid('slide_c_rsf', front, 'xz', -70, -40), 'INTERSECT')
cuts = []
for i, z in enumerate([122.9, 123.6, 124.3]):
    yy = -57.2 + (z - 122.4) * (1.3 / 2.3)
    c = solid('slide_c_rsg%d' % i, [(yy - 0.3, z - 0.18), (yy + 0.3, z - 0.18), (yy + 0.3, z + 0.18), (yy - 0.3, z + 0.18)], 'yz', -12, 12)
    cuts.append(c)
for sx in (-4.7, 4.7):
    cuts.append(solid('slide_c_rsdot%d' % (sx > 0), circle(sx, 120.4, 1.15, 32), 'xz', -60, -56.2))
boolean(rs, cuts)
boolean(rs, sight_base('slide_rsight_base', -56.0, -46.2, 2.5), 'UNION')
finish(rs, 0.14, 2, 30)

fs = solid('slide_fsight', fillet([(101.3, ZT - 0.3), (108.7, ZT - 0.3), (108.7, 120.8), (106.6, 124.7), (102.0, 124.7), (101.3, 123.6)],
                                  [0, 0, 0.6, 0.6, 0.5, 0.4]), 'yz', -1.9, 1.9, MS)
boolean(fs, solid('slide_c_fsdot', circle(0, 122.0, 1.0, 32), 'xz', 100, 102.2), 'DIFFERENCE')
boolean(fs, sight_base('slide_fsight_base', 101.6, 108.4, 2.0), 'UNION')
finish(fs, 0.12, 2, 30)

# tritium inserts: white ring + green glass, each a short cylinder in its hole
def insert(name, cx, cz, r_out, y_face, depth, inward):
    ring = lathe(name + '_ring', [(0.0, 0), (r_out - 0.02, 0), (r_out - 0.02, depth)], 'y', 40, MW, center=(cx, cz))
    glass = lathe(name + '_glass', [(0.0, 0), (r_out * 0.45, 0), (r_out * 0.45, depth)], 'y', 32, MG, center=(cx, cz))
    for o in (ring, glass):
        transform(o, Matrix.Translation((0, y_face + (0.12 if inward > 0 else -0.12) - (0 if inward > 0 else depth), 0)))
    transform(glass, Matrix.Translation((0, -0.03 if inward > 0 else 0.03, 0)))
    for o in (ring, glass):
        for p in o.data.polygons:
            p.use_smooth = True
    return ring, glass

insert('slide_rdotL', -4.7, 120.4, 1.15, -57.2, 1.0, 1)
insert('slide_rdotR', 4.7, 120.4, 1.15, -57.2, 1.0, 1)
insert('slide_fdot', 0, 122.0, 1.0, 101.3, 0.9, 1)

print('slide faces', len(s.data.polygons), 'rsight', len(rs.data.polygons))
