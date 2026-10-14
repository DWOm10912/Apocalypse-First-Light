package com.antaurora.apofirstlight.wiring.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.wiring.WiringChunk;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringPackets;
import com.antaurora.apofirstlight.wiring.WiringSync;
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

/**
 * Building Wiring V1, the client's copy of the wires (docs/gameplay/building_wiring_v1.md): only while the player shows
 * them (wrench or wire in either hand). Picking one up clears the copy and says Hello, the server then sends the nearby
 * chunks; putting it away (or another level) drops everything, so in ordinary play the client holds no wiring at all.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class WiringView {
    /** One chunk's nodes as the server sent them (keys: wiring.WiringChunk). */
    public record Chunk(ChunkPos pos, int[] keys, long[] nodes) {}

    static final Long2ObjectOpenHashMap<Chunk> CHUNKS = new Long2ObjectOpenHashMap<>();
    private static int circuit = 1;
    private static boolean showing;
    private static Object level;
    /** Nodes per circuit in CHUNKS (index = circuit), counted again after any change. */
    private static int[] counts;

    private WiringView() {}

    public static void accept(WiringPackets.ChunkWires p) {
        if (!showing) return;
        long key = ChunkPos.asLong(p.cx(), p.cz());
        if (p.keys().length == 0 || p.keys().length != p.nodes().length) CHUNKS.remove(key);
        else CHUNKS.put(key, new Chunk(new ChunkPos(p.cx(), p.cz()), p.keys(), p.nodes()));
        counts = null;
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

    public static void accept(WiringPackets.Selection p) {
        circuit = p.circuit();
    }

    public static boolean showing() {
        return showing;
    }

    public static int circuit() {
        return circuit;
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
            counts = null;
            showing = false;
            level = null;
            return;
        }
        if (mc.level != level) {
            CHUNKS.clear();
            counts = null;
            showing = false;
            level = mc.level;
        }
        boolean now = holdsWiring(mc.player);
        if (now && !showing) {
            CHUNKS.clear();
            counts = null;
            AflNetwork.wiringToServer(new WiringPackets.Hello());
        } else if (!now && showing) {
            CHUNKS.clear();
            counts = null;
        }
        showing = now;
        if (!showing || CHUNKS.isEmpty()) return;
        ChunkPos at = mc.player.chunkPosition();
        for (var it = CHUNKS.keySet().iterator(); it.hasNext(); ) {
            long k = it.nextLong();
            if (Math.max(Math.abs(ChunkPos.getX(k) - at.x), Math.abs(ChunkPos.getZ(k) - at.z)) > WiringSync.FORGET) {
                it.remove();
                counts = null;
            }
        }
    }
}
