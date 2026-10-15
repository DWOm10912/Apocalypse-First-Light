package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.TubeFarmGateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rural Driveway V1 tube farm gate (docs/models/rural_driveway_v1.md, tools/build-rural-driveway-v1.mjs): a 12 ft five-rail
 * galvanised tube gate between two round wood posts, 4 cells wide and 2 high. FACING points at the placing player (the
 * front); the hinge post stands in the first column (the clicked cell, the master), the latch post and its chain in the
 * fourth. The leaf hangs across the middle of the cells and swings 90 degrees either way: right-click any cell to open it
 * away from you (SWING = the side it went to) or to close it. Sneak-use with an empty hand cycles the PRIVATE DRIVE sign on
 * the leaf: none, facing the front, facing the back. Open, the leaf stands along the hinge column and reaches 3.2 m past
 * the cells, where it has no collision (as the enclosure gate's). Collision stands 1.5 m high, a fence's height.
 */
public class TubeFarmGateBlock extends RectMultiblockBlock<TubeFarmGateBlock.Cell> implements EntityBlock {
    public enum Cell implements StringRepresentable, RectMultiblockBlock.GridCell {
        C0R0, C1R0, C2R0, C3R0, C0R1, C1R1, C2R1, C3R1;

        @Override public int col() { return ordinal() % 4; }
        @Override public int row() { return ordinal() / 4; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /** Which way the leaf swings (or last swung): toward the front (FACING) or the back. */
    public enum Swing implements StringRepresentable {
        FRONT, BACK;

        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    /** The PRIVATE DRIVE sign on the leaf, readable from the front or the back, or none. */
    public enum Sign implements StringRepresentable {
        NONE, FRONT, BACK;

        public Sign next() { return values()[(ordinal() + 1) % values().length]; }

        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final EnumProperty<Cell> CELL = EnumProperty.create("cell", Cell.class);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Swing> SWING = EnumProperty.create("swing", Swing.class);
    public static final EnumProperty<Sign> SIGN = EnumProperty.create("sign", Sign.class);
    // structure frame px (north-facing, x from the first column's west edge; the generator's GATE mm + 500, x 0.016): the
    // two 160 mm posts (1.65 m), the closed leaf across the cells' middle, the open leaf inside the hinge column
    private static final double[] HINGE_POST = {0, 0, 6.72, 2.56, 26.4, 9.28};
    private static final double[] LATCH_POST = {61.44, 0, 6.72, 64, 26.4, 9.28};
    private static final double[] CLOSED = {2.56, 0, 7, 61.44, 24, 9};
    private static final double[] OPEN_FRONT = {1.8, 0, 0, 3.8, 24, 8};
    private static final double[] OPEN_BACK = {1.8, 0, 8, 3.8, 24, 16};
    private static final float PITCH_SPREAD = 0.06F;
    private final Map<String, VoxelShape> gateShapes = new ConcurrentHashMap<>();

    public TubeFarmGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, Cell.C0R0)
                .setValue(OPEN, false).setValue(SWING, Swing.BACK).setValue(SIGN, Sign.NONE));
    }

    @Override protected EnumProperty<Cell> cellProperty() { return CELL; }
    @Override protected Cell[] cells() { return Cell.values(); }
    @Override public Cell master() { return Cell.C0R0; }
    @Override protected double[][] boxes() { return new double[][]{HINGE_POST, LATCH_POST, CLOSED}; }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        BlockPos origin = origin(pos, state), master = masterPosition(pos, state);
        Direction facing = state.getValue(FACING);
        if (player.isSecondaryUseActive()) {
            if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
            Sign sign = state.getValue(SIGN).next();
            setAll(level, origin, facing, s -> s.setValue(SIGN, sign));
            level.playSound(player, master, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        boolean open = !state.getValue(OPEN);
        // away from the player: the front (FACING) side of the leaf's line opens to the back
        double side = (player.getX() - (master.getX() + 0.5)) * facing.getStepX() + (player.getZ() - (master.getZ() + 0.5)) * facing.getStepZ();
        Swing swing = open ? (side >= 0 ? Swing.BACK : Swing.FRONT) : state.getValue(SWING);
        setAll(level, origin, facing, s -> s.setValue(OPEN, open).setValue(SWING, swing));
        level.playSound(player, master, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS,
                0.9F, 0.75F + (level.getRandom().nextFloat() - 0.5F) * PITCH_SPREAD);
        level.playSound(player, master, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.5F, 1.0F);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, master);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void setAll(Level level, BlockPos origin, Direction facing, java.util.function.UnaryOperator<BlockState> change) {
        for (Cell cell : Cell.values()) {
            BlockPos at = cellPosition(origin, facing, cell);
            BlockState other = level.getBlockState(at);
            if (other.is(this) && other.getValue(CELL) == cell) level.setBlock(at, change.apply(other), UPDATE_CLIENTS | UPDATE_NEIGHBORS);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Cell cell = state.getValue(CELL);
        boolean open = state.getValue(OPEN);
        Swing swing = state.getValue(SWING);
        Direction facing = state.getValue(FACING);
        return gateShapes.computeIfAbsent(cell.getSerializedName() + "/" + facing.getName() + "/" + (open ? swing.getSerializedName() : "shut"),
                k -> HorizontalShapeUtils.rotations(cellShape(cell, new double[][]{HINGE_POST, LATCH_POST,
                        !open ? CLOSED : swing == Swing.FRONT ? OPEN_FRONT : OPEN_BACK})).get(facing));
    }

    private static VoxelShape cellShape(Cell cell, double[][] boxes) {
        double ox = 16 * cell.col(), oy = 16 * cell.row();
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes) {
            double x0 = Math.max(box[0], ox), x1 = Math.min(box[3], ox + 16), y0 = Math.max(box[1], oy), y1 = Math.min(box[4], oy + 16);
            if (x1 <= x0 || y1 <= y0) continue;
            shape = Shapes.or(shape, Block.box(x0 - ox, y0 - oy, box[2], x1 - ox, y1 - oy, box[5]));
        }
        return shape.optimize();
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new TubeFarmGateBlockEntity(pos, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL, OPEN, SWING, SIGN);
    }
}
