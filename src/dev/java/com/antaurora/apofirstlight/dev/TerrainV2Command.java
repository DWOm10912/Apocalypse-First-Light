package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.terrain.v2.PlanStabilityDensity;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverCarver;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverNetwork;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanMaps;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanStore;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainV2TestPoints;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Terrain V2 acceptance tools (dev only, 2026-10-10; docs/worldgen/terrain_v2_phase2_generation_v1.md and
 * terrain_v2_phase2b_rivers_v1.md):
 * <pre>
 * /afl dev terrain_v2 here            plan vs generated surface at the player, river / pool state of the column
 * /afl dev terrain_v2 check [chunks]  over the LOADED chunks round the player (nothing is generated or loaded):
 *                                     surface vs plan, chunk-border steps, the stable layer, estuary water, river
 *                                     water (levels, dry beds, exposed water faces, chunk-border agreement), marsh pools
 * /afl dev terrain_v2 points          the river acceptance points of this seed (TerrainV2TestPoints) and the spawn
 * /afl dev terrain_v2 export          plan maps (relief, sea / estuary / marsh, rivers, points) to afl_debug/terrain_v2:
 *                                     the whole plan at 8 m per pixel and a 512 m window round the player at 1 m
 * </pre>
 * The generated top is the ground's top block: down from the MOTION_BLOCKING_NO_LEAVES heightmap past logs, leaves,
 * plants and fluid. Phase 2b fix: ChunkAccess.getHeight already returns the top block (first free Y - 1); Phase 2
 * subtracted one more, so its window sat one block low and reached 13 blocks under the plains surface (the 1.80 % /
 * 4.38 % "openings" were mostly caves at the designed floor of the layer; save audit 2026-10-10).
 */
public final class TerrainV2Command {
    private TerrainV2Command() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("terrain_v2")
                .then(Commands.literal("here").executes(TerrainV2Command::here))
                .then(Commands.literal("check").executes(c -> check(c, 6))
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 24)).executes(c -> check(c, IntegerArgumentType.getInteger(c, "chunks")))))
                .then(Commands.literal("points").executes(TerrainV2Command::points))
                .then(Commands.literal("ecology").executes(c -> ecology(c, 6))
                        .then(Commands.argument("chunks", IntegerArgumentType.integer(1, 24)).executes(c -> ecology(c, IntegerArgumentType.getInteger(c, "chunks")))))
                .then(Commands.literal("export").executes(TerrainV2Command::export));
    }

    static TerrainPlanSurface plan(ServerLevel level) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) level.getChunkSource().randomState();
        return access.apocalypse$hasTerrainPlan() ? TerrainPlanStore.peek(access.apocalypse$getSeed()) : null;
    }

    private static final String[] WATER = {"land", "sea", "estuary", "marsh"};
    private static final String[] KIND = {"-", "river", "bank", "marsh pool"};

    private static int here(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        TerrainPlanSurface plan = plan(level);
        if (plan == null) { c.getSource().sendFailure(Component.literal("No Terrain V2 plan in this world")); return 0; }
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        int x = pos.getX(), z = pos.getZ();
        double h = plan.heightAt(x + 0.5, z + 0.5);
        LevelChunk chunk = level.getChunkAt(pos);
        int top = groundTop(chunk, x & 15, z & 15);
        RiverNetwork.Column col = new RiverNetwork.Column();
        RiverCarver.Edit e = new RiverCarver.Edit();
        RiverCarver.plan(plan, x, z, (int) Math.ceil(h) - 1, col, e);
        String river = e.kind == RiverCarver.NONE ? "no river water"
                : e.kind == RiverCarver.BANK ? String.format("river bank (water Y%d within %.0f m)", col.level, col.edge + 0.5)
                : String.format("%s, water top Y%d, %s", KIND[e.kind], e.water, waterAt(chunk, x, e.water, z) ? "present" : "MISSING");
        c.getSource().sendSuccess(() -> Component.literal(String.format(
                "Terrain V2 %s at %d %d: plan surface %.2f, generated top block %d (planned top %d) | %s | stable %d (hard %d) | shore %d m | grade %.3f | belt %.2f | %s",
                plan.version, x, z, h, top, (int) Math.ceil(h) - 1, WATER[plan.waterClass(x, z)], plan.stableDepth(x, z),
                PlanStabilityDensity.hardDepth(plan.stableDepth(x, z)), plan.shoreDistance(x, z), plan.slopeAt(x, z),
                plan.beltWeight(x, z), river)), false);
        String actual = level.getBiome(new BlockPos(x, top, z)).unwrapKey().map(k -> k.location().toString()).orElse("?");
        String planned = com.antaurora.apofirstlight.world.biome.MainNationBiomeRegionPlan
                .ecologyBiome(plan, x >> 2 << 2, z >> 2 << 2).location().toString();
        int zone = plan.ecology == null ? -1 : plan.ecology.zone()[TerrainPlanSurface.cellIndex(plan, x, z)];
        c.getSource().sendSuccess(() -> Component.literal(String.format("Ecology: biome %s (plan %s) | r1 zone %s",
                actual, planned, zone < 0 ? "water" : com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyPlan.NAMES[zone])), false);
        return 1;
    }

    private static boolean waterAt(LevelChunk chunk, int x, int y, int z) {
        return chunk.getBlockState(new BlockPos(x, y, z)).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
    }

    private static int check(CommandContext<CommandSourceStack> c, int radius) {
        ServerLevel level = c.getSource().getLevel();
        TerrainPlanSurface plan = plan(level);
        if (plan == null) { c.getSource().sendFailure(Component.literal("No Terrain V2 plan in this world")); return 0; }
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        int pcx = pos.getX() >> 4, pcz = pos.getZ() >> 4;
        int chunks = 0, cols = 0, within1 = 0, exact = 0;
        int hardCols = 0, hardOpen = 0, softCols = 0, softOpen = 0, layerLava = 0;
        int estuaryCols = 0, estuaryWet = 0, seaCols = 0, seaExposed = 0;
        int riverCols = 0, riverWet = 0, riverDry = 0, exposed = 0, borderCols = 0, borderMismatch = 0, poolCols = 0, poolWet = 0, bankCols = 0;
        double sumAbs = 0, maxAbs = 0;
        double[] diffs = new double[(2 * radius + 1) * (2 * radius + 1) * 256];
        double seamSum = 0, innerSum = 0;
        int seamN = 0, innerN = 0;
        RiverNetwork.Column col = new RiverNetwork.Column();
        RiverCarver.Edit e = new RiverCarver.Edit();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            chunks++;
            LevelChunk east = level.getChunkSource().getChunkNow(cx + 1, cz), south = level.getChunkSource().getChunkNow(cx, cz + 1);
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                int top = groundTop(chunk, lx, lz);
                int wc = plan.waterClass(x, z);
                double h = plan.heightAt(x + 0.5, z + 0.5);
                if (wc == 2) {
                    estuaryCols++;
                    if (chunk.getBlockState(p.set(x, 62, z)).getFluidState().isSource()) estuaryWet++;
                }
                // sea-level water (sea, estuary, flooded low shore): a water block at Y62 beside open air (2b.1 check)
                if (plan.seaFloodAt(x, z, h) && waterAt(chunk, x, 62, z)) {
                    seaCols++;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int xx = x + d[0], zz = z + d[1];
                        LevelChunk nc = (xx >> 4) == cx && (zz >> 4) == cz ? chunk : level.getChunkSource().getChunkNow(xx >> 4, zz >> 4);
                        if (nc != null && nc.getBlockState(p.set(xx, 62, zz)).isAir()) seaExposed++;
                    }
                }
                if ((wc == 1 || wc == 2) && top < 62) continue;
                // what the river pass intended for this column (from the planned top: the generated top is already edited)
                RiverCarver.plan(plan, x, z, (int) Math.ceil(h) - 1, col, e);
                if (e.kind == RiverCarver.RIVER || e.kind == RiverCarver.POOL) {
                    boolean pool = e.kind == RiverCarver.POOL;
                    if (pool && (int) Math.ceil(h) - 1 != RiverCarver.MARSH_TOP) continue;
                    if (pool) poolCols++; else riverCols++;
                    boolean wet = waterAt(chunk, x, e.water, z) && !waterAt(chunk, x, e.water + 1, z);
                    if (wet) { if (pool) poolWet++; else riverWet++; }
                    else if (!pool && !waterAt(chunk, x, e.water, z)) riverDry++;
                    // exposed water faces: a water block beside open air (a riffle's flow is water, so it does not count)
                    for (int y = top + 1; y <= e.water; y++) {
                        if (!waterAt(chunk, x, y, z)) continue;
                        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int xx = x + d[0], zz = z + d[1];
                            LevelChunk nc = (xx >> 4) == cx && (zz >> 4) == cz ? chunk : level.getChunkSource().getChunkNow(xx >> 4, zz >> 4);
                            if (nc != null && nc.getBlockState(p.set(xx, y, zz)).isAir()) exposed++;
                        }
                    }
                    if (!pool && (lx == 15 && east != null || lz == 15 && south != null)) {
                        borderCols++;
                        int xx = lx == 15 ? x + 1 : x, zz = lx == 15 ? z : z + 1;
                        LevelChunk nc = lx == 15 ? east : south;
                        RiverCarver.Edit n = new RiverCarver.Edit();
                        RiverCarver.plan(plan, xx, zz, (int) Math.ceil(plan.heightAt(xx + 0.5, zz + 0.5)) - 1, new RiverNetwork.Column(), n);
                        if (n.kind == RiverCarver.RIVER && n.water == e.water && waterAt(chunk, x, e.water, z) != waterAt(nc, xx, n.water, zz)) borderMismatch++;
                    }
                    continue;
                }
                if (e.kind == RiverCarver.BANK) { bankCols++; continue; }
                if (wc != 0) continue;
                double d = top + 1 - h;
                diffs[cols++] = d;
                sumAbs += Math.abs(d);
                maxAbs = Math.max(maxAbs, Math.abs(d));
                if (Math.abs(d) <= 1) within1++;
                if (top == (int) Math.ceil(h) - 1) exact++;
                // the stable layer: the hard floor (plains / coastal plain) must hold no air at all; below the foothills
                // and the belt only the soft ramp holds (cave mouths there are by design)
                int stable = plan.stableDepth(x, z), hard = PlanStabilityDensity.hardDepth(stable);
                int depth = hard > 0 ? hard : stable;
                boolean open = false, lava = false;
                for (int y = top - 1; y >= Math.ceil(h - depth) && y > level.getMinBuildHeight(); y--) {
                    BlockState s = chunk.getBlockState(p.set(x, y, z));
                    if (s.isAir()) open = true;
                    if (s.is(Blocks.LAVA)) lava = true;
                }
                if (hard > 0) { hardCols++; if (open) hardOpen++; } else { softCols++; if (open) softOpen++; }
                if (lava) layerLava++;
                if (lx < 15) { innerSum += Math.abs(top - groundTop(chunk, lx + 1, lz)); innerN++; }
                else if (east != null) { seamSum += Math.abs(top - groundTop(east, 0, lz)); seamN++; }
                if (lz < 15) { innerSum += Math.abs(top - groundTop(chunk, lx, lz + 1)); innerN++; }
                else if (south != null) { seamSum += Math.abs(top - groundTop(south, lx, 0)); seamN++; }
            }
        }
        if (chunks == 0) { c.getSource().sendFailure(Component.literal("No loaded chunks round the player")); return 0; }
        double[] sorted = Arrays.copyOf(diffs, cols);
        for (int i = 0; i < cols; i++) sorted[i] = Math.abs(sorted[i]);
        Arrays.sort(sorted);
        final double mean = cols > 0 ? sumAbs / cols : Double.NaN, p95 = cols > 0 ? sorted[(int) (cols * 0.95)] : Double.NaN, fMax = maxAbs;
        final double seam = seamN > 0 ? seamSum / seamN : Double.NaN, inner = innerN > 0 ? innerSum / innerN : Double.NaN;
        String land = String.format("Terrain V2 check: %d chunks, %d dry land columns | surface vs plan: mean |d| %.2f, P95 %.2f, max %.2f, within 1 block %.1f%%, top = planned top %.1f%% "
                        + "| mean step across chunk borders %.3f vs inside %.3f",
                chunks, cols, mean, p95, fMax, pct(within1, cols), pct(exact, cols), seam, inner);
        String layer = String.format("Stable layer: hard floor (plains / coastal) air in %d of %d columns (%.2f%%), soft layer (foothills / belt, cave mouths allowed) air in %d of %d (%.2f%%), lava in the layer %d",
                hardOpen, hardCols, pct(hardOpen, hardCols), softOpen, softCols, pct(softOpen, softCols), layerLava);
        String water = String.format("Water: estuary columns %d, water at Y62 in %d | sea-level water columns %d, faces against air %d | river columns %d: water at the planned level %d, dry %d, exposed water faces %d, "
                        + "chunk-border columns %d (mismatch %d) | bank columns %d | marsh pools %d, with water %d",
                estuaryCols, estuaryWet, seaCols, seaExposed, riverCols, riverWet, riverDry, exposed, borderCols, borderMismatch, bankCols, poolCols, poolWet);
        c.getSource().sendSuccess(() -> Component.literal(land), false);
        c.getSource().sendSuccess(() -> Component.literal(layer), false);
        c.getSource().sendSuccess(() -> Component.literal(water), false);
        return 1;
    }

    private static double pct(int a, int b) { return b > 0 ? 100.0 * a / b : 0; }

    /** The ground's top block in a full chunk: below trees, plants and water. */
    static int groundTop(LevelChunk chunk, int lx, int lz) {
        int x = chunk.getPos().getMinBlockX() + lx, z = chunk.getPos().getMinBlockZ() + lz;
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);   // already the top block
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (y > chunk.getMinBuildHeight()) {
            BlockState s = chunk.getBlockState(p.set(x, y, z));
            if (s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() || !s.getFluidState().isEmpty()) y--;
            else break;
        }
        return y;
    }

    private static int points(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        TerrainPlanSurface plan = plan(level);
        if (plan == null) { c.getSource().sendFailure(Component.literal("No Terrain V2 plan in this world")); return 0; }
        int[] spawn = plan.naturalSpawn(4096);
        c.getSource().sendSuccess(() -> Component.literal("Terrain V2 " + plan.version + " seed " + plan.seed
                + ": natural spawn " + (spawn == null ? "none" : spawn[0] + " " + spawn[1])
                + " (the r1 landform points are in docs/worldgen/terrain_v2_phase2_generation_v1.md)"), false);
        for (TerrainV2TestPoints.Point pt : TerrainV2TestPoints.rivers(plan)) {
            c.getSource().sendSuccess(() -> Component.literal(String.format("  %s: %d %d (plan surface %.1f) - %s",
                    pt.name(), pt.x(), pt.z(), plan.heightAt(pt.x(), pt.z()), pt.note())), false);
        }
        for (TerrainV2TestPoints.Point pt : TerrainV2TestPoints.ecology(plan)) {
            c.getSource().sendSuccess(() -> Component.literal(String.format("  %s: %d %d (plan surface %.1f) - %s",
                    pt.name(), pt.x(), pt.z(), plan.heightAt(pt.x(), pt.z()), pt.note())), false);
        }
        return 1;
    }

    /**
     * Ecology acceptance over the loaded chunks round the player (2026-10-10): per surface biome the columns, canopy cover
     * (leaves within 40 blocks above the ground top), trees (log columns standing on soil), ground cover (a plant on the
     * ground), bare sand / gravel / mud tops; biome-plan agreement; and the cost of the biome resolution and river fill.
     */
    private static int ecology(CommandContext<CommandSourceStack> c, int radius) {
        ServerLevel level = c.getSource().getLevel();
        TerrainPlanSurface plan = plan(level);
        if (plan == null) { c.getSource().sendFailure(Component.literal("No Terrain V2 plan in this world")); return 0; }
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        java.util.Map<String, int[]> stats = new java.util.TreeMap<>();   // columns, canopy, trees, cover, bare, planAgree
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int chunks = 0;
        for (int cx = (pos.getX() >> 4) - radius; cx <= (pos.getX() >> 4) + radius; cx++)
            for (int cz = (pos.getZ() >> 4) - radius; cz <= (pos.getZ() >> 4) + radius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                chunks++;
                for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                    int x = (cx << 4) + lx, z = (cz << 4) + lz;
                    int top = groundTop(chunk, lx, lz);
                    String id = level.getBiome(p.set(x, top, z)).unwrapKey().map(k -> k.location().toString()).orElse("?");
                    int[] st = stats.computeIfAbsent(id, k -> new int[6]);
                    st[0]++;
                    String planned = com.antaurora.apofirstlight.world.biome.MainNationBiomeRegionPlan
                            .ecologyBiome(plan, x >> 2 << 2, z >> 2 << 2).location().toString();
                    if (planned.equals(id)) st[5]++;
                    BlockState ground = chunk.getBlockState(p.set(x, top, z));
                    if (ground.is(Blocks.SAND) || ground.is(Blocks.GRAVEL) || ground.is(Blocks.MUD) || ground.is(Blocks.STONE)) st[4]++;
                    BlockState above = chunk.getBlockState(p.set(x, top + 1, z));
                    if (above.is(BlockTags.LOGS) && !chunk.getBlockState(p.set(x, top, z)).is(BlockTags.LOGS)) st[2]++;
                    else if (!above.isAir() && above.getFluidState().isEmpty() && above.canBeReplaced()) st[3]++;
                    for (int y = top + 1; y <= top + 40; y++) {
                        BlockState s = chunk.getBlockState(p.set(x, y, z));
                        if (s.is(BlockTags.LEAVES)) { st[1]++; break; }
                    }
                }
            }
        if (chunks == 0) { c.getSource().sendFailure(Component.literal("No loaded chunks round the player")); return 0; }
        final int fChunks = chunks;
        c.getSource().sendSuccess(() -> Component.literal("Terrain V2 ecology over " + fChunks + " loaded chunks (biome at the ground top):"), false);
        for (var e : stats.entrySet()) {
            int[] st = e.getValue();
            double area = st[0] / 256.0;
            String line = String.format("  %s: %d columns (%.1f%%) | canopy %.0f%% | trees %.1f per chunk | ground plants %.0f%% | sand/gravel/mud/stone top %.0f%% | matches the plan %.0f%%",
                    e.getKey(), st[0], 100.0 * st[0] / (fChunks * 256.0), 100.0 * st[1] / st[0], st[2] / area,
                    100.0 * st[3] / st[0], 100.0 * st[4] / st[0], 100.0 * st[5] / st[0]);
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        // the cost: biome-plan resolution (cache misses are real evaluations) and the river pass, since server start
        long calls = com.antaurora.apofirstlight.world.biome.MainNationBiomeRegionPlan.CALLS.sum();
        long misses = com.antaurora.apofirstlight.world.biome.MainNationBiomeRegionPlan.MISSES.sum();
        long missNs = com.antaurora.apofirstlight.world.biome.MainNationBiomeRegionPlan.MISS_NANOS.sum();
        long rChunks = com.antaurora.apofirstlight.worldgen.terrain.v2.RiverWaterFill.CHUNKS.sum();
        long rNs = com.antaurora.apofirstlight.worldgen.terrain.v2.RiverWaterFill.NANOS.sum();
        String perf = String.format("Cost since start: biome plan %d calls, %d evaluated (%.1f%% cache hits), %.1f us per evaluation; river / pool pass %d chunks, %.2f ms per chunk",
                calls, misses, calls > 0 ? 100.0 * (calls - misses) / calls : 0, misses > 0 ? missNs / 1000.0 / misses : 0,
                rChunks, rChunks > 0 ? rNs / 1e6 / rChunks : 0);
        c.getSource().sendSuccess(() -> Component.literal(perf), false);
        return 1;
    }

    private static int export(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        TerrainPlanSurface plan = plan(level);
        if (plan == null) { c.getSource().sendFailure(Component.literal("No Terrain V2 plan in this world")); return 0; }
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        MinecraftServer server = c.getSource().getServer();
        CommandSourceStack source = c.getSource();
        Path dir = FMLPaths.GAMEDIR.get().resolve("afl_debug").resolve("terrain_v2")
                .resolve(plan.version + "_" + Long.toUnsignedString(plan.seed));
        source.sendSuccess(() -> Component.literal("Terrain V2 export started (about 10 s, off the server thread)"), false);
        CompletableFuture.runAsync(() -> {
            String msg;
            try {
                Files.createDirectories(dir);
                double ext = TerrainPlanSurface.N * TerrainPlanSurface.CELL, mpp = 8;
                int n = (int) (ext / mpp);
                BufferedImage nat = TerrainPlanMaps.render(plan, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, n, n, mpp);
                List<TerrainV2TestPoints.Point> pts = TerrainV2TestPoints.rivers(plan);
                for (TerrainV2TestPoints.Point pt : pts) TerrainPlanMaps.mark(nat, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, mpp, pt.x(), pt.z(), 0xFFE0402A);
                TerrainPlanMaps.mark(nat, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, mpp, pos.getX(), pos.getZ(), 0xFFFFFFFF);
                ImageIO.write(nat, "png", dir.resolve("national_8m.png").toFile());
                BufferedImage win = TerrainPlanMaps.render(plan, pos.getX() - 256, pos.getZ() - 256, 512, 512, 1.0);
                TerrainPlanMaps.mark(win, pos.getX() - 256, pos.getZ() - 256, 1.0, pos.getX(), pos.getZ(), 0xFFFFFFFF);
                String name = "window_" + pos.getX() + "_" + pos.getZ() + "_1m.png";
                ImageIO.write(win, "png", dir.resolve(name).toFile());
                BufferedImage eco = TerrainPlanMaps.renderEcology(plan, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, n, n, mpp);
                List<TerrainV2TestPoints.Point> ecoPts = TerrainV2TestPoints.ecology(plan);
                for (TerrainV2TestPoints.Point pt : ecoPts) TerrainPlanMaps.mark(eco, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, mpp, pt.x(), pt.z(), 0xFFE0402A);
                TerrainPlanMaps.mark(eco, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, mpp, pos.getX(), pos.getZ(), 0xFFFFFFFF);
                ImageIO.write(eco, "png", dir.resolve("ecology_8m.png").toFile());
                BufferedImage ecoWin = TerrainPlanMaps.renderEcology(plan, pos.getX() - 256, pos.getZ() - 256, 512, 512, 1.0);
                TerrainPlanMaps.mark(ecoWin, pos.getX() - 256, pos.getZ() - 256, 1.0, pos.getX(), pos.getZ(), 0xFFFFFFFF);
                ImageIO.write(ecoWin, "png", dir.resolve("ecology_" + pos.getX() + "_" + pos.getZ() + "_1m.png").toFile());
                pts = new java.util.ArrayList<>(pts);
                pts.addAll(ecoPts);
                StringBuilder txt = new StringBuilder("Terrain V2 " + plan.version + " seed " + plan.seed + "\n");
                txt.append("national_8m.png: x/z ").append((int) TerrainPlanSurface.ORIGIN).append(" .. ").append((int) (TerrainPlanSurface.ORIGIN + ext))
                        .append(", 8 m per pixel, north up; white = the player, red = river acceptance points\n");
                txt.append(name).append(": 512 m round the player at 1 m per pixel (river water and marsh pools column by column)\n");
                txt.append("ecology_8m.png / ecology_<x>_<z>_1m.png: the natural biomes (colours in docs/worldgen/terrain_v2_ecology_v1.md), red = ecology points\n");
                txt.append("rivers: ").append(plan.rivers == null ? 0 : plan.rivers.lines()).append(" lines\n");
                for (TerrainV2TestPoints.Point pt : pts) txt.append(pt.name()).append(' ').append(pt.x()).append(' ').append(pt.z()).append(" - ").append(pt.note()).append('\n');
                Files.writeString(dir.resolve("export.txt"), txt);
                msg = "Terrain V2 export written to " + dir.toAbsolutePath();
            } catch (Exception ex) {
                msg = "Terrain V2 export failed: " + ex;
            }
            String done = msg;
            server.execute(() -> source.sendSuccess(() -> Component.literal(done), false));
        }, Util.backgroundExecutor());
        return 1;
    }
}
