package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.ChairSit;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.OrbitCamera;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.skeleton.seat.ChairPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Sitting on a wooden chair on the client (cabin/ChairSit; docs/dev/block_mesh_v2_b_ingame.md 12):
 * <ul>
 *   <li>the keys: sat, the movement keys get up toward where they point (the camera's way in the orbit camera, the look's
 *   otherwise), sneak or jump in front; nothing else of them reaches the player (no walking, sneaking, sprinting);</li>
 *   <li>the look: the own sitter's within ChairSit#LOOK of the chair's way (each frame after the mouse turned it, before it
 *   is drawn; the orbit camera goes all the way round, only the drawn head turns: client/camera/OrbitCamera);</li>
 *   <li>sat a while, one foot fidgets ({@link ChairPose.Idle}: the heel bouncing, or the foot moved forward and out):
 *   first after {@link #FIRST} s, for {@link #IDLE} s, then still for {@link #BETWEEN} s, eased in and out over
 *   {@link #EASE} s; the four (either kind, either foot) by a hash, never one twice running; only with the hands empty and
 *   nothing in action (eased off over {@link #BUSY_EASE} s when they are not).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ChairSitClient {
    static final double FIRST = 15, IDLE = 6, BETWEEN = 14, EASE = 0.5, BUSY_EASE = 0.3;
    /** The fidgets: the kind, and the right foot (even) or the left (odd). */
    private static final int KINDS = 4;

    /** This draw's idle and its weight. */
    public record Frame(ChairPose.Idle idle, float weight, float time, boolean right) {
        static final Frame NONE = new Frame(ChairPose.Idle.NONE, 0F, 0F, true);
    }

    private static final class Track {
        final double sat;
        double last = Double.NaN;
        float free = 1F;

        Track(double sat) {
            this.sat = sat;
        }
    }

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static boolean asked, hinted;
    private static int askedTicks;

    private ChairSitClient() {
    }

    /** ChairSit.Sync arrived for this entity: its box and eye. */
    public static void synced(int entity) {
        var level = Minecraft.getInstance().level;
        Entity e = level != null ? level.getEntity(entity) : null;
        if (e != null) e.refreshDimensions();
        if (e == Minecraft.getInstance().player) {
            asked = false;
            hinted = false;
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ChairSit.clearClient();
        TRACKS.clear();
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRACKS.clear();
            return;
        }
        TRACKS.keySet().removeIf(id -> !ChairSit.sitting(mc.level.getEntity(id)));
        clampLook(mc.player);
        if (asked && ++askedTicks > 40) asked = false;   // no answer: ask again on the next key
        LocalPlayer player = mc.player;
        if (player != null && ChairSit.sitting(player) && !hinted) {
            hinted = true;
            player.displayClientMessage(Component.translatable("hint.apocalypse_firstlight.chair.stand",
                    mc.options.keyShift.getTranslatedKeyMessage()), true);
        }
    }

    /** Each frame, after the mouse turned the player (Minecraft#runTick: turnPlayer, then the render tick): the look kept in front. */
    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) clampLook(Minecraft.getInstance().player);
    }

    /** The own sitter's yaw within ChairSit#LOOK of the chair's (the old yaw with it, so nothing interpolates past the edge). */
    static void clampLook(@org.jetbrains.annotations.Nullable LocalPlayer player) {
        ChairSit.Sit s = ChairSit.get(player);
        if (s == null) return;
        float yaw = s.yaw(), now = net.minecraft.util.Mth.wrapDegrees(player.getYRot() - yaw);
        float clamped = net.minecraft.util.Mth.clamp(now, -ChairSit.LOOK, ChairSit.LOOK);
        if (clamped != now) {
            player.setYRot(player.getYRot() + clamped - now);
            player.setYHeadRot(player.getYRot());
        }
        float old = net.minecraft.util.Mth.wrapDegrees(player.yRotO - yaw), oldClamped = net.minecraft.util.Mth.clamp(old, -ChairSit.LOOK, ChairSit.LOOK);
        if (oldClamped != old) player.yRotO += oldClamped - old;
    }

    /** Sat: the keys only get up (before the orbit camera reads them). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !ChairSit.sitting(player)) return;
        var in = event.getInput();
        double l = in.leftImpulse, f = in.forwardImpulse;
        boolean move = l * l + f * f > 1e-6, up = in.jumping || in.shiftKeyDown;
        if ((move || up) && !asked && Minecraft.getInstance().screen == null) {
            byte toward = -1;
            if (move) {
                float yaw = OrbitCamera.onFoot() && CameraDirector.holding(OrbitCamera.ID) ? OrbitCamera.viewYaw() : player.getYRot();
                double y = Math.toRadians(yaw), dx = l * Math.cos(y) - f * Math.sin(y), dz = f * Math.cos(y) + l * Math.sin(y);
                toward = (byte) Direction.getNearest(dx, 0, dz).get2DDataValue();
            }
            AflNetwork.chairToServer(new ChairSit.Stand(toward));
            asked = true;
            askedTicks = 0;
        }
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    /** PlayerSkeleton#solve: the idle for this draw of a sitter; {@code busy}: hands holding or doing something. */
    static Frame frame(Player p, float partial, boolean busy) {
        var level = Minecraft.getInstance().level;
        if (level == null || !ChairSit.sitting(p)) return Frame.NONE;
        double now = (level.getGameTime() + partial) / 20.0;
        Track t = TRACKS.computeIfAbsent(p.getId(), id -> new Track(now));
        double dt = Double.isNaN(t.last) ? 0 : Math.max(0, Math.min(0.1, now - t.last));
        t.last = now;
        t.free = (float) Math.max(0, Math.min(1, t.free + (busy ? -dt : dt) / BUSY_EASE));
        double since = now - t.sat - FIRST;
        if (since < 0) return Frame.NONE;
        int k = (int) Math.floor(since / (IDLE + BETWEEN));
        double in = since - k * (IDLE + BETWEEN);
        if (in >= IDLE) return Frame.NONE;
        float w = (float) Math.min(1, Math.min(in / EASE, (IDLE - in) / EASE));
        w = w * w * (3 - 2 * w) * t.free;
        int kind = fidget(p.getId(), k);
        return new Frame(kind < 2 ? ChairPose.Idle.BOUNCE : ChairPose.Idle.SHIFT, w, (float) in, kind % 2 == 0);
    }

    /** The k-th fidget of this sitter (0..KINDS-1): a hash's pick, never the one before it. */
    private static int fidget(int id, int k) {
        int pick = pick(id, k);
        if (k > 0 && pick == pick(id, k - 1)) pick = (pick + 1) % KINDS;
        return pick;
    }

    private static int pick(int id, int k) {
        int h = id * 0x9E3779B1 + k * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return Math.floorMod(h, KINDS);
    }
}
