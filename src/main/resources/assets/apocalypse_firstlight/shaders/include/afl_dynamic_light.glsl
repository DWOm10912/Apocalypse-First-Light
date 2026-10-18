// AFL Dynamic Lights V1 (client/light/DynamicLights, docs/rendering/dynamic_lights_v1.md): moving lights (the T15's lamps,
// later hand torches and lamps with beams) worked out per pixel, from where the lamps are this frame, instead of as block
// light (which steps by whole blocks, shades per vertex in triangles and has to rebuild the chunk meshes it touches).
// Inserted by client/light/DynamicLightShaders into the no-shader-pack world programs (Embeddium's terrain, the vanilla
// entity programs, the road program); never used with a shader pack. Positions are relative to the camera, in world axes.
//
// A light (6 vec4 in afl_Lights):
//   0: lamp position, reach (m)
//   1: forward (unit), light near the lamp (block light levels)
//   2: up (unit), light at FAR_SHARE of the reach (levels)
//   3: aim below forward, half spread across, span below and above the aim (radians)
//   4: colour (rgb), extra reach on the right edge (share)
//   5: occluder index, full light this close (m), the spread is measured from this far behind (m), loss toward the bottom
// The pattern is client/vehicle HeadlightBeam's: full light within the near distance, falling with the log of the distance
// to the far light, from there smoothly to nothing at the reach; across times 1 - share^2, above the aim times 1 - share^2
// (a soft cut-off), below a little less toward the bottom. Lights that share an occluder (the lamps at one end of a truck)
// share one shadow ray per pixel: lights come sorted by occluder.

#define AFL_DYNAMIC_LIGHT 1
#define AFL_MAX_LIGHTS 24
#define AFL_MAX_OCCLUDERS 8
#define AFL_MAX_STEPS 160

uniform int afl_LightCount;
uniform vec4 afl_Lights[AFL_MAX_LIGHTS * 6];
uniform vec4 afl_Occluders[AFL_MAX_OCCLUDERS];
// The blocks around the camera, 128 per side, wrapped (index = world block & 127): 0 open, 255 a full block, 1-254 solid
// from the bottom up to (v - 1) / 253 of the block (a slab, a road layer, a smoothed ground top's lowest corner).
uniform sampler3D afl_OccTex;
uniform ivec3 afl_CamBlock;
uniform vec3 afl_CamFrac;
uniform ivec3 afl_OccMin;
uniform mat4 afl_ClipToWorld;
uniform vec2 afl_ScreenSize;
uniform float afl_Gamma;

#ifndef AFL_VERTEX_STAGE
// This fragment's position (camera-relative, world axes) from its window position and depth.
vec3 afl_fragPos() {
    vec4 ndc = vec4(gl_FragCoord.xy / afl_ScreenSize * 2.0 - 1.0, gl_FragCoord.z * 2.0 - 1.0, 1.0);
    vec4 p = afl_ClipToWorld * ndc;
    return p.xyz / p.w;
}

// The face's normal from the position's screen derivatives, turned toward the camera. Call at the start of main (before
// any discard).
vec3 afl_normal(vec3 p) {
    vec3 n = cross(dFdx(p), dFdy(p));
    float l = length(n);
    n = l > 1e-12 ? n / l : vec3(0.0, 1.0, 0.0);
    return dot(n, p) > 0.0 ? -n : n;
}
#endif

// One light's pattern at v (from the lamp), block light levels.
float afl_pattern(int i, vec3 v) {
    vec4 a = afl_Lights[i * 6], b = afl_Lights[i * 6 + 1], c = afl_Lights[i * 6 + 2], s = afl_Lights[i * 6 + 3], e = afl_Lights[i * 6 + 5];
    vec3 fwd = b.xyz, up = c.xyz, right = cross(fwd, up);
    float f = dot(v, fwd);
    if (f <= 0.0) return 0.0;
    float r = dot(v, right), u = dot(v, up);
    float hz = sqrt(f * f + r * r), d = sqrt(hz * hz + u * u);
    float share = atan(r, f + e.z) / s.y;
    if (abs(share) >= 1.0) return 0.0;
    float va = atan(u, hz) + s.x;
    float vs = va < 0.0 ? -va / s.z : va / s.w;
    if (vs >= 1.0 && va > 0.0) return 0.0;
    float reach = a.w * (1.0 + afl_Lights[i * 6 + 4].w * max(0.0, share)), farAt = reach * 0.7;
    if (d >= reach) return 0.0;
    float along;
    if (d <= e.y) along = 1.0;
    else if (d <= farAt) along = 1.0 - (1.0 - c.w / b.w) * log(d / e.y) / log(farAt / e.y);
    else {
        float t = (d - farAt) / (reach - farAt);
        along = c.w / b.w * (1.0 - t * t * (3.0 - 2.0 * t));
    }
    float vertical = va > 0.0 ? 1.0 - vs * vs : 1.0 - e.w * min(1.0, vs * vs);
    return b.w * along * (1.0 - share * share) * vertical;
}

// 1 if nothing solid lies between the point (camera-relative, lifted off its face along n) and the occluder's origin, else
// 0: a walk through the block grid (3D DDA). The point's own block is skipped; past the grid's edge counts as open.
float afl_visible(vec3 p, vec3 n, vec3 o) {
    vec3 start = p + n * 0.06 + afl_CamFrac;
    vec3 dir = o + afl_CamFrac - start;
    float len = length(dir);
    if (len < 0.5) return 1.0;
    dir /= len;
    float maxT = len - 0.4;
    ivec3 cell = ivec3(floor(start));
    ivec3 stp = ivec3(dir.x > 0.0 ? 1 : -1, dir.y > 0.0 ? 1 : -1, dir.z > 0.0 ? 1 : -1);
    vec3 inv = 1.0 / max(abs(dir), vec3(1e-6));
    vec3 fc = vec3(cell);
    vec3 tMax = mix((start - fc) * inv, (fc + 1.0 - start) * inv, vec3(greaterThan(dir, vec3(0.0))));
    float t = 0.0;
    for (int k = 0; k < AFL_MAX_STEPS; k++) {
        float tNext = min(min(tMax.x, tMax.y), tMax.z);
        ivec3 w = cell + afl_CamBlock;
        ivec3 g = w - afl_OccMin;
        if (any(lessThan(g, ivec3(0))) || any(greaterThanEqual(g, ivec3(128)))) return 1.0;
        if (k > 0) {
            float raw = texelFetch(afl_OccTex, w & 127, 0).r * 255.0;
            if (raw > 254.5) return 0.0;
            if (raw > 0.5) {
                float h = (raw - 1.0) / 253.0;
                float y0 = start.y + dir.y * t, y1 = start.y + dir.y * min(tNext, maxT);
                if (min(y0, y1) - float(cell.y) < h) return 0.0;
            }
        }
        if (tNext >= maxT) return 1.0;
        t = tNext;
        if (tMax.x <= tMax.y && tMax.x <= tMax.z) {
            cell.x += stp.x;
            tMax.x += inv.x;
        } else if (tMax.y <= tMax.z) {
            cell.y += stp.y;
            tMax.y += inv.y;
        } else {
            cell.z += stp.z;
            tMax.z += inv.z;
        }
    }
    return 1.0;
}

// The lights' sum at a point with face normal n (levels per colour channel).
vec3 afl_dynamic(vec3 p, vec3 n) {
    vec3 sum = vec3(0.0);
    int group = -1;
    float vis = 1.0;
    for (int i = 0; i < AFL_MAX_LIGHTS; i++) {
        if (i >= afl_LightCount) break;
        vec3 v = p - afl_Lights[i * 6].xyz;
        float level = afl_pattern(i, v);
        if (level <= 0.01) continue;
        // faces turned away from the lamp take nothing; a soft step, not a hard one, on curved and smoothed surfaces
        float facing = smoothstep(-0.02, 0.08, dot(n, -normalize(v)));
        if (facing <= 0.0) continue;
        int g = int(afl_Lights[i * 6 + 5].x + 0.5);
        if (g != group) {
            group = g;
            vis = afl_visible(p, n, afl_Occluders[g].xyz);
        }
        sum += afl_Lights[i * 6 + 4].rgb * (level * facing * vis);
    }
    return sum;
}

// The light map colour lm with the lights added: their levels through vanilla's block light curve (LightTexture: level
// share x -> x / (4 - 3x), times 1.5, the brightness option's gamma lift), screen-blended over the stored light.
vec3 afl_lit(vec3 lm, vec3 p, vec3 n) {
    if (afl_LightCount <= 0) return lm;
    vec3 x = clamp(afl_dynamic(p, n) / 15.0, 0.0, 1.0);
    vec3 v = clamp(x / (4.0 - 3.0 * x) * 1.5, 0.0, 1.0);
    vec3 q = 1.0 - v;
    v = mix(v, 1.0 - q * q * q * q, afl_Gamma) * 0.96;
    return 1.0 - (1.0 - lm) * (1.0 - v);
}

// The light map colour lm with the lights added at a point in the air (rain drops, AFL_VERTEX_STAGE programs too): no
// face, so no facing term; the shadow ray starts at the point itself.
vec3 afl_litVolume(vec3 lm, vec3 p) {
    if (afl_LightCount <= 0) return lm;
    vec3 sum = vec3(0.0);
    int group = -1;
    float vis = 1.0;
    for (int i = 0; i < AFL_MAX_LIGHTS; i++) {
        if (i >= afl_LightCount) break;
        float level = afl_pattern(i, p - afl_Lights[i * 6].xyz);
        if (level <= 0.01) continue;
        int g = int(afl_Lights[i * 6 + 5].x + 0.5);
        if (g != group) {
            group = g;
            vis = afl_visible(p, vec3(0.0), afl_Occluders[g].xyz);
        }
        sum += afl_Lights[i * 6 + 4].rgb * (level * vis);
    }
    vec3 x = clamp(sum / 15.0, 0.0, 1.0);
    vec3 v = clamp(x / (4.0 - 3.0 * x) * 1.5, 0.0, 1.0);
    vec3 q = 1.0 - v;
    v = mix(v, 1.0 - q * q * q * q, afl_Gamma) * 0.96;
    return 1.0 - (1.0 - lm) * (1.0 - v);
}
