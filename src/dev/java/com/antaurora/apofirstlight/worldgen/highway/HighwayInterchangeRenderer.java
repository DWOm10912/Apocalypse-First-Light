package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.server.level.ServerLevel;

/** Small at-grade diamond interchange assembled from the same profile/renderer pipeline. */
public final class HighwayInterchangeRenderer {
    private HighwayInterchangeRenderer() {}

    public static HighwayRenderStats render(ServerLevel level, HighwayPlan mainPlan, HighwayEditSession edit) {
        HighwayRenderStats stats = new HighwayRenderStats();
        double mid = mainPlan.length() * 0.5;
        HighwayPlan.Point center = mainPlan.sample(mid);
        HighwayPlan.Tangent tangent = mainPlan.tangent(mid);
        double rightX = -tangent.z();
        double rightZ = tangent.x();
        HighwayPlan.Point collectorStart = offset(center, rightX, rightZ, -64.0);
        HighwayPlan.Point collectorEnd = offset(center, rightX, rightZ, 64.0);
        HighwayPlan collector = HighwayPlan.linear(collectorStart, collectorEnd, HighwayPlan.COLLECTOR_WIDTH);
        stats.add(HighwayRenderer.render(level, HighwayProfile.sample(level, collector), edit));

        for (int alongSign : new int[]{-1, 1}) {
            for (int sideSign : new int[]{-1, 1}) {
                HighwayPlan.Point start = new HighwayPlan.Point(center.x() + tangent.x() * alongSign * 34.0 + rightX * sideSign * 12.0,
                        center.z() + tangent.z() * alongSign * 34.0 + rightZ * sideSign * 12.0);
                HighwayPlan.Point end = new HighwayPlan.Point(center.x() + rightX * sideSign * 60.0,
                        center.z() + rightZ * sideSign * 60.0);
                HighwayPlan.Point control1 = new HighwayPlan.Point(start.x() - tangent.x() * alongSign * 14.0 + rightX * sideSign * 10.0,
                        start.z() - tangent.z() * alongSign * 14.0 + rightZ * sideSign * 10.0);
                HighwayPlan.Point control2 = new HighwayPlan.Point(end.x() + tangent.x() * alongSign * 14.0 - rightX * sideSign * 10.0,
                        end.z() + tangent.z() * alongSign * 14.0 - rightZ * sideSign * 10.0);
                HighwayPlan ramp = HighwayPlan.bezier(start, control1, control2, end, HighwayPlan.RAMP_WIDTH);
                stats.add(HighwayRenderer.render(level, HighwayProfile.sample(level, ramp), edit));
            }
        }
        return stats;
    }

    private static HighwayPlan.Point offset(HighwayPlan.Point point, double x, double z, double amount) {
        return new HighwayPlan.Point(point.x() + x * amount, point.z() + z * amount);
    }
}
