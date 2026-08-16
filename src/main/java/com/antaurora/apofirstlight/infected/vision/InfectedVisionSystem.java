package com.antaurora.apofirstlight.infected.vision;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.perception.InfectedHearingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class InfectedVisionSystem {
    public static final double MAX_VISION_DISTANCE = 32.0;
    public static final double HORIZONTAL_FOV_DEGREES = 120.0;
    public static final double CLOSE_AWARENESS_DISTANCE = 4.0;
    public static final float DETECTION_PER_TICK = 1.0F / 20.0F;
    public static final float DETECTION_DECAY_PER_TICK = 0.1F;
    public static final long LOST_VISUAL_GRACE_TICKS = 60L;
    private static final double FOV_DOT_THRESHOLD = Math.cos(Math.toRadians(HORIZONTAL_FOV_DEGREES / 2.0));
    private static final long SCAN_INTERVAL_TICKS = 4L;
    private static final double RAY_STEP = 0.2;
    private static final Map<Zombie, InfectedVisionState> STATES = Collections.synchronizedMap(new WeakHashMap<>());

    private InfectedVisionSystem() {
    }

    public static void tick(Zombie zombie) {
        InfectedVisionState state = STATES.computeIfAbsent(zombie, ignored -> new InfectedVisionState());
        long now = zombie.level().getGameTime();
        if (state.confirmedPlayer != null) {
            tickConfirmedTarget(zombie, state, now);
            return;
        }

        if (state.candidate == null || !isCandidateUsable(zombie, state.candidate) || now - state.lastScanTime >= SCAN_INTERVAL_TICKS) {
            ServerPlayer candidate = findPreferredCandidate(zombie, state.candidate);
            if (candidate != state.candidate) {
                if (candidate == null && state.candidate != null) {
                    ApocalypseFirstLight.LOGGER.debug("[AFL VISION] Zombie={} NoCandidate", zombie.getId());
                }
                state.candidate = candidate;
                state.detectionProgress = 0.0F;
                state.loggedTier = -1;
                state.wasVisible = false;
            }
            state.lastScanTime = now;
        }
        if (state.candidate == null) {
            return;
        }

        if (hasEffectiveVision(zombie, state.candidate)) {
            if (!state.wasVisible) {
                ApocalypseFirstLight.LOGGER.debug("[AFL VISION] Zombie={} Candidate={} EnteredFov", zombie.getId(), state.candidate.getGameProfile().getName());
            }
            state.wasVisible = true;
            updateLastVisiblePosition(state, state.candidate, now);
            state.detectionProgress = Math.min(1.0F, state.detectionProgress + DETECTION_PER_TICK);
            logProgressTier(zombie, state);
            if (state.detectionProgress >= 1.0F) {
                state.confirmedPlayer = state.candidate;
                zombie.setTarget(state.candidate);
                ApocalypseFirstLight.LOGGER.debug("[AFL VISION] Zombie={} Confirmed Player={}", zombie.getId(), state.candidate.getGameProfile().getName());
            }
        } else {
            if (state.wasVisible) {
                ApocalypseFirstLight.LOGGER.debug("[AFL VISION] Zombie={} LostVisual", zombie.getId());
            }
            state.wasVisible = false;
            state.detectionProgress = Math.max(0.0F, state.detectionProgress - DETECTION_DECAY_PER_TICK);
            logProgressTier(zombie, state);
        }

        if (state.detectionProgress >= 0.25F && state.detectionProgress < 1.0F && state.lastVisiblePosition != null) {
            lookAtLastVisiblePosition(zombie, state.lastVisiblePosition);
        }
    }

    public static boolean isManagingConfirmedTarget(Zombie zombie) {
        InfectedVisionState state = STATES.get(zombie);
        return state != null && state.confirmedPlayer != null;
    }

    private static void tickConfirmedTarget(Zombie zombie, InfectedVisionState state, long now) {
        ServerPlayer player = state.confirmedPlayer;
        if (player == null) {
            clearConfirmedState(state);
            return;
        }
        if (!isCandidateUsable(zombie, player)) {
            expireConfirmedTarget(zombie, state, now);
            return;
        }
        if (zombie.getTarget() != null && zombie.getTarget() != player) {
            // Retaliation or another vanilla target won priority; do not overwrite it.
            clearConfirmedState(state);
            return;
        }
        if (hasEffectiveVision(zombie, player)) {
            if (!state.wasVisible) {
                ApocalypseFirstLight.LOGGER.debug("[AFL VISION] Zombie={} RegainedVisual Player={}", zombie.getId(), player.getGameProfile().getName());
            }
            state.wasVisible = true;
            updateLastVisiblePosition(state, player, now);
            if (zombie.getTarget() == null) {
                zombie.setTarget(player);
            }
            return;
        }
        if (state.wasVisible) {
            ApocalypseFirstLight.LOGGER.debug("[AFL VISION] Zombie={} LostVisual", zombie.getId());
        }
        state.wasVisible = false;
        if (now - state.lastVisibleGameTime >= LOST_VISUAL_GRACE_TICKS) {
            expireConfirmedTarget(zombie, state, now);
        }
    }

    private static void expireConfirmedTarget(Zombie zombie, InfectedVisionState state, long now) {
        ServerPlayer player = state.confirmedPlayer;
        Vec3 lastKnownPosition = state.lastVisiblePosition;
        if (player != null && zombie.getTarget() == player) {
            zombie.setTarget(null);
        }
        ApocalypseFirstLight.LOGGER.debug("[AFL VISION] Zombie={} LostVisualExpired LastKnown={}", zombie.getId(), lastKnownPosition);
        clearConfirmedState(state);
        if (lastKnownPosition != null) {
            InfectedHearingSystem.investigatePosition(zombie, lastKnownPosition, now);
            ApocalypseFirstLight.LOGGER.debug("[AFL VISION] Zombie={} HandoffToSearch Pos=({}, {}, {})", zombie.getId(), lastKnownPosition.x(), lastKnownPosition.y(), lastKnownPosition.z());
        }
    }

    private static void clearConfirmedState(InfectedVisionState state) {
        state.confirmedPlayer = null;
        state.candidate = null;
        state.detectionProgress = 0.0F;
        state.wasVisible = false;
        state.loggedTier = -1;
    }

    private static ServerPlayer findPreferredCandidate(Zombie zombie, ServerPlayer currentCandidate) {
        if (isCandidateUsable(zombie, currentCandidate) && hasEffectiveVision(zombie, currentCandidate)) {
            return currentCandidate;
        }
        AABB area = zombie.getBoundingBox().inflate(MAX_VISION_DISTANCE);
        ServerPlayer visibleCandidate = zombie.level().getEntitiesOfClass(ServerPlayer.class, area,
                        player -> isCandidateUsable(zombie, player) && hasEffectiveVision(zombie, player))
                .stream()
                .min((left, right) -> Double.compare(left.distanceToSqr(zombie), right.distanceToSqr(zombie)))
                .orElse(null);
        return visibleCandidate != null ? visibleCandidate : isCandidateUsable(zombie, currentCandidate) ? currentCandidate : null;
    }

    private static boolean isCandidateUsable(Zombie zombie, ServerPlayer player) {
        return player != null && player.isAlive() && !player.isSpectator() && player.level() == zombie.level()
                && player.distanceToSqr(zombie) <= MAX_VISION_DISTANCE * MAX_VISION_DISTANCE;
    }

    private static boolean hasEffectiveVision(Zombie zombie, ServerPlayer player) {
        Vec3 eye = zombie.getEyePosition();
        Vec3 target = player.getEyePosition();
        Vec3 toTarget = target.subtract(eye);
        double distance = toTarget.length();
        if (distance > MAX_VISION_DISTANCE || distance < 0.001) {
            return false;
        }
        boolean closeAwareness = zombie.position().distanceToSqr(player.position()) <= CLOSE_AWARENESS_DISTANCE * CLOSE_AWARENESS_DISTANCE;
        if (!closeAwareness && zombie.getLookAngle().normalize().dot(toTarget.scale(1.0 / distance)) < FOV_DOT_THRESHOLD) {
            return false;
        }
        return hasLineOfSight(zombie, eye, target, distance);
    }

    private static void updateLastVisiblePosition(InfectedVisionState state, ServerPlayer player, long now) {
        state.lastVisiblePosition = player.position();
        state.lastVisibleGameTime = now;
    }

    private static void lookAtLastVisiblePosition(Zombie zombie, Vec3 position) {
        zombie.getLookControl().setLookAt(position.x(), position.y() + zombie.getEyeHeight(), position.z(), 30.0F, 30.0F);
    }

    private static boolean hasLineOfSight(Zombie zombie, Vec3 start, Vec3 end, double distance) {
        Vec3 direction = end.subtract(start).scale(1.0 / distance);
        BlockPos previous = null;
        for (double travelled = 0.0; travelled < distance; travelled += RAY_STEP) {
            BlockPos pos = BlockPos.containing(start.add(direction.scale(travelled)));
            if (pos.equals(previous)) {
                continue;
            }
            previous = pos;
            if (VisionOcclusionRules.blocksVision(zombie.level(), pos, zombie.level().getBlockState(pos))) {
                return false;
            }
        }
        return true;
    }

    private static void logProgressTier(Zombie zombie, InfectedVisionState state) {
        int tier = state.detectionProgress >= 1.0F ? 4
                : state.detectionProgress >= 0.75F ? 3
                : state.detectionProgress >= 0.25F ? 1 : 0;
        if (tier != state.loggedTier) {
            state.loggedTier = tier;
            String phase = switch (tier) {
                case 1 -> "NOTICING";
                case 3 -> "ALERT";
                case 4 -> "CONFIRMED";
                default -> "UNAWARE";
            };
            ApocalypseFirstLight.LOGGER.debug("[AFL VISION] Zombie={} {}", zombie.getId(), phase);
        }
    }
}
