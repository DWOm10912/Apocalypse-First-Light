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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Site Lighting V1 (docs/models/site_lighting_v1.md): one metre of a parking-lot light pole, 127 mm (5 in) square
 * galvanized steel. Stands on the base or another segment; the head cell (AreaLightBlock) goes on the top one. The segment
 * right over the base shows the hand hole on the side the base faces ({@link #HANDHOLE}, kept from the base below).
 */
public class LightPoleBlock extends Block {
    public enum HandHole implements StringRepresentable {
        NONE("none"), NORTH("north"), EAST("east"), SOUTH("south"), WEST("west");

        private final String name;

        HandHole(String name) { this.name = name; }

        static HandHole of(Direction d) {
            return switch (d) { case NORTH -> NORTH; case EAST -> EAST; case SOUTH -> SOUTH; case WEST -> WEST; default -> NONE; };
        }

        Direction direction() {
            return switch (this) { case NORTH -> Direction.NORTH; case EAST -> Direction.EAST; case SOUTH -> Direction.SOUTH; case WEST -> Direction.WEST; default -> null; };
        }

        @Override public String getSerializedName() { return name; }
    }

    public static final EnumProperty<HandHole> HANDHOLE = EnumProperty.create("handhole", HandHole.class);
    static final VoxelShape SHAPE = Block.box(6.984, 0, 6.984, 9.016, 16, 9.016);

    public LightPoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HANDHOLE, HandHole.NONE));
    }

    /** The pole's own cells: the base, segments (the head cell sits on them). */
    public static boolean isPole(BlockState state) {
        return state.getBlock() instanceof LightPoleBlock || state.getBlock() instanceof LightPoleBaseBlock;
    }

    private static HandHole handHole(BlockState below) {
        return below.getBlock() instanceof LightPoleBaseBlock ? HandHole.of(below.getValue(LightPoleBaseBlock.FACING)) : HandHole.NONE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HANDHOLE, handHole(context.getLevel().getBlockState(context.getClickedPos().below())));
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isPole(level.getBlockState(pos.below()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction != Direction.DOWN) return state;
        if (!isPole(neighbor)) return Blocks.AIR.defaultBlockState();
        return state.setValue(HANDHOLE, handHole(neighbor));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        Direction d = state.getValue(HANDHOLE).direction();
        return d == null ? state : state.setValue(HANDHOLE, HandHole.of(rotation.rotate(d)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        Direction d = state.getValue(HANDHOLE).direction();
        return d == null ? state : state.setValue(HANDHOLE, HandHole.of(mirror.mirror(d)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HANDHOLE);
    }
}
