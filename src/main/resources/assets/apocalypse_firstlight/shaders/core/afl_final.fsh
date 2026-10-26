#version 150

// The finished frame, before the HUD (AflPost): edge smoothing, then a gentle grade.
// - Edge smoothing (Smooth 1: on), the published FXAA method written out here: where the brightness across a pixel's
//   neighbours jumps (a geometry or texture edge), find whether the edge runs across or along, walk along it both ways
//   to its ends, and take the colour from a fraction of a pixel across it by where the pixel lies along the edge; very
//   thin lines (wires, grass blades) are blended by how much they stand out from their neighbourhood.
// - Grade: an S-curve that keeps 0 black and 1 white (true darkness unchanged; Contrast how much of it) and a little
//   more saturation (Saturation, 1: none).

uniform sampler2D Scene;
uniform vec2 ViewSize;
uniform float Smooth;
uniform float Contrast;
uniform float Saturation;

in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float lumaAt(vec2 uv) {
    return luma(texture(Scene, uv).rgb);
}

const float STEPS[12] = float[12](1.0, 1.0, 1.0, 1.0, 1.0, 1.5, 2.0, 2.0, 2.0, 2.0, 4.0, 8.0);

vec3 smoothEdges(vec2 uv, vec2 px) {
    vec3 cM = texture(Scene, uv).rgb;
    float lM = luma(cM);
    float lN = lumaAt(uv + vec2(0.0, px.y)), lS = lumaAt(uv - vec2(0.0, px.y));
    float lE = lumaAt(uv + vec2(px.x, 0.0)), lW = lumaAt(uv - vec2(px.x, 0.0));
    float lMin = min(lM, min(min(lN, lS), min(lE, lW))), lMax = max(lM, max(max(lN, lS), max(lE, lW)));
    float range = lMax - lMin;
    if (range < max(0.0312, lMax * 0.125)) return cM;

    float lNE = lumaAt(uv + px), lSW = lumaAt(uv - px);
    float lNW = lumaAt(uv + vec2(-px.x, px.y)), lSE = lumaAt(uv + vec2(px.x, -px.y));
    // an edge running across (horizontal) changes most from north to south
    float across = abs(lN + lS - 2.0 * lM) * 2.0 + abs(lNE + lSE - 2.0 * lE) + abs(lNW + lSW - 2.0 * lW);
    float along = abs(lE + lW - 2.0 * lM) * 2.0 + abs(lNE + lNW - 2.0 * lN) + abs(lSE + lSW - 2.0 * lS);
    bool horizontal = across >= along;

    // which side of the pixel the edge lies on
    float l1 = horizontal ? lS : lW, l2 = horizontal ? lN : lE;
    float g1 = abs(l1 - lM), g2 = abs(l2 - lM);
    float step = horizontal ? px.y : px.x;
    float lEdge, gradient;
    if (g1 >= g2) {
        step = -step;
        lEdge = 0.5 * (l1 + lM);
        gradient = g1;
    } else {
        lEdge = 0.5 * (l2 + lM);
        gradient = g2;
    }
    gradient *= 0.25;

    // walk along the edge, halfway onto it, until its brightness leaves the edge's on each side
    vec2 start = uv + (horizontal ? vec2(0.0, step * 0.5) : vec2(step * 0.5, 0.0));
    vec2 dir = horizontal ? vec2(px.x, 0.0) : vec2(0.0, px.y);
    vec2 a = start - dir, b = start + dir;
    float ea = lumaAt(a) - lEdge, eb = lumaAt(b) - lEdge;
    bool doneA = abs(ea) >= gradient, doneB = abs(eb) >= gradient;
    for (int i = 1; i < 12; i++) {
        if (doneA && doneB) break;
        if (!doneA) {
            a -= dir * STEPS[i];
            ea = lumaAt(a) - lEdge;
            doneA = abs(ea) >= gradient;
        }
        if (!doneB) {
            b += dir * STEPS[i];
            eb = lumaAt(b) - lEdge;
            doneB = abs(eb) >= gradient;
        }
    }
    float da = horizontal ? uv.x - a.x : uv.y - a.y;
    float db = horizontal ? b.x - uv.x : b.y - uv.y;
    bool nearerA = da < db;
    float offset = 0.5 - min(da, db) / (da + db);
    // only toward the end whose brightness goes the other way from this pixel's
    if (((nearerA ? ea : eb) < 0.0) == (lM < lEdge)) offset = 0.0;

    // thin lines: by how much the pixel stands out from its neighbourhood
    float mean = (2.0 * (lN + lS + lE + lW) + lNE + lNW + lSE + lSW) / 12.0;
    float sub = clamp(abs(mean - lM) / range, 0.0, 1.0);
    sub = (-2.0 * sub + 3.0) * sub * sub;
    offset = max(offset, sub * sub * 0.75);

    vec2 uv2 = uv + (horizontal ? vec2(0.0, offset * step) : vec2(offset * step, 0.0));
    return texture(Scene, uv2).rgb;
}

void main() {
    vec2 px = 1.0 / ViewSize;
    vec3 c = Smooth > 0.5 ? smoothEdges(texCoord, px) : texture(Scene, texCoord).rgb;
    c = clamp(c, 0.0, 1.0);
    c = mix(c, c * c * (3.0 - 2.0 * c), Contrast);
    float l = luma(c);
    c = clamp(mix(vec3(l), c, Saturation), 0.0, 1.0);
    fragColor = vec4(c, 1.0);
}
