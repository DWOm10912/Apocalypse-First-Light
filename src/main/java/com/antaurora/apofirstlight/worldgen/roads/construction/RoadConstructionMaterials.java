package com.antaurora.apofirstlight.worldgen.roads.construction;

import com.antaurora.apofirstlight.block.RoadCurbBlock;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Permanent road asset contract; construction chooses heights without depending on model/texture resources. */
public final class RoadConstructionMaterials {
    public enum Material { ASPHALT, SIDEWALK, UTILITY }

    private RoadConstructionMaterials() { }

    public static BlockState surface(Material material, int layers) {
        checkLayers(layers);
        BlockState state = switch (material) {
            case ASPHALT -> AflBlocks.ROAD_ASPHALT_SURFACE.get().defaultBlockState();
            case SIDEWALK -> AflBlocks.ROAD_SIDEWALK_SURFACE.get().defaultBlockState();
            case UTILITY -> AflBlocks.ROAD_UTILITY_SURFACE.get().defaultBlockState();
        };
        return state.setValue(RoadSurfaceBlock.LAYERS, layers);
    }

    public static BlockState asphalt(int layers) { return surface(Material.ASPHALT, layers); }
    public static BlockState sidewalk(int layers) { return surface(Material.SIDEWALK, layers); }
    public static BlockState utility(int layers) { return surface(Material.UTILITY, layers); }

    public static BlockState curb(int layers, Direction facing, RoadCurbBlock.Shape shape) {
        checkLayers(layers);
        if (facing.getAxis().isVertical()) throw new IllegalArgumentException("Road curb facing must be horizontal");
        return AflBlocks.ROAD_CURB.get().defaultBlockState().setValue(RoadCurbBlock.LAYERS, layers)
                .setValue(RoadCurbBlock.FACING, facing).setValue(RoadCurbBlock.SHAPE, shape);
    }

    private static void checkLayers(int layers) {
        if (layers < 1 || layers > 16) throw new IllegalArgumentException("Road surface layers must be 1..16");
    }
}
