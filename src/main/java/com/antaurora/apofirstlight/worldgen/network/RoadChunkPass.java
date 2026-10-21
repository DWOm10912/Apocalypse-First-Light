package com.antaurora.apofirstlight.worldgen.network;

import com.antaurora.apofirstlight.block.GradedTurfBlock;
import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.road.Culvert;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks.Column;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelFixtures;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
 *   <li>TURF: one graded_turf block on the shared corners, dirt below; a rock cut's face (RoadWorks.ROCK_CUT): bare stone;</li>
 *   <li>DECK / ABUTMENT: collision on road_structure (the deck down to its slab's or girder's underside), the abutment
 *   wall down to the ground; structure never replaces natural ground (it only fills above it);</li>
 *   <li>BARRIER: road_structure for the parapets (the top block cut to the parapet's height), and at the span's ends the
 *   parapets and wing walls on the actual ground (a wing wall's top block always, also where that ground reaches it);</li>
 *   <li>the bridge's look is the client's mesh (BridgeMesh): road_structure is invisible collision that holds light;</li>
 *   <li>an overpass's deck (C3.3b group 2, Column#deck): the column's own finish first (the road it spans, its turf), then
 *   the deck's cells as above, never below the finish's top;</li>
 *   <li>OPEN: beside and between the decks, the natural ground as the noise made it (only what rises above the deck level
 *   is cleared; water untouched);</li>
 *   <li>CULVERT (group D): a box culvert's own cell: the gravel bed, the stream's water, the walls' and the roof's
 *   road_structure (waterlogged in the water), the open channel cleared above; a PAVED or TURF column over the culvert's
 *   body gets the culvert under its finish (the barrel kept open). Over a culvert the fills reach down to its bed (the
 *   stream's trench through the embankment, which the river fill cut, is filled again).</li>
 *   <li>TUNNEL (Tunnel V1): a tunnel's works (TunnelWorks#cells): road_structure for the lining, the walkways, the
 *   portals' concrete and the retaining walls, the bore and the cross passages carved out of the mountain, the mountain
 *   kept around them; on an approach everything above the floor is cleared to the sky. A PAVED column of a bore
 *   (RoadWorks.TUNNEL_PAVED) has its pavement as usual and the bore carved above it instead of the sky cleared; a PAVED
 *   or TURF column under a tunnel's works (RoadWorks.TUNNEL_OVER: walls, lead-ins, a portal's front) has its finish as
 *   usual and the works' cells above it, so the ground runs on under the concrete.</li>
 *   <li>DRIVE (County road G3, Driveway): a column wholly on the forest home's gravel driveway, its collision blocks
 *   (road_surface, as a PAVED column's) over two blocks of gravel, dirt to the ground; the client draws its surface
 *   (DriveMesh); its edges and verge are TURF;</li>
 *   <li>a tunnel's equipment and handrail segments (TunnelFixtures, V1.1: gates, signs, signals, gantries, fans,
 *   cabinets, fire doors, luminaires, cameras, plates, rails), last in any column: the part's fixture block
 *   (TunnelFixtureBlock, its block entity holding the placement) in the cells its boxes reach above the finish, where the
 *   cell is open (the road_structure of a wall it reaches into is taken over, its layers kept for when the part is
 *   taken down).</li>
 * </ul>
 * Structure written where the noise put water keeps the water (waterlogged, group D); structure filled over the ground
 * leaves its grass (the ground under a deck stays green).
 * Everything above a finished column is cleared up to the chunk's surface (over turf, a plant already standing on it
 * stays, except on the bare columns beside the pavement, RoadWorks#bare). Later writes into a road column at or above its guard height (trees, snow, structure pieces from this or a
 * neighbouring chunk) are refused by WorldGenRegionRoadGuardMixin, so the road never depends on the order chunks generate
 * in; only the plants on turf are let through (group B), and of those written before this pass only the ones that stand
 * on the turf survive it.
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
        RoadWorks.Ground ground = RoadWorks.Ground.of(n.plan());
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

    /** A rock cut's face (RoadWorks.ROCK_CUT): this many blocks from its top set to stone, m. */
    static final int ROCK_SKIN = 3;

    /** The lowest Y the guard protects in a finished column (Integer.MAX_VALUE: none). */
    public static int guard(Column c) {
        return switch (c.kind()) {
            case NONE -> Integer.MAX_VALUE;
            case PAVED -> (c.data() & RoadWorks.CULVERT_BODY) != 0 ? Math.min(c.y() - 3, c.bottom()) : c.y() - 3;
            case TURF -> (c.data() & RoadWorks.CULVERT_BODY) != 0 ? Math.min(c.y(), c.bottom()) : c.y();
            case DECK, ABUTMENT, BARRIER, CULVERT, TUNNEL -> c.bottom();
            case OPEN -> c.y() - 3;
            case DRIVE -> c.y() - 2;
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
                structure = AflBlocks.ROAD_STRUCTURE.get().defaultBlockState(), stone = Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            Column c = cols[lx | lz << 4];
            if (c.kind() == RoadWorks.Kind.NONE) {
                // a road sign (C3.3b, RoadSigns) may stand on the natural ground beside the works
                parts(chunk, p, n, cx * 16 + lx, cz * 16 + lz, c);
                continue;
            }
            int x = cx * 16 + lx, z = cz * 16 + lz;
            int surf = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, lx, lz);
            double g = n.plan().heightAt(x + 0.5, z + 0.5);
            int ground = (int) Math.floor(g);
            // over a box culvert the fill reaches its bed: the river fill cut the stream's trench through the embankment
            int lowest = ground - 2;
            Culvert[] near = n.works().culvertsNear(x + 0.5, z + 0.5);
            if (near != null)
                for (Culvert cv : near) {
                    double[] b = cv.bounds(Culvert.FILL_REACH);
                    if (x + 0.5 >= b[0] && x + 0.5 <= b[2] && z + 0.5 >= b[1] && z + 0.5 <= b[3]) lowest = Math.min(lowest, cv.bed - 2);
                }
            boolean body = (c.data() & RoadWorks.CULVERT_BODY) != 0 && (c.kind() == RoadWorks.Kind.PAVED || c.kind() == RoadWorks.Kind.TURF);
            switch (c.kind()) {
                case PAVED -> {
                    collision(chunk, p, x, z, c);
                    set(chunk, p, x, c.y() - 1, z, asphalt);
                    set(chunk, p, x, c.y() - 2, z, gravel);
                    set(chunk, p, x, c.y() - 3, z, base);
                    fill(chunk, p, x, c.y() - 4, z, dirt, lowest);
                    if ((c.data() & RoadWorks.TUNNEL_PAVED) != 0) tunnel(chunk, p, n.works(), x, z, c.top() + 1, c.roof(), structure);
                    else if ((c.data() & RoadWorks.TUNNEL_OVER) != 0) over(chunk, p, n.works(), x, z, c, surf, structure);
                    else clear(chunk, p, x, c.top() + 1, surf + 1, z, true);
                    if (body) culvert(chunk, p, x, z, n.works().culvertCell(x, z), c.y() - 2, Integer.MIN_VALUE, false);
                }
                case TURF -> {
                    int d = c.data();
                    if ((d & RoadWorks.ROCK_CUT) != 0) {
                        // a rock cut's face: bare stone (the surface rules' grass and dirt replaced), nothing grows on it
                        for (int k = 0; k < ROCK_SKIN; k++) set(chunk, p, x, c.y() - k, z, stone);
                        fill(chunk, p, x, c.y() - ROCK_SKIN, z, stone, lowest);
                        clear(chunk, p, x, c.y() + 1, surf + 1, z, true);
                        break;
                    }
                    set(chunk, p, x, c.y(), z, ((GradedTurfBlock) AflBlocks.GRADED_TURF.get()).with(d & 15, d >> 4 & 15, d >> 8 & 15, d >> 12 & 15, (d >> 16 & 1) != 0));
                    fill(chunk, p, x, c.y() - 1, z, dirt, lowest);
                    if (body) culvert(chunk, p, x, z, n.works().culvertCell(x, z), c.y() - 1, Integer.MIN_VALUE, false);
                    // a plant a neighbour's features already put on this turf (the guard lets them, any chunk order) stays,
                    // except beside the pavement (RoadWorks#bare)
                    if ((d & RoadWorks.TUNNEL_OVER) != 0) {
                        over(chunk, p, n.works(), x, z, c, surf, structure);
                        break;
                    }
                    BlockState on = chunk.getBlockState(p.set(x, c.y() + 1, z));
                    boolean tall = on.getBlock() instanceof DoublePlantBlock;
                    int keep = (d & RoadWorks.BARE) == 0 && plantOnTurf(on) && (!tall || on.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER)
                            ? c.y() + 1 + (tall ? 1 : 0) : c.y();
                    clear(chunk, p, x, keep + 1, surf + 1, z, true);
                }
                case DECK, ABUTMENT, BARRIER -> deck(chunk, p, x, z, c, solidTop(chunk, lx, lz), ground, Integer.MIN_VALUE, surf, structure);
                // beside and between the decks: the natural ground stays (cut to the planned ground it left bare stone and
                // dirt pits in front of the walls, 2026-10-15); only what rises above the deck level goes
                case OPEN -> clear(chunk, p, x, c.y() + 1, surf + 1, z, false);
                case TUNNEL -> {
                    boolean approach = (c.data() & TunnelWorks.APPROACH_COLUMN) != 0;
                    tunnel(chunk, p, n.works(), x, z, c.bottom(), c.y(), structure);
                    if (approach) clear(chunk, p, x, c.y() + 1, surf + 1, z, true);
                }
                case DRIVE -> {
                    // the forest home's driveway (County road G3): its collision over two blocks of gravel (the client draws
                    // the gravel surface: DriveMesh); nothing grows on it
                    collision(chunk, p, x, z, c);
                    set(chunk, p, x, c.y() - 1, z, gravel);
                    set(chunk, p, x, c.y() - 2, z, gravel);
                    fill(chunk, p, x, c.y() - 3, z, dirt, lowest);
                    clear(chunk, p, x, c.top() + 1, surf + 1, z, true);
                }
                case CULVERT -> {
                    Culvert.Cell cell = n.works().culvertCell(x, z);
                    if (cell == null) break;
                    culvert(chunk, p, x, z, cell, Integer.MAX_VALUE, solidTop(chunk, lx, lz), true);
                    clear(chunk, p, x, Math.max(c.y(), cell.channel() ? cell.culvert().level : c.y()) + 1, surf + 1, z, true);
                }
                default -> { }
            }
            // an overpass's deck over the finish (C3.3b group 2): its cells stand on the finish's top
            if (c.deck() != null) deck(chunk, p, x, z, c.deck(), c.top(), ground, c.top() + 1, surf, structure);
            parts(chunk, p, n, x, z, c);
        }
    }

    /**
     * A bridge column's cells (DECK, ABUTMENT, BARRIER) over the actual ground's top block solid, never below floor (an
     * overpass's deck over a finish, C3.3b group 2; Integer.MIN_VALUE: none).
     */
    private static void deck(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int z, Column c, int solid, int ground, int floor, int surf,
                             BlockState structure) {
        int from = Math.max(Math.max(c.bottom(), solid + 1), floor), lowest = Math.max(floor, Math.min(ground, solid) - 2);
        switch (c.kind()) {
            case DECK, ABUTMENT -> {
                collision(chunk, p, x, z, c);
                // the structure never replaces the natural ground (a cell's square reaches beyond the mesh: through the
                // invisible block the ground must still be there); the abutment stands on it
                for (int y = from; y < c.y(); y++) structure(chunk, p, x, y, z, structure);
                if (c.kind() == RoadWorks.Kind.ABUTMENT) fill(chunk, p, x, c.bottom() - 1, z, structure, lowest);
                clear(chunk, p, x, c.top() + 1, surf + 1, z, true);
            }
            case BARRIER -> {
                // the top block is always placed, also where the actual ground already reaches it (a wing wall, group
                // D: the client draws the walls continuously to their reach and finds every cell intact)
                for (int y = from; y < c.y(); y++) structure(chunk, p, x, y, z, structure);
                structure(chunk, p, x, c.y(), z, structure.setValue(RoadStructureBlock.LAYERS, c.layers()));
                if (c.bottom() <= Math.max(ground, solid) + 1) fill(chunk, p, x, c.bottom() - 1, z, structure, lowest);
                clear(chunk, p, x, c.y() + 1, surf + 1, z, true);
            }
            default -> { }
        }
    }

    /**
     * A tunnel's parts in a column (TunnelFixtures, V1.1, 2026-10-16): the part's fixture block in each of its cells in
     * this column, after the works and the clearing, above a finish (a pole's foot never replaces the turf or the pavement
     * it stands on), where the cell is open (air or a plant: no layers to keep) or road_structure (a sign or a rail against
     * a wall: its layers kept); a cell an earlier part took holds both (two rail segments meet in a cell); never the ground.
     */
    private static void parts(ChunkAccess chunk, BlockPos.MutableBlockPos p, RoadNetworkRuntime.Network n, int x, int z, Column c) {
        TunnelFixtures.Part[] parts = n.works().parts(RoadWorks.Ground.of(n.plan())).at(x, z);
        if (parts.length == 0) return;
        int floor = c.kind() == RoadWorks.Kind.PAVED || c.kind() == RoadWorks.Kind.TURF ? c.top() + 1 : Integer.MIN_VALUE;
        for (TunnelFixtures.Part part : parts) {
            TunnelFixtureBlock.Kind kind = TunnelFixtureBlock.Kind.of(part.placement.mesh());
            if (kind == null) continue;
            BlockState fixture = AflBlocks.TUNNEL_FIXTURES.get(kind).get().defaultBlockState();
            TunnelFixtureBlockEntity.Entry entry = new TunnelFixtureBlockEntity.Entry(kind, part.placement);
            for (long cell : part.cells()) {
                if (BlockPos.getX(cell) != x || BlockPos.getZ(cell) != z) continue;
                int y = BlockPos.getY(cell);
                if (y < floor || y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) continue;
                BlockState old = chunk.getBlockState(p.set(x, y, z));
                if (old.getBlock() instanceof TunnelFixtureBlock && chunk.getBlockEntity(p) instanceof TunnelFixtureBlockEntity taken) {
                    taken.add(entry);
                    continue;
                }
                int layers;
                if (old.is(AflBlocks.ROAD_STRUCTURE.get())) layers = old.getValue(RoadStructureBlock.LAYERS);
                else if (old.isAir() || old.canBeReplaced()) layers = 0;
                else continue;
                chunk.setBlockState(p, fixture, false);
                TunnelFixtureBlockEntity be = new TunnelFixtureBlockEntity(p.immutable(), fixture);
                be.set(java.util.List.of(entry), layers);
                chunk.setBlockEntity(be);
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

    /**
     * A box culvert's blocks in one column (group D): the gravel bed under the channel (filled under it, and under a wall,
     * down to the ground), from the bed up the walls' and the roof's road_structure (waterlogged in the water), the
     * stream's water, and the open barrel or channel as air; up to cap (a body cell under the road's finish). own: the
     * culvert's own cell (else the finish over it stays above its top); solid: the natural ground's top block of a cell
     * that keeps its ground (across a tip, Culvert.Cell#keepGround: only the top block replaces it). Water near a tip is
     * marked for the fluid's first tick (it meets the natural river there).
     */
    private static void culvert(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int z, Culvert.Cell cell, int cap, int solid, boolean own) {
        if (cell == null) return;
        Culvert cv = cell.culvert();
        BlockState structure = AflBlocks.ROAD_STRUCTURE.get().defaultBlockState(), water = Blocks.WATER.defaultBlockState(),
                air = Blocks.AIR.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), gravel = Blocks.GRAVEL.defaultBlockState();
        boolean keep = cell.keepGround();
        if (!keep) {
            if (cell.channel()) {
                set(chunk, p, x, cv.bed - 1, z, gravel);
                fill(chunk, p, x, cv.bed - 2, z, gravel, cv.bed - 8);
            } else fill(chunk, p, x, cv.bed - 1, z, dirt, cv.bed - 8);
        }
        int top = Math.min(cell.top(), cap), hi = Math.max(top, cell.channel() ? Math.min(cv.level, cap) : top);
        double a = cv.a(x + 0.5, z + 0.5);
        boolean tip = own && (a < cv.tip0 + 1.5 || a > cv.tip1 - 1.5);
        for (int y = cv.bed; y <= hi; y++) {
            boolean conc = y <= top && cell.concrete(y), wet = cell.water(y);
            if (keep && y <= solid && !(conc && y == cell.top())) continue;
            if (conc) {
                set(chunk, p, x, y, z, structure.setValue(RoadStructureBlock.LAYERS, y == cell.top() ? cell.layers() : 32).setValue(RoadStructureBlock.WATERLOGGED, wet));
            } else if (wet) {
                set(chunk, p, x, y, z, water);
                if (tip) chunk.markPosForPostprocessing(p.set(x, y, z));
            } else set(chunk, p, x, y, z, air);
        }
    }

    /**
     * A tunnel column's cells y0 .. y1 (TunnelWorks#cells): road_structure, air (fluids too: the bore is dry), or the
     * mountain as the noise made it.
     */
    private static void tunnel(ChunkAccess chunk, BlockPos.MutableBlockPos p, RoadWorks works, int x, int z, int y0, int y1, BlockState structure) {
        y0 = Math.max(y0, chunk.getMinBuildHeight());
        y1 = Math.min(y1, chunk.getMaxBuildHeight() - 1);
        if (y1 < y0) return;
        byte[] cells = TunnelWorks.cells(works, x, z, y0, y1);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = y0; y <= y1; y++) {
            byte st = cells[y - y0];
            if (st == TunnelWorks.STRUCTURE) chunk.setBlockState(p.set(x, y, z), structure, false);
            else if (st == TunnelWorks.AIR && !chunk.getBlockState(p.set(x, y, z)).isAir()) chunk.setBlockState(p, air, false);
        }
    }

    /**
     * A tunnel's works standing on a finish (RoadWorks.TUNNEL_OVER): its cells from the finish's top up to the roof; on an
     * approach (roof MAX_VALUE) up to the works' top or the chunk's surface, whichever is higher (every cell over the
     * floor is the works' structure or air there: the sky cleared).
     */
    private static void over(ChunkAccess chunk, BlockPos.MutableBlockPos p, RoadWorks works, int x, int z, Column c, int surf, BlockState structure) {
        int top = c.roof() == Integer.MAX_VALUE ? Math.max(surf + 1, TunnelWorks.worksTop(works, x, z)) : c.roof();
        tunnel(chunk, p, works, x, z, c.top() + 1, top, structure);
    }

    /** road_structure at (x, y, z), waterlogged where it replaces water. */
    private static void structure(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState s) {
        if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) return;
        chunk.setBlockState(p.set(x, y, z), waterlogged(s, chunk.getBlockState(p)), false);
    }

    /** s waterlogged when it is road_structure replacing a water source. */
    private static BlockState waterlogged(BlockState s, BlockState old) {
        return s.hasProperty(RoadStructureBlock.WATERLOGGED)
                ? s.setValue(RoadStructureBlock.WATERLOGGED, old.getFluidState().is(FluidTags.WATER) && old.getFluidState().isSource()) : s;
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

    /** Ground blocks that stop a fill (and grass-covered ones become dirt when buried under earth, not under structure). */
    private static final Set<Block> GRASSY = Set.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK);

    /**
     * From y down: replace what is not solid ground (air, plants, fluids of the fill area) until the natural ground, but
     * never below lowest (2 blocks under the planned ground, or a culvert's bed): a cave the carvers opened under the road
     * stays a cave. road_structure keeps the water it replaces (waterlogged) and leaves the grass under it (M2, group D).
     */
    private static void fill(ChunkAccess chunk, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState with, int lowest) {
        lowest = Math.max(lowest, chunk.getMinBuildHeight());
        boolean structure = with.hasProperty(RoadStructureBlock.WATERLOGGED);
        for (int n = 0; n < MAX_FILL && y >= lowest; n++, y--) {
            BlockState s = chunk.getBlockState(p.set(x, y, z));
            if (s.getFluidState().isEmpty() && s.isSolid() && !s.is(net.minecraft.tags.BlockTags.LEAVES) && !s.is(net.minecraft.tags.BlockTags.LOGS)) {
                if (!structure && GRASSY.contains(s.getBlock())) chunk.setBlockState(p, Blocks.DIRT.defaultBlockState(), false);
                return;
            }
            chunk.setBlockState(p, structure ? waterlogged(with, s) : with, false);
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

    /**
     * True when a feature / structure write of state at pos must be refused: inside a road column at or above its guard,
     * except the plants that grow on graded turf (#plantOnTurf) in the two cells over a TURF column that is not bare
     * (RoadWorks#bare: its square reaches the pavement) (C3.3a group B: the biome's own vegetation features dress the
     * earthworks like the ground beside them).
     */
    public static boolean guarded(WorldGenLevel level, BlockPos pos, BlockState state) {
        // a town building's own template on its lot (Fuel Stop A1, TownBuildingPass#placeFuelStop)
        if (com.antaurora.apofirstlight.worldgen.building.TownBuildingPass.placing()) return false;
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level.getLevel());
        if (n == null) return false;
        Column[] cols = columns(n, pos.getX() >> 4, pos.getZ() >> 4);
        if (cols == null) return false;
        Column c = cols[(pos.getX() & 15) | (pos.getZ() & 15) << 4];
        if (pos.getY() < guard(c) || pos.getY() > c.roof()) return false;
        // no plant over turf under a tunnel's works (the cells over it are the works' structure and air)
        return !(c.kind() == RoadWorks.Kind.TURF && (c.data() & (RoadWorks.BARE | RoadWorks.TUNNEL_OVER)) == 0 && pos.getY() > c.y() && pos.getY() <= c.y() + 2
                && plantOnTurf(state));
    }

    /** The plants that grow on graded turf (BushBlockGradedTurfMixin: BushBlock's own ground rule, no sapling). */
    public static boolean plantOnTurf(BlockState state) {
        return state.getBlock() instanceof BushBlock && !(state.getBlock() instanceof SaplingBlock);
    }
}
