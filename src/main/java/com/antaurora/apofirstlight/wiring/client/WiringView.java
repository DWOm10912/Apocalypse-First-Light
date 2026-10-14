package com.antaurora.apofirstlight.wiring.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.wiring.WiringChunk;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringPackets;
import com.antaurora.apofirstlight.wiring.WiringSync;
import com.antaurora.apofirstlight.wiring.WiringTool;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Building Wiring V1, the client's copy of the wires (docs/gameplay/building_wiring_v1.md): only while the player shows
 * them (wrench or wire in either hand). Picking one up clears the copy and says Hello, the server then sends the nearby
 * chunks; putting it away (or another level) drops everything, so in ordinary play the client holds no wiring at all.
 * Phase 5 adds the wrench's route as the server last sent it, "show all" (sneak + pick key, off when the wrench leaves the
 * main hand), and a version that changes with any of it (the x-ray rebuilds its geometry then).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WiringView {
    /** One chunk's nodes as the server sent them (keys: wiring.WiringChunk). */
    public record Chunk(ChunkPos pos, int[] keys, long[] nodes) {}

    static final Long2ObjectOpenHashMap<Chunk> CHUNKS = new Long2ObjectOpenHashMap<>();
    private static int circuit = 1;
    private static boolean showing, showAll;
    private static Object level;
    /** Nodes per circuit in CHUNKS (index = circuit), counted again after any change. */
    private static int[] counts;
    private static int version;
    // the route (WiringTool.ROUTE_*)
    private static int routeStatus, routeTotal, routeFresh;
    @Nullable private static BlockPos routeStart, routeEnd;
    private static List<BlockPos> routeWaypoints = List.of(), routePath = List.of();

    private WiringView() {}

    public static void accept(WiringPackets.ChunkWires p) {
        if (!showing) return;
        long key = ChunkPos.asLong(p.cx(), p.cz());
        if (p.keys().length == 0 || p.keys().length != p.nodes().length) CHUNKS.remove(key);
        else CHUNKS.put(key, new Chunk(new ChunkPos(p.cx(), p.cz()), p.keys(), p.nodes()));
        changed();
    }

    public static void accept(WiringPackets.Route p) {
        routeStatus = p.status();
        routeStart = p.status() == WiringTool.ROUTE_NONE ? null : BlockPos.of(p.start());
        routeEnd = p.status() == WiringTool.ROUTE_PREVIEW ? BlockPos.of(p.end()) : null;
        routeWaypoints = positions(p.waypoints());
        routePath = positions(p.path());
        routeTotal = p.total();
        routeFresh = p.fresh();
        version++;
    }

    /** The server answered a click on a light or switch: its group picker (only over the world, the wrench still in hand). */
    public static void accept(WiringPackets.Groups p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || !holdsWrench(mc.player)) return;
        mc.setScreen(new WiringGroupScreen(p));
    }

    private static List<BlockPos> positions(long[] longs) {
        List<BlockPos> out = new ArrayList<>(longs.length);
        for (long l : longs) out.add(BlockPos.of(l));
        return out;
    }

    private static void changed() {
        counts = null;
        version++;
    }

    /**
     * Metres of the circuit's wire this client knows (one per node; the chunks within WiringSync.RADIUS of the player, so a
     * run reaching farther counts only its near part).
     */
    public static int length(int c) {
        if (counts == null) {
            int[] n = new int[WiringNode.CIRCUITS + 1];
            for (Chunk ch : CHUNKS.values()) for (long node : ch.nodes()) n[WiringNode.circuit(node)]++;
            counts = n;
        }
        return c >= 1 && c < counts.length ? counts[c] : 0;
    }

    /** The circuits with wire in the cell, ascending (empty when this client has none there). */
    public static int[] circuitsAt(BlockPos cell) {
        Chunk ch = CHUNKS.get(ChunkPos.asLong(cell.getX() >> 4, cell.getZ() >> 4));
        if (ch == null) return new int[0];
        int base = cell.getX() & 15 | (cell.getZ() & 15) << 4 | (cell.getY() + 2048 & 4095) << 8;
        int[] out = new int[WiringNode.CIRCUITS];
        int n = 0;
        for (int k : ch.keys()) if ((k & 0xFFFFF) == base) out[n++] = WiringChunk.circuitOf(k);
        java.util.Arrays.sort(out, 0, n);
        return java.util.Arrays.copyOf(out, n);
    }

    /** The circuit's node in the cell as this client knows it, 0 if none. */
    public static long node(BlockPos cell, int c) {
        Chunk ch = CHUNKS.get(ChunkPos.asLong(cell.getX() >> 4, cell.getZ() >> 4));
        if (ch == null) return 0;
        int key = cell.getX() & 15 | (cell.getZ() & 15) << 4 | (cell.getY() + 2048 & 4095) << 8 | c << 20;
        int[] keys = ch.keys();
        for (int i = 0; i < keys.length; i++) if (keys[i] == key) return ch.nodes()[i];
        return 0;
    }

    public static void accept(WiringPackets.Selection p) {
        if (circuit != p.circuit()) version++;
        circuit = p.circuit();
    }

    public static boolean showing() {
        return showing;
    }

    public static int circuit() {
        return circuit;
    }

    /** Other circuits drawn too (thinner, dimmer, only where in sight), with all device labels. */
    public static boolean showAll() {
        return showAll;
    }

    static void toggleShowAll() {
        showAll = !showAll;
        version++;
    }

    /** Changes with the wires, the selection, "show all" and the route: the x-ray's geometry is rebuilt then. */
    static int version() {
        return version;
    }

    /** WiringTool.ROUTE_NONE / ROUTE_STARTED / ROUTE_PREVIEW. */
    static int routeStatus() {
        return routeStatus;
    }

    @Nullable static BlockPos routeStart() {
        return routeStart;
    }

    @Nullable static BlockPos routeEnd() {
        return routeEnd;
    }

    static List<BlockPos> routeWaypoints() {
        return routeWaypoints;
    }

    static List<BlockPos> routePath() {
        return routePath;
    }

    static int routeTotal() {
        return routeTotal;
    }

    static int routeFresh() {
        return routeFresh;
    }

    private static void clearRoute() {
        if (routeStatus == WiringTool.ROUTE_NONE) return;
        routeStatus = WiringTool.ROUTE_NONE;
        routeStart = routeEnd = null;
        routeWaypoints = routePath = List.of();
        version++;
    }

    static boolean holdsWiring(Player p) {
        return holds(p.getMainHandItem()) || holds(p.getOffhandItem());
    }

    private static boolean holds(ItemStack s) {
        return s.is(AflItems.WIRING_WRENCH.get()) || s.is(AflItems.INSULATED_COPPER_WIRE.get());
    }

    static boolean holdsWrench(Player p) {
        return p.getMainHandItem().is(AflItems.WIRING_WRENCH.get());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            CHUNKS.clear();
            changed();
            showing = showAll = false;
            clearRoute();
            level = null;
            return;
        }
        if (mc.level != level) {
            CHUNKS.clear();
            changed();
            showing = false;
            clearRoute();
            level = mc.level;
        }
        boolean now = holdsWiring(mc.player);
        if (now && !showing) {
            CHUNKS.clear();
            changed();
            AflNetwork.wiringToServer(new WiringPackets.Hello());
        } else if (!now && showing) {
            CHUNKS.clear();
            changed();
        }
        showing = now;
        if (!holdsWrench(mc.player)) {   // the server ends the route too
            if (showAll) toggleShowAll();
            clearRoute();
        }
        if (!showing || CHUNKS.isEmpty()) return;
        ChunkPos at = mc.player.chunkPosition();
        for (var it = CHUNKS.keySet().iterator(); it.hasNext(); ) {
            long k = it.nextLong();
            if (Math.max(Math.abs(ChunkPos.getX(k) - at.x), Math.abs(ChunkPos.getZ(k) - at.z)) > WiringSync.FORGET) {
                it.remove();
                changed();
            }
        }
    }
}
