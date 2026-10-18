package com.antaurora.apofirstlight.client;

/**
 * HUD V2 (2026-10-16, docs/ui/hud_v2_research.md): the body module at the bottom left (GUI pixels). A round module: the
 * health arc on its left (armour as a thin lane outside it), the stamina arc on its right (air as a lane outside it under
 * water), the temperature dial in the middle, the heart and the health number in the bottom gap; three small needs rings
 * (hunger, thirst, load) round its upper right; the status badges in a column above it (client/StatusBadgeHud). It
 * replaced Survival HUD V1's centred cluster over the hotbar.
 */
public final class SurvivalHudLayout {
    /** The temperature dial's size factor over TemperatureDial's own numbers (geometry only). */
    public static final double SCALE = 1.15;
    /** The module's centre: from the left edge, above the bottom edge. */
    public static final double CENTRE_X = 40, CENTRE_Y = 40;
    /** Health / stamina arcs: outer radius and band; health from the bottom left (200°) up to the top left (340°), stamina
     *  from the top right (20°) down to the bottom right (160°), degrees clockwise from the top. The radius keeps the dial's
     *  air temperature arc (TemperatureDial.AMBIENT_BASE × SCALE = 18.2) clear of the bands. */
    public static final double RADIUS = 22, BAND = 3.2;
    public static final double HEALTH_START = 200, STAMINA_START = 20, ARC_SPAN = 140, SEGMENT_GAP = 0.8;
    /** Outer lanes (armour beside health, air beside stamina): gap to the arc, width. */
    public static final double LANE_GAP = 0.8, LANE_WIDTH = 1.3;
    /** The soft dark disc behind the module: radius over RADIUS, alpha. */
    public static final double BACKING = 3.6, BACKING_ALPHA = 0.32;
    /** Heart and health number in the bottom gap: heart size, the row's centre below the module centre. */
    public static final double HEART_SIZE = 4.4, NUMBER_ROW = 20.2;
    /** Needs rings (hunger, thirst, load): their angles (degrees clockwise from the top) and distance from the centre;
     *  quiet: radius, band, icon; grown (the need matters, with a number): radius, band, icon. */
    public static final double[] NEED_ANGLES = {22, 52, 82};
    public static final double NEED_DISTANCE = 36.5, NEED_R = 5.2, NEED_BAND = 1.4, NEED_ICON = 4.2, NEED_ALPHA = 0.8,
            NEED_R_GROWN = 7.4, NEED_BAND_GROWN = 1.8, NEED_ICON_GROWN = 3.6;
    /** Status badges: column centre from the left edge, the lowest badge's centre above the module centre, step upward,
     *  radius. */
    public static final double BADGE_X = 12, BADGE_RISE = 52, BADGE_STEP = 13.5, BADGE_R = 5.4;
    /** Faint light outline round arcs, rings and the dial's bezel: colour, width (GUI px), alpha. */
    public static final int OUTLINE = 0xE8E4DC;
    public static final double OUTLINE_WIDTH = 0.3, OUTLINE_ALPHA = 0.28;
    private SurvivalHudLayout() {}

    public static double centreX() { return CENTRE_X; }
    public static double centreY(int screenHeight) { return screenHeight - CENTRE_Y; }

    /** Centre of needs ring i (0 hunger, 1 thirst, 2 load). */
    public static double needX(int i) { return CENTRE_X + Math.sin(Math.toRadians(NEED_ANGLES[i])) * NEED_DISTANCE; }
    public static double needY(int screenHeight, int i) { return centreY(screenHeight) - Math.cos(Math.toRadians(NEED_ANGLES[i])) * NEED_DISTANCE; }

    /** Centre height of badge i (0 the lowest). */
    public static double badgeY(int screenHeight, int i) { return centreY(screenHeight) - BADGE_RISE - i * BADGE_STEP; }

    /** Top of the left HUD stack with `badges` badges showing (GUI y): the chat moves up above it. */
    public static double stackTop(int screenHeight, int badges) {
        double module = needY(screenHeight, 0) - NEED_R_GROWN - 1;
        return badges <= 0 ? module : Math.min(module, badgeY(screenHeight, badges - 1) - BADGE_R - 2);
    }
}
