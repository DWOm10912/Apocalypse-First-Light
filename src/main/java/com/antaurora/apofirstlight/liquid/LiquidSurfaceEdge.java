package com.antaurora.apofirstlight.liquid;

/**
 * Where a drawn water surface meets water that is not drawn (falling water 21): a cell's surface is one level, and on
 * the crest of a slope that level reaches out over the downhill side of the cell, where the ground is already lower and
 * the water running on down is a film too thin to draw. Drawn flat to the edge it hung over the slope with no side to it
 * ("水面和斜面完全对不齐", the user in game 2026-10-25); its corners on that side come down to the ground there, so the
 * water bends over the crest and meets the slope. Shared by the client's surface (ClientLiquid#corner) and the offline
 * check (LiquidCheck crest).
 */
public final class LiquidSurfaceEdge {
    /** How far above the ground a corner the water runs off at is drawn (m): the film, as thin as the surface can be. */
    public static final float LIP = 0.003F;

    private LiquidSurfaceEdge() {
    }

    /**
     * The corner's height when the water runs on into a neighbour drawn nowhere near this level: no higher than the
     * neighbour's ground there (not below this cell's own floor) plus {@link #LIP}. Ground higher than the corner (the
     * water runs in from uphill) leaves it as it is; NaN (no ground found) too.
     */
    public static float runoff(float corner, float floor, double ground) {
        if (Double.isNaN(ground)) return corner;
        return (float) Math.min(corner, Math.max(ground, floor) + LIP);
    }
}
