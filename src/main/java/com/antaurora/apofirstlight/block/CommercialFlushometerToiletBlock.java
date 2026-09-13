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
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Static, single-block commercial toilet; model front and shape front are both NORTH. */
public final class CommercialFlushometerToiletBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape NORTH = Shapes.or(
            // Floor base, pedestal, and ceramic support below the bowl.
            Block.box(5.25, 0, 6.65, 10.75, .65, 12.85),
            Block.box(5.55, .6, 6.9, 10.45, 4.35, 12.55),
            Block.box(5.45, 4.25, 4.55, 10.55, 6.35, 12.75),
            Block.box(4.2, 5.6, 3.35, 11.8, 6.55, 12.85),
            // Four sides around the open bowl. Do not span the mouth with a solid box.
            Block.box(3.3, 6.35, 2.05, 5.2, 8.65, 12.85),
            Block.box(10.8, 6.35, 2.05, 12.7, 8.65, 12.85),
            Block.box(5.15, 6.35, 1.4, 10.85, 8.65, 3.35),
            Block.box(5.15, 6.35, 11.1, 10.85, 8.65, 13.15),
            // Rear ceramic connection, vertical pipe, valve, and wall-side fitting.
            Block.box(5.8, 5, 11.9, 10.2, 7.95, 15.3),
            Block.box(7, 7.9, 13.35, 9, 12.1, 15.1),
            Block.box(6.65, 11.65, 12.8, 9.35, 15.75, 15.5),
            Block.box(6.8, 12.6, 15.5, 9.2, 15, 16)
    ).optimize();
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(NORTH);

    public CommercialFlushometerToiletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return getShape(state, level, position, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
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
