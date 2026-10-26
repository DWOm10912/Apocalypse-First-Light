package com.antaurora.apofirstlight.worldgen.underground;

import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import com.antaurora.apofirstlight.boulder.BoulderBlocks;
import com.antaurora.apofirstlight.boulder.LooseStones;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.network.RoadChunkPass;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelFixtures;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

/**
 * U5b (docs/worldgen/underground_geology_caves_v0_design.md 24.5): a chunk's share of the cave trails, at the end of
 * its feature step (ChunkGeneratorCaveTrailMixin: after the plants and the forest), on its own columns only:
 * <ul>
 *   <li>the tread: within CaveTrail.TREAD of the line the turf (grass, podzol, moss, mycelium, dirt) is worn to coarse
 *   dirt, a quarter of it to dirt; out to CaveTrail.VERGE it frays (more turf the further out, in small patches); the
 *   plants on it are taken off. Rock, sand, gravel, snow and water stay as they are; a road's own columns
 *   (RoadChunkPass) are not touched;</li>
 *   <li>the trailhead sign, the caution sign before the mouth and the marker posts (road fixtures, TunnelFixtureBlock) stood on the ground the noise made
 *   (OCEAN_FLOOR_WG, the lower post's foot for the sign), every cell open; a part whose cells are not all open (a
 *   boulder, a log) is left out.</li>
 * </ul>
 * The plan keeps each fixture in one chunk, so the result never depends on the order chunks generate in. Plants that
 * a neighbour's features would write on the tread later are refused (#refused, WorldGenRegionCaveTrailGuardMixin); the
 * forest keeps its trunks off the line (UndergroundRuntime's ForestPlan exclusion).
 */
public final class CaveTrailPass {
    private CaveTrailPass() {}

    static final LongAdder CHUNKS = new LongAdder(), TREAD = new LongAdder(), PARTS = new LongAdder(), PARTS_REFUSED = new LongAdder();
    private static final Set<Block> TURF = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MOSS_BLOCK, Blocks.MYCELIUM, Blocks.DIRT, Blocks.ROOTED_DIRT);

    /** For /afl dev underground. */
    static String report() {
        return String.format(java.util.Locale.ROOT, "cave trails: %d chunks, %d tread columns, %d fixtures, %d fixtures refused",
                CHUNKS.sum(), TREAD.sum(), PARTS.sum(), PARTS_REFUSED.sum());
    }

    static UndergroundPlan plan(WorldGenLevel level) {
        UndergroundRef ref = ((RandomStateSeedAccess) (Object) level.getLevel().getChunkSource().randomState()).apocalypse$underground();
        UndergroundPlan plan = ref == null ? null : ref.get();
        return plan == null || plan.trails.isEmpty() ? null : plan;
    }

    public static void apply(WorldGenLevel level, ChunkAccess chunk) {
        UndergroundPlan plan = plan(level);
        if (plan == null) return;
        ChunkPos cp = chunk.getPos();
        int[] segs = plan.trailSegments(cp.x, cp.z);
        List<TunnelMesh.Placement> parts = plan.trailParts(cp.x, cp.z);
        if (segs.length == 0 && parts.isEmpty()) return;
        CHUNKS.increment();
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level.getLevel());
        RoadWorks.Column[] road = n == null ? null : RoadChunkPass.columns(n, cp.x, cp.z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        if (segs.length > 0)
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                if (road != null && road[lx | lz << 4].kind() != RoadWorks.Kind.NONE) continue;
                int x = cp.getMinBlockX() + lx, z = cp.getMinBlockZ() + lz;
                double[] near = plan.trailNear(x + 0.5, z + 0.5);
                if (near[0] > CaveTrail.VERGE) continue;
                if (tread(chunk, p, x, z, lx, lz, near[0], near[1])) TREAD.increment();
            }
        for (TunnelMesh.Placement pl : parts) {
            if (stand(chunk, p, pl)) PARTS.increment();
            else PARTS_REFUSED.increment();
        }
    }

    /** A column of the tread or its verge; true when its ground was worn. */
    private static boolean tread(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int z, int lx, int lz, double d, double along) {
        int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz);
        BlockState top = chunk.getBlockState(p.set(x, y, z));
        if (!TURF.contains(top.getBlock())) return false;
        // the plants over the tread and the inner verge (both halves of a tall one)
        if (d <= CaveTrail.VERGE - 0.2)
            for (int k = 1; k <= 2; k++) {
                BlockState s = chunk.getBlockState(p.set(x, y + k, z));
                if (plant(s)) chunk.setBlockState(p, Blocks.AIR.defaultBlockState(), false);
            }
        // worn all across the tread, then fewer columns toward the verge's edge, in small patches (a 3 m noise and a column hash)
        double u = hash(x, z), patch = 0.5 + 0.5 * Math.sin(along * 2.1 + x * 0.37) * Math.cos(along * 1.3 - z * 0.41);
        boolean worn = d <= CaveTrail.TREAD || (d - CaveTrail.TREAD) / (CaveTrail.VERGE - CaveTrail.TREAD) < 0.35 + 0.45 * patch - 0.25 * u;
        if (!worn) return false;
        Block b = u < 0.25 && d <= CaveTrail.TREAD ? Blocks.DIRT : Blocks.COARSE_DIRT;
        chunk.setBlockState(p.set(x, y, z), b.defaultBlockState(), false);
        return true;
    }

    static boolean plant(BlockState s) {
        Block b = s.getBlock();
        return b instanceof BushBlock || b instanceof DoublePlantBlock || (s.canBeReplaced() && !s.isAir() && s.getFluidState().isEmpty() && !s.is(Blocks.SNOW));
    }

    private static double hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** A fixture stood on the ground: its feet's columns' ground, every cell open; false when it was left out. */
    private static boolean stand(ChunkAccess chunk, BlockPos.MutableBlockPos p, TunnelMesh.Placement pl) {
        boolean sign = CaveTrail.SIGN.equals(pl.mesh()) || CaveTrail.CAUTION.equals(pl.mesh());
        double[][] feet = sign ? new double[][]{{-CaveTrail.SIGN_POST_X, -0.065}, {CaveTrail.SIGN_POST_X, -0.065}} : new double[][]{{0, 0}};
        int foot = Integer.MAX_VALUE;
        for (double[] f : feet) {
            int x = (int) Math.floor(pl.x() + pl.ex()[0] * f[0] + pl.ez()[0] * f[1]), z = (int) Math.floor(pl.z() + pl.ex()[2] * f[0] + pl.ez()[2] * f[1]);
            if (x >> 4 != chunk.getPos().x || z >> 4 != chunk.getPos().z) return false;
            int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
            BlockState g = chunk.getBlockState(p.set(x, y, z));
            if (!g.getFluidState().isEmpty() || !chunk.getFluidState(p.set(x, y + 1, z)).isEmpty() || !ground(g)) return false;
            foot = Math.min(foot, y + 1);
        }
        TunnelMesh.Placement at = new TunnelMesh.Placement(pl.mesh(), pl.bone(), pl.x(), foot, pl.z(), pl.ex(), pl.ey(), pl.ez());
        TunnelFixtureBlock.Kind kind = TunnelFixtureBlock.Kind.of(at.mesh());
        if (kind == null) return false;
        long[] cells = TunnelFixtures.Part.of(at).cells();
        for (long c : cells) {
            if (BlockPos.getX(c) >> 4 != chunk.getPos().x || BlockPos.getZ(c) >> 4 != chunk.getPos().z) return false;
            if (BlockPos.getY(c) < chunk.getMinBuildHeight() || BlockPos.getY(c) >= chunk.getMaxBuildHeight()) return false;
            BlockState old = chunk.getBlockState(p.set(BlockPos.getX(c), BlockPos.getY(c), BlockPos.getZ(c)));
            if (!(old.isAir() || plant(old))) return false;
        }
        BlockState fixture = AflBlocks.TUNNEL_FIXTURES.get(kind).get().defaultBlockState();
        TunnelFixtureBlockEntity.Entry entry = new TunnelFixtureBlockEntity.Entry(kind, at);
        for (long c : cells) {
            p.set(BlockPos.getX(c), BlockPos.getY(c), BlockPos.getZ(c));
            chunk.setBlockState(p, fixture, false);
            TunnelFixtureBlockEntity be = new TunnelFixtureBlockEntity(p.immutable(), fixture);
            be.set(List.of(entry), 0);
            chunk.setBlockEntity(be);
        }
        return true;
    }

    /** Natural ground a post stands in: solid, not a tree, a boulder or loose stones. */
    private static boolean ground(BlockState s) {
        return s.blocksMotion() && !s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !BoulderBlocks.isBoulder(s) && !LooseStones.is(s)
                && !(s.getBlock() instanceof TunnelFixtureBlock);
    }

    /**
     * Whether a generation write must be refused: a plant (BushBlock, a tall plant) on a trail's tread (within
     * CaveTrail.TREAD + 0.3 of its line), whichever chunk's features write it.
     */
    public static boolean refused(WorldGenLevel level, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        if (!(b instanceof BushBlock) && !(b instanceof DoublePlantBlock)) return false;
        UndergroundPlan plan = plan(level);
        if (plan == null) return false;
        return plan.trailNear(pos.getX() + 0.5, pos.getZ() + 0.5)[0] <= CaveTrail.TREAD + 0.3;
    }
}
