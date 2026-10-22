package com.antaurora.apofirstlight.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The map's record on the server (AFL Map V1, docs/ui/afl_map_v1_plan.md 5.3; user 2026-10-21: "现在做退游戏/重进保留地图的绘制"):
 * the record belongs to the map item (user 2026-10-20: "地图丢了，记录也丢"). A map gets a number the first time it is
 * carried (item tag {@value #TAG}); its record is a MapStore in the world save. For each player carrying a map (the first
 * found: inventory, then the grids) the chunks within {@link #RADIUS} are sampled into that map, again after
 * {@link #RESAMPLE_MS}, and the player's client gets the map's record (all of it when the player starts carrying it,
 * nearest first, then each changed chunk, and its pencil marks); every other player carrying the same map gets the
 * changes too. A start map's record is filled from the plan at once (StartMap, {@link #fill}). Records are written with
 * the world's saves and dropped from memory when no one carries them.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class MapServer {
    private MapServer() {}

    public static final String TAG = "AflMap";
    /** How far round the player the ground is recorded, m (the client's live circle has the same radius). */
    public static final double RADIUS = 64;
    static final long RESAMPLE_MS = 30_000;
    /** Chunks sampled per player and tick at most; chunks sent per player and tick at most. */
    static final int SAMPLES_PER_TICK = 6, SYNC_PER_TICK = 256;

    private static final class View {
        int map;
        int palette;
        int check;
        final LinkedHashSet<Long> queue = new LinkedHashSet<>();
    }

    private static final Map<Integer, MapStore> STORES = new HashMap<>();
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    private static int nextId = -1;

    static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("afl_maps");
    }

    static Path dir(MinecraftServer server, int id) { return root(server).resolve("map_" + id); }

    /** The map's number on a stack (0: none yet). */
    public static int id(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(TAG) ? stack.getTag().getInt(TAG) : 0;
    }

    /** A new map number: one past the highest folder in the save; its folder is made at once so it is never handed out twice. */
    static int newId(MinecraftServer server) {
        if (nextId < 0) {
            nextId = 1;
            Path root = root(server);
            if (Files.isDirectory(root)) {
                try (Stream<Path> dirs = Files.list(root)) {
                    for (Path p : (Iterable<Path>) dirs::iterator) {
                        String n = p.getFileName().toString();
                        if (n.startsWith("map_")) {
                            try { nextId = Math.max(nextId, Integer.parseInt(n.substring(4)) + 1); } catch (NumberFormatException ignored) {}
                        }
                    }
                } catch (IOException e) {
                    ApocalypseFirstLight.LOGGER.warn("[AFL map] cannot list {}", root, e);
                }
            }
        }
        int id = nextId++;
        try {
            Files.createDirectories(dir(server, id));
        } catch (IOException e) {
            ApocalypseFirstLight.LOGGER.warn("[AFL map] cannot make the folder of map {}", id, e);
        }
        return id;
    }

    static MapStore store(MinecraftServer server, int id) {
        MapStore s = STORES.get(id);
        if (s != null) return s;
        s = new MapStore(id, dir(server, id));
        try {
            s.load();
        } catch (IOException | RuntimeException e) {
            ApocalypseFirstLight.LOGGER.warn("[AFL map] map {} could not be read; it starts again where it failed", id, e);
        }
        STORES.put(id, s);
        return s;
    }

    /** The number of the map the player carries (the first found), numbering it when it has none; 0 without a map. */
    static int carried(ServerPlayer player) {
        var map = AflItems.MAP.get();
        for (ItemStack s : player.getInventory().items) if (s.is(map)) return numbered(player, s);
        for (ItemStack s : player.getInventory().offhand) if (s.is(map)) return numbered(player, s);
        var grids = PlayerGrids.view(player);
        for (ItemStack s : grids.stacks()) {
            if (!s.is(map)) continue;
            int id = id(s);
            if (id != 0) return id;
            s.getOrCreateTag().putInt(TAG, id = newId(player.server));
            grids.commit();
            return id;
        }
        return 0;
    }

    private static int numbered(ServerPlayer player, ItemStack s) {
        int id = id(s);
        if (id == 0) s.getOrCreateTag().putInt(TAG, id = newId(player.server));
        return id;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        View view = VIEWS.computeIfAbsent(player.getUUID(), u -> new View());
        if (--view.check <= 0) {
            view.check = 10;
            int id;
            try {
                id = carried(player);
            } catch (RuntimeException e) {
                id = view.map;
            }
            if (id != view.map) {
                view.map = id;
                view.palette = 0;
                view.queue.clear();
                AflNetwork.mapToPlayer(player, new MapPackets.Reset(id));
                if (id != 0) {
                    MapStore s = store(player.server, id);
                    double px = player.getX(), pz = player.getZ();
                    List<Long> keys = new ArrayList<>(s.chunks().keySet());
                    keys.sort(Comparator.comparingDouble(k -> sq((int) (k >> 32) * 16 + 8 - px) + sq((int) (long) k * 16 + 8 - pz)));
                    view.queue.addAll(keys);
                    if (!s.marks().isEmpty()) AflNetwork.mapToPlayer(player, new MapPackets.Marks(id, s.marks()));
                }
            }
        }
        if (view.map == 0) return;
        MapStore store = store(player.server, view.map);
        if (player.level().dimension() == Level.OVERWORLD) sample(player, store);
        sync(player, view, store);
    }

    private static double sq(double v) { return v * v; }

    private static void sample(ServerPlayer player, MapStore store) {
        ServerLevel level = player.serverLevel();
        long now = System.currentTimeMillis();
        double px = player.getX(), pz = player.getZ();
        int pcx = Math.floorDiv((int) Math.floor(px), 16), pcz = Math.floorDiv((int) Math.floor(pz), 16);
        int r = (int) Math.ceil(RADIUS / 16) + 1, budget = SAMPLES_PER_TICK;
        for (int d = 0; d <= r && budget > 0; d++)
            for (int j = -d; j <= d && budget > 0; j++)
                for (int i = -d; i <= d && budget > 0; i++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != d) continue;
                    int cx = pcx + i, cz = pcz + j;
                    double nx = Math.max(cx * 16, Math.min(px, cx * 16 + 16)), nz = Math.max(cz * 16, Math.min(pz, cz * 16 + 16));
                    if (Math.hypot(nx - px, nz - pz) > RADIUS) continue;
                    long k = MapStore.key(cx, cz);
                    Long at = store.taken(k);
                    if (at != null && now - at < RESAMPLE_MS) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null || chunk.isEmpty()) continue;
                    budget--;
                    int[] s = MapSampler.sample(level, chunk, (b, x, z) -> store.biome(MapSampler.id(b)));
                    if (store.put(cx, cz, s, now))
                        for (View v : VIEWS.values()) if (v.map == store.id) v.queue.add(k);
                }
    }

    /**
     * A start map's record from the plan (StartMap#make, on the server thread): the chunks' samples (biomes as MapStyle
     * indices, turned into the map's palette) and its marks; whoever carries it gets them. A chunk sampled here counts as
     * just sampled, so the real ground replaces it a little later round the player.
     */
    static void fill(MinecraftServer server, int id, Map<Long, int[]> ground, List<MapStore.Mark> marks) {
        MapStore store = store(server, id);
        long now = System.currentTimeMillis();
        List<Long> changed = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : ground.entrySet()) {
            long k = e.getKey();
            if (store.chunk(k) != null) continue;
            int[] s = e.getValue().clone();
            for (int i = 0; i < s.length; i++) {
                String b = MapStyle.registryId(MapGround.biome(s[i]));
                s[i] = MapGround.pack(MapGround.y(s[i]), b == null ? -1 : store.biome(b), MapGround.water(s[i]));
            }
            if (store.put((int) (k >> 32), (int) k, s, now)) changed.add(k);
        }
        store.marks(marks);
        for (Map.Entry<UUID, View> e : VIEWS.entrySet()) {
            if (e.getValue().map != id) continue;
            e.getValue().queue.addAll(changed);
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) AflNetwork.mapToPlayer(p, new MapPackets.Marks(id, store.marks()));
        }
    }

    private static void sync(ServerPlayer player, View view, MapStore store) {
        if (view.queue.isEmpty()) return;
        int n = Math.min(SYNC_PER_TICK, view.queue.size());
        long[] keys = new long[n];
        int[][] samples = new int[n][];
        Iterator<Long> it = view.queue.iterator();
        int i = 0;
        while (i < n && it.hasNext()) {
            long k = it.next();
            it.remove();
            int[] s = store.chunk(k);
            if (s == null) continue;
            keys[i] = k;
            samples[i++] = s;
        }
        if (i < n) {
            keys = java.util.Arrays.copyOf(keys, i);
            samples = java.util.Arrays.copyOf(samples, i);
        }
        List<String> pal = store.palette();
        List<String> add = new ArrayList<>(pal.subList(Math.min(view.palette, pal.size()), pal.size()));
        AflNetwork.mapToPlayer(player, new MapPackets.Chunks(view.map, view.palette, add, keys, samples));
        view.palette = pal.size();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEWS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void save(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        for (Iterator<Map.Entry<Integer, MapStore>> it = STORES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, MapStore> e = it.next();
            try {
                e.getValue().save();
            } catch (IOException ex) {
                ApocalypseFirstLight.LOGGER.warn("[AFL map] map {} could not be saved", e.getKey(), ex);
                continue;
            }
            boolean carried = false;
            for (View v : VIEWS.values()) if (v.map == e.getKey()) { carried = true; break; }
            if (!carried) it.remove();
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STORES.clear();
        VIEWS.clear();
        nextId = -1;
    }
}
