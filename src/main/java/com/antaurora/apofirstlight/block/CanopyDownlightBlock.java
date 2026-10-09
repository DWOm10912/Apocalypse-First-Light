package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Site Lighting V1 canopy downlight (docs/models/site_lighting_v1.md, tools/build-site-lighting-v1.mjs): a 150 mm
 * surface-mount cylinder under the storefront's metal eyebrow canopy. The canopy's panel lies 31 mm above the floor of its
 * cell, so the light hangs in the cell below and reaches up into the panel; under an ordinary ceiling its top hides in the
 * block. On the building's lighting circuit ({@link BuildingLightBlock}, wiring looked up in its own cell, which the canopy
 * roofs), dark by day (photocell). Light 15 when lit.
 */
public class CanopyDownlightBlock extends BuildingLightBlock {
    // px, tools/build-site-lighting-v1.mjs DOWNLIGHT (r 75 mm, 948..1031 mm; the shape stops at the cell's top)
    private static final VoxelShape SHAPE = Block.box(6.8, 15.17, 6.8, 9.2, 16, 9.2);

    public CanopyDownlightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** Under an eyebrow canopy panel, or any ceiling with a sturdy underside. */
    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState ceiling = level.getBlockState(above);
        return ceiling.getBlock() instanceof MetalEyebrowCanopyBlock || ceiling.isFaceSturdy(level, above, Direction.DOWN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.UP && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected boolean photocell() {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
