package com.antaurora.apofirstlight.skeleton.seat;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Seated riders V1 (docs/dev/seated_rider_v1_plan.md 2): a vehicle (or any entity with seats) that wants its riders drawn
 * sitting by the player skeleton. The vehicle owns its seats and doors; the skeleton owns the body, its size and the
 * get-in / get-out moves (client/skeleton/SeatedRiders).
 * <ul>
 * <li>{@link #riderSeatIndex}: both sides, from synced data;</li>
 * <li>{@link #riderSeat}: client, per frame, by index: the skeleton keeps asking for a seat after its rider left it
 * (the get-out move needs the door as it swings).</li>
 * </ul>
 * World points with the vehicle's full orientation (its pitch and roll too), at the frame's partial tick.
 */
public interface SeatedRiderHost {
    /** The seat this passenger sits on, or -1 (not seated, or the seat not synced yet). */
    int riderSeatIndex(Entity passenger);

    /** Client: a seat as it is this frame, or null when there is no such seat. */
    @Nullable
    RiderSeat riderSeat(int seat, float partialTick);
}
