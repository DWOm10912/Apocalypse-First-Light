package com.antaurora.apofirstlight.registry;

import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.common.util.ForgeSoundType;

/**
 * Shared block sound sets by acoustic class (mining hit, break, place, footstep, fall), reused by every block of that class.
 * ForgeSoundType takes suppliers because the SoundEvents are registered after this class loads.
 * Noise radii for infected hearing come from block tags, not from these sound types.
 */
public final class AflSoundTypes {
    /** Hollow painted sheet-steel cabinets (industrial locker; later filing cabinets, electrical boxes, bins). */
    public static final SoundType SHEET_METAL = new ForgeSoundType(1.0F, 1.0F,
            AflSounds.SHEET_METAL_BREAK, AflSounds.SHEET_METAL_STEP, AflSounds.SHEET_METAL_PLACE,
            AflSounds.SHEET_METAL_HIT, AflSounds.SHEET_METAL_FALL);

    private AflSoundTypes() {
    }
}
