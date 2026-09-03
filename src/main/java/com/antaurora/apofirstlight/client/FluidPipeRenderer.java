package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FluidPipeRenderer {
    private static final float INNER_MIN = 6.0F / 16.0F;
    private static final float INNER_MAX = 10.0F / 16.0F;

    private FluidPipeRenderer() {
    }

    @SubscribeEvent
    public static void renderActivePipeFluid(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Map<BlockPos, ClientFluidPipeVisuals.VisualState> active = ClientFluidPipeVisuals.snapshot();
        if (active.isEmpty()) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        for (Map.Entry<BlockPos, ClientFluidPipeVisuals.VisualState> entry : active.entrySet()) {
            BlockPos position = entry.getKey();
            if (!minecraft.level.hasChunkAt(position)
                    || !minecraft.level.getBlockState(position).is(AflBlocks.FLUID_PIPE.get())) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(position.getX() - cameraPosition.x,
                    position.getY() - cameraPosition.y,
                    position.getZ() - cameraPosition.z);
            renderPipe(entry.getValue(), poseStack, buffer,
                    LevelRenderer.getLightColor(minecraft.level, position));
            poseStack.popPose();
        }
        buffer.endBatch(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
    }

    private static void renderPipe(ClientFluidPipeVisuals.VisualState state, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight) {
        List<Direction> directions = activeDirections(state.directionMask());
        if (directions.size() == 2 && directions.get(0).getOpposite() == directions.get(1)) {
            renderStraight(state, directions.get(0).getAxis(), poseStack, buffer, packedLight,
                    directions.get(0), directions.get(1));
            return;
        }

        int centerFaces = FluidRenderHelper.ALL_FACES;
        for (Direction direction : directions) {
            centerFaces &= ~FluidRenderHelper.faceBit(direction);
        }
        FluidRenderHelper.renderBox(state.fluid(), true, poseStack, buffer, packedLight,
                OverlayTexture.NO_OVERLAY, INNER_MIN, INNER_MIN, INNER_MIN,
                INNER_MAX, INNER_MAX, INNER_MAX, centerFaces);

        for (Direction direction : directions) {
            renderArm(state, direction, poseStack, buffer, packedLight);
        }
    }

    private static void renderStraight(ClientFluidPipeVisuals.VisualState state, Direction.Axis axis,
                                       PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                       Direction firstDirection, Direction secondDirection) {
        float minX = axis == Direction.Axis.X ? 0.0F : INNER_MIN;
        float minY = axis == Direction.Axis.Y ? 0.0F : INNER_MIN;
        float minZ = axis == Direction.Axis.Z ? 0.0F : INNER_MIN;
        float maxX = axis == Direction.Axis.X ? 1.0F : INNER_MAX;
        float maxY = axis == Direction.Axis.Y ? 1.0F : INNER_MAX;
        float maxZ = axis == Direction.Axis.Z ? 1.0F : INNER_MAX;
        int faces = FluidRenderHelper.ALL_FACES
                & ~FluidRenderHelper.faceBit(firstDirection)
                & ~FluidRenderHelper.faceBit(secondDirection);
        FluidRenderHelper.renderBox(state.fluid(), true, poseStack, buffer, packedLight,
                OverlayTexture.NO_OVERLAY, minX, minY, minZ, maxX, maxY, maxZ, faces);
    }

    private static void renderArm(ClientFluidPipeVisuals.VisualState state, Direction direction,
                                  PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float minX = direction == Direction.WEST ? 0.0F : INNER_MIN;
        float minY = direction == Direction.DOWN ? 0.0F : INNER_MIN;
        float minZ = direction == Direction.NORTH ? 0.0F : INNER_MIN;
        float maxX = direction == Direction.EAST ? 1.0F : INNER_MAX;
        float maxY = direction == Direction.UP ? 1.0F : INNER_MAX;
        float maxZ = direction == Direction.SOUTH ? 1.0F : INNER_MAX;
        int faces = FluidRenderHelper.ALL_FACES
                & ~FluidRenderHelper.faceBit(direction)
                & ~FluidRenderHelper.faceBit(direction.getOpposite());
        FluidRenderHelper.renderBox(state.fluid(), true, poseStack, buffer, packedLight,
                OverlayTexture.NO_OVERLAY, minX, minY, minZ, maxX, maxY, maxZ, faces);
    }

    private static List<Direction> activeDirections(int directionMask) {
        List<Direction> directions = new ArrayList<>(2);
        for (Direction direction : Direction.values()) {
            if ((directionMask & FluidRenderHelper.faceBit(direction)) != 0) {
                directions.add(direction);
            }
        }
        return directions;
    }
}
