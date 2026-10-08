package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The common wrapper around every AFL block entity renderer (registered in {@link AflBlockEntityRenderers};
 * docs/dev/render_performance_v1.md):
 * <ul>
 * <li>shader shadow pass: a block entity fully hidden from the shadow light is not drawn ({@link AflShadowOcclusion},
 * light from {@link AflShadowLight}; switch {@code shadow_cull}, {@link AflRenderDev});</li>
 * <li>development builds: the renderer's CPU time, by pass ({@link AflRenderProfiler});</li>
 * <li>{@code shadow_cull debug}: in the main pass, each AFL block entity's render box outlined, red = left out of the
 * shadow pass, green = kept, grey = not checked yet.</li>
 * </ul>
 */
public final class AflBlockEntityRendering {
    private AflBlockEntityRendering() {}

    public static <T extends BlockEntity> BlockEntityRendererProvider<T> wrap(String name, BlockEntityRendererProvider<T> provider) {
        return context -> new Wrapped<>(name, provider.create(context));
    }

    private static final class Wrapped<T extends BlockEntity> implements BlockEntityRenderer<T> {
        private final String name, skippedName;
        private final BlockEntityRenderer<T> inner;

        Wrapped(String name, BlockEntityRenderer<T> inner) {
            this.name = name;
            this.skippedName = name + ".shadow_skipped";
            this.inner = inner;
        }

        @Override
        public void render(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            boolean shadow = AflShaderCompat.activeShadowPass();
            var cull = AflRenderDev.shadowCull();
            // the light direction is read from the incoming pose, before the renderer's own transforms
            if (shadow && cull != AflRenderDev.ShadowCull.OFF && AflShadowOcclusion.skip(entity, AflShadowLight.direction(pose))) {
                AflRenderProfiler.count(skippedName);
                return;
            }
            long start = AflRenderProfiler.begin();
            try {
                inner.render(entity, partialTick, pose, buffers, light, overlay);
            } finally {
                AflRenderProfiler.end(name, start);
            }
            if (!shadow && cull == AflRenderDev.ShadowCull.DEBUG) outline(entity, pose, buffers);
        }

        private static void outline(BlockEntity entity, PoseStack pose, MultiBufferSource buffers) {
            Boolean skipped = AflShadowOcclusion.lastResult(entity);
            float r = skipped == null ? 0.6F : skipped ? 1.0F : 0.1F, g = skipped == null ? 0.6F : skipped ? 0.15F : 0.9F, b = skipped == null ? 0.6F : 0.15F;
            var box = entity.getRenderBoundingBox().move(-entity.getBlockPos().getX(), -entity.getBlockPos().getY(), -entity.getBlockPos().getZ());
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, b, 1.0F);
        }

        @Override public boolean shouldRenderOffScreen(T entity) { return inner.shouldRenderOffScreen(entity); }
        @Override public int getViewDistance() { return inner.getViewDistance(); }
        @Override public boolean shouldRender(T entity, Vec3 camera) { return inner.shouldRender(entity, camera); }
    }
}
