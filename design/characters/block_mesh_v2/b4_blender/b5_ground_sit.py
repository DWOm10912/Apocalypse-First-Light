# Ground-sit concepts for B5 (user 2026-10-26: "可以做3种坐姿，盘腿、两条腿弓起来那种，还有一种是我们现在的这种坐姿").
# Poses B4_rig by world-space joint targets: the body by FK angles, legs and arms by two-bone IK, each bone's world turn
# set as pb.matrix (rig at the origin). Units: px (Blender z up, front -y, the body's right +x), P m per px.
import bpy, math
from mathutils import Vector, Matrix, Quaternion
P = 0.9375 / 16
rig = bpy.data.objects['B4_rig']; PB = rig.pose.bones; BN = rig.data.bones
R = math.radians
THIGH, SHIN, UPPER, FORE = 6.0, 7.5, 4.5, 3.5
DOWN, FRONT, UP = Vector((0, 0, -1)), Vector((0, -1, 0)), Vector((0, 0, 1))

def basis(d, f):
    d = d.normalized(); f = (f - d * f.dot(d)).normalized()
    return Matrix((d, f, d.cross(f))).transposed()

def turn(d0, f0, d, f):
    """World rotation taking the rest pair (d0, f0) onto (d, f)."""
    return basis(d, f) @ basis(d0, f0).transposed()

def head_rest(n): return BN[n].head_local / P

class Pose:
    def __init__(self): self.rot, self.pos = {}, {}
    def put(self, n, rot, pos=None):
        b = BN[n]
        if pos is None:
            par = b.parent.name
            pos = self.pos[par] + self.rot[par] @ (head_rest(n) - head_rest(par))
        self.rot[n], self.pos[n] = rot, Vector(pos)
        return self.pos[n]
    def chain(self, n): return self.pos[n]

def ik(root, target, l1, l2, pole):
    d = target - root; L = d.length
    L = min(L, l1 + l2 - 0.01); u = d.normalized()
    a = (l1 * l1 - l2 * l2 + L * L) / (2 * L); h = math.sqrt(max(0.0, l1 * l1 - a * a))
    n = (pole - u * pole.dot(u)).normalized()
    mid = root + u * a + n * h
    end = root + u * L
    return mid, end

def leg(p, s, ankle, pole, toe, sole_down=None, front_thigh=None, front_shin=None):
    hip = p.pos['pelvis'] + p.rot['pelvis'] @ (head_rest('thigh_' + s) - head_rest('pelvis'))
    knee, ank = ik(hip, Vector(ankle), THIGH, SHIN, Vector(pole))
    td, sd = (knee - hip).normalized(), (ank - knee).normalized()
    # the knee's front faces the way it bends to (the pole side)
    ft = front_thigh or (Vector(pole) - td * Vector(pole).dot(td))
    p.put('thigh_' + s, turn(DOWN, FRONT, td, ft))
    fs = front_shin or ft
    p.put('shin_' + s, turn(DOWN, FRONT, sd, fs))
    sole = Vector(sole_down) if sole_down else DOWN
    p.put('foot_' + s, turn(DOWN, FRONT, sole, Vector(toe)))
    p.put('toes_' + s, p.rot['foot_' + s])
    return hip, knee, ank

def leg_fk(p, s, thigh_dir, shin_dir, toe, sole_down, front_thigh, front_shin):
    hip = p.pos['pelvis'] + p.rot['pelvis'] @ (head_rest('thigh_' + s) - head_rest('pelvis'))
    td, sd = Vector(thigh_dir).normalized(), Vector(shin_dir).normalized()
    p.put('thigh_' + s, turn(DOWN, FRONT, td, Vector(front_thigh)))
    p.put('shin_' + s, turn(DOWN, FRONT, sd, Vector(front_shin)))
    p.put('foot_' + s, turn(DOWN, FRONT, Vector(sole_down), Vector(toe)))
    p.put('toes_' + s, p.rot['foot_' + s])
    return hip, p.pos['shin_' + s], p.pos['foot_' + s]

def arm(p, s, wrist, pole, hand_dir, hand_front=None):
    # the shoulder pivot rides the chest (the clavicle's rest offset turned with the chest)
    c0 = p.pos['chest'] + p.rot['chest'] @ (head_rest('clavicle_' + s) - head_rest('chest'))
    sh = c0 + p.rot['chest'] @ (head_rest('upper_arm_' + s) - head_rest('clavicle_' + s))
    el, wr = ik(sh, Vector(wrist), UPPER, FORE, Vector(pole))
    ud, fd = (el - sh).normalized(), (wr - el).normalized()
    out = Vector((1 if s == 'r' else -1, 0, 0))
    fu = Vector(pole).cross(ud).cross(ud) * -1 if False else (FRONT - ud * FRONT.dot(ud))
    ua = turn(DOWN, FRONT, ud, fu if fu.length > 1e-3 else Vector(pole))
    # the clavicle (BlockMeshBody#clavicle): the arm's turn less its first 8 deg off the chest, about the shoulder pivot
    rel = (p.rot['chest'].transposed() @ ua).to_quaternion()
    ang = rel.angle; keep = Quaternion() if ang <= R(8) else Quaternion().slerp(rel, (ang - R(8)) / ang)
    cr = p.rot['chest'] @ keep.to_matrix()
    p.put('clavicle_' + s, cr, sh + cr @ (head_rest('clavicle_' + s) - head_rest('upper_arm_' + s)))
    p.put('upper_arm_' + s, ua, sh)
    # the forearm's front: away from the upper arm's bend (the inside of the elbow faces it)
    bend = (ud - fd * ud.dot(fd))
    ff = -bend if bend.length > 1e-3 else fu
    p.put('forearm_' + s, turn(DOWN, FRONT, fd, ff))
    hf = Vector(hand_front) if hand_front else ff
    p.put('hand_' + s, turn(DOWN, FRONT, Vector(hand_dir), hf))
    return sh, el, wr

def torso(p, hip_h, hip_y, tip, bend, head, yaw=0.0, head_yaw=0.0):
    """tip: the pelvis turned back (top behind), deg; bend: the chest forward on the pelvis (the spine half of it, as in
    game), head: forward on the chest, deg."""
    rx = lambda a: Matrix.Rotation(R(a), 3, 'X')
    rz = lambda a: Matrix.Rotation(R(a), 3, 'Z')
    p.put('root', Matrix.Identity(3), (0, 0, 0))
    pr = rz(yaw) @ rx(tip)
    p.put('pelvis', pr, (0, hip_y, hip_h))
    # a forward bend tips the top toward -y: rotation about +x by -angle
    p.put('spine', pr @ rx(-bend / 2))
    p.put('chest', pr @ rx(-bend), p.pos['spine'] + p.rot['spine'] @ (head_rest('chest') - head_rest('spine')))
    p.put('neck', p.rot['chest'])
    p.put('head', p.rot['chest'] @ rz(head_yaw) @ rx(-head))

def apply(p):
    order = ['root', 'pelvis', 'spine', 'chest', 'neck', 'head', 'clavicle_r', 'upper_arm_r', 'forearm_r', 'hand_r',
             'clavicle_l', 'upper_arm_l', 'forearm_l', 'hand_l', 'thigh_r', 'shin_r', 'foot_r', 'toes_r',
             'thigh_l', 'shin_l', 'foot_l', 'toes_l']
    for n in order:
        pb = PB[n]
        rot = p.rot[n] @ BN[n].matrix_local.to_3x3()
        m = rot.to_4x4(); m.translation = p.pos[n] * P
        pb.matrix = m
        bpy.context.view_layer.update()

def reset():
    for pb in PB:
        pb.matrix_basis = Matrix.Identity(4)
    bpy.context.view_layer.update()

def lowest(names=('B5_body', 'B5_jeans', 'B5_shoes', 'B5_jacket')):
    dg = bpy.context.evaluated_depsgraph_get(); out = {}
    for n in names:
        o = bpy.data.objects[n]
        if o.hide_render: continue
        e = o.evaluated_get(dg); me = e.to_mesh()
        out[n] = min((e.matrix_world @ v.co).z for v in me.vertices) / P
        e.to_mesh_clear()
    return out

# ------------------------------------------------------------------ the three sits
HIP = 2.4   # hip joint over the ground with the thighs flat: half the jeans leg (2.06) + the seat's give

def sit_out(p):
    """Now's sit: the left leg out straight (toes up), the right knee up, the right forearm over it, the left hand on the thigh."""
    torso(p, HIP - 0.3, 1.0, tip=8, bend=12, head=-8)
    leg_fk(p, 'l', (0.02, -1, 0.03), (0.02, -1, 0.03), toe=(0, -0.3, 1), sole_down=(0, -1, -0.3),
           front_thigh=UP, front_shin=UP)
    _, kr, _ = leg(p, 'r', (2.6, -7.2, 1.5), pole=(0.15, -0.4, 1), toe=(0.05, -1, 0))
    arm(p, 'r', kr + Vector((-0.3, -2.4, 3.3)), pole=(0.6, 1, -0.2), hand_dir=(0, -0.45, -1))
    arm(p, 'l', (-2.3, -4.6, HIP + 3.6), pole=(-0.6, 1, 0.3), hand_dir=(0, -1, -0.35), hand_front=(0, 0, -1))

def sit_cross(p):
    """Cross-legged, loose: knees wide and low, the right shin in front, the left foot tucked under it, hands on the knees."""
    torso(p, HIP - 0.1, 1.0, tip=2, bend=14, head=-10)
    # right: thigh out and forward, shin across in front, the foot on its outer edge (sole back toward the body)
    _, kr, _ = leg_fk(p, 'r', (4.2, -4.3, 0.1), (-0.74, -0.68, 0.05), toe=(-1, -0.25, 0), sole_down=(0, 1, 0.2),
                      front_thigh=(0.15, -0.1, 1), front_shin=(0.2, -0.25, 1))
    _, kl, _ = leg_fk(p, 'l', (-4.7, -3.7, 0.0), (0.97, -0.23, 0.03), toe=(1, -0.3, 0), sole_down=(0, 1, 0.3),
                      front_thigh=(-0.15, -0.1, 1), front_shin=(-0.1, -0.3, 1))
    arm(p, 'r', kr + Vector((-0.8, -0.6, 3.4)), pole=(1, 0.6, -0.2), hand_dir=(-0.25, -0.6, -1))
    arm(p, 'l', kl + Vector((0.8, -0.6, 3.4)), pole=(-1, 0.6, -0.2), hand_dir=(0.25, -0.6, -1))

def sit_knees(p):
    """Both knees up, feet flat ahead, the forearms over the knees, the hands hanging in front of them."""
    torso(p, HIP - 1.0, 1.0, tip=10, bend=18, head=-14)
    _, kr, _ = leg(p, 'r', (2.7, -7.6, 1.5), pole=(0.1, -0.4, 1), toe=(0.05, -1, 0))
    _, kl, _ = leg(p, 'l', (-2.7, -7.6, 1.5), pole=(-0.1, -0.4, 1), toe=(-0.05, -1, 0))
    arm(p, 'r', kr + Vector((-0.4, -2.2, 3.3)), pole=(0.5, 1, -0.2), hand_dir=(-0.1, -0.4, -1))
    arm(p, 'l', kl + Vector((0.4, -2.2, 3.3)), pole=(-0.5, 1, -0.2), hand_dir=(0.1, -0.4, -1))

SITS = {'out': sit_out, 'cross': sit_cross, 'knees': sit_knees}

def pose(name):
    reset(); p = Pose(); SITS[name](p); apply(p); return p

def sheet(fname, names=('out', 'cross', 'knees')):
    rows = []
    t = (0, -0.25, 0.42)
    for n in names:
        pose(n)
        rows.append([shot(-35, 15, dist=4.4, target=t, name='a'), shot(-90, 3, dist=4.4, target=t, name='b'),
                     shot(0, 12, dist=4.4, target=t, name='c'), shot(140, 22, dist=4.4, target=t, name='d'),
                     shot(-20, 70, dist=4.0, target=t, name='e')])
    grid(rows, fname)
    reset()
