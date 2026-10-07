package com.antaurora.apofirstlight.worldgen.roads;

/** V1 names describe asphalt width. Curb, utility and sidewalk widths are PER SIDE, outside asphalt. */
public enum RoadType {
    R12(12, 1, 2, 2), C14(14, 1, 2, 3), I12(12, 1, 3, 2);

    private final int asphaltWidth;
    private final int curbReservation;
    private final int utilityBand;
    private final int sidewalkWidth;
    RoadType(int asphaltWidth, int curbReservation, int utilityBand, int sidewalkWidth) {
        this.asphaltWidth = asphaltWidth;
        this.curbReservation = curbReservation;
        this.utilityBand = utilityBand;
        this.sidewalkWidth = sidewalkWidth;
    }
    public int asphaltWidth() { return asphaltWidth; }
    public int curbReservation() { return curbReservation; }
    public int utilityBand() { return utilityBand; }
    public int sidewalkWidth() { return sidewalkWidth; }
    public int rightOfWayWidth() { return asphaltWidth + 2 * (curbReservation + utilityBand + sidewalkWidth); }
}
