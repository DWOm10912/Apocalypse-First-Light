package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.GeneratorInletBoxBlockEntity;
import com.antaurora.apofirstlight.energy.PlugCord;
import com.antaurora.apofirstlight.energy.PowerPlugs;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Generator Inlet Box (发电机进线盒, docs/models/generator_inlet_v1.md; forest start home E1, user 2026-10-13): a 30 A
 * power inlet box on an outside wall (FACING away from it) with its 15 m cord, how a portable genset feeds a house. The
 * cord's L14-30 connector stays seated in the inlet; its L14-30P plug goes only into the genset's L14-30R (PlugCord.Kind).
 * Power that comes in through the cord goes on to the panel whose footprint the wall column behind the box lies in
 * ({@link GeneratorInletBoxBlockEntity}), as the service meter box does. Right-click: the weather hood up or down (OPEN);
 * sneak right-click with an empty hand: take the plug off the hook (out of the genset if it is in one), or put it back.
 * Carried too far (the cord's length + 1.5 m) the plug drops back onto the hook. Mining: pickaxe + diamond tier
 * (industrial steel). The hood's sound is a placeholder vanilla sound until a recorded take replaces it.
 */
public class GeneratorInletBoxBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    // canonical (facing north, the wall at z 16), px, tools/build-generator-inlet-v1.mjs SELECTION: the box with the shut
    // hood, the connector under it, the hook; the lifted hood; the coil while the cord hangs on the hook
    private static final VoxelShape BODY = Shapes.or(Block.box(6.59, 6.03, 13.3, 9.41, 9.92, 16), Block.box(7.51, 5.53, 13.45, 8.49, 7.59, 14.97),
            Block.box(7.68, 4.08, 14.52, 8.32, 5.07, 16));
    private static final VoxelShape HOOD_OPEN = Block.box(6.59, 9.92, 11.37, 9.41, 12.45, 15.52);
    private static final VoxelShape COIL = Block.box(5.36, 0, 13.28, 10.63, 5.74, 15.87);
    private static final Map<Direction, VoxelShape> SHUT = HorizontalShapeUtils.rotations(BODY), LIFTED = HorizontalShapeUtils.rotations(Shapes.or(BODY, HOOD_OPEN)),
            SHUT_COILED = HorizontalShapeUtils.rotations(Shapes.or(BODY, COIL)), LIFTED_COILED = HorizontalShapeUtils.rotations(Shapes.or(BODY, HOOD_OPEN, COIL));

    public GeneratorInletBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (!face.getAxis().isHorizontal()) return null;
        BlockState state = defaultBlockState().setValue(FACING, face);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** Empty hand: the hood up or down; sneaking: the cord's plug off the hook / back onto it. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) return PowerPlugs.useDevice(level, pos, server);
        boolean open = !state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.35F, 1.6F);   // placeholder for a recorded hood take
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        return InteractionResult.CONSUME;
    }

    /** The cord hangs coiled on the hook (its plug in no socket and no hand). */
    public static boolean coiled(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PlugCord.Owner owner && owner.plugCord().stowed();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean open = state.getValue(OPEN), coil = coiled(level, pos);
        return (open ? coil ? LIFTED_COILED : LIFTED : coil ? SHUT_COILED : SHUT).get(state.getValue(FACING));
    }

    /** A small wall fixture: nothing to bump into. */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof PlugCord.Owner owner) owner.plugCord().release();
        super.onRemove(state, level, pos, replacement, moved);
    }

    /** Moving parts: the AFL Animated Block Mesh Runtime renderer; resting parts: the chunk model (docs/dev/render_performance_v1.md). */
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeneratorInletBoxBlockEntity(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != AflBlockEntities.GENERATOR_INLET_BOX.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<GeneratorInletBoxBlockEntity>) (l, p, s, inlet) -> inlet.serverTick();
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }
}
