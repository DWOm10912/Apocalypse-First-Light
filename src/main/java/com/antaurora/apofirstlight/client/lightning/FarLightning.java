package com.antaurora.apofirstlight.client.lightning;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Thunder V1 (docs/audio/thunder_sounds_v1_plan.md 5): strikes beyond the bolt entity's tracking range (the server tells
 * every player within 15 km: world/LightningStrikes). A few ticks later, unless the real bolt has come, the client makes
 * its own copy (same UUID, so the same channel) that ticks like a real one: thunder, the sky flash within 1.5 km
 * (AflLightning). Without a shader pack it is drawn here, after the weather: shrunk toward the camera so it fits inside
 * the far plane at the same size on screen, without fog. With a shader pack the channel is not drawn (Oculus defers the
 * buffer source); the pack still gets its flash from AflLightning's flashes (LightningShaderData).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class FarLightning {
    /** Ticks to wait for the real bolt before making the copy. */
    private static final int WAIT = 3;
    /** The copy is drawn no farther than this share of the far plane. */
    private static final double FIT = 0.7;

    private static final class Pending {
        final UUID id;
        final double x, y, z;
        int wait = WAIT;

        Pending(UUID id, double x, double y, double z) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<LightningBolt> BOLTS = new ArrayList<>();

    private FarLightning() {
    }

    /** The server's word of a strike (LightningPackets.FarStrike, client thread). */
    public static void strike(UUID id, double x, double y, double z) {
        if (Minecraft.getInstance().level != null) PENDING.add(new Pending(id, x, y, z));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PENDING.clear();
            BOLTS.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (--p.wait > 0) continue;
            it.remove();
            if (known(mc, p.id)) continue;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(mc.level);
            if (bolt == null) continue;
            bolt.setPos(p.x, p.y, p.z);
            bolt.setUUID(p.id);
            BOLTS.add(bolt);
        }
        for (Iterator<LightningBolt> it = BOLTS.iterator(); it.hasNext(); ) {
            LightningBolt bolt = it.next();
            if (bolt.level() != mc.level) {
                it.remove();
                continue;
            }
            AflLightning.clientTick(bolt);
            if (bolt.isRemoved()) it.remove();
        }
    }

    /** The real bolt is on this client. */
    private static boolean known(Minecraft mc, UUID id) {
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof LightningBolt && id.equals(e.getUUID())) return true;
        return false;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || BOLTS.isEmpty() || AflShaderCompat.shaderPackInUse()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        double far = FIT * mc.gameRenderer.getDepthFar();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (LightningBolt bolt : BOLTS) {
            if (bolt.isRemoved()) continue;
            EntityRenderer<? super LightningBolt> renderer = mc.getEntityRenderDispatcher().getRenderer(bolt);
            double dx = bolt.getX() - cam.x, dy = bolt.getY() - cam.y, dz = bolt.getZ() - cam.z;
            // everything of the bolt is at most its height farther than its foot
            double s = Math.min(1, far / (Math.sqrt(dx * dx + dy * dy + dz * dz) + AflLightning.flash(bolt).height));
            pose.pushPose();
            pose.translate(dx * s, dy * s, dz * s);
            pose.scale((float) s, (float) s, (float) s);
            renderer.render(bolt, 0, event.getPartialTick(), pose, buffers, LightTexture.FULL_BRIGHT);
            pose.popPose();
            drew = true;
        }
        if (!drew) return;
        // the fog would hide it (it is drawn past the render distance)
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        buffers.endBatch(LightningRenderer.type());
        RenderSystem.setShaderFogStart(fogStart);
    }
}
