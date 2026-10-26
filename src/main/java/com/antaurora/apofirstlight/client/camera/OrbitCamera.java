package com.antaurora.apofirstlight.client.camera;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.MaintenanceModeClientState;
import com.antaurora.apofirstlight.entity.OfficeChairEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import com.antaurora.apofirstlight.vehicle.PickupTruckLayout;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
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
import net.minecraftforge.client.event.RenderPlayerEvent;
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
 *   where it walks ({@link #TURN} a tick) and the head looks where the centre dot is, within a neck's reach ({@link #head},
 *   drawn only). Sat on a wooden chair the camera still goes all the way round, the body stays and only the head turns:
 *   toward the camera when it is in front ({@link #SEAT_FACE}), else ahead; clicks act only with the view in the
 *   sitter's reach (cabin/ChairSit#LOOK). While attack / use is held the body turns to the camera's view ({@link #AIM_TURN} a tick) and the keys
 *   strafe. A click (attack, use) acts only once the body faces the view: until then it is held back and played again
 *   when it does, so nothing is struck, used or shot behind the body's back (user 2026-10-19: no flick shots). Aiming a
 *   gun (use held) and the perspective key wait the same way, then drop to first person.</li>
 *   <li>In a T15 (any seat): the camera chases behind the truck, easing after its heading; the mouse swings it round, and
 *   after {@link #RETURN_AFTER} s without the mouse it eases back behind while the truck moves. The wheel zooms. The
 *   rider's head follows the camera's view within the seat's look range ({@link #riderLook}).</li>
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
    /**
     * On foot: the body's turn to the view while attack / use is held or a click waits (deg a tick: half a turn in 6 ticks,
     * 0.3 s), and how near the view's yaw counts as facing it (deg).
     */
    private static final float AIM_TURN = 30, AIM_ALIGNED = 5;
    /**
     * The head (drawn): how far it turns off the body toward the view (deg), how far round the view may go before the
     * head looks ahead again (deg), its pitch range (deg, + down), its easing (s).
     */
    private static final float HEAD_YAW = 70, HEAD_BACK = 120, HEAD_PITCH_MIN = -50, HEAD_PITCH_MAX = 60;
    /**
     * Sat on a wooden chair (cabin/ChairSit; user 2026-10-25: "能不能改成自由视角，但是只能头转"): the camera goes all the way
     * round, the body stays; the head looks at the camera while it is within this far (deg) of straight ahead (in front,
     * on the face), turned at most {@link #HEAD_YAW}; further round (beside, behind the head) it looks ahead.
     */
    private static final float SEAT_FACE = 100;
    /** Sat, not acting: the body's own look back to the chair's way (deg a tick). */
    private static final float SEAT_SETTLE = 10;
    private static final double HEAD_EASE = 0.12;
    /** Driving: how far past the seat's look range the view may go before the head looks ahead again (deg); its pitch range. */
    private static final float RIDER_BACK = 20, RIDER_PITCH_MIN = -30, RIDER_PITCH_MAX = 25;
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
    private static long lastFrame, lastMouse, lastHead;
    @Nullable
    private static PickupTruckEntity riding;
    /** Aiming a gun dropped the view to first person; let go of use, back to the orbit. */
    private static boolean aimDrop;
    /** A click held back until the body faces the view; the perspective key waiting the same way. */
    @Nullable
    private static KeyMapping heldClick;
    private static boolean firstPersonWaiting;
    /** The drawn head off the body (deg) and its pitch; the player's own head values while they are swapped for drawing. */
    private static float headYaw, headPitch;
    private static float savedHeadRot, savedHeadRotO, savedXRot, savedXRotO;
    private static boolean headSwapped;

    private OrbitCamera() {
    }

    /** The orbit is the view: vanilla's back view on the player, nothing else holding the camera. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK && mc.getCameraEntity() == mc.player
                && !MaintenanceModeClientState.INSTANCE.active();
    }

    /** The camera's view yaw (deg). */
    public static float viewYaw() {
        return yaw;
    }

    /** Sat on a wooden chair with the view further round than the sitter can look (cabin/ChairSit#LOOK): nothing to aim at. */
    private static boolean behindSeat(LocalPlayer player) {
        com.antaurora.apofirstlight.cabin.ChairSit.Sit s = com.antaurora.apofirstlight.cabin.ChairSit.get(player);
        return s != null && Math.abs(Mth.wrapDegrees(yaw - s.yaw())) > com.antaurora.apofirstlight.cabin.ChairSit.LOOK;
    }

    /** Where the body turns to face the view: the view's yaw; sat with the view behind the seat, the chair's way. */
    private static float aimYaw(LocalPlayer player) {
        com.antaurora.apofirstlight.cabin.ChairSit.Sit s = com.antaurora.apofirstlight.cabin.ChairSit.get(player);
        return s != null && behindSeat(player) ? s.yaw() : yaw;
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
        boolean foot = onFoot() && mc.screen == null;
        if (!foot) {
            heldClick = null;
            firstPersonWaiting = false;
        } else if (facingView(player)) {
            // the body has turned to the view (movement, below): what waited for it goes ahead now, before handleKeybinds
            if (heldClick != null) {
                faceView(player);
                KeyMapping.click(heldClick.getKey());
                heldClick = null;
            }
            if (firstPersonWaiting) {
                firstPersonWaiting = false;
                toFirstPerson(mc, player);
            }
        }
        // aiming a gun: first person while use is held, once the body faces the view
        if (foot && mc.options.keyUse.isDown() && player.getMainHandItem().getItem() instanceof NativeGunItem && !behindSeat(player)) {
            if (facingView(player)) {
                faceView(player);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                aimDrop = true;
            }
        } else if (aimDrop && !mc.options.keyUse.isDown()) {
            aimDrop = false;
            if (mc.options.getCameraType() == CameraType.FIRST_PERSON) enter(mc, player, true);
        }
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
        // on foot the first-person view is the body's: it turns to the camera's view first
        if (truck() == null && !facingView(player)) firstPersonWaiting = true;
        else toFirstPerson(mc, player);
    }

    private static void toFirstPerson(Minecraft mc, LocalPlayer player) {
        if (truck() == null) faceView(player);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    /** Into the orbit behind the player; {@code keepPitch} (back from aiming): at the player's pitch, else a little from above. */
    private static void enter(Minecraft mc, LocalPlayer player, boolean keepPitch) {
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        yaw = player.getYRot();
        pitch = keepPitch ? Mth.clamp(player.getXRot(), FOOT_PITCH_MIN, FOOT_PITCH_MAX) : Math.max(ENTER_PITCH, Mth.clamp(player.getXRot(), FOOT_PITCH_MIN, 30));
        riding = null;
        shown = -1;
        heldClick = null;
        firstPersonWaiting = false;
        headYaw = 0;
        headPitch = Mth.clamp(player.getXRot(), HEAD_PITCH_MIN, HEAD_PITCH_MAX);
    }

    /** On foot: the body within {@link #AIM_ALIGNED} of the camera's yaw. */
    private static boolean facingView(LocalPlayer player) {
        return Math.abs(Mth.wrapDegrees(aimYaw(player) - player.getYRot())) <= AIM_ALIGNED;
    }

    /** Sets the player exactly to the camera's view (the centre ray runs through the eyes) and picks again; only a small
     *  correction once {@link #facingView} (the turn itself is {@link #AIM_TURN} a tick, in {@link #movement}). */
    private static void faceView(LocalPlayer player) {
        if (truck() != null) return;
        float to = aimYaw(player);
        player.setYRot(to);
        player.setYHeadRot(to);
        player.setXRot(to == yaw ? Mth.clamp(pitch, -90, 90) : 0F);
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
            riderLook(player, truck, camYaw, dt);
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

    /**
     * Driving: the rider's own look (yaw, pitch, head) eases toward the camera's view within the seat's look range (the
     * truck clamps it there too, PickupTruckEntity#clampRotation); a view further round than that, the head looks ahead.
     * Turned like the mouse turns it (Entity#turn: the previous values move along), so first person goes on from it.
     */
    private static void riderLook(LocalPlayer player, PickupTruckEntity truck, float camYaw, double dt) {
        int seat = truck.riderSeatIndex(player);
        if (seat < 0 || dt <= 0) return;
        float range = PickupTruckLayout.SEATS[seat].look(), facing = player.yBodyRot;
        float rel = Mth.wrapDegrees(camYaw - facing), wantPitch = Mth.clamp(pitch, RIDER_PITCH_MIN, RIDER_PITCH_MAX);
        if (Math.abs(rel) > range + RIDER_BACK) {
            rel = 0;
            wantPitch = 0;
        } else rel = Mth.clamp(rel, -range, range);
        float k = (float) (1 - Math.exp(-dt / HEAD_EASE));
        float dy = Mth.wrapDegrees(rel - Mth.wrapDegrees(player.getYRot() - facing)) * k, dx = (wantPitch - player.getXRot()) * k;
        player.setYRot(player.getYRot() + dy);
        player.yRotO += dy;
        player.setYHeadRot(player.getYRot());
        player.yHeadRotO += dy;
        player.setXRot(player.getXRot() + dx);
        player.xRotO += dx;
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

    // ---------------------------------------------------------------- the drawn head (on foot)

    /**
     * On foot the player's head is its body's yaw every tick (Player#serverAiStep), the walking direction here: for drawing
     * only, the head turns toward the camera's view within {@link #HEAD_YAW} of the body and pitches with it (looking
     * further round than {@link #HEAD_BACK}, it looks ahead), eased; put back after the player is drawn ({@link #headBack}).
     * Lowest priority, so a cancelled draw (no Post) is never swapped.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void head(RenderPlayerEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || !onFoot() || !CameraDirector.holding(ID) || headSwapped) return;
        LocalPlayer p = mc.player;
        float body = Mth.rotLerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot);
        float rel = Mth.wrapDegrees(yaw - body), want = Mth.clamp(pitch, HEAD_PITCH_MIN, HEAD_PITCH_MAX);
        if (com.antaurora.apofirstlight.cabin.ChairSit.sitting(p)) {
            // sat on a wooden chair: the head looks at the camera when it is in front, else ahead (SEAT_FACE)
            float toCamera = Mth.wrapDegrees(yaw + 180 - body);
            boolean face = Math.abs(toCamera) <= SEAT_FACE;
            rel = face ? Mth.clamp(toCamera, -HEAD_YAW, HEAD_YAW) : 0F;
            want = face ? Mth.clamp(-pitch, HEAD_PITCH_MIN, HEAD_PITCH_MAX) : 0F;
        } else if (Math.abs(rel) > HEAD_BACK) {
            rel = 0;
            want = 0;
        } else rel = Mth.clamp(rel, -HEAD_YAW, HEAD_YAW);
        long now = System.nanoTime();
        double dt = lastHead == 0 ? 0.1 : Mth.clamp((now - lastHead) / 1e9, 0, 0.1);
        lastHead = now;
        float k = (float) (1 - Math.exp(-dt / HEAD_EASE));
        headYaw += (rel - headYaw) * k;
        headPitch += (want - headPitch) * k;
        savedHeadRot = p.yHeadRot;
        savedHeadRotO = p.yHeadRotO;
        savedXRot = p.getXRot();
        savedXRotO = p.xRotO;
        p.yHeadRot = p.yHeadRotO = body + headYaw;
        p.setXRot(headPitch);
        p.xRotO = headPitch;
        headSwapped = true;
    }

    @SubscribeEvent
    public static void headBack(RenderPlayerEvent.Post event) {
        if (!headSwapped || event.getEntity() != Minecraft.getInstance().player) return;
        LocalPlayer p = Minecraft.getInstance().player;
        p.yHeadRot = savedHeadRot;
        p.yHeadRotO = savedHeadRotO;
        p.setXRot(savedXRot);
        p.xRotO = savedXRotO;
        headSwapped = false;
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

    /**
     * On foot: the keys walk relative to the camera. Free, the body turns toward the way it walks; while attack / use is
     * held or a click waits, the body turns to the view ({@link #AIM_TURN} a tick) and the keys strafe in its frame, so the
     * two never pull against each other. Here, in the player's tick, so the turn is drawn smoothly between ticks.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void movement(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(event.getEntity() instanceof LocalPlayer player) || player != mc.player || !onFoot()) return;
        boolean acting = mc.screen == null && (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown())
                || heldClick != null || firstPersonWaiting;
        com.antaurora.apofirstlight.cabin.ChairSit.Sit sat = com.antaurora.apofirstlight.cabin.ChairSit.get(player);
        if (acting) {
            float to = aimYaw(player), body = Mth.approachDegrees(player.getYRot(), to, AIM_TURN);
            player.setYRot(body);
            player.setYHeadRot(body);
            player.setXRot(Mth.approach(player.getXRot(), to == yaw ? Mth.clamp(pitch, -90, 90) : 0F, AIM_TURN));
        } else if (sat != null) {
            // sat, the camera free: the sitter's own look (the chest follows it) settles back to the chair's way
            float body = Mth.approachDegrees(player.getYRot(), sat.yaw(), SEAT_SETTLE);
            player.setYRot(body);
            player.setYHeadRot(body);
            player.setXRot(Mth.approach(player.getXRot(), 0F, SEAT_SETTLE));
        }
        // on a seat that keeps the body its way (a wood chair, the ground): the keys neither turn nor walk the body (user
        // 2026-10-25: on a wood chair A / D swung the chest and arms); the office chair rolls and turns by them, as on foot
        if (player.isPassenger() && !(player.getVehicle() instanceof OfficeChairEntity)) return;
        Input in = event.getInput();
        double l = in.leftImpulse, f = in.forwardImpulse;
        if (l * l + f * f < 1e-6) return;
        double y = Math.toRadians(yaw), dx = l * Math.cos(y) - f * Math.sin(y), dz = f * Math.cos(y) + l * Math.sin(y);
        float body = player.getYRot();
        if (!acting) {
            body = Mth.approachDegrees(body, (float) Math.toDegrees(Math.atan2(-dx, dz)), TURN);
            player.setYRot(body);
            player.setYHeadRot(body);
        }
        // the same world direction in the body's frame (LivingEntity#moveRelative's rotation, inverted)
        double p = Math.toRadians(body);
        in.leftImpulse = (float) (dx * Math.cos(p) + dz * Math.sin(p));
        in.forwardImpulse = (float) (-dx * Math.sin(p) + dz * Math.cos(p));
    }

    /**
     * On foot, a click (attack, use) acts only once the body faces the camera's view: until then it is cancelled, the body
     * turns ({@link #movement}) and the click is played again when it faces the view ({@link #tick}); facing it, the player
     * is set exactly to the view so what is struck, used or shot is what the centre dot is on. True when held back.
     * Also called first by guns (weapon.client.NativeGunInput#attack), which cancel the click before this sees it.
     */
    public static boolean holdClick(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !onFoot() || !(event.isAttack() || event.isUseItem())) return false;
        if (behindSeat(player)) {
            // sat with the camera round behind the seat: the sitter cannot face the dot, so nothing happens
            event.setCanceled(true);
            event.setSwingHand(false);
            return true;
        }
        if (facingView(player)) {
            faceView(player);
            return false;
        }
        heldClick = event.getKeyMapping();
        event.setCanceled(true);
        event.setSwingHand(false);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        holdClick(event);
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
