package com.antaurora.apofirstlight.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.embeddedt.embeddium.api.ChunkMeshEvent;

/**
 * The one class that touches Embeddium (compile-only; loaded only when Embeddium is, from {@link GroundItemChunking#seen}):
 * a section about to be built gets a mesh appender that writes the mesh items lying in it into its cutout layer.
 */
final class GroundItemEmbeddium {
    private GroundItemEmbeddium() {}

    static void register() {
        ChunkMeshEvent.BUS.addListener(GroundItemEmbeddium::onMesh);
    }

    /** Posted on the main thread when a section's build is prepared; the appender runs on a chunk build thread. */
    private static void onMesh(ChunkMeshEvent event) {
        if (event.getWorld() != Minecraft.getInstance().level || !AflRenderDev.staticMesh()) return;
        var copies = GroundItemChunking.inSection(event.getSectionOrigin().asLong());
        if (copies.isEmpty()) return;
        GroundItemChunking.meshEvents++;
        int shading = WoodGunRackChunking.shading();
        event.addMeshAppender(context -> GroundItemChunking.emit(copies, shading, context.blockRenderView(),
                context.vertexConsumerProvider().apply(RenderType.cutout())));
    }
}
