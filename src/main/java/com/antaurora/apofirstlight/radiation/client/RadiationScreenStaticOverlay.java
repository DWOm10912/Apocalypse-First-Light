package com.antaurora.apofirstlight.radiation.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.Random;

@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT)
public final class RadiationScreenStaticOverlay {
    // Centralized accessibility/tuning settings; no new config framework.
    public static final boolean ENABLED=true;
    public static final float INTENSITY=1F, MAX_ALPHA=.20F, SMOOTHING=.25F;
    public static final int FRAME_TICKS=3, TILE_GUI_SIZE=128;
    private static final Random BANDS=new Random(0xAF512L);
    private static float display,bandX,bandY,bandWidth;
    private static int frameTick,frame,bandTicks,bandCooldown;
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private RadiationScreenStaticOverlay() {}

    /** Existing natural field: 1-10 / 10-60 / 60-240 RU/h; visual-only mapping. */
    public static float normalize(double rate) {
        if(!Double.isFinite(rate)||rate<=0) return 0;
        if(rate<=10) return (float)(rate/10*.30);
        if(rate<=60) return (float)(.30+(rate-10)/50*.40);
        return (float)Math.min(1,.70+(rate-60)/180*.30);
    }
    public static float alpha(float normalized) {
        return Math.min(.25F,normalized*normalized*MAX_ALPHA*INTENSITY);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=level||mc.player==null) {
            level=mc.level;display=0;frameTick=0;frame=0;bandTicks=0;bandCooldown=0;
        }
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        float target=ENABLED?normalize(RadiationAtmosphereClient.screenRadiation()):0;
        display+=(target-display)*SMOOTHING;
        if(Math.abs(target-display)<.001F)display=target;
        if(++frameTick>=FRAME_TICKS){frameTick=0;frame=(frame+1)%RadiationStaticTextures.FRAME_COUNT;}
        if(bandTicks>0)bandTicks--;
        if(bandCooldown>0)bandCooldown--;
        if(display>.45F&&bandTicks==0&&bandCooldown==0&&mc.screen==null&&BANDS.nextFloat()<display*.02F) {
            bandWidth=.2F+BANDS.nextFloat()*.6F;
            bandX=BANDS.nextFloat()*(1-bandWidth);bandY=BANDS.nextFloat();
            bandTicks=2;bandCooldown=20;
        }
    }
    public static void render(GuiGraphics graphics,int width,int height) {
        var mc=Minecraft.getInstance();
        if(!ENABLED||mc.level==null||mc.player==null||!mc.player.isAlive()||mc.screen!=null
                ||mc.options.hideGui||mc.isPaused()||display<=.001F)return;
        var texture=RadiationStaticTextures.frame(frame);
        if(texture==null)return;
        graphics.flush();
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int srcRgb=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dstRgb=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var shader=RenderSystem.getShader();int bound=RenderSystem.getShaderTexture(0);
        var color=RenderSystem.getShaderColor();float r=color[0],g=color[1],b=color[2],a=color[3];
        try {
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0,texture);RenderSystem.setShaderColor(1,1,1,alpha(display));
            var buffer=Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX);
            var pose=graphics.pose().last().pose();float u=(float)width/TILE_GUI_SIZE,v=(float)height/TILE_GUI_SIZE;
            buffer.vertex(pose,0,height,0).uv(0,v).endVertex();
            buffer.vertex(pose,width,height,0).uv(u,v).endVertex();
            buffer.vertex(pose,width,0,0).uv(u,0).endVertex();
            buffer.vertex(pose,0,0,0).uv(0,0).endVertex();
            BufferUploader.drawWithShader(buffer.end());
            if(bandTicks>0) {
                RenderSystem.setShaderColor(1,1,1,1);
                int opacity=Math.round(display*.025F*255);
                graphics.fill((int)(bandX*width),(int)(bandY*(height-2)),
                        (int)((bandX+bandWidth)*width),(int)(bandY*(height-2))+2,(opacity<<24)|0xCCCCCC);
                graphics.flush();
            }
        } finally {
            RenderSystem.setShaderColor(r,g,b,a);
            RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
            if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,bound);
        }
    }
}
