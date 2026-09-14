package com.antaurora.apofirstlight.worldgen.profile;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** Immutable freeze value only. Never installed into a world, reloaded or upgraded automatically. */
public record WorldgenProfile(String profileId, int schemaVersion,
                              Map<ResourceLocation, String> systemVersions, String resourceSnapshot) {
    public WorldgenProfile {
        requireText(profileId); requireText(resourceSnapshot);
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported profile schema");
        if (profileId.equalsIgnoreCase("LEGACY")) throw new IllegalArgumentException("Missing profile is LEGACY, not an activatable profile");
        var sorted = new TreeMap<ResourceLocation, String>(Comparator.comparing(ResourceLocation::toString));
        for (var entry : systemVersions.entrySet()) {
            Objects.requireNonNull(entry.getKey()); requireText(entry.getValue());
            sorted.put(entry.getKey(), entry.getValue());
        }
        if (sorted.isEmpty()) throw new IllegalArgumentException("Profile must declare system versions");
        systemVersions = Collections.unmodifiableMap(sorted);
    }

    public static void requireText(String value) {
        if (Objects.requireNonNull(value).isBlank()) throw new IllegalArgumentException("Blank identity/version/digest");
    }
}
