# BR-51 concept: receiver, bolt, op rod rear (hump + handle), rear sight, selector, bolt lock, firing mechanism
# (guard plate, trigger guard, trigger, safety, magazine latch). Parkerized steel.
# Mechanism as the M14 (TM 9-1005-223-10): the op rod handle rides the track on the receiver's right side; the camming
# hump of the op rod sits over the bolt roller (front right lug) and turns the bolt; the bolt lock is on the left; the
# selector on the right rear; the safety at the front inside the trigger guard (rear = safe); the magazine latch
# behind the magazine; stripper clip guide on the bridge. The outer shapes are our own.
import os
exec(open(os.path.join(BASE, 'geo.py')).read())
for p in ('rcv', 'bolt', 'oprod', 'rs_', 'fcg_', 'c_'):
    clear(p)

PARK = mat('br51_park')
WORN = mat('br51_steel_worn')
KNURL = mat('br51_knurl')

# --- receiver: slab sides, flat top ---
sec = fillet([(-13, -16), (13, -16), (14, -8), (14, 5), (12.5, 11), (-12.5, 11), (-14, 5), (-14, -8)],
             [1.0, 1.0, 1.0, 3.0, 3.0, 3.0, 3.0, 1.0], 8)
rc = solid('rcv', sec, 'xz', 325, 488, PARK)
parts = []
parts.append(solid('rcv_heel', fillet([(-12, 0), (12, 0), (12, 16), (-12, 16)], [0, 0, 3, 3], 6), 'xz', 325, 382, PARK))
parts.append(solid('rcv_bridge', fillet([(-13.6, 0), (13.6, 0), (12.6, 15), (7.0, 21), (-7.0, 21), (-12.6, 15)],
                                        [0, 0, 3, 3, 3, 3], 6), 'xz', 382, 430, PARK))
parts.append(solid('rcv_guide', fillet([(-9.0, 18), (9.0, 18), (9.0, 26), (-9.0, 26)], [0, 0, 1.5, 1.5], 4), 'xz', 392, 420, PARK))
parts.append(cyl('rcv_ring', 15.6, 487, 522, 0, 0, 64, PARK))
parts.append(solid('rcv_track', fillet([(11, 1.0), (17.5, 1.0), (17.5, 9.6), (11, 9.6)], [0, 1.2, 1.2, 0], 4), 'xz', 340, 488, PARK))
boolean(rc, parts, 'UNION')
cut = []
cut.append(box('c_top', -10.6, 10.6, 430, 487.5, 2.0, 40, r=2.0))           # open top over the bolt
cut.append(box('c_magwell', -12.9, 12.9, 437.5, 517, -40, 2, r=1.5))        # magazine and feed opening from below
cut.append(cyl('c_boltway', 10.2, 410, 520, 0, 0, 48))                     # bolt channel
cut.append(box('c_clipslot', -9.5, 9.5, 404, 409, 20.5, 40))               # stripper clip slot across the guide
cut.append(box('c_camslot', 15.6, 25, 350, 480, 3.4, 7.2, r=1.7))          # op rod guide lug slot in the track
cut.append(box('c_flat_l', -20, -13.4, 345, 418, -7, 3.5, r=2.0))          # machined flat, left side
cut.append(box('c_lockpocket', -20, -12.6, 432, 449, -4, 8, r=1.5))         # bolt lock pocket, left side
# front ring wrench flats and a groove near its front; op rod disassembly notch in the track (the guide lug drops out
# here); a milled line along both sides; lightening windows on the bridge sides; lead-in chamfer over the clip slot
cut.append(box('c_ring_flat_r', 14.6, 30, 489, 521, -8.5, 8.5))
cut.append(box('c_ring_flat_l', -30, -14.6, 489, 521, -8.5, 8.5))
g = cyl('c_ring_groove', 17.0, 512.6, 513.8, 0, 0, 64)
boolean(g, cyl('c_ring_groove_in', 15.0, 510, 516, 0, 0, 64))
cut.append(g)
cut.append(box('c_track_notch', 15.6, 25, 471, 479, -1, 3.6, r=1.0))
for x0, x1 in ((13.4, 20), (-20, -13.4)):
    cut.append(box('c_sideline', x0, x1, 338, 486, -6.2, -5.0))
for x0, x1 in ((12.4, 20), (-20, -12.4)):
    cut.append(solid('c_bridge_win', rrect(388, 426, 10.6, 15.4, 1.8, 6), 'yz', x0, x1))
cut.append(solid('c_guide_ch', [(401.0, 26.2), (412.5, 26.2), (409.0, 23.6), (404.0, 23.6)], 'yz', -10, 10))
# left flat: scope mount hole (closed by a plug) and the markings
cut.append(cyl_x('c_scope_hole', 3.4, -20, -11.6, 351.0, -1.5, 40))
if not GAME:
    cut.append(text_solid('rcv_c_m1', 'RIFLE 7.62 MM BR-51', 2.2, (-13.4, 414.0, 0.2), '-x', 0.15, spacing=1.05))
    cut.append(text_solid('rcv_c_m2', 'KESSLER ARSENAL', 2.2, (-13.4, 414.0, -3.6), '-x', 0.15, spacing=1.05))
    cut.append(text_solid('rcv_c_m3', '0412736', 2.0, (-13.4, 381.0, -3.6), '-x', 0.15, spacing=1.1))
boolean(rc, cut)
hr = lathe('c_heelr', [(70, -40), (70, 50)], 'z', 160, center=(0, 395))
boolean(hr, box('c_heelkeep', -40, 40, 395, 700, -60, 60), 'UNION')
boolean(rc, hr, 'INTERSECT')
finish(rc, 0.45, 3, 35)
plug = cyl_x('rcv_scope_plug', 3.1, -12.9, -11.6, 351.0, -1.5, 40, WORN)
boolean(plug, cyl_x('c_hex', 1.5, -14, -12.2, 351.0, -1.5, 6))
finish(plug, 0.15, 2, 35)
# clip guide: two rivets through the guide blocks, a flat spring on the slot floor
for yr in (397.0, 415.0):
    riv = cyl_x('rcv_guide_rivet', 1.6, -9.7, 9.7, yr, 22.0, 24, WORN)
    finish(riv, 0.25, 2, 35)
spr = box('rcv_guide_spring', -2.2, 2.2, 404.6, 408.4, 20.5, 21.3, WORN, 0.4)
finish(spr, 0.1, 2, 35)

# --- bolt (in battery, face at y 520): body, top rib, right lug with roller, extractor ---
bolt = cyl('bolt', 9.8, 428, 520, 0, 0, 48, WORN)
boolean(bolt, [box('bolt_rib', -3.5, 3.5, 440, 515, 6, 11.0, WORN, 1.4), box('bolt_lug_r', 4, 10.5, 490, 506, 3, 9.5, WORN, 1.2)], 'UNION')
boolean(bolt, box('c_bolt_groove', -1.2, 1.2, 446, 482, 10.0, 12, r=1.0))
finish(bolt, 0.3, 3, 35)
roller = cyl_x('bolt_roller', 3.4, 9.0, 13.6, 498, 7.0, 32, WORN)
finish(roller, 0.2, 2, 35)
ext = box('bolt_extractor', 8.0, 10.6, 505, 519, -3, 3, WORN, 1.0)
finish(ext, 0.2, 2, 35)

# --- op rod rear: hump over the roller, handle out to the right (the rod runs forward to the piston) ---
hump = solid('oprod', fillet([(488, 4.0), (514, 4.0), (514, 11.5), (506, 15.0), (494, 15.0), (488, 11.5)], [0, 0, 2.5, 3, 3, 2.5], 5),
             'yz', 6.5, 18.0, PARK)
boolean(hump, cyl_x('c_hump_roll', 4.2, 0, 14.5, 498, 7.0, 32))           # camming recess around the roller
hand = solid('oprod_handle', fillet([(17.0, 486), (25.0, 488), (30.5, 494), (31.0, 506), (27.5, 513), (17.0, 515)],
                                   [0, 3, 3.5, 3.5, 3, 0], 6), 'xy', -2.5, 10.5, PARK)
for k in range(0 if GAME else 4):
    boolean(hand, box('c_handgroove', 29.0, 40, 494.5 + 3.2 * k, 495.9 + 3.2 * k, -5, 15))
boolean(hump, hand, 'UNION')
boolean(hump, cyl('oprod_rear', 4.8, 505, 530, 13.0, -2.5, 24, PARK), 'UNION')
finish(hump, 0.35, 3, 35)
rod = cyl('oprod_rod', 4.6, 528, 872, 12.0, -4.5, 24, PARK)
finish(rod, 0.0, 1, 35)

# --- rear sight: base, hooded ears (our tunnel hood), aperture slide, elevation pinion left, windage knob right ---
rs = box('rs_base', -10.0, 10.0, 336, 380, 14.5, 21.0, PARK, 1.5)
hood = []
for lo, hi in ((-10.0, -7.4), (7.4, 10.0)):
    hood.append(solid('rs_ear', fillet([(338, 20), (378, 20), (376, 33), (370, 37.5), (346, 37.5), (340, 33)], [0, 0, 4, 4, 4, 4], 6),
                      'yz', lo, hi, PARK))
hood.append(solid('rs_roof', fillet([(-10.0, 34.6), (10.0, 34.6), (10.0, 37.5), (-10.0, 37.5)], [0, 0, 1.2, 1.2], 4), 'xz', 347, 369, PARK))
hood.append(box('rs_skirt', -11.0, 11.0, 338, 378, 14.5, 17.2, PARK, 1.0))
boolean(rs, hood, 'UNION')
finish(rs, 0.3, 3, 35)
ap = box('rs_aperture', -5.6, 5.6, 356.0, 358.6, 19.5, 33.0, PARK, 1.0)
boolean(ap, cyl('c_peep', 1.1, 350, 365, 0, 27.0, 24))
boss = cyl('rs_aperture_boss', 3.4, 355.0, 359.6, 0, 27.0, 32, PARK)
boolean(boss, cyl('c_peep2', 1.1, 350, 365, 0, 27.0, 24))
finish(ap, 0.15, 2, 35)
finish(boss, 0.15, 2, 35)
# knobs 0.6 mm off the skirt (1.6 off the ears), their axles into the ears (user 2026-10-21: the 3.6 mm gap read as a hole)
ek = cyl_x('rs_elev', 9.6, -19.0, -11.6, 362, 21.0, 72, KNURL)
boolean(ek, cyl_x('rs_elev_hub', 4.2, -20.6, -9.6, 362, 21.0, 32, PARK), 'UNION')
boolean(ek, box('c_elev_slot', -21.5, -20.0, 361.4, 362.6, 17.5, 24.5))
finish(ek, 0.4, 3, 30)
wk = cyl_x('rs_wind', 7.0, 11.6, 17.6, 362, 21.0, 64, KNURL)
boolean(wk, cyl_x('rs_wind_hub', 3.4, 9.6, 18.2, 362, 21.0, 32, PARK), 'UNION')
finish(wk, 0.35, 3, 30)
nut = cyl_x('rs_wind_nut', 3.0, 17.6, 19.8, 362, 21.0, 6, PARK)
boolean(nut, cyl_x('rs_wind_screw', 1.5, 19.0, 20.4, 362, 21.0, 24, PARK), 'UNION')
finish(nut, 0.2, 2, 35)

# --- selector (right rear; lever up = semi, the A face turned to the shooter = auto) ---
sel = cyl_x('rcv_selector', 5.4, 13.6, 17.0, 345, -1.0, 40, PARK)
boolean(sel, box('rcv_sel_lever', 13.6, 17.0, 341.4, 348.6, -1.0, 10.5, PARK, 1.6), 'UNION')
if not GAME:
    boolean(sel, text_solid('rcv_c_A', 'A', 2.6, (17.0, 345.0, -5.0), '+x', 0.12, align='CENTER'))
finish(sel, 0.3, 2, 35)

# --- bolt lock (left side, ahead of the bridge): thin plate on a pivot pin, finger pad with teeth ---
bl = solid('rcv_boltlock', fillet([(434, 1.5), (441, -2.5), (448.5, -2.5), (448.5, 2.5), (445, 7.5), (436, 7.5)], [1, 2, 3, 3, 2, 1], 4),
           'yz', -14.4, -12.8, PARK)
if not GAME:
    boolean(bl, [box('rcv_bl_tooth', -15.2, -14.2, 436.5 + 2.2 * k, 437.5 + 2.2 * k, 2.5, 7.0, PARK, 0.3) for k in range(4)], 'UNION')
finish(bl, 0.2, 2, 35)
blp = cyl_x('rcv_bl_pin', 2.0, -15.0, -12.6, 446.0, 0.0, 32, WORN)
finish(blp, 0.2, 2, 35)

# --- firing mechanism: guard plate flush with the belly, trigger guard, trigger, safety, magazine latch ---
plate = box('fcg_plate', -10.6, 10.6, 329.2, 436.3, -50.4, -42, PARK, 1.2)
finish(plate, 0.3, 3, 35)
loop_o = fillet([(342, -50.0), (422, -50.0), (422, -62), (415, -93), (352, -93), (344, -80), (342, -62)], [0, 0, 5, 9, 14, 9, 4], 8)
loop_i = fillet([(350, -44), (414, -44), (414, -60), (409, -87), (356, -87), (350, -76), (350, -58)], [0, 0, 4, 6, 10, 6, 3], 8)
gl = solid('fcg_guard', loop_o, 'yz', -6.0, 6.0, PARK)
boolean(gl, solid('c_guard_in', loop_i, 'yz', -10, 10))
finish(gl, 0.6, 3, 35)
gpin = cyl_x('fcg_guard_pin', 2.2, -7.0, 7.0, 416.5, -55.0, 24, WORN)     # the guard swings open on this pin
finish(gpin, 0.25, 2, 35)
front = [(381 - 6 * (t / 12) ** 2, -50 - 26 * t / 12) for t in range(13)]
back = [(front[i][0] - (5.5 - 1.8 * i / 12), front[i][1]) for i in range(13)][::-1]
tb = solid('fcg_trigger', fillet(front + back, 0.8, 4), 'yz', -3.0, 3.0, WORN)
finish(tb, 0.3, 3, 35)
safe = solid('fcg_safety', fillet([(406, -48), (413.5, -48), (413.5, -64), (408, -66), (406, -61)], [0, 0, 1.5, 1.5, 1], 4),
             'yz', -5.5, 5.5, PARK)
boolean(safe, cyl_x('c_safe_hole', 2.2, -10, 10, 409.8, -59.0, 24))
if not GAME:
    boolean(safe, [box('fcg_safe_tooth', -5.0, 5.0, 405.2, 406.4, -53.0 - 2.6 * k, -52.0 - 2.6 * k, PARK, 0.3) for k in range(3)], 'UNION')
finish(safe, 0.25, 2, 35)
latch = solid('fcg_latch', fillet([(427, -48), (436, -48), (436.5, -58), (429, -62.5), (426.5, -60)], [0, 0, 1.5, 2, 1.5], 4),
              'yz', -7.5, 7.5, PARK)
for k in range(0 if GAME else 3):
    boolean(latch, box('c_latch_line', -9, 9, 428.5 + 2.4 * k, 429.3 + 2.4 * k, -64, -56))
finish(latch, 0.25, 2, 35)
lp = cyl_x('fcg_latch_pin', 1.8, -8.3, 8.3, 431.0, -52.0, 24, WORN)
finish(lp, 0.2, 2, 35)
print('receiver ok', len(rc.data.polygons))
