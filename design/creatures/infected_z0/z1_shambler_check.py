# Z1a offline check: the game's skinning (client/creature/InfectedModel: W_b = W_parent * T(P) * R * T(-P), the root
# lowered by lift, Java model px) applied to the exported mesh and clips must put every vertex where Blender's armature
# puts it for the same key. Run inside Blender after z1_shambler_export.py:
#   exec(open(".../z1_shambler_check.py").read()); print(check())
import json
from mathutils import Matrix, Quaternion, Vector

exec(open("D:/Minecraft Modding/AFL-vanilla-rework/design/creatures/infected_z0/z1_shambler_export.py", encoding="utf-8").read())


def game_matrices(cj, mj, clip, ki):
    piv = [Vector(b["pivot"]) for b in mj["bones"]]
    par = [b["parent"] for b in mj["bones"]]
    k = cj["clips"][clip]["keys"][ki]
    W = []
    for i in range(len(piv)):
        x, y, z, w = k["rot"][i]
        R = Quaternion((w, x, y, z)).to_matrix().to_4x4()
        base = Matrix.Translation(Vector((0, -k["lift"], 0))) if par[i] < 0 else W[par[i]]
        W.append(base @ Matrix.Translation(piv[i]) @ R @ Matrix.Translation(-piv[i]))
    return W


def check(cases=(("walk", 0), ("walk", 4), ("walk", 11), ("idle_1", 1), ("idle_2", 2), ("attack", 1), ("attack", 2))):
    f = build()
    me = f.ob.data
    mj = json.load(open(MESH_OUT))
    cj = json.load(open(CLIPS_OUT))
    V, S = mj["vertices"], 13
    # each exported vertex back to its Blender vertex: same rest place and same weights (torso and arm corners coincide)
    sig = {}
    for v in me.vertices:
        ws = tuple(sorted((f.ob.vertex_groups[g.group].name, round(g.weight, 3)) for g in v.groups if g.weight > 0))
        sig[(tuple(round(c, 3) for c in java([c / PX for c in v.co])), ws)] = v.index
    names = [b["name"] for b in mj["bones"]]
    which = []
    for o in range(0, len(V), S):
        tot = sum(V[o + 9:o + 13])
        ws = tuple(sorted((names[int(V[o + 5 + j])], round(V[o + 9 + j], 3)) for j in range(4) if V[o + 9 + j] > 0))
        which.append(sig.get((tuple(round(c, 3) for c in V[o:o + 3]), ws)))
    missing = sum(1 for w in which if w is None)
    out = {"vertices": len(which), "unmatched": missing}
    for clip, ki in cases:
        W = game_matrices(cj, mj, clip, ki)
        f.pose(clip_defs()[clip]["keys"][ki][1])
        dg = bpy.context.evaluated_depsgraph_get()
        ev = f.ob.evaluated_get(dg)
        err = 0.0
        for n, o in enumerate(range(0, len(V), S)):
            if which[n] is None:
                continue
            p = Vector(V[o:o + 3])
            acc = Vector()
            for j in range(4):
                if V[o + 9 + j] > 0:
                    acc += V[o + 9 + j] * (W[int(V[o + 5 + j])] @ p)
            want = Vector(java([c / PX for c in ev.matrix_world @ ev.data.vertices[which[n]].co]))
            err = max(err, (acc - want).length)
        out[f"{clip}[{ki}]"] = round(err, 4)
    f.pose({})
    return out
