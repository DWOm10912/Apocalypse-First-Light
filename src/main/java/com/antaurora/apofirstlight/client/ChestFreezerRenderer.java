package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public final class ChestFreezerRenderer extends GeoBlockRenderer<ChestFreezerBlockEntity> {
    public ChestFreezerRenderer(BlockEntityRendererProvider.Context context) {
        super(new ChestFreezerModel());
    }

    @Override
    public RenderType getRenderType(ChestFreezerBlockEntity entity, ResourceLocation texture,
                                    MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, ChestFreezerBlockEntity entity, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (bone.getName().endsWith("_lid_glass")) {
            RenderType glassType = RenderType.entityTranslucent(getTextureLocation(entity));
            super.renderRecursively(poseStack, entity, bone, glassType, bufferSource,
                    bufferSource.getBuffer(glassType), isReRender, partialTick, packedLight, packedOverlay,
                    red, green, blue, alpha);
        } else {
            super.renderRecursively(poseStack, entity, bone, renderType, bufferSource, buffer,
                    isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        super.rotateBlock(facing, poseStack);
        // Centered Geo spans X=-16..16; GeckoLib mirrors its X when baking cubes.
        // After that mirror, the visual-left/master half is on positive world X.
        // GeckoLib already translates to the master block center (0.5, 0, 0.5).
        poseStack.translate(-0.5, 0, 0);
    }
}
