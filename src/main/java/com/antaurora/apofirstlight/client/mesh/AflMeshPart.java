package com.antaurora.apofirstlight.client.mesh;

/** Immutable, expanded face corners. Positions are blocks relative to the owning bone pivot. */
public final class AflMeshPart {
    public enum Layer { CUTOUT, TRANSLUCENT }
    private final Layer layer;
    public static final int STRIDE = 8; // x y z u v nx ny nz
    public record Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    private final String name;
    private final float[] corners;
    private final int[] faceOffsets;
    private final int quadCount, triangleCount;
    private final Bounds bounds;

    AflMeshPart(String name, float[] corners, int[] faceOffsets, Bounds bounds) {
        this(name, corners, faceOffsets, bounds, Layer.CUTOUT);
    }

    AflMeshPart(String name, float[] corners, int[] faceOffsets, Bounds bounds, Layer layer) {
        this.layer = layer;
        this.name = name;
        this.corners = corners.clone();
        this.faceOffsets = faceOffsets.clone();
        int quads = 0;
        for (int i = 0; i < faceOffsets.length - 1; i++)
            if (faceOffsets[i + 1] - faceOffsets[i] == 4) quads++;
        this.quadCount = quads;
        this.triangleCount = faceOffsets.length - 1 - quads;
        this.bounds = bounds;
    }

    public String name() { return name; }
    public Layer layer() { return layer; }
    public int cornerCount() { return corners.length / STRIDE; }
    public float value(int corner, int component) { return corners[corner * STRIDE + component]; }
    public int faceCount() { return faceOffsets.length - 1; }
    public int faceStart(int face) { return faceOffsets[face]; }
    public int faceSize(int face) { return faceOffsets[face + 1] - faceOffsets[face]; }
    public int quadCount() { return quadCount; }
    public int triangleCount() { return triangleCount; }
    public int triangleEquivalent() { return triangleCount + 2 * quadCount; }
    public Bounds bounds() { return bounds; }
}
