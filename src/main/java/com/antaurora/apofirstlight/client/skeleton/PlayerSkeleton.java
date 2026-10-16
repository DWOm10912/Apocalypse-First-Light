package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBody;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.RifleStance;
import com.antaurora.apofirstlight.skeleton.SkeletonClips;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
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
 * ({@link SkeletonGunStance}) is placed at real size against the eye and both hands reach for its anchors by IK.</li>
 * </ul>
 * First person: when the hands are supported (empty, or the BR51 with the off hand empty) the vanilla hand pass is
 * cancelled, so looking ahead shows no hands; otherwise the old first-person view model stays and the body's arms are
 * hidden, as in the first-person body V1.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PlayerSkeleton {
    /** Waist twist (deg) that starts a step turn while standing; the waist's hard limit is 45. */
    private static final float TURN_TRIGGER = 40F, WAIST = 45F, CHEST_LAG = 15F;
    /** Vanilla part pivots (model px), the frames the write-back reproduces. */
    private static final Vector3f HEAD = new Vector3f(0, 0, 0), BODY = new Vector3f(0, 0, 0),
            RIGHT_ARM = new Vector3f(-5, 2, 0), LEFT_ARM = new Vector3f(5, 2, 0),
            RIGHT_LEG = new Vector3f(-1.9F, 12, 0), LEFT_LEG = new Vector3f(1.9F, 12, 0);

    private static HumanoidPoser poser;
    private static boolean failed;
    private static long frame;

    private PlayerSkeleton() {
    }

    private static HumanoidPoser poser() {
        if (poser == null && !failed) {
            try {
                poser = new HumanoidPoser(SkeletonRig.get(), SkeletonClips.locomotion());
            } catch (RuntimeException e) {
                failed = true;
                ApocalypseFirstLight.LOGGER.error("[AFL skeleton] rig or clips failed to load, the skeleton stays off", e);
            }
        }
        return poser;
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
            boolean rifle = SkeletonGunStance.holds(p);
            float target = head + (rifle ? SkeletonGunStance.CHEST_TWIST : 0);
            if (rifle) chest = target;
            else {
                chest += Mth.wrapDegrees(target - chest) * 0.35F;
                float lag = Mth.wrapDegrees(target - chest);
                if (Math.abs(lag) > CHEST_LAG) chest = target - Math.signum(lag) * CHEST_LAG;
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
            return;
        }
        if (mc.isPaused()) return;
        SkeletonGunStance.tick(); // the own aim goes out even with the skeleton off here: the others may draw it
        if (!enabled()) {
            STATES.clear();
            CACHE.clear();
            return;
        }
        for (AbstractClientPlayer p : mc.level.players()) STATES.computeIfAbsent(p, k -> new State()).tick(p);
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frame++;
    }

    // ---------------------------------------------------------------- per draw

    /** One player's solved frame: the pose, and the gun's frame in model space (blocks) when the BR51 is held. */
    private static final class Solved {
        long frame;
        final SkeletonPose pose;
        boolean slim;
        Matrix4f gun;
        ItemStack gunStack = ItemStack.EMPTY;

        Solved(SkeletonRig rig) {
            pose = new SkeletonPose(rig);
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
    }

    /** LivingEntityRenderer#render RETURN: vanilla's own part values back for whoever draws the shared model next. */
    public static void end(LivingEntity entity) {
        Draw d = DRAWS.peek();
        if (d == null || d.player != entity) return;
        DRAWS.pop();
        if (d.solved != null && d.model != null) restore(d.model, d.saved);
    }

    /** LivingEntityRenderer#render, after setupAnim: solve (or reuse) the pose and write the vanilla parts. */
    public static void afterSetupAnim(LivingEntity entity, EntityModel<?> model, PoseStack pose) {
        Draw d = DRAWS.peek();
        if (d == null || d.player != entity || !(model instanceof PlayerModel<?> playerModel) || !supportedPose(d.player)) return;
        Matrix4f toModel = new Matrix4f(pose.last().pose()).invert().mul(d.head); // render-origin blocks -> model blocks
        Solved s = d.world ? CACHE.get(d.player) : null;
        if (s == null || s.frame != frame || !d.world) {
            if (s == null) s = new Solved(poser().rig());
            try {
                solve(d, playerModel, toModel, s);
            } catch (RuntimeException e) {
                failed = true;
                poser = null;
                ApocalypseFirstLight.LOGGER.error("[AFL skeleton] pose failed, the skeleton turns off", e);
                return;
            }
            s.frame = frame;
            if (d.world) CACHE.put(d.player, s);
        }
        d.model = playerModel;
        save(playerModel, d.saved);
        write(playerModel, s.pose);
        d.solved = s;
    }

    /** In place of the model's renderToBuffer: true when the skeleton drew the body. */
    public static boolean draw(EntityModel<?> model, PoseStack pose, VertexConsumer consumer, int light, int overlay,
                               float r, float g, float b, float a) {
        Draw d = DRAWS.peek();
        if (d == null || d.solved == null || d.model != model) return false;
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
        for (SkeletonRig.SkinPart part : rig.skin) {
            if (base(m, part.part()).visible) SkinMesh.emit(d.solved.pose, part, d.solved.slim, false, sink);
            if (layer(m, part.overlay()).visible) SkinMesh.emit(d.solved.pose, part, d.solved.slim, true, sink);
        }
        m.head.render(pose, consumer, light, overlay, r, g, b, a);
        m.hat.render(pose, consumer, light, overlay, r, g, b, a);
        if (d.solved.gun != null && !AflShaderCompat.activeShadowPass())
            SkeletonGunStance.render(d.player, d.solved.gunStack, d.solved.gun, pose, d.buffers, light,
                    d.world && d.player == Minecraft.getInstance().player && FirstPersonBody.hidingParts());
        return true;
    }

    /** ItemInHandLayer: the BR51 the skeleton draws itself is not drawn again in the vanilla hand. */
    public static boolean ownsHeldItem(LivingEntity entity, ItemStack stack) {
        Draw d = DRAWS.peek();
        return d != null && d.player == entity && d.solved != null && d.solved.gun != null && stack == d.solved.gunStack;
    }

    private static void solve(Draw d, PlayerModel<?> model, Matrix4f toModel, Solved out) {
        AbstractClientPlayer p = d.player;
        float partial = d.partial;
        State s = d.world ? STATES.get(p) : null;
        HumanoidPoser.Input in = new HumanoidPoser.Input();
        in.slim = "slim".equals(p.getModelName());
        out.slim = in.slim;
        float body = bodyYaw(p, partial);
        float head = Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot);
        float pitch = Mth.lerp(partial, p.xRotO, p.getXRot());
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
        boolean rifle = SkeletonGunStance.holds(p);
        float pelvis = s != null ? Mth.rotLerp(partial, s.pelvisO, s.pelvis) : body;
        float chest = s != null ? Mth.rotLerp(partial, s.chestO, s.chest) : head + (rifle ? SkeletonGunStance.CHEST_TWIST : 0);
        in.rootYaw = Mth.wrapDegrees(pelvis - body);
        in.chestYaw = Mth.clamp(Mth.wrapDegrees(chest - pelvis), -WAIST, WAIST);
        in.viewYaw = Mth.wrapDegrees(head - body);
        in.viewPitch = pitch;
        in.chestPitch = HumanoidPoser.chestPitch(pitch, rifle);
        // the eye: the camera's own position for the own first-person body, the entity's eye height otherwise
        Vec3 eye;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        boolean firstPerson = d.world && p == mc.player && !camera.isDetached();
        Vec3 offset = mc.getEntityRenderDispatcher().getRenderer(p).getRenderOffset(p, partial);
        if (firstPerson) {
            Vec3 origin = new Vec3(Mth.lerp(partial, p.xOld, p.getX()), Mth.lerp(partial, p.yOld, p.getY()),
                    Mth.lerp(partial, p.zOld, p.getZ())).add(offset);
            eye = camera.getPosition().subtract(origin);
        } else {
            float eyeHeight = s != null ? Mth.lerp(partial, s.eyeHeightO, s.eyeHeight) : p.getEyeHeight();
            eye = new Vec3(0, eyeHeight - offset.y, 0);
        }
        Vector3f eyeModel = toModel.transformPosition(new Vector3f((float) eye.x, (float) eye.y, (float) eye.z)).mul(16F);
        in.eyeTarget = eyeModel;
        // arms
        in.vanillaRight.rotationZYX(model.rightArm.zRot, model.rightArm.yRot, model.rightArm.xRot);
        in.vanillaLeft.rotationZYX(model.leftArm.zRot, model.leftArm.yRot, model.leftArm.xRot);
        out.gun = null;
        out.gunStack = ItemStack.EMPTY;
        if (rifle) {
            float viewYaw = firstPerson ? camera.getYRot() : head, viewPitch = firstPerson ? camera.getXRot() : pitch;
            Matrix4f gun = SkeletonGunStance.place(p, eye, viewYaw, viewPitch, partial, firstPerson);
            if (gun != null) {
                out.gun = new Matrix4f(toModel).mul(gun);
                out.gunStack = p.getMainHandItem();
                in.right = in.left = HumanoidPoser.ArmMode.IK;
                SkeletonGunStance.handTarget(out.gunStack, out.gun, true, in.slim, in.targetRight);
                SkeletonGunStance.handTarget(out.gunStack, out.gun, false, in.slim, in.targetLeft);
                in.poleLeft.set(0.15F, 1F, 0.25F);
                SkeletonGunStance.supportSlide(out.gunStack, out.gun, in.slideLeft);
                in.shoulderRight.set(RifleStance.STOCK_SHOULDER);
            }
        }
        if (out.gun == null) {
            boolean rightMain = p.getMainArm() == HumanoidArm.RIGHT;
            ItemStack rightStack = rightMain ? p.getMainHandItem() : p.getOffhandItem();
            ItemStack leftStack = rightMain ? p.getOffhandItem() : p.getMainHandItem();
            boolean using = p.isUsingItem();
            in.right = !rightStack.isEmpty() || using ? HumanoidPoser.ArmMode.VANILLA : HumanoidPoser.ArmMode.CLIP;
            in.left = !leftStack.isEmpty() || using ? HumanoidPoser.ArmMode.VANILLA : HumanoidPoser.ArmMode.CLIP;
            if (model.attackTime > 0F) {
                // a punch: the swinging arm follows vanilla's swing over the clip
                boolean swingRight = (p.swingingArm == InteractionHand.MAIN_HAND) == rightMain;
                float w = Mth.sin(model.attackTime * Mth.PI);
                if (swingRight) in.vanillaRightWeight = w;
                else in.vanillaLeftWeight = w;
            }
        }
        if (p.isPassenger() && p.getVehicle() != null && p.getVehicle().shouldRiderSit()) {
            in.legsVanilla = true;
            in.vanillaRightLeg.rotationZYX(model.rightLeg.zRot, model.rightLeg.yRot, model.rightLeg.xRot);
            in.vanillaLeftLeg.rotationZYX(model.leftLeg.zRot, model.leftLeg.yRot, model.leftLeg.xRot);
        }
        poser().solve(in, out.pose);
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

    /** Standing or crouching on the feet: the poses the skeleton draws (the rest stay vanilla's). */
    public static boolean supportedPose(Player p) {
        Pose pose = p.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING) return false;
        return !p.isVisuallySwimming() && !p.isFallFlying() && !p.isAutoSpinAttack() && !p.isSleeping() && !p.isSpectator()
                && p.deathTime <= 0 && !LivingEntityRenderer.isEntityUpsideDown(p);
    }

    /**
     * The own first-person body's hands are the skeleton's: both hands empty, or the BR51 alone (not while one of its
     * actions plays: reload, draw, inspect keep the old first-person rig in P1). Otherwise the old first-person view model.
     */
    public static boolean firstPersonHands(Player p) {
        if (!enabled() || !supportedPose(p) || p.isUsingItem() || p.isScoping() || FieldAttachmentViewState.isActive()) return false;
        return p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty() || SkeletonGunStance.holds(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !enabled() || !FirstPersonBody.shown(mc.gameRenderer.getMainCamera())) return;
        if (firstPersonHands(mc.player)) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- vanilla parts

    private static ModelPart base(PlayerModel<?> m, String name) {
        return switch (name) {
            case "body" -> m.body;
            case "right_arm" -> m.rightArm;
            case "left_arm" -> m.leftArm;
            case "right_leg" -> m.rightLeg;
            case "left_leg" -> m.leftLeg;
            default -> throw new IllegalArgumentException("skin part " + name);
        };
    }

    private static ModelPart layer(PlayerModel<?> m, String name) {
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
    private static void write(PlayerModel<?> m, SkeletonPose pose) {
        SkeletonRig rig = pose.rig;
        set(m.head, pose.world[rig.head], HEAD);
        set(m.body, pose.world[rig.chest], BODY);
        set(m.rightArm, pose.world[rig.upperArmR], RIGHT_ARM);
        set(m.leftArm, pose.world[rig.upperArmL], LEFT_ARM);
        set(m.rightLeg, pose.world[rig.thighR], RIGHT_LEG);
        set(m.leftLeg, pose.world[rig.thighL], LEFT_LEG);
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightPants.copyFrom(m.rightLeg);
        m.leftPants.copyFrom(m.leftLeg);
    }

    /** The part's frame: the bone's skinning matrix at the part's rest pivot, as translation and Z-Y-X angles. */
    private static void set(ModelPart part, Matrix4f skin, Vector3f pivot) {
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
