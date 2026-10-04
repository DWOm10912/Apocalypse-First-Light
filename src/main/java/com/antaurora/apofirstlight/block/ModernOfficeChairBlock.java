package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.entity.OfficeChairEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Directional office chair as placed (a static baked model, tools/build-office-props-v2.mjs). Right-clicking it turns it into
 * an {@link OfficeChairEntity} at the same spot and seats the player: from then on it is that entity (rolls with W / S / A /
 * D, the seat follows the view, stays where it was left, hit to pick it up). Placing the chair item puts a block back.
 * <p>Outline: the mesh's bounding box (one clean box, no stepped outline); collision: seat with the column, backrest, arms.
 */
public final class ModernOfficeChairBlock extends HorizontalDirectionalBlock {
    private static final Map<Direction, VoxelShape> SHAPES =
            HorizontalShapeUtils.rotations(Block.box(1.35, 0.0, 2.8, 14.65, 18.75, 14.2));
    private static final Map<Direction, VoxelShape> COLLISION = HorizontalShapeUtils.rotations(chairCollision());

    public ModernOfficeChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        BlockPos floor = position.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        return direction == Direction.DOWN && !state.canSurvive(level, position)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, position, neighborPosition);
    }

    /** Sit down: the block becomes the chair entity (not while sneaking or riding, nor where the player may not build). */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (player.isSecondaryUseActive() || player.isPassenger()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!player.mayBuild() || !level.mayInteract(player, position)) return InteractionResult.PASS;
        var chair = OfficeChairEntity.fromBlock(level, position, state.getValue(FACING));
        level.removeBlock(position, false);
        if (!level.addFreshEntity(chair)) return InteractionResult.CONSUME;
        player.startRiding(chair);
        return InteractionResult.CONSUME;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position,
                                        CollisionContext context) {
        return COLLISION.get(state.getValue(FACING));
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

    /** Base + seat up to the cushion top, the tilted backrest's envelope, the two T-arms (facing north, sitter faces -Z). */
    private static VoxelShape chairCollision() {
        return Shapes.or(
                Block.box(2.95, 0.0, 2.85, 13.05, 8.75, 12.35),
                Block.box(3.4, 8.75, 11.5, 12.6, 18.75, 14.2),
                Block.box(1.35, 6.5, 4.0, 2.75, 10.85, 10.4),
                Block.box(13.25, 6.5, 4.0, 14.65, 10.85, 10.4)
        ).optimize();
    }
}
