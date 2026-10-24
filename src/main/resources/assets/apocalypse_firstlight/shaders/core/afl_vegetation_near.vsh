#version 150

// The mesh plants' near layer without a shader pack (vegetation/client/VegetationDynamics, docs/gameplay/vegetation_v1_design.md
// 13.10): vanilla rendertype_solid's vertex stage (the same for rendertype_cutout_mipped) with the plants' wind
// (include/afl_veg_wind.glsl, the code the Embeddium terrain stage gets), so a block drawn here sways as the chunk mesh
// around it while its vertices stay in a buffer: ChunkOffset is the block's place relative to the camera (0 for the
// buffer of the moving blocks, already camera-relative).

#moj_import <light.glsl>
#moj_import <fog.glsl>
#moj_import <apocalypse_firstlight:afl_veg_wind.glsl>

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

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;

void main() {
    vec3 pos = afl_vegWind(Position + ChunkOffset, UV0);
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(ModelViewMat, pos, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}
