package com.antaurora.apofirstlight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

public final class FluidRenderHelper {
    public static final int ALL_FACES = (1 << Direction.values().length) - 1;

    private FluidRenderHelper() {
    }

    public static int faceBit(Direction direction) {
        return 1 << direction.get3DDataValue();
    }

    public static void renderTankCuboid(FluidStack fluid, PoseStack poseStack, MultiBufferSource buffer,
                                        int packedLight, int packedOverlay,
                                        float minX, float minY, float minZ,
                                        float maxX, float maxY, float maxZ,
                                        boolean renderTopFace) {
        int faces = faceBit(Direction.NORTH) | faceBit(Direction.SOUTH)
                | faceBit(Direction.WEST) | faceBit(Direction.EAST);
        if (renderTopFace) {
            faces |= faceBit(Direction.UP);
        }
        renderBox(fluid, false, poseStack, buffer, packedLight, packedOverlay,
                minX, minY, minZ, maxX, maxY, maxZ, faces);
    }

    public static void renderBox(FluidStack fluid, boolean flowingTexture,
                                 PoseStack poseStack, MultiBufferSource buffer,
                                 int packedLight, int packedOverlay,
                                 float minX, float minY, float minZ,
                                 float maxX, float maxY, float maxZ,
                                 int faceMask) {
        if (fluid.isEmpty() || maxX <= minX || maxY <= minY || maxZ <= minZ || faceMask == 0) {
            return;
        }

        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid.getFluid());
        if (com.antaurora.apofirstlight.fluid.FluidLighting.intrinsic(fluid) > 0) {
            packedLight = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        }
        ResourceLocation texture = flowingTexture
                ? properties.getFlowingTexture(fluid)
                : properties.getStillTexture(fluid);
        if (texture == null && flowingTexture) {
            texture = properties.getStillTexture(fluid);
        }
        if (texture == null) {
            return;
        }

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(texture);
        int tint = properties.getTintColor(fluid);
        int alpha = tint >>> 24 & 0xFF;
        int red = tint >>> 16 & 0xFF;
        int green = tint >>> 8 & 0xFF;
        int blue = tint & 0xFF;

        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float widthU = sprite.getU(16.0F * (maxX - minX));
        float depthU = sprite.getU(16.0F * (maxZ - minZ));
        float heightV = sprite.getV(16.0F * (1.0F - (maxY - minY)));
        float depthV = sprite.getV(16.0F * (maxZ - minZ));

        if (hasFace(faceMask, Direction.NORTH)) {
            quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                    minX, minY, minZ, u0, v1,
                    minX, maxY, minZ, u0, heightV,
                    maxX, maxY, minZ, widthU, heightV,
                    maxX, minY, minZ, widthU, v1,
                    0.0F, 0.0F, -1.0F);
        }
        if (hasFace(faceMask, Direction.SOUTH)) {
            quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                    maxX, minY, maxZ, u0, v1,
                    maxX, maxY, maxZ, u0, heightV,
                    minX, maxY, maxZ, widthU, heightV,
                    minX, minY, maxZ, widthU, v1,
                    0.0F, 0.0F, 1.0F);
        }
        if (hasFace(faceMask, Direction.WEST)) {
            quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                    minX, minY, maxZ, u0, v1,
                    minX, maxY, maxZ, u0, heightV,
                    minX, maxY, minZ, depthU, heightV,
                    minX, minY, minZ, depthU, v1,
                    -1.0F, 0.0F, 0.0F);
        }
        if (hasFace(faceMask, Direction.EAST)) {
            quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                    maxX, minY, minZ, u0, v1,
                    maxX, maxY, minZ, u0, heightV,
                    maxX, maxY, maxZ, depthU, heightV,
                    maxX, minY, maxZ, depthU, v1,
                    1.0F, 0.0F, 0.0F);
        }
        if (hasFace(faceMask, Direction.UP)) {
            quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                    minX, maxY, minZ, u0, v0,
                    minX, maxY, maxZ, u0, depthV,
                    maxX, maxY, maxZ, widthU, depthV,
                    maxX, maxY, minZ, widthU, v0,
                    0.0F, 1.0F, 0.0F);
        }
        if (hasFace(faceMask, Direction.DOWN)) {
            quad(vertices, pose, packedLight, packedOverlay, red, green, blue, alpha,
                    minX, minY, maxZ, u0, depthV,
                    minX, minY, minZ, u0, v0,
                    maxX, minY, minZ, widthU, v0,
                    maxX, minY, maxZ, widthU, depthV,
                    0.0F, -1.0F, 0.0F);
        }
    }

    /**
     * A fluid box whose flowing texture runs a given way on each face (the pipes): flowOnFace gives, per face, the
     * direction the fluid moves across it, or null. The flowing sprite's animation runs toward +V (vanilla's flowing
     * water runs down a block's side), so +V is laid along that direction. A face with no direction across it (null, or
     * the fluid moving into / out of it), and every face while not flowing, takes the still sprite.
     */
    public static void renderFlowBox(FluidStack fluid, boolean flowingTexture,
                                     PoseStack poseStack, MultiBufferSource buffer,
                                     int packedLight, int packedOverlay,
                                     float minX, float minY, float minZ,
                                     float maxX, float maxY, float maxZ,
                                     int faceMask, java.util.function.Function<Direction, Direction> flowOnFace) {
        if (fluid.isEmpty() || maxX <= minX || maxY <= minY || maxZ <= minZ || faceMask == 0) {
            return;
        }
        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid.getFluid());
        if (com.antaurora.apofirstlight.fluid.FluidLighting.intrinsic(fluid) > 0) {
            packedLight = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        }
        ResourceLocation stillTexture = properties.getStillTexture(fluid);
        ResourceLocation flowTexture = flowingTexture ? properties.getFlowingTexture(fluid) : null;
        if (stillTexture == null) stillTexture = flowTexture;
        if (stillTexture == null) {
            return;
        }
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite still = atlas.apply(stillTexture), flow = flowTexture == null ? null : atlas.apply(flowTexture);
        int tint = properties.getTintColor(fluid);
        int alpha = tint >>> 24 & 0xFF, red = tint >>> 16 & 0xFF, green = tint >>> 8 & 0xFF, blue = tint & 0xFF;
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();
        float[] min = {minX, minY, minZ}, max = {maxX, maxY, maxZ};
        for (Direction face : Direction.values()) {
            if (!hasFace(faceMask, face)) continue;
            Direction across = flow == null ? null : flowOnFace.apply(face);
            if (across != null && across.getAxis() == face.getAxis()) across = null;
            TextureAtlasSprite sprite = across == null ? still : flow;
            // V along the flow (or down a side face / along Z on a top or bottom face), U along the face's other axis
            int vAxis = across != null ? across.getAxis().ordinal() : face.getAxis() == Direction.Axis.Y ? 2 : 1;
            boolean vPositive = across != null ? across.getAxisDirection() == Direction.AxisDirection.POSITIVE : vAxis == 2;
            int uAxis = 3 - face.getAxis().ordinal() - vAxis;
            for (float[] c : corners(face, minX, minY, minZ, maxX, maxY, maxZ)) {
                float u = 16.0F * (c[uAxis] - min[uAxis]);
                float v = 16.0F * (vPositive ? c[vAxis] - min[vAxis] : max[vAxis] - c[vAxis]);
                vertex(vertices, pose, c[0], c[1], c[2], sprite.getU(u), sprite.getV(v), red, green, blue, alpha,
                        packedLight, packedOverlay, face.getStepX(), face.getStepY(), face.getStepZ());
            }
        }
    }

    /** A box face's four corners, in the same order as {@link #renderBox}'s quads. */
    private static float[][] corners(Direction face, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return switch (face) {
            case NORTH -> new float[][]{{minX, minY, minZ}, {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, minY, minZ}};
            case SOUTH -> new float[][]{{maxX, minY, maxZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}, {minX, minY, maxZ}};
            case WEST -> new float[][]{{minX, minY, maxZ}, {minX, maxY, maxZ}, {minX, maxY, minZ}, {minX, minY, minZ}};
            case EAST -> new float[][]{{maxX, minY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {maxX, minY, maxZ}};
            case UP -> new float[][]{{minX, maxY, minZ}, {minX, maxY, maxZ}, {maxX, maxY, maxZ}, {maxX, maxY, minZ}};
            case DOWN -> new float[][]{{minX, minY, maxZ}, {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}};
        };
    }

    private static boolean hasFace(int faceMask, Direction direction) {
        return (faceMask & faceBit(direction)) != 0;
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
