package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.CheckoutCounterGateBlockEntity;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Checkout Counter V1 pass-through gate: a counter section whose top lifts and folds over the neighbouring counter on the
 * hinge side while the swing door below opens toward the cashier side. FACING is the customer side; HINGE left is the
 * customer-facing left (FACING's counter-clockwise neighbour), chosen on placement toward a counter. Counters treat the
 * gate as part of their line (no end cap toward it); it never forms corners and holds nothing. Animated (2026-10-04): the
 * block entity draws it through the AFL Animated Block Mesh Runtime (CheckoutCounterGateBlockEntity); the block model is
 * particle-only. The shapes switch at once, as a door's do; the sounds follow the animation
 * (tools/build-checkout-counter-gate-sounds-v1.mjs).
 */
public class CheckoutCounterGateBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;

    // canonical (facing north, hinge left = -X), px: closed like a counter section; open, only the door along the hinge side
    private static final Map<Direction, VoxelShape> CLOSED = CheckoutCounterBlock.rotations(Block.box(0, 0, 2.15, 16, 16, 15.6));
    private static final Map<Direction, VoxelShape> OPEN_LEFT = CheckoutCounterBlock.rotations(Block.box(0, 0, 3.2, 1.3, 14.8, 16));
    private static final Map<Direction, VoxelShape> OPEN_RIGHT = CheckoutCounterBlock.rotations(Block.box(14.7, 0, 3.2, 16, 14.8, 16));
    /** Open pitch varies a little, as vanilla doors do. */
    private static final float PITCH_SPREAD = 0.06F;

    public CheckoutCounterGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(HINGE, DoorHingeSide.LEFT));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean left = CheckoutCounterBlock.joins(level.getBlockState(pos.relative(facing.getCounterClockWise())));
        boolean right = CheckoutCounterBlock.joins(level.getBlockState(pos.relative(facing.getClockWise())));
        return defaultBlockState().setValue(FACING, facing).setValue(HINGE, !left && right ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        boolean open = !state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        level.playSound(player, pos, open ? AflSounds.CHECKOUT_COUNTER_GATE_OPEN.get() : AflSounds.CHECKOUT_COUNTER_GATE_CLOSE.get(), SoundSource.BLOCKS,
                1.0F, 1.0F + (level.getRandom().nextFloat() - 0.5F) * PITCH_SPREAD);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Moving, glass and lit parts: the AFL Animated Block Mesh Runtime renderer; resting parts: the chunk model (client/blockmesh/AflStaticMeshModel, docs/dev/render_performance_v1.md). */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CheckoutCounterGateBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (!state.getValue(OPEN)) return CLOSED.get(facing);
        return (state.getValue(HINGE) == DoorHingeSide.LEFT ? OPEN_LEFT : OPEN_RIGHT).get(facing);
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? mirrored
                : mirrored.setValue(HINGE, state.getValue(HINGE) == DoorHingeSide.LEFT ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HINGE);
    }
}
