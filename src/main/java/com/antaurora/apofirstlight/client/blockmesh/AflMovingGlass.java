package com.antaurora.apofirstlight.client.blockmesh;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.mesh.AflMeshPart;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The glass the animated mesh renderer draws itself (a door or lid in motion, or not yet back in the chunk) while a shader
 * pack is on (2026-10-08). Drawn by the block entity renderer, glass goes through Oculus' block entity program; Sundial has
 * no gbuffers_block_translucent, so it fell back to the opaque gbuffers_block and showed a dusk-coloured glow instead of the
 * glass the chunk draws (user video, A1 storefront door at night). Oculus maps the terrain translucent shaders to
 * gbuffers_water in every phase but entities and block entities, so this glass is queued during the block entity pass, at
 * the renderer's view-space pose, and drawn after the translucent terrain as {@link RenderType#translucent()} with the block
 * atlas sprite: the same program, PBR maps and blending as the chunk glass and the Storefront Glazing.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AflMovingGlass {
    private static final List<BakedQuad> QUADS = new ArrayList<>();
    private static final List<Integer> LIGHTS = new ArrayList<>();
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(1 << 16));

    private AflMovingGlass() {}

    /** Queues one part's glass at pose (view space, the renderer's current pose); false when it cannot be baked. */
    static boolean queue(List<AflMeshPart> parts, Matrix4f pose, TextureAtlasSprite sprite, int light) {
        int start = QUADS.size();
        if (AflStaticMeshModel.emit(parts, pose, sprite, 0, false, QUADS) != null) {
            QUADS.subList(start, QUADS.size()).clear();
            return false;
        }
        for (int i = start; i < QUADS.size(); i++) LIGHTS.add(light);
        return true;
    }

    /** A frame whose stage never came (no world drawn) leaves nothing behind. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) clear();
    }

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || QUADS.isEmpty()) return;
        RenderType type = RenderType.translucent();
        VertexConsumer out = BUFFERS.getBuffer(type);
        for (int q = 0; q < QUADS.size(); q++) {
            int[] data = QUADS.get(q).getVertices();
            int light = LIGHTS.get(q);
            for (int i = 0; i < 4; i++) {
                int o = i * 8, normal = data[o + 7];
                // positions are view space already (the renderer's pose); the normal is the baked one (view space too)
                out.vertex(Float.intBitsToFloat(data[o]), Float.intBitsToFloat(data[o + 1]), Float.intBitsToFloat(data[o + 2]),
                        1.0F, 1.0F, 1.0F, 1.0F, Float.intBitsToFloat(data[o + 4]), Float.intBitsToFloat(data[o + 5]),
                        OverlayTexture.NO_OVERLAY, light,
                        (byte) normal / 127.0F, (byte) (normal >> 8) / 127.0F, (byte) (normal >> 16) / 127.0F);
            }
        }
        BUFFERS.endBatch(type);
        clear();
    }

    private static void clear() {
        QUADS.clear();
        LIGHTS.clear();
    }
}
