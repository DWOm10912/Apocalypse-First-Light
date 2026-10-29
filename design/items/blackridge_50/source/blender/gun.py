# Blackridge .50 concept: the whole pistol. Units mm; +y muzzle, +z up, +x the gun's right side.
# y = 0 at the beavertail tip, z = 0 at the grip bottom. Desert Eagle Mark XIX proportions (overall 273, 6 in barrel,
# height 159, width 32) and mechanism (fixed barrel on top, gas piston in the slide nose under it, rotating bolt in the
# slide, slide-mounted ambidextrous safety, single-action hammer, barrel lock pin left / lever right); the look is ours
# (docs/native_guns/blackridge_50_remake_v1.md).
import os
exec(open(os.path.join(BASE, 'lib.py')).read())

clear('br50')
MB = mat('br50_black')
MBR = mat('br50_barrel')
MST = mat('br50_steel')
MBO = mat('br50_bore')
MP = mat('br50_grip')
MPD = mat('br50_pad')
MW = mat('br50_white')

BORE_Z = 134.0
SLIDE_Z0, SLIDE_TOP = 100.0, 150.0
BARREL_Z0, BARREL_TOP = 121.0, 150.0
Y_BREECH, Y_MUZZLE, Y_NOSE = 120.0, 273.0, 252.0
WS = 14.0          # slide half width
WB = 12.0          # barrel half width at the bottom

# ---- slide: the tall rear block (bolt, safety, rear sight) and the nose under the barrel (gas cylinder, piston) ----
rear_x = fillet([(-WS, SLIDE_Z0), (WS, SLIDE_Z0), (WS, 139.0), (8.5, SLIDE_TOP), (-8.5, SLIDE_TOP), (-WS, 139.0)], [0.5, 0.5, 2.5, 1.5, 1.5, 2.5])
slide = solid('br50_slide', rear_x, 'xz', 8.0, Y_BREECH, MB)
# the rear block's front edge leans back from the nose to the top (round 2, user 2026-10-28 "抛壳的地方，感觉过度有点生硬"):
# the barrel's rear end follows it 0.3 mm ahead, one slanted parting line instead of a step
SLANT = 12.0 / 29.0     # y back per mm up
side = fillet([(8.0, SLIDE_Z0), (Y_BREECH + 6, SLIDE_Z0), (Y_BREECH + 6, BARREL_Z0), (Y_BREECH + 4, BARREL_Z0), (Y_BREECH + 4 - 29 * SLANT, SLIDE_TOP),
               (16.0, SLIDE_TOP), (8.0, 141.0)], [0.5, 0, 0, 0, 1.0, 3.0, 2.0])
boolean(slide, solid('br50_c_sside', side, 'yz', -20, 20), 'INTERSECT')
nose_x = fillet([(-12.5, SLIDE_Z0), (12.5, SLIDE_Z0), (12.5, BARREL_Z0 + 0.6), (-12.5, BARREL_Z0 + 0.6)], [4.0, 4.0, 0, 0])
nose = solid('br50_slide_nose', nose_x, 'xz', Y_BREECH - 2, Y_NOSE, MB)
nose_side = fillet([(Y_BREECH - 4, SLIDE_Z0), (Y_NOSE, SLIDE_Z0), (Y_NOSE, BARREL_Z0 + 2), (Y_BREECH - 4, BARREL_Z0 + 2)], [0, 3.0, 0, 0])
boolean(nose, solid('br50_c_nside', nose_side, 'yz', -20, 20), 'INTERSECT')
boolean(slide, nose, 'UNION')
cuts = []
# ejection port on the right, behind the barrel (the bolt shows through it)
# the port's front edge runs along the slanted parting line, so the barrel's rear end (the chamber hood) closes it
cuts.append(solid('br50_c_port', fillet([(84.0, 125.0), (Y_BREECH + 6, 125.0), (Y_BREECH - 5, 147.0), (84.0, 147.0)], [9.0, 0, 0, 9.0]), 'yz', 3.0, 20))
# rear serrations: forward-leaning grooves on both sides of the rear block
for i in range(9):
    yc = 18.0 + i * 4.2
    for sx, (x0, x1) in (('r', (WS - 1.2, WS + 5)), ('l', (-WS - 5, -WS + 1.2))):
        g = solid('br50_c_ser%s%d' % (sx, i), fillet([(yc - 1.0, 106.0), (yc + 1.0, 106.0), (yc + 1.0, 141.0), (yc - 1.0, 141.0)], 1.0, 6), 'yz', x0, x1)
        transform(g, rot_x(-6, (0, yc, 123.5)))
        cuts.append(g)
# our own: the nose's upper half stepped in 1 mm, a crisp line from the rear block to the nose tip
step = [(Y_BREECH - 1, 112.0), (Y_NOSE + 5, 112.0), (Y_NOSE + 5, 130.0), (Y_BREECH - 1, 130.0)]
cuts.append(solid('br50_c_step_r', step, 'yz', 11.5, 20))
cuts.append(solid('br50_c_step_l', step, 'yz', -20, -11.5))
# the hammer's slot in the slide's rear face
cuts.append(solid('br50_c_hslot', fillet([(0.0, SLIDE_Z0 - 1), (22.0, SLIDE_Z0 - 1), (22.0, 128.0), (0.0, 128.0)], [0, 0, 3.0, 0]), 'yz', -4.6, 4.6))
# rear sight dovetail
cuts.append(solid('br50_c_rdt', [(14.0, SLIDE_TOP - 2.5), (26.0, SLIDE_TOP - 2.5), (28.0, SLIDE_TOP + 3), (12.0, SLIDE_TOP + 3)], 'yz', -20, 20))
boolean(slide, cuts)
if not GAME:
    boolean(slide, [text_solid('br50_c_m1', 'BLACKRIDGE FIREARMS', 2.6, (12.5, Y_BREECH + 18, 108.8), '+x', 0.15, spacing=1.05),
                    text_solid('br50_c_m2', 'MESA ROJA  S6', 2.2, (-12.5, Y_BREECH + 20, 107.0), '-x', 0.15, align='RIGHT', spacing=1.05)])
finish(slide, 0.3, 3, 30)

# bolt face behind the port (steel), with the extractor claw on the right
bolt = lathe('br50_bolt', [(0.0, 82.0), (9.6, 82.0), (9.6, Y_BREECH - 0.5), (0.0, Y_BREECH - 0.5)], 'y', 64, MST, center=(0, BORE_Z))
finish(bolt, 0.25, 2, 30)
ext = solid('br50_extractor', fillet([(98.0, 131.6), (Y_BREECH - 3.0, 131.6), (Y_BREECH - 3.0, 136.4), (98.0, 136.4)], [1.0, 0.3, 0.3, 1.0]), 'yz', 7.8, 10.6, MST)
finish(ext, 0.12, 2, 30)

# ---- barrel block: fixed, on top; steep sloped sides (round 2, user 2026-10-28: round 1 read like a sci-fi slab
# pistol), one flute down each slope, the rail, the muzzle lug over the gas cylinder ----
WB = 12.5
SLOPE0 = 127.5     # the vertical band below the slopes
bx = fillet([(-WB, BARREL_Z0), (WB, BARREL_Z0), (WB, SLOPE0), (7.4, BARREL_TOP), (-7.4, BARREL_TOP), (-WB, SLOPE0)], [0.6, 0.6, 1.5, 1.2, 1.2, 1.5])
barrel = solid('br50_barrel', bx, 'xz', Y_BREECH - 10, Y_MUZZLE, MBR)
lug = solid('br50_barrel_lug', fillet([(-WB, 108.0), (WB, 108.0), (WB, BARREL_Z0 + 1), (-WB, BARREL_Z0 + 1)], [6.0, 6.0, 0, 0]), 'xz', Y_NOSE + 0.6, Y_MUZZLE, MBR)
boolean(barrel, lug, 'UNION')
prof = fillet([(Y_BREECH + 4.3 + 21 * SLANT, 100.0), (262.0, 100.0), (Y_MUZZLE, 116.0), (Y_MUZZLE, BARREL_TOP - 2.5), (Y_MUZZLE - 2.5, BARREL_TOP),
               (Y_BREECH + 4.3 - 29 * SLANT, BARREL_TOP)], [0, 3.0, 2.0, 1.2, 2.5, 1.0])
boolean(barrel, solid('br50_c_bside', prof, 'yz', -20, 20), 'INTERSECT')
cuts = []
# flutes: a capsule groove 0.9 deep down the middle of each slope
for sx in (1, -1):
    cuts.append(lathe('br50_c_flute%d' % (sx > 0), [(0.0, 150.0), (1.8, 150.8), (2.6, 153.0), (2.6, 240.0), (1.8, 242.2), (0.0, 243.0)], 'y', 32,
                      center=(sx * 11.61, 139.13)))
cuts.append(solid('br50_c_bore', circle(0, BORE_Z, 6.35, 64), 'xz', Y_MUZZLE - 30, Y_MUZZLE + 5))
# recessed crown and the gas cylinder's front cap bore in the lug
cuts.append(lathe('br50_c_crown', [(0.0, Y_MUZZLE - 1.6), (8.2, Y_MUZZLE - 1.6), (9.4, Y_MUZZLE + 1)], 'y', 64, center=(0, BORE_Z)))
cuts.append(solid('br50_c_gascap', circle(0, 116.0, 3.0, 40), 'xz', Y_MUZZLE - 2.6, Y_MUZZLE + 2))
boolean(barrel, cuts)
if not GAME:
    boolean(barrel, [text_solid('br50_c_b1', 'BLACKRIDGE .50', 3.4, (-WB, 165.0, 122.9), '-x', 0.15, align='RIGHT', spacing=1.08),
                     text_solid('br50_c_b2', '.50 AE', 3.4, (WB, 165.0, 122.9), '+x', 0.15, spacing=1.08)])
finish(barrel, 0.3, 3, 30)

# rail: Weaver-style on top of the barrel, cross slots
rx = [(-6.8, BARREL_TOP - 0.2), (6.8, BARREL_TOP - 0.2), (6.8, 151.6), (9.6, 152.6), (9.6, 155.2), (-9.6, 155.2), (-9.6, 152.6), (-6.8, 151.6)]
rail = solid('br50_rail', fillet(rx, [0, 0, 0.3, 0.5, 0.6, 0.6, 0.5, 0.3]), 'xz', 138.0, 252.0, MBR)
boolean(rail, [solid('br50_c_rs%d' % i, [(144.0 + i * 10.0, 152.4), (148.0 + i * 10.0, 152.4), (148.0 + i * 10.0, 160), (144.0 + i * 10.0, 160)], 'yz', -12, 12) for i in range(11)])
boolean(rail, solid('br50_c_rend', fillet([(138.0, 140.0), (252.0, 140.0), (252.0, 155.2), (141.0, 155.2), (138.0, 152.0)], [0, 0, 1.0, 1.0, 0]), 'yz', -12, 12), 'INTERSECT')
finish(rail, 0.18, 2, 30)

# sights: front blade on the barrel ahead of the rail, rear notch sight in the slide dovetail; white dots
fsight = solid('br50_fsight', fillet([(255.0, BARREL_TOP - 0.3), (269.0, BARREL_TOP - 0.3), (269.0, 152.0), (265.5, 161.0), (258.5, 161.0), (255.0, 155.0)],
                                     [0, 0, 0.6, 0.8, 0.8, 1.0]), 'yz', -1.8, 1.8, MBR)
boolean(fsight, solid('br50_c_fdot', circle(0, 158.6, 1.0, 32), 'xz', 250, 261.0))
finish(fsight, 0.15, 2, 30)
fdot = lathe('br50_fdot', [(0.0, 259.0), (0.98, 259.0), (0.98, 260.6)], 'y', 32, MW, center=(0, 158.6))
rsight = solid('br50_rsight', fillet([(12.6, SLIDE_TOP - 2.4), (27.4, SLIDE_TOP - 2.4), (27.4, 157.0), (25.6, 160.0), (14.0, 160.0), (12.6, 158.4)],
                                     [0, 0, 0.6, 0.6, 0.6, 0.6]), 'yz', -11.0, 11.0, MB)
boolean(rsight, [solid('br50_c_notch', fillet([(-1.9, 155.0), (1.9, 155.0), (1.9, 170), (-1.9, 170)], [0.6, 0.6, 0, 0]), 'xz', 0, 40)]
        + [solid('br50_c_rdot%d' % k, circle(sx, 156.6, 1.0, 32), 'xz', 26.6, 30) for k, sx in enumerate((-5.6, 5.6))])
boolean(rsight, solid('br50_c_rsx', fillet([(-11.0, 140.0), (11.0, 140.0), (11.0, 156.0), (9.0, 160.0), (-9.0, 160.0), (-11.0, 156.0)], [0, 0, 0.8, 0.8, 0.8, 0.8]), 'xz', 0, 40), 'INTERSECT')
finish(rsight, 0.15, 2, 30)
for k, sx in enumerate((-5.6, 5.6)):
    lathe('br50_rdot%d' % k, [(0.0, 26.0), (0.98, 26.0), (0.98, 27.2)], 'y', 32, MW, center=(sx, 156.6))

# ---- frame: rails under the slide, dust cover with the barrel lock, trigger guard, grip core ----
fprof = [(2.0, 95.0), (8.0, SLIDE_Z0 + 0.6), (176.0, SLIDE_Z0 + 0.6), (176.0, 92.0), (170.0, 86.0), (140.0, 86.0), (140.0, 62.0), (133.0, 55.0),
         (85.0, 55.0), (79.0, 64.0), (61.0, 0.0), (1.0, 0.0), (22.0, 91.0), (8.0, 91.5)]
frame = solid('br50_frame', fillet(fprof, [1.5, 0.5, 0.5, 1.5, 4.0, 0, 9.0, 10.0, 6.0, 4.0, 3.0, 3.0, 2.0, 3.0]), 'yz', -13.0, 13.0, MB)
cuts = []
# trigger guard opening; the guard is 10 wide, the dust cover 26
cuts.append(solid('br50_c_guard', fillet([(84.0, 61.5), (133.0, 61.5), (133.0, 86.5), (84.0, 86.5)], [6.0, 11.0, 0, 0]), 'yz', -20, 20))
for sx, (x0, x1) in (('r', (5.0, 20.0)), ('l', (-20.0, -5.0))):
    cuts.append(solid('br50_c_gside' + sx, [(80.0, 50.0), (142.0, 50.0), (142.0, 85.0), (80.0, 85.0)], 'yz', x0, x1))
boolean(frame, cuts)
finish(frame, 0.3, 3, 30)

# grip: one-piece stippled wrap with a flared well, panels over the frame core
gp = fillet([(23.5, 92.0), (13.5, 70.0), (4.0, 38.0), (-1.5, -2.0), (63.0, -2.0), (81.0, 66.0), (81.0, 75.0), (45.0, 86.0)], [3.0, 16.0, 16.0, 3.0, 3.0, 4.0, 3.0, 8.0])
grip = solid('br50_grip', gp, 'yz', -17.0, 17.0, MP)
# rounded straps and edges: a wide bevel (the wrap is one moulding)
# rounded straps: a plan-view stadium (x by y) sheared along the grip's rake, intersected with the side profile
plan = solid('br50_c_gplan', rrect(-17.0, 17.0, -8.0, 64.5, [11.0, 9.0, 9.0, 11.0], 10), 'xy', -10.0, 110.0)
transform(plan, Matrix(((1, 0, 0, 0), (0, 1, 0.245, 0), (0, 0, 1, 0), (0, 0, 0, 1))))
boolean(grip, plan, 'INTERSECT')
finish(grip, 2.5, 4, 28)
# magazine base pad under the grip
pad = solid('br50_magpad', fillet([(-2.5, -7.0), (64.5, -7.0), (63.5, -1.8), (-1.0, -1.8)], [2.0, 2.0, 1.0, 1.0]), 'yz', -15.0, 15.0, MPD)
finish(pad, 0.4, 2, 30)

# ---- controls ----
# hammer: spur with a lightening hole, pivot pin through the frame
ham = solid('br50_hammer', fillet([(7.5, 96.0), (15.0, 96.0), (15.5, 112.0), (10.5, 125.0), (2.0, 127.0), (0.8, 122.5), (6.0, 113.0)], [1.5, 2.0, 3.0, 1.0, 1.0, 1.2, 2.0]), 'yz', -3.6, 3.6, MST)
boolean(ham, [solid('br50_c_hh', circle(11.0, 112.0, 2.4, 32), 'yz', -5, 5)] +
        [solid('br50_c_hs%d' % i, [(1.0 + i * 1.6, 124.0), (1.8 + i * 1.6, 124.0), (1.8 + i * 1.6, 130), (1.0 + i * 1.6, 130)], 'yz', -5, 5) for i in range(4)])
finish(ham, 0.2, 2, 30)
for sx in (1, -1):
    lathe('br50_hpin%d' % (sx > 0), [(0.0, 13.0 * sx), (1.6, 13.0 * sx), (1.6, 13.4 * sx)], 'x', 24, MST, center=(12.0, 100.5))
# safety: a drum through the slide's rear, a paddle on each side
for sx, name in ((1, 'r'), (-1, 'l')):
    x0, x1 = (WS - 0.2, WS + 2.2) if sx > 0 else (-WS - 2.2, -WS + 0.2)
    lev = solid('br50_safety_' + name, fillet([(19.0, 130.0), (29.0, 130.0), (29.0, 138.0), (14.5, 145.5), (11.5, 143.5), (19.0, 137.0)], [3.5, 3.5, 3.5, 1.5, 1.5, 0.8]), 'yz', x0, x1, MST)
    boolean(lev, [solid('br50_c_sg%s%d' % (name, i), [(12.6 + i * 1.5, 140.5), (13.3 + i * 1.5, 140.5), (13.3 + i * 1.5, 147), (12.6 + i * 1.5, 147)], 'yz',
                        x1 - 0.35 if sx > 0 else x0 - 1, x1 + 1 if sx > 0 else x0 + 0.35) for i in range(4)])
    finish(lev, 0.15, 2, 30)
# slide stop on the left, above the trigger, thumb pad at the rear
ss = solid('br50_slidestop', fillet([(58.0, 90.0), (96.0, 92.5), (96.0, 97.5), (64.0, 99.0), (55.0, 98.0), (53.0, 92.0)], [1.0, 2.0, 2.0, 1.0, 2.0, 2.0]), 'yz', -14.4, -12.6, MST)
boolean(ss, [solid('br50_c_ssg%d' % i, [(54.5 + i * 1.6, 90.0), (55.2 + i * 1.6, 90.0), (55.2 + i * 1.6, 100), (54.5 + i * 1.6, 100)], 'yz', -16, -14.1) for i in range(4)])
finish(ss, 0.15, 2, 30)
lathe('br50_sspin', [(0.0, 13.0), (2.2, 13.0), (2.2, 13.5)], 'x', 24, MST, center=(93.0, 95.0))
# magazine release button, left side behind the trigger guard
mr = lathe('br50_magrel', [(0.0, -17.9), (3.6, -17.9), (3.9, -17.2), (3.9, -12.0)], 'x', 40, MST, center=(78.5, 75.5))
# barrel lock pin (left) and barrel lock lever (right) at the front of the frame
lathe('br50_lockpin', [(0.0, -13.9), (3.0, -13.9), (3.2, -13.3), (3.2, -12.0)], 'x', 32, MST, center=(160.0, 93.0))
bl = solid('br50_locklever', fillet([(152.0, 89.0), (168.0, 89.0), (168.0, 97.0), (152.0, 97.0)], 4.0), 'yz', 12.8, 14.4, MST)
boolean(bl, solid('br50_c_bl', fillet([(155.0, 92.4), (165.0, 92.4), (165.0, 93.6), (155.0, 93.6)], 0.5), 'yz', 14.1, 15), 'DIFFERENCE')
finish(bl, 0.15, 2, 30)
# trigger: wide, smooth face, single action
trig = solid('br50_trigger', fillet([(92.0, 86.0), (97.5, 86.0), (95.0, 76.0), (91.5, 67.5), (88.5, 66.5), (89.5, 75.5)], [0.5, 0.5, 4.0, 1.5, 1.0, 4.0]), 'yz', -3.4, 3.4, MST)
finish(trig, 0.2, 2, 30)
print('br50 gun ok', len(bpy.data.collections[COL].objects))
