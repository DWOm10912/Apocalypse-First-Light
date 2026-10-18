package com.antaurora.apofirstlight.vehicle;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;

/**
 * The T15's lamp beams as block light (docs/vehicles/first_pickup_v0_design.md 8.10): a fan of rays from each lamp
 * through the world's cells finds the cells the lamp sees; a seen cell's light is worked out from where the cell is in
 * the beam (its middle's distance and its angles off the beam's middle), not from the ray that reached it, so rays
 * crossing a cell at different angles give it one value and the pool has no streaks. Cells between seen ones that no ray
 * passed (the rays spread apart far out) are filled the same way. The lamps' light adds up per cell as it does in the
 * world (two headlamps, low and high beam) and is rounded to block light levels once, at the end ({@link #levels}), so
 * where beams overlap there is no ripple of rounding.
 * <p>A full opaque block stops a ray unlit (its faces take the light of the open cell in front of them, as vanilla
 * light); a partial solid block (a road_surface layer, a slab) takes the light itself (its top face is lit from its own
 * cell) and stops the ray where the ray meets its solid top (World#surfaceTop): over a layer the lamps sit in, the beam
 * goes on. Pure: the client (client/vehicle/HeadlightField) and the offline check (HeadlightBeamCheck) give the cells.
 * <p>One lamp's light (user 2026-10-17: the pool must be smooth, its edges soft): {@link Pattern#near} within
 * {@link #NEAR} m, falling with the log of the distance to {@link Pattern#far} at {@link #FAR_SHARE} of the reach and from
 * there smoothly to nothing at the reach; across, times 1 - share² of the half spread (nothing at the edge), the spread
 * measured from {@link #BACK} behind the lamp (a reflector is not a point: the pool is wide just in front of the bumper);
 * above the aim, times 1 - share² of the span above (a soft cut-off), below it a little less toward the bottom. So every
 * edge of the pool fades out over a few cells. The low beam reaches {@link Pattern#rightReach} farther on its right edge
 * (the kerb side, as a real asymmetric low beam), and is aimed {@link Pattern#aimDown} deg down.
 */
public final class HeadlightBeam {
    /** What a cell does to the beam. */
    public enum Cell {
        /** Lets it through and is lit (air, plants, glass panes would be OPAQUE-free too). */
        OPEN,
        /** Takes it on its own surface (lit) and stops it: a partial solid block. */
        SURFACE,
        /** Stops it unlit: a full opaque block (its faces read the open cell in front). */
        OPAQUE
    }

    /** The world's cells for the beam. */
    public interface World {
        Cell cell(int x, int y, int z);

        /** A SURFACE cell's solid top (share of the cell, 0-1): the beam passes over it in the open part above. */
        default double surfaceTop(int x, int y, int z) { return 1.0; }
    }

    /**
     * One lamp's beam: aim below the lamp's forward (deg); half spread across, the span below and above the aim (deg);
     * reach (m); its light (block light levels, added over the lamps) near the lamp and at {@link #FAR_SHARE} of the reach;
     * the extra reach on the right edge (share).
     */
    public record Pattern(double aimDown, double across, double below, double above, double reach, double near, double far, double rightReach) {}

    /** Per lamp: a pair adds up to about 13 a few metres ahead and 7 at 28 m (low); the high beam adds little near and about 8 at 49 m. */
    public static final Pattern LOW = new Pattern(1.2, 24, 9, 5.0, 40, 7.5, 3.6, 0.15),
            HIGH = new Pattern(0.0, 14, 5, 5, 70, 3, 4.5, 0),
    /** A reverse lamp: short, wide and low, behind (cast backward from the rear). */
            REVERSE = new Pattern(2, 40, 12, 6, 9, 4.5, 2.2, 0);
    /** Full light this close (m); rays start this far out (inside the bumper); where the far light is (share of the reach). */
    public static final double NEAR = 4, START = 0.4, FAR_SHARE = 0.7;
    /** March step along a ray (m); ray spacing across and up (deg). */
    public static final double STEP = 0.3, RAY_ACROSS = 1.5, RAY_UP = 1.2;
    /** The spread across is measured from this far behind the lamp (m). */
    public static final double BACK = 2.5;
    /** How much of the light is lost toward the bottom of the span (share; the top edge loses all of it). */
    public static final double BELOW_LOSS = 0.4;

    private HeadlightBeam() {
    }

    /**
     * One lamp's beam added into {@code out} (cell key BlockPos#asLong -> light, summed over lamps; {@link #levels} rounds
     * it). The lamp at (ox, oy, oz), forward and up unit vectors of the truck (world; forward the way the lamp faces).
     * {@code cache} keeps the world's answers across beams.
     */
    public static void cast(double ox, double oy, double oz, double[] fwd, double[] up, Pattern p, World w, Long2FloatOpenHashMap out,
                            Long2ObjectOpenHashMap<Cell> cache) {
        double[] right = {fwd[1] * up[2] - fwd[2] * up[1], fwd[2] * up[0] - fwd[0] * up[2], fwd[0] * up[1] - fwd[1] * up[0]};
        Beam b = new Beam(ox, oy, oz, fwd, up, right, p);
        // this lamp's cells: seen (its light worked out once, in order), and whether they took any
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayList lit = new LongArrayList();
        LongOpenHashSet litSet = new LongOpenHashSet();
        // the fan starts BACK behind the lamp, at its height: rays spread across from there, as the light does
        double sx = ox - fwd[0] * BACK, sy = oy - fwd[1] * BACK, sz = oz - fwd[2] * BACK;
        // one ray spacing past each edge, so the cells the edge cuts are seen (their light is near none there)
        for (double h = -p.across() - RAY_ACROSS; h <= p.across() + RAY_ACROSS + 1e-6; h += RAY_ACROSS) {
            double hr = Math.toRadians(h);
            double reach = p.reach() * (1 + p.rightReach() * Math.max(0, h / p.across()));
            for (double v = -p.below(); v <= p.above() + 1e-6; v += RAY_UP) {
                double vr = Math.toRadians(v - p.aimDown()), cv = Math.cos(vr);
                double dx = fwd[0] * cv * Math.cos(hr) + right[0] * cv * Math.sin(hr) + up[0] * Math.sin(vr);
                double dy = fwd[1] * cv * Math.cos(hr) + right[1] * cv * Math.sin(hr) + up[1] * Math.sin(vr);
                double dz = fwd[2] * cv * Math.cos(hr) + right[2] * cv * Math.sin(hr) + up[2] * Math.sin(vr);
                ray(b, sx, sy, sz, dx, dy, dz, reach + BACK, w, out, cache, visited, lit, litSet);
            }
        }
        fill(b, w, out, cache, visited, lit, litSet);
    }

    /** Summed light to block light levels (1..15; cells under half a level left out). */
    public static Long2ByteOpenHashMap levels(Long2FloatOpenHashMap light) {
        Long2ByteOpenHashMap out = new Long2ByteOpenHashMap(light.size());
        for (Long2FloatMap.Entry e : light.long2FloatEntrySet()) {
            int level = Math.min(15, Math.round(e.getFloatValue()));
            if (level > 0) out.put(e.getLongKey(), (byte) level);
        }
        return out;
    }

    /** One lamp's frame and pattern. */
    private record Beam(double ox, double oy, double oz, double[] fwd, double[] up, double[] right, Pattern p) {}

    /**
     * One ray from (sx, sy, sz) (behind the lamp) out to {@code reach}: the cells from START past the lamp's own plane on
     * are seen, each once per lamp ({@code visited}).
     */
    private static void ray(Beam b, double sx, double sy, double sz, double dx, double dy, double dz, double reach, World w,
                            Long2FloatOpenHashMap out, Long2ObjectOpenHashMap<Cell> cache, LongOpenHashSet visited,
                            LongArrayList lit, LongOpenHashSet litSet) {
        long last = Long.MIN_VALUE;
        Cell c = Cell.OPEN;
        double top = 1;
        double ahead = dx * b.fwd[0] + dy * b.fwd[1] + dz * b.fwd[2];
        if (ahead <= 0.05) return;
        for (double d = (BACK + START) / ahead; d <= reach; d += STEP) {
            double py = sy + dy * d;
            int x = (int) Math.floor(sx + dx * d), y = (int) Math.floor(py), z = (int) Math.floor(sz + dz * d);
            long key = BlockPos.asLong(x, y, z);
            if (key != last) {
                last = key;
                c = cell(w, cache, x, y, z, key);
                if (c == Cell.OPAQUE) return;
                if (c == Cell.SURFACE) top = w.surfaceTop(x, y, z);
                if (visited.add(key)) {
                    light(b, x, y, z, c == Cell.SURFACE ? top : 0.5, key, out, lit, litSet);
                    // an open cell's light reaches the partial block under it (as vanilla light spreads): a thick road
                    // layer's top face reads its own cell, which a beam skimming over it never enters
                    if (c == Cell.OPEN) {
                        long below = BlockPos.asLong(x, y - 1, z);
                        if (cell(w, cache, x, y - 1, z, below) == Cell.SURFACE && visited.add(below))
                            light(b, x, y - 1, z, w.surfaceTop(x, y - 1, z), below, out, lit, litSet);
                    }
                }
            }
            // into a surface's solid part: stopped there
            if (c == Cell.SURFACE && py - y <= top) return;
        }
    }

    /**
     * The open cells no ray of this lamp passed between two it lit (across or along: the rays spread apart far out), lit
     * the same way. One pass: a gap of one cell closes.
     */
    private static void fill(Beam b, World w, Long2FloatOpenHashMap out, Long2ObjectOpenHashMap<Cell> cache, LongOpenHashSet visited,
                             LongArrayList lit, LongOpenHashSet litSet) {
        int n = lit.size();
        LongArrayList added = new LongArrayList();
        LongOpenHashSet addedSet = new LongOpenHashSet();
        for (int i = 0; i < n; i++) {
            long key = lit.getLong(i);
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            for (int[] s : SIDES) {
                int gx = x + s[0], gz = z + s[1];
                long gap = BlockPos.asLong(gx, y, gz);
                if (visited.contains(gap) || !litSet.contains(BlockPos.asLong(gx + s[0], y, gz + s[1]))) continue;
                Cell c = cell(w, cache, gx, y, gz, gap);
                if (c == Cell.OPAQUE) continue;
                visited.add(gap);
                light(b, gx, y, gz, c == Cell.SURFACE ? w.surfaceTop(gx, y, gz) : 0.5, gap, out, added, addedSet);
            }
        }
    }

    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static Cell cell(World w, Long2ObjectOpenHashMap<Cell> cache, int x, int y, int z, long key) {
        Cell c = cache.get(key);
        if (c == null) {
            c = w.cell(x, y, z);
            cache.put(key, c);
        }
        return c;
    }

    /** A cell's light from the beam, at its middle (height {@code at} in the cell), added to what it has. */
    private static void light(Beam b, int x, int y, int z, double at, long key, Long2FloatOpenHashMap out, LongArrayList lit, LongOpenHashSet litSet) {
        double light = light(b, x + 0.5 - b.ox, y + at - b.oy, z + 0.5 - b.oz);
        if (light <= 0.02) return;
        out.addTo(key, (float) light);
        if (litSet.add(key)) lit.add(key);
    }

    /** The light at a point (relative to the lamp; block light levels), 0 outside the beam. */
    private static double light(Beam b, double vx, double vy, double vz) {
        Pattern p = b.p;
        double f = vx * b.fwd[0] + vy * b.fwd[1] + vz * b.fwd[2];
        if (f <= 0) return 0;
        double r = vx * b.right[0] + vy * b.right[1] + vz * b.right[2], u = vx * b.up[0] + vy * b.up[1] + vz * b.up[2];
        double flat = Math.sqrt(f * f + r * r), d = Math.sqrt(flat * flat + u * u);
        double share = Math.toDegrees(Math.atan2(r, f + BACK)) / p.across();
        if (Math.abs(share) >= 1) return 0;
        double v = Math.toDegrees(Math.atan2(u, flat)) + p.aimDown(), vs = v < 0 ? -v / p.below() : v / p.above();
        if (vs >= 1 && v > 0) return 0;
        double reach = p.reach() * (1 + p.rightReach() * Math.max(0, share)), farAt = reach * FAR_SHARE;
        if (d >= reach) return 0;
        double along;
        if (d <= NEAR) along = 1;
        else if (d <= farAt) along = 1 - (1 - p.far() / p.near()) * Math.log(d / NEAR) / Math.log(farAt / NEAR);
        else {
            double t = (d - farAt) / (reach - farAt);
            along = p.far() / p.near() * (1 - t * t * (3 - 2 * t));
        }
        double across = 1 - share * share;
        double vertical = v > 0 ? 1 - vs * vs : 1 - BELOW_LOSS * Math.min(1, vs * vs);
        return p.near() * along * across * vertical;
    }
}
