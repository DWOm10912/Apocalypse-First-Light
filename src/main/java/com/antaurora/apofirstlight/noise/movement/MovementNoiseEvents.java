package com.antaurora.apofirstlight.noise.movement;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import com.antaurora.apofirstlight.noise.NoiseSystem;
import com.antaurora.apofirstlight.noise.NoiseType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MovementNoiseEvents {
    private static final double SNEAK_RADIUS = 3.0;
    private static final double WALK_RADIUS = 6.0;
    private static final double SPRINT_RADIUS = 12.0;
    private static final double SNEAK_STEP_DISTANCE = 1.0;
    private static final double WALK_STEP_DISTANCE = 0.9;
    private static final double SPRINT_STEP_DISTANCE = 1.2;
    private static final double LIGHT_LANDING_RADIUS = 10.0;
    private static final double HEAVY_LANDING_RADIUS = 16.0;
    private static final double VERY_HEAVY_LANDING_RADIUS = 24.0;
    private static final Map<ServerPlayer, MovementNoiseState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private MovementNoiseEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        MovementNoiseState state = STATES.computeIfAbsent(player, ignored -> new MovementNoiseState());
        boolean onGround = player.onGround();
        if (!state.initialized) {
            initialize(state, player, onGround);
            return;
        }

        if (!onGround) {
            state.maxAirborneFallDistance = Math.max(state.maxAirborneFallDistance, player.fallDistance);
        } else if (!state.wasOnGround && isGroundMovementEligible(player)) {
            emitLanding(player, state.maxAirborneFallDistance);
            state.maxAirborneFallDistance = 0.0F;
        } else if (onGround) {
            state.maxAirborneFallDistance = 0.0F;
        }

        if (isGroundMovementEligible(player)) {
            emitFootstepIfDue(player, state);
        } else {
            state.accumulatedHorizontalDistance = 0.0;
        }

        state.previousX = player.getX();
        state.previousZ = player.getZ();
        state.wasOnGround = onGround;
    }

    private static void initialize(MovementNoiseState state, ServerPlayer player, boolean onGround) {
        state.initialized = true;
        state.previousX = player.getX();
        state.previousZ = player.getZ();
        state.wasOnGround = onGround;
        state.maxAirborneFallDistance = player.fallDistance;
    }

    private static boolean isGroundMovementEligible(ServerPlayer player) {
        return player.onGround()
                && !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isFallFlying()
                && !player.isSwimming()
                && !player.isPassenger();
    }

    private static void emitFootstepIfDue(ServerPlayer player, MovementNoiseState state) {
        double movedX = player.getX() - state.previousX;
        double movedZ = player.getZ() - state.previousZ;
        state.accumulatedHorizontalDistance += Math.sqrt(movedX * movedX + movedZ * movedZ);

        MovementMode mode = movementMode(player);
        if (state.accumulatedHorizontalDistance < mode.stepDistance()) {
            return;
        }
        state.accumulatedHorizontalDistance -= mode.stepDistance();
        NoiseSystem.emit(new NoiseEvent(player, player.position(), NoiseType.FOOTSTEP,
                player.level().getGameTime(), null, mode.radius()));
        ApocalypseFirstLight.LOGGER.debug("[AFL MOVEMENT NOISE] Type=FOOTSTEP Mode={} Radius={} Player={}",
                mode, mode.radius(), player.getGameProfile().getName());
    }

    private static void emitLanding(ServerPlayer player, float fallDistance) {
        double radius = fallDistance <= 1.0F ? 0.0
                : fallDistance <= 3.0F ? LIGHT_LANDING_RADIUS
                : fallDistance <= 6.0F ? HEAVY_LANDING_RADIUS
                : VERY_HEAVY_LANDING_RADIUS;
        if (radius == 0.0) {
            return;
        }
        NoiseSystem.emit(new NoiseEvent(player, player.position(), NoiseType.LANDING,
                player.level().getGameTime(), null, radius));
        ApocalypseFirstLight.LOGGER.debug("[AFL MOVEMENT NOISE] Type=LANDING FallDistance={} Radius={} Player={}",
                fallDistance, radius, player.getGameProfile().getName());
    }

    private static MovementMode movementMode(ServerPlayer player) {
        if (player.isCrouching()) {
            return MovementMode.SNEAK;
        }
        return player.isSprinting() ? MovementMode.SPRINT : MovementMode.WALK;
    }

    private enum MovementMode {
        SNEAK(SNEAK_RADIUS, SNEAK_STEP_DISTANCE),
        WALK(WALK_RADIUS, WALK_STEP_DISTANCE),
        SPRINT(SPRINT_RADIUS, SPRINT_STEP_DISTANCE);

        private final double radius;
        private final double stepDistance;

        MovementMode(double radius, double stepDistance) {
            this.radius = radius;
            this.stepDistance = stepDistance;
        }

        double radius() { return radius; }
        double stepDistance() { return stepDistance; }
    }
}
