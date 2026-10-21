#version 150

// AFL map tiles (client/map/MapProgram): one textured quad per tile, placed by the pose (turned for the minimap).

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
}
