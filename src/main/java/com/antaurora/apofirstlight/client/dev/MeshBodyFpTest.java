package com.antaurora.apofirstlight.client.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.NativeGunAnimations;
import com.antaurora.apofirstlight.weapon.client.NativeGunInput;
import com.antaurora.vextris.client.BonePoser;
import com.antaurora.vextris.client.MeshRenderer;
import com.antaurora.vextris.client.MeshRendering;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * H1-P1 first-person body test (2026-10-23, docs/dev/mesh_survivor_base_body_h1p1.md section 5): dev clients only.
 * {@code /afl dev handtest body} replaces the hand pass with the Blender-made survivor body (geo / animation / skin
 * dev/survivor_base_h1p1) seen from its own eye: the arms (the triangles that are at least half arm or hand weight)
 * are skinned on the CPU and the P9-01 V3 rides on hand_r with the matrix the exporter wrote (skin "props").
 * <p>
 * Hip fire and ADS are the two ends of one raise: right mouse held moves the raise progress to 1 in 0.2 s, released
 * back to 0, and GeckoLib is seeked to that point of fp_raise (or fp_lower, the same motion from the ADS end, after
 * an ADS shot), so every in-between frame is the IK solve Blender baked, never a blend of two poses. A left click at
 * either end fires once (fp_fire_hip / fp_fire) and holds the raise until the shot has played; every clip switch
 * lands on the pose the body is already in. The camera sits at the head's eye point, level; the hand pass keeps its
 * fixed 70 degree projection and the view model is narrowed by scaling view x and y to the skin's fp_view field of
 * view (round 3: hip 42, ADS 34, eased with the raise) or to {@code fov}. Both clicks and the vanilla hands are cancelled
 * while it is on. {@code off} restores everything; nothing is saved, no item or server state is touched.
 * <p>
 * H1-P2 (2026-10-24, docs/dev/mesh_body_p9_fp_clips_h1p2.md): a shot plays the P9-01's own fire sound at the ear (the
 * shooter's {@code _near} copy, as RangedSoundInstance does). Body action clips {@code fp_<formal clip>} (reloads first)
 * start from hip with the reload key or {@code play <clip>}: the reload key is taken before NativeGunInput sees it, so no
 * real reload starts. While one plays, its sounds come from the formal P9 clip of the same name (NativeGunAnimations
 * cues, the timeline the server plays), the gun's parts (old and new magazine, slide) follow the props sidecar Blender
 * wrote, and the view turns by the formal clip's camera bone (author-space angles negated, as NativeCameraBoneConsumer).
 * R3 (user 2026-10-24): the hand pass turns with it too, so the body is seen from the turned head (the hands rise on
 * screen as it looks down) instead of staying fixed on screen like a vanilla view model.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MeshBodyFpTest {
    private static final ResourceLocation GEO = resource("geo/dev/survivor_base_h1p1.geo.json");
    private static final ResourceLocation ANIMATION = resource("animations/dev/survivor_base_h1p1.animation.json");
    private static final ResourceLocation SKIN = resource("skins/dev/survivor_base_h1p1.skin.json");
    private static final ResourceLocation TEXTURE = resource("textures/dev/survivor_base_h1p1.png");
    /** Per action clip: the gun parts' local transforms, which parts show, and the camera bone (Blender export). */
    private static final ResourceLocation PROPS = resource("skins/dev/survivor_base_h1p1.props.json");
    private static final ResourceLocation GUN_MESH = resource("p9_01_v3_native");
    /** The formal P9-01 animation whose clips give each body action its sounds (and, by name, its length). */
    private static final String GUN_ASSET = "p9_01_v3_native";
    /** What the reload key plays (the experienced tactical reload, the first clip of H1-P2). */
    private static final String RELOAD_CLIP = "reload_tactical_experienced";
    private static final ResourceLocation FIRE_SOUND = resource("p9_01_fire");
    private static final ResourceLocation GUN_TEXTURE = resource("textures/item/p9_01_v3_native.png");
    private static final RawAnimation RAISE = RawAnimation.begin().thenPlay("fp_raise");
    private static final RawAnimation LOWER = RawAnimation.begin().thenPlay("fp_lower");
    private static final RawAnimation FIRE = RawAnimation.begin().thenPlay("fp_fire");
    private static final RawAnimation FIRE_HIP = RawAnimation.begin().thenPlay("fp_fire_hip");
    /** fp_fire / fp_fire_hip length, and fp_raise / fp_lower length. */
    private static final double FIRE_SECONDS = 0.3;
    private static final float RAISE_SECONDS = 0.2F;
    /** The hand pass's fixed vertical field of view, and the view model one when the skin has no fp_view. */
    private static final float HAND_PASS_FOV = 70F, DEFAULT_FOV = 50F;
    /** The slide's travel in a shot, mesh px backward (the P9-01 V3 shoot clip's keys). */
    private static final float[] SLIDE_TIME = {0F, 0.0167F, 0.0333F, 0.05F, 0.0667F, 0.0833F};
    private static final float[] SLIDE_PX = {0F, 1.1419F, 1.7F, 1.1516F, 0.162F, 0F};

    private enum Hold { AUTO, HIP, ADS }

    private static final ViewModel VIEW = new ViewModel();
    private static final PoseStack SCRATCH = new PoseStack(), GUN_POSE = new PoseStack();
    private static final Matrix4f OUT = new Matrix4f();
    private static final Matrix3f OUT_NORMAL = new Matrix3f();
    private static final Vector3f EYE = new Vector3f();
    /**
     * In an action clip, its track places and shows the parts; otherwise hides the reload and dropped magazines and
     * runs the slide back and forth after a shot.
     */
    private static final BonePoser GUN_POSER = (bone, pose) -> {
        String name = bone.name();
        Action a = MeshBodyFpTest.action;
        if (a != null && a.track != null) return a.track.pose(name, a.time(MeshBodyFpTest.frameNow), pose);
        if (name.equals("reload_magazine") || name.equals("empty_old_mag")) return false;
        if (name.equals("slide")) pose.translate(0, 0, slide() / 16F);
        return true;
    };
    @Nullable
    private static Renderer renderer;
    @Nullable
    private static Rig rig;
    @Nullable
    private static RenderType triangles;
    /** Action tracks by formal clip name (props sidecar), loaded with the rig. */
    private static Map<String, Track> tracks = Map.of();
    /** The action clip playing, or null. */
    @Nullable
    private static Action action;
    private static long rigGeneration = -1;
    private static boolean on, aiming, firing, attackWasDown;
    /** Raise progress, 0 = hip, 1 = ADS, linear in time (the clip carries the easing). */
    private static float raise;
    /** The raise clip whose first frame is where the body is (fp_lower after an ADS shot). */
    private static RawAnimation raiseClip = RAISE;
    /** The shot playing, fp_fire or fp_fire_hip. */
    private static RawAnimation shot = FIRE;
    /** {@code fov} command override, or NaN to follow the skin's fp_view. */
    private static float fovOverride = Float.NaN;
    private static Hold hold = Hold.AUTO;
    private static long epoch, firedAt, frameNow, lastFrame;

    private MeshBodyFpTest() {
    }

    private static ResourceLocation resource(String path) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path);
    }

    /** The {@code body} branch of {@code /afl dev handtest}. */
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("body")
                .executes(MeshBodyFpTest::on)
                .then(Commands.literal("on").executes(MeshBodyFpTest::on))
                .then(Commands.literal("off").executes(c -> {
                    on = false;
                    return say(c, "第一人称人体测试已关闭");
                }))
                .then(Commands.literal("fov")
                        .then(Commands.literal("auto").executes(c -> {
                            fovOverride = Float.NaN;
                            Rig r = rig;
                            return say(c, r == null ? "手部视野跟随姿势"
                                    : "手部视野跟随姿势（腰射 " + Math.round(r.view[1]) + "°，瞄准 " + Math.round(r.view[3]) + "°）");
                        }))
                        .then(Commands.argument("degrees", FloatArgumentType.floatArg(20F, HAND_PASS_FOV)).executes(c -> {
                            fovOverride = FloatArgumentType.getFloat(c, "degrees");
                            return say(c, "手部视野固定 " + fovOverride + "°（原版手部通道 70°）");
                        })))
                .then(Commands.literal("pose")
                        .then(Commands.literal("auto").executes(c -> hold(c, Hold.AUTO, "按住右键瞄准")))
                        .then(Commands.literal("hip").executes(c -> hold(c, Hold.HIP, "固定腰射")))
                        .then(Commands.literal("ads").executes(c -> hold(c, Hold.ADS, "固定瞄准"))))
                .then(Commands.literal("fire").executes(c -> {
                    if (!settled() || action != null) return say(c, "举枪 / 放枪或动作途中不开枪");
                    fire(System.nanoTime());
                    return say(c, "开一枪");
                }))
                .then(Commands.literal("play")
                        .then(Commands.argument("clip", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(tracks.keySet(), b))
                                .executes(c -> {
                                    String clip = StringArgumentType.getString(c, "clip");
                                    if (!tracks.containsKey(clip)) return say(c, "人体还没有这个片段：" + clip);
                                    return say(c, start(clip, System.nanoTime()) ? "播放 fp_" + clip
                                            : clip.endsWith("_ads") ? "要先完全举枪瞄准、不在开枪或别的动作里"
                                            : "要先放下到腰射、不在开枪或别的动作里");
                                })));
    }

    private static int on(CommandContext<CommandSourceStack> c) {
        on = true;
        epoch = System.nanoTime();
        firedAt = epoch - (long)(FIRE_SECONDS * 2e9);
        lastFrame = 0;
        raise = 0F;
        raiseClip = RAISE;
        action = null;
        return say(c, "第一人称人体测试：右键瞄准，腰射或瞄准时左键开枪，腰射或瞄准时换弹键换弹；fov <度>|auto / pose auto|hip|ads / fire / play <片段> / off");
    }

    private static int hold(CommandContext<CommandSourceStack> c, Hold which, String text) {
        hold = which;
        return say(c, text);
    }

    private static int say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static boolean active() {
        return on && !FMLEnvironment.production;
    }

    /** At hip or fully raised: only there a shot (and its clip switch) starts from a baked pose. */
    private static boolean settled() {
        return raise == 0F || raise == 1F;
    }

    /** One shot from where the raise stands; afterwards the raise resumes on the clip that starts at that end. */
    private static void fire(long now) {
        firedAt = now;
        shot = raise == 1F ? FIRE : FIRE_HIP;
        raiseClip = raise == 1F ? LOWER : RAISE;
        if (VIEW.controller != null) VIEW.controller.forceAnimationReset();
        atEar(FIRE_SOUND);
    }

    /**
     * Starts body action {@code fp_<clip>} from hip, or an {@code _ads} one fully raised (its first and last frames are
     * that end's pose, so both clip switches land on the pose the body is in); false anywhere else, while firing or in
     * another action.
     */
    private static boolean start(String clip, long now) {
        Track track = tracks.get(clip);
        boolean ads = clip.endsWith("_ads");
        if (track == null || action != null || raise != (ads ? 1F : 0F) || (now - firedAt) / 1e9 < FIRE_SECONDS) return false;
        List<NativeGunAnimations.Cue> cues;
        try {
            cues = NativeGunAnimations.cues(GUN_ASSET, clip);
        } catch (RuntimeException missing) {
            cues = List.of();
        }
        cues.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
        action = new Action(clip, track, cues, now);
        raiseClip = ads ? LOWER : RAISE;
        if (VIEW.controller != null) VIEW.controller.forceAnimationReset();
        return true;
    }

    /** The shooter's copy of a gun sound (its {@code _near} variant when sounds.json has one), at the ear. */
    private static void atEar(ResourceLocation id) {
        var sounds = Minecraft.getInstance().getSoundManager();
        ResourceLocation near = new ResourceLocation(id.getNamespace(), id.getPath() + "_near");
        sounds.play(new SimpleSoundInstance(sounds.getSoundEvent(near) != null ? near : id, SoundSource.PLAYERS, 1F, 1F,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    /** An action cue, bound to the player as NativeGunActions#sound plays it on the server. */
    private static void onPlayer(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(id), SoundSource.PLAYERS,
                1F, 1F, mc.player, RandomSource.create().nextLong()));
    }

    /** Slide travel in mesh px at this frame (0 outside a shot). */
    private static float slide() {
        float t = (frameNow - firedAt) / 1e9F;
        if (t <= 0 || t >= SLIDE_TIME[SLIDE_TIME.length - 1]) return 0F;
        int i = 1;
        while (SLIDE_TIME[i] < t) i++;
        float u = (t - SLIDE_TIME[i - 1]) / (SLIDE_TIME[i] - SLIDE_TIME[i - 1]);
        return SLIDE_PX[i - 1] + (SLIDE_PX[i] - SLIDE_PX[i - 1]) * u;
    }

    /**
     * Plays its clip at {@link #seek} instead of by time while one is set. GeckoLib asks {@code adjustTick} for the
     * clip time; a clip switch (no blend here) and its first frame keep GeckoLib's own value, and a held last frame
     * (PAUSED) is seeked too, so the raise can run back from the end.
     */
    private static final class SeekController extends AnimationController<ViewModel> {
        /** GeckoLib ticks into the playing clip, or below 0 to play it by time. */
        double seek = -1;

        SeekController(ViewModel view, AnimationStateHandler<ViewModel> handler) {
            super(view, "main", 0, handler);
        }

        @Override
        protected double adjustTick(double tick) {
            double t = super.adjustTick(tick);
            State s = getAnimationState();
            return seek >= 0 && (s == State.RUNNING || s == State.PAUSED) ? seek : t;
        }
    }

    private static final class ViewModel implements GeoAnimatable {
        final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        @Nullable
        SeekController controller;
        double ticks;

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controller = new SeekController(this, state -> {
                Action a = action;
                return state.setAndContinue(a != null ? a.animation : firing ? shot : raiseClip);
            });
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
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(ViewModel view) {
            return ANIMATION;
        }
    }

    /** Draws nothing: keeps each bone's deformation (model blocks, rest to posed) in the rig's slots. */
    private static final class Renderer extends GeoObjectRenderer<ViewModel> {
        Renderer() {
            super(new Model());
        }

        @Override
        public void preRender(PoseStack pose, ViewModel view, BakedGeoModel model, MultiBufferSource buffers, VertexConsumer buffer, boolean reRender,
                              float partial, int light, int overlay, float red, float green, float blue, float alpha) {
            super.preRender(pose, view, model, buffers, buffer, reRender, partial, light, overlay, red, green, blue, alpha);
            pose.translate(-0.5, -0.51, -0.5);   // GeoObjectRenderer centres a world object; the rig is in model space
        }

        @Override
        public void renderCubesOfBone(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay,
                                      float red, float green, float blue, float alpha) {
            Rig r = rig;
            Integer i = r == null ? null : r.index.get(bone.getName());
            if (i == null) return;
            r.bone[i].set(pose.last().pose());
            r.boneNormal[i].set(pose.last().normal());
            r.captured++;
        }
    }

    /**
     * The skinned body (model blocks), its eye point on the head, the gun's matrix on its hand, the first-person
     * triangles, and the per-frame bone slots and skinned arrays (allocated once at load).
     */
    private static final class Rig {
        static final int WEIGHTS = 4;
        final Map<String, Integer> index = new HashMap<>();
        final float[] position, uv, normal, weight, skinned, skinnedNormal;
        final int[] weightBone, triangles, vertices;
        final Matrix4f[] bone, boneOut;
        final Matrix3f[] boneNormal, boneNormalOut;
        final Vector3f eye;
        final int head, gunBone;
        final Matrix4f gun;
        final Matrix3f gunNormal;
        /** fp_view: metres behind the eye and vertical fov, at hip (0, 1) and ADS (2, 3). */
        final float[] view = {0F, DEFAULT_FOV, 0F, DEFAULT_FOV};
        int captured;

        private Rig(JsonObject json) {
            JsonArray bones = json.getAsJsonArray("bones"), verts = json.getAsJsonArray("vertices"),
                    weights = json.getAsJsonArray("weights"), tris = json.getAsJsonArray("triangles");
            int n = verts.size(), b = bones.size();
            boolean[] arm = new boolean[b];
            JsonArray fpBones = json.getAsJsonArray("fp_bones");
            for (int i = 0; i < b; i++) {
                String name = bones.get(i).getAsString();
                index.put(name, i);
                for (int k = 0; k < fpBones.size(); k++) arm[i] |= name.split("_")[0].equals(fpBones.get(k).getAsString());
                arm[i] |= name.startsWith("upper_arm") || name.startsWith("forearm");
            }
            position = new float[n * 3];
            uv = new float[n * 2];
            normal = new float[n * 3];
            weight = new float[n * WEIGHTS];
            weightBone = new int[n * WEIGHTS];
            float[] armWeight = new float[n];
            for (int i = 0; i < n; i++) {
                JsonArray v = verts.get(i).getAsJsonArray();
                for (int k = 0; k < 3; k++) position[i * 3 + k] = v.get(k).getAsFloat();
                for (int k = 0; k < 2; k++) uv[i * 2 + k] = v.get(3 + k).getAsFloat();
                for (int k = 0; k < 3; k++) normal[i * 3 + k] = v.get(5 + k).getAsFloat();
                JsonArray w = weights.get(i).getAsJsonArray();
                for (int k = 0; k < Math.min(WEIGHTS, w.size()); k++) {
                    int bi = w.get(k).getAsJsonArray().get(0).getAsInt();
                    weightBone[i * WEIGHTS + k] = bi;
                    weight[i * WEIGHTS + k] = w.get(k).getAsJsonArray().get(1).getAsFloat();
                    if (arm[bi]) armWeight[i] += weight[i * WEIGHTS + k];
                }
            }
            // first person draws only the triangles whose three corners are at least half arm or hand
            int[] kept = new int[tris.size() * 3];
            boolean[] used = new boolean[n];
            int count = 0;
            for (int t = 0; t < tris.size(); t++) {
                JsonArray tri = tris.get(t).getAsJsonArray();
                int a = tri.get(0).getAsInt(), c = tri.get(1).getAsInt(), d = tri.get(2).getAsInt();
                if (armWeight[a] < 0.5F || armWeight[c] < 0.5F || armWeight[d] < 0.5F) continue;
                kept[count++] = a;
                kept[count++] = c;
                kept[count++] = d;
                used[a] = used[c] = used[d] = true;
            }
            triangles = Arrays.copyOf(kept, count);
            int u = 0;
            for (boolean x : used) if (x) u++;
            vertices = new int[u];
            for (int i = 0, k = 0; i < n; i++) if (used[i]) vertices[k++] = i;
            skinned = new float[n * 3];
            skinnedNormal = new float[n * 3];
            bone = new Matrix4f[b];
            boneOut = new Matrix4f[b];
            boneNormal = new Matrix3f[b];
            boneNormalOut = new Matrix3f[b];
            for (int i = 0; i < b; i++) {
                bone[i] = new Matrix4f();
                boneOut[i] = new Matrix4f();
                boneNormal[i] = new Matrix3f();
                boneNormalOut[i] = new Matrix3f();
            }
            JsonArray e = json.getAsJsonArray("eye");
            eye = new Vector3f(e.get(0).getAsFloat(), e.get(1).getAsFloat(), e.get(2).getAsFloat());
            head = index.get("head");
            JsonObject p9 = json.getAsJsonObject("props").getAsJsonObject("p9_01");
            gunBone = index.get(p9.get("bone").getAsString());
            float[] k = new float[16];
            JsonArray m = p9.getAsJsonArray("matrix");
            for (int i = 0; i < 16; i++) k[i] = m.get(i).getAsFloat();
            gun = new Matrix4f().set(k);   // column-major: P9 mesh blocks to body model blocks at rest
            gunNormal = new Matrix3f();
            gun.get3x3(gunNormal);
            float scale = (float)Math.sqrt(k[0] * k[0] + k[1] * k[1] + k[2] * k[2]);
            gunNormal.scale(1F / scale);   // a similarity: its rotation alone turns the normals
            JsonObject fpView = json.getAsJsonObject("fp_view");
            if (fpView != null) {
                String[] ends = {"hip", "ads"};
                for (int i = 0; i < 2; i++) {
                    JsonArray v = fpView.getAsJsonArray(ends[i]);
                    view[i * 2] = v.get(0).getAsFloat();
                    view[i * 2 + 1] = v.get(1).getAsFloat();
                }
            }
        }

        @Nullable
        static Rig load() {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(SKIN);
            if (resource.isEmpty()) return null;
            try (Reader reader = resource.get().openAsReader()) {
                return new Rig(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                ApocalypseFirstLight.LOGGER.warn("Mesh body first-person test: cannot read {}", SKIN, e);
                return null;
            }
        }

        /** Linear blend skinning of the first-person vertices with {@link #boneOut}, into a TRIANGLES consumer. */
        void draw(VertexConsumer out, int light) {
            for (int i : vertices) {
                float x = position[i * 3], y = position[i * 3 + 1], z = position[i * 3 + 2];
                float nx = normal[i * 3], ny = normal[i * 3 + 1], nz = normal[i * 3 + 2];
                float px = 0, py = 0, pz = 0, qx = 0, qy = 0, qz = 0;
                for (int k = 0; k < WEIGHTS; k++) {
                    float w = weight[i * WEIGHTS + k];
                    if (w == 0) continue;
                    Matrix4f a = boneOut[weightBone[i * WEIGHTS + k]];
                    Matrix3f r = boneNormalOut[weightBone[i * WEIGHTS + k]];
                    px += w * (a.m00() * x + a.m10() * y + a.m20() * z + a.m30());
                    py += w * (a.m01() * x + a.m11() * y + a.m21() * z + a.m31());
                    pz += w * (a.m02() * x + a.m12() * y + a.m22() * z + a.m32());
                    qx += w * (r.m00() * nx + r.m10() * ny + r.m20() * nz);
                    qy += w * (r.m01() * nx + r.m11() * ny + r.m21() * nz);
                    qz += w * (r.m02() * nx + r.m12() * ny + r.m22() * nz);
                }
                float len = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);
                if (len > 1e-6F) { qx /= len; qy /= len; qz /= len; }
                skinned[i * 3] = px; skinned[i * 3 + 1] = py; skinned[i * 3 + 2] = pz;
                skinnedNormal[i * 3] = qx; skinnedNormal[i * 3 + 1] = qy; skinnedNormal[i * 3 + 2] = qz;
            }
            for (int i : triangles) {
                out.vertex(skinned[i * 3], skinned[i * 3 + 1], skinned[i * 3 + 2], 1F, 1F, 1F, 1F, uv[i * 2], uv[i * 2 + 1],
                        OverlayTexture.NO_OVERLAY, light, skinnedNormal[i * 3], skinnedNormal[i * 3 + 1], skinnedNormal[i * 3 + 2]);
            }
        }
    }

    /** A body action playing: its clip, track, the formal clip's sound cues (ticks) and when it started. */
    private static final class Action {
        final RawAnimation animation;
        final Track track;
        final List<NativeGunAnimations.Cue> cues;
        final long start;
        int nextCue;

        Action(String clip, Track track, List<NativeGunAnimations.Cue> cues, long start) {
            this.animation = RawAnimation.begin().thenPlay("fp_" + clip);
            this.track = track;
            this.cues = cues;
            this.start = start;
        }

        double time(long now) {
            return Math.max(0, (now - start) / 1e9);
        }
    }

    /**
     * One action clip's props track, sampled at its own frame rate: per gun part a local transform at the bone's
     * pivot (column-major, model blocks: the part's place relative to the gun at rest), per part whether it shows (a
     * hidden part hides its children), and the camera bone's author-space rotation (deg).
     */
    private static final class Track {
        final float fps;
        final int frames;
        final double length;
        final Map<String, float[]> parts = new HashMap<>();
        final Map<String, byte[]> shown = new HashMap<>();
        final float[] camera;
        private final Matrix4f m = new Matrix4f();
        private final Matrix3f n = new Matrix3f();
        private final float[] k = new float[16];

        private Track(JsonObject json, float fps) {
            this.fps = fps;
            frames = json.get("frames").getAsInt();
            length = json.get("length").getAsDouble();
            json.getAsJsonObject("parts").entrySet().forEach(e -> parts.put(e.getKey(), floats(e.getValue().getAsJsonArray())));
            json.getAsJsonObject("shown").entrySet().forEach(e -> {
                JsonArray a = e.getValue().getAsJsonArray();
                byte[] b = new byte[a.size()];
                for (int i = 0; i < b.length; i++) b[i] = (byte)a.get(i).getAsInt();
                shown.put(e.getKey(), b);
            });
            camera = floats(json.getAsJsonArray("camera"));
        }

        private static float[] floats(JsonArray a) {
            float[] f = new float[a.size()];
            for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
            return f;
        }

        static Map<String, Track> load() {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(PROPS);
            if (resource.isEmpty()) return Map.of();
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                float fps = json.get("fps").getAsFloat();
                Map<String, Track> out = new HashMap<>();
                json.getAsJsonObject("clips").entrySet().forEach(e -> out.put(e.getKey(), new Track(e.getValue().getAsJsonObject(), fps)));
                return out;
            } catch (Exception e) {
                ApocalypseFirstLight.LOGGER.warn("Mesh body first-person test: cannot read {}", PROPS, e);
                return Map.of();
            }
        }

        /** The frame t falls in and how far it is to the next. */
        private float at(double t, int[] frame) {
            double f = Math.max(0, Math.min(frames - 1, t * fps));
            frame[0] = (int)Math.min(frames - 2, Math.floor(f));
            return (float)(f - frame[0]);
        }

        private final int[] frame = new int[1];

        boolean pose(String name, double t, PoseStack pose) {
            float u = at(t, frame);
            int f = frame[0];
            byte[] s = shown.get(name);
            if (s != null && s[u < 0.5F ? f : f + 1] == 0) return false;
            float[] p = parts.get(name);
            if (p == null) return true;
            for (int i = 0; i < 16; i++) k[i] = p[f * 16 + i] + (p[(f + 1) * 16 + i] - p[f * 16 + i]) * u;
            m.set(k);
            m.get3x3(n);
            pose.last().pose().mul(m);
            pose.last().normal().mul(n);
            return true;
        }

        void camera(double t, float[] out) {
            float u = at(t, frame);
            int f = frame[0];
            for (int i = 0; i < 3; i++) out[i] = camera[f * 3 + i] + (camera[(f + 1) * 3 + i] - camera[f * 3 + i]) * u;
        }
    }

    private static final float[] CAMERA = new float[3];
    private static final float[] HAND_CAMERA = new float[3];

    /**
     * The reload key starts the body's reload instead of a real one (taken before NativeGunInput reads it at END):
     * at hip the hip clip, fully aimed its {@code _ads} one.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keys(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active()) return;
        boolean pressed = false;
        while (NativeGunInput.reloadKey().consumeClick()) pressed = true;
        if (pressed && Minecraft.getInstance().screen == null)
            start(raise == 1F ? RELOAD_CLIP + "_ads" : RELOAD_CLIP, System.nanoTime());
    }

    /** The action's camera bone, as NativeCameraBoneConsumer turns a formal clip's: author-space angles negated. */
    @SubscribeEvent
    public static void view(ViewportEvent.ComputeCameraAngles event) {
        Action a = action;
        if (!active() || a == null || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        a.track.camera(a.time(System.nanoTime()), CAMERA);
        event.setPitch(event.getPitch() - CAMERA[0]);
        event.setYaw(event.getYaw() - CAMERA[1]);
        event.setRoll(event.getRoll() - CAMERA[2]);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void render(RenderHandEvent event) {
        if (!active()) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        MeshModel gun = VextrisMeshes.get(GUN_MESH);
        if (mc.player == null || gun == null) return;
        if (renderer == null) renderer = new Renderer();
        if (rigGeneration != VextrisMeshes.generation()) {
            rig = Rig.load();
            tracks = Track.load();
            action = null;
            triangles = MeshRendering.cutoutType(TEXTURE, MeshRenderer.Topology.TRIANGLES);
            rigGeneration = VextrisMeshes.generation();
        }
        Rig r = rig;
        if (r == null || triangles == null) return;

        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0F : Math.min(0.1F, (now - lastFrame) / 1e9F);
        lastFrame = now;
        frameNow = now;
        Action a = action;
        if (a != null) {                    // the formal clip's sounds as their ticks come; done at its length
            double t = a.time(now);
            while (a.nextCue < a.cues.size() && a.cues.get(a.nextCue).tick() / 20.0 <= t) onPlayer(a.cues.get(a.nextCue++).sound());
            if (t >= a.track.length) action = a = null;
        }
        aiming = hold == Hold.ADS || hold == Hold.AUTO && mc.screen == null && mc.options.keyUse.isDown();
        boolean attack = mc.screen == null && mc.options.keyAttack.isDown();
        if (attack && !attackWasDown && settled() && a == null) fire(now);
        attackWasDown = attack;
        firing = (now - firedAt) / 1e9 < FIRE_SECONDS;
        if (!firing && a == null) raise = Math.max(0F, Math.min(1F, raise + (aiming ? dt : -dt) / RAISE_SECONDS));
        VIEW.ticks = (now - epoch) / 5e7;   // ticks since "on"
        if (VIEW.controller != null)        // GeckoLib ticks into fp_raise (or, from the ADS end, fp_lower); an action plays by time
            VIEW.controller.seek = firing || a != null ? -1 : (raiseClip == RAISE ? raise : 1F - raise) * RAISE_SECONDS * 20.0;

        // 1. GeckoLib poses the body; the renderer keeps each bone's deformation
        MultiBufferSource buffers = event.getMultiBufferSource();
        VertexConsumer skin = buffers.getBuffer(triangles);
        r.captured = 0;
        renderer.render(SCRATCH, VIEW, buffers, triangles, skin, event.getPackedLight());
        if (r.captured < r.bone.length) return;

        // 2. view = narrow-fov scale . hand pass pose . the action's camera bone turn (roll, pitch, yaw, as the world
        // view gets it in view()) . turn to face -z . from the view point (the head's eye this frame, moved back along
        // the body) to the origin; fp_view eased with the raise as the clip is (smoothstep)
        r.bone[r.head].transformPosition(r.eye, EYE);
        float u = raise * raise * (3F - 2F * raise);
        float back = r.view[0] + (r.view[2] - r.view[0]) * u;
        float fov = Float.isNaN(fovOverride) ? r.view[1] + (r.view[3] - r.view[1]) * u : fovOverride;
        float s = (float)(Math.tan(Math.toRadians(HAND_PASS_FOV / 2F)) / Math.tan(Math.toRadians(fov / 2F)));
        float pitch = 0F, yaw = 0F, roll = 0F;
        if (a != null) {
            a.track.camera(a.time(now), HAND_CAMERA);
            pitch = (float)Math.toRadians(-HAND_CAMERA[0]);
            yaw = (float)Math.toRadians(-HAND_CAMERA[1]);
            roll = (float)Math.toRadians(-HAND_CAMERA[2]);
        }
        PoseStack.Pose hand = event.getPoseStack().last();
        OUT.scaling(s, s, 1F).mul(hand.pose()).rotateZ(roll).rotateX(pitch).rotateY(yaw)
                .rotateY((float)Math.PI).translate(-EYE.x, -EYE.y, -EYE.z + back);
        OUT_NORMAL.set(hand.normal()).rotateZ(roll).rotateX(pitch).rotateY(yaw).rotateY((float)Math.PI);   // lit as if unscaled
        for (int b = 0; b < r.bone.length; b++) {
            OUT.mul(r.bone[b], r.boneOut[b]);
            OUT_NORMAL.mul(r.boneNormal[b], r.boneNormalOut[b]);
        }

        // 3. the gun on hand_r, then the arms
        PoseStack.Pose g = GUN_POSE.last();
        g.pose().set(r.boneOut[r.gunBone]).mul(r.gun);
        g.normal().set(r.boneNormalOut[r.gunBone]).mul(r.gunNormal);
        MeshRendering.render(gun, GUN_TEXTURE, GUN_POSE, buffers, event.getPackedLight(), OverlayTexture.NO_OVERLAY, GUN_POSER);
        r.draw(buffers.getBuffer(triangles), event.getPackedLight());
    }
}
