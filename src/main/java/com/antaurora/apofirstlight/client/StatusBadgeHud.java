package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.registry.AflMobEffects;
import com.antaurora.apofirstlight.stamina.ClientStamina;
import com.antaurora.apofirstlight.temperature.ClientTemperature;
import com.antaurora.apofirstlight.temperature.TemperatureReadout;
import com.antaurora.apofirstlight.thirst.ClientThirst;
import com.antaurora.apofirstlight.thirst.ClientThirstHud;
import com.antaurora.apofirstlight.thirst.ThirstConfig;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import com.antaurora.apofirstlight.weight.ClientWeightState;
import com.antaurora.apofirstlight.weight.EncumbranceState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HUD V2 status badges and the status card (2026-10-16, docs/ui/hud_v2_research.md 5; the badges after Abiotic Factor's
 * effect column, user 2026-10-16: icon only is fine). AFL's own states only (user 2026-10-17: vanilla potion effects stay
 * as they are for now; AFL will add more effects of its own later):
 * <ul>
 * <li>A column of round badges above the body module, bottom left, only while a state applies: a filled circle in its
 * kind's colour (green good, brown bad, blue cold / wet, red dangerous), a white pictogram, a light rim. Most urgent
 * nearest the module (danger, bad, cold / wet, good). A timed one (a stomach bug, fuel on the body) shows the time left
 * as a light arc round it and as a number beside it; dehydration counts down to the point where it starts to hurt.</li>
 * <li>Gaining a state (or a worse stage of it): its rim flashes and its name and effect slide out beside it for 4 s.</li>
 * <li>Holding the status key (Left Alt, rebindable): a card above the module with the body's numbers and every state with
 * its effect and time left; the slide-out labels step aside meanwhile. Off for now ({@link #DETAILS_CARD}).</li>
 * <li>The chat moves up above the column (not while a screen is open, so clicking chat links still works).</li>
 * </ul>
 * AFL's effects (radiation sickness, the stomach bug, fuel-soaked) are hidden from vanilla's effect icons (their
 * initializeClient), since the badges show them.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StatusBadgeHud {
    public static final KeyMapping DETAILS = new KeyMapping("key.apocalypse_firstlight.status_details", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.apocalypse_firstlight");
    /**
     * The status key's detail card is off (user 2026-10-17: Left Alt is pressed by mistake too easily) until a vital
     * signs monitor (an advanced wrist watch) carries those readings; its key is not registered meanwhile.
     */
    private static final boolean DETAILS_CARD = false;
    private static final String LANG = "hud.apocalypse_firstlight.status.";
    /** How long a gained state's label stays out, its slide, the rim flash (s). */
    private static final double LABEL_SECONDS = 4, SLIDE = 0.22, FLASH = 0.7, CARD_FADE = 0.12, MOVE = 0.18, CHAT_EASE = 0.2;
    private static final int WHITE = 0xF6F2EA, DANGER_TEXT = 0xE8867C;

    /** A badge's kind: its colour and its rank (most urgent first, nearest the module). */
    enum Kind {
        DANGER(0xB8443C), BAD(0xA86A3A), COLD(0x4F7FB8), GOOD(0x5FA67E);
        final int rgb;
        Kind(int rgb) { this.rgb = rgb; }
    }

    /** One state as shown: key (stable), stage (a higher stage of the same key re-announces it), kind, icon, name, effect;
     *  fraction of a timed state left (NaN: untimed) and the text beside the badge (time left, a countdown), or null. */
    record Status(String key, int stage, Kind kind, AflRingIcon.Glyph icon, Component name, Component effect, double left, String aside) {}

    private static final class Seen {
        int stage;
        double labelUntil, flashUntil, slot = Double.NaN, maxTicks;
    }
    private static final Map<String, Seen> SEEN = new HashMap<>();
    private static double card, chatLift, thirstRate, thirstValue = Double.NaN, thirstAt;
    private static int shownBadges;
    private static boolean drawn;
    private static long lastNanos;

    private StatusBadgeHud() {}

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "status_badges", OVERLAY);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) { if (DETAILS_CARD) event.register(DETAILS); }

    // ---- glyphs (unit box, y down) ----
    private static final double[] ARM_X = new double[6], ARM_Y = new double[6];
    static {
        for (int k = 0; k < 6; k++) { double a = k * Math.PI / 3 - Math.PI / 2; ARM_X[k] = Math.cos(a); ARM_Y[k] = Math.sin(a); }
    }
    static final AflRingIcon.Glyph SNOWFLAKE = (x, y) -> {
        double d = Double.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            double ux = ARM_X[k], uy = ARM_Y[k], bx = 0.5 + ux * 0.27, by = 0.5 + uy * 0.27;
            d = Math.min(d, AflRingIcon.segment(x, y, 0.5, 0.5, 0.5 + ux * 0.42, 0.5 + uy * 0.42) - 0.055);
            for (int side = -1; side <= 1; side += 2) {
                double a = Math.atan2(uy, ux) + side * Math.PI / 4;
                d = Math.min(d, AflRingIcon.segment(x, y, bx, by, bx + Math.cos(a) * 0.13, by + Math.sin(a) * 0.13) - 0.045);
            }
        }
        return d;
    };
    static final AflRingIcon.Glyph SUN = (x, y) -> {
        double d = Math.hypot(x - 0.5, y - 0.5) - 0.17;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4, c = Math.cos(a), s = Math.sin(a);
            d = Math.min(d, AflRingIcon.segment(x, y, 0.5 + c * 0.28, 0.5 + s * 0.28, 0.5 + c * 0.42, 0.5 + s * 0.42) - 0.05);
        }
        return d;
    };
    static final AflRingIcon.Glyph WET = (x, y) -> {
        double drop = Math.min(Math.hypot(x - 0.42, y - 0.47) - 0.17, AflRingIcon.convex(x, y, 0.42, 0.08, 0.567, 0.38, 0.273, 0.38));
        double wave = Math.min(Math.min(AflRingIcon.segment(x, y, 0.1, 0.82, 0.3, 0.74), AflRingIcon.segment(x, y, 0.3, 0.74, 0.5, 0.82)),
                Math.min(AflRingIcon.segment(x, y, 0.5, 0.82, 0.7, 0.9), AflRingIcon.segment(x, y, 0.7, 0.9, 0.9, 0.82))) - 0.05;
        return Math.min(drop, wave);
    };
    static final AflRingIcon.Glyph TREFOIL = (x, y) -> {
        double dx = x - 0.5, dy = y - 0.5, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx), best = Double.MAX_VALUE;
        for (int k = 0; k < 3; k++) {
            double c = -Math.PI / 2 + k * 2 * Math.PI / 3, off = Math.abs(Math.IEEEremainder(a - c, 2 * Math.PI));
            best = Math.min(best, Math.max(Math.abs(r - 0.3) - 0.14, Math.sin(Math.min(Math.PI / 2, Math.max(0, off - 0.52))) * r));
        }
        return Math.min(best, r - 0.09);
    };
    static final AflRingIcon.Glyph GERM = (x, y) -> {
        double d = Math.hypot(x - 0.5, y - 0.5) - 0.2;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8, c = Math.cos(a), s = Math.sin(a);
            d = Math.min(d, AflRingIcon.segment(x, y, 0.5 + c * 0.2, 0.5 + s * 0.2, 0.5 + c * 0.36, 0.5 + s * 0.36) - 0.04);
            d = Math.min(d, Math.hypot(x - 0.5 - c * 0.38, y - 0.5 - s * 0.38) - 0.065);
        }
        return d;
    };
    static final AflRingIcon.Glyph FUEL_CAN = (x, y) -> Math.min(AflRingIcon.convex(x, y, 0.22, 0.24, 0.58, 0.24, 0.78, 0.42, 0.78, 0.9, 0.22, 0.9) - 0.02,
            AflRingIcon.segment(x, y, 0.66, 0.24, 0.82, 0.08) - 0.05);
    static final AflRingIcon.Glyph FORK_KNIFE = (x, y) -> {
        double d = AflRingIcon.segment(x, y, 0.34, 0.4, 0.34, 0.9) - 0.055;
        for (double tx : new double[]{0.22, 0.34, 0.46}) d = Math.min(d, AflRingIcon.segment(x, y, tx, 0.1, tx, 0.36) - 0.04);
        d = Math.min(d, AflRingIcon.segment(x, y, 0.22, 0.36, 0.46, 0.36) - 0.05);
        d = Math.min(d, AflRingIcon.convex(x, y, 0.66, 0.1, 0.78, 0.22, 0.76, 0.56, 0.64, 0.56));
        return Math.min(d, AflRingIcon.segment(x, y, 0.7, 0.56, 0.7, 0.9) - 0.05);
    };
    static final AflRingIcon.Glyph FLAME = (x, y) -> {
        double outer = Math.min(Math.min(Math.hypot(x - 0.5, y - 0.66) - 0.24, AflRingIcon.convex(x, y, 0.58, 0.08, 0.735, 0.60, 0.265, 0.60)),
                Math.min(Math.hypot(x - 0.36, y - 0.62) - 0.13, AflRingIcon.convex(x, y, 0.30, 0.30, 0.47, 0.62, 0.24, 0.62)));
        double inner = Math.min(Math.hypot(x - 0.5, y - 0.74) - 0.095, AflRingIcon.convex(x, y, 0.52, 0.47, 0.595, 0.73, 0.405, 0.73));
        return Math.max(outer, -inner);
    };
    static final AflRingIcon.Glyph SHIELD = (x, y) -> AflRingIcon.convex(x, y, 0.5, 0.08, 0.86, 0.22, 0.8, 0.6, 0.5, 0.92, 0.2, 0.6, 0.14, 0.22);
    static final AflRingIcon.Glyph BOLT = (x, y) -> Math.min(Math.min(AflRingIcon.segment(x, y, 0.62, 0.08, 0.34, 0.52),
            AflRingIcon.segment(x, y, 0.34, 0.52, 0.66, 0.48)), AflRingIcon.segment(x, y, 0.66, 0.48, 0.38, 0.92)) - 0.08;
    static final AflRingIcon.Glyph THERMOMETER = (x, y) -> Math.min(AflRingIcon.segment(x, y, 0.5, 0.12, 0.5, 0.62) - 0.09,
            Math.hypot(x - 0.5, y - 0.74) - 0.17);

    // ---- the states ----

    private static String roman(int n) { return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> "IV"; }; }
    private static String clock(double seconds) {
        int s = (int)Math.ceil(Math.max(0, seconds));
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
    private static Component t(String key, Object... args) { return Component.translatable(LANG + key, args); }

    /** The local player's AFL states this frame. */
    static List<Status> collect(LocalPlayer player, double now) {
        List<Status> list = new ArrayList<>();
        int cold = ClientTemperature.coldStage(), heat = ClientTemperature.heatStage();
        if (cold > 0) list.add(new Status("cold", cold, cold >= 3 ? Kind.DANGER : Kind.COLD, SNOWFLAKE,
                t("cold." + cold), t("cold." + cold + ".effect"), Double.NaN, null));
        if (heat > 0) list.add(new Status("hot", heat, heat >= 3 ? Kind.DANGER : Kind.BAD, SUN,
                t("hot." + heat), t("hot." + heat + ".effect"), Double.NaN, null));
        double wet = ClientTemperature.wet();
        if (wet >= 0.15) {
            int stage = wet >= 0.6 ? 2 : 1;
            list.add(new Status("wet", stage, Kind.COLD, WET, t("wet." + stage), t("wet.effect"), Double.NaN, null));
        }
        var sickness = player.getEffect(AflMobEffects.RADIATION_SICKNESS.get());
        if (sickness != null) {
            int stage = Math.min(4, sickness.getAmplifier() + 1);
            list.add(new Status("radiation", stage, stage >= 4 ? Kind.DANGER : Kind.BAD, TREFOIL,
                    t("radiation", roman(stage)), t("radiation." + stage + ".effect"), Double.NaN, null));
        }
        timed(list, player, AflMobEffects.GASTROENTERITIS.get(), "gastro", GERM);
        timed(list, player, AflMobEffects.GASOLINE_SOAKED.get(), "gasoline", FUEL_CAN);
        timed(list, player, AflMobEffects.DIESEL_SOAKED.get(), "diesel", FUEL_CAN);
        var load = ClientWeightState.state();
        if (load != null && load.penaltiesEnabled() && load.tier().ordinal() >= EncumbranceState.Tier.HEAVY.ordinal()) {
            int slower = (int)Math.round((1 - load.speedMultiplier()) * 100);
            Component effect = load.sprintBlocked() ? t("load.effect_no_sprint", slower) : t("load.effect", slower);
            list.add(new Status("load", load.tier().ordinal(), load.tier() == EncumbranceState.Tier.EXTREME ? Kind.DANGER : Kind.BAD,
                    SurvivalVitalsHud.KETTLEBELL, t("load." + load.tier().name().toLowerCase(Locale.ROOT)), effect, Double.NaN, null));
        }
        thirst(list, now);
        int food = player.getFoodData().getFoodLevel();
        if (food <= 0) list.add(new Status("hunger", 2, Kind.DANGER, SurvivalVitalsHud.DRUMSTICK, t("hunger.2"), t("hunger.2.effect"), Double.NaN, null));
        else if (food <= 6) list.add(new Status("hunger", 1, Kind.BAD, SurvivalVitalsHud.DRUMSTICK, t("hunger.1"), t("hunger.1.effect"), Double.NaN, null));
        // good: well fed while healing (vanilla's natural regeneration from 18 food points, unless AFL stops it)
        var thirst = ClientThirst.state();
        boolean dehydrated = thirst != null && thirst.value() < ThirstConfig.get().dehydratedBelow;
        if (food >= 18 && player.isHurt() && cold < 2 && heat < 2 && !dehydrated)
            list.add(new Status("fed", 1, Kind.GOOD, FORK_KNIFE, t("fed"), t("fed.effect"), Double.NaN, null));
        if (ClientTemperature.coldness() > 0.02 && ClientTemperature.trend() > 0.25)
            list.add(new Status("warming", 1, Kind.GOOD, FLAME, t("warming"), t("warming.effect"), Double.NaN, null));
        return list;
    }

    /** A timed AFL effect: the arc follows the time left against the longest time seen while it lasts. */
    private static void timed(List<Status> list, LocalPlayer player, MobEffect effect, String key, AflRingIcon.Glyph icon) {
        var instance = player.getEffect(effect);
        if (instance == null) return;
        Seen seen = SEEN.computeIfAbsent(key, k -> new Seen());
        int ticks = instance.isInfiniteDuration() ? 0 : instance.getDuration();
        seen.maxTicks = Math.max(seen.maxTicks, ticks);
        double left = ticks <= 0 || seen.maxTicks <= 0 ? Double.NaN : ticks / seen.maxTicks;
        list.add(new Status(key, 1, Kind.BAD, icon, t(key), t(key + ".effect"), left, ticks > 0 ? clock(ticks / 20.0) : null));
    }

    /** Thirsty / dehydrated / out of water; while dehydrated, the time until it starts to hurt (from the observed rate). */
    private static void thirst(List<Status> list, double now) {
        var state = ClientThirst.state();
        if (state == null || state.max() <= 0) { thirstValue = Double.NaN; return; }
        var c = ThirstConfig.get();
        double value = state.value();
        if (Double.isNaN(thirstValue) || value > thirstValue + 1e-3) { thirstValue = value; thirstAt = now; thirstRate = 0; }
        else if (value < thirstValue - 1e-3) {   // a new, lower value: the rate since the last one, smoothed
            double rate = (thirstValue - value) / Math.max(0.05, now - thirstAt);
            thirstRate = thirstRate <= 0 ? rate : thirstRate + (rate - thirstRate) * 0.3;
            thirstValue = value; thirstAt = now;
        }
        if (value <= 0) list.add(new Status("thirst", 3, Kind.DANGER, ClientThirstHud.WATER_DROP, t("thirst.3"),
                t("thirst.3.effect", (int)Math.round(c.damageIntervalSeconds)), Double.NaN, null));
        else if (value < c.dehydratedBelow) {
            double seconds = thirstRate > 1e-5 ? value / thirstRate - (now - thirstAt) : Double.NaN;
            list.add(new Status("thirst", 2, Kind.DANGER, ClientThirstHud.WATER_DROP, t("thirst.2"),
                    t("thirst.2.effect", Math.round(c.staminaRegenDehydrated * 100)), Double.NaN,
                    Double.isNaN(seconds) || seconds > 3600 ? null : clock(seconds)));
        } else if (value < c.thirstyBelow) list.add(new Status("thirst", 1, Kind.BAD, ClientThirstHud.WATER_DROP, t("thirst.1"),
                t("thirst.1.effect", Math.round(c.staminaRegenThirsty * 100)), Double.NaN, null));
    }

    // ---- drawing ----

    static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        var mc = Minecraft.getInstance();
        long nanos = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.25, (nanos - lastNanos) / 1e9), now = nanos / 1e9;
        lastNanos = nanos;
        drawn = false;
        LocalPlayer player = mc.player;
        if (player == null) { SEEN.clear(); thirstValue = Double.NaN; return; }
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || FieldAttachmentViewState.isActive()) return;
        drawn = true;
        List<Status> states = new ArrayList<>(collect(player, now));
        states.sort(Comparator.comparingInt(s -> s.kind().ordinal()));
        SEEN.keySet().retainAll(states.stream().map(Status::key).toList());
        boolean open = DETAILS_CARD && mc.screen == null && DETAILS.isDown();
        card += ((open ? 1 : 0) - card) * (1 - Math.exp(-dt / CARD_FADE));
        if (Math.abs(card - (open ? 1 : 0)) < 0.01) card = open ? 1 : 0;
        shownBadges = states.size();
        Font font = mc.font;
        for (int i = 0; i < states.size(); i++) {
            var s = states.get(i);
            Seen seen = SEEN.computeIfAbsent(s.key(), k -> new Seen());
            if (s.stage() > seen.stage) { seen.labelUntil = now + LABEL_SECONDS; seen.flashUntil = now + FLASH; }
            seen.stage = s.stage();
            seen.slot = Double.isNaN(seen.slot) ? i : seen.slot + (i - seen.slot) * (1 - Math.exp(-dt / MOVE));
            double y = SurvivalHudLayout.badgeY(screenHeight, 0) - seen.slot * SurvivalHudLayout.BADGE_STEP;
            if (card < 0.99) badge(graphics, "badge_" + s.key(), s, SurvivalHudLayout.BADGE_X, y, SurvivalHudLayout.BADGE_R,
                    Math.max(0, seen.flashUntil - now) / FLASH, 1 - card);
            double slide = smooth(Math.min((now - (seen.labelUntil - LABEL_SECONDS)) / SLIDE, (seen.labelUntil - now) / SLIDE)) * (1 - card);
            aside(graphics, font, s, SurvivalHudLayout.BADGE_X + SurvivalHudLayout.BADGE_R + 2, y, slide, 1 - card);
        }
        if (card > 0.01) card(graphics, font, player, states, screenHeight, card);
    };

    /** A badge: shadow, the kind's disc, a light sheen and rim, the time-left arc, the white pictogram, the gained flash. */
    private static void badge(GuiGraphics graphics, String id, Status s, double x, double y, double r, double flash, double alpha) {
        AflGauge.draw(graphics, id, x, y, r + 4.7, badgeBands(s.kind(), r, s.left(), flash, alpha), badgeGlyph(s.icon(), r, alpha));
    }
    /** The badge's bands (also drawn offline for the wiki's status icons, test StatusBadgeIcons). */
    static List<AflGauge.Band> badgeBands(Kind kind, double r, double left, double flash, double alpha) {
        List<AflGauge.Band> bands = new ArrayList<>();
        bands.add(AflGauge.Band.disc(r + 0.7, 0x000000, 0.22 * alpha));
        bands.add(AflGauge.Band.disc(r, kind.rgb, 0.95 * alpha));
        bands.add(AflGauge.Band.line(r - 0.6, 0.5, 300, 120, 0xFFFFFF, 0.14 * alpha));
        bands.add(AflGauge.Band.line(r + 0.2, 0.5, 0, 360, AflUiStyle.LINE, 0.4 * alpha));
        if (!Double.isNaN(left))
            bands.add(new AflGauge.Band(r + 1.6, 0.8, 0, 360, 0, 0, 0, left, 0xF0ECE4, 0.9 * alpha, AflGauge.BACK, 0.4 * alpha));
        if (flash > 0) bands.add(AflGauge.Band.line(r + 2.6 + (1 - flash) * 1.5, 0.6, 0, 360, AflUiStyle.LINE, 0.7 * flash * alpha));
        return bands;
    }
    static AflGauge.Glyph badgeGlyph(AflRingIcon.Glyph icon, double r, double alpha) {
        return new AflGauge.Glyph(icon, r * 1.18, WHITE, alpha);
    }

    /** Beside a badge: the sliding label (name and effect) while `slide` > 0, otherwise its time or countdown, if any. */
    private static void aside(GuiGraphics graphics, Font font, Status s, double x, double y, double slide, double alpha) {
        boolean danger = s.kind() == Kind.DANGER;
        Component name = s.aside() == null ? s.name() : s.name().copy().append(" " + s.aside());
        double k = AflHudText.labelScale(), h = 9, top = y - h / 2, textY = y - 3.5 * k;
        if (slide > 0.01) {
            double nameW = AflHudText.labelWidth(font, name), effectW = AflHudText.labelWidth(font, s.effect()), w = nameW + 4 + effectW + 8;
            double shift = (1 - slide) * -6;
            AflUiShapes.box(graphics, (float)(x + shift), (float)top, (float)w, (float)h, (float)(h / 2), AflUiStyle.BACK,
                    (float)(0.62 * slide * alpha), AflUiStyle.LINE, (float)(0.22 * slide * alpha), 0, 0);
            AflHudText.label(graphics, font, name, x + shift + 4, textY, danger ? DANGER_TEXT : AflUiStyle.TEXT, slide * alpha);
            AflHudText.label(graphics, font, s.effect(), x + shift + 4 + nameW + 4, textY, AflUiStyle.TEXT_DIM, slide * alpha);
            return;
        }
        if (s.aside() == null || alpha < 0.01) return;
        Component text = Double.isNaN(s.left()) ? name : Component.literal(s.aside());   // a countdown keeps its name, a timer is just the time
        double w = AflHudText.labelWidth(font, text) + 8;
        AflUiShapes.box(graphics, (float)x, (float)top, (float)w, (float)h, (float)(h / 2), AflUiStyle.BACK, (float)(0.55 * alpha),
                AflUiStyle.LINE, 0, 0, 0);
        AflHudText.label(graphics, font, text, x + 4, textY, danger ? DANGER_TEXT : AflUiStyle.TEXT, alpha);
    }

    // ---- the status card (hold the status key) ----

    private static final double CARD_X = 6, CARD_W = 158, ROW = 10.5, PAD = 6;

    private static void card(GuiGraphics graphics, Font font, LocalPlayer player, List<Status> states, int screenHeight, double a) {
        double k = AflHudText.labelScale();
        List<Object[]> rows = rows(player);
        double effectsH = 10 + Math.max(1, states.size()) * 12;
        double h = 12 + rows.size() * ROW + 6 + effectsH + 4;
        double bottom = SurvivalHudLayout.needY(screenHeight, 0) - SurvivalHudLayout.NEED_R_GROWN - 4, top = bottom - h, x = CARD_X;
        AflUiShapes.box(graphics, (float)x, (float)top, (float)CARD_W, (float)h, AflUiStyle.RADIUS_PANEL, AflUiStyle.BACK, (float)(0.74 * a),
                AflUiStyle.LINE, (float)(AflUiStyle.LINE_ALPHA * a), (float)(AflUiStyle.SHADOW_ALPHA * a), 0);
        AflHudText.label(graphics, font, t("card.title"), x + PAD, top + 4, AflUiStyle.TEXT_DIM, a);
        Component air = air(player);
        if (air != null) AflHudText.label(graphics, font, air, x + CARD_W - PAD - AflHudText.labelWidth(font, air), top + 4, AflUiStyle.TEXT_DISABLED, a);
        double y = top + 13;
        for (int i = 0; i < rows.size(); i++, y += ROW) {
            Object[] row = rows.get(i);
            int rgb = (int)row[3];
            AflGauge.draw(graphics, "card_row_" + i, x + PAD + 2.5, y + 3.5, 3.5, List.of(),
                    new AflGauge.Glyph((AflRingIcon.Glyph)row[0], 5.2, rgb, a));
            AflHudText.label(graphics, font, (Component)row[1], x + PAD + 8, y + 3.5 - 3.5 * k, AflUiStyle.TEXT, a);
            double barX = x + PAD + 34, barW = 50;
            double fill = (double)row[4];
            if (row.length > 5) {   // two-sided (temperature): from the middle toward the cold or the hot end
                AflUiShapes.fill(graphics, (float)barX, (float)(y + 2.6), (float)barW, 1.8F, 0.9F, AflGauge.BACK, (float)(0.6 * a));
                double from = barW / 2 + Math.min(0, fill) * barW / 2, to = barW / 2 + Math.max(0, fill) * barW / 2;
                if (to - from > 0.3) AflUiShapes.fill(graphics, (float)(barX + from), (float)(y + 2.6), (float)(to - from), 1.8F, 0.9F, rgb, (float)a);
                AflUiShapes.fill(graphics, (float)(barX + barW / 2 - 0.25), (float)(y + 1.8), 0.5F, 3.4F, 0.2F, AflUiStyle.LINE, (float)(0.6 * a));
            } else {
                AflUiShapes.fill(graphics, (float)barX, (float)(y + 2.6), (float)barW, 1.8F, 0.9F, AflGauge.BACK, (float)(0.6 * a));
                if (fill > 0.005) AflUiShapes.fill(graphics, (float)barX, (float)(y + 2.6), (float)Math.max(1.8, barW * Math.min(1, fill)), 1.8F, 0.9F, rgb, (float)a);
            }
            Component value = (Component)row[2];
            AflHudText.label(graphics, font, value, x + CARD_W - PAD - AflHudText.labelWidth(font, value), y + 3.5 - 3.5 * k, AflUiStyle.TEXT_DIM, a);
        }
        y += 2;
        AflUiShapes.fill(graphics, (float)(x + PAD), (float)y, (float)(CARD_W - 2 * PAD), 0.4F, 0.2F, AflUiStyle.LINE, (float)(0.16 * a));
        AflHudText.label(graphics, font, t("card.effects"), x + PAD, y + 3, AflUiStyle.TEXT_DIM, a);
        y += 12;
        if (states.isEmpty()) AflHudText.label(graphics, font, t("card.none"), x + PAD, y + 4 - 3.5 * k, AflUiStyle.TEXT_DISABLED, a);
        for (var s : states) {
            badge(graphics, "card_badge_" + s.key(), s, x + PAD + 4, y + 4, 4.2, 0, a);
            Component name = s.name();
            double nx = x + PAD + 11;
            double nw = AflHudText.label(graphics, font, name, nx, y + 4 - 3.5 * k, s.kind() == Kind.DANGER ? DANGER_TEXT : AflUiStyle.TEXT, a);
            Component effect = s.aside() == null ? s.effect() : s.effect().copy().append("  " + s.aside());
            AflHudText.label(graphics, font, effect, nx + nw + 4, y + 4 - 3.5 * k, AflUiStyle.TEXT_DIM, a);
            y += 12;
        }
    }

    /** The card's rows: icon, label, value, colour, fill (0..1; with a sixth element, a two-sided −1..+1 fill). */
    private static List<Object[]> rows(LocalPlayer player) {
        List<Object[]> rows = new ArrayList<>();
        double hp = Math.max(0, player.getHealth()), max = Math.max(1, player.getMaxHealth());
        rows.add(new Object[]{SurvivalVitalsHud.HEART, t("card.health"), Component.literal((int)Math.ceil(hp - 1e-4) + " / " + (int)Math.round(max)),
                0xCC4A42, hp / max});
        int armor = player.getArmorValue();
        rows.add(new Object[]{SHIELD, t("card.armor"), Component.literal(armor + " / 20"), 0xB4BCC4, Math.min(1, armor / 20.0)});
        var stamina = ClientStamina.state();
        double st = stamina == null || stamina.max() <= 0 ? 0 : Math.max(0, Math.min(1, stamina.value() / stamina.max()));
        rows.add(new Object[]{BOLT, t("card.stamina"), Component.literal(Math.round(st * 100) + "%"), 0xC8C6BE, st});
        int food = player.getFoodData().getFoodLevel();
        rows.add(new Object[]{SurvivalVitalsHud.DRUMSTICK, t("card.hunger"), Component.literal(food * 5 + "%"), 0xD39A4C, food / 20.0});
        var thirst = ClientThirst.state();
        double th = thirst == null || thirst.max() <= 0 ? 0 : Math.max(0, Math.min(1, thirst.value() / thirst.max()));
        rows.add(new Object[]{ClientThirstHud.WATER_DROP, t("card.thirst"), Component.literal(Math.round(th * 100) + "%"), ClientThirstHud.WATER_BLUE, th});
        double bias = Math.max(-1, Math.min(1, ClientTemperature.heat() - ClientTemperature.coldness()));
        rows.add(new Object[]{THERMOMETER, t("card.temperature"), feeling(player), bias < 0 ? 0x8ECAF0 : 0xE8843F, bias, true});
        var load = ClientWeightState.state();
        if (load != null && load.comfortCapacityGrams() > 0) {
            var policy = ClientWeightState.policy();
            double severe = policy == null ? 2.0 : policy.policy().severe();
            rows.add(new Object[]{SurvivalVitalsHud.KETTLEBELL, t("card.load"),
                    Component.literal(String.format(Locale.ROOT, "%.1f kg · ", load.loadGrams() / 1000.0))
                            .append(t("card.load." + load.tier().name().toLowerCase(Locale.ROOT))),
                    SurvivalVitalsHud.tierColour(load.tier()), load.encumbranceRatio() / Math.max(0.01, severe)});
        }
        return rows;
    }

    /** How the body feels (the stage, or cool / comfortable / warm), the trend, and the core number only with a reading. */
    private static Component feeling(LocalPlayer player) {
        int cold = ClientTemperature.coldStage(), heat = ClientTemperature.heatStage();
        String word = cold > 0 ? "cold." + cold : heat > 0 ? "hot." + heat
                : ClientTemperature.coldness() > 0.05 ? "card.cool" : ClientTemperature.heat() > 0.05 ? "card.warm" : "card.comfortable";
        var text = t(word).copy();
        double trend = ClientTemperature.trend();
        if (trend < -0.3) text.append(" ↓"); else if (trend > 0.3) text.append(" ↑");
        var state = ClientTemperature.state();
        double core = state == null ? Double.NaN : TemperatureReadout.coreShown(player, state);
        if (!Double.isNaN(core)) text.append(String.format(Locale.ROOT, " %.1f°C", core));
        return text;
    }

    /** The card's corner: the air temperature (with a wrist thermometer) and how wet the body is. */
    private static Component air(LocalPlayer player) {
        var state = ClientTemperature.state();
        var text = Component.empty();
        boolean any = false;
        if (state != null && TemperatureReadout.ambientShown(player)) { text.append(t("card.air", Math.round(state.ambient()))); any = true; }
        double wet = ClientTemperature.wet();
        if (wet >= 0.02) { if (any) text.append(" · "); text.append(t("card.wet", Math.round(wet * 100))); any = true; }
        return any ? text : null;
    }

    private static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    // ---- the chat moves above the left stack ----

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Chat {
        private static long last;
        private Chat() {}

        @SubscribeEvent
        public static void chat(CustomizeGuiOverlayEvent.Chat event) {
            var mc = Minecraft.getInstance();
            long nanos = System.nanoTime();
            double dt = last == 0 ? 0 : Math.min(0.25, (nanos - last) / 1e9);
            last = nanos;
            int height = event.getWindow().getGuiScaledHeight();
            double target = 0;
            if (drawn && mc.screen == null)
                target = Math.max(0, event.getPosY() - SurvivalHudLayout.stackTop(height, shownBadges) + 3);
            chatLift += (target - chatLift) * (1 - Math.exp(-dt / CHAT_EASE));
            if (Math.abs(target - chatLift) < 0.3) chatLift = target;
            if (chatLift > 0.3) event.setPosY(event.getPosY() - (int)Math.round(chatLift));
        }
    }
}
