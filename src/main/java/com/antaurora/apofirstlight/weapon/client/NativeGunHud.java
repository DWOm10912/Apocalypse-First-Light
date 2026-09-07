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
        int reserve = NativeGunAmmo.reserve(mc.player.getInventory(), definition);
        boolean empty = current == 0;
        boolean flash = mc.level == flashLevel && mc.level.getGameTime() < flashUntil
                && mc.player.getInventory().selected == flashSlot
                && software.bernie.geckolib.animatable.GeoItem.getId(mc.player.getMainHandItem()) == flashGun;
        // Above the hotbar/status bars even at small GUI widths. Coordinates are GUI-scaled units.
        int x = Math.max(4, width - 70), y = Math.max(4, height - 99);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            graphics.setColor(1, empty ? .15F : 1, empty ? .15F : 1, 1);
            graphics.blit(definition.hudIcon(), 0, 0, 36, 22, 0, 0, 1, 1, 1, 1);
            graphics.setColor(1, 1, 1, 1);
            graphics.pose().pushPose();
            graphics.pose().translate(2, 27, 0);
            graphics.pose().scale(1.25F, 1.25F, 1);
            graphics.drawString(mc.font, Integer.toString(current), 0, 0, empty || flash ? 0xFF3333 : 0xFFFFFF, true);
            graphics.pose().popPose();
            graphics.drawString(mc.font, "| " + reserve, 25, 29, 0xFFFFFF, true);
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
