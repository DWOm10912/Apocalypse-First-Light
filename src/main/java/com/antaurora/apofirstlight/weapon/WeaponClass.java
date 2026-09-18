package com.antaurora.apofirstlight.weapon;

import java.util.Locale;

/** Stable semantic category used for presentation defaults, never inferred from a registry ID. */
public enum WeaponClass {
    PISTOL, RIFLE, PRECISION_RIFLE, MACHINE_GUN, SMG, SHOTGUN, SPECIAL;

    public static WeaponClass parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("weapon_class: unknown value " + value);
        }
    }
}
