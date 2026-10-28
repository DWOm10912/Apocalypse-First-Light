package com.antaurora.apofirstlight.cooking;

import com.antaurora.apofirstlight.time.AflTime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The water's heat in an enamel water pail (Cooking V1 K1b, docs/gameplay/cooking_v1_plan.md 7.3): its temperature in the
 * stack's tag ({@link #TEMP_KEY} at the game tick {@link #AT_KEY}), cooling toward {@link #AMBIENT} off the fire by
 * Newton's law, worked out when read (nothing ticks a pail in a pocket); on a lit box stove lid it takes {@link #STOVE_W}.
 * At {@link #BOIL} the heat over what the pail loses boils the water away (latent heat), and {@link #BOIL_SECONDS} at a
 * rolling boil make it boiled water ({@link WaterPailItem#BOILED_KEY}). Real figures on the game clock: a game second is
 * 86 400 / the ticks of a day (1.2 s a tick at 60 minutes a day), so a full pail comes to the boil in about 40 game
 * minutes (100 s real), boiled 3 game minutes later, and boiling on loses about 2.4 L a game hour.
 * <p>Both sides run it: the server keeps the stack, and a client runs the same steps on its copy between the updates
 * ({@link #heat} says when a client should be sent one), as the stove's fuel burns down on both.
 */
public final class PailHeat {
    public static final String TEMP_KEY = "AflWaterC", AT_KEY = "AflWaterAt", BOIL_KEY = "AflBoilS", STEAM_KEY = "AflSteamMl";
    /** °C: the water as scooped and the air it cools to; boiling; loud enough to hear (a simmer). */
    public static final double AMBIENT = 15, BOIL = 100, SIMMER = 88;
    /** W into the pail from a lit stove lid (a cast-iron top at 250-300 °C under a flat bottom: 1.5-2.5 kW). */
    public static final double STOVE_W = 2000;
    /** W per K the open pail loses (sides, surface, evaporation: 100 °C to 60 °C in about 1.5 h). */
    public static final double LOSS_W_PER_K = 6;
    /** J per K of the pail (1.1 kg of enamelled steel), of a mL of water; J to boil a mL away. */
    static final double PAIL_J_PER_K = 1.1 * 460, WATER_J_PER_ML_K = 4.186, LATENT_J_PER_ML = 2257;
    /** Game seconds at a rolling boil that make it boiled water (the CDC's 3 minutes above 2 000 m: on the safe side). */
    public static final double BOIL_SECONDS = 180;

    private PailHeat() {
    }

    /** Game seconds in one tick (60 minutes a day: 1.2). */
    public static double secondsPerTick() {
        return 86_400.0 / AflTime.ticksPerDay();
    }

    private static double capacity(long ml) {
        return PAIL_J_PER_K + ml * WATER_J_PER_ML_K;
    }

    /** °C of the water at game tick {@code now} (cooled since it was last set); {@link #AMBIENT} for an empty or unset pail. */
    public static double temp(ItemStack stack, long now) {
        CompoundTag tag = stack.getTag();
        long ml = WaterPailItem.water(stack);
        if (tag == null || ml <= 0 || !tag.contains(TEMP_KEY)) return AMBIENT;
        double t0 = tag.getDouble(TEMP_KEY), dt = Math.max(0, now - tag.getLong(AT_KEY)) * secondsPerTick();
        return AMBIENT + (t0 - AMBIENT) * Math.exp(-LOSS_W_PER_K * dt / capacity(ml));
    }

    public static void set(ItemStack stack, double celsius, long now) {
        if (WaterPailItem.water(stack) <= 0) return;
        stack.getOrCreateTag().putDouble(TEMP_KEY, celsius);
        stack.getOrCreateTag().putLong(AT_KEY, now);
    }

    /** Takes the heat keys off (an emptied pail). */
    static void clear(CompoundTag tag) {
        tag.remove(TEMP_KEY);
        tag.remove(AT_KEY);
        tag.remove(BOIL_KEY);
        tag.remove(STEAM_KEY);
    }

    /** {@code add} mL of water at {@link #AMBIENT} poured into the pail's {@code have}: mixed, and not boiled any more. */
    static void mixIn(ItemStack stack, long have, long add, long now) {
        double t = (have * temp(stack, now) + add * AMBIENT) / Math.max(1, have + add);
        if (stack.getTag() != null) stack.getTag().remove(BOIL_KEY);
        stack.getOrCreateTag().putDouble(TEMP_KEY, t);
        stack.getOrCreateTag().putLong(AT_KEY, now);
    }

    /** How loud it boils (the boil loop's gain): a quiet simmer at {@link #SIMMER}, full at a rolling boil. */
    public static float loudness(double celsius) {
        return celsius < SIMMER ? 0 : (float) (0.2 + 0.8 * Math.min(1, Math.pow((celsius - SIMMER) / (BOIL - SIMMER), 1.5)));
    }

    /**
     * One tick on a lit stove lid at game tick {@code now}: heats, boils away, counts the time at the boil. True when what
     * a client sees changed (a 5 °C step, the boil starting or stopping, boiled, a tenth of a litre gone): send an update.
     */
    public static boolean heat(ItemStack stack, long now) {
        long ml = WaterPailItem.water(stack);
        if (ml <= 0) return false;
        double t0 = temp(stack, now), dt = secondsPerTick(), c = capacity(ml);
        double t = t0 + STOVE_W * dt / c;
        CompoundTag tag = stack.getOrCreateTag();
        boolean wasBoiled = WaterPailItem.boiled(stack);
        long left = ml;
        if (t >= BOIL) {
            // the heat over the boil goes into steam
            double steam = tag.getDouble(STEAM_KEY) + (t - BOIL) * c / LATENT_J_PER_ML;
            long gone = (long) steam;
            tag.putDouble(STEAM_KEY, steam - gone);
            t = BOIL;
            double at = tag.getDouble(BOIL_KEY) + dt;
            if (at >= BOIL_SECONDS && !wasBoiled) {
                tag.putBoolean(WaterPailItem.BOILED_KEY, true);
                tag.remove(BOIL_KEY);
            } else if (!wasBoiled) tag.putDouble(BOIL_KEY, at);
            if (gone > 0) {
                left = ml - gone;
                WaterPailItem.setWater(stack, left);
                if (left <= 0) return true;   // boiled dry
            }
        }
        set(stack, t, now);
        return (int) (t / 5) != (int) (t0 / 5) || (t >= BOIL) != (t0 >= BOIL) || wasBoiled != WaterPailItem.boiled(stack) || left / 100 != ml / 100;
    }
}
