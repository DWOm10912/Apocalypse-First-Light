package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class IndustrialUtilityLightBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", direction -> direction != Direction.UP);
    private static final VoxelShape CEILING_SHAPE = Shapes.box(3 / 16.0, 13 / 16.0, 3 / 16.0, 13 / 16.0, 1.0, 13 / 16.0);
    private static final VoxelShape NORTH_SHAPE = Shapes.box(3 / 16.0, 3 / 16.0, 13 / 16.0, 13 / 16.0, 13 / 16.0, 1.0);
    private static final VoxelShape SOUTH_SHAPE = Shapes.box(3 / 16.0, 3 / 16.0, 0.0, 13 / 16.0, 13 / 16.0, 3 / 16.0);
    private static final VoxelShape EAST_SHAPE = Shapes.box(0.0, 3 / 16.0, 3 / 16.0, 3 / 16.0, 13 / 16.0, 13 / 16.0);
    private static final VoxelShape WEST_SHAPE = Shapes.box(13 / 16.0, 3 / 16.0, 3 / 16.0, 1.0, 13 / 16.0, 13 / 16.0);
    private static final Set<BlockPos> PLAYER_DESTROYING = new HashSet<>();
    private static final Set<BlockPos> EXPLOSION_DESTROYING = new HashSet<>();

    public IndustrialUtilityLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == Direction.UP || !canAttach(context.getLevel(), context.getClickedPos(), clickedFace)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, clickedFace);
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos position) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPosition = position.relative(facing.getOpposite());
        return level.getBlockState(supportPosition).isFaceSturdy(level, supportPosition, facing);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite()
                && !neighborState.isFaceSturdy(level, neighborPos, state.getValue(FACING))) {
            if (!PLAYER_DESTROYING.contains(currentPos)
                    && !EXPLOSION_DESTROYING.remove(currentPos.immutable())
                    && level instanceof Level serverLevel
                    && !serverLevel.isClientSide()) {
                popResource(serverLevel, currentPos, new ItemStack(AflItems.INDUSTRIAL_UTILITY_LIGHT.get()));
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position,
                               net.minecraft.world.phys.shapes.CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> CEILING_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> Shapes.empty();
        };
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        PLAYER_DESTROYING.add(position);
        try {
            if (!player.isCreative() && player.getMainHandItem().isCorrectToolForDrops(state)) {
                popResource(level, position, new ItemStack(AflItems.INDUSTRIAL_UTILITY_LIGHT.get()));
            }
            super.playerWillDestroy(level, position, state, player);
        } finally {
            PLAYER_DESTROYING.remove(position);
        }
    }

    public static void markExplosion(BlockPos position) {
        EXPLOSION_DESTROYING.add(position.immutable());
    }

    public static void clearExplosionMarks() {
        EXPLOSION_DESTROYING.clear();
    }

    private static boolean canAttach(Level level, BlockPos position, Direction facing) {
        BlockPos supportPosition = position.relative(facing.getOpposite());
        return level.getBlockState(supportPosition).isFaceSturdy(level, supportPosition, facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
