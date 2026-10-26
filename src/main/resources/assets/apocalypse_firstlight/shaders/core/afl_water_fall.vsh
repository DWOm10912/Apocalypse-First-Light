#version 150

// Water in the air without a shader pack (client/liquid/ClientFalls, docs/gameplay/water_falling_v1_design.md 12):
// streams, sheets and drops. Positions camera-relative, unturned (ModelViewMat turns them); Normal in world space.
// Color: r 1 for a drop (the sprite's alpha is its outline), g how broken up and white it is, b the thickness of water
// through it / 0.1 m, a how much of it is there. UV0: a drop's sprite; a ribbon's metres across and down it.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec3 worldPos;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 lightColor;
out float skyLight;
out vec3 normal;
out vec3 viewNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    worldPos = Position;
    vertexDistance = FogShape == 0 ? length(Position) : max(length(Position.xz), abs(Position.y));
    vertexColor = Color;
    texCoord0 = UV0;
    lightColor = texture(Sampler2, clamp(vec2(UV2) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0))).rgb;
    skyLight = clamp(float(UV2.y) / 240.0, 0.0, 1.0);
    normal = Normal;
    viewNormal = mat3(ModelViewMat) * Normal;
}
