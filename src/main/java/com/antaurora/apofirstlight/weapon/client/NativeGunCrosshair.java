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

/** Shooter-only confirmed feedback. Pixel primitives, no textures or client raycast. */
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
        var g=event.getGuiGraphics();int x=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()/2;
        g.fill(x-2,y-2,x+2,y+2,0x80303030);
        g.fill(x-1,y-1,x+1,y+1,0xFFE8E8E2);
        if(!sameGun(mc))return;
        double age=mc.level.getGameTime()+event.getPartialTick()-started,duration=head?6:5;
        if(age<0||age>=duration)return;
        double u=age/duration;int gap=(int)Math.round(7-3*u);
        int alpha=(int)(255*(1-u)*(1-u)),color=(alpha<<24)|(head?0xFF3B30:0xFFFFFF);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        try { for(int sx:new int[]{-1,1})for(int sy:new int[]{-1,1})for(int i=0;i<3;i++) {
            int px=x+sx*(gap+i),py=y+sy*(gap+i);g.fill(px,py,px+1,py+1,color);
        }} finally {com.mojang.blaze3d.systems.RenderSystem.disableBlend();}
    }
    private NativeGunCrosshair() {}
}
