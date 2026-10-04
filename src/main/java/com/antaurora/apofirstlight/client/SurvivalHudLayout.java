package com.antaurora.apofirstlight.client;

/**
 * The survival cluster above the hotbar (GUI pixels; Survival HUD V1, 2026-10-03): one tight group, centred on the screen,
 * nested like Winter Rescue's: left to right the stamina crescent "(" (air as one more arc outside it, only under water),
 * the health ring (armour as an arc over its top, the health number inside), the temperature dial with its dark metal
 * bezel, the hunger ring (the mount's health while riding one), the thirst crescent ")". Crescents open toward the centre
 * with their icon in the opening on a faint dark disc; rings and crescents carry a faint light outline. Vanilla hearts,
 * food, armour, air and mount hearts are hidden. Everything is SCALE times the original sizes (1.0 was small, 1.25 a
 * little big and too low); the item name and the action bar are lifted above the group.
 */
public final class SurvivalHudLayout {
    /** Size factor over the original rings and dial (geometry only; the half-size numbers stay pixel-exact). */
    public static final double SCALE = 1.15;
    /** Centre height above the screen bottom, shared by every gauge: the dial's trend chevrons (its lowest part, 15.2 px
     *  below the centre) stay ~4 px above the load bar (24-29 px above the bottom). */
    public static final double CENTRE_Y = 48;
    /** Temperature dial face radius and its bezel width (TemperatureDial.RADIUS / BEZEL x SCALE). */
    public static final double DIAL_RADIUS = 9.5 * SCALE, DIAL_BEZEL = 1.15 * SCALE;
    /** Health / hunger rings: outer radius, band width, segment gap along the band; centres just clear of the bezel. */
    public static final double VITAL_RADIUS = 7.5 * SCALE, VITAL_BAND = 2.1 * SCALE, SEGMENT_GAP = 0.85 * SCALE;
    public static final double VITAL_OFFSET_X = DIAL_RADIUS + DIAL_BEZEL + 0.45 * SCALE + VITAL_RADIUS;
    /** Stamina / thirst crescents: outer radius, band width; centred on their icon, which sits clear of the big ring. */
    public static final double CRESCENT_RADIUS = 6.1 * SCALE, CRESCENT_BAND = 1.55 * SCALE;
    public static final double CRESCENT_OFFSET_X = VITAL_OFFSET_X + VITAL_RADIUS + 3.6 * SCALE;
    /** How far past the vertical each crescent's tips run (degrees): until their outer corner meets the big ring's
     *  outline, so crescent and ring read as one piece with the icon held between them (user 2026-10-04). */
    public static final double CRESCENT_REACH = reach();
    /** Icon in a crescent: glyph size, the faint dark disc behind it (radius, alpha). */
    public static final double CRESCENT_ICON = 4.7 * SCALE, ICON_DISC = 2.9 * SCALE, ICON_DISC_ALPHA = 0.35;
    /** The dark translucent face inside the health / hunger rings. */
    public static final double FACE_ALPHA = 0.45;
    /** Outer arcs (armour over health, air outside the stamina crescent): gap to their gauge, width. */
    public static final double LANE_GAP = 0.9 * SCALE, LANE_WIDTH = 0.95 * SCALE;
    /** Faint light outline round rings, crescents and the dial's bezel (Winter Rescue-like): colour, width (GUI px), alpha. */
    public static final int OUTLINE = 0xE8E4DC;
    public static final double OUTLINE_WIDTH = 0.3, OUTLINE_ALPHA = 0.28;
    /** The selected item's name and the action bar move up by this much while the cluster shows (vanilla: 50-68 px), clear
     *  of the air temperature number above the dial (top ~71 px). */
    public static final int TEXT_LIFT = 26;
    private SurvivalHudLayout() {}

    /** Where the crescent's outer circle crosses the big ring's outline, as an angle from the vertical (degrees). */
    private static double reach() {
        double a = CRESCENT_RADIUS, b = VITAL_RADIUS + OUTLINE_WIDTH, d = CRESCENT_OFFSET_X - VITAL_OFFSET_X;
        double x = (a * a - b * b + d * d) / (2 * d), y = Math.sqrt(Math.max(0, a * a - x * x));
        return Math.toDegrees(Math.atan2(x, y));
    }

    /** A crescent gauge: its band (opening toward the centre, side -1 is "(" on the left, +1 is ")" on the right, its tips
     *  running CRESCENT_REACH past the vertical to the big ring), filled from the bottom tip up, with its outlines, the
     *  icon disc and the icon. */
    public static void crescent(net.minecraft.client.gui.GuiGraphics graphics, String name, double cx, double cy, int side,
                                double fill, int rgb, AflRingIcon.Glyph icon) {
        double r = CRESCENT_RADIUS, band = CRESCENT_BAND, span = 180 + 2 * CRESCENT_REACH;
        double start = side < 0 ? 180 - CRESCENT_REACH : 360 - CRESCENT_REACH;   // "(": bottom tip round the left; ")": top tip round the right
        double from = side < 0 ? 0 : 1 - fill, to = side < 0 ? fill : 1;
        var bands = java.util.List.of(
                AflGauge.Band.disc(ICON_DISC, AflGauge.BACK, ICON_DISC_ALPHA),
                AflGauge.Band.arc(r, band, start, span, 0, 0, from, to, rgb, 1),
                AflGauge.Band.line(r + OUTLINE_WIDTH, OUTLINE_WIDTH, start, span, OUTLINE, OUTLINE_ALPHA),
                AflGauge.Band.line(r - band, OUTLINE_WIDTH, start, span, OUTLINE, OUTLINE_ALPHA));
        AflGauge.draw(graphics, name, cx, cy, r + OUTLINE_WIDTH + 0.5, bands, new AflGauge.Glyph(icon, CRESCENT_ICON, rgb, 1));
    }
}
