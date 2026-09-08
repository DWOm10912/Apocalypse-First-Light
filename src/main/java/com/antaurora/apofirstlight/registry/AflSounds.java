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

    public static final RegistryObject<SoundEvent> EXPLOSION_TINNITUS =
            SOUND_EVENTS.register("explosion_tinnitus",
                    () -> SoundEvent.createVariableRangeEvent(
                            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "explosion_tinnitus")));

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

    public static final java.util.List<RegistryObject<SoundEvent>> BR51_01 = java.util.stream.Stream.of(
            "fire", "reload_empty_1", "reload_empty_2", "reload_empty_3", "reload_empty_4",
            "reload_tactical_1", "reload_tactical_2", "reload_tactical_3", "draw", "put_away")
            .map(action -> SOUND_EVENTS.register("br51_01_" + action, () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "br51_01_" + action))))
            .toList();

    public static final RegistryObject<SoundEvent> P9_01_FIRE = pistolSound("fire");
    public static final RegistryObject<SoundEvent> P9_01_SUPPRESSED = pistolSound("suppressed");
    public static final RegistryObject<SoundEvent> P9_01_DRY_FIRE = pistolSound("dry_fire");
    public static final RegistryObject<SoundEvent> P9_01_SLIDE_ACTION = pistolSound("slide_action");
    public static final RegistryObject<SoundEvent> CASING_LANDING = SOUND_EVENTS.register("shell_casings_dropping",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "shell_casings_dropping")));
    public static final RegistryObject<SoundEvent> P9_01_MAGAZINE_OUT = pistolSound("magazine_out");
    public static final RegistryObject<SoundEvent> P9_01_MAGAZINE_IN = pistolSound("magazine_in");

    private static RegistryObject<SoundEvent> pistolSound(String action) {
        String name = "p9_01_" + action;
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, name)));
    }
}
