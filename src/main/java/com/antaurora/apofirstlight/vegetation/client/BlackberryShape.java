package com.antaurora.apofirstlight.vegetation.client;

import com.antaurora.apofirstlight.vegetation.client.PlantShape.Blade;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Card;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Clump;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Piece;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Pose;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Quad;
import com.antaurora.apofirstlight.vegetation.client.PlantShape.Rng;

import java.util.ArrayList;
import java.util.List;

import static com.antaurora.apofirstlight.vegetation.client.PlantShape.ASPECT;
import static com.antaurora.apofirstlight.vegetation.client.PlantShape.COLUMNS;

/**
 * Allegheny blackberry (Rubus allegheniensis; AFL vegetation P2b batch 3, docs/gameplay/vegetation_v1_design.md 18): a
 * block is a square metre of a bramble, one or two crowns near its middle.
 * <ul>
 * <li>LOD0: a crown sends up 1-2 of this year's canes nearly upright (0.9-1.5 m) and 2-3 of last year's arching over
 * (1.3-1.7 m long, rising off the crown and bending past level, their tips 0.3-0.7 m above the ground); canes round
 * (4 sides, tapering), prickly, green going red-purple. Along a cane every 13-18 % of it a palmately compound leaf: three
 * or five ovate toothed leaflets 6-9 cm spreading from a short petiole, each turned a little about its own midrib (the
 * leaves face every way, not only up). The arching canes carry 2-4 clusters
 * of drupes on their outer half, hanging a little below them: black when ripe, a few still red. Leaves and clusters are
 * rigid parts carried by the cane; the leaves lie down with it.</li>
 * <li>LOD1: two crossed wide cards a crown. LOD2: one pair a block. Tall: drawn at every distance.</li>
 * </ul>
 * Sheet columns: 61 the cane (real colours), 62 the parts (twelve 9-row bands down the column, each a leaflet across its
 * left three quarters (tinted) and a cluster of drupes in its right quarter (real colours); a part takes the band of the
 * cane's v where it sits, so the wind moves it with the cane), 63 the bramble card. Stiff: 0.8 times the turf's sway.
 */
final class BlackberryShape {
    static final int CANE = 61, PARTS = 62, CARD = 63;
    /** The parts column's bands: BANDS of them, the k-th centred on row 5 + 10.5 k. */
    static final int BANDS = 12;
    /** The leaflet's share of a band's width (u), the drupes' share after it. */
    static final float LEAFLET_U = 0.72F;
    private static final float[] CANE_AT = {0, 0.15F, 0.3F, 0.45F, 0.6F, 0.75F, 0.88F, 1};

    private BlackberryShape() {}

    @SuppressWarnings("unchecked")
    static List<Clump>[][] layouts() {
        List<Clump>[][] out = new List[PlantShape.VARIANTS][PlantShape.LEVELS];
        for (int v = 0; v < PlantShape.VARIANTS; v++) {
            Rng r = new Rng(0xB1ACBE77L * 31 + v * 0x9E3779B97F4A7C15L);
            int crowns = 1 + r.nextInt(2);
            var near = new ArrayList<Clump>();
            var mid = new ArrayList<Clump>();
            float tallest = 0;
            for (int c = 0; c < crowns; c++) {
                float x = 0.3F + r.nextFloat() * 0.4F, z = 0.3F + r.nextFloat() * 0.4F;
                Clump crown = crown(new Rng(r.next()), x, z);
                near.add(crown);
                float top = 0;
                for (Pose p : crown.grown) for (int k = 1; k < p.pts.length; k += 3) top = Math.max(top, p.pts[k]);
                tallest = Math.max(tallest, top);
                mid.add(cards(r, x, z, top * 1.05F, 2.6F, 2));
            }
            out[v][0] = List.copyOf(near);
            out[v][1] = List.copyOf(mid);
            out[v][2] = List.of(cards(r, 0.5F, 0.5F, tallest * 1.05F, 3.2F, 1));
        }
        return out;
    }

    private static Clump cards(Rng r, float x, float z, float h, float wide, int segments) {
        float yaw = r.nextFloat() * (float) Math.PI, w = h * ASPECT * wide;
        return new Clump(x, z, r.nextFloat(), List.of(), List.of(
                new Card(x, z, yaw, h, w, 0.06F, CARD, segments, true),
                new Card(x, z, yaw + (float) (Math.PI / 2), h, w, -0.06F, CARD, segments, true)), List.of());
    }

    /** One crown at (x, z): upright and arching canes with their leaves and fruit. */
    private static Clump crown(Rng r, float x, float z) {
        var blades = new ArrayList<Blade>();
        var pieces = new ArrayList<Piece>();
        int upright = 1 + r.nextInt(2), arching = 2 + r.nextInt(2);
        float turn = r.nextFloat() * (float) (Math.PI * 2);
        for (int k = 0; k < upright + arching; k++) {
            boolean arch = k >= upright;
            float yaw = turn + (k + r.nextFloat() * 0.6F) / (upright + arching) * (float) (Math.PI * 2);
            // an arching cane bends towards the block's middle rather than far out of it
            if (arch) {
                float out = (float) Math.atan2(z - 0.5F, x - 0.5F), dist = (float) Math.hypot(x - 0.5F, z - 0.5F);
                double diff = Math.atan2(Math.sin(yaw - out), Math.cos(yaw - out));
                if (dist > 0.05F && Math.abs(diff) < Math.PI / 2) yaw += (float) (Math.signum(diff) * Math.PI / 2);
            }
            float len = arch ? 1.3F + r.nextFloat() * 0.4F : 0.9F + r.nextFloat() * 0.6F;
            float phi0 = arch ? 0.12F + r.nextFloat() * 0.18F : 0.03F + r.nextFloat() * 0.1F;
            float phi1 = arch ? 1.85F + r.nextFloat() * 0.4F : phi0 + 0.15F + r.nextFloat() * 0.3F;
            float off = 0.01F + r.nextFloat() * 0.03F;
            Blade cane = new Blade(x + (float) Math.cos(yaw) * off, z + (float) Math.sin(yaw) * off, yaw, yaw + (float) (Math.PI / 2),
                    len, 0.008F + r.nextFloat() * 0.003F, phi0, phi1, CANE).profile(PlantShape.TAPER).tube(4).at(CANE_AT).untinted();
            int index = blades.size();
            blades.add(cane);
            Pose grown = PlantShape.grown(cane);
            float side = r.nextFloat() * (float) (Math.PI * 2);
            int n = 0;
            for (float at = 0.18F + r.nextFloat() * 0.06F; at < 0.97F; at += 0.13F + r.nextFloat() * 0.05F, n++)
                pieces.add(new Piece(index, at, PlantShape.footed(leaf(grown, at, side + (float) Math.PI * n + (r.nextFloat() - 0.5F) * 1.2F,
                        0.06F + r.nextFloat() * 0.03F, r), x, z), true));
            if (arch) {
                int clusters = 2 + r.nextInt(3);
                for (int c = 0; c < clusters; c++) {
                    float at = 0.5F + r.nextFloat() * 0.42F;
                    pieces.add(new Piece(index, at, PlantShape.footed(drupes(grown, at, 0.035F + r.nextFloat() * 0.025F, r), x, z)));
                }
            }
        }
        return new Clump(x, z, r.nextFloat(), blades, List.of(), pieces);
    }

    /** The middle of the parts band nearest a cane's v at {@code at} (v). */
    private static float band(float at) {
        float row = (1 - at) * 128;
        int k = Math.max(0, Math.min(BANDS - 1, Math.round((row - 5) / 10.5F)));
        return (5 + 10.5F * k) / 128;
    }

    /**
     * A compound leaf at the share {@code at} of a cane (as grown), off to yaw on a 3-6 cm petiole: three or five leaflets
     * L long spreading nearly level round it (the end one longest, the lowest pair smallest), each one segment along its
     * band's leaflet (its foot at the band's left). Both faces, tinted, cut out.
     */
    private static List<Quad> leaf(Pose cane, float at, float yaw, float len, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(cane.pts, cane.f, at, f);
        float dx = (float) Math.cos(yaw), dz = (float) Math.sin(yaw), pet = 0.03F + r.nextFloat() * 0.03F;
        float[] c = {f[0] + dx * pet, f[1] + 0.01F, f[2] + dz * pet};
        int n = r.nextFloat() < 0.6F ? 5 : 3;
        float[] angle = n == 5 ? new float[]{0, 0.8F, -0.8F, 1.7F, -1.7F} : new float[]{0, 0.9F, -0.9F};
        float[] size = n == 5 ? new float[]{1, 0.9F, 0.9F, 0.65F, 0.65F} : new float[]{1, 0.9F, 0.9F};
        float roll = (r.nextFloat() - 0.5F) * 1.4F;
        float vm = band(at), tile = FlowerParts.TILE, u0 = (PARTS + 0.02F) / COLUMNS, u1 = (PARTS + LEAFLET_U - 0.02F) / COLUMNS;
        var out = new ArrayList<Quad>();
        for (int k = 0; k < n; k++) {
            double a = yaw + angle[k] + (r.nextFloat() - 0.5F) * 0.2F;
            float ax = (float) Math.cos(a), az = (float) Math.sin(a);
            float l = len * size[k], h = l * 0.32F, droop = -0.15F - r.nextFloat() * 0.2F, rho = roll + (r.nextFloat() - 0.5F) * 0.5F;
            // across the leaflet: level, turned rho about its midrib
            float wx = -az * (float) Math.cos(rho), wy = (float) Math.sin(rho), wz = ax * (float) Math.cos(rho);
            float[] p0 = {c[0], c[1], c[2]}, p1 = {c[0] + ax * l, c[1] + l * droop, c[2] + az * l};
            PlantShape.face(out, new float[][]{{p0[0] - wx * h, p0[1] - wy * h, p0[2] - wz * h}, {p0[0] + wx * h, p0[1] + wy * h, p0[2] + wz * h},
                            {p1[0] + wx * h, p1[1] + wy * h, p1[2] + wz * h}, {p1[0] - wx * h, p1[1] - wy * h, p1[2] - wz * h}},
                    new float[][]{{u0, vm - tile}, {u0, vm + tile}, {u1, vm + tile}, {u1, vm - tile}}, new float[]{0, 1, 0},
                    PlantShape.shade(Math.min(1, f[1] / 1.4F)), true, false, true);
        }
        return out;
    }

    /** A cluster of drupes hanging under a cane at {@code at}: two crossed squares s across, the band's right quarter. Real colours. */
    private static List<Quad> drupes(Pose cane, float at, float s, Rng r) {
        float[] f = new float[6];
        PlantShape.frame(cane.pts, cane.f, at, f);
        float vm = band(at), tile = FlowerParts.TILE, u0 = (PARTS + LEAFLET_U + 0.02F) / COLUMNS, u1 = (PARTS + 0.98F) / COLUMNS;
        float top = f[1] - 0.005F, bottom = top - s, spin = r.nextFloat() * (float) Math.PI;
        var out = new ArrayList<Quad>();
        for (int k = 0; k < 2; k++) {
            double a = spin + Math.PI / 2 * k;
            float ax = (float) Math.cos(a) * s / 2, az = (float) Math.sin(a) * s / 2;
            PlantShape.face(out, new float[][]{{f[0] - ax, bottom, f[2] - az}, {f[0] + ax, bottom, f[2] + az}, {f[0] + ax, top, f[2] + az}, {f[0] - ax, top, f[2] - az}},
                    new float[][]{{u0, vm + tile}, {u1, vm + tile}, {u1, vm - tile}, {u0, vm - tile}}, new float[]{-az, 0, ax}, 1, false, false, true);
        }
        return out;
    }
}
