package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.FluidPipeBlock;
import com.antaurora.apofirstlight.block.FluidTankBlock;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.ToIntFunction;

public final class FluidPipeTransfer {
    public static final int MAX_PIPE_NODES = 512;
    private static boolean nodeLimitWarningLogged;

    private FluidPipeTransfer() {
    }

    public static int transferFrom(ServerLevel level, FluidTankBlockEntity sourceTank) {
        BlockState sourceState = sourceTank.getBlockState();
        if (!sourceTank.isController()
                || !sourceState.getValue(FluidTankBlock.BOTTOM_CONNECTED)
                || sourceTank.getFluidAmount() <= 0) {
            return 0;
        }

        return transferFrom(level, sourceTank, Direction.DOWN, sourceTank::restoreControllerFluid);
    }

    public static int transferFrom(ServerLevel level, BlockEntity sourceBlockEntity,
                                   Direction sourceFace,
                                   ToIntFunction<FluidStack> restoreToSource) {
        BlockPos sourcePosition = sourceBlockEntity.getBlockPos();

        Optional<IFluidHandler> sourceOptional = sourceBlockEntity
                .getCapability(ForgeCapabilities.FLUID_HANDLER, sourceFace)
                .resolve();
        if (sourceOptional.isEmpty()) {
            return 0;
        }

        BlockPos firstPipePosition = sourcePosition.relative(sourceFace);
        if (!level.hasChunkAt(firstPipePosition)) {
            return 0;
        }
        BlockState firstPipeState = level.getBlockState(firstPipePosition);
        if (!firstPipeState.is(AflBlocks.FLUID_PIPE.get())
                || !FluidPipeBlock.canPipeEdgeConnect(level, firstPipePosition,
                sourcePosition, sourceFace.getOpposite())) {
            return 0;
        }

        IFluidHandler source = sourceOptional.get();
        FluidStack available = source.drain(
                FluidPortTransferBudget.STANDARD_FLUID_TRANSFER_RATE_MB_PER_TICK,
                IFluidHandler.FluidAction.SIMULATE);
        if (available.isEmpty()) {
            return 0;
        }

        SearchResult searchResult = findSinkRoutes(
                level, sourceBlockEntity, firstPipePosition, available);
        if (searchResult.limitExceeded()) {
            return 0;
        }

        SinkRoute blockedFallback = null;
        for (SinkRoute route : searchResult.routes()) {
            BlockEntity blockEntity = level.getBlockEntity(route.sinkPosition());
            if (blockEntity == null) {
                continue;
            }
            Optional<IFluidHandler> sinkOptional = blockEntity
                    .getCapability(ForgeCapabilities.FLUID_HANDLER, route.sinkFace())
                    .resolve();
            if (sinkOptional.isEmpty()) {
                continue;
            }

            IFluidHandler sink = sinkOptional.get();
            TargetAssessment assessment = assessTarget(sink, available);
            if (assessment.status() == TargetStatus.INVALID) {
                continue;
            }
            if (assessment.status() == TargetStatus.BLOCKED_BUT_VALID) {
                if (blockedFallback == null) {
                    blockedFallback = route;
                }
                continue;
            }

            FluidStack drained = source.drain(Math.min(assessment.acceptedAmount(),
                            FluidPortTransferBudget.STANDARD_FLUID_TRANSFER_RATE_MB_PER_TICK),
                    IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) {
                return 0;
            }
            int filled = sink.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            if (filled < drained.getAmount()) {
                FluidStack remainder = drained.copy();
                remainder.setAmount(drained.getAmount() - filled);
                int restored = restoreToSource.applyAsInt(remainder);
                if (restored != remainder.getAmount()) {
                    ApocalypseFirstLight.LOGGER.error(
                            "[AFL FLUID] Transfer rollback mismatch at source {}: drained={}, filled={}, restored={}",
                            sourcePosition, drained.getAmount(), filled, restored);
                }
            }
            if (filled > 0) {
                markRouteVisual(level, route, sourcePosition, drained, true);
                return filled;
            }
            if (blockedFallback == null) {
                blockedFallback = route;
            }
        }
        if (blockedFallback != null) {
            markRouteVisual(level, blockedFallback, sourcePosition, available, false);
        }
        return 0;
    }

    private static SearchResult findSinkRoutes(ServerLevel level, BlockEntity sourceBlockEntity,
                                               BlockPos firstPipePosition, FluidStack available) {
        Queue<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Map<BlockPos, BlockPos> predecessor = new HashMap<>();
        Map<BlockPos, Integer> distance = new HashMap<>();
        List<SinkRoute> routes = new ArrayList<>();
        pending.add(firstPipePosition.immutable());
        distance.put(firstPipePosition.immutable(), 0);

        while (!pending.isEmpty()) {
            if (visited.size() >= MAX_PIPE_NODES) {
                if (!nodeLimitWarningLogged) {
                    nodeLimitWarningLogged = true;
                    ApocalypseFirstLight.LOGGER.warn(
                            "[AFL FLUID] Fluid pipe scan reached the {} node safety limit; transfer was aborted",
                            MAX_PIPE_NODES);
                }
                return new SearchResult(List.of(), true);
            }

            BlockPos pipePosition = pending.remove();
            if (!visited.add(pipePosition) || !level.hasChunkAt(pipePosition)) {
                continue;
            }
            BlockState pipeState = level.getBlockState(pipePosition);
            if (!pipeState.is(AflBlocks.FLUID_PIPE.get())) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighborPosition = pipePosition.relative(direction);
                if (!level.hasChunkAt(neighborPosition)) {
                    continue;
                }
                if (!FluidPipeBlock.canPipeEdgeConnect(level, pipePosition,
                        neighborPosition, direction)) {
                    continue;
                }
                BlockState neighborState = level.getBlockState(neighborPosition);
                if (neighborState.is(AflBlocks.FLUID_PIPE.get())) {
                    if (!visited.contains(neighborPosition)
                            && !distance.containsKey(neighborPosition)) {
                        BlockPos immutableNeighbor = neighborPosition.immutable();
                        predecessor.put(immutableNeighbor, pipePosition);
                        distance.put(immutableNeighbor, distance.get(pipePosition) + 1);
                        pending.add(immutableNeighbor);
                    }
                } else {
                    Direction targetFace = direction.getOpposite();
                    TargetStatus targetStatus = classifyTargetFromSide(level, sourceBlockEntity,
                            neighborPosition, targetFace, available);
                    if (targetStatus != TargetStatus.INVALID) {
                        routes.add(new SinkRoute(neighborPosition.immutable(), targetFace,
                                buildPath(firstPipePosition, pipePosition, predecessor), targetStatus));
                    }
                }
            }
        }

        routes.sort(Comparator
                .comparingInt((SinkRoute route) -> route.targetStatus().priority())
                .thenComparingInt(route -> route.pipePath().size())
                .thenComparingInt(route -> route.sinkPosition().getX())
                .thenComparingInt(route -> route.sinkPosition().getZ())
                .thenComparingInt(route -> route.sinkPosition().getY()));
        return new SearchResult(List.copyOf(routes), false);
    }

    private static TargetStatus classifyTargetFromSide(ServerLevel level,
                                                       BlockEntity sourceBlockEntity,
                                                       BlockPos position,
                                                       Direction targetFace,
                                                       FluidStack available) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null || sharesFluidStorage(sourceBlockEntity, blockEntity)) {
            return TargetStatus.INVALID;
        }
        return blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, targetFace)
                .map(handler -> assessTarget(handler, available).status())
                .orElse(TargetStatus.INVALID);
    }

    private static TargetAssessment assessTarget(IFluidHandler handler, FluidStack available) {
        int accepted = handler.fill(available, IFluidHandler.FluidAction.SIMULATE);
        if (accepted > 0) {
            return new TargetAssessment(TargetStatus.CAN_FILL_NOW,
                    Math.min(accepted, available.getAmount()));
        }

        for (int tankIndex = 0; tankIndex < handler.getTanks(); tankIndex++) {
            if (handler.getTankCapacity(tankIndex) <= 0
                    || !handler.isFluidValid(tankIndex, available)) {
                continue;
            }
            FluidStack stored = handler.getFluidInTank(tankIndex);
            if (stored.isEmpty() || available.isFluidEqual(stored)) {
                return new TargetAssessment(TargetStatus.BLOCKED_BUT_VALID, 0);
            }
        }
        return new TargetAssessment(TargetStatus.INVALID, 0);
    }

    private static void markRouteVisual(ServerLevel level, SinkRoute route,
                                        BlockPos sourcePosition, FluidStack fluid, boolean isFlowing) {
        FluidStack visualFluid = fluid.copy();
        visualFluid.setAmount(1);
        FluidPipeVisualManager.markRoute(level, route.pipePath(), sourcePosition,
                route.sinkPosition(), visualFluid, isFlowing);
    }

    private static boolean sharesFluidStorage(BlockEntity source, BlockEntity target) {
        if (source == target) {
            return true;
        }
        return source instanceof FluidTankBlockEntity sourceTank
                && target instanceof FluidTankBlockEntity targetTank
                && sourceTank.sharesFluidStorageWith(targetTank);
    }

    private static List<BlockPos> buildPath(BlockPos firstPipePosition, BlockPos lastPipePosition,
                                            Map<BlockPos, BlockPos> predecessor) {
        List<BlockPos> reversed = new ArrayList<>();
        BlockPos cursor = lastPipePosition;
        reversed.add(cursor.immutable());
        while (!cursor.equals(firstPipePosition)) {
            cursor = predecessor.get(cursor);
            if (cursor == null) {
                return List.of();
            }
            reversed.add(cursor.immutable());
        }

        List<BlockPos> path = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            path.add(reversed.get(index));
        }
        return List.copyOf(path);
    }

    private record SearchResult(List<SinkRoute> routes, boolean limitExceeded) {
    }

    private record SinkRoute(BlockPos sinkPosition, Direction sinkFace, List<BlockPos> pipePath,
                             TargetStatus targetStatus) {
    }

    private record TargetAssessment(TargetStatus status, int acceptedAmount) {
    }

    private enum TargetStatus {
        CAN_FILL_NOW(0),
        BLOCKED_BUT_VALID(1),
        INVALID(2);

        private final int priority;

        TargetStatus(int priority) {
            this.priority = priority;
        }

        private int priority() {
            return priority;
        }
    }
}
