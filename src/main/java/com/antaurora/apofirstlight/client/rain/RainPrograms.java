package com.antaurora.apofirstlight.client.rain;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Rain V1's drop program, apocalypse_firstlight:afl_rain (docs/rendering/rain_v1.md); without it vanilla rain stays. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RainPrograms {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ShaderInstance rain;

    private RainPrograms() {
    }

    static ShaderInstance rain() {
        return rain;
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_rain"),
                    DefaultVertexFormat.POSITION_TEX), loaded -> {
                rain = loaded;
                RainRenderer.programChanged();
            });
        } catch (Exception e) {
            rain = null;
            LOGGER.warn("AFL rain: no drop program ({}); vanilla rain stays", e.toString());
        }
    }
}
