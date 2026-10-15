package com.antaurora.apofirstlight.worldgen.network;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.road.BridgeMesh;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks.Column;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The road's chunk pass (C3.2; docs/worldgen/federal_highways_c3.md, 10.4): at the start of a chunk's feature step
 * (ChunkGenerator.applyBiomeDecoration, before structures and features), every road column of the chunk is finished
 * exactly as RoadWorks.column says, writing into this chunk only:
 * <ul>
 *   <li>PAVED: collision (road_surface, 1/32 steps, up to two blocks), asphalt, gravel, a cobblestone sub-base (no ore
 *   rule replaces it: ores write past the guard, through BulkSectionAccess), then dirt down to the
 *   natural ground;</li>
 *   <li>TURF: one graded_turf block on the shared corners, dirt below;</li>
 *   <li>DECK / ABUTMENT: collision on road_structure (the deck down to its slab's or girder's underside), the abutment
 *   wall down to the ground; structure never replaces natural ground (it only fills above it);</li>
 *   <li>BARRIER: road_structure for the parapets (the top block cut to the parapet's height), and at the span's ends the
 *   parapets and wing walls on the actual ground (a wing wall is left out where that ground already reaches its top);</li>
 *   <li>the bridge's look is the client's mesh (BridgeMesh): road_structure is invisible collision that holds light;</li>
 *   <li>OPEN: beside and between the decks, the natural ground as the noise made it (only what rises above the deck level
 *   is cleared; water untouched).</li>
 * </ul>
 * Everything above a finished column is cleared up to the chunk's surface. Later writes into a road column at or above
 * its guard height (trees, snow, structure pieces from this or a neighbouring chunk) are refused by
 * WorldGenRegionRoadGuardMixin, so the result never depends on the order chunks generate in.
 */
public final class RoadChunkPass {
    private RoadChunkPass() {}

    private static final int MAX_FILL = 64;
    private static final int CACHE = 1024;
    /** Columns per chunk, shared by the pass and the guard (bounded; recomputed when evicted: a pure function). */
    private static final Map<RoadNetworkRuntime.Network, Map<Long, Column[]>> COLUMNS = new java.util.WeakHashMap<>();

    /** The 256 finished columns of a chunk (index lx | lz << 4), or null when no road reaches it. */
    public static Column[] columns(RoadNetworkRuntime.Network n, int cx, int cz) {
        if (!n.works().touches(cx, cz)) return null;
        long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
        Map<Long, Column[]> m;
        synchronized (COLUMNS) {
            m = COLUMNS.computeIfAbsent(n, k -> new LinkedHashMap<>(256, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<Long, Column[]> e) { return size() > CACHE; }
            });
            Column[] c = m.get(key);
            if (c != null) return c == EMPTY ? null : c;
        }
        RoadWorks.Ground ground = n.plan()::heightAt;
        Column[] out = new Column[256];
        boolean any = false;
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            Column c = n.works().column(cx * 16 + lx, cz * 16 + lz, ground);
            out[lx | lz << 4] = c;
            any |= c.kind() != RoadWorks.Kind.NONE;
        }
        if (!any) out = EMPTY;
        synchronized (COLUMNS) { m.put(key, out); }
        return out == EMPTY ? null : out;
    }

    private static final Column[] EMPTY = new Column[0];

    /** The lowest Y the guard protects in a finished column (Integer.MAX_VALUE: none). */
    public static int guard(Column c) {
        return switch (c.kind()) {
            case NONE -> Integer.MAX_VALUE;
            case PAVED -> c.y() - 3;
            case TURF -> c.y();
            case DECK, ABUTMENT, BARRIER -> c.bottom();
            case OPEN -> c.y() - 3;
        };
    }

    public static void apply(WorldGenLevel level, ChunkAccess chunk) {
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level.getLevel());
        if (n == null) return;
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        Column[] cols = columns(n, cx, cz);
        if (cols == null) return;
        BlockState dirt = Blocks.DIRT.defaultBlockState(), base = Blocks.COBBLESTONE.defaultBlockState(),
                gravel = Blocks.GRAVEL.defaultBlockState(), asphalt = AflBlocks.ASPHALT.get().defaultBlockState(),
                structure = AflBlocks.ROAD_STRUCTURE.get().defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            Column c = cols[lx | lz << 4];
            if (c.kind() == RoadWorks.Kind.NONE) continue;
            int x = cx * 16 + lx, z = cz * 16 + lz;
            int surf = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
            double g = n.plan().heightAt(x + 0.5, z + 0.5);
            int ground = (int) Math.floor(g);
            switch (c.kind()) {
                case PAVED -> {
                    collision(chunk, p, x, z, c);
                    set(chunk, p, x, c.y() - 1, z, asphalt);
                    set(chunk, p, x, c.y() - 2, z, gravel);
                    set(chunk, p, x, c.y() - 3, z, base);
                    fill(chunk, p, x, c.y() - 4, z, dirt, ground - 2);
                    clear(chunk, p, x, c.top() + 1, surf + 1, z, true);
                }
                case TURF -> {
                    int d = c.data();
                    set(chunk, p, x, c.y(), z, ((GradedTurfBlock) AflBlocks.GRADED_TURF.get()).with(d & 15, d >> 4 & 15, d >> 8 & 15, d >> 12 & 15, (d >> 16 & 1) != 0));
                    fill(chunk, p, x, c.y() - 1, z, dirt, ground - 2);
                    clear(chunk, p, x, c.y() + 1, surf + 1, z, true);
                }
                case DECK, ABUTMENT -> {
                    int solid = solidTop(chunk, lx, lz);
                    collision(chunk, p, x, z, c);
                    // the structure never replaces the natural ground (a cell's square reaches beyond the mesh: through the
                    // invisible block the ground must still be there); the abutment stands on it
                    for (int y = Math.max(c.bottom(), solid + 1); y < c.y(); y++) set(chunk, p, x, y, z, structure);
                    if (c.kind() == RoadWorks.Kind.ABUTMENT) fill(chunk, p, x, c.bottom() - 1, z, structure, Math.min(ground, solid) - 2);
                    clear(chunk, p, x, c.top() + 1, surf + 1, z, true);
                }
                case BARRIER -> {
                    int solid = solidTop(chunk, lx, lz);
                    // a wing wall where the actual ground already reaches its top is left out (the client finds no structure
                    // there and closes the wall beside it)
                    if (c.data() == BridgeMesh.WING && solid >= c.y()) break;
                    for (int y = Math.max(c.bottom(), solid + 1); y < c.y(); y++) set(chunk, p, x, y, z, structure);
                    set(chunk, p, x, c.y(), z, structure.setValue(RoadStructureBlock.LAYERS, c.layers()));
                    if (c.bottom() <= Math.max(ground, solid) + 1) fill(chunk, p, x, c.bottom() - 1, z, structure, Math.min(ground, solid) - 2);
                    clear(chunk, p, x, c.y() + 1, surf + 1, z, true);
                }
                // beside and between the decks: the natural ground stays (cut to the planned ground it left bare stone and
                // dirt pits in front of the walls, 2026-10-15); only what rises above the deck level goes
                case OPEN -> clear(chunk, p, x, c.y() + 1, surf + 1, z, false);
                default -> { }
            }
        }
    }

    /**
     * The top solid (non-fluid) block of a column as the noise made it: the actual ground, under water its bed.
     * ChunkAccess#getHeight is that block's y already (the heightmap's first free cell less one); one less again put the
     * structure on the ground's top block, so a band cell's grass gave way to a bare dirt pit wherever the mesh does not
     * cover the cell (2026-10-15, surface4).
     */
    private static int solidTop(ChunkAccess chunk, int lx, int lz) {
        return chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, lx, lz);
    }

    private static void collision(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int z, Column c) {
        Block b = AflBlocks.ROAD_SURFACE.get();
        set(chunk, p, x, c.y(), z, b.defaultBlockState().setValue(RoadSurfaceBlock.LAYERS, c.layers()));
        if (c.upper() > 0) set(chunk, p, x, c.y() + 1, z, b.defaultBlockState().setValue(RoadSurfaceBlock.LAYERS, c.upper()));
    }

    private static void set(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState s) {
        if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) return;
        chunk.setBlockState(p.set(x, y, z), s, false);
    }

    /** Ground blocks that stop a fill (and grass-covered ones become dirt when buried). */
    private static final Set<Block> GRASSY = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK);

    /**
     * From y down: replace what is not solid ground (air, plants, fluids of the fill area) until the natural ground, but
     * never below lowest (2 blocks under the planned ground): a cave the carvers opened under the road stays a cave.
     */
    private static void fill(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState with, int lowest) {
        lowest = Math.max(lowest, chunk.getMinBuildHeight());
        for (int n = 0; n < MAX_FILL && y >= lowest; n++, y--) {
            BlockState s = chunk.getBlockState(p.set(x, y, z));
            if (s.getFluidState().isEmpty() && s.isSolid() && !s.is(net.minecraft.tags.BlockTags.LEAVES) && !s.is(net.minecraft.tags.BlockTags.LOGS)) {
                if (GRASSY.contains(s.getBlock())) chunk.setBlockState(p, Blocks.DIRT.defaultBlockState(), false);
                return;
            }
            chunk.setBlockState(p, with, false);
        }
    }

    /** Clear y0..y1 to air; fluids too when withFluids (above the road), else only solids and plants (under a bridge). */
    private static void clear(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int y0, int y1, int z, boolean withFluids) {
        y0 = Math.max(y0, chunk.getMinBuildHeight());
        y1 = Math.min(y1, chunk.getMaxBuildHeight() - 1);
        for (int y = y0; y <= y1; y++) {
            BlockState s = chunk.getBlockState(p.set(x, y, z));
            if (s.isAir()) continue;
            if (!withFluids && !s.getFluidState().isEmpty()) continue;
            chunk.setBlockState(p, Blocks.AIR.defaultBlockState(), false);
        }
    }

    /** True when a feature / structure write at pos must be refused: inside a road column at or above its guard. */
    public static boolean guarded(WorldGenLevel level, BlockPos pos) {
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level.getLevel());
        if (n == null) return false;
        Column[] cols = columns(n, pos.getX() >> 4, pos.getZ() >> 4);
        if (cols == null) return false;
        return pos.getY() >= guard(cols[(pos.getX() & 15) | (pos.getZ() & 15) << 4]);
    }
}
