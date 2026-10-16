package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.block.RoadSurfaceBlock;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.RoadChunkPass;
import com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime;
import com.antaurora.apofirstlight.worldgen.network.road.RoadDesign;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
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
        List<Double> out = new ArrayList<>();
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
        int wingsSkipped = 0;
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
                if (why != null) {
                    bad[col.kind().ordinal()]++;
                    if (examples.size() < 4) examples.add(col.kind() + " " + x + " " + col.y() + " " + z + ": " + why);
                }
                // nothing above the finish (OPEN: above the deck level)
                int topY = col.kind() == RoadWorks.Kind.OPEN ? col.y() + 1 : col.top() + 1;
                if (!natural) for (int y = topY; y < topY + 12; y++) if (!c.getBlockState(p.set(x, y, z)).isAir()) { above++; break; }
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
        line.accept(String.format(Locale.ROOT, "  roads: blocks above a finished column %d; collision top vs design max %.3f m; under the deck: water %d (top %s m vs planned ground), solid %d",
                above, maxCollision, underWater, waterTop == Integer.MIN_VALUE ? "-" : String.valueOf(waterTop), underSolid));
        if (!examples.isEmpty()) line.accept("  roads: first mismatches " + examples);
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
