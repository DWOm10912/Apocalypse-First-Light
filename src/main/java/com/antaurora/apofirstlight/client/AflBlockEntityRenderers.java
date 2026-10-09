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
        // Trash enclosure V1 gate: the leaf swings (generic AFL Animated Block Mesh Runtime)
        event.registerBlockEntityRenderer(AflBlockEntities.ENCLOSURE_GATE.get(),
                AflBlockEntityRendering.wrap("enclosure_gate", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
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
        // Industrial Locker V2: generic AFL Animated Block Mesh Runtime, no locker-specific renderer
        event.registerBlockEntityRenderer(AflBlockEntities.INDUSTRIAL_LOCKER.get(),
                AflBlockEntityRendering.wrap("industrial_locker", com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new));
    }
}
