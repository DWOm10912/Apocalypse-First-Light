package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Only the OUTSIDE bridge parapet is intercepted; road edge +/-11 is never owned here. */
final class PylonZoneGeometry {
    private PylonZoneGeometry() {}

    static HighwayBlockWriter deckWriter(HighwayBlockWriter target, HighwayPlan plan,
                                         HighwayProfile profile, LandmarkMainSpan span) {
        return new HighwayBlockWriter() {
            public boolean owns(BlockPos p) { return target.owns(p); }
            public boolean mayAffectHorizontal(int x, int z, int radius) {
                return target.mayAffectHorizontal(x, z, radius);
            }
            public boolean set(BlockPos p, BlockState state) {
                var start = plan.sample(0); var t = plan.tangent(0);
                double dx = p.getX() - start.x(), dz = p.getZ() - start.z();
                double s = dx * t.x() + dz * t.z(), l = -dx * t.z() + dz * t.x();
                if (span.inZone(s) && Math.abs(l) == 12
                        && p.getY() == profile.sampleAt(s).roadY() + 1
                        && state.equals(HighwayPalette.REINFORCED_CONCRETE_SLAB)) return false;
                return target.set(p, state);
            }
        };
    }

    static void addPlatforms(BridgePylonGeometry.Builder builder, LandmarkMainSpan span, HighwayProfile profile) {
        for (int p : span.pylons()) for (int ds = -8; ds <= 8; ds++) {
            int width = Math.abs(ds) <= 5 ? 14 : Math.abs(ds) <= 7 ? 13 : 12;
            int y = profile.sampleAt(p + ds).roadY();
            for (int side : new int[]{-1, 1}) {
                builder.box(p + ds, p + ds, side * 12, side * width, y - 3, y,
                        HighwayPalette.REINFORCED_CONCRETE);
                // Outside safety edge follows the tapered platform, with a clear inner anchor strip.
                builder.box(p + ds, p + ds, side * width, side * width, y + 1, y + 1,
                        HighwayPalette.REINFORCED_CONCRETE_SLAB);
            }
        }
    }
}
