package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Vanilla HUD overlays the mod replaces or moves:
 * - the XP bar (the load bar sits in its slot, weight/ClientWeightHud);
 * - hearts, armour, food, air and mount hearts (Survival HUD V1: client/SurvivalVitalsHud and the survival cluster);
 * - the selected item's name and the action bar are lifted SurvivalHudLayout.TEXT_LIFT px while the cluster shows, so
 *   they sit above it instead of over the temperature dial;
 * - the hotbar, outside spectator mode (client/ui/AflHotbarOverlay draws it in the overlay style);
 * - while the field attachment view is open, the crosshair, hotbar and the rest of the bottom HUD.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AflHudEvents {
    private static final Set<ResourceLocation> REPLACED = Set.of(VanillaGuiOverlay.PLAYER_HEALTH.id(), VanillaGuiOverlay.ARMOR_LEVEL.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id(), VanillaGuiOverlay.AIR_LEVEL.id(), VanillaGuiOverlay.MOUNT_HEALTH.id());
    private static final Set<ResourceLocation> LIFTED = Set.of(VanillaGuiOverlay.ITEM_NAME.id(), VanillaGuiOverlay.RECORD_OVERLAY.id());
    private static final Set<ResourceLocation> FIELD_VIEW_HIDDEN = Set.of(VanillaGuiOverlay.CROSSHAIR.id(), VanillaGuiOverlay.HOTBAR.id(),
            VanillaGuiOverlay.ITEM_NAME.id(), VanillaGuiOverlay.JUMP_BAR.id(), VanillaGuiOverlay.POTION_ICONS.id(),
            com.antaurora.apofirstlight.client.ui.AflHotbarOverlay.ID);
    /** Overlays whose pose this handler pushed in Pre (popped in Post). */
    private static final Set<ResourceLocation> PUSHED = new HashSet<>();

    private AflHudEvents() {}

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) || REPLACED.contains(id)
                || id.equals(VanillaGuiOverlay.HOTBAR.id()) && com.antaurora.apofirstlight.client.ui.AflHotbarOverlay.replaces()
                || FieldAttachmentViewState.isActive() && FIELD_VIEW_HIDDEN.contains(id)) {
            event.setCanceled(true);
        }
    }

    /** Last, and only for overlays nobody cancelled: a cancelled overlay gets no Post, which would leave the pose pushed. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOverlayPreLift(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (LIFTED.contains(id) && clusterShown()) {
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(0, -SurvivalHudLayout.TEXT_LIFT, 0);
            PUSHED.add(id);
        }
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (PUSHED.remove(event.getOverlay().id())) event.getGuiGraphics().pose().popPose();
    }

    /** The survival cluster draws this frame (Survival / Adventure, HUD on, no field attachment view). */
    private static boolean clusterShown() {
        var mc = Minecraft.getInstance();
        return !mc.options.hideGui && mc.gameMode != null && mc.gameMode.canHurtPlayer() && mc.getCameraEntity() instanceof Player
                && !FieldAttachmentViewState.isActive();
    }
}
