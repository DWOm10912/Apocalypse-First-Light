package com.antaurora.apofirstlight.infected.perception;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.InfectedEntityRules;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
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
            double distanceSquared = infected.position().distanceToSqr(position);
            if (distanceSquared > radiusSquared) {
                continue;
            }
            boolean wasSearching = InfectedHearingState.phase(infected) == InfectedHearingState.Phase.SEARCHING;
            Vec3 previousPosition = InfectedHearingState.lastHeardPosition(infected);
            InfectedHearingState.hear(infected, position, event.gameTime(), event.type().name());
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
