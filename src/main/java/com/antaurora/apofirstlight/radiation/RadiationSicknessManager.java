package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.registry.AflMobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative consequences derived from a player's cumulative absorbed dose. */
public final class RadiationSicknessManager {
    public static final double MILD_DOSE_THRESHOLD = 2.0D;
    public static final double MODERATE_DOSE_THRESHOLD = 5.0D;
    public static final double SEVERE_DOSE_THRESHOLD = 10.0D;
    public static final double CRITICAL_DOSE_THRESHOLD = 20.0D;

    public static final UUID MOVEMENT_SPEED_MODIFIER_ID =
            UUID.fromString("a83f58bf-3c45-4f21-9e4c-1764b7fc0cc8");
    private static final String MOVEMENT_SPEED_MODIFIER_NAME =
            "apocalypse_firstlight.radiation_sickness_movement";
    private static final int EFFECT_DURATION_TICKS = 40;
    private static final int CRITICAL_DAMAGE_INTERVAL_TICKS = 100;
    private static final float CRITICAL_DAMAGE_AMOUNT = 1.0F;
    private static final int STAGE_THREE_NAUSEA_COOLDOWN_MIN_TICKS = 900;
    private static final int STAGE_THREE_NAUSEA_COOLDOWN_MAX_TICKS = 1_500;
    private static final int STAGE_THREE_NAUSEA_DURATION_MIN_TICKS = 200;
    private static final int STAGE_THREE_NAUSEA_DURATION_MAX_TICKS = 240;
    private static final int STAGE_FOUR_NAUSEA_COOLDOWN_MIN_TICKS = 500;
    private static final int STAGE_FOUR_NAUSEA_COOLDOWN_MAX_TICKS = 900;
    private static final int STAGE_FOUR_NAUSEA_DURATION_MIN_TICKS = 320;
    private static final int STAGE_FOUR_NAUSEA_DURATION_MAX_TICKS = 480;
    private static final int NAUSEA_SCHEDULE_STEP_TICKS = 20;
    private static final Map<UUID, NauseaSchedule> NAUSEA_SCHEDULES = new HashMap<>();

    private RadiationSicknessManager() {
    }

    public static int getStage(double dose) {
        if (!Double.isFinite(dose) || dose < MILD_DOSE_THRESHOLD) return 0;
        if (dose < MODERATE_DOSE_THRESHOLD) return 1;
        if (dose < SEVERE_DOSE_THRESHOLD) return 2;
        if (dose < CRITICAL_DOSE_THRESHOLD) return 3;
        return 4;
    }

    public static double movementModifier(int stage) {
        return switch (stage) {
            case 1 -> -0.05D;
            case 2 -> -0.10D;
            case 3 -> -0.15D;
            case 4 -> -0.20D;
            default -> 0.0D;
        };
    }

    public static float healingMultiplier(int stage) {
        return switch (stage) {
            case 2 -> 0.75F;
            case 3 -> 0.50F;
            case 4 -> 0.25F;
            default -> 1.0F;
        };
    }

    public static void update(ServerPlayer player) {
        int stage = currentStage(player);
        applyStage(player, stage);
        syncEffect(player, stage);
        updateNausea(player, stage);
        if (stage == 4 && player.isAlive() && player.tickCount > 0
                && player.tickCount % CRITICAL_DAMAGE_INTERVAL_TICKS == 0) {
            player.hurt(player.damageSources().magic(), CRITICAL_DAMAGE_AMOUNT);
        }
    }

    public static void refreshState(ServerPlayer player) {
        int stage = currentStage(player);
        applyStage(player, stage);
        syncEffect(player, stage);
        NAUSEA_SCHEDULES.remove(player.getUUID());
        if (stage >= 3) {
            scheduleNextNausea(player, stage, player.serverLevel().getGameTime());
        }
    }

    public static void clearTransientState(ServerPlayer player) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.removeModifier(MOVEMENT_SPEED_MODIFIER_ID);
        }
        NAUSEA_SCHEDULES.remove(player.getUUID());
    }

    public static void applyStage(ServerPlayer player, int stage) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed == null) return;

        double amount = movementModifier(stage);
        AttributeModifier existing = movementSpeed.getModifier(MOVEMENT_SPEED_MODIFIER_ID);
        if (amount == 0.0D) {
            if (existing != null) movementSpeed.removeModifier(existing);
            return;
        }

        if (existing != null && existing.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL
                && Double.compare(existing.getAmount(), amount) == 0) {
            return;
        }
        if (existing != null) movementSpeed.removeModifier(existing);
        movementSpeed.addTransientModifier(new AttributeModifier(MOVEMENT_SPEED_MODIFIER_ID,
                MOVEMENT_SPEED_MODIFIER_NAME, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void syncEffect(ServerPlayer player, int stage) {
        MobEffectInstance current = player.getEffect(AflMobEffects.RADIATION_SICKNESS.get());
        if (stage == 0) {
            if (current != null) player.removeEffect(AflMobEffects.RADIATION_SICKNESS.get());
            return;
        }

        int amplifier = stage - 1;
        if (current != null && current.getAmplifier() != amplifier) {
            player.removeEffect(AflMobEffects.RADIATION_SICKNESS.get());
        }
        player.addEffect(new MobEffectInstance(AflMobEffects.RADIATION_SICKNESS.get(),
                EFFECT_DURATION_TICKS, amplifier, false, false, true));
    }

    private static void updateNausea(ServerPlayer player, int stage) {
        UUID playerId = player.getUUID();
        if (stage < 3) {
            NAUSEA_SCHEDULES.remove(playerId);
            return;
        }

        long now = player.serverLevel().getGameTime();
        NauseaSchedule schedule = NAUSEA_SCHEDULES.get(playerId);
        if (schedule == null || schedule.stage() != stage) {
            scheduleNextNausea(player, stage, now);
            return;
        }
        if (now < schedule.nextTick()) return;

        int currentStage = currentStage(player);
        if (!player.isAlive() || player.isRemoved() || currentStage != stage) {
            if (currentStage < 3) {
                NAUSEA_SCHEDULES.remove(playerId);
            } else {
                scheduleNextNausea(player, currentStage, now);
            }
            return;
        }

        int duration = randomScheduledDelay(player,
                stage == 3 ? STAGE_THREE_NAUSEA_DURATION_MIN_TICKS : STAGE_FOUR_NAUSEA_DURATION_MIN_TICKS,
                stage == 3 ? STAGE_THREE_NAUSEA_DURATION_MAX_TICKS : STAGE_FOUR_NAUSEA_DURATION_MAX_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 0,
                false, false, false));
        scheduleNextNausea(player, stage, now + duration);
    }

    private static void scheduleNextNausea(ServerPlayer player, int stage, long cooldownStartTick) {
        int minCooldown = stage == 3
                ? STAGE_THREE_NAUSEA_COOLDOWN_MIN_TICKS : STAGE_FOUR_NAUSEA_COOLDOWN_MIN_TICKS;
        int maxCooldown = stage == 3
                ? STAGE_THREE_NAUSEA_COOLDOWN_MAX_TICKS : STAGE_FOUR_NAUSEA_COOLDOWN_MAX_TICKS;
        NAUSEA_SCHEDULES.put(player.getUUID(),
                new NauseaSchedule(stage,
                        cooldownStartTick + randomScheduledDelay(player, minCooldown, maxCooldown)));
    }

    private static int randomScheduledDelay(ServerPlayer player, int minimum, int maximum) {
        int steps = (maximum - minimum) / NAUSEA_SCHEDULE_STEP_TICKS;
        return minimum + player.getRandom().nextInt(steps + 1) * NAUSEA_SCHEDULE_STEP_TICKS;
    }

    private static int currentStage(ServerPlayer player) {
        return player.getCapability(RadiationExposureProvider.CAPABILITY)
                .map(exposure -> getStage(exposure.getDose()))
                .orElse(0);
    }

    private record NauseaSchedule(int stage, long nextTick) {
    }
}
