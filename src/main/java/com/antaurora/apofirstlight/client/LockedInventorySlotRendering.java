package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LockedInventorySlotRendering {
    private LockedInventorySlotRendering() {}

    @SubscribeEvent
    public static void drawLockedSlots(ContainerScreenEvent.Render.Foreground event) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var graphics = event.getGuiGraphics();
        // Foreground already uses container-relative coordinates, below tooltips and dragged items.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 250);
        for (var slot : event.getContainerScreen().getMenu().slots) {
            if (!slot.isActive() || slot.container != player.getInventory()
                    || !PlayerStorageCapacity.isLocked(slot)) continue;
            boolean occupied = slot.hasItem();
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, occupied ? 0x30252A30 : 0x80252A30);
            int line = occupied ? 0x708B939D : 0xA08B939D;
            // One inset 1-pixel / diagonal.
            for (int pixel = 2; pixel < 14; pixel++) {
                graphics.fill(slot.x + pixel, slot.y + 15 - pixel,
                        slot.x + pixel + 1, slot.y + 16 - pixel, line);
            }
        }
        graphics.pose().popPose();
    }

    public static List<Component> tooltip(boolean occupied) {
        String state = occupied ? "overflow" : "locked";
        return List.of(Component.translatable("tooltip.apocalypse_firstlight.inventory." + state)
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("tooltip.apocalypse_firstlight.inventory." + state + ".hint")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }
}
