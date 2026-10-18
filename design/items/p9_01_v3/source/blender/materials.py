# P9 v3 concept materials (new gun, no wear). Object-space procedural layers in mm:
# fine grain (albedo + roughness + bump), mid-scale mottling (roughness + albedo), faint handling smudges,
# moulded stipple and grip checkering on the polymer. Values follow docs/native_guns/gun_surface_texture_reference_v1.md.
import os, math
exec(open(os.path.join(BASE, 'lib.py')).read())


def srgb(v):
    v = v / 255.0
    return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4


class NT:
    """Small node-tree builder."""
    def __init__(self, m):
        m.use_nodes = True
        self.t = m.node_tree
        self.t.nodes.clear()
        self.x = 0

    def n(self, kind, **kw):
        node = self.t.nodes.new(kind)
        node.location = (self.x, 0)
        self.x += 40
        for k, v in kw.items():
            if k.startswith('i_'):
                key = k[2:].replace('_', ' ')
                node.inputs[key].default_value = v
            else:
                setattr(node, k, v)
        return node

    def link(self, a, b):
        self.t.links.new(a, b)

    def math(self, op, a, b=None, clamp=False):
        node = self.n('ShaderNodeMath', operation=op, use_clamp=clamp)
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                node.inputs[i].default_value = v
            else:
                self.link(v, node.inputs[i])
        return node.outputs[0]

    def vmath(self, op, a, b=None):
        node = self.n('ShaderNodeVectorMath', operation=op)
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (tuple, list)):
                node.inputs[i].default_value = v
            else:
                self.link(v, node.inputs[i])
        out = node.outputs['Value'] if op in ('LENGTH', 'DOT_PRODUCT', 'DISTANCE') else node.outputs['Vector']
        return out

    def sep(self, v):
        node = self.n('ShaderNodeSeparateXYZ')
        self.link(v, node.inputs[0])
        return node.outputs['X'], node.outputs['Y'], node.outputs['Z']

    def comb(self, x, y, z):
        node = self.n('ShaderNodeCombineXYZ')
        for i, v in enumerate((x, y, z)):
            if isinstance(v, (int, float)):
                node.inputs[i].default_value = v
            else:
                self.link(v, node.inputs[i])
        return node.outputs[0]

    def noise(self, vec, scale, detail=2.0, rough=0.55, dims='3D'):
        node = self.n('ShaderNodeTexNoise', noise_dimensions=dims)
        node.inputs['Scale'].default_value = scale
        node.inputs['Detail'].default_value = detail
        node.inputs['Roughness'].default_value = rough
        self.link(vec, node.inputs['Vector'])
        return node.outputs['Fac']

    def mapr(self, v, lo, hi):
        """0..1 -> lo..hi"""
        return self.math('MULTIPLY_ADD', v, hi - lo) if False else self.math('ADD', self.math('MULTIPLY', v, hi - lo), lo)

    def centered(self, v, amp):
        """(v - 0.5) * 2 * amp"""
        return self.math('MULTIPLY', self.math('SUBTRACT', v, 0.5), 2 * amp)


def mix_in(node, which):
    return [x for x in node.inputs if x.name == which and x.type == 'RGBA'][0]


def mix_out(node):
    return [x for x in node.outputs if x.type == 'RGBA'][0]


def coords(b):
    tc = b.n('ShaderNodeTexCoord')
    return tc.outputs['Object']


# Game bake tuning (defaults = the concept render): GRAIN_K scales the grain size (game textures have no mipmaps, so the
# grain must be 2-3 texels, about 0.3-0.45 mm at 2048), ALB_K the albedo grain / mottle contrast (vanilla only sees the
# albedo), STEEL_BASE the machined steel colour (the albedo is the F0 of a LabPBR metal and what vanilla shows).
GRAIN_K = globals().get('GRAIN_K', 1.0)
ALB_K = globals().get('ALB_K', 1.0)
STEEL_BASE = globals().get('STEEL_BASE', 0.16)
BARREL_BASE = globals().get('BARREL_BASE', 0.055)


def build_coated(m, base, rough, grain_scale, grain_alb, mottle_alb, grain_rough, mottle_rough, smudge, bump_strength, brushed=0.0,
                 metallic=0.0, ior=1.85, coat=0.0):
    grain_scale *= GRAIN_K
    grain_alb *= ALB_K
    mottle_alb *= ALB_K
    b = NT(m)
    out = b.n('ShaderNodeOutputMaterial')
    bsdf = b.n('ShaderNodeBsdfPrincipled')
    b.link(bsdf.outputs[0], out.inputs['Surface'])
    p = coords(b)
    grain = b.noise(p, grain_scale, 3.0, 0.6)
    grain2 = b.noise(b.vmath('ADD', p, (13.7, 5.1, 2.9)), grain_scale * 2.3, 2.0, 0.5)
    mottle = b.noise(b.vmath('MULTIPLY', p, (1, 1, 1)), 0.06, 3.0, 0.55)
    # albedo: base * (1 + grain + mottle)
    g = b.centered(grain, grain_alb)
    mo = b.centered(mottle, mottle_alb)
    k = b.math('ADD', b.math('ADD', g, mo), 1.0)
    col = b.n('ShaderNodeMix', data_type='RGBA', blend_type='MULTIPLY')
    col.inputs['Factor'].default_value = 1.0
    mix_in(col, 'A').default_value = (base, base, base, 1)
    kc = b.n('ShaderNodeCombineColor')
    for i in range(3):
        b.link(k, kc.inputs[i])
    b.link(kc.outputs[0], mix_in(col, 'B'))
    b.link(mix_out(col), bsdf.inputs['Base Color'])
    # roughness: base + grain + mottle + smudges (stretched noise = wipes and finger marks)
    sm_vec = b.vmath('MULTIPLY', p, (0.9, 0.18, 0.9))
    sm = b.noise(sm_vec, 0.35, 4.0, 0.6)
    sm = b.math('SUBTRACT', b.math('MULTIPLY', sm, 2.2), 0.95, clamp=True)
    r = b.math('ADD', rough, b.centered(grain2, grain_rough))
    r = b.math('ADD', r, b.centered(mottle, mottle_rough))
    r = b.math('SUBTRACT', r, b.math('MULTIPLY', sm, smudge), clamp=True)
    b.link(r, bsdf.inputs['Roughness'])
    bsdf.inputs['Metallic'].default_value = metallic
    bsdf.inputs['IOR'].default_value = ior
    if coat:
        bsdf.inputs['Coat Weight'].default_value = coat
    # bump: fine grain, plus brushing along y when asked
    h = grain
    if brushed:
        bs = max(6.0, 40.0 * GRAIN_K)
        br = b.noise(b.vmath('MULTIPLY', p, (bs, 0.25, bs)), 1.0, 2.0, 0.5)
        h = b.math('ADD', h, b.math('MULTIPLY', br, brushed))
    bump = b.n('ShaderNodeBump')
    bump.inputs['Strength'].default_value = bump_strength
    bump.inputs['Distance'].default_value = 0.02 / math.sqrt(GRAIN_K)
    b.link(h, bump.inputs['Height'])
    b.link(bump.outputs['Normal'], bsdf.inputs['Normal'])
    return b, bsdf, bump, p


# slide: nitride-black coating, fine blast grain, faint brushing along the slide, wipe smudges
build_coated(mat('p9_slide_nitride'), srgb(34), 0.32, 9.0, 0.06, 0.08, 0.05, 0.06, 0.07, 0.10, brushed=0.35, ior=1.65)
# barrel: bare blackened steel, metallic
build_coated(mat('p9_barrel_steel'), BARREL_BASE, 0.30, 12.0, 0.05, 0.05, 0.04, 0.05, 0.05, 0.06, brushed=0.5, metallic=1.0)
# small machined parts: darker metallic steel
build_coated(mat('p9_steel_machined'), STEEL_BASE, 0.32, 12.0, 0.05, 0.05, 0.05, 0.05, 0.04, 0.07, brushed=0.3, metallic=1.0)

# polymer: moulded stipple; grip checkering on the recessed side panels (own material) and on the straps (masked)
def box_mask(b, a, a0, a1, c, c0, c1, r):
    """Rounded-rectangle mask in the (a, c) plane, crisp edge."""
    ca, cc = (a0 + a1) / 2, (c0 + c1) / 2
    ha, hc = (a1 - a0) / 2 - r, (c1 - c0) / 2 - r
    da = b.math('MAXIMUM', b.math('SUBTRACT', b.math('ABSOLUTE', b.math('SUBTRACT', a, ca)), ha), 0)
    dc = b.math('MAXIMUM', b.math('SUBTRACT', b.math('ABSOLUTE', b.math('SUBTRACT', c, cc)), hc), 0)
    d = b.math('SQRT', b.math('ADD', b.math('MULTIPLY', da, da), b.math('MULTIPLY', dc, dc)))
    return b.math('LESS_THAN', d, r)


def pyramids(b, u, v, pitch):
    """Square pyramids on a 45-degree grid: 1 at the tips, 0 in the valleys."""
    s = math.sqrt(0.5)
    uu = b.math('ADD', b.math('MULTIPLY', u, s / pitch), b.math('MULTIPLY', v, s / pitch))
    vv = b.math('SUBTRACT', b.math('MULTIPLY', u, s / pitch), b.math('MULTIPLY', v, s / pitch))
    fu = b.math('ABSOLUTE', b.math('SUBTRACT', b.math('FRACT', uu), 0.5))
    fv = b.math('ABSOLUTE', b.math('SUBTRACT', b.math('FRACT', vv), 0.5))
    return b.math('SUBTRACT', 1.0, b.math('MULTIPLY', b.math('MAXIMUM', fu, fv), 2.0))


def polymer(name, panel):
    b, bsdf, bump, p = build_coated(mat(name), srgb(38), 0.60, 5.0, 0.07, 0.08, 0.05, 0.05, 0.10, 0.16, ior=1.5)
    mp = b.n('ShaderNodeMapping', vector_type='POINT')
    mp.inputs['Location'].default_value = (0.0, 38.36, 9.01)
    mp.inputs['Rotation'].default_value = (math.radians(21.96), 0, 0)
    b.link(p, mp.inputs['Vector'])
    lx, ly, lz = b.sep(mp.outputs[0])
    if panel:
        tex = pyramids(b, ly, lz, PITCH)
        zone = 1.0
    else:
        front = b.math('MULTIPLY', b.math('MULTIPLY', b.math('GREATER_THAN', ly, -3.0), b.math('LESS_THAN', ly, 1.5)), box_mask(b, lx, -8.0, 8.0, lz, 6.0, 41.0, 2.0))
        back = b.math('MULTIPLY', b.math('MULTIPLY', b.math('LESS_THAN', ly, -37.0), b.math('GREATER_THAN', ly, -41.5)), box_mask(b, lx, -8.0, 8.0, lz, 9.0, 70.0, 2.0))
        zone = b.math('MAXIMUM', front, back)
        tex = b.math('MULTIPLY', zone, pyramids(b, lx, lz, PITCH))
    # second bump for the checkering, chained on the grain bump
    cb = b.n('ShaderNodeBump')
    cb.inputs['Strength'].default_value = 1.0
    cb.inputs['Distance'].default_value = CHECK_H
    b.link(tex, cb.inputs['Height'])
    b.link(bump.outputs['Normal'], cb.inputs['Normal'])
    b.link(cb.outputs['Normal'], bsdf.inputs['Normal'])
    # rougher in the zone, tips a touch lighter and valleys darker (reads without shaders)
    r_old = bsdf.inputs['Roughness'].links[0].from_socket
    zr = 0.08 if panel else b.math('MULTIPLY', zone, 0.08)
    b.link(b.math('ADD', r_old, zr), bsdf.inputs['Roughness'])
    c_old = bsdf.inputs['Base Color'].links[0].from_socket
    cm = b.n('ShaderNodeMix', data_type='RGBA', blend_type='MULTIPLY')
    if panel:
        cm.inputs['Factor'].default_value = 1.0
    else:
        b.link(zone, cm.inputs['Factor'])
    b.link(c_old, mix_in(cm, 'A'))
    kk = b.math('ADD', 0.72, b.math('MULTIPLY', tex, 0.55))
    kc = b.n('ShaderNodeCombineColor')
    for i in range(3):
        b.link(kk, kc.inputs[i])
    b.link(kc.outputs[0], mix_in(cm, 'B'))
    b.link(mix_out(cm), bsdf.inputs['Base Color'])


PITCH = 1.3
CHECK_H = 0.32
polymer('p9_polymer', False)
polymer('p9_grip_texture', True)

# sight paint and tritium
m = mat('p9_sight_white')
b = NT(m)
o = b.n('ShaderNodeOutputMaterial')
bs = b.n('ShaderNodeBsdfPrincipled')
bs.inputs['Base Color'].default_value = (0.72, 0.72, 0.68, 1)
bs.inputs['Roughness'].default_value = 0.55
b.link(bs.outputs[0], o.inputs['Surface'])
m = mat('p9_tritium')
b = NT(m)
o = b.n('ShaderNodeOutputMaterial')
bs = b.n('ShaderNodeBsdfPrincipled')
bs.inputs['Base Color'].default_value = (0.05, 0.25, 0.08, 1)
bs.inputs['Roughness'].default_value = 0.08
bs.inputs['Emission Color'].default_value = (0.25, 1.0, 0.35, 1)
bs.inputs['Emission Strength'].default_value = 0.6
b.link(bs.outputs[0], o.inputs['Surface'])
print('materials ok')
