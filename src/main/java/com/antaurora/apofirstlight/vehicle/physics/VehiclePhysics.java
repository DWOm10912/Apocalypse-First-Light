package com.antaurora.apofirstlight.vehicle.physics;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Vehicle physics V0-b packages 1 and 2 (docs/vehicles/first_pickup_v0_design.md sections 8.3, 8.5): one rigid body on four suspended
 * wheels, the same code on the driver's client and on the server (no game classes: it only asks {@link VehicleWorld}).
 * <ul>
 * <li>Body: mass, inertia about the model axes, position of the centre of mass and orientation (model frame to world),
 * linear and angular velocity; 4 sub-steps of 12.5 ms a tick (80 Hz, semi-implicit Euler).</li>
 * <li>Suspension: per wheel the travel (up = compressed) from the static ride height, found from the ground under the
 * hub; spring + damper along the body's up, a stiff bump stop past the bump travel; the force goes along the contact
 * normal, so a kerb's edge pushes back as well as up.</li>
 * <li>Ground: sampled across the tyre's footprint along its heading and rounded by the tyre radius (the lowest centre
 * height that touches nothing), so a block step reads as a ramp: about 0.15 m climbs, a full block does not.</li>
 * <li>Tyres: longitudinal (drive, brake, handbrake, rolling) and lateral (slip angle, linear to its peak, then sliding
 * grip) forces, cut to a friction circle of grip x load.</li>
 * <li>Drive (package 2, {@link Drivetrain}): engine, automatic clutch, 5-speed gearbox, 2H (rear wheels, half each) or 4H
 * (all four by load); the drive force the tyres could not take goes back into the engine (wheelspin revs it).</li>
 * <li>Hull: sample points on the body's outside pushed out of blocks and other vehicles (impulse with a little bounce and
 * friction).</li>
 * <li>Holding still: braked (or handbrake, or no input) below 0.15 m/s on ground the tyres can hold, the sliding is taken
 * out each step, so a parked truck does not creep down a slope.</li>
 * </ul>
 * The brake pedal only brakes (package 1's reverse on the back key went with the gearbox).
 */
public final class VehiclePhysics {
    public static final int SUBSTEPS = 4;
    public static final double TICK = 0.05, DT = TICK / SUBSTEPS;
    /** Below this speed (m/s) a held truck stops sliding; ticks of rest before the server stops simulating it. */
    private static final double HOLD_SPEED = 0.15, HOLD_TURN = 0.1;
    private static final int SLEEP_TICKS = 20;
    /** Hull contacts: bounce, friction, how much of the depth is corrected per step, the most it moves per step. */
    private static final double BOUNCE = 0.15, HULL_FRICTION = 0.5, CORRECT = 0.8, CORRECT_MAX = 0.2;
    private static final double[] SAMPLES = {-1, -0.66, -0.33, 0.33, 0.66, 1};

    public final VehicleSpec spec;
    /** Centre of mass (world) and orientation (model frame to world). */
    public final Vector3d com = new Vector3d();
    public final Quaterniond q = new Quaterniond();
    /** Velocity (m/s) and angular velocity (rad/s), world. */
    public final Vector3d v = new Vector3d(), w = new Vector3d();
    /** Centre steer angle (rad, + to the right) and each wheel's own (Ackermann). */
    public double steer;
    public final double[] wheelSteer = new double[4];
    /** Wheel travel from the ride height (m, + up), turn (rad) and turn rate (rad/s, + rolling forward). */
    public final double[] travel = new double[4], spin = new double[4], spinRate = new double[4];
    public final boolean[] contact = new boolean[4];
    private final double[] travelBefore = new double[4];
    /** Engine, clutch, gears, transfer case (package 2). */
    public final Drivetrain drivetrain;
    /** Each wheel's load last step (N; the 4H split). */
    private final double[] load = new double[4];
    /** The server stopped simulating it: at rest, parked. */
    public boolean asleep;
    private int restTicks;
    private boolean heldAll;
    /** Per wheel the last ground answer (kept while its column is not loaded): lowest centre height, normal, grip. */
    private final double[] lastCentre = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    private final double[][] lastNormal = new double[4][3];
    private final double[] lastGrip = {1, 1, 1, 1};
    private double cMin, cnx, cny, cnz, cGrip;
    // scratch
    private final Matrix3d rot = new Matrix3d();
    private final Quaterniond dq = new Quaterniond();
    private final Vector3d force = new Vector3d(), torque = new Vector3d(), a = new Vector3d(), b = new Vector3d(), c = new Vector3d(),
            d = new Vector3d(), up = new Vector3d(), fwd = new Vector3d(), wb = new Vector3d(), tb = new Vector3d();
    private final VehicleWorld.Ground ground = new VehicleWorld.Ground();
    private final VehicleWorld.Push push = new VehicleWorld.Push();
    // this tick's effective controls
    private double brake;
    private boolean handbrake, holdable;

    public VehiclePhysics(VehicleSpec spec) {
        this.spec = spec;
        drivetrain = new Drivetrain(spec);
    }

    /** Puts the truck at rest with its origin (model frame origin: the ground under the cab) at (x, y, z), heading yaw. */
    public void reset(double x, double y, double z, float yaw) {
        reset(x, y, z, new Quaterniond().rotationY(Math.toRadians(180.0 - yaw)));
    }

    /** The same with a whole orientation (model frame to world). */
    public void reset(double x, double y, double z, Quaterniond orientation) {
        q.set(orientation).normalize();
        q.transform(a.set(spec.comX, spec.comY, spec.comZ));
        com.set(x, y, z).add(a);
        v.zero();
        w.zero();
        steer = 0;
        asleep = false;
        restTicks = 0;
        for (int i = 0; i < 4; i++) {
            travel[i] = 0;
            spinRate[i] = 0;
            wheelSteer[i] = 0;
            lastCentre[i] = Double.NaN;
        }
    }

    /** The model origin (world). */
    public Vector3d origin(Vector3d out) {
        q.transform(out.set(spec.comX, spec.comY, spec.comZ));
        return out.set(com.x - out.x, com.y - out.y, com.z - out.z);
    }

    /** The heading (degrees, Minecraft yaw: the front along (-sin, 0, cos)). */
    public float yaw() {
        q.transform(a.set(0, 0, -1));
        return (float) Math.toDegrees(Math.atan2(-a.x, a.z));
    }

    /** Speed along the heading (m/s, + forward). */
    public double forwardSpeed() {
        q.transform(a.set(0, 0, -1));
        return v.dot(a);
    }

    public void wake() {
        asleep = false;
        restTicks = 0;
    }

    /** This state for the network. */
    public VehicleState capture() {
        Vector3d o = origin(new Vector3d());
        float[] t = new float[4], s = new float[4];
        for (int i = 0; i < 4; i++) {
            t[i] = (float) travel[i];
            s[i] = (float) spinRate[i];
        }
        return new VehicleState(o.x, o.y, o.z, (float) q.x, (float) q.y, (float) q.z, (float) q.w, (float) v.x, (float) v.y, (float) v.z,
                (float) w.x, (float) w.y, (float) w.z, (float) steer, t, s, asleep, drivetrain.capture());
    }

    /** Takes over a state from the network (the driver's, or the server's for a watcher or a correction). */
    public void apply(VehicleState s) {
        q.set(s.qx(), s.qy(), s.qz(), s.qw()).normalize();
        q.transform(a.set(spec.comX, spec.comY, spec.comZ));
        com.set(s.x() + a.x, s.y() + a.y, s.z() + a.z);
        v.set(s.vx(), s.vy(), s.vz());
        w.set(s.wx(), s.wy(), s.wz());
        steer = s.steer();
        for (int i = 0; i < 4; i++) {
            travel[i] = s.travel()[i];
            spinRate[i] = s.spinRate()[i];
        }
        drivetrain.apply(s.drive());
        asleep = s.asleep();
        restTicks = 0;
        steerWheels();
    }

    /** One game tick: the controls, then {@link #SUBSTEPS} steps. */
    public void tick(VehicleInput in, VehicleWorld world) {
        if (!in.idle() || drivetrain.busy()) wake();
        if (asleep) return;
        controls(in);
        heldAll = true;
        for (int s = 0; s < SUBSTEPS; s++) step(in, world);
        drivetrain.endTick();
        for (int i = 0; i < 4; i++) spin[i] = (spin[i] + spinRate[i] * TICK) % (Math.PI * 2);
        if (heldAll && in.idle() && in.handbrake() && !drivetrain.busy()) {
            if (++restTicks >= SLEEP_TICKS) {
                asleep = true;
                v.zero();
                w.zero();
            }
        } else {
            restTicks = 0;
        }
    }

    /** Whether the ground under any wheel moved since it went to sleep (a block broken or placed). */
    public boolean groundChanged(VehicleWorld world) {
        q.get(rot);
        up.set(rot.m10, rot.m11, rot.m12);
        for (int i = 0; i < 4; i++) {
            Vector3d hub = rot.transform(a.set(spec.hubX[i] - spec.comX, spec.hubY - spec.comY, spec.hubZ[i] - spec.comZ)).add(com);
            q.transform(fwd.set(Math.sin(wheelSteer[i]), 0, -Math.cos(wheelSteer[i])));
            double before = lastCentre[i];
            if (!wheelGround(world, i, hub.x, hub.y, hub.z, fwd.x, fwd.z)) continue;
            if (Double.isNaN(before) != Double.isNaN(cMin) || Math.abs(cMin - before) > 0.02) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- controls

    private void controls(VehicleInput in) {
        double speed = forwardSpeed();
        brake = in.brake();
        handbrake = in.handbrake();
        drivetrain.controls(in, speed, speed / spec.radius);
        // steering: towards the key's side within the speed's limit, back to the middle when let go
        double limit = spec.steerLimit(Math.abs(speed)), target = in.steer() * limit;
        double rate = (in.steer() != 0 ? spec.steerRate : spec.steerReturn) * TICK;
        steer += clamp(target - steer, -rate, rate);
        steer = clamp(steer, -spec.maxSteer, spec.maxSteer);
        steerWheels();
    }

    /** Each front wheel's angle from the centre one (Ackermann: the inner wheel turns more). */
    private void steerWheels() {
        for (int i = 0; i < 4; i++) wheelSteer[i] = wheelSteer(spec, steer, i);
    }

    /** A wheel's steer angle (rad, + to the right) for a centre angle: Ackermann, the inner front wheel turns more. */
    public static double wheelSteer(VehicleSpec spec, double steer, int wheel) {
        if (!spec.front[wheel] || Math.abs(steer) < 1e-5) return 0;
        double turn = spec.wheelbase / Math.tan(Math.abs(steer));
        // turning right (+): the right wheel is the inner one
        boolean inner = spec.left[wheel] == steer < 0;
        return Math.signum(steer) * Math.atan(spec.wheelbase / (turn + (inner ? -1 : 1) * spec.track / 2));
    }

    // ---------------------------------------------------------------- one step

    private void step(VehicleInput in, VehicleWorld world) {
        double dt = DT, m = spec.mass;
        q.get(rot);
        up.set(rot.m10, rot.m11, rot.m12);
        force.set(0, -m * spec.gravity, 0);
        torque.zero();
        int grounded = 0;
        System.arraycopy(travel, 0, travelBefore, 0, 4);
        double gripSum = 0, nSumX = 0, nSumY = 0, nSumZ = 0;
        // the drive this step: the clutch's torque through the gears, to the rear wheels (2H) or all four by load (4H)
        double fs = forwardSpeed();
        double clutchTorque = drivetrain.clutchTorque(fs / spec.radius, dt, fs);
        double drive = drivetrain.wheelForce(clutchTorque), delivered = 0;
        double loadSum = 0;
        for (int i = 0; i < 4; i++) if (drivetrain.fourWheel || !spec.front[i]) loadSum += load[i];
        holdable = Math.abs(drive) < 50 && in.throttle() == 0;
        for (int i = 0; i < 4; i++) {
            // the hub at rest travel, relative to the centre of mass (world)
            Vector3d r0 = rot.transform(a.set(spec.hubX[i] - spec.comX, spec.hubY - spec.comY, spec.hubZ[i] - spec.comZ));
            double hx = com.x + r0.x, hy = com.y + r0.y, hz = com.z + r0.z;
            // the wheel's heading (world)
            rot.transform(fwd.set(Math.sin(wheelSteer[i]), 0, -Math.cos(wheelSteer[i])));
            if (!wheelGround(world, i, hx, hy, hz, fwd.x, fwd.z)) {
                if (Double.isNaN(lastCentre[i])) { airborne(i); continue; }
                cMin = lastCentre[i];
                cnx = lastNormal[i][0];
                cny = lastNormal[i][1];
                cnz = lastNormal[i][2];
                cGrip = lastGrip[i];
            }
            if (Double.isNaN(cMin) || up.y < 0.3) { airborne(i); continue; }
            double reach = (cMin - hy) / up.y;   // travel at which the tyre just touches
            if (reach < -spec.droop) { airborne(i); continue; }
            boolean driven = drivetrain.fourWheel || !spec.front[i];
            double t = Math.min(reach, spec.bump);
            // hub at this travel and its velocity
            double rx = r0.x + up.x * t, ry = r0.y + up.y * t, rz = r0.z + up.z * t;
            pointVelocity(rx, ry, rz, b);
            double closing = -b.dot(up);
            double load = spec.spring[i] * (spec.staticCompression[i] + t) + spec.damping[i] * closing
                    + spec.antiRoll[i] * (t - travelBefore[i ^ 1]);   // the other wheel of the axle (FL/FR, RL/RR)
            if (reach > spec.bump) load += spec.stopRate * Math.min(reach - spec.bump, spec.stopDepth) + 3 * spec.damping[i] * closing;
            double n = Math.max(0, load);
            this.load[i] = n;
            travel[i] = t;
            contact[i] = true;
            grounded++;
            // the contact point, below the hub along the ground normal
            double px = rx - cnx * spec.radius, py = ry - cny * spec.radius, pz = rz - cnz * spec.radius;
            pointVelocity(px, py, pz, b);
            // tyre directions in the contact plane: forward and right (forward x normal)
            double dot = fwd.x * cnx + fwd.y * cny + fwd.z * cnz;
            c.set(fwd.x - cnx * dot, fwd.y - cny * dot, fwd.z - cnz * dot);
            if (c.lengthSquared() < 1e-9) c.set(fwd);
            c.normalize();
            d.set(c.y * cnz - c.z * cny, c.z * cnx - c.x * cnz, c.x * cny - c.y * cnx);
            double vl = b.dot(c), vs = b.dot(d);
            double grip = cGrip * n;
            boolean rear = !spec.front[i];
            // longitudinal
            double fx = 0, share = 0;
            if (driven) share = drivetrain.fourWheel ? (loadSum > 1 ? this.load[i] / loadSum : 0.25) : 0.5;
            fx += drive * share;
            double brakeShare = (spec.front[i] ? spec.brakeFront : 1 - spec.brakeFront) / 2;
            fx -= brake * spec.brakeForce * brakeShare * clamp(vl / 0.3, -1, 1);
            if (handbrake && rear) fx -= 0.8 * grip * clamp(vl / 0.3, -1, 1);
            fx -= spec.rolling * n * clamp(vl / 0.5, -1, 1);
            // lateral
            double alpha = Math.atan2(vs, Math.max(Math.abs(vl), spec.slipSpeed));
            double side = handbrake && rear ? 0.5 : 1;
            double fy = -grip * side * slipCurve(alpha);
            double total = Math.hypot(fx, fy), scale = 1;
            if (total > grip && total > 0) {
                scale = grip / total;
                fx *= scale;
                fy *= scale;
            }
            delivered += drive * share * scale;
            double ox = cnx * n + c.x * fx + d.x * fy, oy = cny * n + c.y * fx + d.y * fy, oz = cnz * n + c.z * fx + d.z * fy;
            force.add(ox, oy, oz);
            torque.add(py * oz - pz * oy, pz * ox - px * oz, px * oy - py * ox);
            spinRate[i] = handbrake && rear ? 0 : vl / spec.radius;
            // a driven wheel the tyre cannot hold spins with the engine
            if (driven && scale < 0.95 && drivetrain.clutch > 0.5 && drivetrain.ratio() != 0 && !(handbrake && rear))
                spinRate[i] = drivetrain.omega / drivetrain.ratio();
            gripSum += cGrip;
            nSumX += cnx;
            nSumY += cny;
            nSumZ += cnz;
        }
        drivetrain.engine(drivetrain.clutchFor(delivered), dt, fs);
        // drag
        double speed = v.length();
        force.sub(v.x * spec.drag * speed, v.y * spec.drag * speed, v.z * spec.drag * speed);
        // velocities
        v.fma(dt / m, force);
        rot.transformTranspose(wb.set(w));
        rot.transformTranspose(tb.set(torque));
        double lx = spec.ix * wb.x, ly = spec.iy * wb.y, lz = spec.iz * wb.z;   // body angular momentum
        tb.sub(wb.y * lz - wb.z * ly, wb.z * lx - wb.x * lz, wb.x * ly - wb.y * lx);
        wb.add(tb.x / spec.ix * dt, tb.y / spec.iy * dt, tb.z / spec.iz * dt);
        rot.transform(w.set(wb));
        // holding still on ground the tyres can hold
        boolean held = false;
        if (holdable && grounded == 4) {
            double hs = Math.hypot(v.x, v.z);
            double tan = Math.hypot(nSumX, nSumZ) / Math.max(nSumY, 1e-6);
            if (hs < HOLD_SPEED && Math.abs(w.y) < HOLD_TURN && tan < 0.9 * gripSum / 4) {
                v.x = 0;
                v.z = 0;
                w.y = 0;
                held = true;
            }
        }
        if (!held) heldAll = false;
        // positions
        com.fma(dt, v);
        // the turn by the world angular velocity, applied in the world frame (dq * q)
        double turn = w.length() * dt;
        if (turn > 1e-12) q.premul(dq.rotationAxis(turn, w.x, w.y, w.z)).normalize();
        hull(world);
    }

    private void airborne(int i) {
        contact[i] = false;
        load[i] = 0;
        travel[i] = -spec.droop;
        spinRate[i] *= 0.99;
    }

    /** Side force share at a slip angle: linear to the peak, then easing down to the sliding grip. */
    private double slipCurve(double alpha) {
        double x = Math.abs(alpha) / spec.slipPeak;
        double f = x <= 1 ? x : 1 - (1 - spec.slideGrip) * Math.min(1, (x - 1) / 2.5);
        return Math.copySign(f, alpha);
    }

    /** Velocity of a point at r from the centre of mass (world). */
    private void pointVelocity(double rx, double ry, double rz, Vector3d out) {
        out.set(v.x + w.y * rz - w.z * ry, v.y + w.z * rx - w.x * rz, v.z + w.x * ry - w.y * rx);
    }

    /**
     * The lowest centre height the tyre can have over the ground under the hub (cMin, NaN for no ground in reach), the
     * normal it is pushed along and the grip: the tangent plane under the hub, or across the tyre's length (its heading)
     * a higher edge it would touch first, which then pushes from that edge towards the centre. False when not loaded.
     */
    private boolean wheelGround(VehicleWorld world, int i, double x, double y, double z, double hx, double hz) {
        double r = spec.radius, len = Math.hypot(hx, hz);
        if (len > 1e-6) {
            hx /= len;
            hz /= len;
        }
        double top = y + spec.bump + 0.25, bottom = y - spec.droop - r - 0.6;
        double hint = y - r;   // where the tyre meets the ground at rest travel
        if (!world.ground(x, z, hint, top, bottom, ground)) return false;
        double best = Double.NEGATIVE_INFINITY;
        if (ground.found) {
            best = ground.height + r / Math.max(ground.ny, 0.3);
            cnx = ground.nx;
            cny = ground.ny;
            cnz = ground.nz;
            cGrip = ground.grip;
        }
        for (double k : SAMPLES) {
            double off = k * r;
            if (!world.ground(x + hx * off, z + hz * off, hint, top, bottom, ground) || !ground.found) continue;
            double lift = Math.sqrt(Math.max(0, r * r - off * off)), centre = ground.height + lift;
            if (centre > best + 0.005) {
                best = centre;
                cnx = -hx * off / r;
                cny = lift / r;
                cnz = -hz * off / r;
                cGrip = ground.grip;
            }
        }
        cMin = best == Double.NEGATIVE_INFINITY ? Double.NaN : best;
        lastCentre[i] = cMin;
        lastNormal[i][0] = cnx;
        lastNormal[i][1] = cny;
        lastNormal[i][2] = cnz;
        lastGrip[i] = cGrip;
        return true;
    }

    /** Hull points out of whatever they went into: a position correction and an impulse against the closing speed. */
    private void hull(VehicleWorld world) {
        double[] p = spec.hull;
        for (int k = 0; k < p.length; k += 3) {
            q.get(rot);
            Vector3d r = rot.transform(a.set(p[k] - spec.comX, p[k + 1] - spec.comY, p[k + 2] - spec.comZ));
            double rx = r.x, ry = r.y, rz = r.z;
            if (!world.solid(com.x + rx, com.y + ry, com.z + rz, push)) continue;
            double nx = push.nx, ny = push.ny, nz = push.nz;
            double move = Math.min(push.depth, CORRECT_MAX) * CORRECT;
            com.add(nx * move, ny * move, nz * move);
            pointVelocity(rx, ry, rz, b);
            double vn = b.x * nx + b.y * ny + b.z * nz;
            if (vn >= 0) continue;
            double k1 = effectiveMass(rx, ry, rz, nx, ny, nz);
            double j = -(1 + BOUNCE) * vn / k1;
            impulse(rx, ry, rz, nx * j, ny * j, nz * j);
            // friction along the surface
            pointVelocity(rx, ry, rz, b);
            vn = b.x * nx + b.y * ny + b.z * nz;
            double tx = b.x - nx * vn, ty = b.y - ny * vn, tz = b.z - nz * vn, tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-6) continue;
            tx /= tl;
            ty /= tl;
            tz /= tl;
            double jt = Math.min(tl / effectiveMass(rx, ry, rz, tx, ty, tz), HULL_FRICTION * j);
            impulse(rx, ry, rz, -tx * jt, -ty * jt, -tz * jt);
        }
    }

    /** 1/m + n . ((I^-1 (r x n)) x r): how hard the point is to move along n. */
    private double effectiveMass(double rx, double ry, double rz, double nx, double ny, double nz) {
        invInertia(ry * nz - rz * ny, rz * nx - rx * nz, rx * ny - ry * nx, c);
        double ex = c.y * rz - c.z * ry, ey = c.z * rx - c.x * rz, ez = c.x * ry - c.y * rx;
        return 1 / spec.mass + nx * ex + ny * ey + nz * ez;
    }

    private void impulse(double rx, double ry, double rz, double jx, double jy, double jz) {
        v.add(jx / spec.mass, jy / spec.mass, jz / spec.mass);
        invInertia(ry * jz - rz * jy, rz * jx - rx * jz, rx * jy - ry * jx, c);
        w.add(c);
    }

    /** World inverse inertia applied to a world vector. */
    private void invInertia(double x, double y, double z, Vector3d out) {
        q.get(rot);
        rot.transformTranspose(out.set(x, y, z));
        out.set(out.x / spec.ix, out.y / spec.iy, out.z / spec.iz);
        rot.transform(out);
    }

    private static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : x > hi ? hi : x;
    }
}
