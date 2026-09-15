package com.antaurora.apofirstlight.weapon;

/** Shared timing policy between authoritative ammo consumption and GeckoLib shot playback. */
public final class NativeShotAnimationPolicy {
    /** One server tick exposes the mechanical rearward keyframe before the empty baseline takes over. */
    public static final int LAST_SHOT_HANDOFF_TICKS = 1;

    private NativeShotAnimationPolicy() {}

    public static boolean isLastShot(int ammoBefore, int ammoAfter) {
        return ammoBefore == 1 && ammoAfter == 0;
    }

    public static boolean completedShotCanBeReplaced(boolean reload, boolean operation, long now, long end) {
        return !reload && !operation && now >= end;
    }
}
