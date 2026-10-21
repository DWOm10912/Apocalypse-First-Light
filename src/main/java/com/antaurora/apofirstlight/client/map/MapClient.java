package com.antaurora.apofirstlight.client.map;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.road.RoadClient;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.map.MapGround;
import com.antaurora.apofirstlight.map.MapPackets;
import com.antaurora.apofirstlight.map.MapSampler;
import com.antaurora.apofirstlight.map.MapStyle;
import com.antaurora.apofirstlight.map.MapTowns;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client side of the map (AFL Map V1, docs/ui/afl_map_v1_plan.md 5-6): what the player has seen of the ground and
 * whether a map is carried.
 * <ul>
 *   <li>Carrying a map (anywhere in the inventory or the grids), {@link #RECORD} is that map's record as the server
 *   keeps it (map.MapServer: sampled round whoever carries the map, saved with the world): {@link #reset} when the
 *   carried map changes, then {@link #receive} brings its chunks, the biomes turned from the map's palette into
 *   MapStyle's.</li>
 *   <li>Always, the chunks round the player go into a live record that forgets them once they are {@link #LIVE_KEEP}
 *   away: the minimap without a map shows only that, faded out past RADIUS.</li>
 * </ul>
 * The towns as the map draws them (MapTowns) are worked out once per road network.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class MapClient {
    private MapClient() {}

    /** How far round the player the ground is seen, m (the minimap's live circle too). */
    public static final double RADIUS = 64;
    static final double LIVE_KEEP = 160;
    static final long RESAMPLE_MS = 30_000;
    /** Chunks sampled per tick at most (each about 64 columns walked down through the trees). */
    static final int SAMPLES_PER_TICK = 6;

    /** One record of ground: chunk samples and when each was taken; tile caches listen for changed chunks. */
    public static final class Record implements MapGround {
        final Map<Long, int[]> chunks = new ConcurrentHashMap<>();
        final Map<Long, Long> taken = new ConcurrentHashMap<>();
        final MapTiles tiles = new MapTiles(this);

        @Override public int[] chunk(int cx, int cz) { return chunks.get(key(cx, cz)); }

        public MapTiles tiles() { return tiles; }

        void put(int cx, int cz, int[] s, long now) {
            long k = key(cx, cz);
            taken.put(k, now);
            int[] old = chunks.put(k, s);
            if (old == null || !java.util.Arrays.equals(old, s)) tiles.dirtyChunk(cx, cz);
        }

        void remove(long k) {
            chunks.remove(k);
            taken.remove(k);
            tiles.dirtyChunk((int) (k >> 32), (int) k);
        }

        void clear() {
            chunks.clear();
            taken.clear();
            tiles.clear();
        }
    }

    public static final Record RECORD = new Record(), LIVE = new Record();
    private static int recordMap;
    /** The record's palette (from the server) as MapStyle indices. */
    private static final java.util.List<Integer> palette = new java.util.ArrayList<>();
    private static boolean carrying;
    private static int carryCheck;
    private static RoadWorks townsFor;
    private static MapTowns towns = MapTowns.EMPTY;

    public static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

    /** Whether the player carries a map (checked twice a second). */
    public static boolean carrying() { return carrying; }

    /** The road network on this client (null: none, or not the overworld). */
    public static RoadWorks works() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension() == Level.OVERWORLD ? RoadClient.works() : null;
    }

    /** The towns of the current road network. */
    public static MapTowns towns() {
        RoadWorks w = works();
        if (w != townsFor) {
            townsFor = w;
            towns = MapTowns.of(w, RoadClient.home());
        }
        return towns;
    }

    static boolean hasMap(Player player) {
        var map = AflItems.MAP.get();
        for (ItemStack s : player.getInventory().items) if (s.is(map)) return true;
        for (ItemStack s : player.getInventory().offhand) if (s.is(map)) return true;
        try {
            for (ItemStack s : PlayerGrids.stacks(player)) if (s.is(map)) return true;
        } catch (RuntimeException ignored) {
            // grids not synced yet
        }
        return false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) return;
        if (--carryCheck <= 0) {
            carryCheck = 10;
            carrying = hasMap(player);
        }
        if (level.dimension() != Level.OVERWORLD) return;
        long now = System.currentTimeMillis();
        double px = player.getX(), pz = player.getZ();
        int pcx = Math.floorDiv((int) Math.floor(px), 16), pcz = Math.floorDiv((int) Math.floor(pz), 16);
        int r = (int) Math.ceil(RADIUS / 16) + 1, budget = SAMPLES_PER_TICK;
        for (int d = 0; d <= r && budget > 0; d++)
            for (int j = -d; j <= d && budget > 0; j++)
                for (int i = -d; i <= d && budget > 0; i++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != d) continue;
                    int cx = pcx + i, cz = pcz + j;
                    // the chunk's nearest point within RADIUS
                    double nx = Math.max(cx * 16, Math.min(px, cx * 16 + 16)), nz = Math.max(cz * 16, Math.min(pz, cz * 16 + 16));
                    if (Math.hypot(nx - px, nz - pz) > RADIUS) continue;
                    long k = key(cx, cz);
                    Long liveAt = LIVE.taken.get(k);
                    if (liveAt != null && now - liveAt <= RESAMPLE_MS) continue;
                    LevelChunk chunk = (LevelChunk) level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null || chunk.isEmpty()) continue;
                    budget--;
                    LIVE.put(cx, cz, MapSampler.sample(level, chunk, MapClient::styleIndex), now);
                }
        // the live record forgets what is far behind
        if (level.getGameTime() % 40 == 0)
            for (Iterator<Long> it = LIVE.chunks.keySet().iterator(); it.hasNext(); ) {
                long k = it.next();
                double cx = (int) (k >> 32) * 16 + 8, cz = (int) k * 16 + 8;
                if (Math.hypot(cx - px, cz - pz) > LIVE_KEEP) LIVE.remove(k);
            }
    }

    /** A biome as MapStyle numbers it (its colour from this client's grass colour when new). */
    static int styleIndex(Holder<Biome> biome, int x, int z) {
        return MapStyle.index(MapSampler.id(biome), biome.value().getGrassColor(x, z));
    }

    /** The server: the player now carries map {@code map} (0: none); its record starts again empty. */
    public static void reset(int map) {
        recordMap = map;
        palette.clear();
        RECORD.clear();
    }

    /** The server: chunks of the carried map's record. */
    public static void receive(MapPackets.Chunks p) {
        if (p.map() != recordMap || p.paletteFrom() != palette.size()) return;
        Minecraft mc = Minecraft.getInstance();
        var biomes = mc.level == null ? null : mc.level.registryAccess().registryOrThrow(Registries.BIOME);
        for (String id : p.palette()) {
            int colour = 0x7FB238;
            Biome b = biomes == null ? null : biomes.get(new ResourceLocation(id));
            if (b != null) colour = b.getGrassColor(0, 0);
            palette.add(MapStyle.index(id, colour));
        }
        long now = System.currentTimeMillis();
        for (int i = 0; i < p.keys().length; i++) {
            int[] s = p.samples()[i].clone();
            for (int j = 0; j < s.length; j++) {
                if (s[j] == 0) continue;
                int b = MapGround.biome(s[j]);
                s[j] = MapGround.pack(MapGround.y(s[j]), b >= 0 && b < palette.size() ? palette.get(b) : -1, MapGround.water(s[j]));
            }
            long k = p.keys()[i];
            RECORD.put((int) (k >> 32), (int) k, s, now);
        }
    }

    /** The recorded sample at a world column (the record when carrying a map, else the live one), 0 when unknown. */
    public static int sampleAt(double x, double z) {
        int gx = (int) Math.floor(x / MapGround.CELL), gz = (int) Math.floor(z / MapGround.CELL);
        int[] c = (carrying ? RECORD : LIVE).chunk(Math.floorDiv(gx, MapGround.CELLS), Math.floorDiv(gz, MapGround.CELLS));
        return c == null ? 0 : c[Math.floorMod(gz, MapGround.CELLS) * MapGround.CELLS + Math.floorMod(gx, MapGround.CELLS)];
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        RECORD.clear();
        LIVE.clear();
        recordMap = 0;
        palette.clear();
        carrying = false;
        townsFor = null;
        towns = MapTowns.EMPTY;
    }
}
