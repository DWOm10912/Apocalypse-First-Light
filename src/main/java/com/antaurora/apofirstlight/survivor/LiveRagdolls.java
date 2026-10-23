package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.FallLanding;
import com.antaurora.apofirstlight.health.HealthPackets;
import com.antaurora.apofirstlight.health.Knockdown;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollBody;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollDefinition;
import com.antaurora.apofirstlight.skeleton.ragdoll.RagdollSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Living ragdolls (docs/gameplay/injury_knockdown_v1_plan.md 9, user 2026-10-22 「现在先做活人布娃娃吧，碰撞照常」): a
 * vehicle's hit that does not kill (vehicle/VehicleImpact, through Knockdown#onDamage) throws the player as the corpse
 * ragdoll falls (Ragdoll V1: the same body, the same push), but alive. On the server, each player tick:
 * <ul>
 *   <li>the body steps in the block world (SurvivorRagdolls#world) and its boxes go to the player and its trackers
 *   (HealthPackets.Ragdoll, FALLING) with the point the player's own box follows (under the pelvis, at the body's lowest
 *   point: the own client moves it there, with collision, so the box stays the ordinary standing one);</li>
 *   <li>meanwhile it counts as falling over (FallLanding#ragdoll: no using, hitting or sprinting) and a vehicle's further
 *   hit pushes the body ({@link #push});</li>
 *   <li>once still (RagdollBody#asleep) it gets up by the knockdown clips from where they lie: on the front body/knock_front
 *   from {@link #FRONT_FROM}, on the back body/knock_back from {@link #BACK_FROM}. The player is put where that lying pose's
 *   root is (pelvis and head on the ragdoll's, {@link #align}) facing its way, if its box fits there; the clients blend
 *   the ragdoll's last pose into the clip (client/survivor/LiveRagdollClient). At health at most collapse.at it stays down
 *   collapsed instead (Knockdown#collapseWhereLying).</li>
 * </ul>
 * A death meanwhile hands the body to the corpse ({@link #take}). Not saved: logging out or changing dimension drops it.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class LiveRagdolls {
    /** HealthPackets.Ragdoll states besides the get-up kinds (FallLanding's). */
    public static final byte FALLING = -1, ENDED = -2;
    /** Where the get-ups start (ticks into the clip): lying, a breath before getting up (author_knockdown_v1.cjs). */
    public static final int FRONT_FROM = 30, BACK_FROM = 28;
    /** Living bodies falling at once, server-wide; more vehicle hits meanwhile knock down by the clips. */
    static final int MAX_ACTIVE = 8;
    /**
     * The lying keys the get-ups start from, model px: the pelvis pivot's distance behind the root (knock.front sprawled:
     * zoff 5; knock.back on the back: zoff 0).
     */
    private static final double FRONT_PELVIS_BACK = 5, BACK_PELVIS_BACK = 0;
    /** How high over the body's lowest point the player's box may be put to fit (blocks). */
    private static final double FIT_UP = 0.6;

    private static final Map<UUID, RagdollBody> BODIES = new HashMap<>();

    private LiveRagdolls() {
    }

    /** Whether this player lies as a living ragdoll now (the server's record). */
    public static boolean active(Player player) {
        return player instanceof ServerPlayer && BODIES.containsKey(player.getUUID());
    }

    /** Whether a vehicle's hit may throw this player as a ragdoll now. */
    public static boolean canStart(ServerPlayer player) {
        return !BODIES.containsKey(player.getUUID()) && BODIES.size() < MAX_ACTIVE && player.isAlive() && !player.isInLava()
                && !player.isInWall() && player.getY() >= player.serverLevel().getMinBuildHeight();
    }

    /** Throws the player: its stance and motion now, the vehicle's push (as a corpse's, SurvivorRagdolls#push). */
    public static void start(ServerPlayer player, DamageSource source, Vec3 thrown) {
        RagdollBody body = SurvivorRagdolls.stance(player);
        SurvivorRagdolls.push(body, source, thrown);
        BODIES.put(player.getUUID(), body);
        FallLanding.ragdoll(player);
        send(player, body);
    }

    /** A further vehicle hit while thrown: the same push on the body (m/s, as VehicleImpact throws). */
    public static void push(ServerPlayer player, Vec3 thrown) {
        RagdollBody body = BODIES.get(player.getUUID());
        if (body == null) return;
        Vector3d j = new Vector3d(thrown.x, thrown.y, thrown.z).mul(SurvivorRagdolls.BODY_MASS * 0.5);
        body.impulse(body.def.pelvis, body.position(body.def.pelvis), new Vector3d(j));
        body.impulse(body.def.chest, body.position(body.def.chest), j);
    }

    /** The death of a thrown player: its body, still falling, for the corpse (SurvivorCorpses#create); null if none. */
    @Nullable
    static RagdollBody take(ServerPlayer player) {
        RagdollBody body = BODIES.remove(player.getUUID());
        if (body != null) FallLanding.ragdollEnd(player);
        return body;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        RagdollBody body = BODIES.get(player.getUUID());
        if (body == null) return;
        if (!player.isAlive() || player.isPassenger() || player.isSpectator()) {
            drop(player);
            return;
        }
        long start = System.nanoTime();
        body.tick(SurvivorRagdolls.world(player.serverLevel()));
        SurvivorRagdolls.measured(player.level().getGameTime(), System.nanoTime() - start);
        if (body.asleep()) rest(player, body);
        else send(player, body);
    }

    /** Every tick while it falls: the boxes about the pelvis, and where the player's box goes. */
    private static void send(ServerPlayer player, RagdollBody body) {
        Vector3d pelvis = body.position(body.def.pelvis);
        Vec3 origin = new Vec3(pelvis.x, pelvis.y, pelvis.z);
        float[] pose = RagdollSnapshot.capture(body, pelvis);
        broadcast(player, new HealthPackets.Ragdoll(player.getId(), origin, pose, FALLING, new Vec3(pelvis.x, body.lowest(), pelvis.z),
                player.getYRot()));
    }

    /** Still: the get-up (or the collapse) from where it lies, the player put at the get-up's root. */
    private static void rest(ServerPlayer player, RagdollBody body) {
        BODIES.remove(player.getUUID());
        boolean down = Knockdown.staysDown(player);
        Lying lying = align(body);
        byte kind = down ? FallLanding.COLLAPSE : lying.faceUp ? FallLanding.KNOCK_BACK : FallLanding.KNOCK_FRONT;
        Vec3 root = fit(player, lying.root, down ? Pose.SWIMMING : Pose.STANDING);
        player.connection.teleport(root.x, root.y, root.z, lying.yaw, player.getXRot());
        player.setYBodyRot(lying.yaw);
        player.setYHeadRot(lying.yaw);
        if (down) {
            FallLanding.ragdollEnd(player);
            Knockdown.collapseWhereLying(player);
        } else {
            FallLanding.ragdollGetUp(player, kind, from(kind));
        }
        Vector3d o = new Vector3d(lying.root.x, lying.root.y, lying.root.z);
        broadcast(player, new HealthPackets.Ragdoll(player.getId(), lying.root, RagdollSnapshot.capture(body, o), kind, lying.root, lying.yaw));
    }

    /** Dropped with no get-up (died another way, mounted, spectating, logged out). */
    private static void drop(ServerPlayer player) {
        if (BODIES.remove(player.getUUID()) == null) return;
        FallLanding.ragdollEnd(player);
        broadcast(player, new HealthPackets.Ragdoll(player.getId(), player.position(), new float[0], ENDED, player.position(), player.getYRot()));
    }

    /** Ticks into the get-up clip it starts from. */
    public static int from(byte kind) {
        return kind == FallLanding.KNOCK_BACK ? BACK_FROM : FRONT_FROM;
    }

    /** A still body's lying: on its back or front, the get-up's root (world, the ground at the lowest point) and yaw. */
    record Lying(boolean faceUp, Vec3 root, float yaw) {}

    /**
     * The get-up that matches the lying body: the chest's front up or down picks the clip; the clip's pelvis pivot goes on
     * the ragdoll's, the line from it to the neck on the ragdoll's (on the front the head is ahead of the root, on the back
     * behind it).
     */
    static Lying align(RagdollBody body) {
        RagdollDefinition def = body.def;
        SkeletonRig rig = def.rig;
        Vector3d o = new Vector3d(body.position(def.pelvis));
        SkeletonPose pose = new SkeletonPose(rig);
        body.pose(pose, o);
        Vector3d hip = RagdollBody.world(pose.point(rig.pelvis, rig.pivot[rig.pelvis], new Vector3f()), o, 0, new Vector3d());
        Vector3d neck = RagdollBody.world(pose.point(rig.head, rig.pivot[rig.head], new Vector3f()), o, 0, new Vector3d());
        Vector3d front = body.orientation(def.chest).transform(new Vector3d(0, 0, -1));
        boolean faceUp = front.y > 0;
        double hx = neck.x - hip.x, hz = neck.z - hip.z, h = Math.sqrt(hx * hx + hz * hz);
        if (h < 1e-3) {
            // upright (sitting): the chest's front is the way
            hx = front.x;
            hz = front.z;
            h = Math.max(1e-6, Math.sqrt(hx * hx + hz * hz));
            faceUp = false;
        } else if (faceUp) {
            hx = -hx;
            hz = -hz;
        }
        hx /= h;
        hz /= h;
        double back = (faceUp ? BACK_PELVIS_BACK : FRONT_PELVIS_BACK) * RagdollDefinition.PX;
        Vec3 root = new Vec3(hip.x + hx * back, body.lowest(), hip.z + hz * back);
        float yaw = (float) Math.toDegrees(Math.atan2(-hx, hz));
        return new Lying(faceUp, root, yaw);
    }

    /** The root, raised up to FIT_UP so the player's box fits; where the player is when it fits nowhere there. */
    private static Vec3 fit(ServerPlayer player, Vec3 root, Pose pose) {
        for (double up = 0.02; up <= FIT_UP; up += 0.1) {
            AABB box = player.getDimensions(pose).makeBoundingBox(root.x, root.y + up, root.z).deflate(1.0E-7);
            if (player.level().noCollision(player, box)) return root.add(0, up, 0);
        }
        return player.position();
    }

    private static void broadcast(ServerPlayer player, HealthPackets.Ragdoll packet) {
        AflNetwork.skeletonTo(player, packet);
        AflNetwork.skeletonToTracking(player, packet);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) drop(player);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) drop(player);
    }
}
