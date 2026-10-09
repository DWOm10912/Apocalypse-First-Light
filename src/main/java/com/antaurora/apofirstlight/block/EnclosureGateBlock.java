package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.EnclosureGateBlockEntity;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
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
 * Trash enclosure V1 gate (docs/models/fuel_stop_a1_details_v1.md, tools/build-trash-enclosure-v1.mjs): one steel leaf with
 * its own hinge post, 2 cells wide and 2 high; a pair closes a 4 m opening. FACING is the outside, toward which the leaf
 * opens (placed facing the player); the leaf hangs at the back of its cells, on the enclosure's edge, so the screen walls run
 * from the cells behind. HINGE as seen from outside: RIGHT = the first column (the clicked cell, the master), LEFT = the
 * second (FACING's clockwise side); chosen on placement toward a screen wall behind either end. Right-click any cell to
 * open or close it; all cells keep the same OPEN. Animated by the master's block entity (AFL Animated Block Mesh Runtime,
 * channel 'swing', 0.9 s); the shapes switch at once, as a door's do. Open, the leaf stands along the hinge side and
 * reaches 0.93 m past the front of the cells, where it has no collision.
 */
public class EnclosureGateBlock extends RectMultiblockBlock<EnclosureGateBlock.Cell> implements EntityBlock {
    public enum Cell implements StringRepresentable, RectMultiblockBlock.GridCell {
        C0R0, C1R0, C0R1, C1R1;

        @Override public int col() { return ordinal() % 2; }
        @Override public int row() { return ordinal() / 2; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final EnumProperty<Cell> CELL = EnumProperty.create("cell", Cell.class);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoorHingeSide> HINGE = BlockStateProperties.DOOR_HINGE;
    // structure frame px (north-facing, x from the first column's west edge), the right-hinged gate (tools/build-trash-enclosure-v1.mjs
    // GATE, mm + 500 then x 0.016): the post, the closed leaf, the open leaf inside the cells
    private static final double[] POST = {0.8, 0, 14.4, 2.4, 32, 15.97};
    private static final double[] CLOSED = {2.98, 1.2, 14.27, 31.87, 31.6, 15.07};
    private static final double[] OPENED = {2.72, 1.2, 0, 3.52, 31.6, 13.89};
    private static final float PITCH_SPREAD = 0.06F;
    private final Map<String, VoxelShape> gateShapes = new ConcurrentHashMap<>();

    public EnclosureGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, Cell.C0R0)
                .setValue(OPEN, false).setValue(HINGE, DoorHingeSide.RIGHT));
    }

    @Override protected EnumProperty<Cell> cellProperty() { return CELL; }
    @Override protected Cell[] cells() { return Cell.values(); }
    @Override public Cell master() { return Cell.C0R0; }
    @Override protected double[][] boxes() { return new double[][]{POST, CLOSED}; }

    /** A screen wall in the cell behind, on the edge on this side. */
    private static boolean wallBehind(BlockGetter level, BlockPos cell, Direction facing, Direction side) {
        BlockState behind = level.getBlockState(cell.relative(facing.getOpposite()));
        return behind.getBlock() instanceof CmuScreenWallBlock && behind.getValue(CmuScreenWallBlock.edge(side));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Direction facing = state.getValue(FACING);
        BlockPos first = context.getClickedPos(), second = first.relative(facing.getClockWise());
        boolean left = !wallBehind(context.getLevel(), first, facing, facing.getCounterClockWise()) && wallBehind(context.getLevel(), second, facing, facing.getClockWise());
        return state.setValue(HINGE, left ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        boolean open = !state.getValue(OPEN);
        BlockPos origin = origin(pos, state);
        Direction facing = state.getValue(FACING);
        for (Cell cell : Cell.values()) {
            BlockPos at = cellPosition(origin, facing, cell);
            BlockState other = level.getBlockState(at);
            if (other.is(this) && other.getValue(CELL) == cell) level.setBlock(at, other.setValue(OPEN, open), UPDATE_CLIENTS | UPDATE_NEIGHBORS);
        }
        BlockPos master = masterPosition(pos, state);
        level.playSound(player, master, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS,
                1.0F, 0.8F + (level.getRandom().nextFloat() - 0.5F) * PITCH_SPREAD);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, master);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Cell cell = state.getValue(CELL);
        boolean open = state.getValue(OPEN), left = state.getValue(HINGE) == DoorHingeSide.LEFT;
        Direction facing = state.getValue(FACING);
        return gateShapes.computeIfAbsent(cell.getSerializedName() + "/" + facing.getName() + "/" + open + "/" + left,
                k -> HorizontalShapeUtils.rotations(cellShape(cell, open ? new double[][]{POST, OPENED} : new double[][]{POST, CLOSED}, left)).get(facing));
    }

    /** One cell's part of the right-hinged boxes, mirrored across the structure's middle (x 16) for a left hinge. */
    private static VoxelShape cellShape(Cell cell, double[][] boxes, boolean left) {
        double ox = 16 * cell.col(), oy = 16 * cell.row();
        VoxelShape shape = Shapes.empty();
        for (double[] box : boxes) {
            double bx0 = left ? 32 - box[3] : box[0], bx1 = left ? 32 - box[0] : box[3];
            double x0 = Math.max(bx0, ox), x1 = Math.min(bx1, ox + 16), y0 = Math.max(box[1], oy), y1 = Math.min(box[4], oy + 16);
            if (x1 <= x0 || y1 <= y0) continue;
            shape = Shapes.or(shape, Block.box(x0 - ox, y0 - oy, box[2], x1 - ox, y1 - oy, box[5]));
        }
        return shape.optimize();
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new EnclosureGateBlockEntity(pos, state) : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL, OPEN, HINGE);
    }
}
