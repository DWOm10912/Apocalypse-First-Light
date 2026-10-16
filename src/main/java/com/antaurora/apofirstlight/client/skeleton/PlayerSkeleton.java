package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBody;
import com.antaurora.apofirstlight.client.FirstPersonBodyConfig;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonClips;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.SkinMesh;
import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerProps;
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
 * ({@link SkeletonActions}: thermometer, recoil starter, fuel nozzle) poses the body and the hands it names;</li>
 * <li>held items: drawn on the hand bone (ItemInHandLayer's translateToHand, {@link #translateToHand}), so they follow a
 * bent elbow.</li>
 * </ul>
 * First person: when the hands are supported (empty, the BR51 with the off hand empty, or a player action) the vanilla
 * hand pass is cancelled, so looking ahead shows no hands; otherwise the old first-person view model stays and the body's
 * arms are hidden, as in the first-person body V1.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PlayerSkeleton {
    /** Waist twist (deg) that starts a step turn while standing; the waist's hard limit is 45. */
    private static final float TURN_TRIGGER = 40F, WAIST = 45F, CHEST_LAG = 15F;
    /** Vanilla part pivots (model px), the frames the write-back reproduces. */
    private static final Vector3f HEAD = new Vector3f(0, 0, 0), BODY = new Vector3f(0, 0, 0),
            RIGHT_ARM = new Vector3f(-5, 2, 0), LEFT_ARM = new Vector3f(5, 2, 0),
            RIGHT_LEG = new Vector3f(-1.9F, 12, 0), LEFT_LEG = new Vector3f(1.9F, 12, 0);

    private static final ModelPart HAND = new ModelPart(List.of(), Map.of());
    private static HumanoidPoser poser;
    private static boolean failed;
    private static long frame;

    private PlayerSkeleton() {
    }

    static HumanoidPoser poser() {
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
            boolean gun = SkeletonGunStance.busy(p);
            float target = head + SkeletonGunStance.chestTwist(p, 1F);
            if (gun) chest = target;
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
        boolean propOpen;

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
        d.model = playerModel;
        save(playerModel, d.saved);   // before the solve, which writes the parts for the gun's mounts
        Solved s = d.world ? CACHE.get(d.player) : null;
        if (s == null || s.frame != frame || !d.world) {
            if (s == null) s = new Solved(poser().rig());
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
        if (d.solved.hold != null && !AflShaderCompat.activeShadowPass())
            SkeletonGunStance.render(d.solved.hold, pose, d.buffers, light,
                    d.world && d.player == Minecraft.getInstance().player && FirstPersonBody.hidingParts());
        return true;
    }

    /**
     * ItemInHandLayer: the gun the skeleton draws itself is not drawn again in the vanilla hand, nor is the item that
     * replaced it while the old gun is still on its way back to its mount.
     */
    public static boolean ownsHeldItem(LivingEntity entity, ItemStack stack) {
        Draw d = DRAWS.peek();
        return d != null && d.player == entity && d.solved != null && d.solved.hold != null
                && (stack == d.solved.hold.stack() || stack == d.player.getMainHandItem());
    }

    /**
     * ItemInHandLayer#renderArmWithItem HEAD: the held item an action holds as its prop (player actions S2) is drawn where
     * the action placed it, not in the hand. True: drawn (the vanilla hand item is skipped).
     */
    public static boolean drawProp(LivingEntity entity, ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
        Draw d = DRAWS.peek();
        if (d == null || d.player != entity || d.solved == null || d.solved.prop == null || stack != d.player.getMainHandItem()) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !ApocalypseFirstLight.MOD_ID.equals(id.getNamespace()) || !d.solved.prop.holds(id.getPath())) return false;
        pose.pushPose();
        pose.scale(1F / 16F, 1F / 16F, 1F / 16F);   // model px
        Matrix4f place = new Matrix4f(d.solved.propPose).mul(d.solved.prop.itemToLocal());   // item block units
        pose.last().pose().mul(place);
        Matrix3f normal = new Matrix3f().set(place).invert().transpose();   // mulPoseMatrix would leave the normals unturned
        normal.scale(1F / (float) Math.cbrt(Math.abs(normal.determinant())));
        pose.last().normal().mul(normal);
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.level.block.state.BlockState open = d.solved.propOpen ? openState(d.solved.prop) : null;
        if (open != null) {
            // the block in its open state (the same geometry, block units as the item's): the jerry can's cap swung off
            mc.getBlockRenderer().renderSingleBlock(open, pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                    net.minecraftforge.client.model.data.ModelData.EMPTY, null);
        } else {
            pose.translate(0.5F, 0.5F, 0.5F);   // ItemRenderer centres the model on 0
            mc.getItemRenderer().renderStatic(d.player, stack, net.minecraft.world.item.ItemDisplayContext.NONE, false, pose, buffers,
                    d.player.level(), light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, d.player.getId());
        }
        pose.popPose();
        return true;
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
        set(HAND, d.solved.pose.world[right ? d.solved.pose.rig.handR : d.solved.pose.rig.handL], right ? RIGHT_ARM : LEFT_ARM);
        if (d.solved.slim) HAND.x += right ? 0.5F : -0.5F;
        HAND.translateAndRotate(pose);
        return true;
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
        boolean gun = SkeletonGunStance.busy(p);
        float pelvis = s != null ? Mth.rotLerp(partial, s.pelvisO, s.pelvis) : body;
        float chest = s != null ? Mth.rotLerp(partial, s.chestO, s.chest) : head + SkeletonGunStance.chestTwist(p, partial);
        in.rootYaw = Mth.wrapDegrees(pelvis - body);
        in.chestYaw = Mth.clamp(Mth.wrapDegrees(chest - pelvis), -WAIST, WAIST);
        in.viewYaw = Mth.wrapDegrees(head - body);
        in.viewPitch = pitch;
        in.chestPitch = HumanoidPoser.chestPitch(pitch, SkeletonGunStance.gunUp(p, partial));
        // the eye: the camera's own position for the own first-person body, the entity's eye height otherwise
        Vec3 eye;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        boolean firstPerson = d.world && p == mc.player && !camera.isDetached();
        Vec3 offset = mc.getEntityRenderDispatcher().getRenderer(p).getRenderOffset(p, partial);
        Vec3 origin = new Vec3(Mth.lerp(partial, p.xOld, p.getX()), Mth.lerp(partial, p.yOld, p.getY()),
                Mth.lerp(partial, p.zOld, p.getZ())).add(offset);
        if (firstPerson) {
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
        if (p.isPassenger() && p.getVehicle() != null && p.getVehicle().shouldRiderSit()) {
            in.legsVanilla = true;
            in.vanillaRightLeg.rotationZYX(model.rightLeg.zRot, model.rightLeg.yRot, model.rightLeg.xRot);
            in.vanillaLeftLeg.rotationZYX(model.leftLeg.zRot, model.leftLeg.yRot, model.leftLeg.xRot);
        }
        // a player action (not with a gun up: the gun has both hands): its clips, step and facing before the body
        ActionPose.Moment action = gun ? null : SkeletonActions.moment(p, partial, origin, toModel, d.world);
        // the own first-person body leaves an action that keeps its view model alone (the view model is the hands)
        if (action != null && firstPerson && action.action.firstPerson == PlayerAction.FirstPerson.VIEW_MODEL) action = null;
        if (action != null && !ActionPose.body(in, poser().rig(), action)) action = null;
        if (action != null && d.world) SkeletonActions.stepped(p, in.rootOffset, toModel);
        HumanoidPoser.Report report = poser().solveBody(in, out.pose);
        out.hold = null;
        out.prop = null;
        if (gun) {
            // the gun hangs on the posed body (its mounts) or the eye; the arms reach for it
            write(model, out.pose);
            float viewYaw = firstPerson ? camera.getYRot() : head, viewPitch = firstPerson ? camera.getXRot() : pitch;
            out.hold = SkeletonGunStance.hold(p, model, out.pose, in, eye, viewYaw, viewPitch, partial, firstPerson, toModel);
        }
        if (out.hold == null) {
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
            if (action != null) {
                ActionPose.hands(in, out.pose, poser(), action);
                if (action.prop != null) {
                    out.prop = PlayerProps.get(action.action.prop);
                    out.propPose.set(action.prop);
                    out.propOpen = action.propOpen;
                }
            }
        }
        poser().solveArms(in, out.pose, report);
        if (d.world) SkeletonActions.solved(p, out.pose, action, origin, toModel);
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
        if (!enabled() || !supportedPose(p) || p.isScoping() || FieldAttachmentViewState.isActive()) return false;
        if (SkeletonActions.drawsFirstPerson(p)) return true;
        if (p.isUsingItem()) return false;
        return p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty() || SkeletonGunStance.busy(p);
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
