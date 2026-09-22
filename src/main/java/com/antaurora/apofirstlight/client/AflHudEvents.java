package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AflHudEvents {
    private AflHudEvents() {}
    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())) event.setCanceled(true);
        if (!FieldAttachmentViewState.isActive()) return;

        var overlayId = event.getOverlay().id();
        if (overlayId.equals(VanillaGuiOverlay.CROSSHAIR.id())
                || overlayId.equals(VanillaGuiOverlay.HOTBAR.id())
                || overlayId.equals(VanillaGuiOverlay.ITEM_NAME.id())
                || overlayId.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                || overlayId.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
                || overlayId.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || overlayId.equals(VanillaGuiOverlay.AIR_LEVEL.id())
                || overlayId.equals(VanillaGuiOverlay.MOUNT_HEALTH.id())
                || overlayId.equals(VanillaGuiOverlay.JUMP_BAR.id())
                || overlayId.equals(VanillaGuiOverlay.POTION_ICONS.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) return;
        if (!(net.minecraft.client.Minecraft.getInstance().gui instanceof ForgeGui gui)) return;

        RadiationHeartOverlay.render(gui, event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }
}
