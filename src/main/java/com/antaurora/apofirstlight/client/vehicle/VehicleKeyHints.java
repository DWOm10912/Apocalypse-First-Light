package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflKeyHint;
import com.antaurora.apofirstlight.client.ui.AflKeyHintPanel;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.physics.Drivetrain;
import com.antaurora.apofirstlight.vehicle.physics.VehicleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * The driving keys in the key hint panel, top right (user 2026-10-17; design doc 8.12): in the driver's seat, one row
 * per vehicle key as bound now (AflKeyHint shows the player's own binding and lights up on a press). The start key only
 * while the key is off, get out only while slow enough (PickupTruckEntity#GET_OUT_SPEED), 2H / 4H only on a vehicle with a
 * transfer case. Movement keys (throttle, brake, steering) are left out: they are the walking keys. The hints key (Caps Lock,
 * VehicleDriving.HINTS) fades the panel out and back in (user 2026-10-18), remembered in the client config; its own row
 * comes last so a player finds it.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VehicleKeyHints {
    private static final String LANG = "hud.apocalypse_firstlight.drive.";
    private static final AflKeyHintPanel PANEL = new AflKeyHintPanel();
    /** The panel's size (user 2026-10-17: at full size ten rows filled the top right while driving). */
    private static final float SCALE = 0.62F;

    private VehicleKeyHints() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.POTION_ICONS.id(), "vehicle_keys", OVERLAY);
    }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        List<AflKeyHintPanel.Row> rows = new ArrayList<>();
        boolean driving = mc.player != null && !mc.options.hideGui && mc.getCameraEntity() == mc.player
                && mc.player.getVehicle() instanceof PickupTruckEntity truck && truck.getControllingPassenger() == mc.player;
        boolean shown = driving && com.antaurora.apofirstlight.client.FirstPersonBodyConfig.keyHints();
        if (shown) rows((PickupTruckEntity) mc.player.getVehicle(), mc, rows);
        PANEL.render(graphics, width, rows, shown, SCALE);
    };

    private static void rows(PickupTruckEntity truck, Minecraft mc, List<AflKeyHintPanel.Row> rows) {
        if (!truck.engineRunning() && truck.ignitionPhase() == Drivetrain.KEY_OFF)
            rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.IGNITION), t("start")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.SHIFT_UP), t("shift_up")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.SHIFT_DOWN), t("shift_down")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(mc.options.keyJump), t("handbrake")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.LIGHTS), t("lights")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.SIGNAL_LEFT), AflKeyHint.of(VehicleDriving.SIGNAL_RIGHT), t("signals")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.HAZARD), t("hazard")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.WIPERS), t("wipers")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.CLIMATE), t("climate")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.HORN), t("horn")));
        if (VehicleSpec.T15.fourWheelSpeed > 0) rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.TRANSFER), t("transfer")));
        if (truck.speed() <= PickupTruckEntity.GET_OUT_SPEED) rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(mc.options.keyShift), t("get_out")));
        rows.add(new AflKeyHintPanel.Row(AflKeyHint.of(VehicleDriving.HINTS), t("hide_hints")));
    }

    private static Component t(String key) { return Component.translatable(LANG + key); }
}
