package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds the deterministic rounded-rectangle bore and portal ring for a qualified tunnel span. */
public final class HighwayTunnelGeometry {
    public static final int INTERIOR_HALF_WIDTH = HighwayPlan.MAIN_WIDTH / 2 + 1;
    public static final int OUTER_HALF_WIDTH = INTERIOR_HALF_WIDTH + 1;
    public static final int PORTAL_EXTRA_HALF_WIDTH = OUTER_HALF_WIDTH + 1;
    public static final int INTERIOR_HEIGHT = HighwayTunnelSpanResolver.TUNNEL_INTERIOR_HEIGHT;
    public static final int OUTER_HEIGHT = INTERIOR_HEIGHT + HighwayTunnelSpanResolver.TUNNEL_ROOF_LINING;
    private static final int AREA_LATERAL_MARGIN = HighwayCorridor.ROW_MARGIN + 1;
    private static final int AREA_LONGITUDINAL_MARGIN = HighwayCorridor.ROW_MARGIN;

    private HighwayTunnelGeometry() {}

    public static Geometry build(HighwayPlan plan, HighwayProfile profile,
                                 List<HighwayCorridor.CenterCell> centerline,
                                 HighwayTunnelSpanResolver.Resolution resolution) {
        Set<BlockPos> bore = new LinkedHashSet<>();
        Set<BlockPos> lining = new LinkedHashSet<>();
        Set<BlockPos> portal = new LinkedHashSet<>();
        Set<ColumnKey> area = new LinkedHashSet<>();
        List<TunnelSection> sections = new java.util.ArrayList<>();
        HighwayPlan.Tangent tangent = plan.tangent(0.0);
        double rightX = -tangent.z();
        double rightZ = tangent.x();

        for (HighwayCorridor.CenterCell center : centerline) {
            HighwayTunnelSpanResolver.TunnelSpan span = resolution.spanAt(center.distance());
            if (span == null) continue;
            int roadY = profile.sampleAt(center.distance()).roadY();
            boolean portalSection = span.isPortal(center.distance());
            sections.add(new TunnelSection(center.x(), center.z(), center.distance(), roadY, portalSection));

            for (int longitudinal = -AREA_LONGITUDINAL_MARGIN;
                 longitudinal <= AREA_LONGITUDINAL_MARGIN; longitudinal++) {
                for (int lateral = -INTERIOR_HALF_WIDTH - AREA_LATERAL_MARGIN;
                     lateral <= INTERIOR_HALF_WIDTH + AREA_LATERAL_MARGIN; lateral++) {
                    area.add(column(center.x(), center.z(), tangent, rightX, rightZ,
                            longitudinal, lateral));
                }
            }

            for (int y = 1; y <= 5; y++) {
                addInteriorRow(bore, lining, center, tangent, rightX, rightZ, roadY, y, -12, 12);
                addShellSides(lining, center, tangent, rightX, rightZ, roadY, y, -13, 13);
            }
            addInteriorRow(bore, lining, center, tangent, rightX, rightZ, roadY, 6, -11, 11);
            addShellSides(lining, center, tangent, rightX, rightZ, roadY, 6, -13, 13);
            addInteriorRow(bore, lining, center, tangent, rightX, rightZ, roadY, 7, -10, 10);
            addShellSides(lining, center, tangent, rightX, rightZ, roadY, 7, -12, 12);
            addInteriorRow(bore, lining, center, tangent, rightX, rightZ, roadY, 8, -9, 9);
            addShellSides(lining, center, tangent, rightX, rightZ, roadY, 8, -11, 11);
            addFullRow(lining, center, tangent, rightX, rightZ, roadY + 9, -10, 10);

            if (portalSection) {
                for (int y = 1; y <= 6; y++) {
                    addSingle(lining, center, tangent, rightX, rightZ, roadY + y,
                            -PORTAL_EXTRA_HALF_WIDTH);
                    addSingle(lining, center, tangent, rightX, rightZ, roadY + y,
                            PORTAL_EXTRA_HALF_WIDTH);
                }
                addFullRow(portal, center, tangent, rightX, rightZ, roadY + 10,
                        -PORTAL_EXTRA_HALF_WIDTH, PORTAL_EXTRA_HALF_WIDTH);
                for (int y = 1; y <= 6; y++) {
                    addSingle(portal, center, tangent, rightX, rightZ, roadY + y,
                            -PORTAL_EXTRA_HALF_WIDTH);
                    addSingle(portal, center, tangent, rightX, rightZ, roadY + y,
                            PORTAL_EXTRA_HALF_WIDTH);
                }
            }
        }
        return new Geometry(List.copyOf(sections), Set.copyOf(bore), Set.copyOf(lining),
                Set.copyOf(portal), Set.copyOf(area));
    }

    private static void addInteriorRow(Set<BlockPos> bore, Set<BlockPos> lining,
                                       HighwayCorridor.CenterCell center, HighwayPlan.Tangent tangent,
                                       double rightX, double rightZ, int roadY, int y,
                                       int min, int max) {
        for (int lateral = min; lateral <= max; lateral++) {
            addSingle(bore, center, tangent, rightX, rightZ, roadY + y, lateral);
        }
    }

    private static void addShellSides(Set<BlockPos> lining,
                                      HighwayCorridor.CenterCell center, HighwayPlan.Tangent tangent,
                                      double rightX, double rightZ, int roadY, int y,
                                      int min, int max) {
        addSingle(lining, center, tangent, rightX, rightZ, roadY + y, min);
        addSingle(lining, center, tangent, rightX, rightZ, roadY + y, max);
    }

    private static void addFullRow(Set<BlockPos> positions,
                                   HighwayCorridor.CenterCell center, HighwayPlan.Tangent tangent,
                                   double rightX, double rightZ, int y, int min, int max) {
        for (int lateral = min; lateral <= max; lateral++) {
            addSingle(positions, center, tangent, rightX, rightZ, y, lateral);
        }
    }

    private static void addSingle(Set<BlockPos> positions,
                                  HighwayCorridor.CenterCell center, HighwayPlan.Tangent tangent,
                                  double rightX, double rightZ, int y, int lateral) {
        positions.add(new BlockPos(
                (int) Math.round(center.x() + rightX * lateral), y,
                (int) Math.round(center.z() + rightZ * lateral)));
    }

    private static ColumnKey column(int centerX, int centerZ, HighwayPlan.Tangent tangent,
                                    double rightX, double rightZ, int longitudinal, int lateral) {
        return new ColumnKey(
                (int) Math.round(centerX + tangent.x() * longitudinal + rightX * lateral),
                (int) Math.round(centerZ + tangent.z() * longitudinal + rightZ * lateral));
    }

    public record Geometry(List<TunnelSection> sections, Set<BlockPos> borePositions,
                           Set<BlockPos> liningPositions, Set<BlockPos> portalPositions,
                           Set<ColumnKey> areaColumns) {}

    public record TunnelSection(int x, int z, double distance, int roadY, boolean portal) {}

    public record ColumnKey(int x, int z) {}
}
