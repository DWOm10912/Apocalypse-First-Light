#version 150

// Ambient occlusion applied (afl_ssao.fsh): its noise blurred over 5 x 5 pixels, only among pixels at about the same
// distance (no dark halo round a near edge), then multiplied into the frame (blend: destination colour), weighed by
// Strength.

uniform sampler2D Ao;
uniform vec2 ViewSize;
uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 px = 1.0 / ViewSize;
    float z = texture(Ao, texCoord).g;
    float sum = 0.0, weight = 0.0;
    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            vec2 s = texture(Ao, texCoord + vec2(x, y) * px).rg;
            float w = exp(-abs(s.y - z) / (0.04 * z + 0.02));
            sum += s.x * w;
            weight += w;
        }
    }
    float ao = sum / max(weight, 1e-4);
    fragColor = vec4(vec3(mix(1.0, ao, Strength)), 1.0);
}
