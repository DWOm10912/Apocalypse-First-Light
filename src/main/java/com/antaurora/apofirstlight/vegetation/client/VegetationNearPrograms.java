package com.antaurora.apofirstlight.vegetation.client;

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

/**
 * The near layer's programs without a shader pack (docs/gameplay/vegetation_v1_design.md 13.10): vanilla
 * rendertype_solid and rendertype_cutout_mipped with the plants' wind in the vertex stage (core/afl_vegetation_near_*,
 * include/afl_veg_wind.glsl), so a block's vertices stay in a buffer and still sway as the chunk mesh around it. Null
 * when they did not load: the near layer then draws with vanilla's (no wind on it).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VegetationNearPrograms {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ShaderInstance solid, cutout;

    private VegetationNearPrograms() {
    }

    /** Pass 0 solid, 1 cutout. */
    static ShaderInstance get(int pass) {
        return pass == 0 ? solid : cutout;
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_vegetation_near_solid"),
                    DefaultVertexFormat.BLOCK), loaded -> solid = loaded);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(ApocalypseFirstLight.MOD_ID, "afl_vegetation_near_cutout"),
                    DefaultVertexFormat.BLOCK), loaded -> cutout = loaded);
        } catch (Exception e) {
            solid = null;
            cutout = null;
            LOGGER.warn("AFL vegetation: no near-layer programs ({}); the near layer draws with vanilla's, without the wind", e.toString());
        }
    }
}
