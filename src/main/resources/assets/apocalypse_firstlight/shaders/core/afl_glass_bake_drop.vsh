#version 150

// T15 glass water baked for a shader pack, the drops (client/rain/WindshieldRenderer, GlassAtlas): the quads of
// afl_windshield_drop (same attributes) with their corners in glass coordinates (Position.xy = u, s, metres), drawn into
// the pane's rectangle of the atlas; Box (u0, s0, u1, s1) is the range the viewport spans.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform vec4 Box;

out vec2 local;
out vec2 glass;
flat out vec4 kind;
flat out vec2 axis;

void main() {
    gl_Position = vec4((Position.xy - Box.xy) / (Box.zw - Box.xy) * 2.0 - 1.0, 0.0, 1.0);
    local = UV0;
    glass = Position.xy;
    kind = Color;
    axis = vec2(UV2) / 16383.0;
}
