#version 150

// Ambient occlusion from the depth alone (AflPost, after the opaque world, before the translucent layer): how much of
// the half-sphere above each point the geometry within Radius metres covers, so corners, the foot of a wall, the ground
// under a truck or a tree's roots darken. The normal comes from the neighbours' depths (the nearer side at a step, so
// edges do not smear). 12 fixed samples turned about the normal by a per-pixel angle (interleaved gradient noise); the
// apply pass blurs the noise out. A sample counts as covered when the depth there is in front of it, weighed down when
// that depth is far outside the radius (a wall behind a post does not darken it). Fades out between FadeFrom metres.
// Out: R the light left (1 none covered), G the distance (for the blur's depth test).

uniform sampler2D Depth;
uniform mat4 WorldProj;
uniform mat4 InvProj;
uniform vec2 ViewSize;
uniform float Radius;
uniform vec2 FadeFrom;

in vec2 texCoord;

out vec4 fragColor;

const vec3 KERNEL[12] = vec3[12](
    vec3(0.0203, 0.0517, 0.0472),
    vec3(0.0057, 0.0587, 0.0855),
    vec3(-0.0376, 0.0029, 0.1094),
    vec3(0.1079, -0.0266, 0.0374),
    vec3(-0.0209, -0.0043, 0.1525),
    vec3(-0.0673, -0.1103, 0.2205),
    vec3(0.1608, -0.0920, 0.1023),
    vec3(0.3128, -0.0779, 0.0880),
    vec3(0.2322, -0.3253, 0.0810),
    vec3(0.0264, -0.3825, 0.3173),
    vec3(0.1937, 0.3071, 0.5086),
    vec3(-0.3428, -0.5362, 0.3008)
);

vec3 viewPos(vec2 uv) {
    vec4 v = InvProj * vec4(vec3(uv, texture(Depth, uv).r) * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

void main() {
    if (texture(Depth, texCoord).r >= 1.0) {
        fragColor = vec4(1.0, 1e4, 0.0, 1.0);
        return;
    }
    vec2 px = 1.0 / ViewSize;
    vec3 P = viewPos(texCoord);
    vec3 r = viewPos(texCoord + vec2(px.x, 0.0)), l = viewPos(texCoord - vec2(px.x, 0.0));
    vec3 u = viewPos(texCoord + vec2(0.0, px.y)), d = viewPos(texCoord - vec2(0.0, px.y));
    vec3 dx = abs(r.z - P.z) < abs(P.z - l.z) ? r - P : P - l;
    vec3 dy = abs(u.z - P.z) < abs(P.z - d.z) ? u - P : P - d;
    vec3 N = normalize(cross(dx, dy));
    if (dot(N, P) > 0.0) N = -N;

    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float a = noise * 6.2831853;
    vec3 rnd = vec3(cos(a), sin(a), 0.0);
    vec3 T = rnd - N * dot(rnd, N);
    T = length(T) < 1e-3 ? normalize(cross(N, vec3(0.0, 1.0, 0.0))) : normalize(T);
    vec3 B = cross(N, T);

    float covered = 0.0;
    for (int i = 0; i < 12; i++) {
        vec3 k = KERNEL[i];
        vec3 S = P + (T * k.x + B * k.y + N * k.z) * Radius;
        vec4 c = WorldProj * vec4(S, 1.0);
        vec2 suv = c.xy / c.w * 0.5 + 0.5;
        if (suv.x < 0.0 || suv.y < 0.0 || suv.x > 1.0 || suv.y > 1.0) continue;
        float z = viewPos(suv).z;
        float near = smoothstep(0.0, 1.0, Radius / max(abs(P.z - z), 1e-4));
        covered += (z >= S.z + 0.02 ? 1.0 : 0.0) * near;
    }
    float ao = 1.0 - covered / 12.0;
    ao = mix(ao, 1.0, smoothstep(FadeFrom.x, FadeFrom.y, -P.z));
    fragColor = vec4(ao, -P.z, 0.0, 1.0);
}
