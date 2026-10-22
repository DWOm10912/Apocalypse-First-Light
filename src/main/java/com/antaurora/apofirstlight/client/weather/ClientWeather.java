package com.antaurora.apofirstlight.client.weather;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weather.AflWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Regional weather V1 on the client (docs/gameplay/regional_weather_v1_plan.md 10): each tick, before anything draws or
 * plays, the level's rain and thunder level are set to the weather where the camera is (weather/AflWeather), eased in
 * and out (about 1 s), so everything that reads vanilla's levels (the sky, rain V1, a shader pack's rain, the T15's glass,
 * rain sounds) follows the place. The wind at the camera, eased more slowly, is the rain's slant.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientWeather {
    private static final float LEVEL_EASE = 0.05F;
    private static final double WIND_EASE = 0.02;

    private static ClientLevel tracked;
    private static float rain, thunder;
    private static double windX, windZ, gust;

    private ClientWeather() {
    }

    /** The level has AFL's weather (the overworld, the server's weather received). */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && AflWeather.active(mc.level);
    }

    /** The wind at the camera (m/s, the way it blows). */
    public static double windX() {
        return windX;
    }

    public static double windZ() {
        return windZ;
    }

    /** Gusts at the camera (times the mean wind; 0 calm). */
    public static double gust() {
        return gust;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != tracked) {
            tracked = level;
            rain = thunder = 0;
            windX = windZ = gust = 0;
            if (level == null) return;
            float[] v = at(mc, level);
            if (v != null) {
                // a new level starts where the weather is, no fade in from dry
                rain = AflWeather.level(v[AflWeather.RAIN]);
                thunder = v[AflWeather.THUNDER];
                windX = v[AflWeather.WIND_X];
                windZ = v[AflWeather.WIND_Z];
                gust = v[AflWeather.GUST];
            }
        }
        if (level == null || mc.isPaused()) return;
        float[] v = at(mc, level);
        if (v == null) return;
        rain += (AflWeather.level(v[AflWeather.RAIN]) - rain) * LEVEL_EASE;
        thunder += (v[AflWeather.THUNDER] - thunder) * LEVEL_EASE;
        windX += (v[AflWeather.WIND_X] - windX) * WIND_EASE;
        windZ += (v[AflWeather.WIND_Z] - windZ) * WIND_EASE;
        gust += (v[AflWeather.GUST] - gust) * WIND_EASE;
        level.setRainLevel(rain < 1e-3F ? 0 : rain);
        level.setThunderLevel(thunder < 1e-3F ? 0 : thunder);
    }

    private static float[] at(Minecraft mc, ClientLevel level) {
        Vec3 cam = mc.gameRenderer.getMainCamera().isInitialized() ? mc.gameRenderer.getMainCamera().getPosition()
                : mc.player != null ? mc.player.position() : null;
        return cam == null ? null : AflWeather.at(level, cam.x, cam.z);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AflWeather.clientClear();
        tracked = null;
    }
}
