package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Server half of the C2 harness (dev only): once the test player is in, generates the probe sites one per tick
 * (blocking FULL chunk requests on the server thread, so the time is the generator's), checks each site, hashes every
 * chunk, writes the report, compares with a baseline report if asked (verify=FILE) and lets the client quit.
 * <ul>
 *   <li>time per site (FULL chunks of the site and everything they need);</li>
 *   <li>ground top (first block down that stops movement and is not a log or leaves) against the plan height;</li>
 *   <li>seams: height steps between neighbouring columns across chunk borders against inside chunks;</li>
 *   <li>water faces (still water beside air at the same Y) and water over air;</li>
 *   <li>biomes at the ground; per chunk a hash of every block state name and the surface biomes, and a terrain
 *   signature: per column the ground top, the still water standing on it and the biome there.</li>
 * </ul>
 * verify=FILE compares both with a baseline report. The terrain signatures decide (VERIFY PASS / FAIL); the block hashes
 * are reported only: vanilla decoration is not reproducible block for block (ore, stone and dirt blobs of neighbouring
 * chunks overwrite each other in the order the worker threads decorate them, springs flow while spawn chunks tick;
 * measured 2026-10-14: two worlds from the same build and seed differ in about 95 of 256 chunks, only in such blocks).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class C2Probe {
    private C2Probe() {}

    private static int ticks = -1;
    /** tick the probe begins at: 40, later with look=X,Z (the client meshes the area first) */
    private static int start = 40;
    private static List<Site> sites;
    private static int next;
    private static StringBuilder report;
    private static final Map<String, String> chunkHashes = new TreeMap<>(), terrainHashes = new TreeMap<>();
    private static long t0;

    record Site(String name, int x, int z, int radius) {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (C2Harness.active() && ticks < 0) ticks = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ticks < 0) return;
        ticks++;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        try {
            if (ticks == 40 && C2Harness.ARGS.containsKey("look")) look(server, level);
            if (ticks == start) begin(level);
            else if (ticks > start && sites != null && next < sites.size()) site(level, sites.get(next++));
            else if (ticks > start && sites != null && next == sites.size()) {
                next++;
                finish(level);
            } else if (ticks > start && sites != null && next == sites.size() + 1 && ticks % 20 == 0) {
                ticks = -100000;
                C2HarnessClient.quit();
            }
        } catch (Throwable t) {
            C2Harness.log("probe failed: " + t);
            ApocalypseFirstLight.LOGGER.error("[AFL C2 HARNESS] probe failure", t);
            ticks = -100000;
            C2HarnessClient.quit();
        }
    }

    // ------------------------------------------------------------------ sites
    /**
     * look=X,Z (rendering checks, 2026-10-14): puts the test player on the ground there, looking a little down, and
     * waits lookSeconds (default 45) before the probe, so the client meshes that area (C2HarnessClient#quit logs the
     * terrain smoothing counters of those meshes).
     */
    private static void look(MinecraftServer server, ServerLevel level) {
        String[] xz = C2Harness.arg("look", "").split(",");
        int x = Integer.parseInt(xz[0].trim()), z = Integer.parseInt(xz[1].trim());
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.teleportTo(level, x + 0.5, y + 1, z + 0.5, 0F, 30F);
        start = 40 + 20 * Integer.parseInt(C2Harness.arg("lookSeconds", "45"));
        C2Harness.log("looking at " + x + " " + y + " " + z + " for " + (start - 40) / 20 + " s before the probe");
    }

    private static void begin(ServerLevel level) {
        t0 = System.nanoTime();
        report = new StringBuilder();
        TerrainPlanSurface plan = C2Plan.surface(level);
        BlockPos spawn = level.getSharedSpawnPos();
        line("world " + C2Harness.arg("world", "?") + " tag " + C2Harness.arg("tag", "?") + " seed " + level.getSeed());
        line("plan " + (plan == null ? "none" : C2Plan.describe(plan)));
        line("spawn " + spawn.getX() + " " + spawn.getY() + " " + spawn.getZ());
        line("heap_used_mb_at_start " + usedMb(false));
        sites = new ArrayList<>();
        sites.add(new Site("spawn", spawn.getX(), spawn.getZ(), 4));
        if (plan != null) sites.addAll(C2Plan.sites(level, plan, spawn));
        line("sites " + sites.size());
        C2Harness.log("probing " + sites.size() + " sites");
    }

    private static void site(ServerLevel level, Site s) {
        int cx0 = s.x >> 4, cz0 = s.z >> 4, r = s.radius;
        long a = System.nanoTime();
        LevelChunk[][] chunks = new LevelChunk[2 * r + 1][2 * r + 1];
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++)
            chunks[dx + r][dz + r] = level.getChunk(cx0 + dx, cz0 + dz);
        long ms = (System.nanoTime() - a) / 1_000_000;
        int n = (2 * r + 1) * (2 * r + 1);
        TerrainPlanSurface plan = C2Plan.surface(level);
        int x0 = (cx0 - r) << 4, z0 = (cz0 - r) << 4, w = (2 * r + 1) * 16;
        int[] ground = new int[w * w];
        long[] terrain = new long[(2 * r + 1) * (2 * r + 1)];
        java.util.Arrays.fill(terrain, 0xcbf29ce484222325L);
        Map<String, Integer> biomes = new TreeMap<>();
        int waterFaces = 0, waterOverAir = 0, flowing = 0, snow = 0, landCols = 0, within1 = 0;
        double planDiff = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < w; lz++) for (int lx = 0; lx < w; lx++) {
            int x = x0 + lx, z = z0 + lz;
            LevelChunk c = chunks[lx >> 4][lz >> 4];
            int top = c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            int y = top;
            while (y > level.getMinBuildHeight() && !ground(c.getBlockState(p.set(x, y, z)))) y--;
            ground[lz * w + lx] = y;
            Holder<Biome> b = c.getNoiseBiome((x & 15) >> 2, (y + 1) >> 2, (z & 15) >> 2);
            String biome = b.unwrapKey().map(k -> k.location().toString()).orElse("?");
            biomes.merge(biome, 1, Integer::sum);
            int still = y;
            while (still < top && c.getFluidState(p.set(x, still + 1, z)).isSourceOfType(Fluids.WATER)) still++;
            int t = (lz >> 4) * (2 * r + 1) + (lx >> 4);
            terrain[t] = mix(mix(mix(terrain[t], y), still - y), biome.hashCode());
            for (int yy = y + 1; yy <= top; yy++) {
                BlockState st = c.getBlockState(p.set(x, yy, z));
                if (st.is(Blocks.SNOW) || st.is(Blocks.SNOW_BLOCK)) snow++;
                if (st.getFluidState().isEmpty()) continue;
                if (!st.getFluidState().isSource()) flowing++;
                if (st.getFluidState().getType() != Fluids.WATER) continue;
                if (yy - 1 > y && c.getBlockState(p.set(x, yy - 1, z)).isAir()) waterOverAir++;
                if (lx > 0 && lz > 0 && lx < w - 1 && lz < w - 1) {
                    for (int k = 0; k < 4; k++) {
                        int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0);
                        LevelChunk nc = chunks[(nx - x0) >> 4][(nz - z0) >> 4];
                        if (nc.getBlockState(p.set(nx, yy, nz)).isAir()) { waterFaces++; break; }
                    }
                }
            }
            if (plan != null && !c.getBlockState(p.set(x, y + 1, z)).getFluidState().is(Fluids.WATER)) {
                double d = Math.abs((y + 1) - plan.heightAt(x + 0.5, z + 0.5));
                planDiff += d;
                landCols++;
                if (d <= 1.0) within1++;
            }
        }
        // seams: |dh| between x- and z-neighbours, across a chunk border or inside a chunk
        long[] cnt = new long[2], big = new long[2];
        double[] sum = new double[2];
        int[] max = new int[2];
        for (int lz = 0; lz < w; lz++) for (int lx = 0; lx < w; lx++) for (int k = 0; k < 2; k++) {
            int nx = lx + (k == 0 ? 1 : 0), nz = lz + (k == 1 ? 1 : 0);
            if (nx >= w || nz >= w) continue;
            int cls = (k == 0 ? (x0 + nx) & 15 : (z0 + nz) & 15) == 0 ? 1 : 0;
            int d = Math.abs(ground[nz * w + nx] - ground[lz * w + lx]);
            cnt[cls]++;
            sum[cls] += d;
            if (d >= 4) big[cls]++;
            max[cls] = Math.max(max[cls], d);
        }
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            LevelChunk c = chunks[dx + r][dz + r];
            chunkHashes.put((cx0 + dx) + " " + (cz0 + dz), hash(c));
            terrainHashes.put((cx0 + dx) + " " + (cz0 + dz), Long.toHexString(terrain[(dz + r) * (2 * r + 1) + dx + r]));
        }
        line(String.format("site %s at %d %d: %d chunks in %d ms (%.1f ms/chunk)", s.name, s.x, s.z, n, ms, ms / (double) n));
        line(String.format("  ground vs plan: %d land columns, mean |diff| %.2f, within 1 block %.1f%%", landCols,
                landCols == 0 ? 0 : planDiff / landCols, landCols == 0 ? 0 : 100.0 * within1 / landCols));
        line(String.format("  steps inside chunks: mean %.3f, >=4: %d of %d, max %d", sum[0] / Math.max(1, cnt[0]), big[0], cnt[0], max[0]));
        line(String.format("  steps across chunk borders: mean %.3f, >=4: %d of %d, max %d", sum[1] / Math.max(1, cnt[1]), big[1], cnt[1], max[1]));
        line("  water faces " + waterFaces + ", water over air " + waterOverAir + ", flowing fluid " + flowing + ", snow blocks " + snow);
        line("  biomes " + top(biomes, 6));
        if (plan != null) line("  " + C2Plan.siteInfo(plan, s.x, s.z));
    }

    private static void finish(ServerLevel level) {
        line("total probe time ms " + (System.nanoTime() - t0) / 1_000_000);
        line("heap_used_mb_after_gc " + usedMb(true));
        StringBuilder hashes = new StringBuilder();
        chunkHashes.forEach((k, v) -> hashes.append("chunk ").append(k).append(' ').append(v).append('\n'));
        terrainHashes.forEach((k, v) -> hashes.append("terrain ").append(k).append(' ').append(v).append('\n'));
        String verify = C2Harness.arg("verify", "");
        if (!verify.isEmpty()) {
            try {
                List<String> lines = Files.readAllLines(C2Harness.reportDir().resolve(verify), StandardCharsets.UTF_8);
                int[] blocks = compare(lines, "chunk ", chunkHashes);
                line("verify block hashes against " + verify + ": same " + blocks[0] + ", different " + blocks[1]
                        + ", not in baseline " + blocks[2] + " (decoration order noise expected; not decisive)");
                int[] ter = compare(lines, "terrain ", terrainHashes);
                if (ter[0] + ter[1] == 0) line("verify terrain: the baseline has no terrain signatures");
                else {
                    line("verify terrain signatures against " + verify + ": same " + ter[0] + ", different " + ter[1]
                            + ", not in baseline " + ter[2]);
                    line("VERIFY " + (ter[1] == 0 && ter[0] > 0 ? "PASS" : "FAIL"));
                }
            } catch (Exception e) {
                line("verify failed: " + e);
            }
        }
        line(C2Plan.lockStatus(level));
        String name = C2Harness.arg("world", "w") + "_" + C2Harness.arg("tag", "t") + ".txt";
        C2Harness.writeReport(name, report + hashes.toString());
        C2Harness.log("FINISHED " + name);
    }

    /** same, different, missing: this run's hashes against the baseline's lines with the prefix. */
    private static int[] compare(List<String> baseline, String prefix, Map<String, String> mine) {
        Map<String, String> base = new HashMap<>();
        for (String l : baseline)
            if (l.startsWith(prefix)) { String[] t = l.split(" "); base.put(t[1] + " " + t[2], t[3]); }
        int[] out = new int[3];
        List<String> diffs = new ArrayList<>();
        for (var e : mine.entrySet()) {
            String b = base.get(e.getKey());
            if (b == null) out[2]++;
            else if (b.equals(e.getValue())) out[0]++;
            else { out[1]++; if (diffs.size() < 8) diffs.add(e.getKey()); }
        }
        if (!diffs.isEmpty() && base.size() > 0 && prefix.startsWith("terrain")) line("  first different terrain chunks " + diffs);
        return out;
    }

    // ------------------------------------------------------------------ helpers
    private static final Map<BlockState, Boolean> GROUND = new IdentityHashMap<>();
    private static final Map<BlockState, Long> STATE_HASH = new IdentityHashMap<>();

    /** Ground: blocks that stop movement, without trees (flowers, grass, snow layers and saplings are not ground). */
    @SuppressWarnings("deprecation")
    static boolean ground(BlockState s) {
        return GROUND.computeIfAbsent(s, st -> !st.isAir() && st.getFluidState().isEmpty() && st.blocksMotion()
                && !st.is(BlockTags.LOGS) && !st.is(BlockTags.LEAVES));
    }

    static String hash(LevelChunk c) {
        long h = 0xcbf29ce484222325L;
        for (LevelChunkSection sec : c.getSections()) {
            if (sec.hasOnlyAir()) { h = mix(h, 0x41); continue; }
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                h = mix(h, stateHash(sec.getBlockState(x, y, z)));
        }
        for (int qz = 0; qz < 4; qz++) for (int qx = 0; qx < 4; qx++) {
            int y = c.getHeight(Heightmap.Types.WORLD_SURFACE, qx * 4, qz * 4);
            String b = c.getNoiseBiome(qx, y >> 2, qz).unwrapKey().map(k -> k.location().toString()).orElse("?");
            h = mix(h, b.hashCode());
        }
        return Long.toHexString(h);
    }

    private static long stateHash(BlockState s) {
        return STATE_HASH.computeIfAbsent(s, st -> {
            long v = 0;
            String str = BuiltInRegistries.BLOCK.getKey(st.getBlock()) + st.getValues().toString();
            for (byte b : str.getBytes(StandardCharsets.UTF_8)) v = v * 1099511628211L + b;
            return v;
        });
    }

    private static long mix(long h, long v) {
        h ^= v;
        h *= 0x100000001b3L;
        return h ^ (h >>> 29);
    }

    static String top(Map<String, Integer> m, int k) {
        var list = new ArrayList<>(m.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        int total = 0;
        for (var e : list) total += e.getValue();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(k, list.size()); i++)
            sb.append(i > 0 ? ", " : "").append(list.get(i).getKey().replace("apocalypse_firstlight:", "afl:"))
                    .append(String.format(" %.0f%%", 100.0 * list.get(i).getValue() / total));
        return sb.toString();
    }

    private static long usedMb(boolean gc) {
        Runtime rt = Runtime.getRuntime();
        if (gc) { System.gc(); System.gc(); }
        return (rt.totalMemory() - rt.freeMemory()) >> 20;
    }

    private static void line(String s) {
        report.append(s).append('\n');
        C2Harness.log(s);
    }

}
