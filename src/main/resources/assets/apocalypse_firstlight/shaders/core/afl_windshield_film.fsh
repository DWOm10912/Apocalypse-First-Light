#version 150

// T15 glass water, the glass layer (client/rain/WindshieldRenderer, GlassWater; docs/vehicles/
// t15_windshield_rain_design.md), drawn over a copy of the frame (Scene, with mipmaps) on a mesh on one pane (the
// windshield, the rear window, a door window: TruckGlass), its outline from Outline (left / right edges u = a + b s) and
// Span (bottom, top, corner radius); a pane without wipers has empty Blades, so all of it builds up as Outside says.
// Beads: the fine beads every rainy windshield wears, from textures/effect/windshield_beads (a simulated 0.22 m tile: wet
// share, the normal summed over it, and the wetness at which the beads there show; tools/blender/windshield_water; every
// channel mipmaps right, so a far bead field keeps its true wet share). Two layers of it (the second turned and 1.37 times
// larger) so the tile does not show. Wetness: Film red inside the wipers' sweeps (wiped to 0 by a blade, growing back in
// the rain), Outside elsewhere; the sweeps' edges are worked out here, so the clean arc a blade leaves is sharp. Film
// green: the thin film a blade just left: fine streaks along its stroke, which smear bright lights across them (toward
// the pivot) for a moment.
// Every bead is a lens: the view through it is the world where the ray bent by the glass and the bead goes (a wide view,
// upside down), dim cab where the light inside cannot get out. Beads too small to make out (far, or seen at a slant)
// show the mean of that wide view over a bead's surface instead of their blurred-out normal. The water is mixed into
// the frame in linear light (what the eye's pixel adds up), so bright sky in the beads lifts the glass as it should.
// Hidden behind what is nearer than the glass (SceneDepth; the glass itself writes depth).

uniform sampler2D Scene;
uniform sampler2D SceneDepth;
uniform sampler2D Film;
uniform sampler2D Beads;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform vec3 SkyLight;
uniform vec3 Interior;
uniform float Inside;
uniform float Outside;
uniform vec4 Outline;
uniform vec3 Span;
uniform vec4 Pivots;
uniform vec4 Blades;
uniform vec4 Sweeps;

in vec2 glass;
in vec3 viewPos;
in vec3 axisU;
in vec3 axisS;
in vec3 axisN;
in vec3 worldUp;

out vec4 fragColor;

const float TILE = 0.22, TILE2 = 0.22 * 1.37, TEXELS = 1024.0;
const vec4 FILM = vec4(-0.80, 0.0, 1.60, 0.88);   // film map origin and size (glass coordinates)
const mat2 TURN = mat2(0.8, 0.6, -0.6, 0.8);

// ---- shared with afl_windshield_drop.fsh (keep in step) ----
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

// how far g lies inside the pane's outline (m; negative outside), as client/rain/TruckGlass.Pane.inside: the quad shrunk
// by the corner radius, its distance taken off the radius
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

// the bead tile's mip level at t (tile coordinates) along the pixel's short side (the beads are sampled anisotropically)
float beadLod(vec2 t) {
    vec2 x = dFdx(t) * TEXELS, y = dFdy(t) * TEXELS;
    return 0.5 * log2(max(min(dot(x, x), dot(y, y)), 1e-8));
}

// the share of the beads at a texel that show at wetness wet (a: their time; lod: how many beads it averages, whose
// times spread out over the whole run)
float shown(float a, float wet, float lod) {
    float spread = mix(0.016, 0.96, smoothstep(0.0, 4.0, lod));
    return clamp((wet - a) / spread + 0.5, 0.0, 1.0);
}

// a frame colour as light: roughly what the frame clipped at 1 was (lamps, which every far bead holds a tiny image of)
vec3 asLight(vec3 c) {
    float m = max(c.r, max(c.g, c.b));
    return c / max(1.0 - 0.95 * smoothstep(0.45, 1.0, m), 0.12);
}

// the mean view (linear light) through beads too small to make out: a 40 deg cap's surface sampled at even shares of its
// area (three rings, four ways round), each seen as a wide patch of the frame
vec3 beadMean(vec3 V, vec3 G, vec3 U, vec3 S) {
    const float SINA[3] = float[](0.2624, 0.4550, 0.5866);
    vec3 sum = vec3(0.0);
    for (int r = 0; r < 3; r++)
        for (int k = 0; k < 4; k++) {
            float ph = (float(k) + 0.5 * float(r)) * 1.5708;
            vec2 m = SINA[r] * vec2(cos(ph), sin(ph));
            vec3 N = normalize(U * m.x + S * m.y + G * sqrt(1.0 - dot(m, m)));
            float w;
            vec3 d = through(V, N, G, w);
            sum += toLin(shade(asLight(frameAt(d, screenOf(d), 3.0)), w, V, N, worldUp));
        }
    return sum / 12.0;
}

// within a blade's sweep (pivot, rs: the parked blade's offset up from it, reach along the blade, sweep in rad)?
// rel: the point from the pivot
bool sweptBy(vec2 g, vec2 pivot, float rs, vec2 reach, float sweep, out vec2 rel) {
    rel = g - pivot;
    float q = dot(rel, rel) - rs * rs;
    if (q <= reach.x * reach.x || q >= reach.y * reach.y) return false;
    float along = sqrt(q), rho = sqrt(q + rs * rs);
    vec2 c = vec2(rel.x * along + rel.y * rs, rel.y * along - rel.x * rs) / rho;
    float a = atan(c.y, c.x);
    return a > -0.003 && a < sweep + 0.003;
}

void main() {
    float pw = max(length(fwidth(glass)), 1e-5);
    float mask = smoothstep(-pw, pw, inside(glass));
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float depth = -viewPos.z;
    bool hidden = linearDepth(texture(SceneDepth, uv).r) < depth - (0.004 + depth * 0.0004);

    // wetness: the map inside the sweeps, Outside beyond them
    vec2 relL, relR;
    bool inL = sweptBy(glass, Pivots.xy, Sweeps.z, Blades.xy, Sweeps.x, relL);
    bool inR = sweptBy(glass, Pivots.zw, Sweeps.w, Blades.zw, Sweeps.y, relR);
    vec2 f = texture(Film, (glass - FILM.xy) / FILM.zw).rg;
    float wet = inL || inR ? f.r : Outside;
    float streak = inL || inR ? f.g : 0.0;

    // the beads, two layers (each texel: B the wet share, RG the normal summed over it)
    vec2 tc1 = glass / TILE, tc2 = TURN * glass / TILE2 + vec2(0.37, 0.61);
    vec4 b1 = texture(Beads, tc1), b2 = texture(Beads, tc2);
    float lod1 = beadLod(tc1), lod2 = lod1 - log2(1.37);
    float on1 = shown(b1.a, wet, lod1), on2 = shown(b2.a, wet * 0.55, lod2);
    float c1 = b1.b * on1, c2 = b2.b * on2;
    float cover = c1 + c2 - c1 * c2;
    vec2 n = ((b1.rg * 2.0 - 1.0) * on1 + transpose(TURN) * (b2.rg * 2.0 - 1.0) * on2) / max(c1 + c2, 1.0 / 255.0);
    // how far the beads are past making out (their mean normal says little there)
    float sigma = smoothstep(-0.5, 2.0, lod1);

    // a blade's fresh film: fine streaks along the stroke (the normal tilts across them, toward / away from the pivot)
    vec2 rel = inL ? relL : relR;
    float rho = max(length(rel), 1e-4);
    vec2 radial = rel / rho;
    if (streak > 0.003) {
        float ripple = 0.5 * sin(rho * 5712.0 + 1.3) + 0.3 * sin(rho * 9130.0 + 0.4) + 0.2 * sin(rho * 15800.0 + 2.1);
        n += radial * ripple * 0.035 * min(streak, 1.0);
        float s = min(streak, 1.0);
        sigma *= 1.0 - s * (1.0 - cover);
        cover = max(cover, s);
    }
    if (hidden || cover * mask < 0.002) discard;

    // the lens
    vec3 nT = vec3(n, sqrt(max(1.0 - dot(n, n), 0.0)));
    vec3 V = normalize(viewPos), G = normalize(axisN);
    vec3 N = normalize(axisU * nT.x + axisS * nT.y + G * nT.z);
    float w;
    vec3 d = through(V, N, G, w);
    vec2 p = screenOf(d);
    vec3 c = toLin(shade(frameAt(d, p, lensLod(p)), w, V, N, worldUp));
    if (sigma > 0.002) c = mix(c, beadMean(V, G, normalize(axisU), normalize(axisS)), sigma);

    // the fresh film smears bright lights across its streaks (toward the pivot), for as long as it lasts
    if (streak > 0.003) {
        mat2 j = mat2(dFdx(glass), dFdy(glass));
        float det = j[0][0] * j[1][1] - j[0][1] * j[1][0];
        vec2 tpx = abs(det) > 1e-12 ? vec2(j[1][1] * radial.x - j[1][0] * radial.y, -j[0][1] * radial.x + j[0][0] * radial.y) / det * 0.03 : vec2(0.0);
        float len = length(tpx);
        if (len > 40.0) tpx *= 40.0 / len;
        vec3 sum = vec3(0.0);
        for (int i = -6; i <= 6; i++) sum += max(textureLod(Scene, clamp(uv + tpx * (float(i) / 6.0) / ScreenSize, vec2(0.001), vec2(0.999)), 1.0).rgb - 0.55, 0.0);
        c += toLin(sum / 13.0 * 1.6 * min(streak, 1.0));
    }

    // into the frame in linear light; the glass's outline fades with mask
    vec3 bg = toLin(textureLod(Scene, uv, 0.0).rgb);
    fragColor = vec4(toSrgb(mix(bg, c, cover)), mask);
}
