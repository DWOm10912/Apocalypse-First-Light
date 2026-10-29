# Offline model of DeerEntity.Move on a vanilla node path: MoveControl turns toward the next node (90 deg/tick), the deer
# caps it (7/12/16 deg) and multiplies the speed by clamp(1 - left/100, 0.3, 1); LivingEntity#travel: input zza = speed,
# moveRelative amount = speed (friction 0.6 -> factor 1), so the push is speed^2 along the heading; velocity *= 0.546.
# Nodes advance within 0.45 (bbWidth / 2) on x and z. Prints ground speed per tick around the corners.
import math, sys

def wrap(a):
    return (a + 180) % 360 - 180

def run(nodes, mod, cap, slow=True, label=""):
    x, z = nodes[0][0] + 0.5, nodes[0][1] + 0.5
    vx = vz = 0.0
    tx, tz = nodes[1][0] + 0.5 - x, nodes[1][1] + 0.5 - z
    yaw = math.degrees(math.atan2(tz, tx)) - 90
    i = 1
    speeds = []
    for t in range(400):
        if i >= len(nodes):
            break
        nx, nz = nodes[i][0] + 0.5, nodes[i][1] + 0.5
        if abs(x - nx) < 0.45 and abs(z - nz) < 0.45:
            i += 1
            continue
        want = math.degrees(math.atan2(nz - z, nx - x)) - 90
        d = wrap(want - yaw)
        turn = max(-90, min(90, d))
        s = mod * 0.25
        if abs(turn) > cap:
            left = abs(turn) - cap
            turn = math.copysign(cap, turn)
            if slow:
                s *= max(0.3, min(1, 1 - left / 100))
        yaw += turn
        r = math.radians(yaw)
        ax, az = -math.sin(r) * s * s, math.cos(r) * s * s
        vx, vz = (vx + ax), (vz + az)
        x += vx
        z += vz
        vx *= 0.546
        vz *= 0.546
        speeds.append(math.hypot(vx, vz) / 0.546 * 20)
    steady = max(speeds)
    lows = [v for v in speeds[10:]]
    dips = sum(1 for v in lows if v < 0.8 * steady)
    print(f"{label:34s} steady {steady:5.2f} m/s  min {min(lows):5.2f}  ticks under 80%: {dips:3d} of {len(lows)}  ticks {len(speeds)}")
    return speeds

# a forest path: straight, a 45 deg bend, straight, a 90 deg bend round a trunk, straight
path = [(0, 0), (0, 1), (0, 2), (0, 3), (0, 4), (1, 5), (2, 6), (3, 7), (4, 8), (4, 9), (4, 10), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10)]
for name, mod, cap in (("walk", 0.69, 7), ("trot", 1.14, 12), ("run", 1.82, 16)):
    run(path, mod, cap, True, name + " (now: cap + slow-down)")
    run(path, mod, cap, False, name + " (cap, no slow-down)")
    run(path, mod, 90, False, name + " (vanilla, no cap)")
if len(sys.argv) > 1:
    sp = run(path, 0.69, 7, True, "walk trace")
    print(" ".join(f"{v:.2f}" for v in sp))
