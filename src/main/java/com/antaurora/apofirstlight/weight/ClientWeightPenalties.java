package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The local player's side of WeightPenalties, from the server's last state for this player. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class ClientWeightPenalties {
    private ClientWeightPenalties() {}

    private static EncumbranceState active() {
        var state = ClientWeightState.state();
        return state != null && state.penaltiesEnabled() ? state : null;
    }

    /** mixin client/LocalPlayerWeightSprintMixin */
    public static boolean sprintBlocked() {
        var state = active();
        return state != null && state.sprintBlocked();
    }

    @SubscribeEvent public static void jump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        var state = active();
        if (state != null) WeightPenalties.scaleJump(player, state.jumpMultiplier());
    }

    /**
     * Vanilla narrows the FOV with movement speed (× (speed / walking speed + 1) / 2, like Slowness). A heavy load should
     * not zoom the view, so the encumbrance modifier's share of that term is divided out; flying, sprinting and the bow
     * draw stay. Not while scoping (vanilla's fixed spyglass value).
     */
    @SubscribeEvent public static void fov(ComputeFovModifierEvent event) {
        var player = event.getPlayer();
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var modifier = speed == null ? null : speed.getModifier(WeightPenalties.SPEED_MODIFIER);
        float walking = player.getAbilities().getWalkingSpeed();
        if (modifier == null || walking <= 0 || player.isScoping() || modifier.getAmount() <= -1) return;
        double with = speed.getValue(), without = with / (1 + modifier.getAmount());
        double scale = (without / walking + 1) / (with / walking + 1);
        if (Double.isFinite(scale)) event.setNewFovModifier((float)(event.getNewFovModifier() * scale));
    }
}
