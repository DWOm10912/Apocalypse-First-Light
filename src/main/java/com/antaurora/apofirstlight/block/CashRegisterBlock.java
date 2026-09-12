package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.client.CashRegisterParticleExtensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;

/** One-block countertop POS fixture, with no inventory or interaction in V1. */
public final class CashRegisterBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape NORTH = Shapes.or(
            // Cash drawer and perimeter base.
            Block.box(0.975, 0.0, 1.595, 15.025, 3.25, 14.075),
            // Covered service housing to the left of the operator.
            Block.box(10.0, 3.25, 2.85, 14.35, 6.2, 12.9),
            // Sloped keypad wedge; one stable envelope instead of key-by-key collision.
            Block.box(1.85, 3.25, 3.6, 9.75, 7.0, 12.0),
            Block.box(5.25, 7.0, 11.7, 7.9, 8.92, 13.0),
            Block.box(2.6, 8.6, 11.6, 9.3, 11.93, 13.08)
    ).optimize();
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(NORTH);

    public CashRegisterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public void initializeClient(Consumer<IClientBlockExtensions> consumer) {
        consumer.accept(new CashRegisterParticleExtensions());
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        BlockPos support = position.below();
        return level.getBlockState(support).isFaceSturdy(level, support, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        return direction == Direction.DOWN && !state.canSurvive(level, position)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, position, neighborPosition);
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
