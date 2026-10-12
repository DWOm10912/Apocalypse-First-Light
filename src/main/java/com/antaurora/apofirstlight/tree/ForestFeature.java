package com.antaurora.apofirstlight.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import com.antaurora.apofirstlight.worldgen.terrain.v2.EcologyBiomes;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Mesh Trees P1 (docs/rendering/mesh_trees_p1_forest.md, E/F): the natural forest feature of a forest test world, the
 * last VEGETAL_DECORATION feature of the covered biomes (ForestGate's biome modifier puts it there and takes their
 * vanilla tree features out). Per chunk it plants the trees ForestPlan roots in that chunk:
 * <ol>
 * <li>Ground before features: the WG heightmaps (they stop at the carved terrain, so every chunk that asks gets the same
 * answer). The root goes on the first open y; water on the ground, or within two blocks, refuses the tree.</li>
 * <li>The ground under it: soil (the dirt tag, sand on the coastal plain; not mud); within one block the ground differs
 * by at most one, within two by at most two (no slopes too steep for the trunk, no undercut banks).</li>
 * <li>No structure's bounding box within the tree's reach.</li>
 * <li>Cells: written into air or a replaceable dry plant; a cell another mesh tree wrote is taken by the higher rank
 * (ForestPlan#wins: root, wood, leaf-carrying crown, volume-only crown; then the lower species), so a shared cell ends
 * the same in any generation order; a trunk or branch cell that meets anything else (terrain, a vanilla tree, a
 * building) refuses the whole tree before a block is written; such a crown cell is left out.</li>
 * </ol>
 * The root is written last. Every cell lies within {@link ForestPlan#REACH} blocks of the root, inside the 3 x 3 chunks
 * the FEATURES stage may write. No chunk is loaded, no neighbour is read beyond them.
 */
final class ForestFeature extends Feature<NoneFeatureConfiguration> {
    static final LongAdder CHUNKS = new LongAdder(), PLANNED = new LongAdder(), PLACED = new LongAdder(), CELLS = new LongAdder(),
            SHARED = new LongAdder(), LOST = new LongAdder(), SKIPPED = new LongAdder(), NANOS = new LongAdder(), NO_PLAN = new LongAdder();
    static final Map<String, LongAdder> REFUSED = new ConcurrentHashMap<>();

    ForestFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    static void reset() {
        for (LongAdder a : new LongAdder[]{CHUNKS, PLANNED, PLACED, CELLS, SHARED, LOST, SKIPPED, NANOS, NO_PLAN}) a.reset();
        REFUSED.clear();
    }

    static Map<String, Long> refusals() {
        var out = new TreeMap<String, Long>();
        REFUSED.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ForestPlan plan = ForestGate.plan(level);
        if (plan == null) return false;
        long t0 = System.nanoTime();
        ChunkPos chunk = new ChunkPos(context.origin());
        List<ForestPlan.Tree> trees = plan.trees(chunk.x, chunk.z);
        int placed = 0;
        for (var tree : trees) {
            String why = plant(level, chunk, tree);
            if (why == null) placed++;
            else REFUSED.computeIfAbsent(why, k -> new LongAdder()).increment();
        }
        CHUNKS.increment();
        PLANNED.add(trees.size());
        PLACED.add(placed);
        NANOS.add(System.nanoTime() - t0);
        return placed > 0;
    }

    /** Plants one tree; null when it stands, otherwise why not. */
    static String plant(WorldGenLevel level, ChunkPos chunk, ForestPlan.Tree t) {
        int x = t.x(), z = t.z();
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        if (level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) > floor) return "water on the ground";
        BlockPos root = new BlockPos(x, floor, z);
        BlockState ground = level.getBlockState(root.below());
        if (!soil(ground, t.biome())) return "not soil (" + BuiltInRegistries.BLOCK.getKey(ground.getBlock()).getPath() + ")";
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            if (dx == 0 && dz == 0) continue;
            int f = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + dx, z + dz);
            if (level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + dx, z + dz) > f) return "water within 2 blocks";
            int ring = Math.max(Math.abs(dx), Math.abs(dz));
            if (Math.abs(f - floor) > ring) return "ground too steep at the trunk";
        }
        if (structure(level, x, floor, z)) return "structure";

        var layout = TreeShape.layout(t.species(), t.variant(), t.turns());
        BlockState rootState = TreeBlocks.rootState(t.species(), t.variant(), t.turns());
        var cells = TreeShape.ordered(layout);
        BlockState[] write = new BlockState[cells.size()];
        int shared = 0, lost = 0, skipped = 0, sp = t.species().ordinal(), rootIndex = -1;
        var p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < cells.size(); i++) {
            var c = cells.get(i);
            if (c.x == 0 && c.y == 0 && c.z == 0) rootIndex = i;
            p.set(x + c.x, floor + c.y, z + c.z);
            boolean wood = c.kind == TreeShape.WOOD;
            if (level.isOutsideBuildHeight(p)) { if (wood) return "build height"; skipped++; continue; }
            // REACH keeps every cell in the 3 x 3 chunks round the root (the offline check holds the layouts to it)
            if (Math.abs((p.getX() >> 4) - chunk.x) > 1 || Math.abs((p.getZ() >> 4) - chunk.z) > 1) return "outside the writable chunks";
            BlockState was = level.getBlockState(p);
            if (open(was)) { write[i] = TreeBlocks.stateFor(c, rootState); continue; }
            if (TreeBlocks.isTree(was)) {
                int mine = ForestPlan.rank(c), theirs = rank(was);
                if (ForestPlan.wins(mine, sp, theirs, species(was))) {
                    write[i] = TreeBlocks.stateFor(c, rootState);
                    if (theirs == 2 && mine >= 3) lost++;          // their leaves there give way to this wood
                } else if (mine == 2 && theirs >= 3) lost++;       // these leaves give way to their wood
                shared++;
                continue;
            }
            if (wood) return "trunk or branch blocked (" + BuiltInRegistries.BLOCK.getKey(was.getBlock()).getPath() + ")";
            skipped++;
        }
        // without its root the cells would draw nothing (two roots never share a cell: spacing)
        if (rootIndex < 0 || write[rootIndex] == null) return "root cell taken";
        int written = 0;
        for (int i = 0; i < cells.size(); i++) {
            if (write[i] == null || i == rootIndex) continue;
            var c = cells.get(i);
            level.setBlock(p.set(x + c.x, floor + c.y, z + c.z), write[i], Block.UPDATE_CLIENTS);
            written++;
        }
        level.setBlock(root, write[rootIndex], Block.UPDATE_CLIENTS);
        written++;
        CELLS.add(written);
        SHARED.add(shared);
        LOST.add(lost);
        SKIPPED.add(skipped);
        return null;
    }

    /** Air, or a dry plant / snow cover the tree may replace (never water: a crown cell would drain it). */
    static boolean open(BlockState s) {
        if (s.isAir()) return true;
        return s.getFluidState().isEmpty() && (s.canBeReplaced() || s.getBlock() instanceof BushBlock);
    }

    static boolean soil(BlockState g, int biome) {
        if (g.is(BlockTags.DIRT)) return !g.is(Blocks.MUD) && !g.is(Blocks.MUDDY_MANGROVE_ROOTS);
        return g.is(Blocks.SAND) && (biome == EcologyBiomes.COASTAL_PINE || biome == EcologyBiomes.COASTAL_CLEARING);
    }

    /** The rank of a cell another tree wrote (ForestPlan#rank). */
    static int rank(BlockState s) {
        Block b = s.getBlock();
        if (b == TreeBlocks.root()) return 4;
        if (b == TreeBlocks.wood()) return 3;
        return b instanceof TreeBlocks.Foliage f && f.carrier ? 2 : 1;
    }

    static int species(BlockState s) {
        return s.getBlock() instanceof TreeBlocks.Foliage f ? f.species.ordinal() : 0;
    }

    /** A structure piece's box (any structure started within the region) meets the tree's reach. */
    static boolean structure(WorldGenLevel level, int x, int y, int z) {
        int r = ForestPlan.REACH;
        var box = new BoundingBox(x - r, y - 2, z - r, x + r, y + 32, z + r);
        for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++) for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++) {
            ChunkAccess c = level.getChunk(cx, cz, ChunkStatus.STRUCTURE_REFERENCES, false);
            if (c == null) continue;
            for (var e : c.getAllReferences().entrySet()) for (long start : e.getValue()) {
                ChunkAccess sc = level.getChunk(ChunkPos.getX(start), ChunkPos.getZ(start), ChunkStatus.STRUCTURE_STARTS, false);
                // a start outside this region (more than 8 chunks off) is not readable; such a structure is large and
                // underground in practice, and the cells never replace a solid block anyway
                if (sc == null) continue;
                StructureStart s = sc.getStartForStructure(e.getKey());
                if (s != null && s.isValid() && s.getBoundingBox().intersects(box)) return true;
            }
        }
        return false;
    }
}
