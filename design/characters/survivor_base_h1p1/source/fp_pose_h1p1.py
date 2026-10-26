"""H1-P1 first-person check: put the P9-01 base static grip V1 (grip_pose_h1p1.json) on the H1-P1 body's two hands
through the arm IK, and render what the game's hand pass would show (camera at the eye, the pose's view fov).

Run inside survivor_base_h1p1.blend:
    exec(open(r".../source/fp_pose_h1p1.py", encoding="utf-8").read())
    rep = pose('ads')             # place gun + IK targets + fingers; returns reach / wrist error / elbow flex
    fp_shots(['hip', 'ads'])      # renders/fp_h1p1/<pose>_fp.png (+ a side view); ('raise', u) for a raise frame
    tp_shots()                    # third-person review renders, <pose>_tp_side / _tp_front.png
    export_fp()                   # bake CLIPS, write the GeckoLib geo / animation / skin and the check reference

The grip itself is fixed data (user approved as V1 2026-10-23); this script only moves the gun as a whole and lets
the arm IK follow. Round 2 (2026-10-23, user: natural stance, ADS framing, one hip-fire / ADS logic): a whole-body
stance (STANCE: hips back, forward lean, soft knees, left foot a little ahead), per-side shoulders and elbows, one
raise from hip to ADS (('raise', u) blends the two specs and every frame is a full IK solve), and a first-person
view point per pose ('view': metres behind the eye and the vertical field of view). Round 2b (same day, user: the
first-person arms were pushed too long and too far): first person is fitted on its own, compact and softly bent;
the third-person poses (tp_*) stay as the body reference and are only rendered for review. Round 3 (2026-10-24):
grip round 3 (support hand only, compared with real bare-hand photos; grip_pose_h1p1_v1.json keeps V1), every wrist
inside the anatomical 45 deg ulnar, hip fire upright with the bore parallel to the view, narrower view fovs. Round 4
(2026-10-24, composed in the game's first-person camera, user picked hip B + ADS A): first person has its own grip
(POSE_FP: support hand lower, thumbs stacked; firing hand root unchanged), hip fire further to the lower right, ADS
shoulders forward so the forearms close in symmetrically.

Gun placement per pose: the rear-sight notch centre goes to eye + (right, forward, up) metres, then the gun turns
about it by yaw (+ = muzzle to the left), pitch (+ = muzzle up) and cant (+ = top to the left).

Body frame: metres, Z up, the body faces -Y, its right side is -X. Gun frame: P9-01 V3 geo px (1 px = 19 mm), +x the
gun's right, +y the muzzle, +z up.
"""
import bpy, builtins, json, math, os
import numpy as np
from mathutils import Matrix, Vector

HERE = r"D:/Minecraft Modding/Apocalypse First Light/design/characters/survivor_base_h1p1/source"
P0 = r"D:/Minecraft Modding/Apocalypse First Light/design/items/mesh_hand_p0/source/mesh_hand_p0.blend"
POSE = HERE + "/grip_pose_h1p1.json"
POSE_FP = HERE + "/grip_pose_h1p1_fp.json"   # first-person grip (round 4), used by every spec with a 'view'
OUT = os.path.normpath(HERE + "/../renders/fp_h1p1")

PX = 0.019                                  # metres per gun px
EYE = Vector((0.0, -0.088, 1.655))          # body eye point (between the eyes), rest pose
REAR = Vector((0.0, -3.33, 6.04))           # rear-sight notch centre, gun px
FRONT = Vector((0.0, 6.0, 6.13))            # front-sight dot, gun px
WRIST_R = Vector((0.65, -6.69, 2.74))       # firing wrist in the grip, gun px: the recoil pivot
CLAVICLE = 0.18                             # m, sternal end to shoulder (protraction arc radius)
S4 = Matrix.Diagonal((-1, 1, 1, 1))
RG = Matrix.Rotation(math.pi, 4, 'Z')       # gun px axes -> body axes when aimed straight ahead
FOV_Y = 70.0                                # vanilla hand pass (GameRenderer.renderItemInHand, fov setting off)
WRIST_ULNAR = 45.0                          # build_survivor_base_h1p1.py wrist_limit: anatomical ulnar deviation
ARM_BONES = ('clavicle', 'upper_arm', 'forearm', 'forearm_twist', 'hand', 'thumb', 'index', 'middle', 'ring', 'pinky')

# Whole-body stance, shared by every pose and by first and third person. Degrees about the armature X axis: + tips
# the far end forward for the trunk (spine, chest); legs (right, left): thigh - = knee forward, shin + = foot back,
# the left foot about 8 cm ahead. The feet are turned flat again, the hips go back hips_back m and drop until the
# lower foot is on the floor.
STANCE = dict(hips_back=0.03, spine=6.0, chest=6.0, thigh=(-10.0, -16.0), shin=(24.0, 22.0))

# Rear-sight notch position relative to the eye (right, forward, up; m) and turn (deg: yaw + = muzzle to the left,
# pitch + = muzzle up or None for a level sight line, cant + = top to the left). Arms per side (right, left):
# clavicle protract (shoulder forward) and elevate (shrug), elbow swivel from straight down (always outward).
# neck: head flexion on top of the trunk lean. ulnar: wrist ulnar-deviation limit (right, left; deg). view: (m behind
# the eye, vertical fov) of the first-person view point.
#
# First person (hip, ads; exported) and third person (tp_*; review renders only) are separate geometry on the same
# body, stance and grip (user 2026-10-23: the first-person arms were pushed too far; keep the third-person pose as the
# body reference and fit first person on its own, compact, arms softly bent, the gun in the hands like COD X12).
# Round 3 (2026-10-24, user: ADS cannot look like that, hip fire is canted and does not point at the screen centre;
# COD's canted hold is a tactical move, not a hip state; maybe it is the FOV): no pose asks a wrist past the anatomical
# 45 deg any more (round 2b let the support wrist go to 60 deg, which is what bent the hands apart on screen). First
# person keeps the gun 3-4 cm closer than third person with the elbows softly bent, and the compact look comes from a
# narrower view fov instead of pulling the gun into the face. Hip fire is the same upright hold lowered to the right,
# bore parallel to the view line, so it points at the screen centre; no cant, no pitch.
# Round 4 (2026-10-24, user: hip fire too central, a big gap between the ADS forearms, support hand too high; compose
# in the game camera first, numbers after): ADS shoulders forward and the support shoulder hardly shrugged, so both
# forearms close in symmetrically and the grip shows between the wrists; hip fire 5 cm further right and 1.5 cm lower,
# still level and parallel to the view (not forced onto the crosshair), the support shoulder forward to reach.
# After the in-game look the hip P9 read short and flat: with the bore parallel to the view and only about 20 deg off
# the eye ray the slide is seen nearly end on, so hip fire turns the muzzle 8 deg in about the rear sight (same screen
# spot and size, the side of the slide shows like the old vanilla view model; the raise turns it back to straight).
POSES = {
    # sight picture: elbows about 34 / 32 deg, wrists 36 / 35 deg ulnar, forearms 62 / 59 deg on screen, rear sight
    # centred at 34 deg fov
    'ads': dict(at=(0.0, 0.39, 0.0), yaw=0.0, pitch=None, cant=0.0, neck=23.0, elevate=(0.0, 4.0),
                protract=(10.0, 12.0), swivel=(0.0, 0.0), ulnar=(WRIST_ULNAR, WRIST_ULNAR), view=(0.0, 34.0)),
    # hip fire: upright, lower right, level bore turned 8 deg in; elbows about 58 / 33 deg, wrists 40 / 27 deg ulnar,
    # rear sight at about (0.70, 0.80) of the screen at 42 deg fov (user after the in-game look of hip B: a little
    # further right, +1.5 cm; then 8 deg in so the P9 does not read short and flat)
    'hip': dict(at=(0.10, 0.37, -0.085), yaw=8.0, pitch=None, cant=0.0, neck=18.0, elevate=(2.5, 20.0),
                protract=(6.0, 18.0), swivel=(2.0, 0.0), ulnar=(WRIST_ULNAR, WRIST_ULNAR), view=(0.0, 42.0)),
    # third-person references: isosceles press-out (arms near straight, head down into the sights) and the
    # compressed ready at the chest (elbows down, about 90 deg). tp_ads round 3: the support shoulder a little higher
    # and less forward, so the support wrist stays inside 45 deg with the round 3 grip (elbows 19 / 14 deg)
    'tp_ads': dict(at=(0.0, 0.42, 0.0), yaw=0.0, pitch=None, cant=0.0, neck=10.0, elevate=(8.0, 26.0),
                   protract=(6.0, 7.0), swivel=(14.0, 0.0), ulnar=(WRIST_ULNAR, WRIST_ULNAR)),
    'tp_ready': dict(at=(-0.02, 0.27, -0.36), yaw=-3.0, pitch=6.0, cant=19.0, neck=0.5, elevate=(1.0, 1.5),
                     protract=(30.0, 30.0), swivel=(9.0, 3.0), ulnar=(WRIST_ULNAR, WRIST_ULNAR)),
}


def side(v, s):
    return v[0 if s == 'r' else 1] if isinstance(v, (tuple, list)) else v


def level_pitch():
    return -math.degrees(math.atan2(FRONT.z - REAR.z, FRONT.y - REAR.y))


def blend(a, b, u):
    """Pose spec between a (u = 0) and b (u = 1): numbers and tuples lerp, a level (None) pitch is resolved first."""
    out = {}
    for k in a:
        x, y = a[k], b[k]
        if k == 'pitch':
            x, y = (level_pitch() if x is None else x), (level_pitch() if y is None else y)
        if isinstance(x, (tuple, list)):
            out[k] = tuple(p + (q - p) * u for p, q in zip(x, y))
        else:
            out[k] = x + (y - x) * u
    return out


def spec(name):
    """A pose spec: a POSES name, ('raise', u) from hip (0) to ads (1), or a spec dict."""
    if isinstance(name, dict):
        return name
    if isinstance(name, tuple):
        return blend(POSES['hip'], POSES['ads'], name[1])
    return POSES[name]


def rig():
    return bpy.data.objects['survivor_rig']


def grip(path=None):
    d = json.load(open(path or POSE, encoding='utf-8'))
    return {s: (Matrix(d[k]['root']), {n: tuple(v) for n, v in d[k]['e'].items()})
            for s, k in (('r', 'firing_right'), ('l', 'support_left_mirrored'))}


def p0_to_body():
    """Similarity transform: H1-P0 hand rig space (px) -> survivor_rig armature space (m), fitted on the finger joint
    heads (the body hand was built from the same skeleton). Cached with the H1-P0 'hand' bone matrix Mh."""
    st = getattr(builtins, '_fp_h1p1', None)
    if st:
        return st
    with bpy.data.libraries.load(P0, link=False) as (src, dst):
        dst.objects = ['hand_rig']
    hr = dst.objects[0]
    bones = rig().data.bones
    names = [b.name for b in hr.data.bones if b.name + '_r' in bones]
    a = np.array([hr.data.bones[n].head_local[:] for n in names])
    b = np.array([bones[n + '_r'].head_local[:] for n in names])
    ca, cb = a.mean(0), b.mean(0)
    u, s, vt = np.linalg.svd((b - cb).T @ (a - ca))
    d = np.sign(np.linalg.det(u @ vt))
    r = u @ np.diag([1, 1, d]) @ vt
    k = (s * [1, 1, d]).sum() / ((a - ca) ** 2).sum()
    t = cb - k * r @ ca
    T = Matrix.Translation(Vector(t)) @ Matrix(r.tolist()).to_4x4() @ Matrix.Scale(k, 4)
    res = float(np.sqrt((((k * (r @ a.T)).T + t - b) ** 2).sum(1)).max())
    st = dict(T=T, Mh=hr.data.bones['hand'].matrix_local.copy(), scale=k, res=res)
    bpy.data.objects.remove(hr)
    builtins._fp_h1p1 = st
    return st


def turn(name, deg, axis=(1.0, 0.0, 0.0)):
    """Rotate a pose bone about its posed head in armature space; its children follow."""
    b = rig().pose.bones[name]
    bpy.context.view_layer.update()
    h = b.head.copy()
    b.matrix = (Matrix.Translation(h) @ Matrix.Rotation(math.radians(deg), 4, Vector(axis)) @ Matrix.Translation(-h)
                @ b.matrix)
    bpy.context.view_layer.update()


def eye():
    """Eye point of the posed skeleton, carried by the head bone (the game camera sits here)."""
    ob = rig()
    return ob.pose.bones['head'].matrix @ ob.data.bones['head'].matrix_local.inverted() @ EYE


STANCE_BONES = ('root', 'hips', 'spine', 'chest', 'neck', 'head', 'thigh_r', 'thigh_l', 'shin_r', 'shin_l',
                'foot_r', 'foot_l', 'toes_r', 'toes_l')


def stance(p):
    """Trunk lean, legs and neck for pose spec p."""
    ob = rig()
    pb, bones = ob.pose.bones, ob.data.bones
    for n in STANCE_BONES:
        pb[n].matrix_basis = Matrix.Identity(4)
    bpy.context.view_layer.update()
    turn('spine', STANCE['spine'])
    turn('chest', STANCE['chest'])
    for s in 'rl':
        t, k = side(STANCE['thigh'], s), side(STANCE['shin'], s)
        turn('thigh_' + s, t)
        turn('shin_' + s, k)
        turn('foot_' + s, -(t + k))                     # foot flat again
    drop = min(min(pb['foot_' + s].head.z - bones['foot_' + s].head_local.z,
                   pb['toes_' + s].tail.z - bones['toes_' + s].tail_local.z) for s in 'rl')
    pb['hips'].matrix = Matrix.Translation((0, STANCE['hips_back'], -drop)) @ pb['hips'].matrix
    bpy.context.view_layer.update()
    turn('neck', p.get('neck', 0.0))


def feet():
    """Posed minus rest heights (mm) of the heel (foot head) and toe tip per foot, and how far ahead the left is."""
    ob = rig()
    pb, bones = ob.pose.bones, ob.data.bones
    out = {s: (round((pb['foot_' + s].head.z - bones['foot_' + s].head_local.z) * 1000, 1),
               round((pb['toes_' + s].tail.z - bones['toes_' + s].tail_local.z) * 1000, 1)) for s in 'rl'}
    out['left_ahead_mm'] = round((pb['foot_r'].head.y - pb['foot_l'].head.y) * 1000, 1)
    return out


def gun_matrix(p):
    """Gun px -> armature space for pose spec p (the stance must be posed: the eye comes from the skeleton)."""
    pitch = level_pitch() if p['pitch'] is None else p['pitch']   # None: front dot at the rear notch height
    r, f, u = p['at']
    at = eye() + Vector((-r, -f, u))
    R = (Matrix.Rotation(math.radians(p['yaw']), 4, 'Z') @ Matrix.Rotation(math.radians(-pitch), 4, 'X')
         @ Matrix.Rotation(math.radians(p['cant']), 4, 'Y'))
    return Matrix.Translation(at) @ R @ RG @ Matrix.Scale(PX, 4) @ Matrix.Translation(-REAR)


def hand_targets(G, g=None):
    """Armature-space matrices for hand_r / hand_l that put the body hands into grip g (default POSE) on gun G."""
    st, g = p0_to_body(), g or grip()
    Tinv, Mhi = st['T'].inverted(), st['Mh'].inverted()
    Sb = Matrix.Diagonal((-1, 1, 1, 1))
    bones = rig().data.bones
    out = {}
    for s in 'rl':
        M = g[s][0] @ Matrix.Scale(PX * 1000, 4)     # root takes H mm; the H1-P0 rig is in px (grip_check apply)
        W = G @ M @ Mhi @ Tinv if s == 'r' else G @ S4 @ M @ Mhi @ Tinv @ Sb
        loc, rot, sca = (W @ bones['hand_' + s].matrix_local).decompose()
        out[s] = (Matrix.Translation(loc) @ rot.to_matrix().to_4x4(), sca)
    return out


def set_fingers(g=None, sides='rl', e=None):
    """Finger eulers (deg, the grip files' right-hand convention; the left mirrors y and z) from grip g, or e for
    every side given."""
    g = g or grip()
    pb = rig().pose.bones
    for s in sides:
        es = e or g[s][1]
        for b in pb:
            if b.name.endswith('_' + s) and b.name.split('_')[0] in ('thumb', 'index', 'middle', 'ring', 'pinky'):
                x, y, z = es.get(b.name[:-2], (0, 0, 0))
                if s == 'l':
                    y, z = -y, -z
                b.rotation_mode = 'XYZ'
                b.rotation_euler = tuple(math.radians(v) for v in (x, y, z))


def pose(name, recoil=None, G=None, left=None, right=None):
    """Pose the body for spec(name); recoil = (muzzle up deg, back m, up m) about the firing wrist. Action clips
    (fp_reload_h1p1.py) pass the gun matrix G instead of the spec's, left = (hand_l target, finger eulers or None)
    when the support hand is off the grip and right = finger eulers that replace the grip's on the firing hand (the
    thumb on the magazine release). Returns per-arm numbers (mm / deg), the feet and the eye."""
    p = spec(name)
    ob = rig()
    pb = ob.pose.bones
    ulnar = p.get('ulnar', (WRIST_ULNAR, WRIST_ULNAR))
    pb['hand_r'].constraints['wrist_limit'].max_z = math.radians(ulnar[0])
    pb['hand_l'].constraints['wrist_limit'].min_z = -math.radians(ulnar[1])   # mirrored
    for n in ('hand_r', 'hand_l', 'forearm_r', 'forearm_l', 'upper_arm_r', 'upper_arm_l', 'clavicle_r', 'clavicle_l',
              'forearm_twist_r', 'forearm_twist_l', 'ik_hand_r', 'ik_hand_l', 'pole_elbow_r', 'pole_elbow_l'):
        pb[n].matrix_basis = Matrix.Identity(4)
    stance(p)
    G = gun_matrix(p) if G is None else G
    protract = {s: side(p.get('protract', 0.0), s) for s in 'rl'}
    if recoil:
        up, back, lift = recoil
        # the shoulders give the rearward push: the clavicles roll back by the same distance
        protract = {s: v - math.degrees(back / CLAVICLE) for s, v in protract.items()}
        G = (G @ Matrix.Translation(WRIST_R) @ Matrix.Translation(Vector((0, -back, lift)) / PX)
             @ Matrix.Rotation(math.radians(up), 4, 'X') @ Matrix.Translation(-WRIST_R))
    g = grip(POSE_FP if 'view' in p else POSE)        # first-person specs carry a view; tp_* keep the shared grip
    tg = hand_targets(G, g)
    if left:
        tg['l'] = (left[0], tg['l'][1])
    for s in 'rl':
        out = Vector((-1 if s == 'r' else 1, 0, 0))
        cl = pb['clavicle_' + s]
        head = cl.head.copy()
        rot = (Matrix.Rotation(math.radians(protract[s]) * -out.x, 3, 'Z')
               @ Matrix.Rotation(math.radians(side(p.get('elevate', 0.0), s)), 3, out.cross(Vector((0, 0, 1)))))
        cl.matrix = Matrix.Translation(head) @ rot.to_4x4() @ Matrix.Translation(-head) @ cl.matrix
    bpy.context.view_layer.update()
    for s in 'rl':
        out = Vector((-1 if s == 'r' else 1, 0, 0))
        sh = pb['upper_arm_' + s].head.copy()
        wr = tg[s][0].translation
        ax = (wr - sh).normalized()
        down = Vector((0, 0, -1))
        down = (down - down.dot(ax) * ax).normalized()
        sw = math.radians(side(p.get('swivel', 30.0), s))
        v = Matrix.Rotation(sw, 3, ax) @ down
        if v.dot(out) < down.dot(out):                  # + swivel always turns the elbow outward
            v = Matrix.Rotation(-sw, 3, ax) @ down
        pb['pole_elbow_' + s].matrix = Matrix.Translation((sh + wr) / 2 + v * 0.4)
        pb['ik_hand_' + s].matrix = tg[s][0]
    set_fingers(g)
    if left and left[1]:
        set_fingers(g, 'l', left[1])
    if right:
        set_fingers(g, 'r', dict(g['r'][1], **right))
    bpy.context.view_layer.update()
    gun = bpy.data.objects.get('fp_gun')
    if gun is None:
        gun = bpy.data.objects.new('fp_gun', None)
        gun.instance_type = 'COLLECTION'
        gun.instance_collection = bpy.data.collections['P9_ref']
        bpy.data.collections['Body_H1P1'].objects.link(gun)
    gun.matrix_world = ob.matrix_world @ G
    rep = {}
    for s in 'rl':
        want = tg[s][0]
        got = ob.matrix_world @ pb['hand_' + s].matrix
        sh = pb['upper_arm_' + s].head
        el = pb['forearm_' + s].head
        wr = pb['hand_' + s].head
        flex = 180 - math.degrees((sh - el).angle(wr - el))
        rep[s] = dict(pos_err_mm=round((got.translation - want.translation).length * 1000, 1),
                      rot_err_deg=round(math.degrees(got.to_quaternion().rotation_difference(want.to_quaternion()).angle), 1),
                      reach_mm=round((want.translation - sh).length * 1000, 1), elbow_flex=round(flex, 1),
                      scale=round(tg[s][1].x, 4))
    rep['feet'] = feet()
    rep['eye'] = tuple(round(v, 4) for v in eye())
    return rep


def arm_mask():
    """Vertex group fp_arms = summed weight of arm / hand bones; a Mask modifier keeps verts >= 0.5. Mirrors the
    game's per-triangle filter (the camera sits inside the head)."""
    body = bpy.data.objects['survivor_body']
    vg = body.vertex_groups.get('fp_arms') or body.vertex_groups.new(name='fp_arms')
    names = {g.index: g.name for g in body.vertex_groups}
    for v in body.data.vertices:
        w = sum(g.weight for g in v.groups if names[g.group].split('_')[0] in ARM_BONES or
                names[g.group].startswith(('upper_arm', 'forearm')))
        vg.add([v.index], min(w, 1.0), 'REPLACE')
    m = body.modifiers.get('fp_mask') or body.modifiers.new('fp_mask', 'MASK')
    m.vertex_group = 'fp_arms'
    m.threshold = 0.5
    return m


def fp_camera(p):
    cam = bpy.data.objects.get('fp_cam')
    if cam is None:
        cam = bpy.data.objects.new('fp_cam', bpy.data.cameras.new('fp_cam'))
        bpy.data.collections['Body_H1P1'].objects.link(cam)
    cam.data.type = 'PERSP'
    cam.data.sensor_fit = 'VERTICAL'
    back, fov = p.get('view', (0.0, FOV_Y))
    cam.data.angle_y = math.radians(fov)
    cam.data.clip_start = 0.02
    cam.data.clip_end = 50
    cam.rotation_mode = 'XYZ'
    cam.rotation_euler = (math.pi / 2, 0, math.pi)    # look along -Y, image up +Z, image right = body right (-X)
    cam.location = eye() + Vector((0, back, 0))       # behind the eye (+Y)
    return cam


def fp_shots(names=('hip', 'ads'), w=1280, h=720, sides=True):
    sc = bpy.context.scene
    import importlib.util
    ms = importlib.util.spec_from_file_location('rv', HERE + '/render_views.py')
    rv = importlib.util.module_from_spec(ms)
    ms.loader.exec_module(rv)
    os.makedirs(OUT, exist_ok=True)
    mask = arm_mask()
    keep = {'survivor_body', 'fp_gun'}
    hidden = {}
    ad = rig().animation_data
    action = ad.action if ad else None
    if ad:
        ad.action = None                                # a baked clip would re-pose the keyed bones at render time
    for ob in sc.objects:
        hidden[ob.name] = ob.hide_render
        ob.hide_render = ob.name not in keep and ob.type != 'CAMERA'
    out, reps = [], {}
    try:
        for n in names:
            reps[n] = pose(n)
            rv.setup(w)
            sc.render.resolution_x, sc.render.resolution_y = w, h
            sc.display.shading.background_type = 'WORLD'
            sc.world.color = (0.42, 0.47, 0.52)
            mask.show_render = True
            sc.camera = fp_camera(spec(n))
            tag = n if isinstance(n, str) else '%s_%02d' % (n[0], round(n[1] * 100))
            sc.render.filepath = os.path.join(OUT, tag + '_fp.png')
            bpy.ops.render.render(write_still=True)
            out.append(sc.render.filepath)
            if sides:
                mask.show_render = False
                out += rv.render(['survivor_body', 'fp_gun'], [('side', (-1, -0.15, 0.1), (0, 0, 1))], OUT, tag,
                                 size=720, focus=(0, -0.35, 1.45), span=1.0)
    finally:
        for k, v in hidden.items():
            if k in sc.objects:
                sc.objects[k].hide_render = v
        mask.show_render = False
        mask.show_viewport = False
        if ad:
            ad.action = action
    return out, reps


def tp_shots(names=('tp_ready', 'tp_ads', 'hip', 'ads'), size=720):
    """Third-person review renders (whole body, side and front three-quarter): renders/fp_h1p1/<pose>_tp_*.png."""
    import importlib.util
    ms = importlib.util.spec_from_file_location('rv', HERE + '/render_views.py')
    rv = importlib.util.module_from_spec(ms)
    ms.loader.exec_module(rv)
    ad = rig().animation_data
    action = ad.action if ad else None
    if ad:
        ad.action = None
    out = []
    try:
        for n in names:
            pose(n)
            tag = n if isinstance(n, str) else '%s_%02d' % (n[0], round(n[1] * 100))
            out += rv.render(['survivor_body', 'fp_gun'], [('tp_side', (-1, 0, 0.08), (0, 0, 1)),
                                                           ('tp_front', (-0.55, -1, 0.25), (0, 0, 1))],
                             OUT, tag, size=size, focus=(0, -0.15, 1.0), span=2.1)
    finally:
        if ad:
            ad.action = action
    return out


# ---- clips and export

ASSETS = r"D:/Minecraft Modding/Apocalypse First Light/src/main/resources/assets/apocalypse_firstlight"
EXPORTER = r"D:/Minecraft Modding/Apocalypse First Light/design/items/mesh_hand_p0/source/export_geckolib.py"
REF = os.path.normpath(HERE + "/../export/survivor_base_h1p1_ref.json")
FPS = 60
KEYED = ('ik_hand_r', 'ik_hand_l', 'pole_elbow_r', 'pole_elbow_l', 'clavicle_r', 'clavicle_l') + STANCE_BONES
# 9 mm two-hand recoil, 60 fps frames: (frame, muzzle up deg, back m, up m), the same impulse from hip and ADS. The
# kick peaks in two frames, settles back in a straight line and stops dead at frame 13 (no overshoot); the slide is
# not animated here.
RECOIL = ((0, 0, 0, 0), (2, 6.5, 0.016, 0.004), (4, 5.5, 0.012, 0.003), (7, 3.0, 0.006, 0.0015),
          (10, 1.0, 0.002, 0.0005), (13, 0, 0, 0), (18, 0, 0, 0))
# hip -> ADS in 0.2 s, smoothstep, every frame a full IK solve (no straight-line blending of the arm chains). The game
# seeks it by the raise progress, both ways; fp_lower is the same motion from the ADS end, for coming back after an
# ADS shot (GeckoLib's clip switch shows the new clip's first frame once, so it must be the pose the body is in)
RAISE = 12
# (clip, pose, recoil table); poses 'raise' / 'lower' bake ('raise', u) frame by frame
CLIPS = (('fp_hip', 'hip', None), ('fp_ads', 'ads', None), ('fp_raise', 'raise', None), ('fp_lower', 'lower', None),
         ('fp_fire', 'ads', RECOIL), ('fp_fire_hip', 'hip', RECOIL))
STALE = ('fp_idle',)                        # actions of earlier rounds, removed on bake
# action clips registered by fp_reload_h1p1.py (H1-P2): name -> (last frame, frame -> pose() report), baked after
# CLIPS; AFTER_EXPORT: callables run after the GeckoLib files are written (the props sidecar)
ACTIONS = {}
AFTER_EXPORT = []


def key_all(frame):
    pb = rig().pose.bones
    names = list(KEYED) + [b.name for b in pb if b.name.split('_')[0] in ('thumb', 'index', 'middle', 'ring', 'pinky')]
    for n in names:
        b = pb[n]
        b.keyframe_insert('location', frame=frame)
        b.keyframe_insert('rotation_quaternion' if b.rotation_mode == 'QUATERNION' else 'rotation_euler', frame=frame)
    # the per-pose wrist limits, so the exporter's re-evaluation solves every frame as it was posed
    pb['hand_r'].constraints['wrist_limit'].keyframe_insert('max_z', frame=frame)
    pb['hand_l'].constraints['wrist_limit'].keyframe_insert('min_z', frame=frame)


def recoil_at(table, f):
    for (f0, *a), (f1, *b) in zip(table, table[1:]):
        if f0 <= f <= f1:
            t = (f - f0) / (f1 - f0) if f1 > f0 else 0.0
            return tuple(x + (y - x) * t for x, y in zip(a, b))
    return tuple(table[-1][1:])


def raise_u(f):
    t = f / RAISE
    return t * t * (3 - 2 * t)


def clip_frames(name, table):
    return RAISE if name in ('raise', 'lower') else table[-1][0] if table else 1


def clip_pose(name, f):
    return ('raise', raise_u(f)) if name == 'raise' else ('raise', raise_u(RAISE - f)) if name == 'lower' else name


def bake_clips():
    """One action per clip on survivor_rig, every frame keyed (IK targets, poles, clavicles, stance, fingers); the
    arm chains themselves stay constraint-driven and the exporter samples them evaluated."""
    ob = rig()
    ad = ob.animation_data or ob.animation_data_create()
    for n in STALE:
        if n in bpy.data.actions:
            bpy.data.actions.remove(bpy.data.actions[n])
    out = {}
    for clip, name, table in CLIPS:
        act = bpy.data.actions.get(clip)
        if act:
            bpy.data.actions.remove(act)
        act = bpy.data.actions.new(clip)
        ad.action = act
        last = clip_frames(name, table)
        reps = []
        for f in range(0, last + 1):
            reps.append(pose(clip_pose(name, f), recoil_at(table, f) if table else None))
            key_all(f)
        act.use_fake_user = True
        out[clip] = dict(frames=(0, last), worst_rot=max(r[s]['rot_err_deg'] for r in reps for s in 'rl'),
                         worst_pos=max(r[s]['pos_err_mm'] for r in reps for s in 'rl'))
    for clip, (last, frame) in ACTIONS.items():
        act = bpy.data.actions.get(clip)
        if act:
            bpy.data.actions.remove(act)
        act = bpy.data.actions.new(clip)
        ad.action = act
        reps = []
        for f in range(0, last + 1):
            reps.append(frame(f))
            key_all(f)
        act.use_fake_user = True
        out[clip] = dict(frames=(0, last), worst_rot=max(r[s]['rot_err_deg'] for r in reps for s in 'rl'),
                         worst_pos=max(r[s]['pos_err_mm'] for r in reps for s in 'rl'))
    return out


def prop_matrix():
    """P9-01 mesh model blocks -> body model blocks at the rest pose, carried by hand_r (column-major 4x4 for JOML).
    The grip is rigid, so hand_r's deformation in any clip carries the gun with it."""
    pose('ads')
    ob = rig()
    G = gun_matrix(POSES['ads'])
    hand_rest = ob.data.bones['hand_r'].matrix_local
    hand_now = ob.pose.bones['hand_r'].matrix
    G_rest = hand_rest @ hand_now.inverted() @ G
    C = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))
    K = C @ G_rest @ C.inverted() @ Matrix.Scale(16.0, 4)
    return [round(K[r][c], 6) for c in range(4) for r in range(4)]


def export_fp():
    clips = bake_clips()
    prop = prop_matrix()
    ns = {}
    exec(open(EXPORTER, encoding='utf-8').read(), ns)
    body = bpy.data.objects['survivor_body']
    e = EYE
    def refs(f0, f1):
        return sorted({f0, (f0 + f1) // 2, f1}) if f1 - f0 > 1 else [f0, f1]
    # fp_view: first-person view point per end pose, (m behind the eye, vertical fov); the game eases between them
    # with the raise progress
    view = {n: list(POSES[n].get('view', (0.0, FOV_Y))) for n in ('hip', 'ads')}
    # the inspects' arms reach furthest: the default key tolerance summed down the arm to the fingertips past the
    # check's 0.03 px, so those clips keep finer keys (the others' keys unchanged)
    fine = {c: 0.02 for c in ACTIONS if c.startswith('fp_inspect')}
    cfg = dict(armature='survivor_rig', assets=ASSETS, id='dev/survivor_base_h1p1', texture_size=(16, 16), unit_px=16,
               fps=FPS, bones='deform', skinned=body, ref=REF, rot_tol=0.05, pos_tol=0.002,
               clips=[dict(name=c, action=c, frames=clips[c]['frames'], loop='hold_on_last_frame',
                           ref_frames=refs(*clips[c]['frames']), **({'rot_tol': fine[c]} if c in fine else {}))
                      for c in [c for c, _, _ in CLIPS] + list(ACTIONS)],
               skin_extra=dict(eye=[e.x, e.z, -e.y], props=dict(p9_01=dict(bone='hand_r', matrix=prop)),
                               fp_bones=list(ARM_BONES), fp_view=view))
    summary = ns['export'](cfg)
    for after in AFTER_EXPORT:
        after()
    return clips, summary
