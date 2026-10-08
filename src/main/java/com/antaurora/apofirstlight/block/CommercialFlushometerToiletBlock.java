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

/**
 * Static, single-block commercial toilet; model front and shape front are both NORTH. Restroom Fixtures V2
 * (docs/models/restroom_fixtures_v2.md): real size, dry, exposed flushometer; the boxes are the generator's
 * (tools/build-restroom-fixtures-v2.mjs SHAPES.toilet, which checks every vertex lies inside one).
 */
public final class CommercialFlushometerToiletBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape NORTH = Shapes.or(
            // pedestal, the bowl flaring out of it, the bowl with its seat
            Block.box(6.05, 0, 7.4, 9.95, 3.4, 12.85),
            Block.box(5.75, 3.3, 5.85, 10.25, 4.75, 12.5),
            Block.box(5.0, 4.6, 4.4, 11.0, 7.25, 13.35),
            // neck behind the bowl, the vacuum-breaker tube, the valve with its handle and the control stop at the wall
            Block.box(6.5, 4.75, 12.5, 9.5, 6.95, 15.5),
            Block.box(7.4, 6.9, 13.7, 8.6, 11.0, 14.8),
            Block.box(4.7, 10.7, 13.5, 11.4, 13.85, 16.0)
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
