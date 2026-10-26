package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.earthwork.RockTypes;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * U3a (docs/worldgen/underground_u3_rocks_ores_design.md 2.2): a deep world's underground made of the geology's rocks.
 * Once the chunk's surface is built (the surface rules replace only the noise's default stone, so the rocks cannot be
 * laid by the noise), every {@code minecraft:stone} and {@code minecraft:deepslate} of the chunk becomes the natural
 * rock GeologyModel#rocks answers at the block's middle. Loose ground in the geology where the surface rules left stone
 * (their soil is thinner than the geology's) takes the column's first rock under the loose ground; a coal seam is the
 * coal seam ore rock (lean; U3b).
 * <p>
 * Rock to rock: both block motion and light alike, so the heightmaps and light stay as they were, and the sections are
 * written straight (no updates). The vanilla rock blobs and ores that come later in the features replace only vanilla
 * stone (base_stone_overworld; the ores' replaceables also take the AFL rocks till U3b): in a deep world the blobs find
 * none. Worlds without a bound underground plan (every world but the deep preset) return at once.
 * <p>
 * The passes that lay stone after the surface as natural ground (a road's rock cut, the home plot's graded fill) call
 * {@link #restone}.
 * <p>
 * U6 V3 (user 2026-10-24, "why is there so much soil underground"): the surface rules lay their soil on every floor
 * under air near the surface, a cave's included, so shallow caves had soil floors and soil walls (cubes beside the worn
 * rock). {@link #unroof}: soil stays only in each column's top run (from its first solid block down to the first air or
 * water under it) and only above the run's first rock; soil anywhere else becomes the column's rock.
 * <p>
 * U3b batch 1 (underground_u3_rocks_ores_design.md 14): {@link #placeOres} last, the plan's ore bodies in this chunk
 * (OreBodies: shape, grade, a vein's metal) turn their rock into ore rock; a coal seam body makes its seam rich coal.
 */
public final class GeologyPass {
    private GeologyPass() {}

    private static volatile BlockState[] byRock;
    private static final AtomicLong CHUNKS = new AtomicLong(), NANOS = new AtomicLong(), UNROOFED = new AtomicLong();
    private static final AtomicLongArray PLACED = new AtomicLongArray(RockType.values().length);

    /** The block of each RockType (by ordinal; null for loose ground: never placed). */
    static BlockState[] states() {
        BlockState[] s = byRock;
        if (s != null) return s;
        s = new BlockState[RockType.values().length];
        for (int i = 0; i < RockTypes.BLOCKS.size(); i++)
            s[RockTypes.BLOCKS.get(i).ordinal()] = AflBlocks.NATURAL_ROCKS.get(i).get().defaultBlockState();
        s[RockType.COAL_SEAM.ordinal()] = AflBlocks.COAL_SEAM.get().defaultBlockState();
        return byRock = s;
    }

    /** The rock type a stone block becomes: loose ground and above the plan the bedrock (a seam is the coal seam, U3b). */
    public static RockType placed(RockType r, RockType bedrock) {
        return r == null || r.soil ? bedrock : r;
    }

    /** The column's first rock under its loose ground (GeologyModel#bedrock, never loose ground). */
    public static RockType bedrock(GeologyModel g, GeologyModel.Column c) {
        RockType r = g.bedrock(c);
        return r == null || r.soil ? RockType.GRANITE : r;
    }

    static boolean stone(Block b) { return b == Blocks.STONE || b == Blocks.DEEPSLATE; }

    /** The deep world's geology for this noise state, or null. */
    static GeologyModel geology(RandomState state) {
        UndergroundRef ref = ((RandomStateSeedAccess) (Object) state).apocalypse$underground();
        UndergroundPlan plan = ref == null ? null : ref.get();
        return plan == null ? null : UndergroundRuntime.geology(plan.seed);
    }

    public static void apply(ChunkAccess chunk, RandomState state) {
        UndergroundRef ref = ((RandomStateSeedAccess) (Object) state).apocalypse$underground();
        UndergroundPlan plan = ref == null ? null : ref.get();
        GeologyModel g = plan == null ? null : UndergroundRuntime.geology(plan.seed);
        if (g == null) return;
        long t0 = System.nanoTime();
        restoneAll(chunk, g);
        unroof(chunk, g);
        placeOres(chunk, plan);
        CHUNKS.incrementAndGet();
        NANOS.addAndGet(System.nanoTime() - t0);
    }

    /** Every vanilla stone and deepslate of the chunk to the geology's rock. */
    private static void restoneAll(ChunkAccess chunk, GeologyModel g) {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinBuildHeight(), lo = -1, hi = -1;
        boolean[] has = new boolean[sections.length];
        for (int s = 0; s < sections.length; s++) {
            if (sections[s].hasOnlyAir() || !sections[s].maybeHas(b -> stone(b.getBlock()))) continue;
            has[s] = true;
            if (lo < 0) lo = s;
            hi = s;
        }
        if (lo < 0) return;
        BlockState[] st = states();
        int y0 = minY + 16 * lo, y1 = minY + 16 * hi + 15;
        RockType[] col = new RockType[y1 - y0 + 1];
        long[] count = new long[RockType.values().length];
        GeologyModel.Column c = new GeologyModel.Column();
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            g.column(bx + lx + 0.5, bz + lz + 0.5, c);
            g.rocks(c, y0, y1, col);
            RockType bed = null;
            for (int s = lo; s <= hi; s++) {
                if (!has[s]) continue;
                LevelChunkSection sec = sections[s];
                int sy = minY + 16 * s;
                for (int ly = 0; ly < 16; ly++) {
                    if (!stone(sec.getBlockState(lx, ly, lz).getBlock())) continue;
                    RockType r = col[sy + ly - y0];
                    if ((r == null || r.soil) && bed == null) bed = bedrock(g, c);
                    r = placed(r, bed);
                    sec.setBlockState(lx, ly, lz, st[r.ordinal()], false);
                    count[r.ordinal()]++;
                }
            }
        }
        for (int i = 0; i < count.length; i++) if (count[i] > 0) PLACED.addAndGet(i, count[i]);
    }

    /** What the surface rules lay (and nothing else): soil, sand, gravel, clay, their stone, terracotta, snow. */
    static boolean soil(Block b) {
        return b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.COARSE_DIRT || b == Blocks.PODZOL || b == Blocks.MYCELIUM
                || b == Blocks.MUD || b == Blocks.CLAY || b == Blocks.GRAVEL || b == Blocks.SAND || b == Blocks.RED_SAND
                || b == Blocks.SANDSTONE || b == Blocks.RED_SANDSTONE || b == Blocks.CALCITE || b == Blocks.SNOW_BLOCK || b == Blocks.POWDER_SNOW
                || b == Blocks.TERRACOTTA || b == Blocks.WHITE_TERRACOTTA || b == Blocks.ORANGE_TERRACOTTA;
    }

    /** Over the ground: air, water and lava, and ice on water (a frozen lake's bed is still its top run). */
    private static boolean overGround(BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty() || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE);
    }

    /**
     * Soil out of every place but a column's top run above its first rock (class comment): under rock, under a cave's air
     * or water, as a cave's roof (the run's last block over air), and beside a cave in the run (an air or water cell next
     * to it in this chunk below that column's first solid block). Only the chunk's own columns are read: soil beside a
     * cave across the chunk border stays.
     */
    static void unroof(ChunkAccess chunk, GeologyModel g) {
        int minY = chunk.getMinBuildHeight(), bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        int[] start = new int[256], end = new int[256];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState[] st = states();
        RockColumn rc = new RockColumn(g, minY);
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int i = lx | lz << 4, x = bx + lx, z = bz + lz, y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
            while (y >= minY && overGround(chunk.getBlockState(p.set(x, y, z)))) y--;
            start[i] = y;
            end[i] = minY;
            rc.reset(x, z);
            boolean capped = false, run = true;
            for (; y >= minY; y--) {
                BlockState s = chunk.getBlockState(p.set(x, y, z));
                if (run && overGround(s)) {
                    run = false;
                    end[i] = y + 1;
                    if (soil(chunk.getBlockState(p.set(x, y + 1, z)).getBlock())) rc.set(chunk, p, st);   // the cave's roof
                    continue;
                }
                Block b = s.getBlock();
                if (run && com.antaurora.apofirstlight.earthwork.Earthwork.rock(b)) capped = true;
                if (soil(b) && (!run || capped)) rc.set(chunk, p.set(x, y, z), st);
            }
        }
        // soil in a top run beside a cave
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int i = lx | lz << 4, x = bx + lx, z = bz + lz;
            rc.reset(x, z);
            for (int y = start[i]; y >= end[i]; y--) {
                if (!soil(chunk.getBlockState(p.set(x, y, z)).getBlock())) continue;
                for (int d = 0; d < 4; d++) {
                    int nx = lx + (d == 0 ? -1 : d == 1 ? 1 : 0), nz = lz + (d == 2 ? -1 : d == 3 ? 1 : 0);
                    if (nx < 0 || nz < 0 || nx > 15 || nz > 15 || start[nx | nz << 4] <= y) continue;
                    if (!overGround(chunk.getBlockState(p.set(bx + nx, y, bz + nz)))) continue;
                    rc.set(chunk, p.set(x, y, z), st);
                    break;
                }
            }
        }
    }

    /** One column's rocks, read from the geology once and only when a block of it is to become rock. */
    private static final class RockColumn {
        final GeologyModel g;
        final int minY;
        final GeologyModel.Column c = new GeologyModel.Column();
        RockType[] col;
        RockType bed;
        int x, z;
        boolean read;

        RockColumn(GeologyModel g, int minY) {
            this.g = g;
            this.minY = minY;
        }

        void reset(int x, int z) {
            this.x = x;
            this.z = z;
            read = false;
        }

        /** The block at p (in this column) becomes its rock. */
        void set(ChunkAccess chunk, BlockPos.MutableBlockPos p, BlockState[] st) {
            int top = chunk.getMaxBuildHeight() - 1;
            if (!read) {
                g.column(x + 0.5, z + 0.5, c);
                if (col == null) col = new RockType[top - minY + 1];
                g.rocks(c, minY, top, col);
                bed = null;
                read = true;
            }
            RockType r = col[p.getY() - minY];
            if ((r == null || r.soil) && bed == null) bed = bedrock(g, c);
            r = placed(r, bed);
            chunk.setBlockState(p, st[r.ordinal()], false);
            UNROOFED.incrementAndGet();
        }
    }

    /**
     * In a deep world, the vanilla stone a later pass laid as natural ground in column (x, z) from y1 down to y0 (a road's
     * rock cut, the home plot's fill) becomes the geology's rock there; nothing elsewhere.
     */
    public static void restone(WorldGenLevel level, ChunkAccess chunk, int x, int z, int y0, int y1) {
        GeologyModel g = geology(level.getLevel().getChunkSource().randomState());
        if (g == null) return;
        y0 = Math.max(y0, chunk.getMinBuildHeight());
        y1 = Math.min(y1, chunk.getMaxBuildHeight() - 1);
        if (y1 < y0) return;
        GeologyModel.Column c = g.column(x + 0.5, z + 0.5);
        RockType[] col = new RockType[y1 - y0 + 1];
        g.rocks(c, y0, y1, col);
        BlockState[] st = states();
        RockType bed = null;
        net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            if (!stone(chunk.getBlockState(p.set(x, y, z)).getBlock())) continue;
            RockType r = col[y - y0];
            if ((r == null || r.soil) && bed == null) bed = bedrock(g, c);
            chunk.setBlockState(p, st[placed(r, bed).ordinal()], false);
        }
    }

    private static final AtomicLongArray ORES = new AtomicLongArray(UndergroundPlan.OreKind.values().length * 2);
    private static volatile BlockState[][] oreStates;

    /** The ore rock of a kind by grade {lean, rich} (a vein: by metal, three pairs in a row; null: not batch 1). */
    private static BlockState[][] oreStates() {
        BlockState[][] s = oreStates;
        if (s != null) return s;
        s = new BlockState[UndergroundPlan.OreKind.values().length][];
        var G = com.antaurora.apofirstlight.block.OreRockBlock.GRADE;
        var lean = com.antaurora.apofirstlight.block.OreRockBlock.Grade.LEAN;
        var rich = com.antaurora.apofirstlight.block.OreRockBlock.Grade.RICH;
        java.util.function.Function<Block, BlockState[]> pair = b -> new BlockState[]{b.defaultBlockState().setValue(G, lean), b.defaultBlockState().setValue(G, rich)};
        s[UndergroundPlan.OreKind.COAL_SEAM.ordinal()] = pair.apply(AflBlocks.COAL_SEAM.get());
        s[UndergroundPlan.OreKind.BANDED_IRON.ordinal()] = pair.apply(AflBlocks.BANDED_IRON_FORMATION.get());
        s[UndergroundPlan.OreKind.MVT_LEAD_ZINC.ordinal()] = pair.apply(AflBlocks.LEAD_ZINC_DOLOMITE.get());
        s[UndergroundPlan.OreKind.BRECCIA_PIPE.ordinal()] = pair.apply(AflBlocks.COPPER_BRECCIA.get());
        var metal = com.antaurora.apofirstlight.block.QuartzVeinBlock.METAL;
        var metals = com.antaurora.apofirstlight.block.QuartzVeinBlock.Metal.values();
        BlockState[] vein = new BlockState[6];
        for (int m = 0; m < 3; m++) {
            BlockState b = AflBlocks.QUARTZ_VEIN.get().defaultBlockState().setValue(metal, metals[m]);
            vein[m * 2] = b.setValue(G, lean);
            vein[m * 2 + 1] = b.setValue(G, rich);
        }
        s[UndergroundPlan.OreKind.HYDROTHERMAL_VEIN.ordinal()] = vein;
        return oreStates = s;
    }

    /** Rock (not ore, soil, air or water) an ore body may take. */
    private static boolean host(BlockState s) {
        Block b = s.getBlock();
        return b instanceof com.antaurora.apofirstlight.block.NaturalRockBlock && !(b instanceof com.antaurora.apofirstlight.block.OreRockBlock);
    }

    /** The plan's ore bodies reaching into this chunk lay their ore rock (class comment; OreBodies). */
    static void placeOres(ChunkAccess chunk, UndergroundPlan plan) {
        UndergroundPlan.OreBody[] bodies = plan.oresIn(chunk.getPos().x, chunk.getPos().z);
        if (bodies.length == 0) return;
        BlockState[][] os = oreStates();
        Block coal = AflBlocks.COAL_SEAM.get();
        BlockState richCoal = os[UndergroundPlan.OreKind.COAL_SEAM.ordinal()][1];
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight() - 1;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (UndergroundPlan.OreBody o : bodies) {
            int[] yr = OreBodies.yRange(o);
            int y0 = Math.max(minY, yr[0]), y1 = Math.min(maxY, yr[1]);
            if (y1 < y0) continue;
            int k = o.kind().ordinal();
            BlockState[] st = os[k];
            int metal = o.kind() == UndergroundPlan.OreKind.HYDROTHERMAL_VEIN ? OreBodies.veinMetal(plan.seed, o.district()) : 0;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                int x = bx + lx, z = bz + lz;
                if (o.kind() == UndergroundPlan.OreKind.COAL_SEAM) {
                    if (!OreBodies.inSeam(o, plan.seed, x, z)) continue;
                    for (int y = y0; y <= y1; y++) {
                        if (chunk.getBlockState(p.set(x, y, z)).getBlock() != coal) continue;
                        chunk.setBlockState(p, richCoal, false);
                        ORES.incrementAndGet(k * 2 + 1);
                    }
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    int gr = OreBodies.grade(o, plan.seed, x, y, z);
                    if (gr < 0 || !host(chunk.getBlockState(p.set(x, y, z)))) continue;
                    chunk.setBlockState(p, st[metal * 2 + gr], false);
                    ORES.incrementAndGet(k * 2 + gr);
                }
            }
        }
    }

    /** For /afl dev underground: chunks done, their mean time, the rocks laid. */
    public static String report() {
        long n = CHUNKS.get();
        if (n == 0) return "geology pass: no chunk yet";
        StringBuilder sb = new StringBuilder(String.format(java.util.Locale.ROOT, "geology pass: %d chunks, %.2f ms mean, %d soil blocks off the top run made rock;",
                n, NANOS.get() / 1e6 / n, UNROOFED.get()));
        long all = 0;
        for (int i = 0; i < PLACED.length(); i++) all += PLACED.get(i);
        for (RockType r : RockTypes.BLOCKS) {
            long k = PLACED.get(r.ordinal());
            if (k > 0) sb.append(String.format(java.util.Locale.ROOT, " %s %.1f%%", r.id, 100.0 * k / all));
        }
        long seam = PLACED.get(RockType.COAL_SEAM.ordinal());
        if (seam > 0) sb.append(String.format(java.util.Locale.ROOT, " coal seam %.2f%%", 100.0 * seam / all));
        sb.append("; ore rock (lean/rich):");
        for (UndergroundPlan.OreKind kind : UndergroundPlan.OreKind.values()) {
            long a = ORES.get(kind.ordinal() * 2), b = ORES.get(kind.ordinal() * 2 + 1);
            if (a + b > 0) sb.append(' ').append(kind.name().toLowerCase(java.util.Locale.ROOT)).append(' ').append(a).append('/').append(b);
        }
        return sb.toString();
    }
}
