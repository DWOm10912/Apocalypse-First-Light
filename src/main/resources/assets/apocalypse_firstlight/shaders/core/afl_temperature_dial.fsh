#version 150

// AFL temperature dial (temperature/TemperatureDialProgram): the same picture as TemperatureDial#fill on the CPU, per real
// pixel. Look: the frame's values from TemperatureDial#look (real pixels, radians, colours 0..1); set with glUniform (not
// in the json: MC's uniforms have no arrays). pixel: the position in the dial's square (UV0).

#define VECS 32

uniform vec4 Look[VECS];

in vec2 pixel;

out vec4 fragColor;

const float PI = 3.14159265;

float clamp01(float v) {
    return clamp(v, 0.0, 1.0);
}

void over(inout vec4 p, vec3 rgb, float a) {
    if (a <= 0.0) return;
    a = min(1.0, a);
    p.rgb = p.rgb * (1.0 - a) + rgb * a;
    p.a = p.a + (1.0 - p.a) * a;
}


// as AflRingIcon.segment: distance to the segment a..b
float segment(vec2 p, vec2 a, vec2 b) {
    vec2 e = b - a;
    float t = clamp01(dot(p - a, e) / dot(e, e));
    return length(p - a - t * e);
}

// as AflRingIcon.convex for a triangle listed clockwise on screen
float convex3(vec2 p, vec2 a, vec2 b, vec2 c) {
    vec2 v[3] = vec2[3](a, b, c);
    float d = -1e9;
    for (int i = 0; i < 3; i++) {
        vec2 s = v[i], e = v[(i + 1) % 3] - s;
        d = max(d, ((p.x - s.x) * e.y - (p.y - s.y) * e.x) / length(e));
    }
    return d;
}

// as TemperatureDial.snowflake: six arms from straight up, a pair of branches at 45 degrees on each
float snowflake(vec2 p) {
    float d = 1e9;
    for (int k = 0; k < 6; k++) {
        float a = float(k) * PI / 3.0 - PI / 2.0;
        vec2 u = vec2(cos(a), sin(a));
        d = min(d, segment(p, vec2(0.5), vec2(0.5) + u * 0.40) - 0.048);
        vec2 b = vec2(0.5) + u * 0.25;
        for (int side = 0; side < 2; side++) {
            float c = a + float(side * 2 - 1) * PI / 4.0;
            d = min(d, segment(p, b, b + vec2(cos(c), sin(c)) * 0.13) - 0.042);
        }
    }
    return d;
}

// as TemperatureDial.flame
float flame(vec2 p) {
    float outer = min(min(length(p - vec2(0.5, 0.66)) - 0.24, convex3(p, vec2(0.58, 0.08), vec2(0.735, 0.60), vec2(0.265, 0.60))),
            min(length(p - vec2(0.36, 0.62)) - 0.13, convex3(p, vec2(0.30, 0.30), vec2(0.47, 0.62), vec2(0.24, 0.62))));
    float inner = min(length(p - vec2(0.5, 0.74)) - 0.095, convex3(p, vec2(0.52, 0.47), vec2(0.595, 0.73), vec2(0.405, 0.73)));
    return max(outer, -inner);
}

float toSrgb(float c) {
    return c <= 0.0031308 ? 12.92 * c : 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

// as TemperatureDial.fromOklab
vec3 fromOklab(vec3 lab) {
    float l = lab.x + 0.3963377774 * lab.y + 0.2158037573 * lab.z;
    float m = lab.x - 0.1055613458 * lab.y - 0.0638541728 * lab.z;
    float s = lab.x - 0.0894841775 * lab.y - 1.2914855480 * lab.z;
    l = l * l * l; m = m * m * m; s = s * s * s;
    return vec3(clamp01(toSrgb(clamp01(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s))),
            clamp01(toSrgb(clamp01(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s))),
            clamp01(toSrgb(clamp01(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s))));
}

// as TemperatureDial.ambientColour: ice blue .. neutral .. orange
vec3 ambientColour(float celsius) {
    vec3 ice = Look[10].xyz, neutral = Look[11].xyz, heat = Look[12].xyz;
    return celsius < 10.0 ? fromOklab(mix(ice, neutral, clamp01((celsius + 20.0) / 30.0)))
            : fromOklab(mix(neutral, heat, clamp01((celsius - 10.0) / 30.0)));
}

void main() {
    float s = Look[0].x, r0 = Look[0].y, rIn = Look[0].z, band = Look[0].w;
    vec2 xy = pixel - Look[30].xy;
    float x = xy.x, y = xy.y, r = length(xy), ang = atan(x, -y);   // ang: 0 at the top, clockwise +
    vec4 p = vec4(0.0);
    // halo and ripple
    float haloAlpha = Look[1].w, haloReach = Look[2].x, rippleR = Look[2].y, rippleAlpha = Look[2].z, outline = Look[2].w;
    if (r > r0 - 0.5 && haloAlpha > 0.003) {
        float g = clamp01(1.0 - (r - r0) / haloReach);
        if (g > 0.0) over(p, Look[1].rgb, haloAlpha * pow(g, 1.6));
    }
    if (rippleAlpha > 0.003) over(p, Look[1].rgb, rippleAlpha * clamp01(0.5 - (abs(r - rippleR) - 0.3 * s)));
    // bezel, lit from above, and its outline
    float bezelIn = Look[3].x, bezelOut = Look[3].y, rim = Look[3].z;
    if (r > bezelIn - 1.0 && r < bezelOut + outline + 1.0) {
        float bez = clamp01(0.5 - (abs(r - (bezelIn + bezelOut) / 2.0) - (bezelOut - bezelIn) / 2.0));
        if (bez > 0.0) {
            float lit = pow(clamp01(0.5 - 0.5 * y / max(r, 1e-3)), 1.3);
            over(p, mix(Look[5].rgb, Look[4].rgb, lit), bez);
            over(p, Look[6].rgb, clamp01(0.5 - (abs(r - (bezelOut - rim / 2.0)) - rim / 2.0)));
        }
        over(p, Look[7].rgb, Look[3].w * clamp01(0.5 - (abs(r - (bezelOut + outline / 2.0)) - outline / 2.0)));
    }
    // air temperature arc and its marker
    float ar = Look[8].y, aband = Look[8].z, aspan = Look[8].w, aTip = Look[9].x, aBase = Look[9].y;
    if (Look[8].x > 0.0 && r > ar - aband - 1.0 && r < aBase + 2.0) {
        float onArc = clamp01(0.5 - (abs(r - ar) - aband / 2.0)) * clamp01(0.5 + (aspan - abs(ang)) * r);
        if (onArc > 0.0) over(p, ambientColour(10.0 + ang / aspan * 30.0), 0.6 * onArc);
        float along = x * Look[9].z + y * Look[9].w, across = x * Look[9].w - y * Look[9].z;
        if (along > aTip - 1.0 && along < aBase + 1.0) {
            float halfWidth = max(0.0, (along - aTip) / (aBase - aTip)) * 1.5 * s;
            over(p, Look[13].rgb, clamp01(0.5 - (abs(across) - halfWidth)) * clamp01(0.5 - (aTip - along)) * clamp01(0.5 - (along - aBase)));
        }
    }
    // face and gauge band, the fill from the top to the core marker
    float face = clamp01(0.5 - (r - r0));
    if (face > 0.0) over(p, Look[14].rgb, Look[14].w * face);
    float marker = Look[17].x, lo = Look[17].w, hi = Look[18].x, span = Look[18].y;
    float onBand = clamp01(0.5 - (abs(r - (r0 + rIn) / 2.0) - band / 2.0 + 0.35 * s));
    float inSpan = clamp01(0.5 + (span - abs(ang)) * r);
    if (onBand > 0.0 && inSpan > 0.0) {
        float a = onBand * inSpan, notch = clamp01(abs(x) - 0.35 * s + 0.5);
        if (ang < 0.0) over(p, Look[15].rgb, a * Look[15].w * notch);
        else over(p, Look[16].rgb, a * Look[16].w * notch);
        if (abs(marker) > 0.01) over(p, Look[19].rgb, a * clamp01(0.5 + min(ang - lo, hi - ang) * r));
    }
    // target frame
    float targetAlpha = Look[21].x;
    if (targetAlpha > 0.003) {
        float tx = Look[21].y, ty = Look[21].z;
        float ta = x * tx + y * ty, tc = abs(x * ty - y * tx);
        float frame = max(abs(ta - (r0 + rIn) / 2.0) - Look[21].w, tc - Look[22].x);
        over(p, Look[23].rgb, targetAlpha * clamp01(0.5 - (abs(frame) - Look[22].y)));
    }
    // core marker
    float mx = Look[17].y, my = Look[17].z;
    float along = x * mx + y * my, across = abs(x * my - y * mx);
    float mk = clamp01(0.5 - (across - 0.55 * s)) * clamp01(0.5 - abs(along - (r0 + rIn) / 2.0) + band / 2.0 + 0.45 * s);
    if (mk > 0.0) over(p, Look[20].rgb, mk);
    // glyph: the dot shrinking away while the snowflake / flame grows in
    if (Look[24].x > 0.0 && r < rIn - 0.5 * s) {
        float g = Look[24].y, box = Look[24].w, scale = Look[25].z;
        vec2 uv = (pixel - Look[25].xy) / box;
        float d = length(uv - vec2(0.5)) - 0.17 * (1.0 - g);
        if (g >= 0.02) {
            vec2 su = vec2(0.5) + (uv - vec2(0.5)) / scale;
            d = min(d, (Look[24].z > 0.0 ? snowflake(su) : flame(su)) * scale);
        }
        over(p, Look[26].rgb, clamp01(0.5 - d * box));
    }
    // trend chevrons through the bottom gap
    float trendWeight = Look[27].x;
    if (trendWeight > 0.002 && abs(x) < 2.4 * s && y > 4.6 * s) {
        bool warming = Look[27].y > 0.0;
        float phase = Look[27].z, stroke = Look[27].w, yA = Look[28].x, yB = Look[28].y, hw = Look[28].z, hh = Look[28].w;
        float cover = 0.0;
        for (int i = 0; i < 3; i++) {
            float t = fract(phase + float(i) / 3.0), cyI = warming ? yB - t * (yB - yA) : yA + t * (yB - yA);
            float fade = pow(max(0.0, sin(PI * t)), 1.3), uy = (y - cyI) * (warming ? -1.0 : 1.0);
            vec2 q = vec2(x, uy);
            float d = min(segment(q, vec2(-hw, -hh / 2.0), vec2(0.0, hh / 2.0)), segment(q, vec2(0.0, hh / 2.0), vec2(hw, -hh / 2.0))) - stroke;
            cover = max(cover, clamp01(0.5 - d) * fade);
        }
        over(p, Look[29].rgb, cover * trendWeight);
    }
    if (p.a < 0.5 / 255.0) discard;
    fragColor = vec4(p.rgb / p.a, p.a);
}
