package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Ephemeral successful-shot consumers. No entities, ammo state, prediction or server physics. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class NativeGunFx {
    public static final ResourceLocation FLASH_TEXTURE = id("textures/effects/p9_01_muzzle_flash.png");
    public static final ResourceLocation CASING_MODEL = id("item/9mm_casing");
    public static final float FLASH_TICKS = 1.0F, FLASH_SCALE = .17F, CASING_SCALE = .072F;
    public static final int MAX_CASINGS = 64, CASING_TICKS = 50;
    public static final double GRAVITY = .04, DRAG = .98;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final ArrayDeque<Casing> CASINGS = new ArrayDeque<>();
    private static ClientLevel world;
    private static final Matrix4f WORLD_VIEW = new Matrix4f(), WORLD_PROJECTION = new Matrix4f();
    private static boolean viewValid;
    private NativeGunFx() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path); }
    private static double clock(float partial) { return world.getGameTime() + partial; }
    private static void checkWorld() {
        var current = Minecraft.getInstance().level;
        if (current != world) { world = current; SHOTS.clear(); CASINGS.clear(); viewValid = false; }
    }

    public static void shot(int shooter, long gun) {
        checkWorld();
        if (world == null || world.getEntity(shooter) == null) return;
        if (SHOTS.size() >= 128) SHOTS.remove(0);
        SHOTS.add(new Shot(shooter, gun, clock(Minecraft.getInstance().getFrameTime())));
    }

    public static void anchor(long gun, boolean firstPerson, String name, PoseStack anchor,
                              MultiBufferSource buffers, float partial) {
        checkWorld();
        if (world == null || !viewValid) return;
        var mc = Minecraft.getInstance();
        double now = clock(partial);
        if (name.equals("muzzle_anchor") && NativeBulletTrails.needsAnchor(gun, firstPerson)) {
            // Same projection conversion as the established casing birth position.
            var matrix = new Matrix4f(WORLD_VIEW).invert();
            if (firstPerson)
                matrix.mul(new Matrix4f(WORLD_PROJECTION).invert()).mul(RenderSystem.getProjectionMatrix());
            matrix.mul(anchor.last().pose());
            var p = matrix.transformProject(new Vector3f());
            NativeBulletTrails.anchor(gun, firstPerson,
                    mc.gameRenderer.getMainCamera().getPosition().add(p.x, p.y, p.z), now);
        }
        for (Shot shot : SHOTS) {
            if (shot.gun != gun || now - shot.received > 3) continue;
            boolean localFirst = mc.player != null && shot.shooter == mc.player.getId()
                    && mc.options.getCameraType().isFirstPerson();
            if (firstPerson != localFirst) continue;
            if (name.equals("ejection_anchor") && !shot.ejected) {
                // Convert the rendered locator to world space, never substitute an eye/hand offset.
                var matrix = new Matrix4f(WORLD_VIEW).invert();
                if (firstPerson) {
                    // Hand and world FOV differ. Unproject the actual hand clip position into the
                    // world projection so the detached casing has no birth-frame screen jump.
                    matrix.mul(new Matrix4f(WORLD_PROJECTION).invert()).mul(RenderSystem.getProjectionMatrix());
                }
                matrix.mul(anchor.last().pose());
                var p = matrix.transformProject(new Vector3f());
                var camera = mc.gameRenderer.getMainCamera().getPosition();
                Vec3 origin = camera.add(p.x, p.y, p.z);
                // Live visual check: local -X ejects left; reverse only the lateral basis.
                Vec3 right = direction(matrix, 1, 0, 0), up = direction(matrix, 0, 1, 0);
                Vec3 forward = direction(matrix, 0, 0, -1);
                Vec3 velocity = right.scale(.24 + RANDOM.nextDouble() * .06)
                        .add(up.scale(.12 + RANDOM.nextDouble() * .04))
                        .add(forward.scale((RANDOM.nextDouble() - .5) * .08));
                if (CASINGS.size() >= MAX_CASINGS) CASINGS.removeFirst();
                CASINGS.addLast(new Casing(origin, velocity));
                shot.ejected = true;
            }
            if (name.equals("muzzle_anchor")) {
                if (Double.isNaN(shot.flashStart)) shot.flashStart = now;
                float age = (float)(now - shot.flashStart);
                if (age >= 0 && age < FLASH_TICKS) drawFlash(anchor, buffers, age, shot);
            }
        }
    }

    private static Vec3 direction(Matrix4f matrix, float x, float y, float z) {
        Vector3f v = matrix.transformDirection(new Vector3f(x, y, z)).normalize();
        return new Vec3(v.x, v.y, v.z);
    }

    private static void drawFlash(PoseStack source, MultiBufferSource buffers, float age, Shot shot) {
        var pose = P901RenderMatrices.detachedCopy(source);
        // Remove inherited model scale for a single preset size in both perspectives.
        Vector3f scale = pose.last().pose().getScale(new Vector3f());
        pose.scale(1 / scale.x, 1 / scale.y, 1 / scale.z);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(shot.roll));
        float size = FLASH_SCALE * shot.scale;
        pose.scale(size, size, size);
        float alpha = (age < .6F ? 1 - age * (.25F / .6F) : .75F * (1 - age) / .4F) * shot.alpha;
        VertexConsumer out = buffers.getBuffer(FlashType.TYPE);
        // Three orthogonal double-sided quads: muzzle-facing plus two intersecting axial planes.
        quad(out, pose, alpha, new float[][]{{-.5F,-.5F,0},{.5F,-.5F,0},{.5F,.5F,0},{-.5F,.5F,0}});
        quad(out, pose, alpha, new float[][]{{-.5F,0,-.5F},{.5F,0,-.5F},{.5F,0,.5F},{-.5F,0,.5F}});
        quad(out, pose, alpha, new float[][]{{0,-.5F,-.5F},{0,.5F,-.5F},{0,.5F,.5F},{0,-.5F,.5F}});
    }

    private static void quad(VertexConsumer out, PoseStack pose, float alpha, float[][] vertices) {
        for (int i = 0; i < 4; i++) {
            float[] p = vertices[i];
            out.vertex(pose.last().pose(), p[0], p[1], p[2]).color(1F,1F,1F,alpha)
                    .uv(i == 0 || i == 3 ? 0 : 1, i < 2 ? 1 : 0)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(pose.last().normal(),0,0,1).endVertex();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        checkWorld();
        if (world == null || Minecraft.getInstance().isPaused()) return;
        SHOTS.removeIf(s -> world.getGameTime() - s.received > 4);
        CASINGS.removeIf(c -> ++c.age > CASING_TICKS);
        for (Casing casing : CASINGS) casing.tick();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        checkWorld();
        if (world == null) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            WORLD_VIEW.set(event.getPoseStack().last().pose());
            WORLD_PROJECTION.set(event.getProjectionMatrix());
            viewValid = true;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CASINGS.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var model = mc.getModelManager().getModel(CASING_MODEL);
        var buffers = mc.renderBuffers().bufferSource();
        var type = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        var out = buffers.getBuffer(type);
        Vec3 camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        for (Casing c : CASINGS) {
            Vec3 position = c.previous.lerp(c.position, event.getPartialTick());
            Vec3 rotation = c.oldRotation.lerp(c.rotation, event.getPartialTick());
            pose.pushPose();
            pose.translate(position.x-camera.x, position.y-camera.y, position.z-camera.z);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees((float)rotation.x));
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)rotation.y));
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)rotation.z));
            pose.scale(CASING_SCALE,CASING_SCALE,CASING_SCALE);
            pose.translate(-.5,-.5,-.5);
            int light = LevelRenderer.getLightColor(world, BlockPos.containing(position));
            var random = RandomSource.create(0);
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                random.setSeed(0);
                for (var q : model.getQuads(null, side, random)) out.putBulkData(pose.last(),q,1,1,1,light,OverlayTexture.NO_OVERLAY);
            }
            random.setSeed(0);
            for (var q : model.getQuads(null, null, random)) out.putBulkData(pose.last(),q,1,1,1,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffers.endBatch(type);
    }

    private static final class Shot {
        final int shooter; final long gun; final double received;
        final float roll = (RANDOM.nextFloat() - .5F) * 24, scale = .9F + RANDOM.nextFloat() * .2F;
        final float alpha = .95F + RANDOM.nextFloat() * .05F;
        double flashStart = Double.NaN; boolean ejected;
        Shot(int shooter, long gun, double received) { this.shooter = shooter; this.gun = gun; this.received = received; }
    }

    private static final class Casing {
        Vec3 position, previous, velocity, rotation, oldRotation, spin;
        int age, bounces; boolean sounded, resting;
        Casing(Vec3 position, Vec3 velocity) {
            this.position = this.previous = position; this.velocity = velocity;
            rotation = oldRotation = new Vec3(RANDOM.nextDouble()*360,RANDOM.nextDouble()*360,RANDOM.nextDouble()*360);
            spin = new Vec3(angular(),angular(),angular());
        }
        static double angular() { return (RANDOM.nextBoolean() ? 1 : -1) * (15 + RANDOM.nextDouble()*20); }
        void tick() {
            previous = position; oldRotation = rotation;
            if (resting) return;
            var hit = world.clip(new ClipContext(position,position.add(velocity),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE, Minecraft.getInstance().player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                position = hit.getLocation().add(n.scale(.07));
                double impact = Math.abs(velocity.dot(n));
                if (!sounded && impact > .04) {
                    world.playLocalSound(position.x,position.y,position.z,AflSounds.CASING_LANDING.get(),
                            SoundSource.PLAYERS,.18F,.95F+RANDOM.nextFloat()*.1F,false);
                    sounded = true;
                }
                velocity = velocity.subtract(n.scale(velocity.dot(n)*1.32)).scale(.65);
                spin = spin.scale(.5);
                if (hit.getDirection() == net.minecraft.core.Direction.UP && (++bounces >= 2 || impact < .06)) {
                    resting = true; velocity = Vec3.ZERO;
                }
            } else position = position.add(velocity);
            if (!resting) velocity = velocity.add(0,-GRAVITY,0).scale(DRAG);
            rotation = rotation.add(spin);
        }
    }

    /** RenderType owns state setup/teardown; no leaked blend/depth state in Gecko/HUD. */
    private static final class FlashType extends RenderType {
        static final RenderType TYPE = create("afl_muzzle_flash",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,false,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new TextureStateShard(FLASH_TEXTURE,false,false))
                        // SRC_ALPHA + ONE: additive with the per-frame fade respected.
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));
        private FlashType() { super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,0,false,false,()->{},()->{}); }
    }
}
