# AFL Mesh survivor V1 third-person locomotion (2026-10-25, docs/dev/mesh_survivor_v1_tp_locomotion_a1.md): keys a clip
# made by anim_tp_v1.py (<name>.blend.json) on survivor_rig as its own action tp_<...>, for looking at it in Blender.
# Only actions named tp_* are made or replaced; the fp_* actions, the 54 bones and their rest pose are never touched.
#   K = runpy-loaded module; K.load(path) -> action name; K.view(name) shows it (arm IK off); K.restore() puts back the
#   action and IK state the file had (fp_hip, IK on, rest position) - call it before saving.
import bpy, json
from bpy_extras import anim_utils
from mathutils import Quaternion, Vector

RIG = 'survivor_rig'
ARM_CONSTRAINTS = (('forearm_r', 'arm_ik'), ('forearm_l', 'arm_ik'), ('hand_r', 'hand_rot'), ('hand_l', 'hand_rot'))
STATE = bpy.app.driver_namespace.setdefault('afl_tp_v1', {})


def rig():
    return bpy.data.objects[RIG]


def load(path):
    data = json.load(open(path))
    name = data['name']
    assert name.startswith('tp_'), name
    old = bpy.data.actions.get(name)
    if old is not None:
        bpy.data.actions.remove(old)
    act = bpy.data.actions.new(name)
    act.use_fake_user = True
    arm = rig()
    keep = arm.animation_data.action if arm.animation_data else None
    if keep is not None and keep.name.startswith('tp_'):
        keep = bpy.data.actions.get('fp_hip')
    if arm.animation_data is None:
        arm.animation_data_create()
    arm.animation_data.action = act
    prev = {}
    for i, fr in enumerate(data['frames']):
        f = i + 1
        for b, v in fr.items():
            pb = arm.pose.bones[b]
            q = Quaternion(v[:4])
            e = q.to_euler(pb.rotation_mode, prev[b]) if b in prev else q.to_euler(pb.rotation_mode)
            prev[b] = e
            pb.rotation_euler = e
            pb.keyframe_insert('rotation_euler', frame=f, group=b)
            if b == 'hips':
                pb.location = Vector(v[4:7])
                pb.keyframe_insert('location', frame=f, group=b)
    # one more key = the first, so the action loops in Blender (the game clip leaves it out)
    n = len(data['frames'])
    bag = anim_utils.animdata_get_channelbag_for_assigned_slot(arm.animation_data)   # Blender 5 slotted actions
    for fc in bag.fcurves:
        k0 = fc.keyframe_points[0]
        fc.keyframe_points.insert(n + 1, k0.co[1])
        for k in fc.keyframe_points:
            k.interpolation = 'LINEAR'
    act.frame_range = (1, n + 1)
    arm.animation_data.action = keep
    return name


def view(name):
    arm = rig()
    if 'action' not in STATE:
        STATE['action'] = arm.animation_data.action.name if arm.animation_data and arm.animation_data.action else None
        STATE['pose_position'] = arm.data.pose_position
        STATE['ik'] = {b + c: arm.pose.bones[b].constraints[c].mute for b, c in ARM_CONSTRAINTS}
        STATE['frame'] = (bpy.context.scene.frame_start, bpy.context.scene.frame_end, bpy.context.scene.frame_current)
        STATE['basis'] = {pb.name: pb.matrix_basis.copy() for pb in arm.pose.bones}
    for pb in arm.pose.bones:
        pb.matrix_basis.identity()
    for b, c in ARM_CONSTRAINTS:
        arm.pose.bones[b].constraints[c].mute = True
    arm.animation_data.action = bpy.data.actions[name]
    arm.data.pose_position = 'POSE'
    a, b = bpy.data.actions[name].frame_range
    sc = bpy.context.scene
    sc.frame_start, sc.frame_end = int(a), int(b)
    sc.frame_set(int(a))


def readback(path):
    """Largest gap (mm) between the Blender-evaluated bones and the numpy frames stored in the clip's check block."""
    data = json.load(open(path))
    arm = rig()
    worst = 0.0
    for k, bones in data['check'].items():
        bpy.context.scene.frame_set(int(k) + 1)
        for b, v in bones.items():
            M = arm.matrix_world @ arm.pose.bones[b].matrix
            h, t = M.translation, M.translation + M.col[1].to_3d() * 0.1
            worst = max(worst, (h - Vector(v[:3])).length, (t - Vector(v[3:])).length)
    return worst * 1000


def restore():
    if 'action' not in STATE:
        return
    arm = rig()
    arm.animation_data.action = bpy.data.actions[STATE['action']] if STATE['action'] else None
    arm.data.pose_position = STATE['pose_position']
    for b, c in ARM_CONSTRAINTS:
        arm.pose.bones[b].constraints[c].mute = STATE['ik'][b + c]
    for pb in arm.pose.bones:
        pb.matrix_basis = STATE['basis'][pb.name]
    sc = bpy.context.scene
    sc.frame_start, sc.frame_end, cur = STATE['frame']
    sc.frame_set(cur)
    STATE.clear()
