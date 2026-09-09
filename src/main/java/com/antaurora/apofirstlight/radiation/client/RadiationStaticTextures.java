package com.antaurora.apofirstlight.radiation.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;
import java.util.Random;

@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class RadiationStaticTextures {
    public static final int FRAME_COUNT=8, SIZE=64;
    private static final ResourceLocation[] FRAMES=new ResourceLocation[FRAME_COUNT];
    private static boolean ready;
    static {
        for(int i=0;i<FRAME_COUNT;i++) FRAMES[i]=new ResourceLocation(ApocalypseFirstLight.MOD_ID,"dynamic/radiation_static_"+i);
    }
    private RadiationStaticTextures() {}
    public static ResourceLocation frame(int index) { return ready ? FRAMES[index] : null; }

    @SubscribeEvent public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("radiation_screen_static", (gui,g,partial,w,h)->RadiationScreenStaticOverlay.render(g,w,h));
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            if(RenderSystem.isOnRenderThread()) rebuild();
            else RenderSystem.recordRenderCall(RadiationStaticTextures::rebuild);
        });
    }

    /** TextureManager owns registered textures and closes them on release and shutdown. */
    private static void rebuild() {
        ready=false;
        var manager=Minecraft.getInstance().getTextureManager();
        for(var id:FRAMES) manager.release(id);
        try {
            for(int i=0;i<FRAME_COUNT;i++) {
                NativeImage pixels=generate(i);
                DynamicTexture texture;
                try { texture=new DynamicTexture(pixels); }
                catch(RuntimeException | Error ex) { pixels.close(); throw ex; }
                try {
                    manager.register(FRAMES[i],texture);
                    texture.setFilter(false,false);
                    texture.bind();
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);
                } catch(RuntimeException | Error ex) { texture.close(); throw ex; }
            }
            ready=true;
        } catch(RuntimeException | Error ex) {
            for(var id:FRAMES) manager.release(id);
            throw ex;
        }
    }

    /** Only called during resource reload (and dev validation), never during rendering. */
    public static NativeImage generate(int frame) {
        NativeImage image=new NativeImage(SIZE,SIZE,true);
        Random random=new Random(0xAF1201L+frame*104729L);
        for(int i=0;i<160;i++) {
            int x=random.nextInt(SIZE),y=random.nextInt(SIZE),shape=random.nextInt(100);
            int width=shape<83?1:shape<95?2:shape<98?1:shape<99?2:3+random.nextInt(3);
            int height=shape>=95&&shape<99?2:1;
            int gray=150+random.nextInt(86),alpha=20+random.nextInt(61);
            int abgr=(alpha<<24)|(gray<<16)|(gray<<8)|gray;
            for(int dy=0;dy<height;dy++) for(int dx=0;dx<width;dx++)
                image.setPixelRGBA((x+dx)%SIZE,(y+dy)%SIZE,abgr);
        }
        return image;
    }
}
