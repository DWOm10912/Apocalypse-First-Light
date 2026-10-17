package com.antaurora.apofirstlight.client.vehicle;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflGauge;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.physics.Drivetrain;
import com.antaurora.apofirstlight.vehicle.physics.VehicleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The driving dashboard (design doc 8.12, concepts design/ui/vehicle_dashboard_v1): bottom right the rpm, speed, gear and
 * fuel, at the top the telltale strip (DashTelltales). Two styles, the player picks one (client config vehicle_hud.style,
 * Mods > Config):
 * - RING: a segmented round tachometer (0..5 thousand rpm), the speed in it, the gear in its lower gap, the fuel a
 *   crescent hugging it at the lower left (the survival HUD's crescent make), the strip a rounded pill;
 * - BAND: the speed large, over it a rising slanted rpm ramp, the gear in a box at its left, the fuel a row of cells,
 *   on a panel fading out to the left; the strip fades at both ends.
 * Lit rpm cells go whole (LED style; red from 4300, amber near the cut when it is time to shift up). Speed in mph or
 * km/h (vehicle_hud.speed_unit). Only in the driver's seat. The rpm cells are one cached mask each style cut by a few
 * triangles a frame, the rest cached sprites redrawn only when they change.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VehicleHud {
    private static final int TEXT = AflUiStyle.TEXT, DIM = AflUiStyle.TEXT_DIM, RED = DashTelltales.RED, FUEL = 0xD8A94A;
    /** The tachometer's scale, its red line and where the shift-up hint comes on (rpm; the T15 cuts at 4300). */
    private static final double RPM_MAX = 5000, RPM_RED = 4300, SHIFT_AT = 3800;
    /** Fuel below this share: red, and the fuel lamp. Coolant below / above these (C): the cold / hot lamp. */
    private static final double FUEL_LOW = 0.12, COLD = 50, HOT = 110;
    private static final double MPH = 0.621371;

    /** The T15's lamps (4H: it has a part-time transfer case; glow plugs: diesel). */
    private static final Set<DashTelltales.Telltale> T15 = lamps(VehicleSpec.T15);

    // ring: centre from the bottom right corner, radius and band, from 235 degrees over 250, 20 cells
    private static final double RING_X = 45, RING_Y = 48, R = 34, B = 4.2, A0 = 235, SWEEP = 250, CELL_GAP = 0.9;
    private static final int RING_CELLS = 20, RING_RED = red(RING_CELLS);
    /** The ring's cells in white, cut into the lit part each frame; centred in a square of side 2 (R + 1). */
    private static final DashDraw.Mask RING_MASK = new DashDraw.Mask("ring_cells", 2 * (R + 1), 2 * (R + 1),
            (x, y, px) -> ringCell(x - R - 1, y - R - 1, px));

    // band: the ramp's left end from the right edge, its width, its baseline from the bottom, 25 cells rising, slanted
    private static final double RAMP_X = 153, RAMP_W = 141, RAMP_Y = 58, RAMP_LOW = 2.6, RAMP_HIGH = 11.0, SLANT = 0.2, RAMP_GAP = 1.3;
    private static final int RAMP_CELLS = 25, RAMP_RED = red(RAMP_CELLS);
    /** The ramp's cells; origin 1 left of its first cell and 1 above its tallest. */
    private static final DashDraw.Mask RAMP_MASK = new DashDraw.Mask("ramp_cells", RAMP_W + SLANT * RAMP_HIGH + 2, RAMP_HIGH + 2,
            (x, y, px) -> rampCell(x - 1, RAMP_HIGH + 1 - y, px));

    private VehicleHud() {
    }

    /** The local player sits in a vehicle (any seat): the hotbar group hides (client/AflHudEvents). */
    public static boolean riding() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.getCameraEntity() == mc.player && mc.player.getRootVehicle() instanceof PickupTruckEntity;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "vehicle_readings", OVERLAY);
    }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, width, height) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof PickupTruckEntity truck) || truck.getControllingPassenger() != mc.player)
            return;
        Reading r = new Reading(truck, mc.player, partialTick);
        boolean ring = FirstPersonBodyConfig.dashStyle() == FirstPersonBodyConfig.DashStyle.RING;
        if (ring) ring(graphics, mc.font, r, width, height);
        else band(graphics, mc.font, r, width, height);
        DashTelltales.strip(graphics, width, T15, r.lamps, true, truck.turnLit(false, partialTick), truck.turnLit(true, partialTick), ring);
    };

    /** What the dashboard shows this frame. */
    private static final class Reading {
        final double rpm, fuel;
        final int speed, gear;
        final boolean shifting, shiftUp, mph;
        final int[] lamps = new int[DashTelltales.Telltale.values().length];

        Reading(PickupTruckEntity truck, LocalPlayer driver, float pt) {
            rpm = truck.tachometer(pt);
            // the tank as it is (the dash needle creeps and needs the key on); steps of half a percent
            fuel = Math.round(truck.fuel() / (double) PickupTruckEntity.TANK * 200) / 200.0;
            mph = FirstPersonBodyConfig.speedUnit() == FirstPersonBodyConfig.SpeedUnit.MPH;
            speed = (int) Math.round(truck.speedometer(pt) * (mph ? MPH : 1));
            gear = truck.shiftTarget();
            shifting = truck.shiftProgress() < 1;
            shiftUp = rpm >= SHIFT_AT && gear >= 1 && gear < VehicleSpec.T15.gears.length && !shifting;
            Arrays.fill(lamps, -1);
            boolean running = truck.engineRunning(), key = running || truck.ignitionPhase() != Drivetrain.KEY_OFF;
            set(DashTelltales.Telltale.LOW, truck.headlights() && !truck.highBeam(), DashTelltales.GREEN);
            set(DashTelltales.Telltale.HIGH, truck.highBeam(), DashTelltales.BLUE);
            double coolant = truck.coolantGauge(pt);
            set(DashTelltales.Telltale.TEMP, running && (coolant < COLD || coolant > HOT), coolant < COLD ? DashTelltales.COLD : RED);
            // the bulb check: the engine lamp with the key on, oil and charging until it runs
            set(DashTelltales.Telltale.ENGINE, truck.ignitionPhase() != Drivetrain.KEY_OFF, DashTelltales.AMBER);
            set(DashTelltales.Telltale.GLOW, truck.ignitionPhase() == Drivetrain.PREHEAT, DashTelltales.AMBER);
            set(DashTelltales.Telltale.OIL, key && !running, RED);
            set(DashTelltales.Telltale.BATTERY, key && !running, RED);
            set(DashTelltales.Telltale.BRAKE, driver.input.jumping, RED);
            set(DashTelltales.Telltale.DOOR, truck.doorOpen(false) || truck.doorOpen(true), RED);
            set(DashTelltales.Telltale.FOUR, truck.transferMode() == 1, DashTelltales.GREEN);
            set(DashTelltales.Telltale.FOUR_LOW, truck.transferMode() == 2, DashTelltales.GREEN);
            set(DashTelltales.Telltale.FUEL, key && fuel < FUEL_LOW, DashTelltales.AMBER);
        }

        private void set(DashTelltales.Telltale t, boolean on, int rgb) {
            if (on) lamps[t.ordinal()] = rgb;
        }

        /** Lit rpm cells of n (whole cells). */
        int cells(int n) {
            return (int) Math.max(0, Math.min(n, Math.floor(rpm / RPM_MAX * n + 0.5)));
        }

        String gearName() {
            return gear < 0 ? "R" : gear == 0 ? "N" : String.valueOf(gear);
        }
    }

    private static Set<DashTelltales.Telltale> lamps(VehicleSpec spec) {
        Set<DashTelltales.Telltale> s = EnumSet.allOf(DashTelltales.Telltale.class);
        if (spec.fourWheelSpeed <= 0) s.remove(DashTelltales.Telltale.FOUR);
        if (spec.fourWheelSpeed <= 0 || spec.lowRange <= 1) s.remove(DashTelltales.Telltale.FOUR_LOW);
        return s;
    }

    /** The first red cell of n: the cell whose middle is past the red line. */
    private static int red(int n) {
        int i = 0;
        while ((i + 0.5) / n * RPM_MAX < RPM_RED) i++;
        return i;
    }

    // ---------------------------------------------------------------- RING

    private static void ring(GuiGraphics g, Font font, Reading r, int width, int height) {
        double cx = DashDraw.snap(width - RING_X), cy = DashDraw.snap(height - RING_Y), seg = SWEEP / RING_CELLS;
        double red = (double) RING_RED / RING_CELLS;
        AflGauge.draw(g, "dash_ring", cx, cy, R + 1.5, List.of(
                AflGauge.Band.disc(R - B - 0.4, AflUiStyle.BACK, 0.5),
                new AflGauge.Band(R + 0.6, B + 1.0, A0 - 3, SWEEP + 6, 0, 0, 0, 1, AflUiStyle.BACK, 0.35, AflUiStyle.BACK, 0),
                new AflGauge.Band(R, B, A0, SWEEP, RING_CELLS, CELL_GAP, 0, red, AflUiStyle.LINE, 0.13, AflUiStyle.BACK, 0),
                new AflGauge.Band(R, B, A0, SWEEP, RING_CELLS, CELL_GAP, red, 1, RED, 0.32, AflUiStyle.BACK, 0),
                AflGauge.Band.line(R + 0.5, 0.33, A0 - 2, SWEEP + 4, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA),
                AflGauge.Band.line(R - B - 0.25, 0.3, 0, 360, AflUiStyle.LINE, 0.2)), null);
        // lit cells: cut at the gaps' middles
        int lit = r.cells(RING_CELLS), white = Math.min(lit, RING_RED);
        double m = R + 1;
        RING_MASK.draw(g, cx - m, cy - m, DashDraw.sector(m, m, m, A0, A0 + white * seg), r.shiftUp ? AflUiStyle.ACCENT : TEXT, 1);
        if (lit > RING_RED) RING_MASK.draw(g, cx - m, cy - m, DashDraw.sector(m, m, m, A0 + RING_RED * seg, A0 + lit * seg), RED, 1);
        for (int k = 0; k <= 5; k++) {
            double d = Math.toRadians(A0 + SWEEP * k / 5), rr = R - B - 4.6;
            DashDraw.text(g, font, String.valueOf(k), cx + Math.sin(d) * rr, cy - Math.cos(d) * rr - 1.8, 0.5F, k >= 5 ? RED : DIM, 0.9F, 0, false);
        }
        DashDraw.text(g, font, String.valueOf(r.speed), cx, cy - 10, 2.0F, TEXT, 1, 0, true);
        DashDraw.text(g, font, r.mph ? "MPH" : "KM/H", cx, cy + 7.2, 0.45F, DIM, 1, 0, false);
        // gear in the ring's lower gap; the gear it is going to while shifting, dimmed
        AflUiShapes.box(g, (float) (cx - 7), (float) (cy + R - 10), 14, 14, 3, AflUiStyle.BACK, 0.72F, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA,
                AflUiStyle.SHADOW_ALPHA, 0);
        DashDraw.text(g, font, r.gearName(), cx, cy + R - 7.6, 1.2F, r.shifting ? DIM : TEXT, 1, 0, true);
        if (r.shiftUp) DashTelltales.icon(g, "shift_up_ring", DashTelltales.SHIFT_UP, cx + 13, cy + R - 4.5, 6, AflUiStyle.ACCENT, 1);
        // fuel: a crescent at the lower left, filled from its lower tip
        double fa = Math.toRadians(236), fd = R + 8.2, fx = cx + Math.sin(fa) * fd, fy = cy - Math.cos(fa) * fd;
        double cr = 7.4, cb = 1.9, f0 = 236 - 140, fs = 280;
        int fc = r.fuel < FUEL_LOW ? RED : FUEL;
        AflGauge.draw(g, "dash_ring_fuel", fx, fy, cr + 1, List.of(
                new AflGauge.Band(cr, cb, f0, fs, 0, 0, 1 - r.fuel, 1, fc, 1, AflUiStyle.BACK, 0.55),
                AflGauge.Band.line(cr + 0.3, 0.3, f0, fs, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA),
                AflGauge.Band.line(cr - cb, 0.3, f0, fs, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA),
                AflGauge.Band.disc(3.6, AflUiStyle.BACK, 0.5)), new AflGauge.Glyph(DashTelltales.Telltale.FUEL.glyph, 5.2, fc, 1));
    }

    /** Coverage of the ring's cells at (x, y) GUI px from its centre (the AflGauge segment rule, so they cover its ghosts). */
    private static double ringCell(double x, double y, double px) {
        double r = Math.hypot(x, y), deg = Math.toDegrees(Math.atan2(x, -y));
        if (deg < 0) deg += 360;
        double cover = clamp(0.5 + (B / 2 - Math.abs(r - (R - B / 2))) / px);
        if (cover <= 0) return 0;
        double perDeg = Math.max(r, 1e-3) * Math.PI / 180 / px, rel = ((deg - A0) % 360 + 360) % 360;
        cover *= clamp(0.5 + (rel <= SWEEP ? Math.min(rel, SWEEP - rel) : -Math.min(rel - SWEEP, 360 - rel)) * perDeg);
        double seg = SWEEP / RING_CELLS, local = Math.min(rel, SWEEP) % seg, gap = Math.toDegrees(CELL_GAP / Math.max(r, 1e-3));
        return cover * clamp(0.5 + Math.min(local - gap / 2, seg - gap / 2 - local) * perDeg);
    }

    // ---------------------------------------------------------------- BAND

    private static void band(GuiGraphics g, Font font, Reading r, int width, int height) {
        double px = 1.0 / DashDraw.scale(), x0 = width - 210, y0 = height - 78, y1 = height - 4;
        DashDraw.hGradient(g, x0, y0, width, y1, AflUiStyle.BACK, new double[]{0, 0.42, 1}, new float[]{0, 0.5F, 0.66F});
        DashDraw.hGradient(g, x0, y0, width, y0 + px, AflUiStyle.LINE, new double[]{0, 1}, new float[]{0, AflUiStyle.LINE_ALPHA});
        DashDraw.hGradient(g, x0, y1 - px, width, y1, AflUiStyle.LINE, new double[]{0, 1}, new float[]{0, AflUiStyle.LINE_ALPHA});
        // the rpm ramp: ghost cells (red past the line), the lit ones over them, cut along the slanted gaps
        double rx = DashDraw.snap(width - RAMP_X), base = DashDraw.snap(height - RAMP_Y), ox = rx - 1, oy = base - RAMP_HIGH - 1;
        double cw = RAMP_W / RAMP_CELLS, top = RAMP_HIGH + 1;
        double cutRed = 1 + RAMP_RED * cw - RAMP_GAP / 2, end = 1 + RAMP_W + SLANT * RAMP_HIGH + 1;
        RAMP_MASK.draw(g, ox, oy, slantCut(0, cutRed, top), AflUiStyle.LINE, 0.13F);
        RAMP_MASK.draw(g, ox, oy, slantCut(cutRed, end, top), RED, 0.3F);
        int lit = r.cells(RAMP_CELLS), white = Math.min(lit, RAMP_RED);
        if (white > 0) RAMP_MASK.draw(g, ox, oy, slantCut(0, 1 + white * cw - RAMP_GAP / 2, top), r.shiftUp ? AflUiStyle.ACCENT : TEXT, 1);
        if (lit > RAMP_RED) RAMP_MASK.draw(g, ox, oy, slantCut(cutRed, 1 + lit * cw - RAMP_GAP / 2, top), RED, 1);
        for (int k = 1; k <= 5; k++)
            DashDraw.text(g, font, String.valueOf(k), rx + RAMP_W * k / 5 - 1.2, base + 2.3, 0.42F, k == 5 ? RED : DIM, 0.85F, 0, false);
        // gear box, the shift-up arrow over it
        double gx = width - 153, gy = height - 46;
        AflUiShapes.box(g, (float) gx, (float) gy, 18, 19, 3, AflUiStyle.BACK, 0.7F, AflUiStyle.LINE, AflUiStyle.LINE_ALPHA, AflUiStyle.SHADOW_ALPHA, 0);
        DashDraw.text(g, font, r.gearName(), gx + 9, gy + 3.9, 1.5F, r.shifting ? DIM : TEXT, 1, 0, true);
        if (r.shiftUp) DashTelltales.icon(g, "shift_up_band", DashTelltales.SHIFT_UP, gx + 9, gy - 4.2, 7, AflUiStyle.ACCENT, 1);
        // speed
        DashDraw.text(g, font, String.valueOf(r.speed), width - 28, height - 47.4, 3.2F, TEXT, 1, 1, true);
        DashDraw.text(g, font, r.mph ? "MPH" : "KM/H", width - 13, height - 28.8, 0.54F, DIM, 1, 1, false);
        // fuel: 12 cells E..F
        int fc = r.fuel < FUEL_LOW ? RED : FUEL;
        DashTelltales.icon(g, "fuel_band", DashTelltales.Telltale.FUEL.glyph, width - 148, height - 15.5, 5.6, fc, 1);
        double fx0 = width - 140, fx1 = width - 12, fw = (fx1 - fx0) / 12;
        for (int i = 0; i < 12; i++) {
            double f = clamp(r.fuel * 12 - i), x = fx0 + i * fw;
            AflUiShapes.fill(g, (float) x, (float) (height - 16.7), (float) (fw - 0.9), 2.4F, 0.6F, AflUiStyle.LINE, 0.13F);
            if (f > 0) AflUiShapes.fill(g, (float) x, (float) (height - 16.7), (float) ((fw - 0.9) * f), 2.4F, 0.6F, fc, 1);
        }
        DashDraw.text(g, font, "E", fx0 - 0.2, height - 11.7, 0.38F, DIM, 0.8F, -1, false);
        DashDraw.text(g, font, "F", fx1, height - 11.7, 0.38F, DIM, 0.8F, 1, false);
    }

    /**
     * Triangles (mask px) of the ramp between two slanted cuts: a cut at x (mask px, along the baseline) runs up and right
     * at the cells' slant, so it stays in the gap between two cells.
     */
    private static double[] slantCut(double from, double to, double top) {
        if (to <= from) return new double[0];
        // the baseline is `top` below the mask's top edge and 1 above its bottom edge
        double bottom = top + 1, b0 = from - SLANT, t0 = from + SLANT * top, b1 = to - SLANT, t1 = to + SLANT * top;
        if (from <= 0) b0 = t0 = 0;
        return DashDraw.quad(b0, bottom, t0, 0, t1, 0, b1, bottom);
    }

    /** Coverage of the ramp's cells at (x along the baseline from the first cell's foot, h up from the baseline), GUI px. */
    private static double rampCell(double x, double h, double px) {
        double u = x - SLANT * h, cw = RAMP_W / RAMP_CELLS, w = cw - RAMP_GAP;
        int i = (int) Math.floor(u / cw);
        if (i < 0 || i >= RAMP_CELLS) return 0;
        double local = u - i * cw, height = RAMP_LOW + (RAMP_HIGH - RAMP_LOW) * i / (RAMP_CELLS - 1);
        double across = Math.min(local, w - local) / Math.sqrt(1 + SLANT * SLANT), up = Math.min(h, height - h);
        return clamp(0.5 + Math.min(across, up) / px);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
