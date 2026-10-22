# BR-51 concept materials. Node helpers and the coated-surface builder come from the P9 V3 concept
# (design/items/p9_01_v3/source/blender/materials.py); colour may be a grey value or an (r, g, b) tint. Object-space mm.
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
    mix_in(col, 'A').default_value = (*base, 1) if isinstance(base, tuple) else (base, base, base, 1)
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



def lin(r, g, b):
    return (srgb(r), srgb(g), srgb(b))


def rgbmix(b, fac, a, c, blend='MIX'):
    n = b.n('ShaderNodeMix', data_type='RGBA', blend_type=blend)
    for sock, v in ((n.inputs['Factor'], fac), (mix_in(n, 'A'), a), (mix_in(n, 'B'), c)):
        if isinstance(v, (int, float)):
            sock.default_value = v
        elif isinstance(v, tuple):
            sock.default_value = (*v, 1) if len(v) == 3 else v
        else:
            b.link(v, sock)
    return mix_out(n)


def chain_bump(b, bsdf, height, strength, distance):
    """Second bump on top of whatever already feeds the BSDF normal."""
    cb = b.n('ShaderNodeBump')
    cb.inputs['Strength'].default_value = strength
    cb.inputs['Distance'].default_value = distance
    b.link(height, cb.inputs['Height'])
    old = bsdf.inputs['Normal'].links[0].from_socket if bsdf.inputs['Normal'].links else None
    if old:
        b.link(old, cb.inputs['Normal'])
    b.link(cb.outputs['Normal'], bsdf.inputs['Normal'])


# walnut, oil finish: the furniture walnut photo (walnut_tex.py grades it exactly like the furniture tile), 1 m per
# tile, grain along the stock (y). Sides read (y, z), top and belly read (y, x), blended by the object normal; the end
# grain hides under the buttplate and the nose cap. Darker pores and late wood come out a little rougher and lower.
WALNUT_TEX = os.path.normpath(os.path.join(BASE, '../textures/br51_walnut_furn_1k.png'))


def walnut(name):
    m = mat(name)
    b = NT(m)
    out = b.n('ShaderNodeOutputMaterial')
    bsdf = b.n('ShaderNodeBsdfPrincipled')
    b.link(bsdf.outputs[0], out.inputs['Surface'])
    tc = b.n('ShaderNodeTexCoord')
    x, y, z = b.sep(tc.outputs['Object'])
    nx, ny, nz = b.sep(tc.outputs['Normal'])
    img = bpy.data.images.load(WALNUT_TEX, check_existing=True)
    img.colorspace_settings.name = 'sRGB'
    u = b.math('MULTIPLY', b.math('ADD', y, 70.0), 0.001)
    looks = []
    for v in (b.math('MULTIPLY', b.math('ADD', z, 420.0), 0.001), b.math('MULTIPLY', b.math('ADD', x, 610.0), 0.001)):
        uv = b.n('ShaderNodeCombineXYZ')
        b.link(u, uv.inputs[0])
        b.link(v, uv.inputs[1])
        t = b.n('ShaderNodeTexImage')
        t.image = img
        t.interpolation = 'Cubic'
        b.link(uv.outputs[0], t.inputs['Vector'])
        looks.append(t.outputs['Color'])
    ax = b.math('ABSOLUTE', nx)
    az = b.math('ABSOLUTE', nz)
    fac = b.math('DIVIDE', b.math('POWER', az, 4.0), b.math('ADD', b.math('ADD', b.math('POWER', ax, 4.0), b.math('POWER', az, 4.0)), 1e-4))
    c = rgbmix(b, fac, looks[0], looks[1])
    b.link(c, bsdf.inputs['Base Color'])
    bw = b.n('ShaderNodeRGBToBW')
    b.link(c, bw.inputs[0])
    # graded luminance sits around 0.05 linear; darker = pore / late wood
    dark = b.math('MULTIPLY', b.math('SUBTRACT', 0.06, bw.outputs[0]), 14.0, clamp=True)
    b.link(b.math('ADD', 0.46, b.math('MULTIPLY', dark, 0.16)), bsdf.inputs['Roughness'])
    bsdf.inputs['IOR'].default_value = 1.5
    bsdf.inputs['Coat Weight'].default_value = 0.30
    bsdf.inputs['Coat Roughness'].default_value = 0.22
    bump = b.n('ShaderNodeBump')
    bump.inputs['Strength'].default_value = 0.25
    bump.inputs['Distance'].default_value = 0.04
    b.link(b.math('SUBTRACT', 1.0, dark), bump.inputs['Height'])
    b.link(bump.outputs['Normal'], bsdf.inputs['Normal'])


walnut('br51_walnut')

# parkerized (manganese phosphate) steel: dark grey with a slight green, matte crystalline grain, oil wipes
PARK = lin(43, 45, 41)
build_coated(mat('br51_park'), PARK, 0.56, 16.0, 0.10, 0.09, 0.07, 0.08, 0.14, 0.16, ior=1.6, coat=0.08)
build_coated(mat('br51_barrel'), lin(44, 45, 42), 0.50, 16.0, 0.09, 0.08, 0.06, 0.07, 0.12, 0.14, ior=1.6, coat=0.1)
# bolt, trigger and other bare working parts: dark polished steel, metallic
build_coated(mat('br51_steel_worn'), (0.20, 0.20, 0.195), 0.30, 12.0, 0.05, 0.06, 0.05, 0.06, 0.06, 0.07, brushed=0.4,
             metallic=1.0)
# magazines: black phosphate, a little smoother than the receiver
build_coated(mat('br51_mag'), lin(40, 40, 39), 0.50, 14.0, 0.09, 0.09, 0.06, 0.08, 0.12, 0.14, ior=1.6, coat=0.06)
build_coated(mat('br51_follower'), lin(34, 34, 33), 0.50, 14.0, 0.09, 0.09, 0.06, 0.08, 0.12, 0.14, ior=1.6)

# rear sight knobs: park with a diamond knurl around the knob axis (x axis through y 362, z 21)
b, bsdf, bump, p = build_coated(mat('br51_knurl'), PARK, 0.58, 16.0, 0.10, 0.09, 0.07, 0.08, 0.10, 0.16, ior=1.6)
x, y, z = b.sep(p)
ang = b.n('ShaderNodeMath', operation='ARCTAN2')
b.link(b.math('SUBTRACT', z, 21.0), ang.inputs[0])
b.link(b.math('SUBTRACT', y, 362.0), ang.inputs[1])
chain_bump(b, bsdf, pyramids(b, x, b.math('MULTIPLY', ang.outputs[0], 8.0), 0.75), 1.0, 0.22)

# buttplate: park, fine horizontal ridges across the shoulder face (faces looking back)
b, bsdf, bump, p = build_coated(mat('br51_buttplate'), PARK, 0.56, 16.0, 0.10, 0.09, 0.07, 0.08, 0.10, 0.16, ior=1.6)
x, y, z = b.sep(p)
nrm = b.n('ShaderNodeTexCoord').outputs['Normal']
nx, ny, nz = b.sep(nrm)
face = b.math('GREATER_THAN', b.math('MULTIPLY', ny, -1.0), 0.6)
ridge = b.math('MULTIPLY', b.math('ABSOLUTE', b.math('SUBTRACT', b.math('FRACT', b.math('DIVIDE', z, 1.25)), 0.5)), 2.0)
chain_bump(b, bsdf, b.math('MULTIPLY', ridge, face), 1.0, 0.22)


def metal(name, color, rough, mottle):
    b, bsdf, bump, p = build_coated(mat(name), color, rough, 10.0, 0.04, mottle, 0.04, 0.06, 0.06, 0.05, metallic=1.0)
    return bsdf


metal('br51_brass', (0.74, 0.52, 0.25), 0.26, 0.10)       # cartridge brass, slightly aged
metal('br51_copper', (0.80, 0.47, 0.32), 0.22, 0.08)      # gilding-metal jacket


# drum round-count window: clear polycarbonate, faintly smoked
def poly(name):
    m = mat(name)
    b = NT(m)
    out = b.n('ShaderNodeOutputMaterial')
    bsdf = b.n('ShaderNodeBsdfPrincipled')
    b.link(bsdf.outputs[0], out.inputs['Surface'])
    bsdf.inputs['Base Color'].default_value = (*lin(205, 205, 198), 1.0)
    bsdf.inputs['Transmission Weight'].default_value = 1.0
    bsdf.inputs['Roughness'].default_value = 0.06
    bsdf.inputs['IOR'].default_value = 1.58


poly('br51_poly')


# game version of the window (lib.py GAMEBUILD): opaque smoked polycarbonate, nothing behind it
def smoke(name):
    m = mat(name)
    b = NT(m)
    out = b.n('ShaderNodeOutputMaterial')
    bsdf = b.n('ShaderNodeBsdfPrincipled')
    b.link(bsdf.outputs[0], out.inputs['Surface'])
    bsdf.inputs['Base Color'].default_value = (*lin(26, 26, 27), 1.0)
    bsdf.inputs['Roughness'].default_value = 0.10
    bsdf.inputs['IOR'].default_value = 1.58


smoke('br51_poly_smoke')
print('materials ok')
