package com.antaurora.apofirstlight.worldgen.spatial;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Debuggable textual ID: percent-escape '%' and '|' to keep component boundaries unambiguous. */
public final class DeterministicClaimId {
    private DeterministicClaimId() {}

    public static String create(ResourceLocation owner, ResourceKey<Level> dimension,
                                String generationVersion, String candidateKey) {
        Objects.requireNonNull(owner); Objects.requireNonNull(dimension);
        Objects.requireNonNull(generationVersion); Objects.requireNonNull(candidateKey);
        if (generationVersion.isBlank() || candidateKey.isBlank()) throw new IllegalArgumentException("Blank ID component");
        // No seed: the snapshot has world scope. No locale, hashCode, UUID, clock or random state.
        return escape(owner.toString()) + "|" + escape(dimension.registry().toString()) + "|"
                + escape(dimension.location().toString()) + "|" + escape(generationVersion) + "|" + escape(candidateKey);
    }

    private static String escape(String value) { return value.replace("%", "%25").replace("|", "%7C"); }
}
