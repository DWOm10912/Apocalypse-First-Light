package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.IndustrialElectricalBoxBlockEntity;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Industrial Electrical Box V2 (tools/build-industrial-electrical-box-v2.mjs): wall-mounted electrical cabinet, 9-slot
 * searchable container behind a hinged door with a quarter-turn latch. A locked box only offers "unlock" (anywhere on it);
 * unlocked and closed, the latch locks it again and the rest of the door opens it; open, the interior is searched / viewed
 * and the door closes it. World boxes start locked (default state), boxes placed by a player start unlocked. FACING is the side the door faces (the wall is behind it); ceiling mounting
 * was dropped with V2 because the Animated Block Mesh Runtime only turns horizontally. Shapes, interaction regions and
 * prompt anchors come from data/apocalypse_firstlight/mesh_shapes/industrial_electrical_box.json.
 */
public class IndustrialElectricalBoxBlock extends Block implements EntityBlock, AflMeshInteractionBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final ResourceLocation SHAPE_PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "industrial_electrical_box");
    private static final Set<BlockPos> PLAYER_DESTROYING = new HashSet<>();
    private static final Set<BlockPos> EXPLOSION_DESTROYING = new HashSet<>();

    public enum Action {
        NONE(null), UNLOCK("unlock"), LOCK("lock"), OPEN_DOOR("open"), CLOSE_DOOR("close"), SEARCH("search"), VIEW("view");
        private final String hintKey;
        Action(String hintKey) { this.hintKey = hintKey; }
        public String hintKey() { return hintKey; }
    }

    public IndustrialElectricalBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(LOCKED, true));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (!clickedFace.getAxis().isHorizontal() || !canAttach(context.getLevel(), context.getClickedPos(), clickedFace)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, clickedFace).setValue(LOCKED, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof IndustrialElectricalBoxBlockEntity box) {
            box.markPlacedByPlayer();
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
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

    /** Losing the wall drops the box item (unless a player or an explosion is already handling it); contents via onRemove. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite()
                && !neighborState.isFaceSturdy(level, neighborPos, state.getValue(FACING))) {
            if (!PLAYER_DESTROYING.contains(currentPos)
                    && !EXPLOSION_DESTROYING.remove(currentPos.immutable())
                    && level instanceof Level serverLevel
                    && !serverLevel.isClientSide()) {
                popResource(serverLevel, currentPos, new ItemStack(AflItems.INDUSTRIAL_ELECTRICAL_BOX.get()));
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        PLAYER_DESTROYING.add(position.immutable());
        try {
            if (!player.isCreative() && player.getMainHandItem().isCorrectToolForDrops(state)) {
                popResource(level, position, new ItemStack(AflItems.INDUSTRIAL_ELECTRICAL_BOX.get()));
            }
            super.playerWillDestroy(level, position, state, player);
        } finally {
            PLAYER_DESTROYING.remove(position.immutable());
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

    // ---- door and container ----

    /**
     * closed + locked: latch or door -> unlock (the door cannot open); closed + unlocked: latch -> lock, door -> open;
     * open: door -> close, interior -> search (hidden slots left) or view.
     */
    public static Action action(BlockState state, String region, IndustrialElectricalBoxBlockEntity box) {
        boolean open = state.getValue(OPEN);
        if (!open && ("latch".equals(region) || "door".equals(region))) {
            if (state.getValue(LOCKED)) return Action.UNLOCK;
            return "latch".equals(region) ? Action.LOCK : Action.OPEN_DOOR;
        }
        if ("door".equals(region)) return Action.CLOSE_DOOR;
        if ("interior".equals(region) && open) return box.isSearchCompleteForPrompt() ? Action.VIEW : Action.SEARCH;
        return Action.NONE;
    }

    @Override
    public String interactionHintKey(Level level, BlockState state, BlockPos pos, String region) {
        if (!(level.getBlockEntity(pos) instanceof IndustrialElectricalBoxBlockEntity box)) return null;
        String key = action(state, region, box).hintKey();
        return key == null ? null : "hint.apocalypse_firstlight.industrial_electrical_box." + key;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        var region = meshInteraction(state, pos, player);
        Action action = region == null || !(level.getBlockEntity(pos) instanceof IndustrialElectricalBoxBlockEntity box)
                ? Action.NONE : action(state, region.region(), box);
        if (action == Action.NONE) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        switch (action) {
            case UNLOCK -> setLocked(level, pos, state, false, player);
            case LOCK -> setLocked(level, pos, state, true, player);
            case OPEN_DOOR -> setOpen(level, pos, state, true, player);
            case CLOSE_DOOR -> setOpen(level, pos, state, false, player);
            default -> player.openMenu((IndustrialElectricalBoxBlockEntity) level.getBlockEntity(pos));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Door sounds are placed on the 10-tick door animation (tools/build-industrial-electrical-box-sounds-v1.mjs: the open
     * knock and the close impact land on the last frame), so the pitch only varies by +-2 %.
     */
    private static void setOpen(Level level, BlockPos pos, BlockState state, boolean open, Player player) {
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, pos, open ? AflSounds.INDUSTRIAL_ELECTRICAL_BOX_OPEN.get() : AflSounds.INDUSTRIAL_ELECTRICAL_BOX_CLOSE.get(),
                SoundSource.BLOCKS, 0.8F, 0.98F + level.random.nextFloat() * 0.04F);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    /** One latch click for both directions (it lands at the end of the 4-tick quarter turn); unlocking a touch higher. */
    private static void setLocked(Level level, BlockPos pos, BlockState state, boolean locked, Player player) {
        level.setBlock(pos, state.setValue(LOCKED, locked), Block.UPDATE_ALL);
        level.playSound(null, pos, AflSounds.INDUSTRIAL_ELECTRICAL_BOX_LATCH.get(), SoundSource.BLOCKS, 0.8F,
                (locked ? 0.96F : 1.04F) + level.random.nextFloat() * 0.02F);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
    }

    /** Every removal path (player, explosion, lost wall, /setblock): revealed slots drop, unseen world loot is lost. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof IndustrialElectricalBoxBlockEntity box) {
                box.dropContentsOnce();
            }
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Comparator fullness from revealed slots only, so it never hints at unsearched loot. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof IndustrialElectricalBoxBlockEntity box ? AflContainerSearch.revealedAnalogSignal(box) : 0;
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return meshSelectionShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return meshPhysicalShape(state);
    }

    /** The AFL Animated Block Mesh Runtime draws the box; the baked model is particle only. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IndustrialElectricalBoxBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, LOCKED);
    }
}
