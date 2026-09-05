package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.IndustrialWasteFog;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class IndustrialWasteFogClient {
    private IndustrialWasteFogClient() {
    }

    private static boolean isSubmerged(Camera camera) {
        return camera.isInitialized() && camera.getEntity() != null
                && IndustrialWasteFog.isCameraSubmerged(camera.getEntity().level(), camera.getPosition());
    }

    // FluidType hooks run BEFORE these events. Run after the existing atmospheric fog handlers,
    // which see custom fluids as FogType.NONE, without altering any other atmosphere/fluid.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!isSubmerged(event.getCamera())) return;
        event.setRed(IndustrialWasteFog.RED);
        event.setGreen(IndustrialWasteFog.GREEN);
        event.setBlue(IndustrialWasteFog.BLUE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!isSubmerged(event.getCamera())) return;
        event.setNearPlaneDistance(IndustrialWasteFog.START);
        event.setFarPlaneDistance(IndustrialWasteFog.END);
        event.setFogShape(FogShape.SPHERE);
        // Forge applies the public RenderFog parameters only when the event is cancelled.
        event.setCanceled(true);
    }
}
