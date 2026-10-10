package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.PortableDieselGeneratorBlock;
import com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity;
import com.antaurora.apofirstlight.client.ui.AflUiDraw;
import com.antaurora.apofirstlight.client.ui.AflUiShapes;
import com.antaurora.apofirstlight.client.ui.AflUiStyle;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.weapon.client.NativePlayerArmRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * The portable diesel generator's recoil start as a QTE (docs/machines/portable_diesel_generator_v1.md "拉绳"; user
 * 2026-10-09: "我其实比较喜欢那种QTE互动，也就是视角固定，然后玩家按某个键，就执行动作，不一定每次都一次就成功"), client side.
 * <ul>
 *   <li>Right-click the T handle (block/PortableDieselGeneratorBlock: the server grips it) and the view eases over
 *   {@link #BLEND_IN} ticks to a fixed, bent view of the starter (Camera mixin PortableGeneratorCameraMixin); the mouse cannot
 *   turn it, the player cannot walk; the right hand comes onto the handle and stays on it.</li>
 *   <li>A timing bar under the crosshair: a marker sweeps back and forth ({@link #PERIOD} ticks a sweep), a zone sits
 *   somewhere along it (moved after each pull). The use key pulls (network/PortableGeneratorPullC2SPacket): in the zone a
 *   good pull, else a poor one; the server decides the catch (PortableDieselGeneratorBlockEntity: cold 55 / 12 %, warm
 *   100 / 60 %). The handle flies out with the hand on it and is guided back; the next pull waits for it.</li>
 *   <li>Sneak lets go (also: the engine caught, a screen opened, the server dropped the grip); the view eases back over
 *   {@link #BLEND_OUT} ticks.</li>
 * </ul>
 * The view shakes with the action ({@link #shake}, added to the view's pitch / yaw / roll as the guns' camera bone is,
 * weapon/client/NativeCameraBoneConsumer, at their reload's size: 2-4 degrees): the yank jerks it up and over, the guided
 * return settles it with a small rebound; then the engine either turns over a few times and stops (a few small decaying
 * knocks) or catches (a decaying shudder for about a second).
 * The arm is drawn in the world (client/PortableGeneratorRenderer calls {@link #renderArm}): the guns' player arm, straight
 * from the hand toward a point low right under the view so its cut end stays off screen
 * (tools/check-portable-generator-pull-v1.mjs). Third person: the holder's right arm points at the handle (mixin
 * HumanoidModelPullMixin). Both are stand-ins until the player animation rework (docs/dev/player_animation_rework_plan_v1.md).
 * Also here: the exhaust's puffs while it runs.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PortableGeneratorPull {
    public static final int BLEND_IN = 10, BLEND_OUT = 8, PERIOD = 28;
    /** The zone's width (of the bar) and where its centre may fall. */
    public static final float ZONE = 0.18F, ZONE_MIN = 0.32F, ZONE_MAX = 0.82F;
    /** The bent eye: this far out from the grip (horizontally) and this high over it, blocks; the camera moves at most this far. */
    public static final double BEND_OUT = 0.55, BEND_UP = 0.62, BEND_MAX = 1.4;
    /** Who holds or pulls which set (entity id -> the set), seen this client. */
    private static final Map<Integer, BlockPos> HOLDERS = new HashMap<>();
    private static ClientLevel trackedLevel;
    // the local QTE
    @Nullable private static BlockPos qte;
    private static double enteredAt, leftAt = -1, resultAt = -100;
    private static float lockYaw, lockPitch, zone = 0.6F;
    private static boolean resultGood, shiftWas;

    private PortableGeneratorPull() {}

    // ---- the set's client tick: holders, the exhaust ----

    public static void tick(PortableDieselGeneratorBlockEntity generator) {
        if (!(generator.getLevel() instanceof ClientLevel level)) return;
        if (level != trackedLevel) { HOLDERS.clear(); trackedLevel = level; }
        BlockPos at = generator.getBlockPos().immutable();
        HOLDERS.values().removeIf(at::equals);
        if (generator.gripper() >= 0) HOLDERS.put(generator.gripper(), at);
        if (generator.pulling() && generator.puller() >= 0) HOLDERS.put(generator.puller(), at);
        if (generator.pullStart() >= 0) generator.clientLastPull = generator.pullStart();
        boolean before = generator.clientRunning;
        generator.clientRunning = generator.running();
        if (!generator.running()) return;
        Vec3 out = generator.exhaustWorld();
        long now = level.getGameTime();
        if (!before) {   // caught: a dark cough
            for (int i = 0; i < 6; i++) FireFx.smoke(level, out.x, out.y, out.z, 0.06F + 0.015F * i, FireFx.DIESEL_SMOKE, 0.7F, 50, 0.035 + 0.008 * i);
            generator.clientPuff = now;
            return;
        }
        if (now - generator.clientPuff < 3) return;
        generator.clientPuff = now;
        float load = generator.loadFe() / PortableDieselGeneratorBlockEntity.RATED;
        FireFx.smoke(level, out.x, out.y, out.z, 0.05F, 0.3F, 0.18F + 0.22F * Math.min(1F, load), 45, 0.05);
    }

    // ---- the local QTE ----

    /** The block's use took the handle (client side): start the QTE. */
    public static void enter(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        qte = pos.immutable();
        enteredAt = time(0);
        leftAt = -1;
        lockYaw = mc.player.getYRot();
        lockPitch = mc.player.getXRot();
        zone = nextZone();
        shiftWas = true;   // the sneak key must be pressed anew to let go
        resultAt = -100;
    }

    private static float nextZone() {
        return ZONE_MIN + (ZONE_MAX - ZONE_MIN) * (float) Math.random();
    }

    private static double time(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + partialTick;
    }

    /** The QTE is taking input (not easing out). */
    private static boolean holding() {
        return qte != null && leftAt < 0;
    }

    private static void leave(boolean tell) {
        if (!holding()) return;
        if (tell) AflNetwork.requestPortablePull(qte, false, false);
        leftAt = time(0);
    }

    @Nullable
    private static PortableDieselGeneratorBlockEntity qteSet() {
        Minecraft mc = Minecraft.getInstance();
        return qte != null && mc.level != null && mc.level.getBlockEntity(qte) instanceof PortableDieselGeneratorBlockEntity g ? g : null;
    }

    /** How far the view has gone over: 0 the player's own .. 1 the fixed QTE view. */
    private static double blend(float partialTick) {
        if (qte == null) return 0;
        double now = time(partialTick), in = smooth((now - enteredAt) / BLEND_IN);
        return leftAt < 0 ? in : Math.min(in, 1 - smooth((now - leftAt) / BLEND_OUT));
    }

    /** The marker's place on the bar (0..1), sweeping back and forth. */
    private static float marker(float partialTick) {
        double phase = ((time(partialTick) - enteredAt) % PERIOD + PERIOD) % PERIOD / PERIOD;
        return (float) (phase < 0.5 ? phase * 2 : 2 - phase * 2);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || qte == null) return;
        Minecraft mc = Minecraft.getInstance();
        PortableDieselGeneratorBlockEntity g = qteSet();
        if (mc.player == null || g == null) { qte = null; return; }
        if (!holding()) { if (time(0) - leftAt > BLEND_OUT) qte = null; return; }
        boolean shift = mc.options.keyShift.isDown();
        boolean dropped = time(0) - enteredAt > 10 && g.gripper() != mc.player.getId() && !g.pulling();
        if (shift && !shiftWas || mc.screen != null || !mc.player.isAlive()) leave(true);
        else if (g.running() && !g.pulling() || dropped) leave(false);   // it caught (the server let go), or the grip ended there
        shiftWas = shift;
    }

    /** The use key pulls; every other click is swallowed while the handle is held. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (!holding()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        PortableDieselGeneratorBlockEntity g = qteSet();
        if (!event.isUseItem() || g == null || g.pulling() || g.running() || time(0) - enteredAt < BLEND_IN) return;
        float m = marker(0);
        resultGood = Math.abs(m - zone) <= ZONE / 2;
        resultAt = time(0);
        AflNetwork.requestPortablePull(qte, true, resultGood);
        zone = nextZone();
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        if (!holding()) return;
        var i = event.getInput();
        i.forwardImpulse = 0; i.leftImpulse = 0;
        i.up = i.down = i.left = i.right = i.jumping = false;
        i.shiftKeyDown = false;   // sneak lets go of the handle instead (clientTick)
    }

    /** The mouse cannot turn the held view (restored before the frame, as client/JerryCanPourView does). */
    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || qte == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.setYRot(lockYaw); mc.player.setXRot(lockPitch);
        mc.player.yRotO = lockYaw; mc.player.xRotO = lockPitch;
        mc.player.yHeadRot = lockYaw; mc.player.yHeadRotO = lockYaw;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideHands(RenderHandEvent event) {
        if (qte != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void outline(RenderHighlightEvent.Block event) {
        if (qte != null) event.setCanceled(true);
    }

    /** Whether the world hints should keep quiet (the QTE draws its own). */
    public static boolean active() {
        return qte != null;
    }

    // ---- the timing bar ----

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) || !holding()) return;
        Minecraft mc = Minecraft.getInstance();
        PortableDieselGeneratorBlockEntity g = qteSet();
        if (g == null || mc.options.hideGui) return;
        float partial = event.getPartialTick(), alpha = (float) blend(partial);
        if (alpha <= 0.02F) return;
        var gr = event.getGuiGraphics();
        float W = 150, H = 7, cx = event.getWindow().getGuiScaledWidth() / 2F, y = event.getWindow().getGuiScaledHeight() / 2F + 34;
        boolean busy = g.pulling() || g.running();
        float a = alpha * (busy ? 0.55F : 1F);
        AflUiDraw.panel(gr, cx - W / 2 - 6, y - 6, W + 12, H + 26, a);
        AflUiShapes.fill(gr, cx - W / 2, y, W, H, 2.5F, 0x2A2C2F, 0.9F * a);
        AflUiShapes.fill(gr, cx - W / 2 + (zone - ZONE / 2) * W, y, ZONE * W, H, 2.5F, 0x7FB061, 0.85F * a);
        float m = marker(partial);
        double since = time(partial) - resultAt;
        int markRgb = since < 10 ? (resultGood ? 0xB6F08E : 0xE07A5F) : 0xF2EEE6;
        AflUiShapes.fill(gr, cx - W / 2 + m * W - 1.5F, y - 3, 3, H + 6, 1.5F, markRgb, a);
        Component keys = Component.translatable("hint.apocalypse_firstlight.portable_diesel_generator.qte",
                mc.options.keyUse.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage());
        AflUiDraw.centred(gr, mc.font, keys, cx, y + H + 6, AflUiStyle.TEXT_DIM, a);
    }

    // ---- the camera (mixin client.PortableGeneratorCameraMixin) ----

    /** The held view for this frame, or null: [position, yaw, pitch], looking at the starter. */
    @Nullable
    public static Object[] camera(Camera camera, float partialTick) {
        PortableDieselGeneratorBlockEntity g = qteSet();
        Minecraft mc = Minecraft.getInstance();
        if (g == null || mc.player == null) return null;
        double k = blend(partialTick);
        if (k <= 0) return null;
        Vec3 eye = mc.player.getEyePosition(partialTick), rest = PortableDieselGeneratorBlock.handle(g.getBlockPos(), g.getBlockState(), 0);
        Vec3 flat = new Vec3(eye.x - rest.x, 0, eye.z - rest.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 target = rest.add(flat.scale(BEND_OUT)).add(0, BEND_UP, 0), offset = target.subtract(eye);
        if (offset.length() > BEND_MAX) offset = offset.normalize().scale(BEND_MAX);
        // the body's give in a yank: the view lifts a little and settles
        double age = g.pullAge(partialTick), kick = age >= 0 && age < PortableDieselGeneratorBlockEntity.RELEASE_AT ? Math.sin(Math.PI * age / PortableDieselGeneratorBlockEntity.RELEASE_AT) * 0.035 : 0;
        Vec3 at = eye.add(offset.scale(k)).add(0, kick * k, 0);
        Vec3 look = rest.add(0, 0.04, 0).subtract(at);
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG), pitch = (float) (-Mth.atan2(look.y, Math.hypot(look.x, look.z)) * Mth.RAD_TO_DEG);
        return new Object[]{at, Mth.rotLerp((float) k, camera.getYRot(), yaw), Mth.lerp((float) k, camera.getXRot(), pitch)};
    }

    // ---- the shake (degrees: pitch up is negative) ----

    /** A pulse rising to 1 at tau ticks and dying away (t in ticks; 0 before it starts). */
    private static double pulse(double t, double tau) {
        return t <= 0 ? 0 : t / tau * Math.exp(1 - t / tau);
    }

    /** The view's shake at this age of the pull: {pitch, yaw, roll}; running: the engine caught. */
    static double[] shake(double age, boolean running) {
        if (age < 0) return new double[3];
        double pitch = -2.4 * pulse(age, 2.5) + 0.9 * pulse(age - PortableDieselGeneratorBlockEntity.RELEASE_AT, 2.5);
        double yaw = -0.7 * pulse(age, 3.0);
        double roll = 1.8 * pulse(age - 0.5, 3.0) - 0.6 * pulse(age - PortableDieselGeneratorBlockEntity.RELEASE_AT - 1, 3.0);
        double after = age - PortableDieselGeneratorBlockEntity.CATCH_AT, s = age / 20.0;
        if (after > 0 && running) {   // it caught: a shudder, about a second
            double a = 0.35 * Math.exp(-after / 14);
            pitch += a * Math.sin(2 * Math.PI * 13 * s); roll += 0.7 * a * Math.sin(2 * Math.PI * 11 * s + 1.3); yaw += 0.4 * a * Math.sin(2 * Math.PI * 7 * s + 0.6);
        } else if (age > PortableDieselGeneratorBlockEntity.RELEASE_AT && !running) {   // turned over and stopping: a few small knocks
            double a = 0.18 * Math.exp(-(age - PortableDieselGeneratorBlockEntity.RELEASE_AT) / 10);
            pitch += a * Math.sin(2 * Math.PI * 8 * s); roll += 0.6 * a * Math.sin(2 * Math.PI * 6 * s + 0.9);
        }
        return new double[]{pitch, yaw, roll};
    }

    @SubscribeEvent
    public static void cameraShake(ViewportEvent.ComputeCameraAngles event) {
        PortableDieselGeneratorBlockEntity g = qteSet();
        Minecraft mc = Minecraft.getInstance();
        if (g == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        float partial = (float) event.getPartialTick();
        double k = blend(partial);
        // the shake follows the pull (and the catch's shudder, which outlasts the pull: it runs from the pull's start, kept
        // on this side after the server's reset)
        long start = g.pullStart() >= 0 ? g.pullStart() : g.clientLastPull;
        double age = start == Long.MIN_VALUE ? -1 : mc.level.getGameTime() - start + partial;
        if (k <= 0 || age < 0 || age > 60) return;
        double[] d = shake(age, g.running());
        event.setPitch((float) (event.getPitch() + d[0] * k));
        event.setYaw((float) (event.getYaw() + d[1] * k));
        event.setRoll((float) (event.getRoll() + d[2] * k));
    }

    // ---- first person: the arm in the world ----

    private static double smooth(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** The grip now (world): the mesh channel's own sample, so the hand and the handle agree. */
    private static Vec3 grip(PortableDieselGeneratorBlockEntity g, float partialTick) {
        double v = g.meshAnimation().sample("pull", g.getLevel().getGameTime() + partialTick);
        return PortableDieselGeneratorBlock.handle(g.getBlockPos(), g.getBlockState(), v);
    }

    /** Called by the set's renderer (pose at the block's origin): the local holder's right arm on the handle. */
    public static void renderArm(PortableDieselGeneratorBlockEntity g, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (qte == null || !qte.equals(g.getBlockPos()) || !mc.options.getCameraType().isFirstPerson()) return;
        double k = blend(partialTick);
        if (k <= 0) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition(), fwd = new Vec3(camera.getLookVector()), up = new Vec3(camera.getUpVector()), right = new Vec3(camera.getLeftVector()).scale(-1);
        Vec3 under = cam.add(fwd.scale(0.30)).add(up.scale(-0.62)).add(right.scale(0.26));   // out of view, low right
        Vec3 hand = under.add(grip(g, partialTick).subtract(under).scale(k));
        // not the true shoulder: a point low right under the view, so the 0.585 m arm leaves the screen before it ends (a
        // shoulder 0.3 m under the eye left its cut end in view: tools/check-portable-generator-pull-v1.mjs)
        Vec3 shoulder = cam.add(right.scale(0.30)).add(up.scale(-0.60)).add(fwd.scale(-0.05));
        Vec3 y = hand.subtract(shoulder);
        if (y.lengthSqr() < 1e-6) return;
        y = y.normalize();   // from the shoulder to the hand: the canonical arm runs down -Y from its hand cap
        Vec3 z = hand.subtract(cam);   // the palm away from the eye, onto the handle
        z = z.subtract(y.scale(z.dot(y)));
        if (z.lengthSqr() < 1e-6) z = new Vec3(0, -1, 0).subtract(y.scale(-y.y));
        z = z.normalize();
        Vec3 x = y.cross(z), cap = hand.add(y.scale(0.035));   // the fingers' end a little past the grip
        BlockPos origin = g.getBlockPos();
        pose.pushPose();
        pose.translate(cap.x - origin.getX(), cap.y - origin.getY(), cap.z - origin.getZ());
        Matrix3f basis = new Matrix3f((float) x.x, (float) x.y, (float) x.z, (float) y.x, (float) y.y, (float) y.z, (float) z.x, (float) z.y, (float) z.z);
        pose.mulPose(new Quaternionf().setFromNormalized(basis));
        int light = LevelRenderer.getLightColor(g.getLevel(), BlockPos.containing(hand));
        NativePlayerArmRenderer.render(pose, true, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    // ---- third person (mixin client.HumanoidModelPullMixin) ----

    @Nullable
    private static PortableDieselGeneratorBlockEntity setOf(Player player) {
        BlockPos pos = HOLDERS.get(player.getId());
        if (pos == null || player.level() != trackedLevel) return null;
        return player.level().getBlockEntity(pos) instanceof PortableDieselGeneratorBlockEntity g
                && (g.gripper() == player.getId() || g.pulling() && g.puller() == player.getId()) ? g : null;
    }

    /** The holder's right arm at the handle: straight from the shoulder toward the grip. */
    public static void poseArm(HumanoidModel<?> model, LivingEntity entity, float ageInTicks) {
        if (!(entity instanceof Player player)) return;
        PortableDieselGeneratorBlockEntity g = setOf(player);
        if (g == null) return;
        float partial = Mth.clamp(ageInTicks - player.tickCount, 0, 1);
        float body = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(body), 0, Mth.cos(body)), rightSide = new Vec3(-Mth.cos(body), 0, -Mth.sin(body));
        Vec3 shoulder = player.getPosition(partial).add(0, 1.29, 0).add(rightSide.scale(0.29));
        Vec3 d = grip(g, partial).subtract(shoulder);
        if (d.lengthSqr() < 1e-6) return;
        d = d.normalize();
        // model space: right = -x, down = +y, forward = -z; the arm hangs along +y, turned X then Y (ModelPart's ZYX order)
        double tx = -d.dot(rightSide), ty = -d.y, tz = -d.dot(forward);
        float s = (float) Math.sqrt(Math.max(0, 1 - ty * ty));
        model.rightArm.xRot = (float) -Math.acos(Mth.clamp(ty, -1, 1));
        model.rightArm.yRot = s < 1e-4F ? 0 : (float) Math.atan2(-tx / s, -tz / s);
        model.rightArm.zRot = 0;
    }
}
