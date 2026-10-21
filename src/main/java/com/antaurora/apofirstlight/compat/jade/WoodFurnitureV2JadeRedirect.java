package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.cabin.WoodFurnitureV2;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;

/**
 * Wood Furniture V2 (docs/models/wood_furniture_v2.md, 2026-10-15): aiming at a wardrobe's upper half or the right half
 * of a storage chest or vintage freezer shows the cell that keeps the things (its contents and, for the freezer, the cord), as the other
 * AFL multiblocks do; so does every cell of a workbench but its lower left (AFL Workbench V1, 2026-10-21).
 */
final class WoodFurnitureV2JadeRedirect {
    private WoodFurnitureV2JadeRedirect() {}

    static void register(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (!(accessor instanceof BlockAccessor block)) return accessor;
            BlockState state = block.getBlockState();
            BlockPos pos = block.getPosition(), master;
            if (state.getBlock() instanceof WoodFurnitureV2.Wardrobe && state.getValue(WoodFurnitureV2.Wardrobe.HALF) == DoubleBlockHalf.UPPER)
                master = pos.below();
            else if (state.getBlock() instanceof WoodFurnitureV2.Wide && state.getValue(WoodFurnitureV2.Wide.PART) == WoodFurnitureV2.Wide.Part.RIGHT)
                master = WoodFurnitureV2.Wide.masterPosition(pos, state);
            else if (state.getBlock() instanceof com.antaurora.apofirstlight.cabin.Workbench && !com.antaurora.apofirstlight.cabin.Workbench.isMaster(state))
                master = com.antaurora.apofirstlight.cabin.Workbench.master(state, pos);   // AFL Workbench V1: the lower left cell
            else return accessor;
            Level level = block.getLevel();
            BlockState masterState = level.getBlockState(master);
            BlockEntity masterEntity = level.getBlockEntity(master);
            if (!masterState.is(state.getBlock()) || masterEntity == null) return accessor;
            return registration.blockAccessor().from(block).hit(block.getHitResult().withPosition(master)).blockState(masterState).blockEntity(masterEntity).build();
        });
    }
}
