package com.antaurora.apofirstlight.client.blockmesh;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockmesh.AflBlockMeshProfiles;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Unit;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;

/**
 * Reloads the block mesh profiles ({@code block_mesh_profiles/*.json}) on every client resource reload, against the
 * meshes Vextris published in the same reload (docs/dev/vextris_migration_v1.md). Until the Vextris migration this was
 * the second half of {@code client/mesh/AflMeshCache}, which loaded the meshes too.
 * <p>
 * Order: Vextris' mesh listener is registered first (mods.toml declares vextris with ordering AFTER, and Forge posts
 * the registration event in mod order), and reload listeners apply strictly in registration order (each waits for the
 * previous listener's whole reload), so {@link #apply} sees the new Vextris snapshot. Profiles are small: they are read
 * on the main thread in apply.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AflBlockMeshProfileReload extends SimplePreparableReloadListener<Unit> {
    private static long lastGeneration = Long.MIN_VALUE;

    private AflBlockMeshProfileReload() {}

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new AflBlockMeshProfileReload());
    }

    @Override
    protected Unit prepare(ResourceManager manager, ProfilerFiller profiler) {
        return Unit.INSTANCE;
    }

    @Override
    protected void apply(Unit unused, ResourceManager manager, ProfilerFiller profiler) {
        var snapshot = VextrisMeshes.snapshot();
        if (snapshot.generation() == lastGeneration)
            ApocalypseFirstLight.LOGGER.warn("AFL block mesh profiles: Vextris meshes were not reloaded first (generation {})", lastGeneration);
        lastGeneration = snapshot.generation();
        // profiles name their mesh by its geometry location (ns:geo/<stem>.geo.json), the key AFL always used
        var meshes = new HashMap<ResourceLocation, MeshModel>();
        snapshot.geometries().forEach((geometry, id) -> {
            MeshModel model = snapshot.models().get(id);
            if (model != null) meshes.put(geometry, model);
        });
        AflBlockMeshProfiles.replace(AflBlockMeshProfileLoader.loadAll(manager, meshes));
    }
}
