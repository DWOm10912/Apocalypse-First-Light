// The mesh plants' wind (AFL vegetation, docs/gameplay/vegetation_v1_design.md 10.3, 13.8, 13.10): one copy for every
// program that moves them without a shader pack, the Embeddium terrain vertex stage (VegetationWind patches it in) and
// the near layer's own programs (core/afl_vegetation_near_*); Aureveil ports the same formula (13.9: a change raises the
// afl_vegetation layout number and Aureveil is told). VegetationWindMath keeps a CPU copy for offline checks.
uniform vec4 afl_VegRect;   // the vegetation sprite's atlas rectangle (u0, v0, u1, v1); empty: off
uniform vec4 afl_VegWind;   // time (s), wind x and z (m/s, the way it blows), gust (0..1)
uniform vec3 afl_VegCam;    // the camera position, wrapped (Java's %: a phase fixed to the world)
uniform vec4 afl_VegScale[16];  // the wind's reach per sheet column (64 since P2b: PlantShape#WIND)
// a hash without a sine (VegetationWindMath#hash computes the same on the CPU)
float afl_vegHash(vec2 c) {
    vec3 p3 = fract(vec3(c.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
// smooth value noise (0..1): the same at a point for every vertex there, so a blade never tears
float afl_vegNoise(vec2 x) {
    vec2 i = floor(x), f = fract(x), u = f * f * (3.0 - 2.0 * f);
    return mix(mix(afl_vegHash(i), afl_vegHash(i + vec2(1.0, 0.0)), u.x),
               mix(afl_vegHash(i + vec2(0.0, 1.0)), afl_vegHash(i + vec2(1.0, 1.0)), u.x), u.y);
}
// p: camera-relative position; uv: the vertex's atlas coordinates (kept inside its sheet column: PlantShape#inColumn)
vec3 afl_vegWind(vec3 p, vec2 uv) {
    if (afl_VegRect.z <= afl_VegRect.x || uv.x < afl_VegRect.x || uv.x > afl_VegRect.z || uv.y < afl_VegRect.y || uv.y > afl_VegRect.w) return p;
    float h = clamp((afl_VegRect.w - uv.y) / (afl_VegRect.w - afl_VegRect.y), 0.0, 1.0);
    if (h <= 0.001) return p;
    int column = clamp(int((uv.x - afl_VegRect.x) / (afl_VegRect.z - afl_VegRect.x) * 64.0), 0, 63);
    float scale = afl_VegScale[column >> 2][column & 3];
    vec2 wind = afl_VegWind.yz;
    float speed = length(wind);
    vec2 dir = speed > 0.05 ? wind / speed : vec2(0.6, 0.8);
    vec3 w = p + afl_VegCam;
    float t = afl_VegWind.x;
    // gusts: patches of a few metres drifting downwind (two layers of noise), a slow swell over tens of metres,
    // and each spot's own flutter phase; no bands, so the turf does not sway in stripes
    vec2 drift = dir * t * (0.45 + 0.06 * speed);
    float gusts = 0.65 * afl_vegNoise(w.xz * 0.28 - drift * 0.28) + 0.35 * afl_vegNoise(w.xz * 0.7 - drift * 0.7 + 17.0);
    float swell = afl_vegNoise(w.xz * 0.05 - drift * 0.05 + 41.0);
    float flutter = sin(t * 5.5 + afl_vegNoise(w.xz * 1.3) * 6.2832);
    float calm = clamp(speed / 10.0, 0.0, 1.0);
    float lean = (0.015 + 0.07 * calm) * (0.3 + 0.8 * gusts + 0.3 * afl_VegWind.w * swell);
    float k = h * h;
    p.xz += (dir * (k * lean) + vec2(-dir.y, dir.x) * (k * 0.012 * flutter * (0.3 + calm))) * scale;
    p.y -= k * lean * 0.3 * scale;
    return p;
}
