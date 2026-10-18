#version 150

// T15 windshield drops (client/rain/WindshieldRenderer, WindshieldWater): the drops the CPU moves, each a quad on the
// glass drawn over a copy of the frame (Scene, with mipmaps). A drop's surface comes from its shape cell(s) in
// textures/effect/windshield_drops (simulated: tools/blender/windshield_water), a resting shape blended into a running one
// as it starts to slide; a wet line (a slide trail, the water riding on a blade) is round across and even along. Each is
// a lens like the beads (the optics shared with afl_windshield_film): a wide, upside-down view of the world beyond, dim
// cab where the light inside cannot get out. Just around a drop the glass is clear (it took in the beads there).
// Hidden behind what is nearer than the glass (SceneDepth; the glass itself writes depth).

uniform sampler2D Scene;
uniform sampler2D SceneDepth;
uniform sampler2D Drops;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform vec3 SkyLight;
uniform vec3 Interior;
uniform float Inside;

in vec2 local;
flat in vec4 kind;
flat in vec2 axis;
in vec3 viewPos;
in vec3 axisU;
in vec3 axisS;
in vec3 axisN;
in vec3 worldUp;

out vec4 fragColor;

const vec2 SHEET = vec2(8.0, 4.0);

// ---- shared with afl_windshield_film.fsh (keep in step) ----
float linearDepth(float d) {
    return ProjMat[3][2] / (d * 2.0 - 1.0 + ProjMat[2][2]);
}

vec3 toLin(vec3 c) { return pow(max(c, 0.0), vec3(2.2)); }
vec3 toSrgb(vec3 c) { return pow(clamp(c, 0.0, 1.0), vec3(1.0 / 2.2)); }

// unpolarised Fresnel reflectance from index n1 into n2 at incidence cosine ci (1 past the critical angle)
float fresnel(float ci, float n1, float n2) {
    ci = clamp(ci, 0.0, 1.0);
    float e = n1 / n2, st2 = e * e * (1.0 - ci * ci);
    if (st2 >= 1.0) return 1.0;
    float ct = sqrt(1.0 - st2);
    float rs = (n1 * ci - n2 * ct) / (n1 * ci + n2 * ct), rp = (n1 * ct - n2 * ci) / (n1 * ct + n2 * ci);
    return 0.5 * (rs * rs + rp * rp);
}

// where a view-space direction lands on the screen (0..1; may lie off it)
vec2 screenOf(vec3 d) {
    vec4 c = ProjMat * vec4(d, 0.0);
    return c.xy / max(c.w, 1e-3) * 0.5 + 0.5;
}

// the world beyond the glass along view direction d (p = screenOf(d)), from the frame at lod: folded back in at the
// sides and the bottom, the sky above the top. From the cab, where the frame shows the cab itself along d (the roof, a
// pillar, the dash: nearer than the glass), the water sees past it: the sky above the horizon, a dim ground below.
vec3 frameAt(vec3 d, vec2 p, float lod) {
    vec2 q = 1.0 - abs(1.0 - abs(clamp(p, -0.95, 1.95)));
    float away = smoothstep(0.0, 0.25, p.y - 1.0);
    if (Inside > 0.5) {
        vec3 G = normalize(axisN);
        float dg = dot(d, G);
        float glassZ = dg > 1e-3 ? -d.z * dot(viewPos, G) / dg : 0.0;
        away = max(away, 1.0 - smoothstep(glassZ - 0.08, glassZ - 0.03, linearDepth(textureLod(SceneDepth, q, 0.0).r)));
    }
    vec3 elsewhere = SkyLight * mix(0.3, 1.0, smoothstep(-0.12, 0.12, dot(d, normalize(worldUp))));
    return mix(textureLod(Scene, q, lod).rgb, elsewhere, away);
}

// the mip level for a lens: the frame area one pixel of it spans (p unfolded, so its derivatives hold)
float lensLod(vec2 p) {
    vec2 x = dFdx(p) * ScreenSize, y = dFdy(p) * ScreenSize;
    return clamp(0.5 * log2(max(abs(x.x * y.y - x.y * y.x), 1.0)) - 0.5, 0.0, 5.0);
}

// The ray through water with surface normal N (view space, out of the water) seen along V: from the cab, into the water
// through the flat glass (G its outward normal) and out of the curved surface into the air beyond; from outside, into
// the water and on through the glass into the cab. w = the reflectance at the surface it leaves or meets (1: total
// internal reflection, the ray stays in).
vec3 through(vec3 V, vec3 N, vec3 G, out float w) {
    if (Inside > 0.5) {
        vec3 vw = refract(V, -G, 1.0 / 1.333);
        vec3 d = refract(vw, -N, 1.333);
        w = fresnel(dot(vw, N), 1.333, 1.0);
        return dot(d, d) > 0.5 ? d : V;
    }
    vec3 vw = refract(V, N, 1.0 / 1.333);
    w = fresnel(dot(-V, N), 1.0, 1.333);
    vec3 d = refract(vw, G, 1.333);
    return dot(d, d) > 0.5 ? d : V;
}

// the water's colour from what the ray sees, its reflectance, and (from outside) the sky mirrored off it
vec3 shade(vec3 seen, float w, vec3 V, vec3 N, vec3 up) {
    if (Inside > 0.5) return mix(seen, Interior, w);
    vec3 r = reflect(V, N);
    vec3 sky = SkyLight * mix(0.3, 1.0, smoothstep(-0.25, 0.45, dot(r, normalize(up))));
    return mix(seen, sky, w);
}
// ---- end of the shared part ----

vec2 cellUV(float index, vec2 l) {
    float i = floor(index * 255.0 + 0.5);
    return (vec2(mod(i, SHEET.x), floor(i / SHEET.x)) + l * 0.5 + 0.5) / SHEET;
}

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = -viewPos.z;
    bool hidden = linearDepth(texture(SceneDepth, uv).r) < depth - (0.004 + depth * 0.0004);

    vec2 nl;
    float cover, clear;
    if (kind.g > 0.999) {
        // a wet line: round across (kind.b: its edge slope), faded toward both ends where the next segment overlaps
        cover = (1.0 - smoothstep(0.55, 1.0, abs(local.x))) * (1.0 - smoothstep(0.45, 1.0, abs(local.y)));
        nl = vec2(local.x * kind.b, 0.0);
        clear = cover;
    } else {
        vec2 ca = cellUV(kind.r, local), cb = cellUV(kind.g, local);
        vec4 t = mix(texture(Drops, ca), texture(Drops, cb), kind.b);
        cover = t.a;
        nl = t.rg * 2.0 - 1.0;
        // a blurred cover reaches a little past the edge: the beads it took in
        clear = smoothstep(0.0, 0.08, mix(textureLod(Drops, ca, 2.5).a, textureLod(Drops, cb, 2.5).a, kind.b));
    }
    // the quad's frame into the glass's: x across the axis, y along it
    vec2 n = nl.x * vec2(axis.y, -axis.x) + nl.y * axis;
    vec3 nT = vec3(n, sqrt(max(1.0 - dot(n, n), 0.0)));
    vec3 V = normalize(viewPos), G = normalize(axisN);
    vec3 N = normalize(axisU * nT.x + axisS * nT.y + G * nT.z);
    float w;
    vec3 d = through(V, N, G, w);
    vec2 p = screenOf(d);
    float lod = lensLod(p);
    vec3 water = shade(frameAt(d, p, lod), w, V, N, worldUp);

    float a = max(cover, clear) * kind.a;
    if (hidden || a < 0.003) discard;
    // past the drop's edge, out to the clear ring: the bare glass (the frame as it was, without the beads); mixed in
    // linear light like the beads
    vec3 c = toSrgb(mix(toLin(textureLod(Scene, uv, 0.0).rgb), toLin(water), cover / max(max(cover, clear), 1e-4)));
    fragColor = vec4(c, a);
}
