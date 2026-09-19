package com.antaurora.apofirstlight.client;

/** Per-instance strip timing; no shared atlas animation or world-time phase. */
public final class HitParticleAnimation {
    private HitParticleAnimation() {}
    public static int frame(int age,int lifetime) { return Math.min(7,Math.max(0,age*8/lifetime)); }
    public static float alpha(int age,int lifetime) {
        return Math.min(1,Math.max(0,2F*(lifetime-age)/lifetime));
    }
    /** 0 = yellow star, 1 = blue star, 2 = swirl. */
    public static int variant(int roll) { return roll<50?0:roll<80?1:2; }
}
