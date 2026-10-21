package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.registry.AflMobEffects;
import com.antaurora.apofirstlight.stamina.ClientStamina;
import com.antaurora.apofirstlight.temperature.ClientTemperature;
import com.antaurora.apofirstlight.temperature.TemperatureReadout;
import com.antaurora.apofirstlight.health.BodyPart;
import com.antaurora.apofirstlight.health.ClientHealth;
import com.antaurora.apofirstlight.health.Fractures;
import com.antaurora.apofirstlight.health.HealthConfig;
import com.antaurora.apofirstlight.health.PlayerHealth;
import com.antaurora.apofirstlight.health.Wounds;
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
import net.minecraft.network.chat.contents.TranslatableContents;
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
 * <li>Gaining a state (or a worse stage of it): its rim flashes and its name and effect slide out beside it for 4 s. When
 * only its effect line changes (a wound cleaned or bandaged, resting), the label slides out again without the flash
 * (user 2026-10-20: treating a wound showed nothing). A state back within 10 s of going, at no worse a stage, comes back
 * quietly; warming shows from a trend over 0.25 and goes under 0.1, so standing by a fire does not blink it.</li>
 * <li>Pressing the status key (Left Alt, rebindable; user 2026-10-20): every badge's label slides out together for 4 s,
 * no flash; once per {@link #RECALL_COOLDOWN} s, so a stray Alt does little.</li>
 * <li>Holding the status key, once the vital signs monitor exists: a card above the module with the body's numbers and
 * every state with its effect and time left; the slide-out labels step aside meanwhile. Off for now ({@link #DETAILS_CARD}).</li>
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
     * signs monitor (an advanced wrist watch) carries those readings; meanwhile the key only slides the labels out.
     */
    private static final boolean DETAILS_CARD = false;
    /** The status key slides the labels out at most once per this many seconds (labels 4 s out, 2 s in). */
    private static final double RECALL_COOLDOWN = 6;
    private static double recallAt = -1e9;
    private static final String LANG = "hud.apocalypse_firstlight.status.";
    /** How long a gained state's label stays out, its slide, the rim flash (s). */
    private static final double LABEL_SECONDS = 4, SLIDE = 0.22, FLASH = 0.7, CARD_FADE = 0.12, MOVE = 0.18, CHAT_EASE = 0.2;
    private static final int WHITE = 0xF6F2EA, DANGER_TEXT = 0xE8867C;

    /** A badge's kind: its colour and its rank (most urgent first, nearest the module). */
    public enum Kind {
        DANGER(0xB8443C), BAD(0xA86A3A), COLD(0x4F7FB8), GOOD(0x5FA67E);
        final int rgb;
        Kind(int rgb) { this.rgb = rgb; }
    }

    /** One state as shown: key (stable), stage (a higher stage of the same key re-announces it), kind, icon, name, effect;
     *  fraction of a timed state left (NaN: untimed) and the text beside the badge (time left, a countdown), or null. */
    public record Status(String key, int stage, Kind kind, AflRingIcon.Glyph icon, Component name, Component effect, double left, String aside) {
        /** Where it comes from and how it goes (the character page's status panel, on hover); one text for every fracture. */
        public Component detail() { return t((key.startsWith("fracture_") ? "fracture" : key) + ".detail"); }
    }
    public static final int DANGER_RGB = DANGER_TEXT;

    /** The local player's states, most urgent first (the character page's status panel; also in Creative). */
    public static List<Status> current(LocalPlayer player) {
        List<Status> states = new ArrayList<>(collect(player, System.nanoTime() / 1e9));
        states.sort(Comparator.comparingInt(s -> s.kind().ordinal()));
        return states;
    }

    /** A status badge at GUI coordinates, as in the column (no flash); `id` names its texture. */
    public static void drawBadge(GuiGraphics graphics, String id, Status s, double x, double y, double r, double alpha) {
        badge(graphics, id, s, x, y, r, 0, alpha);
    }

    private static final class Seen {
        int stage;
        /** The label's slide-out start and end, the rim flash's end (s). */
        double labelFrom, labelUntil, flashUntil, slot = Double.NaN, maxTicks;
        /** The effect line's translation key last frame: a new one (a wound cleaned or bandaged) slides the label out again. */
        String effectKey;
    }
    private static final Map<String, Seen> SEEN = new HashMap<>();
    /** A state that went away: when, and at which stage. Back within {@link #QUIET} s at no worse a stage: no flash, no label. */
    private record Gone(double at, int stage) {}
    private static final Map<String, Gone> GONE = new HashMap<>();
    private static final double QUIET = 10;
    /** Warming shows from a trend over {@link #WARMING_ON} and goes under {@link #WARMING_OFF} (no blinking at one edge). */
    private static final double WARMING_ON = 0.25, WARMING_OFF = 0.1;
    private static boolean warmingShown;
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
    public static void keys(RegisterKeyMappingsEvent event) { event.register(DETAILS); }

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
    // wounds (health V1a; user, 2026-10-19: one cross for all three could not be told apart): claw marks, a swollen cut,
    // a swollen cut with a drop of pus under it
    /** A line from a to b, w0 thick at its ends and w1 in its middle. */
    private static double taper(double x, double y, double ax, double ay, double bx, double by, double w0, double w1) {
        double dx = bx - ax, dy = by - ay, t = Math.max(0, Math.min(1, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(x - ax - dx * t, y - ay - dy * t) - (w0 + (w1 - w0) * (1 - Math.abs(2 * t - 1)));
    }
    /** A cut swollen round its lips: an almond along 35 deg up to the right, size k, the cut itself left dark. */
    private static double swollen(double x, double y, double cx, double cy, double k) {
        double u = ((x - cx) * 0.819 - (y - cy) * 0.574) / k, v = ((x - cx) * 0.574 + (y - cy) * 0.819) / k;
        double lens = Math.max(Math.hypot(u, v - 0.2753) - 0.4453, Math.hypot(u, v + 0.2753) - 0.4453);
        double cut = AflRingIcon.segment(u, v, -0.24, 0, 0.24, 0) - 0.04;
        return Math.max(lens, -cut) * k;
    }
    static final AflRingIcon.Glyph CLAW = (x, y) -> Math.min(Math.min(
            taper(x, y, 0.42, 0.16, 0.18, 0.80, 0.02, 0.065),
            taper(x, y, 0.62, 0.10, 0.38, 0.88, 0.02, 0.07)),
            taper(x, y, 0.82, 0.18, 0.58, 0.80, 0.02, 0.065));
    static final AflRingIcon.Glyph SWOLLEN = (x, y) -> swollen(x, y, 0.5, 0.5, 1.0);
    static final AflRingIcon.Glyph FESTERING = (x, y) -> Math.min(swollen(x, y, 0.46, 0.38, 0.86),
            Math.min(Math.hypot(x - 0.56, y - 0.77) - 0.105, AflRingIcon.convex(x, y, 0.56, 0.5, 0.655, 0.74, 0.465, 0.74)));
    // fractures (fractures V1, docs/gameplay/fracture_splint_v1_plan.md 7): a bone with a crack across its middle; splinted, a
    // slat along it on either side
    private static double bone(double x, double y) {
        double shaft = AflRingIcon.segment(x, y, 0.3, 0.7, 0.7, 0.3) - 0.07;
        double knobs = Math.min(Math.min(Math.hypot(x - 0.211, y - 0.711), Math.hypot(x - 0.289, y - 0.789)),
                Math.min(Math.hypot(x - 0.711, y - 0.211), Math.hypot(x - 0.789, y - 0.289))) - 0.075;
        double crack = Math.min(Math.min(AflRingIcon.segment(x, y, 0.42, 0.42, 0.5, 0.47), AflRingIcon.segment(x, y, 0.5, 0.47, 0.5, 0.53)),
                AflRingIcon.segment(x, y, 0.5, 0.53, 0.58, 0.58)) - 0.024;
        return Math.max(Math.min(shaft, knobs), -crack);
    }
    static final AflRingIcon.Glyph BONE = StatusBadgeHud::bone;
    static final AflRingIcon.Glyph BONE_SPLINTED = (x, y) -> Math.min(bone(x, y), Math.min(
            AflRingIcon.segment(x, y, 0.441, 0.841, 0.841, 0.441), AflRingIcon.segment(x, y, 0.159, 0.559, 0.559, 0.159)) - 0.036);
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
        wounds(list);
        fractures(list, player);
        // good: healing (health V1a: the server says whether natural regeneration runs, awake or resting)
        var regen = ClientHealth.regen();
        if (regen == PlayerHealth.Regen.AWAKE || regen == PlayerHealth.Regen.REST)
            list.add(new Status("recovering", 1, Kind.GOOD, SurvivalVitalsHud.HEART, t("recovering"),
                    t(regen == PlayerHealth.Regen.REST ? "recovering.rest" : "recovering.awake"), Double.NaN, null));
        warmingShown = ClientTemperature.coldness() > 0.02 && ClientTemperature.trend() > (warmingShown ? WARMING_OFF : WARMING_ON);
        if (warmingShown)
            list.add(new Status("warming", 1, Kind.GOOD, FLAME, t("warming"), t("warming.effect"), Double.NaN, null));
        return list;
    }

    /**
     * Wounds (health V1a): one badge per stage present (scratch, local infection, suppuration), its line saying what was done
     * and what to do next for the least treated wound of that stage; a cleaned infection / suppuration shows its recovery
     * as the arc (the time left of it).
     */
    private static void wounds(List<Status> list) {
        var wounds = ClientHealth.wounds();
        for (var stage : Wounds.Stage.values()) {
            Wounds.View least = null;
            int count = 0;
            for (var w : wounds) {
                if (w.stage() != stage) continue;
                count++;
                if (least == null || treated(w) < treated(least)) least = w;
            }
            if (least == null) continue;
            String name = stage.name().toLowerCase(Locale.ROOT);
            String care = least.cleaned() && least.bandaged() ? "treated" : least.cleaned() ? "cleaned" : least.bandaged() ? "bandaged" : "untreated";
            Kind kind = stage == Wounds.Stage.SUPPURATION && !least.cleaned() ? Kind.DANGER : Kind.BAD;
            double left = Float.isNaN(least.recovery()) ? Double.NaN : 1 - least.recovery();
            AflRingIcon.Glyph glyph = switch (stage) { case SCRATCH -> CLAW; case INFECTION -> SWOLLEN; case SUPPURATION -> FESTERING; };
            list.add(new Status("wound_" + name, stage.ordinal() + 1, kind, glyph,
                    count > 1 ? t("wound." + name + ".many", count) : t("wound." + name), t("wound." + name + "." + care), left, null));
        }
    }
    /**
     * Fractures: one badge per broken limb (brown, never red: a fracture does not kill by itself), its line what it costs
     * now; splinted, the arc is the healing still to go (none before: unsplinted it heals too slowly to watch).
     */
    private static void fractures(List<Status> list, LocalPlayer player) {
        for (var f : ClientHealth.fractures()) list.add(fracture(f, player));
    }

    /** One fracture as its badge (the HUD column, the inventory's body map). */
    public static Status fracture(Fractures.View f, net.minecraft.world.entity.player.Player player) {
        String limb = f.part().key();
        String effect = f.splinted() ? "fracture.splinted" : f.part().leg() ? "fracture.leg"
                : f.part().mainArm(player) ? "fracture.main_arm" : "fracture.support_arm";
        return new Status("fracture_" + limb, f.splinted() ? 1 : 2, Kind.BAD, f.splinted() ? BONE_SPLINTED : BONE,
                t("fracture." + limb), t(effect), f.splinted() ? 1 - f.progress() : Double.NaN, null);
    }

    /** One wound as its stage's badge, its own care line (the inventory's body map: the wounds of one part). */
    public static Status wound(Wounds.View w) {
        String name = w.stage().name().toLowerCase(Locale.ROOT);
        String care = w.cleaned() && w.bandaged() ? "treated" : w.cleaned() ? "cleaned" : w.bandaged() ? "bandaged" : "untreated";
        Kind kind = w.stage() == Wounds.Stage.SUPPURATION && !w.cleaned() ? Kind.DANGER : Kind.BAD;
        AflRingIcon.Glyph glyph = switch (w.stage()) { case SCRATCH -> CLAW; case INFECTION -> SWOLLEN; case SUPPURATION -> FESTERING; };
        return new Status("wound_" + name, w.stage().ordinal() + 1, kind, glyph, t("wound." + name), t("wound." + name + "." + care),
                Float.isNaN(w.recovery()) ? Double.NaN : 1 - w.recovery(), null);
    }

    private static int treated(Wounds.View w) {
        return (w.cleaned() ? 1 : 0) + (w.bandaged() ? 1 : 0);
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
                t("thirst.3.effect", (int)Math.round(c.damageIntervalSeconds),
                (int)Math.round(HealthConfig.get().damage.defaultScale)), Double.NaN, null));
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
        boolean pressed = false;
        while (DETAILS.consumeClick()) pressed = true;   // drained every frame, so a press while hidden does not fire later
        if (player == null) { SEEN.clear(); GONE.clear(); warmingShown = false; thirstValue = Double.NaN; return; }
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements() || FieldAttachmentViewState.isActive()) return;
        drawn = true;
        List<Status> states = new ArrayList<>(collect(player, now));
        states.sort(Comparator.comparingInt(s -> s.kind().ordinal()));
        var keys = states.stream().map(Status::key).toList();
        for (var e : SEEN.entrySet()) if (!keys.contains(e.getKey())) GONE.put(e.getKey(), new Gone(now, e.getValue().stage));
        SEEN.keySet().retainAll(keys);
        GONE.values().removeIf(g -> now - g.at() > QUIET);
        boolean open = DETAILS_CARD && mc.screen == null && DETAILS.isDown();
        card += ((open ? 1 : 0) - card) * (1 - Math.exp(-dt / CARD_FADE));
        if (Math.abs(card - (open ? 1 : 0)) < 0.01) card = open ? 1 : 0;
        shownBadges = states.size();
        boolean recall = pressed && !DETAILS_CARD && !states.isEmpty() && now - recallAt >= RECALL_COOLDOWN;
        if (recall) recallAt = now;
        Font font = mc.font;
        for (int i = 0; i < states.size(); i++) {
            var s = states.get(i);
            Seen seen = SEEN.get(s.key());
            if (seen == null) {
                seen = new Seen();
                Gone gone = GONE.remove(s.key());
                if (gone != null && s.stage() <= gone.stage()) seen.stage = s.stage();   // back soon, no worse: quietly
                SEEN.put(s.key(), seen);
            }
            String effectKey = s.effect().getContents() instanceof TranslatableContents tc ? tc.getKey() : s.effect().getString();
            if (s.stage() > seen.stage) { label(seen, now); seen.flashUntil = now + FLASH; }
            else if (recall || seen.effectKey != null && !seen.effectKey.equals(effectKey)) label(seen, now);   // the status key; cleaned, bandaged, resting: the label again, no flash
            seen.stage = s.stage();
            seen.effectKey = effectKey;
            seen.slot = Double.isNaN(seen.slot) ? i : seen.slot + (i - seen.slot) * (1 - Math.exp(-dt / MOVE));
            double y = SurvivalHudLayout.badgeY(screenHeight, 0) - seen.slot * SurvivalHudLayout.BADGE_STEP;
            if (card < 0.99) badge(graphics, "badge_" + s.key(), s, SurvivalHudLayout.BADGE_X, y, SurvivalHudLayout.BADGE_R,
                    Math.max(0, seen.flashUntil - now) / FLASH, 1 - card);
            double slide = smooth(Math.min((now - seen.labelFrom) / SLIDE, (seen.labelUntil - now) / SLIDE)) * (1 - card);
            aside(graphics, font, s, SurvivalHudLayout.BADGE_X + SurvivalHudLayout.BADGE_R + 2, y, slide, 1 - card);
        }
        if (card > 0.01) card(graphics, font, player, states, screenHeight, card);
    };

    /** Slides the label out for {@link #LABEL_SECONDS} s; already out, it stays out that much longer. */
    private static void label(Seen seen, double now) {
        if (now >= seen.labelUntil) seen.labelFrom = now;
        seen.labelUntil = now + LABEL_SECONDS;
    }

    /** A badge: shadow, the kind's disc, a light sheen and rim, the time-left arc, the white pictogram, the gained flash. */
    private static void badge(GuiGraphics graphics, String id, Status s, double x, double y, double r, double flash, double alpha) {
        List<AflGauge.Band> bands = new ArrayList<>();
        bands.add(AflGauge.Band.disc(r + 0.7, 0x000000, 0.22 * alpha));
        bands.add(AflGauge.Band.disc(r, s.kind().rgb, 0.95 * alpha));
        bands.add(AflGauge.Band.line(r - 0.6, 0.5, 300, 120, 0xFFFFFF, 0.14 * alpha));
        bands.add(AflGauge.Band.line(r + 0.2, 0.5, 0, 360, AflUiStyle.LINE, 0.4 * alpha));
        if (!Double.isNaN(s.left()))
            bands.add(new AflGauge.Band(r + 1.6, 0.8, 0, 360, 0, 0, 0, s.left(), 0xF0ECE4, 0.9 * alpha, AflGauge.BACK, 0.4 * alpha));
        if (flash > 0) bands.add(AflGauge.Band.line(r + 2.6 + (1 - flash) * 1.5, 0.6, 0, 360, AflUiStyle.LINE, 0.7 * flash * alpha));
        AflGauge.draw(graphics, id, x, y, r + 4.7, bands,
                new AflGauge.Glyph(s.icon(), r * 1.18, WHITE, alpha));
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
