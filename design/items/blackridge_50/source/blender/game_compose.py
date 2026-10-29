# Blackridge .50 game version, step 4 (the BR-51 / P9 v3 game_compose.py, bake prefix br50_gun_bake_): compose the 4096
# bakes into the 2048 game textures (numpy, inside Blender).
#   albedo (sRGB): base colour x AO x cavity, + convex-edge highlight, x micro-relief emboss (all vanilla shows)
#   _s (LabPBR):   R smoothness = 1 - roughness, G F0 byte (polymer 10, coated 24, steel 28), B 0, A 255
#   _n (LabPBR):   RG tangent normal, DirectX (G = +v, image down), B AO, A 255 (no parallax)
# Writes OUT_PREFIX + '.png' / '_s.png' / '_n.png'. The parameter notes are in the BR-51 / P9 copies.
import bpy, numpy as np

RES_OUT = globals().get('RES_OUT', 2048)
AO_K = globals().get('AO_K', 0.6)               # AO strength in the albedo
EDGE_K = globals().get('EDGE_K', 4.0)           # divergence -> edge mask gain
EDGE_ADD = globals().get('EDGE_ADD', (0.030, 0.018))   # linear albedo added on full convex edges (metal/coated, polymer)
CAV_K = globals().get('CAV_K', 0.35)            # darkening in concave creases
EMB_K = globals().get('EMB_K', 1.6)             # micro relief emboss gain
EMB_L = np.array(globals().get('EMB_L', (-0.45, 0.89)), np.float32)   # emboss light in (u, v) of each island
TRITIUM_GLOW = globals().get('TRITIUM_GLOW', 110)
PRE = globals().get('PRE', 'br50_gun_bake_')
MOTTLE_SKIP = globals().get('MOTTLE_SKIP', (10,))
# Texel-space grain (the procedural 3D grain bakes out at under 1 sRGB level): one noise field of 2-3 texel speckle plus
# a 1-texel and a ~10-texel layer, unit std, applied in sRGB to the albedo (multiplicative std), to the smoothness and
# as a height to the normal (normal xy std), per material class: coated steel / slide (F0 >= 20), polymer (the rest).
# Defaults = what the game uses (user 2026-10-17: smoothness / normal grain read as hammered paint under the shader, the
# smooth satin sheen was preferred): a faint colour grain only, so vanilla is not flat black.
# Baked mottle: the concept materials' mottle / wipe marks bake into the colour and roughness at about +-12 % / +-0.04
# on the slide and steel parts. In game (user 2026-10-17, "原版的效果有点略脏") that read as a dirty slide in vanilla and
# as patchy highlights under shaders, so on coated / steel texels (F0 >= 20) the deviation from a local mean of the
# same material is scaled by MOTTLE_KEEP. Polymer (the grip checkering, ~13 texel period, lives in its colour) keeps
# everything finer than a POLY_FINE_R blur and only its coarser band is scaled.
MOTTLE_KEEP = globals().get('MOTTLE_KEEP', 0.25)
MOTTLE_R = globals().get('MOTTLE_R', 64)        # local mean radius in 4096 texels (about 6 mm)
POLY_FINE_R = globals().get('POLY_FINE_R', 6)
GRAIN_SEED = globals().get('GRAIN_SEED', 9)
GRAIN_ALB = globals().get('GRAIN_ALB', (0.02, 0.025))
GRAIN_SMOOTH = globals().get('GRAIN_SMOOTH', (0.0, 0.0))
GRAIN_NRM = globals().get('GRAIN_NRM', (0.0, 0.0))


def load(name):
    im = bpy.data.images[PRE + name]
    w = im.size[0]
    a = np.empty(w * w * 4, np.float32)
    im.pixels.foreach_get(a)
    return a.reshape(w, w, 4)


def s2l(c):
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def l2s(c):
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(c, 1 / 2.4) - 0.055)


def box(a, r):
    """Box blur of radius r (separable, edge-clamped)."""
    if r <= 0:
        return a
    k = 2 * r + 1
    for ax in (0, 1):
        p = np.pad(a, [(r, r) if i == ax else (0, 0) for i in range(a.ndim)], mode='edge')
        c = np.cumsum(p, axis=ax, dtype=np.float64)
        c = np.concatenate([np.zeros_like(np.take(c, [0], axis=ax)), c], axis=ax)
        a = ((np.take(c, range(k, c.shape[ax]), axis=ax) - np.take(c, range(0, c.shape[ax] - k), axis=ax)) / k).astype(np.float32)
    return a


col = load('color')
covered = col[..., :3].sum(-1) > 1e-6
base = s2l(col[..., :3])
del col
rough = load('rough')[..., 0]
mat = load('mat')
f0 = np.rint(mat[..., 0] * 255)
metal = mat[..., 1] > 0.5
glow = mat[..., 2] > 0.5
del mat
nf = load('nfull')[..., :3] * 2 - 1
ng = load('ngeo')[..., :3] * 2 - 1
ao = load('ao')[..., 0]

# flatten the baked mottle on coated / steel texels: local mean per material (normalised box blur inside the F0 class)
for cls in np.unique(f0[f0 >= 20]):
    m = (f0 == cls) & covered
    w = box(m.astype(np.float32), MOTTLE_R)
    ok = m & (w > 1e-3)
    mean_c = box(base * m[..., None], MOTTLE_R)
    mean_r = box(rough * m, MOTTLE_R)
    base[ok] = mean_c[ok] / w[ok][:, None] + MOTTLE_KEEP * (base[ok] - mean_c[ok] / w[ok][:, None])
    rough[ok] = mean_r[ok] / w[ok] + MOTTLE_KEEP * (rough[ok] - mean_r[ok] / w[ok])
    del w, ok, mean_c, mean_r
for cls in np.unique(f0[(f0 < 20) & covered & ~glow]):
    if cls in MOTTLE_SKIP:
        continue
    m = (f0 == cls) & covered & ~glow
    w = box(m.astype(np.float32), MOTTLE_R)
    wf = box(m.astype(np.float32), POLY_FINE_R)
    ok = m & (w > 1e-3) & (wf > 1e-3)
    for a in (base, rough):
        am = a * (m[..., None] if a.ndim == 3 else m)
        lo = box(am, MOTTLE_R)[ok]
        mid = box(am, POLY_FINE_R)[ok]
        wl = w[ok][:, None] if a.ndim == 3 else w[ok]
        wm = wf[ok][:, None] if a.ndim == 3 else wf[ok]
        lo, mid = lo / wl, mid / wm
        a[ok] = lo + MOTTLE_KEEP * (mid - lo) + (a[ok] - mid)
    del w, wf, ok, am, lo, mid

# convex / concave from the geometry normals (bevels): divergence of the tangent xy, inside covered texels only
inner = covered & np.roll(covered, 1, 0) & np.roll(covered, -1, 0) & np.roll(covered, 1, 1) & np.roll(covered, -1, 1)
div = np.gradient(ng[..., 0], axis=1) + np.gradient(ng[..., 1], axis=0)
div = np.where(inner, div, 0)
div = box(div, 1)
edge = np.clip(div * EDGE_K, 0, 1)
cav = np.clip(-div * EDGE_K, 0, 1)
# micro relief = full minus geometry normal; emboss with a fixed light in each island's (u, v)
micro = nf[..., :2] - ng[..., :2]
emb = micro[..., 0] * EMB_L[0] + micro[..., 1] * EMB_L[1]
print('div p99', float(np.percentile(np.abs(div[inner]), 99)), 'emboss p99', float(np.percentile(np.abs(emb[inner]), 99)))

alb = base * (1 - AO_K + AO_K * ao)[..., None]
alb *= (1 - CAV_K * cav)[..., None]
alb *= np.clip(1 + EMB_K * emb, 0.55, 1.6)[..., None]
add = np.where(metal | (f0 > 15), EDGE_ADD[0], EDGE_ADD[1]) * edge
alb += add[..., None]
del base, edge, cav, micro, emb, add

# 4096 -> RES_OUT: box average (albedo in linear, normals renormalised); F0 / glow point sampled
f = alb.shape[0] // RES_OUT


def down(a):
    s = a.shape
    return a.reshape(RES_OUT, f, RES_OUT, f, *s[2:]).mean(axis=(1, 3))


alb2 = down(alb)
smooth2 = down(1 - rough)
n2 = down(nf)
n2 /= np.maximum(np.linalg.norm(n2, axis=-1, keepdims=True), 1e-6)
ao2 = down(ao)
f02 = f0[::f, ::f]
glow2 = glow[::f, ::f]
cov2 = down(covered.astype(np.float32)) > 0
del alb, rough, nf, ng, ao, f0, glow


def unit(a):
    a = a - a.mean()
    return a / max(float(a.std()), 1e-6)


rng = np.random.default_rng(GRAIN_SEED)
shape = (RES_OUT, RES_OUT)
g_fine = unit(rng.standard_normal(shape).astype(np.float32))
g_speck = unit(box(box(rng.standard_normal(shape).astype(np.float32), 1), 1))
g_mottle = unit(box(box(rng.standard_normal(shape).astype(np.float32), 4), 4))
grain = unit(0.3 * g_fine + 0.75 * g_speck + globals().get('GRAIN_MOTTLE', 0.2) * g_mottle)
coat = f02 >= 20
k_alb = np.where(coat, GRAIN_ALB[0], GRAIN_ALB[1])
k_smooth = np.where(coat, GRAIN_SMOOTH[0], GRAIN_SMOOTH[1])
k_nrm = np.where(coat, GRAIN_NRM[0], GRAIN_NRM[1])
alb2 = s2l(np.clip(l2s(alb2) * (1 + k_alb * grain)[..., None], 0, 1))
smooth2 = np.clip(smooth2 - k_smooth * grain, 0, 1)
# height = the speckle + fine layers (the mottle is colour only); tangent normal from its texel gradient
h = unit(0.3 * g_fine + 0.75 * g_speck)
gu, gv = np.gradient(h, axis=1), np.gradient(h, axis=0)
gs = float(np.sqrt((gu * gu + gv * gv).mean() / 2))
n2[..., 0] -= k_nrm * gu / gs
n2[..., 1] -= k_nrm * gv / gs
n2 /= np.maximum(np.linalg.norm(n2, axis=-1, keepdims=True), 1e-6)
del g_fine, g_speck, g_mottle, grain, h, gu, gv

# fill the gutters from the nearest island texels (nearest sampling never mixes, but the edges stay clean)
fields = [alb2, smooth2, n2, ao2, f02.astype(np.float32), glow2.astype(np.float32)]
cov = cov2.copy()
for it in range(6):
    if cov.all():
        break
    grown = cov.copy()
    for k, a in enumerate(fields):
        acc = np.zeros_like(a)
        cnt = np.zeros(cov.shape, np.float32)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            m = np.roll(cov, (dy, dx), (0, 1))
            sh = np.roll(a, (dy, dx), (0, 1))
            sel = m & ~cov
            if a.ndim == 3:
                acc[sel] += sh[sel]
            else:
                acc[sel] += sh[sel]
            if k == 0:
                cnt[sel] += 1
        if k == 0:
            cnt_keep = cnt
        sel = cnt_keep > 0
        if a.ndim == 3:
            a[sel] = acc[sel] / cnt_keep[sel][:, None]
        else:
            a[sel] = acc[sel] / cnt_keep[sel]
        if k == 0:
            grown |= sel
    cov = grown
alb2, smooth2, n2, ao2, f02, glow2 = fields
f02 = np.rint(f02)
glow2 = glow2 > 0.5
n2 /= np.maximum(np.linalg.norm(n2, axis=-1, keepdims=True), 1e-6)


def write(path, rgba, colorspace):
    name = 'br50_out_tmp'
    im = bpy.data.images.get(name)
    if im:
        bpy.data.images.remove(im)
    im = bpy.data.images.new(name, RES_OUT, RES_OUT, alpha=True, float_buffer=False)
    im.colorspace_settings.name = colorspace
    q = np.rint(np.clip(rgba, 0, 1) * 255) / 255.0
    im.pixels.foreach_set(q.astype(np.float32).ravel())
    im.filepath_raw = path
    im.file_format = 'PNG'
    im.save()
    bpy.data.images.remove(im)
    print('wrote', path)


# Each F0 class's smoothness scaled (user 2026-10-29, "这把枪的材质有点奇怪"): the coated slabs at 171 mirrored the sky
# under a shader and read grey; coated 171 -> 118, bare steel 175 -> 145 (the BR-51's metal: 120). retone_spec_v2.py
# applied the same to the composed texture.
for cls, k in globals().get('SMOOTH_SCALE', {24: 118 / 171, 28: 145 / 175}).items():
    smooth2[f02 == cls] *= k

one = np.ones((RES_OUT, RES_OUT), np.float32)
write(OUT_PREFIX + '.png', np.dstack([l2s(alb2), one]), 'sRGB')
spec_a = np.where(glow2, TRITIUM_GLOW / 255.0, 1.0)
write(OUT_PREFIX + '_s.png', np.dstack([smooth2, f02 / 255.0, 0 * one, spec_a]), 'Non-Color')
write(OUT_PREFIX + '_n.png', np.dstack([n2[..., 0] * 0.5 + 0.5, -n2[..., 1] * 0.5 + 0.5, ao2, one]), 'Non-Color')
