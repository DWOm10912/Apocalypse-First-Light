package com.antaurora.apofirstlight.energy;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PowerCableBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public final class PowerCableTransfer {
    private static final int MAX_CABLE_NODES = 4_096;
    private static boolean nodeLimitWarningLogged;

    private PowerCableTransfer() {
    }

    public static int transferFrom(ServerLevel level, BlockPos sourcePosition, Direction outputFace,
                                   IEnergyStorage source, int outputBudget) {
        int available = source.extractEnergy(Math.max(0, outputBudget), true);
        if (available <= 0) {
            return 0;
        }

        BlockPos adjacentPosition = sourcePosition.relative(outputFace);
        if (!level.hasChunkAt(adjacentPosition)) {
            return 0;
        }

        BlockState adjacentState = level.getBlockState(adjacentPosition);
        List<Endpoint> endpoints;
        if (adjacentState.is(AflBlocks.POWER_CABLE.get())) {
            if (!PowerCableBlock.isConnected(adjacentState, outputFace.getOpposite())) {
                return 0;
            }
            endpoints = findCableEndpoints(level, sourcePosition, adjacentPosition);
        } else {
            endpoints = new ArrayList<>(1);
            addEndpoint(level, sourcePosition, adjacentPosition, outputFace.getOpposite(),
                    new HashSet<>(), endpoints);
        }

        int remaining = available;
        int transferred = 0;
        for (Endpoint endpoint : endpoints) {
            if (remaining <= 0) {
                break;
            }
            IEnergyStorage receiver = endpoint.storage();
            int simulatedAcceptance = receiver.receiveEnergy(remaining, true);
            if (simulatedAcceptance <= 0) {
                continue;
            }

            int accepted = receiver.receiveEnergy(simulatedAcceptance, false);
            if (accepted <= 0) {
                continue;
            }
            int extracted = source.extractEnergy(accepted, false);
            if (extracted != accepted) {
                int rollbackRequired = accepted - extracted;
                int rolledBack = receiver.extractEnergy(rollbackRequired, false);
                ApocalypseFirstLight.LOGGER.error(
                        "[AFL ELECTRICITY] Transfer mismatch at endpoint {} face {}: accepted={}, extracted={}, rolledBack={}",
                        endpoint.position(), endpoint.face(), accepted, extracted, rolledBack);
                accepted = extracted;
            }

            remaining -= accepted;
            transferred += accepted;
        }
        return transferred;
    }

    private static List<Endpoint> findCableEndpoints(ServerLevel level, BlockPos sourcePosition,
                                                      BlockPos firstCablePosition) {
        Queue<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<EndpointKey> endpointKeys = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        pending.add(firstCablePosition.immutable());

        while (!pending.isEmpty()) {
            if (visited.size() >= MAX_CABLE_NODES) {
                if (!nodeLimitWarningLogged) {
                    nodeLimitWarningLogged = true;
                    ApocalypseFirstLight.LOGGER.warn(
                            "[AFL ELECTRICITY] Power cable scan reached the {} node safety limit; further nodes were ignored",
                            MAX_CABLE_NODES);
                }
                break;
            }

            BlockPos cablePosition = pending.remove();
            if (!visited.add(cablePosition) || !level.hasChunkAt(cablePosition)) {
                continue;
            }
            BlockState cableState = level.getBlockState(cablePosition);
            if (!cableState.is(AflBlocks.POWER_CABLE.get())) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                if (!PowerCableBlock.isConnected(cableState, direction)) {
                    continue;
                }
                BlockPos neighborPosition = cablePosition.relative(direction);
                if (!level.hasChunkAt(neighborPosition)) {
                    continue;
                }
                BlockState neighborState = level.getBlockState(neighborPosition);
                if (neighborState.is(AflBlocks.POWER_CABLE.get())) {
                    if (PowerCableBlock.isConnected(neighborState, direction.getOpposite())
                            && !visited.contains(neighborPosition)) {
                        pending.add(neighborPosition.immutable());
                    }
                } else {
                    addEndpoint(level, sourcePosition, neighborPosition, direction.getOpposite(),
                            endpointKeys, endpoints);
                }
            }
        }
        return endpoints;
    }

    private static void addEndpoint(ServerLevel level, BlockPos sourcePosition, BlockPos endpointPosition,
                                    Direction endpointFace, Set<EndpointKey> endpointKeys,
                                    List<Endpoint> endpoints) {
        if (endpointPosition.equals(sourcePosition)) {
            return;
        }
        EndpointKey key = new EndpointKey(endpointPosition.immutable(), endpointFace);
        if (!endpointKeys.add(key)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(endpointPosition);
        if (blockEntity == null) {
            return;
        }
        Optional<IEnergyStorage> storage = blockEntity
                .getCapability(ForgeCapabilities.ENERGY, endpointFace)
                .resolve();
        if (storage.isPresent() && storage.get().canReceive()) {
            endpoints.add(new Endpoint(key.position(), key.face(), storage.get()));
        }
    }

    private record EndpointKey(BlockPos position, Direction face) {
    }

    private record Endpoint(BlockPos position, Direction face, IEnergyStorage storage) {
    }
}
