package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animation.AnimationController;

/** Hold-to-aim visual consumer. Deliberately never cancels Vanilla use/interactions or alters aim. */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT)
public final class NativeGunAds {
    private static final NativeAdsProgress PROGRESS=new NativeAdsProgress();
    private static Object level, player;
    private static Item item;
    private static int slot=-1, equippedTicks, reloadRequestTicks;
    private static long gunId;
    private static NativeAdsProfile profile;
    private static String blocked="no weapon";
    private static void reset() {
        PROGRESS.reset(); profile=null; item=null; equippedTicks=reloadRequestTicks=0; gunId=0;
    }
    private static boolean same(Minecraft mc) {
        return mc.player!=null && mc.level==level && mc.player==player && mc.player.isAlive()
                && !mc.player.isSpectator() && mc.player.getInventory().selected==slot
                && mc.player.getMainHandItem().getItem()==item
                && (gunId==0 || GeoItem.getId(mc.player.getMainHandItem())==gunId);
    }
    public static void reloadRequested() { reloadRequestTicks=3; }
    private static String action(NativeGunItem gun,long id) {
        var c=gun.getAnimatableInstanceCache().getManagerForId(id).getAnimationControllers().get("action");
        if(c==null || c.getTriggeredAnimation()==null || c.getAnimationState()==AnimationController.State.STOPPED
                || c.getCurrentAnimation()==null) return "";
        String clip=c.getCurrentAnimation().animation().name();
        return clip.contains("reload")||clip.equals("draw")||clip.equals("put_away")||clip.equals("inspect")?clip:"";
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!mc.player.isAlive()||mc.player.isSpectator()
                ||!(mc.player.getMainHandItem().getItem() instanceof NativeGunItem gun)) {reset();return;}
        if(!same(mc)) {
            reset(); level=mc.level; player=mc.player; slot=mc.player.getInventory().selected;
            item=mc.player.getMainHandItem().getItem(); profile=NativeAdsProfile.forGun(gun.definition().id());
        }
        if(profile==null)return;
        gunId=GeoItem.getId(mc.player.getMainHandItem());
        if(mc.isPaused())return;
        equippedTicks++;
        int drawTicks=gun.animationAsset()==null?3:NativeGunAnimations.ticks(gun.animationAsset(),"draw");
        blocked=action(gun,gunId);
        if(equippedTicks<=drawTicks)blocked="draw/equip";
        if(reloadRequestTicks>0){reloadRequestTicks--;blocked="reload request";}
        if(mc.player.isSprinting())blocked="sprint";
        if(mc.player.isUsingItem())blocked="item use";
        if(mc.screen!=null||!mc.isWindowActive())blocked="screen/focus";
        if(!mc.options.getCameraType().isFirstPerson())blocked="third person";
        float ticks=gun.definition().adsTicks();
        PROGRESS.tick(blocked.isEmpty()&&mc.options.keyUse.isDown(),ticks,ticks);
        if(Boolean.getBoolean("afl.nativeAdsDebug")&&mc.level.getGameTime()%20==0)
            ApocalypseFirstLight.LOGGER.info("[AFL ADS] gun={} progress={} blocked={} HIP={} ADS={} correction={} FOV={}",
                    gun.definition().id(),PROGRESS.sample(1),blocked,profile.hip(),profile.ads(),profile.translation(),gun.definition().adsFov());
    }
    public static float progress(float partial) {
        var mc=Minecraft.getInstance();
        return same(mc)&&profile!=null&&mc.options.getCameraType().isFirstPerson()?PROGRESS.sample(partial):0;
    }
    public static void apply(PoseStack pose,boolean right,float partial) {
        float p=progress(partial);if(p<=0)return;
        var c=profile.correction(right);var t=c.getTranslation(new Vector3f());
        pose.translate(t.x*p,t.y*p,t.z*p);
        pose.mulPose(new Quaternionf().slerp(c.getNormalizedRotation(new Quaternionf()),p));
    }
    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov e) {
        float p=progress((float)e.getPartialTick());
        if(p>0 && Minecraft.getInstance().player.getMainHandItem().getItem() instanceof NativeGunItem gun)
            e.setFOV(e.getFOV()*(1+(gun.definition().adsFov()-1)*p));
    }
    private NativeGunAds() {}
    @SubscribeEvent public static void disconnect(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        com.antaurora.apofirstlight.weapon.NativeGunData.clearClient(); reset();
    }
}
