# Blackridge .50 V2: tone down the smoothness of the LabPBR _s texture (docs/native_guns/blackridge_50_remake_v1.md 12.5).
# User 2026-10-29: "这把枪的材质有点奇怪，为啥会这样？P9就很好看". The composed _s had 74.5 % of the gun coated (F0 24) at a
# median smoothness of 171 (the P9: 23 % coated at 175, the rest polymer at 105; the BR-51's metal 120): under a shader the
# large flat slabs mirror the sky and read grey. Same mapping as game_compose.py SMOOTH_SCALE, applied to the composed PNG
# (the 4096 bakes live only in the Blender session): each F0 class's smoothness scaled, so its variation is kept.
#   python -I design/items/blackridge_50/source/retone_spec_v2.py
# The glossy original is kept beside this file (blackridge_50_v2_native_s_glossy_2026-10-28.png); run from it, so a
# second run gives the same result. Then node tools/export-blackridge-50-v2-native.mjs copies it into the game assets.
import os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..', '..'))
ORIGINAL = os.path.join(HERE, 'blackridge_50_v2_native_s_glossy_2026-10-28.png')
OUT = os.path.join(ROOT, 'src', 'main', 'blockbench', 'textures', 'blackridge_50_v2_native_s.png')
# F0 byte -> smoothness factor (coated steel 171 -> 118, bare steel 175 -> 145; polymer and the rest untouched)
SMOOTH_SCALE = {24: 118 / 171, 28: 145 / 175}

if not os.path.exists(ORIGINAL):
    Image.open(OUT).save(ORIGINAL)
s = np.asarray(Image.open(ORIGINAL).convert('RGBA')).astype(np.float32)
for cls, k in SMOOTH_SCALE.items():
    m = s[..., 1] == cls
    s[..., 0][m] = s[..., 0][m] * k
Image.fromarray(np.rint(np.clip(s, 0, 255)).astype(np.uint8), 'RGBA').save(OUT)
print('wrote', OUT)
