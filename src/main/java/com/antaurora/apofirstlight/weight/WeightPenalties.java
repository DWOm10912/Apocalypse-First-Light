package com.antaurora.apofirstlight.weight;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import java.util.UUID;

/**
 * Movement penalties of an EncumbranceState (docs/gameplay/weight_system_v1.md).
 * - Speed: a transient MULTIPLY_TOTAL movement speed modifier, set by the server, synced as an attribute; never saved.
 *   Its FOV narrowing is divided out on the client (ClientWeightPenalties#fov).
 * - Sprint: the client refuses to start or keep a sprint (mixin LocalPlayerWeightSprintMixin, like an empty stomach);
 *   the server drops a sprint flag it still receives.
 * - Jump: the local player's jump velocity, on the client (ClientWeightPenalties#jump); movement is client-side.
 */
public final class WeightPenalties {
    public static final UUID SPEED_MODIFIER = UUID.fromString("6f1d2b8e-4c3a-4e57-9a1b-2d7c5e9f0a13");
    private WeightPenalties() {}

    static void apply(ServerPlayer player, EncumbranceState state) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        double amount = state.speedMultiplier() - 1.0;
        var current = speed.getModifier(SPEED_MODIFIER);
        if (current != null && Math.abs(current.getAmount() - amount) < 1e-6) return;
        if (current != null) speed.removeModifier(SPEED_MODIFIER);
        if (Math.abs(amount) >= 1e-6) speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER,
                "AFL encumbrance", amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    static void enforceSprint(ServerPlayer player, EncumbranceState state) {
        if (state.sprintBlocked() && player.isSprinting() && !player.isPassenger()) player.setSprinting(false);
    }

    /** Scales the upward velocity a jump just set (Forge LivingJumpEvent fires at the end of jumpFromGround). */
    static void scaleJump(Player player, double multiplier) {
        if (multiplier >= 1.0) return;
        var v = player.getDeltaMovement();
        if (v.y > 0) player.setDeltaMovement(v.x, v.y * multiplier, v.z);
    }
}
