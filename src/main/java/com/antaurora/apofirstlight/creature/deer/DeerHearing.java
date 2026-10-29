package com.antaurora.apofirstlight.creature.deer;

import com.antaurora.apofirstlight.noise.NoiseEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * What the deer hear (docs/gameplay/afl_whitetail_deer_d0_design.md 3, hearing): every noise event (noise/NoiseSystem)
 * reaches the deer within its radius; DeerEntity#heard decides between turning toward it and running.
 */
public final class DeerHearing {
    private DeerHearing() {
    }

    public static void heard(NoiseEvent event, ServerLevel level) {
        if (event.source() instanceof DeerEntity || event.radius() <= 0) return;
        double r = event.radius();
        for (DeerEntity deer : level.getEntitiesOfClass(DeerEntity.class, AABB.ofSize(event.position(), r * 2, r * 2, r * 2))) {
            double d = deer.position().distanceTo(event.position());
            if (d <= r) deer.heard(event.type(), event.position(), d / r, event.source());
        }
    }
}
