#version 150

// Physical water's surface without a shader pack (client/liquid/ClientLiquid, docs/gameplay/water_v1_design.md 16.10,
// docs/rendering/water_visual_v1_design.md 5.2). Positions + ChunkOffset are camera-relative; UV0 is the world position (x, z) from
// an origin on a 1024 m grid, for the ripple textures; Color.r is the height from the vertex up to the water's surface
// and Color.g the water's depth below it, both / 8 m, Color.b how far a film's surface has faded in (0-1); Normal the face's (up: the surface, down: its underside, else a side).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
// a chunk's cached water is built from the chunk's corner; this moves it to the camera (falling water 15)
uniform vec3 ChunkOffset;

out float vertexDistance;
out vec3 worldPos;
out vec2 waterUV;
out vec3 lightColor;
out float skyLight;
out vec3 faceNormal;
out float toSurface;
out float shown;
out vec2 flow;
out float film;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    worldPos = pos;
    vertexDistance = FogShape == 0 ? length(pos) : max(length(pos.xz), abs(pos.y));
    waterUV = UV0;
    // the light map at the surface (block and sky light, dimmed at night), as vanilla samples it
    lightColor = texture(Sampler2, clamp(vec2(UV2) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0))).rgb;
    skyLight = clamp(float(UV2.y) / 240.0, 0.0, 1.0);
    faceNormal = Normal;
    toSurface = Color.r * 8.0;
    shown = Color.b;
    // running water's top: its flow, m/s, in the normal as (fx / 4, 1, fz / 4); a film's Color.a 0. Nothing sends them
    // without a shader pack now (falling water 19): (0, 1, 0) and 1, so still water
    flow = Normal.y > 0.5 ? Normal.xz / Normal.y * 4.0 : vec2(0.0);
    film = 1.0 - Color.a;
}
