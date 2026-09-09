package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.radiation.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashSet;

/** Isolated visual fixtures: no gameplay radiation or world changes. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class RadiationStaticProbe {
    private static int ticks,stage;private static boolean done,loading;
    private static double rate;
    private static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static void validate(){
        var hashes=new HashSet<Integer>();
        for(int frame=0;frame<8;frame++)try(var pixels=RadiationStaticTextures.generate(frame)){
            int count=0,hash=1;
            for(int y=0;y<64;y++)for(int x=0;x<64;x++){
                int color=pixels.getPixelRGBA(x,y);hash=31*hash+color;
                int alpha=color>>>24;if(alpha==0)continue;count++;
                int gray=color&255;
                check(gray>=150&&gray<=235&&((color>>>8)&255)==gray&&((color>>>16)&255)==gray,"gray range");
                check(alpha>=20&&alpha<=80,"pixel opacity");
            }
            check(count>=123&&count<=328,"3-8% coverage: "+count);hashes.add(hash);
            check(RadiationStaticTextures.frame(frame)!=null,"uploaded frame");
        }
        check(hashes.size()==8,"distinct frames");
        check(RadiationScreenStaticOverlay.normalize(0)==0,"zero");
        check(Math.abs(RadiationScreenStaticOverlay.normalize(10)-.3)<.0001,"low");
        check(Math.abs(RadiationScreenStaticOverlay.normalize(60)-.7)<.0001,"medium");
        check(RadiationScreenStaticOverlay.normalize(240)==1,"max");
        check(Math.abs(RadiationScreenStaticOverlay.alpha(.5F)-.05F)<.0001,"quadratic alpha");
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();
        if(done||!Boolean.getBoolean("afl.radiationStaticProbe")||event.phase!=TickEvent.Phase.END||mc.player==null||loading)return;
        mc.options.pauseOnLostFocus=false;
        RadiationAtmosphereClient.setTargetRadiation(rate);
        if(++ticks%60!=0)return;
        try{switch(stage++){
            case 0 -> {validate();mc.setScreen(null);rate=0;}
            case 1 -> {shot("zero");rate=10;}
            case 2 -> {shot("low");rate=45;}
            case 3 -> {shot("medium");rate=240;}
            case 4 -> {shot("high");loading=true;mc.reloadResourcePacks().whenComplete((v,e)->mc.execute(()->{loading=false;if(e!=null)finish("FAIL reload "+e);}));}
            case 5 -> {validate();shot("reloaded");rate=0;}
            case 6 -> {shot("fadeout");finish("PASS generation, normalization, alpha, resource reload; screenshots pending review");}
        }}catch(Exception ex){finish("FAIL "+ex);}
    }
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"radiation_static_"+name+".png",mc.getMainRenderTarget(),c->{});}
    private static void finish(String result){done=true;RadiationAtmosphereClient.setTargetRadiation(0);com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[RADIATION STATIC] {}",result);Minecraft.getInstance().stop();}
}
