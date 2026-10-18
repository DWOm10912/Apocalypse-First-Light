#version 150

// T15 glass water baked for a shader pack, the glass layer (client/rain/WindshieldRenderer, GlassAtlas; docs/rendering/
// rain_v1.md section 6): the fine beads and the blades' fresh film of afl_windshield_film (keep in step), as cover and
// normal instead of the view through them. Out: B the share of the texel the water covers, RG 0.5 + 0.5 x the normal
// (x, y in U, S, Nout) summed over the covered part (so every mip level still averages right), A the thin film a blade
// just left (counted into B, flat). Off the pane's outline B = 0, RG = 0.5.

uniform sampler2D Film;
uniform sampler2D Beads;
uniform float Outside;
uniform vec4 Outline;
uniform vec3 Span;
uniform vec4 Pivots;
uniform vec4 Blades;
uniform vec4 Sweeps;

in vec2 glass;

out vec4 fragColor;

// Aureveil rebuilds the wetness from the baked cover with these (TILE, TILE2, TURN, the second layer's offset, wet * 0.55,
// shown()'s spread; docs/rendering/rain_v1.md section 6, afl_glass_beads): changing them breaks the pack, tell Aureveil A first
const float TILE = 0.22, TILE2 = 0.22 * 1.37, TEXELS = 1024.0;
const vec4 FILM = vec4(-0.80, 0.0, 1.60, 0.88);
const mat2 TURN = mat2(0.8, 0.6, -0.6, 0.8);

// ---- as afl_windshield_film.fsh ----
float inside(vec2 g) {
    float r = Span.z, lo = Span.x + r, hi = Span.y - r;
    float la = Outline.x + r * sqrt(1.0 + Outline.y * Outline.y), ra = Outline.z - r * sqrt(1.0 + Outline.w * Outline.w);
    vec2 p[4] = vec2[](vec2(la + Outline.y * lo, lo), vec2(ra + Outline.w * lo, lo), vec2(ra + Outline.w * hi, hi), vec2(la + Outline.y * hi, hi));
    float outside = -1e9, near = 1e9;
    for (int i = 0; i < 4; i++) {
        vec2 e = p[(i + 1) & 3] - p[i], w = g - p[i];
        outside = max(outside, (w.x * e.y - w.y * e.x) / length(e));
        near = min(near, length(w - e * clamp(dot(w, e) / dot(e, e), 0.0, 1.0)));
    }
    return r - (outside <= 0.0 ? outside : near);
}

float beadLod(vec2 t) {
    vec2 x = dFdx(t) * TEXELS, y = dFdy(t) * TEXELS;
    return 0.5 * log2(max(min(dot(x, x), dot(y, y)), 1e-8));
}

float shown(float a, float wet, float lod) {
    float spread = mix(0.016, 0.96, smoothstep(0.0, 4.0, lod));
    return clamp((wet - a) / spread + 0.5, 0.0, 1.0);
}

bool sweptBy(vec2 g, vec2 pivot, float rs, vec2 reach, float sweep) {
    vec2 rel = g - pivot;
    float q = dot(rel, rel) - rs * rs;
    if (q <= reach.x * reach.x || q >= reach.y * reach.y) return false;
    float along = sqrt(q), rho = sqrt(q + rs * rs);
    vec2 c = vec2(rel.x * along + rel.y * rs, rel.y * along - rel.x * rs) / rho;
    float a = atan(c.y, c.x);
    return a > -0.003 && a < sweep + 0.003;
}
// ---- end ----

void main() {
    float pw = max(length(fwidth(glass)), 1e-5);
    float mask = smoothstep(-pw, pw, inside(glass));

    bool swept = sweptBy(glass, Pivots.xy, Sweeps.z, Blades.xy, Sweeps.x) || sweptBy(glass, Pivots.zw, Sweeps.w, Blades.zw, Sweeps.y);
    vec2 f = texture(Film, (glass - FILM.xy) / FILM.zw).rg;
    float wet = swept ? f.r : Outside;
    float streak = swept ? min(f.g, 1.0) : 0.0;

    vec2 tc1 = glass / TILE, tc2 = TURN * glass / TILE2 + vec2(0.37, 0.61);
    vec4 b1 = texture(Beads, tc1), b2 = texture(Beads, tc2);
    float lod1 = beadLod(tc1), lod2 = lod1 - log2(1.37);
    float on1 = shown(b1.a, wet, lod1), on2 = shown(b2.a, wet * 0.55, lod2);
    float c1 = b1.b * on1, c2 = b2.b * on2;
    float cover = c1 + c2 - c1 * c2;
    // the normal summed over the covered part (the tile's RG is that sum over a texel already)
    vec2 sum = (b1.rg * 2.0 - 1.0) * on1 + transpose(TURN) * (b2.rg * 2.0 - 1.0) * on2;
    // the two layers overlap: scaled to the cover they make together
    sum *= cover / max(c1 + c2, 1.0 / 255.0);
    cover = max(cover, streak);

    fragColor = vec4(0.5 + 0.5 * sum * mask, cover * mask, streak * mask);
}
