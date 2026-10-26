package com.antaurora.apofirstlight.client.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.MeshRendering;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.compat.geckolib.GeckoMeshRenderer;
import com.antaurora.vextris.mesh.MeshModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * H1-P0 five-finger hand test (2026-10-23, docs/dev/mesh_hand_blender_geckolib_p0.md): dev clients only, nothing in
 * the hand pass or on any item. {@code /afl dev handtest} sets the P9-01 V3 (its own aflmesh at bind, the reload and
 * old magazines hidden) twice in the world in front of the player, each with the Blender-made hand playing
 * {@code open_to_grip_p9} through GeckoLib: on the player's left B, the continuous skinned body
 * (skins/dev/mesh_hand_p0.skin.json, linear blend skinning on the CPU from the bone matrices GeckoLib prepared, as
 * Vextris has no skinned part), on the right A, the rigid segments on the bones through Vextris
 * (meshes/dev/mesh_hand_p0.aflmesh.json). The clip replays every {@link #CYCLE} seconds.
 * {@code off} removes it; nothing is saved. {@code body} is the H1-P1 first-person body test ({@link MeshBodyFpTest}).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MeshHandTest {
    private static final ResourceLocation GEO = resource("geo/dev/mesh_hand_p0.geo.json");
    private static final ResourceLocation ANIMATION = resource("animations/dev/mesh_hand_p0.animation.json");
    private static final ResourceLocation SKIN = resource("skins/dev/mesh_hand_p0.skin.json");
    private static final ResourceLocation HAND_TEXTURE = resource("textures/dev/mesh_hand_p0.png");
    private static final ResourceLocation GUN_MESH = resource("p9_01_v3_native");
    private static final ResourceLocation GUN_TEXTURE = resource("textures/item/p9_01_v3_native.png");
    private static final RawAnimation GRIP = RawAnimation.begin().thenPlayAndHold("open_to_grip_p9");
    /** Seconds per replay: the 1.4 s clip and a held grip. */
    private static final double CYCLE = 3.0;
    /** Model px between the two copies along the barrel (gun and hand span 14.8 px), and the model px brought to the anchor. */
    private static final float SPACING = 18F, CENTRE_Y = 2F;

    private enum Show { BOTH, A, B }

    private static final ViewModel VIEW = new ViewModel();
    @Nullable
    private static Renderer renderer;
    @Nullable
    private static Skin skin;
    private static long skinGeneration = -1;
    @Nullable
    private static Vec3 anchor;
    private static float yaw, scale = 2F;
    private static boolean spin;
    private static Show show = Show.BOTH;
    private static long epoch, cycleStart;

    private MeshHandTest() {
    }

    private static ResourceLocation resource(String path) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path);
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("handtest")
                .executes(c -> on(c))
                .then(Commands.literal("on").executes(c -> on(c)))
                .then(Commands.literal("off").executes(c -> {
                    anchor = null;
                    return say(c, "手掌测试已关闭");
                }))
                .then(Commands.literal("replay").executes(c -> {
                    replay();
                    return say(c, "重新播放握持动画");
                }))
                .then(Commands.literal("spin").executes(c -> {
                    spin = !spin;
                    return say(c, spin ? "开始缓慢旋转" : "停止旋转");
                }))
                .then(Commands.literal("scale").then(Commands.argument("factor", FloatArgumentType.floatArg(0.25F, 8F))
                        .executes(c -> {
                            scale = FloatArgumentType.getFloat(c, "factor");
                            return say(c, "缩放 ×" + scale);
                        })))
                .then(Commands.literal("show")
                        .then(Commands.literal("both").executes(c -> show(c, Show.BOTH)))
                        .then(Commands.literal("a").executes(c -> show(c, Show.A)))
                        .then(Commands.literal("b").executes(c -> show(c, Show.B))))
                .then(MeshBodyFpTest.command()))));
    }

    private static int on(CommandContext<CommandSourceStack> c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        Vec3 look = Vec3.directionFromRotation(0, mc.player.getYRot());
        anchor = mc.player.getEyePosition().add(look.scale(1.1)).add(0, -0.15, 0);
        yaw = mc.player.getYRot();
        epoch = System.nanoTime();
        replay();
        return say(c, "手掌测试：左边 B 连续蒙皮，右边 A 分段刚体；replay / spin / scale / show / off");
    }

    private static int show(CommandContext<CommandSourceStack> c, Show which) {
        show = which;
        return say(c, "显示 " + which.name());
    }

    private static int say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static void replay() {
        cycleStart = System.nanoTime();
        if (VIEW.controller != null) VIEW.controller.forceAnimationReset();
    }

    private static final class ViewModel implements GeoAnimatable {
        final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        @Nullable
        AnimationController<ViewModel> controller;
        double ticks;

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controller = new AnimationController<>(this, "main", 0, state -> state.setAndContinue(GRIP));
            controllers.add(controller);
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object context) {
            return ticks;
        }
    }

    private static final class Model extends GeoModel<ViewModel> {
        @Override
        public ResourceLocation getModelResource(ViewModel view) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(ViewModel view) {
            return HAND_TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(ViewModel view) {
            return ANIMATION;
        }
    }

    /** Draws A's segments on the bones, or (capture) only keeps each bone's matrices for B. */
    private static final class Renderer extends GeoObjectRenderer<ViewModel> {
        final Map<String, Matrix4f> poses = new HashMap<>();
        final Map<String, Matrix3f> normals = new HashMap<>();
        boolean capture;
        @Nullable
        private MeshModel mesh;

        Renderer() {
            super(new Model());
        }

        @Override
        public void preRender(PoseStack pose, ViewModel view, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                              float partial, int light, int overlay, float red, float green, float blue, float alpha) {
            super.preRender(pose, view, model, buffers, buffer, reRender, partial, light, overlay, red, green, blue, alpha);
            pose.translate(-0.5, -0.51, -0.5);   // GeoObjectRenderer centres a world object; the rig is in model space
            mesh = VextrisMeshes.byGeometry(GEO);
        }

        @Override
        public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay,
                                      float red, float green, float blue, float alpha) {
            super.renderCubesOfBone(pose, bone, buffer, light, overlay, red, green, blue, alpha);
            if (capture) {
                poses.put(bone.getName(), new Matrix4f(pose.last().pose()));
                normals.put(bone.getName(), new Matrix3f(pose.last().normal()));
            } else if (mesh != null) {
                GeckoMeshRenderer.renderBone(mesh, bone, pose, buffer, light, overlay, red, green, blue, alpha);
            }
        }
    }

    /** B's body: model-block positions, uv, normals, up to four (bone, weight) per vertex, triangles. */
    private record Skin(String[] bones, float[] position, float[] uv, float[] normal, int[] weightBone, float[] weight, int[] triangles) {
        static final int WEIGHTS = 4;

        @Nullable
        static Skin load() {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(SKIN);
            if (resource.isEmpty()) return null;
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray bones = json.getAsJsonArray("bones"), vertices = json.getAsJsonArray("vertices"),
                        weights = json.getAsJsonArray("weights"), triangles = json.getAsJsonArray("triangles");
                int n = vertices.size();
                String[] names = new String[bones.size()];
                for (int i = 0; i < names.length; i++) names[i] = bones.get(i).getAsString();
                float[] position = new float[n * 3], uv = new float[n * 2], normal = new float[n * 3], weight = new float[n * WEIGHTS];
                int[] weightBone = new int[n * WEIGHTS];
                for (int i = 0; i < n; i++) {
                    JsonArray v = vertices.get(i).getAsJsonArray();
                    for (int k = 0; k < 3; k++) position[i * 3 + k] = v.get(k).getAsFloat();
                    for (int k = 0; k < 2; k++) uv[i * 2 + k] = v.get(3 + k).getAsFloat();
                    for (int k = 0; k < 3; k++) normal[i * 3 + k] = v.get(5 + k).getAsFloat();
                    JsonArray w = weights.get(i).getAsJsonArray();
                    for (int k = 0; k < Math.min(WEIGHTS, w.size()); k++) {
                        weightBone[i * WEIGHTS + k] = w.get(k).getAsJsonArray().get(0).getAsInt();
                        weight[i * WEIGHTS + k] = w.get(k).getAsJsonArray().get(1).getAsFloat();
                    }
                }
                int[] tris = new int[triangles.size() * 3];
                for (int t = 0; t < triangles.size(); t++) {
                    JsonArray tri = triangles.get(t).getAsJsonArray();
                    for (int k = 0; k < 3; k++) tris[t * 3 + k] = tri.get(k).getAsInt();
                }
                return new Skin(names, position, uv, normal, weightBone, weight, tris);
            } catch (Exception e) {
                ApocalypseFirstLight.LOGGER.warn("Mesh hand test: cannot read {}", SKIN, e);
                return null;
            }
        }

        /** Linear blend skinning with this frame's bone matrices, into a TRIANGLES consumer. */
        void draw(Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, VertexConsumer out, int light) {
            int n = position.length / 3;
            Matrix4f[] m = new Matrix4f[bones.length];
            Matrix3f[] nm = new Matrix3f[bones.length];
            for (int b = 0; b < bones.length; b++) {
                m[b] = poses.get(bones[b]);
                nm[b] = normals.get(bones[b]);
                if (m[b] == null || nm[b] == null) return;
            }
            float[] p = new float[n * 3], q = new float[n * 3];
            for (int i = 0; i < n; i++) {
                float x = position[i * 3], y = position[i * 3 + 1], z = position[i * 3 + 2];
                float nx = normal[i * 3], ny = normal[i * 3 + 1], nz = normal[i * 3 + 2];
                float px = 0, py = 0, pz = 0, qx = 0, qy = 0, qz = 0;
                for (int k = 0; k < WEIGHTS; k++) {
                    float w = weight[i * WEIGHTS + k];
                    if (w == 0) continue;
                    Matrix4f a = m[weightBone[i * WEIGHTS + k]];
                    Matrix3f r = nm[weightBone[i * WEIGHTS + k]];
                    px += w * (a.m00() * x + a.m10() * y + a.m20() * z + a.m30());
                    py += w * (a.m01() * x + a.m11() * y + a.m21() * z + a.m31());
                    pz += w * (a.m02() * x + a.m12() * y + a.m22() * z + a.m32());
                    qx += w * (r.m00() * nx + r.m10() * ny + r.m20() * nz);
                    qy += w * (r.m01() * nx + r.m11() * ny + r.m21() * nz);
                    qz += w * (r.m02() * nx + r.m12() * ny + r.m22() * nz);
                }
                float len = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
                if (len > 1e-6F) { qx /= len; qy /= len; qz /= len; }
                p[i * 3] = px; p[i * 3 + 1] = py; p[i * 3 + 2] = pz;
                q[i * 3] = qx; q[i * 3 + 1] = qy; q[i * 3 + 2] = qz;
            }
            for (int index : triangles) {
                out.vertex(p[index * 3], p[index * 3 + 1], p[index * 3 + 2], 1F, 1F, 1F, 1F, uv[index * 2], uv[index * 2 + 1],
                        OverlayTexture.NO_OVERLAY, light, q[index * 3], q[index * 3 + 1], q[index * 3 + 2]);
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (FMLEnvironment.production || anchor == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || AflShaderCompat.activeShadowPass()) return;
        MeshModel gun = VextrisMeshes.get(GUN_MESH);
        if (gun == null || VextrisMeshes.byGeometry(GEO) == null) return;
        if (renderer == null) renderer = new Renderer();
        if (skinGeneration != VextrisMeshes.generation()) {
            skin = Skin.load();
            skinGeneration = VextrisMeshes.generation();
        }
        long now = System.nanoTime();
        if ((now - cycleStart) / 1e9 > CYCLE) replay();
        VIEW.ticks = (now - epoch) / 5e7;   // ticks since "on", monotonic: replays restart through the controller

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(anchor));
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
        // model +x (the grip's right side, the palm) toward the player; the muzzle (-z) to the player's right
        float turn = spin ? (float)((now - epoch) / 1e9 * 30.0 % 360.0) : 0F;
        pose.mulPose(Axis.YP.rotationDegrees(90F - yaw + turn));
        pose.scale(scale, scale, scale);
        pose.translate(0, -CENTRE_Y / 16F, 0);
        if (show != Show.A) copy(pose, buffers, gun, light, SPACING / 2F, true);
        if (show != Show.B) copy(pose, buffers, gun, light, show == Show.A ? 0F : -SPACING / 2F, false);
        pose.popPose();
    }

    /** One gun and hand, {@code offset} model px along the barrel; B (skinned) or A (segments). */
    private static void copy(PoseStack pose, MultiBufferSource.BufferSource buffers, MeshModel gun, int light, float offset, boolean skinned) {
        pose.pushPose();
        pose.translate(0, 0, offset / 16F);
        MeshRendering.render(gun, GUN_TEXTURE, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                (bone, at) -> !bone.name().equals("reload_magazine") && !bone.name().equals("empty_old_mag"));
        buffers.endBatch();
        Renderer r = renderer;
        r.capture = skinned;
        RenderType type = skinned ? RenderType.entityCutoutNoCull(HAND_TEXTURE) : MeshRendering.cutoutType(HAND_TEXTURE);
        r.render(pose, VIEW, buffers, type, buffers.getBuffer(type), light);
        if (skinned && skin != null) {
            RenderType triangles = MeshRendering.cutoutType(HAND_TEXTURE, MeshRenderer.Topology.TRIANGLES);
            skin.draw(r.poses, r.normals, buffers.getBuffer(triangles), light);
        }
        buffers.endBatch();
        r.poses.clear();
        r.normals.clear();
        pose.popPose();
    }
}
