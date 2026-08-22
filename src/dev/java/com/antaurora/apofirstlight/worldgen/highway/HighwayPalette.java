package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Concentrated V1A placeholder palette; replaceable without touching renderer geometry. */
public final class HighwayPalette {
    public static final BlockState CARRIAGEWAY = Blocks.BLACK_CONCRETE.defaultBlockState();
    public static final BlockState SHOULDER = Blocks.GRAY_CONCRETE.defaultBlockState();
    public static final BlockState MEDIAN = Blocks.STONE.defaultBlockState();
    public static final BlockState BASE = Blocks.STONE.defaultBlockState();
    public static final BlockState SUB_BASE = Blocks.GRAVEL.defaultBlockState();
    public static final BlockState FILL = Blocks.DIRT.defaultBlockState();
    public static final BlockState PIER = Blocks.STONE.defaultBlockState();

    private HighwayPalette() {
    }
}
