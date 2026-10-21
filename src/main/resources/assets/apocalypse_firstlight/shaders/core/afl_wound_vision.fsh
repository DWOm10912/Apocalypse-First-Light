#version 150

// Wound vision (health/WoundScreenEffect, docs/gameplay/survival_health_v1a.md 6): the frame out of focus, its bright parts
// spreading (light sensitivity) and its colours flat and a little warm (fever). Sampler0 = the frame, Sampler1 = it at a
// quarter size, Sampler2 = an eighth (both box-averaged by halving blits), so the disc samples below stay dense.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform float BlurRadius;    // the blur disc's radius, a fraction of the screen width
uniform float SharpBelow;    // radius under which the result mixes back toward the sharp frame (the quarter copy is soft already)
uniform float Aspect;        // width / height
uniform float Bloom;         // how much of the bright parts spreads
uniform float BloomRadius;
uniform vec3 Grade;          // desaturation, wash toward pale grey, warm shift

in vec2 texCoord;

out vec4 fragColor;

const float GOLDEN = 2.39996323;
const int BLUR_TAPS = 32;
const int BLOOM_TAPS = 16;

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec3 col = texture(Sampler0, texCoord).rgb;
    if (BlurRadius > 0.0) {
        vec3 sum = vec3(0.0);
        float weights = 0.0;
        for (int i = 0; i < BLUR_TAPS; i++) {
            float r = sqrt((float(i) + 0.5) / float(BLUR_TAPS)), a = float(i) * GOLDEN;
            float w = 1.0 - 0.6 * r * r;   // denser weight in the middle: a softer, gaussian-like edge
            sum += texture(Sampler1, texCoord + vec2(cos(a), sin(a) * Aspect) * r * BlurRadius).rgb * w;
            weights += w;
        }
        col = mix(col, sum / weights, clamp(BlurRadius / SharpBelow, 0.0, 1.0));
    }
    if (Bloom > 0.0) {
        vec3 glow = vec3(0.0);
        for (int i = 0; i < BLOOM_TAPS; i++) {
            float r = sqrt((float(i) + 0.5) / float(BLOOM_TAPS)), a = float(i) * GOLDEN + 0.7;
            vec3 c = texture(Sampler2, texCoord + vec2(cos(a), sin(a) * Aspect) * r * BloomRadius).rgb;
            glow += c * smoothstep(0.55, 0.9, luma(c));
        }
        col += glow / float(BLOOM_TAPS) * Bloom * (1.0 - col * 0.5);
    }
    col = mix(col, vec3(luma(col)), Grade.x);
    col += (vec3(0.8) - col) * Grade.y;
    col += vec3(Grade.z, 0.0, -Grade.z);
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
