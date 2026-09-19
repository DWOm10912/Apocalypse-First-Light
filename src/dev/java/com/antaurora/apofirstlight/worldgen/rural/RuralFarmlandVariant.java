package com.antaurora.apofirstlight.worldgen.rural;

/** Visual profiles only; none of these values change Rural tier or building selection. */
public enum RuralFarmlandVariant {
    ABANDONED_FIELD(55, 82, 55, 15, 45, 55),
    SURVIVING_FIELD(25, 97, 85, 5, 95, 90),
    OVERGROWN_FIELD(20, 70, 15, 35, 15, 25);

    public final int weight, fencePercent, cropPercent, emptyRowPercent, waterPercent, pathPercent;

    RuralFarmlandVariant(int weight, int fencePercent, int cropPercent, int emptyRowPercent,
                         int waterPercent, int pathPercent) {
        this.weight = weight;
        this.fencePercent = fencePercent;
        this.cropPercent = cropPercent;
        this.emptyRowPercent = emptyRowPercent;
        this.waterPercent = waterPercent;
        this.pathPercent = pathPercent;
    }

    public static RuralFarmlandVariant choose(long seed) {
        int roll = Math.floorMod(mix(seed), 100);
        for (var variant : values()) {
            roll -= variant.weight;
            if (roll < 0) return variant;
        }
        return OVERGROWN_FIELD;
    }

    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
