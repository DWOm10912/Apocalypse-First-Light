package com.antaurora.apofirstlight.client.dev;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.ChairSit;
import com.antaurora.apofirstlight.entity.OfficeChairEntity;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.vextris.client.BonePoser;
import com.antaurora.vextris.client.MeshRendering;
import com.antaurora.vextris.client.SkinPose;
import com.antaurora.vextris.client.VextrisMeshes;
import com.antaurora.vextris.mesh.MeshModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mesh survivor V1 M2.5 in-game static test (2026-10-25, docs/dev/mesh_survivor_v1_m25_ingame.md): dev clients only,
 * off by default, never for every player. {@code /afl dev meshbody} names the players (by name, on this client) whose
 * third-person draw is replaced by the Blender-made male survivor (mesh dev/survivor_male_v1, Vextris 0.1.1-alpha.5
 * skinned parts, one atlas texture): the vanilla player render is cancelled for them and the body is drawn at one of
 * the exported static test poses (skins/dev/survivor_male_v1.poses.json), the head turned to the look. Auto pose: the
 * P9-01 in the main hand holds it two-handed standing (pose H, the gun on hand_r with the H1-P1 grip matrix), anything
 * else plays the third-person clips (docs/dev/mesh_survivor_v1_tp_locomotion_a1.md), retargeted from CMU motion capture
 * by design/characters/survivor_male_v1/source/anim_tp_v1.py: standing Idle / Walk / Jog / Run / Sprint blended by the
 * smoothed ground speed, crouched Idle / Walk by the sneak, seated on the wooden and the office chair. The moving clips
 * all start at the left foot's touchdown and share one gait phase, advanced by the distance moved along the body's
 * facing over the blended stride (mostly backwards plays them in reverse), so the stance foot holds at any speed and
 * gaits blend in step; in the air the phase holds. Idle and Run are needed (else the static stand, pose A), a missing
 * other clip falls back to its nearest. States without a clip (swimming, crawling, gliding, sleeping, dying, other
 * seats and vehicles, the P9-01 crouched or seated) keep the vanilla render.
 * <p>
 * The own first-person view keeps the vanilla first-person body and the hand pass (no second body round the camera).
 * A missing mesh, texture or pose file leaves every player to the vanilla render. {@code crowd <n>} draws n more
 * copies round the own body (F5) for the cost probe; {@code stats} gives the measured CPU skinning time of the last
 * two seconds (main and shadow pass apart). Nothing is saved, nothing is sent to a server.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MeshBodyTpTest {
    private static final ResourceLocation MESH = resource("dev/survivor_male_v1");
    private static final ResourceLocation TEXTURE = resource("textures/dev/survivor_male_v1.png");
    private static final ResourceLocation POSES = resource("skins/dev/survivor_male_v1.poses.json");
    private static final ResourceLocation GUN_MESH = resource("p9_01_v3_native");
    private static final ResourceLocation GUN_TEXTURE = resource("textures/item/p9_01_v3_native.png");
    private static final String STAND = "A_stand", HOLD = "H_p9_two_hand", AUTO = "auto", TP = "tp_";
    /**
     * Gait bands over the smoothed ground speed (m/s), each from / full: idle -> walk, walk -> fast walk, fast walk ->
     * jog, jog -> run, run -> sprint (vanilla walk 4.32, sprint 5.61, sneak 1.3; AFL's load and injury slow-downs hold any
     * speed from 1.3 up; the clips' reference speeds 1.25 / 2.04 / 2.51 / 4.19 / 5.44). Blends of a stance leg with a
     * swing leg do not hold the feet, so the walk -> run step is narrow (people change gait at about 2.1 m/s) and the
     * starts and stops pass low; jog, run and sprint share one source and blend in step. Crouched: crouch idle -> crouch
     * walk over {@link #CROUCH_BAND} (a loaded sneak is 0.4 m/s).
     */
    private static final float[] BANDS = {0.06F, 0.25F, 1.3F, 1.8F, 2.05F, 2.25F, 2.6F, 4.0F, 4.5F, 5.4F};
    private static final float[] CROUCH_BAND = {0.06F, 0.25F};
    /** Per tick the smoothed speed and the crouch weight move this share of the way to their targets (about 0.15 s). */
    private static final float BLEND_RATE = 0.35F;
    /** A move longer than this in one tick is a teleport, not a step (blocks). */
    private static final double TELEPORT = 2.0;
    /** The head follows the look up to these (deg). */
    private static final float HEAD_YAW = 70F, HEAD_PITCH = 60F;
    /** Crowd copies stand on a ring this far round the own body (blocks). */
    private static final float CROWD_RADIUS = 1.4F;
    private static final int MAX_CROWD = 16;
    private static final long WINDOW_NANOS = 2_000_000_000L;
    /** The P9-01's reload and dropped magazines are only shown by the reload clips. */
    private static final BonePoser GUN_POSER = (bone, pose) ->
            !bone.name().equals("reload_magazine") && !bone.name().equals("empty_old_mag");

    private static final Set<String> players = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static String pose = AUTO;
    private static int crowd;
    @Nullable
    private static Poses poses;
    private static long posesGeneration = -1;
    private static boolean warned;

    /**
     * The third-person clips (skins/dev/survivor_male_v1_tp_&lt;file&gt;_v1.clip.json): moving ones advance by the gait
     * phase, the rest by time. A missing clip plays its fallback's (none: that state keeps the vanilla render).
     */
    private enum Gait {
        IDLE("idle", false, null), RUN("run", true, null), WALK("walk", true, RUN), WALK_FAST("walk_fast", true, WALK),
        JOG("jog", true, RUN), SPRINT("sprint", true, RUN), CROUCH_IDLE("crouch_idle", false, IDLE),
        CROUCH_WALK("crouch_walk", true, WALK),
        SIT_CHAIR("sit_chair", false, null), SIT_OFFICE("sit_office", false, SIT_CHAIR);

        final String file;
        final boolean moving;
        @Nullable
        final Gait fallback;

        Gait(String file, boolean moving, @Nullable Gait fallback) {
            this.file = file;
            this.moving = moving;
            this.fallback = fallback;
        }

        ResourceLocation id() {
            return resource("skins/dev/survivor_male_v1_tp_" + file + "_v1.clip.json");
        }
    }

    private static final Gait[] GAITS = Gait.values();
    /** Standing gaits in band order. */
    private static final Gait[] STANDING = {Gait.IDLE, Gait.WALK, Gait.WALK_FAST, Gait.JOG, Gait.RUN, Gait.SPRINT};
    /**
     * The lowest boot points at rest, right side, model blocks (heel and ball on foot_r, toe tip on toes_r; anim_tp_v1
     * SOLE); the left side mirrors x.
     */
    private static final float[][] SOLE = {{-0.107F, -0.003F, -0.053F}, {-0.116F, -0.003F, 0.117F}, {-0.114F, -0.001F, 0.191F}};

    private static final SkinPose SKIN = new SkinPose();
    private static final PoseStack GUN_POSE = new PoseStack();
    private static final Matrix4f M = new Matrix4f(), LOOK = new Matrix4f();
    private static final Matrix3f N = new Matrix3f(), LOOK_NORMAL = new Matrix3f();
    private static final Vector3f PIVOT = new Vector3f(), OFFSET = new Vector3f();
    private static final Quaternionf QUAT = new Quaternionf(), QUAT_SAMPLE = new Quaternionf(), QUAT_TMP = new Quaternionf();
    /** This frame's blend: the clips, their phases and weights (count of them). */
    private static final Clip[] MIX = new Clip[GAITS.length];
    private static final float[] MIX_PHASE = new float[GAITS.length], MIX_WEIGHT = new float[GAITS.length];
    private static final float[] WEIGHT = new float[GAITS.length];
    /** Per listed player (entity id): the travel and run weight of the last two ticks, the clip pose of this frame. */
    private static final Map<Integer, Motion> motions = new HashMap<>();
    private static long renderFrame;

    /** Measured skinning time (renderSkins: CPU skinning and vertex submission), per pass. */
    private static final Stats MAIN = new Stats(), SHADOW = new Stats();
    /** Measured clip sampling + bone chain time: once per body and frame, shared by both passes and the crowd. */
    private static final Stats POSE = new Stats();

    private MeshBodyTpTest() {
    }

    private static ResourceLocation resource(String path) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, path);
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("meshbody")
                .executes(MeshBodyTpTest::status)
                .then(Commands.literal("self").executes(c -> {
                    Player p = Minecraft.getInstance().player;
                    return p == null ? 0 : add(c, p.getGameProfile().getName());
                }))
                .then(Commands.literal("add").then(Commands.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(onlineNames(), b))
                        .executes(c -> add(c, StringArgumentType.getString(c, "player")))))
                .then(Commands.literal("remove").then(Commands.argument("player", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(players, b))
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "player");
                            return say(c, players.remove(name) ? name + " 恢复原版模型" : name + " 不在名单里");
                        })))
                .then(Commands.literal("off").executes(c -> {
                    players.clear();
                    motions.clear();
                    crowd = 0;
                    return say(c, "Mesh 身体测试已关闭，所有玩家恢复原版模型");
                }))
                .then(Commands.literal("pose").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(poseNames(), b))
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            if (!name.equals(AUTO) && !poseNames().contains(name)) return say(c, "没有这个测试姿势：" + name);
                            pose = name;
                            Gait g = gait(name);
                            return say(c, name.equals(AUTO) ? "姿势：自动（站着手持 P9-01 双手握枪，其他按速度、潜行、座位播放动作）"
                                    : g != null ? (g.moving ? "固定原地按自身节奏播放 " : "固定播放 ") + name
                                    : "姿势固定为 " + name);
                        })))
                .then(Commands.literal("crowd").then(Commands.argument("count", IntegerArgumentType.integer(0, MAX_CROWD))
                        .executes(c -> {
                            crowd = IntegerArgumentType.getInteger(c, "count");
                            return say(c, crowd == 0 ? "不画额外的身体" : "在自己身边多画 " + crowd + " 个身体（第三人称看）");
                        })))
                .then(Commands.literal("stats").executes(MeshBodyTpTest::stats)))));
    }

    private static List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.getOnlinePlayers().forEach(i -> out.add(i.getProfile().getName()));
        return out;
    }

    private static List<String> poseNames() {
        List<String> out = new ArrayList<>(List.of(AUTO));
        Poses p = loaded();
        if (p != null && p.hasClips()) {
            for (Gait g : GAITS) if (p.clips[g.ordinal()] != null) out.add(TP + g.file);
        }
        if (p != null) out.addAll(p.frames.keySet());
        return out;
    }

    /** The clip a pose name plays (tp_&lt;file&gt;), or null. */
    @Nullable
    private static Gait gait(String name) {
        if (!name.startsWith(TP)) return null;
        for (Gait g : GAITS) if (name.equals(TP + g.file)) return g;
        return null;
    }

    private static int add(CommandContext<CommandSourceStack> c, String name) {
        players.add(name);
        String missing = VextrisMeshes.get(MESH) == null ? "（Mesh 没有加载，仍是原版模型，看日志）"
                : loaded() == null ? "（姿势文件没有加载，仍是原版模型，看日志）" : "";
        return say(c, name + " 用 Mesh 身体" + missing + "；名单：" + String.join("、", players));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        MeshModel model = VextrisMeshes.get(MESH);
        String mesh = model == null ? "Mesh 未加载" : "Mesh 已加载：" + model.skins().size() + " 个蒙皮部件，"
                + model.triangleEquivalent() + " 个三角形，" + model.bones().size() + " 根骨骼";
        Poses p = loaded();
        return say(c, mesh + (p == null ? "；姿势文件未加载" : !p.hasClips() ? "；动作片段未加载"
                : "；动作片段已加载 " + p.loadedClips() + " / " + GAITS.length) + "；名单："
                + (players.isEmpty() ? "空" : String.join("、", players)) + "；姿势 " + pose + "；额外 " + crowd
                + " 个。self / add <玩家> / remove <玩家> / pose <名称|auto> / crowd <个数> / stats / off");
    }

    private static int stats(CommandContext<CommandSourceStack> c) {
        String shader = AflShaderCompat.shaderPackInUse() ? "光影 " + AflShaderCompat.currentPackLabel() : "无光影";
        Player self = Minecraft.getInstance().player;
        Motion m = self == null ? null : motions.get(self.getId());
        String move = "";
        if (m != null) {
            float[] w = new float[GAITS.length];
            weights(m.smooth, m.crouch, w);
            StringBuilder mix = new StringBuilder();
            for (Gait g : GAITS) {
                if (w[g.ordinal()] < 0.01F) continue;
                if (mix.length() > 0) mix.append(" + ");
                mix.append(String.format(Locale.ROOT, "%s %.2f", g.file, w[g.ordinal()]));
            }
            move = String.format(Locale.ROOT, "；自己：地面速度 %.2f m/s（平滑 %.2f），潜行权重 %.2f，%s%s", m.speed, m.smooth, m.crouch,
                    self.isPassenger() ? "坐着 " + (m.seat == null ? "（这个座位没有片段）" : m.seat.file) : "", self.isPassenger() ? "" : mix);
        }
        return say(c, String.format(Locale.ROOT, "%s；主通道 %s；阴影通道 %s（最近 2 秒，CPU 蒙皮 + 顶点提交）；动作计算 %s%s",
                shader, MAIN.text(), SHADOW.text(), POSE.text(), move));
    }

    private static int say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** The pose file, reread when Vextris reloads its meshes (F3+T). Null when missing or unreadable. */
    @Nullable
    private static Poses loaded() {
        if (posesGeneration != VextrisMeshes.generation()) {
            posesGeneration = VextrisMeshes.generation();
            poses = Poses.load();
            warned = false;
        }
        return poses;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void render(RenderPlayerEvent.Pre event) {
        if (FMLEnvironment.production || players.isEmpty()) return;
        Player player = event.getEntity();
        if (!players.contains(player.getGameProfile().getName())) return;
        Minecraft mc = Minecraft.getInstance();
        // the own first-person view keeps its vanilla body and hand pass
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        MeshModel model = VextrisMeshes.get(MESH);
        Poses p = loaded();
        if (model == null || !model.hasSkins() || p == null) {
            if (!warned) ApocalypseFirstLight.LOGGER.warn("Mesh body test: {} or {} not loaded, vanilla player render kept", MESH, POSES);
            warned = true;
            return;
        }
        boolean holding = player.getMainHandItem().is(AflItems.P9_01.get());
        Gait seat = p.hasClips() ? seat(player, p) : null;
        String name = pose;
        if (pose.equals(AUTO)) {
            if (!p.hasClips()) {
                name = holding ? HOLD : STAND;
            } else if (unsupported(player, seat)) {
                return;                                    // no clip for this state: the vanilla render
            } else if (holding) {
                if (seat != null || player.isCrouching()) return;
                name = HOLD;
            }
        }
        Gait fixed = gait(name);
        boolean clip = name.equals(AUTO) || fixed != null;
        float partial = event.getPartialTick();
        Frames frames = !clip ? p.frames.get(name) : !p.hasClips() ? null : clipFrames(p, player, name, fixed, seat, partial);
        if (frames == null) return;
        event.setCanceled(true);
        // seated: the clip's floor is the seat entity's (the chair's floor), the rider is drawn lower than that
        float lift = 0F;
        Entity vehicle = player.getVehicle();
        if (name.equals(AUTO) && seat != null && vehicle != null)
            lift = (float) (Mth.lerp(partial, vehicle.yo, vehicle.getY()) - Mth.lerp(partial, player.yo, player.getY()));

        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) - bodyYaw), -HEAD_YAW, HEAD_YAW);
        float headPitch = Mth.clamp(player.getViewXRot(partial), -HEAD_PITCH, HEAD_PITCH);
        boolean look = pose.equals(AUTO) || clip;
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight(), overlay = LivingEntityRenderer.getOverlayCoords(player, 0F);
        boolean gun = holding && name.equals(HOLD);

        PoseStack stack = event.getPoseStack();
        int copies = player == mc.player ? 1 + crowd : 1;
        for (int i = 0; i < copies; i++) {
            stack.pushPose();
            if (i > 0) {   // the crowd: a ring round the own body, each one skinned on its own
                double a = Math.PI * 2 * (i - 1) / crowd;
                stack.translate(Math.sin(a) * CROWD_RADIUS, 0, Math.cos(a) * CROWD_RADIUS);
            }
            if (lift != 0F) stack.translate(0, lift, 0);
            // the mesh faces +z: Ry(-yaw) turns it to the body's facing, (-sin, 0, cos) as the entity's look
            stack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
            draw(model, p, frames, stack.last(), look ? headYaw : 0F, look ? headPitch : 0F, buffers, light, overlay, gun);
            stack.popPose();
        }
    }

    /** One body at this frame: every bone's model-space deformation onto the entity pose, the look on the head. */
    private static void draw(MeshModel model, Poses p, Frames frames, PoseStack.Pose entity, float headYaw, float headPitch,
                             MultiBufferSource buffers, int light, int overlay, boolean gun) {
        // the look: about the head's posed pivot, in the body's model frame (yaw to the left is +, pitch down is +)
        frames.pose[p.head].transformPosition(p.headPivot, PIVOT);
        LOOK.translation(PIVOT).rotateY(-headYaw * Mth.DEG_TO_RAD).rotateX(headPitch * Mth.DEG_TO_RAD).translate(-PIVOT.x, -PIVOT.y, -PIVOT.z);
        LOOK.get3x3(LOOK_NORMAL);
        SKIN.clear();
        for (int b = 0; b < p.bones.size(); b++) {
            M.set(entity.pose());
            N.set(entity.normal());
            if (p.underHead[b]) {
                M.mul(LOOK);
                N.mul(LOOK_NORMAL);
            }
            M.mul(frames.pose[b]);
            N.mul(frames.normal[b]);
            SKIN.set(p.bones.get(b), M, N);
        }
        long start = System.nanoTime();
        MeshRendering.renderSkins(model, SKIN, TEXTURE, buffers, light, overlay, 1F, 1F, 1F, 1F);
        (AflShaderCompat.activeShadowPass() ? SHADOW : MAIN).add(System.nanoTime() - start);
        if (!gun) return;
        MeshModel p9 = VextrisMeshes.get(GUN_MESH);
        if (p9 == null || p.grip == null) return;
        PoseStack.Pose g = GUN_POSE.last();
        g.pose().set(SKIN.pose(p.gripBone)).mul(p.grip);
        g.normal().set(SKIN.normal(p.gripBone)).mul(p.gripNormal);
        MeshRendering.render(p9, GUN_TEXTURE, GUN_POSE, buffers, light, overlay, GUN_POSER);
    }

    /** The chair clip of the seat the player sits on (wooden chair, office chair), or null. */
    @Nullable
    private static Gait seat(Player player, Poses p) {
        Entity v = player.getVehicle();
        return ChairSit.sitting(player) ? p.resolve(Gait.SIT_CHAIR) : v instanceof OfficeChairEntity ? p.resolve(Gait.SIT_OFFICE) : null;
    }

    /** States without a clip: swimming, crawling (prone), gliding, sleeping, dying, riding anything but a chair. */
    private static boolean unsupported(Player player, @Nullable Gait seat) {
        return player.isSleeping() || player.isSwimming() || player.isVisuallyCrawling() || player.isFallFlying()
                || player.isAutoSpinAttack() || player.isDeadOrDying() || player.isPassenger() && seat == null;
    }

    /** The gait weights at a smoothed speed and crouch weight (summing to 1): the standing bands, then crouched. */
    private static void weights(float speed, float crouch, float[] out) {
        Arrays.fill(out, 0F);
        float share = 1F - crouch;
        for (int i = 0; i < STANDING.length - 1; i++) {
            float t = band(speed, BANDS, i);
            out[STANDING[i].ordinal()] = share * (1 - t);
            share *= t;
        }
        out[STANDING[STANDING.length - 1].ordinal()] = share;
        float t = band(speed, CROUCH_BAND, 0);
        out[Gait.CROUCH_IDLE.ordinal()] = crouch * (1 - t);
        out[Gait.CROUCH_WALK.ordinal()] = crouch * t;
    }

    private static float band(float speed, float[] bands, int i) {
        float t = Mth.clamp((speed - bands[2 * i]) / (bands[2 * i + 1] - bands[2 * i]), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    /** This frame's clip pose of the player: worked out once per frame and partial tick, then shared. */
    private static Frames clipFrames(Poses p, Player player, String name, @Nullable Gait fixed, @Nullable Gait seat, float partial) {
        Motion m = motions.computeIfAbsent(player.getId(), id -> new Motion(p.bones.size()));
        if (m.frame == renderFrame && m.partial == partial && m.name.equals(name) && m.poses == p && m.seat == seat) return m.pose;
        long start = System.nanoTime();
        double time = (player.tickCount + partial) / 20.0;
        int n = 0;
        if (fixed != null || seat != null) {   // one clip by time (a moving one in place, at its own cadence)
            Clip c = p.clip(fixed != null ? fixed : seat);
            if (c != null) n = mix(n, c, phase(time / c.duration), 1F);
        } else {
            weights(Mth.lerp(partial, m.smoothO, m.smooth), Mth.lerp(partial, m.crouchO, m.crouch), WEIGHT);
            double gait = Mth.lerp(partial, m.phaseO, m.phase);
            for (Gait g : GAITS) {
                float w = WEIGHT[g.ordinal()];
                Clip c = w < 1e-4F ? null : p.clip(g);
                if (c != null) n = mix(n, c, g.moving ? phase(gait) : phase(time / c.duration), w);
            }
        }
        if (n == 0) n = mix(n, p.clip(Gait.IDLE), phase(time / p.clip(Gait.IDLE).duration), 1F);
        p.compose(MIX, MIX_PHASE, MIX_WEIGHT, n, m.pose);
        m.frame = renderFrame;
        m.partial = partial;
        m.name = name;
        m.poses = p;
        m.seat = seat;
        POSE.add(System.nanoTime() - start);
        return m.pose;
    }

    private static int mix(int n, Clip clip, float phase, float weight) {
        MIX[n] = clip;
        MIX_PHASE[n] = phase;
        MIX_WEIGHT[n] = weight;
        return n + 1;
    }

    private static float phase(double cycles) {
        return (float) (cycles - Math.floor(cycles));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FMLEnvironment.production || motions.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || players.isEmpty()) {
            motions.clear();
            return;
        }
        if (mc.isPaused()) return;
        Poses p = loaded();
        for (Iterator<Map.Entry<Integer, Motion>> it = motions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Motion> e = it.next();
            if (mc.level.getEntity(e.getKey()) instanceof AbstractClientPlayer player && players.contains(player.getGameProfile().getName())) {
                e.getValue().update(player, p);
            } else {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        renderFrame++;
        long now = System.nanoTime();
        MAIN.frame(now);
        SHADOW.frame(now);
        POSE.frame(now);
    }

    /**
     * One player's motion per tick (this tick and the last, for the partial tick): the ground speed, smoothed; the crouch
     * weight; the gait phase (cycles), advanced by the travel over the blended stride.
     */
    private static final class Motion {
        double x, z, phase, phaseO;
        float speed, smooth, smoothO, crouch, crouchO;
        boolean seen;
        final Frames pose;
        long frame = -1;
        float partial;
        String name = "";
        @Nullable
        Poses poses;
        @Nullable
        Gait seat;

        Motion(int bones) {
            pose = new Frames(bones);
        }

        void update(Player player, @Nullable Poses p) {
            double dx = player.getX() - x, dz = player.getZ() - z;
            x = player.getX();
            z = player.getZ();
            phaseO = phase;
            smoothO = smooth;
            crouchO = crouch;
            if (!seen) {
                seen = true;
                return;
            }
            crouch += ((player.isCrouching() ? 1F : 0F) - crouch) * BLEND_RATE;
            if (player.isPassenger()) {           // seated: standing up starts from the idle
                speed = smooth = smoothO = 0F;
                return;
            }
            if (!player.onGround() && !player.isInWater()) return;   // in the air the phase and the speed hold
            double h = Math.sqrt(dx * dx + dz * dz);
            if (h > TELEPORT) h = 0;
            speed = (float) (h * 20);
            smooth += (speed - smooth) * BLEND_RATE;
            // along the body's facing (-sin, cos): mostly backwards plays the cycle in reverse
            float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
            double forward = -Mth.sin(yaw) * dx + Mth.cos(yaw) * dz;
            if (p != null && p.hasClips()) phase += (forward < -0.3 * h ? -h : h) / p.stride(smooth, crouch);
        }
    }

    /** Per bone (skeleton order): its deformation in each static pose, the head's subtree, the P9-01 grip. */
    private static final class Poses {
        final List<String> bones = new ArrayList<>();
        final Map<String, Frames> frames = new LinkedHashMap<>();
        boolean[] underHead;
        int head;
        final Vector3f headPivot = new Vector3f();
        @Nullable
        Matrix4f grip;
        final Matrix3f gripNormal = new Matrix3f();
        String gripBone = "hand_r";
        /** The third-person clips by Gait (Idle and Run, or none at all) and their bone tree, in this file's bone order. */
        final Clip[] clips = new Clip[GAITS.length];
        int[] parent;
        Vector3f[] pivot;
        int hips;
        private final float[] strideWeight = new float[GAITS.length];

        boolean hasClips() {
            return clips[Gait.IDLE.ordinal()] != null && clips[Gait.RUN.ordinal()] != null;
        }

        int loadedClips() {
            int n = 0;
            for (Clip c : clips) if (c != null) n++;
            return n;
        }

        /** The gait itself when its clip is there, else its nearest fallback that is (null: none). */
        @Nullable
        Gait resolve(Gait g) {
            while (g != null && clips[g.ordinal()] == null) g = g.fallback;
            return g;
        }

        @Nullable
        Clip clip(@Nullable Gait g) {
            Gait r = g == null ? null : resolve(g);
            return r == null ? null : clips[r.ordinal()];
        }

        /** The blended stride (blocks a gait cycle covers) of the moving gaits at this speed and crouch weight. */
        double stride(float speed, float crouch) {
            weights(speed, crouch, strideWeight);
            double sum = 0, length = 0;
            for (Gait g : GAITS) {
                float w = strideWeight[g.ordinal()];
                Clip c = g.moving && w > 0F ? clip(g) : null;
                if (c == null) continue;
                sum += w;
                length += w * c.refSpeed * c.duration;
            }
            if (sum > 1e-4) return length / sum;
            Clip c = clip(crouch > 0.5F ? Gait.CROUCH_WALK : Gait.WALK);
            return c.refSpeed * c.duration;
        }

        @Nullable
        static Poses load() {
            JsonObject json = read(POSES);
            if (json == null) return null;
            Poses p;
            try {
                p = new Poses(json);
            } catch (Exception e) {
                ApocalypseFirstLight.LOGGER.warn("Mesh body test: cannot read {}", POSES, e);
                return null;
            }
            JsonObject idle = read(Gait.IDLE.id()), run = read(Gait.RUN.id());
            if (idle == null || run == null) {
                ApocalypseFirstLight.LOGGER.warn("Mesh body test: {} or {} missing, static stand kept", Gait.IDLE.id(), Gait.RUN.id());
                return p;
            }
            try {
                p.tree(idle);
                for (Gait g : GAITS) {
                    JsonObject clipJson = g == Gait.IDLE ? idle : g == Gait.RUN ? run : read(g.id());
                    if (clipJson == null) {
                        ApocalypseFirstLight.LOGGER.warn("Mesh body test: {} missing, {} plays its fallback", g.id(), g.file);
                        continue;
                    }
                    p.clips[g.ordinal()] = p.clip(clipJson, g);
                }
            } catch (Exception e) {
                ApocalypseFirstLight.LOGGER.warn("Mesh body test: cannot use the third-person clips", e);
                Arrays.fill(p.clips, null);
            }
            return p;
        }

        @Nullable
        private static JsonObject read(ResourceLocation id) {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
            if (resource.isEmpty()) return null;
            try (Reader reader = resource.get().openAsReader()) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                ApocalypseFirstLight.LOGGER.warn("Mesh body test: cannot read {}", id, e);
                return null;
            }
        }

        /** The bone tree from a clip, checked against this file's bones. */
        private void tree(JsonObject json) {
            JsonArray names = json.getAsJsonArray("bones");
            if (names.size() != bones.size()) throw new IllegalArgumentException("clip bones differ from the pose file");
            JsonArray parents = json.getAsJsonArray("parents"), pivots = json.getAsJsonArray("pivots");
            parent = new int[bones.size()];
            pivot = new Vector3f[bones.size()];
            for (int b = 0; b < bones.size(); b++) {
                if (!names.get(b).getAsString().equals(bones.get(b))) throw new IllegalArgumentException("clip bone order differs at " + bones.get(b));
                parent[b] = parents.get(b).getAsInt();
                if (parent[b] >= b) throw new IllegalArgumentException("parent after child at " + bones.get(b));
                float[] v = floats(pivots.get(b).getAsJsonArray());
                pivot[b] = new Vector3f(v[0], v[1], v[2]);
            }
            hips = bones.indexOf("hips");
            for (int s = 0; s < 2; s++) {
                String side = s == 0 ? "_r" : "_l";
                int[] chain = {bones.indexOf("thigh" + side), bones.indexOf("shin" + side), bones.indexOf("foot" + side), bones.indexOf("toes" + side)};
                leg[s] = chain;
                for (int k = 0; k < 3; k++) {
                    under[s][k] = new boolean[bones.size()];
                    for (int b = 0; b < bones.size() && chain[k] >= 0; b++)
                        for (int a = b; a >= 0; a = parent[a]) if (a == chain[k]) under[s][k][b] = true;
                }
                for (int k = 0; k < 3; k++) sole[s][k].set(s == 0 ? SOLE[k][0] : -SOLE[k][0], SOLE[k][1], SOLE[k][2]);
            }
        }

        /** Per side (right, left): thigh, shin, foot, toes; the bones under (and with) the thigh, shin and foot; the soles. */
        private final int[][] leg = new int[2][];
        private final boolean[][][] under = new boolean[2][3][];
        private final Vector3f[][] sole = {{new Vector3f(), new Vector3f(), new Vector3f()}, {new Vector3f(), new Vector3f(), new Vector3f()}};
        private final Vector3f hip = new Vector3f(), knee = new Vector3f(), ankle = new Vector3f(), goal = new Vector3f(),
                kneeGoal = new Vector3f(), u = new Vector3f(), w = new Vector3f(), tmp = new Vector3f();
        private final Quaternionf turnHip = new Quaternionf(), turnKnee = new Quaternionf(), back = new Quaternionf();
        private final Matrix4f about = new Matrix4f();

        /**
         * A leg whose blend puts a sole under the ground (a stance leg blended with a swing leg reaches longer or shorter
         * than either): the ankle raised until the lowest sole point is on it, by turning the thigh and the shin in their
         * plane (two-bone IK), the foot kept as it was. A held clip's feet are never under the ground, so this only acts
         * between gaits.
         */
        private void ground(Frames out) {
            for (int s = 0; s < 2; s++) {
                int[] c = leg[s];
                if (c == null || c[0] < 0 || c[1] < 0 || c[2] < 0 || c[3] < 0) continue;
                float low = Float.MAX_VALUE;
                for (int k = 0; k < 3; k++) low = Math.min(low, out.pose[k == 2 ? c[3] : c[2]].transformPosition(sole[s][k], tmp).y);
                if (low >= -1e-4F) continue;
                out.pose[c[0]].transformPosition(pivot[c[0]], hip);
                out.pose[c[1]].transformPosition(pivot[c[1]], knee);
                out.pose[c[2]].transformPosition(pivot[c[2]], ankle);
                float l1 = knee.distance(hip), l2 = ankle.distance(knee);
                goal.set(ankle).add(0, -low, 0);
                float d = goal.distance(hip);
                if (d < Math.abs(l1 - l2) + 1e-4F || d < 1e-4F) continue;
                d = Math.min(d, l1 + l2 - 1e-4F);
                u.set(goal).sub(hip).normalize();
                w.set(knee).sub(hip);
                w.fma(-w.dot(u), u);
                if (w.lengthSquared() < 1e-10F) continue;
                w.normalize();
                float cos = Mth.clamp((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d), -1F, 1F), sin = (float) Math.sqrt(1 - cos * cos);
                kneeGoal.set(hip).fma(l1 * cos, u).fma(l1 * sin, w);
                goal.set(hip).fma(d, u);
                // the thigh onto the new knee, about the hip
                turnHip.rotationTo(tmp.set(knee).sub(hip), w.set(kneeGoal).sub(hip));
                turn(out, under[s][0], hip, turnHip);
                // the shin onto the goal, about the new knee
                out.pose[c[2]].transformPosition(pivot[c[2]], ankle);
                turnKnee.rotationTo(tmp.set(ankle).sub(kneeGoal), w.set(goal).sub(kneeGoal));
                turn(out, under[s][1], kneeGoal, turnKnee);
                // the foot back to its own turn, about the ankle
                turn(out, under[s][2], goal, back.set(turnKnee).mul(turnHip).conjugate());
                for (int b = 0; b < bones.size(); b++) if (under[s][0][b]) out.pose[b].normal(out.normal[b]);
            }
        }

        /** The bones marked turned by q about the point p (model blocks). */
        private void turn(Frames out, boolean[] marked, Vector3f p, Quaternionf q) {
            about.translation(p).rotate(q).translate(-p.x, -p.y, -p.z);
            for (int b = 0; b < bones.size(); b++) if (marked[b]) out.pose[b].mulLocal(about);
        }

        private Clip clip(JsonObject json, Gait g) {
            JsonArray names = json.getAsJsonArray("bones");
            for (int b = 0; b < bones.size(); b++)
                if (b >= names.size() || !names.get(b).getAsString().equals(bones.get(b)))
                    throw new IllegalArgumentException(g.file + ": clip bone order differs at " + bones.get(b));
            Clip c = new Clip(json, bones);
            if (g.moving && c.refSpeed <= 0F) throw new IllegalArgumentException(g.file + ": a moving clip without a reference speed");
            return c;
        }

        /** The clips at their phases, weighted (summing to 1), down the bone tree into out. */
        void compose(Clip[] mix, float[] phases, float[] weights, int count, Frames out) {
            for (int b = 0; b < bones.size(); b++) {
                mix[0].rotation(b, phases[0], QUAT);
                if (count > 1) {   // the weighted sum on one hemisphere, normalised
                    float x = QUAT.x * weights[0], y = QUAT.y * weights[0], z = QUAT.z * weights[0], w = QUAT.w * weights[0];
                    for (int i = 1; i < count; i++) {
                        mix[i].rotation(b, phases[i], QUAT_SAMPLE);
                        float k = QUAT_SAMPLE.dot(QUAT) < 0F ? -weights[i] : weights[i];
                        x += QUAT_SAMPLE.x * k;
                        y += QUAT_SAMPLE.y * k;
                        z += QUAT_SAMPLE.z * k;
                        w += QUAT_SAMPLE.w * k;
                    }
                    QUAT.set(x, y, z, w).normalize();
                }
                Matrix4f d = out.pose[b];
                if (parent[b] < 0) d.identity();
                else d.set(out.pose[parent[b]]);
                if (b == hips) {
                    float x = 0, y = 0, z = 0;
                    for (int i = 0; i < count; i++) {
                        mix[i].hips(phases[i], OFFSET);
                        x += OFFSET.x * weights[i];
                        y += OFFSET.y * weights[i];
                        z += OFFSET.z * weights[i];
                    }
                    d.translate(x, y, z);
                }
                Vector3f v = pivot[b];
                d.translate(v.x, v.y, v.z).rotate(QUAT).translate(-v.x, -v.y, -v.z);
                d.normal(out.normal[b]);
            }
            if (count > 1) ground(out);
        }

        private Poses(JsonObject json) {
            json.getAsJsonArray("bones").forEach(b -> bones.add(b.getAsString()));
            head = bones.indexOf("head");
            if (head < 0) throw new IllegalArgumentException("no head bone");
            JsonArray hp = json.getAsJsonArray("head_pivot");
            headPivot.set(hp.get(0).getAsFloat(), hp.get(1).getAsFloat(), hp.get(2).getAsFloat());
            // the head and everything under it turn with the look (the mesh's bone tree)
            MeshModel model = VextrisMeshes.get(MESH);
            underHead = new boolean[bones.size()];
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < bones.size(); i++) index.put(bones.get(i), i);
            underHead[head] = true;
            if (model != null) mark(model, "head", index);
            json.getAsJsonObject("poses").entrySet().forEach(e -> frames.put(e.getKey(), new Frames(e.getValue().getAsJsonObject(), bones)));
            JsonObject p9 = json.getAsJsonObject("props").getAsJsonObject("p9_01");
            if (p9 != null) {
                gripBone = p9.get("bone").getAsString();
                float[] k = floats(p9.getAsJsonArray("matrix"));
                grip = new Matrix4f().set(k);   // column-major: P9 mesh blocks to body model blocks at rest
                grip.get3x3(gripNormal);
                gripNormal.scale(1F / (float) Math.sqrt(k[0] * k[0] + k[1] * k[1] + k[2] * k[2]));   // a similarity
            }
        }

        private void mark(MeshModel model, String bone, Map<String, Integer> index) {
            for (var child : model.children(bone)) {
                Integer i = index.get(child.name());
                if (i != null) underHead[i] = true;
                mark(model, child.name(), index);
            }
        }
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    /**
     * One locomotion clip (afl_skin_clip v1 from anim_tp_v1.py): evenly spaced looping samples; per bone its rotation
     * about its rest pivot in the body's model frame (no track = rest, one sample = constant); the hips' offset (blocks).
     */
    private static final class Clip {
        final float duration, refSpeed;
        final int samples;
        final float[][] rotation;
        final float[] hipsOffset;

        Clip(JsonObject json, List<String> bones) {
            duration = json.get("duration").getAsFloat();
            refSpeed = json.get("ref_speed").getAsFloat();
            samples = json.get("samples").getAsInt();
            if (samples < 2 || duration <= 0F) throw new IllegalArgumentException("bad clip length");
            JsonObject tracks = json.getAsJsonObject("tracks");
            rotation = new float[bones.size()][];
            for (int b = 0; b < bones.size(); b++) {
                JsonArray t = tracks.getAsJsonArray(bones.get(b));
                if (t == null) continue;
                rotation[b] = floats(t);
                if (rotation[b].length != 4 && rotation[b].length != 4 * samples) throw new IllegalArgumentException("bad track " + bones.get(b));
            }
            hipsOffset = floats(json.getAsJsonArray("hips_offset"));
            if (hipsOffset.length != 3 * samples) throw new IllegalArgumentException("bad hips offset");
        }

        void rotation(int bone, float phase, Quaternionf out) {
            float[] r = rotation[bone];
            if (r == null) {
                out.identity();
            } else if (r.length == 4) {
                out.set(r[0], r[1], r[2], r[3]);
            } else {
                float f = phase * samples;
                int i = Math.min((int) f, samples - 1), j = (i + 1) % samples;
                out.set(r[4 * i], r[4 * i + 1], r[4 * i + 2], r[4 * i + 3])
                        .nlerp(QUAT_TMP.set(r[4 * j], r[4 * j + 1], r[4 * j + 2], r[4 * j + 3]), f - i);
            }
        }

        void hips(float phase, Vector3f out) {
            float f = phase * samples;
            int i = Math.min((int) f, samples - 1), j = (i + 1) % samples;
            float t = f - i;
            float[] o = hipsOffset;
            out.set(Mth.lerp(t, o[3 * i], o[3 * j]), Mth.lerp(t, o[3 * i + 1], o[3 * j + 1]), Mth.lerp(t, o[3 * i + 2], o[3 * j + 2]));
        }
    }

    /** One pose: per bone its model-space deformation (blocks) and the normal matrix of it. */
    private static final class Frames {
        final Matrix4f[] pose;
        final Matrix3f[] normal;

        Frames(int bones) {
            pose = new Matrix4f[bones];
            normal = new Matrix3f[bones];
            for (int b = 0; b < bones; b++) {
                pose[b] = new Matrix4f();
                normal[b] = new Matrix3f();
            }
        }

        Frames(JsonObject json, List<String> bones) {
            pose = new Matrix4f[bones.size()];
            normal = new Matrix3f[bones.size()];
            for (int b = 0; b < bones.size(); b++) {
                JsonArray m = json.getAsJsonArray(bones.get(b));
                if (m == null) throw new IllegalArgumentException("pose has no bone " + bones.get(b));
                pose[b] = new Matrix4f().set(floats(m));
                normal[b] = pose[b].normal(new Matrix3f());
            }
        }
    }

    /** Draw times over the last two seconds: per body, and per frame summed over the bodies of that frame. */
    private static final class Stats {
        private long windowStart, nanos, draws, frames, frameNanos, frameDraws, worstFrame;
        private String last = "没有数据";

        void add(long n) {
            nanos += n;
            draws++;
            frameNanos += n;
            frameDraws++;
        }

        void frame(long now) {
            if (frameDraws > 0) {
                frames++;
                worstFrame = Math.max(worstFrame, frameNanos);
            }
            frameNanos = frameDraws = 0;
            if (windowStart == 0) windowStart = now;
            if (now - windowStart < WINDOW_NANOS) return;
            last = draws == 0 ? "没有画" : String.format(Locale.ROOT, "每个身体 %.3f ms，每帧 %.1f 个、合计 %.3f ms（最差 %.3f ms），%d 帧",
                    nanos / 1e6 / draws, frames == 0 ? 0.0 : (double) draws / frames, frames == 0 ? 0.0 : nanos / 1e6 / frames,
                    worstFrame / 1e6, frames);
            windowStart = now;
            nanos = draws = frames = worstFrame = 0;
        }

        String text() {
            return last;
        }
    }
}
