package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only V1A.6 validator for the authoritative rendered corridor. */
public final class HighwayContinuityValidator {
    private HighwayContinuityValidator() {}

    public static Result validate(ServerLevel level, HighwayCorridor corridor, HighwayProfile profile) {
        int actual = 0;
        int missing = 0;
        int wrongMaterial = 0;
        int bridgeInternalNonViaduct = 0;
        int viaductOutsideStructural = 0;
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.roadY(), cell.z()));
            if (state.isAir()) missing++;
            else actual++;
            if (!state.is(HighwayPalette.ASPHALT.getBlock())) wrongMaterial++;
            if (profile.isWithinResolvedBridgeSpan(cell.distance())
                    && cell.mode() != HighwayTerrainMode.VIADUCT) bridgeInternalNonViaduct++;
            if (cell.mode() == HighwayTerrainMode.VIADUCT && !cell.structuralBridge()) {
                viaductOutsideStructural++;
            }
        }

        int missingViaductDeck = 0;
        int bridgeNormalBaseIntrusions = 0;
        int elevatedNormalBaseIntrusions = 0;
        int structuralMissingConcrete = 0;
        for (HighwayCorridor.Cell cell : corridor.bridgeCells()) {
            if (!cell.structuralBridge()) continue;
            boolean missingConcrete = false;
            for (int offset = 1; offset <= 3; offset++) {
                BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.roadY() - offset, cell.z()));
                if (!state.is(HighwayPalette.REINFORCED_CONCRETE.getBlock())) {
                    missingConcrete = true;
                    if (cell.mode() == HighwayTerrainMode.VIADUCT) missingViaductDeck++;
                }
                if (isNormalRoadBase(state)) {
                    if (cell.mode() == HighwayTerrainMode.VIADUCT) bridgeNormalBaseIntrusions++;
                    else elevatedNormalBaseIntrusions++;
                }
            }
            if (missingConcrete) structuralMissingConcrete++;
        }

        int unexpectedAirspace = 0;
        int roadFurnitureIgnored = 0;
        int remainingLogs = 0;
        int remainingLeaves = 0;
        for (HighwayCorridor.Column column : corridor.rowEnvelope()) {
            for (int y = column.roadY() + 1;
                 y <= column.roadY() + HighwayCorridor.VERTICAL_CLEARANCE; y++) {
                BlockPos pos = new BlockPos(column.x(), y, column.z());
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                if (isExpectedRoadFurniture(corridor, pos, state)) {
                    roadFurnitureIgnored++;
                    continue;
                }
                unexpectedAirspace++;
                if (state.is(BlockTags.LOGS)) remainingLogs++;
                if (state.is(BlockTags.LEAVES)) remainingLeaves++;
            }
        }

        int terrainAirspaceViolations = 0;
        int cutColumnsWithIntrusion = 0;
        for (HighwayCorridor.CutColumn column : corridor.cutColumns()) {
            boolean intrusion = column.capped(level.getMaxBuildHeight());
            int top = column.clearanceTopY(level.getMaxBuildHeight());
            for (int y = column.roadY() + 1; y <= top; y++) {
                BlockPos pos = new BlockPos(column.x(), y, column.z());
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || isExpectedRoadFurniture(corridor, pos, state)) continue;
                terrainAirspaceViolations++;
                intrusion = true;
            }
            if (intrusion) cutColumnsWithIntrusion++;
        }

        int expectedParapet = 0;
        int actualParapet = 0;
        for (HighwayCorridor.Cell cell : corridor.bridgeCells()) {
            if (!cell.structuralBridge() || cell.role() != HighwayCorridor.Role.BRIDGE_EDGE) continue;
            expectedParapet++;
            if (level.getBlockState(new BlockPos(cell.x(), cell.roadY() + 1, cell.z()))
                    .is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock())) actualParapet++;
        }

        int medianBarrier = 0;
        for (HighwayCorridor.Cell cell : corridor.cells()) {
            if (cell.role() == HighwayCorridor.Role.MEDIAN && cell.lateral() == 0
                    && level.getBlockState(new BlockPos(cell.x(), cell.roadY() + 1, cell.z()))
                    .is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock())) medianBarrier++;
        }

        return new Result(actual, missing, unexpectedAirspace, missingViaductDeck, wrongMaterial,
                bridgeInternalNonViaduct, bridgeNormalBaseIntrusions, elevatedNormalBaseIntrusions,
                viaductOutsideStructural, structuralMissingConcrete, cutColumnsWithIntrusion,
                terrainAirspaceViolations, unexpectedAirspace, roadFurnitureIgnored,
                expectedParapet, actualParapet, expectedParapet - actualParapet, medianBarrier,
                remainingLogs, remainingLeaves, remainingLogs + remainingLeaves);
    }

    private static boolean isNormalRoadBase(BlockState state) {
        return state.is(HighwayPalette.BASE.getBlock())
                || state.is(HighwayPalette.SUB_BASE.getBlock())
                || state.is(HighwayPalette.FILL.getBlock());
    }

    private static boolean isExpectedRoadFurniture(HighwayCorridor corridor, BlockPos pos, BlockState state) {
        return corridor.isExpectedRoadFurniture(pos.getX(), pos.getY(), pos.getZ())
                && state.is(HighwayPalette.REINFORCED_CONCRETE_SLAB.getBlock());
    }

    public record Result(int actualSurfaceCells, int missingSurfaceCells, int clearanceViolations,
                         int missingViaductDeckCells, int wrongSurfaceMaterialCells,
                         int bridgeSpanInternalNonViaductCells, int bridgeSpanNormalBaseIntrusions,
                         int elevatedNormalBaseIntrusions, int viaductOutsideStructuralBridgeCells,
                         int structuralBridgeMissingConcreteCells,
                         int cutColumnsWithRemainingTerrainIntrusion, int terrainAirspaceViolations,
                         int unexpectedAirspaceObstructions,
                         int roadFurnitureBlocksIgnoredByAirspaceValidator,
                         int expectedParapetBlocks, int actualParapetBlocks,
                         int missingParapetBlocks, int medianBarrierBlocks,
                         int remainingRowLogs, int remainingRowLeaves,
                         int vegetationClearanceViolations) {
        public int airspaceClearanceViolations() { return clearanceViolations; }
    }
}
