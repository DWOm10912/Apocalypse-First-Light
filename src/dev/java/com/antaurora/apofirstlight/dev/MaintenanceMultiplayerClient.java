package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.client.GunMaintenanceScreen;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class MaintenanceMultiplayerClient {
    private static int ticks,last=-1;private static boolean done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        String role=System.getProperty("afl.maintenanceMultiplayerClient");
        if(role==null||done||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        if(++ticks%20!=0||mc.player==null||!(mc.screen instanceof GunMaintenanceScreen s))return;
        var dir=Path.of(System.getProperty("afl.multiplayerControl"));var file=dir.resolve("stage");if(!Files.exists(file))return;
        int stage=Integer.parseInt(Files.readString(file));if(stage==last)return;boolean a=role.equals("A");
        try{
            if(stage==1||stage==5||stage==9){if(a)s.mouseClicked(s.hotbarX()+90,s.hotbarY()+10,0);}
            if(stage==2||stage==6||stage==10){
                if(s.getMenu().synchronizedBench().isEmpty())return;
                check(a?s.placeholderSlot()==4&&!s.takeButtonVisible():s.placeholderSlot()==-1&&s.takeButtonVisible(),"owner versus non owner UI");
                check(!a||mc.player.getInventory().getItem(4).isEmpty(),"true origin empty");
                Screenshot.grab(mc.gameDirectory,"mp_"+role+"_stored_"+stage+".png",mc.getMainRenderTarget(),m->{});
            }
            if(stage==3&&!a||stage==7&&a||stage==11){s.mouseClicked(a?s.hotbarX()+90:s.takeButtonX()+10,s.hotbarY()+10,0);}
            if(stage==4||stage==8||stage==12){
                if(!s.getMenu().synchronizedBench().isEmpty())return;
                check(s.placeholderSlot()==-1&&!s.takeButtonVisible(),"live controls clear without reopening");
                if(stage==4&&!a)check(mc.player.getInventory().getItem(0).is(AflItems.P9_01.get()),"B received");
                if(stage==8&&a)check(mc.player.getInventory().getItem(4).is(AflItems.P9_01.get()),"A origin restored");
                Screenshot.grab(mc.gameDirectory,"mp_"+role+"_empty_"+stage+".png",mc.getMainRenderTarget(),m->{});
            }
            Files.writeString(dir.resolve("ack"+role),""+stage);last=stage;
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MP LIVE CLIENT] ROLE={} STAGE={} PASS",role,stage);
            if(stage==13){done=true;mc.stop();}
        }catch(Exception ex){done=true;Files.writeString(dir.resolve("failure"+role),ex.toString());com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.error("[MP LIVE CLIENT] FAIL",ex);mc.stop();}
    }
    private static void check(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
}
