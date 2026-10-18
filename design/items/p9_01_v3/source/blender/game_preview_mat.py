# Preview materials on the low poly (P9_lo) from the composed game textures TEX_PREFIX (+ _s, _n):
#   p9_prev_vanilla: emission = albedo x Minecraft's entity diffuse (0.4 ambient + 0.6 x two fixed lights), no specular,
#                    no normal map: what vanilla draws (minus the lightmap)
#   p9_prev_pbr:     Principled from the LabPBR maps (smoothness -> roughness, F0 255 -> metallic, _n DirectX -> OpenGL)
# MODE = 'vanilla' | 'pbr' assigns that material to every P9_lo object.
import bpy, math


def img(path, colorspace):
    im = bpy.data.images.load(path, check_existing=True)
    im.reload()
    im.colorspace_settings.name = colorspace
    return im


A = img(TEX_PREFIX + '.png', 'sRGB')
S = img(TEX_PREFIX + '_s.png', 'Non-Color')
N = img(TEX_PREFIX + '_n.png', 'Non-Color')
for im in (A, S, N):
    im.alpha_mode = 'NONE'


def mat(name):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    m.node_tree.nodes.clear()
    return m, m.node_tree


def tex(nt, im):
    t = nt.nodes.new('ShaderNodeTexImage')
    t.image = im
    t.interpolation = 'Closest'
    return t


# vanilla
m, nt = mat('p9_prev_vanilla')
out = nt.nodes.new('ShaderNodeOutputMaterial')
ta = tex(nt, A)
geo = nt.nodes.new('ShaderNodeNewGeometry')
light = None
for L in ((0.2, 1.0, -0.7), (-0.2, 1.0, 0.7)):
    # Minecraft y-up light directions -> Blender (x, -z, y)
    l = (L[0], -L[2], L[1])
    n = math.sqrt(sum(v * v for v in l))
    d = nt.nodes.new('ShaderNodeVectorMath')
    d.operation = 'DOT_PRODUCT'
    nt.links.new(geo.outputs['Normal'], d.inputs[0])
    d.inputs[1].default_value = tuple(v / n for v in l)
    mx = nt.nodes.new('ShaderNodeMath')
    mx.operation = 'MAXIMUM'
    nt.links.new(d.outputs['Value'], mx.inputs[0])
    mx.inputs[1].default_value = 0.0
    if light is None:
        light = mx
    else:
        s = nt.nodes.new('ShaderNodeMath')
        s.operation = 'ADD'
        nt.links.new(light.outputs[0], s.inputs[0])
        nt.links.new(mx.outputs[0], s.inputs[1])
        light = s
k = nt.nodes.new('ShaderNodeMath')
k.operation = 'MULTIPLY_ADD'
nt.links.new(light.outputs[0], k.inputs[0])
k.inputs[1].default_value = 0.6
k.inputs[2].default_value = 0.4
k.use_clamp = True
mul = nt.nodes.new('ShaderNodeMix')
mul.data_type = 'RGBA'
mul.blend_type = 'MULTIPLY'
mul.inputs['Factor'].default_value = 1.0
nt.links.new(ta.outputs['Color'], [i for i in mul.inputs if i.type == 'RGBA'][0])
kc = nt.nodes.new('ShaderNodeCombineColor')
for i in range(3):
    nt.links.new(k.outputs[0], kc.inputs[i])
nt.links.new(kc.outputs[0], [i for i in mul.inputs if i.type == 'RGBA'][1])
em = nt.nodes.new('ShaderNodeEmission')
nt.links.new([o for o in mul.outputs if o.type == 'RGBA'][0], em.inputs['Color'])
nt.links.new(em.outputs[0], out.inputs['Surface'])

# PBR
m, nt = mat('p9_prev_pbr')
out = nt.nodes.new('ShaderNodeOutputMaterial')
bs = nt.nodes.new('ShaderNodeBsdfPrincipled')
nt.links.new(bs.outputs[0], out.inputs['Surface'])
ta, ts, tn = tex(nt, A), tex(nt, S), tex(nt, N)
nt.links.new(ta.outputs['Color'], bs.inputs['Base Color'])
sp = nt.nodes.new('ShaderNodeSeparateColor')
nt.links.new(ts.outputs['Color'], sp.inputs[0])
r = nt.nodes.new('ShaderNodeMath')
r.operation = 'SUBTRACT'
r.inputs[0].default_value = 1.0
nt.links.new(sp.outputs[0], r.inputs[1])
nt.links.new(r.outputs[0], bs.inputs['Roughness'])
mt = nt.nodes.new('ShaderNodeMath')
mt.operation = 'GREATER_THAN'
nt.links.new(sp.outputs[1], mt.inputs[0])
mt.inputs[1].default_value = 0.9
nt.links.new(mt.outputs[0], bs.inputs['Metallic'])
# dielectric F0 byte -> specular IOR level (0.5 = F0 0.04)
lv = nt.nodes.new('ShaderNodeMath')
lv.operation = 'MULTIPLY'
nt.links.new(sp.outputs[1], lv.inputs[0])
lv.inputs[1].default_value = 255.0 / 10.0 * 0.5
nt.links.new(lv.outputs[0], bs.inputs['Specular IOR Level'])
spn = nt.nodes.new('ShaderNodeSeparateColor')
nt.links.new(tn.outputs['Color'], spn.inputs[0])
inv = nt.nodes.new('ShaderNodeMath')
inv.operation = 'SUBTRACT'
inv.inputs[0].default_value = 1.0
nt.links.new(spn.outputs[1], inv.inputs[1])
cc = nt.nodes.new('ShaderNodeCombineColor')
nt.links.new(spn.outputs[0], cc.inputs[0])
nt.links.new(inv.outputs[0], cc.inputs[1])
cc.inputs[2].default_value = 1.0
nm = nt.nodes.new('ShaderNodeNormalMap')
nt.links.new(cc.outputs[0], nm.inputs['Color'])
nt.links.new(nm.outputs[0], bs.inputs['Normal'])

target = bpy.data.materials['p9_prev_' + MODE]
for o in bpy.data.collections['P9_lo'].objects:
    o.data.materials.clear()
    o.data.materials.append(target)
print('preview material', MODE)
