package com.antaurora.apofirstlight.client.light;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * AFL's own programs for what it draws itself with a vanilla terrain program (docs/rendering/dynamic_lights_v1.md): the
 * vanilla terrain programs bake the light map into the vertex colour (and Vextris's twins share their fragment stage, so
 * they cannot be given new varyings), so the road mesh draws with apocalypse_firstlight:afl_dynlit_solid while the
 * dynamic lights are drawn, else with vanilla's rendertype_solid (a shader pack's replacement of it included).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DynamicLitPrograms {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ShaderInstance solid;

    private DynamicLitPrograms() {
    }

    /** The solid terrain program for the road mesh: AFL's while the lights are drawn this frame, else vanilla's. */
    public static ShaderInstance solid() {
        ShaderInstance s = solid;
        return s != null && DynamicLights.drawing() ? s : GameRenderer.getRendertypeSolidShader();
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_dynlit_solid"),
                    DefaultVertexFormat.BLOCK), loaded -> solid = loaded);
        } catch (Exception e) {
            solid = null;
            LOGGER.warn("AFL dynamic lights: no road program ({}); the road keeps vanilla's", e.toString());
        }
    }
}
