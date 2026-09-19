package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunAmmo;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
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
        // Above the hotbar/status bars even at small GUI widths. Coordinates are GUI-scaled units.
        var mode=com.antaurora.apofirstlight.weapon.NativeFireModes.current(mc.player.getMainHandItem(),definition);
        var modeText=net.minecraft.network.chat.Component.translatable("hud.apocalypse_firstlight.fire_mode."+mode.key());
        String currentText=Integer.toString(current);
        float detailScale=1.10F;
        float modeScale=.78F;
        // No fixed-width ammo column: the separator follows the actual scaled digits.
        int separatorX=2+(int)Math.ceil(mc.font.width(currentText)*1.25F)+3;
        int reserveOffset=mc.font.width("|")+3;
        int modeOffset=reserveOffset+mc.font.width(reserve)+5;
        float modeX=separatorX+modeOffset*detailScale;
        int rowWidth=(int)Math.ceil(modeX+mc.font.width(modeText)*modeScale);
        float rowScale=Math.min(1F,(width-8F)/rowWidth);
        int x = Math.max(4, width - Math.max(70,(int)Math.ceil(rowWidth*rowScale)+4)), y = Math.max(4, height - 99);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            graphics.setColor(1, empty ? .15F : 1, empty ? .15F : 1, 1);
            graphics.blit(definition.hudIcon(), 0, 22 - definition.hudHeight(), definition.hudWidth(), definition.hudHeight(), 0, 0, 1, 1, 1, 1);
            graphics.setColor(1, 1, 1, 1);
            // Name belongs to the combat HUD silhouette, not the hotbar return placeholder.
            var name = mc.player.getMainHandItem().getHoverName();
            int maxNameWidth = Math.min(140, width - 8);
            if (mc.font.width(name) > maxNameWidth) {
                name = net.minecraft.network.chat.Component.literal(mc.font.plainSubstrByWidth(
                        name.getString(), maxNameWidth - mc.font.width("…")) + "…");
            }
            int nameWidth = mc.font.width(name);
            int nameLeft = net.minecraft.util.Mth.clamp(x + definition.hudWidth() / 2 - nameWidth / 2,
                    4, width - 4 - nameWidth);
            int nameTop = Math.max(4, y + 22 - definition.hudHeight() - mc.font.lineHeight - 6);
            graphics.drawString(mc.font, name, nameLeft - x, nameTop - y, 0xCCCCCC, true);
            graphics.pose().pushPose();
            graphics.pose().translate(0,29,0);
            graphics.pose().scale(rowScale,rowScale,1);
            graphics.pose().pushPose();
            graphics.pose().translate(2, -2, 0);
            graphics.pose().scale(1.25F, 1.25F, 1);
            graphics.drawString(mc.font, currentText, 0, 0, empty || flash ? 0xFF3333 : 0xFFFFFF, true);
            graphics.pose().popPose();
            graphics.pose().pushPose();
            // Reserve remains larger; mode uses its own scale around the shared text baseline.
            graphics.pose().translate(separatorX,-1,0);
            graphics.pose().scale(detailScale,detailScale,1);
            graphics.drawString(mc.font,"|",0,0,0xFFFFFF,true);
            graphics.drawString(mc.font,reserve,reserveOffset,0,0xFFFFFF,true);
            graphics.pose().popPose();
            graphics.pose().pushPose();
            // Exclude the font's trailing line spacing when aligning the smaller text.
            float baseline=mc.font.lineHeight-1F;
            graphics.pose().translate(modeX,-1+baseline*(detailScale-modeScale),0);
            graphics.pose().scale(modeScale,modeScale,1);
            graphics.drawString(mc.font,modeText,0,0,mode.color,true);
            graphics.pose().popPose();
            graphics.pose().popPose();
        } finally {
            graphics.setColor(1, 1, 1, 1);
            graphics.pose().popPose();
        }
    };
    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "native_gun", OVERLAY);
    }
}
