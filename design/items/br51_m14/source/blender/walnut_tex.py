# BR-51 concept: the stock uses the furniture walnut. Same CC0 photo (Poly Haven walnut_veneer_02, 1 m tile) and the
# same grade as tools/build-wood-photo-v1.mjs `FURN` (delight 0.25 at radius 64, mean sRGB (92, 60, 42), saturation
# 0.85, contrast 1.35), kept at the photo's full 1024 px instead of the game's 240 px. Grain runs along u.
# Writes ../textures/br51_walnut_furn_1k.png.
import os
import bpy
import numpy as np

ROOT = os.path.normpath(os.path.join(BASE, '../../../../..'))
SRC = os.path.join(ROOT, 'build/cc0-wood/walnut_veneer_02_diff_1k.jpg')
DST = os.path.normpath(os.path.join(BASE, '../textures/br51_walnut_furn_1k.png'))
T, SAT, CON, DELIGHT, HI = (92, 60, 42), 0.85, 1.35, 0.25, 9


def lum(a):
    return 0.2126 * a[..., 0] + 0.7152 * a[..., 1] + 0.0722 * a[..., 2]


def blur_wrap(a, r, n=3):
    for _ in range(n):
        for ax in (1, 0):
            c = np.cumsum(np.concatenate([np.take(a, range(-r - 1, 0), axis=ax), a, np.take(a, range(0, r), axis=ax)],
                                         axis=ax), axis=ax)
            hi = np.take(c, range(2 * r + 1, c.shape[ax]), axis=ax)
            lo = np.take(c, range(0, c.shape[ax] - 2 * r - 1), axis=ax)
            a = (hi - lo) / (2 * r + 1)
    return a


im = bpy.data.images.load(SRC, check_existing=False)
w, h = im.size
d = np.array(im.pixels[:], dtype=np.float64).reshape(h, w, 4)[..., :3].copy()
bpy.data.images.remove(im)

# delight: luminance divided by its blur
L = lum(d)
B = blur_wrap(L, 64)
d *= (L.mean() / np.maximum(1e-3, B))[..., None] ** DELIGHT

# grain along u (the colour changes mostly across rows)
gx = np.abs(np.diff(d[..., 0], axis=1)).sum()
gy = np.abs(np.diff(d[..., 0], axis=0)).sum()
if gx > gy:
    d = np.rot90(d, -1).copy()

# grade toward the mean colour: browns only, contrast around the mean, chroma x sat
mean = d.reshape(-1, 3).mean(0)
ml = lum(mean)
tl = lum(np.array(T) / 255.0)
cf = min(1.0, tl / ml)
r, g, b = d[..., 0], d[..., 1], d[..., 2]
g = np.minimum(g, r)
b = np.minimum(b, g)
d = np.stack([r, g, b], -1)
l = lum(d)
rel = (l / ml - 1) * CON + 1
rel = np.where(rel > 1 + HI, 1 + HI + (rel - 1 - HI) * 0.25, rel)
ch = d - l[..., None]
out = np.clip(np.array(T) / 255.0 * rel[..., None] + ch * SAT * cf, 0, 1)

os.makedirs(os.path.dirname(DST), exist_ok=True)
H, W = out.shape[:2]
o = bpy.data.images.new('br51_walnut_furn_tmp', W, H, alpha=False)
o.pixels[:] = np.concatenate([out, np.ones((H, W, 1))], -1).ravel()
o.filepath_raw = DST
o.file_format = 'PNG'
o.save()
bpy.data.images.remove(o)
print('walnut tex', W, H, 'mean', (out.reshape(-1, 3).mean(0) * 255).round(1), 'rot', gx > gy)
