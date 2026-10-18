#version 150

// T15 glass water baked for a shader pack (client/rain/WindshieldRenderer, GlassAtlas; docs/rendering/rain_v1.md section
// 6): a quad over one pane's glass range in glass coordinates (Position.xy = UV0 = u, s, metres), drawn into that pane's
// rectangle of the atlas (the viewport); Box (u0, s0, u1, s1) is the range the viewport spans.

in vec3 Position;
in vec2 UV0;

uniform vec4 Box;

out vec2 glass;

void main() {
    gl_Position = vec4((Position.xy - Box.xy) / (Box.zw - Box.xy) * 2.0 - 1.0, 0.0, 1.0);
    glass = UV0;
}
