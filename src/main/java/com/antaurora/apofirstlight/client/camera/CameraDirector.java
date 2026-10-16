package com.antaurora.apofirstlight.client.camera;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Camera director V1 (docs/dev/camera_director_v1_plan.md 2): the one place that moves the client's camera for a
 * presentation, and the shake, body offset, input lock and HUD that go with it.
 * <ul>
 *   <li><b>Shots</b> (2.2, 2.3): open {@link Request}s, each a {@link Shot} with a priority and a fade
 *   ({@link CameraFade}, on its {@link Clock}). Each frame, from vanilla's camera up the priorities, every shot's pose is
 *   blended in by its weight (mixin client.CameraDirectorMixin, Camera#setup TAIL); its roll and field of view are eased
 *   the same way (ComputeCameraAngles, ComputeFov).</li>
 *   <li><b>Shake</b> (2.5): the sources in CameraSources#SHAKES, summed and put on the view once a frame, times
 *   {@link #shakeScale}.</li>
 *   <li><b>Body offset</b> (2.6): where a player's body is drawn away from the entity (CameraSources#BODY_OFFSETS), for
 *   the shadow (mixin client.EntityRenderDispatcherShadowMixin) and later the name tag.</li>
 *   <li><b>Input lock</b> (2.7) and <b>HUD</b> (2.8): the union of the open requests' {@link Lock}s (until they start
 *   easing out) and the strictest {@link Hud} (until they are gone).</li>
 * </ul>
 * Systems move over to it one at a time (plan 3); until then they keep their own camera code.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CameraDirector {
    /** Priorities (plan 2.3): higher wins. */
    public static final int PRIORITY_MACHINE = 200, PRIORITY_BODY_EYE = 300, PRIORITY_SCREEN = 400, PRIORITY_CUTSCENE = 500,
            PRIORITY_DEATH = 600;

    /** A request's time: game ticks (stops when the single-player game pauses) or wall time (views over an open screen). */
    public enum Clock {
        GAME, REAL;

        /** Seconds. */
        public double now(float partialTick) {
            if (this == REAL) return System.nanoTime() / 1e9;
            var level = Minecraft.getInstance().level;
            return level == null ? 0 : (level.getGameTime() + partialTick) / 20.0;
        }
    }

    /** What a request hides, least to most (plan 2.8). */
    public enum Hud {
        /** Nothing. */
        NORMAL,
        /** The first-person hands and the block outline. */
        NO_HANDS,
        /** The whole GUI (chat too), the hands and the outline. */
        NO_HUD
    }

    /** What a request locks (plan 2.7). */
    public enum Lock {
        /** Walking and jumping. */
        MOVE,
        /** Sneak held down / kept up. */
        SNEAK_ON, SNEAK_OFF,
        /** The mouse does not turn the player (the player's rotation is left as it is). */
        LOOK,
        /** The attack / use key does nothing (swallowed, no swing). */
        ATTACK, USE
    }

    /** A camera source: the pose it wants this frame given the pose so far, or null for none (the request then adds nothing). */
    @FunctionalInterface
    public interface Shot {
        @Nullable
        CameraPose pose(Camera camera, CameraPose current, float partialTick);
    }

    /** A source of body offsets (world blocks, from the entity's drawn position), or null. */
    @FunctionalInterface
    public interface BodyOffset {
        @Nullable
        Vec3 offset(Entity entity);
    }

    /** One open presentation. */
    public static final class Request {
        final String id;
        final int priority;
        final Shot shot;
        final Clock clock;
        final CameraFade fade;
        final Set<Lock> locks = EnumSet.noneOf(Lock.class);
        Hud hud = Hud.NORMAL;

        /** {@code in} / {@code out}: the fade's seconds on {@code clock}. */
        public Request(String id, int priority, Shot shot, Clock clock, double in, double out) {
            this.id = id;
            this.priority = priority;
            this.shot = shot;
            this.clock = clock;
            this.fade = new CameraFade(in, out);
        }

        public Request locks(Lock... locks) {
            this.locks.addAll(List.of(locks));
            return this;
        }

        public Request hud(Hud hud) {
            this.hud = hud;
            return this;
        }

        public float weight(float partialTick) {
            return (float) fade.weight(clock.now(partialTick));
        }
    }

    /** Open requests, lowest priority first (equal priorities: in opening order). */
    private static final List<Request> REQUESTS = new ArrayList<>();
    /** Times every shake source's sum (a later accessibility option: less camera shake). */
    public static float shakeScale = 1F;
    private static final CameraShake SHAKE = new CameraShake();
    /** This frame's placement: its roll and the field of view steps (target, weight) in order; null when nothing placed it. */
    @Nullable
    private static CameraPose placed;
    private static final List<double[]> FOV_STEPS = new ArrayList<>();

    private CameraDirector() {
    }

    // ---------------------------------------------------------------- requests

    /** Opens a request (one of the same id is replaced) and starts its fade in. */
    public static void open(Request request) {
        REQUESTS.removeIf(r -> r.id.equals(request.id));
        request.fade.open(request.clock.now(0F));
        int at = 0;
        while (at < REQUESTS.size() && REQUESTS.get(at).priority <= request.priority) at++;
        REQUESTS.add(at, request);
    }

    /** Starts the request's fade out; it goes once faded. */
    public static void close(String id) {
        for (Request r : REQUESTS) if (r.id.equals(id)) r.fade.close(r.clock.now(0F));
    }

    /** A request of this id is open and not easing out. */
    public static boolean holding(String id) {
        for (Request r : REQUESTS) if (r.id.equals(id)) return !r.fade.closing();
        return false;
    }

    /** A request of this id is open (also while it eases out). */
    public static boolean isOpen(String id) {
        for (Request r : REQUESTS) if (r.id.equals(id)) return true;
        return false;
    }

    private static void prune() {
        REQUESTS.removeIf(r -> r.fade.finished(r.clock.now(0F)));
    }

    // ---------------------------------------------------------------- the camera

    /**
     * Camera#setup TAIL (mixin client.CameraDirectorMixin): the composed pose for this frame, or null when no shot is
     * open (the camera stays as vanilla and the other mixins left it).
     */
    @Nullable
    public static CameraPose place(Camera camera, float partialTick) {
        if (REQUESTS.isEmpty()) return null;
        prune();
        FOV_STEPS.clear();
        placed = compose(REQUESTS, camera, new CameraPose(camera.getPosition(), camera.getYRot(), camera.getXRot()), partialTick, FOV_STEPS);
        return placed;
    }

    /**
     * From {@code base} up the requests (lowest priority first), each shot's pose blended in by its weight; the field of
     * view steps (target, weight) go to {@code fovSteps}. Null when no request placed the camera.
     */
    @Nullable
    static CameraPose compose(List<Request> requests, Camera camera, CameraPose base, float partialTick, List<double[]> fovSteps) {
        CameraPose pose = base;
        boolean any = false;
        for (Request r : requests) {
            float w = r.weight(partialTick);
            if (w <= 0F) continue;
            CameraPose target = r.shot.pose(camera, pose, partialTick);
            if (target == null) continue;
            pose = CameraPose.blend(pose, target, w);
            if (!Double.isNaN(target.fov())) fovSteps.add(new double[]{target.fov(), w});
            any = true;
        }
        return any ? pose : null;
    }

    /** The field of view after the steps (each eased in by its weight over the one before). */
    static double fov(double game, List<double[]> steps) {
        double fov = game;
        for (double[] step : steps) fov = Mth.lerp(step[1], fov, step[0]);
        return fov;
    }

    /** The open requests, lowest priority first (offline check). */
    static List<Request> requests() {
        return REQUESTS;
    }

    @SubscribeEvent
    public static void frameStart(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            placed = null;
            FOV_STEPS.clear();
        }
    }

    /** The placed roll, then the shake layer, on the view. */
    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        Camera camera = event.getCamera();
        float partial = (float) event.getPartialTick();
        if (placed != null && placed.roll() != 0F) event.setRoll(event.getRoll() + placed.roll());
        SHAKE.reset();
        // every source runs every frame: some keep per-frame state (the guns' shared animation sample)
        for (CameraShake.Source source : CameraSources.SHAKES) source.shake(camera, partial, SHAKE);
        if (SHAKE.pitch == 0 && SHAKE.yaw == 0 && SHAKE.roll == 0) return;
        event.setPitch((float) (event.getPitch() + SHAKE.pitch * shakeScale));
        event.setYaw((float) (event.getYaw() + SHAKE.yaw * shakeScale));
        event.setRoll((float) (event.getRoll() + SHAKE.roll * shakeScale));
    }

    /** The shots' field of view over the game's (world pass only; before ViewFov records it at LOWEST). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || FOV_STEPS.isEmpty()) return;
        event.setFOV(fov(event.getFOV(), FOV_STEPS));
    }

    // ---------------------------------------------------------------- body offset

    /** Where the entity's body is drawn away from it this frame (world blocks), the sources summed; null when not moved. */
    @Nullable
    public static Vec3 bodyOffset(Entity entity) {
        Vec3 sum = null;
        for (BodyOffset source : CameraSources.BODY_OFFSETS) {
            Vec3 o = source.offset(entity);
            if (o != null) sum = sum == null ? o : sum.add(o);
        }
        return sum;
    }

    // ---------------------------------------------------------------- input lock

    public static boolean locked(Lock lock) {
        for (Request r : REQUESTS) if (!r.fade.closing() && r.locks.contains(lock)) return true;
        return false;
    }

    /** mixin client.MouseHandlerLookLockMixin: the mouse's turn this frame is dropped. */
    public static boolean lookLocked() {
        return locked(Lock.LOOK);
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        if (REQUESTS.isEmpty() || event.getEntity() != Minecraft.getInstance().player) return;
        var input = event.getInput();
        if (locked(Lock.MOVE)) {
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.up = input.down = input.left = input.right = input.jumping = false;
        }
        if (locked(Lock.SNEAK_ON)) input.shiftKeyDown = true;
        else if (locked(Lock.SNEAK_OFF)) input.shiftKeyDown = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (REQUESTS.isEmpty()) return;
        if (event.isAttack() && locked(Lock.ATTACK) || event.isUseItem() && locked(Lock.USE)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    // ---------------------------------------------------------------- HUD

    /** The strictest HUD of the open requests (also while they ease out). */
    public static Hud hud() {
        Hud hud = Hud.NORMAL;
        for (Request r : REQUESTS) if (r.hud.ordinal() > hud.ordinal()) hud = r.hud;
        return hud;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hands(RenderHandEvent event) {
        if (hud() != Hud.NORMAL) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void outline(RenderHighlightEvent.Block event) {
        if (hud() != Hud.NORMAL) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void gui(RenderGuiEvent.Pre event) {
        if (hud() == Hud.NO_HUD) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        REQUESTS.clear();
        placed = null;
        FOV_STEPS.clear();
    }
}
