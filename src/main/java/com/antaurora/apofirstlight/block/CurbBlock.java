package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.item.CurbBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Curbs V1 (docs/models/curbs_v1.md): a ground block (sidewalk or grass) with a 15 x 15 cm concrete curb along every edge
 * that meets a road surface, rising 15 cm into the cell above, which stays free. The state keeps the walk's {@link #AXIS}
 * (broom lines, as concrete_sidewalk), the builder's {@link #LEVEL} (sneak + use with an empty hand: full, lowered, flush),
 * {@link #PAINT} (red / yellow dye; light grey dye takes it off) and the curbed edges (NORTH ... WEST).
 * <p>
 * Mining (V2, user 2026-10-09): an edge is set when a road surface is beside it (on placement or when one is laid next to it)
 * and stays when that road is dug or blown away, as a real curb does. In survival the first break knocks the curb off: the
 * cell turns back into the plain walk (same axis) or grass and drops two pieces of concrete rubble; the second break is the
 * ground block's own. The recipe (walk or grass block + two rubble) gives a curb back, so nothing is made from nothing.
 * Creative removes the whole cell at once. Corners, posts, transitions and caps are worked out from the neighbours
 * ({@link CurbGeometry}).
 * <p>
 * The block lets light through (noOcclusion): the curb faces are lit from this cell, and an opaque ground block would be
 * dark inside. Its top still counts as a full face for anything placed on it.
 */
public class CurbBlock extends Block implements com.antaurora.apofirstlight.meshhit.MeshHitAssembled {
    public enum Back { SIDEWALK, GRASS }

    public enum Level implements StringRepresentable {
        FULL("full"), LOWERED("lowered"), FLUSH("flush");

        private final String name;

        Level(String name) { this.name = name; }

        public Level next() { return values()[(ordinal() + 1) % values().length]; }

        @Override public String getSerializedName() { return name; }
    }

    public enum Paint implements StringRepresentable {
        NONE("none", 0xEAEAEA), RED("red", 0xD93C33), YELLOW("yellow", 0xFFE446);

        private final String name;
        private final int tint;

        Paint(String name, int tint) {
            this.name = name;
            this.tint = tint;
        }

        /** The multiplier on the light curb concrete: bare about [183,180,174], red about [170,46,38], yellow about [200,176,52]. */
        public int tint() { return tint; }

        @Override public String getSerializedName() { return name; }

        /** The paint a dye gives, or null. */
        public static @Nullable Paint of(ItemStack stack) {
            return stack.is(Items.RED_DYE) ? RED : stack.is(Items.YELLOW_DYE) ? YELLOW : stack.is(Items.LIGHT_GRAY_DYE) ? NONE : null;
        }
    }

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final EnumProperty<Level> LEVEL = EnumProperty.create("level", Level.class);
    public static final EnumProperty<Paint> PAINT = EnumProperty.create("paint", Paint.class);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
            WEST = BlockStateProperties.WEST;
    /** Rubble from knocking a curb off (the recipe takes the same two). */
    public static final int RUBBLE = 2;
    private final Back back;

    public CurbBlock(Properties properties, Back back) {
        super(properties);
        this.back = back;
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(LEVEL, Level.FULL).setValue(PAINT, Paint.NONE)
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    public Back back() { return back; }

    public static BooleanProperty edge(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    /** The state with an edge for every road surface beside it added (edges are never taken away here). */
    public static BlockState withRoadEdges(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(pos.relative(d)).is(CurbGeometry.ROAD)) state = state.setValue(edge(d), true);
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withRoadEdges(defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis()), context.getLevel(), context.getClickedPos());
    }

    /** A road laid beside it adds that edge; a road taken away leaves it. */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction.getAxis().isHorizontal() && neighbor.is(CurbGeometry.ROAD) && !state.getValue(edge(direction))) return state.setValue(edge(direction), true);
        return state;
    }

    /** The ground block left when the curb is knocked off. */
    public BlockState ground(BlockState state) {
        return back == Back.GRASS ? Blocks.GRASS_BLOCK.defaultBlockState()
                : com.antaurora.apofirstlight.registry.AflBlocks.CONCRETE_SIDEWALK.get().defaultBlockState().setValue(JointedPavementBlock.AXIS, state.getValue(AXIS));
    }

    /** Survival: the first break takes the curb off and leaves the ground (see the class note); creative: the whole cell. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (player.getAbilities().instabuild) return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
        playerWillDestroy(level, pos, state, player);   // break particles and sound, as for any block
        level.setBlock(pos, ground(state), Block.UPDATE_ALL);
        if (willHarvest && !level.isClientSide) popResource(level, pos, new ItemStack(com.antaurora.apofirstlight.registry.AflItems.CONCRETE_RUBBLE.get(), RUBBLE));
        return false;   // the cell is not removed: no loot table, no air
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CurbGeometry.shape(CurbGeometry.compute(level, pos, state));
    }

    /**
     * The hit mesh (crosshair, outline, bullets; docs/rendering/mesh_hit_runtime_v1.md): the ground cube (hit_base) and the
     * curb pieces this cell draws, merged, so the outline follows the curb instead of the collision boxes' steps.
     */
    @Override
    public java.util.List<com.antaurora.apofirstlight.meshhit.MeshHitAssembled.Piece> meshHitPieces(BlockGetter level, BlockPos pos, BlockState state) {
        java.util.List<com.antaurora.apofirstlight.meshhit.MeshHitAssembled.Piece> out = new java.util.ArrayList<>();
        out.add(new com.antaurora.apofirstlight.meshhit.MeshHitAssembled.Piece(HIT_BASE, 0));
        for (CurbGeometry.Piece p : CurbGeometry.pieces(CurbGeometry.compute(level, pos, state)))
            out.add(new com.antaurora.apofirstlight.meshhit.MeshHitAssembled.Piece(new net.minecraft.resources.ResourceLocation(com.antaurora.apofirstlight.ApocalypseFirstLight.MOD_ID, "block/curb/" + p.name()), p.turn() * 90));
        return out;
    }

    private static final net.minecraft.resources.ResourceLocation HIT_BASE = new net.minecraft.resources.ResourceLocation(com.antaurora.apofirstlight.ApocalypseFirstLight.MOD_ID, "block/curb/hit_base");

    /** Things stand on it as on any ground block. */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /** Light passes (see the class note); no neighbour reads in the light engine. */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** Two curb cells hide the faces between them (it does not occlude, so nothing else would). */
    @Override
    @SuppressWarnings("deprecation")
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.getBlock() instanceof CurbBlock || super.skipRendering(state, adjacent, direction);
    }

    /** The paint a dye in hand would change this to, or null. */
    public static @Nullable Paint repaint(BlockState state, ItemStack stack) {
        Paint paint = Paint.of(stack);
        return paint == null || !state.hasProperty(PAINT) || state.getValue(PAINT) == paint ? null : paint;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        Paint paint = repaint(state, stack);
        if (paint != null) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(PAINT, paint), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, state.cycle(LEVEL), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6F, 1.1F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Pick block keeps the paint. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return asItem() instanceof CurbBlockItem item ? item.painted(state.getValue(PAINT)) : super.getCloneItemStack(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) out = out.setValue(edge(rotation.rotate(d)), state.getValue(edge(d)));
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)
            out = out.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        return out;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState out = state;
        for (Direction d : Direction.Plane.HORIZONTAL) out = out.setValue(edge(mirror.mirror(d)), state.getValue(edge(d)));
        return out;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, LEVEL, PAINT, NORTH, EAST, SOUTH, WEST);
    }
}
