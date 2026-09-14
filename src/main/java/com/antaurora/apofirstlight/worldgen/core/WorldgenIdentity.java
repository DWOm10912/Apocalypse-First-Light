package com.antaurora.apofirstlight.worldgen.core;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Comparable generation identity; contains no live world or mutable runtime context. */
public record WorldgenIdentity(long seed, ResourceKey<Level> dimension, ResourceLocation system,
                               String generationVersion, String resourceSnapshot) {
    public WorldgenIdentity {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(generationVersion, "generationVersion");
        Objects.requireNonNull(resourceSnapshot, "resourceSnapshot");
        if (generationVersion.isBlank() || resourceSnapshot.isBlank()) {
            throw new IllegalArgumentException("Version and resource snapshot must not be blank");
        }
    }
}
