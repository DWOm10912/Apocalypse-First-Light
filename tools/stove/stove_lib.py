# Shared Blender helpers for the kitchen stoves (Cooking V1, docs/gameplay/cooking_v1_plan.md 6): Box Stove V2's geometry
# helpers and procedural materials (tools/stove/box_stove_v2/model.py up to its body section: P, box, cyl, loft, coil, bevel,
# iron, plain, the st_* materials) plus porcelain enamel. exec()'d into a model script's globals; needs STOVE_DIR there
# (the tools/stove folder).
import os

_src = open(os.path.join(STOVE_DIR, "box_stove_v2", "model.py"), encoding="utf-8").read()
exec(compile(_src[:_src.index("# ---------------------------------------------------------------- body")], "box_stove_v2/model.py", "exec"))

def porcelain(name, color, chip=0.0, rough=0.10, chip_scale=22.0):
    """Porcelain enamel (glass fused on steel or iron): a glossy glaze, a faint waviness in the bump; chip > 0 knocks small
    chips out of it where the noise peaks (the black iron under it, rough), as on a stove used for decades."""
    m = new_mat(name); nt, N, L = nodes(m); b = N.get("Principled BSDF")
    co = N.new("ShaderNodeTexCoord")
    wav = N.new("ShaderNodeTexNoise"); wav.inputs["Scale"].default_value = 14; wav.inputs["Detail"].default_value = 2; L.new(co.outputs["Object"], wav.inputs["Vector"])
    bp = N.new("ShaderNodeBump"); bp.inputs["Strength"].default_value = 0.04; bp.inputs["Distance"].default_value = 0.001
    L.new(wav.outputs["Fac"], bp.inputs["Height"])
    col = N.new("ShaderNodeRGB"); col.outputs[0].default_value = (*color, 1)
    if chip:
        cn = N.new("ShaderNodeTexNoise"); cn.inputs["Scale"].default_value = chip_scale; cn.inputs["Detail"].default_value = 8; cn.inputs["Roughness"].default_value = 0.7
        L.new(co.outputs["Object"], cn.inputs["Vector"])
        cm = N.new("ShaderNodeMapRange"); cm.inputs["From Min"].default_value = 0.74 - 0.06 * chip; cm.inputs["From Max"].default_value = 0.75 - 0.06 * chip
        L.new(cn.outputs["Fac"], cm.inputs["Value"])
        mx = N.new("ShaderNodeMix"); mx.data_type = "RGBA"; L.new(cm.outputs[0], mx.inputs["Factor"]); L.new(col.outputs[0], mx.inputs[6]); mx.inputs[7].default_value = (0.02, 0.019, 0.018, 1)
        L.new(mx.outputs[2], b.inputs["Base Color"])
        rg = N.new("ShaderNodeMix"); rg.data_type = "FLOAT"; L.new(cm.outputs[0], rg.inputs["Factor"]); rg.inputs[2].default_value = rough; rg.inputs[3].default_value = 0.7
        L.new(rg.outputs[0], b.inputs["Roughness"])
        cb = N.new("ShaderNodeBump"); cb.inputs["Strength"].default_value = 0.5; cb.inputs["Distance"].default_value = 0.0006; cb.invert = True
        L.new(cm.outputs[0], cb.inputs["Height"]); L.new(bp.outputs["Normal"], cb.inputs["Normal"]); L.new(cb.outputs["Normal"], b.inputs["Normal"])
    else:
        L.new(col.outputs[0], b.inputs["Base Color"]); b.inputs["Roughness"].default_value = rough
        L.new(bp.outputs["Normal"], b.inputs["Normal"])
    m["afl_f0"] = 40; m["afl_metal"] = 0
    return m
