package com.antaurora.apofirstlight.equipment;

import net.minecraft.world.item.ItemStack;

/** Independent listener-volume and impulse-exposure policies for HEAD equipment. */
public interface HearingProtection {
    float worldSoundMultiplier(ItemStack stack);

    float impulseProtectionMultiplier(ItemStack stack);
}
