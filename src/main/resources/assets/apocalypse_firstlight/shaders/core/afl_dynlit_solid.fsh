#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 afl_Tint;
in vec3 afl_Lm;
in vec3 afl_Pos;
in vec2 texCoord0;

out vec4 fragColor;

// @afl:dynamic_light (client/light/DynamicLightShaders puts shaders/include/afl_dynamic_light.glsl here)

void main() {
    vec3 afl_N = afl_normal(afl_Pos);
    vec4 color = texture(Sampler0, texCoord0) * vec4(afl_Tint.rgb * afl_lit(afl_Lm, afl_Pos, afl_N), afl_Tint.a) * ColorModulator;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
