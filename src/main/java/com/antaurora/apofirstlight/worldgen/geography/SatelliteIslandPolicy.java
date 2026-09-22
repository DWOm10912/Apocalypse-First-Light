package com.antaurora.apofirstlight.worldgen.geography;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Non-terrain metadata for the three stable geographic slots. */
public final class SatelliteIslandPolicy {
    public static final int COUNT = 3;
    public static final long ROLE_SALT = 0x534154524F4C4501L;

    public enum FacilityRole { VIRUS_LAB, MILITARY_BASE, LARGE_PRISON }
    public enum BridgePolicy { BRIDGE_REQUIRED, NO_BRIDGE }
    public record Slot(int id, FacilityRole facilityRole, BridgePolicy bridgePolicy) {
        public String satelliteId() { return "satellite_" + id; }
    }

    private SatelliteIslandPolicy() {}

    public static List<Slot> slots(long seed) {
        var roles = new ArrayList<>(List.of(FacilityRole.values()));
        var random = new SplittableRandom(seed ^ ROLE_SALT);
        for (int i = roles.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            var role = roles.get(i);
            roles.set(i, roles.get(j));
            roles.set(j, role);
        }
        var result = new ArrayList<Slot>();
        for (int i = 0; i < COUNT; i++)
            result.add(new Slot(i + 1, roles.get(i), BridgePolicy.BRIDGE_REQUIRED));
        return List.copyOf(result);
    }
}
