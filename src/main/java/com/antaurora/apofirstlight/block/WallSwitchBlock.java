package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringSupply;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Wall Switch (墙上开关, Building Wiring V1 phase 3, docs/gameplay/building_wiring_v1_design.md 6): a NA single-pole switch on
 * a white plate, 1.5 x real size like the wall outlet, hung on a wall only (FACING = away from the wall), the plate centred
 * 0.2 m up in its cell (in the second cell above a floor: 1.2 m). No block entity: ON is the handle. Its tap's group is the
 * light group it works (circuit N, group 1-8 of the panel that feeds it): a right-click flips that group in the panel
 * ({@link DistributionPanelBlockEntity#toggleGroup}), every switch of the group turns with it and the group's lights look
 * again at once. Works without power (the state is kept in the panel). A switch on no wire, or in group 0, turns only
 * itself. WIRED marks a switch that works a group (set by its panel after each solve, cleared when it no longer does): Jade
 * shows 未接线 without it (the wiring index is server side and the switch has no block entity). Takes no energy. No collision; the hand breaks it (a small plastic fitting), drops itself. On a round log wall it
 * stands on a pad (LOG, {@link LogMount}, kind 11).
 * The click is a placeholder vanilla sound until a recorded take replaces it.
 */
public class WallSwitchBlock extends HorizontalDirectionalBlock implements LogMount.Padded {
    public static final BooleanProperty ON = BooleanProperty.create("on"), WIRED = BooleanProperty.create("wired");
    /** px (facing north): the plate's centre height in the cell (0.2 m). */
    public static final double CENTRE_Y = 3.2;
    // the plate (1.68 x 2.74 px) and the handle, widened a little for aiming
    private static final VoxelShape NORTH = Block.box(6.9, 1.6, 15.2, 9.1, 4.8, 16);
    private static final Map<Direction, VoxelShape> SHAPES = HorizontalShapeUtils.rotations(NORTH), ON_LOGS = LogMount.rotationsOnPad(NORTH, LogMount.PAD_SWITCH);

    public WallSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, true).setValue(WIRED, false).setValue(LogMount.LOG, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (!face.getAxis().isHorizontal()) return null;
        BlockState state = LogMount.placed(defaultBlockState().setValue(FACING, face), context.getLevel(), context.getClickedPos(), face);
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
        return LogMount.updated(super.updateShape(state, direction, neighbor, level, pos, neighborPos), direction, neighbor, state.getValue(FACING));
    }

    /** Heard as far as the infected hear it (blocks; forest home sounds V1, 2026-10-14). */
    public static final double CLICK_RADIUS = 2;

    /** Flip the switch: its light group in the panel (every switch of the group follows), else only itself. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            DistributionPanelBlockEntity panel = panel(server, pos);
            WiringSupply.Feed feed = WiringSupply.feed(server, pos);
            boolean on;
            if (panel != null) {
                on = panel.toggleGroup(feed.circuit(), feed.group());
                BlockState now = server.getBlockState(pos);
                if (now.is(this)) set(server, pos, now, on, true);   // not in the panel's list yet
            } else {
                on = !state.getValue(ON);
                set(server, pos, state, on, false);
            }
            // the toggle, heard as far as its noise (2 blocks; forest home sounds V1, 2026-10-14)
            com.antaurora.apofirstlight.noise.RangedSound.playWithNoise(server, player, pos.getCenter(), com.antaurora.apofirstlight.registry.AflSounds.WALL_SWITCH_CLICK.get(),
                    CLICK_RADIUS, on ? 1.03F : 0.97F, com.antaurora.apofirstlight.noise.NoiseType.INTERACTION, net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(this));
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** The WIRED panel whose light group 1-8 this switch works, or null (no wire, group 0, panel not loaded). */
    @Nullable
    public static DistributionPanelBlockEntity panel(Level level, BlockPos pos) {
        WiringSupply.Feed feed = WiringSupply.feed(level, pos);
        if (feed == null || feed.role() != WiringSupply.Role.SWITCH || feed.group() < 1 || feed.group() > WiringNode.GROUPS || !level.isLoaded(feed.panel())) return null;
        return level.getBlockEntity(feed.panel()) instanceof DistributionPanelBlockEntity p && p.wired() ? p : null;
    }

    /** Turns the handle (the panel's group state) and marks whether it works a group, without a sound. */
    public void set(ServerLevel level, BlockPos pos, BlockState state, boolean on, boolean wired) {
        if (state.getValue(ON) != on || state.getValue(WIRED) != wired) level.setBlock(pos, state.setValue(ON, on).setValue(WIRED, wired), Block.UPDATE_CLIENTS);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (LogMount.mounted(state) ? ON_LOGS : SHAPES).get(state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public int logPad(BlockState state) {
        return LogMount.PAD_SWITCH;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
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
        builder.add(FACING, ON, WIRED, LogMount.LOG);
    }
}
