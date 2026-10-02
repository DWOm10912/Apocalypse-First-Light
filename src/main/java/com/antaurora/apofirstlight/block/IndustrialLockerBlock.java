package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.IndustrialLockerBlockEntity;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import net.minecraft.resources.ResourceLocation;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class IndustrialLockerBlock extends Block implements EntityBlock, AflMeshInteractionBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Door state on both halves: drives the animated door, the shape state (closed / open) and screen validity. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Physical / selection shapes, interaction regions (door, interior) and prompt anchors, generated with the mesh. */
    public static final ResourceLocation SHAPE_PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "industrial_locker");

    /** What aiming at a region does in the current state. */
    public enum Action {
        NONE(null), OPEN_DOOR("open"), CLOSE_DOOR("close"), SEARCH("search"), VIEW("view");
        private final String hintKey;
        Action(String hintKey) { this.hintKey = hintKey; }
        /** hint.apocalypse_firstlight.locker.* suffix, or null. */
        public String hintKey() { return hintKey; }
    }
    private static final Set<BlockPos> EXPLOSION_DESTROYING = new HashSet<>();
    private static final Set<BlockPos> SUPPORT_DESTROYING = new HashSet<>();

    public IndustrialLockerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return null;
        }
        BlockPos lower = context.getClickedPos();
        BlockPos upper = lower.above();
        if (!context.getLevel().getBlockState(lower).canBeReplaced(context)
                || !context.getLevel().getBlockState(upper).canBeReplaced(context)
                || !context.getLevel().getBlockState(lower.below()).isFaceSturdy(context.getLevel(), lower.below(), Direction.UP)) {
            return null;
        }
        Direction front = context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, front).setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        level.setBlock(position.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (!level.isClientSide() && level.getBlockEntity(position) instanceof IndustrialLockerBlockEntity locker) {
            locker.markPlacedByPlayer();
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = level.getBlockState(position.below());
            return lower.is(this)
                    && lower.getValue(HALF) == DoubleBlockHalf.LOWER
                    && lower.getValue(FACING) == state.getValue(FACING);
        }
        return level.getBlockState(position.below()).isFaceSturdy(level, position.below(), Direction.UP);
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
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && direction == Direction.DOWN
                && !neighborState.isFaceSturdy(level, neighborPos, Direction.UP)) {
            destroyFromSupport(level, currentPos);
            return Blocks.AIR.defaultBlockState();
        }
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER && direction == Direction.DOWN
                && !neighborState.is(this)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
        var region = meshInteraction(state, position, player);
        Action action = region == null || !(level.getBlockEntity(lower) instanceof IndustrialLockerBlockEntity locker)
                ? Action.NONE : action(state, region.region(), locker);
        if (action == Action.NONE) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockState lowerState = level.getBlockState(lower);
        if (!lowerState.is(this)) {
            return InteractionResult.CONSUME;
        }
        switch (action) {
            case OPEN_DOOR -> setOpen(level, lower, lowerState, true, player);
            case CLOSE_DOOR -> setOpen(level, lower, lowerState, false, player);
            default -> player.openMenu((IndustrialLockerBlockEntity) level.getBlockEntity(lower));
        }
        return InteractionResult.CONSUME;
    }

    private static void setOpen(Level level, BlockPos lower, BlockState lowerState, boolean open, Player player) {
        // world loot is rolled as the door opens, so the goods inside show how much it holds (container goods)
        if (open && level.getBlockEntity(lower) instanceof IndustrialLockerBlockEntity locker) locker.unpackLootTable(player);
        level.setBlock(lower, lowerState.setValue(OPEN, open), Block.UPDATE_ALL);
        BlockState upper = level.getBlockState(lower.above());
        if (upper.is(lowerState.getBlock())) {
            level.setBlock(lower.above(), upper.setValue(OPEN, open), Block.UPDATE_ALL);
        }
        level.playSound(null, lower.above(), open ? AflSounds.INDUSTRIAL_LOCKER_OPEN.get() : AflSounds.INDUSTRIAL_LOCKER_CLOSE.get(),
                SoundSource.BLOCKS, 0.8F, 0.96F + level.random.nextFloat() * 0.08F);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, lower);
    }

    /**
     * Region + state -> action, shared by the server use() and the client world interaction prompt:
     * door + closed -> open; door + open -> close; interior + open -> search (hidden slots left) or view.
     */
    public static Action action(BlockState state, String region, IndustrialLockerBlockEntity locker) {
        boolean open = state.getValue(OPEN);
        if ("door".equals(region)) {
            return open ? Action.CLOSE_DOOR : Action.OPEN_DOOR;
        }
        if ("interior".equals(region) && open) {
            return locker.isSearchCompleteForPrompt() ? Action.VIEW : Action.SEARCH;
        }
        return Action.NONE;
    }

    @Override
    public String interactionHintKey(Level level, BlockState state, BlockPos pos, String region) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (!(level.getBlockEntity(lower) instanceof IndustrialLockerBlockEntity locker)) return null;
        String key = action(state, region, locker).hintKey();
        return key == null ? null : "hint.apocalypse_firstlight.locker." + key;
    }

    // ---- AFL Mesh Shape runtime ----

    @Override
    public ResourceLocation meshShapeProfile() {
        return SHAPE_PROFILE;
    }

    @Override
    public String meshShapeState(BlockState state) {
        return state.getValue(OPEN) ? "open" : "closed";
    }

    @Override
    public int meshShapeCell(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? 1 : 0;
    }

    /** Lower half: the AFL Animated Block Mesh Runtime draws the whole locker; the baked model is particle only. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? RenderShape.ENTITYBLOCK_ANIMATED : super.getRenderShape(state);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
        if (!player.isCreative() && level.getBlockState(lower).getBlock() == this
                && player.getMainHandItem().isCorrectToolForDrops(state)) {
            popResource(level, lower, new ItemStack(AflItems.INDUSTRIAL_LOCKER.get()));
        }
        dropContents(level, lower);
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER && level.getBlockState(lower).is(this)) {
            level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        super.playerWillDestroy(level, position, state, player);
    }

    private static void dropContents(LevelAccessor level, BlockPos lower) {
        if (level instanceof Level serverLevel && serverLevel.getBlockEntity(lower) instanceof IndustrialLockerBlockEntity locker) {
            locker.dropContentsOnce();
        }
    }

    private void destroyFromSupport(LevelAccessor level, BlockPos lower) {
        BlockPos canonicalLower = lower.immutable();
        if (!SUPPORT_DESTROYING.add(canonicalLower)) {
            return;
        }
        try {
            if (level instanceof Level serverLevel && !serverLevel.isClientSide()
                    && !EXPLOSION_DESTROYING.remove(canonicalLower)) {
                popResource(serverLevel, canonicalLower, new ItemStack(AflItems.INDUSTRIAL_LOCKER.get()));
                dropContents(serverLevel, canonicalLower);
            }

            BlockPos upper = canonicalLower.above();
            if (level.getBlockState(upper).is(this)
                    && level.getBlockState(upper).getValue(HALF) == DoubleBlockHalf.UPPER) {
                level.setBlock(upper, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        } finally {
            SUPPORT_DESTROYING.remove(canonicalLower);
        }
    }

    public static void markExplosion(BlockPos lower) {
        EXPLOSION_DESTROYING.add(lower.immutable());
    }

    public static void clearExplosionMarks() {
        EXPLOSION_DESTROYING.clear();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return meshSelectionShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return meshPhysicalShape(state);
    }

    @Override
    @Nullable
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new IndustrialLockerBlockEntity(position, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, OPEN);
    }
}
