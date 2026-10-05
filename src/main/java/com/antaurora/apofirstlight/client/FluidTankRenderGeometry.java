package com.antaurora.apofirstlight.client;

import net.minecraft.util.Mth;

public final class FluidTankRenderGeometry {
    // Fluid Tank V2 (tools/build-fluid-tank-v2.mjs FLUID): inside the glass (5.8 px from the centre) and between the
    // port decks (7.4 px from the centre)
    public static final float INNER_MIN_X_PIXELS = 2.2F;
    public static final float INNER_MAX_X_PIXELS = 13.8F;
    public static final float INNER_MIN_Y_PIXELS = 0.6F;
    public static final float INNER_MAX_Y_PIXELS = 15.4F;
    public static final float INNER_MIN_Z_PIXELS = 2.2F;
    public static final float INNER_MAX_Z_PIXELS = 13.8F;

    public static final float MIN_X = INNER_MIN_X_PIXELS / 16.0F;
    public static final float MAX_X = INNER_MAX_X_PIXELS / 16.0F;
    public static final float MIN_Y = INNER_MIN_Y_PIXELS / 16.0F;
    public static final float MAX_Y = INNER_MAX_Y_PIXELS / 16.0F;
    public static final float MIN_Z = INNER_MIN_Z_PIXELS / 16.0F;
    public static final float MAX_Z = INNER_MAX_Z_PIXELS / 16.0F;

    private FluidTankRenderGeometry() {
    }

    public static float singleTankFluidTop(float fillRatio) {
        return Mth.lerp(Mth.clamp(fillRatio, 0.0F, 1.0F), MIN_Y, MAX_Y);
    }
}
