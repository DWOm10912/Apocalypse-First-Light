package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.skeleton.seat.RiderSeat;
import com.antaurora.apofirstlight.skeleton.seat.SeatPose;
import com.antaurora.apofirstlight.skeleton.seat.SeatedRiderHost;
import com.antaurora.apofirstlight.vehicle.PickupTruckEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Seated riders V1 (docs/dev/seated_rider_v1_plan.md 4, 5): which players sit on a {@link SeatedRiderHost}'s seat or are
 * getting on or off one, for the player skeleton (PlayerSkeleton draws them with {@link SeatPose}).
 * <ul>
 * <li>Per client tick, for every player: the seat it sits on (the host's synced index); where it last stood free. A seat
 * taken starts {@link SeatPose.Move#GET_IN} from where it stood; a seat left starts {@link SeatPose.Move#GET_OUT} toward
 * where it stands now. Walking off ends a get-out early; a move needs a standing place seen within
 * {@link #FREE_TICKS} ticks (a world joined seated starts seated).</li>
 * <li>The own camera in first person rides the move (a CameraDirector shot from the eye it left to the live one), and
 * the first-person body is drawn meanwhile ({@link #cameraOnBody}).</li>
 * <li>The draw scale ({@link SeatPose#scale}) for PlayerSkeleton#begin.</li>
 * </ul>
 * Hosts: the vehicle entity itself when it implements SeatedRiderHost, else an interim adapter (the T15, {@link T15Seats}).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SeatedRiders {
    private static final int FREE_TICKS = 5;
    private static final String CAMERA = "afl_seat_move";

    private static final class Track {
        @Nullable Entity vehicle;
        int seat = -1;
        /** The last tick it stood free, where, facing, its eye. */
        long freeTick = Long.MIN_VALUE;
        Vec3 freeFeet = Vec3.ZERO, freeEye = Vec3.ZERO;
        float freeYaw;
        /** The eye seated (the camera's place last tick on the seat). */
        Vec3 seatedEye = Vec3.ZERO;
        /** The move. */
        SeatPose.Move move = SeatPose.Move.NONE;
        long start;
        @Nullable Entity moveVehicle;
        int moveSeat = -1;
        Vec3 standFeet = Vec3.ZERO, cameraFrom = Vec3.ZERO;
        float standYaw;
        boolean camera;
        /** The torso share the seated pose solved last (for the moves). */
        float torso = 0.68F;
    }

    /** One player's seated state this frame. */
    public record Seated(RiderSeat seat, SeatPose.Move move, float seconds, Vec3 standFeet, float standYaw, float torso) {
        public SeatPose.Moment moment() {
            return SeatPose.moment(move, seat.doorSide() != 0, seconds, seat.door());
        }
    }

    private static final Map<Player, Track> TRACKS = new WeakHashMap<>();

    private SeatedRiders() {
    }

    /** The host for a vehicle, or null. */
    @Nullable
    public static SeatedRiderHost host(@Nullable Entity vehicle) {
        if (vehicle instanceof SeatedRiderHost host) return host;
        if (vehicle instanceof PickupTruckEntity truck) return new T15Seats(truck);   // interim, until the truck is a host
        return null;
    }

    /** The seat a player sits on now, or -1. */
    private static int seatOf(Player p) {
        SeatedRiderHost host = host(p.getVehicle());
        return host == null ? -1 : host.riderSeatIndex(p);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRACKS.clear();
            return;
        }
        if (mc.isPaused()) return;
        if (!PlayerSkeleton.enabled()) {
            TRACKS.clear();
            CameraDirector.close(CAMERA);
            return;
        }
        long now = mc.level.getGameTime();
        for (AbstractClientPlayer p : mc.level.players()) tick(mc, p, TRACKS.computeIfAbsent(p, k -> new Track()), now);
    }

    private static void tick(Minecraft mc, AbstractClientPlayer p, Track t, long now) {
        int seat = seatOf(p);
        boolean own = p == mc.player;
        if (seat >= 0 && t.seat < 0) {
            // sat down: from where it stood (a seat swap or a world joined seated: straight on)
            boolean fresh = now - t.freeTick <= FREE_TICKS;
            t.move = fresh ? SeatPose.Move.GET_IN : SeatPose.Move.NONE;
            t.start = now;
            t.moveVehicle = p.getVehicle();
            t.moveSeat = seat;
            t.standFeet = t.freeFeet;
            t.standYaw = t.freeYaw;
            t.cameraFrom = t.freeEye;
            startCamera(mc, t, own && fresh);
        } else if (seat < 0 && t.seat >= 0 && t.vehicle != null && t.vehicle.isAlive() && !p.isPassenger()) {
            t.move = SeatPose.Move.GET_OUT;
            t.start = now;
            t.moveVehicle = t.vehicle;
            t.moveSeat = t.seat;
            t.cameraFrom = t.seatedEye;
            startCamera(mc, t, own);
        }
        t.vehicle = seat >= 0 ? p.getVehicle() : null;
        t.seat = seat;
        if (seat >= 0) {
            t.seatedEye = p.getEyePosition();
        } else if (!p.isPassenger()) {
            t.freeTick = now;
            t.freeFeet = p.position();
            t.freeYaw = p.yBodyRot;
            t.freeEye = p.getEyePosition();
        }
        if (t.move == SeatPose.Move.NONE) return;
        float seconds = (now - t.start) / 20F;
        boolean done = t.move == SeatPose.Move.GET_IN ? seconds >= SeatPose.IN_END : seconds >= SeatPose.OUT_MAX;
        if (t.move == SeatPose.Move.GET_OUT && !done) {
            Seated s = frame(p, 0F);
            done = s == null || s.moment().done;
        }
        // walking off (or a vehicle gone) ends a get-out at once
        if (t.move == SeatPose.Move.GET_OUT) {
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo;
            if (dx * dx + dz * dz > 1e-3 || p.isPassenger() || t.moveVehicle == null || !t.moveVehicle.isAlive()) done = true;
        } else if (seat < 0) {
            done = true;
        }
        if (done) {
            t.move = SeatPose.Move.NONE;
            t.moveVehicle = null;
            if (t.camera) {
                CameraDirector.close(CAMERA);
                t.camera = false;
            }
        }
    }

    private static void startCamera(Minecraft mc, Track t, boolean own) {
        if (!own || t.move == SeatPose.Move.NONE || !mc.options.getCameraType().isFirstPerson()) return;
        t.camera = true;
        CameraDirector.open(new CameraDirector.Request(CAMERA, CameraDirector.PRIORITY_BODY_EYE, SeatedRiders::shot,
                CameraDirector.Clock.GAME, 0, 0.1));
    }

    /** The own camera during a move: from the eye it left toward the live eye, as the body goes. */
    @Nullable
    private static CameraPose shot(Camera camera, CameraPose current, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return null;
        Seated s = frame(mc.player, partialTick);
        if (s == null || s.move() == SeatPose.Move.NONE) return null;
        SeatPose.Moment m = s.moment();
        float e = s.move() == SeatPose.Move.GET_IN ? m.seated : 1 - m.seated;
        Track t = TRACKS.get(mc.player);
        Vec3 from = t == null ? current.position() : t.cameraFrom;
        return new CameraPose(from.lerp(current.position(), e), current.yaw(), current.pitch());
    }

    /** The own first-person camera is on the moving body (FirstPersonBody#shown draws it though the camera left the eye). */
    public static boolean cameraOnBody(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        Track t = mc.player == null ? null : TRACKS.get(mc.player);
        return t != null && t.camera && t.move != SeatPose.Move.NONE && camera.getEntity() == mc.player;
    }

    /** A player's seat, move and time this frame, or null when it neither sits nor gets on or off. */
    @Nullable
    public static Seated frame(Player p, float partialTick) {
        Track t = TRACKS.get(p);
        if (t == null || !PlayerSkeleton.enabled()) return null;
        Minecraft mc = Minecraft.getInstance();
        float seconds = mc.level == null ? 0 : (mc.level.getGameTime() - t.start + partialTick) / 20F;
        if (t.move != SeatPose.Move.NONE && t.moveVehicle != null) {
            SeatedRiderHost host = host(t.moveVehicle);
            RiderSeat seat = host == null ? null : host.riderSeat(t.moveSeat, partialTick);
            if (seat != null) return new Seated(seat, t.move, seconds, t.standFeet, t.standYaw, t.torso);
        }
        if (t.seat < 0 || t.vehicle == null) return null;
        SeatedRiderHost host = host(t.vehicle);
        RiderSeat seat = host == null ? null : host.riderSeat(t.seat, partialTick);
        return seat == null ? null : new Seated(seat, SeatPose.Move.NONE, 0, Vec3.ZERO, 0, t.torso);
    }

    /** The size a player is drawn at this frame (1 unless seated or moving). */
    public static float scale(Player p, float partialTick) {
        Seated s = frame(p, partialTick);
        return s == null ? 1F : SeatPose.scale(s.moment().seated);
    }

    /** The skeleton draws this player seated (VehicleRiderScale leaves it alone). */
    public static boolean handles(Player p) {
        return PlayerSkeleton.enabled() && PlayerSkeleton.supportedPose(p) && frame(p, 0F) != null;
    }

    /** The seated torso share the last still seated frame solved. */
    static void solvedTorso(Player p, float torso) {
        Track t = TRACKS.get(p);
        if (t != null) t.torso = torso;
    }
}
