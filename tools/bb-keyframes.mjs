// Offline evaluation of hand-keyed Blockbench animation channels, matching Blockbench 5.2 exactly
// (BoneAnimator#interpolate, Keyframe#getLerp / #getBezierLerp), so a clip keyed by hand in Blockbench can be baked to
// 60 Hz runtime keys and mixed with solved tracks (docs/dev/p9_reload_inspect_v1.md 2.11).
// Supported: linear, step, bezier (THREE.CubicBezierCurve.getPoints(200), nearest-two lookup, as Blockbench does).
// Catmull-Rom, Molang expressions and quaternion (per-animator) interpolation are refused.
import assert from 'node:assert/strict';

const AXES = ['x', 'y', 'z'];
const EPS = 1 / 1200;
const num = (kf, i, axis) => {
    const dp = kf.data_points[i] ?? kf.data_points[0], v = dp[axis];
    const n = typeof v === 'number' ? v : v === '' || v === undefined ? 0 : Number(v);
    assert(Number.isFinite(n), `Keyframe ${kf.channel} @${kf.time}: ${axis} = ${JSON.stringify(v)} is not a number`);
    return n;
};
const arr = (a, d) => Array.isArray(a) ? a.map(Number) : [d, d, d];
function bezier(e, t, axis, a) {
    const o = AXES.indexOf(axis), r = num(e, 1, axis), s = num(t, 0, axis), l = t.time - e.time;
    const c = Math.max(0, Math.min(l, arr(e.bezier_right_time, 0.1)[o] || 0));
    const d = Math.max(-l, Math.min(0, arr(t.bezier_left_time, -0.1)[o] || 0));
    // Blockbench writes `r + value || 0`: an undefined handle value collapses the control point to 0
    const p = [[e.time, r], [e.time + c, (r + arr(e.bezier_right_value, 0)[o]) || 0], [t.time + d, (s + arr(t.bezier_left_value, 0)[o]) || 0], [t.time, s]];
    const m = e.time + (t.time - e.time) * a, pts = [];
    for (let i = 0; i <= 200; i++) {
        const u = i / 200, k = 1 - u, w = [k * k * k, 3 * k * k * u, 3 * k * u * u, u * u * u];
        pts.push([w.reduce((acc, wi, j) => acc + wi * p[j][0], 0), w.reduce((acc, wi, j) => acc + wi * p[j][1], 0)]);
    }
    let A = null, g = Infinity;
    for (const v of pts) { const b = Math.abs(v[0] - m); if (b < g) { g = b; A = v; } }
    let B = null; g = Infinity;
    for (const v of pts) { if (v === A) continue; const b = Math.abs(v[0] - m); if (b < g) { g = b; B = v; } }
    const q = B[0] === A[0] ? 0 : Math.max(0, Math.min(1, (m - A[0]) / (B[0] - A[0])));
    return A[1] + (B[1] - A[1]) * q;
}
// keyframes: one Blockbench animator channel (the saved .bbmodel keyframe objects). Returns t => [x, y, z].
export function bbChannel(keyframes) {
    const ks = keyframes.slice().sort((p, q) => p.time - q.time);
    assert(ks.length, 'Empty channel');
    for (const k of ks) assert(['linear', 'step', 'bezier'].includes(k.interpolation), `Unsupported interpolation ${k.interpolation} @${k.time}`);
    return a => {
        let o = null, r = null;
        for (const m of ks) { if (m.time < a) { if (!o || m.time > o.time) o = m; } else if (!r || m.time < r.time) r = m; }
        let c = null;
        if (o && Math.abs(o.time - a) <= EPS) c = o;
        else if (r && Math.abs(r.time - a) <= EPS) c = r;
        else if (o && o.interpolation === 'step') c = o;
        else if (o && !r) c = o;
        else if (r && !o) c = r;
        if (c) { const i = c.time > a || Math.abs(c.time - a) <= EPS ? 0 : c.data_points.length - 1; return AXES.map(x => num(c, i, x)); }
        const h = (a - o.time) / (r.time - o.time);
        if (o.interpolation === 'linear' && (r.interpolation === 'linear' || r.interpolation === 'step'))
            return AXES.map(x => num(o, 1, x) + (num(r, 0, x) - num(o, 1, x)) * h);
        return AXES.map(x => bezier(o, r, x, h));
    };
}
// Cubic Hermite key list -> Blockbench bezier keyframes. keys: [time, [x, y, z], tangent] where tangent is 'flat'
// (zero slope: an extreme or a hold), 'auto' (monotone slope from the neighbours: a pass-through) or [dx, dy, dz]
// (units per second). Handles sit a third of the way into each neighbouring segment, so x(u) stays linear and the
// curve is the Hermite spline through the keys.
export function hermiteKeys(channel, keys, uuid) {
    const slope = i => {
        const [t, v, tan] = keys[i];
        if (Array.isArray(tan)) return tan;
        if (tan === 'flat' || i === 0 || i === keys.length - 1) return [0, 0, 0];
        const [t0, v0] = keys[i - 1], [t1, v1] = keys[i + 1];
        return v.map((x, j) => {
            const d0 = (x - v0[j]) / (t - t0), d1 = (v1[j] - x) / (t1 - t);
            if (d0 * d1 <= 0) return 0;                                  // local extreme: no overshoot between keys
            const w1 = 2 * (t1 - t) + (t - t0), w2 = (t1 - t) + 2 * (t - t0);
            return (w1 + w2) / (w1 / d0 + w2 / d1);
        });
    };
    return keys.map(([t, v], i) => {
        const m = slope(i), lt = i ? (keys[i - 1][0] - t) / 3 : -0.1, rt = i < keys.length - 1 ? (keys[i + 1][0] - t) / 3 : 0.1;
        const r4 = x => +x.toFixed(4) || 0;
        return {channel, data_points: [{x: String(r4(v[0])), y: String(r4(v[1])), z: String(r4(v[2]))}], uuid: uuid(`${channel}:${t}`),
            time: t, color: -1, interpolation: 'bezier', bezier_linked: true,
            bezier_left_time: [lt, lt, lt].map(r4), bezier_left_value: m.map(x => r4(x * lt)),
            bezier_right_time: [rt, rt, rt].map(r4), bezier_right_value: m.map(x => r4(x * rt))};
    });
}
