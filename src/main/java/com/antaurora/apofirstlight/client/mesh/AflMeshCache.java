package com.antaurora.apofirstlight.client.mesh;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfile;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfiles;
import com.antaurora.apofirstlight.client.blockmesh.AflBlockMeshProfileLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.Map;

/** One immutable snapshot per client resource generation; invalid sidecars fail individually. */
@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class AflMeshCache {
    private static final String PREFIX = "meshes/", SUFFIX = ".aflmesh.json";
    public record Snapshot(long generation, Map<ResourceLocation, AflMeshModel> models) {
        public Snapshot { models = Map.copyOf(models); }
        public AflMeshModel get(ResourceLocation geometry) { return models.get(geometry); }
    }
    private static volatile Snapshot current = new Snapshot(0, Map.of());
    public static Snapshot snapshot() { return current; }
    private record ReloadData(Map<ResourceLocation, AflMeshModel> meshes,
                              Map<ResourceLocation, AflBlockMeshProfile> blockProfiles) {}

    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<ReloadData>() {
            @Override protected ReloadData prepare(ResourceManager manager, ProfilerFiller profiler) {
                var loaded = new HashMap<ResourceLocation, AflMeshModel>();
                manager.listResources("meshes", id -> id.getPath().endsWith(SUFFIX)).forEach((sidecar, resource) -> {
                    String path = sidecar.getPath();
                    try {
                        String stem = path.substring(PREFIX.length(), path.length() - SUFFIX.length());
                        var geometry = new ResourceLocation(sidecar.getNamespace(), "geo/" + stem + ".geo.json");
                        try (var meshReader = resource.openAsReader(); var geoReader = manager.openAsReader(geometry)) {
                            loaded.put(geometry, AflMeshLoader.load(sidecar.toString(), meshReader, geoReader));
                        }
                    } catch (Exception e) {
                        ApocalypseFirstLight.LOGGER.error("Rejected AFL mesh sidecar {}: {}", sidecar, e.getMessage());
                    }
                });
                return new ReloadData(Map.copyOf(loaded), AflBlockMeshProfileLoader.loadAll(manager, loaded));
            }
            @Override protected void apply(ReloadData data, ResourceManager manager, ProfilerFiller profiler) {
                current = new Snapshot(current.generation() + 1, data.meshes());
                AflBlockMeshProfiles.replace(data.blockProfiles());
            }
        });
    }
    private AflMeshCache() {}
}
