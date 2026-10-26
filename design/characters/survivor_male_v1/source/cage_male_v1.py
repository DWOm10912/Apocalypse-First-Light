"""Male V1 body cage: a skin-modifier graph on the frozen H1-P1 bones (world mm), right side authored, left mirrored.

Each node: (key, position mm, radius x mm, radius y mm). Edges join keys. The skin modifier gives one quad ring per
node; one Catmull-Clark level makes 8-sided limbs, then the vertices are projected onto the H1-P1 body surface.
"""
import bpy
from mathutils import Vector

M = 0.001


def bone_pts(rig):
    b = rig.data.bones
    h = {n: Vector(bn.head_local) / M for n, bn in ((x.name, x) for x in b)}
    t = {n: Vector(bn.tail_local) / M for n, bn in ((x.name, x) for x in b)}
    return h, t


def graph(rig):
    h, t = bone_pts(rig)
    lerp = lambda a, b, s: a + (b - a) * s
    N, E = {}, []

    def node(k, p, rx, ry=None):
        N[k] = (Vector(p), rx, ry if ry is not None else rx)

    def chain(*ks):
        for a, b in zip(ks, ks[1:]):
            E.append((a, b))
    # trunk (centre line): crotch -> pelvis -> waist -> chest -> neck
    node('crotch', (0, 10, 840), 60, 60)
    node('pelvis', (0, 15, 935), 150, 95)
    node('belly', (0, 12, 1060), 140, 92)
    node('waist', (0, 12, 1160), 138, 95)
    node('chest', (0, 15, 1290), 155, 105)
    node('upchest', (0, 18, 1400), 150, 95)
    node('neck0', (0, 22, 1490), 60, 55)
    node('neck1', (0, 15, 1555), 55, 52)
    chain('crotch', 'pelvis', 'belly', 'waist', 'chest', 'upchest', 'neck0', 'neck1')
    for s, sg in (('r', -1), ('l', 1)):
        X = lambda p: Vector((p.x if sg < 0 else -p.x, p.y, p.z)) if False else p
        ua, fa, hd = h['upper_arm_' + s], h['forearm_' + s], h['hand_' + s]
        node('sh_' + s, lerp(h['clavicle_' + s], ua, 0.75) + Vector((0, 0, 10)), 55, 55)
        node('ua0_' + s, ua, 52, 52)
        node('ua1_' + s, lerp(ua, fa, 0.5), 46, 46)
        node('el_' + s, fa, 40, 40)
        node('fa1_' + s, lerp(fa, hd, 0.4), 40, 36)
        node('wr_' + s, lerp(fa, hd, 0.94), 30, 22)
        chain('upchest', 'sh_' + s, 'ua0_' + s, 'ua1_' + s, 'el_' + s, 'fa1_' + s, 'wr_' + s)
        th, sh, ft, to = h['thigh_' + s], h['shin_' + s], h['foot_' + s], h['toes_' + s]
        node('hip_' + s, th + Vector((sg * 10, 0, -40)), 85, 85)
        node('th1_' + s, lerp(th, sh, 0.5), 72, 72)
        node('kn_' + s, sh, 52, 52)
        node('sh1_' + s, lerp(sh, ft, 0.4), 50, 50)
        node('an_' + s, ft + Vector((0, 0, 10)), 36, 36)
        node('heel_' + s, ft + Vector((0, 45, -55)), 34, 30)
        node('ball_' + s, to + Vector((0, 0, 0)), 45, 22)
        node('toe_' + s, t['toes_' + s] + Vector((0, 0, 0)), 35, 18)
        chain('pelvis', 'hip_' + s, 'th1_' + s, 'kn_' + s, 'sh1_' + s, 'an_' + s, 'ball_' + s, 'toe_' + s)
        chain('an_' + s, 'heel_' + s)
        # the hands come from the H1-P1 hand (decimated), stitched on at the wrist (build_survivor_male_v1.hands)
    return N, E


def build(rig, name='cage_test'):
    N, E = graph(rig)
    keys = list(N)
    idx = {k: i for i, k in enumerate(keys)}
    me = bpy.data.meshes.new(name)
    me.from_pydata([tuple(N[k][0] * M) for k in keys], [(idx[a], idx[b]) for a, b in E], [])
    old = bpy.data.objects.get(name)
    if old:
        bpy.data.objects.remove(old)
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    sk = ob.modifiers.new('skin', 'SKIN')
    sk.use_smooth_shade = False
    sk.branch_smoothing = 0.0
    sk.use_x_symmetry = True
    for i, k in enumerate(keys):
        me.skin_vertices[0].data[i].radius = (N[k][1] * M, N[k][2] * M)
    me.skin_vertices[0].data[idx['pelvis']].use_root = True
    return ob
