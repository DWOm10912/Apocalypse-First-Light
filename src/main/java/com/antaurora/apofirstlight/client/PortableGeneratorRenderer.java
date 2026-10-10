package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.PortableDieselGeneratorBlock;
import com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The portable diesel generator (docs/machines/portable_diesel_generator_v1.md): the generic AFL animated mesh renderer for
 * the set itself, then what the mesh cannot hold: the starter rope, a thin cream tube from the rope guide to the T handle
 * wherever the 'pull' channel has it (taut: the recoil spring keeps it so), and, during the local player's pull in first
 * person, their arm on the handle (client/PortableGeneratorPull#renderArm).
 */
public final class PortableGeneratorRenderer implements BlockEntityRenderer<PortableDieselGeneratorBlockEntity> {
    private static final double ROPE_RADIUS = 0.0028;
    private static final int SIDES = 6;
    private static final ResourceLocation ROPE_SPRITE = new ResourceLocation("minecraft", "block/white_concrete");
    private static final int[] ROPE_COLOUR = {214, 204, 180};
    private final AflAnimatedBlockMeshRenderer<PortableDieselGeneratorBlockEntity> mesh;

    public PortableGeneratorRenderer(BlockEntityRendererProvider.Context context) {
        mesh = new AflAnimatedBlockMeshRenderer<>(context);
    }

    @Override public int getViewDistance() { return mesh.getViewDistance(); }

    @Override
    public void render(PortableDieselGeneratorBlockEntity generator, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        mesh.render(generator, partialTick, pose, buffers, light, overlay);
        if (generator.getLevel() == null) return;
        BlockPos at = generator.getBlockPos();
        double v = generator.meshAnimation().sample("pull", generator.getLevel().getGameTime() + partialTick);
        Vec3 guide = PortableDieselGeneratorBlock.world(at, generator.getBlockState(), PortableDieselGeneratorBlock.GUIDE);
        Vec3 grip = PortableDieselGeneratorBlock.handle(at, generator.getBlockState(), v);
        rope(pose, buffers, at, guide, grip, light);
        PortableGeneratorPull.renderArm(generator, partialTick, pose, buffers);
    }

    private static void rope(PoseStack pose, MultiBufferSource buffers, BlockPos origin, Vec3 a, Vec3 b, int light) {
        if (a.distanceToSqr(b) < 1e-8) return;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ROPE_SPRITE);
        float[] uv = LiquidJetRenderer.texelCentre(sprite, 0.5, 0.5);
        float u = uv[0], w = uv[1];
        Vec3[] points = {a, b};
        Vec3[][] ring = LiquidJetRenderer.rings(points, SIDES);
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normals = pose.last().normal();
        for (int k = 0; k < SIDES; k++) {
            int k2 = (k + 1) % SIDES;
            vertex(out, matrix, normals, origin, a, ring[0][k], u, w, light);
            vertex(out, matrix, normals, origin, a, ring[0][k2], u, w, light);
            vertex(out, matrix, normals, origin, b, ring[1][k2], u, w, light);
            vertex(out, matrix, normals, origin, b, ring[1][k], u, w, light);
        }
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Matrix3f normals, BlockPos origin, Vec3 centre, Vec3 direction, float u, float v, int light) {
        out.vertex(matrix, (float) (centre.x - origin.getX() + direction.x * ROPE_RADIUS), (float) (centre.y - origin.getY() + direction.y * ROPE_RADIUS),
                        (float) (centre.z - origin.getZ() + direction.z * ROPE_RADIUS))
                .color(ROPE_COLOUR[0], ROPE_COLOUR[1], ROPE_COLOUR[2], 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(normals, (float) direction.x, (float) direction.y, (float) direction.z).endVertex();
    }
}
