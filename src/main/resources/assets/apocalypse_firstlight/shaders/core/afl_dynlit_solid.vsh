#version 150

// The road mesh's program while dynamic lights are on (client/road/RoadRenderTypes, docs/rendering/dynamic_lights_v1.md):
// vanilla rendertype_solid with the light map kept apart from the vertex colour, so the fragment stage can add the lights
// to it. The fog distance comes from the camera-relative world position (exact for both fog shapes, whatever pose the
// model-view matrix carries).

#moj_import <light.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;
uniform mat4 afl_ClipToWorld;

out float vertexDistance;
out vec4 afl_Tint;
out vec3 afl_Lm;
out vec3 afl_Pos;
out vec2 texCoord0;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vec4 world = afl_ClipToWorld * gl_Position;
    afl_Pos = world.xyz / world.w;
    vertexDistance = FogShape == 0 ? length(afl_Pos) : max(length(afl_Pos.xz), abs(afl_Pos.y));
    afl_Tint = Color;
    afl_Lm = minecraft_sample_lightmap(Sampler2, UV2).rgb;
    texCoord0 = UV0;
}
