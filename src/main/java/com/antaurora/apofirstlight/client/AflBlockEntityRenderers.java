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
        event.registerBlockEntityRenderer(AflBlockEntities.ENERGY_CELL.get(),
                EnergyCellBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.LEAD_CHEST.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);   // Lead Chest V3
        event.registerBlockEntityRenderer(AflBlockEntities.INDUSTRIAL_ELECTRICAL_BOX.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);   // Industrial Electrical Box V2
        event.registerBlockEntityRenderer(AflBlockEntities.CASH_REGISTER.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);   // Cash Register V2
        event.registerBlockEntityRenderer(AflBlockEntities.COMMERCIAL_GLASS_DOUBLE_DOOR.get(), CommercialGlassDoubleDoorRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.RESTROOM_STALL_DOOR.get(), RestroomStallDoorRenderer::new);
        // Beverage Cooler V2: displayed items, then the generic AFL Animated Block Mesh renderer
        event.registerBlockEntityRenderer(AflBlockEntities.BEVERAGE_COOLER.get(), BeverageCoolerRenderer::new);
        // Charging Station V1: the generic AFL Animated Block Mesh renderer plus the tray item and the front displays
        event.registerBlockEntityRenderer(AflBlockEntities.CHARGING_STATION.get(), ChargingStationRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.CHEST_FREEZER.get(), ChestFreezerRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.VENDING_MACHINE.get(), VendingMachineRenderer::new);
        // Metal Trash Can V2 and Water Dispenser V2: generic AFL Animated Block Mesh Runtime
        event.registerBlockEntityRenderer(AflBlockEntities.METAL_TRASH_CAN.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.WATER_DISPENSER.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.THERMAL_GENERATOR.get(), ThermalGeneratorRenderer::new);
        event.registerBlockEntityRenderer(AflBlockEntities.GUN_MAINTENANCE_BENCH.get(), GunMaintenanceBenchRenderer::new);
        // Industrial Locker V2: generic AFL Animated Block Mesh Runtime, no locker-specific renderer
        event.registerBlockEntityRenderer(AflBlockEntities.INDUSTRIAL_LOCKER.get(),
                com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer::new);
    }
}
