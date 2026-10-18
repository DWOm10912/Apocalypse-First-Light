# P9 v3 small parts: trigger, slide stop, takedown levers, magazine release, magazine.
import os
exec(open(os.path.join(BASE, 'lib.py')).read())

clear('part')
MP = mat('p9_polymer')
MST = mat('p9_steel_machined')
W = 13.2
GA = math.radians(-21.96)
M_GRIP = Matrix.Translation((0, -38.96, 6.0)) @ Matrix.Rotation(GA, 4, 'X')


def bez(p0, p1, p2, n=14):
    out = []
    for k in range(n + 1):
        t = k / n
        a = (1 - t) ** 2
        b = 2 * (1 - t) * t
        c = t * t
        out.append((a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1]))
    return out


# trigger: curved blade, finger face forward (+y)
front = bez((10.3, 87.6), (10.2, 70.5), (15.6, 66.3))
back = bez((13.8, 64.3), (5.8, 67.0), (6.3, 87.6))
outline = front + back
tr = solid('part_trigger', outline, 'yz', -3.0, 3.0, MP)
# round the finger face across its width
face = fillet([(-3.0, 60), (3.0, 60), (3.0, 90), (-3.0, 90)], 0)
finish(tr, 0.75, 4, 25)

# slide stop (left side): bar + serrated thumb pad + pivot boss
ss = solid('part_slidestop', rrect(-14.0, 15.5, 95.4, 97.6, 1.0), 'yz', -14.2, -12.5, MST)
boolean(ss, [solid('part_ss_pad', rrect(-20.5, -11.5, 93.8, 98.2, 1.2), 'yz', -14.7, -12.5),
             solid('part_ss_boss', circle(14.5, 96.6, 1.8, 40), 'yz', -14.4, -12.5)], 'UNION')
cuts = [solid('part_ss_g%d' % i, rrect(y - 0.35, y + 0.35, 94.5, 97.5, 0.3), 'yz', -16, -14.3) for i, y in enumerate([-19.0, -17.5, -16.0, -14.5, -13.0])]
cuts.append(solid('part_ss_pin', circle(14.5, 96.6, 0.85, 24), 'yz', -16, -14.25))
boolean(ss, cuts)
finish(ss, 0.18, 2, 30)

# takedown levers both sides: boss + paddle pointing rear-down
for sx in (1, -1):
    x0, x1 = (12.6, 14.1) if sx > 0 else (-14.1, -12.6)
    tdl = solid('part_takedown_%s' % ('r' if sx > 0 else 'l'), circle(38.6, 94.0, 3.1, 48), 'yz', x0, x1, MST)
    pad = solid('part_td_pad', rrect(31.2, 38.6, 92.5, 95.5, [1.4, 0, 0, 1.4]), 'yz', x0 if sx > 0 else x0 + 0.25, x1 - 0.25 if sx > 0 else x1, None)
    transform(pad, rot_x(22, (0, 38.6, 94.0)))
    boolean(tdl, pad, 'UNION')
    ring = solid('part_td_ring', circle(38.6, 94.0, 1.9, 40), 'yz', (x1 - 0.2) if sx > 0 else x0 - 1, (x1 + 1) if sx > 0 else x0 + 0.2)
    boolean(tdl, ring, 'DIFFERENCE')
    finish(tdl, 0.16, 2, 30)

# magazine release buttons both sides, three grip grooves
for sx in (1, -1):
    x0, x1 = (11.9, 14.1) if sx > 0 else (-14.1, -11.9)
    mr = solid('part_magrel_%s' % ('r' if sx > 0 else 'l'), rrect(-19.55, -12.05, 79.45, 86.15, 1.45), 'yz', x0, x1, MST)
    gx0, gx1 = ((x1 - 0.35), x1 + 1) if sx > 0 else (x0 - 1, x0 + 0.35)
    boolean(mr, [solid('part_mr_g%d' % i, rrect(-18.6, -13.0, z - 0.32, z + 0.32, 0.3), 'yz', gx0, gx1) for i, z in enumerate([81.0, 82.8, 84.6])])
    finish(mr, 0.18, 2, 30)

# magazine: body in the well, baseplate under the grip
def gs(x, y0, y1, r):
    return rrect(-x, x, y0, y1, r)
mb = loft('part_mag_body', [(gs(9.7, -37.2, -3.0, 2.8), -0.6), (gs(9.7, -37.2, -3.0, 2.8), 75.0)], 'xy', MP)
if INTERIOR:
    # open shell with feed lips over the rear two thirds (the game draws the top round under them, centred at z' 67.2)
    boolean(mb, loft('part_c_mag_in', [(gs(8.7, -36.2, -4.0, 1.9), 1.0), (gs(8.7, -36.2, -4.0, 1.9), 80.0)], 'xy'))
    lips = [solid('part_mag_lip%d' % (sx > 0), rrect(4.3, 9.0, -36.5, -13.0, [0, 0, 0, 0]) if sx > 0 else rrect(-9.0, -4.3, -36.5, -13.0, 0), 'xy', 73.6, 75.0)
            for sx in (1, -1)]
    boolean(mb, lips, 'UNION')
    fo = solid('part_mag_follower', rrect(-8.3, 8.3, -35.5, -4.7, 1.5), 'xy', 57.0, 61.6, MP)
    transform(fo, M_GRIP)
    finish(fo, 0.2, 2, 30)
transform(mb, M_GRIP)
finish(mb, 0.2, 2, 30)
bp = loft('part_mag_base', [(gs(11.5, -40.6, 0.8, [6.8, 6.8, 5.8, 5.8]), -5.0),
                            (gs(12.3, -41.4, 1.6, [7.6, 7.6, 6.6, 6.6]), -3.8),
                            (gs(12.3, -41.4, 1.6, [7.6, 7.6, 6.6, 6.6]), -0.55)], 'xy', MP)
transform(bp, M_GRIP)
finish(bp, 0.3, 3, 30)
print('parts ok')
