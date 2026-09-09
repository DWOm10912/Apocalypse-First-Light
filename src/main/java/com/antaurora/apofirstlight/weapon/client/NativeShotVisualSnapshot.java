package com.antaurora.apofirstlight.weapon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.NativeTrailGeometry;
import software.bernie.geckolib.animatable.GeoItem;
import java.util.LinkedHashMap;

/** Input freezes the last actually presented gun, not a future post-confirmation render pose.
 * No predicted effects. The server's successful result supplies the endpoint and unlocks playback. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class NativeShotVisualSnapshot {
    public record Snapshot(long shotId,long gun,double inputTime,double poseTime,Vec3 muzzle,
                           Vec3 barrelDirection,boolean suppressed) {}
    private record Presented(long gun,double time,long frame,Vec3 muzzle,Vec3 direction,boolean suppressed,ItemStack stack) {}
    private static Object world,player;
    private static Presented presented;
    private static long sequence;
    private static long lastConfirmed;
    private static long frame;
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent e){
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.START){frame++;check();}
    }
    private static final LinkedHashMap<Long,Snapshot> pending=new LinkedHashMap<>();
    private static double now(){var mc=Minecraft.getInstance();return mc.level.getGameTime()+mc.getFrameTime();}
    private static void check(){var mc=Minecraft.getInstance();if(world!=mc.level||player!=mc.player){world=mc.level;player=mc.player;presented=null;pending.clear();}
        if(mc.level!=null)pending.values().removeIf(s->now()-s.inputTime()>100);
    }
    public static void presented(long gun,Vec3 origin,Vec3 direction,boolean suppressed){
        check();var mc=Minecraft.getInstance();
        if(mc.player==null||mc.screen!=null||!mc.options.getCameraType().isFirstPerson())return;
        if(GeoItem.getId(mc.player.getMainHandItem())!=gun||!NativeTrailGeometry.finite(origin)||direction.lengthSqr()<.5)return;
        presented=new Presented(gun,now(),frame,origin,direction,suppressed,mc.player.getMainHandItem().copy());
    }
    public static long capture(){
        check();long id=++sequence;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!mc.options.getCameraType().isFirstPerson())return id;
        var p=presented;
        // Never substitute an eye position or wait for a recoiled pose when the gun was not presented.
        if(p==null||frame-p.frame()>1||now()-p.time()>1||!ItemStack.isSameItemSameTags(p.stack(),mc.player.getMainHandItem()))return id;
        if(pending.size()>=128)pending.remove(pending.keySet().iterator().next());
        pending.put(id,new Snapshot(id,p.gun(),now(),p.time(),p.muzzle(),p.direction(),p.suppressed()));
        if(Boolean.getBoolean("afl.shotSnapshotDebug"))com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[SHOT SNAPSHOT] capture id={} poseTime={} inputTime={} muzzle={}",id,p.time(),now(),p.muzzle());
        return id;
    }
    public static boolean confirm(int shooter,long gun,long id,Vec3 end){
        check();var mc=Minecraft.getInstance();
        if(mc.player==null||mc.player.getId()!=shooter||id<=0)return false;
        var s=pending.remove(id);
        if(Boolean.getBoolean("afl.shotSnapshotDebug"))com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[SHOT SNAPSHOT] pairing id={} snapshotGun={} resultGun={} heldGun={} firstPerson={}",id,s==null?null:s.gun(),gun,GeoItem.getId(mc.player.getMainHandItem()),mc.options.getCameraType().isFirstPerson());
        if(!mc.options.getCameraType().isFirstPerson())return false;
        // Local first-person positive IDs must never fall back to a later gun pose.
        NativeGunFx.shot(shooter,gun,true);
        if(s==null||!NativeTrailGeometry.finite(end)||(s.gun()!=0&&s.gun()!=gun)
                ||!(mc.player.getMainHandItem().getItem() instanceof NativeGunItem item))return true;
        if(GeoItem.getId(mc.player.getMainHandItem())!=gun)return true;
        NativeBulletTrails.snapshot(s.muzzle(),end,item.definition().trail());
        NativeGunFx.frozen(s,shooter,gun);
        lastConfirmed=id;
        if(Boolean.getBoolean("afl.shotSnapshotDebug"))com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[SHOT SNAPSHOT] confirm id={} muzzle={} endpoint={}",id,s.muzzle(),end);
        return true;
    }
    private NativeShotVisualSnapshot(){}
}
