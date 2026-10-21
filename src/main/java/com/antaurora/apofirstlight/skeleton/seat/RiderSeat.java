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
 *                  <li>{@code wheel_hub}, {@code wheel_axis}: the wheel's middle and a point along its column (the wrists
 *                  turn with the rim); {@code wheel_rim}: a point on the rim (a grip off it no longer turns);</li>
 *                  <li>{@code shift_knob}: the gear lever's knob, moved with the lever (while shifting the host puts
 *                  {@code grip_r} on it);</li>
 *                  <li>{@code transfer_knob}: the transfer case lever's knob (the same while it is moved);</li>
 *                  <li>{@code key_r}, {@code key_axis}, {@code key_up}: DRIVER, the right hand's hold on the ignition key (on
 *                  its way from the lap, in, turning), a point further in along the key and one along the key's blade as
 *                  it is turned now (the wrist turns with it), as far as {@link #reach};</li>
 *                  <li>planned: {@code pedal_clutch}, {@code pedal_brake}, {@code pedal_accel} (out of the seated legs'
 *                  reach in the T15: docs/dev/seated_rider_v1_plan.md 13).</li>
 *                  </ul>
 * @param hold      DRIVER: the hands from the lap (0) to the wheel's grips (1), eased by the host (the T15: the driver takes
 *                  hold of the wheel to drive, user 2026-10-21); 1 for other seats
 * @param reach     DRIVER: the right hand from wherever it is (0) to the ignition key ({@code key_r}, 1), eased by the
 *                  host (the T15's start, user 2026-10-21); 0 for other seats
 */
public record RiderSeat(Kind kind, Vec3 cushion, Quaternionf frame, int doorSide, float door, Map<String, Vec3> anchors, float hold,
                        float reach) {
    /** A seat whose driver always holds the wheel. */
    public RiderSeat(Kind kind, Vec3 cushion, Quaternionf frame, int doorSide, float door, Map<String, Vec3> anchors) {
        this(kind, cushion, frame, doorSide, door, anchors, 1F, 0F);
    }

    /** A seat whose driver does not reach for a key. */
    public RiderSeat(Kind kind, Vec3 cushion, Quaternionf frame, int doorSide, float door, Map<String, Vec3> anchors, float hold) {
        this(kind, cushion, frame, doorSide, door, anchors, hold, 0F);
    }

    public enum Kind {
        /** Hands on the wheel (as far as {@link #hold}; in the lap the rest of the way). */
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
