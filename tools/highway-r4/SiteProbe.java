package com.antaurora.apofirstlight.dev.highway.r4;

import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanV2;

import java.util.Locale;

/** Offline (tools/highway-r4/site-probe.mjs): run the game's R4SiteSearch on a seed, as the find command does (minus the road-protection filter). */
public final class SiteProbe {
    public static void main(String[] a) {
        long seed = Long.parseLong(a[0]);
        double cx = Double.parseDouble(a[1]), cz = Double.parseDouble(a[2]);
        TerrainPlanSurface p = new TerrainPlanV2(seed).toSurface();
        for (int i = 3; i < a.length; i++) {
            int radius = Integer.parseInt(a[i]);
            long t = System.nanoTime();
            var r = R4SiteSearch.search(p, cx, cz, radius, 384);
            System.out.printf(Locale.ROOT, "radius %d: %d suitable of %d in %.0f ms; rejected: %s%n", radius, r.candidates().size(), r.evaluated(), (System.nanoTime() - t) / 1e6, r.rejections());
            int k = 0;
            for (var c : r.candidates()) {
                if (k++ >= 3) break;
                System.out.printf(Locale.ROOT, "   mid %.0f %.0f y %.0f  %s  -> plan %.0f %.0f %.0f %.0f %.0f%n", c.midX(), c.midZ(), c.midY(), c.describe(), c.x0() - 0.5, c.z0() - 0.5, c.heading(), c.length(), c.bend());
            }
        }
    }
}
