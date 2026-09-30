package com.antaurora.apofirstlight.blockmesh;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Published only by the existing AFL mesh resource reload listener; empty on dedicated servers. */
public final class AflBlockMeshProfiles {
    private static volatile Map<ResourceLocation, AflBlockMeshProfile> current = Map.of();
    public static AflBlockMeshProfile get(ResourceLocation id) { return current.get(id); }
    public static void replace(Map<ResourceLocation, AflBlockMeshProfile> profiles) { current = Map.copyOf(profiles); }
    private AflBlockMeshProfiles() {}
}
