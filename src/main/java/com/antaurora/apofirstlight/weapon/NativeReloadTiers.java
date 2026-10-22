package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.survivor.SurvivorGrowth;
import net.minecraft.util.RandomSource;

/**
 * Reloads by gun proficiency tier (docs/dev/gun_proficiency_v1_plan.md 4): below the practised tier the server swaps the
 * reload clip for the tier's own (<clip>_experienced, <clip>_novice), slower and with the eyes on the gun; a novice may get
 * the fumble variant (<clip>_novice_fumble: the magazine catches on the magwell edge and goes in on the second try). The
 * swapped clip sets the reload's length and the moment the rounds go in (its magazine_seat cue). A gun or a fitted
 * magazine without the tier's clip keeps its practised clip and time.
 */
public final class NativeReloadTiers {
    public static final String EXPERIENCED = "_experienced", NOVICE = "_novice", FUMBLE = "_novice_fumble";

    private NativeReloadTiers() {}

    /** The chosen clip with its length and the tick the rounds go in (relative to the start). */
    public record Choice(String clip, int ticks, int magInTick) {}

    /** Fumble chance for a novice at this level: 25% at 0, 18%, 12%, 6% at 3, none from the experienced tier on. */
    public static float fumbleChance(int level) {
        return switch (Math.max(0, level)) {
            case 0 -> 0.25F;
            case 1 -> 0.18F;
            case 2 -> 0.12F;
            case 3 -> 0.06F;
            default -> 0;
        };
    }

    /** The tier's clip for this reload, or null to keep the practised one. */
    public static Choice choose(String asset, String clip, int level, RandomSource random) {
        if (asset == null) return null;
        int tier = SurvivorGrowth.tier(level);
        if (tier >= 2) return null;
        String pick = null;
        if (tier == 0 && random.nextFloat() < fumbleChance(level) && NativeGunAnimations.hasClip(asset, clip + FUMBLE)) pick = clip + FUMBLE;
        if (pick == null && NativeGunAnimations.hasClip(asset, clip + (tier == 0 ? NOVICE : EXPERIENCED))) pick = clip + (tier == 0 ? NOVICE : EXPERIENCED);
        if (pick == null) return null;
        int seat = -1;
        for (var cue : NativeGunAnimations.cues(asset, pick)) if (cue.sound().getPath().endsWith("magazine_seat")) seat = Math.max(seat, cue.tick());
        int ticks = NativeGunAnimations.ticks(asset, pick);
        return seat < 0 ? null : new Choice(pick, ticks, seat);
    }

    /** Whether a clip is one of the tiers' reloads (its length is its own, not the gun's reload time). */
    public static boolean tiered(String clip) {
        return clip.endsWith(EXPERIENCED) || clip.endsWith(NOVICE) || clip.endsWith(FUMBLE);
    }
}
