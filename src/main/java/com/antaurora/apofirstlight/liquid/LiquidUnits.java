package com.antaurora.apofirstlight.liquid;

/**
 * Units of the liquid framework (docs/gameplay/water_v1_design.md 5.12).
 * <ul>
 *   <li>Liquid is counted in whole millilitres (long). 1 L = 1 mB (AFL's Forge rule since 2026-10-04,
 *   docs/项目内容/01 - 设计/工业/流体系统.md 0) = 1,000 mL; a block of liquid is 1,000 L.</li>
 *   <li>Heights are metres (blocks). The ground comes in 1/16 m slabs and 1/256 m2 areas (GroundView V0), so one area
 *   unit over one slab is 1/4096 m3, the earthwork quantum: 244.140625 mL, exact in a double. That is geometry (how much
 *   a column holds), never an amount that moves: liquid and ground are conserved apart and never converted.</li>
 *   <li>To a Forge fluid handler only whole litres go (floor), the millilitres left stay in the world; from one, mB x 1000
 *   exactly. Products in long.</li>
 * </ul>
 */
public final class LiquidUnits {
    public static final int SLABS = 16;
    public static final double SLAB = 1.0 / SLABS;
    /** Area units in a 1 m x 1 m column. */
    public static final int AREA = 256;
    public static final long ML_PER_LITRE = 1000;
    public static final long ML_PER_M3 = 1_000_000;
    /** One area unit over one slab: 1/4096 m3. */
    public static final double ML_PER_GROUND_UNIT = ML_PER_M3 / 4096.0;
    /**
     * A game hour in ticks: the one place rain, infiltration and evaporation rates (all mm a game hour,
     * docs/gameplay/world_time_b_units_v1.md 2, docs/gameplay/water_rain_v1_design.md 5) meet the tick. 1,000 until C's
     * world time T1 merges; then AflTime.ticksPerGameHour (3,000: a 60-minute day) and the offline checks run again.
     */
    public static final int TICKS_PER_GAME_HOUR = 1000;
    /** Rain: 1 mm/h on 1 m2 is 1 L a game hour, so 1,000 mL over TICKS_PER_GAME_HOUR ticks. */
    public static final double ML_PER_TICK_PER_MM_H = 1000.0 / TICKS_PER_GAME_HOUR;

    private LiquidUnits() {}

    /** Whole litres (= mB) a Forge handler may take out of {@code ml}; the rest stays. Never rounds up. */
    public static long floorLitres(long ml) {
        if (ml < 0) throw new IllegalArgumentException("negative volume " + ml);
        return ml / ML_PER_LITRE;
    }

    /** mB (litres) from a Forge handler as millilitres, exact; throws rather than overflow. */
    public static long litresToMl(long litres) {
        return Math.multiplyExact(litres, ML_PER_LITRE);
    }

    /** A Forge amount (int mB) for {@code ml}, floored and capped at Integer.MAX_VALUE. */
    public static int forgeAmount(long ml) {
        return (int) Math.min(Integer.MAX_VALUE, floorLitres(ml));
    }

    public static double metres(int slab) {
        return slab * SLAB;
    }
}
