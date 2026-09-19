package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.client.config.NativeCrosshairConfig;

/** Frame-rate-independent presentation state, owned by the local held-gun view. */
public final class NativeCrosshairMotion {
    private double gap,bloom;
    public void reset(double target) { gap=target;bloom=0; }
    public void shot(NativeCrosshairConfig c) { bloom=Math.min(c.maxBloom(),bloom+c.shotImpulse()); }
    public void advance(double seconds,double target,NativeCrosshairConfig c) {
        double dt=Math.max(0,seconds);
        gap=target+(gap-target)*Math.exp(-c.responseSpeed()*dt);
        bloom*=Math.exp(-c.recoverySpeed()*dt);
    }
    public double gap(NativeCrosshairConfig c) { return Math.min(c.maxGap(),Math.max(c.baseGap(),gap+bloom)); }
}
