package com.antaurora.apofirstlight.client.camera;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.MaintenanceModeClientState;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * The orbit camera (docs/dev/orbit_camera_v1.md; user 2026-10-18: a GTA V style third person that turns all the way round,
 * on foot and driving, in place of vanilla's F5). Vanilla's perspective key (F5 unless rebound) now steps first person and
 * orbit only; the front view is gone. The orbit is a director shot ({@link CameraDirector}, priority {@link #PRIORITY}, below
 * every other view) over vanilla's back view, so the game's third-person drawing (own body, no hands) stays.
 * <ul>
 *   <li>The mouse turns the camera, not the player (mixin client.MouseHandlerLookLockMixin, {@link #takeMouse}).</li>
 *   <li>On foot: the camera turns about the eyes; the movement keys walk relative to the camera, the body turns toward
 *   where it walks ({@link #TURN} a tick). Attack / use (and holding them) first turn the player to the camera's view, so
 *   what is struck or used is what the centre dot is on (the screen's centre ray runs through the eyes). Aiming a gun (use
 *   held) drops to first person until it is let go.</li>
 *   <li>In a T15 (any seat): the camera chases behind the truck, easing after its heading; the mouse swings it round, and
 *   after {@link #RETURN_AFTER} s without the mouse it eases back behind while the truck moves. The wheel zooms.</li>
 *   <li>The camera is pulled in where blocks are between it and its centre (as vanilla's back view), and eases back out.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class OrbitCamera {
    public static final String ID = "orbit";
    /** Below every other director shot (machines 200 and up). */
    public static final int PRIORITY = 100;
    /** On foot: camera distance (blocks) and the pitch range (deg, + down). */
    private static final double FOOT_DIST = 4.0;
    private static final float FOOT_PITCH_MIN = -60, FOOT_PITCH_MAX = 80, ENTER_PITCH = 12;
    /** Body turn toward the walking direction (deg a tick). */
    private static final float TURN = 40;
    /** Driving: the centre above the truck's origin (blocks), distance (blocks, wheel range), pitch at rest (deg). */
    private static final double DRIVE_UP = 1.7, DRIVE_DIST = 7.0, DRIVE_DIST_MIN = 4.0, DRIVE_DIST_MAX = 14.0;
    private static final float DRIVE_PITCH = 10, DRIVE_PITCH_MIN = -30, DRIVE_PITCH_MAX = 70;
    /** Driving: the chase easing after the truck's heading (s), the mouse's swing returning after RETURN_AFTER s (ease s). */
    private static final double CHASE = 0.3, RETURN_AFTER = 1.5, RETURN_EASE = 0.6, RETURN_SPEED = 2.0;
    /** Pulled-in distance easing back out (s). */
    private static final double OUT_EASE = 0.3;

    /** On foot: the camera's yaw and pitch. Driving: the swing off the chase and the pitch over DRIVE_PITCH. */
    private static float yaw, pitch;
    private static float chaseYaw;
    private static double driveDist = DRIVE_DIST, shown = -1;
    private static long lastFrame, lastMouse;
    @Nullable
    private static PickupTruckEntity riding;
    /** Aiming a gun dropped the view to first person; let go of use, back to the orbit. */
    private static boolean aimDrop;

    private OrbitCamera() {
    }

    /** The orbit is the view: vanilla's back view on the player, nothing else holding the camera. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK && mc.getCameraEntity() == mc.player
                && !MaintenanceModeClientState.INSTANCE.active();
    }

    /** The orbit on foot (the centre dot, turning to the view on clicks). */
    public static boolean onFoot() {
        return active() && truck() == null;
    }

    @Nullable
    private static PickupTruckEntity truck() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.getVehicle() instanceof PickupTruckEntity t ? t : null;
    }

    // ---------------------------------------------------------------- the key, the request

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // vanilla's perspective key is ours: drained before Minecraft#handleKeybinds reads it
        while (mc.options.keyTogglePerspective.consumeClick()) if (player != null && !MaintenanceModeClientState.INSTANCE.active()) toggle(mc, player);
        if (player == null) return;
        // aiming a gun: first person while use is held
        if (onFoot() && mc.screen == null && mc.options.keyUse.isDown() && player.getMainHandItem().getItem() instanceof NativeGunItem) {
            faceView(player);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            aimDrop = true;
        } else if (aimDrop && !mc.options.keyUse.isDown()) {
            aimDrop = false;
            if (mc.options.getCameraType() == CameraType.FIRST_PERSON) enter(mc, player, true);
        }
        // holding attack / use on foot keeps the player turned to the view (mining, eating, placing in a row)
        if (onFoot() && mc.screen == null && (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown())) faceView(player);
        boolean on = active();
        if (on && !CameraDirector.holding(ID)) CameraDirector.open(new CameraDirector.Request(ID, PRIORITY, OrbitCamera::pose, CameraDirector.Clock.REAL, 0.25, 0));
        else if (!on && CameraDirector.holding(ID)) CameraDirector.close(ID);
    }

    private static void toggle(Minecraft mc, LocalPlayer player) {
        if (mc.options.getCameraType().isFirstPerson()) {
            enter(mc, player, false);
            return;
        }
        aimDrop = false;
        if (truck() == null) {
            player.setYRot(yaw);
            player.setYHeadRot(yaw);
            player.setXRot(Mth.clamp(pitch, -90, 90));
        }
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    /** Into the orbit behind the player; {@code keepPitch} (back from aiming): at the player's pitch, else a little from above. */
    private static void enter(Minecraft mc, LocalPlayer player, boolean keepPitch) {
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        yaw = player.getYRot();
        pitch = keepPitch ? Mth.clamp(player.getXRot(), FOOT_PITCH_MIN, FOOT_PITCH_MAX) : Math.max(ENTER_PITCH, Mth.clamp(player.getXRot(), FOOT_PITCH_MIN, 30));
        riding = null;
        shown = -1;
    }

    /** Turns the player to the camera's view (the centre ray runs through the eyes) and picks again. */
    private static void faceView(LocalPlayer player) {
        if (truck() != null) return;
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(Mth.clamp(pitch, -90, 90));
        Minecraft.getInstance().gameRenderer.pick(1.0F);
    }

    // ---------------------------------------------------------------- the shot

    @Nullable
    private static CameraPose pose(Camera camera, CameraPose current, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return null;
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Mth.clamp((now - lastFrame) / 1e9, 0, 0.1);
        lastFrame = now;
        PickupTruckEntity truck = truck();
        Vec3 centre;
        float camYaw, camPitch;
        double dist;
        if (truck != null) {
            float heading = Mth.rotLerp(partialTick, truck.yRotO, truck.getYRot());
            if (riding != truck) {
                // into the seat: keep where the camera looked, the return eases it behind
                riding = truck;
                chaseYaw = heading;
                yaw = Mth.wrapDegrees(yaw - heading);
                pitch = Mth.clamp(pitch - DRIVE_PITCH, DRIVE_PITCH_MIN - DRIVE_PITCH, DRIVE_PITCH_MAX - DRIVE_PITCH);
            }
            chaseYaw = Mth.rotLerp((float) (1 - Math.exp(-dt / CHASE)), chaseYaw, heading);
            if ((now - lastMouse) / 1e9 > RETURN_AFTER && truck.speed() > RETURN_SPEED) {
                float k = (float) Math.exp(-dt / RETURN_EASE);
                yaw = Mth.wrapDegrees(yaw) * k;
                pitch *= k;
            }
            centre = truck.getPosition(partialTick).add(0, DRIVE_UP, 0);
            camYaw = chaseYaw + yaw;
            camPitch = DRIVE_PITCH + pitch;
            dist = driveDist;
        } else {
            if (riding != null) {
                // out of the seat: the orbit goes on from where the chase camera looked
                yaw = chaseYaw + yaw;
                pitch = Mth.clamp(DRIVE_PITCH + pitch, FOOT_PITCH_MIN, FOOT_PITCH_MAX);
                riding = null;
            }
            centre = player.getEyePosition(partialTick);
            camYaw = yaw;
            camPitch = pitch;
            dist = FOOT_DIST;
        }
        Vec3 look = Vec3.directionFromRotation(camPitch, camYaw);
        double free = clear(mc, player, centre, look, dist);
        // blocked: in at once; clear (or a new distance, in or out of a seat): eased
        if (shown < 0 || free < dist - 1e-3 && free < shown) shown = free;
        else shown += (free - shown) * (1 - Math.exp(-dt / OUT_EASE));
        return new CameraPose(centre.subtract(look.scale(shown)), camYaw, camPitch);
    }

    /** How far back from the centre the camera can sit (vanilla Camera#getMaxZoom: eight rays 0.1 off the line). */
    private static double clear(Minecraft mc, LocalPlayer player, Vec3 centre, Vec3 look, double dist) {
        for (int i = 0; i < 8; i++) {
            double ox = ((i & 1) * 2 - 1) * 0.1, oy = ((i >> 1 & 1) * 2 - 1) * 0.1, oz = ((i >> 2 & 1) * 2 - 1) * 0.1;
            Vec3 from = centre.add(ox, oy, oz), to = centre.subtract(look.scale(dist)).add(ox, oy, oz);
            HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) dist = Math.min(dist, hit.getLocation().distanceTo(centre));
        }
        return dist;
    }

    // ---------------------------------------------------------------- input

    /**
     * mixin client.MouseHandlerLookLockMixin: the mouse turn gathered this frame turns the orbit instead of the player
     * (vanilla's sensitivity curve and Entity#turn's 0.15); true when taken.
     */
    public static boolean takeMouse(double dx, double dy) {
        if (!active() || !CameraDirector.holding(ID)) return false;
        Minecraft mc = Minecraft.getInstance();
        double s = mc.options.sensitivity().get() * 0.6 + 0.2, k = s * s * s * 8.0 * 0.15;
        float turnX = (float) (dx * k), turnY = (float) (dy * k * (mc.options.invertYMouse().get() ? -1 : 1));
        if (turnX != 0 || turnY != 0) lastMouse = System.nanoTime();
        yaw += turnX;
        if (truck() != null) pitch = Mth.clamp(pitch + turnY, DRIVE_PITCH_MIN - DRIVE_PITCH, DRIVE_PITCH_MAX - DRIVE_PITCH);
        else pitch = Mth.clamp(pitch + turnY, FOOT_PITCH_MIN, FOOT_PITCH_MAX);
        return true;
    }

    /** On foot: the keys walk relative to the camera, the body turns toward the way it walks. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void movement(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player || !onFoot()) return;
        Input in = event.getInput();
        double l = in.leftImpulse, f = in.forwardImpulse;
        if (l * l + f * f < 1e-6) return;
        double y = Math.toRadians(yaw), dx = l * Math.cos(y) - f * Math.sin(y), dz = f * Math.cos(y) + l * Math.sin(y);
        float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float body = Mth.approachDegrees(player.getYRot(), want, TURN);
        player.setYRot(body);
        player.setYHeadRot(body);
        // the same world direction in the turned body's frame (LivingEntity#moveRelative's rotation, inverted)
        double p = Math.toRadians(body);
        in.leftImpulse = (float) (dx * Math.cos(p) + dz * Math.sin(p));
        in.forwardImpulse = (float) (-dx * Math.sin(p) + dz * Math.cos(p));
    }

    /** A click on foot: turned to the view first, so it lands on what the centre dot is on. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && onFoot()) faceView(player);
    }

    /** Driving: the wheel zooms (the hotbar does not change while driving anyway). */
    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if (!active() || truck() == null || Minecraft.getInstance().screen != null) return;
        driveDist = Mth.clamp(driveDist - event.getScrollDelta() * 0.8, DRIVE_DIST_MIN, DRIVE_DIST_MAX);
        event.setCanceled(true);
    }

    /** On foot the block outline would show what the body faces, not the centre: hidden. */
    @SubscribeEvent
    public static void outline(RenderHighlightEvent.Block event) {
        if (onFoot()) event.setCanceled(true);
    }

    /** The centre dot on foot (vanilla draws its crosshair in first person only). */
    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Overlay {
        private Overlay() {
        }

        @SubscribeEvent
        public static void register(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "orbit_dot", DOT);
        }

        static final IGuiOverlay DOT = (gui, graphics, partialTick, width, height) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.screen != null || !onFoot() || !CameraDirector.holding(ID)) return;
            int cx = width / 2, cy = height / 2;
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0x66000000);
            graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xE6FFFFFF);
        };
    }
}
