package com.antaurora.apofirstlight.health;

import net.minecraft.util.Mth;

/**
 * Client copy of the own lightning injury (LightningPackets.State), counted down here between syncs (client/LightningInjuryClient
 * ticks it) so the badges drain smoothly; fainted and paralysed hold at their last tick until the server says they are over,
 * so the client never stands up before the server does. Cleared on leaving a world.
 */
public final class ClientLightningInjury {
    private static int faint, faintTotal, paralysis, paralysisTotal, weak, weakTotal;
    private static float eardrum, aftermath, eardrumStep, aftermathStep;
    private static byte eardrumSide;

    private ClientLightningInjury() {
    }

    static void accept(LightningPackets.State p) {
        faint = Math.max(0, p.faint());
        faintTotal = Math.max(faint, p.faintTotal());
        paralysis = Math.max(0, p.paralysis());
        paralysisTotal = Math.max(paralysis, p.paralysisTotal());
        weak = Math.max(0, p.weak());
        weakTotal = Math.max(weak, p.weakTotal());
        eardrum = Mth.clamp(p.eardrum(), 0, 1);
        aftermath = Mth.clamp(p.aftermath(), 0, 1);
        eardrumSide = p.eardrumSide();
        var c = LightningInjuryConfig.get();
        long day = WoundConfig.get().dayTicks();
        eardrumStep = (float) (1 / (c.eardrumDays * day));
        aftermathStep = (float) (1 / (c.aftermathDays * day));
    }

    /** One client tick (asleep: × sleep). */
    public static void tick(boolean asleep) {
        if (faint > 1) faint--;
        else if (faint == 0 && paralysis > 1) paralysis--;
        else if (faint == 0 && paralysis == 0 && weak > 0) weak--;
        double rate = asleep ? LightningInjuryConfig.get().sleep : 1;
        if (eardrum > 0) eardrum = (float) Math.max(0, eardrum - eardrumStep * rate);
        if (aftermath > 0) aftermath = (float) Math.max(0, aftermath - aftermathStep * rate);
    }

    public static void clear() {
        faint = faintTotal = paralysis = paralysisTotal = weak = weakTotal = 0;
        eardrum = aftermath = 0;
        eardrumSide = 0;
    }

    public static boolean any() {
        return faint > 0 || paralysis > 0 || weak > 0 || eardrum > 0 || aftermath > 0;
    }

    public static boolean fainted() {
        return faint > 0;
    }

    public static boolean down() {
        return faint > 0 || paralysis > 0;
    }

    public static boolean paralysed() {
        return faint == 0 && paralysis > 0;
    }

    public static boolean weakLegs() {
        return faint == 0 && paralysis == 0 && weak > 0;
    }

    /** No sprinting lying down or on weak legs (LocalPlayerWeightSprintMixin). */
    public static boolean sprintBlocked() {
        return faint > 0 || paralysis > 0 || weak > 0;
    }

    /** The legs badge: lying (fainted counts as paralysed) then weak legs; share left of the whole (paralysis then weakness). */
    public static double legsLeft() {
        int total = paralysisTotal + weakTotal;
        return total <= 0 ? 0 : Mth.clamp((double) (paralysis + weak) / total, 0, 1);
    }

    public static boolean legs() {
        return paralysis > 0 || weak > 0;
    }

    public static double faintLeft() {
        return faintTotal <= 0 ? 0 : (double) faint / faintTotal;
    }

    public static float eardrumLeft() {
        return eardrum;
    }

    /** The bad ear: -1 left, 1 right. */
    public static int eardrumSide() {
        return eardrumSide;
    }

    public static float aftermathLeft() {
        return aftermath;
    }

    /** The world's sounds × this for the burst eardrum (back to whole over its last eardrum_heal share). */
    public static float eardrumHearing() {
        if (eardrum <= 0) return 1;
        var c = LightningInjuryConfig.get();
        double heal = c.eardrumHeal <= 0 ? 1 : Math.min(1, eardrum / c.eardrumHeal);
        return (float) Mth.lerp(heal, 1, c.eardrumHearing);
    }

    /** Aim sway × this (the aftermath). */
    public static float sway() {
        return (float) Mth.lerp(LightningInjury.aftermath(aftermath), 1, LightningInjuryConfig.get().aftermathSway);
    }
}
