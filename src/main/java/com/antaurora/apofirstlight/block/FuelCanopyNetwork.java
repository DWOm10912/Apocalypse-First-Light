package com.antaurora.apofirstlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fuel Canopy Kit V1 wiring (docs/models/fuel_canopy_kit_v1.md): the canopy is wired inside, no cables. Columns pass the
 * power up and down (vertical neighbours only), the canopy pieces (ceiling, light, fascia) to every neighbouring canopy
 * piece and to the column under / over them. A connected set is one network: its column bases take the power through
 * their ports, the base with the lowest position runs it (FuelCanopyColumnBlockEntity) and lights all its lamps at once.
 */
public final class FuelCanopyNetwork {
    /** Blocks scanned at most per network (a larger canopy stays dark beyond it). */
    public static final int LIMIT = 4096;

    private FuelCanopyNetwork() {
    }

    /** A canopy piece: ceiling, light or fascia. */
    public interface Part {
    }

    public static boolean isColumn(BlockState state) {
        return state.getBlock() instanceof FuelCanopyColumnBlock;
    }

    public static boolean isPart(BlockState state) {
        return state.getBlock() instanceof Part;
    }

    public static boolean conducts(BlockState state) {
        return isColumn(state) || isPart(state);
    }

    /** Whether the wiring runs from {@code a} to its neighbour {@code b} on side {@code side}: columns only vertically. */
    public static boolean linked(BlockState a, BlockState b, Direction side) {
        if (!conducts(a) || !conducts(b)) return false;
        return side.getAxis() == Direction.Axis.Y || (isPart(a) && isPart(b));
    }

    /** The network's column bases (where the power comes in) and lamps. */
    public record Network(List<BlockPos> bases, List<BlockPos> lights) {
        /** The base that runs the network: the lowest position. */
        public BlockPos controller() {
            BlockPos best = null;
            for (BlockPos pos : bases) if (best == null || pos.asLong() < best.asLong()) best = pos;
            return best;
        }
    }

    /** The network {@code start} belongs to (loaded blocks only, at most {@link #LIMIT}). */
    public static Network scan(Level level, BlockPos start) {
        List<BlockPos> bases = new ArrayList<>(), lights = new ArrayList<>();
        BlockState first = level.getBlockState(start);
        if (!conducts(first)) return new Network(bases, lights);
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FuelCanopyColumnBlock && state.getValue(FuelCanopyColumnBlock.SEGMENT) == FuelCanopyColumnBlock.Segment.BASE) bases.add(pos);
            if (state.getBlock() instanceof FuelCanopyLightBlock) lights.add(pos);
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (seen.size() >= LIMIT || seen.contains(next) || !level.isLoaded(next)) continue;
                if (!linked(state, level.getBlockState(next), side)) continue;
                seen.add(next);
                queue.add(next);
            }
        }
        return new Network(bases, lights);
    }

    /** Server: switches the lamps on / off (only those that change). */
    public static void setLights(Level level, List<BlockPos> lights, boolean lit) {
        for (BlockPos pos : lights) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FuelCanopyLightBlock && state.getValue(FuelCanopyLightBlock.LIT) != lit) {
                level.setBlock(pos, state.setValue(FuelCanopyLightBlock.LIT, lit), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * Server: a canopy piece or column went away. Next tick, every network it leaves behind that has no column base left
     * goes dark (a network that still has a base is run by its controller, within a second). Queued, so the removal and
     * its drop are not disturbed.
     */
    public static void removed(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        BlockPos at = pos.immutable();
        server.getServer().tell(new TickTask(server.getServer().getTickCount(), () -> {
            Set<BlockPos> done = new HashSet<>();
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (done.contains(next) || !server.isLoaded(next) || !conducts(server.getBlockState(next))) continue;
                Network network = scan(server, next);
                done.addAll(network.lights());
                done.addAll(network.bases());
                if (network.bases().isEmpty()) setLights(server, network.lights(), false);
            }
        }));
    }
}
