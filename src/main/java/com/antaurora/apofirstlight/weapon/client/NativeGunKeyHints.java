package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflKeyHintPanel;
import com.antaurora.apofirstlight.client.vehicle.VehicleDriving;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import com.antaurora.apofirstlight.weapon.NativeMagazines;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The gun keys in the key hint panel, top right, as the driving keys show (user 2026-10-21, client/vehicle/VehicleKeyHints):
 * with a gun in the hand on foot, one row per gun key as bound now. The reload key by what it would do now
 * (docs/native_guns/magazine_system_v1.md 3): tapped, take the fitted magazine out, or put one in when none is fitted;
 * held, swap in another loaded magazine from the rig or the pockets (only while there is one); a loaded one only in the
 * backpack is a status line. Fire mode only on a gun with more than one. Fire and aim are left out (the mouse buttons, as
 * the vehicle leaves out the walking keys). The hints key (Caps Lock, VehicleDriving.HINTS) hides and shows it as it does
 * the driving keys (the same client config); hidden while the field attachment view shows its own keys.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeGunKeyHints {
    private static final AflKeyHintPanel PANEL = new AflKeyHintPanel();
    /** As VehicleKeyHints#SCALE. */
    private static final float SCALE = 0.62F;

    private NativeGunKeyHints() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.POTION_ICONS.id(), "gun_keys", OVERLAY);
    }

    /** A gun is in the hand on foot, where the panel would show (VehicleDriving: the hints key works then too). */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && !mc.player.isSpectator() && mc.player.isAlive() && mc.player.getVehicle() == null
                && mc.getCameraEntity() == mc.player && mc.player.getMainHandItem().getItem() instanceof NativeGunItem
                && !FieldAttachmentViewState.isActive();
    }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        List<AflKeyHintPanel.Row> rows = new ArrayList<>();
        boolean shown = !mc.options.hideGui && active() && com.antaurora.apofirstlight.client.FirstPersonBodyConfig.keyHints();
        if (shown) rows(mc, mc.player.getMainHandItem(), rows);
        PANEL.render(graphics, width, rows, shown, SCALE);
    };

    private static void rows(Minecraft mc, ItemStack stack, List<AflKeyHintPanel.Row> rows) {
        var definition = ((NativeGunItem) stack.getItem()).definition();
        AflKeyHint reload = AflKeyHint.of(NativeGunInput.reloadKey());
        if (NativeMagazines.system(definition)) {
            boolean fitted = NativeMagazines.ejectable(stack, definition), spare = NativeMagazines.canReload(mc.player, definition);
            if (fitted) rows.add(new AflKeyHintPanel.Row(reload, gun("unload_magazine")));
            if (spare) rows.add(new AflKeyHintPanel.Row(reload, gun(fitted ? "swap_magazine" : "insert_magazine")));
            else if (NativeMagazines.inBackpack(mc.player, definition)) rows.add(new AflKeyHintPanel.Row(null, null, gun("magazine_in_backpack")));
        } else {
            rows.add(new AflKeyHintPanel.Row(reload, gun("reload")));
        }
        if (definition.fire().modes().size() > 1) rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(NativeGunInput.fireModeKey()), keys("fire_mode")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(NativeGunInput.inspectKey()), keys("inspect")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(NativeGunInput.FIELD_ATTACHMENT), keys("attachments")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.HINTS), Component.translatable("hud.apocalypse_firstlight.drive.hide_hints")));
    }

    private static Component gun(String key) { return Component.translatable("hud.apocalypse_firstlight.gun." + key); }

    private static Component keys(String key) { return Component.translatable("hud.apocalypse_firstlight.gun_keys." + key); }
}
