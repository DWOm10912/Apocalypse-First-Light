package com.antaurora.apofirstlight.survivor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Where a successor may start (docs/gameplay/survivor_death_succession_v0.md 5). Providers are asked in registration
 * order ({@link SurvivorSpawns#register}); V0 has one, the wilderness ring around the world spawn. Later providers (the
 * Columbian Federation's states, towns, POI) register in front of it and return null where they have nothing.
 *
 * <p>A provider only proposes columns: it must not load or generate chunks and must be deterministic in
 * {@link Context#seed()} and the attempt number. The ground check, with the chunk loaded, is done once by
 * {@link SurvivorSpawns} for every provider alike.
 */
public interface SurvivorSpawnProvider {
    /** Stored with the survivor as the start source (audits, later UI). */
    String id();

    /** A candidate column for this attempt (y ignored), or null when this provider has none for it. */
    @Nullable
    BlockPos propose(Context context, int attempt);

    /**
     * What a provider may use. The death and old bed positions are given only when they lie in the start dimension;
     * successors never start within {@link SurvivorSpawns#MIN_FROM_DEATH} of the death or
     * {@link SurvivorSpawns#MIN_FROM_BED} of the old bed, whatever the provider proposes.
     */
    record Context(ServerLevel level, UUID player, int lifeIndex, long seed, BlockPos worldSpawn,
                   @Nullable BlockPos deathPos, @Nullable BlockPos lastBed, int ringMin, int ringMax) {
    }
}
