package com.antaurora.apofirstlight.wiring;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.network.AflNetwork;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Building Wiring V1, the wiring view (docs/gameplay/building_wiring_v1_design.md 7.4): only a player who shows the wires
 * (wrench or wire in hand) gets them. After the client's Hello the server sends the selection and the nodes of the
 * {@link #RADIUS} chunks around the player, then every {@link #EVERY} ticks any of those chunks whose revision changed;
 * chunks farther than {@link #FORGET} are forgotten on both sides (the client drops them too) and sent again on return.
 * The first send of a chunk drops nodes in cells that can no longer hold wire (blocks changed by tools that bypass block
 * updates, such as WorldEdit's fast mode), without a refund.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class WiringSync {
    public static final int RADIUS = 2, FORGET = 3, EVERY = 5;

    private static final class View {
        final ResourceKey<Level> level;
        final Long2IntOpenHashMap sent = new Long2IntOpenHashMap();

        View(ResourceKey<Level> level) {
            this.level = level;
            sent.defaultReturnValue(-1);
        }
    }

    private static final Map<UUID, View> VIEWS = new HashMap<>();

    private WiringSync() {}

    /** The client starts showing wires (picked up the wrench or the wire, or entered another level). */
    static void hello(ServerPlayer p) {
        View v = new View(p.level().dimension());
        VIEWS.put(p.getUUID(), v);
        selection(p);
        push(p, v);
    }

    public static void selection(ServerPlayer p) {
        AflNetwork.wiring(p, new WiringPackets.Selection(WiringTool.circuit(p)));
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        View v = VIEWS.get(p.getUUID());
        if (v == null || (p.tickCount + p.getId()) % EVERY != 0 || !WiringTool.holdsWiring(p)) return;
        if (v.level != p.level().dimension()) return;   // the client says Hello again in the new level
        push(p, v);
    }

    private static void push(ServerPlayer p, View v) {
        ServerLevel level = p.serverLevel();
        ChunkPos at = p.chunkPosition();
        for (var it = v.sent.keySet().iterator(); it.hasNext(); ) {
            long k = it.nextLong();
            if (Math.max(Math.abs(ChunkPos.getX(k) - at.x), Math.abs(ChunkPos.getZ(k) - at.z)) > FORGET) it.remove();
        }
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            int cx = at.x + dx, cz = at.z + dz;
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            long key = ChunkPos.asLong(cx, cz);
            int had = v.sent.get(key);
            WiringChunk w = BuildingWiring.data(chunk, false);
            if (w == null) {
                if (had > 0) AflNetwork.wiring(p, new WiringPackets.ChunkWires(cx, cz, new int[0], new long[0]));
                v.sent.put(key, 0);
                continue;
            }
            if (had == -1) validate(level, chunk.getPos(), w);
            if (had == w.revision()) continue;
            int[] keys = w.keys();
            AflNetwork.wiring(p, new WiringPackets.ChunkWires(cx, cz, keys, w.values(keys)));
            v.sent.put(key, w.revision());
        }
    }

    /** Drops nodes in cells that cannot hold wire any more (no refund), and slope links that no longer follow a slope. */
    static int validate(ServerLevel level, ChunkPos cp, WiringChunk w) {
        List<BlockPos> bad = new ArrayList<>(), sloped = new ArrayList<>();
        int[] keys = w.keys();
        long[] nodes = w.values(keys);
        for (int i = 0; i < keys.length; i++) {
            BlockPos cell = WiringChunk.cellOf(cp, keys[i]);
            if (!BuildingWiring.wireable(level, cell)) { if (!bad.contains(cell)) bad.add(cell); }
            else if (WiringNode.slopes(nodes[i]) != 0 && !sloped.contains(cell)) sloped.add(cell);
        }
        int n = 0;
        for (BlockPos cell : bad) n += BuildingWiring.clear(level, cell);
        for (BlockPos cell : sloped) n += BuildingWiring.checkSlopes(level, cell);
        return n;
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEWS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void changedLevel(PlayerEvent.PlayerChangedDimensionEvent event) {
        VIEWS.remove(event.getEntity().getUUID());
    }
}
