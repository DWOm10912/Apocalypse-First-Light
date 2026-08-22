package com.antaurora.apofirstlight.worldgen.highway;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Geometry-only route plan. It never reads or writes a level. */
public final class HighwayPlan {
    public static final int MAIN_WIDTH = 23;
    public static final int COLLECTOR_WIDTH = 11;
    public static final int RAMP_WIDTH = 7;

    private final List<Point> controlPoints;
    private final double length;
    private final int width;
    private final Curve curve;

    private HighwayPlan(List<Point> controlPoints, double length, int width, Curve curve) {
        this.controlPoints = List.copyOf(controlPoints);
        this.length = length;
        this.width = width;
        this.curve = curve;
    }

    public static HighwayPlan main(BlockPos start, double headingX, double headingZ, int length, long seed) {
        double magnitude = Math.sqrt(headingX * headingX + headingZ * headingZ);
        double hx = headingX / magnitude;
        double hz = headingZ / magnitude;
        double rx = -hz;
        double rz = hx;
        int segmentLength = Math.max(192, Math.min(320, length / 4));
        int pointCount = Math.max(2, (int) Math.ceil(length / (double) segmentLength) + 1);
        List<Point> points = new ArrayList<>();
        java.util.Random random = new java.util.Random(seed ^ ((long) start.getX() * 341873128712L)
                ^ ((long) start.getZ() * 132897987541L));
        for (int i = 0; i < pointCount; i++) {
            double distance = i == pointCount - 1 ? length : Math.min(length, (double) i * length / (pointCount - 1));
            double lateral = i == 0 || i == pointCount - 1 ? 0.0 : (random.nextDouble() * 2.0 - 1.0) * 56.0;
            points.add(new Point(start.getX() + hx * distance + rx * lateral,
                    start.getZ() + hz * distance + rz * lateral));
        }
        return new HighwayPlan(points, length, MAIN_WIDTH, Curve.CATMULL_ROM);
    }

    public static HighwayPlan linear(Point start, Point end, int width) {
        return new HighwayPlan(List.of(start, end), distance(start, end), width, Curve.LINEAR);
    }

    public static HighwayPlan bezier(Point p0, Point p1, Point p2, Point p3, int width) {
        double length = approximateBezierLength(p0, p1, p2, p3);
        return new HighwayPlan(List.of(p0, p1, p2, p3), length, width, Curve.BEZIER);
    }

    public Point sample(double distance) {
        double t = length <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, distance / length));
        return switch (curve) {
            case LINEAR -> lerp(controlPoints.get(0), controlPoints.get(1), t);
            case BEZIER -> bezier(controlPoints.get(0), controlPoints.get(1), controlPoints.get(2), controlPoints.get(3), t);
            case CATMULL_ROM -> catmullRom(t);
        };
    }

    public Tangent tangent(double distance) {
        double delta = Math.min(2.0, Math.max(0.25, length / 1000.0));
        Point before = sample(Math.max(0.0, distance - delta));
        Point after = sample(Math.min(length, distance + delta));
        double dx = after.x() - before.x();
        double dz = after.z() - before.z();
        double magnitude = Math.sqrt(dx * dx + dz * dz);
        return magnitude == 0.0 ? new Tangent(0.0, 1.0) : new Tangent(dx / magnitude, dz / magnitude);
    }

    public List<Point> controlPoints() { return controlPoints; }
    public double length() { return length; }
    public int width() { return width; }

    private Point catmullRom(double t) {
        if (controlPoints.size() < 3) return lerp(controlPoints.get(0), controlPoints.get(controlPoints.size() - 1), t);
        double scaled = t * (controlPoints.size() - 1);
        int segment = Math.min(controlPoints.size() - 2, (int) Math.floor(scaled));
        double local = scaled - segment;
        Point p0 = controlPoints.get(Math.max(0, segment - 1));
        Point p1 = controlPoints.get(segment);
        Point p2 = controlPoints.get(Math.min(controlPoints.size() - 1, segment + 1));
        Point p3 = controlPoints.get(Math.min(controlPoints.size() - 1, segment + 2));
        return new Point(cat(p0.x(), p1.x(), p2.x(), p3.x(), local), cat(p0.z(), p1.z(), p2.z(), p3.z(), local));
    }

    private static double cat(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * ((2.0 * p1) + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t * t
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t * t * t);
    }

    private static Point bezier(Point p0, Point p1, Point p2, Point p3, double t) {
        double u = 1.0 - t;
        return new Point(u * u * u * p0.x() + 3.0 * u * u * t * p1.x() + 3.0 * u * t * t * p2.x() + t * t * t * p3.x(),
                u * u * u * p0.z() + 3.0 * u * u * t * p1.z() + 3.0 * u * t * t * p2.z() + t * t * t * p3.z());
    }

    private static Point lerp(Point a, Point b, double t) { return new Point(a.x() + (b.x() - a.x()) * t, a.z() + (b.z() - a.z()) * t); }
    private static double distance(Point a, Point b) { return Math.hypot(b.x() - a.x(), b.z() - a.z()); }
    private static double approximateBezierLength(Point p0, Point p1, Point p2, Point p3) {
        double total = 0.0;
        Point previous = p0;
        for (int i = 1; i <= 32; i++) {
            Point current = bezier(p0, p1, p2, p3, i / 32.0);
            total += distance(previous, current);
            previous = current;
        }
        return total;
    }

    public record Point(double x, double z) {}
    public record Tangent(double x, double z) {}
    private enum Curve { LINEAR, BEZIER, CATMULL_ROM }
}
