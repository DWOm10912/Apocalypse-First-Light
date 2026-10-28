// The look of AFL water without a shader pack, one for the standing water (afl_water) and the water in the air
// (afl_water_fall): the same body colour, reflection, glint, white water and fog, so a stream and the pool it falls into
// are one material (Mesh water M3; "材质都不一样", the user 2026-10-25, falling water 19). Both programs are drawn
// opaque over a copy of the frame (Scene, SceneDepth); positions camera-relative, unturned (ModelViewMat turns them).

uniform sampler2D Scene;
uniform sampler2D SceneDepth;
uniform mat4 InvProj;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ViewSize;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec3 SkyColor;
uniform vec3 HorizonColor;
uniform vec3 SunDir;
uniform vec3 SunColor;
uniform vec3 Absorption;
uniform float Scatter;
uniform float ScatterLight;
uniform vec3 ScatterTint;

// distance from the eye of the point at screen uv and depth (0-1)
float eyeDistance(vec2 uv, float depth) {
    vec4 v = InvProj * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
    return length(v.xyz / v.w);
}

// screen uv and depth (0-1) of a camera-relative point; z < 0: behind the eye
vec3 toScreen(vec3 p) {
    vec4 c = ProjMat * ModelViewMat * vec4(p, 1.0);
    if (c.w <= 1e-3) return vec3(-1.0);
    return c.xyz / c.w * 0.5 + 0.5;
}

// what the reflected ray from p along r meets in the frame copy: rgb, and a = how sure (0: nothing, the sky). Steps that
// grow from 0.15 m out to about 60 m; a hit is the ray passing behind what the copy holds there, by less than the step
// (else it went behind a hill, not into it), refined by halves. Faded out at the screen's edges and the trace's end, where
// the copy has nothing more to show.
vec4 traceReflection(vec3 p, vec3 r) {
    float step = 0.15, dist = 0.0, prev = 0.0;
    for (int i = 0; i < 28; i++) {
        prev = dist;
        dist += step;
        step *= 1.17;
        vec3 s = toScreen(p + r * dist);
        if (s.z < 0.0 || s.x < 0.0 || s.x > 1.0 || s.y < 0.0 || s.y > 1.0) return vec4(0.0);
        float d = texture(SceneDepth, s.xy).r;
        if (d >= 1.0) continue;
        float behind = length(p + r * dist) - eyeDistance(s.xy, d);
        if (behind <= 0.0) continue;
        if (behind > step * 2.0 + 0.3) return vec4(0.0);
        float lo = prev, hi = dist;
        for (int k = 0; k < 5; k++) {
            float mid = (lo + hi) * 0.5;
            vec3 m = toScreen(p + r * mid);
            float md = texture(SceneDepth, m.xy).r;
            if (md < 1.0 && length(p + r * mid) > eyeDistance(m.xy, md)) hi = mid;
            else lo = mid;
        }
        s = toScreen(p + r * hi);
        vec2 e = min(s.xy, 1.0 - s.xy);
        float sure = smoothstep(0.0, 0.06, e.x) * smoothstep(0.0, 0.06, e.y) * (1.0 - smoothstep(18.0, 26.0, float(i)));
        return vec4(texture(Scene, s.xy).rgb, sure);
    }
    return vec4(0.0);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

// the light through thick metres of the water from behind it (absorption and scattering per metre, Beer-Lambert, red
// first), and what the water scatters back: the colour of what is held in it (ScatterTint: silt, grey-brown), lit by the
// light map where it is (dark at night and under a roof)
vec3 waterThrough(vec3 behind, float thick, vec3 lightColor) {
    vec3 ext = Absorption + vec3(Scatter);
    vec3 through = exp(-ext * thick);
    vec3 own = Scatter * ScatterTint / ext * (1.0 - through) * lightColor * ScatterLight;
    return behind * through + own;
}

// a river's water, clear (river plan R1, "哪有河水这么黑的" in game 2026-10-26): a clean stream lets its gravel bed show
// through a metre or more, tinted green-grey (red absorbed first, little held in it); clear 0 is rain water standing on
// earth (the uniforms), 1 the river's
const vec3 RIVER_ABSORPTION = vec3(0.45, 0.09, 0.07);
const float RIVER_SCATTER = 0.22;
const vec3 RIVER_TINT = vec3(0.40, 0.52, 0.46);

vec3 waterThroughAs(vec3 behind, float thick, vec3 lightColor, float clear) {
    vec3 absorb = mix(Absorption, RIVER_ABSORPTION, clear);
    float scatter = mix(Scatter, RIVER_SCATTER, clear);
    vec3 ext = absorb + vec3(scatter);
    vec3 through = exp(-ext * thick);
    vec3 own = scatter * mix(ScatterTint, RIVER_TINT, clear) / ext * (1.0 - through) * lightColor * ScatterLight;
    return behind * through + own;
}

// reflection by Fresnel (Schlick, water 0.02 head-on) over what comes through: what stands round the water (traced in the
// frame copy along the reflected ray, when trace) else the sky where the surface sees it (skyLight); then the sun's or
// moon's glint, as bright as Fresnel lets it be, its edge rolled off so ripples break it into soft sparkles (M1 round 3).
// shore: how much of the gloss there is (thin edges fade it); under: seen from below the surface.
vec3 waterReflect(vec3 color, vec3 p, vec3 V, vec3 n, float skyLight, float shore, bool trace, bool under) {
    vec3 R = reflect(V, n);
    float cosV = clamp(dot(-V, n), 0.0, 1.0);
    float fresnel = under ? 0.02 : 0.02 + 0.98 * pow(1.0 - cosV, 5.0);
    float sees = skyLight * skyLight;
    vec3 sky = mix(HorizonColor, SkyColor, smoothstep(-0.05, 0.45, R.y)) * sees;
    vec3 mirrored = sky;
    if (trace && fresnel * shore > 0.03) {
        vec4 seen = traceReflection(p, R);
        mirrored = mix(sky, seen.rgb, seen.a);
    }
    color = mix(color, mirrored, fresnel * shore);
    if (!under) {
        float g = pow(max(dot(R, SunDir), 0.0), 500.0) * (0.3 + 6.0 * fresnel);
        color += SunColor * (1.0 - exp(-g)) * 0.35 * sees * shore;
    }
    return color;
}

// white water (broken up, full of air) over color, white 0-1: lit as the water round it is
vec3 whiteWater(vec3 color, float white, vec3 lightColor, float skyLight) {
    return mix(color, vec3(0.80, 0.82, 0.80) * lightColor * (0.55 + 0.35 * skyLight), clamp(white, 0.0, 1.0));
}

vec3 waterFog(vec3 color, float vertexDistance) {
    float fog = vertexDistance < FogEnd ? smoothstep(FogStart, FogEnd, vertexDistance) : 1.0;
    return mix(color, FogColor.rgb, fog * FogColor.a);
}
