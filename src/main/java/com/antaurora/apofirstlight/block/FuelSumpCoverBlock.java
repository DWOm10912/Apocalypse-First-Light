package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.fluid.AflFluidPortBlock;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The forecourt covers over an Underground Fuel Tank (2026-10-05, tools/build-fuel-station-sump-v1.mjs,
 * docs/models/fuel_station_sump_v1.md), set flush with the forecourt:
 * <ul>
 *   <li>{@link Kind#MANHOLE}: the pump manhole cover over the Submersible Fuel Pump's sump: a round steel lid in a concrete
 *   frame, pried open and shut with the crowbar (no crowbar animation yet);</li>
 *   <li>{@link Kind#FILL}: the fill cover (gasoline / diesel colours on its rim and cap) over the tank's fill riser: a small
 *   lid over a spill bucket, opened by hand, with the AFL fluid port on its bottom face for the pipe from the tank's fill
 *   port. Filling through it (a tanker or a can) comes later.</li>
 * </ul>
 * FACING is the player's horizontal facing when placed: the lid's hinge is on that side, so it opens away from them.
 * Open, the cell keeps its frame and loses its middle (too narrow for a player to drop through).
 */
public final class FuelSumpCoverBlock extends HorizontalDirectionalBlock implements AflFluidPortBlock {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public enum Kind {MANHOLE, FILL}

    private static final VoxelShape MANHOLE_OPEN = Shapes.join(Shapes.block(), Block.box(3.5, 0, 3.5, 12.5, 16, 12.5), BooleanOp.ONLY_FIRST);
    private static final VoxelShape FILL_OPEN = Shapes.join(Shapes.block(), Block.box(3.6, 6.4, 3.6, 12.4, 16, 12.4), BooleanOp.ONLY_FIRST);

    private final Kind kind;

    public FuelSumpCoverBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /** The fill cover's port: its bottom face, toward the tank's fill port. The manhole cover has none. */
    @Override
    public boolean hasFluidPort(BlockState state, Direction face) {
        return kind == Kind.FILL && face == Direction.DOWN;
    }

    private boolean opensWith(Player player) {
        return kind == Kind.MANHOLE ? player.getMainHandItem().is(AflItems.CROWBAR.get()) : player.getMainHandItem().isEmpty();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND || !opensWith(player)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean open = !state.getValue(OPEN);
        level.setBlock(position, state.setValue(OPEN, open), UPDATE_ALL);
        level.playSound(null, position.getX() + 0.5, position.getY() + 0.9, position.getZ() + 0.5,
                open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS,
                1.0F, kind == Kind.MANHOLE ? 0.8F : 1.15F);
        return InteractionResult.CONSUME;
    }

    /** What a click would do (WorldInteractionHint): hint key suffix, drawn over the lid. */
    public record Prompt(String key, Vec3 anchor) {}

    @Nullable
    public static Prompt prompt(BlockPos position, BlockState state, Player player) {
        if (!(state.getBlock() instanceof FuelSumpCoverBlock block)) return null;
        String name = block.kind == Kind.MANHOLE ? "pump_manhole_cover" : "fuel_fill_cover";
        Vec3 anchor = new Vec3(position.getX() + 0.5, position.getY() + 1.05, position.getZ() + 0.5);
        if (block.opensWith(player)) return new Prompt(name + "." + (state.getValue(OPEN) ? "close" : "open"), anchor);
        if (block.kind == Kind.MANHOLE && !state.getValue(OPEN) && player.getMainHandItem().isEmpty()) return new Prompt(name + ".needs_crowbar", anchor);
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return !state.getValue(OPEN) ? Shapes.block() : kind == Kind.MANHOLE ? MANHOLE_OPEN : FILL_OPEN;
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
        builder.add(FACING, OPEN);
    }
}
