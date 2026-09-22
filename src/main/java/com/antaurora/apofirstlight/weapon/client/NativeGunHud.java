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
        NativeDynamicCrosshair.shot(slot, gunId);
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        flashLevel = mc.level;
        flashUntil = mc.level.getGameTime() + SHOT_FLASH_TICKS;
        flashGun = gunId;
        flashSlot = slot;
    }
    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (FieldAttachmentViewState.isActive() || mc.player == null || mc.options.hideGui || mc.player.isSpectator()
                || !(mc.player.getMainHandItem().getItem() instanceof NativeGunItem gun)) return;
        var definition = gun.definition();
        int current = NativeGunAmmo.read(mc.player.getMainHandItem(), definition);
        String reserve = NativeGunAmmo.infiniteReserve(mc.player.getInventory())
                ? "\u221e" : Integer.toString(NativeGunAmmo.reserve(mc.player.getInventory(), definition));
        boolean empty = current == 0;
        boolean flash = mc.level == flashLevel && mc.level.getGameTime() < flashUntil
                && mc.player.getInventory().selected == flashSlot
                && software.bernie.geckolib.animatable.GeoItem.getId(mc.player.getMainHandItem()) == flashGun;
        renderLayout(graphics,width,height,NativeGunHudConfigManager.get(),mc.player.getMainHandItem(),
                Integer.toString(current),reserve,empty,flash);
    };

    /** Same renderer for editor previews; never equips or mutates the sample stack. */
    public static net.minecraft.world.item.ItemStack editorStack() {
        var player=Minecraft.getInstance().player;
        return player!=null && player.getMainHandItem().getItem() instanceof NativeGunItem
                ?player.getMainHandItem():com.antaurora.apofirstlight.registry.AflItems.P9_01.get().getDefaultInstance();
    }

    public static java.util.Map<String,NativeGunHudLayout.Box> preview(GuiGraphics graphics,int width,int height,
                                                                       NativeGunHudConfig layout) {
        var stack=editorStack();var definition=((NativeGunItem)stack.getItem()).definition();
        int current=NativeGunAmmo.read(stack,definition);
        var player=Minecraft.getInstance().player;
        String reserve=player==null?"0":NativeGunAmmo.infiniteReserve(player.getInventory())?"∞"
                :Integer.toString(NativeGunAmmo.reserve(player.getInventory(),definition));
        return renderLayout(graphics,width,height,layout,stack,Integer.toString(current),reserve,current==0,false);
    }

    /** Null graphics measures the exact fitted text/icon geometry without issuing draw calls. */
    private static java.util.Map<String,NativeGunHudLayout.Box> renderLayout(GuiGraphics graphics,int width,int height,
                NativeGunHudConfig layout,net.minecraft.world.item.ItemStack stack,String current,String reserve,
                boolean empty,boolean flash) {
        var mc=Minecraft.getInstance();var definition=((NativeGunItem)stack.getItem()).definition();
        var frame=NativeGunHudLayout.frame(layout.global(),width,height);
        var mode=com.antaurora.apofirstlight.weapon.NativeFireModes.current(stack,definition);
        var modeText=Component.translatable("hud.apocalypse_firstlight.fire_mode."+mode.key());
        var icon=NativeGunHudLayout.silhouette(layout,definition.hudWidth(),definition.hudHeight());
        var divider=NativeGunHudLayout.divider(layout);
        var name=layout.weaponName();var fm=layout.fireMode();
        String modeColor=switch(mode){case SEMI->fm.semiColor();case BURST->fm.burstColor();case AUTO->fm.autoColor();};
        if(graphics==null) {
            var boxes=new java.util.LinkedHashMap<String,NativeGunHudLayout.Box>();
            boxes.put("silhouette",icon);boxes.put("divider",divider);
            boxes.put("weapon_name",textRow(null,mc.font,stack.getHoverName(),layout,
                    name.offsetX(),name.offsetY(),name.scale(),name.width(),name.height(),name.color()));
            boxes.put("ammo",ammoRow(null,mc.font,current,reserve,layout,empty,flash));
            boxes.put("fire_mode",textRow(null,mc.font,modeText,layout,fm.offsetX(),fm.offsetY(),fm.scale(),fm.width(),fm.height(),modeColor));
            return boxes;
        }
        graphics.enableScissor((int)Math.floor(frame.x()),(int)Math.floor(frame.y()),
                (int)Math.ceil(frame.x()+frame.width()),(int)Math.ceil(frame.y()+frame.height()));
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(frame.x(),frame.y(),0);
            graphics.pose().scale(frame.scale(),frame.scale(),1);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            int tint=NativeGunHudConfig.argb(empty?layout.silhouette().emptyColor():layout.silhouette().color(),1);
            graphics.setColor(((tint>>16)&255)/255F,((tint>>8)&255)/255F,(tint&255)/255F,layout.silhouette().alpha());
            graphics.pose().pushPose();
            graphics.pose().translate(icon.x(),icon.y(),0);
            graphics.pose().scale(icon.width(),icon.height(),1);
            graphics.blit(definition.hudIcon(),0,0,1,1,0,0,1,1,1,1);
            graphics.pose().popPose();
            graphics.setColor(1,1,1,1);
            graphics.pose().pushPose();
            graphics.pose().translate(divider.x(),divider.y(),0);
            graphics.pose().scale(divider.width(),divider.height(),1);
            graphics.fill(0,0,1,1,NativeGunHudConfig.argb(layout.divider().color(),layout.divider().alpha()));
            graphics.pose().popPose();
            textRow(graphics,mc.font,stack.getHoverName(),layout,
                    name.offsetX(),name.offsetY(),name.scale(),name.width(),name.height(),name.color());
            ammoRow(graphics,mc.font,current,reserve,layout,empty,flash);
            textRow(graphics,mc.font,modeText,layout,fm.offsetX(),fm.offsetY(),fm.scale(),fm.width(),fm.height(),modeColor);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            graphics.pose().popPose();
            graphics.disableScissor();
        }
        return java.util.Map.of();
    }

    private static NativeGunHudLayout.Box textRow(GuiGraphics g,Font font,Component text,NativeGunHudConfig c,
                                float left,float y,float scale,float width,float height,String color) {
        var row=NativeGunHudLayout.row(left,width);
        // Both box dimensions are limits. Neither a short string nor a wide box may upscale text.
        scale=NativeGunHudLayout.textScale(scale,row.width(),height,font.width(text),font.lineHeight);
        if(font.width(text)*scale>row.width()) {
            int budget=(int)Math.floor(row.width()/scale);
            text=budget<font.width("…")?Component.empty():Component.literal(
                    font.plainSubstrByWidth(text.getString(),budget-font.width("…"))+"…");
        }
        float x=row.center()-font.width(text)*scale/2;
        if(g!=null) draw(g,font,text,x,y,scale,NativeGunHudConfig.argb(color,1));
        return new NativeGunHudLayout.Box(x,y,(font.width(text)+1)*scale,font.lineHeight*scale);
    }

    private static NativeGunHudLayout.Box ammoRow(GuiGraphics g,Font font,String current,String reserve,NativeGunHudConfig c,
                                boolean empty,boolean flash) {
        var a=c.ammo();var row=NativeGunHudLayout.row(a.offsetX(),a.width());
        float currentWidth=font.width(current)*a.currentScale();
        float separatorWidth=font.width("|")*a.separatorScale();
        float width=currentWidth+separatorWidth+font.width(reserve)*a.reserveScale()+2*a.gap();
        float largest=Math.max(a.currentScale(),Math.max(a.reserveScale(),a.separatorScale()));
        float fit=Math.min(1,row.width()/Math.max(1,width));
        fit=Math.min(fit,a.height()/(font.lineHeight*largest));
        float y=a.offsetY();
        var bounds=new NativeGunHudLayout.Box(row.center()-width*fit/2,y,(width+largest)*fit,font.lineHeight*largest*fit);
        if(g==null) return bounds;
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
        return bounds;
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
