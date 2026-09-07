package com.antaurora.apofirstlight.weapon;

import net.minecraft.world.phys.Vec3;

/** Bounded moving segment; no collision or trajectory RNG. */
public final class NativeTrailGeometry {
    public static final double MAX_TICKS = 6, HIT_FADE_TICKS = .15;
    public record Segment(double tail, double head, double alpha) {}
    private NativeTrailGeometry() {}
    public static boolean finite(Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }
    public static Segment segment(double distance, double age, NativeTrailProfile p) {
        if (!Double.isFinite(distance) || !Double.isFinite(age) || distance <= p.hideDistance()
                || age < 0 || age >= MAX_TICKS) return null;
        double arrival = distance / p.speed();
        if (age >= arrival + HIT_FADE_TICKS) return null;
        double head = Math.min(distance, age * p.speed());
        double tail = Math.max(p.hideDistance(), head - p.length());
        if (head <= tail) return null;
        return new Segment(tail, head, Math.max(0, 1 - Math.max(0, age - arrival) / HIT_FADE_TICKS));
    }
    public static Vec3 side(Vec3 direction, Vec3 towardCamera) {
        Vec3 side = direction.cross(towardCamera.normalize());
        if (side.lengthSqr() < 1e-8) {
            // Select a nonparallel axis; symmetric no-cull ribbons are insensitive to side sign.
            side = direction.cross(Math.abs(direction.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        return side.normalize();
    }
}
