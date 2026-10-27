package com.antaurora.apofirstlight.creature;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/**
 * Rules every AFL body follows (players and AFL's own creatures; user 2026-10-26): no white death smoke and no red hurt
 * tint. Wounds show as blood and lost limbs later; a dead body stays as a corpse that can be searched and rots
 * (docs/gameplay/afl_fauna_plan_v0.md 1).
 */
public final class AflCreatures {
    private AflCreatures() {
    }

    /** A player or an AFL entity: no vanilla death smoke, no red hurt tint. */
    public static boolean isAflBody(Entity entity) {
        return entity instanceof Player || ApocalypseFirstLight.MOD_ID.equals(EntityType.getKey(entity.getType()).getNamespace());
    }
}
