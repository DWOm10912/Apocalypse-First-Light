package com.antaurora.apofirstlight.worldgen.highway;

public final class HighwayRenderStats {
    public int groundSegments;
    public int cutSegments;
    public int fillSegments;
    public int viaductSegments;
    public int blocksPlaced;
    public int blocksCleared;
    public int fillBlocks;
    public int cutBlocks;
    public int viaductBlocks;
    public int piersPlaced;

    public void addMode(HighwayTerrainMode mode) {
        switch (mode) {
            case GROUND -> groundSegments++;
            case CUT -> cutSegments++;
            case FILL -> fillSegments++;
            case VIADUCT -> viaductSegments++;
        }
    }

    public void add(HighwayRenderStats other) {
        groundSegments += other.groundSegments;
        cutSegments += other.cutSegments;
        fillSegments += other.fillSegments;
        viaductSegments += other.viaductSegments;
        blocksPlaced += other.blocksPlaced;
        blocksCleared += other.blocksCleared;
        fillBlocks += other.fillBlocks;
        cutBlocks += other.cutBlocks;
        viaductBlocks += other.viaductBlocks;
        piersPlaced += other.piersPlaced;
    }
}
