package com.antaurora.apofirstlight.tinnitus;

/** One player's transient server-side exposure. This state is deliberately never serialized. */
public final class GunshotExposureAccumulator {
    private double exposure;
    private long lastUpdateTick = Long.MIN_VALUE;
    private long lastImpulseTick = Long.MIN_VALUE;
    private float lastSentSeverity;

    /** Returns an impulse severity, or zero when no packet should be sent for this shot. */
    public float recordShot(double shotExposure, long serverTick) {
        decayTo(serverTick);
        if (!Double.isFinite(shotExposure) || shotExposure <= 0.0) {
            return 0.0F;
        }
        exposure = Math.min(GunshotTinnitusProfile.MAX_ACCUMULATED_EXPOSURE,
                exposure + Math.min(GunshotTinnitusProfile.MAX_SHOT_EXPOSURE, shotExposure));
        if (GunshotTinnitusProfile.thresholdBand(exposure) == 0 || coolingDown(serverTick)) {
            return 0.0F;
        }

        float severity = GunshotTinnitusProfile.severity(exposure);
        boolean significantIncrease = severity - lastSentSeverity
                >= GunshotTinnitusProfile.SIGNIFICANT_SEVERITY_DELTA;
        boolean needsExtension = lastImpulseTick == Long.MIN_VALUE
                || serverTick - lastImpulseTick >= GunshotTinnitusProfile.EXTENSION_PACKET_INTERVAL_TICKS;
        if (!significantIncrease && !needsExtension) {
            return 0.0F;
        }
        lastImpulseTick = serverTick;
        lastSentSeverity = Math.max(lastSentSeverity, severity);
        exposure *= GunshotTinnitusProfile.POST_IMPULSE_RETENTION;
        return severity;
    }

    public double exposureAt(long serverTick) {
        decayTo(serverTick);
        return exposure;
    }

    public boolean activeAt(long serverTick) {
        return exposureAt(serverTick) > 0.0;
    }

    private void decayTo(long serverTick) {
        if (lastUpdateTick == Long.MIN_VALUE) {
            lastUpdateTick = serverTick;
            return;
        }
        long elapsed = Math.max(0L, serverTick - lastUpdateTick);
        if (elapsed > 0L) {
            exposure = Math.max(0.0,
                    exposure - elapsed * GunshotTinnitusProfile.DECAY_PER_TICK);
            lastUpdateTick = serverTick;
        }
    }

    private boolean coolingDown(long serverTick) {
        return lastImpulseTick != Long.MIN_VALUE
                && serverTick - lastImpulseTick < GunshotTinnitusProfile.IMPULSE_COOLDOWN_TICKS;
    }
}
