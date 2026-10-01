package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

/**
 * Lead Chest V3 (tools/build-lead-chest-v3.mjs, stainless-clad lead shielding box; behaviour unchanged since V2): single-block shielded box. Open the lid first (aim at the cask), then
 * search / view through the opening; aiming at the standing lid closes it. No double chests, not a vanilla chest.
 * Shapes, interaction regions and prompt anchors come from data/apocalypse_firstlight/mesh_shapes/lead_chest.json.
 */
public class LeadChestBlock extends Block implements EntityBlock, AflMeshInteractionBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final ResourceLocation SHAPE_PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "lead_chest");
    /** Same radius the vanilla-chest version produced through the container-open noise (InteractionNoiseResolver). */
    private static final double OPEN_NOISE_RADIUS = 3.0;

    public enum Action {
        NONE(null), OPEN_LID("open"), CLOSE_LID("close"), SEARCH("search"), VIEW("view");
        private final String hintKey;
        Action(String hintKey) { this.hintKey = hintKey; }
        public String hintKey() { return hintKey; }
    }

    public LeadChestBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LeadChestBlockEntity chest) {
            chest.markPlacedByPlayer();
        }
    }

    /** lid + closed -> open; lid + open -> close; interior + open -> search (hidden slots left) or view. */
    public static Action action(BlockState state, String region, LeadChestBlockEntity chest) {
        boolean open = state.getValue(OPEN);
        if ("lid".equals(region)) return open ? Action.CLOSE_LID : Action.OPEN_LID;
        if ("interior".equals(region) && open) return chest.isSearchCompleteForPrompt() ? Action.VIEW : Action.SEARCH;
        return Action.NONE;
    }

    @Override
    public String interactionHintKey(Level level, BlockState state, BlockPos pos, String region) {
        if (!(level.getBlockEntity(pos) instanceof LeadChestBlockEntity chest)) return null;
        String key = action(state, region, chest).hintKey();
        return key == null ? null : "hint.apocalypse_firstlight.lead_chest." + key;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        var region = meshInteraction(state, pos, player);
        Action action = region == null || !(level.getBlockEntity(pos) instanceof LeadChestBlockEntity chest)
                ? Action.NONE : action(state, region.region(), chest);
        if (action == Action.NONE) {
            return InteractionResult.PASS;
        }
        // like a vanilla chest: a solid block on top keeps the heavy lid shut
        if (action == Action.OPEN_LID && level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above())) {
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        switch (action) {
            case OPEN_LID -> setOpen(level, pos, state, true, player);
            case CLOSE_LID -> setOpen(level, pos, state, false, player);
            default -> player.openMenu((LeadChestBlockEntity) level.getBlockEntity(pos));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Latches, seal and lid are one sound per direction, already placed on the lid animation's keyframes (the lid lands at
     * 0.70 s when closing), so the pitch only varies by +-2 % to keep that timing; the open noise is unchanged.
     */
    private static void setOpen(Level level, BlockPos pos, BlockState state, boolean open, Player player) {
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, pos, open ? AflSounds.LEAD_CHEST_OPEN.get() : AflSounds.LEAD_CHEST_CLOSE.get(),
                SoundSource.BLOCKS, 0.8F, 0.98F + level.random.nextFloat() * 0.04F);
        level.gameEvent(player, open ? GameEvent.CONTAINER_OPEN : GameEvent.CONTAINER_CLOSE, pos);
        if (open && level instanceof ServerLevel server) {
            NoiseSystem.emit(new NoiseEvent(player, pos.getCenter(), NoiseType.INTERACTION, server.getGameTime(),
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()), OPEN_NOISE_RADIUS), server);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof LeadChestBlockEntity chest) {
                chest.dropContentsOnce();
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
        return level.getBlockEntity(pos) instanceof LeadChestBlockEntity chest ? AflContainerSearch.revealedAnalogSignal(chest) : 0;
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

    /** The AFL Animated Block Mesh Runtime draws the cask; the baked model is particle only. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LeadChestBlockEntity(pos, state);
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }
}
