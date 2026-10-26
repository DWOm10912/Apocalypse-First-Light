#version 150

// Water in the air (afl_water_fall.vsh) with the standing water's look (afl_water; falling water 17, "水帘、水柱的贴图和
// 我们之前做的水不一样，看起来有点割裂" in game 2026-10-25): the same two ripple textures, here running down it at
// falling speed; the same water colour by Beer-Lambert over the thickness the eye looks through (b, / 0.1 m); the same
// sky by Fresnel (Schlick, 0.02 head-on) and the sun's glint; drawn opaque over a copy of the frame (Scene), which it
// bends by its ripples. Partly there (a < 1): a smooth blend with the copy, not dithered. Where it is broken up (g) it
// turns white in the light around it, in streaks running down. Drops (r 1): the sprite's alpha is their outline, no
// ripples. Ribbons' texture coordinates are metres across and down them. Both sides: the normal faces the eye.
// The ripples' and streaks' speeds times WaterTime's 1,200 s wrap are whole tiles, so the wrap does not jump.

uniform sampler2D Sampler0;
uniform sampler2D Scene;
uniform sampler2D Normal0;
uniform sampler2D Normal1;
uniform vec2 ViewSize;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec3 SkyColor;
uniform vec3 HorizonColor;
uniform vec3 SunDir;
uniform vec3 SunColor;
uniform float Refraction;
uniform float WaterTime;
uniform vec3 Absorption;
uniform float Scatter;
uniform float ScatterLight;
uniform vec2 Waves;

in float vertexDistance;
in vec3 worldPos;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 lightColor;
in float skyLight;
in vec3 normal;
in vec3 viewNormal;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 x) {
    vec2 i = floor(x), f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    bool drop = vertexColor.r > 0.5;
    float cover = vertexColor.a;
    if (drop) cover *= smoothstep(0.25, 0.6, texture(Sampler0, texCoord0).a);

    vec3 V = normalize(worldPos);
    vec3 N = normalize(normal);
    vec3 vn = normalize(viewNormal);
    if (dot(N, V) > 0.0) {
        N = -N;
        vn = -vn;
    }
    // the ribbon's frame across (T) and down (B), from the screen derivatives (taken for drops too: outside any branch)
    vec2 p = texCoord0;
    vec3 dp1 = dFdx(worldPos), dp2 = dFdy(worldPos);
    vec2 du1 = dFdx(p), du2 = dFdy(p);
    vec3 d2 = cross(dp2, N), d1 = cross(N, dp1);
    vec3 T = d2 * du1.x + d1 * du2.x, B = d2 * du1.y + d1 * du2.y;
    float k = inversesqrt(max(max(dot(T, T), dot(B, B)), 1e-12));

    vec2 s = vec2(0.0);
    float streak = 1.0;
    if (!drop) {
        // the ripples, drawn out along it and running down at about falling speed
        vec2 a = texture(Normal0, vec2(p.x, p.y * 0.35 - 2.5 * WaterTime) / 1.6).xy * 2.0 - 1.0;
        vec2 b = texture(Normal1, vec2(p.x, p.y * 0.5 - 3.0 * WaterTime) / 0.8).xy * 2.0 - 1.0;
        s = a * Waves.x + b * Waves.y;
        N = normalize(N + (T * s.x + B * s.y) * k * 0.5);
        streak = noise(vec2(p.x * 24.0, p.y * 3.0 - WaterTime * 3.0));
    }

    float cosV = clamp(dot(-V, N), 0.0, 1.0);
    vec2 uv = gl_FragCoord.xy / ViewSize;
    vec2 ruv = clamp(uv - (vn.xy + s * 0.3) * Refraction * (1.0 - 0.6 * cosV), vec2(0.001), vec2(0.999));

    // the water the eye looks through: longer at a slant
    float thick = vertexColor.b * 0.1 / max(cosV, 0.3);
    vec3 ext = Absorption + vec3(Scatter);
    vec3 through = exp(-ext * thick);
    vec3 own = Scatter / ext * (1.0 - through) * lightColor * ScatterLight;
    vec3 color = texture(Scene, ruv).rgb * through + own;

    float fresnel = 0.02 + 0.98 * pow(1.0 - cosV, 5.0);
    float sees = skyLight * skyLight;
    vec3 R = reflect(V, N);
    vec3 sky = mix(HorizonColor, SkyColor, smoothstep(-0.05, 0.45, R.y)) * sees;
    color = mix(color, sky, fresnel);
    color += SunColor * pow(max(dot(R, SunDir), 0.0), 900.0) * sees;

    float foam = clamp(vertexColor.g * (drop ? 1.0 : smoothstep(0.3, 0.75, streak)), 0.0, 1.0);
    color = mix(color, lightColor * 0.92, foam);
    color = mix(texture(Scene, uv).rgb, color, cover);

    // not there: discarded only now, after the screen derivatives above
    if (cover < 0.01) discard;
    float fog = vertexDistance < FogEnd ? smoothstep(FogStart, FogEnd, vertexDistance) : 1.0;
    fragColor = vec4(mix(color, FogColor.rgb, fog * FogColor.a), 1.0);
}
