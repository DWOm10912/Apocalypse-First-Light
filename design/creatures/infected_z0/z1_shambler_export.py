# Z1a shambler game files (docs/gameplay/afl_infected_z0_design.md 12): the r6 shambler's skinned mesh, its texture and
# its clips, read by creature/infected (client/creature/InfectedModel). Run inside Blender:
#   exec(open("D:/Minecraft Modding/AFL-vanilla-rework/design/creatures/infected_z0/z1_shambler_export.py").read())
#   export_all()            # mesh, texture, clips into the mod's assets
#   preview(out_dir)        # one render per checked key (walk, idles, attack), for the key sheet
#
# Axes: Blender X = the character's left, -Y = front, Z up, 1 px = 1/16 m (z0_concept_r6_build.py). Java model px (the
# vanilla PlayerModel's space): x the same, y = 24 - z (down, the feet at 24), z = Blender y (front -z). The map is a
# proper rotation, so a quad's winding and a rotation's axis carry over: v_java = (x, -z, y).
# Clip rotations use Fig.pose's convention: per bone (x, y, z) degrees, Euler YXZ in the parent's rest axes (armature
# axes), so the game's local rotation is the same matrix in Java axes: W_b = W_parent * T(P_b) * R_b * T(-P_b).
import bpy, math, json, os, shutil
from mathutils import Vector, Euler

HERE = "D:/Minecraft Modding/AFL-vanilla-rework/design/creatures/infected_z0"
ASSETS = "D:/Minecraft Modding/AFL-vanilla-rework/src/main/resources/assets/apocalypse_firstlight"
MESH_OUT = ASSETS + "/creatures/infected/shambler_mesh.json"
CLIPS_OUT = ASSETS + "/creatures/infected/shambler_clips.json"
TEX_OUT = ASSETS + "/textures/entity/infected/shambler.png"
PX = 1 / 16

G = {"__name__": "z0r6"}
exec(open(HERE + "/z0_concept_r6_build.py", encoding="utf-8").read(), G)
FIG = None


def build():
    global FIG
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o)
    FIG = G["build_type"]("shambler", bpy.context.scene.collection, 0)
    G["FIGS"].clear()
    G["FIGS"]["shambler"] = FIG
    return FIG


def java(p):
    """Blender px -> Java model px."""
    return [round(p[0], 5), round(24 - p[2], 5), round(p[1], 5)]


# ------------------------------------------------------------------------------------------------------------ mesh
def islands(me):
    parent = list(range(len(me.vertices)))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    for p in me.polygons:
        vs = list(p.vertices)
        for v in vs[1:]:
            parent[find(v)] = find(vs[0])
    return [find(v) for v in range(len(me.vertices))]


def part_name(me, ob, verts):
    """The part a loose piece is: by the bone carrying most of its weight (the hat: the larger box on the head)."""
    tally = {}
    for v in verts:
        for g in me.vertices[v].groups:
            n = ob.vertex_groups[g.group].name
            tally[n] = tally.get(n, 0) + g.weight
    top = max(tally, key=tally.get)
    side = top[-2:] if top[-2:] in (".R", ".L") else ""
    if top in ("chest", "belly", "pelvis"):
        return "torso"
    if top in ("upper.R", "fore.R", "upper.L", "fore.L"):
        return "arm" + side
    if top in ("thigh.R", "shin.R", "thigh.L", "shin.L"):
        return "leg" + side
    if top == "head":
        xs = [me.vertices[v].co.x for v in verts]
        return "hat" if (max(xs) - min(xs)) / PX > 8.5 else "head"
    return top


def mesh_json(f):
    ob, me = f.ob, f.ob.data
    names = list(f.bones)
    index = {n: i for i, n in enumerate(names)}
    bones = [{"name": n, "parent": index[par] if par else -1, "pivot": java(hd)}   # Fig.bones is in px already
             for n, (hd, tl, par) in f.bones.items()]
    root = islands(me)
    pieces = {}
    for v in range(len(me.vertices)):
        pieces.setdefault(root[v], []).append(v)
    piece_part = {r: part_name(me, ob, vs) for r, vs in pieces.items()}
    centre = {r: sum((me.vertices[v].co for v in vs), Vector()) / len(vs) for r, vs in pieces.items()}
    parts = sorted(set(piece_part.values()))
    uv = me.uv_layers.active.data
    quads, verts = [], []
    for poly in me.polygons:
        if len(poly.vertices) != 4:
            raise ValueError("not a quad: polygon %d" % poly.index)
        vs, ls = list(poly.vertices), list(poly.loop_indices)
        r = root[vs[0]]
        if poly.normal.dot(poly.center - centre[r]) < 0:
            vs, ls = vs[::-1], ls[::-1]
        pj = [Vector(java([c / PX for c in me.vertices[v].co])) for v in vs]
        n = (pj[2] - pj[0]).cross(pj[3] - pj[1])
        cj = Vector(java([c / PX for c in centre[r]]))
        mid = sum(pj, Vector()) / 4
        if n.dot(mid - cj) <= 0:
            raise ValueError("winding: polygon %d" % poly.index)
        quads.append(parts.index(piece_part[r]))
        for v, l in zip(vs, ls):
            w = sorted(((ob.vertex_groups[g.group].name, g.weight) for g in me.vertices[v].groups if g.weight > 0),
                       key=lambda kv: -kv[1])[:4]
            tot = sum(x for _, x in w)
            js = [index[b] for b, _ in w] + [0] * (4 - len(w))
            ws = [round(x / tot, 5) for _, x in w] + [0.0] * (4 - len(w))
            u, vv = uv[l].uv
            verts += java([c / PX for c in me.vertices[v].co]) + [round(u, 6), round(1 - vv, 6)] + js + ws
    return {
        "format_version": 1,
        "about": "AFL infected Z1a shambler (docs/gameplay/afl_infected_z0_design.md 12), generated by "
                 "design/creatures/infected_z0/z1_shambler_export.py from the r6 concept. Java model px; per quad 4 vertices "
                 "of x y z u v j0..j3 w0..w3; (p2-p0)x(p3-p1) points out of the part.",
        "texture_size": G["ATLAS"],
        "bones": bones,
        "parts": parts,
        "quad_parts": quads,
        "vertices": verts,
    }


# ------------------------------------------------------------------------------------------------------------ clips
BASE = dict(G["POSES"]["shambler"])
LEGS = ("thigh.R", "shin.R", "thigh.L", "shin.L")


def key(**over):
    """The shambler's posture with some bones replaced (same names with _ for the dot: upper_L)."""
    k = {b: tuple(v) for b, v in BASE.items()}
    for b, v in over.items():
        k[b.replace("_", ".")] = v
    return k


def add(a, b):
    return tuple(x + y for x, y in zip(a, b))


def walk_key(p):
    """The shamble at phase p (0..1, two steps): long lurching steps, both thighs sweeping the same 68 deg so the planted
    foot keeps its place at an even body speed; the right leg lifts its knee, the left one swings through stiff-kneed
    with its toe low; the hips turn and roll over the stance leg; the hanging right arm swings against the right leg, the
    raised left one bobs; the head nods on each step. (r1 in game 2026-10-26: 60/40 deg sweeps read as small quick steps.)"""
    c, s = math.cos(2 * math.pi * p), math.sin(2 * math.pi * p)
    c2 = math.cos(4 * math.pi * p)
    k = key()
    # the pelvis leans 10 deg forward (BASE): thighs at -10 hang straight down
    k["thigh.R"] = (-12 - 34 * c, 0, 3)
    k["shin.R"] = (12 + 55 * max(0.0, -s), 0, 0)
    k["thigh.L"] = (-12 + 34 * c, 0, -4)
    k["shin.L"] = (14 + 14 * max(0.0, s), 0, 0)
    k["pelvis"] = add(BASE["pelvis"], (3 * c2, 6 * c, 8 * c))
    k["belly"] = add(BASE["belly"], (0, -3 * c, -3 * c))
    k["chest"] = add(BASE["chest"], (3 * c2, 0, -5 * c))
    k["head"] = add(BASE["head"], (6 * c2, 0, 3 * c))
    k["upper.R"] = add(BASE["upper.R"], (20 * c, 0, 0))
    k["fore.R"] = add(BASE["fore.R"], (-6 * max(0.0, c), 0, 0))
    k["upper.L"] = add(BASE["upper.L"], (6 * c2, 0, 0))
    k["fore.L"] = add(BASE["fore.L"], (5 * c2, 0, 0))
    k["jaw"] = add(BASE["jaw"], (4 * s, 0, 0))
    return k


def idle_keys(variant):
    """Standing: slow breathing in the chest and jaw, the raised arm sinking and lifting; idle_2 twitches the head round."""
    stand = dict(thigh_L=(-22, 0, -4), shin_L=(24, 0, 0), thigh_R=(-4, 0, 4), shin_R=(22, 0, 0))
    if variant == 1:
        return [(0.0, key(**stand)),
                (1.4, key(**stand, chest=add(BASE["chest"], (-3, 0, 0)), jaw=(26, 0, 0), upper_L=add(BASE["upper.L"], (8, 0, 0)))),
                (2.8, key(**stand, chest=add(BASE["chest"], (2, 2, 0)), jaw=(14, 0, 0), head=add(BASE["head"], (4, -4, 0)))),
                (4.0, key(**stand))]
    return [(0.0, key(**stand)),
            (0.9, key(**stand, head=add(BASE["head"], (6, -14, -30)), neck=add(BASE["neck"], (-6, 0, -8)), jaw=(28, 0, 0))),
            (1.15, key(**stand, head=add(BASE["head"], (8, -18, -38)), neck=add(BASE["neck"], (-6, 0, -10)), jaw=(30, 0, 0))),
            (2.4, key(**stand, head=add(BASE["head"], (2, 6, 4)), jaw=(16, 0, 0), upper_L=add(BASE["upper.L"], (10, 0, 0)))),
            (3.6, key(**stand))]


def attack_keys():
    """0.6 s: both arms come up and back, then claw forward and down with the chest and the jaw wide; the blow lands at 0.3 s
    (InfectedEntity.HIT_TICK = 6)."""
    stand = dict(thigh_L=(-26, 0, -4), shin_L=(26, 0, 0), thigh_R=(-2, 0, 4), shin_R=(20, 0, 0))
    up = dict(upper_R=(-112, 14, 0), fore_R=(-38, 0, 0), upper_L=(-118, -14, 0), fore_L=(-42, 0, 0),
              chest=add(BASE["chest"], (-12, 0, 0)), belly=add(BASE["belly"], (-5, 0, 0)), head=add(BASE["head"], (-6, 0, 0)), jaw=(30, 0, 0))
    strike = dict(upper_R=(-70, 6, 0), fore_R=(-12, 0, 0), upper_L=(-78, -6, 0), fore_L=(-16, 0, 0),
                  chest=add(BASE["chest"], (16, 0, 0)), belly=add(BASE["belly"], (8, 0, 0)), head=add(BASE["head"], (-16, 0, 0)), jaw=(38, 0, 0))
    return [(0.0, key(**stand)), (0.16, key(**stand, **up)), (0.3, key(**stand, **strike)),
            (0.42, key(**stand, **dict(strike, upper_R=(-50, 6, 0), upper_L=(-56, -6, 0)))), (0.6, key(**stand))]


def clip_defs():
    walk = [(i / 16, walk_key(i / 16)) for i in range(17)]
    return {
        "walk": dict(loop=True, ease="linear", keys=walk),
        "idle_1": dict(loop=True, ease="smooth", keys=idle_keys(1)),
        "idle_2": dict(loop=True, ease="smooth", keys=idle_keys(2)),
        "attack": dict(loop=False, ease="smooth", keys=attack_keys()),
    }


def jquat(rot):
    q = Euler(tuple(math.radians(a) for a in rot), "YXZ").to_matrix().to_quaternion()
    return [round(q.x, 6), round(-q.z, 6), round(q.y, 6), round(q.w, 6)]


def feet(f):
    """Both soles (the shins' ends), Blender px: {bone: (z, y)}."""
    ao = f.ao
    return {n: tuple(c / PX for c in (ao.matrix_world @ ao.pose.bones[n].tail).zy) for n in ("shin.R", "shin.L")}


def walk_cycle(f, samples=64):
    """How far the body goes in one cycle: the right foot is planted while its thigh swings back (phase 0..0.5), the
    left one for the other half (walk_key), and a planted foot slides back (+y) as far as the body goes forward."""
    prev, halves = None, [0.0, 0.0]
    for i in range(samples + 1):
        p = i / samples
        f.pose(walk_key(p))
        now = feet(f)
        if prev:
            n = "shin.R" if p <= 0.5 else "shin.L"
            halves[p > 0.5] += now[n][1] - prev[n][1]
        prev = now
    WALK_HALVES[:] = halves
    return sum(halves)


WALK_HALVES = [0.0, 0.0]  # the two steps' lengths from the last walk_cycle (they should be close: the body moves evenly)


def clips_json(f):
    names = list(f.bones)
    out = {}
    cycle = walk_cycle(f)
    for name, c in clip_defs().items():
        keys = []
        for t, k in c["keys"]:
            f.pose(k)
            lift = f.ao.location.z / PX
            keys.append({"t": round(t, 4), "lift": round(lift, 4), "rot": [jquat(k.get(b, (0, 0, 0))) for b in names]})
        length = c["keys"][-1][0]
        out[name] = {"length": length, "loop": c["loop"], "ease": c["ease"], "keys": keys}
    out["walk"]["length"] = 1.0
    return {
        "format_version": 1,
        "about": "AFL infected Z1a shambler clips, generated by design/creatures/infected_z0/z1_shambler_export.py. Per key: t "
                 "(s; walk: the phase 0..1 of one cycle of two steps), lift (model px the body is raised so the lowest point "
                 "touches the ground), rot per bone (x y z w, Java model axes, the bone's turn in its parent's frame).",
        "bones": names,
        "walk_cycle_px": round(cycle, 3),
        "attack_hit": 0.3,
        "clips": out,
    }


def export_all():
    f = build()
    os.makedirs(os.path.dirname(MESH_OUT), exist_ok=True)
    os.makedirs(os.path.dirname(TEX_OUT), exist_ok=True)
    mj = mesh_json(f)
    json.dump(mj, open(MESH_OUT, "w"), separators=(",", ":"))
    shutil.copyfile(f"{HERE}/r6/z0r6_{f.A.name}_atlas.png", TEX_OUT)
    cj = clips_json(f)
    json.dump(cj, open(CLIPS_OUT, "w"), separators=(",", ":"))
    f.pose({})
    return {"quads": len(mj["quad_parts"]), "parts": mj["parts"], "bones": len(mj["bones"]),
            "walk_cycle_px": cj["walk_cycle_px"], "steps_px": [round(h, 3) for h in WALK_HALVES], "lifts": {n: [k["lift"] for k in c["keys"]] for n, c in cj["clips"].items()}}


def preview(out):
    """Renders the checked keys side on and three-quarter: walk at 8 phases, the idles' keys, the attack's keys."""
    f = FIG or build()
    G["stage"]()
    os.makedirs(out, exist_ok=True)
    shots = []
    for name, c in clip_defs().items():
        ks = c["keys"][:-1] if c["loop"] else c["keys"]
        if name == "walk":
            ks = ks[::2]
        for i, (t, k) in enumerate(ks):
            f.pose(k)
            for view, d in (("side", (-1, 0, 0.08)), ("q", (-0.75, -1, 0.2))):
                path = f"{out}/{name}_{i:02d}_{view}.png"
                G["shoot"](f, d, path, res=(360, 480))
                shots.append(path)
    f.pose({})
    return shots
