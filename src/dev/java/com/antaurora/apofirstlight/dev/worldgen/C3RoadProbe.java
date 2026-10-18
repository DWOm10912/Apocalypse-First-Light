package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.RoadChunkPass;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.RoadSurfaceQuery;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelSection;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelMesh;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelFixtures;
import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import com.antaurora.apofirstlight.worldgen.network.road.TunnelWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * C3 road checks for the C2 harness (dev only; {@code roads=1}; docs/worldgen/federal_highways_c3.md, 10.6 and 11): the
 * probe sites are windows at a few stations of every built road (ends, middle, bridges, first culvert), and every column
 * of a window is compared with what the road's chunk pass must have built there (RoadChunkPass.columns, the same pure
 * function):
 * <ul>
 *   <li>collision columns (road_surface and its layers), asphalt, turf states, the bridge's road_structure (deck under its
 *   collision, parapet and wing tops with their layers);</li>
 *   <li>nothing standing above a finished column (trees, plants, snow, structure pieces);</li>
 *   <li>the collision top the server sees (block collision shapes) against the designed pavement;</li>
 *   <li>under the bridge: water present and no solid block between the planned ground and the deck;</li>
 *   <li>tunnels (Tunnel V1): every cell of a tunnel column (and over a bore's pavement) against TunnelWorks#cells:
 *   road_structure where the works put structure, air in the bores and passages; {@code only=tunnels} probes the
 *   tunnels' stations alone (portals, middle, passages, an approach);</li>
 *   <li>per chunk a hash of the road columns (from the guard height to 6 above the finish), for determinism between two
 *   worlds of one seed and for save / reload (verify=FILE compares the "road" lines).</li>
 * </ul>
 * Then armor stands are dropped onto the pavement (server physics) and their resting height is read after 3 s.
 */
final class C3RoadProbe {
    private C3RoadProbe() {}

    /** A natural solid block (the pass leaves the ground under a deck as it is, 2026-10-15). */
    private static boolean naturalGround(BlockState b) {
        return !b.isAir() && b.getFluidState().isEmpty() && b.isSolid() && !b.is(AflBlocks.ROAD_STRUCTURE.get()) && !b.is(AflBlocks.ROAD_SURFACE.get());
    }

    static boolean active() { return "1".equals(C2Harness.arg("roads", "")); }

    static final Map<String, String> HASHES = new TreeMap<>();
    /** roaddump=cx,cz/cx,cz: every block of the road columns of these chunks, written to TAG_roaddump.txt */
    private static final java.util.Set<String> DUMP_CHUNKS = new java.util.HashSet<>(List.of(C2Harness.arg("roaddump", "").split("/")));
    static final StringBuilder DUMP = new StringBuilder();
    private static final List<double[]> STANDS = new ArrayList<>();   // x, z, designed pavement
    private static final List<ArmorStand> ENTITIES = new ArrayList<>();
    /** chunks forced so the stands tick (released after the read) */
    private static final List<int[]> FORCED = new ArrayList<>();

    /** A span this shallow is a culvert (RoadNetworkJson's culvert deck). */
    private static final double CULVERT_DEPTH = 1.0;

    /**
     * The probe stations of a road: both ends (where it stops short of another road), its middle, each bridge and the
     * first culvert. A few windows per road, not the whole country (the harness generates every window's chunks).
     */
    private static List<Double> stations(RoadDesign d, boolean ends) {
        List<Double> out = new ArrayList<>(tunnelStations(d, ends));
        if (tunnelsOnly()) return out;
        if (ends) out.add(0.0);
        out.add(d.length / 2);
        if (ends) out.add(d.length);
        boolean culvert = false;
        for (RoadDesign.Span sp : d.spans) {
            if (sp.depth() < CULVERT_DEPTH) {
                if (culvert) continue;
                culvert = true;
            }
            out.add((sp.s0() + sp.s1()) / 2);
        }
        return out;
    }

    private static boolean tunnelsOnly() { return "tunnels".equals(C2Harness.arg("only", "")); }

    /** A tunnel's probe stations: its portals and middle (ends: also 30 m out on each approach and the passages). */
    private static List<Double> tunnelStations(RoadDesign d, boolean ends) {
        List<Double> out = new ArrayList<>();
        for (RoadDesign.Tunnel tn : d.tunnels) {
            out.add((tn.s0() + tn.s1()) / 2);
            if (!ends) continue;
            out.add(tn.s0());
            out.add(tn.s1());
            out.add(tn.s0() - 30);
            out.add(tn.s1() + 30);
            // the CMS gantries (TunnelMesh.all)
            if (tn.length() >= TunnelMesh.SYSTEMS)
                for (int sgn : new int[]{1, -1}) {
                    double sp = sgn > 0 ? tn.s0() : tn.s1(), t = TunnelMesh.gantry(d, tn, sp, sgn);
                    if (!Double.isNaN(t)) out.add(sp - sgn * t);
                }
            for (double ps : TunnelWorks.passages(TunnelSection.of(d.section), tn)) out.add(ps);
        }
        return out;
    }

    /** The tunnel parts standing on the ground beside the road (their feet are checked). */
    private static final java.util.Set<String> FOOTED = java.util.Set.of("portal_signal", "headlight_sign", "closure_gate", "cms_gantry");

    /** Windows (radius 2 chunks) at the probe stations of every built road. */
    static List<C2Probe.Site> sites(ServerLevel level) {
        List<C2Probe.Site> out = new ArrayList<>();
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level);
        if (n == null) return out;
        for (RoadDesign d : n.works().roads()) {
            for (double s : stations(d, true)) {
                double[] p = d.point(s, 0);
                out.add(new C2Probe.Site(String.format(Locale.ROOT, "road %s %.0f", d.id, s), (int) Math.floor(p[0]), (int) Math.floor(p[1]), 2));
            }
        }
        return out;
    }

    static void check(ServerLevel level, C2Probe.Site s, LevelChunk[][] chunks, int x0, int z0, int w, Consumer<String> line) {
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level);
        if (n == null) { line.accept("  roads: none bound"); return; }
        int r = s.radius();
        int[] byKind = new int[RoadWorks.Kind.values().length], bad = new int[RoadWorks.Kind.values().length];
        int wingsSkipped = 0, tunnelCells = 0, tunnelBad = 0, overCols = 0, rockCells = 0, rockCaves = 0;
        List<String> caveExamples = new ArrayList<>();
        // the tunnel fixtures' cells (TunnelFixtures, V1.1) and the feet of the roadside parts over the ground
        TunnelFixtures.Index parts = n.works().parts();
        int partCells = 0, partBad = 0, partEmpty = 0, partGround = 0, partTaken = 0, feet = 0;
        java.util.Map<String, Integer> fixtureKinds = new java.util.TreeMap<>();
        double footMin = Double.POSITIVE_INFINITY, footMax = Double.NEGATIVE_INFINITY;
        List<String> partExamples = new ArrayList<>(), footExamples = new ArrayList<>();
        List<String> aboveExamples = new ArrayList<>();
        int above = 0, underSolid = 0, underWater = 0, waterTop = Integer.MIN_VALUE;
        double maxCollision = 0;
        List<String> examples = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState asphalt = AflBlocks.ASPHALT.get().defaultBlockState(), structure = AflBlocks.ROAD_STRUCTURE.get().defaultBlockState();
        for (int cz = 0; cz < 2 * r + 1; cz++) for (int cx = 0; cx < 2 * r + 1; cx++) {
            LevelChunk c = chunks[cx][cz];
            int ccx = c.getPos().x, ccz = c.getPos().z;
            RoadWorks.Column[] cols = RoadChunkPass.columns(n, ccx, ccz);
            long h = 0xcbf29ce484222325L;
            if (cols == null) { HASHES.put(ccx + " " + ccz, "none"); continue; }
            for (TunnelMesh.Placement pl : TunnelMesh.parts(n.works(), ccx, ccz)) {
                if (!FOOTED.contains(pl.mesh())) continue;
                int fx = (int) Math.floor(pl.x()), fz = (int) Math.floor(pl.z()), fy = (int) Math.floor(pl.y());
                double ground = Double.NaN;
                for (int y = fy + 1; y >= fy - 4 && Double.isNaN(ground); y--) {
                    BlockState b = c.getBlockState(p.set(fx, y, fz));
                    // the part's own cells (fixture blocks, V1.1) and structure are not the ground
                    if (b.isAir() || b.is(AflBlocks.ROAD_STRUCTURE.get()) || b.getBlock() instanceof TunnelFixtureBlock) continue;
                    net.minecraft.world.phys.shapes.VoxelShape sh = b.getCollisionShape(level, p);
                    if (!sh.isEmpty()) ground = y + sh.max(net.minecraft.core.Direction.Axis.Y);
                }
                if (Double.isNaN(ground)) continue;
                feet++;
                double gap = pl.y() - ground;
                footMin = Math.min(footMin, gap);
                footMax = Math.max(footMax, gap);
                if (footExamples.size() < 8) footExamples.add(String.format(Locale.ROOT, "%s %.1f %.2f %.1f: %+.2f", pl.mesh(), pl.x(), pl.y(), pl.z(), gap));
            }
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                RoadWorks.Column col = cols[lx | lz << 4];
                if (col.kind() == RoadWorks.Kind.NONE) continue;
                int x = ccx * 16 + lx, z = ccz * 16 + lz;
                byKind[col.kind().ordinal()]++;
                String why = null;
                boolean natural = false;
                switch (col.kind()) {
                    case PAVED, DECK, ABUTMENT -> {
                        BlockState b = c.getBlockState(p.set(x, col.y(), z));
                        if (!b.is(AflBlocks.ROAD_SURFACE.get()) || b.getValue(RoadSurfaceBlock.LAYERS) != col.layers()) why = "collision " + b;
                        else if (col.upper() > 0 && c.getBlockState(p.set(x, col.y() + 1, z)).getOptionalValue(RoadSurfaceBlock.LAYERS).orElse(-1) != col.upper())
                            why = "upper collision";
                        else if (col.kind() == RoadWorks.Kind.PAVED && c.getBlockState(p.set(x, col.y() - 1, z)) != asphalt) why = "no asphalt";
                        else if (col.kind() != RoadWorks.Kind.PAVED && col.bottom() < col.y() && c.getBlockState(p.set(x, col.y() - 1, z)) != structure
                                && !naturalGround(c.getBlockState(p))) why = "no deck";
                        if (why == null) {
                            double top = col.y() + c.getBlockState(p.set(x, col.top(), z)).getCollisionShape(level, p).max(net.minecraft.core.Direction.Axis.Y)
                                    + (col.upper() > 0 ? 1 : 0);
                            RoadWorks.Hit hit = n.works().hit(x + 0.5, z + 0.5, RoadWorks.REACH);
                            if (hit != null && hit.road().section.paved(hit.v()) && hit.beyond() == 0)
                                maxCollision = Math.max(maxCollision, Math.abs(top - hit.road().pavementY(hit.sc(), hit.u())));
                        }
                    }
                    case TURF -> {
                        BlockState b = c.getBlockState(p.set(x, col.y(), z));
                        if (!b.is(AflBlocks.GRADED_TURF.get())) why = "turf " + b;
                    }
                    case BARRIER -> {
                        BlockState top = c.getBlockState(p.set(x, col.y(), z));
                        // a wing wall the pass left out stands in ground that reaches its top
                        boolean skipped = col.data() == com.antaurora.apofirstlight.worldgen.network.road.BridgeMesh.WING && !top.is(AflBlocks.ROAD_STRUCTURE.get())
                                && !top.isAir() && top.getFluidState().isEmpty();
                        if (skipped) { wingsSkipped++; natural = true; }
                        else if (!top.is(AflBlocks.ROAD_STRUCTURE.get()) || top.getValue(com.antaurora.apofirstlight.block.RoadStructureBlock.LAYERS) != col.layers())
                            why = "barrier " + top;
                    }
                    case CULVERT -> {
                        // a box culvert's own cell (group D): its top concrete block, or the stream's water
                        BlockState top = c.getBlockState(p.set(x, col.y(), z));
                        if (col.layers() > 0 && (!top.is(AflBlocks.ROAD_STRUCTURE.get())
                                || top.getValue(com.antaurora.apofirstlight.block.RoadStructureBlock.LAYERS) != col.layers())) why = "culvert " + top;
                        else if (col.layers() == 0 && !top.getFluidState().is(Fluids.WATER)) why = "culvert water " + top;
                    }
                    case OPEN -> {
                        int g = (int) Math.floor(n.plan().heightAt(x + 0.5, z + 0.5));
                        for (int y = g - 8; y < col.y() - 2; y++) {
                            BlockState b = c.getBlockState(p.set(x, y, z));
                            if (b.getFluidState().is(Fluids.WATER)) { underWater++; waterTop = Math.max(waterTop, y - g); }
                            else if (y > g && !b.isAir() && b.getFluidState().isEmpty() && b.blocksMotion()) underSolid++;
                        }
                    }
                    default -> { }
                }
                // a tunnel's cells: the works' structure, the open bore; the mountain as the noise made it; works standing
                // on a finish (TUNNEL_OVER) from its top up
                boolean bore = col.kind() == RoadWorks.Kind.PAVED && (col.data() & RoadWorks.TUNNEL_PAVED) != 0;
                boolean over = (col.kind() == RoadWorks.Kind.PAVED || col.kind() == RoadWorks.Kind.TURF) && (col.data() & RoadWorks.TUNNEL_OVER) != 0;
                int worksTop = over ? TunnelWorks.worksTop(n.works(), x, z) : Integer.MIN_VALUE;
                if (over) overCols++;
                if (col.kind() == RoadWorks.Kind.TUNNEL || bore || over) {
                    int y0 = bore || over ? col.top() + 1 : col.bottom(),
                            y1 = bore ? col.roof() : over ? (col.roof() == Integer.MAX_VALUE ? worksTop : col.roof()) : col.y();
                    byte[] cells = TunnelWorks.cells(n.works(), x, z, y0, y1);
                    for (int y = y0; y <= y1; y++) {
                        byte st = cells[y - y0];
                        if (st == TunnelWorks.NATURAL) continue;
                        // a cell a tunnel part takes is the part's (checked below)
                        if (partCell(parts, x, y, z)) continue;
                        tunnelCells++;
                        BlockState b = c.getBlockState(p.set(x, y, z));
                        boolean ok = st == TunnelWorks.STRUCTURE ? b.is(AflBlocks.ROAD_STRUCTURE.get()) : b.isAir();
                        if (!ok) {
                            tunnelBad++;
                            if (why == null) why = (st == TunnelWorks.STRUCTURE ? "tunnel structure " : "tunnel air ") + y + " " + b;
                        }
                    }
                    // the bore's roof and the mountain stand over it
                    if (over ? col.roof() != Integer.MAX_VALUE : bore || (col.data() & TunnelWorks.APPROACH_COLUMN) == 0) natural = true;
                }
                // the rock a tunnel keeps (TunnelWorks#rock): no cave, no fluid in its natural cells under the ground
                double[] rock = TunnelWorks.rock(n.works(), x, z);
                if (rock != null) {
                    int r0 = (int) Math.ceil(rock[0]), r1 = (int) Math.min(Math.floor(rock[1]), c.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz) - 1);
                    if (r1 >= r0) {
                        byte[] cells = TunnelWorks.cells(n.works(), x, z, r0, r1);
                        for (int y = r0; y <= r1; y++) {
                            if (cells[y - r0] != TunnelWorks.NATURAL) continue;
                            rockCells++;
                            BlockState b = c.getBlockState(p.set(x, y, z));
                            if (b.isAir() || !b.getFluidState().isEmpty()) {
                                rockCaves++;
                                if (caveExamples.size() < 6) caveExamples.add(col.kind() + " " + x + " " + y + " " + z + " " + b);
                            }
                        }
                    }
                }
                // the parts' cells: their fixture block, its block entity holding the part, a shape; above a finish
                for (TunnelFixtures.Part part : parts.at(x, z)) {
                    int floor = col.kind() == RoadWorks.Kind.PAVED || col.kind() == RoadWorks.Kind.TURF ? col.top() + 1 : Integer.MIN_VALUE;
                    boolean master = true;
                    for (long cell : part.cells()) {
                        boolean first = master;
                        master = false;
                        if (BlockPos.getX(cell) != x || BlockPos.getZ(cell) != z || BlockPos.getY(cell) < floor) continue;
                        partCells++;
                        BlockState b = c.getBlockState(p.set(x, BlockPos.getY(cell), z));
                        TunnelFixtureBlock.Kind pk = TunnelFixtureBlock.Kind.of(part.placement.mesh());
                        if (b.getBlock() instanceof TunnelFixtureBlock && pk != null && c.getBlockEntity(p) instanceof TunnelFixtureBlockEntity be
                                && be.holds(new TunnelFixtureBlockEntity.Entry(pk, part.placement))) {
                            if (first) fixtureKinds.merge(part.placement.mesh(), 1, Integer::sum);
                            if (be.structure() > 0) partTaken++;
                            if (b.getShape(level, p).isEmpty()) partEmpty++;
                        } else if (b.getBlock() instanceof TunnelFixtureBlock || b.isAir() || b.is(AflBlocks.ROAD_STRUCTURE.get())) {
                            partBad++;
                            if (partExamples.size() < 6) partExamples.add(part.placement.mesh() + " " + col.kind() + " " + x + " " + BlockPos.getY(cell) + " " + z + " " + b);
                        } else partGround++;
                    }
                }
                if (why != null) {
                    bad[col.kind().ordinal()]++;
                    if (examples.size() < 4) examples.add(col.kind() + " " + x + " " + col.y() + " " + z + ": " + why);
                }
                // nothing above the finish (OPEN: above the deck level)
                int topY = col.kind() == RoadWorks.Kind.OPEN ? col.y() + 1 : over ? Math.max(col.top(), worksTop) + 1 : col.top() + 1;
                // the plants turf may carry (RoadChunkPass#guarded) are no blocks above it
                boolean plants = col.kind() == RoadWorks.Kind.TURF && (col.data() & (RoadWorks.BARE | RoadWorks.TUNNEL_OVER)) == 0;
                if (!natural) for (int y = topY; y < topY + 12; y++) if (!c.getBlockState(p.set(x, y, z)).isAir()
                        && !(plants && y <= col.y() + 2 && RoadChunkPass.plantOnTurf(c.getBlockState(p)))
                        && !(c.getBlockState(p).getBlock() instanceof TunnelFixtureBlock)) {
                    above++;
                    if (aboveExamples.size() < 8) aboveExamples.add(col.kind() + "/" + col.data() + " " + x + " " + col.y() + " " + z + ": " + (y - col.y()) + " up " + c.getBlockState(p));
                    break;
                }
                // from the guard height up: below it the natural ground follows vanilla decoration, whose ores depend on
                // the order chunks generate in (air exposure); OPEN: from the deck level
                for (int y = col.kind() == RoadWorks.Kind.OPEN ? col.y() : RoadChunkPass.guard(col); y <= col.top() + 6; y++)
                    h = mix(h, state(c.getBlockState(p.set(x, y, z))));
            }
            HASHES.put(ccx + " " + ccz, Long.toHexString(h));
            if (DUMP_CHUNKS.contains(ccx + "," + ccz)) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                RoadWorks.Column col = cols[lx | lz << 4];
                if (col.kind() == RoadWorks.Kind.NONE) continue;
                int x = ccx * 16 + lx, z = ccz * 16 + lz;
                for (int y = col.y() - 4; y <= col.top() + 6; y++)
                    DUMP.append(x).append(' ').append(z).append(' ').append(y).append(' ').append(col.kind()).append(' ')
                            .append(c.getBlockState(p.set(x, y, z))).append('\n');
            }
        }
        StringBuilder k = new StringBuilder();
        for (RoadWorks.Kind kind : RoadWorks.Kind.values())
            if (byKind[kind.ordinal()] > 0) k.append(String.format(Locale.ROOT, " %s %d/%d", kind, byKind[kind.ordinal()] - bad[kind.ordinal()], byKind[kind.ordinal()]));
        line.accept("  roads: columns as built / expected:" + k + (wingsSkipped > 0 ? " (wing walls left out in ground reaching their top: " + wingsSkipped + ")" : ""));
        if (tunnelCells > 0) line.accept(String.format(Locale.ROOT, "  roads: tunnel cells as built %d/%d; finishes under works %d", tunnelCells - tunnelBad, tunnelCells, overCols));
        if (partCells > 0) line.accept(String.format(Locale.ROOT, "  roads: fixture cells as built %d/%d (in the ground, left out %d; took over structure %d), without a shape %d; parts by first cell %s%s",
                partCells - partBad - partGround, partCells, partGround, partTaken, partEmpty, fixtureKinds, partExamples.isEmpty() ? "" : " " + partExamples));
        if (feet > 0) line.accept(String.format(Locale.ROOT, "  roads: roadside equipment feet over the ground %+.2f .. %+.2f m (%d parts) %s", footMin, footMax, feet, footExamples));
        if (rockCells > 0) line.accept(String.format(Locale.ROOT, "  roads: tunnel rock cells %d, open (air or fluid) %d%s", rockCells, rockCaves, caveExamples.isEmpty() ? "" : " " + caveExamples));
        line.accept(String.format(Locale.ROOT, "  roads: blocks above a finished column %d; collision top vs design max %.3f m; under the deck: water %d (top %s m vs planned ground), solid %d",
                above, maxCollision, underWater, waterTop == Integer.MIN_VALUE ? "-" : String.valueOf(waterTop), underSolid));
        if (!examples.isEmpty()) line.accept("  roads: first mismatches " + examples);
        if (!aboveExamples.isEmpty()) line.accept("  roads: first blocks above " + aboveExamples);
        ground(level, n, chunks, r, line);
    }

    /**
     * The road ground query (RoadSurfaceQuery, docs/worldgen/road_surface_query_v1.md) on the window's built columns: four
     * points per column, yHint the column's own collision top (a wheel resting there). On the design's pavement: asphalt at
     * the design's height (and how far that lies from the blocks' collision top); elsewhere what each column kind gives
     * (null / asphalt / concrete / turf, blocking); and the time per call on the live level.
     */
    private static void ground(ServerLevel level, RoadNetworkRuntime.Network n, LevelChunk[][] chunks, int r, Consumer<String> line) {
        RoadWorks.Kind[] kinds = {RoadWorks.Kind.PAVED, RoadWorks.Kind.DECK, RoadWorks.Kind.ABUTMENT, RoadWorks.Kind.TURF, RoadWorks.Kind.BARRIER, RoadWorks.Kind.TUNNEL};
        long[][] got = new long[RoadWorks.Kind.values().length][5];   // null, asphalt, concrete, turf, blocking
        long paved = 0, pavedBad = 0, covered = 0, calls = 0, nanos = 0;
        double maxDesign = 0, maxBlocks = 0;
        List<String> bad = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        double[][] at = {{0.25, 0.25}, {0.75, 0.25}, {0.25, 0.75}, {0.75, 0.75}};
        for (int cz = 0; cz < 2 * r + 1; cz++) for (int cx = 0; cx < 2 * r + 1; cx++) {
            LevelChunk c = chunks[cx][cz];
            int ccx = c.getPos().x, ccz = c.getPos().z;
            RoadWorks.Column[] cols = RoadChunkPass.columns(n, ccx, ccz);
            if (cols == null) continue;
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                RoadWorks.Column col = cols[lx | lz << 4];
                if (!java.util.Arrays.asList(kinds).contains(col.kind())) continue;
                int x = ccx * 16 + lx, z = ccz * 16 + lz;
                double top = Double.NaN;
                BlockState topBlock = null;
                for (int y = col.top() + 3; y >= col.y() - 2 && Double.isNaN(top); y--) {
                    BlockState b = c.getBlockState(p.set(x, y, z));
                    if (b.isAir() || b.getBlock() instanceof TunnelFixtureBlock) continue;
                    net.minecraft.world.phys.shapes.VoxelShape sh = b.getCollisionShape(level, p);
                    if (!sh.isEmpty()) { top = y + sh.max(net.minecraft.core.Direction.Axis.Y); topBlock = b; }
                }
                if (Double.isNaN(top)) continue;
                for (double[] f : at) {
                    double px = x + f[0], pz = z + f[1];
                    long t0 = System.nanoTime();
                    RoadSurfaceQuery.Hit h = RoadSurfaceQuery.sample(level, px, pz, top);
                    nanos += System.nanoTime() - t0;
                    calls++;
                    long[] g = got[col.kind().ordinal()];
                    if (h == null) g[0]++;
                    else {
                        g[1 + h.surface().ordinal()]++;
                        if (h.blocking()) g[4]++;
                    }
                    RoadWorks.Hit d = n.works().hit(px, pz, RoadWorks.REACH);
                    boolean design = col.kind() != RoadWorks.Kind.TURF && col.kind() != RoadWorks.Kind.BARRIER && col.kind() != RoadWorks.Kind.TUNNEL
                            && d != null && d.road().section.paved(d.v()) && d.beyond() == 0;
                    if (!design) continue;
                    paved++;
                    double want = d.road().pavementY(d.sc(), d.u());
                    // concrete standing on the road (a portal's barrier on the shoulder): its own top, blocking
                    if (topBlock.is(AflBlocks.ROAD_STRUCTURE.get()) && top > want + 0.1) {
                        covered++;
                        if (h == null || h.surface() != RoadSurfaceQuery.Surface.CONCRETE || !h.blocking() || h.height() != top) {
                            pavedBad++;
                            if (bad.size() < 6) bad.add(String.format(Locale.ROOT, "covered %s %.2f %.2f %.2f: %s", col.kind(), px, top, pz, h));
                        }
                        continue;
                    }
                    if (h == null || h.surface() != RoadSurfaceQuery.Surface.ASPHALT || h.blocking() || Math.abs(h.height() - want) > 1e-6) {
                        pavedBad++;
                        if (bad.size() < 6) bad.add(String.format(Locale.ROOT, "%s %.2f %.2f %.2f: %s", col.kind(), px, top, pz, h));
                        continue;
                    }
                    maxDesign = Math.max(maxDesign, Math.abs(h.height() - want));
                    maxBlocks = Math.max(maxBlocks, Math.abs(h.height() - top));
                }
            }
        }
        if (calls == 0) return;
        StringBuilder k = new StringBuilder();
        for (RoadWorks.Kind kind : kinds) {
            long[] g = got[kind.ordinal()];
            if (g[0] + g[1] + g[2] + g[3] == 0) continue;
            k.append(String.format(Locale.ROOT, " %s null %d asphalt %d concrete %d turf %d blocking %d;", kind, g[0], g[1], g[2], g[3], g[4]));
        }
        line.accept(String.format(Locale.ROOT, "  roads: ground query on the design pavement %d/%d as expected (of them under concrete standing on the road, blocking: %d; open: vs design max %.6f m, vs blocks max %.3f m)%s; by column:%s %.2f us/call (%d calls)",
                paved - pavedBad, paved, covered, maxDesign, maxBlocks, bad.isEmpty() ? "" : " " + bad, k, nanos / 1000.0 / calls, calls));
    }

    /** Whether cell (x, y, z) is one of a tunnel part's (TunnelFixtures.Part#cells). */
    private static boolean partCell(TunnelFixtures.Index parts, int x, int y, int z) {
        long k = TunnelFixtures.pack(x, y, z);
        for (TunnelFixtures.Part part : parts.at(x, z)) for (long cell : part.cells()) if (cell == k) return true;
        return false;
    }

    /** Drop armor stands onto the outer lane centres at the probe stations, ends left out (server-side physics). */
    static void dropStands(ServerLevel level, Consumer<String> line) {
        RoadNetworkRuntime.Network n = RoadNetworkRuntime.bound(level);
        if (n == null) return;
        for (RoadDesign d : n.works().roads()) {
            double lane = d.section.laneCentre(d.section.lanes - 1);
            for (double s : stations(d, false)) for (double u : new double[]{lane, -lane}) {
                double[] xz = d.point(s, u);
                double y = d.pavementY(s, u);
                int scx = (int) Math.floor(xz[0]) >> 4, scz = (int) Math.floor(xz[1]) >> 4;
                if (level.setChunkForced(scx, scz, true)) FORCED.add(new int[]{scx, scz});
                level.getChunk(scx, scz);
                ArmorStand a = EntityType.ARMOR_STAND.create(level);
                if (a == null) continue;
                a.moveTo(xz[0], y + 2.0, xz[1], 0, 0);
                level.addFreshEntity(a);
                ENTITIES.add(a);
                STANDS.add(new double[]{xz[0], xz[1], y, s, u});
            }
        }
        line.accept("roads: dropped " + ENTITIES.size() + " armor stands 2 m above the pavement");
    }

    /** All dropped stands have come to rest (onGround and not moving). */
    static boolean allResting() {
        for (ArmorStand a : ENTITIES) if (!a.onGround() || Math.abs(a.getDeltaMovement().y) > 0.1) return false;
        return true;
    }

    static void readStands(ServerLevel level, Consumer<String> line) {
        double worst = 0;
        int resting = 0;
        for (int i = 0; i < ENTITIES.size(); i++) {
            ArmorStand a = ENTITIES.get(i);
            double[] st = STANDS.get(i);
            double err = a.getY() - st[2];
            worst = Math.max(worst, Math.abs(err));
            if (a.onGround()) resting++;
            line.accept(String.format(Locale.ROOT, "  stand at station %.0f offset %+.1f: y %.3f, pavement %.3f, diff %+.3f, on ground %s",
                    st[3], st[4], a.getY(), st[2], err, a.onGround()));
            a.discard();
        }
        for (int[] f : FORCED) level.setChunkForced(f[0], f[1], false);
        FORCED.clear();
        line.accept(String.format(Locale.ROOT, "roads: armor stands resting %d/%d, worst |y - pavement| %.3f m", resting, ENTITIES.size(), worst));
    }

    private static final Map<BlockState, Long> STATE_HASH = new java.util.IdentityHashMap<>();

    private static long state(BlockState st) {
        return STATE_HASH.computeIfAbsent(st, s -> {
            long v = 0;
            for (byte b : (BuiltInRegistries.BLOCK.getKey(s.getBlock()) + s.getValues().toString()).getBytes(StandardCharsets.UTF_8)) v = v * 1099511628211L + b;
            return v;
        });
    }

    private static long mix(long h, long v) {
        h ^= v;
        h *= 0x100000001b3L;
        return h ^ (h >>> 29);
    }
}
