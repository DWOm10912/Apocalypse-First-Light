package com.antaurora.apofirstlight.skeleton.ragdoll;

import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Ragdoll V1 (docs/dev/ragdoll_v1_plan.md 2): one falling body, the rig's eleven boxes as rigid bodies solved with
 * extended position-based dynamics (small substeps, one pass each): joints hold the boxes together and keep them in their
 * limits, the boxes' surface points are pushed out of the world's solid boxes with friction, and limbs are kept out of the
 * trunk and out of each other. Gravity 9.81 m/s^2 (1 block = 1 m). It sleeps once still on the ground, or at the latest
 * after {@link #MAX_TICKS} ticks or {@link #GROUND_TICKS} ticks after it first touched something.
 *
 * <p>World blocks, a body's orientation maps its model axes (Java model px: y down, front -z) to the world. Pure: no
 * Minecraft classes, so the server and the offline check run the same code.
 */
public final class RagdollBody {
    public static final double GRAVITY = 9.81, TICK = 0.05;
    public static final int SUBSTEPS = 10;
    /** Hard stops: ticks in all, ticks after the first touch. */
    public static final int MAX_TICKS = 240, GROUND_TICKS = 160;
    /**
     * Asleep once, touching something, no box has moved more than {@link #QUIET_MOVE} blocks or turned more than
     * {@link #QUIET_TURN} radians in a tick for {@link #QUIET_TICKS} ticks in a row. A lying body of boxes never quite
     * stops by itself (the 8 px head and the 4 px chest cannot both lie flat and stay joined, so the head rocks on an
     * edge), and a real body stops within a second or two of landing: this ends it there.
     */
    public static final int QUIET_TICKS = 8;
    public static final double QUIET_MOVE = 0.01, QUIET_TURN = Math.toRadians(3);
    /** Landed this many ticks ago and slower than SETTLE_SPEED: extra damping (1/s), the body's own give. */
    private static final int SETTLE_AFTER = 10;
    private static final double SETTLE_SPEED = 1.0, SETTLE_DAMPING = 8;
    private static final double STATIC_FRICTION = 0.9, DYNAMIC_FRICTION = 0.7;
    private static final double LINEAR_DAMPING = 0.05, ANGULAR_DAMPING = 1.0;
    /** Caps: blocks per second, radians per second, blocks pushed out per substep. */
    private static final double MAX_SPEED = 40, MAX_SPIN = 25, MAX_PUSH = 0.01;
    /** The world is read this far around the body each tick (blocks), plus its travel. */
    private static final double MARGIN = 0.3;

    public final RagdollDefinition def;
    private final int n;
    private final Vector3d[] x, v, w, px;
    private final Quaterniond[] q, pq;
    private final double[] invMass;
    private final Vector3d[] invInertia;
    /** Per body: its surface points and inset points as offsets from its centre (metres, body axes); bounding radius. */
    private final Vector3d[][] points, inner;
    private final double[] radius;
    private final List<double[]> boxes = new ArrayList<>();
    private final List<Contact> contacts = new ArrayList<>();

    private int ticks, quiet, touched = -1;
    private boolean settling;
    /** The places the quiet count started from. */
    private final Vector3d[] restX;
    private final Quaterniond[] restQ;
    private boolean asleep;

    private record Contact(int body, Vector3d local, Vector3d normal, double depth) {}

    public RagdollBody(RagdollDefinition def) {
        this.def = def;
        n = def.bodies.length;
        x = new Vector3d[n];
        v = new Vector3d[n];
        w = new Vector3d[n];
        px = new Vector3d[n];
        q = new Quaterniond[n];
        pq = new Quaterniond[n];
        invMass = new double[n];
        invInertia = new Vector3d[n];
        points = new Vector3d[n][];
        inner = new Vector3d[n][];
        radius = new double[n];
        restX = new Vector3d[n];
        restQ = new Quaterniond[n];
        for (int i = 0; i < n; i++) {
            restX[i] = new Vector3d();
            restQ[i] = new Quaterniond();
            RagdollDefinition.Body b = def.bodies[i];
            x[i] = new Vector3d();
            v[i] = new Vector3d();
            w[i] = new Vector3d();
            px[i] = new Vector3d();
            q[i] = new Quaterniond();
            pq[i] = new Quaterniond();
            invMass[i] = 1.0 / b.mass();
            invInertia[i] = new Vector3d(1.0 / b.inertia().x, 1.0 / b.inertia().y, 1.0 / b.inertia().z);
            points[i] = offsets(b.points(), b.centre());
            inner[i] = offsets(b.inner(), b.centre());
            double hx = b.size(0) / 2, hy = b.size(1) / 2, hz = b.size(2) / 2;
            radius[i] = Math.sqrt(hx * hx + hy * hy + hz * hz) * RagdollDefinition.PX;
        }
    }

    private static Vector3d[] offsets(Vector3f[] pts, Vector3f centre) {
        Vector3d[] out = new Vector3d[pts.length];
        for (int k = 0; k < pts.length; k++)
            out[k] = new Vector3d(pts[k].x - centre.x, pts[k].y - centre.y, pts[k].z - centre.z).mul(RagdollDefinition.PX);
        return out;
    }

    // ---------------------------------------------------------------- frames

    /** The rotation a living body at this yaw draws its model with (LivingEntityRenderer: Y by 180 - yaw, then z by 180). */
    public static Quaterniond facing(double yawDeg) {
        return axis(0, 1, 0, Math.toRadians(180 - yawDeg)).mul(axis(0, 0, 1, Math.PI));
    }

    /** Model px of the render origin's height: the model's y at the feet' level is 24, the origin sits 1.501 blocks up. */
    public static final double ORIGIN_Y = 1.501 / RagdollDefinition.PX * RagdollDefinition.SCALE;

    /** A model px point (rest or posed) of a body standing at {@code origin} facing {@code yaw} to the world. */
    public static Vector3d world(Vector3f model, Vector3d origin, double yawDeg, Vector3d out) {
        out.set(model.x, model.y - ORIGIN_Y, model.z).mul(RagdollDefinition.PX);
        facing(yawDeg).transform(out);
        return out.add(origin);
    }

    // ---------------------------------------------------------------- start

    /**
     * Starts from a posed skeleton (model px, SkeletonPose#world) standing at {@code origin} facing {@code yawDeg}, every
     * box moving at {@code velocity} (blocks per second).
     */
    public void start(SkeletonPose pose, Vector3d origin, double yawDeg, Vector3d velocity) {
        Quaterniond face = facing(yawDeg);
        Quaternionf r = new Quaternionf();
        for (int i = 0; i < n; i++) {
            RagdollDefinition.Body b = def.bodies[i];
            Matrix4f m = pose.world[b.bone()];
            world(m.transformPosition(new Vector3f(b.centre())), origin, yawDeg, x[i]);
            m.getNormalizedRotation(r);
            q[i].set(face).mul(new Quaterniond(r.x, r.y, r.z, r.w)).normalize();
            v[i].set(velocity);
            w[i].zero();
        }
        ticks = 0;
        quiet = 0;
        touched = -1;
        asleep = false;
        settling = false;
        for (int i = 0; i < n; i++) {
            restX[i].set(x[i]);
            restQ[i].set(q[i]);
        }
    }

    /** Pushes one box at a world point (an impulse in kg m/s). */
    public void impulse(int body, Vector3d point, Vector3d impulse) {
        v[body].fma(invMass[body], impulse);
        Vector3d r = new Vector3d(point).sub(x[body]);
        w[body].add(invInertiaWorld(body, r.cross(impulse)));
        asleep = false;
        quiet = 0;
    }

    /** The box whose centre is nearest a world point. */
    public int nearest(Vector3d point) {
        int best = 0;
        double d = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double e = x[i].distanceSquared(point);
            if (e < d) {
                d = e;
                best = i;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- step

    /** One game tick. */
    public void tick(RagdollWorld world) {
        if (asleep) return;
        gather(world);
        double h = TICK / SUBSTEPS;
        boolean touch = false;
        for (int s = 0; s < SUBSTEPS; s++) touch |= substep(h);
        ticks++;
        if (touch && touched < 0) touched = ticks;
        boolean still = touch;
        double speed = 0;
        for (int i = 0; i < n; i++) {
            if (x[i].distance(restX[i]) >= QUIET_MOVE || angle(q[i], restQ[i]) >= QUIET_TURN) still = false;
            restX[i].set(x[i]);
            restQ[i].set(q[i]);
            speed = Math.max(speed, v[i].length());
        }
        quiet = still ? quiet + 1 : 0;
        settling = touched >= 0 && ticks - touched >= SETTLE_AFTER && speed < SETTLE_SPEED;
        if (quiet >= QUIET_TICKS || ticks >= MAX_TICKS || touched >= 0 && ticks - touched >= GROUND_TICKS) sleep();
    }

    public void sleep() {
        asleep = true;
        for (int i = 0; i < n; i++) {
            v[i].zero();
            w[i].zero();
        }
    }

    public boolean asleep() { return asleep; }

    public int ticks() { return ticks; }

    /** The tick it first touched something, or -1. */
    public int touched() { return touched; }

    private void gather(RagdollWorld world) {
        double minX = 1e30, minY = 1e30, minZ = 1e30, maxX = -1e30, maxY = -1e30, maxZ = -1e30;
        for (int i = 0; i < n; i++) {
            double r = radius[i] + MARGIN + v[i].length() * TICK;
            minX = Math.min(minX, x[i].x - r); maxX = Math.max(maxX, x[i].x + r);
            minY = Math.min(minY, x[i].y - r); maxY = Math.max(maxY, x[i].y + r);
            minZ = Math.min(minZ, x[i].z - r); maxZ = Math.max(maxZ, x[i].z + r);
        }
        boxes.clear();
        world.boxes(minX, minY, minZ, maxX, maxY, maxZ, boxes);
    }

    private boolean substep(double h) {
        for (int i = 0; i < n; i++) {
            px[i].set(x[i]);
            pq[i].set(q[i]);
            v[i].y -= GRAVITY * h;
            x[i].fma(h, v[i]);
            rotate(q[i], w[i], h);
        }
        for (int i = 1; i < n; i++) limit(i);
        for (int i = 1; i < n; i++) join(i);
        for (int[] pair : def.selfPairs) separate(pair[0], pair[1]);
        contacts.clear();
        for (int i = 0; i < n; i++) collide(i, h);
        // a second joint pass: the contacts moved the boxes, the joints win the last word (a wedged body stays whole)
        for (int i = 1; i < n; i++) join(i);
        for (int i = 0; i < n; i++) {
            v[i].set(x[i]).sub(px[i]).div(h);
            Quaterniond d = new Quaterniond(q[i]).mul(new Quaterniond(pq[i]).conjugate());
            w[i].set(d.x, d.y, d.z).mul(2 / h);
            if (d.w < 0) w[i].negate();
        }
        for (Contact c : contacts) friction(c, h);
        for (int i = 0; i < n; i++) {
            double extra = settling ? SETTLE_DAMPING : 0;
            v[i].mul(Math.max(0, 1 - (LINEAR_DAMPING + extra) * h));
            w[i].mul(Math.max(0, 1 - (ANGULAR_DAMPING + extra) * h));
            // caps: a body wedged between walls must not wind up (conflicting pushes turn into spin)
            double sp = v[i].length(), sn = w[i].length();
            if (sp > MAX_SPEED) v[i].mul(MAX_SPEED / sp);
            if (sn > MAX_SPIN) w[i].mul(MAX_SPIN / sn);
        }
        return !contacts.isEmpty();
    }

    // ---------------------------------------------------------------- joints

    /** Holds a body's joint point on its parent's. */
    private void join(int i) {
        RagdollDefinition.Body b = def.bodies[i];
        int p = b.parent();
        Vector3d r1 = anchor(p, b.anchor()), r2 = anchor(i, b.anchor());
        Vector3d d = beyondSlack(i, new Vector3d(x[i]).add(r2).sub(x[p]).sub(r1));
        double c = d.length();
        if (c < 1e-9) return;
        pair(p, r1, i, r2, d.div(c), c);
    }

    /** The part of a joint's drift {@code d} (world) past the body's slack, measured along the parent's axes. */
    private Vector3d beyondSlack(int i, Vector3d d) {
        Vector3f s = def.bodies[i].slack();
        if (s.x == 0 && s.y == 0 && s.z == 0) return d;
        Quaterniond qp = q[def.bodies[i].parent()];
        Vector3d local = new Quaterniond(qp).conjugate().transform(new Vector3d(d)).div(RagdollDefinition.PX);
        local.set(over(local.x, s.x), over(local.y, s.y), over(local.z, s.z));
        return qp.transform(local.mul(RagdollDefinition.PX));
    }

    private static double over(double v, double slack) {
        return v > slack ? v - slack : v < -slack ? v + slack : 0;
    }

    /** A model px point of body i as an offset from its centre (world axes). */
    private Vector3d anchor(int i, Vector3f model) {
        Vector3f c = def.bodies[i].centre();
        return q[i].transform(new Vector3d(model.x - c.x, model.y - c.y, model.z - c.z).mul(RagdollDefinition.PX));
    }

    /** Turns a body back inside its joint's limits (relative to its parent). */
    private void limit(int i) {
        RagdollDefinition.Body b = def.bodies[i];
        int p = b.parent();
        Quaterniond rel = new Quaterniond(q[p]).conjugate().mul(q[i]);
        Quaterniond target = clamp(b, rel);
        if (target == null) return;
        // the world turn that takes the child to the target: q_p * target * q_c^-1
        Quaterniond turn = new Quaterniond(q[p]).mul(target).mul(new Quaterniond(q[i]).conjugate());
        if (turn.w < 0) turn.set(-turn.x, -turn.y, -turn.z, -turn.w);
        double s = Math.sqrt(turn.x * turn.x + turn.y * turn.y + turn.z * turn.z);
        if (s < 1e-9) return;
        double angle = 2 * Math.atan2(s, turn.w);
        Vector3d axis = new Vector3d(turn.x, turn.y, turn.z).div(s);
        double w1 = axis.dot(invInertiaWorld(p, new Vector3d(axis))), w2 = axis.dot(invInertiaWorld(i, new Vector3d(axis)));
        Vector3d impulse = axis.mul(angle / (w1 + w2));
        rotate(q[i], invInertiaWorld(i, new Vector3d(impulse)), 1);
        rotate(q[p], invInertiaWorld(p, new Vector3d(impulse)).negate(), 1);
    }

    /** The nearest relative turn inside a body's limits, or null when it is inside already. */
    static Quaterniond clamp(RagdollDefinition.Body b, Quaterniond rel) {
        // one of the two quaternions of the turn: w >= 0, so its angles are the short way round
        if (rel.w < 0) rel = new Quaterniond(-rel.x, -rel.y, -rel.z, -rel.w);
        if (b.joint() == RagdollDefinition.Joint.HINGE) {
            // only about x, between its limits
            double a = twist(rel, 0);
            double c = Math.max(b.xMin(), Math.min(b.xMax(), a));
            Quaterniond t = axis(1, 0, 0, c);
            return angle(t, rel) < 1e-5 ? null : t;
        }
        double tw = twist(rel, 1);
        Quaterniond twist = axis(0, 1, 0, tw);
        Quaterniond swing = new Quaterniond(rel).mul(new Quaterniond(twist).conjugate());
        if (swing.w < 0) swing.set(-swing.x, -swing.y, -swing.z, -swing.w);
        double s = Math.sqrt(swing.x * swing.x + swing.y * swing.y + swing.z * swing.z);
        double sa = 2 * Math.atan2(s, swing.w);
        double sx = s < 1e-12 ? 0 : swing.x / s * sa, sz = s < 1e-12 ? 0 : swing.z / s * sa;
        double tc = Math.max(b.twistMin(), Math.min(b.twistMax(), tw));
        double lx = sx >= 0 ? b.xMax() : -b.xMin(), lz = sz >= 0 ? b.zMax() : -b.zMin();
        double cx = sx, cz = sz;
        if (lx <= 1e-6) cx = 0;
        if (lz <= 1e-6) cz = 0;
        double e = (lx > 1e-6 ? cx * cx / (lx * lx) : 0) + (lz > 1e-6 ? cz * cz / (lz * lz) : 0);
        if (e > 1) {
            double k = 1 / Math.sqrt(e);
            cx *= k;
            cz *= k;
        }
        if (Math.abs(cx - sx) < 1e-6 && Math.abs(cz - sz) < 1e-6 && Math.abs(tc - tw) < 1e-6) return null;
        double ca = Math.sqrt(cx * cx + cz * cz);
        Quaterniond cs = ca < 1e-12 ? new Quaterniond() : axis(cx / ca, 0, cz / ca, ca);
        return cs.mul(axis(0, 1, 0, tc));
    }

    /**
     * A turn of {@code angle} radians about a unit axis. (JOML 1.10's Quaterniond#rotationX gives (sin, 0, cos, 0) for
     * x, the wrong quaternion, so the ragdoll builds its own.)
     */
    public static Quaterniond axis(double x, double y, double z, double angle) {
        double s = Math.sin(angle / 2);
        return new Quaterniond(x * s, y * s, z * s, Math.cos(angle / 2));
    }

    /** The twist angle of a turn about one model axis (0 x, 1 y), in (-pi, pi]. */
    static double twist(Quaterniond r, int axis) {
        double c = axis == 0 ? r.x : r.y;
        double a = 2 * Math.atan2(c, r.w);
        if (a > Math.PI) a -= 2 * Math.PI;
        if (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    /** The angle between two turns (radians). */
    static double angle(Quaterniond a, Quaterniond b) {
        double d = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w);
        return 2 * Math.acos(Math.min(1, d));
    }

    // ---------------------------------------------------------------- contacts

    /** Keeps body a's inset surface out of body b's box. */
    private void separate(int a, int b) {
        if (x[a].distance(x[b]) > radius[a] + radius[b]) return;
        RagdollDefinition.Body bb = def.bodies[b];
        float[] box = bb.box();
        Quaterniond inv = new Quaterniond(q[b]).conjugate();
        Vector3d local = new Vector3d(), p = new Vector3d();
        for (Vector3d off : inner[a]) {
            p.set(off);
            q[a].transform(p).add(x[a]);
            local.set(p).sub(x[b]);
            inv.transform(local).div(RagdollDefinition.PX).add(bb.centre().x, bb.centre().y, bb.centre().z);
            if (local.x <= box[0] || local.x >= box[3] || local.y <= box[1] || local.y >= box[4] || local.z <= box[2] || local.z >= box[5]) continue;
            double[] depth = {local.x - box[0], box[3] - local.x, local.y - box[1], box[4] - local.y, local.z - box[2], box[5] - local.z};
            int face = 0;
            for (int f = 1; f < 6; f++) if (depth[f] < depth[face]) face = f;
            Vector3d normal = new Vector3d(face == 0 ? -1 : face == 1 ? 1 : 0, face == 2 ? -1 : face == 3 ? 1 : 0, face == 4 ? -1 : face == 5 ? 1 : 0);
            q[b].transform(normal);
            pair(a, new Vector3d(p).sub(x[a]), b, new Vector3d(p).sub(x[b]), normal, depth[face] * RagdollDefinition.PX);
        }
    }

    /** Pushes body i's surface points out of the world's boxes; static friction holds a touching point in place. */
    private void collide(int i, double h) {
        Vector3d p = new Vector3d(), start = new Vector3d(), n2 = new Vector3d();
        double r = radius[i];
        for (double[] box : boxes) {
            if (x[i].x + r < box[0] || x[i].x - r > box[3] || x[i].y + r < box[1] || x[i].y - r > box[4]
                    || x[i].z + r < box[2] || x[i].z - r > box[5]) continue;
            for (Vector3d off : points[i]) {
                p.set(off);
                q[i].transform(p).add(x[i]);
                if (!inside(p, box, 0)) continue;
                double depth = exit(p, box, n2);
                if (depth <= 0) continue;
                Vector3d rr = new Vector3d(p).sub(x[i]);
                // what it moved in this substep goes back out at once; an older, deeper overlap comes out over several
                // substeps, not in one kick
                start.set(off);
                pq[i].transform(start).add(px[i]);
                double travel = Math.max(0, -new Vector3d(p).sub(start).dot(n2));
                depth = Math.min(depth, travel + MAX_PUSH);
                push(i, rr, n2, depth);
                contacts.add(new Contact(i, new Vector3d(off), new Vector3d(n2), depth));
                // static friction: undo this point's slide over the surface during the substep
                p.set(off);
                q[i].transform(p).add(x[i]);
                start.set(off);
                pq[i].transform(start).add(px[i]);
                Vector3d slide = new Vector3d(p).sub(start);
                slide.fma(-slide.dot(n2), n2);
                double s = slide.length();
                if (s > 1e-9 && s < STATIC_FRICTION * depth) push(i, new Vector3d(p).sub(x[i]), slide.div(-s), s);
            }
        }
    }

    /**
     * How far a point inside a box must go to leave it, along the nearest face whose exit is not inside another box (the
     * floor's next block); the normal into {@code normal}.
     */
    private double exit(Vector3d p, double[] box, Vector3d normal) {
        double[] depth = {p.x - box[0], box[3] - p.x, p.y - box[1], box[4] - p.y, p.z - box[2], box[5] - p.z};
        int[] order = {0, 1, 2, 3, 4, 5};
        for (int a = 1; a < 6; a++) for (int b = a; b > 0 && depth[order[b]] < depth[order[b - 1]]; b--) {
            int t = order[b]; order[b] = order[b - 1]; order[b - 1] = t;
        }
        Vector3d e = new Vector3d();
        for (int k = 0; k < 6; k++) {
            int f = order[k];
            face(f, normal);
            e.set(p).fma(depth[f] + 1e-4, normal);
            boolean blocked = false;
            for (double[] other : boxes) if (other != box && inside(e, other, 1e-6)) { blocked = true; break; }
            if (!blocked) return depth[f];
        }
        face(order[0], normal);
        return depth[order[0]];
    }

    private static void face(int f, Vector3d out) {
        out.set(f == 0 ? -1 : f == 1 ? 1 : 0, f == 2 ? -1 : f == 3 ? 1 : 0, f == 4 ? -1 : f == 5 ? 1 : 0);
    }

    private static boolean inside(Vector3d p, double[] b, double eps) {
        return p.x > b[0] + eps && p.x < b[3] - eps && p.y > b[1] + eps && p.y < b[4] - eps && p.z > b[2] + eps && p.z < b[5] - eps;
    }

    /** Velocity pass for a world contact: no bounce, dynamic friction against the slide. */
    private void friction(Contact c, double h) {
        int i = c.body();
        Vector3d r = q[i].transform(new Vector3d(c.local()));
        Vector3d vp = new Vector3d(w[i]).cross(r).add(v[i]);
        double vn = vp.dot(c.normal());
        Vector3d vt = new Vector3d(vp).fma(-vn, c.normal());
        Vector3d dv = new Vector3d();
        if (vn < 0) dv.fma(-vn, c.normal());
        double t = vt.length();
        if (t > 1e-9) dv.fma(-Math.min(DYNAMIC_FRICTION * c.depth() / h, t) / t, vt);
        double m = dv.length();
        if (m < 1e-9) return;
        Vector3d dir = new Vector3d(dv).div(m);
        Vector3d rn = new Vector3d(r).cross(dir);
        double wsum = invMass[i] + rn.dot(invInertiaWorld(i, new Vector3d(rn)));
        Vector3d impulse = dir.mul(m / wsum);
        v[i].fma(invMass[i], impulse);
        w[i].add(invInertiaWorld(i, new Vector3d(r).cross(impulse)));
    }

    // ---------------------------------------------------------------- corrections

    /** Moves body i's point (offset r, world axes) by {@code c} along {@code n} against the static world. */
    private void push(int i, Vector3d r, Vector3d n, double c) {
        Vector3d rn = new Vector3d(r).cross(n);
        double wi = invMass[i] + rn.dot(invInertiaWorld(i, new Vector3d(rn)));
        Vector3d impulse = new Vector3d(n).mul(c / wi);
        x[i].fma(invMass[i], impulse);
        rotate(q[i], invInertiaWorld(i, new Vector3d(r).cross(impulse)), 1);
    }

    /** Closes a gap of {@code c} along {@code n}: body a's point (offset ra) moves along +n, body b's (rb) along -n. */
    private void pair(int a, Vector3d ra, int b, Vector3d rb, Vector3d n, double c) {
        Vector3d an = new Vector3d(ra).cross(n), bn = new Vector3d(rb).cross(n);
        double wa = invMass[a] + an.dot(invInertiaWorld(a, new Vector3d(an)));
        double wb = invMass[b] + bn.dot(invInertiaWorld(b, new Vector3d(bn)));
        Vector3d impulse = new Vector3d(n).mul(c / (wa + wb));
        x[a].fma(invMass[a], impulse);
        rotate(q[a], invInertiaWorld(a, new Vector3d(ra).cross(impulse)), 1);
        x[b].fma(-invMass[b], impulse);
        rotate(q[b], invInertiaWorld(b, new Vector3d(rb).cross(impulse)).negate(), 1);
    }

    /** I^-1 v in world axes (in place). */
    private Vector3d invInertiaWorld(int i, Vector3d vec) {
        Quaterniond c = new Quaterniond(q[i]).conjugate();
        c.transform(vec);
        vec.mul(invInertia[i]);
        return q[i].transform(vec);
    }

    /** q += 0.5 h [omega, 0] q, normalised. */
    private static void rotate(Quaterniond q, Vector3d omega, double h) {
        Quaterniond d = new Quaterniond(omega.x, omega.y, omega.z, 0).mul(q);
        q.set(q.x + 0.5 * h * d.x, q.y + 0.5 * h * d.y, q.z + 0.5 * h * d.z, q.w + 0.5 * h * d.w).normalize();
    }

    // ---------------------------------------------------------------- output

    public Vector3d position(int body) { return x[body]; }

    public Quaterniond orientation(int body) { return q[body]; }

    public Vector3d velocity(int body) { return v[body]; }

    public Vector3d spin(int body) { return w[body]; }

    /** Sets one box's place (a snapshot from the server, or a saved pose). */
    public void set(int body, Vector3d position, Quaterniond orientation) {
        x[body].set(position);
        q[body].set(orientation);
    }

    /**
     * The skeleton pose (model px) that draws this body when the model is drawn at {@code origin} facing yaw 0: every
     * bone's skinning matrix, the attached bones (hands, feet, root) with their box, and local turns and offsets to match
     * (SkeletonPose#half needs them).
     */
    public void pose(SkeletonPose out, Vector3d origin) {
        pose(out, origin, x, q);
    }

    /** The same from given box places (interpolated). */
    public void pose(SkeletonPose out, Vector3d origin, Vector3d[] pos, Quaterniond[] rot) {
        SkeletonRig rig = def.rig;
        Quaterniond fix = facing(0).conjugate();
        Vector3d t = new Vector3d();
        for (int i = 0; i < n; i++) {
            RagdollDefinition.Body b = def.bodies[i];
            Quaterniond r = new Quaterniond(fix).mul(rot[i]);
            t.set(pos[i]).sub(origin);
            fix.transform(t).div(RagdollDefinition.PX);
            t.y += ORIGIN_Y;
            Matrix4f m = out.world[b.bone()];
            m.identity().translate((float) t.x, (float) t.y, (float) t.z)
                    .rotate(new Quaternionf((float) r.x, (float) r.y, (float) r.z, (float) r.w))
                    .translate(-b.centre().x, -b.centre().y, -b.centre().z);
            for (int a : b.attached()) out.world[a].set(m);
        }
        Matrix4f inv = new Matrix4f();
        Vector3f p = new Vector3f();
        for (int bone = 0; bone < rig.size(); bone++) {
            int parent = rig.parent[bone];
            Matrix4f rel = parent < 0 ? new Matrix4f(out.world[bone]) : inv.set(out.world[parent]).invert().mul(out.world[bone], new Matrix4f());
            rel.getNormalizedRotation(out.local[bone]);
            Vector3f pivot = rig.pivot[bone];
            rel.transformPosition(p.set(pivot), out.offset[bone]).sub(pivot);
            out.squash[bone] = 1F;
        }
        out.shoulderDrop = 0F;
    }

    // ---------------------------------------------------------------- measures (offline check, debug)

    /** The deepest any surface point sits inside the world's boxes (blocks), against the boxes read last tick. */
    public double penetration() {
        double worst = 0;
        Vector3d p = new Vector3d(), nn = new Vector3d();
        for (int i = 0; i < n; i++) for (Vector3d off : points[i]) {
            p.set(off);
            q[i].transform(p).add(x[i]);
            for (double[] box : boxes) if (inside(p, box, 0)) worst = Math.max(worst, exit(p, box, nn));
        }
        return worst;
    }

    /** The widest gap at any joint (blocks). */
    public double jointGap() {
        double worst = 0;
        for (int i = 1; i < n; i++) {
            RagdollDefinition.Body b = def.bodies[i];
            Vector3d d = new Vector3d(x[i]).add(anchor(i, b.anchor())).sub(x[b.parent()]).sub(anchor(b.parent(), b.anchor()));
            worst = Math.max(worst, beyondSlack(i, d).length());
        }
        return worst;
    }

    /** The furthest any joint is past its limits (radians), and which body into {@code which[0]}. */
    public double limitExcess(int[] which) {
        double worst = 0;
        for (int i = 1; i < n; i++) {
            RagdollDefinition.Body b = def.bodies[i];
            Quaterniond rel = new Quaterniond(q[b.parent()]).conjugate().mul(q[i]);
            Quaterniond t = clamp(b, rel);
            if (t == null) continue;
            double a = angle(t, rel);
            if (a > worst) {
                worst = a;
                if (which != null) which[0] = i;
            }
        }
        return worst;
    }

    /** The lowest surface point's height (blocks). */
    public double lowest() {
        double y = 1e30;
        Vector3d p = new Vector3d();
        for (int i = 0; i < n; i++) for (Vector3d off : points[i]) y = Math.min(y, q[i].transform(p.set(off)).y + x[i].y);
        return y;
    }

    public int size() { return n; }
}
