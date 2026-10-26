package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Field;

/** Binds the opted-in plain Items (ammo, Material Meshes V1) and the Pure Mesh block items (locker, electrical box, cash register) to the AFL Mesh item renderer. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AflStaticMeshItemClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            bind(AflItems.ROUND_12_GAUGE.get(), "12_gauge_round", "12_gauge_round_mesh", 0.32);
            // 12 Gauge fired shell (tools/build-12ga-ammo.mjs, hand-made 12ga_hybrid_mesh_spent): 0.5 - height 18.945 / 32,
            // shares the round's atlas; display scales keep scale x height equal to the other casings.
            bind(AflItems.CASING_12_GAUGE.get(), "12_gauge_casing", "12_gauge_round_mesh", -0.09203125);
            // ItemEntityRenderer adds 0.25 * ground scale above the entity. Keep these upright rounds
            // at the same visible base height as the 12 Gauge shell without changing other views.
            bind(AflItems.ROUND_50_AE.get(), "50_ae_round", "blackridge_50ae_ammo_v1", 0.431217, 0.257);
            bind(AflItems.CASING_50_AE.get(), "50_ae_casing", "blackridge_50ae_ammo_v1", 0.444568);
            // 9x19mm Pure Mesh set (tools/build-9x19mm-ammo.mjs): offset = 0.5 - mesh height / 32 centres it like .50 AE.
            bind(AflItems.ROUND_9MM.get(), "9x19mm_round", "9x19mm_ammo_v1", 0.451463, 0.2558333333);
            bind(AflItems.CASING_9MM.get(), "9x19mm_casing", "9x19mm_ammo_v1", 0.468168);
            // 7.62x51mm set (tools/build-762x51mm-ammo.mjs): 0.5 - height / 32 (round 3.30791, casing 2.38605); the round's
            // ground offset puts its base 0.014 above the entity like the other rounds (0.25 + 0.014 / ground scale 1.33).
            bind(AflItems.ROUND_762MM.get(), "762x51mm_round", "762x51mm_ammo_v1", 0.396628, 0.2605263158);
            bind(AflItems.CASING_762MM.get(), "762x51mm_casing", "762x51mm_ammo_v1", 0.425436);
            // 12.7x55mm set (tools/build-127x55mm-ammo.mjs): 0.5 - height / 32 (round 3.109, casing 2.34453); the round's
            // ground offset puts its base 0.014 above the entity like the other rounds (0.25 + 0.014 / ground scale 1.415).
            bind(AflItems.ROUND_127MM.get(), "12_7x55mm_round", "12_7x55mm_ammo_v1", 0.40284375, 0.2598939929);
            bind(AflItems.CASING_127MM.get(), "12_7x55mm_casing", "12_7x55mm_ammo_v1", 0.4267334375);
            // Material Meshes V1 (tools/build-material-meshes-v1.mjs): offset = 0.5 - mesh height / 32 centres each mesh, its own atlas.
            bind(AflItems.STEEL_BILLET.get(), "steel_billet", "steel_billet_mesh", 0.3875);
            bind(AflItems.LEAD_BRICK.get(), "lead_brick", "lead_brick_mesh", 0.40625);
            bind(AflItems.ELECTROLYTIC_NICKEL.get(), "electrolytic_nickel", "electrolytic_nickel_mesh", 0.44375);
            bind(AflItems.TUNGSTEN_FILAMENT.get(), "tungsten_filament", "tungsten_filament_mesh", 0.263462);
            bind(AflItems.CEMENTED_CARBIDE_BLANK.get(), "cemented_carbide_blank", "cemented_carbide_blank_mesh", 0.388885);
            bind(AflItems.STEEL_SCRAP.get(), "steel_scrap", "steel_scrap_mesh", 0.446402);
            bind(AflItems.REFRACTORY_CERAMIC.get(), "refractory_ceramic", "refractory_ceramic_mesh", 0.4);
            bind(AflItems.HIGH_PURITY_QUARTZ_SAND.get(), "high_purity_quartz_sand", "high_purity_quartz_sand_mesh", 0.40625);
            // Energy Battery V1 (tools/build-energy-battery-v1.mjs): 0.5 - height 4.66 / 32
            bind(AflItems.ENERGY_BATTERY.get(), "energy_battery", "energy_battery_mesh", 0.354375);
            // Equipment Meshes V1 (tools/build-equipment-meshes-v1.mjs): 0.5 - height / 32 (1.22 / 3.5 px)
            bind(AflItems.CLINICAL_THERMOMETER.get(), () -> new AflStaticMeshItemRenderer("clinical_thermometer", "clinical_thermometer_mesh", 0.46189),
                    "clinical_thermometer", com.antaurora.apofirstlight.temperature.ClientThermometerHands.INSTANCE);
            bind(AflItems.WRIST_THERMOMETER.get(), "wrist_thermometer", "wrist_thermometer_mesh", 0.390625);
            // Building Wiring V1: the wire hank (material meshes, 0.5 - 5.545 / 32) and the wrench (equipment meshes, 0.5 - 1.144 / 32)
            bind(AflItems.INSULATED_COPPER_WIRE.get(), "insulated_copper_wire", "insulated_copper_wire_mesh", 0.326722);
            bind(AflItems.WIRING_WRENCH.get(), "wiring_wrench", "wiring_wrench_mesh", 0.464246);
            // Crowbar mesh (2026-10-14, equipment meshes): authored in the V1 cuboid model's frame, whose origin the bake moves
            // up 5.0271 px; (-0.8 - 5.0271) / 16 keeps the bar where the V1 model held it (its display transforms are kept)
            bind(AflItems.CROWBAR.get(), "crowbar", "crowbar_mesh", -0.36419375);
            // Round point shovel (Earthwork A2, tools/build-round-point-shovel-v1.mjs, 2026-10-21): the mesh and the heap its load needs
            bind(AflItems.ROUND_POINT_SHOVEL.get(), ShovelItemRenderer::new, "round_point_shovel");
            // Pick mattock (Rock R1a, tools/build-pick-mattock-v1.mjs, 2026-10-21): the handle's foot 0.0442 above the item's floor
            bind(AflItems.PICK_MATTOCK.get(), "pick_mattock", "pick_mattock_mesh", 0.0442);
            // Field stone (Rock R1a, tools/boulders/build-field-stone.mjs, 2026-10-21): the boulders' granite; 0.5 - height / 32
            bind(AflItems.FIELD_STONE.get(), "field_stone", "field_stone_mesh", 0.443338);
            // Each rock's stone (U3a-2, the same generator, 2026-10-24): 0.5 - height / 32
            bind(AflItems.GNEISS_STONE.get(), "gneiss_stone", "gneiss_stone_mesh", 0.444075);
            bind(AflItems.SCHIST_STONE.get(), "schist_stone", "schist_stone_mesh", 0.463513);
            bind(AflItems.SANDSTONE_STONE.get(), "sandstone_stone", "sandstone_stone_mesh", 0.444491);
            bind(AflItems.LIMESTONE_STONE.get(), "limestone_stone", "limestone_stone_mesh", 0.440994);
            bind(AflItems.DOLOMITE_STONE.get(), "dolomite_stone", "dolomite_stone_mesh", 0.442645);
            bind(AflItems.SHALE_SHARD.get(), "shale_shard", "shale_shard_mesh", 0.485658);
            bind(AflItems.MARL_LUMP.get(), "marl_lump", "marl_lump_mesh", 0.447311);
            bind(AflItems.ROCK_SALT_LUMP.get(), "rock_salt_lump", "rock_salt_lump_mesh", 0.443647);
            // The raw ores (U3b batch 1, the same generator on the ore rocks' sprites, 2026-10-24): 0.5 - height / 32
            bind(AflItems.COAL_LUMP.get(), "coal_lump", "coal_lump_mesh", 0.450015);
            bind(AflItems.RAW_HEMATITE.get(), "raw_hematite", "raw_hematite_mesh", 0.471257);
            bind(AflItems.RAW_CHALCOPYRITE.get(), "raw_chalcopyrite", "raw_chalcopyrite_mesh", 0.457635);
            bind(AflItems.GOLD_QUARTZ.get(), "gold_quartz", "gold_quartz_mesh", 0.459505);
            bind(AflItems.RAW_GALENA.get(), "raw_galena", "raw_galena_mesh", 0.45841);
            bind(AflItems.RAW_SPHALERITE.get(), "raw_sphalerite", "raw_sphalerite_mesh", 0.459492);
            // Pebble and flint (Rock R1b, the same generator, 2026-10-21): 0.5 - height / 32
            bind(AflItems.PEBBLE.get(), "pebble", "pebble_mesh", 0.481284);
            bind(AflItems.ASPHALT_CHUNK.get(), "asphalt_chunk", "asphalt_chunk_mesh", 0.448939);
            bind(AflItems.FLINT.get(), "flint", "flint_mesh", 0.485968);
            // Felling axe (equipment meshes, 2026-10-20): 0.5 - 25.92 / 32
            bind(AflItems.FELLING_AXE.get(), "felling_axe", "felling_axe_mesh", -0.31);
            // Claw hammer (equipment meshes, 2026-10-21): 0.5 - 10.44 / 32
            bind(AflItems.CLAW_HAMMER.get(), "claw_hammer", "claw_hammer_mesh", 0.17375);
            // Building hammer (equipment meshes, 2026-10-21): 0.5 - 10.59 / 32
            bind(AflItems.BUILDING_HAMMER.get(), "building_hammer", "building_hammer_mesh", 0.169062);
            // Hand saw (equipment meshes, 2026-10-21): 0.5 - 24.76 / 32
            bind(AflItems.HAND_SAW.get(), "hand_saw", "hand_saw_mesh", -0.27375);
            // Field Survival Guide (equipment meshes, 2026-10-22): 0.5 - 5.669 / 32
            bind(AflItems.FIELD_SURVIVAL_GUIDE.get(), "field_survival_guide", "field_survival_guide_mesh", 0.322835);
            // Grid inventory gear (2026-10-14, equipment meshes, worn by ClientEquipmentSlots): 0.5 - height / 32
            bind(AflItems.FIELD_CHEST_RIG.get(), "field_chest_rig", "field_chest_rig_mesh", 0.157344);
            bind(AflItems.CANVAS_BACKPACK.get(), "canvas_backpack", "canvas_backpack_mesh", 0.18375);
            bind(AflItems.HIKING_BACKPACK.get(), "hiking_backpack", "hiking_backpack_mesh", 0.111875);
            bind(AflItems.GUN_SLING.get(), "gun_sling", "gun_sling_mesh", 0.385235);
            bind(AflItems.PISTOL_HOLSTER.get(), "pistol_holster", "pistol_holster_mesh", 0.26875);
            bind(AflItems.SIMPLE_HEARING_PROTECTION.get(), "simple_hearing_protection", "simple_hearing_protection_mesh", 0.280324);
            // Geiger counter V2 (equipment meshes, 2026-10-15): the mesh with a live LCD and count LED (0.5 - 3.12 / 32)
            bind(AflItems.GEIGER_COUNTER.get(), GeigerCounterItemRenderer::new, "geiger_counter");
            // Flip lighter V1 (equipment meshes, 2026-10-20): the lid, wheel and flame from the stack's tag (0.5 - 11.4 / 32)
            bind(AflItems.FLIP_LIGHTER.get(), FlipLighterRenderer::new, "flip_lighter");
            // Health V1a medical items (equipment meshes, 2026-10-19): 0.5 - height / 32 (4.149 / 9.25 px); in use, the player skeleton
            // draws them as action props (player_props/bandage, medical_alcohol: the bottle's parts, its cap a second prop)
            bind(AflItems.BANDAGE.get(), "bandage", "bandage_mesh", 0.370357);
            bind(AflItems.MEDICAL_ALCOHOL.get(), "medical_alcohol", "medical_alcohol_mesh", 0.210938);
            // Fractures and splints V1 (equipment meshes, 2026-10-20): 0.5 - 0.77 / 32
            bind(AflItems.SPLINT.get(), "splint", "splint_mesh", 0.475938);
            // Tamarack T15 key (equipment meshes, 2026-10-21): 0.5 - 3.32 / 32
            bind(AflItems.PICKUP_T15_KEY.get(), "pickup_t15_key", "pickup_t15_key_mesh", 0.39625);
            // Pocket compass (equipment meshes, 2026-10-21): the mesh with its card turned to the north (0.5 - 1.385 / 32)
            bind(AflItems.COMPASS.get(), CompassItemRenderer::new, "compass");
            // Industrial Locker V2 (tools/build-industrial-locker-v2.mjs): the block's own mesh and atlas, closed door pose.
            // Mesh origin = block bottom centre, same place as the old 2-block item model, so its display transforms are kept.
            bind(AflItems.INDUSTRIAL_LOCKER.get(), () -> new AflStaticMeshItemRenderer(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/industrial_locker.geo.json"),
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/industrial_locker.png"), 0.0, 0.0), "industrial_locker");
            // Cash Register V2 (tools/build-cash-register-v2.mjs): the block's mesh and atlas, drawer closed; GUI view fitted
            bind(AflItems.CASH_REGISTER.get(), () -> new AflStaticMeshItemRenderer(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/cash_register.geo.json"),
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/cash_register.png"), 0.0, 0.0), "cash_register");
            // Commercial Dumpster V2 (tools/build-commercial-dumpster-v2.mjs): one mesh, each colour's atlas, lids shut, no goods
            bind(AflItems.COMMERCIAL_DUMPSTER.get(), () -> new AflStaticMeshItemRenderer(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/commercial_dumpster.geo.json"),
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/commercial_dumpster_green.png"), 0.0, 0.0), "commercial_dumpster");
            bind(AflItems.COMMERCIAL_DUMPSTER_BLUE.get(), () -> new AflStaticMeshItemRenderer(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/commercial_dumpster.geo.json"),
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/commercial_dumpster_blue.png"), 0.0, 0.0), "commercial_dumpster_blue");
            bind(AflItems.COMMERCIAL_DUMPSTER_BROWN.get(), () -> new AflStaticMeshItemRenderer(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/commercial_dumpster.geo.json"),
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/commercial_dumpster_brown.png"), 0.0, 0.0), "commercial_dumpster_brown");
            bind(AflItems.COMMERCIAL_DUMPSTER_GRAY.get(), () -> new AflStaticMeshItemRenderer(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/commercial_dumpster.geo.json"),
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/commercial_dumpster_gray.png"), 0.0, 0.0), "commercial_dumpster_gray");
            // Metal Trash Can V2 (tools/build-metal-trash-can-v2.mjs): the block's mesh and atlas, lid shut, no bags; views fitted by the generator
            bind(AflItems.METAL_TRASH_CAN.get(), () -> new AflStaticMeshItemRenderer(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/metal_trash_can.geo.json"),
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/metal_trash_can.png"), 0.0, 0.0), "metal_trash_can");
            // Furniture Mesh V1 (tools/furniture/furniture-mesh.mjs, 2026-10-15): each piece's mesh and atlas, closed, the lamp unlit; views fitted by the generator
            for (String id : new String[]{"wooden_kitchen_counter", "wooden_sink_counter", "wooden_wardrobe", "wooden_nightstand", "wooden_storage_chest", "vintage_chest_freezer", "wooden_workbench"})
                bind(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, id)), () -> new AflStaticMeshItemRenderer(
                        new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + id + ".geo.json"),
                        new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/furniture/" + id + ".png"), 0.0, 0.0), id);
            // Water Dispenser V2 (tools/build-water-dispenser-v2.mjs): the block's mesh and atlas, lights off; views fitted by the generator
            bind(AflItems.WATER_DISPENSER.get(), () -> new AflStaticMeshItemRenderer(
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/water_dispenser.geo.json"),
                    new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/water_dispenser.png"), 0.0, 0.0), "water_dispenser");
        });
    }

    private static void bind(Item item, String model, String atlas, double verticalOffset) {
        bind(item, model, atlas, verticalOffset, verticalOffset);
    }

    private static void bind(Item item, String model, String atlas, double verticalOffset, double groundVerticalOffset) {
        bind(item, () -> new AflStaticMeshItemRenderer(model, atlas, verticalOffset, groundVerticalOffset), model);
    }

    private static void bind(Item item, java.util.function.Supplier<? extends net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer> factory, String model) {
        bind(item, factory, model, new IClientItemExtensions() {});
    }

    /** hands: the item's own arm pose and first-person hand transform (null / false: vanilla). */
    private static void bind(Item item, java.util.function.Supplier<? extends net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer> factory, String model, IClientItemExtensions hands) {
        try {
            // Forge 1.20.1 has no separate ordinary-Item extension registration event.
            Field field = Item.class.getDeclaredField("renderProperties");
            field.setAccessible(true);
            field.set(item, new IClientItemExtensions() {
                private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;

                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (renderer == null) renderer = factory.get();
                    return renderer;
                }

                @Override
                public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                        net.minecraft.world.InteractionHand hand, net.minecraft.world.item.ItemStack stack) {
                    return hands.getArmPose(entity, hand, stack);
                }

                @Override
                public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.player.LocalPlayer player,
                        net.minecraft.world.entity.HumanoidArm arm, net.minecraft.world.item.ItemStack stack, float partialTick,
                        float equipProcess, float swingProcess) {
                    return hands.applyForgeHandTransform(pose, player, arm, stack, partialTick, equipProcess, swingProcess);
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot bind the AFL Mesh item renderer to Forge's Item extension: " + model, e);
        }
    }

    private AflStaticMeshItemClient() {}
}
