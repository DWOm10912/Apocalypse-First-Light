# Blackridge .50 concept materials (new gun, no wear). The layer builders (grain, mottle, smudges, bump) are the P9 v3
# ones (design/items/p9_01_v3/source/blender/materials.py); the colours follow the gun material rules: black steel
# 30-40 sRGB, large surfaces coated (not mirror metal), bare steel only on machined parts.
# FINISH (global): 'black' = all black steel; 'two_tone' = the barrel block in bead-blasted stainless, the rest black.
import os
P9 = os.path.join(os.path.dirname(BASE.rstrip('/\\')), '..', '..', 'p9_01_v3', 'source', 'blender')
exec(open(os.path.join(P9, 'materials.py')).read())
FINISH = globals().get('FINISH', 'black')

# slide and frame: nitride-black coating, fine blast grain, faint brushing along the slide
build_coated(mat('br50_black'), srgb(34), 0.34, 9.0, 0.06, 0.08, 0.05, 0.06, 0.07, 0.10, brushed=0.3, ior=1.65)
# small machined parts (hammer, safety, slide stop, bolt, trigger): darker bare steel
build_coated(mat('br50_steel'), STEEL_BASE, 0.32, 12.0, 0.05, 0.05, 0.05, 0.05, 0.04, 0.07, brushed=0.3, metallic=1.0)
# the barrel block: the same black coating, or bead-blasted stainless (matte: no mirror, fine even grain, no brushing)
if FINISH == 'two_tone':
    build_coated(mat('br50_barrel'), 0.42, 0.44, 14.0, 0.05, 0.05, 0.05, 0.04, 0.05, 0.08, metallic=1.0)
else:
    build_coated(mat('br50_barrel'), srgb(32), 0.32, 9.0, 0.06, 0.08, 0.05, 0.06, 0.07, 0.10, brushed=0.45, ior=1.65)
# the bore and crown: bare steel, darker
build_coated(mat('br50_bore'), 0.05, 0.3, 12.0, 0.04, 0.04, 0.04, 0.04, 0.03, 0.05, metallic=1.0)


def stippled(name, base, rough, pitch, height):
    """Moulded polymer with a stippled grip texture: round dimples from a cellular noise, over the P9 grain."""
    b, bsdf, bump, p = build_coated(mat(name), srgb(base), rough, 5.0, 0.07, 0.08, 0.05, 0.05, 0.10, 0.16, ior=1.5)
    vor = b.n('ShaderNodeTexVoronoi', feature='F1')
    vor.inputs['Scale'].default_value = 1.0 / pitch
    vor.inputs['Randomness'].default_value = 0.7
    b.link(p, vor.inputs['Vector'])
    d = b.math('MINIMUM', b.math('MULTIPLY', vor.outputs['Distance'], 2.2), 1.0)
    cb = b.n('ShaderNodeBump')
    cb.inputs['Strength'].default_value = 1.0
    cb.inputs['Distance'].default_value = height
    b.link(d, cb.inputs['Height'])
    b.link(bump.outputs['Normal'], cb.inputs['Normal'])
    b.link(cb.outputs['Normal'], bsdf.inputs['Normal'])
    # dimples a touch darker (reads without shaders)
    c_old = bsdf.inputs['Base Color'].links[0].from_socket
    cm = b.n('ShaderNodeMix', data_type='RGBA', blend_type='MULTIPLY')
    cm.inputs['Factor'].default_value = 1.0
    b.link(c_old, mix_in(cm, 'A'))
    kk = b.math('ADD', 0.78, b.math('MULTIPLY', d, 0.3))
    kc = b.n('ShaderNodeCombineColor')
    for i in range(3):
        b.link(kk, kc.inputs[i])
    b.link(kc.outputs[0], mix_in(cm, 'B'))
    b.link(mix_out(cm), bsdf.inputs['Base Color'])


stippled('br50_grip', 36, 0.64, 1.1, 0.35)
stippled('br50_pad', 32, 0.58, 3.0, 0.05)

# sights: white dots
m = mat('br50_white')
b = NT(m)
o = b.n('ShaderNodeOutputMaterial')
bs = b.n('ShaderNodeBsdfPrincipled')
bs.inputs['Base Color'].default_value = (0.7, 0.7, 0.66, 1)
bs.inputs['Roughness'].default_value = 0.55
b.link(bs.outputs[0], o.inputs['Surface'])
print('br50 materials ok', FINISH)
