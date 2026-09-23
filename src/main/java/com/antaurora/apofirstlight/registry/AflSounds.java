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

    public static final RegistryObject<SoundEvent> GEIGER_LOW = SOUND_EVENTS.register("geiger_low",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geiger_low")));
    public static final RegistryObject<SoundEvent> GEIGER_MEDIUM = SOUND_EVENTS.register("geiger_medium",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geiger_medium")));
    public static final RegistryObject<SoundEvent> GEIGER_EXTREME = SOUND_EVENTS.register("geiger_extreme",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geiger_extreme")));
    public static final RegistryObject<SoundEvent> GEIGER_CLICK = SOUND_EVENTS.register("geiger_click",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geiger_click")));
    public static final RegistryObject<SoundEvent> ATTACHMENT_OPERATION = SOUND_EVENTS.register("attachment_operation",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID,"attachment_operation")));
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
    public static final RegistryObject<SoundEvent> GLASS_DOOR_OPEN = SOUND_EVENTS.register("commercial_glass_double_door_open",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "commercial_glass_double_door_open")));
    public static final RegistryObject<SoundEvent> GLASS_DOOR_CLOSE = SOUND_EVENTS.register("commercial_glass_double_door_close",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "commercial_glass_double_door_close")));
    public static final RegistryObject<SoundEvent> CHEST_FREEZER_SLIDE = SOUND_EVENTS.register("chest_freezer_slide",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "chest_freezer_slide")));

    public static final RegistryObject<SoundEvent> VENDING_MACHINE_BREAK = SOUND_EVENTS.register("vending_machine_break",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID,"vending_machine_break")));
    private AflSounds() {
    }

    public static final java.util.List<RegistryObject<SoundEvent>> BR51_01 = java.util.stream.Stream.of(
            "fire", "reload_empty_1", "reload_empty_2", "reload_empty_3", "reload_empty_4",
            "reload_tactical_1", "reload_tactical_2", "reload_tactical_3", "draw", "put_away")
            .map(action -> SOUND_EVENTS.register("br51_01_" + action, () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "br51_01_" + action))))
            .toList();

    public static final RegistryObject<SoundEvent> P9_01_FIRE = pistolSound("fire");
    public static final RegistryObject<SoundEvent> BR51_01_SUPPRESSED = SOUND_EVENTS.register("br51_01_suppressed",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID,"br51_01_suppressed")));
    public static final java.util.List<RegistryObject<SoundEvent>> HR55 = java.util.stream.Stream.of(
            "fire", "fire_suppressed", "reload_tactical", "reload_empty", "inspect", "draw", "put_away")
            .map(action -> SOUND_EVENTS.register("hr55_" + action, () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "hr55_" + action))))
            .toList();
    public static final java.util.List<RegistryObject<SoundEvent>> SILVERWOOD_12 = java.util.stream.Stream.of(
            "fire", "reload_empty", "reload_tactical", "draw", "put_away", "inspect")
            .map(action -> SOUND_EVENTS.register("silverwood_12_" + action, () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "silverwood_12_" + action))))
            .toList();
    public static final java.util.List<RegistryObject<SoundEvent>> CAT = java.util.stream.Stream.of(
            "fire", "reload", "inspect", "draw", "put_away")
            .map(action -> SOUND_EVENTS.register("cat_" + action, () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "cat_" + action))))
            .toList();
    public static final RegistryObject<SoundEvent> CAT_IDLE_1 = catIdleSound("idle_1");
    public static final RegistryObject<SoundEvent> CAT_IDLE_2 = catIdleSound("idle_2");
    public static final RegistryObject<SoundEvent> P9_01_SUPPRESSED = pistolSound("suppressed");
    public static final RegistryObject<SoundEvent> P9_01_DRY_FIRE = pistolSound("dry_fire");
    public static final RegistryObject<SoundEvent> P9_01_SLIDE_ACTION = pistolSound("slide_action");
    public static final RegistryObject<SoundEvent> P9_01_INSPECT = pistolSound("inspect");
    public static final RegistryObject<SoundEvent> P9_01_DRAW = pistolSound("draw");
    public static final RegistryObject<SoundEvent> P9_01_PUT_AWAY = pistolSound("put_away");
    public static final RegistryObject<SoundEvent> CASING_LANDING = SOUND_EVENTS.register("shell_casings_dropping",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "shell_casings_dropping")));
    public static final RegistryObject<SoundEvent> P9_01_MAGAZINE_OUT = pistolSound("magazine_out");
    public static final RegistryObject<SoundEvent> P9_01_MAGAZINE_IN = pistolSound("magazine_in");
    public static final RegistryObject<SoundEvent> NATIVE_GUN_DRY_FIRE = nativeGunSound("dry_fire");
    public static final RegistryObject<SoundEvent> NATIVE_GUN_MAGAZINE_OUT = nativeGunSound("magazine_out");
    public static final RegistryObject<SoundEvent> NATIVE_GUN_MAGAZINE_IN = nativeGunSound("magazine_in");
    public static final RegistryObject<SoundEvent> NATIVE_GUN_ACTION = nativeGunSound("action");
    public static final RegistryObject<SoundEvent> NATIVE_GUN_FIRE_MODE_SWITCH = nativeGunSound("fire_mode_switch");

    private static RegistryObject<SoundEvent> nativeGunSound(String action) {
        String name = "native_gun_" + action;
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, name)));
    }

    private static RegistryObject<SoundEvent> catIdleSound(String action) {
        String name = "cat_" + action;
        return SOUND_EVENTS.register(name, () -> SoundEvent.createFixedRangeEvent(
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, name), 8.0F));
    }

    private static RegistryObject<SoundEvent> pistolSound(String action) {
        String name = "p9_01_" + action;
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, name)));
    }
}
