#version 150

// AFL map tiles (client/map/MapProgram). The tile's alpha is how much of it is known.
// Mask: a rounded rectangle in framebuffer pixels (x0, y0, x1, y1, origin bottom left) and its corner radius; outside it
// nothing is drawn, whatever the pose turned. FogMix 1: unknown shows the fog colour (the minimap), 0: it stays clear
// (the world map draws its own fog under the tiles). Circle (x, y, r; r <= 0 off): past r the map fades to fog over
// Feather pixels (the minimap without a map: only the live circle).
// Set with glUniform (not in the json).

uniform sampler2D Sampler0;
uniform vec4 Mask;
uniform float MaskRadius;
uniform vec3 Circle;
uniform float Feather;
uniform float FogMix;
uniform vec3 Fog;
uniform float Alpha;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 t = texture(Sampler0, texCoord);
    vec3 rgb = t.rgb;
    float a = t.a;
    if (FogMix > 0.5) {
        rgb = mix(Fog, t.rgb, t.a);
        a = 1.0;
    }
    vec2 p = gl_FragCoord.xy;
    vec2 c = (Mask.xy + Mask.zw) * 0.5;
    vec2 h = (Mask.zw - Mask.xy) * 0.5 - vec2(MaskRadius);
    vec2 q = abs(p - c) - h;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - MaskRadius;
    a *= clamp(0.5 - d, 0.0, 1.0);
    if (Circle.z > 0.0) {
        float k = clamp((Circle.z - length(p - Circle.xy)) / max(Feather, 1.0), 0.0, 1.0);
        rgb = mix(Fog, rgb, k);
    }
    if (a <= 0.0) discard;
    fragColor = vec4(rgb, a * Alpha);
}
