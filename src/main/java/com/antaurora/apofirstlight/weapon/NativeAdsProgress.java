package com.antaurora.apofirstlight.weapon;

/** Tick-based visual state. No shot authority or accuracy modifier. */
public final class NativeAdsProgress {
    private float previous, current;
    public void tick(boolean aiming, float inTicks, float outTicks) {
        previous = current;
        current = aiming ? Math.min(1, current + 1 / inTicks) : Math.max(0, current - 1 / outTicks);
    }
    public float sample(float partial) { return previous + (current - previous) * Math.max(0, Math.min(1, partial)); }
    public void reset() { previous = current = 0; }
}
