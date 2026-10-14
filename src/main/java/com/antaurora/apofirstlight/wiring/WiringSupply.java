package com.antaurora.apofirstlight.wiring;

import com.antaurora.apofirstlight.block.BuildingLightBlock;
import com.antaurora.apofirstlight.block.WallSwitchBlock;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Building Wiring V1 phase 2, which device a WIRED panel feeds (docs/gameplay/building_wiring_v1_design.md 5.1): a panel
 * solves its wiring ({@link #solve}) and registers the result in its level's device index; a light, an outlet or a service
 * entry asks the index ({@link #feed}, {@link #entryPanel}) instead of the old building footprint. Server side only.
 * <p>
 * Solving one circuit: start at the node in the panel's mount cell that taps the panel, walk its links and slope links
 * (loaded chunks only) and collect every tap: a load on circuits 1-4, a service entry on the feeder. A circuit whose wire
 * also reaches another panel is in conflict (two panels joined): neither feeds anything on it. A cell on the way that can no
 * longer hold wire (a block changed past the hooks, e.g. WorldEdit's fast mode) loses its nodes, without refund (design
 * 8.4).
 */
public final class WiringSupply {
    /** At most this many nodes per circuit are walked (a house holds a few hundred). */
    public static final int MAX_NODES = 4096;

    /**
     * What feeds a device over the wires: its panel, the circuit (1-4), the group it follows (phase 3, design 6): the tap's
     * group as it counts for the device. A light 0 (always on), 1-8 or 15 (photocell); a switch 1-8, 0 when it controls
     * nothing; an outlet and the emergency light always 0 (a switch never cuts them).
     */
    public record Feed(BlockPos panel, int circuit, int group, Role role) {}

    /** What a load is to its groups: a light follows them, a switch sets them, everything else (outlets, the emergency light's charger) ignores them. */
    public enum Role { LIGHT, SWITCH, OTHER }

    /** One panel's solved wiring: per circuit (index 1-5) the devices it reaches, whether it is joined to another panel. */
    public static final class Result {
        public final int[] devices = new int[WiringNode.CIRCUITS + 1];
        public final boolean[] wired = new boolean[WiringNode.CIRCUITS + 1];
        public final boolean[] conflict = new boolean[WiringNode.CIRCUITS + 1];
        /** Per circuit 1-4 and group 0-15: the lights, the switches that follow it; per circuit the other loads (outlets...). */
        public final int[][] lights = new int[WiringNode.BREAKERS + 1][16], switches = new int[WiringNode.BREAKERS + 1][16];
        public final int[] others = new int[WiringNode.BREAKERS + 1];
        /** Some wire runs into a chunk that is not loaded (the devices there have no power until it is). */
        public boolean partial;

        /** A light group is in use on the circuit: a light or a switch follows it. */
        public boolean used(int circuit, int group) {
            return circuit >= 1 && circuit <= WiringNode.BREAKERS && (lights[circuit][group & 15] > 0 || switches[circuit][group & 15] > 0);
        }
    }

    private static final class Index {
        final Map<BlockPos, Feed> devices = new HashMap<>();
        final Map<BlockPos, BlockPos> entries = new HashMap<>();
        final Map<BlockPos, List<BlockPos>> byPanel = new HashMap<>();
    }

    private static final Map<Level, Index> INDEX = new WeakHashMap<>();

    private WiringSupply() {}

    private static Index index(Level level) {
        return INDEX.computeIfAbsent(level, k -> new Index());
    }

    /** The feed of the load at {@code device}, or null (no WIRED panel reaches it). */
    @Nullable
    public static Feed feed(Level level, BlockPos device) {
        Index i = INDEX.get(level);
        return i == null ? null : i.devices.get(device);
    }

    /** The WIRED panel the service entry at {@code entry} feeds over its feeder, or null. */
    @Nullable
    public static BlockPos entryPanel(Level level, BlockPos entry) {
        Index i = INDEX.get(level);
        return i == null ? null : i.entries.get(entry);
    }

    /**
     * The panel the circuit's wire through cell taps (walking its links and slope links in loaded chunks, as a solve does), or
     * null: the wrench's group picker (phase 5) for a device tapped a moment ago, before any panel has solved it.
     */
    @Nullable
    public static BlockPos panelOf(ServerLevel level, BlockPos cell, int circuit) {
        if (BuildingWiring.node(level, cell, circuit) == 0) return null;
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(cell.asLong());
        queue.enqueue(cell.asLong());
        while (!queue.isEmpty() && seen.size() <= MAX_NODES) {
            BlockPos at = BlockPos.of(queue.dequeueLong());
            long node = BuildingWiring.node(level, at, circuit);
            if (node == 0) continue;
            for (Direction d : Direction.values()) {
                if (WiringNode.tapped(node, d)) {
                    BlockPos dev = at.relative(d);
                    if (BuildingWiring.chunk(level, dev) != null) {
                        BlockState s = level.getBlockState(dev);
                        if (BuildingWiring.kind(s) == BuildingWiring.Kind.PANEL && at.equals(BuildingWiring.mount(s, dev))) return dev.immutable();
                    }
                }
                BlockPos o = at.relative(d);
                if (WiringNode.linked(node, d) && BuildingWiring.chunk(level, o) != null && seen.add(o.asLong())) queue.enqueue(o.asLong());
            }
            for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : new boolean[]{true, false}) {
                BlockPos o = BuildingWiring.slopeCell(at, h, up);
                if (WiringNode.slopeLinked(node, h, up) && BuildingWiring.chunk(level, o) != null && seen.add(o.asLong())) queue.enqueue(o.asLong());
            }
        }
        return null;
    }

    /** The devices the panel feeds on the circuit that follow the group (lights and switches; phase 3), in loaded chunks. */
    public static List<BlockPos> members(Level level, BlockPos panel, int circuit, int group) {
        Index i = INDEX.get(level);
        List<BlockPos> mine = i == null ? null : i.byPanel.get(panel);
        if (mine == null) return List.of();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : mine) {
            Feed f = i.devices.get(p);
            if (f != null && f.panel().equals(panel) && f.circuit() == circuit && f.group() == group && f.role() != Role.OTHER) out.add(p);
        }
        return out;
    }

    /** Every switch the panel feeds, with its feed (to line them up with the panel's groups after a solve). */
    public static Map<BlockPos, Feed> switches(Level level, BlockPos panel) {
        Index i = INDEX.get(level);
        List<BlockPos> mine = i == null ? null : i.byPanel.get(panel);
        if (mine == null) return Map.of();
        Map<BlockPos, Feed> out = new HashMap<>();
        for (BlockPos p : mine) {
            Feed f = i.devices.get(p);
            if (f != null && f.panel().equals(panel) && f.role() == Role.SWITCH) out.put(p, f);
        }
        return out;
    }

    /** Drops everything the panel registered (it is removed, unloaded, or no longer WIRED). */
    public static void unregister(Level level, BlockPos panel) {
        Index i = INDEX.get(level);
        if (i == null) return;
        List<BlockPos> mine = i.byPanel.remove(panel);
        if (mine == null) return;
        for (BlockPos p : mine) {
            Feed f = i.devices.get(p);
            if (f != null && f.panel().equals(panel)) i.devices.remove(p);
            if (panel.equals(i.entries.get(p))) i.entries.remove(p);
        }
    }

    /** Solves the panel's wiring and registers it (replacing what it registered before). */
    public static Result solve(ServerLevel level, BlockPos panel, BlockState panelState) {
        unregister(level, panel);
        Result r = new Result();
        BlockPos mount = BuildingWiring.mount(panelState, panel);
        if (mount == null) return r;
        Direction toward = Direction.fromDelta(panel.getX() - mount.getX(), panel.getY() - mount.getY(), panel.getZ() - mount.getZ());
        if (toward == null) return r;
        Index index = index(level);
        List<BlockPos> mine = new ArrayList<>();
        List<BlockPos> bad = new ArrayList<>();
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) {
            if (!WiringNode.tapped(BuildingWiring.node(level, mount, c), toward)) continue;
            r.wired[c] = true;
            List<BlockPos> loads = new ArrayList<>();
            List<Integer> groups = new ArrayList<>();
            walk(level, mount, toward, c, r, loads, groups, bad);
            r.devices[c] = loads.size();
            if (r.conflict[c]) continue;
            for (int k = 0; k < loads.size(); k++) {
                BlockPos d = loads.get(k);
                if (c == WiringNode.FEEDER) {
                    if (index.entries.putIfAbsent(d, panel.immutable()) == null) mine.add(d);
                    continue;
                }
                Role role = role(level.getBlockState(d));
                int g = effectiveGroup(role, groups.get(k));
                if (index.devices.putIfAbsent(d, new Feed(panel.immutable(), c, g, role)) != null) continue;
                mine.add(d);
                switch (role) {
                    case LIGHT -> r.lights[c][g]++;
                    case SWITCH -> r.switches[c][g]++;
                    default -> r.others[c]++;
                }
            }
        }
        if (!mine.isEmpty()) index.byPanel.put(panel.immutable(), mine);
        for (BlockPos p : bad) BuildingWiring.clear(level, p);
        return r;
    }

    /** A building light follows its group, a wall switch sets it; outlets and the emergency light (it only charges) neither. */
    public static Role role(BlockState s) {
        if (s.getBlock() instanceof WallSwitchBlock) return Role.SWITCH;
        if (s.getBlock() instanceof BuildingLightBlock) return Role.LIGHT;
        return Role.OTHER;
    }

    /** The group a tap's device follows: a light 1-8 or the photocell, a switch 1-8, anything else (and any other number) 0. */
    public static int effectiveGroup(Role role, int group) {
        boolean light = group >= 1 && group <= WiringNode.GROUPS;
        return switch (role) {
            case LIGHT -> light || group == WiringNode.PHOTOCELL ? group : WiringNode.ALWAYS;
            case SWITCH -> light ? group : WiringNode.ALWAYS;
            default -> WiringNode.ALWAYS;
        };
    }

    private static void walk(ServerLevel level, BlockPos mount, Direction toward, int c, Result r, List<BlockPos> loads, List<Integer> groups, List<BlockPos> bad) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(mount.asLong());
        queue.enqueue(mount.asLong());
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && seen.size() <= MAX_NODES) {
            BlockPos cell = BlockPos.of(queue.dequeueLong());
            long node = BuildingWiring.node(level, cell, c);
            if (node == 0) continue;
            if (!BuildingWiring.wireable(level, cell)) { bad.add(cell); continue; }
            for (Direction d : Direction.values()) {
                if (WiringNode.tapped(node, d) && !(cell.equals(mount) && d == toward)) tap(level, cell, d, c, WiringNode.group(node, d), r, loads, groups);
                if (WiringNode.linked(node, d)) visit(level, cell.relative(d), seen, queue, r);
            }
            for (Direction h : Direction.Plane.HORIZONTAL) for (boolean up : new boolean[]{true, false})
                if (WiringNode.slopeLinked(node, h, up)) visit(level, next.set(BuildingWiring.slopeCell(cell, h, up)), seen, queue, r);
        }
    }

    private static void visit(ServerLevel level, BlockPos cell, LongOpenHashSet seen, LongArrayFIFOQueue queue, Result r) {
        if (BuildingWiring.chunk(level, cell) == null) { r.partial = true; return; }
        if (seen.add(cell.asLong())) queue.enqueue(cell.asLong());
    }

    /** A tap from cell toward d: the device there, if it is still mounted on this cell. */
    private static void tap(ServerLevel level, BlockPos cell, Direction d, int c, int group, Result r, List<BlockPos> loads, List<Integer> groups) {
        BlockPos dev = cell.relative(d);
        if (BuildingWiring.chunk(level, dev) == null) { r.partial = true; return; }
        BlockState s = level.getBlockState(dev);
        BuildingWiring.Kind kind = BuildingWiring.kind(s);
        if (kind == null || !cell.equals(BuildingWiring.mount(s, dev))) return;   // no device there now: the tap waits in the wall
        if (kind == BuildingWiring.Kind.PANEL) { r.conflict[c] = true; return; }   // a second panel on this circuit
        if (!BuildingWiring.accepts(kind, c)) return;
        loads.add(dev.immutable());
        groups.add(group);
    }
}
