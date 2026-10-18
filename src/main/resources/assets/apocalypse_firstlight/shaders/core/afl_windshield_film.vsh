#version 150

// T15 windshield water, the glass layer (client/rain/WindshieldRenderer, docs/vehicles/t15_windshield_rain_design.md): a
// mesh on the glass, camera-relative positions; UV0 carries the glass coordinates (u across, s up the glass, metres).
// GlassAcross / GlassUp / GlassOut: the glass's axes in the world, turned into view space here.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 GlassAcross;
uniform vec3 GlassUp;
uniform vec3 GlassOut;

out vec2 glass;
out vec3 viewPos;
out vec3 axisU;
out vec3 axisS;
out vec3 axisN;
out vec3 worldUp;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    glass = UV0;
    viewPos = view.xyz;
    mat3 r = mat3(ModelViewMat);
    axisU = r * GlassAcross;
    axisS = r * GlassUp;
    axisN = r * GlassOut;
    worldUp = r * vec3(0.0, 1.0, 0.0);
}
