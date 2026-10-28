#version 150

// Water in the air (afl_water_fall.vsh) with the standing water's look: the body colour, reflection (what stands round
// it traced in the frame copy, else the sky), glint, white water and fog are afl_water's own (afl_water_look.glsl, Mesh
// water M3; "水帘、水柱的贴图和我们之前做的水不一样，看起来有点割裂" in game 2026-10-25). The same two ripple textures,
// here running down it at falling speed; the colour over the thickness the eye looks through (b, / 0.1 m); drawn opaque
// over a copy of the frame (Scene), which it bends by its ripples. Partly there (a < 1): a smooth blend with the copy, not dithered. Where it is broken up (g) it
// turns white in the light around it, in streaks running down. Drops (r 1): the sprite's alpha is their outline, no
// ripples. Ribbons' texture coordinates are metres across and down them. Both sides: the normal faces the eye.
// The ripples' and streaks' speeds times WaterTime's 1,200 s wrap are whole tiles, so the wrap does not jump.

#moj_import <apocalypse_firstlight:afl_water_look.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Normal0;
uniform sampler2D Normal1;
uniform float Refraction;
uniform float WaterTime;
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
        streak = valueNoise(vec2(p.x * 24.0, p.y * 3.0 - WaterTime * 3.0));
    }

    float cosV = clamp(dot(-V, N), 0.0, 1.0);
    vec2 uv = gl_FragCoord.xy / ViewSize;
    vec2 ruv = clamp(uv - (vn.xy + s * 0.3) * Refraction * (1.0 - 0.6 * cosV), vec2(0.001), vec2(0.999));

    // the water the eye looks through: longer at a slant
    float thick = vertexColor.b * 0.1 / max(cosV, 0.3);
    vec3 color = waterThrough(texture(Scene, ruv).rgb, thick, lightColor);
    // drops are too small for a traced mirror image: the sky alone
    color = waterReflect(color, worldPos, V, N, skyLight, 1.0, !drop, false);

    float foam = vertexColor.g * (drop ? 1.0 : smoothstep(0.3, 0.75, streak));
    color = whiteWater(color, foam, lightColor, skyLight);
    color = mix(texture(Scene, uv).rgb, color, cover);

    // not there: discarded only now, after the screen derivatives above
    if (cover < 0.01) discard;
    fragColor = vec4(waterFog(color, vertexDistance), 1.0);
}
