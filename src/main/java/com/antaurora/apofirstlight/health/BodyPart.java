package com.antaurora.apofirstlight.health;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * Where on the body an injury is (docs/ui/body_silhouette_v1_plan.md 3, docs/gameplay/fracture_splint_v1_plan.md 10): the
 * seven parts of the inventory's body figure. Only a place, not a health pool of its own: wounds and fractures keep their own
 * rules; the part says where they are drawn (the figure, the gauze, the splint) and which arm a fracture hinders.
 * Right and left are the body's own (the figure is seen from the front, so its right is on the screen's left).
 */
public enum BodyPart {
    HEAD, CHEST, BELLY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG;

    private static final BodyPart[] ALL = values();
    /**
     * Where an infected's scratch lands (user 2026-10-20, the recommended weights): mostly the arms raised against it, then
     * the chest and belly, the legs least, never the head. In BodyPart order.
     */
    private static final double[] SCRATCH_WEIGHTS = {0, 15, 10, 30, 30, 7.5, 7.5};

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean arm() {
        return this == RIGHT_ARM || this == LEFT_ARM;
    }

    public boolean leg() {
        return this == RIGHT_LEG || this == LEFT_LEG;
    }

    public boolean limb() {
        return arm() || leg();
    }

    public boolean right() {
        return this == RIGHT_ARM || this == RIGHT_LEG;
    }

    /** The bit of this part in a mask (HealthPackets.Marks). */
    public int bit() {
        return 1 << ordinal();
    }

    /** The arm that holds the gun and swings the tool (no off hand: the main arm, right unless the player is left-handed). */
    public boolean mainArm(Player player) {
        return arm() && right() == (player.getMainArm() == HumanoidArm.RIGHT);
    }

    public static BodyPart byOrdinal(int i) {
        return ALL[Math.max(0, Math.min(ALL.length - 1, i))];
    }

    /** A saved name, or null. */
    public static BodyPart parse(String name) {
        for (BodyPart p : ALL) if (p.key().equals(name)) return p;
        return null;
    }

    public static BodyPart scratchPart(RandomSource random) {
        double total = 0;
        for (double w : SCRATCH_WEIGHTS) total += w;
        double r = random.nextDouble() * total;
        for (int i = 0; i < ALL.length; i++) {
            r -= SCRATCH_WEIGHTS[i];
            if (r < 0 && SCRATCH_WEIGHTS[i] > 0) return ALL[i];
        }
        return LEFT_ARM;
    }

    public static BodyPart randomArm(RandomSource random) {
        return random.nextBoolean() ? RIGHT_ARM : LEFT_ARM;
    }

    public static BodyPart randomLeg(RandomSource random) {
        return random.nextBoolean() ? RIGHT_LEG : LEFT_LEG;
    }
}
