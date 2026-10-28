package com.antaurora.apofirstlight.liquid;

import com.antaurora.apofirstlight.liquid.world.LiquidPackets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mesh water M1 offline check (docs/gameplay/water_mesh_parcels_v1_design.md A4): a ditch, a slope of 0.2 one metre wide,
 * from a pool fed with 2 L/s to a pool below, across a chunk border (x = 16), on the engine with the floor's films on
 * (as in game). From the Surface packets the server would send, the client's cells as it builds them, and the surface's
 * vertices from LiquidSurfaceMesh, the ground the synthetic one:
 * <ul>
 *   <li>A1: every cell of the slope is sent, none of them left out, and drawn as running water on the ground;</li>
 *   <li>A2: every vertex two cells (or chunks) share is the same number from both; the slope's vertices lie on the ground,
 *   as thick as the film, within 1 cm; nowhere under the ground; the pools level;</li>
 *   <li>a steady flow: how often a chunk's Surface changes (the digest), reported;</li>
 *   <li>after the feed stops and the ditch drains, the film the floor holds is no longer sent;</li>
 *   <li>rain on a field: its runoff is sent while it runs; once drained, the films on the grass are not;</li>
 *   <li>a pit dug block by block in creative (uneven, 1 and 2 m deep) filled with {@code fill} and poured into until it
 *   spills onto the grass: no vertex stands far above the water round it (the first look in game, 2026-10-26: deep water
 *   running fast was drawn as a film and stood up in spikes metres tall, design 11.5);</li>
 *   <li>a pool with a ledge 0.3 m up one wall, poured onto so it runs off the ledge, and a smooth bank: level water's
 *   vertices never stand above its level (the second look, 2026-10-26: water climbed the walls and its banks, 11.7).</li>
 * </ul>
 *   ./gradlew waterMeshCheck
 */
public final class WaterMeshCheck {
    private static int failures, checks;

    private static void check(boolean ok, String what) {
        checks++;
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    /** The ditch: z = 2, sloping from x = 5 to 21; the pools at x 1-4 (floor 22) and 21-26 (floor 19), banks round. */
    static final int DITCH_Z = 2, SLOPE_FROM = 5, SLOPE_TO = 21;

    static double ground(double x, double z) {
        if (x < 1 || x >= 27) return 25;
        if (x < SLOPE_FROM) return 22.0;
        if (x >= SLOPE_TO) return 19.0;
        return z >= DITCH_Z && z < DITCH_Z + 1 ? 22.6 - 0.2 * (x - SLOPE_FROM) : 25;
    }

    /** A client cell from the packet. */
    static final class Cell extends LiquidSurfaceMesh.Cell {
        final float l;

        Cell(int x, int z, int floor, float level, int flags, short vx, short vz, short film) {
            super(x, z, floor, flags >> LiquidPackets.SIDES_SHIFT & 15, (flags & LiquidPackets.BOUNDARY) != 0,
                    (flags & LiquidPackets.STILL) != 0, (flags & LiquidPackets.RUNNING) != 0);
            l = level;
            this.vx = (float) (vx / LiquidPackets.Surface.VELOCITY);
            this.vz = (float) (vz / LiquidPackets.Surface.VELOCITY);
            this.film = film / 1000F;
        }

        @Override
        public float level() {
            return l;
        }
    }

    static class Client implements LiquidSurfaceMesh.Source {
        final Map<Long, List<Cell>> columns = new HashMap<>();
        final List<Cell> all = new ArrayList<>();
        final SyntheticGround.Height ground;

        Client(SyntheticGround.Height ground) {
            this.ground = ground;
        }

        void receive(LiquidPackets.Surface p) {
            for (int i = 0; i < p.column().length; i++) {
                int x = (p.chunkX() << 4) + ((p.column()[i] >> 4) & 15), z = (p.chunkZ() << 4) + (p.column()[i] & 15);
                Cell c = new Cell(x, z, p.floor()[i], p.level()[i] / 4096F, p.flags()[i], p.vx()[i], p.vz()[i], p.film()[i]);
                columns.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(c);
                all.add(c);
            }
            // as the client does once a chunk comes: which cells lie on the ground
            for (Cell c : all) LiquidSurfaceMesh.classify(this, c);
        }

        @Override
        public LiquidSurfaceMesh.Cell[] column(int x, int z) {
            List<Cell> at = columns.get(key(x, z));
            return at == null ? null : at.toArray(new LiquidSurfaceMesh.Cell[0]);
        }

        @Override
        public double ground(double x, double z, float level) {
            return ground.at(x, z);
        }

        Cell at(int x, int z) {
            List<Cell> c = columns.get(key(x, z));
            return c == null ? null : c.get(0);
        }
    }

    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    static Client client(LiquidEngine e) {
        Client c = new Client(WaterMeshCheck::ground);
        for (int cx = 0; cx <= 1; cx++) c.receive(LiquidPackets.Surface.of(cx, 0, LiquidPackets.Surface.shown(e.wetCells(cx, 0))));
        return c;
    }

    public static void main(String[] args) {
        SyntheticGround g = new SyntheticGround(0, 0, 28, 6, WaterMeshCheck::ground);
        LiquidEngine e = new LiquidEngine(g, 10, 30);
        for (int cx = 0; cx <= 1; cx++) e.loadChunk(cx, 0, null);
        // the pools: the upper one to its brim (22.6) and over it, the lower one 0.4 m deep
        for (int x = 1; x < 5; x++) for (int z = 0; z < 6; z++) e.add(x, 22.1, z, 640_000);
        for (int x = 21; x < 27; x++) for (int z = 0; z < 6; z++) e.add(x, 19.1, z, 400_000);
        int[] digests = new int[2];
        LiquidPackets.Surface[] last = new LiquidPackets.Surface[2];
        // what changed: level, flags, velocity, film
        int[] fields = new int[4];
        int changes = 0, slopeChanges = 0, syncs = 0;
        for (int t = 0; t < 2400; t++) {
            // 2 L/s into the upper pool
            e.add(2, 22.1, 2, 100);
            e.tick();
            if (t >= 1800 && t % 2 == 0) {
                for (int cx = 0; cx <= 1; cx++) {
                    LiquidPackets.Surface p = LiquidPackets.Surface.of(cx, 0, LiquidPackets.Surface.shown(e.wetCells(cx, 0)));
                    int digest = digest(p);
                    if (syncs >= 2 && digest != digests[cx]) {
                        changes++;
                        // the slope's cells alone (the lower pool rises as it fills: a real change)
                        LiquidPackets.Surface q = last[cx];
                        boolean[] f = new boolean[4];
                        for (int i = 0; i < p.column().length; i++) {
                            int x = (cx << 4) + ((p.column()[i] >> 4) & 15), z = p.column()[i] & 15;
                            if (z != DITCH_Z || x < SLOPE_FROM || x >= SLOPE_TO) continue;
                            for (int k = 0; k < q.column().length; k++) {
                                if (q.column()[k] != p.column()[i] || q.floor()[k] != p.floor()[i]) continue;
                                f[0] |= q.level()[k] != p.level()[i];
                                f[1] |= q.flags()[k] != p.flags()[i];
                                f[2] |= q.vx()[k] != p.vx()[i] || q.vz()[k] != p.vz()[i];
                                f[3] |= q.film()[k] != p.film()[i];
                            }
                        }
                        boolean any = false;
                        for (int k = 0; k < 4; k++) { if (f[k]) fields[k]++; any |= f[k]; }
                        if (any) slopeChanges++;
                    }
                    digests[cx] = digest;
                    last[cx] = p;
                    syncs++;
                }
            }
        }
        List<String> problems = e.verify();
        check(problems.isEmpty(), "the ledger holds to the millilitre (" + (problems.isEmpty() ? "ok" : problems.get(0)) + ")");

        // ---- A1: the slope's cells sent -------------------------------------------------------------------------------
        int slopeCells = 0, sent = 0, thin = 0, running = 0;
        StringBuilder row = new StringBuilder();
        for (int x = SLOPE_FROM; x < SLOPE_TO; x++) {
            LiquidColumn col = e.column(x, DITCH_Z);
            LiquidCell w = null;
            for (LiquidCell c : col.cells()) if (c.volume() > 0) w = c;
            slopeCells++;
            if (w == null) { row.append("    -"); continue; }
            boolean s = LiquidPackets.Surface.sends(w);
            if (s) sent++;
            if (w.running()) running++;
            if (w.level() - w.floor() < 0.008) thin++;
            row.append(String.format(java.util.Locale.ROOT, " %4.1f", w.volume() / 1000.0));
        }
        System.out.printf(java.util.Locale.ROOT, "     the slope's cells, L (mean depth mm):%n    %s%n", row);
        check(sent == slopeCells, "every cell of the slope is sent (" + sent + " of " + slopeCells + ", " + thin + " of them under 8 mm deep)");
        check(running == slopeCells, "every cell of the slope runs (" + running + " of " + slopeCells + ")");

        // ---- A2: the surface --------------------------------------------------------------------------------------------
        Client c = client(e);
        int drapes = 0;
        for (int x = SLOPE_FROM; x < SLOPE_TO; x++) {
            Cell w = c.at(x, DITCH_Z);
            if (w != null && w.drapes()) drapes++;
        }
        check(drapes == slopeCells, "every cell of the slope is drawn on the ground (" + drapes + " of " + slopeCells + ")");
        StringBuilder speeds = new StringBuilder();
        for (int x = SLOPE_FROM; x < SLOPE_TO; x++) {
            Cell w = c.at(x, DITCH_Z);
            speeds.append(w == null ? "    -" : String.format(java.util.Locale.ROOT, " %4.2f", Math.sqrt(w.vx * w.vx + w.vz * w.vz)));
        }
        System.out.printf(java.util.Locale.ROOT, "     the slope's speeds as sent, m/s:%n    %s%n", speeds);

        LiquidSurfaceMesh.Cell[] scratch = new LiquidSurfaceMesh.Cell[4];
        Map<Cell, LiquidSurfaceMesh.Vertex[]> mesh = new HashMap<>();
        for (Cell w : c.all) {
            LiquidSurfaceMesh.Vertex[] v = new LiquidSurfaceMesh.Vertex[9];
            for (int j = 0; j < 3; j++)
                for (int i = 0; i < 3; i++) LiquidSurfaceMesh.vertex(c, w, i, j, scratch, v[j * 3 + i] = new LiquidSurfaceMesh.Vertex());
            mesh.put(w, v);
        }
        // shared vertices: across each linked side the three on the edge, from both cells
        int shared = 0, unequal = 0, border = 0, borderUnequal = 0;
        int[][] east = {{2, 0}, {2, 1}, {2, 2}}, south = {{0, 2}, {1, 2}, {2, 2}};
        for (Cell w : c.all) {
            for (int side = 0; side < 2; side++) {
                int bit = side == 0 ? LiquidCell.EAST : LiquidCell.SOUTH;
                LiquidSurfaceMesh.Cell o = LiquidSurfaceMesh.across(c, w, bit);
                if (o == null) continue;
                LiquidSurfaceMesh.Vertex[] a = mesh.get(w), b = mesh.get((Cell) o);
                for (int[] ij : side == 0 ? east : south) {
                    int oi = side == 0 ? 0 : ij[0], oj = side == 0 ? ij[1] : 0;
                    float ha = a[ij[1] * 3 + ij[0]].height, hb = b[oj * 3 + oi].height;
                    boolean atBorder = side == 0 && w.x == 15;
                    shared++;
                    if (atBorder) border++;
                    if (Float.floatToIntBits(ha) != Float.floatToIntBits(hb)) {
                        unequal++;
                        if (atBorder) borderUnequal++;
                        if (unequal <= 6) System.out.printf(java.util.Locale.ROOT, "     unequal at (%.1f %.1f): %.5f / %.5f%n",
                                w.x + ij[0] * 0.5, w.z + ij[1] * 0.5, ha, hb);
                    }
                }
            }
        }
        check(shared > 0 && unequal == 0, "every shared vertex is the same from both cells (" + unequal + " of " + shared + " not)");
        check(border > 0 && borderUnequal == 0, "across the chunk border too (" + borderUnequal + " of " + border + " not)");

        // the slope's vertices on the ground, as thick as the film (1 cm); none under the ground
        double worst = 0, under = 0;
        StringBuilder heights = new StringBuilder();
        for (int x = SLOPE_FROM + 1; x < SLOPE_TO - 1; x++) {
            Cell w = c.at(x, DITCH_Z);
            if (w == null) continue;
            LiquidSurfaceMesh.Vertex[] v = mesh.get(w);
            for (int k = 0; k < 9; k++) {
                double px = x + (k % 3) * 0.5, pz = DITCH_Z + (k / 3) * 0.5;
                // as the surface reads it: from inside the higher (west) cell on a shared edge
                double gr = ground(px == Math.floor(px) ? px - 0.02 : px, Math.min(Math.max(pz, DITCH_Z + 0.02), DITCH_Z + 0.98));
                double above = v[k].height - gr;
                worst = Math.max(worst, Math.abs(above - Math.max(LiquidSurfaceMesh.MIN_FILM, w.film)));
                under = Math.min(under, above);
            }
            heights.append(String.format(java.util.Locale.ROOT, " %4.1f", (v[4].height - ground(x + 0.5, DITCH_Z + 0.5)) * 1000));
            if (System.getProperty("afl.trace") != null) System.out.printf(java.util.Locale.ROOT, "       x%d level %.4f film %.4f top %.4f ground %.4f..%.4f%n", x, w.level(), w.film, w.drapeTop, ground(x + 0.02, DITCH_Z + 0.5), ground(x + 0.98, DITCH_Z + 0.5));
        }
        System.out.printf(java.util.Locale.ROOT, "     the slope's surface over the ground at each cell's centre, mm:%n    %s%n", heights);
        check(worst <= 0.01, String.format(java.util.Locale.ROOT, "the slope's vertices lie on the ground as thick as the film, within 1 cm (%.4f m off at most)", worst));
        check(under >= 0, String.format(java.util.Locale.ROOT, "no vertex of the slope under the ground (%.4f m)", under));

        // the pools level inside (their middles)
        double spread = 0;
        for (int[] p : new int[][]{{2, 3}, {3, 4}, {24, 1}, {25, 4}}) {
            Cell w = c.at(p[0], p[1]);
            if (w == null) { spread = Double.MAX_VALUE; continue; }
            LiquidSurfaceMesh.Vertex[] v = mesh.get(w);
            for (LiquidSurfaceMesh.Vertex x : v) spread = Math.max(spread, Math.abs(x.height - w.level()));
        }
        check(spread < 0.005, String.format(java.util.Locale.ROOT, "the pools' surfaces are level inside (%.4f m)", spread));
        float foamMax = 0;
        for (LiquidSurfaceMesh.Vertex[] v : mesh.values()) for (LiquidSurfaceMesh.Vertex x : v) foamMax = Math.max(foamMax, x.foam);
        System.out.printf(java.util.Locale.ROOT, "     white water at most %.2f; steady flow: %d of %d chunk syncs changed, %d of them on the slope (level %d, flags %d, velocity %d, film %d)%n",
                foamMax, changes, syncs - 2, slopeChanges, fields[0], fields[1], fields[2], fields[3]);

        // ---- the feed stopped: the ditch drains to the film the floor holds, which is not sent ---------------------------
        for (int t = 0; t < 6000; t++) e.tick();
        int left = 0, sentAfter = 0;
        for (int x = SLOPE_FROM; x < SLOPE_TO; x++) {
            for (LiquidCell w : e.column(x, DITCH_Z).cells()) {
                if (w.volume() <= 0) continue;
                left++;
                if (LiquidPackets.Surface.sends(w)) sentAfter++;
            }
        }
        check(sentAfter == 0, "drained, the film the floor holds on the slope is not sent (" + sentAfter + " of " + left + " wet cells)");

        rainOnGrass();
        creativePit();
        wallsAndBank();
        dugNotch();
        loggedDig();

        System.out.printf("waterMeshCheck: %d checks, %d failures%n", checks, failures);
        if (failures > 0) System.exit(1);
    }

    /**
     * Rain on a field (a 2 % fall, gently uneven): while it rains and its runoff drains, the water that runs is sent; once
     * drained, the films the floor holds are not (no sheet of water on grass after rain, 2026-10-24).
     */
    private static void rainOnGrass() {
        SyntheticGround g = new SyntheticGround(0, 0, 16, 16, (x, z) -> 20 + 0.02 * (16 - x) + 0.03 * Math.sin(x * 1.3) * Math.cos(z * 0.9));
        LiquidEngine e = new LiquidEngine(g, 10, 30);
        e.loadChunk(0, 0, null);
        e.setRain(new LiquidEngine.RainField() {
            public double mmPerHour(int x, int z) { return 15; }
            public int minX() { return 0; }
            public int minZ() { return 0; }
            public int maxX() { return 16; }
            public int maxZ() { return 16; }
        });
        int ticks = (int) LiquidUnits.TICKS_PER_GAME_HOUR;
        for (int t = 0; t < ticks; t++) e.tick();
        int[] during = films(e);
        e.setRain(null);
        // the runoff goes on after the rain (sent: it runs) until the field has drained to what its floor holds
        int drained = -1;
        for (int t = 0; t < 12 * ticks && drained < 0; t++) {
            e.tick();
            if (t % 100 == 99 && films(e)[1] == 0) drained = t + 1;
        }
        int[] after = films(e);
        System.out.printf(java.util.Locale.ROOT, "     rain on grass, 15 mm an hour for %d ticks: %d wet cells, %d thin ones sent (running) while it rains; the runoff drained %d ticks after it stopped%n",
                ticks, during[0], during[1], drained);
        check(drained > 0 && after[1] == 0, "once the runoff has drained no thin film on the field is sent (" + after[1] + " of " + after[0] + " wet cells, " + drained + " ticks)");
    }

    /** The creative pit's depth in whole blocks at a column: 1 or 2 m, unevenly; 0 outside it (x 4-11, z 4-10). */
    static int pitDepth(int x, int z) {
        if (x < 4 || x >= 12 || z < 4 || z >= 11) return 0;
        return (x * 7 + z * 3) % 4 == 0 || x >= 9 && z >= 8 ? 2 : 1;
    }

    static double pitGround(double x, double z) {
        return 25 - pitDepth((int) Math.floor(x), (int) Math.floor(z));
    }

    /**
     * A pit dug block by block (uneven, 1 and 2 m deep) in a flat field: {@code fill} 70 m³ into it as the dev command does,
     * then 50 L a tick poured into one column until it spills over the rim onto the grass. Every 10 ticks, every vertex of
     * every cell sent: never more than {@link LiquidSurfaceMesh#STEP} + {@link LiquidSurfaceMesh#FILM_MAX} above the highest
     * level round it (a film on ground up to half a metre above its water's level is the most), and a vertex drawn on the
     * ground never more than FILM_MAX above that ground unless the water's level round it is higher. Also reported: how many cells ran fast and deep (the ones the old
     * rule drew as films).
     */
    private static void creativePit() {
        SyntheticGround g = new SyntheticGround(0, 0, 16, 16, WaterMeshCheck::pitGround);
        LiquidEngine e = new LiquidEngine(g, 10, 30);
        e.loadChunk(0, 0, null);
        int took = e.fill(7, pitGround(7.5, 7.5) + 0.3, 7, 70_000_000);
        double worstAbove = 0, worstFilm = 0;
        int frames = 0, fastDeep = 0, draped = 0, spilled = 0;
        LiquidSurfaceMesh.Cell[] members = new LiquidSurfaceMesh.Cell[4];
        LiquidSurfaceMesh.Vertex v = new LiquidSurfaceMesh.Vertex();
        for (int t = 0; t < 1600; t++) {
            if (t < 1200) e.add(6, pitGround(6.5, 6.5) + 0.2, 6, 50_000);
            e.tick();
            if (t % 10 != 9) continue;
            frames++;
            Client c = new Client(WaterMeshCheck::pitGround);
            c.receive(LiquidPackets.Surface.of(0, 0, LiquidPackets.Surface.shown(e.wetCells(0, 0))));
            for (Cell w : c.all) {
                if (w.running && w.film >= LiquidSurfaceMesh.FILM_MAX && w.vx * w.vx + w.vz * w.vz >= LiquidSurfaceMesh.DRAPE_SPEED * LiquidSurfaceMesh.DRAPE_SPEED) fastDeep++;
                if (w.drapes()) draped++;
                if (pitDepth(w.x, w.z) == 0) spilled++;
                for (int j = 0; j < 3; j++) {
                    for (int i = 0; i < 3; i++) {
                        java.util.Arrays.fill(members, null);
                        LiquidSurfaceMesh.vertex(c, w, i, j, members, v);
                        float hi = -Float.MAX_VALUE;
                        for (LiquidSurfaceMesh.Cell m : members) if (m != null) hi = Math.max(hi, m.level());
                        worstAbove = Math.max(worstAbove, v.height - hi);
                        if (v.draped) {
                            double px = w.x + i * 0.5, pz = w.z + j * 0.5, gr = -Double.MAX_VALUE;
                            // the highest ground round the point, from inside each draping member's column
                            for (LiquidSurfaceMesh.Cell m : members) {
                                if (m == null || !m.drapes()) continue;
                                gr = Math.max(gr, pitGround(Math.min(Math.max(px, m.x + 0.02), m.x + 0.98), Math.min(Math.max(pz, m.z + 0.02), m.z + 0.98)));
                            }
                            // lifted by the film rule no more than FILM_MAX over that ground (a mean of levels may stand higher)
                            worstFilm = Math.max(worstFilm, v.height - Math.max(hi, gr + LiquidSurfaceMesh.FILM_MAX));
                        }
                    }
                }
            }
        }
        List<String> problems = e.verify();
        System.out.printf(java.util.Locale.ROOT, "     creative pit: fill took %d cells; over %d frames %d cells ran fast and deep (drawn level now), %d drawn on the ground, %d cell-frames on the grass outside the pit%n",
                took, frames, fastDeep, draped, spilled);
        check(problems.isEmpty(), "creative pit: the ledger holds to the millilitre (" + (problems.isEmpty() ? "ok" : problems.get(0)) + ")");
        check(spilled > 0, "creative pit: the pour spills over the rim onto the grass (" + spilled + " cell-frames)");
        check(worstAbove <= LiquidSurfaceMesh.STEP + LiquidSurfaceMesh.FILM_MAX, String.format(java.util.Locale.ROOT,
                "creative pit: no vertex more than %.2f m above the water round it (%.3f m at most)", LiquidSurfaceMesh.STEP + LiquidSurfaceMesh.FILM_MAX, worstAbove));
        check(worstFilm <= 1e-3, String.format(java.util.Locale.ROOT,
                "creative pit: a vertex drawn on the ground stands at most %.0f mm above it, or at the water's level round it (%.1f mm over at most)", LiquidSurfaceMesh.FILM_MAX * 1000, worstFilm * 1000));
    }

    /** The pool (x 3-10, z 4-10, floor 24), a ledge 0.3 m up its north wall (z 3), a smooth bank east (x 11-12 up to 25). */
    static double bankGround(double x, double z) {
        if (x < 3 || x >= 13 || z < 3 || z >= 11) return 25;
        if (x >= 11) return 24 + 0.5 * (x - 11);
        return z < 4 ? 24.3 : 24.0;
    }

    /**
     * A pool filled to about 24.15 with a ledge 0.3 m up its north wall and a smooth bank east, then 3 L a tick poured onto
     * the ledge so it runs off into the pool. Every 10 ticks: no vertex of a cell drawn level stands more than 1 cm above
     * that cell's level (water does not climb the ledge's wall nor the bank); reported: the ledge's cells drawn on the
     * ground, the bank's cells that are the pool's shore.
     */
    private static void wallsAndBank() {
        SyntheticGround g = new SyntheticGround(0, 0, 16, 16, WaterMeshCheck::bankGround);
        LiquidEngine e = new LiquidEngine(g, 10, 30);
        e.loadChunk(0, 0, null);
        int took = e.fill(6, 24.05, 6, 9_000_000);
        double worst = 0;
        int frames = 0, over = 0, ledgeDraped = 0, bankDraped = 0, bankWet = 0;
        LiquidSurfaceMesh.Cell[] members = new LiquidSurfaceMesh.Cell[4];
        LiquidSurfaceMesh.Vertex v = new LiquidSurfaceMesh.Vertex();
        for (int t = 0; t < 1000; t++) {
            if (t < 600) e.add(6, 24.4, 3, 3_000);
            e.tick();
            if (t % 10 != 9) continue;
            frames++;
            Client c = new Client(WaterMeshCheck::bankGround);
            c.receive(LiquidPackets.Surface.of(0, 0, LiquidPackets.Surface.shown(e.wetCells(0, 0))));
            for (Cell w : c.all) {
                if (w.z == 3 && w.x >= 3 && w.x < 11 && w.drapes()) ledgeDraped++;
                if (w.x >= 11 && w.x < 13 && w.z >= 4 && w.z < 11) { bankWet++; if (w.drapes()) bankDraped++; }
                if (w.drapes()) continue;
                for (int j = 0; j < 3; j++) {
                    for (int i = 0; i < 3; i++) {
                        LiquidSurfaceMesh.vertex(c, w, i, j, members, v);
                        double above = v.height - w.level();
                        if (above > 0.01) over++;
                        worst = Math.max(worst, above);
                    }
                }
            }
        }
        List<String> problems = e.verify();
        System.out.printf(java.util.Locale.ROOT, "     walls and bank: fill took %d cells; over %d frames the ledge's cells drawn on the ground %d, the bank's wet cells %d (%d of them drawn on the ground)%n",
                took, frames, ledgeDraped, bankWet, bankDraped);
        check(problems.isEmpty(), "walls and bank: the ledger holds to the millilitre (" + (problems.isEmpty() ? "ok" : problems.get(0)) + ")");
        check(ledgeDraped > 0, "walls and bank: the water poured on the ledge runs on it as a film (" + ledgeDraped + " cell-frames)");
        check(over == 0, String.format(java.util.Locale.ROOT,
                "walls and bank: no vertex of water drawn level more than 1 cm above its level (%d over, %.3f m at most)", over, worst));
    }

    /** Whether the notch is dug yet (dugNotch). */
    static boolean notchDug;

    /**
     * A pond (x 3-10, z 4-10, floor 24) in walls up to 26; once dug, column (11, 7) in its east wall is a slope as the
     * smoothed ground draws a dug block: 25.2 at the pond's edge (under the water) up to 26.0 at its back.
     */
    static double notchGround(double x, double z) {
        if (x >= 3 && x < 11 && z >= 4 && z < 11) return 24;
        // a V groove down the slope, as the smoothed ground draws a block dug out of a wall: little room at its bottom
        if (notchDug && x >= 11 && x < 12 && z >= 7 && z < 8) return Math.min(26, 25.2 + 0.8 * (x - 11) + 1.6 * Math.abs(z - 7.5));
        return 26;
    }

    /**
     * A still pond at 25.5, then a block dug in its wall at the waterline (the smoothed slope of notchGround): the pond runs
     * into the bottom of the slope. Every tick for 300: no vertex of the notch's water stands more than 2 cm above the
     * pond (M1 round 4, 2026-10-26: "挖掉方块，水会往上覆盖" — the water entering from below was drawn as a film over the
     * whole slope, up to 0.75 m above the pond). The ground is asked as the game asks it: from 0.75 m above the level down.
     */
    private static void dugNotch() {
        notchDug = false;
        SyntheticGround g = new SyntheticGround(0, 0, 16, 16, WaterMeshCheck::notchGround);
        LiquidEngine e = new LiquidEngine(g, 10, 30);
        e.loadChunk(0, 0, null);
        e.fill(6, 24.05, 6, 84_000_000);
        for (int t = 0; t < 1500; t++) e.tick();
        notchDug = true;
        g.shape(11, 7, 12, 8, (x, z, old) -> notchGround(x, z));
        e.groundChanged(10, 6, 12, 8);
        SyntheticGround.Height asked = (x, z) -> notchGround(x, z);
        double worst = -1;
        int wet = 0, draped = 0;
        LiquidSurfaceMesh.Cell[] members = new LiquidSurfaceMesh.Cell[4];
        LiquidSurfaceMesh.Vertex v = new LiquidSurfaceMesh.Vertex();
        for (int t = 0; t < 300; t++) {
            e.tick();
            Client c = new Client(asked) {
                @Override
                public double ground(double x, double z, float level) {
                    double h = notchGround(x, z);
                    return h > level + 0.75 ? Double.NaN : h;
                }
            };
            c.receive(LiquidPackets.Surface.of(0, 0, LiquidPackets.Surface.shown(e.wetCells(0, 0))));
            float pond = -Float.MAX_VALUE;
            for (Cell w : c.all) if (w.x < 11) pond = Math.max(pond, w.level());
            if (t < 40 && System.getProperty("afl.trace") != null) for (Cell w : c.all) if (w.x == 11 && w.z == 7) System.out.printf(java.util.Locale.ROOT, "       t%d notch level %.4f pond %.4f film %.3f running %s drape %s%n", t, w.level(), pond, w.film, w.running, w.drapes());
            for (Cell w : c.all) {
                if (w.x != 11 || w.z != 7) continue;
                wet++;
                if (w.drapes()) draped++;
                for (int j = 0; j < 3; j++) {
                    for (int i = 0; i < 3; i++) {
                        java.util.Arrays.fill(members, null);
                        LiquidSurfaceMesh.vertex(c, w, i, j, members, v);
                        worst = Math.max(worst, v.height - pond);
                    }
                }
            }
        }
        notchDug = false;
        List<String> problems = e.verify();
        System.out.printf(java.util.Locale.ROOT, "     dug notch: the notch wet %d of 300 ticks, drawn on the ground %d; its highest vertex %.3f m above the pond%n", wet, draped, worst);
        check(problems.isEmpty(), "dug notch: the ledger holds to the millilitre (" + (problems.isEmpty() ? "ok" : problems.get(0)) + ")");
        check(wet > 0, "dug notch: the pond runs into the dug slope (" + wet + " ticks)");
        check(worst <= 0.02, String.format(java.util.Locale.ROOT, "dug notch: the water entering the slope from below is not drawn up it (%.3f m above the pond at most)", worst));
    }

    /** The blocks' tops round the dig of round 5 (x 696-700, z 1224-1229): the pond and the dug columns 129, walls 130. */
    static double loggedTop(int x, int z) {
        boolean pond = x >= 697 && z >= 1227 || x >= 698 && z >= 1225 || x == 697 && z == 1226;
        return pond ? 129 : 130;
    }

    /** The smoothed ground there: each column's top, rising within 0.25 m of a higher neighbour to meet it. */
    static double loggedGround(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        double h = loggedTop(bx, bz), fx = x - bx, fz = z - bz;
        h = Math.max(h, loggedTop(bx - 1, bz) - 4 * fx);
        h = Math.max(h, loggedTop(bx + 1, bz) - 4 * (1 - fx));
        h = Math.max(h, loggedTop(bx, bz - 1) - 4 * fz);
        h = Math.max(h, loggedTop(bx, bz + 1) - 4 * (1 - fz));
        return h;
    }

    /**
     * The cells the client logged a tick after a block was dug beside the pond in game (M1 round 5, 2026-10-26), as they
     * came: the dug column (697, 1226) holding 8 mm and running, its smoothed ground rising to the wall's top at its edges.
     * It was drawn up to 130.004, 0.996 m over its water, and its neighbours' shared vertices with it: "很白的水面往上覆盖".
     * No vertex of these cells may stand more than 2 cm above the highest of their levels.
     */
    private static void loggedDig() {
        Client c = new Client(WaterMeshCheck::loggedGround) {
            @Override
            public double ground(double x, double z, float level) {
                // as the game answered (the log: 130.0 found for water at 129.008): the ground, however high
                return loggedGround(x, z);
            }
        };
        int run = LiquidPackets.RUNNING, sh = LiquidPackets.SIDES_SHIFT;
        Object[][] logged = {
                {697, 1226, 2064, 129.0081, 3, 0.00, -0.30, 0.001}, {697, 1227, 2064, 129.6091, 11, 0.02, -0.14, 0.236},
                {698, 1225, 2064, 129.3875, 3, -0.10, 0.18, 0.146}, {698, 1226, 2064, 129.0481, 15, -0.14, -0.04, 0.048},
                {698, 1227, 2064, 129.5581, 15, 0.02, -0.14, 0.558}, {699, 1226, 2064, 129.5581, 15, -0.14, 0.02, 0.558},
                {699, 1227, 2060, 129.5205, 15, -0.02, -0.02, 0.602}, {699, 1225, 2064, 129.6094, 15, 0.0, 0.0, 0.6},
                {697, 1228, 2064, 129.6133, 15, 0.0, 0.0, 0.6}, {698, 1228, 2064, 129.6125, 15, 0.0, 0.0, 0.6},
                {700, 1226, 2064, 129.6125, 15, 0.0, 0.0, 0.6}, {700, 1227, 2064, 129.6116, 15, 0.0, 0.0, 0.6}};
        List<Cell> cells = new ArrayList<>();
        for (Object[] l : logged) {
            Cell w = new Cell((Integer) l[0], (Integer) l[1], (Integer) l[2], (float) (double) (Double) l[3], (Integer) l[4] << sh | run,
                    (short) Math.round((Double) l[5] * LiquidPackets.Surface.VELOCITY), (short) Math.round((Double) l[6] * LiquidPackets.Surface.VELOCITY),
                    (short) Math.round((Double) l[7] * 1000));
            c.columns.computeIfAbsent(key(w.x, w.z), k -> new ArrayList<>()).add(w);
            c.all.add(w);
            cells.add(w);
        }
        for (Cell w : c.all) LiquidSurfaceMesh.classify(c, w);
        float top = -Float.MAX_VALUE;
        for (Cell w : cells) top = Math.max(top, w.level());
        double worst = -1;
        String at = "";
        LiquidSurfaceMesh.Cell[] members = new LiquidSurfaceMesh.Cell[4];
        LiquidSurfaceMesh.Vertex v = new LiquidSurfaceMesh.Vertex();
        for (Cell w : cells) {
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    java.util.Arrays.fill(members, null);
                    LiquidSurfaceMesh.vertex(c, w, i, j, members, v);
                    if (v.height - top > worst) {
                        worst = v.height - top;
                        at = "(" + w.x + "," + w.z + ")";
                    }
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT, "     logged dig: the dug cell drawn on the ground %s; the highest vertex %.3f m over the highest level, at %s%n", c.at(697, 1226).drapes(), worst, at);
        check(worst <= 0.02, String.format(java.util.Locale.ROOT, "logged dig: no water drawn up the dug block's slope (%.3f m over the highest level at most)", worst));
    }

    /** The wet cells, and the thin (under 8 mm) ones sent. */
    private static int[] films(LiquidEngine e) {
        int wet = 0, sent = 0;
        for (LiquidCell c : e.wetCells(0, 0)) {
            if (c.boundary()) continue;
            wet++;
            if (c.level() - c.floor() < 0.008 && LiquidPackets.Surface.sends(c)) sent++;
        }
        return new int[]{wet, sent};
    }

    private static int digest(LiquidPackets.Surface p) {
        int h = java.util.Arrays.hashCode(p.column());
        h = 31 * h + java.util.Arrays.hashCode(p.floor());
        h = 31 * h + java.util.Arrays.hashCode(p.level());
        h = 31 * h + java.util.Arrays.hashCode(p.flags());
        h = 31 * h + java.util.Arrays.hashCode(p.vx());
        h = 31 * h + java.util.Arrays.hashCode(p.vz());
        return 31 * h + java.util.Arrays.hashCode(p.film());
    }
}
