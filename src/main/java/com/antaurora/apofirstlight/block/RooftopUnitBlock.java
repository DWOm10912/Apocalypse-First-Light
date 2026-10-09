package com.antaurora.apofirstlight.block;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Locale;

/**
 * A packaged rooftop unit (Fuel Stop A1 details V1, docs/models/fuel_stop_a1_details_v1.md, tools/build-store-roof-v1.mjs):
 * 2 cells long, 1 deep, 2 high, on its roof curb. V1 is its look only (user 2026-10-09: no power, no fan yet). The master is
 * the first column's bottom cell.
 */
public final class RooftopUnitBlock extends RectMultiblockBlock<RooftopUnitBlock.Cell> {
    public enum Cell implements StringRepresentable, RectMultiblockBlock.GridCell {
        C0R0, C1R0, C0R1, C1R1;

        @Override public int col() { return ordinal() % 2; }
        @Override public int row() { return ordinal() / 2; }
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final EnumProperty<Cell> CELL = EnumProperty.create("cell", Cell.class);
    // structure frame px (tools/build-store-roof-v1.mjs RTU; the model's x origin at the master's centre, x 8): roof curb,
    // base rail and cabinet (the rain hood and the parts on the casing stay out of the shape)
    private static final double[][] BOXES = {
            {8 - 260 * 0.016, 0, 8 - 420 * 0.016, 8 + 1310 * 0.016, 350 * 0.016, 8 + 420 * 0.016},
            {8 - 300 * 0.016, 350 * 0.016, 8 - 475 * 0.016, 8 + 1350 * 0.016, 1400 * 0.016, 8 + 475 * 0.016}};

    public RooftopUnitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CELL, Cell.C0R0));
    }

    @Override protected EnumProperty<Cell> cellProperty() { return CELL; }
    @Override protected Cell[] cells() { return Cell.values(); }
    @Override public Cell master() { return Cell.C0R0; }
    @Override protected double[][] boxes() { return BOXES; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CELL);
    }
}
