package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Storefront Glazing V1 (docs/models/storefront_glazing_v1.md): one wall cell of a centre-set aluminium storefront, a clear
 * lite in a black anodized frame set 3 px behind the outer face. FACING is the outside. LEFT / RIGHT / UP / DOWN: the
 * neighbour on that side (left and right as seen from outside) is glazing with the same facing, so the frame runs on;
 * otherwise that edge gets a jamb, head or sill. MULLION places the vertical member of the 1.5 m rhythm (EDGE: on the left
 * edge, CENTER: mid-cell, NONE); placed next to glazing it continues the neighbour's rhythm, and sneak + use with an empty
 * hand cycles it. TRANSOM draws a horizontal member at the top edge between two lites (sneak + use on the top quarter).
 */
public class StorefrontGlazingBlock extends HorizontalDirectionalBlock {
    public enum Mullion implements StringRepresentable {
        EDGE("edge"), CENTER("center"), NONE("none");

        private final String name;

        Mullion(String name) {
            this.name = name;
        }

        /** The next cell to the right in a 1.5 m rhythm: edge, center, none, edge... */
        public Mullion next() {
            return values()[(ordinal() + 1) % 3];
        }

        public Mullion previous() {
            return values()[(ordinal() + 2) % 3];
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final EnumProperty<Mullion> MULLION = EnumProperty.create("mullion", Mullion.class);
    public static final BooleanProperty TRANSOM = BooleanProperty.create("transom");
    /** The share of the cell height, from the top, where sneak + use toggles the transom instead of the mullion. */
    public static final double TRANSOM_ZONE = 0.25;

    // the frame's depth band, 3..5 px behind the outer face (tools/build-storefront-glazing-v1.mjs SF)
    private static final VoxelShape NORTH = box(0, 0, 3, 16, 16, 5), SOUTH = box(0, 0, 11, 16, 16, 13),
            WEST = box(3, 0, 0, 5, 16, 16), EAST = box(11, 0, 0, 13, 16, 16);

    public StorefrontGlazingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false)
                .setValue(UP, false).setValue(DOWN, false).setValue(MULLION, Mullion.EDGE).setValue(TRANSOM, false));
    }

    /** Left as seen from outside: the outside's clockwise side. */
    public static Direction leftOf(Direction facing) {
        return facing.getClockWise();
    }

    private boolean joins(BlockState other, Direction facing) {
        return other.is(this) && other.getValue(FACING) == facing;
    }

    private BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, joins(level.getBlockState(pos.relative(leftOf(facing))), facing))
                .setValue(RIGHT, joins(level.getBlockState(pos.relative(leftOf(facing).getOpposite())), facing))
                .setValue(UP, joins(level.getBlockState(pos.above()), facing))
                .setValue(DOWN, joins(level.getBlockState(pos.below()), facing));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState left = level.getBlockState(pos.relative(leftOf(facing)));
        BlockState right = level.getBlockState(pos.relative(leftOf(facing).getOpposite()));
        Mullion mullion = joins(left, facing) ? left.getValue(MULLION).next()
                : joins(right, facing) ? right.getValue(MULLION).previous() : Mullion.EDGE;
        BlockState below = level.getBlockState(pos.below()), above = level.getBlockState(pos.above());
        if (joins(below, facing)) mullion = below.getValue(MULLION);   // stacked lites keep the run's mullions in line
        else if (joins(above, facing)) mullion = above.getValue(MULLION);
        return connected(defaultBlockState().setValue(FACING, facing).setValue(MULLION, mullion), level, pos);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        return connected(state, level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        BlockState next = inTransomZone(pos, hit.getLocation().y) ? state.cycle(TRANSOM) : state.setValue(MULLION, state.getValue(MULLION).next());
        if (!level.isClientSide()) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.5F, 1.4F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Sneak + use on the top quarter of a lite toggles the transom (WorldInteractionHint uses the same rule). */
    public static boolean inTransomZone(BlockPos pos, double hitY) {
        return hitY - pos.getY() >= 1.0 - TRANSOM_ZONE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        // a mirrored run keeps its rhythm only approximately (an edge mullion moves to the other edge); V1 structures never mirror
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, UP, DOWN, MULLION, TRANSOM);
    }
}
