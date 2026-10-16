package com.antaurora.apofirstlight.skeleton.seat;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.Map;

/**
 * Seated riders V1 (docs/dev/seated_rider_v1_plan.md 2.2): one seat this frame, world coordinates.
 *
 * @param kind      how the rider sits and what the hands do
 * @param cushion   the cushion's top under the middle of the hips
 * @param frame     the seat frame to the world: +X the rider's right, +Y up, -Z the way the rider faces
 * @param doorSide  the side the rider gets in and out by a door: -1 left, +1 right, 0 none (open seat: climb on, off)
 * @param door      how far that door stands open, 0 shut .. 1 fully open
 * @param anchors   points the body reaches for (all optional; see {@link #anchor}):
 *                  <ul>
 *                  <li>{@code grip_l}, {@code grip_r}: where the hands hold the steering wheel (DRIVER), turned with it;</li>
 *                  <li>{@code floor_l}, {@code floor_r}: where the soles rest;</li>
 *                  <li>{@code door_pull}: the door's inside pull, swung with the door;</li>
 *                  <li>{@code door_outer}: the door's outside handle, swung with the door;</li>
 *                  <li>{@code entry}: where one stands outside this seat's door (the feet);</li>
 *                  <li>planned (P3, gears): {@code shift_knob}, {@code pedal_clutch}, {@code pedal_brake}, {@code pedal_accel}.</li>
 *                  </ul>
 */
public record RiderSeat(Kind kind, Vec3 cushion, Quaternionf frame, int doorSide, float door, Map<String, Vec3> anchors) {
    public enum Kind {
        /** Hands on the wheel. */
        DRIVER,
        /** A seat in a cab: hands in the lap. */
        PASSENGER,
        /** An open bench (a pickup's bed): hands in the lap, no door. */
        BENCH
    }

    @Nullable
    public Vec3 anchor(String name) {
        return anchors.get(name);
    }

    /** A point of the seat frame (metres: right, up, back from the cushion point) in the world. */
    public Vec3 local(double right, double up, double back) {
        var v = frame.transform(new org.joml.Vector3f((float) right, (float) up, (float) back));
        return cushion.add(v.x, v.y, v.z);
    }
}
