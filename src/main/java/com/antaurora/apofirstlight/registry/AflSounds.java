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
    public static final RegistryObject<SoundEvent> GLASS_DOOR_OPEN = SOUND_EVENTS.register("glass_door_open",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "glass_door_open")));
    public static final RegistryObject<SoundEvent> GLASS_DOOR_CLOSE = SOUND_EVENTS.register("glass_door_close",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "glass_door_close")));
    public static final RegistryObject<SoundEvent> CRUSHER_RUNNING = SOUND_EVENTS.register("crusher_running",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "crusher_running")));
    public static final RegistryObject<SoundEvent> INDUSTRIAL_FURNACE_RUNNING =
            SOUND_EVENTS.register("industrial_furnace_running",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "industrial_furnace_running")));
    public static final RegistryObject<SoundEvent> COMPRESSOR_RUNNING = SOUND_EVENTS.register("compressor_running",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "compressor_running")));
    public static final RegistryObject<SoundEvent> ALLOY_FURNACE_RUNNING =
            SOUND_EVENTS.register("alloy_furnace_running",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "alloy_furnace_running")));

    private AflSounds() {
    }
}
