package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<SoundEvent> GEIGER_CLICK = SOUND_EVENTS.register("geiger_click",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geiger_click")));

    private AflSounds() {
    }
}
