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
        event.registerBlockEntityRenderer(AflBlockEntities.RETAIL_SHELF_SINGLE.get(),
                RetailShelfSingleBlockEntityRenderer::new);
        // Checkout Counter V1: the goods in the cubbies / on the trays and on the back bar's shelves (the furniture is baked)
        event.registerBlockEntityRenderer(AflBlockEntities.CHECKOUT_COUNTER.get(), CheckoutCounterRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.BACK_BAR_SHELF.get(), BackBarShelfRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.STORAGE_RACK.get(), StorageRackRenderer::new);
        // Fuel Dispenser V1: the live hoses of nozzles that are out (the dispenser and its holstered nozzles are baked)
        event.registerBlockEntityRenderer(AflBlockEntities.FUEL_DISPENSER.get(), FuelDispenserRenderer::new);
        // Fuel containers V1: the hand pump's crank and hose (its body is baked per mount)
        event.registerBlockEntityRenderer(AflBlockEntities.HAND_FUEL_PUMP.get(), HandFuelPumpRenderer::new);
        // Checkout Counter V1 gate: the flap and the door animate (generic AFL Animated Block Mesh Runtime)
        event.registerBlockEntityRenderer(AflBlockEntities.CHECKOUT_COUNTER_GATE.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.ENERGY_CELL.get(),
                EnergyCellBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.LEAD_CHEST.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);   // Lead Chest V3
        event.registerBlockEntityRenderer(AflBlockEntities.CASH_REGISTER.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);   // Cash Register V2
        // Commercial Glass Double Door V2 (both finishes): generic AFL Animated Block Mesh Runtime
        event.registerBlockEntityRenderer(AflBlockEntities.COMMERCIAL_GLASS_DOUBLE_DOOR.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        // Building Power V1 (distribution panel, service meter box): generic AFL Animated Block Mesh Runtime
        event.registerBlockEntityRenderer(AflBlockEntities.DISTRIBUTION_PANEL.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.SERVICE_METER_BOX.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        // Steel-frame doors V1 (steel door, commercial wood door): generic AFL Animated Block Mesh Runtime
        event.registerBlockEntityRenderer(AflBlockEntities.STEEL_DOOR.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.COMMERCIAL_WOOD_DOOR.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.RESTROOM_STALL_DOOR.get(), RestroomStallDoorRenderer::new);
        // Beverage Cooler V2: displayed items, then the generic AFL Animated Block Mesh renderer
        event.registerBlockEntityRenderer(AflBlockEntities.BEVERAGE_COOLER.get(), BeverageCoolerRenderer::new);
        // Charging Station V1: the generic AFL Animated Block Mesh renderer plus the tray item and the front displays
        event.registerBlockEntityRenderer(AflBlockEntities.CHARGING_STATION.get(), ChargingStationRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.CHEST_FREEZER.get(), ChestFreezerRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.VENDING_MACHINE.get(), VendingMachineRenderer::new);
        // Metal Trash Can V2, Water Dispenser V2, Commercial Dumpster V2 (every colour): generic AFL Animated Block Mesh Runtime
        event.registerBlockEntityRenderer(AflBlockEntities.COMMERCIAL_DUMPSTER.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.METAL_TRASH_CAN.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.WATER_DISPENSER.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.THERMAL_GENERATOR.get(), ThermalGeneratorRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.GUN_MAINTENANCE_BENCH.get(), GunMaintenanceBenchRenderer::new);
        // Office chair someone sat on (an entity from then on): its own mesh renderer (base, five casters, swivel)
        event.registerEntityRenderer(com.antaurora.apofirstlight.registry.AflEntities.MODERN_OFFICE_CHAIR.get(), OfficeChairRenderer::new);
        // Industrial Locker V2: generic AFL Animated Block Mesh Runtime, no locker-specific renderer
        event.registerBlockEntityRenderer(AflBlockEntities.INDUSTRIAL_LOCKER.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
    }
}
