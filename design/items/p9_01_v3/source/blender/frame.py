# P9 v3 polymer frame: upper body with beavertail, angled grip, trigger guard, rail, control pockets, pins.
import os
exec(open(os.path.join(BASE, 'lib.py')).read())

clear('frame')
MP = mat('p9_polymer')
MST = mat('p9_steel_machined')

W = 13.2
ZT, ZB = 99.0, 86.1
GW = 12.7                       # grip half width
GA = math.radians(-21.96)       # grip axis tilt
GT = (-38.96, 6.0)              # grip front-bottom corner (y, z)
GD = 40.06                      # grip depth front strap to back strap
M_GRIP = Matrix.Translation((0, GT[0], GT[1])) @ Matrix.Rotation(GA, 4, 'X')


def yf(z):
    """Grip front line."""
    return -5.1 - (90.0 - z) * 0.4031


def g2w(yl, zl):
    v = M_GRIP @ Vector((0, yl, zl))
    return (v.y, v.z)


# ---- A: upper body with the beavertail
pu = fillet([(117.6, ZT), (-60.0, ZT), (-71.8, 97.6), (-74.6, 94.2), (-71.0, 90.6), (-57.0, 88.6), (-46.0, ZB), (114.4, ZB), (117.6, 89.2)],
            [1.0, 5.0, 3.2, 3.2, 3.0, 4.0, 0, 1.2, 1.0])
A = solid('frame', pu, 'yz', -W, W, MP)
su = fillet([(-W, ZT), (W, ZT), (W, 88.3), (11.0, ZB), (-11.0, ZB), (-W, 88.3)], [0.3, 0.3, 1.6, 1.2, 1.2, 1.6])
boolean(A, solid('frame_c_su', su, 'xz', -100, 140), 'INTERSECT')
plan = fillet([(W + 2, 98), (W, 114.0), (W - 1.0, 122.0), (-W + 1.0, 122.0), (-W, 114.0), (-W - 2, 98),
               (-W - 2, -46), (-W, -54), (-10.4, -75.6), (10.4, -75.6), (W, -54), (W + 2, -46)],
              [0, 6, 0, 0, 6, 0, 0, 10, 3, 3, 10, 0])
boolean(A, solid('frame_c_plan', plan, 'xy', 60, 110), 'INTERSECT')

# ---- G: grip (built upright in grip space, then tilted), bottom edge chamfered, top clipped below the frame top
def grip_sec(inset):
    return rrect(-GW + inset, GW - inset, -GD + inset, -inset, [8.0 - inset, 8.0 - inset, 7.0 - inset, 7.0 - inset])
G = loft('frame_grip', [(grip_sec(0.9), 0.0), (grip_sec(0.0), 0.9), (grip_sec(0.0), 112.0)], 'xy', MP)
# loft profile is (x, y'); extrusion along z' -> apply the tilt
transform(G, M_GRIP)
boolean(G, solid('frame_c_gclip', rrect(-30, 30, -120, 60, 0), 'xy', -20, ZT - 0.2), 'INTERSECT')

# ---- T: trigger guard ring (cut only from itself so the grip front stays whole)
gout = fillet([(44.6, 87.0), (44.6, 61.3), (40.4, 57.1), (-6.0, 57.1), (yf(48.0), 48.0), (-30.0, 48.0), (-30.0, 87.0)],
              [0, 3.6, 3.6, 9.0, 0, 0, 0])
T = solid('frame_guard', gout, 'yz', -6.4, 6.4, MP)
gin = fillet([(41.4, 88.0), (41.4, 63.6), (38.0, 60.2), (yf(60.2) - 1.0, 60.2), (yf(88.0) - 1.0, 88.0)], [0, 3.0, 3.0, 7.0, 0])
boolean(T, solid('frame_c_gin', gin, 'yz', -10, 10), 'DIFFERENCE')

# ---- R: accessory rail under the dust cover
rs = fillet([(-8.4, 86.3), (8.4, 86.3), (8.4, 85.6), (10.6, 83.4), (7.8, 80.6), (-7.8, 80.6), (-10.6, 83.4), (-8.4, 85.6)],
            [0, 0, 0.4, 0.3, 0.3, 0.3, 0.3, 0.4])
R = solid('frame_rail', rs, 'xz', 50.0, 111.6, MP)
boolean(R, solid('frame_c_rplan', rrect(-12, 12, 50.0, 111.6, [0, 0, 2.0, 2.0]), 'xy', 70, 90), 'INTERSECT')

# ---- mag release bezels (raised pads) both sides
pads = []
for sx in (1, -1):
    x0, x1 = (11.0, 13.5) if sx > 0 else (-13.5, -11.0)
    pads.append(solid('frame_pad%d' % (sx > 0), rrect(-21.0, -10.6, 78.0, 87.6, 2.4), 'yz', x0, x1, MP))

boolean(A, [G, T, R] + pads, 'UNION')
frame = A

# ---- cuts
cuts = []
# magazine well: straight cavity with a flared mouth
def mw_sec(grow):
    return rrect(-9.9 - grow, 9.9 + grow, -37.4 - grow, -2.8 + grow, 3.0 + grow)
mw = loft('frame_c_mw', [(mw_sec(1.2), -5.0), (mw_sec(1.2), -0.2), (mw_sec(0.0), 1.6), (mw_sec(0.0), 76.0)], 'xy')
transform(mw, M_GRIP)
cuts.append(mw)
# rail cross slots
for i, yc in enumerate([61.5, 71.5, 81.5, 91.5, 101.5]):
    cuts.append(solid('frame_c_slot%d' % i, rrect(yc - 2.68, yc + 2.68, 79.0, 83.6, 0), 'yz', -12, 12))
# mag release pockets
for sx in (1, -1):
    x0, x1 = (12.0, 20.0) if sx > 0 else (-20.0, -12.0)
    cuts.append(solid('frame_c_mrp%d' % (sx > 0), rrect(-19.7, -11.9, 79.3, 86.3, 1.6), 'yz', x0, x1))
# slide stop recess on the left (lever + thumb pad + pivot boss, 0.25 clearance)
cuts.append(solid('frame_c_ssbar', rrect(-14.25, 15.75, 95.15, 97.85, 1.2), 'yz', -20, -12.4))
cuts.append(solid('frame_c_sspad', rrect(-20.75, -11.25, 93.55, 98.45, 1.4), 'yz', -20, -12.4))
cuts.append(solid('frame_c_ssboss', circle(14.5, 96.6, 2.05, 40), 'yz', -20, -12.4))
# takedown lever seats both sides
for sx in (1, -1):
    x0, x1 = (12.6, 20.0) if sx > 0 else (-20.0, -12.6)
    cuts.append(solid('frame_c_td%d' % (sx > 0), circle(38.6, 94.0, 3.35, 48), 'yz', x0, x1))
# pin holes both sides
PINS = [(4.8, 91.2), (25.6, 91.6), (-36.5, 93.2), (14.5, 96.6)]
for i, (py, pz) in enumerate(PINS):
    if i == 3:
        cuts.append(solid('frame_c_pin%d' % i, circle(py, pz, 1.55, 32), 'yz', 12.2, 20))
        continue
    cuts.append(solid('frame_c_pinr%d' % i, circle(py, pz, 1.55, 32), 'yz', 12.2, 20))
    cuts.append(solid('frame_c_pinl%d' % i, circle(py, pz, 1.55, 32), 'yz', -20, -12.2))
# serial plate pocket (right side of the dust cover)
cuts.append(solid('frame_c_serial', rrect(76.0, 96.0, 88.9, 92.4, 0.8), 'yz', 12.8, 20))
# recoil guide rod opening in the dust cover nose (on the recoil spring axis when the insides are built)
GRZ = 95.6 if INTERIOR else 93.0
cuts.append(solid('frame_c_rod', circle(0, GRZ, 2.9 if INTERIOR else 3.2, 48), 'xz', 114.8, 130))
if INTERIOR:
    # receiver channel between the slide rails (the barrel lug, the recoil spring and the magazine top sit in it) and
    # the magazine well carried up through its floor, narrower than the well so the rails stay whole
    cuts.append(solid('frame_c_chan', rrect(-7.7, 7.7, 92.9, 105.0, [1.0, 1.0, 0, 0]), 'xz', -58.0, 112.0))
    mwt = loft('frame_c_mwt', [(rrect(-7.7, 7.7, -36.0, -4.2, 2.0), 70.0), (rrect(-7.7, 7.7, -36.0, -4.2, 2.0), 112.0)], 'xy')
    transform(mwt, M_GRIP)
    cuts.append(mwt)
boolean(frame, cuts)

# grip side panels: recessed 0.5 mm, their faces carry the checkered texture material
MG = mat('p9_grip_texture')
panel = fillet([(-32.4, 8.0), (-6.8, 8.0), (-6.8, 61.0), (-13.5, 70.5), (-32.4, 70.5)], [3.5, 3.5, 6.0, 5.0, 3.5])
gp = []
for sx in (1, -1):
    x0, x1 = (GW - 0.5, 20.0) if sx > 0 else (-20.0, -GW + 0.5)
    c = solid('frame_c_gpanel%d' % (sx > 0), panel, 'yz', x0, x1, MG)
    transform(c, M_GRIP)
    gp.append(c)
boolean(frame, gp)
finish(frame, 0.32, 3, 30)

# ---- steel bits that live in the frame
parts = []
for i, (py, pz) in enumerate(PINS):
    sides = (1,) if i == 3 else (1, -1)
    for sx in sides:
        x0, x1 = (11.5, W - 0.12) if sx > 0 else (-W + 0.12, -11.5)
        if i == 3:
            x1 = W - 0.12
        p = solid('frame_pin_%d_%s' % (i, 'r' if sx > 0 else 'l'), circle(py, pz, 1.42, 32), 'yz', x0, x1, MST)
        finish(p, 0.12, 2, 30)
sp = solid('frame_serial_plate', rrect(76.1, 95.9, 89.0, 92.3, 0.72), 'yz', 12.0, W - 0.08, MST)
if not GAME:
    boolean(sp, text_solid('frame_c_sn', 'AF 04172', 2.0, (W - 0.08, 78.2, 89.9), '+x', 0.12, spacing=1.08))
finish(sp, 0.06, 2, 30)
rod = lathe('frame_guide_rod', [(0.0, 112.0), (2.5, 112.0), (2.5, 116.6), (2.2, 116.9), (0.9, 116.9), (0.8, 116.6), (0.0, 116.6)], 'y', 48, MST, center=(0, GRZ))
finish(rod, 0.0, 1, 35)
if INTERIOR:
    # recoil spring around the guide rod, from the barrel lug to the nose (coils are in the textures)
    if GAME:
        spring = lathe('frame_spring', [(0.0, 10.0), (2.1, 10.0), (2.4, 10.6), (2.4, 111.4), (2.1, 112.0), (0.0, 112.0)], 'y', 32, MST, center=(0, GRZ))
        finish(spring, 0.0, 1, 35)
    else:
        # bake source: a real wire helix (0.55 mm wire, 2.6 mm pitch) on a core the rays land on between the coils
        bm = bmesh.new()
        RW, RC, PITCH_S, Y0, Y1, NU, NV = 0.55, 1.85, 2.6, 10.6, 111.4, 18, 8
        turns = (Y1 - Y0) / PITCH_S
        steps = int(turns * NU)
        rings = []
        for k in range(steps + 1):
            a = 2 * math.pi * k / NU
            yc = Y0 + PITCH_S * k / NU
            cen = Vector((RC * math.cos(a), yc, GRZ + RC * math.sin(a)))
            rad = Vector((math.cos(a), 0, math.sin(a)))
            rings.append([bm.verts.new(cen + rad * (RW * math.cos(2 * math.pi * j / NV)) + Vector((0, RW * math.sin(2 * math.pi * j / NV), 0))) for j in range(NV)])
        for r0, r1 in zip(rings, rings[1:]):
            for j in range(NV):
                bm.faces.new((r0[j], r0[(j + 1) % NV], r1[(j + 1) % NV], r1[j]))
        bm.faces.new(rings[0])
        bm.faces.new(list(reversed(rings[-1])))
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        me = bpy.data.meshes.new('frame_spring')
        bm.to_mesh(me)
        bm.free()
        spring = bpy.data.objects.new('frame_spring', me)
        col().objects.link(spring)
        me.materials.append(MST)
        for p in me.polygons:
            p.use_smooth = True
        core = lathe('frame_spring_core', [(0.0, 10.0), (1.6, 10.0), (1.6, 112.0), (0.0, 112.0)], 'y', 24, MST, center=(0, GRZ))
        finish(core, 0.0, 1, 35)

print('frame faces', len(frame.data.polygons))
