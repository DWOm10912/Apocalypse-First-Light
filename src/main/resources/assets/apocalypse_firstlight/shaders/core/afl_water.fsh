#version 150

// Physical water without a shader pack (afl_water.vsh): drawn opaque over a copy of the frame behind it (Scene,
// SceneDepth), which it bends and dims itself, so the water is a body with depth, not a tinted sheet:
// - the light that comes through: the scene behind, bent by the ripples, dimmed per colour by the length of the view
//   ray inside the water (absorption and scattering, per metre: Beer-Lambert), red first, so deep water is blue-green;
// - the light the water scatters back: its own colour (Scatter over the extinction) grows with that length, lit by the
//   light map at the surface (dark at night and under a roof);
// (body colour, reflection, glint, white water and fog: afl_water_look.glsl, shared with the water in the air)
// - reflection by Fresnel (Schlick, water 0.02 head-on): what stands round the water (banks, grass, trees: traced in
//   the frame copy along the reflected ray, screen-space), else the sky (its colour overhead, the fog's at the horizon)
//   where the surface sees it (sky light); the sun's or moon's glint, by Fresnel too, its edge soft (M1 round 3);
// - two layers of ripples (water_normal_0, 8 m; water_normal_1, 1.6 m), drifting different ways; their speeds times the
//   1,200 s WaterTime wraps are whole tiles, so the wrap does not jump;
// - rings where water lands on it (Impacts, ClientFalls: a drop's ring spreads and fades in 2 s, a fall's goes on and
//   on), and in the rain, rain rings in a 0.5 m cell grid, each cell's drop at its own time (only under open sky);
// - running water's ripples carried along its real flow, and white water (foam, from the vertices: fast water, running
//   water meeting a pool, a break in the slope) as streaks drawn out along the flow (Mesh water A3).
// The ray's length: from the surface to what the depth copy holds behind it; on a side face, no further than up to the
// surface (toSurface); seen from under water, from the eye to the surface. Shores fade in over the first centimetres.

#moj_import <apocalypse_firstlight:afl_water_look.glsl>

uniform sampler2D Normal0;
uniform sampler2D Normal1;
uniform float WaterTime;
uniform vec2 Waves;
uniform float Refraction;
uniform float RainRipple;
uniform vec4 Impacts[16];
uniform int ImpactCount;

in float vertexDistance;
in vec3 worldPos;
in vec2 waterUV;
in vec3 lightColor;
in float skyLight;
in vec3 faceNormal;
in float toSurface;
in float shown;
in vec2 flow;
in float film;
in float foam;
in float clear;

out vec4 fragColor;

// the slope of a ring wave at distance r from where it began, t seconds ago: a crest running out at c, a few wavelengths
// behind it, dying out as it spreads
vec2 ring(vec2 d, float t, float strength, float speed, float wave) {
    float r = length(d);
    if (r < 1e-4) return vec2(0.0);
    float front = speed * t;
    float x = (front - r) / wave;
    if (x < 0.0 || x > 3.0) return vec2(0.0);
    float a = strength * sin(x * 6.2832) * (1.0 - x / 3.0) / (1.0 + r * 6.0) * exp(-t * 1.6);
    return d / r * a;
}

vec2 impactRipples(vec2 p) {
    vec2 s = vec2(0.0);
    for (int i = 0; i < 16; i++) {
        if (i >= ImpactCount) break;
        vec4 im = Impacts[i];
        vec2 d = p - im.xy;
        if (dot(d, d) > 4.0) continue;
        if (im.w < 0.0) {
            // steady: a ring every 0.4 s, without end
            for (int k = 0; k < 3; k++) s += ring(d, mod(WaterTime + float(k) * 0.4, 1.2), im.z, 0.55, 0.07);
        } else {
            float t = WaterTime - im.w;
            if (t >= 0.0) s += ring(d, t, im.z, 0.55, 0.07);
        }
    }
    return s;
}

vec2 rainRipples(vec2 p) {
    vec2 s = vec2(0.0);
    {
        // a 0.5 m grid; each cell (and its eight neighbours) a drop every 0.9-1.6 s at its own place and time
        vec2 cell = floor(p / 0.5);
        for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
            vec2 c = cell + vec2(x, y);
            float h = hash(c);
            if (h > RainRipple * 1.2) continue;
            float period = 0.9 + 0.7 * hash(c + 17.0);
            float t = mod(WaterTime + h * period * 7.0, period);
            vec2 at = (c + vec2(hash(c + 3.1), hash(c + 5.7))) * 0.5;
            s += ring(p - at, t, 0.35, 0.35, 0.05);
        }
    }
    return s;
}

// white water's streaks at p: long along the fixed direction d (moved t along it), fine across it
float streaksAlong(vec2 p, vec2 d, float t) {
    vec2 q = vec2(dot(p, d) - t, dot(p, vec2(-d.y, d.x)));
    return valueNoise(vec2(q.x * 1.5, q.y * 9.0)) * 0.6 + valueNoise(vec2(q.x * 4.0, q.y * 22.0) + 7.3) * 0.4;
}

// ... along the flow dir: the two nearest of 16 fixed directions, blended. Never a frame turning with the flow: p is the
// world position, so a turning frame swings the streaks round the world origin, kilometres away, and a bend's white
// water comes out as arcs across the river (river R2, docs/worldgen/river_lake_v1_design.md 13.4)
float streaks(vec2 p, vec2 dir, float t) {
    const float SECTOR = 6.2831853 / 16.0;
    float a = atan(dir.y, dir.x) / SECTOR, k = floor(a);
    float a0 = k * SECTOR, a1 = a0 + SECTOR;
    return mix(streaksAlong(p, vec2(cos(a0), sin(a0)), t), streaksAlong(p + vec2(3.1, 5.7), vec2(cos(a1), sin(a1)), t),
            smoothstep(0.2, 0.8, a - k));
}

// the two ripple layers at p, drifting their own ways
vec2 ripples(vec2 p) {
    vec2 a = texture(Normal0, (p + vec2(0.04, 0.0) * WaterTime) / 8.0).xy * 2.0 - 1.0;
    vec2 b = texture(Normal1, (p + vec2(0.04, -1.6 * 20.0 / 1200.0) * WaterTime) / 1.6).xy * 2.0 - 1.0;
    return a * Waves.x + b * Waves.y;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ViewSize;
    float dWater = eyeDistance(uv, gl_FragCoord.z);
    vec3 V = normalize(worldPos);
    bool top = faceNormal.y > 0.5, under = faceNormal.y < -0.5;

    vec3 n = normalize(faceNormal);
    if (top || under) {
        vec2 s;
        if (top && dot(flow, flow) > 1e-4) {
            // running water: the ripples carried along its flow, in two phases a second apart, each fading out as the
            // other starts again, so they never stretch out of shape (600 periods in WaterTime's 1,200 s)
            float p0 = fract(WaterTime * 0.5), p1 = fract(WaterTime * 0.5 + 0.5);
            s = mix(ripples(waterUV - flow * p0 * 2.0), ripples(waterUV - flow * p1 * 2.0 + vec2(0.37, 0.61)), abs(1.0 - 2.0 * p0));
        } else s = ripples(waterUV);
        if (top && ImpactCount > 0) s += impactRipples(waterUV);
        // rain rings only where the surface sees the sky
        if (top && RainRipple > 0.0 && skyLight > 0.85) s += rainRipples(waterUV) * smoothstep(0.85, 0.95, skyLight);
        n = normalize(vec3(s.x, 1.0, s.y));
        if (under) n.y = -n.y;
    }

    // the view ray inside the water
    float thick;
    vec2 ruv = uv;
    if (under) {
        thick = dWater;
    } else {
        float sceneDepth = texture(SceneDepth, uv).r;
        thick = max(eyeDistance(uv, sceneDepth) - dWater, 0.0);
        if (top) {
            // bent by the ripples, more the more water there is to look through; not onto something in front of the water
            ruv = uv + n.xz * Refraction * clamp(max(thick, film * 0.3), 0.0, 1.0);
            float bent = texture(SceneDepth, ruv).r;
            if (bent < gl_FragCoord.z) ruv = uv;
            else thick = max(eyeDistance(ruv, bent) - dWater, 0.0);
        } else if (V.y > 1e-3) {
            thick = min(thick, toSurface / V.y);
        }
    }
    thick = min(thick, 64.0);

    vec3 color = waterThroughAs(texture(Scene, ruv).rgb, thick, lightColor, clear);

    // reflection; a running film is thin everywhere: its gloss is not faded out as a shore's is
    float shore = max(smoothstep(0.0, 0.06, thick), film * 0.75);
    color = waterReflect(color, worldPos, V, n, skyLight, shore, top, under);

    // white water: streaks carried along the flow in the ripples' two phases, more and denser the stronger it is, lit as
    // the surface is
    if (top && foam > 0.01) {
        float speed = length(flow);
        vec2 dir = speed > 1e-3 ? flow / speed : vec2(1.0, 0.0);
        float p0 = fract(WaterTime * 0.5), p1 = fract(WaterTime * 0.5 + 0.5);
        float st = mix(streaks(waterUV, dir, speed * p0 * 2.0), streaks(waterUV + vec2(0.37, 0.61), dir, speed * p1 * 2.0), abs(1.0 - 2.0 * p0));
        // soft-edged and never opaque: bright specks read as a white texture (M1 in game 2026-10-26)
        float white = smoothstep(0.85 - foam * 0.45, 1.25 - foam * 0.4, st + foam * 0.25) * foam * 0.55;
        color = whiteWater(color, white, lightColor, skyLight);
    }

    // a film's surface fades in over the ground under it (ClientLiquid SHOW_FROM / SHOW_FULL), running water out at its dry edges
    color = mix(texture(Scene, uv).rgb, color, shown);

    fragColor = vec4(waterFog(color, vertexDistance), 1.0);
}
