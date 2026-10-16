package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.skeleton.seat.RiderSeat;
import com.antaurora.apofirstlight.skeleton.seat.SeatedRiderHost;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * Seated riders V1, interim (docs/dev/seated_rider_v1_plan.md 2.3): the T15's seats for the skeleton until
 * PickupTruckEntity implements SeatedRiderHost itself (Main; SeatedRiders asks the entity first). Rig frame metres (X the
 * truck's right, Y up, Z back; src/main/blockbench/vehicles/pickup_t15.rig.json, tools/pickup-t15/cab.mjs), heading only
 * (no pitch or roll here).
 */
record T15Seats(PickupTruckEntity truck) implements SeatedRiderHost {
    /** Cushion tops and floors: cab (bench seat, floor pan), bed (wheel housing tops, bed floor). */
    static final float CAB_CUSHION = 0.845F, CAB_FLOOR = 0.535F, BED_CUSHION = 0.89F, BED_FLOOR = 0.772F;
    /** Door hinge (rig, the left door; the right mirrors), the inside pull on the armrest, the outside handle. */
    static final float HINGE_X = 0.825F, HINGE_Z = -0.916F;
    static final float[] PULL = {0.76F, 0.95F, -0.12F}, OUTER = {0.85F, 0.98F, 0.057F}, ENTRY = {1.45F, 0F, -0.30F};
    static final float[] GRIP_L = {-0.56F, 1.125F, -0.40F}, GRIP_R = {-0.18F, 1.125F, -0.40F};

    @Override
    public int riderSeatIndex(Entity passenger) {
        return truck.seatOf(passenger);
    }

    @Nullable
    @Override
    public RiderSeat riderSeat(int index, float partialTick) {
        if (index < 0 || index >= PickupTruckLayout.SEATS.length) return null;
        PickupTruckLayout.Seat s = PickupTruckLayout.SEATS[index];
        Vec3 origin = truck.getPosition(partialTick);
        float yaw = Mth.rotLerp(partialTick, truck.yRotO, truck.getYRot());
        boolean cab = s.cab();
        float cushionY = cab ? CAB_CUSHION : BED_CUSHION, floorY = cab ? CAB_FLOOR : BED_FLOOR;
        Vec3 cushion = PickupTruckLayout.world(origin, yaw, s.f(), s.r(), cushionY);
        Quaternionf frame = new Quaternionf().rotationY((float) Math.toRadians(180F - (yaw + s.yaw())));
        RiderSeat.Kind kind = index == PickupTruckLayout.DRIVER ? RiderSeat.Kind.DRIVER : cab ? RiderSeat.Kind.PASSENGER : RiderSeat.Kind.BENCH;
        int side = cab ? (s.right() ? 1 : -1) : 0;
        float angle = cab ? truck.doorAngle(s.right(), partialTick) : 0F;
        Map<String, Vec3> anchors = new HashMap<>();
        RiderSeat probe = new RiderSeat(kind, cushion, frame, side, 0, anchors);
        float ahead = cab ? 0.40F : 0.30F;
        anchors.put("floor_l", probe.local(-0.10, floorY - cushionY, -ahead));
        anchors.put("floor_r", probe.local(0.10, floorY - cushionY, -ahead));
        if (kind == RiderSeat.Kind.DRIVER) {
            anchors.put("grip_l", rig(origin, yaw, GRIP_L[0], GRIP_L[1], GRIP_L[2]));
            anchors.put("grip_r", rig(origin, yaw, GRIP_R[0], GRIP_R[1], GRIP_R[2]));
        }
        if (side != 0) {
            anchors.put("door_pull", door(origin, yaw, side, PULL, angle));
            anchors.put("door_outer", door(origin, yaw, side, OUTER, angle));
            anchors.put("entry", rig(origin, yaw, side * ENTRY[0], ENTRY[1], ENTRY[2]));
        }
        return new RiderSeat(kind, cushion, frame, side, angle / PickupTruckEntity.DOOR_OPEN, anchors);
    }

    /** A rig point to the world. */
    private static Vec3 rig(Vec3 origin, float yaw, float x, float y, float z) {
        return PickupTruckLayout.world(origin, yaw, -z, x, y);
    }

    /** A point on a door (left-door numbers, mirrored for the right) swung by its angle, as PickupTruckRenderer turns it. */
    private static Vec3 door(Vec3 origin, float yaw, int side, float[] p, float angle) {
        float px = HINGE_X * side, x = p[0] * side - px, z = p[2] - HINGE_Z;
        double a = Math.toRadians(side < 0 ? -angle : angle), c = Math.cos(a), s = Math.sin(a);
        return rig(origin, yaw, (float) (px + x * c + z * s), p[1], (float) (HINGE_Z - x * s + z * c));
    }
}
