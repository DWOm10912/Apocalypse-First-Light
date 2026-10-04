package com.antaurora.apofirstlight.client;

/**
 * Where the survival cluster sits (GUI pixels): the temperature dial at the screen centre above the hearts / food rows,
 * the stamina ring left of it and the thirst ring right of it. High enough that the rings clear the armour row (left)
 * and the air bubbles (right), which vanilla draws 40-49 px above the bottom; the dial fits between those two columns.
 */
public final class SurvivalHudLayout {
    /** Centre height above the screen bottom, shared by the dial and the two rings. */
    public static final double CENTRE_Y = 55;
    /** The rings' centres left / right of the screen centre. */
    public static final double RING_OFFSET_X = 19;
    private SurvivalHudLayout() {}
}
