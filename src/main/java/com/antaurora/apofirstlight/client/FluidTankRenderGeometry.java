package com.antaurora.apofirstlight.client;

import net.minecraft.util.Mth;

public final class FluidTankRenderGeometry {
    public static final float TANK_FLUID_EPSILON_MODEL = 0.125F;
    public static final float INNER_MIN_X_PIXELS = 1.0F + TANK_FLUID_EPSILON_MODEL;
    public static final float INNER_MAX_X_PIXELS = 15.0F - TANK_FLUID_EPSILON_MODEL;
    public static final float INNER_MIN_Y_PIXELS = 1.0F + TANK_FLUID_EPSILON_MODEL;
    public static final float INNER_MAX_Y_PIXELS = 15.0F - TANK_FLUID_EPSILON_MODEL;
    public static final float INNER_MIN_Z_PIXELS = 1.0F + TANK_FLUID_EPSILON_MODEL;
    public static final float INNER_MAX_Z_PIXELS = 15.0F - TANK_FLUID_EPSILON_MODEL;

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
