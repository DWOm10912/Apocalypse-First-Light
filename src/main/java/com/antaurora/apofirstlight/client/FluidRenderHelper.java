package com.antaurora.apofirstlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public final class FluidRenderHelper {
    private FluidRenderHelper() {
    }

    public static void renderCuboid(FluidStack fluid, PoseStack poseStack, MultiBufferSource buffer,
                                    int packedLight, int packedOverlay,
                                    float minX, float minY, float minZ,
                                    float maxX, float maxY, float maxZ) {
        if (fluid.isEmpty() || maxY <= minY) {
            return;
        }

        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation stillTexture = properties.getStillTexture(fluid);
        if (stillTexture == null) {
            return;
        }

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(stillTexture);
        int tint = properties.getTintColor(fluid);
        int alpha = tint >>> 24 & 0xFF;
        int red = tint >>> 16 & 0xFF;
        int green = tint >>> 8 & 0xFF;
        int blue = tint & 0xFF;

        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float sideU = u0 + (u1 - u0) * (maxX - minX);
        float sideV = v1 - (v1 - v0) * (maxY - minY);
        float depthU = u0 + (u1 - u0) * (maxZ - minZ);
        float depthV = v0 + (v1 - v0) * (maxZ - minZ);

        quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                minX, minY, minZ, u0, v1,
                minX, maxY, minZ, u0, sideV,
                maxX, maxY, minZ, sideU, sideV,
                maxX, minY, minZ, sideU, v1,
                0.0F, 0.0F, -1.0F);
        quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                maxX, minY, maxZ, u0, v1,
                maxX, maxY, maxZ, u0, sideV,
                minX, maxY, maxZ, sideU, sideV,
                minX, minY, maxZ, sideU, v1,
                0.0F, 0.0F, 1.0F);
        quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                minX, minY, maxZ, u0, v1,
                minX, maxY, maxZ, u0, sideV,
                minX, maxY, minZ, depthU, sideV,
                minX, minY, minZ, depthU, v1,
                -1.0F, 0.0F, 0.0F);
        quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                maxX, minY, minZ, u0, v1,
                maxX, maxY, minZ, u0, sideV,
                maxX, maxY, maxZ, depthU, sideV,
                maxX, minY, maxZ, depthU, v1,
                1.0F, 0.0F, 0.0F);
        quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                minX, maxY, minZ, u0, v0,
                minX, maxY, maxZ, u0, depthV,
                maxX, maxY, maxZ, sideU, depthV,
                maxX, maxY, minZ, sideU, v0,
                0.0F, 1.0F, 0.0F);
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose pose,
                             int packedLight, int packedOverlay,
                             int red, int green, int blue, int alpha,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3,
                             float x4, float y4, float z4, float u4, float v4,
                             float normalX, float normalY, float normalZ) {
        vertex(vertices, pose, x1, y1, z1, u1, v1, red, green, blue, alpha,
                packedLight, packedOverlay, normalX, normalY, normalZ);
        vertex(vertices, pose, x2, y2, z2, u2, v2, red, green, blue, alpha,
                packedLight, packedOverlay, normalX, normalY, normalZ);
        vertex(vertices, pose, x3, y3, z3, u3, v3, red, green, blue, alpha,
                packedLight, packedOverlay, normalX, normalY, normalZ);
        vertex(vertices, pose, x4, y4, z4, u4, v4, red, green, blue, alpha,
                packedLight, packedOverlay, normalX, normalY, normalZ);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int red, int green, int blue, int alpha,
                               int packedLight, int packedOverlay,
                               float normalX, float normalY, float normalZ) {
        vertices.vertex(pose.pose(), x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(packedOverlay)
                .uv2(packedLight)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
    }
}
