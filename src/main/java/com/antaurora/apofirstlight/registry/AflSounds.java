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
    /** Geiger counter V2 sound (2026-10-15): one tick per count (four cut variants) and the dense loop for high rates. */
    public static final RegistryObject<SoundEvent> GEIGER_CLICK = SOUND_EVENTS.register("geiger_click",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geiger_click")));
    public static final RegistryObject<SoundEvent> GEIGER_DENSE = SOUND_EVENTS.register("geiger_dense",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geiger_dense")));
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
    // Fuel Dispenser V1 nozzle: cut and loudness-matched by tools/build-fuel-nozzle-sounds-v1.mjs
    public static final RegistryObject<SoundEvent> FUEL_NOZZLE_TAKE = simple("fuel_nozzle_take");
    public static final RegistryObject<SoundEvent> FUEL_NOZZLE_HANG = simple("fuel_nozzle_hang");
    public static final RegistryObject<SoundEvent> FUEL_NOZZLE_BREAKAWAY = simple("fuel_nozzle_breakaway");
    // Lead Chest lid: mixed onto the 0.7 s lid animation by tools/build-lead-chest-sounds-v1.mjs
    public static final RegistryObject<SoundEvent> LEAD_CHEST_OPEN = simple("lead_chest_open");
    public static final RegistryObject<SoundEvent> LEAD_CHEST_CLOSE = simple("lead_chest_close");
    // Distribution Panel door: tools/build-distribution-panel-sounds-v1.mjs (made for the removed Industrial Electrical Box,
    // whose door ran the same 10 ticks; the latch click plays as the door opens)
    public static final RegistryObject<SoundEvent> DISTRIBUTION_PANEL_LATCH = simple("distribution_panel_latch");
    public static final RegistryObject<SoundEvent> DISTRIBUTION_PANEL_OPEN = simple("distribution_panel_open");
    public static final RegistryObject<SoundEvent> DISTRIBUTION_PANEL_CLOSE = simple("distribution_panel_close");
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
    // Diesel standby generator V1: registered silent (sounds.json lists no files) until the user's recordings are mixed in
    public static final RegistryObject<SoundEvent> DIESEL_GENERATOR_CRANK = simple("diesel_generator_crank");
    public static final RegistryObject<SoundEvent> DIESEL_GENERATOR_START = simple("diesel_generator_start");
    public static final RegistryObject<SoundEvent> DIESEL_GENERATOR_RUN = simple("diesel_generator_run");
    public static final RegistryObject<SoundEvent> DIESEL_GENERATOR_STOP = simple("diesel_generator_stop");
    /** Portable diesel generator V1: cut from the user's recordings (docs/machines/portable_diesel_generator_v1.md "声音"). */
    public static final RegistryObject<SoundEvent> PORTABLE_GENERATOR_PULL = simple("portable_generator_pull");
    public static final RegistryObject<SoundEvent> PORTABLE_GENERATOR_PULL_FAIL = simple("portable_generator_pull_fail");
    public static final RegistryObject<SoundEvent> PORTABLE_GENERATOR_START = simple("portable_generator_start");
    public static final RegistryObject<SoundEvent> PORTABLE_GENERATOR_RUN = simple("portable_generator_run");
    public static final RegistryObject<SoundEvent> PORTABLE_GENERATOR_STOP = simple("portable_generator_stop");
    // T15 pickup V1 (docs/vehicles/first_pickup_v0_design.md 8.11, tools/build-pickup-t15-sounds.mjs; client/vehicle/TruckSounds)
    public static final RegistryObject<SoundEvent> TRUCK_IDLE = simple("truck_idle");
    public static final RegistryObject<SoundEvent> TRUCK_MID = simple("truck_mid");
    public static final RegistryObject<SoundEvent> TRUCK_LOAD = simple("truck_load");
    public static final RegistryObject<SoundEvent> TRUCK_COAST = simple("truck_coast");
    public static final RegistryObject<SoundEvent> TRUCK_CRANK = simple("truck_crank");
    public static final RegistryObject<SoundEvent> TRUCK_CATCH = simple("truck_catch");
    public static final RegistryObject<SoundEvent> TRUCK_STOP = simple("truck_stop");
    public static final RegistryObject<SoundEvent> TRUCK_GLOW_CLICK = simple("truck_glow_click");
    public static final RegistryObject<SoundEvent> TRUCK_KEY = simple("truck_key");
    public static final RegistryObject<SoundEvent> TRUCK_TYRES_ASPHALT = simple("truck_tyres_asphalt");
    public static final RegistryObject<SoundEvent> TRUCK_TYRES_GRAVEL = simple("truck_tyres_gravel");
    public static final RegistryObject<SoundEvent> TRUCK_TYRES_DIRT = simple("truck_tyres_dirt");
    public static final RegistryObject<SoundEvent> TRUCK_TYRES_GRASS = simple("truck_tyres_grass");
    public static final RegistryObject<SoundEvent> TRUCK_WIND = simple("truck_wind");
    public static final RegistryObject<SoundEvent> TRUCK_SKID = simple("truck_skid");
    public static final RegistryObject<SoundEvent> TRUCK_BOTTOM_OUT = simple("truck_bottom_out");
    public static final RegistryObject<SoundEvent> TRUCK_CRASH_LIGHT = simple("truck_crash_light");
    public static final RegistryObject<SoundEvent> TRUCK_CRASH_HEAVY = simple("truck_crash_heavy");
    public static final RegistryObject<SoundEvent> TRUCK_SCRAPE = simple("truck_scrape");
    public static final RegistryObject<SoundEvent> TRUCK_DOOR_OPEN = simple("truck_door_open");
    public static final RegistryObject<SoundEvent> TRUCK_DOOR_CLOSE = simple("truck_door_close");
    public static final RegistryObject<SoundEvent> TRUCK_TAILGATE = simple("truck_tailgate");
    public static final RegistryObject<SoundEvent> TRUCK_FUEL_CAP_OPEN = simple("truck_fuel_cap_open");
    public static final RegistryObject<SoundEvent> TRUCK_FUEL_CAP_CLOSE = simple("truck_fuel_cap_close");
    public static final RegistryObject<SoundEvent> TRUCK_SHIFT = simple("truck_shift");
    public static final RegistryObject<SoundEvent> TRUCK_TRANSFER = simple("truck_transfer");
    public static final RegistryObject<SoundEvent> TRUCK_HANDBRAKE_PULL = simple("truck_handbrake_pull");
    public static final RegistryObject<SoundEvent> TRUCK_HANDBRAKE_RELEASE = simple("truck_handbrake_release");
    public static final RegistryObject<SoundEvent> TRUCK_HORN = simple("truck_horn");
    // T15 turn signals (tools/build-pickup-t15-signal-sounds.mjs): the stalk on / off, the flasher relay as the lamps light / go out
    public static final RegistryObject<SoundEvent> TRUCK_SIGNAL_ON = simple("truck_signal_on");
    public static final RegistryObject<SoundEvent> TRUCK_SIGNAL_OFF = simple("truck_signal_off");
    public static final RegistryObject<SoundEvent> TRUCK_SIGNAL_TICK = simple("truck_signal_tick");
    public static final RegistryObject<SoundEvent> TRUCK_SIGNAL_TOCK = simple("truck_signal_tock");
    public static final RegistryObject<SoundEvent> TRUCK_WIPER = simple("truck_wiper");
    public static final RegistryObject<SoundEvent> TRUCK_WIPER_RETURN = simple("truck_wiper_return");
    public static final RegistryObject<SoundEvent> TRUCK_TYRES_WET = simple("truck_tyres_wet");
    public static final RegistryObject<SoundEvent> TRUCK_CAB_RAIN = simple("truck_cab_rain");
    // Hollow sheet-metal block sounds (AflSoundTypes.SHEET_METAL); sounds.json cancels the vanilla SoundType pitch factors
    public static final RegistryObject<SoundEvent> SHEET_METAL_BREAK = simple("sheet_metal_break");
    public static final RegistryObject<SoundEvent> SHEET_METAL_STEP = simple("sheet_metal_step");
    public static final RegistryObject<SoundEvent> SHEET_METAL_PLACE = simple("sheet_metal_place");
    public static final RegistryObject<SoundEvent> SHEET_METAL_HIT = simple("sheet_metal_hit");
    public static final RegistryObject<SoundEvent> SHEET_METAL_FALL = simple("sheet_metal_fall");

    // Intake Pump (tools/build-intake-pump-sounds-v1.mjs)
    public static final RegistryObject<SoundEvent> INTAKE_PUMP_SWITCH = simple("intake_pump_switch");
    public static final RegistryObject<SoundEvent> INTAKE_PUMP_START = simple("intake_pump_start");
    public static final RegistryObject<SoundEvent> INTAKE_PUMP_STOP = simple("intake_pump_stop");
    public static final RegistryObject<SoundEvent> INTAKE_PUMP_LOOP = simple("intake_pump_loop");
    public static final RegistryObject<SoundEvent> FUEL_NOZZLE_SPRAY = simple("fuel_nozzle_spray");
    /** The nozzle put into a fuel opening (2026-10-10): in, the fill loop, the automatic shut-off, out. Placeholder files until the user's recording. */
    public static final RegistryObject<SoundEvent> FUEL_NOZZLE_INSERT = simple("fuel_nozzle_insert");
    public static final RegistryObject<SoundEvent> FUEL_NOZZLE_FILL = simple("fuel_nozzle_fill");
    public static final RegistryObject<SoundEvent> FUEL_NOZZLE_SHUT_OFF = simple("fuel_nozzle_shut_off");
    public static final RegistryObject<SoundEvent> FUEL_NOZZLE_PULL = simple("fuel_nozzle_pull");
    /** A breaker tripping on an overload (2026-10-10): the distribution panel's main, the portable generator's (tools/build-breaker-trip-sound-v1.mjs). */
    public static final RegistryObject<SoundEvent> BREAKER_TRIP = simple("breaker_trip");
    /** Fuel fire (docs/gameplay/fuel_fire_v1.md "声音", tools/build-fuel-fire-sounds-v1.mjs): catching, burning, a tank going up. */
    public static final RegistryObject<SoundEvent> FUEL_IGNITE = simple("fuel_ignite");
    public static final RegistryObject<SoundEvent> FUEL_FIRE_LOOP = simple("fuel_fire_loop");
    public static final RegistryObject<SoundEvent> FUEL_EXPLODE = simple("fuel_explode");
    /** Jerry can pour (docs/models/fuel_containers_v1.md "声音", tools/build-jerry-can-pour-sounds-v1.mjs; client/JerryCanPourSounds). */
    public static final RegistryObject<SoundEvent> JERRY_CAN_OPEN = simple("jerry_can_open");
    public static final RegistryObject<SoundEvent> JERRY_CAN_POUR = simple("jerry_can_pour");
    public static final RegistryObject<SoundEvent> JERRY_CAN_CLOSE = simple("jerry_can_close");
    /** A container's cap screwed off / back on where it stands (FuelCanBlock OPEN, 2026-10-10): the jerry can's cap, a drum's 2" bung.
     *  The first-person pour sounds above run on that animation's 2-3 s clock, so they do not fit an instant cap. All four play the panel's latch click
     *  at their own pitch (sounds.json); the user kept it (2026-10-10: generated cap foley comes out clanky). */
    public static final RegistryObject<SoundEvent> JERRY_CAN_CAP_OPEN = simple("jerry_can_cap_open");
    public static final RegistryObject<SoundEvent> JERRY_CAN_CAP_CLOSE = simple("jerry_can_cap_close");
    public static final RegistryObject<SoundEvent> DRUM_BUNG_OPEN = simple("drum_bung_open");
    public static final RegistryObject<SoundEvent> DRUM_BUNG_CLOSE = simple("drum_bung_close");
    /**
     * The forest start home's own pieces (docs/audio/forest_home_sounds_v1.md, tools/build-forest-home-sounds-v1.mjs; the
     * user's takes, 2026-10-14). All play on noise/RangedSound with their noise radius; variants are sounds.json entries.
     */
    public static final RegistryObject<SoundEvent> BOX_STOVE_DOOR_OPEN = simple("box_stove_door_open");
    public static final RegistryObject<SoundEvent> BOX_STOVE_DOOR_CLOSE = simple("box_stove_door_close");
    public static final RegistryObject<SoundEvent> BOX_STOVE_ADD_WOOD = simple("box_stove_add_wood");
    public static final RegistryObject<SoundEvent> BOX_STOVE_FIRE_LOOP = simple("box_stove_fire_loop");
    public static final RegistryObject<SoundEvent> BOX_STOVE_BOIL_LOOP = simple("box_stove_boil_loop");
    public static final RegistryObject<SoundEvent> BOX_STOVE_FRY_LOOP = simple("box_stove_fry_loop");
    public static final RegistryObject<SoundEvent> EMERGENCY_RADIO_SWITCH_ON = simple("emergency_radio_switch_on");
    public static final RegistryObject<SoundEvent> EMERGENCY_RADIO_SWITCH_OFF = simple("emergency_radio_switch_off");
    public static final RegistryObject<SoundEvent> EMERGENCY_RADIO_CRANK = simple("emergency_radio_crank");
    public static final RegistryObject<SoundEvent> EMERGENCY_RADIO_STATIC_LOOP = simple("emergency_radio_static_loop");
    public static final RegistryObject<SoundEvent> WOOD_CABINET_OPEN = simple("wood_cabinet_open");
    public static final RegistryObject<SoundEvent> WOOD_CABINET_CLOSE = simple("wood_cabinet_close");
    public static final RegistryObject<SoundEvent> LAMP_PULL_CHAIN = simple("lamp_pull_chain");
    public static final RegistryObject<SoundEvent> WALL_SWITCH_CLICK = simple("wall_switch_click");
    public static final RegistryObject<SoundEvent> POWER_PLUG_IN = simple("power_plug_in");
    public static final RegistryObject<SoundEvent> POWER_PLUG_OUT = simple("power_plug_out");
    public static final RegistryObject<SoundEvent> RURAL_MAILBOX_FLAG_UP = simple("rural_mailbox_flag_up");
    public static final RegistryObject<SoundEvent> RURAL_MAILBOX_FLAG_DOWN = simple("rural_mailbox_flag_down");
    public static final RegistryObject<SoundEvent> TUBE_FARM_GATE_OPEN = simple("tube_farm_gate_open");
    public static final RegistryObject<SoundEvent> TUBE_FARM_GATE_CLOSE = simple("tube_farm_gate_close");
    public static final RegistryObject<SoundEvent> TREE_CHOP = simple("tree_chop");
    public static final RegistryObject<SoundEvent> TREE_CRACK = simple("tree_crack");
    public static final RegistryObject<SoundEvent> TREE_CRASH = simple("tree_crash");
    // Flip lighter V1: placeholders on vanilla events in sounds.json until the user records them (Firefly)
    public static final RegistryObject<SoundEvent> FLIP_LIGHTER_OPEN = simple("flip_lighter_open");
    public static final RegistryObject<SoundEvent> FLIP_LIGHTER_STRIKE = simple("flip_lighter_strike");
    public static final RegistryObject<SoundEvent> FLIP_LIGHTER_CLOSE = simple("flip_lighter_close");
    /** Health V1a medical use (docs/gameplay/survival_health_v1a.md 7.6, tools/build-medical-sounds-v1a.cjs): pieces of the user's
     *  Firefly takes, played by health/MedicalItem on the actions' keyframes. */
    public static final RegistryObject<SoundEvent> MEDICAL_BANDAGE_GRAB = simple("medical_bandage_grab");
    public static final RegistryObject<SoundEvent> MEDICAL_BANDAGE_PRESS = simple("medical_bandage_press");
    public static final RegistryObject<SoundEvent> MEDICAL_BANDAGE_WRAP_1 = simple("medical_bandage_wrap_1");
    public static final RegistryObject<SoundEvent> MEDICAL_BANDAGE_WRAP_2 = simple("medical_bandage_wrap_2");
    public static final RegistryObject<SoundEvent> MEDICAL_BANDAGE_WRAP_3 = simple("medical_bandage_wrap_3");
    public static final RegistryObject<SoundEvent> MEDICAL_BANDAGE_LAY = simple("medical_bandage_lay");
    public static final RegistryObject<SoundEvent> MEDICAL_BANDAGE_TEAR = simple("medical_bandage_tear");
    public static final RegistryObject<SoundEvent> MEDICAL_BANDAGE_PAT = simple("medical_bandage_pat");
    public static final RegistryObject<SoundEvent> MEDICAL_BANDAGE_LOWER = simple("medical_bandage_lower");
    public static final RegistryObject<SoundEvent> MEDICAL_ALCOHOL_TWIST_1 = simple("medical_alcohol_twist_1");
    public static final RegistryObject<SoundEvent> MEDICAL_ALCOHOL_TWIST_2 = simple("medical_alcohol_twist_2");
    public static final RegistryObject<SoundEvent> MEDICAL_ALCOHOL_THREAD_CLICK = simple("medical_alcohol_thread_click");
    public static final RegistryObject<SoundEvent> MEDICAL_ALCOHOL_CAP_OFF = simple("medical_alcohol_cap_off");
    public static final RegistryObject<SoundEvent> MEDICAL_ALCOHOL_CAP_ON = simple("medical_alcohol_cap_on");
    public static final RegistryObject<SoundEvent> MEDICAL_ALCOHOL_POUR = simple("medical_alcohol_pour");
    /** Fractures and falls V1 (docs/gameplay/fall_landing_v1_plan.md 5, tools/build-fracture-sounds-v1.cjs): a bone breaking
     *  (health/Fractures#breakLimb) and a body falling over on landing (four variants). */
    public static final RegistryObject<SoundEvent> FRACTURE_CRACK = simple("fracture_crack");
    public static final RegistryObject<SoundEvent> BODY_FALL = simple("body_fall");

    private static RegistryObject<SoundEvent> simple(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ApocalypseFirstLight.MOD_ID, name)));
    }

    private AflSounds() {
    }

    /** BR-51 V3 (M14 mechanism, tools/build-br51-01-sounds.mjs): the clips' cues (animations/br51_01_v3_native) and the
     *  magazine press (NativeMagazineLoading: br51_01_mag_load / _last, the P9's takes pitched down in sounds.json). */
    public static final java.util.List<RegistryObject<SoundEvent>> BR51_01 = java.util.stream.Stream.of(
            "fire", "draw", "put_away", "magazine_release", "magazine_out", "magazine_in", "charging_back", "charging_release",
            "mag_load", "mag_load_last")
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
    /** A round pressed into a P9-01 magazine by hand (weapon/NativeMagazineLoading; docs/native_guns/magazine_system_v1.md 4). */
    public static final RegistryObject<SoundEvent> P9_01_MAG_LOAD = pistolSound("mag_load");
    public static final RegistryObject<SoundEvent> P9_01_MAG_LOAD_LAST = pistolSound("mag_load_last");
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
