package com.antaurora.apofirstlight.block;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Metal Eyebrow Canopy V1 (docs/models/metal_eyebrow_canopy_v1.md): a 1 m hanger-rod storefront canopy in the bottom of
 * its cell: a 22 cm fascia on the FACING (outside) edge, a 10 cm deck back to the wall. LEFT / RIGHT: the run continues on
 * the facing's clockwise / counter-clockwise side (another canopy facing the same way, or a Metal Panel Jamb carrying the
 * canopy through a portal); an end without one closes with a fascia return. ROD: a steel tie rod from behind the fascia up
 * to the wall (45 degrees, to about 1.14 m above the canopy), toggled with sneak + use and an empty hand.
 * Placement: against a canopy or a canopy-carrying jamb, the same facing; against the side of a wall, facing away from it;
 * otherwise towards the player.
 */
public class MetalEyebrowCanopyBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty ROD = BooleanProperty.create("rod");
    // fascia z 0..0.8 (y 0..3.5) and deck y 0.5..2.1, facing north (see tools/build-metal-eyebrow-canopy-v1.mjs)
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    static {
        for (Direction f : Direction.Plane.HORIZONTAL) SHAPES.put(f, run(f, 0, 16));
    }

    public MetalEyebrowCanopyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(LEFT, false).setValue(RIGHT, false).setValue(ROD, false));
    }

    /** A canopy run from x a to x b in the north-facing frame, turned to face f. */
    static VoxelShape run(Direction f, double a, double b) {
        return Shapes.or(turned(f, a, 0, 0, b, 3.5, 0.8), turned(f, a, 0.5, 0.8, b, 2.1, 16));
    }

    /** A box given in the north-facing frame (px), turned as the blockstate turns the model for facing f. */
    static VoxelShape turned(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (f) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    private boolean continues(BlockState neighbour, Direction facing) {
        return neighbour.is(this) && neighbour.getValue(FACING) == facing || MetalPanelJambBlock.eyebrowFacing(neighbour) == facing;
    }

    private BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, continues(level.getBlockState(pos.relative(facing.getClockWise())), facing))
                .setValue(RIGHT, continues(level.getBlockState(pos.relative(facing.getCounterClockWise())), facing));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState against = context.getLevel().getBlockState(pos.relative(face.getOpposite()));
        Direction carried = MetalPanelJambBlock.eyebrowFacing(against);
        Direction facing = against.is(this) ? against.getValue(FACING)
                : carried != null ? carried
                : face.getAxis().isHorizontal() ? face
                : context.getHorizontalDirection().getOpposite();
        return connect(defaultBlockState().setValue(FACING, facing), context.getLevel(), pos);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        if (direction == facing.getClockWise()) return state.setValue(LEFT, continues(neighborState, facing));
        if (direction == facing.getCounterClockWise()) return state.setValue(RIGHT, continues(neighborState, facing));
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            level.setBlock(pos, state.cycle(ROD), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, ROD);
    }
}
