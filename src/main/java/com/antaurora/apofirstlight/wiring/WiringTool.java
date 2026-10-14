package com.antaurora.apofirstlight.wiring;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.BuildingLightBlock;
import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The wiring wrench, server side (Building Wiring V1 phase 1, docs/gameplay/building_wiring_v1.md):
 * <ul>
 *   <li>lay: a use on a block puts the selected circuit's wire into it, or into a device's mount cell when the block is a
 *   device (and taps the device). Uses that follow each other within {@link #STROKE_GAP} ticks are one stroke: a cell that
 *   is not next to the last one is reached by a short route ({@link WiringRouter}, at most {@link #GAP_CELLS} cells), so
 *   a quick sweep with the use key held leaves no gaps. Every new cell joins the same circuit's wire around it (also along
 *   a roof slope or stairs) and taps the devices mounted on it; a new end that meets another end of the circuit only
 *   across an edge is joined through the corner cell (BuildingWiring#cornerJoin, one more wire). Survival: one Insulated
 *   Copper Wire per new cell from the inventory, none laid without enough.</li>
 *   <li>cut: an attack on a block removes the selected circuit's node there (survival: the wire back into the inventory, or
 *   dropped at the feet when it is full); on a device it removes the device's tap (no wire, no refund).</li>
 *   <li>select: sneak + scroll steps through circuits 1-4 and the feeder, the pick key takes another circuit from the aimed
 *   cell ({@link #choose}); the choice is kept on the player (not in the item, which stays stackable with other wrenches)
 *   and shown on the action bar. The colour is the circuit's (WiringNode#colourOf).</li>
 *   <li>route (phase 5, docs/gameplay/building_wiring_v1_phase5_draft.md 2): sneak + use sets a start (a wire cell, a
 *   device's mount cell), then use previews the route to the aimed cell or device (sneak + use adds a waypoint, at most
 *   {@link #WAYPOINTS}), use on the same end again confirms: routed again, laid only when it is still the same, one wire per
 *   new cell. Attack or putting the wrench away cancels. Each leg is a {@link WiringRouter} route of at most
 *   {@link #ROUTE_CELLS} cells; previews at most {@link #PREVIEWS_PER_SECOND} a second.</li>
 *   <li>groups (phase 5, draft 3): the client asks for a light's or switch's group picker ({@link #openGroups}): the tap's
 *   circuit and group and its panel's solve; {@link #setGroup} sets it and the panel solves again at once.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WiringTool {
    public static final int STROKE_GAP = 8, GAP_CELLS = 24, GAP_MARGIN = 2, GAP_VISITS = 4000;
    public static final int ROUTE_CELLS = 96, ROUTE_MARGIN = 8, ROUTE_VISITS = 20_000, WAYPOINTS = 4, PREVIEWS_PER_SECOND = 4;
    /** Route actions from the client: sneak + use (start, then waypoints), use (preview, confirm), attack (cancel). */
    public static final int ROUTE_MARK = 0, ROUTE_USE = 1, ROUTE_CANCEL = 2;
    /** Route states to the client. */
    public static final int ROUTE_NONE = 0, ROUTE_STARTED = 1, ROUTE_PREVIEW = 2;
    private static final String KEY = "afl_wiring_circuit";

    private static final class Session {
        int circuit = 1;
        BlockPos last;
        int lastCircuit;
        long lastTick;
        ResourceKey<Level> lastLevel;
        // a route (phase 5): its start, waypoints, the previewed end and cells; null start = none
        BlockPos start;
        ResourceKey<Level> routeLevel;
        final List<BlockPos> waypoints = new ArrayList<>();
        BlockPos end, endDevice;
        List<BlockPos> preview;
        boolean repreview;
        final long[] previews = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
        int previewAt;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private WiringTool() {}

    private static Session session(ServerPlayer p) {
        return SESSIONS.computeIfAbsent(p.getUUID(), id -> {
            Session s = new Session();
            int saved = p.getPersistentData().getInt(KEY);
            if (saved >= 1 && saved <= WiringNode.CIRCUITS) s.circuit = saved;
            return s;
        });
    }

    public static int circuit(ServerPlayer p) {
        return session(p).circuit;
    }

    /** The player holds the wrench or the wire in either hand: wiring mode (the client shows the wires). */
    public static boolean holdsWiring(Player p) {
        return holds(p.getMainHandItem()) || holds(p.getOffhandItem());
    }

    private static boolean holds(ItemStack s) {
        return s.is(AflItems.WIRING_WRENCH.get()) || s.is(AflItems.INSULATED_COPPER_WIRE.get());
    }

    public static boolean holdsWrench(Player p) {
        return p.getMainHandItem().is(AflItems.WIRING_WRENCH.get());
    }

    // ------------------------------------------------------------------------------------------------------ select

    /** Steps the selection by delta (wrapping through 1-4 and the feeder). */
    public static void select(ServerPlayer p, int delta) {
        if (!holdsWrench(p) || delta == 0) return;
        choose(p, Math.floorMod(circuit(p) - 1 + Integer.signum(delta), WiringNode.CIRCUITS) + 1);
    }

    /** Selects this circuit (the pick key on a wire of it). */
    public static void choose(ServerPlayer p, int circuit) {
        if (!holdsWrench(p) || circuit < 1 || circuit > WiringNode.CIRCUITS) return;
        Session s = session(p);
        s.circuit = circuit;
        s.last = null;
        p.getPersistentData().putInt(KEY, s.circuit);
        WiringSync.selection(p);
        p.displayClientMessage(circuitName(s.circuit), true);
        if (s.preview != null) s.repreview = true;   // the preview follows the circuit (next tick the rate allows)
    }

    /** "第 3 路 · 绿" / "进线 · 黑". */
    public static Component circuitName(int circuit) {
        Component colour = Component.translatable("message.apocalypse_firstlight.wiring.colour." + WiringNode.colourName(WiringNode.colourOf(circuit)));
        return circuit == WiringNode.FEEDER ? Component.translatable("message.apocalypse_firstlight.wiring.feeder", colour)
                : Component.translatable("message.apocalypse_firstlight.wiring.circuit", circuit, colour);
    }

    // --------------------------------------------------------------------------------------------------------- lay

    public static InteractionResult use(ServerPlayer p, BlockPos hit) {
        ServerLevel level = p.serverLevel();
        if (!holdsWrench(p) || !level.isLoaded(hit) || !p.mayBuild() || !level.mayInteract(p, hit)) return InteractionResult.FAIL;
        Session s = session(p);
        if (s.start != null) return InteractionResult.FAIL;   // a route: the client sends its clicks as route actions
        int circuit = s.circuit;
        BlockState state = level.getBlockState(hit);
        BlockPos cell = hit.immutable();
        if (BuildingWiring.kind(state) != null) {
            BlockPos mount = BuildingWiring.mount(state, hit);
            if (mount == null) return InteractionResult.FAIL;
            cell = mount.immutable();
        }
        if (!level.isLoaded(cell) || !BuildingWiring.wireable(level, cell)) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wiring.no_wiring"), true);
            s.last = null;
            return InteractionResult.FAIL;
        }
        long now = level.getGameTime();
        boolean stroke = s.last != null && s.lastCircuit == circuit && level.dimension() == s.lastLevel && now >= s.lastTick && now - s.lastTick <= STROKE_GAP;
        List<BlockPos> path = List.of(cell);
        if (stroke && s.last.distManhattan(cell) > 1) {
            List<BlockPos> r = WiringRouter.route(level, s.last, cell, circuit, GAP_MARGIN, GAP_VISITS, GAP_CELLS + 1);
            if (r != null) path = r.subList(1, r.size());
        }
        if (!lay(p, level, path, circuit)) {
            s.last = null;
            return InteractionResult.FAIL;
        }
        s.last = cell;
        s.lastCircuit = circuit;
        s.lastTick = now;
        s.lastLevel = level.dimension();
        return InteractionResult.SUCCESS;
    }

    /**
     * Lays the cells in order (a cell may come twice): each new one costs a wire (survival; nothing laid without enough, false
     * with the message), joins the circuit around it and taps the devices mounted on it; then any end that meets another end
     * only across an edge (a wall wire and a floor wire) turns the corner with one more metre.
     */
    private static boolean lay(ServerPlayer p, ServerLevel level, List<BlockPos> path, int circuit) {
        int need = fresh(level, path, circuit);
        if (need > 0 && !p.isCreative() && wires(p) < need) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wiring.no_wire"), true);
            return false;
        }
        int made = 0;
        for (BlockPos c : path) {
            if (BuildingWiring.add(level, c, circuit)) made++;
            BuildingWiring.linkAround(level, c, circuit);
            BuildingWiring.autoTap(level, c, circuit);
        }
        if (made > 0 && !p.isCreative()) take(p, made);
        for (BlockPos c : path) {
            if (!p.isCreative() && wires(p) < 1) break;
            if (BuildingWiring.cornerJoin(level, c, circuit) != null && !p.isCreative()) take(p, 1);
        }
        return true;
    }

    /** The cells of the path (each once) without the circuit's wire yet: the wire it costs. */
    private static int fresh(ServerLevel level, List<BlockPos> path, int circuit) {
        int n = 0;
        for (BlockPos c : new LinkedHashSet<>(path)) if (BuildingWiring.node(level, c, circuit) == 0) n++;
        return n;
    }

    // ------------------------------------------------------------------------------------------------------- route

    /** A route action from the client (ROUTE_MARK, ROUTE_USE, ROUTE_CANCEL) on the aimed block. */
    public static void route(ServerPlayer p, int action, BlockPos hit) {
        ServerLevel level = p.serverLevel();
        Session s = session(p);
        if (action == ROUTE_CANCEL) {
            clearRoute(p, s);
            return;
        }
        if (!holdsWrench(p) || !level.isLoaded(hit) || !p.mayBuild() || !level.mayInteract(p, hit) || !p.canReach(hit, 1.0)) return;
        if (s.start != null && s.routeLevel != level.dimension()) clearRoute(p, s);
        BlockState state = level.getBlockState(hit);
        BlockPos device = BuildingWiring.kind(state) != null ? hit.immutable() : null;
        BlockPos cell = device != null ? BuildingWiring.mount(state, hit) : hit.immutable();
        if (cell == null || !level.isLoaded(cell) || !BuildingWiring.wireable(level, cell)) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wiring.no_wiring"), true);
            return;
        }
        cell = cell.immutable();
        if (action == ROUTE_MARK) {
            if (s.start == null) {
                s.start = cell;
                s.routeLevel = level.dimension();
                s.waypoints.clear();
                s.end = s.endDevice = null;
                s.preview = null;
                s.last = null;
                sendRoute(p, s);
                return;
            }
            if (cell.equals(s.start) || s.waypoints.contains(cell)) return;
            if (s.waypoints.size() >= WAYPOINTS) {
                p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wiring.waypoints_full", WAYPOINTS), true);
                return;
            }
            s.waypoints.add(cell);
            if (s.end != null) {
                s.repreview = true;   // now, or by the tick once the rate allows
                preview(p, s, s.end, s.endDevice);
            } else sendRoute(p, s);
            return;
        }
        if (action != ROUTE_USE || s.start == null) return;
        if (s.preview != null && cell.equals(s.end)) confirm(p, s, device);
        else preview(p, s, cell, device);
    }

    /** The route's start cell, null without a route (GameTests). */
    @Nullable
    public static BlockPos routeStart(ServerPlayer p) {
        return session(p).start;
    }

    public static List<BlockPos> routeWaypoints(ServerPlayer p) {
        return List.copyOf(session(p).waypoints);
    }

    /** The previewed cells, null without a preview (GameTests). */
    @Nullable
    public static List<BlockPos> routePreview(ServerPlayer p) {
        Session s = session(p);
        return s.preview == null ? null : List.copyOf(s.preview);
    }

    /** The route through the waypoints to the end, the selected circuit: start, ..., end (a cell may come twice), or null. */
    private static List<BlockPos> plan(ServerLevel level, Session s, BlockPos end) {
        List<BlockPos> points = new ArrayList<>();
        points.add(s.start);
        points.addAll(s.waypoints);
        points.add(end);
        List<BlockPos> out = new ArrayList<>();
        out.add(s.start);
        for (int i = 1; i < points.size(); i++) {
            List<BlockPos> leg = WiringRouter.route(level, points.get(i - 1), points.get(i), s.circuit, ROUTE_MARGIN, ROUTE_VISITS, ROUTE_CELLS);
            if (leg == null) return null;
            out.addAll(leg.subList(1, leg.size()));
        }
        return out;
    }

    /** At most PREVIEWS_PER_SECOND route searches in any 20 ticks. */
    private static boolean allow(Session s, long now) {
        if (now - s.previews[s.previewAt] < 20) return false;
        s.previews[s.previewAt] = now;
        s.previewAt = (s.previewAt + 1) % s.previews.length;
        return true;
    }

    private static void preview(ServerPlayer p, Session s, BlockPos end, BlockPos device) {
        ServerLevel level = p.serverLevel();
        if (!allow(s, level.getGameTime())) return;
        s.repreview = false;
        List<BlockPos> path = plan(level, s, end);
        if (path == null) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wiring.no_route"), true);
            s.end = s.endDevice = null;
            s.preview = null;
        } else {
            s.end = end;
            s.endDevice = device;
            s.preview = path;
        }
        sendRoute(p, s);
    }

    /** Use on the previewed end: routed again; the same route is laid (then the group picker for a light or switch), another one only shown. */
    private static void confirm(ServerPlayer p, Session s, BlockPos device) {
        ServerLevel level = p.serverLevel();
        if (!allow(s, level.getGameTime())) return;
        List<BlockPos> path = plan(level, s, s.end);
        if (path == null) {
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wiring.no_route"), true);
            s.end = s.endDevice = null;
            s.preview = null;
            sendRoute(p, s);
            return;
        }
        if (!path.equals(s.preview)) {   // a block changed since the preview: show the new route, lay on the next click
            s.preview = path;
            p.displayClientMessage(Component.translatable("message.apocalypse_firstlight.wiring.route_changed"), true);
            sendRoute(p, s);
            return;
        }
        if (!lay(p, level, path, s.circuit)) return;   // not enough wire: the preview stays
        BlockPos picker = device != null ? device : s.endDevice;
        clearRoute(p, s);
        if (picker != null) openGroups(p, picker);
    }

    private static void clearRoute(ServerPlayer p, Session s) {
        boolean had = s.start != null;
        s.start = null;
        s.routeLevel = null;
        s.waypoints.clear();
        s.end = s.endDevice = null;
        s.preview = null;
        s.repreview = false;
        s.last = null;
        if (had) sendRoute(p, s);
    }

    private static void sendRoute(ServerPlayer p, Session s) {
        if (s.start == null) {
            AflNetwork.wiring(p, new WiringPackets.Route(ROUTE_NONE, 0, new long[0], 0, new long[0], 0, 0));
            return;
        }
        long[] waypoints = new long[s.waypoints.size()];
        for (int i = 0; i < waypoints.length; i++) waypoints[i] = s.waypoints.get(i).asLong();
        if (s.preview == null) {
            AflNetwork.wiring(p, new WiringPackets.Route(ROUTE_STARTED, s.start.asLong(), waypoints, 0, new long[0], 0, 0));
            return;
        }
        Set<BlockPos> cells = new LinkedHashSet<>(s.preview);
        long[] path = new long[s.preview.size()];
        for (int i = 0; i < path.length; i++) path[i] = s.preview.get(i).asLong();
        AflNetwork.wiring(p, new WiringPackets.Route(ROUTE_PREVIEW, s.start.asLong(), waypoints, s.end.asLong(), path, cells.size(),
                fresh(p.serverLevel(), s.preview, s.circuit)));
    }

    /** The route ends when the wrench leaves the main hand or the player another level; a circuit change previews again. */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.start == null) return;
        if (!holdsWrench(p) || s.routeLevel != p.level().dimension()) clearRoute(p, s);
        else if (s.repreview && s.end != null) preview(p, s, s.end, s.endDevice);
    }

    // ------------------------------------------------------------------------------------------------------ groups

    /** The light or switch at device and the tap that feeds it: circuit 1-4, the direction from its mount cell; null if none. */
    private static int[] tapOf(ServerLevel level, BlockPos device, BlockState state) {
        if (BuildingWiring.kind(state) != BuildingWiring.Kind.LOAD) return null;
        BlockPos m = BuildingWiring.mount(state, device);
        Direction toward = m == null ? null : Direction.fromDelta(device.getX() - m.getX(), device.getY() - m.getY(), device.getZ() - m.getZ());
        if (toward == null) return null;
        for (int c = 1; c <= WiringNode.BREAKERS; c++)
            if (WiringNode.tapped(BuildingWiring.node(level, m, c), toward)) return new int[]{c, toward.get3DDataValue()};
        return null;
    }

    /** The WIRED panel the circuit's wire at the cell reaches, solved again now (its counts include what was just laid), or null. */
    @Nullable
    private static DistributionPanelBlockEntity panelSolved(ServerLevel level, BlockPos device, BlockPos cell, int circuit) {
        WiringSupply.Feed f = WiringSupply.feed(level, device);
        BlockPos at = f != null && f.circuit() == circuit ? f.panel() : WiringSupply.panelOf(level, cell, circuit);
        if (at == null || !(level.getBlockEntity(at) instanceof DistributionPanelBlockEntity panel) || !panel.wired()) return null;
        panel.solveNow();
        return panel;
    }

    /**
     * The group picker for the light or switch at device (a lone click on it, or the end of a confirmed route): its tap's
     * circuit and group, and per group the lights and switches of the panel's solve (none without a panel). Outlets,
     * emergency lights and devices without a tap get none.
     */
    public static void openGroups(ServerPlayer p, BlockPos device) {
        ServerLevel level = p.serverLevel();
        if (!holdsWrench(p) || !level.isLoaded(device) || !p.canReach(device, 2.0)) return;
        BlockState state = level.getBlockState(device);
        WiringSupply.Role role = WiringSupply.role(state);
        int[] tap = tapOf(level, device, state);
        if (role == WiringSupply.Role.OTHER || tap == null) return;
        Direction toward = Direction.from3DDataValue(tap[1]);
        BlockPos m = device.relative(toward.getOpposite());
        int circuit = tap[0], group = WiringNode.group(BuildingWiring.node(level, m, circuit), toward);
        DistributionPanelBlockEntity panel = panelSolved(level, device, m, circuit);
        WiringSupply.Result r = panel == null ? null : panel.wiring();
        int[] lights = new int[16], switches = new int[16];
        if (r != null) {
            System.arraycopy(r.lights[circuit], 0, lights, 0, 16);
            System.arraycopy(r.switches[circuit], 0, switches, 0, 16);
        }
        AflNetwork.wiring(p, new WiringPackets.Groups(device.immutable(), circuit, role == WiringSupply.Role.SWITCH, group, r != null, lights, switches));
    }

    /**
     * Sets the group of the tap feeding the light or switch at device (a light: always on, 1-8, photocell; a switch 1-8). Its
     * panel solves again at once: the switches line up with their groups, a light looks again.
     */
    public static boolean setGroup(ServerPlayer p, BlockPos device, int group) {
        ServerLevel level = p.serverLevel();
        if (!holdsWrench(p) || !level.isLoaded(device) || !p.mayBuild() || !level.mayInteract(p, device) || !p.canReach(device, 2.0)) return false;
        BlockState state = level.getBlockState(device);
        WiringSupply.Role role = WiringSupply.role(state);
        boolean numbered = group >= 1 && group <= WiringNode.GROUPS;
        boolean fits = role == WiringSupply.Role.LIGHT ? numbered || group == WiringNode.ALWAYS || group == WiringNode.PHOTOCELL
                : role == WiringSupply.Role.SWITCH && numbered;
        int[] tap = tapOf(level, device, state);
        if (!fits || tap == null) return false;
        Direction toward = Direction.from3DDataValue(tap[1]);
        BlockPos m = device.relative(toward.getOpposite());
        BuildingWiring.setGroup(level, m, tap[0], toward, group);
        panelSolved(level, device, m, tap[0]);
        BlockState now = level.getBlockState(device);
        if (now.getBlock() instanceof BuildingLightBlock light) light.recheck(level, device, now);
        return true;
    }

    // --------------------------------------------------------------------------------------------------------- cut

    /** An attack on a block with the wrench (the client never mines with it). */
    public static void cut(ServerPlayer p, BlockPos hit) {
        ServerLevel level = p.serverLevel();
        Session s = session(p);
        if (s.start != null) {   // with a route the attack only cancels it
            clearRoute(p, s);
            return;
        }
        if (!holdsWrench(p) || !level.isLoaded(hit) || !p.mayBuild() || !level.mayInteract(p, hit) || !p.canReach(hit, 1.0)) return;
        BlockState state = level.getBlockState(hit);
        if (BuildingWiring.kind(state) != null) {
            BuildingWiring.untap(level, hit, state);
            return;
        }
        int n = BuildingWiring.cut(level, hit, session(p).circuit);
        if (n > 0 && !p.isCreative()) give(p, n);
    }

    // ------------------------------------------------------------------------------------------------------- wire

    /** Insulated Copper Wire in the player's inventory (both hands included). */
    public static int wires(Player p) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(AflItems.INSULATED_COPPER_WIRE.get())) n += s.getCount();
        }
        return n;
    }

    /** Takes n wires (the off hand first, then the inventory in order). */
    static void take(Player p, int n) {
        Inventory inv = p.getInventory();
        ItemStack off = p.getOffhandItem();
        if (off.is(AflItems.INSULATED_COPPER_WIRE.get())) {
            int k = Math.min(n, off.getCount());
            off.shrink(k);
            n -= k;
        }
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(AflItems.INSULATED_COPPER_WIRE.get())) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
        inv.setChanged();
    }

    /** Gives n wires back: into the inventory, the rest dropped at the player's feet. */
    static void give(Player p, int n) {
        ItemStack s = new ItemStack(AflItems.INSULATED_COPPER_WIRE.get(), n);
        p.getInventory().add(s);   // what does not fit stays in s
        if (!s.isEmpty()) p.drop(s, false);
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }
}
