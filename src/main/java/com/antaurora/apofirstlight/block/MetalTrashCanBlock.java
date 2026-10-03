package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.MetalTrashCanBlockEntity;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import com.antaurora.apofirstlight.registry.AflSounds;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Metal Trash Can V2 (2026-10-02, tools/build-metal-trash-can-v2.mjs): a round can with a hinged lid, a 9-slot searchable
 * container (MetalTrashCanBlockEntity). Shapes, interaction regions and prompt anchors come from the Mesh Shape profile
 * (data/apocalypse_firstlight/mesh_shapes/metal_trash_can.json): shut, aiming anywhere at the can opens the lid; open,
 * aiming into the mouth searches (or views), aiming at the rest shuts the lid. {@link #OPEN} drives the lid animation, the
 * shape state and screen validity. Lid sounds: metal_trash_can_open / _close (tools/build-metal-trash-can-sounds-v1.mjs);
 * searching plays the framework's shared rummaging sound.
 */
public final class MetalTrashCanBlock extends Block implements EntityBlock, AflMeshInteractionBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final ResourceLocation SHAPE_PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "metal_trash_can");

    /** What aiming at a region does in the current state. */
    public enum Action {
        NONE(null), OPEN_LID("open"), CLOSE_LID("close"), SEARCH("search"), VIEW("view");
        private final String hintKey;
        Action(String hintKey) { this.hintKey = hintKey; }
        /** hint.apocalypse_firstlight.trash_can.* suffix, or null. */
        public String hintKey() { return hintKey; }
    }

    public MetalTrashCanBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    /** The lid hinge at the back, the front toward the player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        // placed by a player: its contents are the player's own, never searched
        if (!level.isClientSide() && level.getBlockEntity(position) instanceof MetalTrashCanBlockEntity can) can.markPlacedByPlayer();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        var region = meshInteraction(state, position, player);
        Action action = region == null || !(level.getBlockEntity(position) instanceof MetalTrashCanBlockEntity can)
                ? Action.NONE : action(state, region.region(), can);
        if (action == Action.NONE) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        switch (action) {
            case OPEN_LID -> setOpen(level, position, state, true, player);
            case CLOSE_LID -> setOpen(level, position, state, false, player);
            default -> player.openMenu((MetalTrashCanBlockEntity) level.getBlockEntity(position));
        }
        return InteractionResult.CONSUME;
    }

    private static void setOpen(Level level, BlockPos position, BlockState state, boolean open, Player player) {
        // world loot is rolled as the lid opens, so the bags inside show how much it holds (container goods)
        if (open && level.getBlockEntity(position) instanceof MetalTrashCanBlockEntity can) can.unpackLootTable(player);
        level.setBlock(position, state.setValue(OPEN, open), Block.UPDATE_ALL);
        // the clips are placed on the 8-tick lid animation, so the pitch stays close to 1
        level.playSound(null, position, open ? AflSounds.METAL_TRASH_CAN_OPEN.get() : AflSounds.METAL_TRASH_CAN_CLOSE.get(),
                SoundSource.BLOCKS, 0.8F, 0.98F + level.random.nextFloat() * 0.04F);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, position);
    }

    /**
     * Region + state -> action, shared by the server use() and the client world interaction prompt:
     * lid + shut -> open; open: mouth -> search (hidden slots left) or view, lid (the rest of the can) -> shut.
     */
    public static Action action(BlockState state, String region, MetalTrashCanBlockEntity can) {
        boolean open = state.getValue(OPEN);
        if ("lid".equals(region)) return open ? Action.CLOSE_LID : Action.OPEN_LID;
        if ("mouth".equals(region) && open) return can.isSearchCompleteForPrompt() ? Action.VIEW : Action.SEARCH;
        return Action.NONE;
    }

    @Override
    public String interactionHintKey(Level level, BlockState state, BlockPos pos, String region) {
        if (!(level.getBlockEntity(pos) instanceof MetalTrashCanBlockEntity can)) return null;
        String key = action(state, region, can).hintKey();
        return key == null ? null : "hint.apocalypse_firstlight.trash_can." + key;
    }

    /** Every removal path (player, explosion, commands) ends here: revealed slots drop, unseen loot is lost. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(position) instanceof MetalTrashCanBlockEntity can) can.dropContentsOnce();
        super.onRemove(state, level, position, next, moving);
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

    /** The AFL Animated Block Mesh Runtime draws the can; the baked model is particle only. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
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
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new MetalTrashCanBlockEntity(position, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }
}
