package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.PriceSignBlockEntity;
import com.antaurora.apofirstlight.energy.AflPowerPortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The PRAIRIE roadside price sign (Fuel Stop A1 details V1, docs/models/fuel_stop_a1_details_v1.md,
 * tools/build-prairie-signage-v1.mjs): 4 cells wide, 4 high, both faces alike. The master (the second column's bottom cell)
 * draws it all and has the power port in its underside, where an underground cable comes up through the footing pad.
 * {@link #DIGITS}: the LED prices show (only while powered: user 2026-10-09, "价格数字默认不显示，但是如果有电了就固定显示
 * 一个"); {@link #LIT}: the PRAIRIE light box is lit (powered and dark). PriceSignBlockEntity switches both.
 */
public final class PriceSignBlock extends RectMultiblockBlock<PriceSignBlock.Cell> implements EntityBlock, AflPowerPortBlock {
    public enum Cell implements StringRepresentable, RectMultiblockBlock.GridCell {
        C0R0, C1R0, C2R0, C3R0, C0R1, C1R1, C2R1, C3R1, C0R2, C1R2, C2R2, C3R2, C0R3, C1R3, C2R3, C3R3;

        @Override public int col() { return ordinal() % 4; }
        @Override public int row() { return ordinal() / 4; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final EnumProperty<Cell> CELL = EnumProperty.create("cell", Cell.class);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty DIGITS = BooleanProperty.create("digits");
    // structure frame px (tools/build-prairie-signage-v1.mjs SIGN; the sign's centre at x 32): footing pad, base, cap, cabinet
    private static final double[][] BOXES = {
            {0, 0, 0, 64, 0.4, 16}, {0.8, 0.4, 3.2, 63.2, 14.4, 12.8}, {0.16, 14.4, 2.56, 63.84, 15.68, 13.44}, {1.6, 15.68, 4.8, 62.4, 60.8, 11.2}};

    public PriceSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, Cell.C1R0).setValue(LIT, false).setValue(DIGITS, false));
    }

    @Override protected EnumProperty<Cell> cellProperty() { return CELL; }
    @Override protected Cell[] cells() { return Cell.values(); }
    @Override public Cell master() { return Cell.C1R0; }
    @Override protected double[][] boxes() { return BOXES; }

    /** Block light of a cell: the light box rows while it is lit, a little from the LED rows while the prices show. */
    public static int lightLevel(BlockState state) {
        int row = state.getValue(CELL).row();
        int box = state.getValue(LIT) ? (row == 3 ? 10 : row == 2 ? 8 : 0) : 0;
        int digits = state.getValue(DIGITS) && (row == 1 || row == 2) ? 4 : 0;
        return Math.max(box, digits);
    }

    /** The one power port: the master's underside (docs/models/power_cable_v2.md). */
    @Override
    public boolean hasPowerPort(BlockState state, Direction face) {
        return face == Direction.DOWN && isMaster(state);
    }

    /** Both looks on every cell (each cell's light follows them). */
    public void setLook(Level level, BlockPos master, boolean digits, boolean lit) {
        BlockState state = level.getBlockState(master);
        if (!state.is(this) || !isMaster(state) || (state.getValue(DIGITS) == digits && state.getValue(LIT) == lit)) return;
        Direction facing = state.getValue(FACING);
        BlockPos origin = origin(master, state);
        for (Cell cell : Cell.values()) {
            BlockPos position = cellPosition(origin, facing, cell);
            BlockState other = level.getBlockState(position);
            if (other.is(this) && other.getValue(CELL) == cell) level.setBlock(position, other.setValue(DIGITS, digits).setValue(LIT, lit), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return isMaster(state) ? new PriceSignBlockEntity(position, state) : null;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !isMaster(state)) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<PriceSignBlockEntity>) (tickerLevel, tickerPos, tickerState, sign) -> sign.serverTick();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL, LIT, DIGITS);
    }
}
