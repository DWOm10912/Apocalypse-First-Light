package com.antaurora.apofirstlight.survivor;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * V0's only start provider: a random point in the ring between {@code successorRingMin} and {@code successorRingMax}
 * blocks around the world spawn (area-uniform), from the survivor's roll seed. It knows no states, towns or POI; those
 * come as further providers in front of it.
 */
final class WildernessSpawnProvider implements SurvivorSpawnProvider {
    static final String ID = "wilderness_ring";

    @Override
    public String id() {
        return ID;
    }

    @Override
    @Nullable
    public BlockPos propose(Context context, int attempt) {
        long a = SurvivorNames.stream(context.seed(), 2 * attempt + 1);
        long b = SurvivorNames.stream(context.seed(), 2 * attempt + 2);
        double angle = (a >>> 11) * 0x1.0p-53 * Math.PI * 2.0;
        double u = (b >>> 11) * 0x1.0p-53;
        double r0 = context.ringMin(), r1 = Math.max(context.ringMin() + 64, context.ringMax());
        double radius = Math.sqrt(r0 * r0 + u * (r1 * r1 - r0 * r0));
        int x = context.worldSpawn().getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = context.worldSpawn().getZ() + (int) Math.round(Math.sin(angle) * radius);
        return new BlockPos(x, 0, z);
    }
}
