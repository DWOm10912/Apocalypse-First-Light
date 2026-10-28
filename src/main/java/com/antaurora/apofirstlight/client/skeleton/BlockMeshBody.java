package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.ChairSit;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.entity.OfficeChairEntity;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import com.antaurora.apofirstlight.skeleton.seat.ChairLegs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * AFL Block-Mesh V2, prototype B in game (user 2026-10-25 "批准使用开发测试开关", docs/dev/block_mesh_v2_b_ingame.md;
 * since 2026-10-25 "直接把我们的B3模型换到游戏里面" the default body, in dev runs and built jars alike, docs 13): every
 * player is solved on the B rig ({@code /afl dev blockmesh off|on}, dev runs only, goes back to the old body and returns)
 * (rigs/afl_block_mesh_v2.json, SkeletonRig#blockMeshV2) and draws the body's skinned mesh the rig names (since 2026-10-25
 * B3: skins/block_mesh_v2/b3_body.json, continuous box arms under their own sleeves, a straight jacket over the waist, and its
 * 256 x 256 sample skin with two mip levels, BlockMeshSkin; design/characters/block_mesh_v2/source/bm2_b3.py --game; B2's
 * b_body.json from bm2_game.py is kept; since 2026-10-26 "用方案A" B5: skins/block_mesh_v2/b5/body.json, B3's look rebuilt
 * in Blender, drawn in layers, BlockMeshWardrobe: the body in the survivor's skin tone, briefs, hair and the clothes worn,
 * one set of bone matrices for all; B3's files are kept) in
 * place of the C-min skin mesh and
 * vanilla's head. Off: the vanilla rig and the player's own skin, as before. Everything else (clips, actions, the gun
 * stances, seats, the camera, the hitbox) is the same code on the other rig.
 * <ul>
 * <li>The mesh's 22 bones from the poser's 16: a bone of the same name takes its skinning matrix; the spine half the chest's
 * turn at the waist (SkeletonPose#half, as the C-min mesh's waist ring), the neck the chest's, the clavicles their upper
 * arm's less its first 8 deg off the chest ({@link #CLAVICLE_DEAD_DEG}: B3's level shoulder tops), the toes the
 * foot's, bent up at the ball while the toe box would go into the ground;</li>
 * <li>a seated rider's compressed torso (SkeletonPose#squash) pulls the torso above the waist toward it, as the C-min
 * mesh's top ring;</li>
 * <li>the parts follow vanilla's visibility (the own first person hides head and arms).</li>
 * </ul>
 * Chairs (the wooden one: cabin/ChairSit, AFL's sitting; OfficeChairEntity: vanilla's riding): the hips go on the seat where the chair's size puts
 * them and the legs reach its floor by IK (skeleton/seat/ChairLegs); the hips keep the seat's way, no turn steps
 * (PlayerSkeleton.State); the own first-person camera goes to the posed eye ({@code chaircam}, on by default; off leaves
 * vanilla's camera where it is).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlockMeshBody {
    private static final String CAMERA = "afl_block_mesh_chair";
    private static final double CAMERA_OUT = 0.25;
    private static final int STRIDE = 13;

    private static boolean on = true, chairCamera = true, failed;
    /** The rig's mesh (its bones drive every layer) and each layer's, by path. */
    private static Mesh mesh;
    private static final java.util.Map<String, Mesh> LAYERS = new java.util.HashMap<>();
    private static final java.util.Set<String> MISSING = new java.util.HashSet<>();
    private static long frame, eyeFrame;
    @Nullable
    private static Vec3 eye;

    private BlockMeshBody() {
    }

    /** B is the body (the default; off only by the dev command, or when its rig or mesh failed): PlayerSkeleton solves and draws on the B rig. */
    public static boolean active() {
        return on && !failed;
    }

    /** The B rig or mesh failed to load: back to vanilla's, logged once. */
    static void fail(RuntimeException e) {
        failed = true;
        ApocalypseFirstLight.LOGGER.error("[AFL Block-Mesh V2] rig or mesh failed to load, the dev test stays off", e);
    }

    // ---------------------------------------------------------------- the mesh

    /**
     * Per mesh bone where its matrix comes from: the poser bone ({@code source}) and how ({@code kind}). Vertices
     * {@link #STRIDE} floats: x y z u v j0..j3 w0..w3 (Java model px).
     */
    private record Mesh(int[] source, int[] kind, Vector3f[] pivot, int[] quadPart, String[] parts, float[] v,
                        int toesR, int toesL, int footR, int footL, int torso) {}

    private static final int SAME = 0, SPINE = 1, TOES = 2, CLAVICLE = 3;
    /**
     * B3's sleeve tops ride the clavicles (bm2_b3.py clavicle_hold): a clavicle turns with its upper arm less this much of
     * the arm's turn off the chest, so the idle clip's 4-5 deg arm swing leaves the shoulder top level and square.
     */
    private static final float CLAVICLE_DEAD_DEG = 8F;
    private static final Quaternionf CHEST_Q = new Quaternionf(), ARM_Q = new Quaternionf(), KEEP_Q = new Quaternionf();

    @Nullable
    private static Mesh mesh(SkeletonRig rig) {
        if (mesh == null && !failed && rig.profile != null) {
            try {
                mesh = read(rig.profile.mesh(), rig);
            } catch (IOException | RuntimeException e) {
                fail(e instanceof RuntimeException r ? r : new IllegalStateException(e));
                PlayerSkeleton.reset();
            }
        }
        return mesh;
    }

    /** A layer's mesh (BlockMeshWardrobe), loaded once; one that fails is logged once and left out. */
    @Nullable
    private static Mesh layer(SkeletonRig rig, String path) {
        Mesh m = LAYERS.get(path);
        if (m != null || MISSING.contains(path)) return m;
        try {
            m = read(path, rig);
            if (m.source.length != mesh.source.length) throw new IllegalArgumentException("block mesh: " + path + " has other bones than the rig's mesh");
            LAYERS.put(path, m);
        } catch (IOException | RuntimeException e) {
            MISSING.add(path);
            ApocalypseFirstLight.LOGGER.error("[AFL Block-Mesh V2] layer {} failed to load, drawn without it", path, e);
        }
        return m;
    }

    private static Mesh read(String path, SkeletonRig rig) throws IOException {
        try (InputStream in = BlockMeshBody.class.getClassLoader().getResourceAsStream("assets/" + ApocalypseFirstLight.MOD_ID + "/" + path)) {
            if (in == null) throw new IOException("missing " + path);
            return load(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject(), rig);
        }
    }

    private static Mesh load(JsonObject json, SkeletonRig rig) {
        JsonArray bones = json.getAsJsonArray("bones");
        int n = bones.size();
        int[] source = new int[n], kind = new int[n];
        Vector3f[] pivot = new Vector3f[n];
        int toesR = -1, toesL = -1;
        for (int i = 0; i < n; i++) {
            JsonObject b = bones.get(i).getAsJsonObject();
            String name = b.get("name").getAsString();
            JsonArray p = b.getAsJsonArray("pivot");
            pivot[i] = new Vector3f(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat());
            int same = rig.find(name);
            if (same >= 0) source[i] = same;
            else switch (name) {
                case "spine" -> { source[i] = rig.chest; kind[i] = SPINE; }
                case "neck" -> source[i] = rig.chest;
                case "clavicle_r" -> { source[i] = rig.upperArmR; kind[i] = CLAVICLE; }
                case "clavicle_l" -> { source[i] = rig.upperArmL; kind[i] = CLAVICLE; }
                case "toes_r" -> { source[i] = rig.footR; kind[i] = TOES; toesR = i; }
                case "toes_l" -> { source[i] = rig.footL; kind[i] = TOES; toesL = i; }
                default -> throw new IllegalArgumentException("block mesh: no source for bone " + name);
            }
        }
        JsonArray partList = json.getAsJsonArray("parts"), quads = json.getAsJsonArray("quad_parts"), verts = json.getAsJsonArray("vertices");
        String[] parts = new String[partList.size()];
        for (int i = 0; i < parts.length; i++) parts[i] = partList.get(i).getAsString();
        int[] quadPart = new int[quads.size()];
        for (int i = 0; i < quadPart.length; i++) quadPart[i] = quads.get(i).getAsInt();
        float[] v = new float[verts.size()];
        int i = 0;
        for (JsonElement e : verts) v[i++] = e.getAsFloat();
        if (v.length != quadPart.length * 4 * STRIDE) throw new IllegalArgumentException("block mesh: vertices vs quads");
        int torso = java.util.Arrays.asList(parts).indexOf("torso");
        return new Mesh(source, kind, pivot, quadPart, parts, v, toesR, toesL, findBone(bones, "foot_r"), findBone(bones, "foot_l"), torso);
    }

    private static int findBone(JsonArray bones, String name) {
        for (int i = 0; i < bones.size(); i++) if (bones.get(i).getAsJsonObject().get("name").getAsString().equals(name)) return i;
        return -1;
    }

    // ---------------------------------------------------------------- drawing

    private static Matrix4f[] matrices = new Matrix4f[0];
    private static final Vector3f[] CORNER = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

    /**
     * The B body in the solved pose, from the model-origin pose stack (model px / 16): the player's layers
     * (BlockMeshWardrobe: skin tone, hair, clothes worn; a null player: the defaults). {@code ground}: the ground's model y
     * (px) for the toes, NaN off it (riding). False when the mesh is missing (the caller draws vanilla's).
     */
    static boolean draw(SkeletonPose pose, PlayerModel<?> model, PoseStack stack, MultiBufferSource buffers, int light, int overlay,
                        float r, float g, float b, float a, float ground, @Nullable Player player) {
        return draw(pose, model, stack, buffers, light, overlay, r, g, b, a, ground, BlockMeshWardrobe.layers(player));
    }

    /**
     * The B body in the given layers (the appearance page's preview: {@code model} null, every part shown). False when
     * the rig's mesh is missing.
     */
    public static boolean draw(SkeletonPose pose, @Nullable PlayerModel<?> model, PoseStack stack, MultiBufferSource buffers, int light,
                               int overlay, float r, float g, float b, float a, float ground, java.util.List<BlockMeshWardrobe.Layer> layers) {
        Mesh rigMesh = mesh(pose.rig);
        if (rigMesh == null) return false;
        Matrix4f[] mats = matrices(rigMesh, pose, ground);
        boolean cull = stack.last().pose().determinant() > 0F;   // back faces off but under a mirror (BlockMeshSkin#body)
        for (BlockMeshWardrobe.Layer layer : layers) {
            Mesh m = layer.mesh().equals(pose.rig.profile.mesh()) ? rigMesh : layer(pose.rig, layer.mesh());
            if (m != null) emit(m, mats, pose, model, stack, buffers.getBuffer(BlockMeshSkin.body(layer.texture(), cull)), light, overlay, r, g, b, a);
        }
        return true;
    }

    private static void emit(Mesh m, Matrix4f[] mats, SkeletonPose pose, @Nullable PlayerModel<?> model, PoseStack stack,
                             VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        PoseStack.Pose last = stack.last();
        Matrix4f mp = last.pose();
        Matrix3f mn = last.normal();
        Vector3f p = new Vector3f(), n = new Vector3f();
        SkinMesh.Sink sink = (x, y, z, u, v, nx, ny, nz) -> {
            mp.transformPosition(x / 16F, y / 16F, z / 16F, p);
            mn.transform(nx, ny, nz, n);
            consumer.vertex(p.x, p.y, p.z, r, g, b, a, u, v, overlay, light, n.x, n.y, n.z);
        };
        boolean[] shown = new boolean[m.parts.length];
        for (int i = 0; i < shown.length; i++) shown[i] = model == null || visible(model, m.parts[i]);
        float squash = pose.squash[pose.rig.chest], waist = pose.rig.pivot[pose.rig.chest].y;
        float[] uv = new float[8];
        Vector3f rest = new Vector3f(), part = new Vector3f();
        for (int q = 0; q < m.quadPart.length; q++) {
            if (!shown[m.quadPart[q]]) continue;
            boolean torso = m.quadPart[q] == m.torso;
            for (int k = 0; k < 4; k++) {
                int o = (q * 4 + k) * STRIDE;
                float y = m.v[o + 1];
                if (torso && squash != 1F && y < waist) y = waist + (y - waist) * squash;
                rest.set(m.v[o], y, m.v[o + 2]);
                Vector3f c = CORNER[k].zero();
                for (int j = 0; j < 4; j++) {
                    float w = m.v[o + 9 + j];
                    if (w <= 0F) continue;
                    mats[(int) m.v[o + 5 + j]].transformPosition(rest, part);
                    c.fma(w, part);
                }
                // SkinMesh#quad takes 64 x 64 skin texels (it divides by 64); the mesh's uv is 0..1
                uv[k * 2] = m.v[o + 3] * 64F;
                uv[k * 2 + 1] = m.v[o + 4] * 64F;
            }
            SkinMesh.quad(sink, CORNER[0], uv[0], uv[1], CORNER[1], uv[2], uv[3], CORNER[2], uv[4], uv[5], CORNER[3], uv[6], uv[7]);
        }
    }

    private static boolean visible(PlayerModel<?> m, String part) {
        return switch (part) {
            case "head", "neck" -> m.head.visible;
            case "torso", "waist" -> m.body.visible;
            case "arm_r", "sleeve_r", "hand_r" -> m.rightArm.visible;
            case "arm_l", "sleeve_l", "hand_l" -> m.leftArm.visible;
            case "leg_r", "foot_r" -> m.rightLeg.visible;
            case "leg_l", "foot_l" -> m.leftLeg.visible;
            default -> true;
        };
    }

    private static Matrix4f[] matrices(Mesh m, SkeletonPose pose, float ground) {
        if (matrices.length != m.source.length) {
            matrices = new Matrix4f[m.source.length];
            for (int i = 0; i < matrices.length; i++) matrices[i] = new Matrix4f();
        }
        for (int i = 0; i < m.source.length; i++) {
            if (m.kind[i] == SPINE) pose.half(m.source[i], matrices[i]);
            else if (m.kind[i] == CLAVICLE) clavicle(pose, m.source[i], matrices[i]);
            else matrices[i].set(pose.world[m.source[i]]);
        }
        if (!Float.isNaN(ground)) {
            toes(m, matrices, m.toesR, m.footR, ground);
            toes(m, matrices, m.toesL, m.footL, ground);
        }
        return matrices;
    }

    /**
     * A clavicle: its upper arm's matrix turned back about the shoulder pivot by up to {@link #CLAVICLE_DEAD_DEG} of the
     * arm's turn off the chest (within it, the chest's turn about the arm's own pivot, so a seat's squashed shoulder still
     * carries it). Mesh bones without a sleeve weight on it (B2) draw the same as before.
     */
    private static void clavicle(SkeletonPose pose, int upperArm, Matrix4f out) {
        Matrix4f arm = pose.world[upperArm];
        pose.world[pose.rig.chest].getUnnormalizedRotation(CHEST_Q).normalize();   // getNormalizedRotation assumes an unscaled matrix
        arm.getUnnormalizedRotation(ARM_Q).normalize();
        Quaternionf rel = CHEST_Q.conjugate().mul(ARM_Q);   // the arm's turn in the chest's frame
        if (rel.w < 0) rel.set(-rel.x, -rel.y, -rel.z, -rel.w);
        float angle = 2F * (float) Math.acos(Math.min(1F, rel.w)), dead = (float) Math.toRadians(CLAVICLE_DEAD_DEG);
        KEEP_Q.identity();
        if (angle > dead) KEEP_Q.slerp(rel, (angle - dead) / angle);
        Quaternionf delta = rel.conjugate().mul(KEEP_Q);    // from the arm's turn back to the kept part
        Vector3f p = pose.rig.pivot[upperArm];
        out.set(arm).translate(p).rotate(delta).translate(-p.x, -p.y, -p.z);
    }

    /** The toe box out of the ground: the toes bend up (or down) at the ball, at most 60 deg (bm2_eval.py toe_clamp). */
    private static void toes(Mesh m, Matrix4f[] mats, int toes, int foot, float ground) {
        if (toes < 0 || foot < 0) return;
        Vector3f ball = m.pivot[toes];
        Matrix4f base = new Matrix4f(mats[toes]);
        if (lowest(base, ball.x) <= ground + 1e-4F) return;
        Vector3f pivot = base.transformPosition(new Vector3f(ball)), axis = mats[foot].transformDirection(new Vector3f(1, 0, 0)).normalize();
        Matrix4f turned = new Matrix4f();
        for (int deg = 2; deg <= 60; deg += 2) {
            for (int sign : new int[]{1, -1}) {
                turned.translation(pivot).rotate((float) Math.toRadians(sign * deg), axis).translate(-pivot.x, -pivot.y, -pivot.z).mul(base);
                if (lowest(turned, ball.x) <= ground + 1e-4F) {
                    mats[toes].set(turned);
                    return;
                }
            }
        }
        mats[toes].set(turned);
    }

    /** The toe box's lowest corner (the largest Java y), the front of the foot (bm2_rig FOOT). */
    private static float lowest(Matrix4f toes, float x) {
        float low = -Float.MAX_VALUE;
        Vector3f q = new Vector3f();
        for (float dx : new float[]{-2F, 2F})
            for (float y : new float[]{24F, 23F}) low = Math.max(low, toes.transformPosition(q.set(x + dx, y, -3.5F)).y);
        return low;
    }

    // ---------------------------------------------------------------- the rig's vanilla part frames

    /** The vanilla arm part's pivot on this rig: the hand's end where vanilla's arm frame puts it (ItemInHandLayer). */
    static Vector3f armPivot(SkeletonRig rig, boolean right) {
        SkeletonRig.Profile b = rig.profile;
        Vector3f s = rig.pivot[right ? rig.upperArmR : rig.upperArmL];
        float sx = right ? 1F : -1F;
        // vanilla: the hand end at arm frame (-1, 10) right / (1, 10) left; this rig's at the shoulder + (+-handCapX, handCapY)
        return new Vector3f(s.x + sx * (b.handCapX() + 1F), s.y + b.handCapY() - 10F, 0);
    }

    /** The vanilla leg part's pivot on this rig (worn leg gear, the holster). */
    static Vector3f legPivot(SkeletonRig rig, boolean right) {
        Vector3f l = rig.profile.legPivot();
        return new Vector3f(right ? -Math.abs(l.x) : Math.abs(l.x), l.y, 0);
    }

    // ---------------------------------------------------------------- chairs

    /** On a chair: a wooden one (cabin/ChairSit, AFL's sitting) or the office chair (vanilla's riding). */
    static boolean chair(Player p) {
        return ChairSit.sitting(p) || p.getVehicle() instanceof OfficeChairEntity;
    }

    /** The chair this player sits on, or null. */
    @Nullable
    static ChairLegs.Chair chairOf(Player p) {
        return ChairSit.sitting(p) ? ChairLegs.WOOD : p.getVehicle() instanceof OfficeChairEntity ? ChairLegs.OFFICE : null;
    }

    /** The hips onto the chair and the legs to its floor (ChairLegs: by the chair's size, docs 11). */
    static ChairLegs.Result chairLegs(HumanoidPoser.Input in, SkeletonRig rig, Player p, Vec3 origin, Matrix4f toModel, float partial) {
        ChairSit.Sit sit = ChairSit.get(p);
        Vec3 at = sit != null ? sit.point() : p.getVehicle().getPosition(partial);
        float floor = toModel.transformPosition(new Vector3f((float) (at.x - origin.x), (float) (at.y - origin.y), (float) (at.z - origin.z))).mul(16F).y;
        float pxPerMetre = toModel.transformDirection(new Vector3f(0, 1, 0)).length() * 16F;
        return ChairLegs.legs(in, rig, chairOf(p), floor, pxPerMetre);
    }

    /** After the own player's level solve on a chair: the camera on the posed eye. */
    static void chairSolved(Player p, SkeletonPose pose, Vec3 origin, Matrix4f toModel) {
        Minecraft mc = Minecraft.getInstance();
        if (p != mc.player || !chairCamera) return;
        Vector3f e = new Matrix4f(toModel).invert().transformPosition(ActionPose.cameraEye(pose).div(16F));
        eye = origin.add(e.x, e.y, e.z);
        eyeFrame = frame;
        if (mc.options.getCameraType().isFirstPerson() && !CameraDirector.holding(CAMERA))
            CameraDirector.open(new CameraDirector.Request(CAMERA, CameraDirector.PRIORITY_BODY_EYE, BlockMeshBody::shot,
                    CameraDirector.Clock.GAME, 0, CAMERA_OUT));
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        frame++;
        Minecraft mc = Minecraft.getInstance();
        boolean sitting = active() && chairCamera && mc.player != null && chair(mc.player) && frame - eyeFrame <= 2;
        if (!sitting && CameraDirector.holding(CAMERA)) CameraDirector.close(CAMERA);
    }

    @Nullable
    private static CameraPose shot(Camera camera, CameraPose current, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || eye == null || frame - eyeFrame > 2) return null;
        return new CameraPose(eye, current.yaw(), current.pitch());
    }

    // ---------------------------------------------------------------- the dev command

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(Commands.literal("blockmesh")
                .executes(BlockMeshBody::status)
                .then(Commands.literal("on").executes(c -> set(c, true)))
                .then(Commands.literal("off").executes(c -> set(c, false)))
                .then(Commands.literal("chaircam").then(Commands.literal("on").executes(c -> chairCam(c, true)))
                        .then(Commands.literal("off").executes(c -> chairCam(c, false))))
                .then(Commands.literal("status").executes(BlockMeshBody::status)))));
    }

    private static int set(CommandContext<CommandSourceStack> c, boolean value) {
        if (value && failed) return say(c, "Block-Mesh V2 骨架或网格加载失败，看日志 [AFL Block-Mesh V2]");
        on = value;
        PlayerSkeleton.reset();
        return say(c, value ? "Block-Mesh V2（B5）已打开：所有玩家用 B 的骨架和身体，肤色、发型和衣服按幸存者"
                : "Block-Mesh V2 已关闭，所有玩家恢复原来的骨架和皮肤（只到这次退出游戏，下次启动默认又是 B5）");
    }

    private static int chairCam(CommandContext<CommandSourceStack> c, boolean value) {
        chairCamera = value;
        if (!value) CameraDirector.close(CAMERA);
        return say(c, value ? "坐椅子时第一人称相机跟随 B 的眼睛（比原版低 3 px）" : "坐椅子时第一人称相机保持原版位置");
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        return say(c, (failed ? "Block-Mesh V2 加载失败" : active() ? "Block-Mesh V2（原型 B）开着" : "Block-Mesh V2 关着")
                + "，坐椅子相机" + (chairCamera ? "跟随 B 的眼睛" : "保持原版"));
    }

    private static int say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSystemMessage(Component.literal(text));
        return 1;
    }
}
