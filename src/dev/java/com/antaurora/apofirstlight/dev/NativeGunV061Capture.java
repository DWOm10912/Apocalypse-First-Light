package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;

/** Opt-in render evidence, no input, ammo mutation or production diagnostics. */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID, value=Dist.CLIENT)
public final class NativeGunV061Capture {
    private static String previous="";
    private static int elapsed, captures;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("afl.dev.captureGunV061") || event.phase!=TickEvent.Phase.END || captures>=100) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.screen!=null || mc.getOverlay()!=null) return;
        var stack=mc.player.getMainHandItem();
        if(!(stack.getItem() instanceof P901Item item))return;
        var controller=item.getAnimatableInstanceCache().getManagerForId(GeoItem.getId(stack))
                .getAnimationControllers().get(P901Item.CONTROLLER);
        String clip=controller.getCurrentAnimation()==null ? "ready" : controller.getCurrentAnimation().animation().name().replace("animation.p9_01.","");
        String state=NativeGunAmmo.read(stack,item.definition())+"-"+clip;
        if(!state.equals(previous)){ previous=state;elapsed=0; }
        int t=elapsed++;
        if(t!=1 && t!=3 && t!=6 && t!=18 && t!=22 && t!=25 && t!=29) return;
        String name="afl-v061-"+String.format("%03d",captures++)+"-"+state+"-t"+t+".png";
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),
                message->ApocalypseFirstLight.LOGGER.info("[AFL V061 CAPTURE] {}",message.getString()));
    }
}
