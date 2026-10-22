/*
 * AFL Rig: a Blockbench plugin for the AFL player skeleton (docs/dev/afl_rig_blockbench_plugin_v1.md).
 *
 * A free-format project is posed by the game's own skeleton math:
 * - the 16-bone rig of rigs/afl_humanoid_v1.json;
 * - the locomotion idle under the action, and the action clip on its mask;
 * - the held prop placed by the runtime's prop keys;
 * - both arms solved by the same two-bone IK to the hand targets.
 *
 * The core below is a line-by-line port of skeleton/HumanoidPoser, SkeletonPose, SkeletonClips and
 * skeleton/action/ActionPose. Java model px throughout: y down, front -z, the right arm at -x.
 * tools/blockbench/afl_rig_check.mjs runs the port against the Java code (./gradlew aflRigParity).
 *
 * Blockbench side:
 * - the display hook (display_animation_frame) writes every rig group's mesh from the solved pose;
 * - the panel edits the action's settings and hand timelines;
 * - export writes three files:
 *   - player_actions/<group>/<name>.json;
 *   - the clip into animations/player/<group>.animation.json (only files this plugin wrote);
 *   - player_props/<id>.json.
 *
 * Load it from Blockbench: File > Plugins > Load Plugin from File, then pick this file.
 * Node: require() returns the core.
 */
(function () {
    'use strict';

    const VERSION = '0.1.0';
    const DEFAULT_REPO = 'D:/Minecraft Modding/AFL-survivor-v0';
    const ASSETS = 'src/main/resources/assets/apocalypse_firstlight/';
    const TEST_ASSETS = 'src/test/resources/assets/apocalypse_firstlight/';

    // ================================================================ math (JOML semantics)

    const DEG = Math.PI / 180;
    const rad = d => d * DEG;
    const deg = r => r / DEG;
    const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
    const clamp01 = v => clamp(v, 0, 1);
    /** GunStance.smooth: smoothstep of t clamped to 0..1. */
    const smooth = t => { t = clamp01(t); return t * t * (3 - 2 * t); };

    const v3 = {
        add: (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]],
        sub: (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]],
        mul: (a, s) => [a[0] * s, a[1] * s, a[2] * s],
        dot: (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2],
        cross: (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]],
        lenSq: a => a[0] * a[0] + a[1] * a[1] + a[2] * a[2],
        len: a => Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]),
        norm: a => {
            const l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
            return l > 0 ? [a[0] / l, a[1] / l, a[2] / l] : [0, 0, 0];
        },
        lerp: (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t],
        dist: (a, b) => Math.sqrt((a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2),
    };

    /** Quaternions [x, y, z, w]; mul(a, b) = a * b (JOML q.mul(b)), axis rotations as JOML rotationX/Y/Z. */
    const q4 = {
        id: () => [0, 0, 0, 1],
        mul: (a, b) => [
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]],
        conj: q => [-q[0], -q[1], -q[2], q[3]],
        neg: q => [-q[0], -q[1], -q[2], -q[3]],
        dot: (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3],
        norm: q => {
            const l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
            return l > 0 ? [q[0] / l, q[1] / l, q[2] / l, q[3] / l] : [0, 0, 0, 1];
        },
        x: a => [Math.sin(a / 2), 0, 0, Math.cos(a / 2)],
        y: a => [0, Math.sin(a / 2), 0, Math.cos(a / 2)],
        z: a => [0, 0, Math.sin(a / 2), Math.cos(a / 2)],
        /** JOML rotationZYX(z, y, x): Rz * Ry * Rx (the clips' order, Blockbench's ZYX Euler). */
        zyx: (z, y, x) => q4.mul(q4.mul(q4.z(z), q4.y(y)), q4.x(x)),
        /** rotateX(x).rotateY(y).rotateZ(z) from identity: Rx * Ry * Rz (PropKey rot). */
        xyz: (x, y, z) => q4.mul(q4.mul(q4.x(x), q4.y(y)), q4.z(z)),
        /** JOML transform: rotates by the normalized quaternion. */
        apply: (q, v) => {
            const [x, y, z, w] = q4.norm(q);
            const tx = 2 * (y * v[2] - z * v[1]), ty = 2 * (z * v[0] - x * v[2]), tz = 2 * (x * v[1] - y * v[0]);
            return [v[0] + w * tx + y * tz - z * ty, v[1] + w * ty + z * tx - x * tz, v[2] + w * tz + x * ty - y * tx];
        },
        /** JOML Quaternionf.slerp (no normalization; the short way by the sign of the dot). */
        slerp: (a, b, alpha) => {
            const cosom = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
            const absCosom = Math.abs(cosom);
            let s0, s1;
            if (1 - absCosom > 1e-6) {
                const sinSqr = 1 - absCosom * absCosom, sinom = 1 / Math.sqrt(sinSqr);
                const omega = Math.atan2(sinSqr * sinom, absCosom);
                s0 = Math.sin((1 - alpha) * omega) * sinom;
                s1 = Math.sin(alpha * omega) * sinom;
            } else {
                s0 = 1 - alpha;
                s1 = alpha;
            }
            s1 = cosom >= 0 ? s1 : -s1;
            return [s0 * a[0] + s1 * b[0], s0 * a[1] + s1 * b[1], s0 * a[2] + s1 * b[2], s0 * a[3] + s1 * b[3]];
        },
        /** JOML getEulerAnglesZYX (radians, [x, y, z]): the inverse of zyx. */
        eulerZYX: q => {
            const [x, y, z, w] = q;
            return [Math.atan2(y * z + w * x, 0.5 - x * x - y * y), Math.asin(clamp(-2 * (x * z - w * y), -1, 1)),
                Math.atan2(x * y + w * z, 0.5 - y * y - z * z)];
        },
        /** JOML getEulerAnglesXYZ (radians, [x, y, z]): the inverse of xyz. */
        eulerXYZ: q => {
            const [x, y, z, w] = q;
            return [Math.atan2(x * w - y * z, 0.5 - x * x - y * y), Math.asin(clamp(2 * (x * z + y * w), -1, 1)),
                Math.atan2(z * w - x * y, 0.5 - y * y - z * z)];
        },
        /** JOML setFromNormalized of the matrix with columns x, y, z (mij: column i, row j). */
        fromBasis: (cx, cy, cz) => {
            const m00 = cx[0], m01 = cx[1], m02 = cx[2], m10 = cy[0], m11 = cy[1], m12 = cy[2], m20 = cz[0], m21 = cz[1], m22 = cz[2];
            let x, y, z, w, t;
            const tr = m00 + m11 + m22;
            if (tr >= 0) {
                t = Math.sqrt(tr + 1); w = t * 0.5; t = 0.5 / t;
                x = (m12 - m21) * t; y = (m20 - m02) * t; z = (m01 - m10) * t;
            } else if (m00 >= m11 && m00 >= m22) {
                t = Math.sqrt(m00 - (m11 + m22) + 1); x = t * 0.5; t = 0.5 / t;
                y = (m10 + m01) * t; z = (m02 + m20) * t; w = (m12 - m21) * t;
            } else if (m11 > m22) {
                t = Math.sqrt(m11 - (m22 + m00) + 1); y = t * 0.5; t = 0.5 / t;
                z = (m21 + m12) * t; x = (m10 + m01) * t; w = (m20 - m02) * t;
            } else {
                t = Math.sqrt(m22 - (m00 + m11) + 1); z = t * 0.5; t = 0.5 / t;
                x = (m02 + m20) * t; y = (m21 + m12) * t; w = (m01 - m10) * t;
            }
            return [x, y, z, w];
        },
        /** ActionCheck#angle: the turn's angle (radians). */
        angle: q => 2 * Math.acos(Math.min(1, Math.abs(q4.norm(q)[3]))),
    };

    /**
     * Rigid transforms {q, t}: p -> q p + t (every matrix the poser and ActionPose build is one), and {q, t, s} with a
     * uniform scale, p -> s q p + t (a gun prop at its carry mount, drawn larger there: ActionPose carry frame).
     */
    const scaleOf = m => (m.s === undefined ? 1 : m.s);
    const tf = {
        id: () => ({q: [0, 0, 0, 1], t: [0, 0, 0]}),
        of: (q, t) => ({q, t}),
        translation: t => ({q: [0, 0, 0, 1], t}),
        apply: (m, p) => v3.add(v3.mul(q4.apply(m.q, p), scaleOf(m)), m.t),
        mul: (a, b) => {
            const sa = scaleOf(a), out = {q: q4.mul(a.q, b.q), t: v3.add(v3.mul(q4.apply(a.q, b.t), sa), a.t)};
            if (a.s !== undefined || b.s !== undefined) out.s = sa * scaleOf(b);
            return out;
        },
        inv: a => {
            const c = q4.conj(q4.norm(a.q)), s = scaleOf(a);
            const out = {q: c, t: v3.mul(q4.apply(c, a.t), -1 / s)};
            if (a.s !== undefined) out.s = 1 / s;
            return out;
        },
        scale: s => ({q: [0, 0, 0, 1], t: [0, 0, 0], s}),
        scaleOf,
    };

    /**
     * Java model px <-> Blockbench free-format px:
     * - B = (-x, 24 - y, z), a turn half round z plus 24 up, its own inverse;
     * - turns: (qx, qy, qz, qw) <-> (-qx, -qy, qz, qw).
     */
    const J2B = {q: [0, 0, 1, 0], t: [0, 24, 0]};
    const j2b = p => [-p[0], 24 - p[1], p[2]];
    const RZ180 = [0, 0, 1, 0], RX180 = [1, 0, 0, 0];

    // ================================================================ rig (SkeletonRig)

    /** rigs/afl_humanoid_v1.json as of 2026-10-16, when the repo file cannot be read. */
    const RIG_FALLBACK = {
        bones: [
            {name: 'root', parent: null, pivot: [0, 0, 0]},
            {name: 'pelvis', parent: 'root', pivot: [0, 12, 0]},
            {name: 'chest', parent: 'pelvis', pivot: [0, 18, 0]},
            {name: 'head', parent: 'chest', pivot: [0, 24, 0]},
            {name: 'upper_arm_r', parent: 'chest', pivot: [-5, 22, 0]},
            {name: 'forearm_r', parent: 'upper_arm_r', pivot: [-5, 18, 0]},
            {name: 'hand_r', parent: 'forearm_r', pivot: [-5, 14, 0]},
            {name: 'upper_arm_l', parent: 'chest', pivot: [5, 22, 0]},
            {name: 'forearm_l', parent: 'upper_arm_l', pivot: [5, 18, 0]},
            {name: 'hand_l', parent: 'forearm_l', pivot: [5, 14, 0]},
            {name: 'thigh_r', parent: 'pelvis', pivot: [-1.9, 12, 0]},
            {name: 'shin_r', parent: 'thigh_r', pivot: [-1.9, 6, 0]},
            {name: 'foot_r', parent: 'shin_r', pivot: [-1.9, 2, 0]},
            {name: 'thigh_l', parent: 'pelvis', pivot: [1.9, 12, 0]},
            {name: 'shin_l', parent: 'thigh_l', pivot: [1.9, 6, 0]},
            {name: 'foot_l', parent: 'shin_l', pivot: [1.9, 2, 0]},
        ],
        skin: [
            {part: 'body', bones: ['chest', 'pelvis'], cuts: [6], from: [-4, 12, -2], size: [8, 12, 4]},
            {part: 'right_arm', bones: ['upper_arm_r', 'forearm_r', 'hand_r'], cuts: [6, 10], from: [-8, 12, -2], size: [4, 12, 4],
                slim_from: [-7, 12, -2], slim_size: [3, 12, 4]},
            {part: 'left_arm', bones: ['upper_arm_l', 'forearm_l', 'hand_l'], cuts: [6, 10], from: [4, 12, -2], size: [4, 12, 4],
                slim_from: [4, 12, -2], slim_size: [3, 12, 4]},
            {part: 'right_leg', bones: ['thigh_r', 'shin_r', 'foot_r'], cuts: [6, 10], from: [-3.9, 0, -2], size: [4, 12, 4]},
            {part: 'left_leg', bones: ['thigh_l', 'shin_l', 'foot_l'], cuts: [6, 10], from: [-0.1, 0, -2], size: [4, 12, 4]},
        ],
        sockets: {
            eye: {bone: 'head', pos: [0, 27.63, -2.5]},
            armpit_l: {bone: 'chest', pos: [3.0, 19.5, -4.8]},
            armpit_r: {bone: 'chest', pos: [-3.0, 19.5, -4.8]},
            holster: {bone: 'thigh_r', pos: [-5.15, 9.4, 0]},
        },
        limits_deg: {
            forearm_r: {x: [-145, 0], y: [-90, 90]}, forearm_l: {x: [-145, 0], y: [-90, 90]},
            hand_r: {x: [-60, 60], y: [-30, 30], z: [-30, 30]}, hand_l: {x: [-60, 60], y: [-30, 30], z: [-30, 30]},
            chest: {x: [-25, 30], y: [-45, 45], z: [-15, 15]}, head: {x: [-70, 60], y: [-70, 70]},
            shin_r: {x: [0, 150]}, shin_l: {x: [0, 150]}, foot_r: {x: [-30, 45]}, foot_l: {x: [-30, 45]},
        },
    };

    /** Bedrock px (y up, ground 0) to Java model px (y down, neck 0). */
    const java = p => [p[0], 24 - p[1], p[2]];

    function loadRig(json) {
        const names = json.bones.map(b => b.name), index = {};
        names.forEach((n, i) => { index[n] = i; });
        const bone = n => {
            if (!(n in index)) throw new Error('rig: no bone ' + n);
            return index[n];
        };
        const parent = json.bones.map((b, i) => {
            const p = b.parent == null ? -1 : bone(b.parent);
            if (p >= i) throw new Error('rig: parent after child ' + b.name);
            return p;
        });
        const sockets = {};
        for (const k in json.sockets) sockets[k] = {bone: bone(json.sockets[k].bone), pos: java(json.sockets[k].pos)};
        const limits = names.map(() => null);
        const range = (l, a) => l[a] ? l[a] : [NaN, NaN];
        for (const k in (json.limits_deg || {})) {
            const l = json.limits_deg[k], x = range(l, 'x'), y = range(l, 'y'), z = range(l, 'z');
            limits[bone(k)] = {minX: x[0], maxX: x[1], minY: y[0], maxY: y[1], minZ: z[0], maxZ: z[1]};
        }
        return {
            json, names, index, parent, sockets, limits, size: names.length, bone,
            pivot: json.bones.map(b => java(b.pivot)),
            find: n => (n in index ? index[n] : -1),
            root: bone('root'), pelvis: bone('pelvis'), chest: bone('chest'), head: bone('head'),
            upperArmR: bone('upper_arm_r'), forearmR: bone('forearm_r'), handR: bone('hand_r'),
            upperArmL: bone('upper_arm_l'), forearmL: bone('forearm_l'), handL: bone('hand_l'),
            thighR: bone('thigh_r'), shinR: bone('shin_r'), footR: bone('foot_r'),
            thighL: bone('thigh_l'), shinL: bone('shin_l'), footL: bone('foot_l'),
        };
    }

    // ================================================================ pose (SkeletonPose)

    /** world[b] = W_parent * T(offset_b) * T(P_b) * R_b * T(-P_b). */
    class Pose {
        constructor(rig) {
            this.rig = rig;
            this.reset();
        }

        reset() {
            this.local = [];
            this.offset = [];
            this.world = [];
            for (let i = 0; i < this.rig.size; i++) {
                this.local.push([0, 0, 0, 1]);
                this.offset.push([0, 0, 0]);
                this.world.push(tf.id());
            }
            this.shoulderDrop = 0;
            return this;
        }

        updateAll() {
            for (let b = 0; b < this.rig.size; b++) this.update(b);
        }

        update(b) {
            const p = this.rig.pivot[b], q = this.local[b];
            const parent = this.rig.parent[b] < 0 ? tf.id() : this.world[this.rig.parent[b]];
            this.world[b] = tf.mul(parent, {q, t: v3.sub(v3.add(this.offset[b], p), q4.apply(q, p))});
        }

        updateFrom(b) {
            this.update(b);
            for (let c = b + 1; c < this.rig.size; c++) if (this.descends(c, b)) this.update(c);
        }

        descends(child, ancestor) {
            for (let p = this.rig.parent[child]; p >= 0; p = this.rig.parent[p]) if (p === ancestor) return true;
            return false;
        }

        point(b, rest) { return tf.apply(this.world[b], rest); }

        rotation(b) { return q4.norm(this.world[b].q); }
    }

    // ================================================================ clips (SkeletonClips)

    function catmullRom(t, p0, p1, p2, p3) {
        const t2 = t * t, t3 = t2 * t;
        return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }

    /** One channel: keys in seconds, [pre, post] per key, mode 0 linear, 1 catmullrom, 2 step. */
    class Channel {
        constructor(times, pre, post, mode) {
            this.times = times;
            this.pre = pre;
            this.post = post;
            this.mode = mode;
        }

        sample(t) {
            const times = this.times, n = times.length;
            if (n === 1 || t <= times[0]) return (n === 1 ? this.post[0] : this.pre[0]).slice();
            if (t >= times[n - 1]) return this.post[n - 1].slice();
            let b = 0;
            while (times[b] < t) b++;
            if (times[b] === t) return this.post[b].slice();
            const a = b - 1, f = (t - times[a]) / (times[b] - times[a]);
            if (this.mode[a] === 2) return this.post[a].slice();
            if (this.mode[a] === 1 || this.mode[b] === 1) {
                const p0 = a > 0 ? this.post[a - 1] : this.post[a], p3 = b < n - 1 ? this.pre[b + 1] : this.pre[b];
                return [0, 1, 2].map(k => catmullRom(f, p0[k], this.post[a][k], this.pre[b][k], p3[k]));
            }
            return [0, 1, 2].map(k => this.post[a][k] + (this.pre[b][k] - this.post[a][k]) * f);
        }
    }

    /** A clip; channel values are the Bedrock file's numbers (rotation deg Z-Y-X, position y up). */
    class Clip {
        constructor(name, length, loop) {
            this.name = name;
            this.length = length;
            this.loop = loop;
            this.rot = {};
            this.pos = {};
        }

        rotates(b) { return !!this.rot[b]; }

        moves(b) { return !!this.pos[b]; }

        time(t) {
            if (this.length <= 0) return 0;
            return this.loop ? ((t % this.length) + this.length) % this.length : Math.min(Math.max(t, 0), this.length);
        }

        rotation(b, t) {
            const c = this.rot[b];
            if (!c) return [0, 0, 0, 1];
            const v = c.sample(this.time(t));
            return q4.zyx(rad(v[2]), rad(v[1]), rad(v[0]));
        }

        position(b, t) {
            const c = this.pos[b];
            if (!c) return [0, 0, 0];
            const v = c.sample(this.time(t));
            return [v[0], -v[1], v[2]];
        }
    }

    /** Gson getAsFloat: numbers and numeric strings; molang reads as 0. */
    const numberOf = e => {
        const f = typeof e === 'number' ? e : typeof e === 'string' ? Number(e) : NaN;
        return Number.isFinite(f) ? f : 0;
    };

    function vectorOf(e, arriving) {
        if (Array.isArray(e)) {
            const v = [0, 0, 0];
            for (let i = 0; i < 3 && i < e.length; i++) v[i] = numberOf(e[i]);
            return v;
        }
        if (typeof e !== 'object' || e === null) {
            const f = numberOf(e);
            return [f, f, f];
        }
        if ('vector' in e) return vectorOf(e.vector, arriving);
        const pick = arriving ? ('pre' in e ? e.pre : e.post) : ('post' in e ? e.post : e.pre);
        return pick === undefined ? [0, 0, 0] : vectorOf(pick, arriving);
    }

    function channelFromJson(e) {
        if (Array.isArray(e) || typeof e !== 'object' || e === null || 'vector' in e) {
            const v = vectorOf(e, true);
            return new Channel([0], [v], [v], [0]);
        }
        const entries = Object.keys(e).map(k => [parseFloat(k), e[k]]).sort((x, y) => x[0] - y[0]);
        const mode = entries.map(([, v]) => {
            const m = v && typeof v === 'object' && !Array.isArray(v) ? v.lerp_mode : undefined;
            return m === 'catmullrom' ? 1 : m === 'step' ? 2 : 0;
        });
        return new Channel(entries.map(x => x[0]), entries.map(x => vectorOf(x[1], true)), entries.map(x => vectorOf(x[1], false)), mode);
    }

    /** A Bedrock animation file's clips for the rig's bones (helper bones such as prop_* are skipped). */
    function loadClips(rig, json) {
        const out = {}, anims = (json && json.animations) || {};
        for (const key in anims) {
            const a = anims[key];
            const clip = new Clip(key, typeof a.animation_length === 'number' ? a.animation_length : 0, a.loop === true);
            for (const name in (a.bones || {})) {
                const b = rig.find(name);
                if (b < 0) continue;
                const ch = a.bones[name];
                if (ch.rotation !== undefined) clip.rot[b] = channelFromJson(ch.rotation);
                if (ch.position !== undefined) clip.pos[b] = channelFromJson(ch.position);
            }
            out[key] = clip;
        }
        return out;
    }

    // ================================================================ poser (HumanoidPoser: what actions use)

    const ELBOW_MAX = rad(145), TWIST_MAX = rad(90), WRIST_MAX = rad(60);
    const HIP_SHARE = 1.0, LEG_SHARE = 0.5;
    const LOCO = ['locomotion.idle', 'locomotion.walk', 'locomotion.run', 'locomotion.crouch_idle', 'locomotion.crouch_walk'];

    function newInput() {
        return {
            slim: false, walkPhase: 0, move: 0, run: 0, crouch: 0, time: 0,
            rootYaw: 0, chestYaw: 0, chestPitch: 0, viewYaw: 0, viewPitch: 0,
            eyeTarget: null, maxRootShift: 4.27, headClip: 0,
            right: 'CLIP', left: 'CLIP', ikRightWeight: 1, ikLeftWeight: 1,
            vanillaRight: [0, 0, 0, 1], vanillaLeft: [0, 0, 0, 1], vanillaRightWeight: 0, vanillaLeftWeight: 0,
            targetRight: tf.id(), targetLeft: tf.id(),
            poleRight: [-0.35, 1, 0.45], poleLeft: [0.35, 1, 0.45], protraction: 1.5,
            slideRight: [0, 0, 0], slideLeft: [0, 0, 0], shoulderRight: [0, 0, 0], shoulderLeft: [0, 0, 0],
            actionClip: null, paramClip: null, actionTime: 0, actionWeight: 0, paramWeight: 0,
            actionMask: null, actionMirror: false, rootOffset: [0, 0, 0],
        };
    }

    function limitAngle(q, max) {
        if (q[3] < 0) q = q4.neg(q);
        const angle = 2 * Math.acos(Math.min(1, q[3]));
        return angle > max ? q4.slerp(q4.id(), q, max / angle) : q;
    }

    function clampDeg(radians, min, max) {
        return Number.isNaN(min) ? radians : Math.max(rad(min), Math.min(rad(max), radians));
    }

    /**
     * JOML 1.10.5 (Minecraft 1.20.1) getEulerAnglesZYX as shipped. Its x has "0.5 - x*x + y*y" where the true inverse
     * of rotationZYX has "- y*y". The game's clampEuler goes through it, so a head with both yaw and pitch comes back
     * pitched a little differently even inside its limits (0.11 deg in tool/crowbar_smash). The preview copies that.
     */
    const jomlEulerZYX = q => {
        const [x, y, z, w] = q;
        return [Math.atan2(y * z + w * x, 0.5 - x * x + y * y), Math.asin(clamp(-2 * (x * z - w * y), -1, 1)),
            Math.atan2(x * y + w * z, 0.5 - y * y - z * z)];
    };

    /** Clamps a local rotation's Z-Y-X angles to the rig's limit (the free axes stay), as the game does (jomlEulerZYX). */
    function clampEuler(q, limit) {
        if (!limit) return q;
        const e = jomlEulerZYX(q);
        return q4.zyx(clampDeg(e[2], limit.minZ, limit.maxZ), clampDeg(e[1], limit.minY, limit.maxY), clampDeg(e[0], limit.minX, limit.maxX));
    }

    /** axis 0 = x (pitch), 1 = y (twist). */
    function clampLimit(degrees, limit, axis) {
        if (!limit) return degrees;
        const min = axis === 0 ? limit.minX : limit.minY, max = axis === 0 ? limit.maxX : limit.maxY;
        return Number.isNaN(min) ? degrees : Math.max(min, Math.min(max, degrees));
    }

    class Poser {
        constructor(rig, clips) {
            this.rig = rig;
            this.clips = clips || {};
            this.upperLength = v3.dist(rig.pivot[rig.forearmR], rig.pivot[rig.upperArmR]);
            this.foreLength = v3.dist(rig.pivot[rig.handR], rig.pivot[rig.forearmR]);
            this.mirror = rig.names.map((n, b) => {
                const m = n.endsWith('_r') ? n.slice(0, -2) + '_l' : n.endsWith('_l') ? n.slice(0, -2) + '_r' : n;
                return rig.find(m) >= 0 ? rig.find(m) : b;
            });
        }

        /** The chest's share of the view pitch (deg, + = down). */
        static chestPitch(viewPitch, rifle) {
            return rifle ? clamp(viewPitch * 0.5, -25, 30) : clamp(viewPitch * 0.3, -15, 20);
        }

        solveBody(inp, pose) {
            const rig = this.rig;
            const report = {rightReach: 0, leftReach: 0, rightSlide: 0, leftSlide: 0, rightElbowDeg: 0, leftElbowDeg: 0,
                rootShift: [0, 0, 0], clipArms: []};
            pose.reset();
            this.locomotion(inp, pose);
            this.action(inp, pose);
            pose.local[rig.root] = q4.y(rad(inp.rootYaw));
            pose.offset[rig.root] = v3.add(pose.offset[rig.root], inp.rootOffset);
            const chestClip = pose.local[rig.chest].slice();
            const twist = clampLimit(inp.chestYaw, rig.limits[rig.chest], 1), bend = clampLimit(inp.chestPitch, rig.limits[rig.chest], 0);
            const hip = twist * HIP_SHARE, legShare = LEG_SHARE * (1 - clamp01(inp.move));
            pose.local[rig.pelvis] = q4.mul(pose.local[rig.pelvis], q4.y(rad(hip)));
            const legBack = q4.y(rad(-hip * (1 - legShare)));
            pose.local[rig.thighR] = q4.mul(legBack, pose.local[rig.thighR]);
            pose.local[rig.thighL] = q4.mul(legBack, pose.local[rig.thighL]);
            pose.local[rig.chest] = q4.mul(q4.mul(q4.y(rad(twist - hip)), q4.x(rad(bend))), chestClip);
            pose.updateAll();
            // head: the view in model space, relative to the chest as posed; headClip of the way back to the clips' head
            // (lying out cold: the face on the ground, not where the view looks)
            const headClip = pose.local[rig.head].slice();
            const view = q4.mul(q4.y(rad(inp.viewYaw)), q4.x(rad(inp.viewPitch)));
            const chestWorld = pose.rotation(rig.chest);
            pose.local[rig.head] = clampEuler(q4.mul(q4.conj(chestWorld), view), rig.limits[rig.head]);
            if (inp.headClip > 0) {
                const viewed = pose.local[rig.head];
                pose.local[rig.head] = q4.slerp(viewed, q4.dot(viewed, headClip) < 0 ? q4.neg(headClip) : headClip, clamp01(inp.headClip));
            }
            pose.update(rig.head);
            if (inp.eyeTarget) {
                const eye = rig.sockets.eye;
                const at = pose.point(eye.bone, eye.pos);
                let dx = inp.eyeTarget[0] - at[0], dz = inp.eyeTarget[2] - at[2];
                const length = Math.sqrt(dx * dx + dz * dz);
                if (length > inp.maxRootShift) {
                    dx *= inp.maxRootShift / length;
                    dz *= inp.maxRootShift / length;
                }
                pose.offset[rig.root] = v3.add(pose.offset[rig.root], [dx, 0, dz]);
                report.rootShift = [dx, 0, dz];
                pose.updateAll();
            }
            const arms = [rig.upperArmR, rig.forearmR, rig.handR, rig.upperArmL, rig.forearmL, rig.handL];
            report.clipArms = arms.map(b => pose.local[b].slice());
            return report;
        }

        locomotion(inp, pose) {
            const move = clamp01(inp.move), run = clamp01(inp.run), crouch = clamp01(inp.crouch), stand = 1 - crouch;
            const weights = [stand * (1 - move), stand * move * (1 - run), stand * move * run, crouch * (1 - move), crouch * move];
            const clips = LOCO.map(n => this.clips[n] || null);
            const times = clips.map((c, i) => !c ? 0 : (i === 1 || i === 2 || i === 4) ? inp.walkPhase * c.length : inp.time);
            for (let b = 0; b < this.rig.size; b++) {
                let sum = [0, 0, 0, 0], offset = [0, 0, 0];
                for (let i = 0; i < 5; i++) {
                    if (weights[i] <= 0 || !clips[i]) continue;
                    let q = clips[i].rotation(b, times[i]);
                    if (q4.dot(sum, q) < 0) q = q4.neg(q);
                    sum = sum.map((s, k) => s + q[k] * weights[i]);
                    offset = v3.add(offset, v3.mul(clips[i].position(b, times[i]), weights[i]));
                }
                pose.local[b] = q4.dot(sum, sum) < 1e-12 ? [0, 0, 0, 1] : q4.norm(sum);
                pose.offset[b] = offset;
            }
        }

        action(inp, pose) {
            if (!inp.actionMask) return;
            this.apply(inp, pose, inp.actionClip, inp.actionTime, inp.actionWeight);
            this.apply(inp, pose, inp.paramClip, 0, inp.paramWeight);
        }

        apply(inp, pose, clip, time, weight) {
            if (!clip || weight <= 0) return;
            const w = Math.min(1, weight);
            for (let b = 0; b < this.rig.size; b++) {
                if (b >= inp.actionMask.length || !inp.actionMask[b]) continue;
                const from = inp.actionMirror ? this.mirror[b] : b;
                if (clip.rotates(from)) {
                    let q = clip.rotation(from, time);
                    if (inp.actionMirror) q = [q[0], -q[1], -q[2], q[3]];
                    pose.local[b] = q4.norm(q4.slerp(pose.local[b], q, w));
                }
                if (clip.moves(from)) {
                    const p = clip.position(from, time);
                    if (inp.actionMirror) p[0] = -p[0];
                    pose.offset[b] = v3.lerp(pose.offset[b], p, w);
                }
            }
        }

        solveArms(inp, pose, report) {
            const rig = this.rig, arms = [rig.upperArmR, rig.forearmR, rig.handR, rig.upperArmL, rig.forearmL, rig.handL];
            for (let i = 0; i < 6; i++) pose.local[arms[i]] = report.clipArms[i].slice();
            pose.offset[rig.upperArmR] = [0, pose.shoulderDrop, 0];
            pose.offset[rig.upperArmL] = [0, pose.shoulderDrop, 0];
            pose.updateFrom(rig.upperArmR);
            pose.updateFrom(rig.upperArmL);
            const chestWorld = pose.rotation(rig.chest);
            this.arm(inp, pose, report, true, chestWorld);
            this.arm(inp, pose, report, false, chestWorld);
        }

        arm(inp, pose, report, right, chestWorld) {
            const rig = this.rig;
            const upper = right ? rig.upperArmR : rig.upperArmL, fore = right ? rig.forearmR : rig.forearmL, hand = right ? rig.handR : rig.handL;
            const mode = right ? inp.right : inp.left;
            if (mode === 'IK') {
                const w = clamp01(right ? inp.ikRightWeight : inp.ikLeftWeight);
                if (w > 0) {
                    const u = pose.local[upper].slice(), f = pose.local[fore].slice(), h = pose.local[hand].slice();
                    this.ik(inp, pose, report, right, upper, fore, hand, chestWorld);
                    if (w < 1) {
                        pose.local[upper] = q4.slerp(u, pose.local[upper], w);
                        pose.local[fore] = q4.slerp(f, pose.local[fore], w);
                        pose.local[hand] = q4.slerp(h, pose.local[hand], w);
                        pose.offset[upper] = v3.lerp([0, pose.shoulderDrop, 0], pose.offset[upper], w);
                    }
                }
            } else {
                const w = mode === 'VANILLA' ? 1 : clamp01(right ? inp.vanillaRightWeight : inp.vanillaLeftWeight);
                if (w > 0) {
                    const vanilla = q4.mul(q4.conj(chestWorld), right ? inp.vanillaRight : inp.vanillaLeft);
                    pose.local[upper] = q4.slerp(pose.local[upper], vanilla, w);
                    pose.local[fore] = q4.slerp(pose.local[fore], q4.id(), w);
                    pose.local[hand] = q4.slerp(pose.local[hand], q4.id(), w);
                }
            }
            pose.updateFrom(upper);
        }

        shoulderSet(inp, pose, right) {
            return v3.add(right ? inp.shoulderRight : inp.shoulderLeft, [0, pose.shoulderDrop, 0]);
        }

        /** Shoulder angle and the upper arm's world turn for a wrist at distance d, the elbow toward the pole. */
        upperTurn(inp, chestWorld, right, dir, d) {
            const U = this.upperLength, F = this.foreLength;
            const cosElbow = (U * U + F * F - d * d) / (2 * U * F);
            const flex = Math.min(ELBOW_MAX, Math.PI - Math.acos(clamp(cosElbow, -1, 1)));
            const reachD = Math.sqrt(U * U + F * F + 2 * U * F * Math.cos(flex));
            const cosShoulder = (U * U + reachD * reachD - F * F) / (2 * U * reachD);
            const shoulderAngle = Math.acos(clamp(cosShoulder, -1, 1));
            const pole = q4.apply(chestWorld, right ? inp.poleRight : inp.poleLeft);
            let side = v3.sub(pole, v3.mul(dir, v3.dot(pole, dir)));
            if (v3.lenSq(side) < 1e-8) side = v3.sub([0, 0, 1], v3.mul(dir, dir[2]));
            side = v3.norm(side);
            // upper arm: +y toward the elbow, +z toward the pole (the elbow's point), the forearm folding toward -z
            const y = v3.norm(v3.add(v3.mul(dir, Math.cos(shoulderAngle)), v3.mul(side, Math.sin(shoulderAngle))));
            const z = v3.norm(v3.sub(side, v3.mul(y, v3.dot(side, y))));
            const x = v3.norm(v3.cross(y, z));
            return {flex, upperWorld: q4.fromBasis(x, y, z)};
        }

        /** The forearm's frame (model rotation, +y toward the hand) the IK gives a wrist at `wrist`, untwisted. */
        forearm(inp, pose, right, wrist) {
            const rig = this.rig, upper = right ? rig.upperArmR : rig.upperArmL;
            const chestWorld = pose.rotation(rig.chest);
            let shoulder = v3.add(tf.apply(pose.world[rig.chest], rig.pivot[upper]), q4.apply(chestWorld, this.shoulderSet(inp, pose, right)));
            const reach = this.upperLength + this.foreLength;
            let toTarget = v3.sub(wrist, shoulder), distance = v3.len(toTarget);
            const protract = Math.min(inp.protraction, Math.max(0, distance - 0.98 * reach));
            if (protract > 0 && distance > 1e-4) shoulder = v3.add(shoulder, v3.mul(toTarget, protract / distance));
            toTarget = v3.sub(wrist, shoulder);
            distance = v3.len(toTarget);
            const d = Math.max(0.15 * reach, Math.min(distance, 0.999 * reach));
            const dir = distance > 1e-4 ? v3.mul(toTarget, 1 / distance) : [0, 1, 0];
            const {flex, upperWorld} = this.upperTurn(inp, chestWorld, right, dir, d);
            return q4.mul(upperWorld, q4.x(-flex));
        }

        ik(inp, pose, report, right, upper, fore, hand, chestWorld) {
            const rig = this.rig, target = right ? inp.targetRight : inp.targetLeft;
            const restPivot = rig.pivot[upper];
            let wrist = tf.apply(target, v3.sub(rig.pivot[hand], restPivot));
            const handWorld = target.q;
            const set = this.shoulderSet(inp, pose, right);
            let shoulder = v3.add(tf.apply(pose.world[rig.chest], restPivot), q4.apply(chestWorld, set));
            const reach = this.upperLength + this.foreLength;
            let toTarget = v3.sub(wrist, shoulder), distance = v3.len(toTarget);
            // support hand: slide back along the gun until the wrist is in reach (after the shoulder's own reach)
            const slideMax = right ? inp.slideRight : inp.slideLeft, slideLength = v3.len(slideMax);
            let slid = 0;
            const want = 0.98 * reach + inp.protraction;
            if (slideLength > 1e-4 && distance > want) {
                const axis = v3.mul(slideMax, 1 / slideLength);
                const b = v3.dot(toTarget, axis), c = v3.lenSq(toTarget) - want * want, disc = b * b - c;
                slid = disc >= 0 ? -b - Math.sqrt(disc) : -b;
                slid = Math.max(0, Math.min(slideLength, slid));
                const move = v3.mul(axis, slid);
                wrist = v3.add(wrist, move);
                toTarget = v3.add(toTarget, move);
                distance = v3.len(toTarget);
            }
            if (right) report.rightSlide = slid;
            else report.leftSlide = slid;
            // shoulder reach: the shoulder moves toward the target before the arm gives up (never a longer arm)
            const protract = Math.min(inp.protraction, Math.max(0, distance - 0.98 * reach));
            let shift = [0, 0, 0];
            if (protract > 0 && distance > 1e-4) shift = v3.mul(toTarget, protract / distance);
            pose.offset[upper] = v3.add(q4.apply(q4.conj(chestWorld), shift), set);
            shoulder = v3.add(shoulder, shift);
            toTarget = v3.sub(wrist, shoulder);
            distance = v3.len(toTarget);
            const d = Math.max(0.15 * reach, Math.min(distance, 0.999 * reach));
            const dir = distance > 1e-4 ? v3.mul(toTarget, 1 / distance) : [0, 1, 0];
            const {flex, upperWorld} = this.upperTurn(inp, chestWorld, right, dir, d);
            pose.local[upper] = q4.norm(q4.mul(q4.conj(chestWorld), upperWorld));
            const foreFlex = q4.x(-flex);
            const rel = q4.mul(q4.conj(q4.mul(upperWorld, foreFlex)), handWorld);
            let twist = [0, rel[1], 0, rel[3]];
            twist = v3.lenSq([twist[1], twist[3], 0]) < 1e-8 ? q4.id() : q4.norm(twist);
            twist = limitAngle(twist, TWIST_MAX);
            pose.local[fore] = q4.mul(foreFlex, twist);
            pose.local[hand] = limitAngle(q4.norm(q4.mul(q4.conj(twist), rel)), WRIST_MAX);
            const ratio = v3.dist(wrist, shoulder) / reach;
            if (right) { report.rightReach = ratio; report.rightElbowDeg = deg(flex); }
            else { report.leftReach = ratio; report.leftElbowDeg = deg(flex); }
        }
    }

    // ================================================================ actions (ActionPose, PlayerProps)

    const DISTAL_Y = 10, PALM_Y = 1.5;
    const POLE_R = [-0.35, 1, 0.45], POLE_L = [0.35, 1, 0.45];
    /** NativeHandBinding#centreX: the arm box's centre off the pivot (px), right arm negative. */
    const centreX = (right, slim) => (right ? -1 : 1) * (slim ? 0.5 : 1);
    const MASKS = ['full', 'upper', 'arms', 'arm_r', 'arm_l', 'legs'];

    function parseTarget(s) {
        const c = String(s).indexOf(':');
        return c < 0 ? {kind: String(s), name: ''} : {kind: s.slice(0, c), name: s.slice(c + 1)};
    }

    /** How much of the action shows: eased in over its blend-in, out over its blend-out after it stopped. */
    function actionWeight(m) {
        const a = m.action;
        const inW = a.blendIn <= 0 ? 1 : smooth((Number.isNaN(m.age) ? m.seconds : m.age) / a.blendIn);
        const outW = m.stopped < 0 ? 1 : a.blendOut <= 0 ? 0 : 1 - smooth(m.stopped / a.blendOut);
        return Math.max(0, Math.min(inW, outW));
    }

    function actionMask(rig, mask, mirror) {
        const m = new Array(rig.size).fill(false);
        let side = mask;
        if (mirror && side === 'arm_r') side = 'arm_l';
        else if (mirror && side === 'arm_l') side = 'arm_r';
        const set = bones => bones.forEach(b => { m[b] = true; });
        const armR = [rig.upperArmR, rig.forearmR, rig.handR], armL = [rig.upperArmL, rig.forearmL, rig.handL];
        switch (side) {
            case 'full': m.fill(true); break;
            case 'upper': set(armR); set(armL); set([rig.chest, rig.head]); break;
            case 'arms': set(armR); set(armL); break;
            case 'arm_r': set(armR); break;
            case 'arm_l': set(armL); break;
            case 'legs': set([rig.root, rig.pelvis, rig.thighR, rig.shinR, rig.footR, rig.thighL, rig.shinL, rig.footL]); break;
            default: throw new Error('mask ' + mask);
        }
        return m;
    }

    /** ActionPose.body without the approach (no anchors in Blockbench). False when the action does not show. */
    function actionBody(inp, rig, m) {
        const w = actionWeight(m);
        if (w <= 0) return false;
        const a = m.action;
        inp.actionMask = actionMask(rig, a.mask, m.mirror);
        inp.actionMirror = m.mirror;
        inp.actionClip = a.clip || null;
        inp.actionTime = m.seconds;
        inp.actionWeight = w;
        inp.paramClip = a.paramClip || null;
        inp.paramWeight = w * clamp01(m.param);
        if (a.holdBody) {   // PlayerAction#holdBody: the clip alone turns and bends the chest
            inp.chestYaw *= 1 - w;
            inp.chestPitch *= 1 - w;
        }
        if (a.holdHead) inp.headClip = Math.max(inp.headClip, w);   // PlayerAction#holdHead: the clip's head, not the look
        return true;
    }

    /** PlayerProps.Prop#local: item block units to the prop's model frame (px). */
    function propLocal(def, name) {
        const p = def && def.points ? def.points[name] : null;
        if (!p) return null;
        const k = 16 * def.scale;
        return [p[0] * k, -p[1] * k, -p[2] * k];
    }

    /** After solveBody: the props' placements, then both hands' targets. */
    function actionHands(inp, pose, poser, m) {
        placeProps(pose, m);
        const w = actionWeight(m);
        if (w <= 0) return;
        const right = m.mirror ? m.action.left : m.action.right, left = m.mirror ? m.action.right : m.action.left;
        handTimeline(inp, pose, poser, m, right, true, w);
        handTimeline(inp, pose, poser, m, left, false, w);
    }

    function poleOf(key, right, mirror) {
        if (!key.pole) return (right ? POLE_R : POLE_L).slice();
        const p = key.pole.slice();
        if (mirror) p[0] = -p[0];
        return p;
    }

    function handTimeline(inp, pose, poser, m, keys, right, w) {
        if (!keys || !keys.length) return;
        let k = 0;
        for (let i = 0; i < keys.length; i++) if (keys[i].at <= m.seconds) k = i;
        const cur = keys[k], prev = k > 0 ? keys[k - 1] : null;
        const p = !prev ? 1 : cur.blend <= 0 ? 1 : smooth((m.seconds - cur.at) / cur.blend);
        let pole = poleOf(cur, right, m.mirror);
        if (prev && p < 1) pole = v3.lerp(poleOf(prev, right, m.mirror), pole, p);
        if (right) inp.poleRight = pole;
        else inp.poleLeft = pole;
        const isPose = key => parseTarget(key.target).kind === 'pose';
        const to = isPose(cur) ? null : handTarget(inp, pose, poser, m, cur, right);
        const from = !prev || isPose(prev) ? null : handTarget(inp, pose, poser, m, prev, right);
        let target, weight;
        if (!to && !from) { weight = 0; target = null; }
        else if (!to) { weight = 1 - p; target = from; }
        else if (!from) { weight = prev ? p : 1; target = to; }
        else { weight = 1; target = blendTargets(from, to, p, right, inp.slim); }
        if (right) { inp.right = 'IK'; inp.ikRightWeight = weight * w; }
        else { inp.left = 'IK'; inp.ikLeftWeight = weight * w; }
        if (target) {
            if (right) inp.targetRight = target;
            else inp.targetLeft = target;
        }
    }

    /** A key's IK target (the rest arm frame to model px), or null when it resolves to nothing. */
    function handTarget(inp, pose, poser, m, key, right) {
        const t = parseTarget(key.target);
        if (t.kind === 'prop' && !m.mirror) {
            const held = gripTarget(m, t.name, right, inp.slim);
            if (held) return held;
        }
        let grip = null;
        if (t.kind === 'socket') {
            const s = socketOf(pose.rig, t.name, m.mirror);
            grip = s ? pose.point(s.bone, s.pos) : null;
        } else if (t.kind === 'block') {
            grip = m.anchors(t.name);
        } else if (t.kind === 'prop') {
            // prop:<point> on the first prop, prop:<id>:<point> on that one
            let name = t.name, track = 0;
            const colon = name.indexOf(':');
            if (colon >= 0) {
                track = propIndex(m.action, name.slice(0, colon));
                name = name.slice(colon + 1);
            }
            const placed = track >= 0 && m.props && track < m.props.length ? m.props[track] : null;
            const local = placed ? propLocal(m.action.props[track].def, name) : null;
            grip = local ? tf.apply(placed, local) : null;
        }
        if (!grip) return null;
        return hold(poser.forearm(inp, pose, right, grip), grip, right, inp.slim, m.mirror ? -key.roll : key.roll);
    }

    /**
     * ActionPose.gripTarget: a hand on a gun prop's grip (PlayerProps.Prop#grips), locked to the gun as
     * GunStance#handTarget places it (the anchor's frame without the prop's size, turned about the palm by the grip), or
     * null (no such grip, the prop not placed).
     */
    function gripTarget(m, name, right, slim) {
        let point = name, track = 0;
        const colon = name.indexOf(':');
        if (colon >= 0) {
            track = propIndex(m.action, name.slice(0, colon));
            point = name.slice(colon + 1);
        }
        const placed = track >= 0 && m.props && track < m.props.length ? m.props[track] : null;
        const def = placed ? m.action.props[track].def : null;
        const g = def && def.grips ? def.grips[point] : null;
        if (!g) return null;
        // the prop's model frame -> item units (PlayerProps.Prop#itemToLocal: scale(k, -k, -k)) -> the anchor
        const M = tf.mul(tf.mul(placed, {q: RX180, t: [0, 0, 0], s: 16 * def.scale}), {q: q4.norm(g.quat), t: g.pos});
        let out = {q: M.q, t: M.t};
        const turn = g.grip || [0, 0, 0];
        if (turn[0] || turn[1] || turn[2])
            out = tf.mul(tf.mul(tf.mul(out, tf.translation([0, -PALM_Y, 0])), {q: q4.xyz(rad(turn[0]), rad(turn[1]), rad(turn[2])), t: [0, 0, 0]}),
                tf.translation([0, PALM_Y, 0]));
        return tf.mul(out, tf.translation([-centreX(right, slim), -DISTAL_Y, 0]));
    }

    const propIndex = (action, id) => (action.props || []).findIndex(t => t.id === id);
    /** ActionPose.otherSide: x_r <-> x_l; a name without a side stays. */
    const otherSide = n => (n.endsWith('_r') ? n.slice(0, -2) + '_l' : n.endsWith('_l') ? n.slice(0, -2) + '_r' : n);
    /** An arm bone: the IK places it after the props, so a prop cannot ride on it. */
    const isArm = (rig, b) => [rig.upperArmR, rig.forearmR, rig.handR, rig.upperArmL, rig.forearmL, rig.handL].includes(b);

    /** ActionPose.socket: a body socket for this side; one without a side is reflected onto its bone's other side. */
    function socketOf(rig, name, mirror) {
        if (!mirror) return rig.sockets[name] || null;
        const other = otherSide(name);
        if (other !== name) return rig.sockets[other] || null;
        const s = rig.sockets[name];
        if (!s) return null;
        const bone = rig.find(otherSide(rig.names[s.bone]));
        return bone < 0 ? null : {bone, pos: [-s.pos[0], s.pos[1], s.pos[2]]};
    }

    /** ActionPose.props: every prop placed in order (one riding on another finds it placed), hidden, open; m.prop the first. */
    function placeProps(pose, m) {
        const tracks = m.action.props || [];
        m.props = tracks.map(() => null);
        m.propHidden = tracks.map(() => false);
        m.propsOpen = tracks.map(() => false);
        tracks.forEach((t, i) => {
            m.props[i] = propPlacement(pose, m, i);
            m.propsOpen[i] = !!m.props[i] && m.seconds >= t.openFrom;
        });
        m.prop = tracks.length ? m.props[0] : null;
    }

    /** Prop track i placed at this moment (its model frame to model px), or null. Not mirrored. */
    function propPlacement(pose, m, index) {
        const track = m.action.props[index], keys = track ? track.keys : [];
        if (!track || !keys.length || !track.def) return null;
        const def = track.def;
        let k = 0;
        for (let i = 0; i < keys.length; i++) if (keys[i].at <= m.seconds) k = i;
        let to = propPlace(pose, def, keys[k], m);
        while (!to && k > 0) to = propPlace(pose, def, keys[--k], m);
        const cur = keys[k], prev = k > 0 ? keys[k - 1] : null;
        if (m.propHidden) m.propHidden[index] = !!cur.hidden;
        if (!to || !prev) return to;
        const p = cur.blend <= 0 ? 1 : smooth((m.seconds - cur.at) / cur.blend);
        if (p >= 1) return to;
        const from = propPlace(pose, def, prev, m);
        if (!from) return to;
        const pivot = propLocal(def, cur.point);
        const pa = tf.apply(from, pivot), pb = tf.apply(to, pivot);
        const qa = from.q;
        let qb = to.q;
        if (q4.dot(qa, qb) < 0) qb = q4.neg(qb);
        const q = q4.slerp(qa, qb, p);
        // a gun leaving its carry mount (drawn larger there) changes size on the way
        const sa = scaleOf(from), sb = scaleOf(to), s = sa + (sb - sa) * p;
        if (Math.abs(s - 1) > 1e-5) return {q, t: v3.sub(v3.lerp(pa, pb, p), v3.mul(q4.apply(q, pivot), s)), s};
        return {q, t: v3.sub(v3.lerp(pa, pb, p), q4.apply(q, pivot))};
    }

    /**
     * ActionPose.carryPreview: a gun prop at its carry mount as its file previews it (def.carry on the posed bone), or null:
     * the carry frame where there is no live mount (always, here).
     */
    function carryPreview(pose, def) {
        const c = def ? def.carry : null, bone = c ? pose.rig.find(c.bone) : -1;
        return bone < 0 ? null : tf.mul(pose.world[bone], {q: q4.norm(c.quat), t: c.pos, s: c.scale});
    }

    function propPlace(pose, def, key, m) {
        const local = propLocal(def, key.point);
        if (!local) return null;
        if (key.frame.startsWith('prop:')) return onProp(key, local, m);
        const turn = q4.xyz(rad(key.rot[0]), rad(key.rot[1]), rad(key.rot[2]));
        if (key.frame === 'carry') {
            // the mounted gun, turned about the key's point in its own (mounted, sized) frame
            const carry = m.carry || carryPreview(pose, def);
            if (!carry) return null;
            return tf.mul(carry, {q: turn, t: v3.sub(v3.add(local, key.pos), q4.apply(turn, local))});
        }
        const rig = pose.rig, rest = key.pos.slice();
        let at, r;
        if (key.frame === 'view') {
            // the eye, turned as the look (ActionPose.Moment#view)
            if (!m.view) return null;
            const view = actionView(m.view, m.action.viewPitch);
            at = tf.apply(view, m.viewAhead ? [rest[0], rest[1], rest[2] - m.viewAhead] : rest);
            r = q4.mul(view.q, turn);
        } else if (key.frame.startsWith('anchor:')) {
            const anchor = m.anchors(key.frame.slice(7));
            if (!anchor) return null;
            const root = pose.rotation(rig.root);
            at = v3.add(anchor, q4.apply(root, rest));
            r = q4.mul(root, turn);
        } else if (key.frame.startsWith('shoulder_')) {
            const right = (key.frame === 'shoulder_r') !== m.mirror, upper = right ? rig.upperArmR : rig.upperArmL;
            const root = pose.rotation(rig.root);
            at = v3.add(pose.point(upper, rig.pivot[upper]), q4.apply(root, rest));
            r = q4.mul(root, turn);
        } else if (key.frame.startsWith('socket:')) {
            const s = socketOf(rig, key.frame.slice(7), m.mirror);
            if (!s || isArm(rig, s.bone)) return null;
            at = pose.point(s.bone, v3.add(rest, s.pos));
            r = q4.mul(pose.rotation(s.bone), turn);
        } else {
            const name = key.frame.startsWith('bone:') ? key.frame.slice(5) : key.frame;   // chest, root, bone:<name>
            const bone = rig.find(m.mirror ? otherSide(name) : name);
            if (bone < 0 || isArm(rig, bone)) return null;
            at = pose.point(bone, rest);
            r = q4.mul(pose.rotation(bone), turn);
        }
        return {q: r, t: v3.sub(at, q4.apply(r, local))};
    }

    /** ActionPose.onProp (unmirrored): the key's point at pos in an earlier prop's placed frame, turned from it. */
    function onProp(key, local, m) {
        const parent = propIndex(m.action, key.frame.slice(5));
        const on = parent >= 0 && m.props && parent < m.props.length ? m.props[parent] : null;
        if (!on) return null;
        const turn = q4.xyz(rad(key.rot[0]), rad(key.rot[1]), rad(key.rot[2]));
        return tf.mul(on, {q: turn, t: v3.sub(key.pos, q4.apply(turn, local))});
    }

    /**
     * A prop track's placement on the rest pose (identity bones, no clips): what the keys mean in Blockbench's rest
     * space. The tracks before it must be in action.props (the ones it may ride on).
     */
    function restPlacement(rig, action, index, seconds) {
        const pose = new Pose(rig);
        pose.updateAll();
        const m = {action, seconds, stopped: -1, age: NaN, param: 0, mirror: false, anchors: () => null, props: [], propHidden: [],
            view: restFrame(rig, 'view')};
        for (let i = 0; i <= index; i++) m.props[i] = propPlacement(pose, m, i);
        return m.props[index];
    }

    /** The hand with its palm on grip (model px), turned as forearm (no twist), then roll deg about it. */
    function hold(forearm, grip, right, slim, roll) {
        let r = forearm.slice();
        if (roll) r = q4.mul(r, q4.y(rad(roll)));
        const cap = v3.add(v3.mul(q4.apply(r, [0, 1, 0]), PALM_Y), grip);
        return tf.mul({q: r, t: cap}, tf.translation([-centreX(right, slim), -DISTAL_Y, 0]));
    }

    /** Between two IK targets: the palm's place straight, the hand's turn the short way. */
    function blendTargets(a, b, t, right, slim) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        const palm = [centreX(right, slim), DISTAL_Y - PALM_Y, 0];
        const pa = tf.apply(a, palm), pb = tf.apply(b, palm);
        let qb = b.q;
        if (q4.dot(a.q, qb) < 0) qb = q4.neg(qb);
        const q = q4.slerp(a.q, qb, t);
        return {q, t: v3.sub(v3.lerp(pa, pb, t), q4.apply(q, palm))};
    }

    // ================================================================ one frame (client/skeleton/PlayerSkeleton#solve)

    /** PlayerSkeletonCheck.eyeO through toModel: the vanilla eye in model px (only x and z steer the root). */
    function eyeModelY(crouch) {
        return 16 * (1.501 - (crouch ? 1.27 + 0.125 : 1.62) / 0.9375);
    }

    /** ActionPose.viewFrame: the view frame from the eye (model px) and the view's yaw and pitch against the body (deg). */
    function viewFrame(eye, viewYaw, viewPitch) {
        return {q: q4.mul(q4.y(rad(viewYaw)), q4.x(rad(viewPitch))), t: eye.slice()};
    }

    /**
     * GunStance#firstPersonReach: first person with a gun held out on the view frame, the view frame's keys this far further
     * ahead (px) and the shoulders' reach as much more (ActionPose.Moment#viewAhead, docs/dev/p9_stance_loops_v1.md 4.2): full
     * up to 15 deg down, none past 45.
     */
    const FIRST_PERSON_REACH = 5, FIRST_PERSON_REACH_FROM = 15, FIRST_PERSON_REACH_TO = 45;
    function firstPersonReach(pitch) {
        let t = Math.max(0, Math.min(1, (pitch - FIRST_PERSON_REACH_FROM) / (FIRST_PERSON_REACH_TO - FIRST_PERSON_REACH_FROM)));
        t = t * t * (3 - 2 * t);
        return FIRST_PERSON_REACH * (1 - t);
    }

    /**
     * GunStance#firstPersonArm: with that reach the arms are drawn this wide (each box's x about its middle; the old view
     * model's 0.62; depth and length as they are), docs/dev/p9_stance_loops_v1.md 4.3.
     */
    const FIRST_PERSON_ARM = 0.62;
    function firstPersonArm(ahead) {
        return 1 - (1 - FIRST_PERSON_ARM) * Math.min(1, ahead / FIRST_PERSON_REACH);
    }

    /** ActionPose.VIEW_KNEE / eased: a pitch held within [up, down], easing toward a limit over its last VIEW_KNEE deg. */
    const VIEW_KNEE = 10;
    function easedPitch(pitch, up, down) {
        const knee = Math.min(VIEW_KNEE, (down - up) / 2);
        if (pitch > down - knee) return down - knee * Math.exp(-(pitch - (down - knee)) / knee);
        if (pitch < up + knee) return up + knee * Math.exp(-((up + knee) - pitch) / knee);
        return pitch;
    }

    /** ActionPose.actionView: the view frame turned back about its x where the look's pitch passes the action's view_pitch. */
    function actionView(view, limits) {
        if (!limits) return view;
        const ahead = v3.norm(q4.apply(view.q, [0, 0, -1]));
        const pitch = deg(Math.asin(Math.max(-1, Math.min(1, ahead[1])))), held = easedPitch(pitch, limits[0], limits[1]);
        return held === pitch ? view : {q: q4.mul(view.q, q4.x(rad(held - pitch))), t: view.t};
    }

    /**
     * A standing player (PlayerSkeletonCheck.standing: facing ahead, no chest twist) with an action at o.seconds.
     * o: {slim, crouch, pitch, yaw, chestYaw, rifle, time, action, seconds, param, fp, free, headClip}. fp: the own first
     * person, a gun action (one with a gun prop) reaching further (firstPersonReach; client/skeleton/SkeletonGunStance#hold).
     * free: no eye target, the root stays put (lying on the front, client/skeleton/ProneClient); headClip: Input.headClip.
     */
    function solveFrame(poser, o) {
        const rig = poser.rig, inp = newInput();
        inp.slim = !!o.slim;
        inp.crouch = o.crouch ? 1 : 0;
        inp.viewPitch = o.pitch || 0;
        inp.chestPitch = Poser.chestPitch(inp.viewPitch, !!o.rifle);
        inp.time = o.time || 0;
        inp.viewYaw = o.yaw || 0;
        // the chest right of the view: given, else the action's (a gun stance loop's bladed body)
        inp.chestYaw = o.chestYaw !== undefined ? o.chestYaw : (o.action && o.action.chestTwist) || 0;
        inp.eyeTarget = [0, eyeModelY(o.crouch), 0];
        let m = o.action ? {action: o.action, seconds: o.seconds || 0, stopped: -1, age: NaN, param: o.param || 0, mirror: false,
            anchors: () => null, prop: null, props: [], propHidden: [], view: viewFrame(inp.eyeTarget, inp.viewYaw, inp.viewPitch)} : null;
        inp.headClip = o.headClip || 0;
        if (o.free) inp.eyeTarget = null;
        if (m && !actionBody(inp, rig, m)) m = null;
        if (m && o.fp && (m.action.props || []).some(p => p.def && p.def.gun)) {
            m.viewAhead = firstPersonReach(inp.viewPitch);
            inp.protraction += m.viewAhead;
        }
        const pose = new Pose(rig);
        const report = poser.solveBody(inp, pose);
        inp.right = 'CLIP';
        inp.left = 'CLIP';
        if (m) actionHands(inp, pose, poser, m);
        poser.solveArms(inp, pose, report);
        return {inp, pose, report, moment: m, prop: m ? m.prop : null, props: m ? m.props : [], hidden: m ? m.propHidden : [],
            arms: m && m.viewAhead ? firstPersonArm(m.viewAhead) : 1};
    }

    /** ActionCheck's numbers: palm off its target (px), wrist bend, elbow, forearm twist (deg), reach, IK weight. */
    function readouts(res) {
        const {inp, pose, report} = res, rig = pose.rig, out = {};
        for (const right of [true, false]) {
            const side = right ? 'right' : 'left';
            if ((right ? inp.right : inp.left) !== 'IK') {
                out[side] = {mode: 'clip'};
                continue;
            }
            const upper = right ? rig.upperArmR : rig.upperArmL, fore = right ? rig.forearmR : rig.forearmL, hand = right ? rig.handR : rig.handL;
            const palmRest = v3.add(rig.pivot[upper], [centreX(right, false), DISTAL_Y - PALM_Y, 0]);
            const palm = pose.point(hand, palmRest);
            const want = tf.apply(right ? inp.targetRight : inp.targetLeft, v3.sub(palmRest, rig.pivot[upper]));
            const f = pose.local[fore];
            out[side] = {
                mode: 'ik', off: v3.dist(palm, want), wrist: deg(q4.angle(pose.local[hand])), elbow: deg(q4.angle(f)),
                twist: deg(q4.angle(q4.norm([0, f[1], 0, f[3]]))), reach: right ? report.rightReach : report.leftReach,
                w: right ? inp.ikRightWeight : inp.ikLeftWeight,
            };
        }
        return out;
    }

    /** A PlayerAction JSON's prop list: {@code props}, or the single {@code prop}. */
    const propsOfJson = json => (json.props ? json.props : json.prop ? [json.prop] : []);

    /** One PlayerAction prop key, defaults filled. */
    const propKeyOf = k => ({
        at: k.at || 0, frame: k.frame || 'chest', point: k.point, pos: k.pos || [0, 0, 0], rot: k.rot || [0, 0, 0],
        blend: k.blend !== undefined ? k.blend : 0.3, hidden: !!k.hidden,
    });

    /**
     * A PlayerAction JSON as solveFrame's action. clipFor: clip key -> Clip or null; defFor: prop id -> {scale, points} or
     * null (the PlayerProps file).
     */
    function actionFromJson(json, defFor, clipFor) {
        const keys = side => (((json.hands || {})[side]) || []).map(k => ({
            at: k.at || 0, target: k.target, blend: k.blend !== undefined ? k.blend : 0.2, roll: k.roll || 0, pole: k.pole || null,
        }));
        const blend = json.blend || {};
        return {
            mask: json.mask || 'upper',
            blendIn: blend.in !== undefined ? blend.in : 0.25,
            blendOut: blend.out !== undefined ? blend.out : 0.25,
            clip: json.clip && clipFor ? clipFor(json.clip) : null,
            paramClip: json.param_clip && clipFor ? clipFor(json.param_clip) : null,
            right: keys('right'), left: keys('left'),
            viewPitch: Array.isArray(json.view_pitch) ? json.view_pitch : null,
            chestTwist: json.chest_twist || 0,
            holdBody: !!json.hold_body,
            holdHead: !!json.hold_head,
            props: propsOfJson(json).map(p => ({
                id: p.id, def: defFor ? defFor(p.id) : null, keys: p.keys.map(propKeyOf),
                openFrom: p.open_from !== undefined ? p.open_from : NaN,
            })),
        };
    }

    // ================================================================ prop keys <-> Blockbench keyframes

    /**
     * Where a frame's pos is measured from on the rest pose (Java model px): the shoulder pivot, the socket, the eye
     * (view), else the origin (chest, root, bone:). A prop: frame has no fixed one; its caller passes the earlier prop's rest placement.
     */
    function restFrame(rig, frame) {
        if (frame === 'view') return tf.translation(rig.sockets.eye ? rig.sockets.eye.pos : [0, eyeModelY(false), 0]);   // the eye, looking ahead
        if (frame.startsWith('shoulder_')) return tf.translation(rig.pivot[frame === 'shoulder_r' ? rig.upperArmR : rig.upperArmL]);
        if (frame.startsWith('socket:')) {
            const s = rig.sockets[frame.slice(7)];
            return tf.translation(s ? s.pos : [0, 0, 0]);
        }
        return tf.id();
    }

    /**
     * The prop group (its origin G at the hold point's edit place) with a keyframe of position delta and rotation
     * (Blockbench numbers, ZYX Euler) shows a placement on the rest pose; as a runtime PropKey in a frame whose rest
     * transform is F (restFrame, or an earlier prop's rest placement for prop:):
     * - turn = F.q^-1 * Rz180 * R * Rx180;
     * - pos = F^-1 B2J(G + delta + R (e_point - G)).
     * Prop content edit coords e map into the prop's model frame as Rx180 (e - D).
     */
    function bbToPropKey(rig, rotDeg, delta, G, ePoint, frame, F) {
        F = F || restFrame(rig, frame);
        const R = q4.zyx(rad(rotDeg[2]), rad(rotDeg[1]), rad(rotDeg[0]));
        const Fi = tf.inv(F);
        const turn = q4.mul(Fi.q, q4.mul(q4.mul(RZ180, R), RX180));
        const restB = v3.add(v3.add(G, delta), q4.apply(R, v3.sub(ePoint, G)));
        return {pos: tf.apply(Fi, j2b(restB)), rot: q4.eulerXYZ(turn).map(deg)};
    }

    /** The inverse: a PropKey's pos and rot as the prop group's keyframe {rot (deg), delta}. */
    function propKeyToBB(rig, key, G, ePoint, F) {
        F = F || restFrame(rig, key.frame);
        const turn = q4.mul(F.q, q4.xyz(rad(key.rot[0]), rad(key.rot[1]), rad(key.rot[2])));
        const R = q4.mul(q4.mul(RZ180, turn), RX180);
        const delta = v3.sub(v3.sub(j2b(tf.apply(F, key.pos)), q4.apply(R, v3.sub(ePoint, G))), G);
        return {rot: q4.eulerZYX(R).map(deg), delta};
    }

    const core = {
        VERSION, v3, q4, tf, J2B, j2b, rad, deg, smooth, loadRig, RIG_FALLBACK, Pose, Channel, Clip, loadClips, channelFromJson, numberOf,
        Poser, newInput, solveFrame, viewFrame, easedPitch, firstPersonReach, firstPersonArm, readouts, actionFromJson, actionMask, propLocal, centreX, DISTAL_Y, PALM_Y, MASKS,
        bbToPropKey, propKeyToBB, restFrame, restPlacement, propsOfJson, propKeyOf, propIndex, isArm, eyeModelY, parseTarget,
        carryPreview, gripTarget, RX180,
    };

    if (typeof module !== 'undefined' && module.exports) module.exports = core;
    if (typeof Plugin !== 'undefined' && typeof Blockbench !== 'undefined') registerPlugin(core);

    // ================================================================ Blockbench

    function registerPlugin(core) {
        const S = {repo: null, rig: null, poser: null, loco: false, err: '', fs: null, fpSaved: null, listeners: [], disposables: []};
        const UI = {
            project: false, status: '', anim: '', masks: MASKS, layers: ['stance', 'action', 'full_body'], sides: ['right', 'left'],
            view: {pitch: 0, fp: false, crouch: false, breathing: false, rifle: false, slim: false},
            action: {action_id: '', clip_key: '', mask: 'arms', layer: 'action', priority: 50, first_person: 'body', blend_in: 0.25,
                blend_out: 0.25, loop: 'none', hip_fire: false, view_up: '', view_down: '', chest_twist: 0, about: ''},
            loops: ['none', 'time', 'stride'],
            hands: {right: [], left: []},
            props: [], targets: ['pose'], problems: '',
            read: {right: null, left: null},
        };

        // ------------------------------------------------ project state

        const CFG_DEFAULT = () => ({version: 2, repo: DEFAULT_REPO, slim: false, view_pitch: 0, first_person: false, crouch: false,
            breathing: false, rifle: false, props: []});
        /**
         * Per animation. props: per prop id {segments [{at, frame, point, hidden}] (from at on the prop rides on frame, its
         * keys placing point: its attach points over time), open_from}. Hand targets prop:<id>:<point>. extra.props_raw:
         * imported tracks the plugin cannot show (anchor frames), written back as they were at their old place.
         */
        const AFL_DEFAULT = () => ({action_id: '', clip_key: '', mask: 'arms', layer: 'action', priority: 50, first_person: 'body',
            blend_in: 0.25, blend_out: 0.25, loop: 'none', hip_fire: false, view_pitch: null, chest_twist: 0, about: '', props: {}, hands: {right: [], left: []},
            extra: {}});
        const clone = o => JSON.parse(JSON.stringify(o));

        function cfg() {
            const c = typeof Project !== 'undefined' && Project && Project.afl_rig && Project.afl_rig.version ? Project.afl_rig : null;
            if (c && !Array.isArray(c.props)) {   // a phase 1 project: one prop
                c.props = c.prop ? [c.prop] : [];
                delete c.prop;
            }
            return c;
        }

        const propsOf = c => (c && c.props) || [];

        /** prop:<point> (the first prop, as the runtime reads it) as prop:<id>:<point>, the panel's form. */
        const fullTarget = (target, first) => {
            const t = core.parseTarget(target);
            return t.kind === 'prop' && first && !t.name.includes(':') ? 'prop:' + first + ':' + t.name : target;
        };

        function aflOf(anim) {
            const a = Object.assign(AFL_DEFAULT(), anim && anim.afl ? clone(anim.afl) : {});
            a.hands = Object.assign({right: [], left: []}, a.hands || {});
            a.extra = a.extra || {};
            a.props = a.props || {};
            // phase 1 animations: one prop's point and open_from, prop:<point> targets
            const first = propsOf(cfg()).length ? cfg().props[0].id : null;
            if (first && a.prop_point) (a.props[first] = a.props[first] || {}).point = a.prop_point;
            if (first && a.extra.prop_open_from !== undefined) (a.props[first] = a.props[first] || {}).open_from = a.extra.prop_open_from;
            if (a.extra.prop_raw) a.extra.props_raw = [{index: 0, json: a.extra.prop_raw}];
            delete a.prop_point;
            delete a.extra.prop_open_from;
            delete a.extra.prop_raw;
            for (const side of ['right', 'left']) a.hands[side].forEach(k => { k.target = fullTarget(k.target, first); });
            return a;
        }

        /**
         * The animation's settings of prop p: attach segments (sorted, the first from 0; each a frame, the point its keys
         * place and turn about, hidden) and open_from. A phase 1 animation's one key point is every segment's.
         */
        function trackSet(a, p) {
            const s = a.props[p.id] || {};
            const segments = (s.segments && s.segments.length ? clone(s.segments) : [{at: 0, frame: p.frame, hidden: false}])
                .sort((x, y) => x.at - y.at);
            segments[0].at = 0;
            segments.forEach(x => {
                x.frame = x.frame || p.frame;
                x.point = x.point || s.point || p.hold;
                x.hidden = !!x.hidden;
            });
            return {segments, open_from: s.open_from};
        }

        /** The segment in effect at t. */
        const segmentAt = (segs, t) => segs.reduce((s, x) => (x.at <= t + 1e-6 ? x : s), segs[0]);

        /**
         * When the run of segments on the same frame as the one at t began: a prop: frame's keys are read against the
         * earlier prop as it was then (the attach moment), so a magazine left alone rides with the pistol.
         */
        function frameSince(segs, t) {
            const i = segs.indexOf(segmentAt(segs, t));
            let j = i;
            while (j > 0 && segs[j - 1].frame === segs[i].frame) j--;
            return segs[j].at;
        }

        function repo() {
            const c = cfg();
            return ((c && c.repo) || DEFAULT_REPO).replace(/[\\/]+$/, '');
        }

        const res = rel => repo() + '/' + ASSETS + rel;

        function nodeFs() {
            if (!S.fs) S.fs = typeof requireNativeModule === 'function' ? requireNativeModule('fs') : require('fs');
            return S.fs;
        }

        function readJson(path) {
            const fs = nodeFs();
            if (!fs.existsSync(path)) return null;
            return JSON.parse(fs.readFileSync(path, 'utf8'));
        }

        /** Loads the rig and the locomotion clips from the repo (once per repo path). */
        function ensureCore() {
            const r = repo();
            if (S.poser && S.repo === r) return;
            S.repo = r;
            S.err = '';
            let rigJson = null, loco = null;
            try {
                rigJson = readJson(res('rigs/afl_humanoid_v1.json'));
                loco = readJson(res('animations/player/locomotion.animation.json'));
            } catch (e) {
                S.err = '读仓库文件失败：' + e.message;
            }
            S.rig = core.loadRig(rigJson || core.RIG_FALLBACK);
            S.poser = new core.Poser(S.rig, loco ? core.loadClips(S.rig, loco) : {});
            S.loco = !!loco;
            if (!rigJson && !S.err) S.err = '仓库里没找到骨架文件，用插件自带的一份';
            else if (!loco && !S.err) S.err = '没找到 locomotion.animation.json，身体用静止原姿';
        }

        // ------------------------------------------------ rig project

        const MARKER = {head: 1, chest: 0, pelvis: 0, upper_arm_r: 3, forearm_r: 2, hand_r: 1, upper_arm_l: 5, forearm_l: 4, hand_l: 1,
            thigh_r: 6, shin_r: 7, foot_r: 6, thigh_l: 6, shin_l: 7, foot_l: 6};
        const geoToB = p => [-p[0], p[1], p[2]];
        const groupNamed = name => Group.all.find(g => g.name === name);

        /** The skin as cubes, one per bone segment (rig skin parts cut along y), plus the head. */
        function buildSkin(slim) {
            const made = [];
            const box = (name, bone, from, to) => {
                const g = groupNamed(bone);
                if (!g) return;
                const c = new Cube({name, from, to, origin: g.origin.slice(), color: MARKER[bone] || 0}).addTo(g).init();
                made.push(c);
            };
            for (const part of S.rig.json.skin) {
                const from = slim && part.slim_from ? part.slim_from : part.from, size = slim && part.slim_size ? part.slim_size : part.size;
                const top = from[1] + size[1], x0 = -(from[0] + size[0]), x1 = -from[0], z0 = from[2], z1 = from[2] + size[2];
                part.bones.forEach((bone, i) => {
                    const hi = i === 0 ? top : top - part.cuts[i - 1], lo = i === part.bones.length - 1 ? from[1] : top - part.cuts[i];
                    box('skin_' + bone, bone, [x0, lo, z0], [x1, hi, z1]);
                });
            }
            box('skin_head', 'head', [-4, 24, -4], [4, 32, 4]);
            return made;
        }

        function newRigProject() {
            ensureCore();
            if (!newProject(Formats.free)) return;
            Project.name = 'afl_action';
            Project.afl_rig = CFG_DEFAULT();
            Project.afl_rig.repo = S.repo || DEFAULT_REPO;
            const groups = {};
            for (const b of S.rig.json.bones) {
                const g = new Group({name: b.name, origin: geoToB(b.pivot)}).init();
                if (b.parent) g.addTo(groups[b.parent]);
                groups[b.name] = g;
            }
            buildSkin(false);
            for (const name in S.rig.json.sockets) {
                const s = S.rig.json.sockets[name];
                new Locator({name, position: geoToB(s.pos)}).addTo(groups[s.bone]).init();
            }
            addHolsterRef();
            Canvas.updateAll();
            refreshUi();
            Blockbench.showQuickMessage('AFL 人物工程已建好：切到动画模式，新建动画开始做动作', 3000);
        }

        function rebuildSkin(slim) {
            const old = Cube.all.filter(c => c.name.startsWith('skin_') && c.parent instanceof Group && S.rig.find(c.parent.name) >= 0);
            Undo.initEdit({elements: old, outliner: true});
            old.forEach(c => c.remove());
            const made = buildSkin(slim);
            Undo.finishEdit('AFL 手臂粗细', {elements: made, outliner: true});
            Canvas.updateAll();
        }

        // ------------------------------------------------ holster reference

        const HOLSTER_REF = 'ref_pistol_holster';

        /**
         * The pistol holster as worn (meshes/pistol_holster.aflmesh.json under thigh_r, ClientEquipmentSlots'
         * WornMeshRenderer: leg frame = Rz180 of the leg, mesh px 16 v - origin): a locked look-only reference for draw and
         * holster work, not exported. Its mouth sits at the rig's holster socket.
         */
        function addHolsterRef() {
            const old = groupNamed(HOLSTER_REF), thigh = groupNamed('thigh_r');
            if (old || !thigh) return old || null;
            const json = readJson(res('meshes/pistol_holster.aflmesh.json'));
            if (!json) {
                Blockbench.showQuickMessage('没找到 meshes/pistol_holster.aflmesh.json', 2500);
                return null;
            }
            const O = [-0.605, 7.6, 0];   // ClientEquipmentSlots: the bake's origin shift (px)
            // leg frame L -> Blockbench: Java (pivot - Lx, pivot - Ly, Lz), then j2b: the thigh origin + L
            const at = v => v3.add(thigh.origin, v3.sub(v3.mul(v.slice(0, 3), 16), O));
            const g = new Group({name: HOLSTER_REF, origin: thigh.origin.slice(), locked: true, export: false}).addTo(thigh).init();
            for (const part of json.parts) {
                const mesh = new Mesh({name: 'ref_' + part.name, origin: [0, 0, 0], vertices: {}, color: 1, locked: true, export: false});
                const keys = mesh.addVertices(...part.vertices.map(at));
                for (const f of part.faces) {
                    // quads and triangles as they are, bigger polygons as fans
                    const polys = f.length <= 4 ? [f] : f.slice(1, -1).map((x, i) => [f[0], x, f[i + 2]]);
                    for (const poly of polys) mesh.addFaces(new MeshFace(mesh, {vertices: poly.map(x => keys[x])}));
                }
                mesh.addTo(g).init();
            }
            Canvas.updateAll();
            return g;
        }

        // ------------------------------------------------ props

        /** A cfg prop's group, or null (deleted in the outliner: the prop is skipped until made again). */
        const propGroupOf = p => (p ? groupNamed(p.group) || null : null);
        const liveProps = c => propsOf(c).filter(p => propGroupOf(p));

        /** The prop's points (item block units) from the locators under its group. */
        function propDef(p) {
            const g = propGroupOf(p);
            if (!g) return null;
            const k = 16 * p.scale, points = {};
            for (const child of g.children) {
                if (child instanceof Locator) points[child.name] = v3.mul(v3.sub(child.position, p.D), 1 / k);
            }
            return {scale: p.scale, points, gun: p.gun || null, grips: p.grips || null, carry: p.carry || null};
        }

        const pointEdit = (g, name) => {
            const l = g.children.find(c => c instanceof Locator && c.name === name);
            return l ? l.position.slice() : null;
        };

        /** The rest right palm (Blockbench px): where a new prop's hold point goes. */
        function restPalmB(slim) {
            const r = S.rig;
            return j2b(v3.add(r.pivot[r.upperArmR], [centreX(true, slim), DISTAL_Y - PALM_Y, 0]));
        }

        /**
         * Where a new prop's hold point goes for its first frame: the socket, the gun's carry mount (def, hold), the earlier
         * prop's hold point, else the palm.
         */
        function restPlaceB(c, frame, def, hold) {
            if (frame.startsWith('socket:') && S.rig.sockets[frame.slice(7)]) return j2b(S.rig.sockets[frame.slice(7)].pos);
            if (frame === 'carry' && def && def.carry) return j2b(tf.apply(carryRest(def), core.propLocal(def, hold)));
            if (frame.startsWith('prop:')) {
                const g = propGroupOf(propsOf(c).find(p => p.id === frame.slice(5)));
                if (g) return g.origin.slice();
            }
            return restPalmB(c.slim);
        }

        /** The rig bone group a prop group sits under (only seen while the prop is not in the shown action). */
        function parentBoneOf(frame) {
            if (frame === 'chest') return 'chest';
            if (frame.startsWith('bone:')) return frame.slice(5);
            if (frame === 'carry') return 'thigh_r';
            if (frame === 'view') return 'head';
            if (frame.startsWith('socket:') && S.rig.sockets[frame.slice(7)]) return S.rig.names[S.rig.sockets[frame.slice(7)].bone];
            return 'root';
        }

        /**
         * The frames a prop's segments may use: body frames, carry (a gun prop with a carry mount; def: its definition
         * before the prop is made), then the props listed before it.
         */
        function framesOf(c, id, def) {
            const rig = S.rig, out = ['chest', 'root', 'shoulder_r', 'shoulder_l', 'view'];
            const own = propsOf(c).find(p => p.id === id);
            if ((def && def.carry) || (own && own.carry)) out.push('carry');
            rig.names.forEach((n, b) => { if (n !== 'chest' && n !== 'root' && !core.isArm(rig, b)) out.push('bone:' + n); });
            Object.keys(rig.sockets).forEach(s => { if (!core.isArm(rig, rig.sockets[s].bone)) out.push('socket:' + s); });
            for (const p of propsOf(c)) {
                if (p.id === id) break;
                out.push('prop:' + p.id);
            }
            return out;
        }

        /** A player_props definition: main resources, else the test ones (the phase 2 test props). */
        function readPropDef(id) {
            return readJson(res('player_props/' + id + '.json')) || readJson(repo() + '/' + TEST_ASSETS + 'player_props/' + id + '.json');
        }

        /**
         * Creates a prop group from a player_props definition (a bounds box and one locator per point) and adds it to the
         * project's props; one with the same id is replaced in its place. frame: its first frame (the default of every
         * animation's first segment).
         */
        function createProp(id, def, frame, holdPoint) {
            const c = cfg();
            const at = propsOf(c).findIndex(p => p.id === id);
            const old = at >= 0 ? propGroupOf(c.props[at]) : null;
            if (old) old.remove(false);
            const hold = def.points[holdPoint] ? holdPoint : Object.keys(def.points)[0];
            const k = 16 * def.scale, place = restPlaceB(c, frame, def, hold);
            const D = v3.sub(place, v3.mul(def.points[hold], k));
            const parent = groupNamed(parentBoneOf(frame)) || groupNamed('root');
            const name = 'prop_' + id.replace(/[^a-zA-Z0-9_]/g, '_');
            const g = new Group({name, origin: place.slice()}).init();
            g.addTo(parent);
            if (def.bounds) {
                new Cube({name: 'bounds', from: v3.add(D, v3.mul(def.bounds[0], k)), to: v3.add(D, v3.mul(def.bounds[1], k)),
                    origin: place.slice(), color: 2}).addTo(g).init();
            }
            for (const p in def.points) new Locator({name: p, position: v3.add(D, v3.mul(def.points[p], k))}).addTo(g).init();
            const entry = {id, group: name, scale: def.scale, D, hold, frame, items: def.items || [id], symmetry: def.symmetry || null,
                open_block: def.open_block || null, about: def.about || ''};
            if (def.gun) Object.assign(entry, {gun: def.gun, grips: def.grips || null, carry: def.carry || null});
            if (def.mesh) entry.mesh = clone(def.mesh);
            if (at >= 0) c.props[at] = entry;
            else c.props.push(entry);
            if (def.gun) addGunLook(entry, g);
            if (def.mesh) addMeshLook(entry, g);
            Project.saved = false;
            Canvas.updateAll();
            return g;
        }

        /** fn(prop) for the only prop, or after picking one. */
        function withProp(title, fn) {
            const list = liveProps(cfg());
            if (!list.length) return Blockbench.showQuickMessage('先建道具', 2000);
            if (list.length === 1) return fn(list[0]);
            const options = {};
            list.forEach(p => { options[p.id] = p.id; });
            new Dialog({id: 'afl_rig_pick_prop', title, form: {id: {label: '道具', type: 'select', value: list[0].id, options}},
                onConfirm(r) { fn(list.find(p => p.id === r.id)); }}).show();
        }

        /** A gun prop's carry preview on the rest pose (Java rest px, its size there): ActionPose.carryPreview with identity bones. */
        function carryRest(def) {
            return {q: q4.norm(def.carry.quat), t: def.carry.pos.slice(), s: def.carry.scale};
        }

        /** client/MaintenanceGunRendering#copy: the bones (and their children) left out of a gun's bind pose, besides empty_old*. */
        const GUN_SKIPPED = ['reload_magazine', 'additional_magazine', 'lefthand', 'righthand', 'camera', 'view', 'positioning'];

        /**
         * A gun prop's look: the gun's own Vextris mesh (meshes/<asset>.aflmesh.json, what the game draws) in its bind pose
         * (GeoAnchorSampler's chain: pivot x and rotation x / y negated, Z-Y-X), as locked meshes under the prop group that
         * are not exported. Item units are the gun's bare GeckoLib blocks, so edit coords e = D + item * k, no mirroring.
         */
        function addGunLook(p, g) {
            g = g || propGroupOf(p);
            if (!g || !p.gun) return;
            const geoName = p.gun === 'p9_01' ? 'p9_01_v2_native' : p.gun;   // GunStance.Carried#asset
            const geo = readJson(res('geo/' + geoName + '.geo.json')), mesh = readJson(res('meshes/' + geoName + '.aflmesh.json'));
            if (!geo || !mesh) return Blockbench.showQuickMessage('没找到 ' + geoName + ' 的 geo 或 aflmesh', 2500);
            const bones = {};
            for (const b of geo['minecraft:geometry'][0].bones) bones[b.name] = b;
            const skipped = name => { for (let b = name; b; b = bones[b] && bones[b].parent) if (b.startsWith('empty_old') || GUN_SKIPPED.includes(b)) return true; return !bones[name]; };
            const frames = {};
            const frameOf = name => {
                if (frames[name]) return frames[name];
                const b = bones[name], pv = b.pivot || [0, 0, 0], r = b.rotation || [0, 0, 0];
                const pivot = [-pv[0] / 16, pv[1] / 16, pv[2] / 16];
                const parent = b.parent && bones[b.parent] ? frameOf(b.parent) : tf.id();
                const turn = q4.zyx(rad(r[2]), rad(-r[1]), rad(-r[0]));
                const own = tf.mul(tf.mul(tf.translation(pivot), {q: turn, t: [0, 0, 0]}), tf.translation(v3.mul(pivot, -1)));
                return (frames[name] = tf.mul(parent, own));
            };
            for (const old of g.children.filter(x => x instanceof Mesh && x.name.startsWith('gun_'))) old.remove();
            const k = 16 * p.scale;
            for (const part of mesh.parts) {
                if (skipped(part.bone)) continue;
                const pv = bones[part.bone].pivot || [0, 0, 0], at = tf.mul(frameOf(part.bone), tf.translation([-pv[0] / 16, pv[1] / 16, pv[2] / 16]));
                const el = new Mesh({name: 'gun_' + part.name, origin: g.origin.slice(), vertices: {}, color: 3, locked: true, export: false});
                const keys = el.addVertices(...part.vertices.map(v => v3.sub(v3.add(p.D, v3.mul(tf.apply(at, v.slice(0, 3)), k)), g.origin)));
                for (const face of part.faces) {
                    const polys = face.length <= 4 ? [face] : face.slice(1, -1).map((x, i) => [face[0], x, face[i + 2]]);   // bigger polygons as fans
                    for (const poly of polys) el.addFaces(new MeshFace(el, {vertices: poly.map(x => keys[x])}));
                }
                el.addTo(g).init();
            }
            Canvas.updateAll();
        }

        /**
         * A prop drawn as parts of an item's AFL Mesh (player_props "mesh", skeleton/action/PlayerProps.MeshLook; health V1a
         * 2026-10-19: the medical alcohol's bottle and its cap as two props): those parts of meshes/<model>.aflmesh.json where
         * the item renderer puts them (vertex v blocks -> item units (v.x + 0.5, v.y + offset, v.z + 0.5)), as locked meshes
         * under the prop group that are not exported. Edit coords e = D + item * k, as the points.
         */
        function addMeshLook(p, g) {
            g = g || propGroupOf(p);
            if (!g || !p.mesh) return;
            const json = readJson(res('meshes/' + p.mesh.model + '.aflmesh.json'));
            if (!json) return Blockbench.showQuickMessage('没找到 meshes/' + p.mesh.model + '.aflmesh.json', 2500);
            for (const old of g.children.filter(x => x instanceof Mesh && x.name.startsWith('look_'))) old.remove();
            const k = 16 * p.scale, off = p.mesh.offset || 0;
            const colors = {};
            p.mesh.parts.forEach((n, i) => { colors[n] = [0, 4, 3, 5, 6, 7][i % 6]; });
            for (const part of json.parts) {
                if (!p.mesh.parts.includes(part.name)) continue;
                const el = new Mesh({name: 'look_' + part.name, origin: g.origin.slice(), vertices: {}, color: colors[part.name], locked: true, export: false});
                const keys = el.addVertices(...part.vertices.map(v => v3.sub(v3.add(p.D, v3.mul([v[0] + 0.5, v[1] + off, v[2] + 0.5], k)), g.origin)));
                for (const face of part.faces) {
                    const polys = face.length <= 4 ? [face] : face.slice(1, -1).map((x, i) => [face[0], x, face[i + 2]]);   // bigger polygons as fans
                    for (const poly of polys) el.addFaces(new MeshFace(el, {vertices: poly.map(x => keys[x])}));
                }
                el.addTo(g).init();
            }
            Canvas.updateAll();
        }

        /** Merges a .bbmodel's look into a prop group, scaled to the prop (item px + offset, scale). */
        function mergePropLook(file, offset, p) {
            const g = propGroupOf(p);
            if (!g) return Blockbench.showQuickMessage('先建道具', 2000);
            const beforeNodes = new Set([...Outliner.elements, ...Group.all]), beforeAnims = new Set(Animation.all);
            Codecs.project.merge(JSON.parse(file.content), file.path);
            Animation.all.filter(a => !beforeAnims.has(a)).forEach(a => a.remove(false));
            const s = p.scale, D = p.D;
            const map = v => v3.add(D, v3.mul(v3.add(v, offset), s));
            const newGroups = Group.all.filter(x => !beforeNodes.has(x)), newEls = Outliner.elements.filter(x => !beforeNodes.has(x));
            const ANCHORS = {right_hand_anchor: 'grip_r', left_hand_anchor: 'grip_l'};
            for (const x of newGroups) {
                if (ANCHORS[x.name] && !pointEdit(g, ANCHORS[x.name])) new Locator({name: ANCHORS[x.name], position: map(x.origin)}).addTo(g).init();
                x.origin = map(x.origin);
            }
            for (const el of newEls) {
                if (el instanceof Cube) {
                    el.from = map(el.from);
                    el.to = map(el.to);
                    el.origin = map(el.origin);
                    el.inflate = (el.inflate || 0) * s;
                } else if (el instanceof Locator || el instanceof NullObject) {
                    el.position = map(el.position);
                } else if (el.vertices && el.origin) {
                    el.origin = map(el.origin);
                    for (const key in el.vertices) el.vertices[key] = v3.mul(el.vertices[key], s);
                }
            }
            for (const x of [...newGroups, ...newEls]) if (!beforeNodes.has(x.parent) && !(x.parent instanceof Group && newGroups.includes(x.parent))) x.addTo(g);
            Canvas.updateAll();
            Blockbench.showQuickMessage('外观已并入道具（动画已丢弃）', 2500);
        }

        // ------------------------------------------------ Blockbench animation -> runtime action

        const kfValues = (k, i) => ['x', 'y', 'z'].map(a => core.numberOf(k.calc(a, i)));
        const rotToFile = v => [-v[0], -v[1], v[2]];
        const posToFile = v => [-v[0], v[1], v[2]];
        const rotFromFile = rotToFile, posFromFile = posToFile;
        const sorted = kfs => (kfs || []).slice().sort((a, b) => a.time - b.time);
        const interpMode = k => k.interpolation === 'catmullrom' ? 1 : k.interpolation === 'step' ? 2 : 0;

        function bbChannel(kfs, conv) {
            const s = sorted(kfs);
            return new core.Channel(s.map(k => k.time), s.map(k => conv(kfValues(k, 0))), s.map(k => conv(kfValues(k, k.data_points.length - 1))),
                s.map(interpMode));
        }

        /** The animation's keyframes on the rig bones as a runtime clip (the exported file's numbers), or null. */
        function clipOf(anim) {
            const clip = new core.Clip(anim.name, anim.length, anim.loop === 'loop');
            let any = false;
            S.rig.names.forEach((name, b) => {
                const g = groupNamed(name), an = g && anim.animators[g.uuid];
                if (!an) return;
                if (an.rotation && an.rotation.length) { clip.rot[b] = bbChannel(an.rotation, rotToFile); any = true; }
                if (an.position && an.position.length) { clip.pos[b] = bbChannel(an.position, posToFile); any = true; }
            });
            return any ? clip : null;
        }

        /** Blockbench numbers of a channel at t (linear between keys; the prop's own blend is the runtime's). */
        function sampleBB(kfs, t) {
            const s = sorted(kfs);
            if (!s.length) return [0, 0, 0];
            if (t <= s[0].time) return kfValues(s[0], 0);
            const last = s[s.length - 1];
            if (t >= last.time) return kfValues(last, last.data_points.length - 1);
            let b = 0;
            while (s[b].time < t) b++;
            if (Math.abs(s[b].time - t) < 1e-6) return kfValues(s[b], s[b].data_points.length - 1);
            const a = s[b - 1], f = (t - a.time) / (s[b].time - a.time);
            return v3.lerp(kfValues(a, a.data_points.length - 1), kfValues(s[b], 0), f);
        }

        const r4 = x => Math.round(x * 1e4) / 1e4 || 0;

        /**
         * Prop p's group keyframes as a runtime prop track, or null when the animation does not use the prop (no keyframes,
         * no segments set), or {error}. One key per keyframe time and segment start, eased from the one before (key i: at
         * t(i-1), blend t(i) - t(i-1)); its frame, point and hidden are the segment's at its start t(i-1). In a prop: frame
         * the Blockbench numbers place the prop as if the earlier prop stayed where it was (on the rest pose) when that
         * frame began; built: the tracks made so far (the earlier props).
         */
        function propTrackOf(anim, a, p, built) {
            const g = propGroupOf(p);
            if (!g) return null;
            const an = anim.animators[g.uuid], set = trackSet(a, p), segs = set.segments;
            const rk = (an && an.rotation) || [], pk = (an && an.position) || [];
            const own = a.props[p.id] && a.props[p.id].segments && a.props[p.id].segments.length;
            if (!rk.length && !pk.length && !own) return null;
            const times = [...new Set([...rk, ...pk].map(k => r4(k.time)).concat(segs.slice(1).map(s => r4(s.at))))].sort((x, y) => x - y);
            if (!times.length) times.push(0);
            const keys = [];
            for (let i = 0; i < times.length; i++) {
                const t = times[i], start = i === 0 ? 0 : times[i - 1], seg = segmentAt(segs, start);
                let F;
                if (seg.frame.startsWith('prop:')) {
                    const parent = built.findIndex(x => x.id === seg.frame.slice(5));
                    if (parent < 0) return {error: p.id + ' 挂在 ' + seg.frame.slice(5) + ' 上，但那个道具不在这个动作里（没有关键帧）或排在它后面'};
                    F = core.restPlacement(S.rig, {props: built}, parent, frameSince(segs, start));
                    if (!F) return {error: p.id + '：' + seg.frame.slice(5) + ' 在 ' + r4(frameSince(segs, start)) + ' 秒没有位置'};
                }
                const ePoint = pointEdit(g, seg.point) || g.origin.slice();
                // carry: the gun in its mount as the body's carried-weapons layer draws it (its keyframes only time it)
                const key = seg.frame === 'carry' ? {pos: [0, 0, 0], rot: [0, 0, 0]}
                    : core.bbToPropKey(S.rig, sampleBB(rk, t), sampleBB(pk, t), g.origin, ePoint, seg.frame, F);
                keys.push({at: start, frame: seg.frame, point: seg.point, pos: key.pos, rot: key.rot, blend: i === 0 ? 0 : r4(t - start), hidden: !!seg.hidden});
            }
            return {id: p.id, def: propDef(p), keys, openFrom: set.open_from !== undefined ? set.open_from : NaN};
        }

        /** The animation's prop tracks in the project's prop order, and what kept any out. */
        function propTracksOf(anim, a) {
            const built = [], problems = [];
            for (const p of liveProps(cfg())) {
                const t = propTrackOf(anim, a, p, built);
                if (t && t.error) problems.push(t.error);
                else if (t) built.push(t);
            }
            return {tracks: built, problems};
        }

        const uiKey = k => ({at: k.at || 0, target: k.target || 'pose', blend: k.blend !== undefined ? k.blend : 0.2, roll: k.roll || 0,
            pole: k.pole ? k.pole.join(' ') : ''});

        function keyOfUi(k) {
            const key = {at: Number(k.at) || 0, target: String(k.target || 'pose'), blend: Number(k.blend) || 0, roll: Number(k.roll) || 0};
            const pole = String(k.pole || '').trim().split(/[\s,]+/).filter(x => x).map(Number);
            if (pole.length === 3 && pole.every(Number.isFinite)) key.pole = pole;
            return key;
        }

        function actionOf(anim) {
            const a = aflOf(anim);
            const runtimeKey = k => ({at: k.at, target: k.target, blend: k.blend, roll: k.roll, pole: k.pole || null});
            const {tracks, problems} = propTracksOf(anim, a);
            return {
                mask: a.mask, blendIn: a.blend_in, blendOut: a.blend_out, clip: clipOf(anim), paramClip: null,
                right: a.hands.right.map(runtimeKey), left: a.hands.left.map(runtimeKey), props: tracks, problems,
                viewPitch: a.view_pitch || null, chestTwist: a.chest_twist || 0, holdBody: !!(a.extra && a.extra.hold_body),
                holdHead: !!(a.extra && a.extra.hold_head),
            };
        }

        // ------------------------------------------------ display

        function setMesh(mesh, m) {
            const s = tf.scaleOf(m);
            mesh.position.set(m.t[0], m.t[1], m.t[2]);
            mesh.quaternion.set(m.q[0], m.q[1], m.q[2], m.q[3]);
            mesh.scale.set(s, s, s);   // a gun at its carry mount is drawn larger there
        }

        /**
         * Writes the solved pose into the group meshes. A group's mesh frame holds its children relative to its origin G,
         * so its model-space frame is X = A T(G) with A = J2B * W * J2B the posed map of rest Blockbench px.
         */
        function writeDisplay(res, action) {
            const X = new Map();
            S.rig.names.forEach((name, b) => {
                const g = groupNamed(name);
                if (!g || !g.mesh) return;
                const A = tf.mul(tf.mul(J2B, res.pose.world[b]), J2B), Xg = tf.mul(A, tf.translation(g.origin));
                X.set(g, Xg);
                const parent = g.parent instanceof Group ? X.get(g.parent) : null;
                setMesh(g.mesh, parent ? tf.mul(tf.inv(parent), Xg) : Xg);
            });
            for (const p of liveProps(cfg())) {
                const pg = propGroupOf(p);
                if (!pg.mesh) continue;
                const i = action ? core.propIndex(action, p.id) : -1;
                const placed = i >= 0 && res.props ? res.props[i] : null;
                if (i < 0) {
                    pg.mesh.visible = pg.visibility !== false;   // not in this action: Blockbench's own display
                } else if (!placed) {
                    pg.mesh.visible = false;   // the action does not show yet (blend-in): the game draws no prop
                } else {
                    const P = {q: RX180, t: v3.mul(q4.apply(RX180, p.D), -1)};
                    const Xp = tf.mul(tf.mul(tf.mul(J2B, placed), P), tf.translation(pg.origin));
                    const parent = pg.parent instanceof Group ? X.get(pg.parent) : null;
                    setMesh(pg.mesh, parent ? tf.mul(tf.inv(parent), Xp) : Xp);
                    pg.mesh.visible = pg.visibility !== false && !res.hidden[i];   // a hidden key: the game does not draw it
                }
            }
            // the own first person's narrower arms (GunStance#firstPersonArm): each arm box's x scaled about its middle
            const k = res.arms || 1;
            for (const name of S.rig.names) {
                if (!/^(upper_arm|forearm|hand)_[rl]$/.test(name)) continue;
                const g = groupNamed(name);
                if (!g) continue;
                for (const cu of g.children) {
                    if (!(cu instanceof Cube) || !cu.mesh || (k === 1 && !cu.mesh.userData.aflThin)) continue;
                    Canvas.adaptObjectPosition(cu);   // its own place (and scale 1), then thinned
                    cu.mesh.userData.aflThin = k !== 1;
                    cu.mesh.scale.set(k, 1, 1);
                    const mid = new THREE.Vector3(((cu.from[0] + cu.to[0]) / 2 - cu.origin[0]) * (1 - k), 0, 0)
                        .applyQuaternion(cu.mesh.quaternion);
                    cu.mesh.position.add(mid);
                }
            }
            if (Project.model_3d) Project.model_3d.updateMatrixWorld(true);
        }

        function applyFirstPerson(on, c) {
            const pv = typeof Preview !== 'undefined' ? Preview.selected : null, head = groupNamed('head');
            if (!pv || !pv.camera || !pv.controls || pv.isOrtho) return;
            if (!on) {
                if (S.fpSaved) {
                    pv.camera.fov = S.fpSaved.fov;
                    pv.camera.near = S.fpSaved.near;
                    pv.camera.position.copy(S.fpSaved.pos);
                    pv.controls.target.copy(S.fpSaved.target);
                    pv.camera.updateProjectionMatrix();
                    S.fpSaved = null;
                }
                if (head && head.mesh) head.mesh.visible = true;
                return;
            }
            if (!S.fpSaved) S.fpSaved = {fov: pv.camera.fov, near: pv.camera.near, pos: pv.camera.position.clone(), target: pv.controls.target.clone()};
            const eye = j2b([0, core.eyeModelY(c.crouch), 0]), p = rad(c.view_pitch || 0);
            const ahead = v3.add(eye, [0, -Math.sin(p), -Math.cos(p)]);
            const toWorld = v => (Project.model_3d ? Project.model_3d.localToWorld(new THREE.Vector3(v[0], v[1], v[2])) : new THREE.Vector3(v[0], v[1], v[2]));
            pv.camera.position.copy(toWorld(eye));
            pv.controls.target.copy(toWorld(ahead));
            pv.camera.fov = 70;
            pv.camera.near = 0.5;
            pv.camera.updateProjectionMatrix();
            pv.camera.lookAt(pv.controls.target);
            if (head && head.mesh) head.mesh.visible = false;
        }

        /** PlayerAction#viewAt: the animation's first_person_view [[s, deg], ...] at t, smooth-stepped; NaN without one. */
        function viewAt(anim, t) {
            const v = anim.afl && anim.afl.extra && anim.afl.extra.first_person_view;
            if (!Array.isArray(v) || !v.length) return NaN;
            if (t <= v[0][0]) return v[0][1];
            for (let i = 1; i < v.length; i++) {
                if (t > v[i][0]) continue;
                const u = (t - v[i - 1][0]) / (v[i][0] - v[i - 1][0]), s = u * u * (3 - 2 * u);
                return v[i - 1][1] + (v[i][1] - v[i - 1][1]) * s;
            }
            return v[v.length - 1][1];
        }

        const fix1 = x => (x === undefined || x === null ? '' : Number(x).toFixed(Math.abs(x) < 10 ? 2 : 0));

        function onFrame() {
            const c = cfg();
            if (!c || !Modes.animate) return;
            try {
                ensureCore();
                const anim = Animation.selected;
                const action = anim ? actionOf(anim) : null;
                const t = Timeline.time;
                // an action with its own view (first_person_view): the look follows it, as PlayerAction#viewAt in game
                const keyed = anim ? viewAt(anim, t) : NaN;
                const view = Number.isFinite(keyed) ? Object.assign({}, c, {view_pitch: keyed}) : c;
                const solved = core.solveFrame(S.poser, {slim: c.slim, crouch: c.crouch, pitch: view.view_pitch, rifle: c.rifle,
                    time: c.breathing ? t : 0, action, seconds: t, fp: !!c.first_person});
                writeDisplay(solved, action);
                applyFirstPerson(!!c.first_person, view);
                UI.problems = action ? action.problems.join('；') : '';
                const read = core.readouts(solved);
                for (const side of ['right', 'left']) {
                    const r = read[side];
                    UI.read[side] = r.mode !== 'ik' ? {text: '片段手臂（没有 IK 目标）', bad: false} : {
                        text: '离目标 ' + fix1(r.off) + ' px · 伸 ' + fix1(r.reach) + ' · 肘 ' + Math.round(r.elbow) + '° · 腕 '
                            + Math.round(r.wrist) + '° · 扭 ' + Math.round(r.twist) + '°' + (r.w < 0.99 ? ' · IK ' + fix1(r.w) : ''),
                        bad: r.wrist > 50 || r.twist > 30 || (r.w >= 0.99 && r.off > 0.6),
                    };
                }
                if (UI.anim !== (anim ? anim.name : '')) refreshUi();
            } catch (e) {
                console.error('[afl_rig]', e);
                UI.status = '出错：' + e.message;
            }
        }

        // ------------------------------------------------ panel state

        function refreshUi() {
            const c = cfg();
            UI.project = !!c;
            if (!c) return;
            ensureCore();
            UI.status = S.err || ('骨架 ' + S.rig.size + ' 块骨头 · 走路片段已载入 · 仓库 ' + repo());
            UI.view = {pitch: c.view_pitch || 0, fp: !!c.first_person, crouch: !!c.crouch, breathing: !!c.breathing, rifle: !!c.rifle, slim: !!c.slim};
            const props = liveProps(c).map(p => ({id: p.id, frame: p.frame, hold: p.hold, frames: framesOf(c, p.id),
                points: propGroupOf(p).children.filter(x => x instanceof Locator).map(x => x.name), segments: []}));
            UI.targets = ['pose', ...Object.keys(S.rig.sockets).map(s => 'socket:' + s),
                ...[].concat(...props.map(p => p.points.map(x => 'prop:' + p.id + ':' + x)))];
            const anim = typeof Animation !== 'undefined' ? Animation.selected : null;
            UI.anim = anim ? anim.name : '';
            if (anim) {
                const a = aflOf(anim);
                UI.action = {action_id: a.action_id, clip_key: a.clip_key, mask: a.mask, layer: a.layer, priority: a.priority,
                    first_person: a.first_person, blend_in: a.blend_in, blend_out: a.blend_out, loop: a.loop || 'none', hip_fire: !!a.hip_fire,
                    view_up: a.view_pitch ? a.view_pitch[0] : '', view_down: a.view_pitch ? a.view_pitch[1] : '', chest_twist: a.chest_twist || 0,
                    about: a.about};
                UI.hands = {right: a.hands.right.map(uiKey), left: a.hands.left.map(uiKey)};
                props.forEach(u => { u.segments = trackSet(a, liveProps(c).find(p => p.id === u.id)).segments; });
                UI.problems = actionOf(anim).problems.join('；');
            }
            UI.props = props;
        }

        /**
         * The panel's segments as the animation's props settings (open_from kept). A prop's default single segment is not
         * stored unless it was: stored segments put a prop without keyframes into the action.
         */
        function segmentsOfUi(a) {
            for (const u of UI.props) {
                const segs = u.segments.map(s => ({at: r4(Math.max(0, Number(s.at) || 0)), frame: String(s.frame), point: String(s.point),
                    hidden: !!s.hidden})).sort((x, y) => x.at - y.at);
                if (segs.length) segs[0].at = 0;
                const old = a.props[u.id] || {};
                const plain = segs.length === 1 && segs[0].frame === u.frame && segs[0].point === u.hold && !segs[0].hidden;
                if (!segs.length || (plain && !(old.segments && old.segments.length))) continue;
                a.props[u.id] = {segments: segs};
                if (old.open_from !== undefined) a.props[u.id].open_from = old.open_from;
            }
        }

        /**
         * A new attach segment of prop id from the timeline's time, the frame it was on carried over (the panel then
         * changes it); the prop's keyframes are pinned there so its curve stays as it was.
         */
        function addSegment(id) {
            const anim = Animation.selected, p = liveProps(cfg()).find(x => x.id === id);
            if (!anim || !p) return;
            const t = r4(Timeline.time);
            if (t <= 0) return Blockbench.showQuickMessage('把时间轴移到换挂点的时刻（0 秒的挂点改第一行）', 2500);
            const a = aflOf(anim), set = trackSet(a, p);
            if (set.segments.some(s => Math.abs(s.at - t) < 1e-4)) return Blockbench.showQuickMessage('这个时间已经有一段了', 2000);
            Undo.initEdit({animations: [anim]});
            const an = anim.getBoneAnimator(propGroupOf(p));
            for (const channel of ['rotation', 'position']) {
                const kfs = an[channel] || [];
                if (kfs.some(k => Math.abs(k.time - t) < 1e-4)) continue;
                const v = sampleBB(kfs, t);
                an.addKeyframe({channel, time: t, interpolation: 'linear', data_points: [{x: r4(v[0]), y: r4(v[1]), z: r4(v[2])}]});
            }
            const cur = segmentAt(set.segments, t);
            set.segments.push({at: t, frame: cur.frame, point: cur.point, hidden: cur.hidden});
            a.props[id] = {segments: set.segments.sort((x, y) => x.at - y.at)};
            if (set.open_from !== undefined) a.props[id].open_from = set.open_from;
            anim.afl = a;
            Undo.finishEdit('AFL 换挂点');
            refreshUi();
            Animator.preview();
        }

        function commitUi() {
            const anim = Animation.selected;
            if (!anim) return;
            Undo.initEdit({animations: [anim]});
            const a = aflOf(anim), u = UI.action;
            segmentsOfUi(a);
            a.action_id = String(u.action_id || '').trim();
            a.clip_key = String(u.clip_key || '').trim();
            a.mask = u.mask;
            a.layer = u.layer;
            a.priority = Math.round(Number(u.priority) || 0);
            a.first_person = u.first_person;
            a.blend_in = Math.max(0, Number(u.blend_in) || 0);
            a.blend_out = Math.max(0, Number(u.blend_out) || 0);
            a.loop = UI.loops.includes(u.loop) ? u.loop : 'none';
            if (a.loop !== 'none') anim.loop = 'loop';   // a stance loop (docs/dev/p9_stance_loops_v1.md) plays round in the preview too
            a.hip_fire = a.loop !== 'none' && !!u.hip_fire;
            // how far the view frame follows the look's pitch: both limits, up above down, or none
            const vu = parseFloat(u.view_up), vd = parseFloat(u.view_down);
            a.view_pitch = Number.isFinite(vu) && Number.isFinite(vd) && vu < vd ? [vu, vd] : null;
            a.chest_twist = a.loop !== 'none' ? Number(u.chest_twist) || 0 : 0;
            a.about = String(u.about || '');
            const byTime = (x, y) => x.at - y.at;
            a.hands = {right: UI.hands.right.map(keyOfUi).sort(byTime), left: UI.hands.left.map(keyOfUi).sort(byTime)};
            anim.afl = a;
            Undo.finishEdit('AFL 动作设置');
            refreshUi();
            Animator.preview();
        }

        function saveView() {
            const c = cfg();
            if (!c) return;
            const slimChanged = !!c.slim !== !!UI.view.slim;
            c.view_pitch = Number(UI.view.pitch) || 0;
            c.first_person = !!UI.view.fp;
            c.crouch = !!UI.view.crouch;
            c.breathing = !!UI.view.breathing;
            c.rifle = !!UI.view.rifle;
            c.slim = !!UI.view.slim;
            if (slimChanged) rebuildSkin(c.slim);
            if (!c.first_person) applyFirstPerson(false, c);
            Animator.preview();
        }

        // ------------------------------------------------ export

        /** JSON with short arrays and small objects on one line (the hand-written files' look). */
        function pretty(value, indent) {
            indent = indent || '';
            const inner = indent + '  ';
            const flat = v => v === null || typeof v !== 'object' || (Array.isArray(v) && v.every(x => x === null || typeof x !== 'object'));
            const one = JSON.stringify(value);
            if (flat(value) || (!Array.isArray(value) && Object.values(value).every(flat) && one.length < 150)) {
                return one.replace(/,(?=["\[{\-\d])/g, ', ').replace(/":/g, '": ');
            }
            if (Array.isArray(value)) return '[\n' + value.map(v => inner + pretty(v, inner)).join(',\n') + '\n' + indent + ']';
            return '{\n' + Object.keys(value).filter(k => value[k] !== undefined)
                .map(k => inner + JSON.stringify(k) + ': ' + pretty(value[k], inner)).join(',\n') + '\n' + indent + '}';
        }

        const timeKey = t => { const x = r4(t); return Number.isInteger(x) ? x.toFixed(1) : String(x); };

        function exportChannel(kfs, conv, warn) {
            const out = {};
            for (const k of sorted(kfs)) {
                const n = k.data_points.length, post = conv(kfValues(k, n - 1)).map(r4);
                if (k.interpolation === 'bezier') warn.push('贝塞尔插值导出成线性（运行时不支持贝塞尔）');
                ['x', 'y', 'z'].forEach(a => { for (let i = 0; i < n; i++) if (typeof k.get(a, i) === 'string' && !Number.isFinite(Number(k.get(a, i)))) warn.push('molang 表达式在运行时读成 0'); });
                let value;
                if (n > 1) value = {pre: conv(kfValues(k, 0)).map(r4), post};
                else value = post;
                if (k.interpolation === 'catmullrom' || k.interpolation === 'step') value = Object.assign(Array.isArray(value) ? {post: value} : value, {lerp_mode: k.interpolation});
                out[timeKey(k.time)] = value;
            }
            return out;
        }

        function compileClip(anim, warn) {
            const out = {animation_length: r4(anim.length)};
            if (anim.loop === 'loop') out.loop = true;
            else if (anim.loop === 'hold') out.loop = 'hold_on_last_frame';
            out.bones = {};
            for (const name of S.rig.names) {
                const g = groupNamed(name), an = g && anim.animators[g.uuid];
                if (!an) continue;
                const b = {};
                if (an.rotation && an.rotation.length) b.rotation = exportChannel(an.rotation, rotToFile, warn);
                if (an.position && an.position.length) b.position = exportChannel(an.position, posToFile, warn);
                if (b.rotation || b.position) out.bones[name] = b;
            }
            return Object.keys(out.bones).length ? out : null;
        }

        function writeFiles(files, done) {
            const fs = nodeFs();
            const lines = files.map(f => '- ' + f.path.replace(repo() + '/', '') + (f.exists ? (f.owned ? '（覆盖，插件写的）' : '（覆盖：这个文件不是插件写的）') : '（新文件）'));
            Blockbench.showMessageBox({
                title: '写入 AFL 文件', icon: 'save',
                message: '仓库：' + repo() + '\n\n' + lines.join('\n') + (files.warn && files.warn.length ? '\n\n注意：\n' + [...new Set(files.warn)].map(w => '- ' + w).join('\n') : ''),
                buttons: ['写入', '取消'], confirm: 0, cancel: 1,
            }, button => {
                if (button !== 0) return;
                for (const f of files) {
                    fs.mkdirSync(f.path.replace(/\/[^/]+$/, ''), {recursive: true});
                    fs.writeFileSync(f.path, f.text + '\n', 'utf8');
                }
                Blockbench.showQuickMessage('已写入 ' + files.length + ' 个文件', 2500);
                if (done) done();
            });
        }

        /** dry: return the files instead of asking to write them. */
        function exportAction(dry) {
            const anim = Animation.selected, c = cfg();
            if (!anim || !c) return;
            dry = dry === true;
            const refuse = message => (dry ? {error: message} : Blockbench.showMessageBox({title: 'AFL', message}));
            ensureCore();
            const a = aflOf(anim);
            if (!/^[a-z0-9_]+\/[a-z0-9_]+$/.test(a.action_id)) return refuse('先填动作 ID，格式 group/name（小写字母、数字、下划线）');
            const [group, name] = a.action_id.split('/');
            const clipKey = a.clip_key || group + '.' + name;
            if (!/^[a-z0-9_]+\.[a-z0-9_.]+$/.test(clipKey)) return refuse('片段键格式 group.name');
            const warn = [], files = [];
            const clip = compileClip(anim, warn);
            if (clip) {
                const clipGroup = clipKey.split('.')[0], path = res('animations/player/' + clipGroup + '.animation.json');
                const existing = readJson(path);
                if (existing && !existing.afl_rig) {
                    return refuse('片段文件 animations/player/' + clipGroup + '.animation.json 是脚本或手写的，插件不改它。'
                            + '换一个片段组名（片段键 ' + clipGroup + '.xxx 的前半段），比如 ' + clipGroup + '_bb。');
                }
                const file = existing || {format_version: '1.8.0', afl_rig: {about: 'Written by the AFL Rig Blockbench plugin (tools/blockbench/afl_rig.js). Edit in Blockbench, not by hand.'}, animations: {}};
                file.animations[clipKey] = clip;
                files.push({path, text: pretty(file), exists: !!existing, owned: true});
            }
            const actionPath = res('player_actions/' + group + '/' + name + '.json');
            const old = readJson(actionPath);
            const x = a.extra || {};
            const json = {format_version: 1};
            if (a.about) json.about = a.about;
            json.afl_rig = {plugin: VERSION, animation: anim.name};
            if (clip) json.clip = clipKey;
            if (x.param_clip) json.param_clip = x.param_clip;
            Object.assign(json, {layer: a.layer, mask: a.mask, priority: a.priority, first_person: a.first_person, blend: {in: a.blend_in, out: a.blend_out}});
            // the own first person's view eased down to at least this pitch while it runs (PlayerAction#firstPersonPitch)
            if (x.first_person_pitch !== undefined) json.first_person_pitch = x.first_person_pitch;
            // the own first-person camera on the posed eye (PlayerAction#firstPersonEye: the splint on a leg, sat down)
            if (x.first_person_eye) json.first_person_eye = true;
            // the clip alone turns and bends the chest (PlayerAction#holdBody: arms keyed to the body by FK)
            if (x.hold_body) json.hold_body = true;
            // the clip's head, not the look (PlayerAction#holdHead: wrapping the head)
            if (x.hold_head) json.hold_head = true;
            // the own view's pitch keyed over the action (PlayerAction#firstPersonView: falling over on a landing)
            if (x.first_person_view) json.first_person_view = x.first_person_view;
            // a gun's stance loop: by the clock or the stride, its time within the animation's length
            if (a.loop && a.loop !== 'none') Object.assign(json, {loop: a.loop, length: r4(anim.length)});
            if (a.loop && a.loop !== 'none' && a.hip_fire) json.hip_fire = true;   // the idle is the hip-fire stance: a shot leaves it
            if (a.view_pitch) json.view_pitch = a.view_pitch;
            if (a.chest_twist) json.chest_twist = a.chest_twist;   // a stance loop's bladed body
            if (x.approach) json.approach = x.approach;
            const {tracks, problems} = propTracksOf(anim, a);
            if (problems.length) return refuse('道具：' + problems.join('；'));
            const out = tracks.map(t => {
                const o = {id: t.id, keys: t.keys.map(k => {
                    const j = {at: r4(k.at), frame: k.frame, point: k.point, pos: k.pos.map(r4), rot: k.rot.map(r4), blend: r4(k.blend)};
                    if (k.hidden) j.hidden = true;
                    return j;
                })};
                if (!Number.isNaN(t.openFrom)) o.open_from = t.openFrom;
                return o;
            });
            for (const r of (x.props_raw || []).slice().sort((p, q) => p.index - q.index)) {
                out.splice(Math.min(r.index, out.length), 0, r.json);
                warn.push(r.json.id + ' 的关键帧是原样写回的（锚点坐标系插件不能编辑）');
            }
            // one prop as "prop" (the phase 1 form), more as "props" in order: one rides only on an earlier one
            if (out.length === 1) json.prop = out[0];
            else if (out.length) json.props = out;
            if (x.view_anchors) json.view_anchors = x.view_anchors;
            const first = out.length ? out[0].id : null;
            const target = t => {
                const p = core.parseTarget(t), c2 = p.name.indexOf(':');
                if (p.kind !== 'prop' || c2 < 0) return t;
                const id = p.name.slice(0, c2);
                if (!out.some(o => o.id === id)) warn.push('手的目标 ' + t + '：这个道具不在动作里');
                return id === first ? 'prop:' + p.name.slice(c2 + 1) : t;   // the first prop's points in the short form
            };
            const hand = k => { const o = {at: r4(k.at), target: target(k.target), blend: r4(k.blend), roll: r4(k.roll)}; if (k.pole) o.pole = k.pole; return o; };
            json.hands = {right: a.hands.right.map(hand), left: a.hands.left.map(hand)};
            json.events = x.events || [];
            if (x.check) json.check = x.check;
            files.unshift({path: actionPath, text: pretty(json), exists: !!old, owned: !!(old && old.afl_rig)});
            files.warn = warn;
            if (dry) return files;
            writeFiles(files);
        }

        /** Prop p's player_props definition (dry: return the files instead of asking to write them). */
        function exportProp(dry, p) {
            dry = dry === true;
            const g = propGroupOf(p);
            if (!g) return Blockbench.showQuickMessage('没有道具', 2000);
            if (p.gun) {
                const message = '枪道具 player_props/' + p.id + '.json 由 ./gradlew gunPropExport 从枪的模型生成，不从插件导出';
                return dry ? {error: message} : Blockbench.showMessageBox({title: 'AFL', message});
            }
            const k = 16 * p.scale, def = propDef(p);
            const box = g.children.find(x => x instanceof Cube && x.name === 'bounds');
            const json = {};
            if (p.about) json.about = p.about;
            json.afl_rig = {plugin: VERSION};
            json.items = p.items;
            json.scale = p.scale;
            if (box) json.bounds = [v3.mul(v3.sub(box.from, p.D), 1 / k).map(r4), v3.mul(v3.sub(box.to, p.D), 1 / k).map(r4)];
            if (p.symmetry) json.symmetry = p.symmetry;
            if (p.open_block) json.open_block = p.open_block;
            if (p.mesh) json.mesh = p.mesh;
            json.points = {};
            for (const n in def.points) json.points[n] = def.points[n].map(r4);
            const path = res('player_props/' + p.id + '.json'), old = readJson(path);
            const files = [{path, text: pretty(json), exists: !!old, owned: !!(old && old.afl_rig)}];
            files.warn = [];
            if (dry) return files;
            writeFiles(files);
        }

        // ------------------------------------------------ import

        function idFromPath(path) {
            const m = path.replace(/\\/g, '/').match(/player_actions\/([^/]+)\/([^/]+)\.json$/);
            return m ? m[1] + '/' + m[2] : 'imported/' + path.replace(/\\/g, '/').split('/').pop().replace(/\.json$/, '');
        }

        function addKeys(animator, channel, json, fromFile) {
            if (json === undefined) return 0;
            const ch = core.channelFromJson(json);
            ch.times.forEach((t, i) => {
                const pre = fromFile(ch.pre[i]), post = fromFile(ch.post[i]);
                const same = pre.every((v, k) => Math.abs(v - post[k]) < 1e-6);
                const dp = v => ({x: r4(v[0]), y: r4(v[1]), z: r4(v[2])});
                animator.addKeyframe({channel, time: t, interpolation: ['linear', 'catmullrom', 'step'][ch.mode[i]],
                    data_points: same ? [dp(post)] : [dp(pre), dp(post)]});
            });
            return ch.times.length ? ch.times[ch.times.length - 1] : 0;
        }

        /**
         * One prop track of an action file as the prop group's keyframes and the animation's segments (the inverse of
         * propTrackOf). Each key's keyframe is where it arrives (at + blend, kept after the one before by at least 0.1 ms);
         * a key starting later than the one before arrived (by 5 ms or more) gets a hold keyframe at its start. A key's
         * segment starts at the keyframe before its own, as the export reads it back. Returns the last keyframe time.
         */
        function importTrack(anim, a, p, t, jt, notes) {
            const g = propGroupOf(p), keys = t.keys.map(core.propKeyOf), puts = [], segs = [];
            let lastT = 0, prev = null;
            keys.forEach((k, i) => {
                let start = lastT;
                if (i > 0 && k.at - lastT > 0.005) {
                    puts.push({time: r4(k.at), key: prev, start: lastT});
                    start = lastT = r4(k.at);
                }
                if (i > 0 && k.at + k.blend < lastT - 1e-6) notes.push(t.id + ' 的关键帧 ' + i + ' 在上一帧到位前开始，导入后时间会变');
                const arrival = i === 0 ? r4(k.at) : r4(Math.max(k.at + k.blend, lastT + 1e-4));
                puts.push({time: arrival, key: k, start: i === 0 ? 0 : start});
                const seg = segs[segs.length - 1];
                if (!seg || seg.frame !== k.frame || seg.point !== k.point || seg.hidden !== k.hidden) {
                    segs.push({at: i === 0 ? 0 : start, frame: k.frame, point: k.point, hidden: k.hidden});
                }
                lastT = arrival;
                prev = k;
            });
            const an = anim.getBoneAnimator(g);
            for (const u of puts) {
                let F;
                if (u.key.frame.startsWith('prop:')) {
                    F = core.restPlacement(S.rig, {props: jt}, core.propIndex({props: jt}, u.key.frame.slice(5)), frameSince(segs, u.start));
                    if (!F) {
                        notes.push(t.id + '：' + u.key.frame.slice(5) + ' 那时没有位置，关键帧跳过');
                        continue;
                    }
                }
                const ePoint = pointEdit(g, u.key.point);
                if (!ePoint) notes.push(t.id + ' 没有点 ' + u.key.point);
                let key = u.key;
                if (key.frame === 'carry') {
                    // shown where the mount puts the key's point, turned as the mount (its size is the display's)
                    const def = propDef(p), C = carryRest(def), local = core.propLocal(def, key.point);
                    const turn = q4.xyz(rad(key.rot[0]), rad(key.rot[1]), rad(key.rot[2]));
                    const placed = tf.mul(C, {q: turn, t: v3.sub(v3.add(local, key.pos), q4.apply(turn, local))});
                    key = {frame: 'root', point: key.point, pos: tf.apply(placed, local), rot: q4.eulerXYZ(placed.q).map(deg)};
                    F = tf.id();
                }
                const bb = core.propKeyToBB(S.rig, key, g.origin, ePoint || g.origin.slice(), F);
                an.addKeyframe({channel: 'rotation', time: u.time, interpolation: 'linear', data_points: [{x: r4(bb.rot[0]), y: r4(bb.rot[1]), z: r4(bb.rot[2])}]});
                an.addKeyframe({channel: 'position', time: u.time, interpolation: 'linear', data_points: [{x: r4(bb.delta[0]), y: r4(bb.delta[1]), z: r4(bb.delta[2])}]});
            }
            a.props[p.id] = {segments: segs};
            if (t.open_from !== undefined) a.props[p.id].open_from = t.open_from;
            return puts.length ? puts[puts.length - 1].time : 0;
        }

        function importAction(file) {
            const c = cfg();
            if (!c) return;
            ensureCore();
            const json = JSON.parse(file.content), id = idFromPath(file.path);
            const notes = [];
            // the tracks the plugin can show: a definition found, no anchor frame, not riding on one it cannot show
            const tracksJson = core.propsOfJson(json), raw = new Set(), defs = {};
            for (const t of tracksJson) {
                const frames = t.keys.map(k => String(k.frame || 'chest'));
                if (frames.some(f => f.startsWith('anchor:'))) {
                    notes.push(t.id + ' 用锚点坐标系，插件里只能原样保留，不能显示');
                    raw.add(t.id);
                } else if (frames.some(f => f.startsWith('prop:') && (raw.has(f.slice(5)) || !tracksJson.some(o => o.id === f.slice(5))))) {
                    notes.push(t.id + ' 挂在插件不能显示的道具上，原样保留');
                    raw.add(t.id);
                } else if (!(defs[t.id] = readPropDef(t.id))) {
                    notes.push('没找到道具定义 player_props/' + t.id + '.json，原样保留');
                    raw.add(t.id);
                }
            }
            const shown = tracksJson.filter(t => !raw.has(t.id));
            for (const t of shown) {
                const have = propsOf(c).find(p => p.id === t.id);
                if (!have || !propGroupOf(have)) createProp(t.id, defs[t.id], t.keys[0].frame || 'chest', t.keys[0].point);
            }
            // the action's props first, in its order: one rides only on an earlier one
            const ids = shown.map(t => t.id);
            c.props = [...ids.map(x => c.props.find(p => p.id === x)), ...c.props.filter(p => !ids.includes(p.id))];
            let clipJson = null;
            if (json.clip) {
                const file2 = readJson(res('animations/player/' + json.clip.split('.')[0] + '.animation.json'));
                clipJson = file2 && file2.animations ? file2.animations[json.clip] : null;
                if (!clipJson) notes.push('没找到片段 ' + json.clip);
            }
            const clipLength = clipJson && clipJson.animation_length ? clipJson.animation_length : 0;
            const anim = new Animation({name: json.clip || id.replace('/', '.'), loop: clipJson && clipJson.loop === true ? 'loop' : 'once',
                length: clipLength || 1}).add(true);
            let length = clipLength;
            Undo.initEdit({animations: [anim]});
            if (clipJson) for (const bone in (clipJson.bones || {})) {
                const g = groupNamed(bone);
                if (!g || S.rig.find(bone) < 0) continue;
                const an = anim.getBoneAnimator(g);
                length = Math.max(length, addKeys(an, 'rotation', clipJson.bones[bone].rotation, rotFromFile));
                length = Math.max(length, addKeys(an, 'position', clipJson.bones[bone].position, posFromFile));
            }
            const a = AFL_DEFAULT();
            a.action_id = id;
            a.clip_key = json.clip && json.clip !== id.replace('/', '.') ? json.clip : '';
            a.mask = json.mask || 'upper';
            a.layer = json.layer || 'action';
            a.priority = json.priority !== undefined ? json.priority : 50;
            a.first_person = json.first_person || 'body';
            a.blend_in = json.blend && json.blend.in !== undefined ? json.blend.in : 0.25;
            a.blend_out = json.blend && json.blend.out !== undefined ? json.blend.out : 0.25;
            a.loop = json.loop === 'time' || json.loop === 'stride' ? json.loop : 'none';
            if (a.loop !== 'none') anim.loop = 'loop';
            if (a.loop !== 'none' && json.length > 0) length = Math.max(length, json.length);   // a loop's length is its own
            a.hip_fire = a.loop !== 'none' && json.hip_fire === true;
            a.view_pitch = Array.isArray(json.view_pitch) ? json.view_pitch.slice(0, 2) : null;
            a.chest_twist = a.loop !== 'none' ? json.chest_twist || 0 : 0;
            a.about = json.about || '';
            const first = tracksJson.length ? tracksJson[0].id : null;
            const hk = k => ({at: k.at || 0, target: fullTarget(k.target, first), blend: k.blend !== undefined ? k.blend : 0.2, roll: k.roll || 0, pole: k.pole});
            a.hands = {right: ((json.hands || {}).right || []).map(hk), left: ((json.hands || {}).left || []).map(hk)};
            for (const side of ['right', 'left']) for (const k of a.hands[side]) length = Math.max(length, k.at + k.blend);
            for (const f of ['param_clip', 'approach', 'view_anchors', 'events', 'check', 'first_person_pitch', 'first_person_eye', 'hold_body', 'hold_head', 'first_person_view']) if (json[f] !== undefined) a.extra[f] = json[f];
            const rawList = tracksJson.map((t, i) => ({index: i, json: t})).filter(r => raw.has(r.json.id));
            if (rawList.length) a.extra.props_raw = rawList;
            // the file's tracks as the runtime reads them (the project's points): where a prop: frame's earlier prop is
            const jt = tracksJson.map(t => ({id: t.id, def: raw.has(t.id) ? null : propDef(c.props.find(p => p.id === t.id)),
                keys: t.keys.map(core.propKeyOf), openFrom: NaN}));
            for (const t of shown) length = Math.max(length, importTrack(anim, a, c.props.find(p => p.id === t.id), t, jt, notes));
            anim.afl = a;
            anim.setLength(r4(length) || 1);
            Undo.finishEdit('AFL 导入动作');
            anim.select();
            refreshUi();
            Animator.preview();
            Blockbench.showQuickMessage('已导入 ' + id + (notes.length ? '（' + notes.join('；') + '）' : ''), 5000);
        }

        // ------------------------------------------------ dialogs

        function propDialog(file) {
            const def = JSON.parse(file.content);
            const id = file.path.replace(/\\/g, '/').split('/').pop().replace(/\.json$/, '');
            const points = {}, frames = {};
            Object.keys(def.points || {}).forEach(p => { points[p] = p; });
            const LABELS = {chest: 'chest（跟胸口）', root: 'root（跟身体根）', shoulder_r: 'shoulder_r（右肩，按根转）',
                shoulder_l: 'shoulder_l（左肩，按根转）', view: 'view（跟视线：眼睛前方，随抬头低头转）', 'socket:holster': 'socket:holster（右大腿枪套口）', carry: 'carry（枪在枪套里，和身上画的一样）'};
            framesOf(cfg(), id, def).forEach(f => { frames[f] = LABELS[f] || f; });
            new Dialog({
                id: 'afl_rig_prop', title: '从道具定义建道具：' + id,
                form: {
                    frame: {label: '开始时挂在', type: 'select', value: def.carry ? 'carry' : 'chest', options: frames},
                    hold: {label: '握点（旋转支点）', type: 'select', value: points.grip ? 'grip' : Object.keys(points)[0], options: points},
                },
                onConfirm(r) { createProp(id, def, r.frame, r.hold); refreshUi(); Animator.preview(); },
            }).show();
        }

        function settingsDialog() {
            const c = cfg();
            if (!c) return;
            new Dialog({
                id: 'afl_rig_settings', title: 'AFL 骨架设置',
                form: {repo: {label: '仓库路径（worktree 根目录）', type: 'text', value: c.repo || DEFAULT_REPO}},
                onConfirm(r) { c.repo = r.repo; S.poser = null; ensureCore(); refreshUi(); Animator.preview(); },
            }).show();
        }

        const pick = (type, ext, start, cb) => Blockbench.import({resource_id: 'afl_rig_' + ext, extensions: [ext], type, readtype: 'text', startpath: start}, files => files[0] && cb(files[0]));

        // ------------------------------------------------ register

        Plugin.register('afl_rig', {
            title: 'AFL Rig',
            author: 'AFL',
            icon: 'accessibility_new',
            description: 'AFL player skeleton: the game\'s rig, IK and action format in Blockbench.',
            version: VERSION,
            variant: 'desktop',
            min_version: '5.0.0',
            onload() {
                S.disposables.push(new Property(ModelProject, 'object', 'afl_rig'));
                S.disposables.push(new Property(Animation, 'object', 'afl'));
                const need = () => !!cfg();
                const acts = [
                    new Action('afl_rig_new_project', {name: 'AFL：新建人物动画工程', icon: 'accessibility_new', click: newRigProject}),
                    new Action('afl_rig_prop_from_def', {name: 'AFL：从道具定义建道具…', icon: 'construction', condition: need,
                        click: () => pick('AFL 道具定义', 'json', res('player_props'), propDialog)}),
                    new Action('afl_rig_prop_look', {name: 'AFL：把模型并入道具外观…', icon: 'merge_type', condition: need,
                        click: () => withProp('并入哪个道具', p => pick('Blockbench 模型', 'bbmodel', repo() + '/src/main/blockbench', f => mergePropLook(f, [0, 0, 0], p)))}),
                    new Action('afl_rig_gun_look', {name: 'AFL：重建枪道具外观（游戏网格）', icon: 'view_in_ar', condition: () => need() && liveProps(cfg()).some(p => p.gun),
                        click: () => withProp('哪个枪道具', p => addGunLook(p))}),
                    new Action('afl_rig_import_action', {name: 'AFL：导入动作 JSON…', icon: 'file_download', condition: need,
                        click: () => pick('AFL 动作', 'json', res('player_actions'), importAction)}),
                    new Action('afl_rig_export_action', {name: 'AFL：导出当前动作', icon: 'file_upload', condition: () => need() && !!Animation.selected, click: exportAction}),
                    new Action('afl_rig_export_prop', {name: 'AFL：导出道具定义', icon: 'file_upload', condition: () => need() && liveProps(cfg()).length > 0,
                        click: () => withProp('导出哪个道具', p => exportProp(false, p))}),
                    new Action('afl_rig_holster_ref', {name: 'AFL：加枪套参考模型', icon: 'view_in_ar', condition: () => need() && !groupNamed(HOLSTER_REF),
                        click: () => { if (addHolsterRef()) Blockbench.showQuickMessage('枪套参考模型挂在 thigh_r 下（锁定，不导出）', 2500); }}),
                    new Action('afl_rig_settings', {name: 'AFL：设置仓库路径', icon: 'settings', condition: need, click: settingsDialog}),
                ];
                S.disposables.push(...acts);
                const menu = new Action('afl_rig_menu', {name: 'AFL 骨架', icon: 'accessibility_new', children: acts, click() {}});
                S.disposables.push(menu);
                MenuBar.addAction(menu, 'tools');
                S.disposables.push(Blockbench.addCSS(`
                    .afl_rig { padding: 4px 8px 10px; font-size: 13px; }
                    .afl_rig .afl_h { margin: 8px 0 3px; font-weight: bold; display: flex; align-items: center; gap: 6px; }
                    .afl_rig .afl_r { display: flex; align-items: center; gap: 6px; margin: 2px 0; flex-wrap: wrap; }
                    .afl_rig .afl_l { min-width: 52px; opacity: .8; }
                    .afl_rig input, .afl_rig select { background: var(--color-back); border: 1px solid var(--color-border); color: var(--color-text); padding: 1px 3px; }
                    .afl_rig input[type=number] { width: 54px; }
                    .afl_rig table { border-collapse: collapse; width: 100%; }
                    .afl_rig td, .afl_rig th { padding: 1px 2px; font-weight: normal; opacity: .95; text-align: left; }
                    .afl_rig td input[type=number] { width: 46px; }
                    .afl_rig td input.afl_pole { width: 70px; }
                    .afl_rig .afl_note { opacity: .7; }
                    .afl_rig .afl_read { font-size: 12px; opacity: .85; margin: 2px 0 4px; }
                    .afl_rig .afl_bad { color: var(--color-close); opacity: 1; }
                    .afl_rig button { margin: 0; padding: 0 6px; min-width: 0; height: 22px; }
                `));
                S.disposables.push(new Panel('afl_rig_panel', {
                    name: 'AFL 骨架', icon: 'accessibility_new', growable: true, resizable: true,
                    condition: {modes: ['animate']},
                    default_position: {slot: 'right_bar', float_position: [0, 0], float_size: [360, 520], height: 520, folded: false},
                    component: {
                        data() { return UI; },
                        methods: {
                            onView() { this.$nextTick(saveView); },
                            commit() { this.$nextTick(commitUi); },
                            addKey(side) {
                                const list = this.hands[side], last = list[list.length - 1];
                                list.push({at: r4(Timeline.time), target: last ? last.target : 'pose', blend: 0.2, roll: 0, pole: ''});
                                this.$nextTick(commitUi);
                            },
                            removeKey(side, i) { this.hands[side].splice(i, 1); this.$nextTick(commitUi); },
                            targetsFor(current) { return this.targets.includes(current) ? this.targets : [current, ...this.targets]; },
                            framesFor(p, current) { return p.frames.includes(current) ? p.frames : [current, ...p.frames]; },
                            addSegment(id) { this.$nextTick(() => addSegment(id)); },
                            removeSegment(p, i) { p.segments.splice(i, 1); this.$nextTick(commitUi); },
                            exportAction() { exportAction(); },
                            importAction() { pick('AFL 动作', 'json', res('player_actions'), importAction); },
                            newProject() { newRigProject(); },
                        },
                        template: `
<div class="afl_rig">
  <div v-if="!project" class="afl_note">当前不是 AFL 人物动画工程。<button @click="newProject">新建人物动画工程</button></div>
  <div v-else>
    <div class="afl_note">{{ status }}</div>
    <div class="afl_h">视角</div>
    <div class="afl_r">俯仰 <input type="range" min="-60" max="60" step="1" v-model.number="view.pitch" @change="onView()" style="flex:1"> {{ view.pitch }}°</div>
    <div class="afl_r">
      <label><input type="checkbox" v-model="view.fp" @change="onView()"> 第一人称</label>
      <label><input type="checkbox" v-model="view.crouch" @change="onView()"> 下蹲</label>
      <label><input type="checkbox" v-model="view.breathing" @change="onView()"> 呼吸</label>
      <label><input type="checkbox" v-model="view.rifle" @change="onView()"> 步枪胸口</label>
      <label><input type="checkbox" v-model="view.slim" @change="onView()"> 细手臂</label>
    </div>
    <div v-if="!anim" class="afl_note">在动画面板里选一个动画（或 导入动作）。<button @click="importAction">导入动作</button></div>
    <div v-else>
      <div class="afl_h">动作 · {{ anim }}</div>
      <div class="afl_r"><span class="afl_l">动作 ID</span><input v-model.lazy="action.action_id" @change="commit()" placeholder="group/name" style="flex:1"></div>
      <div class="afl_r"><span class="afl_l">片段键</span><input v-model.lazy="action.clip_key" @change="commit()" placeholder="空 = 按动作 ID" style="flex:1"></div>
      <div class="afl_r"><span class="afl_l">遮罩</span><select v-model="action.mask" @change="commit()"><option v-for="o in masks" :value="o">{{ o }}</option></select>
        <span>层</span><select v-model="action.layer" @change="commit()"><option v-for="o in layers" :value="o">{{ o }}</option></select>
        <span>优先</span><input type="number" v-model.lazy.number="action.priority" @change="commit()"></div>
      <div class="afl_r"><span class="afl_l">淡入</span><input type="number" step="0.05" min="0" v-model.lazy.number="action.blend_in" @change="commit()">
        <span>淡出</span><input type="number" step="0.05" min="0" v-model.lazy.number="action.blend_out" @change="commit()">
        <span>第一人称</span><select v-model="action.first_person" @change="commit()"><option value="body">body</option><option value="view_model">view_model</option></select></div>
      <div class="afl_r"><span class="afl_l">循环</span><select v-model="action.loop" @change="commit()" title="枪的姿势循环：none 一次；time 按时间（待机）；stride 按步伐（冲刺）。长度 = 动画长度"><option v-for="o in loops" :value="o">{{ o }}</option></select>
        <label v-if="action.loop !== 'none'" title="待机就是腰射姿势（枪已经在准星前）：开枪不再把枪抬到眼前"><input type="checkbox" v-model="action.hip_fire" @change="commit()"> 腰射</label>
        <span v-if="action.loop !== 'none'" title="胸口转到视线右边多少度（侧身站，左肩在前）；举枪瞄准时回正">侧身</span><input v-if="action.loop !== 'none'" type="number" step="1" v-model.lazy.number="action.chest_twist" @change="commit()"></div>
      <div class="afl_r" title="挂在 view（跟视线）上的道具跟着抬头低头到多少度为止（负 = 抬头），快到时慢慢停住；空 = 一直跟。预览改上面的视线俯仰看"><span class="afl_l">视线俯仰</span>
        <span>上</span><input type="number" step="5" v-model.lazy="action.view_up" @change="commit()" placeholder="不限">
        <span>下</span><input type="number" step="5" v-model.lazy="action.view_down" @change="commit()" placeholder="不限"></div>
      <div class="afl_r"><span class="afl_l">说明</span><input v-model.lazy="action.about" @change="commit()" style="flex:1"></div>
      <div v-for="p in props" :key="'prop_' + p.id">
        <div class="afl_h">道具 · {{ p.id }} <button @click="addSegment(p.id)" title="从当前时间起换一个挂点（在这里钉住道具的关键帧）">+ 当前时间 换挂点</button></div>
        <table>
          <tr><th>从</th><th>挂在</th><th>点</th><th>藏</th><th></th></tr>
          <tr v-for="(s, i) in p.segments" :key="p.id + i">
            <td><input type="number" step="0.01" min="0" v-model.lazy.number="s.at" @change="commit()" :disabled="i === 0"></td>
            <td><select v-model="s.frame" @change="commit()"><option v-for="f in framesFor(p, s.frame)" :value="f">{{ f }}</option></select></td>
            <td><select v-model="s.point" @change="commit()"><option v-for="x in p.points" :value="x">{{ x }}</option></select></td>
            <td><input type="checkbox" v-model="s.hidden" @change="commit()" title="这段时间游戏里不画它"></td>
            <td><button v-if="i > 0" @click="removeSegment(p, i)" title="删掉">×</button></td>
          </tr>
        </table>
      </div>
      <div v-if="problems" class="afl_read afl_bad">{{ problems }}</div>
      <div v-for="side in sides" :key="side">
        <div class="afl_h">{{ side === 'right' ? '右手' : '左手' }} <button @click="addKey(side)" title="在当前时间加一个目标">+ 当前时间</button></div>
        <table v-if="hands[side].length">
          <tr><th>时间</th><th>目标</th><th>过渡</th><th>转</th><th>肘向</th><th></th></tr>
          <tr v-for="(k, i) in hands[side]" :key="side + i">
            <td><input type="number" step="0.01" min="0" v-model.lazy.number="k.at" @change="commit()"></td>
            <td><select v-model="k.target" @change="commit()"><option v-for="t in targetsFor(k.target)" :value="t">{{ t }}</option></select></td>
            <td><input type="number" step="0.01" min="0" v-model.lazy.number="k.blend" @change="commit()"></td>
            <td><input type="number" step="5" v-model.lazy.number="k.roll" @change="commit()"></td>
            <td><input class="afl_pole" v-model.lazy="k.pole" @change="commit()" placeholder="默认"></td>
            <td><button @click="removeKey(side, i)" title="删掉">×</button></td>
          </tr>
        </table>
        <div v-if="read[side]" class="afl_read" :class="{afl_bad: read[side].bad}">{{ read[side].text }}</div>
      </div>
      <div class="afl_r" style="margin-top:8px"><button @click="exportAction">导出动作</button><button @click="importAction">导入动作</button></div>
    </div>
  </div>
</div>`,
                    },
                }));
                // scripting (MCP, the console): Plugins.registered.afl_rig.api
                this.api = {
                    core,
                    state: () => ({repo: S.repo, err: S.err, loco: S.loco, ui: clone(UI)}),
                    importAction: path => importAction({path, content: nodeFs().readFileSync(path, 'utf8')}),
                    createProp: (id, frame, hold) => { createProp(id, readPropDef(id), frame, hold); refreshUi(); },
                    /** Merges a .bbmodel into prop id's look (as the menu item; offset: item px of the file's origin). */
                    mergeLook: (path, offset, id) => mergePropLook({path, content: nodeFs().readFileSync(path, 'utf8')}, offset || [0, 0, 0], liveProps(cfg()).find(p => p.id === id)),
                    /** The selected animation's prop tracks as the export writes them (unrounded), and what kept any out. */
                    propTracks: () => {
                        const anim = Animation.selected;
                        if (!anim) return null;
                        const r = propTracksOf(anim, aflOf(anim));
                        return {tracks: r.tracks.map(t => ({id: t.id, keys: t.keys, openFrom: t.openFrom})), problems: r.problems};
                    },
                    /** Sets prop id's segments on the selected animation ([{at, frame, point, hidden}]). */
                    setSegments: (id, segments) => {
                        const anim = Animation.selected;
                        if (!anim) return;
                        const a = aflOf(anim);
                        a.props[id] = Object.assign(a.props[id] || {}, {segments: clone(segments)});
                        anim.afl = a;
                        refreshUi();
                        Animator.preview();
                    },
                    addSegment, holster: addHolsterRef,
                    meshLook: id => addMeshLook(liveProps(cfg()).find(p => p.id === id)),
                    solve: seconds => {
                        const c = cfg(), anim = Animation.selected;
                        const r = core.solveFrame(S.poser, {slim: c.slim, crouch: c.crouch, pitch: c.view_pitch, rifle: c.rifle,
                            action: anim ? actionOf(anim) : null, seconds, fp: !!c.first_person});
                        return {readouts: core.readouts(r), world: r.pose.world, prop: r.prop, props: r.props, hidden: r.hidden};
                    },
                    exportAction, exportProp: (dry, id) => exportProp(dry, liveProps(cfg()).find(p => p.id === id)), refresh: refreshUi,
                };
                const on = (event, fn) => { Blockbench.on(event, fn); S.listeners.push([event, fn]); };
                on('display_animation_frame', onFrame);
                on('select_project', () => { applyFirstPerson(false, cfg() || {}); refreshUi(); });
                on('select_mode', () => { if (!Modes.animate) applyFirstPerson(false, cfg() || {}); refreshUi(); });
                refreshUi();
            },
            onunload() {
                for (const [event, fn] of S.listeners) Blockbench.removeListener(event, fn);
                S.listeners = [];
                try { MenuBar.removeAction('tools.afl_rig_menu'); } catch (e) { /* not in the menu */ }
                try { applyFirstPerson(false, cfg() || {}); } catch (e) { /* the preview may be gone */ }
                for (const d of S.disposables.reverse()) {
                    try { d.delete(); } catch (e) { console.warn('[afl_rig] unload', e); }
                }
                S.disposables = [];
            },
        });
    }
})();
