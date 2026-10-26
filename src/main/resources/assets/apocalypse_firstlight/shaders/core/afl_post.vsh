#version 150

// AFL picture upgrade (client/post/AflPost, docs/rendering/vanilla_visual_upgrade_v1.md): one quad over the whole
// screen, its corners given in clip space; texCoord 0-1 across the screen.

in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
