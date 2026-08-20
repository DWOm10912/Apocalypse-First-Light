package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.config.GeigerHudConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GeigerHudRegistration {
    private GeigerHudRegistration() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> GeigerHudConfigManager.load(Minecraft.getInstance().getResourceManager()));
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "geiger_counter", GeigerHudOverlay.OVERLAY);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(GeigerHudConfigManager.reloadListener());
    }
}
