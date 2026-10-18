package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.vehicle.physics.LevelVehicleWorld;
import com.antaurora.apofirstlight.vehicle.physics.VehicleWorld;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Where the rain meets the ground as it is drawn (docs/rendering/rain_v1.md), not the blocks' collision tops: the vehicle
 * ground query (vehicle/physics/LevelVehicleWorld#ground) gives the C3 road's pavement and graded turf as drawn, smoothed
 * terrain by its triangles, other blocks by their collision top. Splashes laid on the collision top sat under the road
 * mesh and the smoothed slopes (user in game 2026-10-17: none to be seen looking down). Client main thread only.
 */
final class RainGround {
    private static final VehicleWorld.Ground OUT = new VehicleWorld.Ground();
    private static ClientLevel of;
    private static LevelVehicleWorld world;

    private RainGround() {
    }

    /** The drawn surface under (x, z) near the column's MOTION_BLOCKING top (world y), or NaN when none is found. */
    static double surface(ClientLevel level, double x, double z, int top) {
        if (level != of) {
            of = level;
            world = new LevelVehicleWorld(level, null);
        }
        // smoothed tops reach 2 above their floor block (top - 1); the road layer within 1.5 of the hint
        if (!world.ground(x, z, top - 0.5, top + 1.0, top - 3.0, OUT) || !OUT.found) return Double.NaN;
        return OUT.height;
    }

    /** What answered the last #surface: road, smoothed terrain or block (status counts). */
    static VehicleWorld.Source source() {
        return OUT.source;
    }
}
