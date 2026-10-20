package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflMobEffects;
import com.antaurora.apofirstlight.stamina.ClientStamina;
import com.antaurora.apofirstlight.thirst.ClientThirst;
import com.antaurora.apofirstlight.thirst.ClientThirstHud;
import com.antaurora.apofirstlight.thirst.ThirstConfig;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import com.antaurora.apofirstlight.weight.ClientWeightState;
import com.antaurora.apofirstlight.weight.EncumbranceState;
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
import java.util.Locale;
import java.util.UUID;

/**
 * HUD V2 body module (2026-10-16, client/SurvivalHudLayout, docs/ui/hud_v2_research.md 5), bottom left; the temperature
 * dial in its middle is temperature/ClientTemperatureHud, the status badges above it client/StatusBadgeHud. Vanilla
 * hearts, food, armour, air, mount hearts and the experience bar are hidden (AflHudEvents).
 * <ul>
 * <li>Health: the left arc, one segment per 10 HP at 100 HP (health V1a; 2 HP below 50), filled from the bottom up. Red, poisoned green, withered grey,
 * frozen ice blue; absorption continues the fill in gold (the arc gains segments for it); health just lost lingers as a
 * pale ghost for 0.45 s and then drains; at 4 HP or less the arc pulses, glows red and the number turns red. Radiation
 * sickness II / III / IV tints about 30 / 60 / 100 % of the filled segments (a stable per-player choice).</li>
 * <li>Armour: a thin lane outside the health arc (0..20 points), faded in while any armour is worn.</li>
 * <li>Stamina: the right arc, filled from the bottom; muted light, amber below 30, red while winded.</li>
 * <li>Air: a lane of 10 segments outside the stamina arc, filling from the bottom, faded in while the eyes are under water
 * or the air is not full; its empty backing pulses red while drowning.</li>
 * <li>The heart and the health number (rounded up) in the bottom gap.</li>
 * <li>Needs rings round the upper right: hunger (10 food points; the mount's health with a horseshoe while riding a
 * living mount), thirst and the load. Small and quiet while fine; grown, opaque and with a number once they matter
 * (hunger at 6 food points or less, thirst below thirsty, any load tier above light, or for 3 s after the load changed);
 * hunger and thirst turn red at their worst stage (3 food points, dehydrated). The load ring follows the old load bar:
 * full at the severe ratio, the tier's colour, the kg number; it hides while riding a vehicle.</li>
 * </ul>
 * Every value eases (0.15 s; health drops in 0.06 s); lanes fade over 0.25 s; rings grow over 0.25 s. Survival /
 * Adventure only.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SurvivalVitalsHud {
    private static final double EASE = 0.15, DROP_EASE = 0.06, GHOST_HOLD = 0.45, GHOST_EASE = 0.3, FADE = 0.25, GROW = 0.25,
            LOAD_WAKE_SECONDS = 3, LOAD_WAKE_GRAMS = 50;
    private static final int RED = 0xCC4A42, POISON = 0x8FA83C, WITHER = 0x5E5E5E, FROZEN = 0x9FD8F2, GOLD = 0xE2BE4A,
            GHOST = 0xF2E6DE, FOOD = 0xD39A4C, HUNGER_EFFECT = 0x92A84A, MOUNT = 0xC28A57, ARMOR = 0xB4BCC4,
            AIR = 0x92D4F2, DROWN = 0xBE443A, NUMBER = 0xF0ECE4, NUMBER_LOW = 0xE8867C, CRITICAL = 0xCC4A42,
            STAMINA = 0xC8C6BE, STAMINA_LOW = 0xD6983A, WINDED = 0xBE443A;
    /** Radiation sickness II, III, IV. */
    private static final int[] RADIATION = {0xA09666, 0x88805A, 0x7A7454};
    /** Low health: at or under this share of the maximum (with absorption). */
    private static final double LOW_HEALTH = 0.2;
    private static final int FOOD_LOW = 6, FOOD_CRITICAL = 3;
    private static double health = Double.NaN, ghost, absorb, armor, armorAlpha, stamina = Double.NaN, air = Double.NaN, airAlpha;
    private static double ghostUntil;
    private static final double[] NEED = {Double.NaN, Double.NaN, Double.NaN}, GROWN = new double[3];
    private static double loadChange = Double.NEGATIVE_INFINITY;
    private static long loadGrams = Long.MIN_VALUE;
    private static EncumbranceState.Tier loadTier;
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
    /** Kettlebell: the handle ring and the flared body. */
    static final AflRingIcon.Glyph KETTLEBELL = (x, y) -> Math.min(Math.abs(Math.hypot(x - 0.5, y - 0.28) - 0.14) - 0.055,
            AflRingIcon.convex(x, y, 0.31, 0.42, 0.69, 0.42, 0.87, 0.9, 0.13, 0.9) - 0.02);

    private static double ease(double dt, double seconds) { return 1 - Math.exp(-dt / seconds); }

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        long nanos = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (nanos - lastNanos) / 1e9), now = nanos / 1e9;
        lastNanos = nanos;
        LocalPlayer player = mc.player;
        if (player == null) { health = stamina = air = Double.NaN; NEED[0] = NEED[1] = NEED[2] = Double.NaN; loadGrams = Long.MIN_VALUE; return; }
        trackLoad(now);
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || FieldAttachmentViewState.isActive()) return;
        double cx = SurvivalHudLayout.centreX(), cy = SurvivalHudLayout.centreY(screenHeight);
        double reach = SurvivalHudLayout.RADIUS + SurvivalHudLayout.BACKING + 0.5;
        AflGauge.draw(graphics, "body_back", cx, cy, reach, List.of(AflGauge.Band.disc(SurvivalHudLayout.RADIUS + SurvivalHudLayout.BACKING,
                AflGauge.BACK, SurvivalHudLayout.BACKING_ALPHA)), null);
        com.antaurora.apofirstlight.temperature.ClientTemperatureHud.OVERLAY.render(gui, graphics, partialTick, screenWidth, screenHeight);
        boolean low = health(graphics, mc.font, player, cx, cy, dt, now);
        stamina(graphics, player, cx, cy, dt, now);
        number(graphics, mc.font, player, cx, cy, low);
        needs(graphics, mc.font, player, screenHeight, dt, now);
    };

    /** Draws the health arc and the armour lane; true while health is low. */
    private static boolean health(GuiGraphics graphics, Font font, LocalPlayer player, double x, double y, double dt, double now) {
        // absorption stops vanilla-scale damage before health V1a scales it, so one point stands for about this much health
        double hp = Math.max(0, player.getHealth()), max = Math.max(1, player.getMaxHealth()),
                abs = Math.max(0, player.getAbsorptionAmount()) * com.antaurora.apofirstlight.health.HealthConfig.get().damage.defaultScale;
        if (Double.isNaN(health)) { health = ghost = hp; absorb = abs; armor = Math.min(20, player.getArmorValue()) / 20.0; }
        if (hp < health - 1e-3) {   // damage: the arc drops fast, what was lost lingers as a ghost, then drains
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
        double unit = segmentHealth(max);
        int segments = (int)Math.min(30, Math.ceil(cap / unit - 1e-6));
        int colour = player.hasEffect(MobEffects.POISON) ? POISON : player.hasEffect(MobEffects.WITHER) ? WITHER
                : player.isFullyFrozen() ? FROZEN : RED;
        boolean low = hp + abs <= LOW_HEALTH * max;
        double pulse = low ? 0.5 + 0.5 * Math.sin(now * Math.PI * 2 * 1.6) : 0;
        if (low) colour = mix(colour, 0xFFFFFF, 0.3 * pulse);
        double r = SurvivalHudLayout.RADIUS, band = SurvivalHudLayout.BAND, gap = SurvivalHudLayout.SEGMENT_GAP;
        double start = SurvivalHudLayout.HEALTH_START, span = SurvivalHudLayout.ARC_SPAN;
        List<AflGauge.Band> bands = new ArrayList<>();
        if (low) {   // a soft red glow round the arc
            bands.add(new AflGauge.Band(r + 2.2, band + 4.4, start, span, 0, 0, 0, 1, RED, 0.10 + 0.12 * pulse, AflGauge.BACK, 0));
            bands.add(new AflGauge.Band(r + 1.1, band + 2.2, start, span, 0, 0, 0, 1, RED, 0.12 + 0.14 * pulse, AflGauge.BACK, 0));
        }
        bands.add(AflGauge.Band.arc(r, band, start, span, segments, gap, 0, health / cap, colour, 1));
        radiation(bands, player, health, cap, segments, unit);
        if (absorb > 1e-3) bands.add(overlay(segments, health / cap, (health + absorb) / cap, GOLD, 1));
        if (ghost > health + 1e-3) bands.add(overlay(segments, health / cap, ghost / cap, GHOST, 0.8));
        bands.add(AflGauge.Band.line(r + SurvivalHudLayout.OUTLINE_WIDTH, SurvivalHudLayout.OUTLINE_WIDTH, start, span,
                SurvivalHudLayout.OUTLINE, SurvivalHudLayout.OUTLINE_ALPHA));
        double lane = r + SurvivalHudLayout.LANE_GAP + SurvivalHudLayout.LANE_WIDTH;
        if (armorAlpha > 0.004)
            bands.add(AflGauge.Band.arc(lane, SurvivalHudLayout.LANE_WIDTH, start, span, 0, 0, 0, armor, ARMOR, armorAlpha));
        AflGauge.draw(graphics, "health", x, y, lane + 4.6, bands, null);
        return low;
    }

    /** Health per arc segment: 10 at 100 HP, a vanilla heart (2) for small maximums. */
    private static double segmentHealth(double max) {
        return max >= 50 ? 10 : 2;
    }

    private static AflGauge.Band overlay(int segments, double from, double to, int rgb, double alpha) {
        return new AflGauge.Band(SurvivalHudLayout.RADIUS, SurvivalHudLayout.BAND, SurvivalHudLayout.HEALTH_START, SurvivalHudLayout.ARC_SPAN,
                segments, SurvivalHudLayout.SEGMENT_GAP, from, to, rgb, alpha, AflGauge.BACK, 0);
    }

    /** The stamina arc and the air lane outside it. */
    private static void stamina(GuiGraphics graphics, LocalPlayer player, double x, double y, double dt, double now) {
        double r = SurvivalHudLayout.RADIUS, band = SurvivalHudLayout.BAND;
        double start = SurvivalHudLayout.STAMINA_START, span = SurvivalHudLayout.ARC_SPAN;
        List<AflGauge.Band> bands = new ArrayList<>();
        var state = ClientStamina.state();
        if (state != null && state.max() > 0) {
            double fraction = Math.max(0, Math.min(1, state.value() / state.max()));
            stamina = Double.isNaN(stamina) ? fraction : stamina + (fraction - stamina) * ease(dt, 0.12);
            int colour = state.winded() ? WINDED : state.value() < 30 ? STAMINA_LOW : STAMINA;
            bands.add(AflGauge.Band.arc(r, band, start, span, 0, 0, 1 - stamina, 1, colour, 1));
        } else stamina = Double.NaN;
        bands.add(AflGauge.Band.line(r + SurvivalHudLayout.OUTLINE_WIDTH, SurvivalHudLayout.OUTLINE_WIDTH, start, span,
                SurvivalHudLayout.OUTLINE, SurvivalHudLayout.OUTLINE_ALPHA));
        // air: filling from the bottom up, faded in while it matters
        int max = Math.max(1, player.getMaxAirSupply()), supply = Math.max(0, player.getAirSupply());
        double value = Math.min(1, supply / (double)max);
        boolean shown = player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < max;
        air = Double.isNaN(air) ? value : air + (value - air) * ease(dt, EASE);
        airAlpha += ((shown ? 1 : 0) - airAlpha) * ease(dt, FADE);
        double lane = r + SurvivalHudLayout.LANE_GAP + SurvivalHudLayout.LANE_WIDTH;
        if (airAlpha > 0.004) {
            boolean drowning = player.getAirSupply() <= 0;
            double pulse = drowning ? 0.5 + 0.5 * Math.sin(now * Math.PI * 2 * 1.6) : 0;
            bands.add(new AflGauge.Band(lane, SurvivalHudLayout.LANE_WIDTH, start, span, 10, SurvivalHudLayout.SEGMENT_GAP * 0.65,
                    1 - air, 1, AIR, airAlpha, drowning ? DROWN : AflGauge.BACK, drowning ? 0.45 + 0.4 * pulse : AflGauge.BACK_ALPHA));
        }
        AflGauge.draw(graphics, "stamina", x, y, lane + 0.5, bands, null);
    }

    /**
     * The heart and the health number side by side, centred in the bottom gap. Only the current health: every survivor has
     * the same maximum (health V1a: 100), and "100/100" did not fit between the arcs' ends (user, 2026-10-19).
     */
    private static void number(GuiGraphics graphics, Font font, LocalPlayer player, double cx, double cy, boolean low) {
        String text = String.valueOf((int)Math.ceil(Math.max(0, player.getHealth()) - 1e-4));
        double k = AflHudText.halfScale(), w = font.width(text) * k, heart = SurvivalHudLayout.HEART_SIZE, gap = 0.9;
        double left = cx - (heart + gap + w) / 2, row = cy + SurvivalHudLayout.NUMBER_ROW;
        int colour = player.hasEffect(MobEffects.POISON) ? POISON : player.hasEffect(MobEffects.WITHER) ? WITHER : RED;
        AflGauge.draw(graphics, "health_heart", left + heart / 2, row, heart / 2 + 0.5, List.of(),
                new AflGauge.Glyph(HEART, heart, colour, 1));
        AflHudText.half(graphics, font, text, left + heart + gap + w / 2, row - 3.5 * k, low ? NUMBER_LOW : NUMBER);
    }

    // ---- needs rings ----

    /** Notes when the load last changed (by 50 g or into another tier): the load ring shows its number for a while. */
    private static void trackLoad(double now) {
        var state = ClientWeightState.state();
        if (state == null) { loadGrams = Long.MIN_VALUE; return; }
        if (loadGrams == Long.MIN_VALUE) { loadGrams = state.loadGrams(); loadTier = state.tier(); return; }
        if (Math.abs(state.loadGrams() - loadGrams) >= LOAD_WAKE_GRAMS || state.tier() != loadTier) {
            loadGrams = state.loadGrams(); loadTier = state.tier(); loadChange = now;
        }
    }

    private static void needs(GuiGraphics graphics, Font font, LocalPlayer player, int screenHeight, double dt, double now) {
        // hunger, or the mount's health while riding a living mount
        if (player.getVehicle() instanceof LivingEntity ride) {
            double value = Math.max(0, Math.min(1, ride.getHealth() / Math.max(1, ride.getMaxHealth())));
            ring(graphics, font, screenHeight, 0, value, MOUNT, HORSESHOE, false, null, dt);
        } else {
            int food = Math.max(0, Math.min(20, player.getFoodData().getFoodLevel()));
            int colour = food <= FOOD_CRITICAL ? CRITICAL : player.hasEffect(MobEffects.HUNGER) ? HUNGER_EFFECT : FOOD;
            ring(graphics, font, screenHeight, 0, food / 20.0, colour, DRUMSTICK, food <= FOOD_LOW, String.valueOf(food * 5), dt);
        }
        var thirst = ClientThirst.state();
        if (thirst != null && thirst.max() > 0) {
            var c = ThirstConfig.get();
            double value = Math.max(0, Math.min(thirst.max(), thirst.value()));
            int colour = value < c.dehydratedBelow ? CRITICAL : ClientThirstHud.WATER_BLUE;
            ring(graphics, font, screenHeight, 1, value / thirst.max(), colour, ClientThirstHud.WATER_DROP, value < c.thirstyBelow,
                    String.valueOf((int)Math.round(value / thirst.max() * 100)), dt);
        } else NEED[1] = Double.NaN;
        var load = ClientWeightState.state();
        if (load != null && load.penaltiesEnabled() && load.comfortCapacityGrams() > 0 && !com.antaurora.apofirstlight.client.vehicle.VehicleHud.riding()) {
            var policy = ClientWeightState.policy();
            double severe = policy == null ? 2.0 : policy.policy().severe();
            boolean woken = load.tier() != EncumbranceState.Tier.LIGHT || now - loadChange < LOAD_WAKE_SECONDS;
            ring(graphics, font, screenHeight, 2, load.encumbranceRatio() / Math.max(severe, 0.01), tierColour(load.tier()), KETTLEBELL,
                    woken, String.format(Locale.ROOT, "%.1f", load.loadGrams() / 1000.0), dt);
        } else { NEED[2] = Double.NaN; GROWN[2] = 0; }
    }

    /** Muted fills per load tier: grey, yellow, amber, orange, red (the former load bar's). */
    public static int tierColour(EncumbranceState.Tier tier) {
        return switch (tier) {
            case LIGHT -> 0xB4B2A8;
            case BURDENED -> 0xD6BE5E;
            case HEAVY -> 0xD6983A;
            case HAULING -> 0xCE6A2C;
            case EXTREME -> 0xBE443A;
        };
    }

    /** Needs ring i: eased fill, eased growth; grown it is opaque, bigger and shows `number` under its icon. */
    private static void ring(GuiGraphics graphics, Font font, int screenHeight, int i, double value, int colour, AflRingIcon.Glyph icon,
                             boolean grown, String number, double dt) {
        value = Math.max(0, Math.min(1, value));
        NEED[i] = Double.isNaN(NEED[i]) ? value : NEED[i] + (value - NEED[i]) * ease(dt, EASE);
        GROWN[i] += ((grown ? 1 : 0) - GROWN[i]) * ease(dt, GROW);
        if (Math.abs(GROWN[i] - (grown ? 1 : 0)) < 0.004) GROWN[i] = grown ? 1 : 0;
        double g = GROWN[i], x = SurvivalHudLayout.needX(i), y = SurvivalHudLayout.needY(screenHeight, i);
        double r = lerp(SurvivalHudLayout.NEED_R, SurvivalHudLayout.NEED_R_GROWN, g);
        double band = lerp(SurvivalHudLayout.NEED_BAND, SurvivalHudLayout.NEED_BAND_GROWN, g);
        double alpha = lerp(SurvivalHudLayout.NEED_ALPHA, 1, g), withNumber = number == null ? 0 : g;
        double iconSize = lerp(SurvivalHudLayout.NEED_ICON, SurvivalHudLayout.NEED_ICON_GROWN, withNumber);
        List<AflGauge.Band> bands = new ArrayList<>();
        bands.add(AflGauge.Band.disc(r, AflGauge.BACK, 0.5 * alpha));
        bands.add(new AflGauge.Band(r, band, 0, 360, 0, 0, 0, NEED[i], colour, alpha, AflGauge.BACK, 0.5 * alpha));
        bands.add(AflGauge.Band.line(r + 0.2, SurvivalHudLayout.OUTLINE_WIDTH, 0, 360, SurvivalHudLayout.OUTLINE, SurvivalHudLayout.OUTLINE_ALPHA * alpha));
        AflGauge.draw(graphics, "need_" + i, x, y, SurvivalHudLayout.NEED_R_GROWN + 0.8, bands,
                new AflGauge.Glyph(icon, iconSize, colour, alpha, -1.9 * withNumber));
        if (withNumber > 0.6) AflHudText.half(graphics, font, number, x, y + 1.0, NUMBER);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    // ---- radiation sickness: tinted health segments ----

    /** Stage II / III / IV: about 30 / 60 / 100 % of the filled segments, chosen per player (stable while health holds). */
    private static void radiation(List<AflGauge.Band> bands, LocalPlayer player, double shownHealth, double cap, int segments, double unit) {
        var sickness = player.getEffect(AflMobEffects.RADIATION_SICKNESS.get());
        if (sickness == null) return;
        int stage = sickness.getAmplifier() + 1;
        if (stage < 2) return;
        int filled = (int)Math.ceil(Math.max(0, player.getHealth()) / unit);
        int target = switch (stage) {
            case 2 -> (filled * 3 + 9) / 10;
            case 3 -> (filled * 6 + 9) / 10;
            default -> filled;
        };
        if (target <= 0) return;
        int colour = RADIATION[Math.min(stage, 4) - 2];
        UUID id = player.getUUID();
        for (int k = 0; k < filled; k++) {
            if (!selected(id, k, filled, target)) continue;
            double from = unit * k / cap, to = Math.min(shownHealth, unit * (k + 1)) / cap;
            if (to > from) bands.add(overlay(segments, from, to, colour, 1));
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
