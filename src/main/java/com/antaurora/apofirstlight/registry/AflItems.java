package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.item.FluidTankBlockItem;
import com.antaurora.apofirstlight.item.PavementArrowItem;
import com.antaurora.apofirstlight.item.PavementPaintBlockItem;
import com.antaurora.apofirstlight.block.PavementArrowBlock;
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
                    new com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem.Profile("p9_01_v3_native", "static_idle",
                            java.util.List.of("static_idle", "empty_idle", "reload_tactical", "reload_empty",
                                    "reload_tactical_ads", "reload_empty_ads", "reload_tactical_no_mag", "reload_empty_no_mag",
                                    "reload_tactical_no_mag_ads", "reload_empty_no_mag_ads", "shoot", "draw", "first_draw", "put_away", "inspect", "inspect_empty",
                                    "inspect_no_mag", "inspect_empty_no_mag", "unload", "unload_empty"),
                            java.util.Set.of("static_idle", "empty_idle"), "right_hand_anchor", "left_hand_anchor",
                            "muzzle_anchor", "ejection_anchor")));
    public static final RegistryObject<Item> BR51_01 = ITEMS.register("br51_01", () ->
            new com.antaurora.apofirstlight.weapon.ConfiguredNativeGunItem(new net.minecraft.resources.ResourceLocation("apocalypse_firstlight","br51_01"),
                    new com.antaurora.apofirstlight.weapon.NativeAnimatedWeaponItem.Profile("br51_01_v3_native", "static_idle",
                            java.util.List.of("static_idle", "empty_idle", "reload_tactical", "reload_empty", "reload_tactical_no_mag",
                                    "reload_empty_no_mag", "shoot", "draw", "first_draw",
                                    "put_away", "inspect", "inspect_empty", "inspect_no_mag", "inspect_empty_no_mag", "unload", "unload_empty"),
                            java.util.Set.of("static_idle", "empty_idle"), "right_hand_anchor", "left_hand_anchor",
                            "muzzle_anchor", "ejection_anchor")));
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
    // Earthwork A2 (docs/worldgen/earthwork_a2.md 4): the round point shovel, creative only
    public static final RegistryObject<Item> ROUND_POINT_SHOVEL = ITEMS.register("round_point_shovel",
            com.antaurora.apofirstlight.item.ShovelItem::new);
    // Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 4): the pick mattock, creative only
    public static final RegistryObject<Item> PICK_MATTOCK = ITEMS.register("pick_mattock",
            com.antaurora.apofirstlight.item.PickMattockItem::new);
    // Rock R1a (docs/gameplay/rock_r1_stones_boulders_design.md 2.1, 11.2): a field stone, what a broken boulder piece drops;
    // a material only (no recipe before the recipe rework), creative tab too
    public static final RegistryObject<Item> FIELD_STONE = ITEMS.register("field_stone",
            () -> new Item(new Item.Properties().stacksTo(8)));
    // Rock R1b (docs/gameplay/rock_r1_stones_boulders_design.md 2.1, 2.4, 11.3): a pebble and a flint, picked from loose
    // stones; materials only (no recipe before the recipe rework), creative tab too
    public static final RegistryObject<Item> PEBBLE = ITEMS.register("pebble",
            () -> new Item(new Item.Properties().stacksTo(32)));
    public static final RegistryObject<Item> FLINT = ITEMS.register("flint",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PISTOL_RED_DOT = ITEMS.register("pistol_red_dot",
            () -> new com.antaurora.apofirstlight.weapon.NativeSightItem(true));
    public static final RegistryObject<Item> RIFLE_RED_DOT_01 = ITEMS.register("rifle_red_dot_01",
            () -> new com.antaurora.apofirstlight.weapon.NativeSightItem(true));
    // P9-01 standard 17-round magazine (docs/native_guns/magazine_system_v1.md): the gun's own magazine bone as an item, cut
    // from the v3 mesh (tools/build-p9-01-magazine.mjs) and drawn with the gun's texture; replaces no bone (the gun's own
    // magazine is it); the mount's 22 deg tilt taken out for the item.
    public static final RegistryObject<Item> P9_01_MAGAZINE = ITEMS.register("p9_01_magazine",
            () -> new com.antaurora.apofirstlight.weapon.NativeMagazineItem("p9_01",17,java.util.Set.of(),false,2.16f,22f,"p9_01_v3_native"));
    public static final RegistryObject<Item> P9_01_EXTENDED_MAGAZINE = ITEMS.register("p9_01_extended_magazine",
            com.antaurora.apofirstlight.weapon.NativeMagazineItem::new);
    // BR-51 V3 magazines (tools/build-br51-01-magazines.mjs, docs/native_guns/br51_01_v3_game_model_v1.md): all three stand
    // where the 20-round one does, feed lips at the same place, so the gun's own top rounds show on any of them. The 20-round
    // one is the gun's own magazine bone (the gun's texture, replaces nothing); the 30-round curved and the 50-round drum
    // replace the loaded, reload (mag_out) and dropped magazine bones, drawn with the mags texture.
    public static final RegistryObject<Item> BR51_01_MAGAZINE = ITEMS.register("br51_01_magazine",
            () -> new com.antaurora.apofirstlight.weapon.NativeMagazineItem("br51_01",20,java.util.Set.of(),false,3.11883f,0f,"br51_01_v3_native"));
    public static final RegistryObject<Item> BR51_01_EXTENDED_MAGAZINE = ITEMS.register("br51_01_extended_magazine",
            () -> new com.antaurora.apofirstlight.weapon.NativeMagazineItem("br51_01",30,
                    java.util.Set.of("magazine","reload_magazine","empty_old_mag"),false,4.82568f,0f,"br51_01_v3_mags"));
    public static final RegistryObject<Item> BR51_DRUM_MAGAZINE_50 = ITEMS.register("br51_drum_magazine_50",
            () -> new com.antaurora.apofirstlight.weapon.NativeMagazineItem("br51_01",50,
                    java.util.Set.of("magazine","reload_magazine","empty_old_mag"),false,4.14022f,0f,"br51_01_v3_mags"));
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
    public static final RegistryObject<Item> PORCELAIN_LAMPHOLDER = ITEMS.register("porcelain_lampholder",
            () -> new BlockItem(AflBlocks.PORCELAIN_LAMPHOLDER.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KEYLESS_LAMPHOLDER = ITEMS.register("keyless_lampholder",
            () -> new BlockItem(AflBlocks.KEYLESS_LAMPHOLDER.get(), new Item.Properties().stacksTo(16)));
    /** Fuel Stop A1 details V1 (docs/models/fuel_stop_a1_details_v1.md): one channel-letter item, the letter in its BlockStateTag. */
    public static final RegistryObject<Item> CHANNEL_LETTER = ITEMS.register("channel_letter",
            () -> new com.antaurora.apofirstlight.item.ChannelLetterBlockItem(AflBlocks.CHANNEL_LETTER.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PRICE_SIGN = ITEMS.register("price_sign",
            () -> new com.antaurora.apofirstlight.item.RectMultiblockBlockItem(
                    (com.antaurora.apofirstlight.block.PriceSignBlock) AflBlocks.PRICE_SIGN.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ROOF_TPO = ITEMS.register("roof_tpo",
            () -> new BlockItem(AflBlocks.ROOF_TPO.get(), new Item.Properties()));
    public static final RegistryObject<Item> DIESEL_GENERATOR = ITEMS.register("diesel_generator",
            () -> new com.antaurora.apofirstlight.item.RectMultiblockBlockItem(
                    (com.antaurora.apofirstlight.block.DieselGeneratorBlock) AflBlocks.DIESEL_GENERATOR.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PORTABLE_DIESEL_GENERATOR = ITEMS.register("portable_diesel_generator",
            () -> new net.minecraft.world.item.BlockItem(AflBlocks.PORTABLE_DIESEL_GENERATOR.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ENCLOSURE_GATE = ITEMS.register("enclosure_gate",
            () -> new com.antaurora.apofirstlight.item.RectMultiblockBlockItem(
                    (com.antaurora.apofirstlight.block.EnclosureGateBlock) AflBlocks.ENCLOSURE_GATE.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> TUBE_FARM_GATE = ITEMS.register("tube_farm_gate",
            () -> new com.antaurora.apofirstlight.item.RectMultiblockBlockItem(
                    (com.antaurora.apofirstlight.block.TubeFarmGateBlock) AflBlocks.TUBE_FARM_GATE.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> RURAL_MAILBOX = ITEMS.register("rural_mailbox",
            () -> new com.antaurora.apofirstlight.item.RectMultiblockBlockItem(
                    (com.antaurora.apofirstlight.block.RuralMailboxBlock) AflBlocks.RURAL_MAILBOX.get(), new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> ROOFTOP_UNIT = ITEMS.register("rooftop_unit",
            () -> new com.antaurora.apofirstlight.item.RectMultiblockBlockItem(
                    (com.antaurora.apofirstlight.block.RooftopUnitBlock) AflBlocks.ROOFTOP_UNIT.get(), new Item.Properties().stacksTo(1)));
    /** Site Lighting V1 (docs/models/site_lighting_v1.md); the hidden light point has no item. */
    public static final RegistryObject<Item> LIGHT_POLE_BASE = ITEMS.register("light_pole_base",
            () -> new BlockItem(AflBlocks.LIGHT_POLE_BASE.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> LIGHT_POLE = ITEMS.register("light_pole",
            () -> new BlockItem(AflBlocks.LIGHT_POLE.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> AREA_LIGHT = ITEMS.register("area_light",
            () -> new BlockItem(AflBlocks.AREA_LIGHT.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> WALL_PACK = ITEMS.register("wall_pack",
            () -> new BlockItem(AflBlocks.WALL_PACK.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> CANOPY_DOWNLIGHT = ITEMS.register("canopy_downlight",
            () -> new BlockItem(AflBlocks.CANOPY_DOWNLIGHT.get(), new Item.Properties().stacksTo(16)));
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
    /** Creative-only test sources (no recipe, no loot): 5000 L of gasoline / diesel, poured at once, never filled (2026-10-09). */
    public static final RegistryObject<Item> CREATIVE_GASOLINE_BARREL = ITEMS.register("creative_gasoline_barrel",
            () -> new com.antaurora.apofirstlight.item.CreativeFuelBarrelItem(AflFluids.GASOLINE, new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final RegistryObject<Item> CREATIVE_DIESEL_BARREL = ITEMS.register("creative_diesel_barrel",
            () -> new com.antaurora.apofirstlight.item.CreativeFuelBarrelItem(AflFluids.DIESEL, new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)));
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
    public static final RegistryObject<Item> CMU_SCREEN_WALL = ITEMS.register("cmu_screen_wall",
            () -> new BlockItem(AflBlocks.CMU_SCREEN_WALL.get(), new Item.Properties()));
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
    public static final RegistryObject<Item> CONCRETE_SIDEWALK = ITEMS.register("concrete_sidewalk",
            () -> new BlockItem(AflBlocks.CONCRETE_SIDEWALK.get(), new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_PAVEMENT = ITEMS.register("concrete_pavement",
            () -> new BlockItem(AflBlocks.CONCRETE_PAVEMENT.get(), new Item.Properties()));
    public static final RegistryObject<Item> CURB_SIDEWALK = ITEMS.register("curb_sidewalk",
            () -> new com.antaurora.apofirstlight.item.CurbBlockItem(AflBlocks.CURB_SIDEWALK.get(), new Item.Properties()));
    public static final RegistryObject<Item> CURB_GRASS = ITEMS.register("curb_grass",
            () -> new com.antaurora.apofirstlight.item.CurbBlockItem(AflBlocks.CURB_GRASS.get(), new Item.Properties()));
    public static final RegistryObject<Item> SEALED_CONCRETE_FLOOR = ITEMS.register("sealed_concrete_floor",
            () -> new BlockItem(AflBlocks.SEALED_CONCRETE_FLOOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> PORCELAIN_FLOOR_TILE = ITEMS.register("porcelain_floor_tile",
            () -> new BlockItem(AflBlocks.PORCELAIN_FLOOR_TILE.get(), new Item.Properties()));
    public static final RegistryObject<Item> RESTROOM_FLOOR_TILE = ITEMS.register("restroom_floor_tile",
            () -> new BlockItem(AflBlocks.RESTROOM_FLOOR_TILE.get(), new Item.Properties()));
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
    public static final RegistryObject<Item> LOAD_CENTER = ITEMS.register("load_center",
            () -> new com.antaurora.apofirstlight.item.AflMeshBlockItem(AflBlocks.LOAD_CENTER.get(), new Item.Properties().stacksTo(4), "load_center"));
    public static final RegistryObject<Item> SERVICE_METER_BOX = ITEMS.register("service_meter_box",
            () -> new com.antaurora.apofirstlight.item.AflMeshBlockItem(AflBlocks.SERVICE_METER_BOX.get(), new Item.Properties().stacksTo(4), "service_meter_box"));
    public static final RegistryObject<Item> GENERATOR_INLET_BOX = ITEMS.register("generator_inlet_box",
            () -> new com.antaurora.apofirstlight.item.AflMeshBlockItem(AflBlocks.GENERATOR_INLET_BOX.get(), new Item.Properties().stacksTo(4), "generator_inlet_box"));
    public static final RegistryObject<Item> WALL_OUTLET = ITEMS.register("wall_outlet",
            () -> new BlockItem(AflBlocks.WALL_OUTLET.get(), new Item.Properties()));
    public static final RegistryObject<Item> WALL_SWITCH = ITEMS.register("wall_switch",
            () -> new BlockItem(AflBlocks.WALL_SWITCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> POWER_STRIP_3 = ITEMS.register("power_strip_3",
            () -> new BlockItem(AflBlocks.POWER_STRIP_3.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> POWER_STRIP_6 = ITEMS.register("power_strip_6",
            () -> new BlockItem(AflBlocks.POWER_STRIP_6.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> POWER_CABLE = ITEMS.register("power_cable",
            () -> new BlockItem(AflBlocks.POWER_CABLE.get(), new Item.Properties()));
    // Building Wiring V1 (docs/gameplay/building_wiring_v1.md): hidden building wire, one item = one metre = one cell, and the
    // wrench that lays and cuts it (Terraria style)
    public static final RegistryObject<Item> INSULATED_COPPER_WIRE = ITEMS.register("insulated_copper_wire",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> WIRING_WRENCH = ITEMS.register("wiring_wrench",
            () -> new com.antaurora.apofirstlight.item.WiringWrenchItem(new Item.Properties().stacksTo(1)));
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
    public static final RegistryObject<Item> EDGE_LANE_BLUE = ITEMS.register("edge_lane_blue",
            () -> new BlockItem(AflBlocks.EDGE_LANE_BLUE.get(), new Item.Properties()));
    public static final RegistryObject<Item> PAVEMENT_HATCH = ITEMS.register("pavement_hatch",
            () -> new PavementPaintBlockItem(AflBlocks.PAVEMENT_HATCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> PAVEMENT_CROSSHATCH = ITEMS.register("pavement_crosshatch",
            () -> new PavementPaintBlockItem(AflBlocks.PAVEMENT_CROSSHATCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> PAVEMENT_BAR = ITEMS.register("pavement_bar",
            () -> new PavementPaintBlockItem(AflBlocks.PAVEMENT_BAR.get(), new Item.Properties()));
    // the arrow block has no item of its own: one item per arrow places all its cells
    public static final RegistryObject<Item> PAVEMENT_ARROW_STRAIGHT = ITEMS.register("pavement_arrow_straight",
            () -> new PavementArrowItem(PavementArrowBlock.Kind.STRAIGHT, new Item.Properties()));
    public static final RegistryObject<Item> PAVEMENT_ARROW_LEFT = ITEMS.register("pavement_arrow_left",
            () -> new PavementArrowItem(PavementArrowBlock.Kind.LEFT, new Item.Properties()));
    public static final RegistryObject<Item> PAVEMENT_ARROW_RIGHT = ITEMS.register("pavement_arrow_right",
            () -> new PavementArrowItem(PavementArrowBlock.Kind.RIGHT, new Item.Properties()));
    public static final RegistryObject<Item> PAVEMENT_ACCESSIBLE_SYMBOL = ITEMS.register("pavement_accessible_symbol",
            () -> new BlockItem(AflBlocks.PAVEMENT_ACCESSIBLE_SYMBOL.get(), new Item.Properties()));
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
    // Flip lighter V1 (docs/gameplay/flip_lighter_v1.md, 2026-10-20): flick open, strike, light; the fuel rides in its tag
    public static final RegistryObject<Item> FLIP_LIGHTER = ITEMS.register("flip_lighter",
            () -> new com.antaurora.apofirstlight.item.FlipLighterItem(new Item.Properties()));
    // AFL Map V1 (docs/ui/afl_map_v1_plan.md 5): carried anywhere, it records the ground the player walks past; used in
    // hand it unfolds into the world map
    public static final RegistryObject<Item> MAP = ITEMS.register("map",
            () -> new com.antaurora.apofirstlight.item.AflMapItem(new Item.Properties().stacksTo(1)));
    // Start map (docs/ui/afl_map_v1_plan.md 5.4): the spread sheet on a table; taking it off the table gives the map
    public static final RegistryObject<Item> PRAIRIE_ROAD_MAP = ITEMS.register("prairie_road_map",
            () -> new BlockItem(AflBlocks.PRAIRIE_ROAD_MAP.get(), new Item.Properties().stacksTo(1)));
    // Clips (docs/gameplay/equipment_slots_v1.md, user 2026-10-21): in the hand or clipped on, it shows the compass bar
    public static final RegistryObject<Item> COMPASS = ITEMS.register("compass",
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
    // Health V1a (docs/gameplay/survival_health_v1a.md 7): a bandage covers a wound, medical alcohol cleans one; one used per
    // wound treated. Placeholder cuboid models (tools/author-medical-items-v1a.mjs); Creative tab only, no recipe or loot yet.
    public static final RegistryObject<Item> BANDAGE = ITEMS.register("bandage",
            () -> new com.antaurora.apofirstlight.health.MedicalItem(com.antaurora.apofirstlight.health.Wounds.Treatment.BANDAGE,
                    new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MEDICAL_ALCOHOL = ITEMS.register("medical_alcohol",
            () -> new com.antaurora.apofirstlight.health.MedicalItem(com.antaurora.apofirstlight.health.Wounds.Treatment.ALCOHOL,
                    new Item.Properties().stacksTo(8)));
    // Fractures and splints V1 (docs/gameplay/fracture_splint_v1_plan.md 5): sets one broken limb; 2 sticks + 1 bandage
    public static final RegistryObject<Item> SPLINT = ITEMS.register("splint",
            () -> new com.antaurora.apofirstlight.health.SplintItem(new Item.Properties().stacksTo(4)));
    // Grid Inventory V0 test gear (docs/ui/afl_inventory_grid_v0_plan.md 7): a rig with four separate pouches, a 5x6
    // backpack, and a 6x10 one that makes the right column scroll; the rig has two clips on its straps (RigClips)
    public static final RegistryObject<Item> FIELD_CHEST_RIG = ITEMS.register("field_chest_rig",
            () -> new com.antaurora.apofirstlight.inventory.grid.GridGearItem(com.antaurora.apofirstlight.equipment.AflEquipmentSlots.RIG,
                    com.antaurora.apofirstlight.inventory.grid.GridLayout.of(1, 2, 1, 2, 1, 2, 2, 2), 2, new Item.Properties()));
    public static final RegistryObject<Item> CANVAS_BACKPACK = ITEMS.register("canvas_backpack",
            () -> new com.antaurora.apofirstlight.inventory.grid.GridGearItem(com.antaurora.apofirstlight.equipment.AflEquipmentSlots.BACK,
                    com.antaurora.apofirstlight.inventory.grid.GridLayout.of(5, 6), new Item.Properties()));
    public static final RegistryObject<Item> HIKING_BACKPACK = ITEMS.register("hiking_backpack",
            () -> new com.antaurora.apofirstlight.inventory.grid.GridGearItem(com.antaurora.apofirstlight.equipment.AflEquipmentSlots.BACK,
                    com.antaurora.apofirstlight.inventory.grid.GridLayout.of(6, 10), new Item.Properties()));
    // Weapon positions (docs/ui/afl_inventory_grid_v0_plan.md 10): a sling fitted to a long gun carries it on the back
    // (key 1), a worn holster carries the pistol (key 2); placeholder icons
    public static final RegistryObject<Item> GUN_SLING = ITEMS.register("gun_sling",
            () -> new com.antaurora.apofirstlight.inventory.grid.GunSlingItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> PISTOL_HOLSTER = ITEMS.register("pistol_holster",
            () -> new com.antaurora.apofirstlight.inventory.grid.PistolHolsterItem(new Item.Properties()));
    // Vehicle keys (docs/vehicles/t15_hotwire_v1_design.md 9, 2026-10-21): one per vehicle, bound by its UUID; one key item
    // per vehicle model, its maker's badge on the head (user 2026-10-21); Creative tab only (an unbound key), no recipe.
    // Saves from before the split hold vehicle_key: VehicleKeyLegacyMappings
    public static final RegistryObject<Item> PICKUP_T15_KEY = ITEMS.register("pickup_t15_key",
            () -> new com.antaurora.apofirstlight.vehicle.VehicleKeyItem(new Item.Properties().stacksTo(1), "pickup_t15"));
    // Vehicles tab (2026-10-21): a use on the ground opens the spawner (colour, wear, fuel, key spot); Creative only, no recipe
    public static final RegistryObject<Item> PICKUP_T15 = ITEMS.register("pickup_t15",
            () -> new com.antaurora.apofirstlight.vehicle.VehicleSpawnerItem(new Item.Properties().stacksTo(1)));

    private AflItems() {
    }

    /**
     * Tunnel parts removable V1.1 (2026-10-16): the small tunnel parts a crowbar takes down, placeable again
     * (TunnelFixtureItem); the large ones (jet fan, gantry, portal signal, closure gate, fire door) have none.
     */
    public static final java.util.Map<com.antaurora.apofirstlight.block.TunnelFixtureBlock.Kind, RegistryObject<Item>> TUNNEL_FIXTURE_ITEMS = tunnelFixtureItems();

    private static java.util.Map<com.antaurora.apofirstlight.block.TunnelFixtureBlock.Kind, RegistryObject<Item>> tunnelFixtureItems() {
        var out = new java.util.EnumMap<com.antaurora.apofirstlight.block.TunnelFixtureBlock.Kind, RegistryObject<Item>>(com.antaurora.apofirstlight.block.TunnelFixtureBlock.Kind.class);
        for (var kind : com.antaurora.apofirstlight.block.TunnelFixtureBlock.Kind.values()) {
            if (!kind.item()) continue;
            int stack = switch (kind) {
                case HEADLIGHT_SIGN, NAME_PLATE, EMERGENCY_CABINET, STOP_SIGN, SIDE_ROAD_SIGN, CURVE_SIGN, DO_NOT_ENTER_SIGN, WRONG_WAY_SIGN,
                     GUIDE_SIGN -> 1;
                case LANE_SIGNAL -> 2;
                case EGRESS_SIGN -> 8;
                default -> 4;
            };
            out.put(kind, ITEMS.register(kind.id(), () -> new com.antaurora.apofirstlight.item.TunnelFixtureItem(
                    (com.antaurora.apofirstlight.block.TunnelFixtureBlock) AflBlocks.TUNNEL_FIXTURES.get(kind).get(), new Item.Properties().stacksTo(stack))));
        }
        return java.util.Collections.unmodifiableMap(out);
    }
}
