package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * Vanilla HUD overlays the mod replaces or moves:
 * - the XP bar;
 * - hearts, armour, food, air and mount hearts (HUD V2: the body module at the bottom left, client/SurvivalVitalsHud);
 * - the hotbar, outside spectator mode (client/ui/AflHotbarOverlay draws it in the overlay style);
 * - while the field attachment view is open, the crosshair, hotbar and the rest of the bottom HUD;
 * - while riding a vehicle (client/vehicle/VehicleHud#riding; user 2026-10-16: in the cab they are in the way), the
 *   hotbar with its off-hand tile, the item name, the weapon list and the gun HUD. The body module stays (its load ring
 *   hides itself).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AflHudEvents {
    private static final Set<ResourceLocation> REPLACED = Set.of(VanillaGuiOverlay.PLAYER_HEALTH.id(), VanillaGuiOverlay.ARMOR_LEVEL.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id(), VanillaGuiOverlay.AIR_LEVEL.id(), VanillaGuiOverlay.MOUNT_HEALTH.id());
    private static final Set<ResourceLocation> FIELD_VIEW_HIDDEN = Set.of(VanillaGuiOverlay.CROSSHAIR.id(), VanillaGuiOverlay.HOTBAR.id(),
            VanillaGuiOverlay.ITEM_NAME.id(), VanillaGuiOverlay.JUMP_BAR.id(), VanillaGuiOverlay.POTION_ICONS.id(),
            com.antaurora.apofirstlight.client.ui.AflHotbarOverlay.ID, com.antaurora.apofirstlight.client.inventory.LoadoutHud.WEAPON_LIST);
    private static final Set<ResourceLocation> RIDING_HIDDEN = Set.of(VanillaGuiOverlay.HOTBAR.id(), VanillaGuiOverlay.ITEM_NAME.id(),
            com.antaurora.apofirstlight.client.ui.AflHotbarOverlay.ID, com.antaurora.apofirstlight.client.inventory.LoadoutHud.WEAPON_LIST,
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "native_gun"));

    private AflHudEvents() {}

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id()) || REPLACED.contains(id)
                || id.equals(VanillaGuiOverlay.HOTBAR.id()) && com.antaurora.apofirstlight.client.ui.AflHotbarOverlay.replaces()
                || FieldAttachmentViewState.isActive() && FIELD_VIEW_HIDDEN.contains(id)
                || RIDING_HIDDEN.contains(id) && com.antaurora.apofirstlight.client.vehicle.VehicleHud.riding()) {
            event.setCanceled(true);
        }
    }
}
