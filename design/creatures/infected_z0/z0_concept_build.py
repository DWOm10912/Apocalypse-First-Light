# Z0 concept r5: an infected rig of its own (not the player body) and seven common-infected types that differ by build,
# posture, gait and damage rather than by clothes. Block boxes with low-poly mesh balls at the joints.
# User 2026-10-26: "丧尸可以完全不需要和玩家的模型一样吧？毕竟都是丧尸了 / 变体本身的思路不对 / 你能不能参考一下其他游戏是怎么做
# 丧尸的，不要只看脸 / 然后给我设计概念图 / 模型还是继续用blockmesh/mesh，因为丧尸可以有很夸张的肢体动作".
# Run inside Blender 5.x:
#   exec(open(r"<repo>/design/creatures/infected_z0/z0_concept_build.py", encoding="utf-8").read())
# Builds a fresh file, renders z0_concept_silhouettes_<ROUND>.png and z0_concept_<ROUND>.png next to this script and
# saves z0_concept.blend.
ROUND = "r5"
import bpy, bmesh, math, os
from mathutils import Vector

HERE = r"D:\Minecraft Modding\AFL-vanilla-rework\design\creatures\infected_z0"
P = 1 / 16   # one texture pixel; the rig is measured in pixels like the vanilla body


def lin(h):
    return tuple((int(h[i:i + 2], 16) / 255) ** 2.2 for i in (1, 3, 5))


# ---------------------------------------------------------------- meshes and materials

def box_mesh(name, sx, sy, sz, c=(0, 0, 0)):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1)
    for v in bm.verts:
        v.co = Vector((v.co.x * sx + c[0], v.co.y * sy + c[1], v.co.z * sz + c[2])) * P
    bm.to_mesh(me)
    bm.free()
    return me


def ball_mesh(name, r):
    # a cube cut twice and pushed onto a sphere: a 54-face blocky ball for the joints
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=2)
    bmesh.ops.subdivide_edges(bm, edges=bm.edges[:], cuts=2, use_grid_fill=True)
    for v in bm.verts:
        v.co = v.co.normalized() * r * P
    bm.to_mesh(me)
    bm.free()
    return me


MATS = {}


def mat(name, base, dark=0.5, blood=0.0, seed=0.0, emit=0.0):
    """Pixel-snapped blotchy noise between a darker and the base colour, plus blood patches, like a painted 128 skin."""
    key = (name, base, dark, blood, seed, emit)
    if key in MATS:
        return MATS[key]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    N, L = nt.nodes, nt.links
    bsdf = next(n for n in N if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Roughness"].default_value = 0.9
    tc = N.new("ShaderNodeTexCoord")
    snap = N.new("ShaderNodeVectorMath")
    snap.operation = "SNAP"
    snap.inputs[1].default_value = (P * 0.5, P * 0.5, P * 0.5)
    L.new(tc.outputs["Object"], snap.inputs[0])
    n1 = N.new("ShaderNodeTexNoise")
    n1.noise_dimensions = "4D"
    n1.inputs["W"].default_value = seed
    n1.inputs["Scale"].default_value = 7.0
    n1.inputs["Detail"].default_value = 4.0
    L.new(snap.outputs["Vector"], n1.inputs["Vector"])
    ramp = N.new("ShaderNodeValToRGB")
    b = lin(base)
    d = tuple(c * dark for c in b)
    ramp.color_ramp.elements[0].position, ramp.color_ramp.elements[1].position = 0.3, 0.7
    ramp.color_ramp.elements[0].color = (*d, 1)
    ramp.color_ramp.elements[1].color = (*b, 1)
    L.new(n1.outputs["Fac"], ramp.inputs["Fac"])
    out = ramp.outputs["Color"]
    if blood > 0:
        n2 = N.new("ShaderNodeTexNoise")
        n2.noise_dimensions = "4D"
        n2.inputs["W"].default_value = seed + 7.3
        n2.inputs["Scale"].default_value = 4.0
        n2.inputs["Detail"].default_value = 6.0
        L.new(snap.outputs["Vector"], n2.inputs["Vector"])
        r2 = N.new("ShaderNodeValToRGB")
        r2.color_ramp.elements[0].position = 0.72 - blood * 0.22
        r2.color_ramp.elements[1].position = 0.74 - blood * 0.22
        L.new(n2.outputs["Fac"], r2.inputs["Fac"])
        mix = N.new("ShaderNodeMix")
        mix.data_type = "RGBA"
        mix.inputs[7].default_value = (*lin("#4A1512"), 1)
        L.new(r2.outputs["Color"], mix.inputs[0])
        L.new(out, mix.inputs[6])
        out = mix.outputs[2]
    L.new(out, bsdf.inputs["Base Color"])
    if emit:
        L.new(out, bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = emit
    MATS[key] = m
    return m


# ---------------------------------------------------------------- builds (pixels): (w, length, depth)

AVG = dict(pelvis=(8, 3, 4.5), abd=(7.5, 5, 4), belly=0, chest=(8.5, 6, 4.5), neck=(3, 2.5, 3), head=(7, 5.5, 7),
           jaw=(6.4, 1.8, 6.2), uarm=(3.4, 7, 3.4), farm=(3, 6.5, 3), hand=(3.4, 4.2, 1.8), thigh=(3.8, 6.5, 3.8),
           shin=(3.4, 6.5, 3.4), foot=(3.8, 1.8, 6), shoulder=5.9, legx=2.1, balls=True)
HEAVY = dict(AVG, pelvis=(10, 3, 6), abd=(11, 6, 8), belly=1.6, chest=(11, 6.5, 6.5), neck=(4, 1.5, 4), head=(7.4, 5.5, 7.2),
             uarm=(4.4, 7, 4.4), farm=(3.8, 6, 3.8), hand=(3.8, 4, 2.2), thigh=(5, 6, 5), shin=(4.2, 6.2, 4.2),
             foot=(4.2, 1.8, 6.2), shoulder=7.4, legx=2.8)
LANKY = dict(AVG, pelvis=(7, 3, 4), abd=(6.5, 6, 3.6), chest=(8, 6.5, 4), neck=(2.6, 3.5, 2.6), head=(6.4, 6, 6.6),
             jaw=(5.8, 2, 5.8), uarm=(2.8, 8.5, 2.8), farm=(2.5, 8.5, 2.5), hand=(3, 5.2, 1.6), thigh=(3.2, 8, 3.2),
             shin=(2.9, 8, 2.9), foot=(3.4, 1.6, 6), shoulder=5.5, legx=1.9)
PLAYER = dict(pelvis=(8, 3, 4), abd=(8, 4.5, 4), belly=0, chest=(8, 6, 4), neck=(0, 0, 0), head=(8, 8, 8), jaw=(0, 0, 0),
              uarm=(4, 6, 4), farm=(4, 6, 4), hand=(0, 0, 0), thigh=(4, 6, 4), shin=(4, 6, 4), foot=(0, 0, 0),
              shoulder=6, legx=2, balls=False)


class Fig:
    def __init__(self, coll, spec, look, name):
        self.coll, self.s, self.k, self.name = coll, spec, look, name
        self.j, self.meshes = {}, []

    def joint(self, name, parent, off):
        e = bpy.data.objects.new(f"{self.name}.{name}", None)
        e.empty_display_size = 0.05
        self.coll.objects.link(e)
        if parent:
            e.parent = parent
        e.location = Vector(off) * P
        e.rotation_mode = "XYZ"
        self.j[name] = e
        return e

    def part(self, name, joint, mesh, m):
        o = bpy.data.objects.new(f"{self.name}.{name}", mesh)
        o.data.materials.append(m)
        o.parent = joint
        self.coll.objects.link(o)
        self.meshes.append(o)
        return o

    def boxp(self, name, joint, size, c, m):
        if min(size) <= 0:
            return None
        return self.part(name, joint, box_mesh(name, *size, c), m)

    def ball(self, name, joint, r, m):
        if self.s.get("balls") and r > 0:
            self.part(name, joint, ball_mesh(name, r), m)

    def build(self):
        s, k = self.s, self.k
        sk, sh, pa, shoe = k["skin"], k["shirt"], k["pants"], k["shoe"]
        long_sl, shorts = k.get("sleeves") == "long", k.get("shorts")
        miss = k.get("missing", ())
        hipz = s["thigh"][1] + s["shin"][1] + s["foot"][1]
        root = self.joint("root", None, (0, 0, 0))
        pel = self.joint("pelvis", root, (0, 0, hipz))
        self.boxp("pelvis", pel, (s["pelvis"][0], s["pelvis"][2], s["pelvis"][1]), (0, 0, 0), pa)
        sp1 = self.joint("spine1", pel, (0, 0, s["pelvis"][1] / 2))
        a = s["abd"]
        self.boxp("abd", sp1, (a[0], a[2], a[1]), (0, -s["belly"] / 2, a[1] / 2), sh)
        sp2 = self.joint("spine2", sp1, (0, 0, a[1]))
        c = s["chest"]
        self.boxp("chest", sp2, (c[0], c[2], c[1]), (0, 0, c[1] / 2), sh)
        nk = self.joint("neck", sp2, (0, -0.4, c[1]))
        n = s["neck"]
        self.boxp("neck", nk, (n[0], n[2], n[1] + 1), (0, 0, (n[1] - 1) / 2), sk)
        hd = self.joint("head", nk, (0, 0, n[1]))
        h, jw = s["head"], s["jaw"]
        self.boxp("head", hd, (h[0], h[2], h[1]), (0, -0.5, jw[1] + h[1] / 2), sk)
        if jw[0]:
            # the jaw hinges near the back; the dark inside of the mouth shows when it drops
            self.boxp("mouth", hd, (jw[0] - 1.2, jw[2] - 1.5, 0.6), (0, -0.4, jw[1] + 0.2), k["dark"])
            jj = self.joint("jaw", hd, (0, 1.6, jw[1]))
            self.boxp("jaw", jj, (jw[0], jw[2], jw[1]), (0, -1.6 - 0.5 - (h[2] - jw[2]) / 2 + 0.1, -jw[1] / 2), sk)
            ez = jw[1] + h[1] * 0.42
            fy = -0.5 - h[2] / 2
            for sx in (-1, 1):
                self.boxp(f"socket{sx}", hd, (2.0, 0.25, 1.4), (sx * 1.6, fy - 0.05, ez), k["dark"])
                self.boxp(f"eye{sx}", hd, (1.1, 0.3, 0.6), (sx * 1.6, fy - 0.12, ez - 0.1), k["eye"])
        hair = k.get("hair")
        if hair:
            hc, style = hair
            top = jw[1] + h[1]
            if style in ("short", "long"):
                self.boxp("hairtop", hd, (h[0] + 0.5, h[2] + 0.5, 1.6), (0, -0.4, top - 0.55), hc)
                ln = h[1] * 0.75 if style == "short" else h[1] + 3
                self.boxp("hairback", hd, (h[0] + 0.5, 1.2, ln), (0, -0.5 + h[2] / 2 + 0.2, top - ln / 2), hc)
                if style == "long":
                    for sx in (-1, 1):
                        self.boxp(f"hairside{sx}", hd, (0.8, h[2] * 0.55, h[1] + 1), (sx * (h[0] / 2 + 0.2), 1.2, top - (h[1] + 1) / 2), hc)
            elif style == "fringe":   # balding: a horseshoe round the back
                self.boxp("hairback", hd, (h[0] + 0.4, 1.0, 2.2), (0, -0.5 + h[2] / 2 + 0.2, top - 3.2), hc)
                for sx in (-1, 1):
                    self.boxp(f"hairside{sx}", hd, (0.6, h[2] * 0.5, 2.0), (sx * (h[0] / 2 + 0.1), 1.0, top - 3.2), hc)
        # arms
        for side, sx in (("R", -1), ("L", 1)):
            drop = k.get("dislocate") == side
            shj = self.joint("sh" + side, sp2, (sx * (s["shoulder"] + (1 if drop else 0)), 0, c[1] - 1.6 - (2.6 if drop else 0)))
            ua, fa, hn = s["uarm"], s["farm"], s["hand"]
            self.ball("shb" + side, shj, ua[0] * 0.62, sh)
            self.boxp("uarm" + side, shj, (ua[0], ua[2], ua[1] + 1), (0, 0, (1 - ua[1]) / 2), sh)
            el = self.joint("el" + side, shj, (0, 0, -ua[1]))
            arm_m = sh if long_sl else sk
            self.ball("elb" + side, el, fa[0] * 0.62, arm_m)
            self.boxp("farm" + side, el, (fa[0], fa[2], fa[1]), (0, 0, -fa[1] / 2), arm_m)
            wr = self.joint("wr" + side, el, (0, 0, -fa[1]))
            self.boxp("hand" + side, wr, (hn[0], hn[2], hn[1]), (0, 0, -hn[1] / 2 + 0.3), sk)
        # legs
        for side, sx in (("R", -1), ("L", 1)):
            hp = self.joint("hip" + side, pel, (sx * s["legx"], 0, -0.8))
            th, sn, ft = s["thigh"], s["shin"], s["foot"]
            self.boxp("thigh" + side, hp, (th[0], th[2], th[1] + 1), (0, 0, (1 - th[1]) / 2 - 0.2), pa)
            kn = self.joint("kn" + side, hp, (0, 0, -th[1]))
            if ("shin" + side) in miss:   # a torn-off leg: a short stump with a raw end
                self.ball("knb" + side, kn, sn[0] * 0.62, pa)
                self.boxp("stump" + side, kn, (sn[0], sn[2], 1.6), (0, 0, -0.8), pa)
                self.boxp("raw" + side, kn, (sn[0] - 0.6, sn[2] - 0.6, 0.5), (0, 0, -1.75), k["raw"])
                continue
            leg_m = sk if shorts else pa
            self.ball("knb" + side, kn, sn[0] * 0.64, pa)
            self.boxp("shin" + side, kn, (sn[0], sn[2], sn[1]), (0, 0, -sn[1] / 2), leg_m)
            an = self.joint("an" + side, kn, (0, 0, -sn[1]))
            self.boxp("foot" + side, an, (ft[0], ft[2], ft[1]), (0, -ft[2] / 2 + 1.4, -ft[1] / 2), shoe)
        return self

    def pose(self, P_, loc, yaw):
        for jn, rot in P_.items():
            if jn == "lift":
                continue
            r = rot if isinstance(rot, (tuple, list)) else (rot, 0, 0)
            self.j[jn].rotation_euler = tuple(math.radians(v) for v in r)
        root = self.j["root"]
        root.location = Vector(loc)
        root.rotation_euler = (0, 0, math.radians(yaw))
        bpy.context.view_layer.update()
        low = min((o.matrix_world @ Vector(cn)).z for o in self.meshes for cn in o.bound_box)
        root.location.z += loc[2] - low + P_.get("lift", 0) * P
        return self


# ---------------------------------------------------------------- the seven types and the player for scale

def look(skin, shirt, pants, shoe="#1E1B19", seed=0.0, blood=0.35, **extra):
    k = dict(skin=mat("skin", skin, 0.62, blood * 0.8, seed), shirt=mat("shirt", shirt, 0.55, blood, seed + 1),
             pants=mat("pants", pants, 0.6, blood * 0.5, seed + 2), shoe=mat("shoe", shoe, 0.6, 0, seed + 3),
             dark=mat("dark", "#140C0B", 0.8), eye=mat("eye", "#C9CCC0", 0.85, 0, 0, emit=0.15), raw=mat("raw", "#6A1E18", 0.6, 0.6, seed))
    for name, v in extra.items():
        if name == "hair":
            k["hair"] = (mat("hair", v[0], 0.55, 0, seed + 4), v[1])
        else:
            k[name] = v
    return k


TYPES = [
    # key, label, build, look, pose
    ("player", "参照：玩家身体", PLAYER,
     dict(skin="#C69A7C", shirt="#3E6A86", pants="#2E3550", blood=0, hair=("#3A2A1E", "short")),
     {}),
    ("wander", "1 游荡者：拖步、垂头、一肩低", AVG,
     dict(skin="#7E8275", shirt="#4C5F6E", pants="#2C3A55", hair=("#2A2420", "short")),
     dict(pelvis=(6, 4, 0), spine1=(12, 0, 0), spine2=(12, -4, 0), neck=(22, 0, 0), head=(16, -20, 12), jaw=14,
          shR=(-12, 4, 0), elR=(-12, 0, 0), wrR=(12, 0, 0), shL=(10, -3, 0), elL=(-8, 0, 0),
          hipR=(-20, 0, 0), knR=(14, 0, 0), anR=(-6, 0, 0), hipL=(16, 0, 0), knL=(34, 0, 0), anL=(22, 0, 0))),
    ("lunge", "2 扑击者：前扑、双手前伸、张嘴", AVG,
     dict(skin="#848679", shirt="#7A2A26", pants="#3C4A63", hair=("#4A3522", "short"), seed=3),
     dict(pelvis=(24, 0, 0), spine1=(14, 0, 0), spine2=(10, 0, 0), neck=(-14, 0, 0), head=(-22, 0, 0), jaw=38,
          shR=(-118, 14, 0), elR=(-18, 0, 0), wrR=(-24, 0, 0), shL=(-92, -18, 0), elL=(-34, 0, 0), wrL=(-20, 0, 0),
          hipR=(-48, 0, 0), knR=(46, 0, 0), anR=(-10, 0, 0), hipL=(36, 0, 0), knL=(62, 0, 0), anL=(34, 0, 0))),
    ("crawl", "3 爬行者：断腿，用手拖着身子", AVG,
     dict(skin="#7A7E70", shirt="#5A5E4A", pants="#3A3A36", hair=("#1E1A18", "long"), seed=5, blood=0.55,
          missing=("shinR",)),
     dict(pelvis=(78, 0, 0), spine1=(4, 0, 0), spine2=(8, 0, 0), neck=(-46, 0, 0), head=(-26, 0, 8), jaw=26,
          shR=(-176, 8, 0), elR=(-6, 0, 0), wrR=(-40, 0, 0), shL=(-94, -6, 0), elL=(-86, 0, 0), wrL=(-10, 0, 0),
          hipR=(-8, 4, 0), knR=(4, 0, 0), hipL=(-4, -10, 0), knL=(-30, 0, -20), anL=(30, 0, 0))),
    ("broken", "4 歪脖子：颈断、右臂脱臼", AVG,
     dict(skin="#80837A", shirt="#B4AE9E", pants="#20242C", hair=("#5E4630", "long"), seed=7, blood=0.5,
          dislocate="R"),
     dict(pelvis=(4, 7, 0), spine1=(6, 8, 0), spine2=(10, 6, -6), neck=(12, -38, 0), head=(6, -44, -10), jaw=22,
          shR=(4, -3, 0), elR=(-2, 0, 0), wrR=(8, 0, 0), shL=(-62, -12, 0), elL=(-30, 0, 0), wrL=(22, 0, 0),
          hipR=(-10, 0, 0), knR=(16, 0, 0), anR=(0, 0, 38), hipL=(12, 0, 0), knL=(22, 0, 0), anL=(10, 0, 0))),
    ("heavy", "5 胖子：后仰挺肚、两臂外张", HEAVY,
     dict(skin="#7C7F72", shirt="#C9C4B5", pants="#34383E", hair=("#5A5650", "fringe"), seed=9, blood=0.45),
     dict(spine1=(-6, 0, 0), spine2=(-2, 0, 0), neck=(26, 0, 0), head=(10, -10, 0), jaw=16,
          shR=(-24, 26, 0), elR=(-28, 0, 0), wrR=(10, 0, 0), shL=(-8, -30, 0), elL=(-34, 0, 0),
          hipR=(-10, 7, 0), knR=(10, 0, 0), anR=(-4, -6, 0), hipL=(8, -7, 0), knL=(14, 0, 0), anL=(2, 6, 0))),
    ("lanky", "6 瘦高：长臂、耸肩、伸头", LANKY,
     dict(skin="#86887C", shirt="#2E3A30", pants="#4C4232", hair=("#3A2E26", "short"), seed=11, sleeves="long"),
     dict(spine1=(16, 0, 0), spine2=(26, 0, 0), neck=(44, 0, 0), head=(-34, 6, 0), jaw=10,
          shR=(-22, 6, 0), elR=(-26, 0, 0), wrR=(24, 0, 0), shL=(-38, -6, 0), elL=(-46, 0, 0), wrL=(30, 0, 0),
          hipR=(-26, 0, 0), knR=(38, 0, 0), anR=(-10, 0, 0), hipL=(6, 0, 0), knL=(22, 0, 0), anL=(10, 0, 0))),
    ("feed", "7 啃食者：蹲着弓背、手捧到嘴边", AVG,
     dict(skin="#7E8174", shirt="#3C6A72", pants="#2E3446", hair=("#2A2420", "long"), seed=13, blood=0.75),
     dict(pelvis=(20, 0, 0), spine1=(30, 0, 0), spine2=(32, 0, 0), neck=(-6, 0, 0), head=(-18, 0, 0), jaw=40,
          shR=(-80, 8, 26), elR=(-115, 0, 0), wrR=(-30, 0, 0), shL=(-72, -8, -26), elL=(-120, 0, 0), wrL=(-30, 0, 0),
          hipR=(-105, 10, 0), knR=(145, 0, 0), anR=(-40, 0, 0), hipL=(-95, -10, 0), knL=(140, 0, 0), anL=(-45, 0, 0))),
]

# Motion sheet: four key poses each. "lift" raises the figure off the ground (px) for the leap.
WANDER_BASE = dict(pelvis=(6, 4, 0), spine1=(12, 0, 0), spine2=(12, -4, 0), neck=(22, 0, 0), head=(16, -20, 12), jaw=14,
                   wrR=(12, 0, 0))
MOTIONS = [
    ("wander", "游荡者 拖步（左腿拖地）", -62, [
        dict(WANDER_BASE, shR=(-20, 4, 0), elR=(-12, 0, 0), shL=(14, -3, 0), elL=(-8, 0, 0),
             hipR=(-22, 0, 0), knR=(12, 0, 0), anR=(-8, 0, 0), hipL=(18, 0, 0), knL=(34, 0, 0), anL=(26, 0, 0)),
        dict(WANDER_BASE, pelvis=(6, 9, 0), shR=(-8, 4, 0), elR=(-12, 0, 0), shL=(6, -3, 0), elL=(-8, 0, 0),
             hipR=(-2, 0, 0), knR=(6, 0, 0), hipL=(2, 0, 0), knL=(48, 0, 0), anL=(40, 0, 0)),
        dict(WANDER_BASE, shR=(10, 4, 0), elR=(-12, 0, 0), shL=(-10, -3, 0), elL=(-8, 0, 0),
             hipR=(14, 0, 0), knR=(18, 0, 0), anR=(10, 0, 0), hipL=(-10, 0, 0), knL=(16, 0, 0), anL=(24, 0, 0)),
        dict(WANDER_BASE, pelvis=(6, -2, 0), shR=(-2, 4, 0), elR=(-12, 0, 0), shL=(4, -3, 0), elL=(-8, 0, 0),
             hipR=(-6, 0, 0), knR=(38, 0, 0), anR=(18, 0, 0), hipL=(4, 0, 0), knL=(10, 0, 0), anL=(16, 0, 0)),
    ]),
    ("lunge", "扑击者 蓄力 → 扑 → 抓 → 咬", -70, [
        dict(pelvis=(30, 0, 0), spine1=(20, 0, 0), spine2=(15, 0, 0), neck=(-20, 0, 0), head=(-25, 0, 0), jaw=10,
             shR=(30, 10, 0), elR=(-40, 0, 0), shL=(25, -10, 0), elL=(-40, 0, 0),
             hipR=(-70, 0, 0), knR=(100, 0, 0), anR=(-30, 0, 0), hipL=(-40, 0, 0), knL=(90, 0, 0), anL=(-20, 0, 0)),
        dict(lift=9, pelvis=(45, 0, 0), spine1=(10, 0, 0), spine2=(5, 0, 0), neck=(-30, 0, 0), head=(-20, 0, 0), jaw=45,
             shR=(-150, 15, 0), elR=(-10, 0, 0), wrR=(-20, 0, 0), shL=(-140, -15, 0), elL=(-15, 0, 0), wrL=(-20, 0, 0),
             hipR=(-60, 0, 0), knR=(70, 0, 0), hipL=(40, 0, 0), knL=(40, 0, 0), anL=(40, 0, 0)),
        dict(pelvis=(25, 0, 0), spine1=(15, 0, 0), spine2=(10, 0, 0), neck=(-10, 0, 0), head=(-10, 0, 0), jaw=50,
             shR=(-100, -10, -20), elR=(-40, 0, 0), wrR=(-30, 0, 0), shL=(-95, 10, 20), elL=(-45, 0, 0), wrL=(-30, 0, 0),
             hipR=(-50, 0, 0), knR=(40, 0, 0), hipL=(30, 0, 0), knL=(50, 0, 0), anL=(20, 0, 0)),
        dict(pelvis=(30, 0, 0), spine1=(25, 0, 0), spine2=(20, 0, 0), neck=(10, 0, 0), head=(20, 0, 0), jaw=30,
             shR=(-70, 0, 20), elR=(-100, 0, 0), wrR=(-20, 0, 0), shL=(-65, 0, -20), elL=(-105, 0, 0), wrL=(-20, 0, 0),
             hipR=(-40, 0, 0), knR=(50, 0, 0), anR=(-10, 0, 0), hipL=(25, 0, 0), knL=(40, 0, 0)),
    ]),
    ("crawl", "爬行者 伸手 → 拖 → 换手 → 拖", -70, [
        dict(TYPES[3][4]),
        dict(TYPES[3][4], pelvis=(80, 0, 0), head=(-20, 0, 4), shR=(-122, 8, 0), elR=(-72, 0, 0), shL=(-112, -6, 0), elL=(-40, 0, 0)),
        dict(TYPES[3][4], head=(-26, 0, -8), shR=(-94, 6, 0), elR=(-86, 0, 0), wrR=(-10, 0, 0), shL=(-176, -8, 0), elL=(-6, 0, 0), wrL=(-40, 0, 0)),
        dict(TYPES[3][4], pelvis=(80, 0, 0), head=(-20, 0, -4), shR=(-112, 6, 0), elR=(-40, 0, 0), shL=(-122, -8, 0), elL=(-72, 0, 0)),
    ]),
]


# ---------------------------------------------------------------- scene

bpy.ops.wm.read_homefile(use_empty=True)
sc = bpy.context.scene
FONT = None
for f in (r"C:\Windows\Fonts\msyh.ttc", r"C:\Windows\Fonts\simhei.ttf"):
    try:
        FONT = bpy.data.fonts.load(f)
        break
    except Exception:
        pass


def text(coll, s, loc, size, m):
    cu = bpy.data.curves.new("t", "FONT")
    cu.body = s
    cu.size = size
    if FONT:
        cu.font = FONT
    o = bpy.data.objects.new("label", cu)
    o.location = loc
    o.rotation_euler = (math.radians(90), 0, 0)
    o.data.materials.append(m)
    coll.objects.link(o)
    return o


YAW = dict(player=-30, wander=-30, lunge=-62, crawl=-70, broken=-24, heavy=-30, lanky=-55, feed=-58)
ink = mat("ink", "#1E2226", 1.0)
floor_m = mat("floor", "#A7ABAA", 0.92)


def floor_row(x0, x1, z):
    # floors receive shadows but cast none, so a row above does not shade the row below
    o = bpy.data.objects.new("floor", box_mesh("floor", (x1 - x0) / P, 120, 0.2, (0, 0, -0.1)))
    o.location = ((x0 + x1) / 2, 3.2, z)
    o.data.materials.append(floor_m)
    o.visible_shadow = False
    sc.collection.objects.link(o)
    return o


# sheet 1: the player for scale and the seven types, two rows of four
SP, RZ = 3.1, 4.4
for i, (key, label, spec, lk, pose) in enumerate(TYPES):
    x, z = (i % 4) * SP, RZ if i < 4 else 0
    Fig(sc.collection, spec, look(**lk), key).build().pose(pose, (x, 0, z), YAW[key])
    text(sc.collection, label, (x - 1.35, -2.5, z + 2.85), 0.17, ink)
for z in (RZ, 0):
    floor_row(-1.6, 3 * SP + 1.6, z)

# sheet 2: motion, three rows of four key poses, far to the side of sheet 1
MX, MSP = 100, 2.7
for row, (key, label, yaw, frames) in enumerate(MOTIONS):
    z = (2 - row) * RZ
    spec, lk = next((t[2], t[3]) for t in TYPES if t[0] == key)
    for i, pz in enumerate(frames):
        Fig(sc.collection, spec, look(**lk), f"{key}{i}").build().pose(pz, (MX + i * MSP, 0, z), yaw)
        text(sc.collection, str(i + 1), (MX + i * MSP - 0.1, -2.5, z + 2.75), 0.17, ink)
    text(sc.collection, label, (MX - 1.3, -2.5, z + 3.2), 0.19, ink)
    floor_row(MX - 1.6, MX + 3 * MSP + 1.6, z)

world = bpy.data.worlds.new("bg")
world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == "BACKGROUND")
bg.inputs[0].default_value = (*lin("#B9BEC0"), 1)
bg.inputs[1].default_value = 0.9
sc.world = world
sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
sun.data.energy = 3.2
sun.data.angle = math.radians(8)
sun.rotation_euler = (math.radians(50), math.radians(-12), math.radians(-38))
sc.collection.objects.link(sun)

r = sc.render
try:
    r.engine = "BLENDER_EEVEE_NEXT"
except TypeError:
    r.engine = "BLENDER_EEVEE"
sc.view_settings.view_transform = "Standard"
sc.view_settings.look = "None"
r.image_settings.file_format = "PNG"
r.resolution_percentage = 100


def shoot(cx, z0, z1, w, out, px=2400):
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    sc.collection.objects.link(cam)
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = w
    pitch = 7
    cam.rotation_euler = (math.radians(90 - pitch), 0, 0)
    cam.location = (cx, -30, (z0 + z1) / 2 + 30 * math.tan(math.radians(pitch)))
    sc.camera = cam
    r.resolution_x, r.resolution_y = px, round(px * (z1 - z0) / w)
    r.filepath = os.path.join(HERE, out)
    bpy.ops.render.render(write_still=True)


W1 = 4 * SP + 0.2
shoot(1.5 * SP, -0.75, RZ + 3.3, W1, f"z0_concept_{ROUND}.png")
W2 = 4 * MSP + 2.8
shoot(MX + 1.5 * MSP - 0.3, -0.75, 2 * RZ + 3.6, W2, f"z0_concept_motion_{ROUND}.png")

# silhouettes of sheet 1: every figure flat black, floors hidden
vl = sc.view_layers[0]
vl.material_override = mat("silhouette", "#0E0F10", 1.0)
floors = [o for o in sc.objects if o.name.startswith("floor")]
for o in floors:
    o.hide_render = True
shoot(1.5 * SP, -0.75, RZ + 3.3, W1, f"z0_concept_silhouettes_{ROUND}.png", 1600)
vl.material_override = None
for o in floors:
    o.hide_render = False
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "z0_concept.blend"))
print("ok")
