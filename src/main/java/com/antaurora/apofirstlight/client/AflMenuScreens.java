package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AflMenuScreens {
    private AflMenuScreens() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(AflMenus.THERMAL_GENERATOR.get(), ThermalGeneratorScreen::new);
            MenuScreens.register(AflMenus.ENERGY_CELL.get(), EnergyCellScreen::new);
            MenuScreens.register(AflMenus.CRUSHER.get(), CrusherScreen::new);
            MenuScreens.register(AflMenus.INDUSTRIAL_FURNACE.get(), IndustrialFurnaceScreen::new);
            MenuScreens.register(AflMenus.COMPRESSOR.get(), CompressorScreen::new);
        });
    }
}
