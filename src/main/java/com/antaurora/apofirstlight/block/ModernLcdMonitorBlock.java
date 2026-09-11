package com.antaurora.apofirstlight.block;

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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Desktop monitor that lowers by 2.5 model units when supported by the 13.5-unit office desk. */
public final class ModernLcdMonitorBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty LOWERED = BooleanProperty.create("lowered");

    private static final VoxelShape NORMAL_NORTH = Shapes.or(
            Block.box(4.3, 0.0, 5.55, 11.7, 0.7, 10.45),
            Block.box(6.6, 0.55, 7.85, 9.4, 6.8, 9.4),
            Block.box(0.0, 3.0, 6.85, 16.0, 12.8, 9.2)
    ).optimize();
    private static final VoxelShape LOWERED_NORTH = Shapes.or(
            Block.box(4.3, -2.5, 5.55, 11.7, -1.8, 10.45),
            Block.box(6.6, -1.95, 7.85, 9.4, 4.3, 9.4),
            Block.box(0.0, 0.5, 6.85, 16.0, 10.3, 9.2)
    ).optimize();
    private static final Map<Direction, VoxelShape> NORMAL_SHAPES = HorizontalShapeUtils.rotations(NORMAL_NORTH);
    private static final Map<Direction, VoxelShape> LOWERED_SHAPES = HorizontalShapeUtils.rotations(LOWERED_NORTH);

    public ModernLcdMonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LOWERED, false));
    }

    private static boolean isDesk(LevelReader level, BlockPos position) {
        return level.getBlockState(position.below()).getBlock() instanceof ModernOfficeDeskBlock;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LOWERED, isDesk(context.getLevel(), context.getClickedPos()));
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        if (isDesk(level, position)) return true;
        BlockPos floor = position.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        if (direction == Direction.DOWN) {
            if (!state.canSurvive(level, position)) return Blocks.AIR.defaultBlockState();
            return state.setValue(LOWERED, isDesk(level, position));
        }
        return super.updateShape(state, direction, neighbor, level, position, neighborPosition);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return (state.getValue(LOWERED) ? LOWERED_SHAPES : NORMAL_SHAPES).get(state.getValue(FACING));
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
        builder.add(FACING, LOWERED);
    }
}
