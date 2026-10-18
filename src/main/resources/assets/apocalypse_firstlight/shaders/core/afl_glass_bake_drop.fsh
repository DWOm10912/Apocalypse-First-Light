#version 150

// T15 glass water baked for a shader pack, the drops (client/rain/WindshieldRenderer, GlassAtlas; docs/rendering/rain_v1.md
// section 6): a drop's or wet line's cover and surface as afl_windshield_drop works them out (keep in step), over the glass
// layer already baked. A drop takes in the beads just round it (its clear ring), so what lies under it gives way by its
// opacity a = max(cover, clear): blended ONE, ONE_MINUS_SRC_ALPHA on RGB, the destination's A (the blade film) kept.
// Out: B its cover, RG 0.5 a + 0.5 x its normal (U, S) times its cover (the glass layer's encoding, under the blend).

uniform sampler2D Drops;
uniform vec4 Outline;
uniform vec3 Span;

in vec2 local;
in vec2 glass;
flat in vec4 kind;
flat in vec2 axis;

out vec4 fragColor;

const vec2 SHEET = vec2(8.0, 4.0);

// as afl_glass_bake_film.fsh
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

vec2 cellUV(float index, vec2 l) {
    float i = floor(index * 255.0 + 0.5);
    return (vec2(mod(i, SHEET.x), floor(i / SHEET.x)) + l * 0.5 + 0.5) / SHEET;
}

void main() {
    vec2 nl;
    float cover, clear;
    if (kind.g > 0.999) {
        cover = (1.0 - smoothstep(0.55, 1.0, abs(local.x))) * (1.0 - smoothstep(0.45, 1.0, abs(local.y)));
        nl = vec2(local.x * kind.b, 0.0);
        clear = cover;
    } else {
        vec2 ca = cellUV(kind.r, local), cb = cellUV(kind.g, local);
        vec4 t = mix(texture(Drops, ca), texture(Drops, cb), kind.b);
        cover = t.a;
        nl = t.rg * 2.0 - 1.0;
        clear = smoothstep(0.0, 0.08, mix(textureLod(Drops, ca, 2.5).a, textureLod(Drops, cb, 2.5).a, kind.b));
    }
    vec2 n = nl.x * vec2(axis.y, -axis.x) + nl.y * axis;
    float pw = max(length(fwidth(glass)), 1e-5);
    float mask = smoothstep(-pw, pw, inside(glass)) * kind.a;
    float c = cover * mask, a = max(cover, clear) * mask;
    if (a < 0.002) discard;
    fragColor = vec4(0.5 * a + 0.5 * n * c, c, a);
}
