package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shooter-only confirmed feedback. Pixel primitives, no textures or client raycast. Crosshair V1 (docs/ui/crosshair_v1.md,
 * 2026-10-26): hip fire the dot and the X (NativeDynamicCrosshair), a hit flashing the X white / red; aiming down the sights
 * the X shows only for a hit.
 */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT)
public final class NativeGunCrosshair {
    private static net.minecraft.client.multiplayer.ClientLevel hitLevel;
    private static long hitGun;
    private static int hitSlot;
    private static double started;
    private static boolean head;
    private static boolean holding(Minecraft mc) {
        return mc.player!=null && mc.level!=null && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.player.getMainHandItem().getItem() instanceof NativeGunItem;
    }
    public static void hit(boolean isHead) {
        var mc=Minecraft.getInstance();if(!holding(mc))return;
        hitLevel=mc.level;hitGun=software.bernie.geckolib.animatable.GeoItem.getId(mc.player.getMainHandItem());
        hitSlot=mc.player.getInventory().selected;head=isHead;
        started=mc.level.getGameTime()+mc.getFrameTime();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        var mc=Minecraft.getInstance();
        if(e.phase==TickEvent.Phase.END)NativeDynamicCrosshair.tick();
        if(e.phase==TickEvent.Phase.END && (!holding(mc)||!sameGun(mc))) {
            hitLevel=null;
        }
    }
    private static boolean sameGun(Minecraft mc) {
        return mc.level==hitLevel && mc.player.getInventory().selected==hitSlot
                && software.bernie.geckolib.animatable.GeoItem.getId(mc.player.getMainHandItem())==hitGun;
    }
    @SubscribeEvent public static void render(RenderGuiOverlayEvent.Pre event) {
        if(!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()))return;
        var mc=Minecraft.getInstance();if(!holding(mc))return;
        event.setCanceled(true);
        if(mc.options.hideGui||mc.screen!=null||!mc.options.getCameraType().isFirstPerson())return;
        var g=event.getGuiGraphics();
        double hit=-1;
        if(sameGun(mc)) {
            double age=mc.level.getGameTime()+event.getPartialTick()-started,duration=head?6:5;
            if(age>=0&&age<duration)hit=age/duration;
        }
        boolean hip=NativeGunAds.progress(event.getPartialTick())<.999F;
        if(!hip&&hit<0)return;
        com.antaurora.apofirstlight.client.AflCrosshair.begin(g);
        try {
            if(hip)NativeDynamicCrosshair.render(g,event.getPartialTick(),hit,head);
            else NativeDynamicCrosshair.hitOnly(g,hit,head);
        } finally {com.antaurora.apofirstlight.client.AflCrosshair.end(g);}
    }
    private NativeGunCrosshair() {}
}
