package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.EntityRenderersEvent;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AflBlockEntityRenderers {
    private AflBlockEntityRenderers() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // every block entity renderer goes through AflBlockEntityRendering.wrap: lossless shadow-pass culling, and in development builds the per-renderer timing
        event.registerBlockEntityRenderer(AflBlockEntities.RETAIL_SHELF_SINGLE.get(),
                AflBlockEntityRendering.wrap("retail_shelf_single", RetailShelfSingleBlockEntityRenderer::new));
        // Checkout Counter V1: the goods in the cubbies / on the trays and on the back bar's shelves (the furniture is baked)
        event.registerBlockEntityRenderer(AflBlockEntities.CHECKOUT_COUNTER.get(), AflBlockEntityRendering.wrap("checkout_counter", CheckoutCounterRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.BACK_BAR_SHELF.get(), AflBlockEntityRendering.wrap("back_bar_shelf", BackBarShelfRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.STORAGE_RACK.get(), AflBlockEntityRendering.wrap("storage_rack", StorageRackRenderer::new));
        // Fuel Dispenser V1: the live hoses of nozzles that are out (the dispenser and its holstered nozzles are baked)
        event.registerBlockEntityRenderer(AflBlockEntities.FUEL_DISPENSER.get(), AflBlockEntityRendering.wrap("fuel_dispenser", FuelDispenserRenderer::new));
        // Fuel containers V1: the hand pump's crank and hose (its body is baked per mount)
        event.registerBlockEntityRenderer(AflBlockEntities.HAND_FUEL_PUMP.get(), AflBlockEntityRendering.wrap("hand_fuel_pump", HandFuelPumpRenderer::new));
        // Checkout Counter V1 gate: the flap and the door animate (generic AFL Animated Block Mesh Runtime)
        event.registerBlockEntityRenderer(AflBlockEntities.CHECKOUT_COUNTER_GATE.get(),
                AflBlockEntityRendering.wrap("checkout_counter_gate", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        // Diesel standby generator V1: doors, fill lid, rain flap, gauge needles, key, hour meter drums, lamps (generic runtime)
        event.registerBlockEntityRenderer(AflBlockEntities.DIESEL_GENERATOR.get(),
                AflBlockEntityRendering.wrap("diesel_generator", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        // Portable diesel generator V1: the generic runtime for the set, plus the starter rope and the first-person pulling arm
        event.registerBlockEntityRenderer(AflBlockEntities.PORTABLE_DIESEL_GENERATOR.get(),
                AflBlockEntityRendering.wrap("portable_diesel_generator", PortableGeneratorRenderer::new));
        // Trash enclosure V1 gate: the leaf swings (generic AFL Animated Block Mesh Runtime)
        event.registerBlockEntityRenderer(AflBlockEntities.ENCLOSURE_GATE.get(),
                AflBlockEntityRendering.wrap("enclosure_gate", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        // Rural Driveway V1: the tube farm gate's leaf (either way) and the mailbox's door and flag (generic runtime)
        event.registerBlockEntityRenderer(AflBlockEntities.TUBE_FARM_GATE.get(),
                AflBlockEntityRendering.wrap("tube_farm_gate", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.RURAL_MAILBOX.get(),
                AflBlockEntityRendering.wrap("rural_mailbox", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.ENERGY_CELL.get(),
                AflBlockEntityRendering.wrap("energy_cell", EnergyCellBlockEntityRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.LEAD_CHEST.get(),
                AflBlockEntityRendering.wrap("lead_chest", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));   // Lead Chest V3
        event.registerBlockEntityRenderer(AflBlockEntities.CASH_REGISTER.get(),
                AflBlockEntityRendering.wrap("cash_register", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));   // Cash Register V2
        // Commercial Glass Double Door V2 (both finishes): generic AFL Animated Block Mesh Runtime
        event.registerBlockEntityRenderer(AflBlockEntities.COMMERCIAL_GLASS_DOUBLE_DOOR.get(),
                AflBlockEntityRendering.wrap("commercial_glass_double_door", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        // Building Power V1 (distribution panel, service meter box): generic AFL Animated Block Mesh Runtime
        event.registerBlockEntityRenderer(AflBlockEntities.DISTRIBUTION_PANEL.get(),
                AflBlockEntityRendering.wrap("distribution_panel", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.SERVICE_METER_BOX.get(),
                AflBlockEntityRendering.wrap("service_meter_box", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.GENERATOR_INLET_BOX.get(), AflBlockEntityRendering.wrap("generator_inlet_box", GeneratorInletBoxRenderer::new));   // + its 15 m cord
        event.registerBlockEntityRenderer(AflBlockEntities.POWER_STRIP.get(), AflBlockEntityRendering.wrap("power_strip", PowerStripRenderer::new));   // Power Outlets V1: cord + plug
        // Steel-frame doors V1 (steel door, commercial wood door): generic AFL Animated Block Mesh Runtime
        event.registerBlockEntityRenderer(AflBlockEntities.STEEL_DOOR.get(),
                AflBlockEntityRendering.wrap("steel_door", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.COMMERCIAL_WOOD_DOOR.get(),
                AflBlockEntityRendering.wrap("commercial_wood_door", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.RESTROOM_STALL_DOOR.get(), AflBlockEntityRendering.wrap("restroom_stall_door", RestroomStallDoorRenderer::new));
        // Beverage Cooler V2: displayed items, then the generic AFL Animated Block Mesh renderer
        event.registerBlockEntityRenderer(AflBlockEntities.BEVERAGE_COOLER.get(), AflBlockEntityRendering.wrap("beverage_cooler", BeverageCoolerRenderer::new));
        // Charging Station V1: the generic AFL Animated Block Mesh renderer plus the tray item and the front displays
        event.registerBlockEntityRenderer(AflBlockEntities.CHARGING_STATION.get(), AflBlockEntityRendering.wrap("charging_station", ChargingStationRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.CHEST_FREEZER.get(), AflBlockEntityRendering.wrap("chest_freezer", ChestFreezerRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.VENDING_MACHINE.get(), AflBlockEntityRendering.wrap("vending_machine", VendingMachineRenderer::new));
        // Metal Trash Can V2, Water Dispenser V2, Commercial Dumpster V2 (every colour): generic AFL Animated Block Mesh Runtime
        event.registerBlockEntityRenderer(AflBlockEntities.COMMERCIAL_DUMPSTER.get(),
                AflBlockEntityRendering.wrap("commercial_dumpster", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.METAL_TRASH_CAN.get(),
                AflBlockEntityRendering.wrap("metal_trash_can", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.WATER_DISPENSER.get(), AflBlockEntityRendering.wrap("water_dispenser", WaterDispenserRenderer::new));   // + power cord (Power Outlets V1)
        event.registerBlockEntityRenderer(AflBlockEntities.FLUID_TANK.get(), AflBlockEntityRendering.wrap("fluid_tank", FluidTankRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.THERMAL_GENERATOR.get(), AflBlockEntityRendering.wrap("thermal_generator", ThermalGeneratorRenderer::new));
        event.registerBlockEntityRenderer(AflBlockEntities.GUN_MAINTENANCE_BENCH.get(), AflBlockEntityRendering.wrap("gun_maintenance_bench", GunMaintenanceBenchRenderer::new));
        // Office chair someone sat on (an entity from then on): its own mesh renderer (base, five casters, swivel)
        event.registerEntityRenderer(com.antaurora.apofirstlight.registry.AflEntities.MODERN_OFFICE_CHAIR.get(), OfficeChairRenderer::new);
        // T15 pickup body V0: paint tint, wear overlay, hood and tailgate
        event.registerEntityRenderer(com.antaurora.apofirstlight.registry.AflEntities.PICKUP_T15.get(),
                com.antaurora.apofirstlight.client.vehicle.PickupTruckRenderer::new);
        // Industrial Locker V2: generic AFL Animated Block Mesh Runtime, no locker-specific renderer
        event.registerBlockEntityRenderer(AflBlockEntities.INDUSTRIAL_LOCKER.get(),
                AflBlockEntityRendering.wrap("industrial_locker", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        // Wood Furniture V1: what hangs on the gun rack until the chunk shows it (the rack itself and the counters are chunk-drawn cabin meshes);
        // the chair's seat is an invisible rider anchor
        event.registerBlockEntityRenderer(com.antaurora.apofirstlight.cabin.WoodFurniture.GUN_RACK, AflBlockEntityRendering.wrap("wooden_gun_rack", WoodGunRackRenderer::new));
        event.registerBlockEntityRenderer(com.antaurora.apofirstlight.cabin.BoxStove.STOVE, AflBlockEntityRendering.wrap("box_stove", BoxStoveRenderer::new));   // Box Stove V1: the cook top, the door
        // Emergency Radio V1: the crank's arm, an animated mesh (2026-10-15)
        event.registerBlockEntityRenderer(com.antaurora.apofirstlight.cabin.EmergencyRadio.RADIO,
                AflBlockEntityRendering.wrap("emergency_radio", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        // Wood Furniture V2: the tool rack's tools (the gun rack's renderer and chunk drawing); the vintage freezer's cord
        event.registerBlockEntityRenderer(com.antaurora.apofirstlight.cabin.WoodFurnitureV2.TOOL_RACK, AflBlockEntityRendering.wrap("wooden_tool_rack", WoodGunRackRenderer::new));
        event.registerBlockEntityRenderer(com.antaurora.apofirstlight.cabin.WoodFurnitureV2.FREEZER, AflBlockEntityRendering.wrap("vintage_chest_freezer", VintageFreezerRenderer::new));
        // Furniture Mesh V1 (2026-10-15): the counters, wardrobe, nightstand and storage chest as animated meshes
        event.registerBlockEntityRenderer(com.antaurora.apofirstlight.cabin.WoodFurniture.COUNTER,
                AflBlockEntityRendering.wrap("wood_furniture", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        event.registerBlockEntityRenderer(com.antaurora.apofirstlight.cabin.WoodFurnitureV2.CHEST,
                AflBlockEntityRendering.wrap("wooden_storage_chest", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
        com.antaurora.apofirstlight.cabin.WoodGunRackBlockEntity.chunkData = WoodGunRackChunking::modelData;   // resting guns: drawn by the chunk
        event.registerEntityRenderer(com.antaurora.apofirstlight.cabin.WoodFurniture.SEAT, net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(com.antaurora.apofirstlight.registry.AflEntities.GROUND_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        // Survivor Death and Succession V0: the placeholder body (owner's skin lying on its back)
        event.registerEntityRenderer(com.antaurora.apofirstlight.registry.AflEntities.SURVIVOR_CORPSE.get(),
                com.antaurora.apofirstlight.client.survivor.SurvivorCorpseRenderer::new);
    }
}
