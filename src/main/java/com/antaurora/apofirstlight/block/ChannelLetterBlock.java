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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * A channel letter (Fuel Stop A1 details V1, docs/models/fuel_stop_a1_details_v1.md, tools/build-prairie-signage-v1.mjs;
 * the AFL sign font, tools/afl-sign-font.mjs): one aluminium letter per cell, 0.75 m cap height, red acrylic face, hung on
 * the wall behind it (FACING away from the wall). Any word is spelled from these (the store's PRAIRIE). On the building's
 * lighting circuit like the wall pack: wiring found through the wall behind it, dark by day (photocell), light 9 when lit.
 * The item carries the letter in its BlockStateTag (ChannelLetterBlockItem) and a broken letter drops its own letter.
 */
public class ChannelLetterBlock extends BuildingLightBlock {
    public enum Letter implements StringRepresentable {
        A(640), B(560), C(600), D(620), E(500), F(480), G(620), H(600), I(150), J(480), K(580), L(480), M(760),
        N(620), O(640), P(540), Q(640), R(580), S(560), T(600), U(600), V(640), W(860), X(620), Y(640), Z(560);

        /** Glyph width, mm (tools/afl-sign-font.mjs WIDTH). */
        public final int width;

        Letter(int width) { this.width = width; }

        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Letter> LETTER = EnumProperty.create("letter", Letter.class);
    public static final int LIGHT_LEVEL = 9;
    private static final Map<Letter, VoxelShape[]> SHAPES = new EnumMap<>(Letter.class);

    static {
        // px, facing north (the wall at +z): the face 373..500 mm from the cell's centre, the cap height 125..875 mm
        for (Letter letter : Letter.values()) {
            double half = (letter.width / 2.0 + 2.5) * 0.016, z0 = 8 + 373 * 0.016;
            VoxelShape north = Block.box(8 - half, 2, z0, 8 + half, 14, 16);
            SHAPES.put(letter, HorizontalShapeUtils.rotations(north).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey((a, b) -> Integer.compare(a.get2DDataValue(), b.get2DDataValue())))
                    .map(Map.Entry::getValue).toArray(VoxelShape[]::new));
        }
    }

    public ChannelLetterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LETTER, Letter.A).setValue(LIT, false));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
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
        return direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected boolean photocell() {
        return true;
    }

    /** The wall it hangs on (its own cell is outside the building's roof). */
    @Override
    protected BlockPos wiringPosition(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(LETTER))[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LETTER, LIT);
    }
}
