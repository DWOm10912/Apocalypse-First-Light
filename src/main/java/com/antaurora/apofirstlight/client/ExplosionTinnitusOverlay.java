package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExplosionTinnitusOverlay {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID,
            "textures/gui/effects/explosion_tinnitus_overlay.png");

    private ExplosionTinnitusOverlay() {}

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.screen != null
                || mc.options.hideGui) return;
        float alpha = ExplosionTinnitusClientState.overlayAlpha(event.getPartialTick());
        if (alpha <= 0) return;
        GuiGraphics graphics = event.getGuiGraphics();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var shader = RenderSystem.getShader();
        int texture = RenderSystem.getShaderTexture(0);
        graphics.pose().pushPose();
        try {
            graphics.flush();
            mc.getTextureManager().getTexture(TEXTURE).setFilter(false, false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1, 1, 1, alpha);
            RenderSystem.disableDepthTest();
            graphics.blit(TEXTURE, 0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    0.0F, 0.0F, 256, 256, 256, 256);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            graphics.pose().popPose();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, texture);
        }
    }
}
