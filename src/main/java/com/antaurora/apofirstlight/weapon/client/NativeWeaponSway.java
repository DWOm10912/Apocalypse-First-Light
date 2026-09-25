package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class NativeWeaponSway {
    private static final ResourceLocation SILVERWOOD_12 = new ResourceLocation("apocalypse_firstlight", "silverwood_12");
    private static Object level,player;
    private static float previous,amplitude;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=level||mc.player!=player){level=mc.level;player=mc.player;previous=amplitude=0;}
        if(mc.isPaused())return;
        previous=amplitude;
        float target=0;
        if(mc.player!=null&&mc.player.isAlive()&&mc.player.getMainHandItem().getItem() instanceof NativeGunItem
                &&mc.screen==null&&mc.options.getCameraType().isFirstPerson()){
            var p=WeaponSwayProfile.DEFAULT;
            target=Mth.lerp(NativeGunAds.progress(1),1,mc.player.isCrouching()?p.crouchAds():p.ads());
            if(mc.player.getDeltaMovement().horizontalDistanceSqr()>.0001)target*=.4f;
            String blocked=NativeGunAds.swayBlockReason();
            if(blocked.contains("reload"))target*=.1f;
            else if(!blocked.isEmpty())target=0;
            if(mc.player.isSprinting())target=0;
        }
        // Exponential 150 ms time constant, followed by render partial-tick interpolation.
        amplitude+=(target-amplitude)*(float)(1-Math.exp(-.05/.15));
    }
    public static void apply(PoseStack pose,float partial){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.screen!=null||!mc.options.getCameraType().isFirstPerson())return;
        var p=WeaponSwayProfile.DEFAULT;
        float silverwoodAds = mc.player.getMainHandItem().getItem() instanceof NativeGunItem gun
                && SILVERWOOD_12.equals(gun.definition().id()) ? NativeGunAds.progress(partial) : 0;
        double amount=Mth.lerp(partial,previous,amplitude);
        double seconds=(mc.level.getGameTime()+partial)/20d;
        double a=seconds*2*Math.PI/p.period(),b=seconds*2*Math.PI/p.secondaryPeriod();
        // Blend the two waves while aiming so the slower Silverwood cadence cannot snap phase at ADS entry.
        double sinA=Mth.lerp(silverwoodAds,Math.sin(a),Math.sin(a*.75));
        double sinB=Mth.lerp(silverwoodAds,Math.sin(b),Math.sin(b*.75));
        double cosB=Mth.lerp(silverwoodAds,Math.cos(b),Math.cos(b*.75));
        double horizontal=1-.35*silverwoodAds,vertical=1-.20*silverwoodAds,roll=1-.50*silverwoodAds;
        pose.translate(p.x()*horizontal*sinB*amount,p.y()*vertical*sinA*amount,0);
        pose.mulPose(Axis.YP.rotationDegrees((float)(p.yaw()*horizontal*(.8*sinB+.2*sinA)*amount)));
        pose.mulPose(Axis.XP.rotationDegrees((float)(p.pitch()*vertical*sinA*amount)));
        pose.mulPose(Axis.ZP.rotationDegrees((float)(p.roll()*roll*cosB*amount)));
    }
    private NativeWeaponSway(){}
}
