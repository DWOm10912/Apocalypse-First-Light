package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.NativeGunFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Collection;

/** Opt-in screenshot evidence only; no input or inventory/world mutation. */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT)
public final class NativeGunV062Capture {
    private static int frame, shots, captured;
    private static Object newest;
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) throws ReflectiveOperationException {
        if (!Boolean.getBoolean("afl.dev.captureGunV062") || event.phase!=TickEvent.Phase.END || captured>=160) return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || mc.screen!=null || mc.getOverlay()!=null)return;
        var field=NativeGunFx.class.getDeclaredField("SHOTS"); field.setAccessible(true);
        Object[] pending=((Collection<?>)field.get(null)).toArray();
        if(pending.length>0 && pending[pending.length-1]!=newest) { newest=pending[pending.length-1];frame=0;shots++; }
        if(shots==0)return;
        int f=frame++;
        if(f>7 && f!=12 && f!=24 && f!=48 && f!=90)return;
        String name=String.format("afl-v062-%03d-%s-shot%d-frame%d.png",captured++,mc.options.getCameraType(),shots,f);
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->
                ApocalypseFirstLight.LOGGER.info("[AFL V062 CAPTURE] {}",message.getString()));
    }
}
