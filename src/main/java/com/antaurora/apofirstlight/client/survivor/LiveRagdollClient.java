package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.client.skeleton.FallLandingClient;
import com.antaurora.apofirstlight.health.ClientKnockdown;
import com.antaurora.apofirstlight.health.FallLanding;
import com.antaurora.apofirstlight.health.HealthPackets;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollBody;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollDefinition;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollSnapshot;
import com.antaurora.apofirstlight.survivor.LiveRagdolls;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A living player's ragdoll on the client (survivor/LiveRagdolls simulates it; docs/gameplay/injury_knockdown_v1_plan.md 9,
 * user 2026-10-22 「被撞那一下镜头没反应 现在先做活人布娃娃吧，碰撞照常」):
 * <ul>
 *   <li>While it falls the server sends its boxes every tick (HealthPackets.Ragdoll, FALLING); one is taken a tick (a few
 *   kept against jitter) and the drawn pose is the last two interpolated by the partial tick (CorpsePoses), put into the
 *   player's own model frame: client/skeleton/PlayerSkeleton draws it in place of the solve. The own player's normal box
 *   follows the point the server sends under the pelvis (moved by vanilla, so it collides as always); in first person the
 *   camera rides the ragdoll's eye socket (CameraDirector, above the cutscenes, under the death camera), turned with the
 *   head, the body drawn under it (client/FirstPersonBody) with its arms (PlayerSkeleton#firstPersonHands).</li>
 *   <li>Once still the server picks the get-up (knock_front on the front, knock_back on the back), puts the player at its
 *   root and yaw, and sends the last pose: the get-up clip plays from where it lies (FallLandingClient#getUpFrom), solved
 *   at that root and yaw whatever the entity's own drawn place and body yaw ({@link #alignedYaw}, {@link #afterSolve}), and
 *   the drawn pose blends from the ragdoll's last over {@link #HANDOVER} s. Low on health it stays down instead
 *   (COLLAPSE): the prone stance, blended into the same way.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class LiveRagdollClient {
    static final String ID = "afl:live_ragdoll";
    /** Over cutscenes (500), under the death camera (600): a death mid-fall takes the view to the corpse. */
    static final int PRIORITY = 550;
    static final double EASE_IN = 0.12, EASE_OUT = 0.35;
    /** The blend from the ragdoll's last pose into the get-up (or the collapse) (s). */
    static final double HANDOVER = 0.4, COLLAPSE_HANDOVER = 0.5;
    /** The get-up clip's blend out after it ends (s; the knock actions' 0.3 and a little). */
    static final double BLEND_OUT = 0.35;
    /** Ticks with no packet before a falling ragdoll is dropped; snapshots kept waiting. */
    static final int LOST_TICKS = 40, QUEUE = 3;
    /** The own box follows the body at most this far a tick (blocks). */
    static final double FOLLOW_MAX = 3;
    private static final int BODIES = RagdollDefinition.get().bodies.length;

    private static final class Live {
        final ArrayDeque<HealthPackets.Ragdoll> queue = new ArrayDeque<>();
        float[] pose, before;
        Vec3 origin, originBefore;
        /** Where the own box goes (world). */
        Vec3 anchor;
        byte state = LiveRagdolls.FALLING;
        Vec3 root;
        float yaw;
        /** The level clock (s) it came to rest at; when it is dropped (s). */
        double rested = Double.NaN, ends;
        int quiet;

        boolean falling() {
            return state == LiveRagdolls.FALLING;
        }
    }

    private static final Map<Integer, Live> LIVE = new HashMap<>();
    @Nullable private static SkeletonPose temp, shotPose;

    private LiveRagdollClient() {
    }

    // ---------------------------------------------------------------- packets

    public static void accept(HealthPackets.Ragdoll packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int id = packet.entity();
        if (packet.state() == LiveRagdolls.ENDED) {
            LIVE.remove(id);
            return;
        }
        if (!RagdollSnapshot.valid(packet.pose(), BODIES)) return;
        Live live = LIVE.get(id);
        if (packet.state() == LiveRagdolls.FALLING) {
            if (live == null || !live.falling()) {
                live = new Live();
                LIVE.put(id, live);
            }
            live.anchor = packet.root();
            live.quiet = 0;
            if (live.pose == null) {
                live.pose = live.before = packet.pose();
                live.origin = live.originBefore = packet.origin();
            } else {
                live.queue.addLast(packet);
                while (live.queue.size() > QUEUE) live.queue.removeFirst();
            }
            return;
        }
        // still: the last pose, and the get-up (or the collapse) from it
        if (live == null) {
            live = new Live();
            LIVE.put(id, live);
        }
        byte kind = packet.state();
        double now = mc.level.getGameTime() / 20.0;
        live.queue.clear();
        live.pose = live.before = packet.pose();
        live.origin = live.originBefore = packet.origin();
        live.state = kind;
        live.root = packet.root();
        live.yaw = packet.yaw();
        live.rested = now;
        LocalPlayer own = mc.player;
        boolean mine = own != null && own.getId() == id;
        Entity e = mc.level.getEntity(id);
        if (e instanceof LivingEntity body) {
            float yaw = packet.yaw();
            if (mine) {
                body.setYRot(yaw);
                body.yRotO = yaw;
            }
            body.setYBodyRot(yaw);
            body.yBodyRotO = yaw;
            body.setYHeadRot(yaw);
            body.yHeadRotO = yaw;
        }
        if (kind == FallLanding.COLLAPSE) {
            live.ends = now + COLLAPSE_HANDOVER;
            if (mine) ClientKnockdown.set(true);
        } else {
            int from = LiveRagdolls.from(kind);
            int length = kind == FallLanding.KNOCK_BACK ? FallLanding.KNOCK_BACK_TICKS : FallLanding.KNOCK_FRONT_TICKS;
            live.ends = now + (length - from) / 20.0 + BLEND_OUT;
            FallLandingClient.getUpFrom(id, kind, from);
        }
        if (mine && CameraDirector.isOpen(ID)) CameraDirector.close(ID);
    }

    // ---------------------------------------------------------------- per tick

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LIVE.clear();
            if (CameraDirector.isOpen(ID)) CameraDirector.close(ID);
            return;
        }
        double now = mc.level.getGameTime() / 20.0;
        for (Iterator<Map.Entry<Integer, Live>> it = LIVE.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Live live = entry.getValue();
            Entity e = mc.level.getEntity(entry.getKey());
            if (!(e instanceof Player p) || !p.isAlive() || p.isPassenger() || p.isSpectator()) {
                it.remove();
                continue;
            }
            if (live.falling()) {
                HealthPackets.Ragdoll next = live.queue.pollFirst();
                live.before = live.pose;
                live.originBefore = live.origin;
                if (next != null) {
                    live.pose = next.pose();
                    live.origin = next.origin();
                }
                if (++live.quiet > LOST_TICKS) it.remove();
            } else if (now > live.ends) {
                it.remove();
            }
        }
        LocalPlayer own = mc.player;
        boolean view = own != null && own.isAlive() && ownFalling() && mc.options.getCameraType().isFirstPerson();
        if (view && !CameraDirector.isOpen(ID)) {
            CameraDirector.open(new CameraDirector.Request(ID, PRIORITY, LiveRagdollClient::shot, CameraDirector.Clock.GAME,
                    EASE_IN, EASE_OUT).hud(CameraDirector.Hud.NO_HANDS)
                    .locks(CameraDirector.Lock.LOOK, CameraDirector.Lock.MOVE, CameraDirector.Lock.ATTACK, CameraDirector.Lock.USE));
        } else if (!view && CameraDirector.isOpen(ID)) {
            CameraDirector.close(ID);
        }
    }

    /** The own box follows the falling body (vanilla moves it: it collides as always). */
    @SubscribeEvent
    public static void follow(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof LocalPlayer own)) return;
        Live live = LIVE.get(own.getId());
        if (live == null || !live.falling() || live.anchor == null) return;
        Vec3 d = live.anchor.subtract(own.position());
        double length = d.length();
        if (length > FOLLOW_MAX) d = d.scale(FOLLOW_MAX / length);
        own.setDeltaMovement(d);
    }

    // ---------------------------------------------------------------- queries

    /** The own player is a falling ragdoll now (FallLandingClient: no moving, using or turning the view). */
    public static boolean ownFalling() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Live live = LIVE.get(mc.player.getId());
        return live != null && live.falling();
    }

    /** This player is a ragdoll, falling or blending into its get-up (PlayerSkeleton#firstPersonHands: the body's arms). */
    public static boolean active(Player p) {
        return LIVE.containsKey(p.getId());
    }

    /** client/FirstPersonBody#shown: the own camera is on the ragdoll's head (the body is still the first-person body). */
    public static boolean cameraOnBody(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && camera.getEntity() == mc.player && LIVE.containsKey(mc.player.getId())
                && mc.options.getCameraType().isFirstPerson();
    }

    /** The yaw the get-up stands at while it plays (PlayerSkeleton solves it there), or NaN. */
    public static float alignedYaw(Player p) {
        Live live = LIVE.get(p.getId());
        return live == null || live.falling() || live.state == FallLanding.COLLAPSE ? Float.NaN : live.yaw;
    }

    /** PlayerSkeleton.State: the hips and chest keep the yaw it lies at from the rest on, or NaN. */
    public static float bodyYaw(Player p) {
        Live live = LIVE.get(p.getId());
        return live == null || live.falling() ? Float.NaN : live.yaw;
    }

    // ---------------------------------------------------------------- poses

    /**
     * PlayerSkeleton#solve, in place of the solve: the falling ragdoll's pose this frame in the draw's model frame
     * ({@code origin} the drawn entity position, {@code toModel} from it into model blocks); false when it is not one.
     */
    public static boolean pose(Player p, float partial, Vec3 origin, Matrix4f toModel, SkeletonPose out) {
        Live live = LIVE.get(p.getId());
        if (live == null || !live.falling() || live.pose == null) return false;
        SkeletonPose t = temp(out.rig);
        CorpsePoses.pose(live.pose, live.origin, live.before, live.originBefore, partial, t);
        Matrix4f m = frame(live.origin, 0F, origin, toModel);
        for (int b = 0; b < out.world.length; b++) {
            m.mul(t.world[b], out.world[b]);
            out.squash[b] = 1F;
        }
        relocal(out);
        out.shoulderDrop = 0F;
        return true;
    }

    /**
     * PlayerSkeleton#solve after the arms: the get-up clip (solved with no root yaw) moved to the root and yaw the server put
     * it at, and the blend from the ragdoll's last pose.
     */
    public static void afterSolve(Player p, float partial, Vec3 origin, Matrix4f toModel, SkeletonPose out) {
        Live live = LIVE.get(p.getId());
        if (live == null || live.falling() || live.pose == null) return;
        if (live.state != FallLanding.COLLAPSE) {
            Matrix4f g = frame(live.root, live.yaw, origin, toModel), w = new Matrix4f();
            for (int b = 0; b < out.world.length; b++) out.world[b].set(g.mul(out.world[b], w));
            relocal(out);
        }
        double t = now(partial) - live.rested;
        double handover = live.state == FallLanding.COLLAPSE ? COLLAPSE_HANDOVER : HANDOVER;
        if (!(t < handover)) return;
        SkeletonPose rest = temp(out.rig);
        CorpsePoses.pose(live.pose, live.origin, live.pose, live.origin, 0F, rest);
        Matrix4f m = frame(live.origin, 0F, origin, toModel);
        for (int b = 0; b < rest.world.length; b++) rest.world[b].set(m.mul(rest.world[b], new Matrix4f()));
        float f = (float) Mth.clamp(t / handover, 0, 1);
        blend(rest, out, f * f * (3 - 2 * f), out);
    }

    /** The camera on the ragdoll's eye socket, turned with its head. */
    @Nullable
    private static CameraPose shot(Camera camera, CameraPose current, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        Live live = LIVE.get(mc.player.getId());
        if (live == null || !live.falling() || live.pose == null) return null;
        if (shotPose == null) shotPose = new SkeletonPose(SkeletonRig.get());
        CorpsePoses.pose(live.pose, live.origin, live.before, live.originBefore, partial, shotPose);
        Vec3[] eye = CorpsePoses.eye(shotPose, live.origin);
        if (eye == null) return null;
        Vec3 at = DeathCamera.lift(mc.level, eye[0]);
        float[] angles = DeathCamera.angles(eye[1], eye[2]);
        return new CameraPose(at, angles[0], angles[1], angles[2], Double.NaN);
    }

    /**
     * Model px of a body standing at {@code at} facing {@code yaw} (RagdollBody#world) into this draw's model px:
     * {@code 16 · toModel · T(at − origin) · facing(yaw) · PX · T(0, −ORIGIN_Y, 0)}.
     */
    private static Matrix4f frame(Vec3 at, float yaw, Vec3 origin, Matrix4f toModel) {
        Quaterniond f = RagdollBody.facing(yaw);
        return new Matrix4f().scaling(16F).mul(toModel)
                .translate((float) (at.x - origin.x), (float) (at.y - origin.y), (float) (at.z - origin.z))
                .rotate(new Quaternionf((float) f.x, (float) f.y, (float) f.z, (float) f.w))
                .scale(RagdollDefinition.PX).translate(0F, (float) -RagdollBody.ORIGIN_Y, 0F);
    }

    /** Each bone's pivot and turn from {@code a} to {@code b} by {@code w} (into {@code out}, which may be {@code b}). */
    private static void blend(SkeletonPose a, SkeletonPose b, float w, SkeletonPose out) {
        SkeletonRig rig = out.rig;
        Vector3f pa = new Vector3f(), pb = new Vector3f();
        Quaternionf ra = new Quaternionf(), rb = new Quaternionf();
        for (int i = 0; i < out.world.length; i++) {
            Vector3f pivot = rig.pivot[i];
            a.world[i].transformPosition(pivot, pa);
            b.world[i].transformPosition(pivot, pb);
            a.world[i].getNormalizedRotation(ra);
            b.world[i].getNormalizedRotation(rb);
            float dot = ra.x * rb.x + ra.y * rb.y + ra.z * rb.z + ra.w * rb.w, s = dot < 0 ? -1 : 1;
            rb.set(ra.x + (s * rb.x - ra.x) * w, ra.y + (s * rb.y - ra.y) * w, ra.z + (s * rb.z - ra.z) * w,
                    ra.w + (s * rb.w - ra.w) * w).normalize();
            pa.lerp(pb, w);
            out.squash[i] = Mth.lerp(w, a.squash[i], b.squash[i]);
            out.world[i].translation(pa).rotate(rb).translate(-pivot.x, -pivot.y, -pivot.z);
        }
        out.shoulderDrop = Mth.lerp(w, a.shoulderDrop, b.shoulderDrop);
        relocal(out);
    }

    /** Local turns and offsets to match the skinning matrices (SkeletonPose#half needs them; as RagdollBody#pose). */
    private static void relocal(SkeletonPose pose) {
        SkeletonRig rig = pose.rig;
        Matrix4f inv = new Matrix4f(), rel = new Matrix4f();
        Vector3f p = new Vector3f();
        for (int b = 0; b < pose.world.length; b++) {
            int parent = rig.parent[b];
            if (parent < 0) rel.set(pose.world[b]);
            else inv.set(pose.world[parent]).invert().mul(pose.world[b], rel);
            rel.getNormalizedRotation(pose.local[b]);
            Vector3f pivot = rig.pivot[b];
            rel.transformPosition(p.set(pivot), pose.offset[b]).sub(pivot);
        }
    }

    private static SkeletonPose temp(SkeletonRig rig) {
        if (temp == null || temp.rig != rig) temp = new SkeletonPose(rig);
        return temp;
    }

    private static double now(float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : (level.getGameTime() + partial) / 20.0;
    }
}
