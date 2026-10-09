package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.block.PavementMarkingBlock;
import com.antaurora.apofirstlight.block.RoadMarkingBlock;
import com.antaurora.apofirstlight.block.RoadMarkingStepConnectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;

/**
 * Pavement markings (lines, hatching, bars, arrows, the accessibility symbol; Pavement Markings V1 and the older road lines)
 * are paint on the road, not things of their own: Jade shows the surface they lie on, so looking along a painted road reads
 * the same as the bare asphalt beside it (user 2026-10-09: no Jade layer for decals).
 */
public final class PavementMarkingJadeRedirect {
    private PavementMarkingJadeRedirect() {}

    static boolean isMarking(Block block) {
        return block instanceof PavementMarkingBlock || block instanceof RoadMarkingBlock || block instanceof RoadMarkingStepConnectorBlock;
    }

    public static void register(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (!(accessor instanceof BlockAccessor block) || !isMarking(block.getBlock())) return accessor;
            Level level = block.getLevel();
            BlockPos below = block.getPosition().below();
            BlockState ground = level.getBlockState(below);
            if (ground.isAir()) return accessor;
            return registration.blockAccessor().from(block).hit(block.getHitResult().withPosition(below)).blockState(ground)
                    .blockEntity(level.getBlockEntity(below)).build();
        });
    }
}
