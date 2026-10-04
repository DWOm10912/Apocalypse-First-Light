package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflMobEffects;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Survival HUD V1 (2026-10-03): the health ring left of the temperature dial and the hunger ring right of it
 * (client/SurvivalHudLayout), the armour arc over the top of health and the air arc outside the stamina crescent.
 * Vanilla hearts, food, armour, air and mount hearts are hidden (AflHudEvents). Both rings have a dark translucent face
 * and a faint light outline.
 * <ul>
 * <li>Health: one segment per heart (2 HP), filled clockwise from the top; the heart and the health number (always shown,
 * rounded up) on the face. Red, poisoned green, withered grey, frozen ice blue; absorption continues the fill in gold
 * (the ring gains segments for it, as vanilla adds golden hearts); health just lost lingers as a pale ghost for 0.45 s
 * and then drains; at 4 HP or less (health + absorption, vanilla's shaking hearts) the ring pulses. Radiation sickness
 * II / III / IV tints about 30 / 60 / 100 % of the filled segments (the same per-player choice as the former radiation
 * hearts).</li>
 * <li>Armour: a thin arc over the top of the health ring (0..20 points, continuous), faded in while any armour is worn.</li>
 * <li>Hunger: 10 segments (2 food points each), amber, green under the Hunger effect; no number, no saturation. While
 * riding a living mount it shows the mount's health instead (as vanilla swaps the food row for mount hearts).</li>
 * <li>Air: a thin arc of 10 segments outside the stamina crescent, filling from the bottom up, faded in while the eyes are
 * under water or the air is not full; its empty backing pulses red while drowning.</li>
 * </ul>
 * Every value eases (0.15 s; health drops in 0.06 s); arcs fade over 0.25 s. Health and hunger always show in Survival /
 * Adventure.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SurvivalVitalsHud {
    private static final double EASE = 0.15, DROP_EASE = 0.06, GHOST_HOLD = 0.45, GHOST_EASE = 0.3, FADE = 0.25;
    private static final int RED = 0xCC4A42, POISON = 0x8FA83C, WITHER = 0x5E5E5E, FROZEN = 0x9FD8F2, GOLD = 0xE2BE4A,
            GHOST = 0xF2E6DE, FOOD = 0xD39A4C, HUNGER_EFFECT = 0x92A84A, MOUNT = 0xC28A57, ARMOR = 0xB4BCC4,
            AIR = 0x92D4F2, DROWN = 0xBE443A, NUMBER = 0xF0ECE4;
    /** Radiation sickness II, III, IV (the former radiation hearts' colours). */
    private static final int[] RADIATION = {0xA09666, 0x88805A, 0x7A7454};
    /** Armour arc over the health ring (degrees): from 45 deg left of the top over 70 deg, clear of the stamina crescent's
     *  tip and of the wrist thermometer's air arc over the dial. */
    private static final double ARMOR_START = 315, ARMOR_SPAN = 70;
    /** Air arc outside the stamina crescent (degrees): from the bottom-left up to the top-left. */
    private static final double AIR_START = 196, AIR_SPAN = 148;
    /** Health glyph and number inside the ring (GUI px): heart size and how far above the centre, the number's top below it. */
    private static final double HEART_SIZE = 3.8 * SurvivalHudLayout.SCALE, HEART_RISE = 1.75 * SurvivalHudLayout.SCALE,
            NUMBER_TOP = 0.6 * SurvivalHudLayout.SCALE;
    private static double health = Double.NaN, ghost, absorb, food = Double.NaN, mount = Double.NaN, armor, armorAlpha, air = Double.NaN, airAlpha;
    private static double ghostUntil;
    private static long lastNanos;
    private SurvivalVitalsHud() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "survival_vitals", OVERLAY);
    }

    // ---- glyphs (unit box, y down) ----
    /** Heart: two lobes and the point. */
    static final AflRingIcon.Glyph HEART = (x, y) -> Math.min(Math.min(Math.hypot(x - 0.31, y - 0.37) - 0.2, Math.hypot(x - 0.69, y - 0.37) - 0.2),
            AflRingIcon.convex(x, y, 0.12, 0.44, 0.88, 0.44, 0.5, 0.86));
    /** Drumstick: the meat, the bone and its knuckle. */
    static final AflRingIcon.Glyph DRUMSTICK = (x, y) -> Math.min(Math.min(Math.hypot((x - 0.38) / 1.0, (y - 0.38) / 0.86) - 0.27,
            AflRingIcon.segment(x, y, 0.5, 0.5, 0.78, 0.78) - 0.07),
            Math.min(Math.hypot(x - 0.84, y - 0.73) - 0.075, Math.hypot(x - 0.73, y - 0.84) - 0.075));
    /** Horseshoe: an arch with two legs, open at the bottom. */
    static final AflRingIcon.Glyph HORSESHOE = (x, y) -> Math.min(Math.max(Math.abs(Math.hypot(x - 0.5, y - 0.47) - 0.27) - 0.085, y - 0.47),
            Math.min(AflRingIcon.segment(x, y, 0.23, 0.47, 0.26, 0.82) - 0.085, AflRingIcon.segment(x, y, 0.77, 0.47, 0.74, 0.82) - 0.085));

    private static double ease(double dt, double seconds) { return 1 - Math.exp(-dt / seconds); }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        long nanos = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (nanos - lastNanos) / 1e9), now = nanos / 1e9;
        lastNanos = nanos;
        LocalPlayer player = mc.player;
        if (player == null) { health = food = mount = air = Double.NaN; return; }
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || FieldAttachmentViewState.isActive()) return;
        double cx = screenWidth / 2.0, cy = screenHeight - SurvivalHudLayout.CENTRE_Y;
        health(graphics, mc.font, player, cx - SurvivalHudLayout.VITAL_OFFSET_X, cy, dt, now);
        right(graphics, player, cx + SurvivalHudLayout.VITAL_OFFSET_X, cy, dt);
        air(graphics, player, cx - SurvivalHudLayout.CRESCENT_OFFSET_X, cy, dt, now);
    };

    /** A ring's face (dark, translucent) under everything and its outline round the outside. */
    private static void framed(List<AflGauge.Band> bands, double r) {
        bands.add(0, AflGauge.Band.disc(r, AflGauge.BACK, SurvivalHudLayout.FACE_ALPHA));
        bands.add(AflGauge.Band.line(r + SurvivalHudLayout.OUTLINE_WIDTH, SurvivalHudLayout.OUTLINE_WIDTH, 0, 360,
                SurvivalHudLayout.OUTLINE, SurvivalHudLayout.OUTLINE_ALPHA));
    }

    private static void health(GuiGraphics graphics, Font font, LocalPlayer player, double x, double y, double dt, double now) {
        double hp = Math.max(0, player.getHealth()), max = Math.max(1, player.getMaxHealth()), abs = Math.max(0, player.getAbsorptionAmount());
        if (Double.isNaN(health)) { health = ghost = hp; absorb = abs; armor = Math.min(20, player.getArmorValue()) / 20.0; }
        if (hp < health - 1e-3) {   // damage: the ring drops fast, what was lost lingers as a ghost, then drains
            ghost = Math.max(ghost, health);
            ghostUntil = now + GHOST_HOLD;
            health += (hp - health) * ease(dt, DROP_EASE);
        } else health += (hp - health) * ease(dt, EASE);
        if (now > ghostUntil) ghost += (health - ghost) * ease(dt, GHOST_EASE);
        ghost = Math.max(ghost, health);
        absorb += (abs - absorb) * ease(dt, EASE);
        int armorPoints = player.getArmorValue();
        armor += (Math.min(20, armorPoints) / 20.0 - armor) * ease(dt, EASE);
        armorAlpha += ((armorPoints > 0 ? 1 : 0) - armorAlpha) * ease(dt, FADE);

        double cap = Math.max(max, hp + abs);
        int segments = (int)Math.min(30, Math.ceil(cap / 2 - 1e-6));
        int colour = player.hasEffect(MobEffects.POISON) ? POISON : player.hasEffect(MobEffects.WITHER) ? WITHER
                : player.isFullyFrozen() ? FROZEN : RED;
        double glyphAlpha = 1;
        if (hp + abs <= 4) {   // low: the ring pulses (vanilla shakes the hearts)
            double pulse = 0.5 + 0.5 * Math.sin(now * Math.PI * 2 * 1.6);
            colour = mix(colour, 0xFFFFFF, 0.3 * pulse);
            glyphAlpha = 0.7 + 0.3 * pulse;
        }
        double r = SurvivalHudLayout.VITAL_RADIUS, band = SurvivalHudLayout.VITAL_BAND, gap = SurvivalHudLayout.SEGMENT_GAP;
        List<AflGauge.Band> bands = new ArrayList<>();
        bands.add(AflGauge.Band.gauge(r, band, segments, gap, health / cap, colour, 1));
        radiation(bands, player, health, cap, segments);
        if (absorb > 1e-3) bands.add(AflGauge.Band.overlay(r, band, segments, gap, health / cap, (health + absorb) / cap, GOLD, 1));
        if (ghost > health + 1e-3) bands.add(AflGauge.Band.overlay(r, band, segments, gap, health / cap, ghost / cap, GHOST, 0.8));
        framed(bands, r);
        double lane = r + SurvivalHudLayout.LANE_GAP + SurvivalHudLayout.LANE_WIDTH;
        if (armorAlpha > 0.004)
            bands.add(AflGauge.Band.arc(lane, SurvivalHudLayout.LANE_WIDTH, ARMOR_START, ARMOR_SPAN, 0, 0, 0, armor, ARMOR, armorAlpha));
        AflGauge.draw(graphics, "health", x, y, lane + 0.5, bands, new AflGauge.Glyph(HEART, HEART_SIZE, colour, glyphAlpha, -HEART_RISE));
        AflHudText.half(graphics, font, String.valueOf((int)Math.ceil(hp - 1e-4)), x, y + NUMBER_TOP, NUMBER);
    }

    /** Hunger, or the mount's health while riding a living mount. */
    private static void right(GuiGraphics graphics, LocalPlayer player, double x, double y, double dt) {
        double r = SurvivalHudLayout.VITAL_RADIUS, band = SurvivalHudLayout.VITAL_BAND, gap = SurvivalHudLayout.SEGMENT_GAP;
        double glyphSize = (r - band) * 2 * 0.63, reach = r + SurvivalHudLayout.OUTLINE_WIDTH + 0.5;
        List<AflGauge.Band> bands = new ArrayList<>();
        if (player.getVehicle() instanceof LivingEntity ride) {
            double max = Math.max(1, ride.getMaxHealth()), value = Math.max(0, Math.min(1, ride.getHealth() / max));
            mount = Double.isNaN(mount) ? value : mount + (value - mount) * ease(dt, EASE);
            int segments = (int)Math.min(30, Math.ceil(max / 2 - 1e-6));
            bands.add(AflGauge.Band.gauge(r, band, segments, gap, mount, MOUNT, 1));
            framed(bands, r);
            AflGauge.draw(graphics, "hunger", x, y, reach, bands, new AflGauge.Glyph(HORSESHOE, glyphSize, MOUNT, 1));
            return;
        }
        mount = Double.NaN;
        double value = Math.max(0, Math.min(20, player.getFoodData().getFoodLevel())) / 20.0;
        food = Double.isNaN(food) ? value : food + (value - food) * ease(dt, EASE);
        int colour = player.hasEffect(MobEffects.HUNGER) ? HUNGER_EFFECT : FOOD;
        bands.add(AflGauge.Band.gauge(r, band, 10, gap, food, colour, 1));
        framed(bands, r);
        AflGauge.draw(graphics, "hunger", x, y, reach, bands, new AflGauge.Glyph(DRUMSTICK, glyphSize, colour, 1));
    }

    /** Air arc outside the stamina crescent, faded in while it matters. */
    private static void air(GuiGraphics graphics, LocalPlayer player, double x, double y, double dt, double now) {
        int max = Math.max(1, player.getMaxAirSupply()), supply = Math.max(0, player.getAirSupply());
        double value = Math.min(1, supply / (double)max);
        boolean shown = player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < max;
        air = Double.isNaN(air) ? value : air + (value - air) * ease(dt, EASE);
        airAlpha += ((shown ? 1 : 0) - airAlpha) * ease(dt, FADE);
        if (airAlpha < 0.004) return;
        double lane = SurvivalHudLayout.CRESCENT_RADIUS + SurvivalHudLayout.LANE_GAP + SurvivalHudLayout.LANE_WIDTH;
        boolean drowning = player.getAirSupply() <= 0;
        double pulse = drowning ? 0.5 + 0.5 * Math.sin(now * Math.PI * 2 * 1.6) : 0;
        var band = new AflGauge.Band(lane, SurvivalHudLayout.LANE_WIDTH, AIR_START, AIR_SPAN, 10, SurvivalHudLayout.SEGMENT_GAP * 0.65,
                0, air, AIR, airAlpha, drowning ? DROWN : AflGauge.BACK, drowning ? 0.45 + 0.4 * pulse : AflGauge.BACK_ALPHA);
        AflGauge.draw(graphics, "air", x, y, lane + 0.5, List.of(band), null);
    }

    // ---- radiation sickness: tinted health segments ----

    /** Stage II / III / IV: about 30 / 60 / 100 % of the filled segments, chosen per player (stable while health holds). */
    private static void radiation(List<AflGauge.Band> bands, LocalPlayer player, double shownHealth, double cap, int segments) {
        var sickness = player.getEffect(AflMobEffects.RADIATION_SICKNESS.get());
        if (sickness == null) return;
        int stage = sickness.getAmplifier() + 1;
        if (stage < 2) return;
        int filled = (int)Math.ceil(Math.max(0, player.getHealth()) / 2.0);
        int target = switch (stage) {
            case 2 -> (filled * 3 + 9) / 10;
            case 3 -> (filled * 6 + 9) / 10;
            default -> filled;
        };
        if (target <= 0) return;
        int colour = RADIATION[Math.min(stage, 4) - 2];
        UUID id = player.getUUID();
        double r = SurvivalHudLayout.VITAL_RADIUS, band = SurvivalHudLayout.VITAL_BAND, gap = SurvivalHudLayout.SEGMENT_GAP;
        for (int k = 0; k < filled; k++) {
            if (!selected(id, k, filled, target)) continue;
            double from = 2.0 * k / cap, to = Math.min(shownHealth, 2.0 * (k + 1)) / cap;
            if (to > from) bands.add(AflGauge.Band.overlay(r, band, segments, gap, from, to, colour, 1));
        }
    }

    private static boolean selected(UUID playerId, int index, int filled, int target) {
        if (target >= filled) return true;
        long score = score(playerId, index);
        int rank = 0;
        for (int other = 0; other < filled; other++) {
            if (other == index) continue;
            int c = Long.compareUnsigned(score(playerId, other), score);
            if (c < 0 || c == 0 && other < index) rank++;
        }
        return rank < target;
    }

    private static long score(UUID playerId, int index) {
        long v = playerId.getMostSignificantBits() ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17)
                ^ 0x9E3779B97F4A7C15L * (index + 1L);
        v = (v ^ (v >>> 30)) * 0xBF58476D1CE4E5B9L;
        v = (v ^ (v >>> 27)) * 0x94D049BB133111EBL;
        return v ^ (v >>> 31);
    }

    private static int mix(int a, int b, double t) {
        int r = (int)Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int)Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int)Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }
}
