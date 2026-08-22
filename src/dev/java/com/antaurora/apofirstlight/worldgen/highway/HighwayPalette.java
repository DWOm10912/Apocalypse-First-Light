package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Concentrated palette for the procedural highway renderer. */
public final class HighwayPalette {
    public static final BlockState CARRIAGEWAY = Blocks.BLACK_CONCRETE.defaultBlockState();
    public static final BlockState SHOULDER = Blocks.GRAY_CONCRETE.defaultBlockState();
    public static final BlockState MEDIAN = Blocks.STONE.defaultBlockState();
    public static final BlockState BASE = Blocks.STONE.defaultBlockState();
    public static final BlockState SUB_BASE = Blocks.GRAVEL.defaultBlockState();
    public static final BlockState FILL = Blocks.DIRT.defaultBlockState();
    public static final BlockState ASPHALT = AflBlocks.ASPHALT.get().defaultBlockState();
    public static final BlockState REINFORCED_CONCRETE = AflBlocks.REINFORCED_CONCRETE.get().defaultBlockState();
    public static final BlockState REINFORCED_CONCRETE_SLAB = AflBlocks.REINFORCED_CONCRETE_SLAB.get().defaultBlockState()
            .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    public static final BlockState PIER = REINFORCED_CONCRETE;

    private HighwayPalette() {
    }
}
