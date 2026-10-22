# BR-51 concept: one-piece walnut stock (straight comb, semi-pistol grip with a steel grip cap, flat-sided forend with
# a finger groove each side), walnut upper handguard with vent slots, steel buttplate (lined face, storage trapdoor,
# hinged shoulder rest folded flat), steel nose cap, butt sling swivel.
import os
exec(open(os.path.join(BASE, 'geo.py')).read())
for p in ('stock', 'hg_', 'butt'):
    clear(p)

WOOD = mat('br51_walnut')
STEEL = mat('br51_park')
BUTT = mat('br51_buttplate')

# outline tables (y -> value), proportions from the measured side view, lines our own
Z_TOP = [(10, -38), (120, -35), (222, -31), (250, -33), (275, -28), (296, -18), (314, -8), (326, -5), (520, -5),
         (532, 3), (852, 3)]
Z_BOT = [(10, -166), (40, -162), (100, -146), (160, -127), (205, -115), (232, -114), (252, -118), (268, -116),
         (282, -104), (298, -86), (314, -70), (326, -58), (336, -50), (520, -50), (650, -45), (852, -38)]
W_BOT = [(10, 20.5), (120, 20.0), (205, 18.5), (245, 17.5), (270, 17.5), (300, 18.5), (336, 20.5), (520, 21.0),
         (600, 20.5), (852, 18.5)]
W_TOP = [(10, 17.5), (120, 17.0), (205, 16.0), (245, 15.5), (275, 16.0), (305, 19.0), (326, 21.0), (520, 21.0),
         (532, 20.0), (852, 18.0)]
N_BOT = [(10, 3.0), (205, 2.6), (260, 2.3), (310, 2.6), (336, 3.4), (520, 3.6), (852, 3.4)]
N_TOP = [(10, 2.5), (205, 2.4), (262, 2.4), (305, 4.0), (326, 6.0), (520, 6.0), (532, 5.0), (852, 5.0)]

secs = []
# game low poly: 20 mm apart, 10 mm through the grip curve (Vextris takes 16384 triangles for the whole rifle)
ys = (list(range(10, 200, 20)) + list(range(200, 340, 10)) + list(range(340, 852, 20)) + [852]) if GAME else list(range(10, 852, 4)) + [852]
for y in ys:
    lt = lerp_table
    secs.append((superellipse(lt(W_BOT, y), lt(W_TOP, y), interp(Z_BOT, y), interp(Z_TOP, y), lt(N_BOT, y), lt(N_TOP, y), 28 if GAME else 64), y))
st = loft('stock', secs, 'xz', WOOD)

# grip cap: the knob under the grip is cut flat along the line from the belly (y 232) to the grip front (y 282); the
# piece below the cut becomes the steel cap. The cut is boxed to the grip so the plane never reaches the butt.
GRIP_N = Vector((0, 10, -50)).normalized()
P0 = Vector((0, 232, -114)) + GRIP_N * 2.5


def grip_region(name):
    o = halfspace(name, P0, GRIP_N)
    boolean(o, box(name + '_lim', -60, 60, 200, 300, -250, 0), 'INTERSECT')
    return o


cap = dup(st, 'stock_gripcap')
boolean(cap, grip_region('c_cap'), 'INTERSECT')
cap.data.materials.clear()
cap.data.materials.append(STEEL)
for p in cap.data.polygons:
    p.material_index = 0
cut = [grip_region('c_grip')]
# magazine well, firing mechanism recess (the guard plate fills it flush), selector cutout, receiver inletting
cut.append(box('c_magwell', -13.8, 13.8, 437.0, 517.5, -80, 0))
cut.append(box('c_guard', -10.8, 10.8, 329.0, 436.5, -80, -42))
cut.append(box('c_sel', 12, 30, 334, 356, -12, 2, r=3))
cut.append(box('c_inlet', -14.3, 14.3, 323, 528, -16, 8))
# barrel channel at the nose, finger grooves along both forend sides
cut.append(cyl('c_barrel', 11.5, 800, 900, 0, 0, 48))
for s in (-1, 1):
    cut.append(capsule_y('c_groove', 7.0, 570, 760, s * 26.6, -20.0, 40))
boolean(st, cut)
finish(st, 0.6, 3, 40)
finish(cap, 0.35, 3, 40)

# upper handguard: walnut half shell over the barrel, four vent slots a side, steel clip at the rear
def hg_section(r_out, zc, thick, m=40):
    pts = [(r_out * math.cos(math.pi * k / m), zc + r_out * 0.92 * math.sin(math.pi * k / m)) for k in range(m + 1)]
    ri = r_out - thick
    pts += [(ri * math.cos(math.pi * k / m), zc + ri * 0.92 * math.sin(math.pi * k / m)) for k in range(m, -1, -1)]
    return pts


secs = []
NHG = 10 if GAME else 40
for i in range(NHG + 1):
    y = 532 + (852 - 532) * i / NHG
    secs.append((hg_section(16.6 - 1.2 * (y - 532) / 320, 3.2, 4.2, 12 if GAME else 40), y))
hg = loft('hg_wood', secs, 'xz', WOOD)
vents = []
for k in range(4):
    y = 600 + 50 * k
    v = solid('c_vent', rrect(y - 14, y + 14, 9.0, 13.0, 1.99, 6), 'yz', -30, 30)
    vents.append(v)
boolean(hg, vents)
finish(hg, 0.5, 3, 40)
MHC = 12 if GAME else 40
hc = loft('hg_clip', [(hg_section(17.6, 3.0, 2.0, MHC), 526.0), (hg_section(17.6, 3.0, 2.0, MHC), 536.0)], 'xz', STEEL)
finish(hc, 0.25, 2, 40)

# nose cap: steel cap over the stock tip and the handguard front
secs = []
for i in range(9):
    k = i / 8
    y = 848 + 18 * k
    secs.append((superellipse(19.6 - 1.4 * k, 18.6 - 1.4 * k, -39.0 + 3.0 * k, 18.6 - 1.0 * k, 3.4, 2.4, 28 if GAME else 64), y))
nc = loft('stock_nosecap', secs, 'xz', STEEL)
boolean(nc, cyl('c_nc_brl', 10.4, 830, 880, 0, 0, 48))
finish(nc, 0.5, 3, 35)

# buttplate: steel, follows the stock end, slightly rounded face, lined (material), trapdoor and folded shoulder rest
bp_prof = superellipse(interp(W_BOT, 10) + 0.5, interp(W_TOP, 10) + 0.5, -167.5, -36.6, 3.0, 2.5, 28 if GAME else 64)
bp = loft('butt_plate', [(bp_prof, -1.5), (bp_prof, 10.5)], 'xz', BUTT)
boolean(bp, lathe('c_shave', [(700, -40), (700, 40)], 'x', 256, center=(698.4, -102)), 'INTERSECT')
seams = []
for x0, x1, z0, z1, rr in ((-12, 12, -150, -100, 6), (-14.5, 14.5, -92, -46, 7)):
    o = box('c_seam', x0, x1, -10, 2.5, z0, z1, r=rr)
    boolean(o, box('c_seam_in', x0 + 0.6, x1 - 0.6, -11, 2.7, z0 + 0.6, z1 - 0.6, r=rr - 0.6))
    seams.append(o)
boolean(bp, seams)
finish(bp, 0.35, 3, 35)
# shoulder rest hinge knuckle across the top of the rest
hinge = cyl_x('butt_hinge', 2.6, -9, 9, 0.6, -44.5, 32, STEEL)
finish(hinge, 0.15, 2, 35)

# butt sling swivel: plate let into the belly, loop hanging below
zb = interp(Z_BOT, 100)
sp = box('stock_swivel_plate', -5.5, 5.5, 86, 114, zb - 0.6, zb + 2.0, STEEL, 2.6)
finish(sp, 0.25, 2, 40)
tab = box('stock_swivel_tab', -2.4, 2.4, 96, 104, zb - 6.0, zb, STEEL, 1.2)
finish(tab, 0.2, 2, 40)
ring_x('stock_swivel_loop', 9.0, 1.7, 100, zb - 13.0, 40, STEEL)
print('stock ok', len(st.data.polygons))
