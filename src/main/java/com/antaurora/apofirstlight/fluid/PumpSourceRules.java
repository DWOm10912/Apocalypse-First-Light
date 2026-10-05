package com.antaurora.apofirstlight.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * What a pump may draw from a source block (user, 2026-10-05, for gameplay): water and lava come from pools. A pump draws
 * them only from a source inside a pool of at least 3 x 3 x 1 sources of the same fluid, and then never uses the pool up.
 * Every other liquid (gasoline, diesel, industrial waste, other mods' fluids) is finite: any source block will do, and
 * it is used up after {@link #SOURCE_MB}. Shared by the intake pump and the later high-temperature (lava) pump.
 */
public final class PumpSourceRules {
    /** The pool's least width and length, in blocks. */
    public static final int POOL_SIZE = 3;
    /** A finite liquid's source block gives this much before it is used up. */
    public static final int SOURCE_MB = 1000;

    private PumpSourceRules() {
    }

    /** Water and lava: pool fluids, drawn only from a big enough pool and never used up. */
    public static boolean isPoolFluid(Fluid fluid) {
        return fluid.isSame(Fluids.WATER) || fluid.isSame(Fluids.LAVA);
    }

    /**
     * Whether the source at {@code source} lies inside some 3 x 3 square of sources of {@code fluid} at its level: any of
     * the nine squares that hold it, so a source on the pool's edge (where a bank-side pump reaches) counts. Unloaded
     * blocks count as no source.
     */
    public static boolean inPool(Level level, BlockPos source, Fluid fluid) {
        int n = POOL_SIZE;
        for (int ox = 1 - n; ox <= 0; ox++) {
            for (int oz = 1 - n; oz <= 0; oz++) {
                if (fullSquare(level, source.offset(ox, 0, oz), fluid, n)) return true;
            }
        }
        return false;
    }

    private static boolean fullSquare(Level level, BlockPos corner, Fluid fluid, int n) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < n; dx++) {
            for (int dz = 0; dz < n; dz++) {
                cursor.set(corner.getX() + dx, corner.getY(), corner.getZ() + dz);
                if (!level.isLoaded(cursor)) return false;
                FluidState state = level.getFluidState(cursor);
                if (!state.isSource() || !state.getType().isSame(fluid)) return false;
            }
        }
        return true;
    }
}
