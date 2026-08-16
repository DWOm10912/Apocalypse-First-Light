package com.antaurora.apofirstlight.infected.perception;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.InfectedEntityRules;
import com.antaurora.apofirstlight.infected.breach.InfectedBreachAuthorization;
import com.antaurora.apofirstlight.noise.AcousticOcclusionResolver;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class InfectedHearingSystem {
    private InfectedHearingSystem() {
    }

    /**
     * Starts the existing investigate/search flow from a known position without
     * inventing a noise event. Vision uses this after it has lost a confirmed
     * target for its grace period.
     */
    public static void investigatePosition(LivingEntity infected, Vec3 position, long gameTime) {
        if (infected instanceof Zombie zombie) {
            InfectedBreachAuthorization.clearNoiseAuthorization(zombie, "NonNoiseInvestigate");
        }
        InfectedHearingState.hear(infected, position, gameTime, "VISION");
    }

    public static void handle(NoiseEvent event, ServerLevel level) {
        double radius = event.radius();
        if (radius < 0) {
            return;
        }
        Vec3 position = event.position();
        double radiusSquared = radius * radius;
        AABB searchBox = AABB.ofSize(position, radius * 2.0, radius * 2.0, radius * 2.0);
        for (LivingEntity infected : level.getEntitiesOfClass(LivingEntity.class, searchBox, InfectedEntityRules::isNoiseResponsive)) {
            double distanceSquared = infected.getEyePosition().distanceToSqr(position);
            if (distanceSquared > radiusSquared) {
                continue;
            }
            var acoustic = AcousticOcclusionResolver.resolve(level, position, infected.getEyePosition(), radius);
            double effectiveRadius = acoustic.effectiveRadius();
            if (distanceSquared > effectiveRadius * effectiveRadius) {
                continue;
            }
            boolean wasSearching = InfectedHearingState.phase(infected) == InfectedHearingState.Phase.SEARCHING;
            Vec3 previousPosition = InfectedHearingState.lastHeardPosition(infected);
            InfectedHearingState.hear(infected, position, event.gameTime(), event.type().name());
            if (infected instanceof Zombie zombie) {
                InfectedBreachAuthorization.updateFromHeardNoise(zombie, event, effectiveRadius);
            }
            if (acoustic.woolLayers() > 0) ApocalypseFirstLight.LOGGER.debug(
                    "[AFL ACOUSTIC] type={} base={} woolLayers={} effective={} listener={}",
                    event.type(), radius, acoustic.woolLayers(), effectiveRadius, infected.getId());
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL HEARING DEBUG] Zombie={} accepted noise state={} -> INVESTIGATING pos=({}, {}, {})",
                    infected.getId(), wasSearching ? "SEARCHING" : "IDLE_OR_INVESTIGATING",
                    position.x(), position.y(), position.z()
            );
            if (wasSearching && (previousPosition == null || previousPosition.distanceToSqr(position) > 4.0)) {
                ApocalypseFirstLight.LOGGER.debug(
                        "[AFL SEARCH] Zombie={} InterruptedByNoise NewPos=({}, {}, {})",
                        infected.getId(), position.x(), position.y(), position.z()
                );
            }
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL HEARING] Zombie={} Heard={} Pos=({}, {}, {}) Distance={}",
                    infected.getId(), event.type(), position.x(), position.y(), position.z(), Math.sqrt(distanceSquared)
            );
        }
    }
}
