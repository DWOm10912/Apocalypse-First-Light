package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.config.NativeGunHudConfig;
import com.antaurora.apofirstlight.client.config.NativeGunHudConfigManager;
import com.antaurora.apofirstlight.weapon.NativeGunAmmo;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A view of vanilla-synced stack/inventory data; no ammo cache or client writes. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class NativeGunHud {
    private NativeGunHud() {}
    public static final int SHOT_FLASH_TICKS = 5;
    private static long flashUntil, flashGun;
    private static int flashSlot;
    private static net.minecraft.client.multiplayer.ClientLevel flashLevel;
    public static void shot(int slot, long gunId) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        flashLevel = mc.level;
        flashUntil = mc.level.getGameTime() + SHOT_FLASH_TICKS;
        flashGun = gunId;
        flashSlot = slot;
    }
    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.player.isSpectator()
                || !(mc.player.getMainHandItem().getItem() instanceof NativeGunItem gun)) return;
        var definition = gun.definition();
        int current = NativeGunAmmo.read(mc.player.getMainHandItem(), definition);
        String reserve = NativeGunAmmo.infiniteReserve(mc.player.getInventory())
                ? "\u221e" : Integer.toString(NativeGunAmmo.reserve(mc.player.getInventory(), definition));
        boolean empty = current == 0;
        boolean flash = mc.level == flashLevel && mc.level.getGameTime() < flashUntil
                && mc.player.getInventory().selected == flashSlot
                && software.bernie.geckolib.animatable.GeoItem.getId(mc.player.getMainHandItem()) == flashGun;
        var layout=NativeGunHudConfigManager.get();
        var frame=NativeGunHudLayout.frame(layout.global(),width,height);
        var mode=com.antaurora.apofirstlight.weapon.NativeFireModes.current(mc.player.getMainHandItem(),definition);
        var modeText=Component.translatable("hud.apocalypse_firstlight.fire_mode."+mode.key());
        graphics.enableScissor((int)Math.floor(frame.x()),(int)Math.floor(frame.y()),
                (int)Math.ceil(frame.x()+frame.width()),(int)Math.ceil(frame.y()+frame.height()));
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(frame.x(),frame.y(),0);
            graphics.pose().scale(frame.scale(),frame.scale(),1);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            var icon=NativeGunHudLayout.silhouette(layout,definition.hudWidth(),definition.hudHeight());
            int tint=NativeGunHudConfig.argb(empty?layout.silhouette().emptyColor():layout.silhouette().color(),1);
            graphics.setColor(((tint>>16)&255)/255F,((tint>>8)&255)/255F,(tint&255)/255F,layout.silhouette().alpha());
            graphics.pose().pushPose();
            graphics.pose().translate(icon.x(),icon.y(),0);
            graphics.pose().scale(icon.width(),icon.height(),1);
            graphics.blit(definition.hudIcon(),0,0,1,1,0,0,1,1,1,1);
            graphics.pose().popPose();
            graphics.setColor(1,1,1,1);
            var divider=NativeGunHudLayout.divider(layout);
            graphics.pose().pushPose();
            graphics.pose().translate(divider.x(),divider.y(),0);
            graphics.pose().scale(divider.width(),divider.height(),1);
            graphics.fill(0,0,1,1,NativeGunHudConfig.argb(layout.divider().color(),layout.divider().alpha()));
            graphics.pose().popPose();
            var name=layout.weaponName();
            textRow(graphics,mc.font,mc.player.getMainHandItem().getHoverName(),layout,
                    name.offsetX(),name.offsetY(),name.scale(),name.maxWidth(),name.color());
            ammoRow(graphics,mc.font,Integer.toString(current),reserve,layout,empty,flash);
            var fm=layout.fireMode();
            String modeColor=switch(mode){case SEMI->fm.semiColor();case BURST->fm.burstColor();case AUTO->fm.autoColor();};
            textRow(graphics,mc.font,modeText,layout,fm.offsetX(),fm.offsetY(),fm.scale(),fm.maxWidth(),modeColor);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    };

    private static void textRow(GuiGraphics g,Font font,Component text,NativeGunHudConfig c,
                                float center,float y,float scale,float maxWidth,String color) {
        var row=NativeGunHudLayout.row(c,center,maxWidth);
        scale=Math.min(scale,(c.global().height()-2)/font.lineHeight);
        // Preserve the preferred size for short names; shrink, then elide very long localized names.
        scale=Math.max(Math.min(.55F,scale),Math.min(scale,row.width()/Math.max(1,font.width(text))));
        if(font.width(text)*scale>row.width()) {
            int budget=(int)Math.floor(row.width()/scale);
            text=budget<font.width("…")?Component.empty():Component.literal(
                    font.plainSubstrByWidth(text.getString(),budget-font.width("…"))+"…");
        }
        y=NativeGunHudLayout.clamp(y,1,c.global().height()-font.lineHeight*scale-1);
        draw(g,font,text,row.center()-font.width(text)*scale/2,y,scale,NativeGunHudConfig.argb(color,1));
    }

    private static void ammoRow(GuiGraphics g,Font font,String current,String reserve,NativeGunHudConfig c,
                                boolean empty,boolean flash) {
        var a=c.ammo();var row=NativeGunHudLayout.row(c,a.offsetX(),a.maxWidth());
        float currentWidth=font.width(current)*a.currentScale();
        float separatorWidth=font.width("|")*a.separatorScale();
        float width=currentWidth+separatorWidth+font.width(reserve)*a.reserveScale()+2*a.gap();
        float largest=Math.max(a.currentScale(),Math.max(a.reserveScale(),a.separatorScale()));
        float fit=Math.min(1,row.width()/Math.max(1,width));
        fit=Math.min(fit,(c.global().height()-2)/(font.lineHeight*largest));
        float y=NativeGunHudLayout.clamp(a.offsetY(),1,c.global().height()-font.lineHeight*largest*fit-1);
        g.pose().pushPose();
        try {
            g.pose().translate(row.center()-width*fit/2,y,0);g.pose().scale(fit,fit,1);
            float baseline=(font.lineHeight-1)*largest;
            draw(g,font,Component.literal(current),0,baseline-(font.lineHeight-1)*a.currentScale(),a.currentScale(),
                    NativeGunHudConfig.argb(empty?a.emptyColor():flash?a.flashColor():a.currentColor(),1));
            draw(g,font,Component.literal("|"),currentWidth+a.gap(),baseline-(font.lineHeight-1)*a.separatorScale(),
                    a.separatorScale(),NativeGunHudConfig.argb(a.separatorColor(),1));
            draw(g,font,Component.literal(reserve),currentWidth+separatorWidth+2*a.gap(),baseline-(font.lineHeight-1)*a.reserveScale(),
                    a.reserveScale(),NativeGunHudConfig.argb(a.reserveColor(),1));
        } finally {g.pose().popPose();}
    }

    private static void draw(GuiGraphics g,Font font,Component text,float x,float y,float scale,int color) {
        g.pose().pushPose();
        try {g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawString(font,text,0,0,color,true);}
        finally {g.pose().popPose();}
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> NativeGunHudConfigManager.load(Minecraft.getInstance().getResourceManager()));
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(NativeGunHudConfigManager.reloadListener());
    }
    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "native_gun", OVERLAY);
    }
}
