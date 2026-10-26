package com.antaurora.apofirstlight.ground;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The ground's geometry changed (terrain rework R0, docs/worldgen/terrain_rework_v1_design.md 6.3): on the server, at the
 * end of the tick, once per changed chunk with the y range touched, after Earthwork's and RockCarve's own end-of-tick work.
 * Sources: a block change (TerrainSmooth#changed: its slopes, worn faces and boxes two blocks round), an edit of
 * Earthwork's vertices, a carving. Chunk loads are not reported (listen to ChunkEvent.Load). Nothing is kept or done while
 * no one listens.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class GroundEvents {
    public interface Listener {
        void changed(ServerLevel level, int chunkX, int chunkZ, int minY, int maxY);
    }

    /** How far a block change reaches into the geometry round it: slopes read their 3 x 3 columns, worn faces two blocks out. */
    private static final int REACH = 2;

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Map<ServerLevel, Long2LongOpenHashMap> PENDING = new IdentityHashMap<>();

    private GroundEvents() {}

    public static void listen(Listener l) { LISTENERS.add(l); }

    public static void unlisten(Listener l) { LISTENERS.remove(l); }

    /** Whether anyone listens (a caller may skip working out the y range). */
    public static boolean listening() { return !LISTENERS.isEmpty(); }

    /** Hook (TerrainSmooth#changed, server): the block at pos changed. */
    public static void blockChanged(Level level, BlockPos pos) {
        if (LISTENERS.isEmpty() || !(level instanceof ServerLevel s)) return;
        for (int cx = (pos.getX() - REACH) >> 4; cx <= (pos.getX() + REACH) >> 4; cx++)
            for (int cz = (pos.getZ() - REACH) >> 4; cz <= (pos.getZ() + REACH) >> 4; cz++)
                mark(s, cx, cz, pos.getY() - REACH, pos.getY() + REACH);
    }

    /**
     * Hook (Earthwork's and RockCarve's end of tick): chunk (cx, cz)'s earth or carving changed between minY and maxY
     * (blocks); its neighbours read its border vertices and faces, so they are reported too.
     */
    public static void areaChanged(ServerLevel level, int cx, int cz, int minY, int maxY) {
        if (LISTENERS.isEmpty()) return;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) mark(level, cx + dx, cz + dz, minY - REACH, maxY + REACH);
    }

    private static void mark(ServerLevel level, int cx, int cz, int minY, int maxY) {
        Long2LongOpenHashMap m = PENDING.computeIfAbsent(level, k -> new Long2LongOpenHashMap());
        long key = ChunkPos.asLong(cx, cz);
        if (m.containsKey(key)) {
            long v = m.get(key);
            minY = Math.min(minY, (int) (v >> 32));
            maxY = Math.max(maxY, (int) v);
        }
        m.put(key, (long) minY << 32 | (maxY & 0xFFFFFFFFL));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        for (var e : PENDING.entrySet()) {
            ServerLevel level = e.getKey();
            for (Long2LongMap.Entry c : e.getValue().long2LongEntrySet()) {
                int cx = ChunkPos.getX(c.getLongKey()), cz = ChunkPos.getZ(c.getLongKey());
                int minY = Math.max(level.getMinBuildHeight(), (int) (c.getLongValue() >> 32));
                int maxY = Math.min(level.getMaxBuildHeight() - 1, (int) c.getLongValue());
                for (Listener l : LISTENERS) l.changed(level, cx, cz, minY, maxY);
            }
        }
        PENDING.clear();
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }
}
