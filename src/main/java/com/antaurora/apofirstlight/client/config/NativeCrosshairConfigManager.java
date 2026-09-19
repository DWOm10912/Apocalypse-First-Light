package com.antaurora.apofirstlight.client.config;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.hudlayout.HudLayoutDevResources;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class NativeCrosshairConfigManager {
    private static final ResourceLocation RESOURCE=new ResourceLocation(ApocalypseFirstLight.MOD_ID,"gui/layout/native_crosshair.json");
    private static volatile NativeCrosshairConfig current=NativeCrosshairConfig.defaults();
    public static NativeCrosshairConfig get() { return current; }
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<NativeCrosshairConfig>() {
            @Override protected NativeCrosshairConfig prepare(ResourceManager manager,ProfilerFiller profiler) {
                try(var reader=HudLayoutDevResources.open(manager,RESOURCE)) {
                    return NativeCrosshairConfig.parse(JsonParser.parseReader(reader).toString());
                } catch(Exception e) {
                    ApocalypseFirstLight.LOGGER.warn("Cannot load native crosshair; using defaults",e);
                    return NativeCrosshairConfig.defaults();
                }
            }
            @Override protected void apply(NativeCrosshairConfig config,ResourceManager manager,ProfilerFiller profiler) { current=config; }
        });
    }
    private NativeCrosshairConfigManager() {}
}
