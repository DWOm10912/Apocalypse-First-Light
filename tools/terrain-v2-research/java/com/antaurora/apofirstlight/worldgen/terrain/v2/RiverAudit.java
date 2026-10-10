package com.antaurora.apofirstlight.worldgen.terrain.v2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Offline audit of the Phase 2b river water (2026-10-10): builds the plan for a seed, then runs RiverCarver's column
 * rule on the planned surface (generated top = ceil(h) - 1, what the Phase 2 save audit measured for 97.6 % of land
 * columns) over every column a river can touch, and checks the water the way the game would hold it:
 * <ul>
 *   <li>leak: a water block with a horizontal neighbour that is neither water at the same or a higher level nor solid
 *   at that height (the side face would be open air);</li>
 *   <li>riffle: a neighbour with lower water (the generator lets it flow; counted, not an error);</li>
 *   <li>dry: a river column without water; floating: water above air (none by construction: the bed is solid);</li>
 *   <li>uphill: a line whose level rises downstream, a tributary below its parent at the junction;</li>
 *   <li>bank fill / cut volumes (how much the banks change the r1 surface).</li>
 * </ul>
 * <pre>java RiverAudit SEED OUT_DIR [x z label]...</pre>
 * Writes river_audit.md, a national map and 512 m windows round the given points.
 */
public final class RiverAudit {
    public static void main(String[] args) throws Exception {
        long seed = Long.parseLong(args[0]);
        File out = new File(args[1]);
        out.mkdirs();
        long t0 = System.nanoTime();
        TerrainPlanV2 p = new TerrainPlanV2(seed);
        TerrainPlanSurface s = p.toSurface();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        RiverNetwork r = s.rivers;
        StringBuilder md = new StringBuilder();
        md.append("# River audit, seed ").append(seed).append("\n\nPlan + rivers built in ").append(ms).append(" ms; report ")
                .append(p.report).append("\n\n");
        // ---------------------------------------------------------------- network
        double total = 0, mainLen = 0;
        int mains = 0, uphill = 0, junctionLow = 0, drops = 0, maxDrop = 0;
        int mouthsTotal = 0, mouthsWet = 0;
        StringBuilder mouthList = new StringBuilder();
        double[] widthLen = new double[5];    // < 4, 4-8, 8-12, 12-16, >= 16 m
        float maxArea = 0;
        for (int line = 0; line < r.lines(); line++) {
            int a = r.lineStart(line), b = r.lineEnd(line);
            if (r.lineParent(line) < 0) mains++;
            for (int i = a; i < b - 1; i++) {
                double len = Math.hypot(r.vertexX(i + 1) - r.vertexX(i), r.vertexZ(i + 1) - r.vertexZ(i));
                total += len;
                if (r.lineParent(line) < 0) mainLen += len;
                float w = r.vertexWidth(i);
                widthLen[w < 4 ? 0 : w < 8 ? 1 : w < 12 ? 2 : w < 16 ? 3 : 4] += len;
                int d = r.vertexLevel(i) - r.vertexLevel(i + 1);
                if (d < 0) uphill++;
                if (d > 0) { drops++; maxDrop = Math.max(maxDrop, d); }
                maxArea = Math.max(maxArea, r.vertexArea(i));
            }
            int par = r.lineParent(line);
            if (par < 0) {
                // a main stem must end in open water (the sea fill: ground under Y63 in a sea / estuary cell or the low shore)
                int e = b - 1;
                double hx = r.vertexX(e), hz = r.vertexZ(e), hh = s.heightAt(hx, hz);
                int wc = s.waterClass(hx, hz);
                mouthsTotal++;
                if (hh < 63 && s.seaFloodAt(hx, hz, hh)) mouthsWet++;
                else if (mouthList.length() < 600) mouthList.append(String.format(" (%.0f, %.0f h %.1f class %d)", hx, hz, hh, wc));
            }
            if (par >= 0) {
                int end = b - 1;
                int best = -1;
                double bd = Double.MAX_VALUE;
                for (int k = r.lineStart(par); k < r.lineEnd(par); k++) {
                    double dx = r.vertexX(k) - r.vertexX(end), dz = r.vertexZ(k) - r.vertexZ(end), dd = dx * dx + dz * dz;
                    if (dd < bd) { bd = dd; best = k; }
                }
                if (r.vertexLevel(end) < r.vertexLevel(best)) junctionLow++;
            }
        }
        md.append(String.format("## Network%n%n- lines %d (main stems %d), vertices %d%n- river length %.1f km (main stems %.1f km), largest drainage %.1f km2%n"
                        + "- length by width: <4 m %.1f km, 4-8 %.1f, 8-12 %.1f, 12-16 %.1f, >=16 %.1f%n"
                        + "- level drops (riffles) %d, largest %d block(s); uphill steps %d; tributaries below their parent at the junction %d%n%n",
                r.lines(), mains, r.vertices(), total / 1000, mainLen / 1000, maxArea / 1e6,
                widthLen[0] / 1000, widthLen[1] / 1000, widthLen[2] / 1000, widthLen[3] / 1000, widthLen[4] / 1000,
                drops, maxDrop, uphill, junctionLow));
        md.append(String.format("- main stems ending in open water: %d of %d%s%n%n", mouthsWet, mouthsTotal, mouthList.length() > 0 ? "; dry ends:" + mouthList : ""));
        // ---------------------------------------------------------------- column simulation over every touched bucket
        Map<Long, int[]> cols = new HashMap<>();   // key -> {kind, ground, water, level}
        RiverNetwork.Column col = new RiverNetwork.Column();
        RiverCarver.Edit e = new RiverCarver.Edit();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < r.vertices(); i++) {
            minX = Math.min(minX, (int) r.vertexX(i)); maxX = Math.max(maxX, (int) r.vertexX(i));
            minZ = Math.min(minZ, (int) r.vertexZ(i)); maxZ = Math.max(maxZ, (int) r.vertexZ(i));
        }
        int river = 0, bank = 0, pool = 0, dry = 0, filled = 0, cut = 0, maxFill = 0, maxCut = 0;
        long fillVol = 0, cutVol = 0;
        int[] cutHist = new int[6];        // cut 1, 2-3, 4-5, 6-7, 8-9, >= 10
        // marsh pools are everywhere in the marsh: scan the marsh cells' bounding box too
        int[] box = {minX - 32, minZ - 32, maxX + 32, maxZ + 32};
        for (int i = 0; i < TerrainPlanSurface.N * TerrainPlanSurface.N; i++) if (s.water[i] == 3) {
            int c = i % TerrainPlanSurface.N, rr = i / TerrainPlanSurface.N;
            int x = (int) (TerrainPlanSurface.ORIGIN + c * TerrainPlanSurface.CELL), z = (int) (TerrainPlanSurface.ORIGIN + rr * TerrainPlanSurface.CELL);
            box[0] = Math.min(box[0], x - 16); box[1] = Math.min(box[1], z - 16); box[2] = Math.max(box[2], x + 32); box[3] = Math.max(box[3], z + 32);
        }
        for (int bz = box[1]; bz < box[3]; bz += 16) for (int bx = box[0]; bx < box[2]; bx += 16) {
            // cheap reject: a chunk with no river reach and no marsh
            boolean any = false;
            for (int k = 0; k < 9 && !any; k++) {
                int x = bx + (k % 3) * 7 + 1, z = bz + (k / 3) * 7 + 1;
                r.column(x, z, col);
                if (col.kind != RiverNetwork.Column.NONE || s.waterClass(x, z) == 3 || s.waterClass(x + 16, z) == 3 || s.waterClass(x, z + 16) == 3) any = true;
            }
            if (!any) {
                // the 9 probes can miss a narrow river: check against the index reach for the chunk centre
                if (r.edgeDistance(bx + 8, bz + 8) > 14) continue;
            }
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                int x = bx + lx, z = bz + lz;
                int top = (int) Math.ceil(s.heightAt(x, z)) - 1;
                RiverCarver.plan(s, x, z, top, col, e);
                if (e.kind == RiverCarver.NONE) continue;
                cols.put(key(x, z), new int[]{e.kind, e.ground, e.water, e.level});
                if (e.kind == RiverCarver.RIVER) { river++; if (e.water <= e.ground) dry++; }
                else if (e.kind == RiverCarver.POOL) pool++;
                else {
                    bank++;
                    if (e.ground > top) { filled++; fillVol += e.ground - top; maxFill = Math.max(maxFill, e.ground - top); }
                    if (e.ground < top) { cut++; cutVol += top - e.ground; maxCut = Math.max(maxCut, top - e.ground); cutHist[Math.min(5, (top - e.ground) / 2)]++; }
                }
            }
        }
        int leaks = 0, riffles = 0, waterCols = 0;
        StringBuilder leakList = new StringBuilder();
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (Map.Entry<Long, int[]> en : cols.entrySet()) {
            int[] v = en.getValue();
            if (v[2] == Integer.MIN_VALUE) continue;
            waterCols++;
            int x = (int) (en.getKey() >> 32), z = (int) (long) en.getKey().intValue();
            boolean riffle = false;
            for (int[] d : nb) {
                int xx = x + d[0], zz = z + d[1];
                int[] w = cols.get(key(xx, zz));
                int ground, water;
                if (w != null) { ground = w[1]; water = w[2]; }
                else {
                    int wc = s.waterClass(xx, zz);
                    ground = (int) Math.ceil(s.heightAt(xx, zz)) - 1;
                    boolean sea = s.seaFloodAt(xx, zz, s.heightAt(xx, zz));
                    water = sea && ground < RiverNetwork.SEA_WATER_TOP ? RiverNetwork.SEA_WATER_TOP : Integer.MIN_VALUE;
                }
                // every block of this column's water from the ground up must be held: by water as high or higher, or by solid
                int lowestOpen = Math.max(water == Integer.MIN_VALUE ? Integer.MIN_VALUE : water + 1, ground + 1);
                if (lowestOpen <= v[2]) {
                    if (water != Integer.MIN_VALUE && water < v[2] && water >= ground) riffle = true;   // a lower river or the sea beside it
                    else {
                        leaks++;
                        if (leakList.length() < 2400) leakList.append(String.format("  - %d %d: water top %d, neighbour %d %d ground %d water %s (kind %d)%n",
                                x, z, v[2], xx, zz, ground, water == Integer.MIN_VALUE ? "-" : Integer.toString(water), v[0]));
                    }
                }
            }
            if (riffle) riffles++;
        }
        md.append(String.format("## Columns (simulated on the planned surface)%n%n- river %d, bank %d, marsh pool %d; water columns %d%n"
                        + "- dry river columns %d; leaks %d; columns with a riffle beside them %d%n"
                        + "- bank fill: %d columns, %d blocks, max %d; bank cut: %d columns, %d blocks, max %d%n%n",
                river, bank, pool, waterCols, dry, leaks, riffles, filled, fillVol, maxFill, cut, cutVol, maxCut));
        md.append(String.format("- bank cut depth: 1 block %d, 2-3 %d, 4-5 %d, 6-7 %d, 8-9 %d, >=10 %d%n%n",
                cutHist[0], cutHist[1], cutHist[2], cutHist[3], cutHist[4], cutHist[5]));
        if (leaks > 0) md.append("Leaks (first):\n").append(leakList).append('\n');
        // marsh pool share
        int marshCols = 0, marshPool = 0;
        for (int i = 0; i < TerrainPlanSurface.N * TerrainPlanSurface.N; i += 1) if (s.water[i] == 3) {
            int c = i % TerrainPlanSurface.N, rr = i / TerrainPlanSurface.N;
            for (int k = 0; k < 16; k += 4) for (int j = 0; j < 16; j += 4) {
                int x = (int) (TerrainPlanSurface.ORIGIN + c * TerrainPlanSurface.CELL) + j, z = (int) (TerrainPlanSurface.ORIGIN + rr * TerrainPlanSurface.CELL) + k;
                marshCols++;
                if (s.poolAt(x, z) && (int) Math.ceil(s.heightAt(x, z)) - 1 == 63) marshPool++;
            }
        }
        md.append(String.format("Marsh: %d sampled columns, open-water pools %.1f %%%n%n", marshCols, 100.0 * marshPool / Math.max(1, marshCols)));
        // ---------------------------------------------------------------- acceptance points (the same rule as /afl dev terrain_v2 points)
        java.util.List<TerrainV2TestPoints.Point> pts = TerrainV2TestPoints.rivers(s);
        md.append(String.format("## River acceptance points%n%n| point | x | z | plan surface | note |%n|---|---|---|---|---|%n"));
        for (TerrainV2TestPoints.Point pt : pts)
            md.append(String.format("| %s | %d | %d | %.1f | %s |%n", pt.name(), pt.x(), pt.z(), s.heightAt(pt.x(), pt.z()), pt.note()));
        md.append(String.format("%n"));
        // ---------------------------------------------------------------- maps
        double ext = TerrainPlanSurface.N * TerrainPlanSurface.CELL;
        int nw = 2816;
        BufferedImage nat = TerrainPlanMaps.render(s, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, nw, nw, ext / nw);
        for (int k = 2; k + 2 < args.length; k += 3)
            TerrainPlanMaps.mark(nat, TerrainPlanSurface.ORIGIN, TerrainPlanSurface.ORIGIN, ext / nw, Double.parseDouble(args[k]), Double.parseDouble(args[k + 1]), 0xFFE0402A);
        ImageIO.write(nat, "png", new File(out, "national_rivers.png"));
        for (int k = 2; k + 2 < args.length; k += 3) {
            double x = Double.parseDouble(args[k]), z = Double.parseDouble(args[k + 1]);
            BufferedImage win = TerrainPlanMaps.render(s, x - 256, z - 256, 512, 512, 1.0);
            TerrainPlanMaps.mark(win, x - 256, z - 256, 1.0, x, z, 0xFFE0402A);
            ImageIO.write(win, "png", new File(out, "window_" + args[k + 2] + ".png"));
        }
        for (TerrainV2TestPoints.Point pt : pts) {
            BufferedImage win = TerrainPlanMaps.render(s, pt.x() - 256, pt.z() - 256, 512, 512, 1.0);
            TerrainPlanMaps.mark(win, pt.x() - 256, pt.z() - 256, 1.0, pt.x(), pt.z(), 0xFFE0402A);
            ImageIO.write(win, "png", new File(out, "point_" + pt.name() + ".png"));
        }
        try (PrintWriter w = new PrintWriter(new File(out, "river_audit.md"), StandardCharsets.UTF_8)) { w.print(md); }
        System.out.print(md);
    }

    static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
}
