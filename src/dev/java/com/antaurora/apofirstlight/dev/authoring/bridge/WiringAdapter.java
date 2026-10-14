package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.BuildingAuthoringSession;
import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.dev.wiring.WiringCommands;
import com.antaurora.apofirstlight.wiring.BuildingWiring;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringSupply;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/**
 * Building Wiring V1 through the bridge (phase 4, 2026-10-14; docs/gameplay/building_wiring_v1.md 9): the "lay a run by
 * coordinates" tool of design 13.1 item 6, for scripts that prewire a building.
 * <ul>
 *   <li>{@code wiring_lay {cells, circuit, dry_run?}}: lays the given cells in order exactly as {@code /afl dev wiring lay}
 *   (joins the circuit around each, taps the devices mounted there that have no tap yet, group 0); every cell must lie in
 *   the plot and hold wire, else nothing is laid;</li>
 *   <li>{@code wiring_group {device, group}}: the group of the tap feeding a device ({@code /afl dev wiring group});</li>
 *   <li>{@code wiring_clear {min, max, dry_run?}}: every node in a box of the plot;</li>
 *   <li>{@code wiring_read {min?, max?}}: the nodes and the devices (their feed) in the plot or a crop;</li>
 *   <li>{@code wiring_panel {pos, main?}}: a panel's mode, main and last solve; {@code main} closes or opens the main
 *   (no undo step, like a player at the panel).</li>
 * </ul>
 * Lay, group and clear are one undo step each in the shared history: the nodes of every touched cell and the 26 cells
 * around it (links and slope links reach no further), before and after. Wire costs nothing here and nothing is refunded.
 */
final class WiringAdapter {
    private static final int MAX_CELLS = 4096, MAX_BOX = 32768;
    private final BridgeHistory history;

    WiringAdapter(BridgeHistory history) { this.history = history; }

    record NodeChange(BlockPos pos, int circuit, long before, long after) {}

    /** One wiring edit: the exact nodes before and after. */
    static final class WiringEntry implements BridgeHistory.Entry {
        private final List<NodeChange> changes;
        WiringEntry(List<NodeChange> changes) { this.changes = List.copyOf(changes); }
        @Override public long size() { return changes.size(); }
        @Override public int verify(ServerPlayer p, BridgeBounds scope, boolean redo) {
            var level = p.serverLevel();
            for (var c : changes) {
                if (!level.isLoaded(c.pos())) throw new IllegalArgumentException("HISTORY_CHUNK_NOT_LOADED at " + c.pos().toShortString());
                if (BuildingWiring.node(level, c.pos(), c.circuit()) != (redo ? c.before() : c.after()))
                    throw new IllegalArgumentException("HISTORY_CONFLICT: wiring changed since at " + c.pos().toShortString() + " circuit " + c.circuit());
            }
            return changes.size();
        }
        @Override public void apply(ServerPlayer p, BridgeBounds scope, boolean redo) {
            var level = p.serverLevel();
            for (var c : changes)
                if (!BuildingWiring.restore(level, c.pos(), c.circuit(), redo ? c.after() : c.before()))
                    throw new IllegalArgumentException("HISTORY_APPLY_INCOMPLETE at " + c.pos().toShortString() + " circuit " + c.circuit());
        }
    }

    JsonObject call(String tool, JsonObject a, ServerPlayer p) throws Exception {
        var s = AuthoringAdapter.active(p);
        history.bind(s);
        var scope = new BridgeBounds(s.origin, s.max());
        if (!string(a, "target", "AUTHORING_SESSION").equals("AUTHORING_SESSION")) throw new IllegalArgumentException("REFERENCE_READ_ONLY");
        var level = p.serverLevel();
        return switch (tool) {
            case "wiring_lay" -> lay(a, level, s, scope);
            case "wiring_group" -> group(a, level, s, scope);
            case "wiring_clear" -> clear(a, level, s, scope);
            case "wiring_read" -> read(a, level, scope);
            case "wiring_panel" -> panel(a, level, scope);
            default -> throw new IllegalArgumentException("UNKNOWN_TOOL");
        };
    }

    private JsonObject lay(JsonObject a, ServerLevel level, BuildingAuthoringSession s, BridgeBounds scope) {
        int circuit = integer(a, "circuit", 0);
        if (circuit < 1 || circuit > WiringNode.CIRCUITS) throw new IllegalArgumentException("INVALID_CIRCUIT: 1-4, 5 the feeder");
        var arr = a.getAsJsonArray("cells");
        if (arr == null || arr.isEmpty() || arr.size() > MAX_CELLS) throw new IllegalArgumentException("CELLS_REQUIRED: 1-" + MAX_CELLS + " [x,y,z]");
        var cells = new ArrayList<BlockPos>();
        for (var e : arr) {
            var v = e.getAsJsonArray();
            if (v.size() != 3) throw new IllegalArgumentException("Expected integer [x,y,z] in cells");
            cells.add(new BlockPos(v.get(0).getAsInt(), v.get(1).getAsInt(), v.get(2).getAsInt()));
        }
        var bad = new ArrayList<String>();
        for (var c : cells) {
            if (!scope.contains(c)) bad.add(c.toShortString() + " outside the plot");
            else if (!level.isLoaded(c)) bad.add(c.toShortString() + " not loaded");
            else if (!BuildingWiring.wireable(level, c)) bad.add(c.toShortString() + " cannot hold wire (" + id(level.getBlockState(c)) + ")");
        }
        if (!bad.isEmpty()) throw new IllegalArgumentException("WIRING_CELLS_REJECTED: " + String.join("; ", bad.subList(0, Math.min(16, bad.size()))));
        int fresh = 0;
        for (var c : cells) if (BuildingWiring.node(level, c, circuit) == 0) fresh++;
        if (bool(a, "dry_run")) return object("dry_run", true, "cells", cells.size(), "new", fresh);
        var before = snapshot(level, cells);
        int made = WiringCommands.layAll(level, cells, circuit);
        var changes = diff(level, before);
        // the taps this run made: a tap bit toward a device that was not there before
        var taps = new JsonArray();
        for (var c : changes) {
            if (c.circuit() != circuit) continue;
            for (Direction d : Direction.values())
                if (WiringNode.tapped(c.after(), d) && !WiringNode.tapped(c.before(), d)) {
                    var dev = c.pos().relative(d);
                    taps.add(object("device", xyz(dev), "block", id(level.getBlockState(dev))));
                }
        }
        push(changes, s);
        var out = object("cells", cells.size(), "laid", made, "changed_nodes", changes.size());
        out.add("taps", taps);
        return out;
    }

    private JsonObject group(JsonObject a, ServerLevel level, BuildingAuthoringSession s, BridgeBounds scope) {
        var dev = pos(a, "device");
        if (!scope.contains(dev)) throw new IllegalArgumentException("OUTSIDE_AUTHORING_SCOPE");
        int g = integer(a, "group", -1);
        if (g < 0 || g > WiringNode.PHOTOCELL) throw new IllegalArgumentException("INVALID_GROUP: 0 always on, 1-8 light groups, 15 photocell");
        var state = level.getBlockState(dev);
        var m = BuildingWiring.mount(state, dev);
        if (m == null) throw new IllegalArgumentException("WIRING_GROUP_REJECTED: not a wiring device (" + id(state) + ")");
        var before = snapshot(level, List.of(m));
        String err = WiringCommands.setGroup(level, dev, g);
        if (err != null) throw new IllegalArgumentException("WIRING_GROUP_REJECTED: " + err);
        push(diff(level, before), s);
        Direction toward = Direction.fromDelta(dev.getX() - m.getX(), dev.getY() - m.getY(), dev.getZ() - m.getZ());
        int circuit = 0;
        for (int c = 1; c <= WiringNode.BREAKERS && toward != null; c++) if (WiringNode.tapped(BuildingWiring.node(level, m, c), toward)) circuit = c;
        return object("device", xyz(dev), "circuit", circuit, "group", g);
    }

    private JsonObject clear(JsonObject a, ServerLevel level, BuildingAuthoringSession s, BridgeBounds scope) {
        var box = new BridgeBounds(pos(a, "min"), pos(a, "max"));
        box.inside(scope);
        if (box.volume() > MAX_BOX) throw new IllegalArgumentException("MAX_BOX: " + MAX_BOX + " cells");
        var cells = new ArrayList<BlockPos>();
        for (var c : BlockPos.betweenClosed(box.min(), box.max())) if (BuildingWiring.any(level, c)) cells.add(c.immutable());
        if (bool(a, "dry_run")) return object("dry_run", true, "cells_with_wire", cells.size());
        var before = snapshot(level, cells);
        int n = 0;
        for (var c : cells) n += BuildingWiring.clear(level, c);
        var changes = diff(level, before);
        push(changes, s);
        return object("cleared_nodes", n, "changed_nodes", changes.size());
    }

    private JsonObject read(JsonObject a, ServerLevel level, BridgeBounds scope) {
        var box = a.has("min") ? new BridgeBounds(pos(a, "min"), pos(a, "max")) : scope;
        box.inside(scope);
        var nodes = new JsonArray();
        var devices = new JsonArray();
        var panels = new JsonArray();
        for (var c : BlockPos.betweenClosed(box.min(), box.max())) {
            if (!level.isLoaded(c)) continue;
            for (int circuit : BuildingWiring.circuits(level, c)) {
                long node = BuildingWiring.node(level, c, circuit);
                var links = new StringBuilder();
                for (Direction d : Direction.values()) if (WiringNode.linked(node, d)) links.append(d.getName().charAt(0));
                var taps = new JsonArray();
                for (Direction d : Direction.values()) if (WiringNode.tapped(node, d)) taps.add(object("toward", d.getName(), "group", WiringNode.group(node, d)));
                var o = object("pos", xyz(c), "circuit", circuit, "links", links.toString(), "slope_links", Integer.bitCount(WiringNode.slopes(node)));
                o.add("taps", taps);
                nodes.add(o);
            }
            BlockState state = level.getBlockState(c);
            var kind = BuildingWiring.kind(state);
            if (kind == null) continue;
            var d = object("pos", xyz(c), "block", id(state), "kind", kind.name());
            var f = WiringSupply.feed(level, c);
            if (f != null) d.add("feed", object("panel", xyz(f.panel()), "circuit", f.circuit(), "group", f.group(), "role", f.role().name()));
            var entry = kind == BuildingWiring.Kind.ENTRY ? WiringSupply.entryPanel(level, c) : null;
            if (entry != null) d.add("entry_panel", GSON.toJsonTree(xyz(entry)));
            devices.add(d);
            if (level.getBlockEntity(c) instanceof DistributionPanelBlockEntity panel) panels.add(panelJson(panel));
        }
        var out = new JsonObject();
        out.add("nodes", nodes);
        out.add("devices", devices);
        out.add("panels", panels);
        return out;
    }

    private JsonObject panel(JsonObject a, ServerLevel level, BridgeBounds scope) {
        var at = pos(a, "pos");
        if (!scope.contains(at)) throw new IllegalArgumentException("OUTSIDE_AUTHORING_SCOPE");
        if (!(level.getBlockEntity(at) instanceof DistributionPanelBlockEntity panel)) throw new IllegalArgumentException("NOT_A_PANEL: " + id(level.getBlockState(at)));
        if (a.has("main")) {
            boolean on = bool(a, "main");
            if (panel.live() != on) panel.toggleMain(null);
            if (panel.live() != on) panel.toggleMain(null);   // a tripped main resets to off first
        }
        return panelJson(panel);
    }

    private static JsonObject panelJson(DistributionPanelBlockEntity panel) {
        var o = object("pos", xyz(panel.getBlockPos()), "mode", panel.wired() ? "wired" : "legacy_area", "main_live", panel.live());
        var r = panel.wiring();
        if (r == null) { o.addProperty("solved", false); return o; }
        o.addProperty("solved", true);
        var circuits = new JsonArray();
        for (int c = 1; c <= WiringNode.CIRCUITS; c++) {
            if (!r.wired[c]) continue;
            var groups = new JsonArray();
            if (c <= WiringNode.BREAKERS) for (int g = 0; g < 16; g++)
                if (r.used(c, g)) groups.add(object("group", g, "lights", r.lights[c][g], "switches", r.switches[c][g], "on", g < 1 || g > WiringNode.GROUPS || panel.groupOn(c, g)));
            var co = object("circuit", c, "devices", r.devices[c], "conflict", r.conflict[c]);
            if (c <= WiringNode.BREAKERS) co.addProperty("others", r.others[c]);
            co.add("groups", groups);
            circuits.add(co);
        }
        o.add("circuits", circuits);
        o.addProperty("partial", r.partial);
        return o;
    }

    /** The nodes (circuits 1-5) of the cells and the 26 cells around each. */
    private static LinkedHashMap<BlockPos, long[]> snapshot(ServerLevel level, Collection<BlockPos> cells) {
        var out = new LinkedHashMap<BlockPos, long[]>();
        for (var c : cells) for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            var p = c.offset(dx, dy, dz);
            if (out.containsKey(p) || !level.isLoaded(p)) continue;
            long[] n = new long[WiringNode.CIRCUITS + 1];
            for (int k = 1; k <= WiringNode.CIRCUITS; k++) n[k] = BuildingWiring.node(level, p, k);
            out.put(p, n);
        }
        return out;
    }

    private static List<NodeChange> diff(ServerLevel level, Map<BlockPos, long[]> before) {
        var out = new ArrayList<NodeChange>();
        for (var e : before.entrySet()) for (int k = 1; k <= WiringNode.CIRCUITS; k++) {
            long now = BuildingWiring.node(level, e.getKey(), k);
            if (now != e.getValue()[k]) out.add(new NodeChange(e.getKey(), k, e.getValue()[k], now));
        }
        return out;
    }

    private void push(List<NodeChange> changes, BuildingAuthoringSession s) {
        if (changes.isEmpty()) return;
        history.reserve(changes.size());
        history.push(new WiringEntry(changes));
        s.changed();
    }

    private static String id(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }
}
