package com.antaurora.apofirstlight.radiation.client;

import com.antaurora.apofirstlight.radiation.RadiationAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import com.antaurora.apofirstlight.ApocalypseFirstLight;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class RadiationAtmosphereClient {
    private static volatile double targetRadiation;
    private static float currentIntensity;

    private RadiationAtmosphereClient() {
    }

    public static void setTargetRadiation(double radiation) {
        targetRadiation = Math.max(0.0, radiation);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        float target = RadiationAtmosphere.getIntensity(targetRadiation);
        currentIntensity += (target - currentIntensity) * 0.10F;
        if (Math.abs(target - currentIntensity) < 0.001F) currentIntensity = target;
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float strength = currentIntensity * 0.48F;
        event.setRed(lerp(event.getRed(), 0.43F, strength));
        event.setGreen(lerp(event.getGreen(), 0.46F, strength));
        event.setBlue(lerp(event.getBlue(), 0.34F, strength));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) return;
        float multiplier = lerp(1.0F, 0.70F, currentIntensity);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), event.getFarPlaneDistance() * multiplier));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
