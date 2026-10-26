package com.antaurora.apofirstlight.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Natural ground N1 (docs/worldgen/natural_ground_n1_design.md 2, 6): AFL's own natural ground, drawn like the rocks
 * (CC0 photos at 128 texels per metre, LabPBR; tools/ground/build-natural-ground.mjs). Terrain only: no loot (the shovel
 * gives bulk material, Earthwork), the item in the creative tab only. Terrain Smooth slopes it and the earthwork edits it as
 * it does vanilla's soils.
 * <p>
 * Wet ground (river / lake V1 R0, docs/worldgen/river_lake_v1_design.md 4.9): {@link #WATERLOGGED} holds water above its
 * sloped top, so a bed or a shore slopes down into the water (Terrain Smooth reads it as SmoothRules#WET: natural ground
 * the water over it does not pin). Its occlusion shape is empty: vanilla draws a waterlogged block's water only where the
 * block itself does not occlude it, and a sloped top lies below the cube's.
 */
public class NaturalGroundBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** Its bulk material (BulkMaterials id: what a shovel takes out of it), or null where a shovel takes none (snow). */
    public final String bulk;

    public NaturalGroundBlock(String bulk, Properties properties) {
        super(properties);
        this.bulk = bulk;
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, false));
    }

    /** Loose ground a shovel digs (Earthwork#soil). */
    public boolean loose() {
        return bulk != null;
    }

    /** Natural ground holding water (SmoothRules#WET). */
    public static boolean wet(BlockState s) {
        return s.getBlock() instanceof NaturalGroundBlock && s.getValue(WATERLOGGED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(WATERLOGGED) ? Shapes.empty() : super.getOcclusionShape(state, level, pos);
    }
}
