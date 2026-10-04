package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fuel Island Kit V1 straight curb (tools/build-fuel-island-v1.mjs, docs/models/fuel_island_kit_v1.md): one cell of the
 * concrete island the fuel dispenser stands on, 3 px high with a steel angle on its long top edges, the same section as
 * the dispenser's own segment. FACING as the dispenser's: the island runs along the facing's clockwise side.
 */
public class FuelIslandCurbBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);

    public FuelIslandCurbBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Island pieces: the curb, the end and the fuel dispenser's bottom cells (its own segment). */
    public static boolean isIslandPiece(BlockState state) {
        if (state.getBlock() instanceof FuelIslandCurbBlock) return true;
        if (state.getBlock() instanceof FuelDispenserBlock) {
            FuelDispenserBlock.Cell cell = state.getValue(FuelDispenserBlock.CELL);
            return cell == FuelDispenserBlock.Cell.A0 || cell == FuelDispenserBlock.Cell.B0;
        }
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
