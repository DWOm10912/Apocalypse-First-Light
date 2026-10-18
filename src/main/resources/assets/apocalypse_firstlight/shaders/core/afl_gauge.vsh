#version 150

// AFL HUD gauge bands (client/AflGaugeProgram): one quad over the gauge, UV0 = the real pixel position in it.

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 pixel;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    pixel = UV0;
}
