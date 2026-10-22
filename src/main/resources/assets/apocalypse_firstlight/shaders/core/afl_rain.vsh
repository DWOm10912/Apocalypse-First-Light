#version 150

// Rain V1 (client/rain/RainRenderer, docs/rendering/rain_v1.md): falling drops as thin streaks, without a shader pack.
// Each quad is one drop, fixed in the world: its seed (Position, 0-1 on each axis) places it in a box that tiles space
// (RainBox), the drops fall and drift with the time and the box is wrapped around the camera (RainOffset = camera mod box),
// so the drops stay where they are as the camera moves. A drop draws as a streak along its own motion (fall and wind,
// the same for every viewer: the camera's own motion is left out) over a short exposure, never thinner than a pixel
// (a thinner one is drawn a pixel wide and fainter). Drops under cover (the column map: the rain's top block per column,
// its light, whether it rains there) are not drawn; a streak stops on the ground and on the nearest trucks' rain shells
// (client/rain/RainTrucks: boxes, each cut by a plane where the body slants, the windshield and the hood; roof, glass,
// hood, bed floor; none inside the cab). Lit by the light map at its column and by the dynamic lights (headlamps light the rain in front of them).

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform int FogShape;

uniform vec3 RainBox;
uniform vec3 RainOffset;
uniform vec3 RainWind;
uniform float RainTime;
uniform vec3 RainDrift;
uniform float RainCount;
uniform float RainAlpha;
uniform float PixelScale;
uniform float Exposure;
uniform float CamY;
uniform vec2 ColWrap;

#define TRUCKS 2
#define TRUCK_BOXES 16
uniform int TruckCount;
uniform int TruckBoxCount[TRUCKS];
uniform vec3 TruckO[TRUCKS];
uniform vec3 TruckF[TRUCKS];
uniform vec3 TruckR[TRUCKS];
uniform vec3 TruckU[TRUCKS];
uniform vec3 TruckLo[TRUCKS * TRUCK_BOXES];
uniform vec3 TruckHi[TRUCKS * TRUCK_BOXES];
// each box kept to dot(xyz, local point) <= w (all 0: no cut)
uniform vec4 TruckCut[TRUCKS * TRUCK_BOXES];

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

#define AFL_VERTEX_STAGE
// @afl:dynamic_light

float afl_hash(vec3 p) {
    return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
}

// The streak from its tail (where the drop was, t = 0) to its head (t = 1) against the nearest trucks' boxes: 1 when it
// meets none, below 0 when its tail is already inside one (hidden), else where it first enters one (it stops there, on
// the roof, the hood, the bed floor).
float afl_truckClip(vec3 tail, vec3 head) {
    float clip = 1.0;
    for (int t = 0; t < TRUCKS; t++) {
        if (t >= TruckCount) break;
        mat3 toLocal = transpose(mat3(TruckF[t], TruckR[t], TruckU[t]));
        vec3 a = toLocal * (tail - TruckO[t]);
        vec3 d = toLocal * (head - tail);
        d = mix(d, vec3(1e-6), lessThan(abs(d), vec3(1e-6)));
        vec3 inv = 1.0 / d;
        for (int b = 0; b < TRUCK_BOXES; b++) {
            if (b >= TruckBoxCount[t]) break;
            vec3 lo = TruckLo[t * TRUCK_BOXES + b] - vec3(0.0, 0.0, 0.05), hi = TruckHi[t * TRUCK_BOXES + b];
            vec3 t0 = (lo - a) * inv, t1 = (hi - a) * inv;
            vec3 near = min(t0, t1), far = max(t0, t1);
            float enter = max(max(near.x, near.y), near.z), leave = min(min(far.x, far.y), far.z);
            // the slanted face across it (RainTrucks#hit)
            vec4 cut = TruckCut[t * TRUCK_BOXES + b];
            float s = dot(cut.xyz, a) - cut.w, ds = dot(cut.xyz, d);
            if (abs(ds) < 1e-6) { if (s > 0.0) continue; }
            else if (ds < 0.0) enter = max(enter, -s / ds);
            else leave = min(leave, -s / ds);
            if (leave < enter || leave < 0.0 || enter > clip) continue;
            if (enter <= 0.0) return -1.0;
            clip = enter;
        }
    }
    return clip;
}

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    vertexDistance = 0.0;
    vertexColor = vec4(0.0);
    texCoord0 = UV0;
}

void main() {
    float w = afl_hash(Position);
    if (w > RainCount) { hide(); return; }
    float w2 = afl_hash(Position.zxy + 0.37);
    float speed = mix(6.2, 9.0, w2);
    float f = mix(0.9, 1.1, w);
    vec3 vel = vec3(RainWind.x, -speed, RainWind.z) * f;
    // the fall and the drift so far (RainDrift: the wind integrated over time, so a change of wind bends the streaks, never moves the drops)
    vec3 travel = vec3(RainDrift.x, -speed * RainTime, RainDrift.z) * f;
    vec3 head = mod(Position * RainBox + travel - RainOffset, RainBox) - RainBox * 0.5;

    // the column the drop falls in: its cover height, light and weather
    ivec2 col = ivec2(floor(ColWrap + head.xz)) & 127;
    vec4 c = texelFetch(Sampler0, col, 0);
    if (c.a < 0.5) { hide(); return; }
    // the drawn surface (1/64 blocks + 512 * 64: RainColumns)
    float top = (floor(c.r * 255.0 + 0.5) + floor(c.g * 255.0 + 0.5) * 256.0) / 64.0 - 512.0;
    float ground = top - CamY;

    vec3 rel = vel;
    float len = clamp(length(rel) * Exposure, 0.06, 1.6);
    vec3 dir = normalize(rel);
    vec3 tail = head - dir * len;
    if (tail.y < ground) { hide(); return; }
    if (head.y < ground && dir.y < -0.01) head = tail + dir * ((ground - tail.y) / dir.y);
    if (TruckCount > 0) {
        float clip = afl_truckClip(tail, head);
        if (clip < 0.0) { hide(); return; }
        head = mix(tail, head, clip);
    }

    // soft edges of the box (no popping where it wraps) and with distance
    vec3 e = abs(head) / (RainBox * 0.5);
    float edge = 1.0 - smoothstep(0.75, 1.0, max(e.x, max(e.y, e.z)));
    if (edge <= 0.0) { hide(); return; }

    vec3 mid = (head + tail) * 0.5;
    float depth = max(length(mid), 0.05);
    // a drop right at the eye would cross the screen as a broad bar: faded out close to the camera
    edge *= smoothstep(0.4, 1.4, depth);
    if (edge <= 0.0) { hide(); return; }
    float worldWidth = mix(0.0016, 0.0032, w2);
    float drawWidth = max(worldWidth, depth * PixelScale);
    vec3 across = cross(dir, mid);
    across = length(across) > 1e-5 ? normalize(across) : vec3(1.0, 0.0, 0.0);

    vec3 pos = (UV0.y < 0.5 ? head : tail) + across * (UV0.x - 0.5) * drawWidth;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexDistance = FogShape == 0 ? length(pos) : max(length(pos.xz), abs(pos.y));

    float raw = floor(c.b * 255.0 + 0.5);
    float block = floor(raw / 16.0), sky = raw - block * 16.0;
    vec3 lm = texture(Sampler2, clamp(vec2(block, sky) * 16.0 / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0))).rgb;
#ifdef AFL_DYNAMIC_LIGHT
    lm = afl_litVolume(lm, mid);
#endif
    // drops show by the light they catch: brighter where it is bright (added, never darkening the scene)
    vertexColor = vec4(lm * vec3(0.72, 0.76, 0.82), RainAlpha * edge * (worldWidth / drawWidth) * mix(0.7, 1.0, w2));
    texCoord0 = UV0;
}
