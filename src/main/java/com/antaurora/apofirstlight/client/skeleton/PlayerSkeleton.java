package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBody;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.antaurora.apofirstlight.client.survivor.SurvivorIdentityClient;
import com.antaurora.apofirstlight.skeleton.GunStance;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonClips;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerProps;
import com.antaurora.apofirstlight.cabin.ChairSit;
import com.antaurora.apofirstlight.skeleton.seat.ChairLegs;
import com.antaurora.apofirstlight.skeleton.seat.ChairPose;
import com.antaurora.apofirstlight.skeleton.seat.RiderSeat;
import com.antaurora.apofirstlight.skeleton.seat.SeatPose;
import com.antaurora.apofirstlight.weapon.client.AflShaderCompat;
import com.antaurora.apofirstlight.weapon.client.FieldAttachmentViewState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Player skeleton V1 prototype (P1, docs/dev/player_skeleton_v1_design.md; behind {@code [skeleton] enabled}, default
 * off): every player standing or crouching is drawn by the AFL skeleton instead of vanilla's six rigid boxes, in third
 * person and, through the first-person body's world-pass draw (client/FirstPersonBody), for the own body in first person.
 * <ul>
 * <li>per tick ({@link State}): the pelvis' own yaw (step turn, dragged past the waist's 45 deg), the chest's lagging
 * yaw, smoothed crouch / run / eye height;</li>
 * <li>per draw (mixin/client/LivingEntityRendererSkeletonMixin): after vanilla's setupAnim the pose is solved
 * ({@link HumanoidPoser}; once per frame and player, the shadow pass reuses it), written back to the six vanilla parts
 * (layers, armour, Curios follow chest, head, upper arms and thighs) and the body is drawn as the C-min skin mesh
 * ({@link SkinMesh}) in place of renderToBuffer; the head stays vanilla's;</li>
 * <li>hands: empty hands hang (the clips), an ordinary item takes vanilla's arm angles with a straight arm, the BR51
 * ({@link SkeletonGunStance}) is placed against the eye and both hands reach for its anchors by IK; a player action
 * ({@link SkeletonActions}: thermometer, recoil starter, fuel nozzle) poses the body and the hands it names; with a gun
 * up only the gun's own draw and put-away actions play, through the gun stance (the P9);</li>
 * <li>held items: drawn on the hand bone (ItemInHandLayer's translateToHand, {@link #translateToHand}), so they follow a
 * bent elbow.</li>
 * </ul>
 * First person: when the hands are supported (empty, the BR51 with the off hand empty, or a player action) the vanilla
 * hand pass is cancelled, so looking ahead shows no hands; otherwise the old first-person view model stays and the body's
 * arms are hidden, as in the first-person body V1. A held gun at its ready (stance loops: the P9) goes from its view model
 * to the body's arms looking down (client/FirstPersonBody#gunOnBody, user 2026-10-19).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PlayerSkeleton {
    /** Waist twist (deg) that starts a step turn while standing; the waist's hard limit is 45. */
    private static final float TURN_TRIGGER = 40F, WAIST = 45F, CHEST_LAG = 15F;
    /** Vanilla part pivots (model px), the frames the write-back reproduces. */
    static final Vector3f HEAD = new Vector3f(0, 0, 0), BODY = new Vector3f(0, 0, 0),
            RIGHT_ARM = new Vector3f(-5, 2, 0), LEFT_ARM = new Vector3f(5, 2, 0),
            RIGHT_LEG = new Vector3f(-1.9F, 12, 0), LEFT_LEG = new Vector3f(1.9F, 12, 0);

    private static final ModelPart HAND = new ModelPart(List.of(), Map.of());
    private static HumanoidPoser poser;
    /** The poser is on the Block-Mesh V2 rig (BlockMeshBody#active when it was built). */
    private static boolean poserBlockMesh;
    private static boolean failed;
    private static long frame;

    private PlayerSkeleton() {
    }

    static HumanoidPoser poser() {
        // the Block-Mesh V2 dev test (BlockMeshBody) switches the rig: a new poser, no cached frames of the other
        if (poser != null && poserBlockMesh != BlockMeshBody.active()) reset();
        if (poser == null && !failed) {
            SkeletonRig rig = null;
            if (BlockMeshBody.active()) {
                try {
                    rig = SkeletonRig.blockMeshV2();
                    poser = new HumanoidPoser(rig, SkeletonClips.locomotion());
                } catch (RuntimeException e) {
                    BlockMeshBody.fail(e);
                }
            }
            try {
                if (poser == null) poser = new HumanoidPoser(rig = SkeletonRig.get(), SkeletonClips.locomotion());
                poserBlockMesh = rig.profile != null;
                GunStance.useRig(rig);
                SeatPose.useRig(rig);
            } catch (RuntimeException e) {
                failed = true;
                poser = null;
                ApocalypseFirstLight.LOGGER.error("[AFL skeleton] rig or clips failed to load, the skeleton stays off", e);
            }
        }
        return poser;
    }

    /** The poser rebuilt on its next use and the cached frames dropped (the Block-Mesh V2 test switched). */
    static void reset() {
        poser = null;
        CACHE.clear();
    }

    public static boolean enabled() {
        return FirstPersonBodyConfig.skeleton() && poser() != null;
    }

    // ---------------------------------------------------------------- per player state, per tick

    private static final class State {
        float pelvis, pelvisO, chest, chestO, crouch, crouchO, run, runO, eyeHeight, eyeHeightO;
        int turnTicks = -1;
        float turnFrom, turnTo;
        boolean started;

        void tick(AbstractClientPlayer p) {
            float head = p.getYHeadRot();
            float crouching = p.isCrouching() ? 1 : 0;
            if (!started) {
                pelvis = chest = head;
                crouch = crouching;
                eyeHeight = p.getEyeHeight();
                started = true;
            }
            pelvisO = pelvis; chestO = chest; crouchO = crouch; runO = run; eyeHeightO = eyeHeight;
            crouch += (crouching - crouch) * 0.5F;   // as the camera eases its eye height
            run += ((p.isSprinting() ? 1 : 0) - run) * 0.35F;
            eyeHeight += (p.getEyeHeight() - eyeHeight) * 0.5F;
            float prone = ProneClient.bodyYaw(p, 1F);
            if (!Float.isNaN(prone)) {
                // lying on the front: hips and chest the body's own way (ProneClient turns it)
                pelvis = chest = prone;
                turnTicks = -1;
                return;
            }
            float thrown = com.antaurora.apofirstlight.client.survivor.LiveRagdollClient.bodyYaw(p);
            if (!Float.isNaN(thrown)) {
                // a living ragdoll at rest (getting up): hips and chest the way it lies, no turn after
                pelvis = chest = thrown;
                turnTicks = -1;
                return;
            }
            boolean gun = SkeletonGunStance.busy(p);
            float target = head + SkeletonGunStance.chestTwist(p, 1F);
            float seat = GroundSitClient.seatYaw(p);
            if (!Float.isNaN(seat)) {
                // sat on the ground: the hips keep the way it sat, the chest turns with the look within the waist's twist
                pelvis = seat;
                chest += Mth.wrapDegrees(target - chest) * 0.35F;
                chest = pelvis + Mth.clamp(Mth.wrapDegrees(chest - pelvis), -WAIST, WAIST);
                turnTicks = -1;
                return;
            }
            if (gun) chest = target;
            else {
                chest += Mth.wrapDegrees(target - chest) * 0.35F;
                float lag = Mth.wrapDegrees(target - chest);
                if (Math.abs(lag) > CHEST_LAG) chest = target - Math.signum(lag) * CHEST_LAG;
            }
            ChairSit.Sit sat = ChairSit.get(p);
            if (sat != null || BlockMeshBody.active() && p.isPassenger() && BlockMeshBody.chair(p)) {
                // on a chair (a wooden one: cabin/ChairSit, any rig; the office chair: Block-Mesh V2's ChairLegs): the hips keep
                // the seat's way (the wooden chair's facing, the office chair the look), no turn steps swinging the legs; the
                // chest within the waist's twist after the look (in the orbit camera the sitter's own look, which settles
                // ahead: only the drawn head turns to the camera, client/camera/OrbitCamera#head)
                pelvis = sat != null ? sat.yaw() : p.yBodyRot;
                chest = pelvis + Mth.clamp(Mth.wrapDegrees(chest - pelvis), -WAIST, WAIST);
                turnTicks = -1;
                return;
            }
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            if (dx * dx + dz * dz > 1e-4 && !p.isPassenger()) {
                // walking: the pelvis turns toward the way it goes, at most the waist's twist off the chest; backwards it stays
                float rel = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - chest);
                if (Math.abs(rel) > 112.5F) rel = Mth.wrapDegrees(rel + 180);
                float wanted = chest + Mth.clamp(rel, -WAIST, WAIST);
                pelvis += Mth.clamp(Mth.wrapDegrees(wanted - pelvis), -25F, 25F);
                turnTicks = -1;
            } else if (turnTicks >= 0) {
                turnTicks++;
                float f = Math.min(1F, turnTicks / 5F);
                pelvis = turnFrom + Mth.wrapDegrees(turnTo - turnFrom) * f * f * (3 - 2 * f);
                if (turnTicks >= 10) turnTicks = -1;
            } else if (Math.abs(Mth.wrapDegrees(chest - pelvis)) > TURN_TRIGGER) {
                turnFrom = pelvis;
                turnTo = chest;
                turnTicks = 0;
            }
            float twist = Mth.wrapDegrees(chest - pelvis);
            if (Math.abs(twist) > WAIST) pelvis = chest - Math.signum(twist) * WAIST;
        }
    }

    private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            CACHE.clear();
            SkeletonGunStance.clear();
            SkeletonActions.clear();
            return;
        }
        if (mc.isPaused()) return;
        SkeletonGunStance.tick(enabled()); // the own aim goes out even with the skeleton off here: the others may draw it
        if (!enabled()) {
            STATES.clear();
            CACHE.clear();
            return;
        }
        for (AbstractClientPlayer p : mc.level.players()) STATES.computeIfAbsent(p, k -> new State()).tick(p);
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            frame++;
            SkeletonActions.newFrame();
        }
    }

    // ---------------------------------------------------------------- per draw

    /** One player's solved frame: the pose, and the gun the skeleton draws (its frame in model blocks), if any. */
    private static final class Solved {
        long frame;
        final SkeletonPose pose;
        boolean slim;
        SkeletonGunStance.Hold hold;
        /** The prop the action holds (player actions S2) and its placement this frame (its model frame to model px), or null. */
        PlayerProps.Prop prop;
        final Matrix4f propPose = new Matrix4f();
        boolean propOpen, propHidden;
        /** The action hides what the player holds (PlayerAction#heldHidden): its first prop is one of the extras. */
        boolean heldHidden;
        /** The action's other props this frame (AFL Rig phase 2: the pistol's magazines), drawn from their own items. */
        final List<Extra> extras = new java.util.ArrayList<>();

        record Extra(PlayerProps.Prop prop, Matrix4f pose, boolean open) {}

        /** The ground's model y (px), NaN off it (riding): the Block-Mesh V2 toes. */
        float ground = Float.NaN;
        /** The frame the pose was solved in: the render origin (world) and render-origin blocks to model blocks. */
        Vec3 lastOrigin;
        final Matrix4f lastToModel = new Matrix4f();
        /**
         * What was shown when a chained action last took over (SkeletonActions#switchedAt: this switch's time), eased from
         * ({@link #crossfade}): the pose, its frame, the props; whether there was one shown just before.
         */
        final SkeletonPose from;
        final Matrix4f fromToModel = new Matrix4f();
        Vec3 fromOrigin;
        double fromAt = Double.NaN;
        boolean fromShown;
        final List<Extra> fromProps = new java.util.ArrayList<>();

        Solved(SkeletonRig rig) {
            pose = new SkeletonPose(rig);
            from = new SkeletonPose(rig);
        }
    }

    private static final class Draw {
        AbstractClientPlayer player;
        float partial;
        boolean world;
        final Matrix4f head = new Matrix4f();
        MultiBufferSource buffers;
        int light;
        Solved solved;
        final float[] saved = new float[12 * 6];
        PlayerModel<?> model;
        /** The pose stack the draw scaled (a seated rider, SeatedRiders#scale), popped at the end. */
        PoseStack scaled;
    }

    private static final Deque<Draw> DRAWS = new ArrayDeque<>();
    private static final Map<AbstractClientPlayer, Solved> CACHE = new WeakHashMap<>();
    /** The entity LevelRenderer#renderEntity is drawing (null: GUI or other previews). */
    private static Entity worldEntity;

    /** LevelRenderer#renderEntity HEAD / RETURN. */
    public static void worldBegin(Entity entity) { worldEntity = entity; }

    public static void worldEnd() { worldEntity = null; }

    /** LivingEntityRenderer#render HEAD. */
    public static void begin(LivingEntity entity, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!(entity instanceof AbstractClientPlayer player) || !enabled()) return;
        Draw d = new Draw();
        d.player = player;
        d.partial = partial;
        d.world = worldEntity == entity;
        d.head.set(pose.last().pose());
        d.buffers = buffers;
        d.light = light;
        DRAWS.push(d);
        // a seated rider is drawn smaller about the render origin; d.head stays unscaled, so the solve's model frame
        // (toModel) carries the scale and the seat's points land where they are in the world
        float scale = supportedPose(player) ? SeatedRiders.scale(player, partial) : 1F;
        if (scale != 1F) {
            pose.pushPose();
            pose.scale(scale, scale, scale);
            d.scaled = pose;
        }
    }

    /** LivingEntityRenderer#render RETURN: vanilla's own part values back for whoever draws the shared model next. */
    public static void end(LivingEntity entity) {
        Draw d = DRAWS.peek();
        if (d == null || d.player != entity) return;
        DRAWS.pop();
        if (d.solved != null && d.model != null) restore(d.model, d.saved);
        if (d.scaled != null) d.scaled.popPose();
    }

    /** LivingEntityRenderer#render, after setupAnim: solve (or reuse) the pose and write the vanilla parts. */
    public static void afterSetupAnim(LivingEntity entity, EntityModel<?> model, PoseStack pose) {
        Draw d = DRAWS.peek();
        if (d == null || d.player != entity || !(model instanceof PlayerModel<?> playerModel) || !supportedPose(d.player)) return;
        Matrix4f toModel = new Matrix4f(pose.last().pose()).invert().mul(d.head); // render-origin blocks -> model blocks
        d.model = playerModel;
        save(playerModel, d.saved);   // before the solve, which writes the parts for the gun's mounts
        Solved s = d.world ? CACHE.get(d.player) : null;
        if (s == null || s.frame != frame || !d.world) {
            if (s == null || s.pose.rig != poser().rig()) s = new Solved(poser().rig());
            try {
                solve(d, playerModel, toModel, s);
            } catch (RuntimeException e) {
                failed = true;
                poser = null;
                ApocalypseFirstLight.LOGGER.error("[AFL skeleton] pose failed, the skeleton turns off", e);
                restore(playerModel, d.saved);
                d.model = null;
                return;
            }
            s.frame = frame;
            if (d.world) CACHE.put(d.player, s);
        }
        write(playerModel, s.pose);
        d.solved = s;
    }

    /** In place of the model's renderToBuffer: true when the skeleton drew the body. */
    public static boolean draw(EntityModel<?> model, PoseStack pose, VertexConsumer consumer, int light, int overlay,
                               float r, float g, float b, float a) {
        Draw d = DRAWS.peek();
        if (d == null || d.solved == null || d.model != model) return false;
        // lying in the own first person the arms come forward along the view for this draw (ProneClient#firstPersonReach);
        // the solved pose is the frame's cached one, so they go back after it
        Vector3f ahead = null;
        if (d.world && d.player == Minecraft.getInstance().player && FirstPersonBody.hidingParts() && ProneClient.prone(d.player)) {
            net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Matrix4f toModel = new Matrix4f(pose.last().pose()).invert().mul(d.head);
            float yaw = camera.getYRot() * Mth.DEG_TO_RAD;
            Vector3f level = toModel.transformDirection(new Vector3f(-Mth.sin(yaw), 0F, Mth.cos(yaw)));
            level.y = 0F;
            if (level.lengthSquared() > 1e-6F) {
                level.normalize();
                Vec3 at = camera.getPosition().subtract(Mth.lerp(d.partial, d.player.xOld, d.player.getX()),
                        Mth.lerp(d.partial, d.player.yOld, d.player.getY()), Mth.lerp(d.partial, d.player.zOld, d.player.getZ()));
                Vector3f eye = toModel.transformPosition(new Vector3f((float) at.x, (float) at.y, (float) at.z)).mul(16F);
                float reach = ProneClient.firstPersonReach(d.player, d.partial, d.solved.pose, eye, level, camera.getXRot());
                if (reach > 0F) shiftArms(d.solved.pose, ahead = level.mul(reach));
            }
        }
        try {
            return drawSolved(d, pose, consumer, light, overlay, r, g, b, a);
        } finally {
            if (ahead != null) shiftArms(d.solved.pose, ahead.negate());
        }
    }

    private static final String[] ARM_BONES = {"upper_arm_r", "forearm_r", "hand_r", "upper_arm_l", "forearm_l", "hand_l"};

    /** Both arms (their skin rings, the gauze on them) moved by {@code by} (model px). */
    private static void shiftArms(SkeletonPose pose, Vector3f by) {
        for (String bone : ARM_BONES) {
            int b = pose.rig.find(bone);
            if (b >= 0) pose.world[b].translateLocal(by);
        }
    }

    private static boolean drawSolved(Draw d, PoseStack pose, VertexConsumer consumer, int light, int overlay,
                                      float r, float g, float b, float a) {
        PlayerModel<?> m = d.model;
        PoseStack.Pose last = pose.last();
        Matrix4f mp = last.pose();
        Matrix3f mn = last.normal();
        Vector3f p = new Vector3f(), n = new Vector3f();
        SkinMesh.Sink sink = (x, y, z, u, v, nx, ny, nz) -> {
            mp.transformPosition(x / 16F, y / 16F, z / 16F, p);
            mn.transform(nx, ny, nz, n);
            consumer.vertex(p.x, p.y, p.z, r, g, b, a, u, v, overlay, light, n.x, n.y, n.z);
        };
        SkeletonRig rig = d.solved.pose.rig;
        // the own first person's arms thinner with a pistol held out (GunStance#firstPersonArm)
        float arms = d.solved.hold != null ? d.solved.hold.arms() : 1F;
        // AFL Block-Mesh V2: the B body in its layers (BlockMeshWardrobe) in place of the skin mesh, the head and the wound marks
        if (rig.profile != null && BlockMeshBody.draw(d.solved.pose, m, pose, d.buffers, light, overlay, r, g, b, a, d.solved.ground, d.player)) {
            drawGear(d, pose, light, overlay, 1F);
            return true;
        }
        // female body V1: the survivor's sex (SurvivorIdentityClient) gives the female torso and chest
        boolean female = d.player != null && SurvivorIdentityClient.body(d.player.getUUID()).female();
        for (SkeletonRig.SkinPart part : rig.skin) {
            float thick = part.part().endsWith("_arm") ? arms : 1F;
            if (base(m, part.part()).visible) SkinMesh.emit(d.solved.pose, part, d.solved.slim, false, thick, female, sink);
            if (layer(m, part.overlay()).visible) SkinMesh.emit(d.solved.pose, part, d.solved.slim, true, thick, female, sink);
        }
        m.head.render(pose, consumer, light, overlay, r, g, b, a);
        m.hat.render(pose, consumer, light, overlay, r, g, b, a);
        // skin wound marks V1: the bare skin once more, with the player's wound texture (the same vertices, after the skin)
        VertexConsumer wounds = com.antaurora.apofirstlight.client.WoundSkin.consumer(d.player, d.solved.slim, d.buffers);
        if (wounds != null) {
            SkinMesh.Sink marks = (x, y, z, u, v, nx, ny, nz) -> {
                mp.transformPosition(x / 16F, y / 16F, z / 16F, p);
                mn.transform(nx, ny, nz, n);
                wounds.vertex(p.x, p.y, p.z, r, g, b, a, u, v, overlay, light, n.x, n.y, n.z);
            };
            for (SkeletonRig.SkinPart part : rig.skin)
                if (base(m, part.part()).visible)
                    SkinMesh.emit(d.solved.pose, part, d.solved.slim, false, part.part().endsWith("_arm") ? arms : 1F, female, marks);
            m.head.render(pose, wounds, light, overlay, r, g, b, a);
        }
        // old-body clothes V1: what the player wears over the skin (after the skin's own buffer is done with)
        OldBodyWardrobe.draw(d.player, d.solved.pose, d.solved.slim, female, arms, mp, mn, d.buffers, light, overlay, r, g, b, a,
                name -> base(m, name).visible);
        drawGear(d, pose, light, overlay, arms);
        return true;
    }

    /** Gauze, the gun the skeleton holds and the action's other props: on the posed body, whichever skin drew it. */
    private static void drawGear(Draw d, PoseStack pose, int light, int overlay, float arms) {
        PlayerModel<?> m = d.model;
        // health V1a: the gauze on the forearm, and the cloth off the roll the bandage action carries (the splint actions'
        // second prop, fractures V1)
        Vector3f roll = d.solved.prop != null && !d.solved.propHidden && "bandage".equals(d.solved.prop.id())
                ? d.solved.propPose.transformPosition(com.antaurora.apofirstlight.skeleton.action.ActionPose.point(d.solved.prop, "center", false), new Vector3f()) : null;
        for (Solved.Extra extra : d.solved.extras)
            if (roll == null && "bandage".equals(extra.prop().id()))
                roll = extra.pose().transformPosition(com.antaurora.apofirstlight.skeleton.action.ActionPose.point(extra.prop(), "center", false), new Vector3f());
        com.antaurora.apofirstlight.client.BandageWrapRenderer.drawSkeleton(d.player, d.solved.pose, d.solved.slim, arms, m, pose, d.buffers, light, overlay, roll);
        // the own first-person body draws a gun only while its arms are shown (#firstPersonHands, or the gun handed to
        // the body looking down: FirstPersonBody#gunOnBody); otherwise a held one is the view model's
        boolean ownView = d.world && d.player == Minecraft.getInstance().player && FirstPersonBody.hidingParts();
        if (d.solved.hold != null && !AflShaderCompat.activeShadowPass() && (!ownView || FirstPersonBody.armsShown()))
            SkeletonGunStance.render(d.solved.hold, pose, d.buffers, light);
        for (Solved.Extra e : d.solved.extras) {
            ItemStack stack = e.open() ? openStack(e.prop()) : extraStack(e.prop());
            if (!stack.isEmpty() || e.prop().mesh() != null) drawPlaced(d, e.prop(), e.pose(), e.open(), stack, pose, d.buffers, light);
        }
    }

    private static final Map<String, ItemStack> EXTRA_STACKS = new java.util.HashMap<>();

    /** An extra prop's look: its first listed item (PlayerProps.Prop#items), or empty when it is not an item. */
    private static ItemStack extraStack(PlayerProps.Prop prop) {
        return EXTRA_STACKS.computeIfAbsent(prop.id(), k -> {
            if (prop.items().isEmpty()) return ItemStack.EMPTY;
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation(ApocalypseFirstLight.MOD_ID, prop.items().get(0)));
            return item == null || item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        });
    }

    private static final Map<String, ItemStack> OPEN_STACKS = new java.util.HashMap<>();

    /**
     * An opened extra prop (PlayerAction open_from) that is an item: its stack with the tag Open (the folding knife's blade
     * out, FoldingKnifeItem#OPEN; items that ignore the tag look the same).
     */
    private static ItemStack openStack(PlayerProps.Prop prop) {
        return OPEN_STACKS.computeIfAbsent(prop.id(), k -> {
            ItemStack stack = extraStack(prop);
            if (stack.isEmpty()) return stack;
            stack = stack.copy();
            stack.getOrCreateTag().putBoolean(com.antaurora.apofirstlight.item.FoldingKnifeItem.OPEN, true);
            return stack;
        });
    }

    /**
     * ItemInHandLayer: the gun the skeleton draws itself is not drawn again in the vanilla hand, nor is the item that
     * replaced it while the old gun is still on its way back to its mount.
     */
    public static boolean ownsHeldItem(LivingEntity entity, ItemStack stack) {
        Draw d = DRAWS.peek();
        if (d == null || d.player != entity) return false;
        // a seated hand busy with the vehicle's controls (the T15's key, SeatedRiderHost#handBusy): its item is put by
        if (stack == d.player.getMainHandItem() && entity.getVehicle() instanceof com.antaurora.apofirstlight.skeleton.seat.SeatedRiderHost host
                && host.handBusy(entity)) return true;
        // crawling: both hands on the ground, the held item put away (ProneClient#stowed)
        if (d.solved != null && ProneClient.stowed(d.player)) return true;
        return d.solved != null && d.solved.hold != null && (stack == d.solved.hold.stack() || stack == d.player.getMainHandItem());
    }

    /**
     * ItemInHandLayer#renderArmWithItem HEAD: the held item an action holds as its prop (player actions S2) is drawn where
     * the action placed it, not in the hand, or not at all while its key hides it. True: the action owns it (the vanilla
     * hand item is skipped). An action that hides the held item (PlayerAction#heldHidden) owns both hands' items.
     */
    public static boolean drawProp(LivingEntity entity, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        Draw d = DRAWS.peek();
        if (d != null && d.player == entity && d.solved != null && d.solved.heldHidden) return true;   // PlayerAction#heldHidden
        if (d == null || d.player != entity || d.solved == null || d.solved.prop == null || stack != d.player.getMainHandItem()) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !ApocalypseFirstLight.MOD_ID.equals(id.getNamespace()) || !d.solved.prop.holds(id.getPath())) return false;
        if (!d.solved.propHidden) drawPlaced(d, d.solved.prop, d.solved.propPose, d.solved.propOpen, stack, pose, buffers, light);
        return true;
    }

    /** A prop at its placement (its model frame to model px), from the model-frame pose stack (blocks). */
    private static void drawPlaced(Draw d, PlayerProps.Prop prop, Matrix4f propPose, boolean opened, ItemStack stack, PoseStack pose,
                                   MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.scale(1F / 16F, 1F / 16F, 1F / 16F);   // model px
        Matrix4f place = new Matrix4f(propPose).mul(prop.itemToLocal());   // item block units
        pose.last().pose().mul(place);
        Matrix3f normal = new Matrix3f().set(place).invert().transpose();   // mulPoseMatrix would leave the normals unturned
        normal.scale(1F / (float) Math.cbrt(Math.abs(normal.determinant())));
        pose.last().normal().mul(normal);
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.level.block.state.BlockState open = opened ? openState(prop) : null;
        if (prop.mesh() != null) {
            // only the named parts of the item's AFL Mesh, where its item renderer puts the mesh (PlayerProps.MeshLook)
            drawMeshParts(prop.mesh(), pose, buffers, light);
        } else if (open != null) {
            // the block in its open state (the same geometry, block units as the item's): the jerry can's cap swung off
            mc.getBlockRenderer().renderSingleBlock(open, pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    net.minecraftforge.client.model.data.ModelData.EMPTY, null);
        } else {
            pose.translate(0.5F, 0.5F, 0.5F);   // ItemRenderer centres the model on 0
            mc.getItemRenderer().renderStatic(d.player, stack, net.minecraft.world.item.ItemDisplayContext.NONE, false, pose, buffers,
                    d.player.level(), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, d.player.getId());
        }
        pose.popPose();
    }

    /**
     * PlayerProps.MeshLook: the named parts of the item's mesh, from the item block units pose (the mesh's foot at (0.5,
     * offset, 0.5), as client/AflStaticMeshItemRenderer draws the whole item). The parts' bones are the bake's one root
     * bone at the origin (tools/build-material-meshes-v1.mjs bake), so no bone transform.
     */
    private static void drawMeshParts(PlayerProps.MeshLook look, PoseStack pose, MultiBufferSource buffers, int light) {
        net.minecraft.resources.ResourceLocation geometry = new net.minecraft.resources.ResourceLocation(ApocalypseFirstLight.MOD_ID,
                "geo/" + look.model() + ".geo.json");
        com.antaurora.vextris.mesh.MeshModel mesh = com.antaurora.vextris.client.VextrisMeshes.byGeometry(geometry);
        if (mesh == null) return;
        List<com.antaurora.vextris.mesh.MeshPart> parts = new java.util.ArrayList<>();
        for (com.antaurora.vextris.mesh.MeshPart part : mesh.allParts()) if (look.parts().contains(part.name())) parts.add(part);
        if (parts.isEmpty()) return;
        net.minecraft.resources.ResourceLocation texture = new net.minecraft.resources.ResourceLocation(ApocalypseFirstLight.MOD_ID,
                "textures/item/" + look.atlas() + ".png");
        pose.pushPose();
        pose.translate(0.5F, look.offset(), 0.5F);
        com.antaurora.vextris.client.MeshRenderer.renderParts(parts, pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture)),
                light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        pose.popPose();
    }

    /** The prop's open block state (PlayerProps.Prop#openBlock, facing north, open), or null. */
    private static net.minecraft.world.level.block.state.BlockState openState(PlayerProps.Prop prop) {
        if (prop.openBlock() == null) return null;
        net.minecraft.world.level.block.Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new net.minecraft.resources.ResourceLocation(ApocalypseFirstLight.MOD_ID, prop.openBlock()));
        if (block == null) return null;
        net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN))
            state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, true);
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING))
            state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.NORTH);
        return state;
    }

    /**
     * ItemInHandLayer#renderArmWithItem in place of the model's translateToHand: the hand bone's frame (as the vanilla arm
     * part it replaces would be, slim shift included), so an item follows the hand. False: not a skeleton draw.
     */
    public static boolean translateToHand(LivingEntity entity, HumanoidArm arm, PoseStack pose) {
        Draw d = DRAWS.peek();
        if (d == null || d.player != entity || d.solved == null || d.model == null) return false;
        boolean right = arm == HumanoidArm.RIGHT;
        SkeletonRig rig = d.solved.pose.rig;
        set(HAND, d.solved.pose.world[right ? rig.handR : rig.handL], armPivot(rig, right));
        if (d.solved.slim) HAND.x += right ? 0.5F : -0.5F;
        HAND.translateAndRotate(pose);
        return true;
    }

    private static void solve(Draw d, PlayerModel<?> model, Matrix4f toModel, Solved out) {
        AbstractClientPlayer p = d.player;
        float partial = d.partial;
        State s = d.world ? STATES.get(p) : null;
        HumanoidPoser.Input in = new HumanoidPoser.Input();
        // the Block-Mesh V2 body has one arm width (no Steve / Alex, user 2026-10-25)
        boolean blockMesh = poser().rig().profile != null;
        in.slim = !blockMesh && "slim".equals(p.getModelName());
        out.slim = in.slim;
        float body = bodyYaw(p, partial);
        float head = Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot);
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        boolean firstPerson = d.world && p == mc.player && !camera.isDetached();
        // the own first person while an action's look turns the camera alone (FirstPersonActionView): posed to the camera's pitch
        float pitch = firstPerson && FirstPersonActionView.looking() ? camera.getXRot() : Mth.lerp(partial, p.xRotO, p.getXRot());
        boolean crouching = p.isCrouching();
        float crouch = s != null ? Mth.lerp(partial, s.crouchO, s.crouch) : crouching ? 1 : 0;
        float speed = Math.min(1F, p.walkAnimation.speed(partial));
        float position = p.walkAnimation.position(partial);
        in.walkPhase = frac(position * 0.6662F / Mth.TWO_PI);
        in.move = Math.min(1F, speed * Mth.lerp(crouch, 1.15F, 3F));
        in.run = s != null ? Mth.lerp(partial, s.runO, s.run) : p.isSprinting() ? 1 : 0;
        in.crouch = crouch;
        in.time = (p.tickCount + partial) / 20F;
        in.turnStep = s != null && s.turnTicks >= 0 ? (s.turnTicks + partial) / 20F : -1F;
        // a hit's flinch (injury knockdown V1): the body sways away from it
        HitReactClient.apply(p, in, partial);
        // lying on the front (ProneClient): a held gun is an ordinary item there, put away while crawling
        boolean gun = SkeletonGunStance.busy(p) && !ProneClient.prone(p);
        float pelvis = s != null ? Mth.rotLerp(partial, s.pelvisO, s.pelvis) : body;
        float chest = s != null ? Mth.rotLerp(partial, s.chestO, s.chest) : head + SkeletonGunStance.chestTwist(p, partial);
        in.rootYaw = Mth.wrapDegrees(pelvis - body);
        in.chestYaw = Mth.clamp(Mth.wrapDegrees(chest - pelvis), -WAIST, WAIST);
        in.viewYaw = Mth.wrapDegrees(head - body);
        in.viewPitch = pitch;
        in.chestPitch = HumanoidPoser.chestPitch(pitch, SkeletonGunStance.gunUp(p, partial));
        // a living ragdoll's get-up: solved facing ahead, then put at the root and yaw it lies at (LiveRagdollClient#afterSolve)
        float aligned = d.world ? com.antaurora.apofirstlight.client.survivor.LiveRagdollClient.alignedYaw(p) : Float.NaN;
        if (!Float.isNaN(aligned)) {
            in.rootYaw = 0F;
            in.chestYaw = 0F;
            in.viewYaw = Mth.wrapDegrees(head - aligned);
        }
        // the eye: the camera's own position for the own first-person body, the entity's eye height otherwise
        Vec3 eye;
        Vec3 offset = mc.getEntityRenderDispatcher().getRenderer(p).getRenderOffset(p, partial);
        Vec3 origin = new Vec3(Mth.lerp(partial, p.xOld, p.getX()), Mth.lerp(partial, p.yOld, p.getY()),
                Mth.lerp(partial, p.zOld, p.getZ())).add(offset);
        // a living ragdoll falling (client/survivor/LiveRagdollClient): its boxes are the body
        if (d.world && com.antaurora.apofirstlight.client.survivor.LiveRagdollClient.pose(p, partial, origin, toModel, out.pose)) {
            out.hold = null;
            out.prop = null;
            out.heldHidden = false;
            out.extras.clear();
            out.lastOrigin = origin;
            out.lastToModel.set(toModel);
            return;
        }
        if (firstPerson) {
            eye = camera.getPosition().subtract(origin);
        } else {
            float eyeHeight = s != null ? Mth.lerp(partial, s.eyeHeightO, s.eyeHeight) : p.getEyeHeight();
            eye = new Vec3(0, eyeHeight - offset.y, 0);
        }
        Vector3f eyeModel = toModel.transformPosition(new Vector3f((float) eye.x, (float) eye.y, (float) eye.z)).mul(16F);
        in.eyeTarget = Float.isNaN(aligned) ? eyeModel : null;
        // arms
        in.vanillaRight.rotationZYX(model.rightArm.zRot, model.rightArm.yRot, model.rightArm.xRot);
        in.vanillaLeft.rotationZYX(model.leftArm.zRot, model.leftArm.yRot, model.leftArm.xRot);
        // the ground for the Block-Mesh V2 toes: the entity's feet (the render origin is off them by the render offset)
        out.ground = blockMesh && !p.isPassenger() ? toModel.transformPosition(new Vector3f(0, (float) -offset.y, 0)).mul(16F).y : Float.NaN;
        // on a wooden chair (cabin/ChairSit: no vehicle, the player stands on the chair's floor) or Block-Mesh V2 on the office chair
        boolean aflChair = ChairSit.sitting(p);
        boolean chair = blockMesh && BlockMeshBody.chair(p);
        if (p.isPassenger() && p.getVehicle() != null && p.getVehicle().shouldRiderSit()) {
            in.legsVanilla = true;
            in.vanillaRightLeg.rotationZYX(model.rightLeg.zRot, model.rightLeg.yRot, model.rightLeg.xRot);
            in.vanillaLeftLeg.rotationZYX(model.leftLeg.zRot, model.leftLeg.yRot, model.leftLeg.xRot);
        } else if (aflChair && !blockMesh) {
            // the vanilla rig on a wooden chair: vanilla's riding legs (HumanoidModel#setupAnim riding), the body as low as
            // vanilla's rider was (its feet ChairLegs.WOOD.drop() under the floor)
            in.legsVanilla = true;
            in.vanillaRightLeg.rotationZYX(0.07853982F, Mth.PI / 10F, -1.4137167F);
            in.vanillaLeftLeg.rotationZYX(-0.07853982F, -Mth.PI / 10F, -1.4137167F);
            in.rootOffset.add(0, ChairLegs.WOOD.drop() * toModel.transformDirection(new Vector3f(0, 1, 0)).length() * 16F, 0);
        }
        // seated riders (docs/dev/seated_rider_v1_plan.md): on a seat or getting on or off one, the seat poses the body
        SeatedRiders.Seated seated = SeatedRiders.frame(p, partial);
        SeatPose.Seat seat = null;
        SeatPose.Moment moment = null;
        if (seated != null) {
            seat = seat(seated.seat(), origin, toModel);
            moment = seated.moment();
            SeatPose.Stand stand = new SeatPose.Stand();
            stand.feet.set(toModel.transformPosition(new Vector3f()).mul(16F));
            if (seated.move() == SeatPose.Move.GET_IN) {
                stand.feet.set(model(seated.standFeet(), origin, toModel));
                stand.turn.set(facing(seated.standYaw(), toModel));
            }
            SeatPose.body(in, seat, stand, moment, seated.torso(), eyeModel.y);
        }
        float chairRootYaw = in.rootYaw;
        // sat on the ground (docs/gameplay/splint_animation_v2_plan.md 3): the sitting clips on the stance layer; the eye is
        // the posed one (the own first-person camera follows it), the look does not bend the chest
        GroundSitClient.Sit sit = d.world && seated == null ? GroundSitClient.stance(p, in, poser().rig(), partial) : null;
        if (sit != null) {
            in.eyeTarget = null;
            in.chestPitch = GroundSitClient.chestPitch(sit, in.chestPitch);
        }
        // lying on the front (docs/gameplay/lightning_injury_v1_plan.md 10): the prone clips, the eye the posed one
        ProneClient.Lie lie = seated == null && sit == null ? ProneClient.stance(p, in, poser().rig(), partial) : null;
        // a player action: its clips, step and facing before the body. With a gun up only the gun's own draw and put-away
        // (the gun has both hands), and those only with it (docs/dev/p9_draw_holster_actions_v1.md 2.4)
        ActionPose.Moment action = seated != null ? null : SkeletonActions.moment(p, partial, origin, toModel, d.world);
        // a chained action took over (the bench's next one): what was shown till now, to ease from (#crossfade)
        if (d.world) snapshot(p, out);
        // (by its source, not its id: the magazine loading gun/<gun>_mag_load is no gun's own, user 2026-10-20 "左手从来就没有出现过")
        if (action != null && gun != SkeletonActions.gunAction(p)) action = null;
        if (action != null) action.view = ActionPose.viewFrame(eyeModel, in.viewYaw, in.viewPitch);   // a gun's own: its stance's
        // the own first-person body leaves an action that keeps its view model alone (the view model is the hands)
        if (action != null && firstPerson && action.action.firstPerson == PlayerAction.FirstPerson.VIEW_MODEL) action = null;
        if (action != null && !ActionPose.body(in, poser().rig(), action)) action = null;
        if (action != null && sit != null) GroundSitClient.keepLegs(in, poser().rig(), action.action);
        if (action != null && d.world) SkeletonActions.stepped(p, in.rootOffset, toModel);
        // a gun's stance loops (idle, sprint) on the body under its draw or put-away (docs/dev/p9_stance_loops_v1.md)
        // the gun's stance as others see it, the own first-person body too: there a held gun is its old view model's
        // (#firstPersonHands) but for looking down at its ready (FirstPersonBody#gunOnBody), and back to its mount
        if (gun && seated == null) SkeletonGunStance.stanceClips(p, in, poser().rig(), partial);
        // on a chair the legs and hips are the chair's under any action or stance (their legs give way to the IK, the hips
        // keep the seat's place and way); a foot's fidgets (ChairPose, the wooden chair)
        ChairLegs.Result chairLegs = null;
        ChairSitClient.Frame chairIdle = ChairSitClient.Frame.NONE;
        if (chair && seated == null) {
            in.rootYaw = chairRootYaw;
            in.rootTurn = null;
            chairLegs = BlockMeshBody.chairLegs(in, poser().rig(), p, origin, toModel, partial);
            if (aflChair) {
                boolean busy = gun || action != null || p.isUsingItem() || !p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty();
                chairIdle = ChairSitClient.frame(p, partial, busy);
                ChairPose.body(in, ChairLegs.WOOD, chairLegs, chairIdle.idle(), chairIdle.weight(), chairIdle.time(), chairIdle.right());
            }
        }
        HumanoidPoser.Report report = poser().solveBody(in, out.pose);
        if (seated != null && d.world && seated.move() == SeatPose.Move.NONE) SeatedRiders.solvedTorso(p, report.torso);
        out.hold = null;
        out.prop = null;
        out.heldHidden = false;
        out.extras.clear();
        if (gun) {
            // the gun hangs on the posed body (its mounts) or the eye; the arms reach for it
            write(model, out.pose);
            float viewYaw = firstPerson ? camera.getYRot() : head, viewPitch = firstPerson ? camera.getXRot() : pitch;
            out.hold = SkeletonGunStance.hold(p, model, out.pose, in, eye, viewYaw, viewPitch, partial, false, toModel, action);
            if (out.hold == null) action = null;   // a gun action's hands and prop belong to the stance
        }
        if (out.hold == null) {
            boolean rightMain = p.getMainArm() == HumanoidArm.RIGHT;
            ItemStack rightStack = rightMain ? p.getMainHandItem() : p.getOffhandItem();
            ItemStack leftStack = rightMain ? p.getOffhandItem() : p.getMainHandItem();
            boolean using = p.isUsingItem();
            in.right = !rightStack.isEmpty() || using ? HumanoidPoser.ArmMode.VANILLA : HumanoidPoser.ArmMode.CLIP;
            in.left = !leftStack.isEmpty() || using ? HumanoidPoser.ArmMode.VANILLA : HumanoidPoser.ArmMode.CLIP;
            // lying, the arms are the clips' (on the ground, or under the chest holding the item)
            if (lie != null) in.right = in.left = HumanoidPoser.ArmMode.CLIP;
            // an action clip that moves an arm keeps it (the bandage's held-out forearm while the item is in use): vanilla's
            // pose only as the action blends in or out
            if (action != null && in.actionClip != null) {
                if (in.right == HumanoidPoser.ArmMode.VANILLA && clipMovesArm(in.actionClip, true, action.mirror)) {
                    in.right = HumanoidPoser.ArmMode.CLIP;
                    in.vanillaRightWeight = 1F - in.actionWeight;
                }
                if (in.left == HumanoidPoser.ArmMode.VANILLA && clipMovesArm(in.actionClip, false, action.mirror)) {
                    in.left = HumanoidPoser.ArmMode.CLIP;
                    in.vanillaLeftWeight = 1F - in.actionWeight;
                }
            }
            if (model.attackTime > 0F) {
                // a punch: the swinging arm follows vanilla's swing over the clip
                boolean swingRight = (p.swingingArm == InteractionHand.MAIN_HAND) == rightMain;
                float w = Mth.sin(model.attackTime * Mth.PI);
                if (swingRight) in.vanillaRightWeight = w;
                else in.vanillaLeftWeight = w;
            }
            if (seat != null) SeatPose.hands(in, out.pose, poser(), seat, moment);
            else if (chairLegs != null) {
                ChairPose.hands(in, out.pose, poser());
            }
            if (action != null) {
                ActionPose.hands(in, out.pose, poser(), action);
                if (action.action.heldHidden && ActionPose.weight(action) > 0) {
                    out.heldHidden = true;
                    PlayerProps.Prop first = action.prop == null || action.propHidden[0] ? null : PlayerProps.get(action.action.prop);
                    if (first != null) out.extras.add(new Solved.Extra(first, new Matrix4f(action.prop), action.propOpen));
                } else if (action.prop != null) {
                    out.prop = PlayerProps.get(action.action.prop);
                    out.propPose.set(action.prop);
                    out.propOpen = action.propOpen;
                    out.propHidden = action.propHidden[0];
                }
                for (int i = 1; i < action.props.length; i++) {
                    PlayerProps.Prop extra = action.props[i] == null || action.propHidden[i] ? null : PlayerProps.get(action.action.props.get(i).id());
                    if (extra != null) out.extras.add(new Solved.Extra(extra, new Matrix4f(action.props[i]), action.propsOpen[i]));
                }
            }
        }
        poser().solveArms(in, out.pose, report);
        if (d.world && action != null) crossfade(p, out, origin, toModel);
        out.lastOrigin = origin;
        out.lastToModel.set(toModel);
        if (d.world) com.antaurora.apofirstlight.client.survivor.LiveRagdollClient.afterSolve(p, partial, origin, toModel, out.pose);
        if (d.world) SkeletonActions.solved(p, out.pose, action, origin, toModel);
        if (sit != null) GroundSitClient.solved(p, out.pose, origin, toModel);
        if (chair && seated == null && d.world) BlockMeshBody.chairSolved(p, out.pose, origin, toModel);
        if (lie != null && d.world) ProneClient.solved(p, out.pose, origin, toModel);
        if (d.world && out.prop != null) {
            Matrix4f fromModel = new Matrix4f(toModel).invert();
            Map<String, Vec3> points = new java.util.HashMap<>();
            for (String name : out.prop.points().keySet()) {
                Vector3f local = ActionPose.point(out.prop, name, action.mirror);
                Vector3f w = fromModel.transformPosition(out.propPose.transformPosition(local).div(16F));
                points.put(name, origin.add(w.x, w.y, w.z));
            }
            SkeletonActions.propPoints(p, points);
        }
    }

    /**
     * A chained action took over since the last solve (SkeletonActions#switchedAt): the pose and the props as they were
     * shown (the frame before, in the frame they were solved in), to ease from. Nothing when none was shown just before.
     */
    private static void snapshot(AbstractClientPlayer p, Solved out) {
        double at = SkeletonActions.switchedAt(p);
        if (Double.isNaN(at) || at == out.fromAt) return;
        out.fromAt = at;
        out.fromShown = out.lastOrigin != null && frame - out.frame <= 2;
        if (!out.fromShown) return;
        out.from.copyFrom(out.pose);
        out.fromOrigin = out.lastOrigin;
        out.fromToModel.set(out.lastToModel);
        out.fromProps.clear();
        if (out.prop != null) out.fromProps.add(new Solved.Extra(out.prop, new Matrix4f(out.propPose), out.propOpen));
        for (Solved.Extra e : out.extras) out.fromProps.add(new Solved.Extra(e.prop(), new Matrix4f(e.pose()), e.open()));
    }

    /**
     * Docs furniture_bench_work_v1.md 12.9: a chained action taking over (the saw put down and the hammer taken up, the next
     * nail, the next piece) eases from what was shown over SkeletonActions#crossfade, the whole pose (and with it the posed
     * eye the own camera sits on) and each prop both show; the old frame taken into this one (the body's yaw may turn).
     */
    private static void crossfade(AbstractClientPlayer p, Solved out, Vec3 origin, Matrix4f toModel) {
        if (!out.fromShown || out.fromAt != SkeletonActions.switchedAt(p)) return;
        float f = SkeletonActions.crossfade(p);
        if (f >= 1F) return;
        // the old model px to this frame's: px = 16 toModel (world - origin)
        Matrix4f c = new Matrix4f().scale(16F).mul(toModel)
                .translate((float) (out.fromOrigin.x - origin.x), (float) (out.fromOrigin.y - origin.y), (float) (out.fromOrigin.z - origin.z))
                .mul(new Matrix4f(out.fromToModel).invert()).scale(1F / 16F);
        out.pose.blendFrom(out.from, c, f);
        blendProp(out.prop, out.propPose, out, c, f);
        for (Solved.Extra e : out.extras) blendProp(e.prop(), e.pose(), out, c, f);
    }

    /** A prop shown before the switch too: eased from where it was (its origin and turn), else left where it is. */
    private static void blendProp(PlayerProps.Prop prop, Matrix4f to, Solved out, Matrix4f c, float f) {
        if (prop == null) return;
        for (Solved.Extra e : out.fromProps) {
            if (e.prop() != prop) continue;
            Matrix4f from = new Matrix4f(c).mul(e.pose());
            if (from.determinant3x3() <= 0F || to.determinant3x3() <= 0F) return;   // a reflected one (mirrored): as it is
            Vector3f ta = from.getTranslation(new Vector3f()), tb = to.getTranslation(new Vector3f());
            Quaternionf qa = from.getNormalizedRotation(new Quaternionf()), qb = to.getNormalizedRotation(new Quaternionf());
            float sa = from.getScale(new Vector3f()).x, sb = to.getScale(new Vector3f()).x;
            to.translation(ta.lerp(tb, f)).rotate(qa.slerp(qb, f)).scale(sa + (sb - sa) * f);
            return;
        }
    }

    /** The action clip turns this arm (upper arm or forearm; the clip's other side when the action is mirrored). */
    private static boolean clipMovesArm(com.antaurora.apofirstlight.skeleton.SkeletonClips.Clip clip, boolean right, boolean mirror) {
        SkeletonRig rig = poser().rig();
        boolean r = right != mirror;
        return clip.rotates(r ? rig.upperArmR : rig.upperArmL) || clip.rotates(r ? rig.forearmR : rig.forearmL);
    }

    /** A world point (blocks) to model px. */
    private static Vector3f model(Vec3 world, Vec3 origin, Matrix4f toModel) {
        return toModel.transformPosition(new Vector3f((float) (world.x - origin.x), (float) (world.y - origin.y), (float) (world.z - origin.z))).mul(16F);
    }

    /**
     * The model's turn for a body whose right, up and back point these world ways: rest body right is model -x, up -y,
     * back +z.
     */
    private static Quaternionf turn(Vector3f right, Vector3f up, Vector3f back, Matrix4f toModel) {
        Vector3f r = toModel.transformDirection(new Vector3f(right)).normalize().negate();
        Vector3f u = toModel.transformDirection(new Vector3f(up)).normalize().negate();
        Vector3f b = toModel.transformDirection(new Vector3f(back)).normalize();
        return new Quaternionf().setFromUnnormalized(new Matrix3f(r, u, b)).normalize();
    }

    /** The model's turn for a body standing upright facing a world yaw. */
    private static Quaternionf facing(float yaw, Matrix4f toModel) {
        float a = (float) Math.toRadians(yaw);
        Vector3f front = new Vector3f(-Mth.sin(a), 0, Mth.cos(a)), up = new Vector3f(0, 1, 0);
        return turn(new Vector3f(front).cross(up), up, front.negate(), toModel);
    }

    /** A host's seat (world) in model px. */
    private static SeatPose.Seat seat(RiderSeat rs, Vec3 origin, Matrix4f toModel) {
        SeatPose.Seat s = new SeatPose.Seat();
        s.kind = rs.kind();
        s.cushion.set(model(rs.cushion(), origin, toModel));
        s.turn.set(turn(rs.frame().transform(new Vector3f(1, 0, 0)), rs.frame().transform(new Vector3f(0, 1, 0)),
                rs.frame().transform(new Vector3f(0, 0, 1)), toModel));
        s.doorSide = rs.doorSide();
        s.door = rs.door();
        s.hold = rs.hold();
        s.reach = rs.reach();
        for (var e : rs.anchors().entrySet()) s.anchors.put(e.getKey(), model(e.getValue(), origin, toModel));
        return s;
    }

    /** The body yaw LivingEntityRenderer#render turns the model by (riders included). */
    private static float bodyYaw(LivingEntity e, float partial) {
        float body = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        float head = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot);
        if (e.isPassenger() && e.getVehicle() instanceof LivingEntity vehicle) {
            body = Mth.rotLerp(partial, vehicle.yBodyRotO, vehicle.yBodyRot);
            float twist = Mth.clamp(Mth.wrapDegrees(head - body), -85F, 85F);
            body = head - twist;
            if (twist * twist > 2500F) body += twist * 0.2F;
        }
        return body;
    }

    /**
     * Standing or crouching on the feet, or lying on the front out of water (ProneClient): the poses the skeleton draws
     * (the rest, swimming among them, stay vanilla's).
     */
    public static boolean supportedPose(Player p) {
        Pose pose = p.getPose();
        boolean prone = ProneClient.prone(p);
        if (pose != Pose.STANDING && pose != Pose.CROUCHING && !prone) return false;
        return (prone || !p.isVisuallySwimming()) && !p.isFallFlying() && !p.isAutoSpinAttack() && !p.isSleeping() && !p.isSpectator()
                && p.deathTime <= 0 && !LivingEntityRenderer.isEntityUpsideDown(p);
    }

    /**
     * The own first-person body's hands are the skeleton's: both hands empty, or an action the body draws in first person
     * (a gun's put-away to its mount). Otherwise the old first-person view model: a held gun keeps its own (its hip and
     * ADS hold, rigid arms and clips; user 2026-10-17, docs/dev/first_person_gun_view_v1_design.md), the body's arms and
     * the gun on them hidden; looking down at its ready the gun goes to the body's arms with the view model still drawn,
     * out of view (client/FirstPersonBody#gunOnBody), so this stays false and the hand pass runs.
     */
    public static boolean firstPersonHands(Player p) {
        // a living ragdoll (client/survivor/LiveRagdollClient): the body's arms, the item in its hand
        if (enabled() && com.antaurora.apofirstlight.client.survivor.LiveRagdollClient.active(p) && supportedPose(p)) return true;
        if (!enabled() || !supportedPose(p) || p.isScoping() || FieldAttachmentViewState.isActive()) return false;
        // crawling the hands are on the ground (the item put away); lying still the item's own view model, as standing
        if (ProneClient.prone(p) && ProneClient.stowed(p)) return true;
        // the own one once the held item's view model has slid out of view (FirstPersonActionView), the two never at once
        if (SkeletonActions.drawsFirstPerson(p) && (p != Minecraft.getInstance().player || FirstPersonActionView.bodyHands())) return true;
        // a seated hand on the vehicle's controls (the T15's key): the body's arms show it
        if (p.getVehicle() instanceof com.antaurora.apofirstlight.skeleton.seat.SeatedRiderHost host && host.handBusy(p)) return true;
        if (p.isUsingItem()) return false;
        return p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty();
    }

    /** PlayerRenderer#setupRotations: the skeleton lays this player down itself (no vanilla swim turn). */
    public static boolean drawsProne(Player p) {
        return enabled() && ProneClient.prone(p) && supportedPose(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !enabled() || !FirstPersonBody.shown(mc.gameRenderer.getMainCamera())) return;
        // an ordinary held item handed over to the body looking down (FirstPersonBody#itemOnBody): the body's hand holds it
        if (firstPersonHands(mc.player) || FirstPersonBody.itemOnBody()) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- vanilla parts

    static ModelPart base(PlayerModel<?> m, String name) {
        return switch (name) {
            case "body" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> throw new IllegalArgumentException("skin part " + name);
        };
    }

    static ModelPart layer(PlayerModel<?> m, String name) {
        return switch (name) {
            case "jacket" -> m.jacket;
            case "right_sleeve" -> m.rightSleeve;
            case "left_sleeve" -> m.leftSleeve;
            case "right_pants" -> m.rightPants;
            case "left_pants" -> m.leftPants;
            default -> throw new IllegalArgumentException("skin layer " + name);
        };
    }

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[]{m.head, m.hat, m.body, m.jacket, m.rightArm, m.rightSleeve, m.leftArm, m.leftSleeve,
                m.rightLeg, m.rightPants, m.leftLeg, m.leftPants};
    }

    private static void save(PlayerModel<?> m, float[] to) {
        ModelPart[] parts = parts(m);
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            int o = i * 6;
            to[o] = p.x; to[o + 1] = p.y; to[o + 2] = p.z; to[o + 3] = p.xRot; to[o + 4] = p.yRot; to[o + 5] = p.zRot;
        }
    }

    private static void restore(PlayerModel<?> m, float[] from) {
        ModelPart[] parts = parts(m);
        for (int i = 0; i < parts.length; i++) {
            ModelPart p = parts[i];
            int o = i * 6;
            p.x = from[o]; p.y = from[o + 1]; p.z = from[o + 2]; p.xRot = from[o + 3]; p.yRot = from[o + 4]; p.zRot = from[o + 5];
        }
    }

    /** Vanilla parts <- head, chest, upper arms, thighs; the second layer copies its base as vanilla does. */
    static void write(PlayerModel<?> m, SkeletonPose pose) {
        SkeletonRig rig = pose.rig;
        set(m.head, pose.world[rig.head], HEAD);
        set(m.body, pose.world[rig.chest], BODY);
        set(m.rightArm, pose.world[rig.upperArmR], armPivot(rig, true));
        set(m.leftArm, pose.world[rig.upperArmL], armPivot(rig, false));
        set(m.rightLeg, pose.world[rig.thighR], rig.profile != null ? BlockMeshBody.legPivot(rig, true) : RIGHT_LEG);
        set(m.leftLeg, pose.world[rig.thighL], rig.profile != null ? BlockMeshBody.legPivot(rig, false) : LEFT_LEG);
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /** The vanilla arm part's rest pivot on the rig (the Block-Mesh V2 rig's narrower, longer arms: BlockMeshBody#armPivot). */
    static Vector3f armPivot(SkeletonRig rig, boolean right) {
        if (rig.profile == null) return right ? RIGHT_ARM : LEFT_ARM;
        return BlockMeshBody.armPivot(rig, right);
    }

    /** The part's frame: the bone's skinning matrix at the part's rest pivot, as translation and Z-Y-X angles. */
    static void set(ModelPart part, Matrix4f skin, Vector3f pivot) {
        Matrix4f f = new Matrix4f(skin).translate(pivot);
        part.x = f.m30();
        part.y = f.m31();
        part.z = f.m32();
        Vector3f e = f.getEulerAnglesZYX(new Vector3f());
        part.xRot = e.x;
        part.yRot = e.y;
        part.zRot = e.z;
    }

    /** The hand's skinning matrix in model px, for anything that hangs on the hand (tools, checks). */
    public static Matrix4f handFrame(SkeletonPose pose, boolean right) {
        return new Matrix4f(pose.world[right ? pose.rig.handR : pose.rig.handL]);
    }

    static Quaternionf rotation(Matrix4f m) { return m.getUnnormalizedRotation(new Quaternionf()); }

    private static float frac(float v) { return v - (float) Math.floor(v); }
}
