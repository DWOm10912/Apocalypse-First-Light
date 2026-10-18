#version 150

// Rain V1 streaks (afl_rain.vsh): soft across the streak and at both ends, added over the scene, fading into the fog.

uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    float across = 1.0 - abs(texCoord0.x * 2.0 - 1.0);
    float along = smoothstep(0.0, 0.18, texCoord0.y) * (1.0 - smoothstep(0.62, 1.0, texCoord0.y));
    float fog = FogEnd > FogStart ? clamp((FogEnd - vertexDistance) / (FogEnd - FogStart), 0.0, 1.0) : 1.0;
    float a = vertexColor.a * across * along * fog;
    if (a < 0.002) discard;
    fragColor = vec4(vertexColor.rgb, a);
}
