package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.dev.highwaymesh.DevHighwayMeshRegistration;
import com.antaurora.apofirstlight.dev.highwaymesh.DevRoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Material roles of the R4 sample (docs/worldgen/highway_r4_sample_v1.md, D), in one place: what each earthworks kind is
 * built from, and which existing blocks count as natural ground, plants or natural trees. Replacing a material later (a
 * production collision surface, a PBR aggregate block) changes only this class.
 * <p>
 * The natural lists say what may be replaced in an authorised test world. They cannot tell a natural block from one a
 * player placed; that is why construction is limited to explicitly authorised AFL_R4_ test worlds (section F).
 */
public final class R4Materials {
    private R4Materials() {}

    public static BlockState state(R4Earthworks.Kind kind, int data) {
        return switch (kind) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case STONE -> Blocks.STONE.defaultBlockState();
            case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case ASPHALT -> AflBlocks.ASPHALT.get().defaultBlockState();
            case COLLISION -> DevHighwayMeshRegistration.block().defaultBlockState().setValue(DevRoadSurfaceBlock.LAYERS, data);
            case TURF -> ((GradedTurfBlock) AflBlocks.GRADED_TURF.get()).with(data & 15, data >> 4 & 15, data >> 8 & 15, data >> 12 & 15, (data >> 16 & 1) != 0);
        };
    }

    private static final Set<Block> GROUND = Set.of(
            Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL, Blocks.MUD, Blocks.CLAY,
            Blocks.GRAVEL, Blocks.SAND, Blocks.RED_SAND, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.MOSS_BLOCK,
            Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE, Blocks.DEEPSLATE,
            Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.REDSTONE_ORE, Blocks.LAPIS_ORE,
            Blocks.EMERALD_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.DEEPSLATE_GOLD_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.DEEPSLATE_LAPIS_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE);
    private static final Set<Block> GRASSY = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK);
    private static final Set<Block> PLANTS = Set.of(Blocks.GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH,
            Blocks.SWEET_BERRY_BUSH, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.SNOW);

    /** Natural ground that earthworks may dig out or build over (no fluid, no block entity: checked separately). */
    public static boolean ground(BlockState s) { return GROUND.contains(s.getBlock()) || s.is(net.minecraftforge.common.Tags.Blocks.ORES); }

    /** A ground block that dies when buried. */
    public static boolean grassy(BlockState s) { return GRASSY.contains(s.getBlock()); }

    /** Small plants: removed with the ground they stand on. */
    public static boolean plant(BlockState s) {
        return PLANTS.contains(s.getBlock()) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.SAPLINGS);
    }

    /** A natural tree part: any log, or leaves that the game placed (not persistent). */
    public static boolean treeLog(BlockState s) { return s.is(BlockTags.LOGS); }

    public static boolean naturalLeaves(BlockState s) {
        return s.is(BlockTags.LEAVES) && s.hasProperty(LeavesBlock.PERSISTENT) && !s.getValue(LeavesBlock.PERSISTENT);
    }

    public static boolean playerLeaves(BlockState s) {
        return s.is(BlockTags.LEAVES) && (!s.hasProperty(LeavesBlock.PERSISTENT) || s.getValue(LeavesBlock.PERSISTENT));
    }

    /**
     * A change the live world makes by itself between two looks at the same position (R4Journal): grass spreading onto
     * dirt or eaten / dying back to dirt (and its snowy flag), a plant eaten or trampled, natural leaves decayed, snow
     * falling or melting. Both states are already replaceable; nothing new becomes replaceable. Player edits (any other
     * block, any other transition) are not natural and fail closed.
     */
    public static boolean naturalChange(BlockState was, BlockState now) {
        if (was == now) return false;
        if (!was.getFluidState().isEmpty() || !now.getFluidState().isEmpty() || was.hasBlockEntity() || now.hasBlockEntity()) return false;
        boolean soilWas = was.is(Blocks.DIRT) || was.is(Blocks.GRASS_BLOCK), soilNow = now.is(Blocks.DIRT) || now.is(Blocks.GRASS_BLOCK);
        if (soilWas && soilNow) return true;
        if (now.isAir() && (plant(was) || naturalLeaves(was))) return true;
        return was.isAir() && now.is(Blocks.SNOW);
    }

    /** Anything that may stand above the ground and is cleared with it (air, plants, natural tree parts). */
    public static boolean clearable(BlockState s) {
        return s.isAir() || plant(s) || treeLog(s) || naturalLeaves(s);
    }
}
