package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.CashRegisterBlockEntity;
import com.antaurora.apofirstlight.client.CashRegisterParticleExtensions;
import com.antaurora.apofirstlight.containersearch.AflContainerSearch;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Cash Register V2 (tools/build-cash-register-v2.mjs): countertop POS terminal whose cash drawer is a 9-slot (3 x 3)
 * searchable container. Closed, aiming anywhere at the register opens the drawer; open, the drawn-out tray is searched /
 * viewed and the drawer front closes it. FACING is the operator side (drawer and keypad). The drawer slides out of the
 * block toward the operator; it is selectable there but has no collision. Shapes, interaction
 * regions and prompt anchors come from data/apocalypse_firstlight/mesh_shapes/cash_register.json.
 */
public final class CashRegisterBlock extends HorizontalDirectionalBlock implements EntityBlock, AflMeshInteractionBlock {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final ResourceLocation SHAPE_PROFILE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "cash_register");

    public enum Action {
        NONE(null), OPEN_DRAWER("open"), CLOSE_DRAWER("close"), SEARCH("search"), VIEW("view");
        private final String hintKey;
        Action(String hintKey) { this.hintKey = hintKey; }
        public String hintKey() { return hintKey; }
    }

    public CashRegisterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CashRegisterBlockEntity register) {
            register.markPlacedByPlayer();
        }
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
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    // ---- drawer and container ----

    /** closed: any part -> open the drawer; open: tray -> search (hidden slots left) or view, drawer front -> close. */
    public static Action action(BlockState state, String region, CashRegisterBlockEntity register) {
        boolean open = state.getValue(OPEN);
        if ("drawer".equals(region)) return open ? Action.CLOSE_DRAWER : Action.OPEN_DRAWER;
        if ("interior".equals(region) && open) return register.isSearchCompleteForPrompt() ? Action.VIEW : Action.SEARCH;
        return Action.NONE;
    }

    @Override
    public String interactionHintKey(Level level, BlockState state, BlockPos pos, String region) {
        if (!(level.getBlockEntity(pos) instanceof CashRegisterBlockEntity register)) return null;
        String key = action(state, region, register).hintKey();
        return key == null ? null : "hint.apocalypse_firstlight.cash_register." + key;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        var region = meshInteraction(state, pos, player);
        Action action = region == null || !(level.getBlockEntity(pos) instanceof CashRegisterBlockEntity register)
                ? Action.NONE : action(state, region.region(), register);
        if (action == Action.NONE) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        switch (action) {
            case OPEN_DRAWER -> setOpen(level, pos, state, true, player);
            case CLOSE_DRAWER -> setOpen(level, pos, state, false, player);
            default -> player.openMenu((CashRegisterBlockEntity) level.getBlockEntity(pos));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Drawer sounds are placed on the 6-tick linear drawer animation (tools/build-cash-register-sounds-v1.mjs: the stop and
     * the catch impacts land on the last frame, the bell rings at the release), so the pitch only varies by +-2 %.
     */
    private static void setOpen(Level level, BlockPos pos, BlockState state, boolean open, Player player) {
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, pos, open ? AflSounds.CASH_REGISTER_OPEN.get() : AflSounds.CASH_REGISTER_CLOSE.get(),
                SoundSource.BLOCKS, 0.8F, 0.98F + level.random.nextFloat() * 0.04F);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    /** Every removal path (player, explosion, lost support, /setblock): revealed slots drop, unseen world loot is lost. */
    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof CashRegisterBlockEntity register) {
                register.dropContentsOnce();
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
        return level.getBlockEntity(pos) instanceof CashRegisterBlockEntity register ? AflContainerSearch.revealedAnalogSignal(register) : 0;
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

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /** The AFL Animated Block Mesh Runtime draws the register's moving parts; resting parts: the chunk model (client/blockmesh/AflStaticMeshModel, docs/dev/render_performance_v1.md). */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CashRegisterBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }
}
