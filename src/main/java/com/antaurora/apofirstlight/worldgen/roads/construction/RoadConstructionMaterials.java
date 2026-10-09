package com.antaurora.apofirstlight.worldgen.roads.construction;

import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Road surface materials for V1-B construction. Since 2026-10-08 the quantized road blocks are gone (the user had them
 * removed; road surfaces return as meshes, docs/worldgen/road_surface_assets_v1.md), so every surface is a full-block
 * placeholder and the planned layer height (1..16 sixteenths) is checked but not built: asphalt = {@code asphalt}, sidewalk
 * and curb = {@code reinforced_concrete}, utility strip = grass. The planner still decides kinds, curb shapes and heights.
 */
public final class RoadConstructionMaterials {
    public enum Material { ASPHALT, SIDEWALK, UTILITY }
    /** The curb piece the planner chose (kept from the removed road_curb block's shape property). */
    public enum CurbShape { STRAIGHT, INNER, OUTER, DRIVEWAY }

    private RoadConstructionMaterials() { }

    public static BlockState surface(Material material, int layers) {
        checkLayers(layers);
        return switch (material) {
            case ASPHALT -> AflBlocks.ASPHALT.get().defaultBlockState();
            case SIDEWALK -> AflBlocks.REINFORCED_CONCRETE.get().defaultBlockState();
            case UTILITY -> Blocks.GRASS_BLOCK.defaultBlockState();
        };
    }

    public static BlockState asphalt(int layers) { return surface(Material.ASPHALT, layers); }
    public static BlockState sidewalk(int layers) { return surface(Material.SIDEWALK, layers); }
    public static BlockState utility(int layers) { return surface(Material.UTILITY, layers); }

    public static BlockState curb(int layers, Direction facing, CurbShape shape) {
        checkLayers(layers);
        if (facing.getAxis().isVertical()) throw new IllegalArgumentException("Road curb facing must be horizontal");
        return AflBlocks.REINFORCED_CONCRETE.get().defaultBlockState();
    }

    private static void checkLayers(int layers) {
        if (layers < 1 || layers > 16) throw new IllegalArgumentException("Road surface layers must be 1..16");
    }
}
