package com.antaurora.apofirstlight.blockmesh;

import net.minecraftforge.client.model.data.ModelData;

import java.util.function.Function;

/**
 * Where an animated mesh block entity's model data comes from ({@link AflAnimatedMeshHost#getModelData}): the client's
 * chunk mesh bookkeeping (client/blockmesh/AflMeshChunking, docs/dev/render_performance_v1.md) installs itself here at
 * client setup; until then, and on a dedicated server, there is none. Common code, so the host interface stays loadable
 * on the server.
 */
public final class AflMeshChunkData {
    private static volatile Function<AflAnimatedMeshHost, ModelData> provider = host -> ModelData.EMPTY;

    private AflMeshChunkData() {}

    public static void install(Function<AflAnimatedMeshHost, ModelData> next) {
        provider = next;
    }

    static ModelData of(AflAnimatedMeshHost host) {
        return provider.apply(host);
    }
}
