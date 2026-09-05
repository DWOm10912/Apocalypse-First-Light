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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/** Tick-local cable coordination. FE remains exclusively in endpoint capabilities. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class PowerCableTransfer {
    private static final int MAX_CABLE_NODES = 4_096;
    private static final Map<ServerLevel, TickState> LEVELS = new WeakHashMap<>();
    private static boolean nodeLimitWarningLogged;

    private PowerCableTransfer() {}

    public static int transferFrom(ServerLevel level, BlockPos sourcePosition, Direction outputFace,
                                   IEnergyStorage source, int outputBudget) {
        BlockPos adjacent = sourcePosition.relative(outputFace);
        if (!level.hasChunkAt(adjacent)) return 0;
        if (level.getBlockState(adjacent).is(AflBlocks.POWER_CABLE.get())) {
            if (PowerCableBlock.isConnected(level.getBlockState(adjacent), outputFace.getOpposite())) {
                // All producers finish generating before the level-END settlement.
                state(level).seeds.add(adjacent.immutable());
            }
            return 0;
        }
        BlockEntity target = level.getBlockEntity(adjacent);
        if (target == null) return 0;
        return target.getCapability(ForgeCapabilities.ENERGY, outputFace.getOpposite())
                .map(receiver -> move(source, receiver, Math.max(0, outputBudget))).orElse(0);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            distributePending(level);
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        LEVELS.remove(event.getLevel());
    }

    /** Also used by DEV tests after explicitly ticking their producers. */
    public static void distributePending(ServerLevel level) {
        TickState tick = state(level);
        List<BlockPos> seeds = new ArrayList<>(tick.seeds);
        tick.seeds.clear();
        seeds.sort(BlockPos::compareTo);
        for (BlockPos seed : seeds) {
            if (tick.settledCables.contains(seed)) continue;
            Component component = scan(level, seed);
            if (component.cables.isEmpty() || !component.complete) continue;
            BlockPos canonical = Collections.min(component.cables);
            tick.settledCables.addAll(component.cables);
            if (tick.settledKeys.add(canonical)) {
                distribute(component.endpoints, level.getGameTime());
            }
        }
    }

    private static TickState state(ServerLevel level) {
        TickState tick = LEVELS.get(level);
        if (tick == null || tick.time != level.getGameTime()) {
            tick = new TickState(level.getGameTime());
            LEVELS.put(level, tick);
        }
        return tick;
    }

    private static Component scan(ServerLevel level, BlockPos seed) {
        Queue<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> discovered = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        Set<EndpointKey> keys = new HashSet<>();
        Set<IEnergyStorage> storages = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(seed);
        discovered.add(seed);
        while (!pending.isEmpty()) {
            BlockPos pos = pending.remove();
            if (!level.hasChunkAt(pos)) continue;
            BlockState cable = level.getBlockState(pos);
            if (!cable.is(AflBlocks.POWER_CABLE.get())) continue;
            for (Direction direction : Direction.values()) {
                if (!PowerCableBlock.isConnected(cable, direction)) continue;
                BlockPos neighbor = pos.relative(direction);
                if (!level.hasChunkAt(neighbor)) continue;
                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.is(AflBlocks.POWER_CABLE.get())) {
                    if (PowerCableBlock.isConnected(neighborState, direction.getOpposite())
                            && discovered.add(neighbor)) {
                        if (discovered.size() > MAX_CABLE_NODES) {
                            if (!nodeLimitWarningLogged) {
                                nodeLimitWarningLogged = true;
                                ApocalypseFirstLight.LOGGER.warn(
                                        "[AFL ELECTRICITY] Cable component exceeds {} nodes; settlement aborted",
                                        MAX_CABLE_NODES);
                            }
                            return new Component(discovered, endpoints, false);
                        }
                        pending.add(neighbor);
                    }
                    continue;
                }
                Direction face = direction.getOpposite();
                if (!keys.add(new EndpointKey(neighbor, face))) continue;
                BlockEntity be = level.getBlockEntity(neighbor);
                if (be != null) {
                    be.getCapability(ForgeCapabilities.ENERGY, face).resolve().ifPresent(storage -> {
                        if (storages.add(storage)) endpoints.add(new Endpoint(neighbor, face, storage));
                    });
                }
            }
        }
        endpoints.sort(Comparator.comparing(Endpoint::position).thenComparing(Endpoint::face));
        return new Component(discovered, endpoints, true);
    }

    private static void distribute(List<Endpoint> endpoints, long time) {
        List<IEnergyStorage> sources = new ArrayList<>();
        List<IEnergyStorage> receivers = new ArrayList<>();
        long available = 0;
        for (Endpoint endpoint : endpoints) {
            IEnergyStorage storage = endpoint.storage;
            int output = storage.canExtract() ? storage.extractEnergy(Integer.MAX_VALUE, true) : 0;
            if (output > 0) {
                sources.add(storage);
                available += output;
            } else if (storage.canReceive() && storage.receiveEnergy(Integer.MAX_VALUE, true) > 0) {
                receivers.add(storage);
            }
        }
        if (available == 0 || receivers.isEmpty()) return;
        Collections.rotate(receivers, -Math.floorMod(time, receivers.size()));
        Collections.rotate(sources, -Math.floorMod(time, sources.size()));
        long[] planned = new long[receivers.size()];
        int[] demand = new int[receivers.size()];
        for (int i = 0; i < demand.length; i++) {
            demand[i] = receivers.get(i).receiveEnergy(Integer.MAX_VALUE, true);
        }
        // Water filling: saturated consumers leave the active set; unused shares are redistributed.
        while (available > 0) {
            int active = 0;
            for (int i = 0; i < demand.length; i++) {
                if (planned[i] < demand[i]) active++;
            }
            if (active == 0) break;
            long share = Math.max(1, available / active);
            for (int i = 0; i < demand.length && available > 0; i++) {
                long give = Math.min(available, Math.min(share, demand[i] - planned[i]));
                planned[i] += give;
                available -= give;
            }
        }
        for (int i = 0; i < receivers.size(); i++) {
            int remaining = (int) planned[i];
            for (IEnergyStorage source : sources) {
                if (remaining <= 0) break;
                remaining -= move(source, receivers.get(i), remaining);
            }
        }
    }

    private static int move(IEnergyStorage source, IEnergyStorage receiver, int limit) {
        if (source == receiver || !receiver.canReceive() || limit <= 0) return 0;
        int offered = source.extractEnergy(limit, true);
        int acceptedPlan = receiver.receiveEnergy(offered, true);
        if (acceptedPlan <= 0) return 0;
        // Preserve the live transfer contract: simulations immediately precede execution on
        // the server thread. AFL wrappers share per-tick budgets across all queries.
        int accepted = receiver.receiveEnergy(acceptedPlan, false);
        int extracted = source.extractEnergy(accepted, false);
        if (extracted != accepted) {
            int rolledBack = receiver.extractEnergy(accepted - extracted, false);
            ApocalypseFirstLight.LOGGER.error(
                    "[AFL ELECTRICITY] Capability violated simulation: accepted={}, extracted={}, rollback={}",
                    accepted, extracted, rolledBack);
        }
        return extracted;
    }

    private record EndpointKey(BlockPos position, Direction face) {}
    private record Endpoint(BlockPos position, Direction face, IEnergyStorage storage) {}
    private record Component(Set<BlockPos> cables, List<Endpoint> endpoints, boolean complete) {}
    private static final class TickState {
        final long time;
        final Set<BlockPos> seeds = new HashSet<>();
        final Set<BlockPos> settledCables = new HashSet<>();
        final Set<BlockPos> settledKeys = new HashSet<>();
        TickState(long time) { this.time = time; }
    }
}
