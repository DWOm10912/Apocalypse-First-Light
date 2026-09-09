package com.antaurora.apofirstlight.weapon.client;

/** Per-confirmed-shot presentation clock; independent of FPS and server tick rate. */
public final class NativeFlashLifetime {
    public static final long DURATION_NANOS = 50_000_000L;
    private boolean started;
    private long startNanos, firstFrame, lastTailFrame;

    public boolean presentSnapshot(long nanos, long frame) {
        if (started) return false;
        started = true;
        startNanos = nanos;
        firstFrame = lastTailFrame = frame;
        return true;
    }

    public boolean expired(long nanos) {
        return started && nanos - startNanos >= DURATION_NANOS;
    }

    /** -1 means no draw; same-frame and repeated hand passes must not double the flash. */
    public float attachedAge(long nanos, long frame) {
        if (!started || frame <= firstFrame || frame == lastTailFrame || expired(nanos)) return -1;
        lastTailFrame = frame;
        return Math.max(0, (float)(nanos - startNanos) / DURATION_NANOS);
    }
}
