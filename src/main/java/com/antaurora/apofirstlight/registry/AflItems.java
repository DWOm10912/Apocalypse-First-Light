package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.item.FluidTankBlockItem;
import com.antaurora.apofirstlight.item.LeadChestBlockItem;
import com.antaurora.apofirstlight.item.CommercialDumpsterBlockItem;
import com.antaurora.apofirstlight.item.CommercialGlassDoubleDoorBlockItem;
import com.antaurora.apofirstlight.item.BeverageCoolerBlockItem;
import com.antaurora.apofirstlight.item.ChestFreezerBlockItem;
import com.antaurora.apofirstlight.item.ModernOfficeDeskBlockItem;
import com.antaurora.apofirstlight.item.SteelFrameDoorBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<Item> SIMPLE_HEARING_PROTECTION = ITEMS.register("simple_hearing_protection",
            com.antaurora.apofirstlight.item.SimpleHearingProtectionItem::new);

    public static final RegistryObject<Item> P9_01 = ITEMS.register("p9_01", () ->
            new com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "p9_01"),
                    new com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem.Profile("p9_01_v2_native", "static_idle",
                            java.util.List.of("static_idle", "empty_idle", "reload_tactical", "reload_empty",
                                    "shoot", "draw", "put_away", "inspect", "inspect_empty"),
                            java.util.Set.of("static_idle", "empty_idle"), "right_hand_anchor", "left_hand_anchor",
                            "muzzle_anchor", "ejection_anchor")));
    public static final RegistryObject<Item> BR51_01 = ITEMS.register("br51_01", () ->
            new com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight","br51_01"),
                    new com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem.Profile("br51_01", "static_idle",
                            java.util.List.of("static_idle", "reload_empty", "reload_tactical", "static_bolt_caught",
                                    "inspect", "shoot", "put_away", "draw", "reload_empty_drum"),
                            java.util.Set.of("static_idle", "static_bolt_caught"), "right_hand_anchor", "left_hand_anchor", "muzzle_pos", "ejection_anchor", 4.8125F)));
    public static final RegistryObject<Item> HR55 = ITEMS.register("hr55", () ->
            new com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "hr55"),
                    new com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem.Profile("hr55", "static_idle",
                            java.util.List.of("static_idle", "reload_empty", "reload_tactical", "static_bolt_caught",
                                    "inspect", "inspect_empty", "shoot", "put_away", "draw"),
                            java.util.Set.of("static_idle", "static_bolt_caught"), "right_hand_anchor", "left_hand_anchor",
                            "muzzle_pos", "ejection_anchor", 3.55125F)));
    public static final RegistryObject<Item> SILVERWOOD_12 = ITEMS.register("silverwood_12", () ->
            new com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "silverwood_12"),
                    new com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem.Profile("silverwood_12", "static_idle",
                            java.util.List.of("static_idle", "reload_empty", "reload_tactical", "draw", "put_away", "shoot", "inspect"),
                            java.util.Set.of("static_idle"), "right_hand_anchor", "left_hand_anchor",
                            "muzzle_right_anchor", null)));
    public static final RegistryObject<Item> BLACKRIDGE_50 = ITEMS.register("blackridge_50", () ->
            new com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "blackridge_50"),
                    new com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem.Profile("blackridge_50", "static_idle",
                            java.util.List.of("static_idle", "static_bolt_caught", "shoot", "draw", "put_away",
                                    "reload_tactical", "reload_empty", "inspect", "inspect_empty"),
                            java.util.Set.of("static_idle", "static_bolt_caught"), "right_hand_anchor", "left_hand_anchor",
                            "muzzle_anchor", "ejection_anchor")));
    public static final RegistryObject<Item> CAT = ITEMS.register("cat", () ->
            new com.antaurora.apofirstlight.weapon.CatNativeGunItem(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight", "cat"),
                    new com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem.Profile("cat", "static_idle",
                            java.util.List.of("static_idle", "reload_empty", "reload_tactical", "static_bolt_caught",
                                    "inspect", "shoot", "put_away", "draw"),
                            java.util.Set.of("static_idle", "static_bolt_caught"), "right_hand_anchor", "left_hand_anchor",
                            "muzzle_anchor", "ejection_disabled")));
    public static final RegistryObject<Item> CROWBAR = ITEMS.register("crowbar",
            com.antaurora.apofirstlight.item.CrowbarItem::new);
    public static final RegistryObject<Item> PISTOL_RED_DOT = ITEMS.register("pistol_red_dot",
            () -> new com.antaurora.apofirstlight.weapon.NativeSightItem(true));
    public static final RegistryObject<Item> RIFLE_RED_DOT_01 = ITEMS.register("rifle_red_dot_01",
            () -> new com.antaurora.apofirstlight.weapon.NativeSightItem(true));
    public static final RegistryObject<Item> P9_01_EXTENDED_MAGAZINE = ITEMS.register("p9_01_extended_magazine",
            com.antaurora.apofirstlight.weapon.NativeMagazineItem::new);
    public static final RegistryObject<Item> BR51_EXTENDED_MAGAZINE_35 = ITEMS.register("br51_extended_magazine_35",
            () -> new com.antaurora.apofirstlight.weapon.NativeMagazineItem("br51_01",35,
                    java.util.Set.of("mag_standard","reload_mag_standard","empty_old_mag_standard"),true,3.8f));
    // 50-round drum: same replaced bones; its empty reload is reload_empty_drum (native_attachments/br51_drum_magazine_50.json).
    public static final RegistryObject<Item> BR51_DRUM_MAGAZINE_50 = ITEMS.register("br51_drum_magazine_50",
            () -> new com.antaurora.apofirstlight.weapon.NativeMagazineItem("br51_01",50,
                    java.util.Set.of("mag_standard","reload_mag_standard","empty_old_mag_standard"),true,3.4f));
    public static final RegistryObject<Item> PISTOL_SUPPRESSOR_01 = ITEMS.register("pistol_suppressor_01",
            com.antaurora.apofirstlight.weapon.NativeSuppressorItem::new);
    public static final RegistryObject<Item> RIFLE_SUPPRESSOR_01 = ITEMS.register("rifle_suppressor_01",
            com.antaurora.apofirstlight.weapon.NativeSuppressorItem::new);
    // 12.7x55mm heavy suppressor (heavy_brake_qd, native_attachments/heavy_suppressor_01.json): HR55.
    public static final RegistryObject<Item> HEAVY_SUPPRESSOR_01 = ITEMS.register("heavy_suppressor_01",
            com.antaurora.apofirstlight.weapon.NativeSuppressorItem::new);
    public static final RegistryObject<Item> ROUND_9MM = ITEMS.register("9x19mm_round",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> ROUND_762MM = ITEMS.register("762x51mm_round",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> ROUND_127MM = ITEMS.register("12_7x55mm_round",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> ROUND_12_GAUGE = ITEMS.register("12_gauge_round",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> ROUND_50_AE = ITEMS.register("50_ae_round",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> CASING_9MM = ITEMS.register("9x19mm_casing",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> CASING_762MM = ITEMS.register("762x51mm_casing",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> CASING_127MM = ITEMS.register("12_7x55mm_casing",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> CASING_12_GAUGE = ITEMS.register("12_gauge_casing",
            () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> CASING_50_AE = ITEMS.register("50_ae_casing",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> INDUSTRIAL_WASTE_BUCKET = ITEMS.register("industrial_waste_bucket",
            () -> new BucketItem(AflFluids.INDUSTRIAL_WASTE,
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final RegistryObject<Item> REINFORCED_CONCRETE = ITEMS.register("reinforced_concrete",
            () -> new BlockItem(AflBlocks.REINFORCED_CONCRETE.get(), new Item.Properties()));
    public static final RegistryObject<Item> REINFORCED_CONCRETE_SLAB = ITEMS.register("reinforced_concrete_slab",
            () -> new BlockItem(AflBlocks.REINFORCED_CONCRETE_SLAB.get(), new Item.Properties()));
    public static final RegistryObject<Item> REINFORCED_CONCRETE_STAIRS = ITEMS.register("reinforced_concrete_stairs",
            () -> new BlockItem(AflBlocks.REINFORCED_CONCRETE_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> LEAD_SHIELDING_BRICKS = ITEMS.register("lead_shielding_bricks",
            () -> new BlockItem(AflBlocks.LEAD_SHIELDING_BRICKS.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_BLOCK = ITEMS.register("steel_block",
            () -> new BlockItem(AflBlocks.STEEL_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_CABLE = ITEMS.register("steel_cable",
            () -> new BlockItem(AflBlocks.STEEL_CABLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_BEAM = ITEMS.register("steel_beam",
            () -> new BlockItem(AflBlocks.STEEL_BEAM.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_BRACE = ITEMS.register("steel_brace",
            () -> new BlockItem(AflBlocks.STEEL_BRACE.get(), new Item.Properties()));
    public static final RegistryObject<Item> BAUXITE_ORE = ITEMS.register("bauxite_ore",
            () -> new BlockItem(AflBlocks.BAUXITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> GALENA_ORE = ITEMS.register("galena_ore",
            () -> new BlockItem(AflBlocks.GALENA_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LEAD_CHEST = ITEMS.register("lead_chest",
            () -> new LeadChestBlockItem(AflBlocks.LEAD_CHEST.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> GUN_MAINTENANCE_BENCH = ITEMS.register("gun_maintenance_bench",
            () -> new com.antaurora.apofirstlight.item.StaticWorkstationBlockItem(AflBlocks.GUN_MAINTENANCE_BENCH.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PRECISION_FABRICATION_STATION = ITEMS.register("precision_fabrication_station",
            () -> new com.antaurora.apofirstlight.item.StaticWorkstationBlockItem(AflBlocks.PRECISION_FABRICATION_STATION.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SPHALERITE_ORE = ITEMS.register("sphalerite_ore",
            () -> new BlockItem(AflBlocks.SPHALERITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> CASSITERITE_ORE = ITEMS.register("cassiterite_ore",
            () -> new BlockItem(AflBlocks.CASSITERITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> PENTLANDITE_ORE = ITEMS.register("pentlandite_ore",
            () -> new BlockItem(AflBlocks.PENTLANDITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> WOLFRAMITE_ORE = ITEMS.register("wolframite_ore",
            () -> new BlockItem(AflBlocks.WOLFRAMITE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> SPODUMENE_ORE = ITEMS.register("spodumene_ore",
            () -> new BlockItem(AflBlocks.SPODUMENE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_BLOCK_SLAB = ITEMS.register("steel_block_slab",
            () -> new BlockItem(AflBlocks.STEEL_BLOCK_SLAB.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_BLOCK_STAIRS = ITEMS.register("steel_block_stairs",
            () -> new BlockItem(AflBlocks.STEEL_BLOCK_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_GRATE = ITEMS.register("steel_grate",
            () -> new BlockItem(AflBlocks.STEEL_GRATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_RAILING = ITEMS.register("steel_railing",
            () -> new BlockItem(AflBlocks.STEEL_RAILING.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_PLATE = ITEMS.register("steel_plate",
            () -> new BlockItem(AflBlocks.STEEL_PLATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_PLATE_SLAB = ITEMS.register("steel_plate_slab",
            () -> new BlockItem(AflBlocks.STEEL_PLATE_SLAB.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_PLATE_STAIRS = ITEMS.register("steel_plate_stairs",
            () -> new BlockItem(AflBlocks.STEEL_PLATE_STAIRS.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_DOOR = ITEMS.register("steel_door",
            () -> new SteelFrameDoorBlockItem(AflBlocks.STEEL_DOOR.get(), new Item.Properties(), "steel_door"));
    public static final RegistryObject<Item> COMMERCIAL_WOOD_DOOR = ITEMS.register("commercial_wood_door",
            () -> new SteelFrameDoorBlockItem(AflBlocks.COMMERCIAL_WOOD_DOOR.get(), new Item.Properties(), "commercial_wood_door"));
    // AFL V1 functional materials (docs/gameplay/material_system_v1.md): no per-metal ingot / sheet / block templates
    public static final RegistryObject<Item> STEEL_BILLET = ITEMS.register("steel_billet",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BAUXITE = ITEMS.register("bauxite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ALUMINA = ITEMS.register("alumina",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GALENA = ITEMS.register("galena",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPHALERITE = ITEMS.register("sphalerite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CASSITERITE = ITEMS.register("cassiterite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LEAD_BRICK = ITEMS.register("lead_brick",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PENTLANDITE = ITEMS.register("pentlandite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ELECTROLYTIC_NICKEL = ITEMS.register("electrolytic_nickel",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SILVER_SCRAP = ITEMS.register("silver_scrap",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WOLFRAMITE = ITEMS.register("wolframite",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TUNGSTEN_OXIDE = ITEMS.register("tungsten_oxide",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TUNGSTEN_POWDER = ITEMS.register("tungsten_powder",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SPODUMENE_CONCENTRATE = ITEMS.register("spodumene_concentrate",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LITHIUM_CARBONATE = ITEMS.register("lithium_carbonate",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TUNGSTEN_FILAMENT = ITEMS.register("tungsten_filament",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TUNGSTEN_CARBIDE_POWDER = ITEMS.register("tungsten_carbide_powder",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CEMENTED_CARBIDE_BLANK = ITEMS.register("cemented_carbide_blank",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> INDUSTRIAL_UTILITY_LIGHT = ITEMS.register("industrial_utility_light",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_UTILITY_LIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> LINEAR_LIGHT = ITEMS.register("linear_light",
            () -> new BlockItem(AflBlocks.LINEAR_LIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> EMERGENCY_LIGHT = ITEMS.register("emergency_light",
            () -> new BlockItem(AflBlocks.EMERGENCY_LIGHT.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> INDUSTRIAL_LOCKER = ITEMS.register("industrial_locker",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_LOCKER.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RETAIL_SHELF_SINGLE = ITEMS.register("retail_shelf_single",
            () -> new BlockItem(AflBlocks.RETAIL_SHELF_SINGLE.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CHECKOUT_COUNTER = ITEMS.register("checkout_counter",
            () -> new BlockItem(AflBlocks.CHECKOUT_COUNTER.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CHECKOUT_COUNTER_DISPLAY = ITEMS.register("checkout_counter_display",
            () -> new BlockItem(AflBlocks.CHECKOUT_COUNTER_DISPLAY.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CHECKOUT_COUNTER_GATE = ITEMS.register("checkout_counter_gate",
            () -> new BlockItem(AflBlocks.CHECKOUT_COUNTER_GATE.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> BACK_BAR_SHELF = ITEMS.register("back_bar_shelf",
            () -> new BlockItem(AflBlocks.BACK_BAR_SHELF.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> STORAGE_RACK = ITEMS.register("storage_rack",
            () -> new BlockItem(AflBlocks.STORAGE_RACK.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CASH_REGISTER = ITEMS.register("cash_register",
            () -> new BlockItem(AflBlocks.CASH_REGISTER.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> WATER_DISPENSER = ITEMS.register("water_dispenser",
            () -> new BlockItem(AflBlocks.WATER_DISPENSER.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> METAL_TRASH_CAN = ITEMS.register("metal_trash_can",
            () -> new BlockItem(AflBlocks.METAL_TRASH_CAN.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> COMMERCIAL_DUMPSTER = ITEMS.register("commercial_dumpster",
            () -> new CommercialDumpsterBlockItem(
                    (com.antaurora.apofirstlight.block.CommercialDumpsterBlock) AflBlocks.COMMERCIAL_DUMPSTER.get(),
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COMMERCIAL_DUMPSTER_BLUE = ITEMS.register("commercial_dumpster_blue",
            () -> new CommercialDumpsterBlockItem(
                    (com.antaurora.apofirstlight.block.CommercialDumpsterBlock) AflBlocks.COMMERCIAL_DUMPSTER_BLUE.get(),
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COMMERCIAL_DUMPSTER_BROWN = ITEMS.register("commercial_dumpster_brown",
            () -> new CommercialDumpsterBlockItem(
                    (com.antaurora.apofirstlight.block.CommercialDumpsterBlock) AflBlocks.COMMERCIAL_DUMPSTER_BROWN.get(),
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COMMERCIAL_DUMPSTER_GRAY = ITEMS.register("commercial_dumpster_gray",
            () -> new CommercialDumpsterBlockItem(
                    (com.antaurora.apofirstlight.block.CommercialDumpsterBlock) AflBlocks.COMMERCIAL_DUMPSTER_GRAY.get(),
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FUEL_DISPENSER = ITEMS.register("fuel_dispenser",
            () -> new com.antaurora.apofirstlight.item.FuelDispenserBlockItem(
                    (com.antaurora.apofirstlight.block.FuelDispenserBlock) AflBlocks.FUEL_DISPENSER.get(), new Item.Properties().stacksTo(1)));
    // Fuel Dispenser V1 nozzles: only ever made by the dispenser, tethered to it (not in any creative tab)
    public static final RegistryObject<Item> FUEL_NOZZLE_GASOLINE = ITEMS.register("fuel_nozzle_gasoline",
            com.antaurora.apofirstlight.item.FuelNozzleItem::new);
    public static final RegistryObject<Item> FUEL_NOZZLE_DIESEL = ITEMS.register("fuel_nozzle_diesel",
            com.antaurora.apofirstlight.item.FuelNozzleItem::new);
    public static final RegistryObject<Item> FUEL_ISLAND_CURB = ITEMS.register("fuel_island_curb",
            () -> new BlockItem(AflBlocks.FUEL_ISLAND_CURB.get(), new Item.Properties()));
    public static final RegistryObject<Item> FUEL_ISLAND_END = ITEMS.register("fuel_island_end",
            () -> new BlockItem(AflBlocks.FUEL_ISLAND_END.get(), new Item.Properties()));
    public static final RegistryObject<Item> FUEL_ISLAND_BOLLARD = ITEMS.register("fuel_island_bollard",
            () -> new BlockItem(AflBlocks.FUEL_ISLAND_BOLLARD.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FUEL_CANOPY_COLUMN = ITEMS.register("fuel_canopy_column",
            () -> new com.antaurora.apofirstlight.item.FuelCanopyColumnItem(
                    (com.antaurora.apofirstlight.block.FuelCanopyColumnBlock) AflBlocks.FUEL_CANOPY_COLUMN.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FUEL_CANOPY_CEILING = ITEMS.register("fuel_canopy_ceiling",
            () -> new BlockItem(AflBlocks.FUEL_CANOPY_CEILING.get(), new Item.Properties()));
    public static final RegistryObject<Item> FUEL_CANOPY_LIGHT = ITEMS.register("fuel_canopy_light",
            () -> new BlockItem(AflBlocks.FUEL_CANOPY_LIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> FUEL_CANOPY_FASCIA = ITEMS.register("fuel_canopy_fascia",
            () -> new BlockItem(AflBlocks.FUEL_CANOPY_FASCIA.get(), new Item.Properties()));
    public static final RegistryObject<Item> UNDERGROUND_FUEL_TANK_GASOLINE = ITEMS.register("underground_fuel_tank_gasoline",
            () -> new com.antaurora.apofirstlight.item.UndergroundFuelTankItem(
                    (com.antaurora.apofirstlight.block.UndergroundFuelTankBlock) AflBlocks.UNDERGROUND_FUEL_TANK_GASOLINE.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> UNDERGROUND_FUEL_TANK_DIESEL = ITEMS.register("underground_fuel_tank_diesel",
            () -> new com.antaurora.apofirstlight.item.UndergroundFuelTankItem(
                    (com.antaurora.apofirstlight.block.UndergroundFuelTankBlock) AflBlocks.UNDERGROUND_FUEL_TANK_DIESEL.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SUBMERSIBLE_FUEL_PUMP = ITEMS.register("submersible_fuel_pump",
            () -> new BlockItem(AflBlocks.SUBMERSIBLE_FUEL_PUMP.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PUMP_MANHOLE_COVER = ITEMS.register("pump_manhole_cover",
            () -> new BlockItem(AflBlocks.PUMP_MANHOLE_COVER.get(), new Item.Properties()));
    public static final RegistryObject<Item> FUEL_FILL_COVER_GASOLINE = ITEMS.register("fuel_fill_cover_gasoline",
            () -> new BlockItem(AflBlocks.FUEL_FILL_COVER_GASOLINE.get(), new Item.Properties()));
    public static final RegistryObject<Item> FUEL_FILL_COVER_DIESEL = ITEMS.register("fuel_fill_cover_diesel",
            () -> new BlockItem(AflBlocks.FUEL_FILL_COVER_DIESEL.get(), new Item.Properties()));
    public static final RegistryObject<Item> JERRY_CAN = ITEMS.register("jerry_can",
            () -> new com.antaurora.apofirstlight.item.FuelCanItem((com.antaurora.apofirstlight.block.FuelCanBlock) AflBlocks.JERRY_CAN.get(), true, new Item.Properties()));
    public static final RegistryObject<Item> SMALL_FUEL_DRUM = ITEMS.register("small_fuel_drum",
            () -> new com.antaurora.apofirstlight.item.FuelCanItem((com.antaurora.apofirstlight.block.FuelCanBlock) AflBlocks.SMALL_FUEL_DRUM.get(), false, new Item.Properties()));
    public static final RegistryObject<Item> FUEL_DRUM = ITEMS.register("fuel_drum",
            () -> new com.antaurora.apofirstlight.item.FuelCanItem((com.antaurora.apofirstlight.block.FuelCanBlock) AflBlocks.FUEL_DRUM.get(), false, new Item.Properties()));
    public static final RegistryObject<Item> HAND_FUEL_PUMP = ITEMS.register("hand_fuel_pump",
            () -> new BlockItem(AflBlocks.HAND_FUEL_PUMP.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FUEL_DISPENSER_SUMP = ITEMS.register("fuel_dispenser_sump",
            () -> new com.antaurora.apofirstlight.item.FuelDispenserSumpItem(
                    (com.antaurora.apofirstlight.block.FuelDispenserSumpBlock) AflBlocks.FUEL_DISPENSER_SUMP.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> INTAKE_PUMP = ITEMS.register("intake_pump",
            () -> new com.antaurora.apofirstlight.item.IntakePumpItem(
                    (com.antaurora.apofirstlight.block.IntakePumpBlock) AflBlocks.INTAKE_PUMP.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COMMERCIAL_GLASS_DOUBLE_DOOR = ITEMS.register("commercial_glass_double_door",
            () -> new CommercialGlassDoubleDoorBlockItem(AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COMMERCIAL_GLASS_DOUBLE_DOOR_BLACK = ITEMS.register("commercial_glass_double_door_black",
            () -> new CommercialGlassDoubleDoorBlockItem(AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR_BLACK.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FACE_BRICK_WARM_GRAY = ITEMS.register("face_brick_warm_gray",
            () -> new BlockItem(AflBlocks.FACE_BRICK_WARM_GRAY.get(), new Item.Properties()));
    public static final RegistryObject<Item> FACE_BRICK_CHARCOAL = ITEMS.register("face_brick_charcoal",
            () -> new BlockItem(AflBlocks.FACE_BRICK_CHARCOAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> GROUND_FACE_BLOCK = ITEMS.register("ground_face_block",
            () -> new BlockItem(AflBlocks.GROUND_FACE_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> ALUMINUM_CORNICE = ITEMS.register("aluminum_cornice",
            () -> new BlockItem(AflBlocks.ALUMINUM_CORNICE.get(), new Item.Properties()));
    public static final RegistryObject<Item> METAL_WALL_PANEL = ITEMS.register("metal_wall_panel",
            () -> new BlockItem(AflBlocks.METAL_WALL_PANEL.get(), new Item.Properties()));
    public static final RegistryObject<Item> METAL_PANEL_JAMB = ITEMS.register("metal_panel_jamb",
            () -> new BlockItem(AflBlocks.METAL_PANEL_JAMB.get(), new Item.Properties()));
    public static final RegistryObject<Item> METAL_EYEBROW_CANOPY = ITEMS.register("metal_eyebrow_canopy",
            () -> new BlockItem(AflBlocks.METAL_EYEBROW_CANOPY.get(), new Item.Properties()));
    public static final RegistryObject<Item> STOREFRONT_GLAZING = ITEMS.register("storefront_glazing",
            () -> new BlockItem(AflBlocks.STOREFRONT_GLAZING.get(), new Item.Properties()));
    public static final RegistryObject<Item> BEVERAGE_COOLER = ITEMS.register("beverage_cooler",
            () -> new BeverageCoolerBlockItem((com.antaurora.apofirstlight.block.BeverageCoolerBlock)
                    AflBlocks.BEVERAGE_COOLER.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> VENDING_MACHINE = ITEMS.register("vending_machine",
            () -> new com.antaurora.apofirstlight.item.VendingMachineBlockItem(AflBlocks.VENDING_MACHINE.get(),new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CHEST_FREEZER = ITEMS.register("chest_freezer",
            () -> new ChestFreezerBlockItem((com.antaurora.apofirstlight.block.ChestFreezerBlock)
                    AflBlocks.CHEST_FREEZER.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MODERN_OFFICE_DESK = ITEMS.register("modern_office_desk",
            () -> new ModernOfficeDeskBlockItem(
                    (com.antaurora.apofirstlight.block.ModernOfficeDeskBlock) AflBlocks.MODERN_OFFICE_DESK.get(),
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MODERN_OFFICE_CHAIR = ITEMS.register("modern_office_chair",
            () -> new BlockItem(AflBlocks.MODERN_OFFICE_CHAIR.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> MODERN_LCD_MONITOR = ITEMS.register("modern_lcd_monitor",
            () -> new BlockItem(AflBlocks.MODERN_LCD_MONITOR.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> OFFICE_COMPUTER_STATION = ITEMS.register("office_computer_station",
            () -> new BlockItem(AflBlocks.OFFICE_COMPUTER_STATION.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> OFFICE_KEYBOARD = ITEMS.register("office_keyboard",
            () -> new BlockItem(AflBlocks.OFFICE_KEYBOARD.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> OFFICE_MOUSE = ITEMS.register("office_mouse",
            () -> new BlockItem(AflBlocks.OFFICE_MOUSE.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> OFFICE_CUBICLE_PARTITION = ITEMS.register("office_cubicle_partition",
            () -> new BlockItem(AflBlocks.OFFICE_CUBICLE_PARTITION.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> RESTROOM_PARTITION = ITEMS.register("restroom_partition",
            () -> new BlockItem(AflBlocks.RESTROOM_PARTITION.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> RESTROOM_STALL_DOOR = ITEMS.register("restroom_stall_door",
            () -> new com.antaurora.apofirstlight.item.RestroomStallDoorBlockItem(AflBlocks.RESTROOM_STALL_DOOR.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> COMMERCIAL_FLUSHOMETER_TOILET = ITEMS.register("commercial_flushometer_toilet",
            () -> new BlockItem(AflBlocks.COMMERCIAL_FLUSHOMETER_TOILET.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> COMMERCIAL_WALL_MOUNTED_SINK = ITEMS.register("commercial_wall_mounted_sink",
            () -> new BlockItem(AflBlocks.COMMERCIAL_WALL_MOUNTED_SINK.get(), new Item.Properties().stacksTo(4)));   // one cell since Restroom Fixtures V2
    public static final RegistryObject<Item> WALL_MIRROR = ITEMS.register("wall_mirror",
            () -> new BlockItem(AflBlocks.WALL_MIRROR.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> LOW_FILING_CABINET = ITEMS.register("low_filing_cabinet",
            () -> new BlockItem(AflBlocks.LOW_FILING_CABINET.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> TALL_FILING_CABINET = ITEMS.register("tall_filing_cabinet",
            () -> new BlockItem(AflBlocks.TALL_FILING_CABINET.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> OFFICE_MULTIFUNCTION_PRINTER = ITEMS.register("office_multifunction_printer",
            () -> new BlockItem(AflBlocks.OFFICE_MULTIFUNCTION_PRINTER.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FALLOUT_SOIL = ITEMS.register("fallout_soil",
            () -> new BlockItem(AflBlocks.FALLOUT_SOIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> SCORCHED_SOIL = ITEMS.register("scorched_soil",
            () -> new BlockItem(AflBlocks.SCORCHED_SOIL.get(), new Item.Properties()));
    public static final RegistryObject<Item> FUSED_GROUND = ITEMS.register("fused_ground",
            () -> new BlockItem(AflBlocks.FUSED_GROUND.get(), new Item.Properties()));
    public static final RegistryObject<Item> ASPHALT = ITEMS.register("asphalt",
            () -> new BlockItem(AflBlocks.ASPHALT.get(), new Item.Properties()));
    public static final RegistryObject<Item> THERMAL_GENERATOR = ITEMS.register("thermal_generator",
            () -> new BlockItem(AflBlocks.THERMAL_GENERATOR.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ENERGY_CELL = ITEMS.register("energy_cell",
            () -> new BlockItem(AflBlocks.ENERGY_CELL.get(), new Item.Properties().stacksTo(1)));
    // Energy Battery V1: small rechargeable cell, made empty, 50,000 FE (machine_balance/energy_battery.json)
    public static final RegistryObject<Item> ENERGY_BATTERY = ITEMS.register("energy_battery",
            () -> new com.antaurora.apofirstlight.item.EnergyBatteryItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CHARGING_STATION = ITEMS.register("charging_station",
            () -> new com.antaurora.apofirstlight.item.ChargingStationBlockItem((com.antaurora.apofirstlight.block.ChargingStationBlock)
                    AflBlocks.CHARGING_STATION.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CRUSHER = ITEMS.register("crusher",
            () -> new BlockItem(AflBlocks.CRUSHER.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> INDUSTRIAL_FURNACE = ITEMS.register("industrial_furnace",
            () -> new BlockItem(AflBlocks.INDUSTRIAL_FURNACE.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ALLOY_FURNACE = ITEMS.register("alloy_furnace",
            () -> new BlockItem(AflBlocks.ALLOY_FURNACE.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COMPRESSOR = ITEMS.register("compressor",
            () -> new BlockItem(AflBlocks.COMPRESSOR.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CHEMICAL_REACTOR = ITEMS.register("chemical_reactor",
            () -> new BlockItem(AflBlocks.CHEMICAL_REACTOR.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> DISTRIBUTION_PANEL = ITEMS.register("distribution_panel",
            () -> new com.antaurora.apofirstlight.item.AflMeshBlockItem(AflBlocks.DISTRIBUTION_PANEL.get(), new Item.Properties().stacksTo(4), "distribution_panel"));
    public static final RegistryObject<Item> SERVICE_METER_BOX = ITEMS.register("service_meter_box",
            () -> new com.antaurora.apofirstlight.item.AflMeshBlockItem(AflBlocks.SERVICE_METER_BOX.get(), new Item.Properties().stacksTo(4), "service_meter_box"));
    public static final RegistryObject<Item> WALL_OUTLET = ITEMS.register("wall_outlet",
            () -> new BlockItem(AflBlocks.WALL_OUTLET.get(), new Item.Properties()));
    public static final RegistryObject<Item> POWER_STRIP_3 = ITEMS.register("power_strip_3",
            () -> new BlockItem(AflBlocks.POWER_STRIP_3.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> POWER_STRIP_6 = ITEMS.register("power_strip_6",
            () -> new BlockItem(AflBlocks.POWER_STRIP_6.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> POWER_CABLE = ITEMS.register("power_cable",
            () -> new BlockItem(AflBlocks.POWER_CABLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> FLUID_PIPE = ITEMS.register("fluid_pipe",
            () -> new BlockItem(AflBlocks.FLUID_PIPE.get(), new Item.Properties()));
    public static final RegistryObject<Item> FLUID_TANK = ITEMS.register("fluid_tank",
            () -> new FluidTankBlockItem(AflBlocks.FLUID_TANK.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HEAT_RESISTANT_FLUID_PIPE = ITEMS.register("heat_resistant_fluid_pipe",
            () -> new BlockItem(AflBlocks.HEAT_RESISTANT_FLUID_PIPE.get(), new Item.Properties()));
    public static final RegistryObject<Item> HEAT_RESISTANT_FLUID_TANK = ITEMS.register("heat_resistant_fluid_tank",
            () -> new FluidTankBlockItem(AflBlocks.HEAT_RESISTANT_FLUID_TANK.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HEAT_RESISTANT_INTAKE_PUMP = ITEMS.register("heat_resistant_intake_pump",
            () -> new com.antaurora.apofirstlight.item.IntakePumpItem(
                    (com.antaurora.apofirstlight.block.IntakePumpBlock) AflBlocks.HEAT_RESISTANT_INTAKE_PUMP.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> EDGE_LANE_WHITE = ITEMS.register("edge_lane_white",
            () -> new BlockItem(AflBlocks.EDGE_LANE_WHITE.get(), new Item.Properties()));
    public static final RegistryObject<Item> EDGE_LANE_YELLOW = ITEMS.register("edge_lane_yellow",
            () -> new BlockItem(AflBlocks.EDGE_LANE_YELLOW.get(), new Item.Properties()));
    public static final RegistryObject<Item> WHITE_LANE_DIVIDER = ITEMS.register("white_lane_divider",
            () -> new BlockItem(AflBlocks.WHITE_LANE_DIVIDER.get(), new Item.Properties()));
    public static final RegistryObject<Item> REFRACTORY_CERAMIC = ITEMS.register("refractory_ceramic",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> HIGH_PURITY_QUARTZ_SAND = ITEMS.register("high_purity_quartz_sand",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> QUARTZ_GLASS = ITEMS.register("quartz_glass",
            () -> new BlockItem(AflBlocks.QUARTZ_GLASS.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_SCRAP = ITEMS.register("steel_scrap",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RUBBLE = ITEMS.register("concrete_rubble",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLASTIC_SCRAP = ITEMS.register("plastic_scrap",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PLASTIC_PELLETS = ITEMS.register("plastic_pellets",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> GEIGER_COUNTER = ITEMS.register("geiger_counter",
            () -> new Item(new Item.Properties().stacksTo(1)));
    // Thirst V1 (docs/gameplay/thirst_system_v1.md): filled at a water source (dirty); boiled, it becomes purified water
    // when it carries no radiation and boiled water (still radioactive) when it does
    public static final RegistryObject<Item> DIRTY_WATER_BOTTLE = ITEMS.register("dirty_water_bottle",
            () -> new com.antaurora.apofirstlight.thirst.ThirstWaterItem(true, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> BOILED_WATER_BOTTLE = ITEMS.register("boiled_water_bottle",
            () -> new com.antaurora.apofirstlight.thirst.ThirstWaterItem(false, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PURIFIED_WATER_BOTTLE = ITEMS.register("purified_water_bottle",
            () -> new com.antaurora.apofirstlight.thirst.ThirstWaterItem(false, new Item.Properties().stacksTo(16)));
    // Temperature V1 thermometers (docs/gameplay/temperature_system_v1.md; meshes: tools/build-equipment-meshes-v1.mjs):
    // held to the mouth for a body temperature reading / worn on the wrist for the air temperature
    public static final RegistryObject<Item> CLINICAL_THERMOMETER = ITEMS.register("clinical_thermometer",
            () -> new com.antaurora.apofirstlight.temperature.ClinicalThermometerItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WRIST_THERMOMETER = ITEMS.register("wrist_thermometer",
            () -> new com.antaurora.apofirstlight.temperature.WristThermometerItem(new Item.Properties().stacksTo(1)));

    private AflItems() {
    }
}
