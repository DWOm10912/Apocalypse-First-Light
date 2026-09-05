package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.explosion.ExplosionTinnitusProfile;

/** Small, non-persistent timeline; independent of rendering/audio APIs for deterministic tests. */
public final class ExplosionTinnitusEnvelope {
    public enum TriggerResult { IGNORE, RESTART, EXTEND }

    private float severity;
    private int elapsedTicks;
    private int durationTicks;

    public TriggerResult trigger(float incoming) {
        if (!ExplosionTinnitusProfile.shouldTrigger(incoming)) return TriggerResult.IGNORE;
        return triggerAccepted(incoming);
    }

    /** Separate visual timeline, with the original squared scalar retained for the unchanged timing curve. */
    public TriggerResult triggerOverlay(float incoming) {
        if (!ExplosionTinnitusProfile.shouldTriggerOverlay(incoming)) return TriggerResult.IGNORE;
        return triggerAccepted(incoming);
    }

    private TriggerResult triggerAccepted(float incoming) {
        if (!active() || incoming > severity) {
            int remaining = Math.max(0, durationTicks - elapsedTicks);
            severity = incoming;
            elapsedTicks = 0;
            durationTicks = Math.max(remaining, ExplosionTinnitusProfile.durationTicks(incoming));
            return TriggerResult.RESTART;
        }
        durationTicks = Math.min(ExplosionTinnitusProfile.MAX_PLAYBACK_TICKS,
                durationTicks + ExplosionTinnitusProfile.WEAK_EXTENSION_TICKS);
        return TriggerResult.EXTEND;
    }

    public void tick() {
        if (active() && ++elapsedTicks >= durationTicks) clear();
    }

    public boolean active() {
        return durationTicks > 0;
    }

    public float volume() {
        return active() ? ExplosionTinnitusProfile.initialVolume(severity)
                * ExplosionTinnitusProfile.envelope(elapsedTicks, durationTicks, 0.15F, 1.0F) : 0;
    }

    public float overlayAlpha(float partialTick) {
        return active() ? ExplosionTinnitusProfile.peakOverlayAlpha(ExplosionTinnitusProfile.overlaySeverity(severity))
                * ExplosionTinnitusProfile.envelope(elapsedTicks + Math.max(0, Math.min(1, partialTick)),
                durationTicks, 0.10F, 0.85F) : 0;
    }

    public int remainingTicks() {
        return Math.max(0, durationTicks - elapsedTicks);
    }

    public float severity() {
        return severity;
    }

    public void clear() {
        severity = 0;
        elapsedTicks = 0;
        durationTicks = 0;
    }
}
