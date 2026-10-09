package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Site Lighting V1 (docs/models/site_lighting_v1.md, tools/build-site-lighting-v1.mjs): the head cell on top of a
 * parking-lot pole, about 8 m up on the standard pole. The pole's top and cap, and one to four LED area lights on 380 mm arms
 * ({@link #HEADS}, quarter turns clockwise from FACING; the arms reach over the cell's side, the heads hang in the next
 * cell). FACING points the first head (toward the player who placed it: a pole on the lot's edge is placed from inside the
 * lot); the twist-lock photocontrol sits on that head. LIT is set by the pole's base (LightPoleBaseBlockEntity: powered and
 * dark); lit, the cell gives light 15 and the base hangs the hidden light points.
 * <p>
 * Players place single heads only (the item's default state) and cannot change the layout (user 2026-10-09: multi-head
 * poles come from code, worldgen and authoring; a player who takes one down rebuilds it with single heads). Taken down, the
 * cell drops one item per head (loot table: set_count by HEADS).
 */
public class AreaLightBlock extends HorizontalDirectionalBlock {
    public enum Heads implements StringRepresentable {
        SINGLE("single", 0), TWIN("twin", 0, 2), TWIN_CORNER("twin_corner", 0, 1), TRIPLE("triple", 0, 1, 3), QUAD("quad", 0, 1, 2, 3);

        private final String name;
        private final int[] turns;

        Heads(String name, int... turns) {
            this.name = name;
            this.turns = turns;
        }

        public int count() {
            return turns.length;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Heads> HEADS = EnumProperty.create("heads", Heads.class);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    // px, tools/build-site-lighting-v1.mjs HEAD: the pole to its cap boss (263 mm) and the cap; per head the arm with its
    // slipfitter inside the cell (y 128..213 mm)
    private static final VoxelShape TOP = Shapes.or(Block.box(6.984, 0, 6.984, 9.016, 4.2, 9.016), Block.box(6.84, 3.92, 6.84, 9.16, 4.08, 9.16));
    /** By Direction#get2DDataValue (south, west, north, east): the arm reaching that way. */
    private static final VoxelShape[] ARM = {
            Block.box(7, 2.05, 9.016, 9, 3.4, 16), Block.box(0, 2.05, 7, 6.984, 3.4, 9),
            Block.box(7, 2.05, 0, 9, 3.4, 6.984), Block.box(9.016, 2.05, 7, 16, 3.4, 9)};
    /** By FACING's 2D data value, then HEADS. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[4][Heads.values().length];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) for (Heads heads : Heads.values()) {
            VoxelShape shape = TOP;
            for (int turn : heads.turns) {
                Direction arm = facing;
                for (int k = 0; k < turn; k++) arm = arm.getClockWise();   // as the blockstate's y turns: north to east
                shape = Shapes.or(shape, ARM[arm.get2DDataValue()]);
            }
            SHAPES[facing.get2DDataValue()][heads.ordinal()] = shape;
        }
    }

    public AreaLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HEADS, Heads.SINGLE).setValue(LIT, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return LightPoleBlock.isPole(level.getBlockState(pos.below()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !LightPoleBlock.isPole(neighbor) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()][state.getValue(HEADS).ordinal()];
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /**
     * Mirrored: every head's side mirrored. The corner pair (FACING and the next side clockwise) mirrors to the side before
     * the mirrored FACING and that side, so it starts one quarter turn back (the photocontrol changes heads); the others are
     * symmetric about FACING.
     */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        Direction facing = mirror.mirror(state.getValue(FACING));
        return state.setValue(FACING, state.getValue(HEADS) == Heads.TWIN_CORNER ? facing.getCounterClockWise() : facing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HEADS, LIT);
    }
}
