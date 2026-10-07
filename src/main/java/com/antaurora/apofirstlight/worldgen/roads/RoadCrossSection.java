package com.antaurora.apofirstlight.worldgen.roads;

import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import java.util.List;

/** Absolute, symmetric half-open bands. Negative/positive transverse sides are independent of travel direction. */
public record RoadCrossSection(BoundsXZ corridorBounds, BoundsXZ asphalt, List<BoundsXZ> curbs,
                               List<BoundsXZ> utilities, List<BoundsXZ> sidewalks) {
    public RoadCrossSection {
        curbs = List.copyOf(curbs); utilities = List.copyOf(utilities); sidewalks = List.copyOf(sidewalks);
    }

    public static RoadCrossSection of(int x1, int z1, int x2, int z2, RoadType type) {
        if ((x1 == x2) == (z1 == z2)) throw new IllegalArgumentException("A road must be a nonzero axis-aligned segment");
        int a = type.asphaltWidth() / 2;
        int c = a + type.curbReservation();
        int u = c + type.utilityBand();
        int s = u + type.sidewalkWidth();
        return new RoadCrossSection(band(x1,z1,x2,z2,-s,s), band(x1,z1,x2,z2,-a,a),
                List.of(band(x1,z1,x2,z2,-c,-a), band(x1,z1,x2,z2,a,c)),
                List.of(band(x1,z1,x2,z2,-u,-c), band(x1,z1,x2,z2,c,u)),
                List.of(band(x1,z1,x2,z2,-s,-u), band(x1,z1,x2,z2,u,s)));
    }

    private static BoundsXZ band(int x1,int z1,int x2,int z2,int from,int to) {
        return z1 == z2
                ? new BoundsXZ(Math.min(x1,x2), z1+from, Math.max(x1,x2), z1+to)
                : new BoundsXZ(x1+from, Math.min(z1,z2), x1+to, Math.max(z1,z2));
    }
}
