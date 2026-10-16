package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graded turf (docs/worldgen/highway_r4_sample_v1.md, E): engineered grass ground with a sloped top, for road medians, side
 * slopes and other graded earthworks where whole-block steps would leave a saw-tooth against a smooth pavement. A real
 * natural-ground block: grass top (biome tint), dirt below, shovel, drops dirt, no random ticks.
 * <p>
 * The top is given by four corner heights in 1/8 block: nw (x0 z0), ne (x1 z0), sw (x0 z1), se (x1 z1), 0..8 above the
 * block floor, or 4..12 (shift: the corners sit 0.5..1.5 above the floor, for tops that cross a whole-block height).
 * Earthworks quantise corner heights in world space first, so neighbouring blocks share their corners: the surface is
 * continuous across blocks. Collision: four 8 x 8 quadrant boxes, each at the mean of its corner and the block centre.
 */
public class GradedTurfBlock extends Block {
    public static final IntegerProperty NW = IntegerProperty.create("nw", 0, 8), NE = IntegerProperty.create("ne", 0, 8),
            SW = IntegerProperty.create("sw", 0, 8), SE = IntegerProperty.create("se", 0, 8);
    public static final BooleanProperty SHIFT = BooleanProperty.create("shift");
    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public GradedTurfBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NW, 8).setValue(NE, 8).setValue(SW, 8).setValue(SE, 8).setValue(SHIFT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NW, NE, SW, SE, SHIFT);
    }

    /** Corner heights above the block floor (blocks): nw, ne, sw, se. */
    public static float[] corners(BlockState state) {
        float base = state.getValue(SHIFT) ? 0.5F : 0;
        return new float[]{base + state.getValue(NW) / 8F, base + state.getValue(NE) / 8F, base + state.getValue(SW) / 8F, base + state.getValue(SE) / 8F};
    }

    /** The state for corner values 0..8 (relative to the floor or, with shift, to the floor + 0.5). */
    public BlockState with(int nw, int ne, int sw, int se, boolean shift) {
        return defaultBlockState().setValue(NW, nw).setValue(NE, ne).setValue(SW, sw).setValue(SE, se).setValue(SHIFT, shift);
    }

    private static VoxelShape shape(BlockState state) {
        return SHAPES.computeIfAbsent(state, s -> {
            float[] c = corners(s);
            float mid = (c[0] + c[1] + c[2] + c[3]) / 4;
            VoxelShape out = Shapes.empty();
            int[][] quads = {{0, 0, 0}, {1, 8, 0}, {2, 0, 8}, {3, 8, 8}};
            for (int[] q : quads) {
                double top = Math.max(0.5, (c[q[0]] + mid) / 2 * 16);
                out = Shapes.or(out, Block.box(q[1], 0, q[2], q[1] + 8, top, q[2] + 8));
            }
            return out.optimize();
        });
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /**
     * What the turf hides of its neighbours: only the floor. The block below never shows its top face under the turf; it
     * lay in the same plane wherever a top triangle reached the floor (all three corners 0), and the two fought (z-fighting
     * on steep fills, user 2026-10-15). The sides hide nothing (the turf may be lower than its neighbours' faces), and a
     * 1/16 floor is no full face for them.
     */
    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return FLOOR;
    }

    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 1, 16);
}
