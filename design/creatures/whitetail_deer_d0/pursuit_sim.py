# Pursuit steering for DeerEntity.Move: aim at a point LOOK blocks further along the path (not the next node), pass a node
# once within PASS of it, turn capped as before, and slow only when the aim is far off the heading (> 60 deg), easing
# the slow-down in and out (at most 0.1 of the factor a tick). Physics as turn_sim.py. Reports speed dips, the time to the
# end, and how far the body strays from the path's centre line (corner cutting).
import math

def wrap(a):
    return (a + 180) % 360 - 180

def seg_dist(px, pz, ax, az, bx, bz):
    dx, dz = bx - ax, bz - az
    L2 = dx * dx + dz * dz
    t = 0 if L2 == 0 else max(0, min(1, ((px - ax) * dx + (pz - az) * dz) / L2))
    return math.hypot(px - ax - t * dx, pz - az - t * dz)

def run(nodes, mod, cap, look, pas, label):
    pts = [(a + 0.5, b + 0.5) for a, b in nodes]
    x, z = pts[0]
    vx = vz = 0.0
    yaw = math.degrees(math.atan2(pts[1][1] - z, pts[1][0] - x)) - 90
    i = 1
    factor = 1.0
    speeds, stray = [], 0.0
    for t in range(600):
        while i < len(pts) - 1 and math.hypot(x - pts[i][0], z - pts[i][1]) < pas:
            i += 1
        if i == len(pts) - 1 and math.hypot(x - pts[i][0], z - pts[i][1]) < 0.45:
            break
        # walk LOOK blocks along the path from the deer's place
        left = look
        ax, az = x, z
        j = i
        tx, tz = pts[j]
        while True:
            d = math.hypot(pts[j][0] - ax, pts[j][1] - az)
            if d >= left or j == len(pts) - 1:
                f = min(1, left / d) if d > 0 else 0
                tx, tz = ax + (pts[j][0] - ax) * f, az + (pts[j][1] - az) * f
                break
            left -= d
            ax, az = pts[j]
            j += 1
        want = math.degrees(math.atan2(tz - z, tx - x)) - 90
        d = wrap(want - yaw)
        off = abs(d)
        turn = max(-cap, min(cap, d))
        yaw += turn
        goal = 1.0 if off <= 60 else max(0.45, 1 - (off - 60) / 120)
        factor += max(-0.1, min(0.1, goal - factor))
        s = mod * 0.25 * factor
        r = math.radians(yaw)
        vx += -math.sin(r) * s * s
        vz += math.cos(r) * s * s
        x += vx
        z += vz
        vx *= 0.546
        vz *= 0.546
        speeds.append(math.hypot(vx, vz) / 0.546 * 20)
        stray = max(stray, min(seg_dist(x, z, *pts[k], *pts[k + 1]) for k in range(len(pts) - 1)))
    steady = max(speeds)
    lows = speeds[10:]
    dips = sum(1 for v in lows if v < 0.8 * steady)
    print(f"{label:28s} steady {steady:5.2f}  min {min(lows):5.2f}  under 80%: {dips:3d}  ticks {len(speeds):3d}  strays {stray:4.2f} blocks")

path = [(0, 0), (0, 1), (0, 2), (0, 3), (0, 4), (1, 5), (2, 6), (3, 7), (4, 8), (4, 9), (4, 10), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10)]
zig = [(0, 0), (0, 1), (0, 2), (1, 3), (1, 4), (0, 5), (0, 6), (1, 7), (2, 7), (2, 8), (2, 9), (3, 10), (4, 10), (4, 11), (4, 12)]
hook = [(0, 0), (0, 1), (0, 2), (0, 3), (0, 4), (1, 4), (2, 4), (2, 3), (2, 2), (2, 1), (2, 0)]   # a U-turn round an obstacle
for name, p in (("bends", path), ("zigzag", zig), ("U-turn", hook)):
    for g, mod, cap, look, pas in (("walk", 0.69, 7, 1.2, 0.8), ("trot", 1.14, 12, 1.8, 1.2), ("run", 1.82, 16, 3.0, 2.0)):
        run(p, mod, cap, look, pas, f"{name} {g}")
