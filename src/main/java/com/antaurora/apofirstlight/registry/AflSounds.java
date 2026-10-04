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
    // Industrial locker door (the close file's slam sits at ~0.47 s, the end of the 10-tick door animation)
    public static final RegistryObject<SoundEvent> INDUSTRIAL_LOCKER_OPEN = simple("industrial_locker_open");
    public static final RegistryObject<SoundEvent> INDUSTRIAL_LOCKER_CLOSE = simple("industrial_locker_close");
    // Progressive Container Search: the one search sound for every searchable container, a seamless rustle loop
    // (tools/build-container-search-sounds-v2.mjs), looped by client/ContainerSearchSoundController while a search runs
    public static final RegistryObject<SoundEvent> CONTAINER_SEARCH_RUMMAGE = simple("container_search_rummage");
    // Office chair (entity) rolling: a seamless loop (tools/build-office-chair-sounds-v1.mjs), looped by
    // client/OfficeChairRollSound while a chair moves, volume and pitch following its speed
    public static final RegistryObject<SoundEvent> OFFICE_CHAIR_ROLL = simple("office_chair_roll");
    // Stamina V1: single nasal breaths, one random variant at a time while tired (light) or winded (heavy)
    // (tools/build-stamina-breath-sounds-v1.mjs), scheduled by stamina/ClientStamina
    public static final RegistryObject<SoundEvent> STAMINA_BREATH_LIGHT = simple("stamina_breath_light");
    public static final RegistryObject<SoundEvent> STAMINA_BREATH_HEAVY = simple("stamina_breath_heavy");
    // Metal Trash Can V2 lid, on the 8-tick lid animation (tools/build-metal-trash-can-sounds-v1.mjs)
    public static final RegistryObject<SoundEvent> METAL_TRASH_CAN_OPEN = simple("metal_trash_can_open");
    public static final RegistryObject<SoundEvent> METAL_TRASH_CAN_CLOSE = simple("metal_trash_can_close");
    // Commercial Dumpster V2 lids (every colour), on the 10-tick lid animation (tools/build-commercial-dumpster-sounds-v1.mjs)
    public static final RegistryObject<SoundEvent> COMMERCIAL_DUMPSTER_OPEN = simple("commercial_dumpster_open");
    public static final RegistryObject<SoundEvent> COMMERCIAL_DUMPSTER_CLOSE = simple("commercial_dumpster_close");
    // Checkout Counter V1 gate: on the flap (0.8 s) and door (0.4 s) animation (tools/build-checkout-counter-gate-sounds-v1.mjs)
    public static final RegistryObject<SoundEvent> CHECKOUT_COUNTER_GATE_OPEN = simple("checkout_counter_gate_open");
    public static final RegistryObject<SoundEvent> CHECKOUT_COUNTER_GATE_CLOSE = simple("checkout_counter_gate_close");
    // Lead Chest lid: mixed onto the 0.7 s lid animation by tools/build-lead-chest-sounds-v1.mjs
    public static final RegistryObject<SoundEvent> LEAD_CHEST_OPEN = simple("lead_chest_open");
    public static final RegistryObject<SoundEvent> LEAD_CHEST_CLOSE = simple("lead_chest_close");
    // Industrial Electrical Box: tools/build-industrial-electrical-box-sounds-v1.mjs (one latch click for lock and unlock)
    public static final RegistryObject<SoundEvent> INDUSTRIAL_ELECTRICAL_BOX_LATCH = simple("industrial_electrical_box_latch");
    public static final RegistryObject<SoundEvent> INDUSTRIAL_ELECTRICAL_BOX_OPEN = simple("industrial_electrical_box_open");
    public static final RegistryObject<SoundEvent> INDUSTRIAL_ELECTRICAL_BOX_CLOSE = simple("industrial_electrical_box_close");
    // Cash Register: tools/build-cash-register-sounds-v1.mjs (the open sound carries the bell)
    public static final RegistryObject<SoundEvent> CASH_REGISTER_OPEN = simple("cash_register_open");
    public static final RegistryObject<SoundEvent> CASH_REGISTER_CLOSE = simple("cash_register_close");
    // Beverage Cooler doors: tools/build-beverage-cooler-sounds-v1.mjs (both doors share them)
    public static final RegistryObject<SoundEvent> BEVERAGE_COOLER_DOOR_OPEN = simple("beverage_cooler_door_open");
    public static final RegistryObject<SoundEvent> BEVERAGE_COOLER_DOOR_CLOSE = simple("beverage_cooler_door_close");
    // Beverage Cooler compressor (tools/build-beverage-cooler-compressor-sounds-v1.mjs)
    public static final RegistryObject<SoundEvent> BEVERAGE_COOLER_COMPRESSOR_START = simple("beverage_cooler_compressor_start");
    public static final RegistryObject<SoundEvent> BEVERAGE_COOLER_COMPRESSOR_STOP = simple("beverage_cooler_compressor_stop");
    public static final RegistryObject<SoundEvent> BEVERAGE_COOLER_COMPRESSOR_LOOP = simple("beverage_cooler_compressor_loop");
    // Charging Station (tools/build-charging-station-sounds-v1.mjs); placing / taking the item uses the vanilla leather equip sound
    public static final RegistryObject<SoundEvent> CHARGING_STATION_START = simple("charging_station_start");
    public static final RegistryObject<SoundEvent> CHARGING_STATION_FULL = simple("charging_station_full");
    public static final RegistryObject<SoundEvent> CHARGING_STATION_HUM = simple("charging_station_hum");
    // Hollow sheet-metal block sounds (AflSoundTypes.SHEET_METAL); sounds.json cancels the vanilla SoundType pitch factors
    public static final RegistryObject<SoundEvent> SHEET_METAL_BREAK = simple("sheet_metal_break");
    public static final RegistryObject<SoundEvent> SHEET_METAL_STEP = simple("sheet_metal_step");
    public static final RegistryObject<SoundEvent> SHEET_METAL_PLACE = simple("sheet_metal_place");
    public static final RegistryObject<SoundEvent> SHEET_METAL_HIT = simple("sheet_metal_hit");
    public static final RegistryObject<SoundEvent> SHEET_METAL_FALL = simple("sheet_metal_fall");

    private static RegistryObject<SoundEvent> simple(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, name)));
    }

    private AflSounds() {
    }

    public static final java.util.List<RegistryObject<SoundEvent>> BR51_01 = java.util.stream.Stream.of(
            "fire", "reload_empty_1", "reload_empty_2", "reload_empty_3", "reload_empty_4",
            "reload_tactical_1", "reload_tactical_2", "reload_tactical_3", "draw", "put_away",
            "inspect_slide_back", "inspect_slide_release")
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
            "fire", "open", "eject", "shell_insert", "close")
            .map(action -> SOUND_EVENTS.register("silverwood_12_" + action, () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "silverwood_12_" + action))))
            .toList();
    public static final java.util.List<RegistryObject<SoundEvent>> BLACKRIDGE_50 = java.util.stream.Stream.of(
            "fire", "suppressed", "magazine_release", "magazine_out", "magazine_flick", "magazine_in",
            "slide_back", "slide_release", "safety_lever")
            .map(action -> SOUND_EVENTS.register("blackridge_50_" + action, () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "blackridge_50_" + action))))
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
    public static final RegistryObject<SoundEvent> P9_01_MAGAZINE_RELEASE = pistolSound("magazine_release");
    public static final RegistryObject<SoundEvent> P9_01_MAGAZINE_SEAT = pistolSound("magazine_seat");
    public static final RegistryObject<SoundEvent> P9_01_SLIDE_BACK = pistolSound("slide_back");
    public static final RegistryObject<SoundEvent> P9_01_SLIDE_RELEASE = pistolSound("slide_release");
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
