# BR-51 concept: barrel, square gas block (gas cylinder + piston inside, spindle valve on the right, hex gas plug, lock
# ring, bayonet lug), front sight on its own clamp base (hooded post), closed-bottom five-slot flash hider and lock nut,
# front sling swivel under the nose cap. Gas system works as the M14's (cut-off / expansion piston under the barrel).
import os
exec(open(os.path.join(BASE, 'geo.py')).read())
for p in ('brl', 'gas_', 'fs_', 'fh_', 'nose_', 'c_'):
    clear(p)

PARK = mat('br51_park')
BRL = mat('br51_barrel')

brl = lathe('brl', [(0.0, 505), (14.5, 505), (14.5, 530), (13.2, 570), (11.5, 680), (10.4, 800), (9.6, 862), (9.6, 935),
                    (8.8, 945), (8.6, 1040), (7.6, 1046), (7.6, 1079), (0.0, 1079)], 'y', 64, BRL)
boolean(brl, cyl('c_bore', 3.9, 1060, 1090, 0, 0, 32))
if INTERIOR:
    boolean(brl, cyl('c_chamber', 6.2, 500, 548, 0, 0, 32))     # chamber mouth, seen past the open bolt
finish(brl, 0.25, 2, 35)

GZ = -17.0
# gas block: rounded-square section around the barrel and the gas cylinder below it
gsec = fillet([(-12.5, -28.0), (12.5, -28.0), (12.5, 4.0), (6.5, 11.2), (-6.5, 11.2), (-12.5, 4.0)], [4, 4, 4, 5, 5, 4], 8)
gb = solid('gas_block', gsec, 'xz', 862, 935, PARK)
boolean(gb, [cyl('c_gb_brl', 9.75, 850, 950, 0, 0, 48),
             box('c_gb_win_r', 11.3, 20, 878, 918, GZ + 1.5, -5.5, r=3.0), box('c_gb_win_l', -20, -11.3, 878, 918, GZ + 1.5, -5.5, r=3.0),
             box('c_gb_step', -20, 20, 920, 940, -40, -27.0)])
finish(gb, 0.45, 3, 35)
# lock ring at the front of the block (two wrench flats) and the hex gas plug
lr = cyl('gas_lock', 11.2, 935, 945, 0, 0, 56, PARK)
boolean(lr, [cyl('c_lr_brl', 8.9, 930, 950, 0, 0, 48), box('c_lr_f1', 9.6, 20, 930, 950, -20, 20), box('c_lr_f2', -20, -9.6, 930, 950, -20, 20)])
finish(lr, 0.3, 3, 35)
hexp = [(7.0 * math.cos(math.pi / 3 * k + math.pi / 6), GZ + 7.0 * math.sin(math.pi / 3 * k + math.pi / 6)) for k in range(6)]
plug = solid('gas_plug', hexp, 'xz', 935, 944, PARK)
boolean(plug, cyl('gas_plug_neck', 5.0, 930, 936, 0, GZ, 32, PARK), 'UNION')
finish(plug, 0.35, 2, 35)
# spindle valve on the right face (slot at 12 o'clock = ON)
sv = cyl_x('gas_valve', 4.3, 12.0, 15.4, 926, GZ + 2.0, 32, PARK)
boolean(sv, box('c_valve_slot', 14.6, 17, 925.3, 926.7, GZ - 2.6, GZ + 6.6))
finish(sv, 0.2, 2, 35)
# bayonet lug under the front of the block
lug = solid('gas_lug', fillet([(912, -27.5), (941, -27.5), (941, -33.5), (930, -36.5), (912, -33.0)], [0, 0, 1.5, 3, 1.5], 4),
            'yz', -4.2, 4.2, PARK)
finish(lug, 0.3, 2, 35)

# front sight: clamp base on the barrel, hooded post
fsb = cyl('fs_clamp', 12.0, 1018, 1042, 0, 0, 56, PARK)
boolean(fsb, box('fs_tower', -6.5, 6.5, 1020, 1040, 0, 16.5, PARK, 2.0), 'UNION')
hood = lathe('fs_hood', [(8.6, 1022), (8.6, 1040), (7.1, 1040), (7.1, 1022)], 'y', 64, PARK, center=(0, 24.0), caps=False)
boolean(fsb, hood, 'UNION')
boolean(fsb, [cyl('c_fs_brl', 8.65, 1010, 1050, 0, 0, 48), cyl('c_fs_hood_in', 7.1, 1015, 1050, 0, 24.0, 48)])
boolean(fsb, box('fs_lug', -5.5, 5.5, 1023, 1037, -17.5, -8.0, PARK, 1.5), 'UNION')
boolean(fsb, box('c_fs_slit', -0.8, 0.8, 1016, 1046, -20, -7.5))                 # clamp slit through the lug
finish(fsb, 0.35, 3, 35)
screw = cyl_x('fs_screw', 2.0, -7.0, 7.0, 1030, -13.5, 24, PARK)
boolean(screw, [cyl_x('fs_screw_head', 3.4, 5.5, 7.4, 1030, -13.5, 32, PARK), cyl_x('fs_screw_nut', 3.4, -7.4, -5.5, 1030, -13.5, 6, PARK)], 'UNION')
finish(screw, 0.15, 2, 35)
post = solid('fs_post', fillet([(1027.5, 15.5), (1034.5, 15.5), (1033.0, 27.0), (1029.0, 27.0)], [0, 0, 0.4, 0.4], 3), 'yz', -1.0, 1.0, PARK)
finish(post, 0.1, 2, 35)

# flash hider: lock nut, threaded collar, closed bottom, five slots
nut = cyl('fh_nut', 10.6, 1042.5, 1048.5, 0, 0, 64, PARK)
boolean(nut, [cyl('c_nut_brl', 7.7, 1035, 1055, 0, 0, 32)] + [box('c_nut_f%d' % k, 9.4, 20, 1040, 1052, -20, 20) for k in range(1)])
for k in range(1, 4):
    o = box('c_nut_g', 9.4, 20, 1040, 1052, -20, 20)
    o.data.transform(rot_y(90 * k))
    boolean(nut, o)
finish(nut, 0.25, 2, 35)
fh = lathe('fh_body', [(0.0, 1049), (11.0, 1049), (11.0, 1064), (10.6, 1070), (10.6, 1117), (11.2, 1119), (11.2, 1126), (0.0, 1126)], 'y', 72, PARK)
boolean(fh, [cyl('c_fh_brl', 7.7, 1040, 1080, 0, 0, 32), cyl('c_fh_bore', 6.6, 1078, 1135, 0, 0, 40)])
slots = []
for a in (30, 90, 150, 210, 330):
    s = box('c_slot', -1.7, 1.7, 1086, 1116, 3, 16, r=1.69)
    s.data.transform(rot_y(-(a - 90)))
    slots.append(s)
boolean(fh, slots)
for k in range(0 if GAME else 24):
    g = box('c_fh_knurl', -0.45, 0.45, 1052, 1062, 10.2, 13)
    g.data.transform(rot_y(15 * k))
    boolean(fh, g)
finish(fh, 0.3, 3, 35)

# front sling swivel under the nose cap
tab = box('nose_swivel_tab', -2.4, 2.4, 853, 862, -44.0, -36.5, PARK, 1.2)
finish(tab, 0.2, 2, 40)
ring_x('nose_swivel_loop', 8.0, 1.6, 857.5, -50.5, 40, PARK)
print('barrel ok')
