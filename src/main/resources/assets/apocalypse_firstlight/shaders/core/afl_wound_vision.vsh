#version 150

// Wound vision (health/WoundScreenEffect): one quad over the screen, UV0 = the frame copy's coordinates.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
}
