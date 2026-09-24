package com.antaurora.apofirstlight.client.mesh;

/** Immutable, expanded triangle corners. Positions are blocks relative to the owning bone pivot. */
public final class AflMeshPart {
    public static final int STRIDE = 8; // x y z u v nx ny nz
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    private final String name;
    private final float[] corners;
    private final Bounds bounds;

    AflMeshPart(String name, float[] corners, Bounds bounds) {
        this.name = name;
        this.corners = corners.clone();
        this.bounds = bounds;
    }

    public String name() { return name; }
    public int cornerCount() { return corners.length / STRIDE; }
    public float value(int corner, int component) { return corners[corner * STRIDE + component]; }
    public Bounds bounds() { return bounds; }
}
