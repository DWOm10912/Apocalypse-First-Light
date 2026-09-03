package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.FluidTankBlock;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public final class FluidTankStacks {
    public static final int MAX_TANK_STACK_HEIGHT = 4;

    private FluidTankStacks() {
    }

    public static boolean canPlaceTank(Level level, BlockPos candidatePosition) {
        List<BlockPos> prospectiveRun = findRunWithCandidate(level, candidatePosition);
        for (int start = 0; start < prospectiveRun.size(); start += MAX_TANK_STACK_HEIGHT) {
            int end = Math.min(start + MAX_TANK_STACK_HEIGHT, prospectiveRun.size());
            FluidStack selectedFluid = FluidStack.EMPTY;
            for (int index = start; index < end; index++) {
                BlockEntity blockEntity = level.getBlockEntity(prospectiveRun.get(index));
                if (!(blockEntity instanceof FluidTankBlockEntity tank)) {
                    continue;
                }
                FluidStack localFluid = tank.getLocalFluidForTopology();
                if (localFluid.isEmpty()) {
                    continue;
                }
                if (selectedFluid.isEmpty()) {
                    selectedFluid = localFluid;
                } else if (!selectedFluid.isFluidEqual(localFluid)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void rebuildColumn(ServerLevel level, BlockPos position) {
        if (!isTank(level, position)) {
            return;
        }
        List<BlockPos> run = findRun(level, position);
        List<FluidStack> fluids = snapshotAndClear(level, run, null);
        redistribute(level, List.of(run), fluids);
    }

    public static void handleTankRemoved(ServerLevel level, BlockPos removedPosition,
                                         FluidTankBlockEntity removedTank) {
        List<BlockPos> oldRun = findRunAroundRemovedPosition(level, removedPosition);
        List<FluidStack> fluids = snapshotAndClear(level, oldRun, removedTank);

        List<BlockPos> lowerRun = new ArrayList<>();
        List<BlockPos> upperRun = new ArrayList<>();
        for (BlockPos position : oldRun) {
            if (position.getY() < removedPosition.getY() && isTank(level, position)) {
                lowerRun.add(position);
            } else if (position.getY() > removedPosition.getY() && isTank(level, position)) {
                upperRun.add(position);
            }
        }

        List<List<BlockPos>> resultingRuns = new ArrayList<>(2);
        if (!lowerRun.isEmpty()) {
            resultingRuns.add(lowerRun);
        }
        if (!upperRun.isEmpty()) {
            resultingRuns.add(upperRun);
        }
        redistribute(level, resultingRuns, fluids);
    }

    private static List<FluidStack> snapshotAndClear(ServerLevel level, List<BlockPos> positions,
                                                     FluidTankBlockEntity removedTank) {
        List<FluidStack> fluids = new ArrayList<>();
        for (BlockPos position : positions) {
            FluidTankBlockEntity tank = position.equals(removedTank == null ? null : removedTank.getBlockPos())
                    ? removedTank
                    : getTank(level, position);
            if (tank == null) {
                continue;
            }
            FluidStack localFluid = tank.getLocalFluidForTopology();
            if (!localFluid.isEmpty()) {
                fluids.add(localFluid);
            }
            tank.clearLocalFluidForTopology();
        }
        return fluids;
    }

    private static void redistribute(ServerLevel level, List<List<BlockPos>> runs,
                                     List<FluidStack> fluidSources) {
        List<FluidStack> remaining = new ArrayList<>(fluidSources.size());
        for (FluidStack fluid : fluidSources) {
            if (!fluid.isEmpty()) {
                remaining.add(fluid.copy());
            }
        }

        for (List<BlockPos> run : runs) {
            for (int start = 0; start < run.size(); start += MAX_TANK_STACK_HEIGHT) {
                int end = Math.min(start + MAX_TANK_STACK_HEIGHT, run.size());
                List<BlockPos> group = run.subList(start, end);
                applyGroupStates(level, group);

                int capacity = FluidTankBlockEntity.CAPACITY_MB * group.size();
                FluidStack assignedFluid = takeNextCompatibleFluid(remaining, capacity);
                for (int memberIndex = 0; memberIndex < group.size(); memberIndex++) {
                    FluidTankBlockEntity tank = getTank(level, group.get(memberIndex));
                    if (tank != null) {
                        tank.applyTopologyRole(memberIndex == 0, group.size(), assignedFluid);
                    }
                }
            }
        }

        int lostAmount = remaining.stream().mapToInt(FluidStack::getAmount).sum();
        if (lostAmount > 0) {
            ApocalypseFirstLight.LOGGER.warn(
                    "[AFL FLUID] Tank topology rebuild discarded {} mB because the resulting stacks lacked compatible capacity",
                    lostAmount);
        }

        for (List<BlockPos> run : runs) {
            for (BlockPos position : run) {
                FluidTankBlockEntity tank = getTank(level, position);
                if (tank != null) {
                    tank.syncAfterTopologyChange();
                }
            }
        }
    }

    private static FluidStack takeNextCompatibleFluid(List<FluidStack> remaining, int capacity) {
        int firstIndex = firstNonEmptyIndex(remaining);
        if (firstIndex < 0 || capacity <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack template = remaining.get(firstIndex).copy();
        int needed = capacity;
        int assignedAmount = 0;
        for (FluidStack candidate : remaining) {
            if (needed <= 0 || candidate.isEmpty() || !template.isFluidEqual(candidate)) {
                continue;
            }
            int moved = Math.min(needed, candidate.getAmount());
            assignedAmount += moved;
            candidate.shrink(moved);
            needed -= moved;
        }
        FluidStack result = template.copy();
        result.setAmount(assignedAmount);
        return result;
    }

    private static int firstNonEmptyIndex(List<FluidStack> fluids) {
        for (int index = 0; index < fluids.size(); index++) {
            if (!fluids.get(index).isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static void applyGroupStates(ServerLevel level, List<BlockPos> group) {
        for (int memberIndex = 0; memberIndex < group.size(); memberIndex++) {
            BlockPos position = group.get(memberIndex);
            BlockState state = level.getBlockState(position);
            if (!(state.getBlock() instanceof FluidTankBlock)) {
                continue;
            }
            boolean hasTankBelow = memberIndex > 0;
            boolean hasTankAbove = memberIndex + 1 < group.size();
            boolean topConnected = !hasTankAbove && isPipe(level, position.above());
            boolean bottomConnected = !hasTankBelow && isPipe(level, position.below());
            BlockState updatedState = state
                    .setValue(FluidTankBlock.HAS_TANK_ABOVE, hasTankAbove)
                    .setValue(FluidTankBlock.HAS_TANK_BELOW, hasTankBelow)
                    .setValue(FluidTankBlock.TOP_CONNECTED, topConnected)
                    .setValue(FluidTankBlock.BOTTOM_CONNECTED, bottomConnected);
            if (updatedState != state) {
                level.setBlock(position, updatedState, Block.UPDATE_ALL);
            }
        }
    }

    private static List<BlockPos> findRun(Level level, BlockPos memberPosition) {
        BlockPos bottom = memberPosition;
        while (bottom.getY() > level.getMinBuildHeight() && isTank(level, bottom.below())) {
            bottom = bottom.below();
        }

        List<BlockPos> positions = new ArrayList<>();
        BlockPos cursor = bottom;
        while (cursor.getY() < level.getMaxBuildHeight() && isTank(level, cursor)) {
            positions.add(cursor.immutable());
            cursor = cursor.above();
        }
        return positions;
    }

    private static List<BlockPos> findRunWithCandidate(Level level, BlockPos candidatePosition) {
        BlockPos bottom = candidatePosition;
        while (bottom.getY() > level.getMinBuildHeight() && isTank(level, bottom.below())) {
            bottom = bottom.below();
        }

        List<BlockPos> positions = new ArrayList<>();
        BlockPos cursor = bottom;
        while (cursor.getY() < level.getMaxBuildHeight()) {
            if (!cursor.equals(candidatePosition) && !isTank(level, cursor)) {
                break;
            }
            positions.add(cursor.immutable());
            cursor = cursor.above();
        }
        return positions;
    }

    private static List<BlockPos> findRunAroundRemovedPosition(Level level, BlockPos removedPosition) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos cursor = removedPosition.below();
        while (cursor.getY() >= level.getMinBuildHeight() && isTank(level, cursor)) {
            positions.add(0, cursor.immutable());
            cursor = cursor.below();
        }
        positions.add(removedPosition.immutable());
        cursor = removedPosition.above();
        while (cursor.getY() < level.getMaxBuildHeight() && isTank(level, cursor)) {
            positions.add(cursor.immutable());
            cursor = cursor.above();
        }
        return positions;
    }

    private static boolean isTank(Level level, BlockPos position) {
        return level.hasChunkAt(position) && level.getBlockState(position).is(AflBlocks.FLUID_TANK.get());
    }

    private static boolean isPipe(Level level, BlockPos position) {
        return level.hasChunkAt(position) && level.getBlockState(position).is(AflBlocks.FLUID_PIPE.get());
    }

    private static FluidTankBlockEntity getTank(Level level, BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity instanceof FluidTankBlockEntity tank ? tank : null;
    }
}
