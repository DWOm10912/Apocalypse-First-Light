#version 150

// T15 windshield drops (client/rain/WindshieldRenderer): one quad per drop or wet line on the glass, camera-relative
// positions. UV0: the corner in the quad's own frame (-1..1; x across its axis, y along it). UV2: the axis in glass
// coordinates (u, s) times 16383. Color: red / green = the two drop shapes (cells of textures/effect/windshield_drops,
// index / 255; green 1 = a wet line instead), blue = the blend between them (a line: how round it is across), alpha =
// its opacity.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 GlassAcross;
uniform vec3 GlassUp;
uniform vec3 GlassOut;

out vec2 local;
flat out vec4 kind;
flat out vec2 axis;
out vec3 viewPos;
out vec3 axisU;
out vec3 axisS;
out vec3 axisN;
out vec3 worldUp;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    local = UV0;
    kind = Color;
    axis = vec2(UV2) / 16383.0;
    viewPos = view.xyz;
    mat3 r = mat3(ModelViewMat);
    axisU = r * GlassAcross;
    axisS = r * GlassUp;
    axisN = r * GlassOut;
    worldUp = r * vec3(0.0, 1.0, 0.0);
}
