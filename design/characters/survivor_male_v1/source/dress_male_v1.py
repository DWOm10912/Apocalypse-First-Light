"""Male V1 look: painted face, hair, default outfit and painted textures (preview only, not in the game).

Run inside survivor_male_v1.blend after build_survivor_male_v1 (body, head). Nothing here touches the rig, the
bones or any animation: the garments are offset copies of the body surface, the hair is a shell over the head.
All textures are painted procedurally from 3D positions (world mm); no outside images or models are used.

Stages: uv_head, face, hair, outfit, paint, render.
"""
import math
import os
import sys

import bpy
import bmesh
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

SRC = os.path.dirname(os.path.abspath(__file__))
H1 = os.path.normpath(os.path.join(SRC, '..', '..', 'survivor_base_h1p1', 'source'))
for p in (SRC, H1):
    if p not in sys.path:
        sys.path.insert(0, p)
import head_male_v1 as H        # noqa: E402
import face_male_v1 as F        # noqa: E402

TEXEL_PART = None               # per texel vertex attribute 'part' of the object being painted (paint sets it)

M = 0.001
COLL = 'Male_V1'
TEX = os.path.normpath(os.path.join(SRC, '..', 'textures'))
RENDERS = os.path.normpath(os.path.join(SRC, '..', 'renders'))


# ---------------------------------------------------------------- helpers

def collection():
    c = bpy.data.collections.get(COLL) or bpy.data.collections.new(COLL)
    if c.name not in bpy.context.scene.collection.children:
        bpy.context.scene.collection.children.link(c)
    return c


def replace_object(name, me):
    old = bpy.data.objects.get(name)
    if old:
        oldme = old.data
        bpy.data.objects.remove(old)
        if oldme.users == 0:
            bpy.data.meshes.remove(oldme)
    ob = bpy.data.objects.new(name, me)
    collection().objects.link(ob)
    return ob


def tri_count(ob):
    return sum(len(p.vertices) - 2 for p in ob.data.polygons)


def smooth(x, a, b):
    t = np.clip((np.asarray(x, float) - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    t = np.asarray(t, float)[..., None]
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


def vnoise(P, scale, seed=0):
    """Value noise in 3D (P in mm, scale = feature size mm), range 0..1, vectorised."""
    q = np.asarray(P, float) / scale + seed * 17.13
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)

    def h(dx, dy, dz):
        n = (i[:, 0] + dx) * 73856093 ^ (i[:, 1] + dy) * 19349663 ^ (i[:, 2] + dz) * 83492791 ^ seed * 2654435761
        n = (n ^ (n >> 13)) * 1274126177
        return ((n ^ (n >> 16)) & 0xffff) / 65535.0
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[:, 0] if dx else 1 - f[:, 0]) * (f[:, 1] if dy else 1 - f[:, 1]) * (f[:, 2] if dz else 1 - f[:, 2])
                out = out + w * h(dx, dy, dz)
    return out


def fbm(P, scale, seed=0, octaves=3):
    s, a, tot, out = scale, 1.0, 0.0, 0.0
    for o in range(octaves):
        out = out + a * vnoise(P, s, seed + o)
        tot += a
        s *= 0.5
        a *= 0.5
    return out / tot


def bones():
    """Deform bones (fingers folded into the hand) as (head, tail) in mm."""
    out = {}
    for b in bpy.data.objects['survivor_rig'].data.bones:
        n = b.name
        if not b.use_deform or n == 'root' or n.split('_')[0] in ('thumb', 'index', 'middle', 'ring', 'pinky'):
            continue
        out[n] = (np.array(b.head_local) / M, np.array(b.tail_local) / M)
    return out


def seg_t(P, a, b):
    ab = b - a
    return ((P - a) @ ab) / (ab @ ab)


def labels(P):
    """Nearest bone per point, plus forearm (elbow -> wrist) and shin (knee -> ankle) parameters."""
    B = bones()
    names = list(B)
    D = []
    for n in names:
        a, b = B[n]
        t = np.clip(seg_t(P, a, b), 0, 1)
        D.append(np.linalg.norm(P - (a + t[:, None] * (b - a)), axis=1))
    lab = np.array(names)[np.array(D).argmin(0)]
    side = np.where(P[:, 0] < 0, 'r', 'l')
    tfa = np.zeros(len(P))
    tsh = np.zeros(len(P))
    for s in 'rl':
        m = side == s
        tfa[m] = seg_t(P[m], B['forearm_' + s][0], B['hand_' + s][0])
        tsh[m] = seg_t(P[m], B['shin_' + s][0], B['foot_' + s][0])
    return lab, side, tfa, tsh


def plane_cut(bm, co, no, keep_negative=True, where=None):
    """Bisect bm at a plane (mm) and delete the positive side (or the negative side); `where(v)` limits the cut."""
    geom = [e for e in bm.edges if where is None or all(where(v) for v in e.verts)]
    geom += [f for f in bm.faces if where is None or all(where(v) for v in f.verts)]
    geom += [v for v in bm.verts if where is None or where(v)]
    co, no = Vector(co), Vector(no).normalized()
    bmesh.ops.bisect_plane(bm, geom=geom, dist=0.01, plane_co=co, plane_no=no)
    dead = [f for f in bm.faces
            if (where is None or all(where(v) for v in f.verts))
            and ((sum(((v.co - co).dot(no) for v in f.verts), 0.0) / len(f.verts)) > 0) == keep_negative]
    bmesh.ops.delete(bm, geom=dead, context='FACES')


# ---------------------------------------------------------------- UVs and painting

def uv_cylinder(ob, y0=12.0, z0=1515.0, z1=1780.0, v0=0.0, skip=()):
    """u = angle round the head (front at 0.5), v = height (from v0 up); the seam is at the back of the head."""
    me = ob.data
    uvl = me.uv_layers.get('UVMap') or me.uv_layers.new(name='UVMap')
    for p in me.polygons:
        if p.index in skip:
            continue
        us = []
        for li in p.loop_indices:
            co = me.vertices[me.loops[li].vertex_index].co / M
            us.append((0.5 + math.atan2(co.x, -(co.y - y0)) / (2 * math.pi), v0 + (1 - v0) * (co.z - z0) / (z1 - z0)))
        if max(u for u, _ in us) - min(u for u, _ in us) > 0.5:
            us = [(u + 1 if u < 0.5 else u, v) for u, v in us]
        for li, uv in zip(p.loop_indices, us):
            uvl.data[li].uv = uv


def uv_smart(ob, margin=0.01):
    bpy.ops.object.select_all(action='DESELECT')
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.uv.smart_project(angle_limit=math.radians(60), island_margin=margin, scale_to_bounds=True)
    bpy.ops.object.mode_set(mode='OBJECT')
    ob.select_set(False)


def raster(ob, W, Hh):
    """Per texel: the 3 vertex ids and barycentrics of the triangle covering it (-1 where empty)."""
    me = ob.data
    me.calc_loop_triangles()
    uvl = me.uv_layers.active.data
    vid = -np.ones((Hh, W, 3), np.int64)
    bar = np.zeros((Hh, W, 3))
    tris = []
    for t in me.loop_triangles:
        uv = np.array([uvl[li].uv[:] for li in t.loops]) * (W, Hh)
        tris.append((t, uv))
        if uv[:, 0].max() > W:
            tris.append((t, uv - (W, 0)))
    for t, uv in tris:
        x0, y0 = np.floor(uv.min(0)).astype(int) - 1
        x1, y1 = np.ceil(uv.max(0)).astype(int) + 1
        x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, W), min(y1, Hh)
        if x1 <= x0 or y1 <= y0:
            continue
        xs, ys = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        a, b, c = uv
        v0, v1 = b - a, c - a
        d = v0[0] * v1[1] - v0[1] * v1[0]
        if abs(d) < 1e-9:
            continue
        px, py = xs - a[0], ys - a[1]
        l1 = (px * v1[1] - py * v1[0]) / d
        l2 = (v0[0] * py - v0[1] * px) / d
        l0 = 1 - l1 - l2
        e = -0.6 / max(1.0, math.sqrt(abs(d)))
        m = (l0 >= e) & (l1 >= e) & (l2 >= e)
        iy = (ys[m] - 0.5).astype(int)
        ix = (xs[m] - 0.5).astype(int)
        vid[iy, ix] = t.vertices[:]
        bar[iy, ix] = np.stack([l0[m], l1[m], l2[m]], 1)
    return vid, bar


def vertex_ao(ob, bvh, rays=40, dist=0.16):
    """Ambient occlusion per vertex against the whole character (0 open .. 1 closed)."""
    rng = np.random.default_rng(7)
    u = rng.random((rays, 2))
    th = np.arccos(np.sqrt(1 - u[:, 0]))
    ph = 2 * math.pi * u[:, 1]
    loc = np.stack([np.sin(th) * np.cos(ph), np.sin(th) * np.sin(ph), np.cos(th)], 1)
    me = ob.data
    out = np.zeros(len(me.vertices))
    for i, v in enumerate(me.vertices):
        n = v.normal
        if n.length < 1e-6:
            continue
        t1 = n.orthogonal().normalized()
        t2 = n.cross(t1)
        o = v.co + n * 0.0015
        hit = 0.0
        for x, y, z in loc:
            d = t1 * x + t2 * y + n * z
            r = bvh.ray_cast(o, d, dist)
            if r[0] is not None:
                hit += 1.0 - r[3] / dist * 0.6
        out[i] = hit / rays
    return out


def dilate(img, mask, steps=6):
    img, mask = img.copy(), mask.copy()
    for _ in range(steps):
        acc = np.zeros_like(img)
        cnt = np.zeros(mask.shape)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            m = np.roll(mask, (dy, dx), (0, 1))
            acc += np.roll(img, (dy, dx), (0, 1)) * m[..., None]
            cnt += m
        grow = (~mask) & (cnt > 0)
        img[grow] = acc[grow] / cnt[grow][:, None]
        mask = mask | grow
    return img


def paint(ob, W, Hh, fn, bvh, name):
    """fn(P mm, N, ao, uv) -> sRGB colours; writes textures/<name>.png and a material using it."""
    vid, bar = raster(ob, W, Hh)
    mask = vid[..., 0] >= 0
    me = ob.data
    co = np.array([v.co[:] for v in me.vertices]) / M
    no = np.array([v.normal[:] for v in me.vertices])
    ao = vertex_ao(ob, bvh)
    V, Bc = vid[mask], bar[mask]
    global TEXEL_PART
    pa = me.attributes.get('part')
    TEXEL_PART = None
    if pa is not None and pa.domain == 'POINT':
        pv = np.array([d.value for d in pa.data])
        TEXEL_PART = pv[V[np.arange(len(V)), Bc.argmax(1)]]
    P = (co[V] * Bc[..., None]).sum(1)
    N = (no[V] * Bc[..., None]).sum(1)
    N /= np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
    A = (ao[V] * Bc).sum(1)
    ys, xs = np.nonzero(mask)
    UV = np.stack([(xs + 0.5) / W, (ys + 0.5) / Hh], 1)
    img = np.zeros((Hh, W, 3))
    img[mask] = np.clip(fn(P, N, A, UV), 0, 1)
    img = dilate(img, mask, 8)
    os.makedirs(TEX, exist_ok=True)
    path = os.path.join(TEX, name + '.png')
    im = bpy.data.images.get(name) or bpy.data.images.new(name, W, Hh, alpha=False)
    if tuple(im.size) != (W, Hh):
        im.scale(W, Hh)
    rgba = np.concatenate([img, np.ones((Hh, W, 1))], 2)
    im.pixels.foreach_set(rgba.astype(np.float32).ravel())
    im.filepath_raw = path
    im.file_format = 'PNG'
    im.save()
    material(ob, name, im)
    return path


def material(ob, name, im):
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    bsdf = nt.nodes.new('ShaderNodeBsdfPrincipled')
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = im
    tex.interpolation = 'Linear'
    nt.links.new(tex.outputs[0], bsdf.inputs['Base Color'])
    nt.links.new(bsdf.outputs[0], out.inputs[0])
    bsdf.inputs['Roughness'].default_value = 0.85
    nt.nodes.active = tex
    ob.data.materials.clear()
    ob.data.materials.append(mat)


def character_bvh(names):
    verts, polys = [], []
    for n in names:
        ob = bpy.data.objects.get(n)
        if ob is None:
            continue
        base = len(verts)
        mw = ob.matrix_world
        verts += [mw @ v.co for v in ob.data.vertices]
        polys += [[base + i for i in p.vertices] for p in ob.data.polygons]
    return BVHTree.FromPolygons(verts, polys)


# ---------------------------------------------------------------- colours (sRGB)

SKIN = np.array([0.78, 0.60, 0.48])
SKIN_SHADE = np.array([0.58, 0.40, 0.32])
SKIN_FLUSH = np.array([0.80, 0.53, 0.45])
HAIR = np.array([0.23, 0.17, 0.12])
HAIR_LIGHT = np.array([0.40, 0.30, 0.21])
STUBBLE = np.array([0.33, 0.25, 0.20])
LIP = np.array([0.64, 0.42, 0.37])
LINE = np.array([0.16, 0.11, 0.09])
SCLERA = np.array([0.82, 0.78, 0.72])
IRIS = np.array([0.33, 0.24, 0.16])
DIRT = np.array([0.36, 0.30, 0.23])
JACKET = np.array([0.52, 0.40, 0.26])        # faded brown duck canvas
JACKET_DARK = np.array([0.30, 0.22, 0.15])   # corduroy collar, cuff lining
TEE = np.array([0.36, 0.36, 0.35])            # dark heather grey tee
DENIM = np.array([0.30, 0.37, 0.47])
DENIM_FADE = np.array([0.50, 0.56, 0.62])
STITCH = np.array([0.72, 0.58, 0.36])
LEATHER = np.array([0.33, 0.22, 0.14])
SOLE = np.array([0.12, 0.11, 0.10])
BELT = np.array([0.24, 0.16, 0.11])
STEEL = np.array([0.55, 0.55, 0.53])


def line_mask(d, width, soft=0.6):
    """1 within width/2 of a signed distance d, soft edge (mm)."""
    return 1 - smooth(np.abs(d), width / 2, width / 2 + soft)


def ellipse(P, cx, cz, rx, rz):
    return np.sqrt(((P[:, 0] - cx) / rx) ** 2 + ((P[:, 2] - cz) / rz) ** 2)


def grime(P, A, base, amount=1.0, seed=1):
    """Shared wear: painted AO, low-frequency mottling, dust towards the ground."""
    n = fbm(P, 60, seed)
    c = base * (0.88 + 0.22 * n)[:, None]
    c = mix(c, c * 0.55, np.clip(A * 1.3, 0, 0.8))
    low = (1 - smooth(P[:, 2], 80, 520)) * 0.45 * amount
    spots = smooth(fbm(P, 25, seed + 5), 0.55, 0.75) * 0.35 * amount
    return mix(c, DIRT, np.clip(low + spots, 0, 0.75))


# ---------------------------------------------------------------- face (painted on the head)

def hairline_z(a_deg):
    """Hairline height (mm) at a column angle from the front (deg, either side)."""
    A = [0, 20, 35, 50, 62, 72, 79, 88, 97, 106, 118, 132, 148, 164, 180]
    Z = [1716, 1715, 1710, 1702, 1696, 1688, 1652, 1674, 1673, 1666, 1652, 1642, 1636, 1633, 1632]
    return np.interp(np.abs(a_deg), A, Z)


def paint_head(P, N, A, UV):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    ax = np.abs(x)
    ang = np.degrees(np.arctan2(x, -(y - 12.0)))
    front = smooth(-y, 10, 50)
    c = np.tile(SKIN, (len(P), 1)) * (0.94 + 0.10 * fbm(P, 30, 3))[:, None]
    # warm cheeks, nose and ears; cooler under the jaw
    flush = (1 - smooth(ellipse(P, 38, 1630, 22, 16), 0.4, 1.0)) * (ax > 18)
    flush = np.maximum(flush, (1 - smooth(ellipse(P, 0, 1625, 10, 14), 0.3, 1.0)) * front)
    ear = (ax > 79) & (np.abs(y - 14) < 20) & (z > 1615) & (z < 1678)
    c = mix(c, SKIN_FLUSH, np.clip(flush * 0.55 + ear * 0.35, 0, 1))
    c = mix(c, SKIN_SHADE, np.clip(A * 1.4, 0, 0.85))
    # stubble: a few days' growth, light; weight on the chin, the upper lip and along the jaw, cheeks faint
    cheek_top = np.interp(ax, [0, 22, 40, 58, 72, 80], [1606, 1598, 1588, 1590, 1608, 1625])
    beard = smooth(cheek_top - z, -2, 6) * (1 - smooth(np.abs(ang), 95, 110))
    chin = (1 - smooth(ellipse(P, 0, 1570, 32, 20), 0.5, 1.1)) * front
    jl = np.interp(np.abs(ang), [0, 40, 80, 100], [1553, 1560, 1585, 1595])
    jaw = (1 - smooth(np.abs(z - jl), 4, 12)) * (1 - smooth(np.abs(ang), 95, 110))
    neck = smooth(z, 1532, 1556)
    moust = (1 - smooth(ellipse(P, 0, 1605, 22, 4.6), 0.5, 1.0)) * front
    speck = 0.75 + 0.5 * vnoise(P, 1.6, 9)
    b = np.clip(np.maximum((beard * 0.18 + chin * 0.2 + jaw * 0.13) * neck, moust * 0.3) * speck, 0, 0.45)
    c = mix(c, STUBBLE, b)
    # lips: muted, the lower a little lighter; a thin dark line in the slit
    lips = (1 - smooth(ellipse(P, 0, 1593, 21, 7.5), 0.6, 1.0)) * front
    c = mix(c, LIP, lips * 0.5)
    c = mix(c, LIP * 1.05, lips * (z < F.MOUTH_Z - 1.5) * 0.15)
    mouth = line_mask(z - F.mouth_line(x), 0.9, 0.5) * (ax < F.CORNER_X) * front
    c = mix(c, LINE * 1.5, mouth * 0.6)
    # philtrum groove, nostrils under the wings, faint folds from the wings to the mouth corners
    phil = (1 - smooth(ax, 1.5, 3.5)) * (z > 1601) * (z < 1610) * front
    c = mix(c, SKIN_SHADE, phil * 0.25)
    for s in (-1, 1):
        nost = 1 - smooth(ellipse(P, 7.0 * s, 1612, 4.5, 2.4), 0.4, 1.0)
        c = mix(c, LINE * 1.6, nost * front * 0.7)
        t = np.clip((1616 - z) / 24, 0, 1)
        fold = line_mask(x * s - (19 + 6 * t), 2.0, 1.5) * (z < 1617) * (z > 1590) * front
        c = mix(c, SKIN_SHADE, fold * 0.22)
    # eyes: real openings (face_male_v1); the lids carry a heavy lash line, a crease and the lower lid shade
    for s in (-1, 1):
        ex, ez = F.EYE_X * s, F.EYE_Z
        u = (x - ex) * s                       # + outward
        half = F.EYE_HALF
        tt = np.clip(u / half, -1, 1)
        top, bot = F.eye_top(tt), F.eye_bot(tt)
        lidskin = (z > top) * (z < top + 3.2) * (np.abs(u) < half + 1) * (y < -20)
        c = mix(c, SKIN_SHADE, lidskin * 0.12)
        lid = line_mask(z - top - 0.4, 1.4, 0.6) * (np.abs(u) < half + 1.0) * (y < -20)
        c = mix(c, LINE * 1.2, lid * 0.8)
        crease = line_mask(z - top - 3.6, 1.4, 1.0) * (np.abs(u + 1) < half) * (y < -20)
        c = mix(c, SKIN_SHADE, crease * 0.3)
        low = line_mask(z - bot + 0.6, 1.2, 0.8) * (np.abs(u) < half - 1) * (y < -20)
        c = mix(c, SKIN_SHADE * 0.9, low * 0.55)
        under = (1 - smooth(ellipse(P, ex + 2 * s, ez - 7.0, 12, 3.5), 0.3, 1.0)) * (y < -20)
        c = mix(c, SKIN_SHADE, under * 0.15)
        # brow: a relaxed resting brow, a finger above the eye, wide apart, an even weight with a soft arch
        bu = np.clip(((x - 10 * s) * s) / 34.0, 0, 1)
        bc = 1672.5 + 2.2 * np.sin(bu * math.pi * 0.85)
        bw = 4.6 - 1.6 * bu
        brow = (1 - smooth(np.abs(z - bc), bw / 2, bw / 2 + 0.9)) * ((x - 10 * s) * s > 0) * ((x - 10 * s) * s < 35) * (y < -15)
        c = mix(c, HAIR * 1.2, brow * (0.8 * (0.8 + 0.3 * vnoise(P, 1.3, 11))).clip(0, 1))
    # ears: darker inner bowl
    ei = (1 - smooth(np.sqrt(((y - 15) / 9) ** 2 + ((z - 1646) / 16) ** 2), 0.6, 1.0)) * (ax > 79.5)
    c = mix(c, SKIN_SHADE * 0.85, ei * 0.6)
    # scalp under the hair
    hz = hairline_z(ang)
    by_ear = (ax > 79.5) * (np.abs(y - 14) < 18) * (z < 1676)       # the ear itself stays skin
    c = mix(c, HAIR * 0.9, smooth(z - hz, -4, 2) * (1 - by_ear))
    # short taper under the hair edge on the sides and the back (clippered, not shaved)
    taper = smooth(z - (hz - 22), 0, 20) * smooth(np.abs(ang), 55, 80) * (1 - by_ear)
    c = mix(c, mix(SKIN_SHADE, HAIR, 0.75), np.clip(taper * 0.85 * (0.75 + 0.5 * vnoise(P, 1.2, 13)), 0, 1))
    # sideburns: short, tapering into the stubble line in front of the ear
    sbz = np.interp(np.abs(ang), [70, 74, 80, 84, 88], [1700, 1648, 1640, 1652, 1700])
    sb = smooth(z - sbz, -2, 3) * smooth(np.abs(ang), 69, 73) * (1 - smooth(np.abs(ang), 84, 88)) * (z < 1690)
    c = mix(c, HAIR * 0.95, sb * 0.7 * (1 - by_ear))
    # inner walls (UV strip at the bottom): lids pink-grey with the lash shadow on top, inner lips darker inwards
    strip = UV[:, 1] < F.V_SKIN - 0.004
    if strip.any():
        d = np.clip((UV[:, 1] - F.STRIP_V[0]) / (F.STRIP_V[1] - F.STRIP_V[0]), 0, 1)
        lids = mix(mix(SKIN_SHADE, LIP, 0.4) * 0.8, LINE * 1.6, (z > F.EYE_Z + 0.5) * 0.7)
        lids = mix(lids, lids * 0.6, d)
        lipin = mix(LIP * 0.85, LIP * 0.45, d)
        inner = np.where((UV[:, 0] < 0.66)[:, None], lids, lipin)
        c = np.where(strip[:, None], inner, c)
    return c


def paint_face(P, N, A, UV):
    """Eyeballs, mouth bag and teeth (male_v1_face): picked by the vertex attribute 'part'."""
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    part = TEXEL_PART
    c = np.zeros((len(P), 3))
    # eyeballs: sclera shaded to the corners, brown iris looking straight ahead, pupil, one catchlight
    eye = part == 1
    ce = np.tile(SCLERA, (len(P), 1))
    for s in (-1, 1):
        ex = F.EYE_X * s
        u = np.abs(x - ex)
        ce = np.where((np.sign(x) == s)[:, None], mix(ce, SCLERA * 0.8, smooth(u, 5, 13)), ce)
        ir = np.sqrt((x - ex + 0.8 * s) ** 2 + (z - F.EYE_Z - 0.4) ** 2)
        front = (np.sign(x) == s) & (y < -55)
        ci = mix(IRIS * 1.15, IRIS * 0.75, smooth(ir, 1.5, 4.6))
        ci = mix(ci, LINE * 0.8, (1 - smooth(ir, 1.6, 2.2)))
        ci = mix(ci, LINE, (smooth(ir, 4.3, 4.8) * (1 - smooth(ir, 4.8, 5.3))) * 0.6)
        ce = np.where((front & (ir < 5.1))[:, None], ci, ce)
        hl = 1 - smooth(np.sqrt((x - ex + 2.0 * s) ** 2 + (z - F.EYE_Z - 2.0) ** 2), 0.7, 1.2)
        ce = mix(ce, np.array([0.92, 0.90, 0.86]), (hl * front))
    c[eye] = ce[eye]
    # mouth bag: dark, darker inwards, the floor a tongue colour
    bag = part == 2
    deep = smooth(y + 85, 0, 22)
    cb = mix(np.array([0.50, 0.24, 0.21]), np.array([0.20, 0.08, 0.07]), deep)
    cb = mix(cb, np.array([0.48, 0.22, 0.21]) * (1 - 0.5 * deep)[:, None], (1 - smooth(z, F.MOUTH_Z - 9, F.MOUTH_Z - 4)) * 0.8)
    c[bag] = cb[bag]
    # teeth: dull ivory, gaps between the incisors, gum line at the hidden end
    teeth = (part == 3) | (part == 4)
    gap = line_mask(((x + 2.0) % 4.4) - 2.2, 0.6, 0.4) * (np.abs(x) < 12)
    ct = mix(np.tile(np.array([0.80, 0.76, 0.66]), (len(P), 1)), np.array([0.55, 0.50, 0.42]), (gap * 0.6))
    gum = np.where(part == 3, smooth(z, 1598.5, 1599.5), 1 - smooth(z, 1587.5, 1588.5))
    ct = mix(ct, LIP * 0.9, gum)
    ct = mix(ct, ct * 0.7, smooth(np.abs(x), 8, 16))
    c[teeth] = ct[teeth]
    return mix(c, c * 0.7, np.clip(A * 0.6, 0, 0.4))


HEAD_SCALE = 1.09


def stage_head_scale(k=HEAD_SCALE):
    """Shape key 'stylise' on the head, hair and face part: everything above the jaw scaled by k about the neck join, faded in
    from z 1550 to 1600 mm, so the neck ring still meets the body. The basis (used for UVs and painting) stays."""
    c = np.array([0.0, 12.0, 1550.0])
    for name in ('male_v1_head', 'male_v1_hair', 'male_v1_face'):
        ob = bpy.data.objects[name]
        if not ob.data.shape_keys:
            ob.shape_key_add(name='Basis')
        key = ob.data.shape_keys.key_blocks.get('stylise') or ob.shape_key_add(name='stylise')
        for i, v in enumerate(ob.data.vertices):
            p = np.array(v.co) / M
            w = float(smooth(p[2], 1550, 1600))
            key.data[i].co = Vector((c + (p - c) * (1 + (k - 1) * w)) * M)
        key.value = 1.0


SMOOTH_SKIN = ('male_v1_head', 'male_v1_face', 'male_v1_body')


def stage_smooth_skin(angle=36.0):
    """Skin and eyes smooth-shaded with hard edges kept above the angle (lid and lip rims, ears, jaw edge): the low-poly
    silhouette stays, the flat-shading bands across the cheeks go. Hair and clothes stay flat."""
    for name in SMOOTH_SKIN:
        me = bpy.data.objects[name].data
        me.shade_smooth()
        me.set_sharp_from_angle(angle=math.radians(angle))


def uv_head(ob):
    """Cylinder UVs for the skin above V_SKIN; the inner lid / lip walls go to the strip below it."""
    uv_cylinder(ob, v0=F.V_SKIN, skip=F.inner_faces(ob))
    F.uv_inner(ob)


def stage_uv_head():
    uv_head(bpy.data.objects['male_v1_head'])


# ---------------------------------------------------------------- hair

HAIR_COLS = [0, 12, 24, 36, 48, 60, 70, 79, 88, 97, 106, 118, 132, 148, 164]
HAIR_ROWS = 9
CENTRE = np.array([0.0, 14.0, 1648.0])


def column_curve(a):
    """Head surface polyline (mm) up one column from the jaw to the crown point."""
    pts = [H.ring_point(r, i, a) for i, r in enumerate(H.RINGS)] + [np.array(H.TOP, float)]
    return np.array(pts)


def along(pl, z0):
    """Point on the polyline at height z0, then the rest of the polyline above it."""
    for k in range(len(pl) - 1):
        a, b = pl[k], pl[k + 1]
        if a[2] <= z0 <= b[2] and b[2] > a[2]:
            t = (z0 - a[2]) / (b[2] - a[2])
            return np.vstack([a + (b - a) * t, pl[k + 1:]])
    return pl[-2:]


def resample(pl, n):
    seg = np.linalg.norm(np.diff(pl, axis=0), axis=1)
    s = np.concatenate([[0], np.cumsum(seg)])
    ts = (np.linspace(0, 1, n) ** 0.8) * s[-1]
    return np.array([[np.interp(t, s, pl[:, k]) for k in range(3)] for t in ts])


def thickness(a, s):
    """Short sides and back (clippered), a longer top that starts at the corners of the head."""
    a = abs(a)
    edge = np.interp(a, [0, 40, 70, 100, 140, 180], [4, 3.5, 1.2, 1.2, 1.4, 1.4])
    top = np.interp(a, [0, 40, 80, 120, 180], [14, 12, 6, 5.5, 5])
    start = np.interp(a, [0, 40, 70, 120, 180], [0.0, 0.1, 0.55, 0.55, 0.5])
    return edge + (top - edge) * smooth(s, start, start + 0.35)


def stage_hair():
    cols = HAIR_COLS + [-c for c in reversed(HAIR_COLS[1:])] + [180]
    cols = sorted(cols, key=lambda c: c % 360)
    nc = len(cols)
    rng = np.random.default_rng(3)
    outer, inner = [], []
    for ci, a in enumerate(cols):
        pl = along(column_curve(a), float(hairline_z(a)))
        pts = resample(pl, HAIR_ROWS + 1)[:-1]          # the crown point is shared, added below
        col_o = []
        for ri, p in enumerate(pts):
            s = ri / HAIR_ROWS
            n = p - CENTRE
            n[2] *= 0.8
            n /= np.linalg.norm(n)
            # short practical cut, a few days past the barber: uneven clumps on top, tighter on the sides
            mess = 1.2 + 2.6 * smooth(s, 0.2, 0.7) * (1 - 0.6 * smooth(abs(a), 90, 130))
            q = p + n * (thickness(a, s) + (rng.uniform(-mess, mess) if ri else 0.0))
            if ri and s > 0.3 and abs(a) < 120:
                q += np.array([rng.uniform(-2.5, 2.5), rng.uniform(-2.0, 1.0), 0.0]) * smooth(s, 0.3, 0.7)
                if rng.random() < 0.3:                    # a tuft sticking up / out
                    q += n * rng.uniform(3.0, 6.0) * smooth(s, 0.35, 0.6) * (1 - smooth(abs(a), 70, 110))
            if ri == 0 and abs(a) >= 45:                  # sides and back: the grown-out edge breaks up
                q += np.array([0.0, 0.0, (-3.0 if ci % 2 else 1.0) + rng.uniform(-1.0, 1.0)])
            if abs(a) < 45 and ri <= 1:                   # front: short, pushed up and to +x, uneven pieces
                piece = (-6.0 if ci % 2 else 0.5) + rng.uniform(-1.0, 1.0) if ri == 0 else 0.5
                q += np.array([3.0 * (1 - abs(a) / 45), -3.0 if (ri == 0 and ci % 2) else -2.0, piece])
            col_o.append(q)
        outer.append(col_o)
        n0 = pts[0] - CENTRE
        inner.append(pts[0] + n0 / np.linalg.norm(n0) * 0.8)
    crown = np.array(H.TOP, float) + np.array([0, 2.0, 13.0])
    bm = bmesh.new()
    O = [[bm.verts.new(Vector(p * M)) for p in col] for col in outer]
    I = [bm.verts.new(Vector(p * M)) for p in inner]
    C = bm.verts.new(Vector(crown * M))
    uvf = []
    for ci in range(nc):
        cj = (ci + 1) % nc
        u0, u1 = ci / nc, (ci + 1) / nc
        f = bm.faces.new((I[ci], I[cj], O[cj][0], O[ci][0]))
        uvf.append((f, [(u0, 0.0), (u1, 0.0), (u1, 0.08), (u0, 0.08)]))
        for ri in range(HAIR_ROWS - 1):
            v0, v1 = 0.08 + 0.92 * ri / HAIR_ROWS, 0.08 + 0.92 * (ri + 1) / HAIR_ROWS
            f = bm.faces.new((O[ci][ri], O[cj][ri], O[cj][ri + 1], O[ci][ri + 1]))
            uvf.append((f, [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]))
        f = bm.faces.new((O[ci][-1], O[cj][-1], C))
        uvf.append((f, [(u0, 0.08 + 0.92 * (HAIR_ROWS - 1) / HAIR_ROWS), (u1, 0.08 + 0.92 * (HAIR_ROWS - 1) / HAIR_ROWS),
                        ((u0 + u1) / 2, 1.0)]))
    uvl = bm.loops.layers.uv.new('UVMap')
    for f, uvs in uvf:
        for lp, uv in zip(f.loops, uvs):
            lp[uvl].uv = uv
    bm.normal_update()
    for f in bm.faces:                                     # outward
        c = np.array(f.calc_center_median()) / M
        if f.normal.dot(Vector(c - CENTRE)) < 0:
            f.normal_flip()
    me = bpy.data.meshes.new('male_v1_hair')
    bm.to_mesh(me)
    bm.free()
    replace_object('male_v1_hair', me)


def paint_hair(P, N, A, UV):
    u, v = UV[:, 0], UV[:, 1]
    strands = 0.5 * vnoise(np.stack([u * 220, v * 9, u * 0], 1), 1.0, 21) + 0.5 * vnoise(np.stack([u * 90, v * 4, v], 1), 1.0, 22)
    c = mix(np.tile(HAIR, (len(P), 1)), HAIR_LIGHT, smooth(strands, 0.45, 0.9) * 0.8)
    c = mix(c, HAIR * 0.6, smooth(0.12 - v, -0.05, 0.1) * 0.6)            # rim and roots darker
    c = mix(c, HAIR * 0.55, np.clip(A * 1.2, 0, 0.7))
    return c


# ---------------------------------------------------------------- outfit (offset copies of the body)

HEM = 860.0          # jacket hem
NECK_TOP = 1478.0
WAIST = 1012.0


def body_data():
    ob = bpy.data.objects['male_v1_body']
    me = ob.data
    me.update()
    P = np.array([v.co[:] for v in me.vertices]) / M
    N = np.array([v.normal[:] for v in me.vertices])
    lab, side, tfa, tsh = labels(P)
    return ob, P, N, lab, side, tfa, tsh


def garment(name, keep_face, offset, cuts, thick, extra=None):
    """Copy the body faces where keep_face(vertex ids) is true, push vertices out along the normal by
    offset(i, P) mm, trim with plane cuts, then give the cloth a thickness (solidify, outward)."""
    ob, P, N, lab, side, tfa, tsh = body_data()
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    bm.verts.ensure_lookup_table()
    bm.faces.ensure_lookup_table()
    src = bm.verts.layers.int.new('src')
    for v in bm.verts:
        v[src] = v.index
    dead = [f for f in bm.faces if not keep_face([v.index for v in f.verts])]
    bmesh.ops.delete(bm, geom=dead, context='FACES')
    loose = [v for v in bm.verts if not v.link_faces]
    bmesh.ops.delete(bm, geom=loose, context='VERTS')
    for v in bm.verts:
        i = v[src]
        v.co = Vector((P[i] + N[i] * offset(i, P[i])) * M)
    for co, no, where in cuts:
        plane_cut(bm, Vector(co) * M, no, True, where)
    if extra:
        extra(bm)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    g = replace_object(name, me)
    sol = g.modifiers.new('cloth', 'SOLIDIFY')
    sol.thickness = thick * M
    sol.offset = 1.0
    sol.use_rim = True
    sol.use_even_offset = False
    bpy.context.view_layer.objects.active = g
    bpy.ops.object.modifier_apply(modifier=sol.name)
    return g


def stage_outfit():
    ob, P, N, lab, side, tfa, tsh = body_data()
    ARM = {'upper_arm_r', 'upper_arm_l', 'forearm_r', 'forearm_l', 'forearm_twist_r', 'forearm_twist_l'}
    HAND = {'hand_r', 'hand_l'}
    LEG = {'thigh_r', 'thigh_l', 'shin_r', 'shin_l', 'foot_r', 'foot_l', 'toes_r', 'toes_l'}
    FOOT = {'foot_r', 'foot_l', 'toes_r', 'toes_l'}
    B = bones()

    def fa_plane(s, t):
        a, b = B['forearm_' + s][0], B['hand_' + s][0]
        return a + (b - a) * t, (b - a) / np.linalg.norm(b - a)

    def sh_plane(s, t):
        a, b = B['shin_' + s][0], B['foot_' + s][0]
        return a + (b - a) * t, (b - a) / np.linalg.norm(b - a)

    def arm_side(s):
        return lambda v: (v.co.x < -0.2) if s == 'r' else (v.co.x > 0.2)

    def leg_side(s):
        return lambda v: (v.co.x < 0) == (s == 'r') and v.co.z < 0.75

    # --- tee (charcoal, short sleeves)
    def tee_keep(ids):
        return all(lab[i] not in LEG | HAND for i in ids) and any(P[i, 2] > 930 for i in ids) and \
            all(lab[i] not in {'forearm_r', 'forearm_l', 'forearm_twist_r', 'forearm_twist_l'} for i in ids)
    cuts = [((0, 0, 950), (0, 0, -1), lambda v: abs(v.co.x) < 0.3),
            ((0, 0, 1492), (0, 0, 1), lambda v: abs(v.co.x) < 0.12)]
    for s in 'rl':
        a, b = B['upper_arm_' + s]
        cuts.append((a + (b - a) * 0.42, (b - a) / np.linalg.norm(b - a), arm_side(s)))
    garment('male_v1_tee', tee_keep, lambda i, p: 3.0, cuts, 2.0)

    # --- jacket (brown duck canvas chore coat, worn open)
    def jk_keep(ids):
        if any(lab[i] in HAND | FOOT for i in ids):
            return False
        if any(lab[i] in ARM for i in ids):
            return all(tfa[i] < 0.86 for i in ids if lab[i] in ARM)
        return any(P[i, 2] > HEM - 30 for i in ids)

    def jk_off(i, p):
        if lab[i] in ARM:
            cuff = smooth(tfa[i], 0.62, 0.70) * 3.0
            return 8.0 + cuff
        loose = 6.0 * (1 - smooth(p[2], 1180, 1330))      # hangs straight over the waist
        hem = smooth(HEM + 45 - p[2], 0, 15) * 2.0
        return 9.0 + loose + hem

    OPEN0, OPEN1 = 22.0, 46.0
    k = (OPEN1 - OPEN0) / (NECK_TOP - HEM)
    front = lambda v: v.co.y < 0.0 and abs(v.co.x) < 0.16 and v.co.z > (HEM - 40) * M
    cuts = [((0, 0, HEM), (0, 0, -1), lambda v: abs(v.co.x) < 0.3),
            ((0, 0, NECK_TOP), (0, 0, 1), lambda v: abs(v.co.x) < 0.13)]
    for s in 'rl':
        co, no = fa_plane(s, 0.78)
        cuts.append((co, no, arm_side(s)))

    def opening(bm):
        """Worn open: cut the front along two lines (OPEN0 at the hem to OPEN1 at the neck) and drop the strip."""
        for sx in (1, -1):
            geom = [g for g in list(bm.verts) + list(bm.edges) + list(bm.faces)
                    if all(front(v) for v in (g.verts if hasattr(g, 'verts') else [g]))]
            bmesh.ops.bisect_plane(bm, geom=geom, dist=0.01 * M, plane_co=Vector((sx * OPEN0, 0, HEM)) * M,
                                   plane_no=Vector((1, 0, -sx * k)).normalized())
        dead = []
        for f in bm.faces:
            c = f.calc_center_median() / M
            if c.y < 0 and c.z > HEM - 40 and abs(c.x) < OPEN0 + k * (c.z - HEM):
                dead.append(f)
        bmesh.ops.delete(bm, geom=dead, context='FACES')

    def collar(bm):
        """Fold-over collar from the neck edge: up, then out and down over the shoulders."""
        edges = [e for e in bm.edges if e.is_boundary and all(abs(v.co.z - NECK_TOP * M) < 0.0025 for v in e.verts)]
        verts = list({v for e in edges for v in e.verts})
        ring = {}
        for v in verts:
            c = Vector((0, 0.012, v.co.z))
            r = (v.co - c)
            r.z = 0
            r.normalize()
            up = bm.verts.new(v.co + Vector((0, 0, 0.034)) + r * 0.010)
            fold = bm.verts.new(v.co + Vector((0, 0, 0.006)) + r * 0.048)
            ring[v] = (up, fold)
        for e in edges:
            a, b = e.verts
            bm.faces.new((a, b, ring[b][0], ring[a][0]))
            bm.faces.new((ring[a][0], ring[b][0], ring[b][1], ring[a][1]))
    garment('male_v1_jacket', jk_keep, jk_off, cuts, 3.0, lambda bm: (opening(bm), collar(bm)))

    # --- jeans
    def pants_keep(ids):
        if any(lab[i] in ARM | HAND | {'foot_r', 'foot_l', 'toes_r', 'toes_l'} for i in ids):
            return False
        return any(P[i, 2] < WAIST + 30 for i in ids)

    def pants_off(i, p):
        if lab[i] in {'shin_r', 'shin_l'}:
            return 8.0 + 10.0 * smooth(tsh[i], 0.35, 0.85)
        return 6.0 + 2.0 * smooth(p[2], 900, 990)
    cuts = [((0, 0, WAIST), (0, 0, 1), lambda v: abs(v.co.x) < 0.3 and v.co.z > 0.8)]
    for s in 'rl':
        co, no = sh_plane(s, 0.84)
        cuts.append((co, no, leg_side(s)))
    garment('male_v1_jeans', pants_keep, pants_off, cuts, 2.5)

    # --- belt (a band over the jeans top)
    def belt_keep(ids):
        return all(lab[i] not in ARM | HAND for i in ids) and any(975 < P[i, 2] < WAIST + 30 for i in ids)
    cuts = [((0, 0, WAIST - 3), (0, 0, 1), lambda v: abs(v.co.x) < 0.3),
            ((0, 0, 976), (0, 0, -1), lambda v: abs(v.co.x) < 0.3)]
    garment('male_v1_belt', belt_keep, lambda i, p: 10.5, cuts, 2.5)

    # --- work boots
    def boot_keep(ids):
        return any(lab[i] in FOOT or (lab[i] in {'shin_r', 'shin_l'} and tsh[i] > 0.6) for i in ids)

    def boot_off(i, p):
        if lab[i] in {'shin_r', 'shin_l'}:
            return 7.0 + 5.0 * smooth(tsh[i], 0.75, 0.97)
        return 15.0

    def sole(bm):
        for v in bm.verts:
            if v.co.z < 0.022:
                v.co.z = max(v.co.z, 0.0) if v.co.z > 0.012 else 0.0
    cuts = []
    for s in 'rl':
        co, no = sh_plane(s, 0.62)
        cuts.append((co, -no, leg_side(s)))
    garment('male_v1_boots', boot_keep, boot_off, cuts, 3.0, sole)


# ---------------------------------------------------------------- garment paint

def paint_jacket(P, N, A, UV):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    ax = np.abs(x)
    weave = 0.94 + 0.08 * vnoise(P, 1.2, 31)
    c = np.tile(JACKET, (len(P), 1)) * weave[:, None]
    # faded on the top of the shoulders and the elbows, darker in folds
    c = mix(c, JACKET * 1.18, smooth(N[:, 2], 0.4, 0.9) * 0.35)
    c = grime(P, A, c, 0.8, 4)
    is_arm = ax > 230
    lab, side, tfa, tsh = labels(P)
    arm = np.isin(lab, ['upper_arm_r', 'upper_arm_l', 'forearm_r', 'forearm_l', 'forearm_twist_r', 'forearm_twist_l'])
    elbow = arm * (1 - smooth(np.abs(tfa), 0.0, 0.16)) * smooth(y, -10, 30)
    c = mix(c, JACKET * 1.2, elbow * 0.5)
    cuff = arm * smooth(tfa, 0.66, 0.68)
    c = mix(c, JACKET * 0.78, cuff * 0.85)
    c = mix(c, JACKET_DARK, arm * line_mask(tfa - 0.665, 0.012, 0.004) * 0.8)
    c = mix(c, DIRT, cuff * smooth(fbm(P, 8, 34), 0.4, 0.7) * 0.5)
    # collar and cuffs: dark corduroy / lining
    c = mix(c, JACKET_DARK, smooth(z, NECK_TOP - 4, NECK_TOP + 2) * (~is_arm))
    # front edges: stitched placket
    edge = np.abs(ax - (22 + (46 - 22) * (z - HEM) / (NECK_TOP - HEM)))
    st = line_mask(edge - 7, 1.8, 0.5) * (y < -40) * (~is_arm) * (vnoise(P, 2.4, 33) > 0.35)
    c = mix(c, STITCH, st * 0.8)
    c = mix(c, JACKET_DARK, line_mask(edge - 1.5, 3.0, 0.8) * (y < -40) * 0.7)
    # chest patch pockets with flaps
    for s in (-1, 1):
        px, pz = 70 * s, 1330
        inbox = (np.abs(x - px) < 34) & (z > pz - 52) & (z < pz + 30) & (y < -30)
        c = np.where(inbox[:, None], c * 1.07, c)
        ring = inbox & ((np.abs(np.abs(x - px) - 32) < 2.4) | (np.abs(z - (pz - 50)) < 2.4))
        c = mix(c, JACKET_DARK, ring * 0.75)
        flap = (np.abs(x - px) < 36) & (z > pz + 6) & (z < pz + 32) & (y < -30)
        c = np.where(flap[:, None], c * 1.08, c)
        shadow = (np.abs(x - px) < 36) & (z > pz) & (z < pz + 7) & (y < -30)
        c = mix(c, JACKET_DARK * 0.8, shadow * 0.8)
        btn = np.sqrt((x - px) ** 2 + (z - pz - 13) ** 2) < 4.0
        c = np.where((btn & (y < -40))[:, None], STEEL * 0.7, c)
        # lower pockets
        qx, qz = 95 * s, 980
        lb = (np.abs(x - qx) < 48) & (z > qz - 70) & (z < qz + 55) & (y < -30)
        c = np.where(lb[:, None], c * 1.06, c)
        lring = lb & ((np.abs(np.abs(x - qx) - 46) < 2.4) | (np.abs(z - (qz - 68)) < 2.4) | (np.abs(z - (qz + 52)) < 3.5))
        c = mix(c, JACKET_DARK, lring * 0.75)
    # shoulder seam and sleeve cuff band
    c = mix(c, JACKET_DARK, ((np.abs(z - 1420) < 2.2) & (ax > 150) & (ax < 230)) * 0.5)
    hem = smooth(HEM + 26 - z, 0, 2) * (~is_arm)
    c = mix(c, JACKET * 0.8, hem * 0.6)
    c = mix(c, JACKET_DARK, smooth(HEM + 4 - z, 0, 3) * (~is_arm) * 0.7)
    return c


def paint_tee(P, N, A, UV):
    c = np.tile(TEE, (len(P), 1)) * (0.92 + 0.12 * vnoise(P, 3, 41))[:, None]
    c = mix(c, TEE * 1.25, smooth(N[:, 2], 0.5, 0.9) * 0.3)
    c = mix(c, TEE * 0.6, np.clip(A * 1.3, 0, 0.8))
    neck = smooth(P[:, 2], 1478, 1486)
    return mix(c, TEE * 0.75, neck)


def paint_jeans(P, N, A, UV):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    ax = np.abs(x)
    twill = 0.9 + 0.14 * vnoise(np.stack([x + z, y, z - x], 1), 1.0, 51)
    c = np.tile(DENIM, (len(P), 1)) * twill[:, None]
    # fading: thighs front, knees, seat
    leg_x = np.where(x < 0, -95.0, 95.0)
    fade = (1 - smooth(np.abs(z - 760), 40, 220)) * smooth(-y, 0, 60) * 0.55
    fade = np.maximum(fade, (1 - smooth(np.sqrt((z - 470) ** 2 / 3 + (x - leg_x) ** 2), 10, 60)) * smooth(-y, 10, 60) * 0.5)
    fade = np.maximum(fade, (1 - smooth(np.abs(z - 860), 30, 90)) * smooth(y, 20, 80) * 0.45)
    c = mix(c, DENIM_FADE, fade * (0.7 + 0.5 * fbm(P, 20, 52)))
    # outer side seam, stitched
    side_seam = line_mask(np.abs(np.arctan2(y, np.abs(x - leg_x))) - math.pi / 2, 0.035, 0.02)
    c = mix(c, DENIM * 0.75, side_seam * 0.6 * (z < 960))
    # front pockets (curved opening), fly
    for s in (-1, 1):
        d = np.sqrt((x - 120 * s) ** 2 + (z - 1010) ** 2) - 62
        c = mix(c, STITCH * 0.9, line_mask(d, 2.0, 0.6) * (y < -30) * (z < 1005) * (ax > 60) * 0.75)
        bp = (np.abs(x - 75 * s) < 45) & (z > 860) & (z < 960) & (y > 40)
        c = mix(c, STITCH * 0.9, (bp & ((np.abs(np.abs(x - 75 * s) - 44) < 2) | (np.abs(z - 959) < 2))) * 0.7)
    fly = line_mask(x - 14, 2.0, 0.5) * (y < -40) * (z > 905) * (z < 1000)
    c = mix(c, STITCH * 0.9, fly * 0.7)
    c = grime(P, A, c, 0.8, 5)
    hem = smooth(P[:, 2], 0, 1) * 0
    return c


def paint_belt(P, N, A, UV):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    c = np.tile(BELT, (len(P), 1)) * (0.9 + 0.2 * vnoise(P, 4, 61))[:, None]
    c = mix(c, BELT * 1.4, (np.abs(z - 978) < 1.2) | (np.abs(z - 1006) < 1.2))
    buckle = (np.abs(x) < 22) & (y < -60)
    c = np.where(buckle[:, None], STEEL * (0.8 + 0.3 * smooth(np.abs(x), 14, 20))[:, None], c)
    c = np.where(((np.abs(x) < 14) & (np.abs(z - 992) < 7) & (y < -60))[:, None], BELT * 0.7, c)
    return mix(c, BELT * 0.5, np.clip(A, 0, 0.6))


def paint_boots(P, N, A, UV):
    x, y, z = P[:, 0], P[:, 1], P[:, 2]
    c = np.tile(LEATHER, (len(P), 1)) * (0.88 + 0.24 * fbm(P, 8, 71))[:, None]
    c = mix(c, LEATHER * 1.35, smooth(N[:, 2], 0.3, 0.9) * 0.4)          # scuffed toe and instep
    leg_x = np.where(x < 0, -95.0, 95.0)
    # laces up the front
    lace_zone = (np.abs(x - leg_x) < 16) & (y < -25) & (z > 60) & (z < 200)
    lace = lace_zone & (np.mod(z + 3 * np.sign(x - leg_x) * (x - leg_x) / 16, 18) < 4)
    c = np.where(lace[:, None], np.array([0.62, 0.50, 0.34]), c)
    c = mix(c, LEATHER * 0.6, ((np.abs(np.abs(x - leg_x) - 17) < 1.4) & (y < -25) & (z > 55) & (z < 205)) * 0.8)
    # toe cap seam, welt and sole
    c = mix(c, LEATHER * 0.6, line_mask(y + 130, 1.6, 0.6) * (z < 60) * 0.7)
    c = mix(c, STITCH * 0.8, line_mask(z - 24, 1.2, 0.4) * 0.7)
    c = np.where((z < 20)[:, None], SOLE * (0.9 + 0.2 * vnoise(P, 3, 72))[:, None], c)
    c = mix(c, SOLE * 1.2, line_mask(z - 20.5, 1.5, 0.3))
    c = mix(c, LEATHER * 0.55, np.clip(A * 1.2, 0, 0.7))
    dust = smooth(110 - z, 0, 80) * 0.5 + smooth(fbm(P, 12, 73), 0.5, 0.75) * 0.35
    return mix(c, DIRT * 1.1, np.clip(dust, 0, 0.7))


def paint_skin(P, N, A, UV):
    c = np.tile(SKIN, (len(P), 1)) * (0.94 + 0.1 * fbm(P, 20, 81))[:, None]
    hand = np.abs(P[:, 0]) > 520
    c = mix(c, SKIN_FLUSH, hand * 0.25)
    c = mix(c, SKIN_SHADE, np.clip(A * 1.4, 0, 0.85))
    dirt = hand * smooth(fbm(P, 10, 82), 0.45, 0.7) * 0.45
    return mix(c, DIRT, dirt)


PARTS = [('male_v1_head', 512, 256, paint_head, 'm1_head'),
         ('male_v1_hair', 256, 128, paint_hair, 'm1_hair'),
         ('male_v1_body', 256, 256, paint_skin, 'm1_skin'),
         ('male_v1_tee', 256, 256, paint_tee, 'm1_tee'),
         ('male_v1_jacket', 512, 512, paint_jacket, 'm1_jacket'),
         ('male_v1_jeans', 512, 512, paint_jeans, 'm1_jeans'),
         ('male_v1_belt', 128, 128, paint_belt, 'm1_belt'),
         ('male_v1_boots', 256, 256, paint_boots, 'm1_boots'),
         ('male_v1_face', 128, 128, paint_face, 'm1_face')]


def stage_paint(only=None):
    bvh = character_bvh([p[0] for p in PARTS])
    out = {}
    for name, W, Hh, fn, tex in PARTS:
        if only and name not in only:
            continue
        ob = bpy.data.objects[name]
        if name == 'male_v1_head':
            uv_head(ob)
        elif name != 'male_v1_hair':
            uv_smart(ob)
        out[name] = paint(ob, W, Hh, fn, bvh, tex)
    return out


# ---------------------------------------------------------------- previews

def stage_render(prefix='look', size=900, span=2.05, focus=(0, 0, 0.9), views=None):
    import render_views as R
    for name, *_ in PARTS:
        ob = bpy.data.objects[name]
        ob.hide_render = False
        if name not in SMOOTH_SKIN:                        # skin keeps stage_smooth_skin; hair and clothes flat
            for p in ob.data.polygons:
                p.use_smooth = False
    sc = bpy.context.scene
    sc.display.shading.background_color = (0.30, 0.31, 0.32)
    sc.display.shading.show_specular_highlight = False
    views = views or ['front', 'right', 'back', ('q45', (-0.72, -0.69, 0.08), (0, 0, 1))]
    paths = R.render([p[0] for p in PARTS], views, os.path.join(RENDERS), prefix, size=size, focus=focus, span=span)
    sc.display.shading.color_type = 'TEXTURE'
    return paths


def counts():
    return {p[0]: tri_count(bpy.data.objects[p[0]]) for p in PARTS if bpy.data.objects.get(p[0])}
