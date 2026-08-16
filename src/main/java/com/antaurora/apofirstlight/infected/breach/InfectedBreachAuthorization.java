package com.antaurora.apofirstlight.infected.breach;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.infected.perception.InfectedHearingState;
import com.antaurora.apofirstlight.infected.vision.InfectedVisionSystem;
import com.antaurora.apofirstlight.noise.NoiseEvent;
import net.minecraft.world.entity.monster.Zombie;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Maintains the short-lived, hearing-derived Breach authorization. */
public final class InfectedBreachAuthorization {
    public static final double HIGH_INTENSITY_BREACH_THRESHOLD = 40.0;
    private static final Map<Zombie, InfectedBreachContext> NOISE_CONTEXTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private InfectedBreachAuthorization() {
    }

    public static void updateFromHeardNoise(Zombie zombie, NoiseEvent event) {
        if (event.radius() < HIGH_INTENSITY_BREACH_THRESHOLD) {
            clearNoiseAuthorization(zombie, "LowIntensityNoise");
            return;
        }
        InfectedBreachContext context = new InfectedBreachContext(
                event.position(),
                InfectedBreachContext.Source.HIGH_INTENSITY_NOISE,
                event.gameTime(),
                event.type().name()
        );
        NOISE_CONTEXTS.put(zombie, context);
        ApocalypseFirstLight.LOGGER.debug(
                "[AFL BREACH] Authorized source=HIGH_INTENSITY_NOISE Zombie={} radius={} target={}",
                zombie.getId(), event.radius(), event.position()
        );
    }

    public static void clearNoiseAuthorization(Zombie zombie, String reason) {
        if (NOISE_CONTEXTS.remove(zombie) != null) {
            ApocalypseFirstLight.LOGGER.debug("[AFL BREACH] Noise authorization cleared Zombie={} reason={}", zombie.getId(), reason);
        }
    }

    public static InfectedBreachContext getBreachContext(Zombie zombie) {
        InfectedBreachContext visionContext = InfectedVisionSystem.getVisionBreachContext(zombie);
        if (visionContext != null) {
            return visionContext;
        }
        InfectedBreachContext noiseContext = NOISE_CONTEXTS.get(zombie);
        if (noiseContext == null) {
            return null;
        }
        if (!isCurrentInvestigateTarget(zombie, noiseContext)) {
            clearNoiseAuthorization(zombie, "InvestigateEndedOrReplaced");
            return null;
        }
        return noiseContext;
    }

    private static boolean isCurrentInvestigateTarget(Zombie zombie, InfectedBreachContext context) {
        if (!InfectedHearingState.isValid(zombie)
                || InfectedHearingState.phase(zombie) != InfectedHearingState.Phase.INVESTIGATING
                || InfectedHearingState.heardGameTime(zombie) != context.createdGameTime()) {
            return false;
        }
        var position = InfectedHearingState.lastHeardPosition(zombie);
        return position != null && position.distanceToSqr(context.targetPosition()) < 0.0001;
    }
}
