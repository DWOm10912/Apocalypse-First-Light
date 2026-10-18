#version 150

// AFL HUD gauge bands (client/AflGaugeProgram): the same coverage as AflGauge#raster on the CPU, per real pixel.
// Bands: 4 vec4 per band, real pixels and degrees clockwise from the top:
//   (outer, width, start, span), (segments, gap, from, to), (rgb, alpha), (back rgb, back alpha)
// Set with glUniform (not in the json: MC's uniforms have no arrays).

#define MAX_BANDS 48

uniform vec4 Bands[MAX_BANDS * 4];
uniform int BandCount;
uniform vec2 Center;

in vec2 pixel;

out vec4 fragColor;

float clamp01(float v) {
    return clamp(v, 0.0, 1.0);
}

void over(inout vec4 p, vec3 rgb, float a) {
    if (a <= 0.0) return;
    a = min(1.0, a);
    p.rgb = p.rgb * (1.0 - a) + rgb * a;
    p.a = p.a + (1.0 - p.a) * a;
}

float circular(float a, float b) {
    float d = mod(abs(a - b), 360.0);
    return min(d, 360.0 - d);
}

// coverage at deg by the clockwise interval from..to on a full circle, anti-aliased at both ends
float between(float deg, float from, float to, float perDeg) {
    if (to - from >= 360.0 - 1e-4) return 1.0;
    if (to <= from) return 0.0;
    float inside = deg >= from && deg <= to ? min(deg - from, to - deg) : -min(circular(deg, from), circular(deg, to));
    return clamp01(0.5 + inside * perDeg);
}

void main() {
    vec2 d = pixel - Center;
    float r = length(d);
    float deg = degrees(atan(d.x, -d.y));
    if (deg < 0.0) deg += 360.0;
    vec4 p = vec4(0.0);
    for (int i = 0; i < BandCount && i < MAX_BANDS; i++) {
        vec4 shape = Bands[i * 4], fill = Bands[i * 4 + 1], colour = Bands[i * 4 + 2], back = Bands[i * 4 + 3];
        if (colour.a <= 0.0) continue;
        float ro = shape.x, w = shape.y, start = shape.z, span = shape.w;
        float radial = clamp01(0.5 - (abs(r - (ro - w / 2.0)) - w / 2.0));
        if (radial <= 0.0) continue;
        float perDeg = max(r, 1e-3) * 3.14159265 / 180.0;
        bool full = span >= 360.0 - 1e-4;
        float rel = mod(deg - start, 360.0);
        float cover = radial * colour.a;
        if (!full) cover *= clamp01(0.5 + (rel <= span ? min(rel, span - rel) : -min(rel - span, 360.0 - rel)) * perDeg);
        if (fill.x > 0.0 && cover > 0.0) {
            float seg = span / fill.x, local = mod(min(rel, span), seg), gap = degrees(fill.y / max(r, 1e-3));
            cover *= clamp01(0.5 + min(local - gap / 2.0, seg - gap / 2.0 - local) * perDeg);
        }
        if (cover <= 0.0) continue;
        over(p, back.rgb, cover * back.a);
        float f = full ? between(rel, fill.z * 360.0, fill.w * 360.0, perDeg)
                : fill.w <= fill.z ? 0.0 : clamp01(0.5 + min(rel - fill.z * span, fill.w * span - rel) * perDeg);
        over(p, colour.rgb, cover * f);
    }
    if (p.a < 0.5 / 255.0) discard;
    fragColor = vec4(p.rgb / p.a, p.a);
}
