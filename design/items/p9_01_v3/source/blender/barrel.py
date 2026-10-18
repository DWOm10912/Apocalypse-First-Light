# P9 v3 barrel: hood in the ejection port, tube, threaded muzzle with crown and rifling.
import os
exec(open(os.path.join(BASE, 'lib.py')).read())

clear('barrel')
MB = mat('p9_barrel_steel')
ZC0 = 109.1

hood = solid('barrel_hood', rrect(-7.9, 7.9, 102.4, 118.82, [1.0, 1.0, 0.8, 0.8]), 'xz', 0.2, 27.4, MB)
hp = fillet([(0.2, 102.4), (24.6, 102.4), (27.4, 105.0), (27.4, 118.82), (0.2, 118.82)], [0.4, 1.2, 0, 0.6, 0.4])
boolean(hood, solid('barrel_c_hp', hp, 'yz', -10, 10), 'INTERSECT')
if INTERIOR:
    # 9x19 chamber mouth in the hood's rear face, and the feed ramp lug under it: the ramp runs from the magazine side
    # up into the chamber mouth (seen through the port when the slide is back)
    lug = solid('barrel_lug', [(-6.4, 96.2), (9.0, 96.2), (9.0, 103.0), (-6.4, 103.0)], 'yz', -4.6, 4.6)
    boolean(hood, lug, 'UNION')
    boolean(hood, [solid('barrel_c_ch', circle(0, 109.13, 5.0, 40), 'xz', -1.0, 14.0),
                   solid('barrel_c_ramp', [(-6.7, 95.6), (0.25, 104.25), (-20.0, 120.0), (-20.0, 95.6)], 'yz', -5.0, 5.0)])
finish(hood, 0.2, 3, 30)

prof = [(2.85, 20.0), (5.9, 20.0), (5.9, 121.4), (5.45, 121.85)]
if GAME:
    # thread ridges only in the bake
    t = 128.6
else:
    t = 122.2
    while t < 128.6:
        prof += [(5.45, t), (5.12, t + 0.36), (5.45, t + 0.72)]
        t += 0.8
prof += [(5.45, t + 0.15), (5.3, t + 0.35), (5.3, 129.45), (5.1, 129.7), (3.55, 129.7), (3.25, 129.45), (2.85, 129.2)]
tube = lathe('barrel_tube', prof, 'y', 96, MB, center=(0, ZC0), caps=False)
if not GAME:
    lands = []
    for k in range(6):
        g = solid('barrel_c_rf%d' % k, rrect(-0.5, 0.5, ZC0 + 2.7, ZC0 + 3.08, 0.0), 'xz', 112, 131)
        transform(g, rot_y(k * 60 + 10, (0, 0, ZC0)))
        lands.append(g)
    boolean(tube, lands)
finish(tube, 0.0, 1, 35)
print('barrel ok', len(tube.data.polygons))
