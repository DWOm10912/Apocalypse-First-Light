package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.energy.BuildingLights;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Porcelain pull-chain lampholder (拉线瓷灯座, Building Lights V1 E, docs/models/building_lights_v1.md; forest start home F7,
 * user 2026-10-13 concept B): a real-size white porcelain lampholder with a frosted 60 W A19 bulb, on a ceiling (FACING
 * down) or a wall (FACING away from it), the nickel bead chain hanging straight down from its side. Right-click pulls the
 * chain: ON toggles this one lamp. LIT while the chain is on and the building's lighting circuit has power
 * ({@link BuildingLightBlock}); the bulb takes its real wattage, {@link BuildingLights#BULB_60W_PERIOD} FE a period
 * (0.24 FE/t). A wall lamp finds its wiring through the wall behind it (a porch wall lamp's own cell may be outdoors).
 * Light 14. A small porcelain fitting: hand-breakable, drops itself (like the wall outlet), no collision.
 * The chain's click is a placeholder vanilla sound until a recorded take replaces it.
 */
public class PorcelainLampholderBlock extends BuildingLightBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", direction -> direction != Direction.UP);
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final int LIGHT_LEVEL = 14;
    // px, tools/build-building-lights-v1.mjs SELECTION: the porcelain and bulb, and the chain (widened for aiming)
    private static final VoxelShape CEILING = Shapes.or(Block.box(7.16, 13.34, 7.16, 8.84, 16, 8.84), Block.box(8.26, 10.1, 7.7, 8.86, 15.4, 8.3));
    private static final Map<Direction, VoxelShape> WALL = HorizontalShapeUtils.rotations(
            Shapes.or(Block.box(7.16, 5.88, 13.34, 8.84, 7.56, 16), Block.box(7.7, 1.9, 14.95, 8.3, 6.2, 15.6)));

    public PorcelainLampholderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(ON, true).setValue(LIT, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.UP) return null;
        BlockState state = defaultBlockState().setValue(FACING, face);
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** Pull the chain: on lights the lamp at once when the lighting circuit has power (that period drawn), off darkens it. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            boolean on = !state.getValue(ON), lit = on && BuildingLights.period(level, wiringPosition(state, pos), false, periodEnergy()) == BuildingLights.Check.LIT;
            level.setBlock(pos, state.setValue(ON, on).setValue(LIT, lit), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, on ? 1.75F : 1.6F);   // placeholder for a recorded pull-chain click
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected boolean switchedOn(BlockState state) {
        return state.getValue(ON);
    }

    @Override
    protected int periodEnergy() {
        return BuildingLights.BULB_60W_PERIOD;
    }

    @Override
    protected BlockPos wiringPosition(BlockState state, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.DOWN ? pos : pos.relative(facing.getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return facing == Direction.DOWN ? CEILING : WALL.get(facing);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
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
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON, LIT);
    }
}
