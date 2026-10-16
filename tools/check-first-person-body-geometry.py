"""First-person body V1 (docs/rendering/first_person_body_v1.md): where the body sits against the eye after the lean.

Blocks, measured from the render origin (the entity's feet; crouching, PlayerRenderer#getRenderOffset moves it 0.125 down).
f = forward of the eye's vertical, y = up, x = to the side. Vanilla 1.20.1 sizes:
- PlayerRenderer scales the model 0.9375; LivingEntityRenderer translates by -1.501, so the model's top (neck, y = 0 px)
  is 1.501 * 0.9375 over the origin and 1 model px = 0.9375 / 16 blocks. Model -z is the front, +y is down.
- HumanoidModel crouching: body.y 3.2, body.xRot 0.5, legs z 4.0 / y 12.2; eye 1.27 over the feet (standing 1.62).
- The lean (client/FirstPersonBody#lean) turns the whole model back about the origin so the torso's front-top edge sits
  GAP behind the eye's vertical. Looking down, the body also moves back NECK * sin(pitch) (the neck bend).
- Part 2 projects points to the screen (vertical FOV 70, 16:9; y -1 = bottom edge, +1 = top edge) and finds the pitch at
  which the body's arms first show (hanging, and swung forward when walking), against the first-person hand's slide
  (FirstPersonBody HAND_FROM 25 -> HAND_TO 45 deg). Run: python tools/check-first-person-body-geometry.py
"""
import math

PX = 0.9375 / 16
TOP = 1.501 * 0.9375
GAP = 0.03
NEAR = 0.05  # GameRenderer near plane


def lean_for(body_y, body_rot):
    """As FirstPersonBody#lean: degrees that put the torso's front-top corner GAP behind the eye's vertical."""
    y = body_y + 2 * math.sin(body_rot)
    z = -2 * math.cos(body_rot)
    forward, up = -z * PX, TOP - y * PX
    r, phi = math.hypot(forward, up), math.atan2(up, forward)
    return math.degrees(math.acos(-GAP / r) - phi)


def box(pivot, rot, lo, hi):
    """Corners of a model box (px, part-local lo..hi) under a part at pivot (px) tilted forward by rot (rad)."""
    out = []
    for x in (lo[0], hi[0]):
        for y in (lo[1], hi[1]):
            for z in (lo[2], hi[2]):
                ry = y * math.cos(rot) - z * math.sin(rot)
                rz = y * math.sin(rot) + z * math.cos(rot)
                out.append(((pivot[0] + x) * PX, TOP - (pivot[1] + ry) * PX, -(pivot[2] + rz) * PX))  # (x, up, forward)
    return out


def lean(p, a):
    x, y, f = p
    return (x, f * math.sin(a) + y * math.cos(a), f * math.cos(a) - y * math.sin(a))


for crouch in (False, True):
    body_y, rot = (3.2, 0.5) if crouch else (0.0, 0.0)
    leg_y, leg_z = (12.2, 4.0) if crouch else (12.0, 0.0)
    eye = (1.27 + 0.125) if crouch else 1.62  # over the render origin
    deg = lean_for(body_y, rot)
    a = math.radians(deg)
    parts = {
        'torso': box((0, body_y, 0), rot, (-4, 0, -2), (4, 12, 2)),
        'jacket': box((0, body_y, 0), rot, (-4.25, -0.25, -2.25), (4.25, 12.25, 2.25)),
        'right leg': box((-1.9, leg_y, leg_z), 0, (-2, 0, -2), (2, 12, 2)),
        'left leg': box((1.9, leg_y, leg_z), 0, (-2, 0, -2), (2, 12, 2)),
    }
    print(('crouch' if crouch else 'stand'), f"lean back {deg:.2f} deg, eye {eye:.3f} over the origin")
    nearest = 9.0
    for name, pts in parts.items():
        q = [lean(p, a) for p in pts]
        d = min(math.sqrt(x * x + (eye - y) ** 2 + f * f) for x, y, f in q)
        upper = max(y for _, y, _ in q)
        front_top = max(f for _, y, f in q if y > upper - 1e-6) if upper > eye - 0.5 else None
        nearest = min(nearest, d)
        extra = f"   top {eye - upper:.3f} under the eye, its front f {front_top:+.3f}" if front_top is not None else ""
        print(f"   {name:10s} nearest corner to the eye {d:.3f}{extra}")
    print(f"   nearest {nearest:.3f} = {nearest / NEAR:.1f} x the near plane")


# ---- part 2: on screen, standing ----
NECK = 0.13
EYE = 1.62
TV = math.tan(math.radians(35))
TH = TV * 16 / 9
LEAN = math.radians(lean_for(0.0, 0.0))


def screen(p_px, pitch, swing=0.0, pivot=None):
    """Model point (px: x right, y down from the neck, z front) to the screen; None behind the camera."""
    x, y, z = p_px
    if pivot is not None:   # an arm swung forward about its shoulder pivot
        s = math.radians(swing)
        dy, dz = y - pivot[1], z - pivot[2]
        y, z = pivot[1] + dy * math.cos(s) - dz * math.sin(s), pivot[2] + dy * math.sin(s) + dz * math.cos(s)
    up, f = TOP - y * PX, z * PX
    f, up = f * math.cos(LEAN) - up * math.sin(LEAN), f * math.sin(LEAN) + up * math.cos(LEAN)
    f -= NECK * math.sin(math.radians(max(pitch, 0.0)))
    v = up - EYE
    pr = math.radians(pitch)
    depth = v * -math.sin(pr) + f * math.cos(pr)
    if depth <= 0.02:
        return None
    return x * PX / depth / TH, (v * math.cos(pr) + f * math.sin(pr)) / depth / TV


print()
print('on screen (standing, neck bend on): y -1 bottom edge .. +1 top edge')
POINTS = {'torso top front edge': (0, 0, 2), 'chest rig flap top': (0, 5.42, 4.25), 'belt front': (0, 12, 2.2), 'toe': (2, 24, 2)}
for pitch in (45, 60, 75, 90):
    cells = []
    for name, pt in POINTS.items():
        r = screen(pt, pitch)
        cells.append(f"{name} {'behind' if r is None else f'{r[1]:+.2f}'}")
    print(f"   pitch {pitch}: " + ', '.join(cells))


def arm_on_screen(pitch, swing):
    for side in (-1, 1):
        px = -5 if side < 0 else 5
        for x in ((-3, 1) if side < 0 else (-1, 3)):
            for y in (-2, 10):
                for z in (-2, 2):
                    r = screen((px + x, 2 + y, z), pitch, swing, (px, 2, 0))
                    if r is not None and abs(r[0]) <= 1 and abs(r[1]) <= 1:
                        return True
    return False


print('body arms first on screen (the first-person hand is gone from 45 deg):')
for swing in (0, 20, 40, 57):  # vanilla walk 49, sprint 57; FirstPersonBody caps it at 20 while the arms show
    first = next((p / 10 for p in range(0, 901) if arm_on_screen(p / 10, swing)), None)
    print(f"   arm swung {swing:2d} deg forward: from pitch {first}")
