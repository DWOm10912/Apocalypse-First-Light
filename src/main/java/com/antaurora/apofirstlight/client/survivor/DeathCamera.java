package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.survivor.SurvivorCorpse;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Ragdoll V1 R-C (docs/dev/ragdoll_v1_plan.md 4): the first-person death camera and what goes with it.
 * <ul>
 *   <li>Once the own player is dead in first person and the own corpse has a ragdoll pose, a camera director request
 *   (PRIORITY_DEATH) puts the view at the corpse head's eye socket, turned with the head, eased in over 0.2 s. It is
 *   lifted to stay {@link #CLEARANCE} above the block under it. The corpse's head is not drawn meanwhile.</li>
 *   <li>The death screen waits until the body has been still for {@link #STILL_TICKS}, at most {@link #MAX_TICKS} after the
 *   death (SurvivorClient asks {@link #holdsDeathScreen}); then the view stays on the corpse behind the screen until the
 *   respawn.</li>
 *   <li>Third person, spectator, or no ragdoll pose: no camera, the death screen is not held (a death with no corpse at
 *   all waits {@link #FIND_TICKS} for it at most). Switching to third person during the fall ends the camera; the
 *   death screen still waits for the body.</li>
 *   <li>A dead player whose corpse is near is not drawn (vanilla would lay a second body down beside the falling one)
 *   and makes no death poof.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class DeathCamera {
    static final String ID = "afl:death_camera";
    /** Ticks a dead player waits for its corpse entity to arrive. */
    static final int FIND_TICKS = 10;
    /** Ticks the body lies still before the death screen; ticks after the death at most. */
    static final int STILL_TICKS = 20, MAX_TICKS = 120;
    static final double CLEARANCE = 0.12, MAX_LIFT = 0.4, EASE = 0.2;
    /** How near an own corpse must lie for a dead player to count as that body. */
    static final double BESIDE = 8.0;

    @Nullable private static SurvivorCorpse own;
    private static int deadTicks, stillTicks;
    private static boolean dead, released;
    @Nullable private static Screen deferred;
    private static final SkeletonPose POSE = new SkeletonPose(SkeletonRig.get());

    private DeathCamera() {
    }

    /** SurvivorClient: the death screen waits (the camera is still following the falling body). */
    public static boolean holdsDeathScreen() {
        return dead && !released;
    }

    /** SurvivorClient: a death screen that wanted to open while held; it opens on release unless the AFL one takes over. */
    public static void defer(Screen screen) {
        deferred = screen;
    }

    /** SurvivorCorpseRenderer: this corpse's head holds the camera (not drawn). */
    static boolean insideHead(SurvivorCorpse corpse) {
        return corpse == own && CameraDirector.isOpen(ID);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.isDeadOrDying()) {
            if (dead) reset();
            return;
        }
        dead = true;
        deadTicks++;
        // F5 during the fall: the view goes back to vanilla's (it stays there until the respawn)
        if (own != null && CameraDirector.isOpen(ID) && !mc.options.getCameraType().isFirstPerson()) CameraDirector.close(ID);
        if (released) return;
        if (own == null) {
            SurvivorCorpse corpse = find(mc.level, player);
            if (corpse == null) {
                if (deadTicks >= FIND_TICKS) release(mc);
                return;
            }
            if (corpse.pose() == null || player.isSpectator() || !mc.options.getCameraType().isFirstPerson()) {
                release(mc);
                return;
            }
            own = corpse;
            CameraDirector.open(new CameraDirector.Request(ID, CameraDirector.PRIORITY_DEATH, DeathCamera::shot,
                    CameraDirector.Clock.GAME, EASE, EASE).hud(CameraDirector.Hud.NO_HUD)
                    .locks(CameraDirector.Lock.LOOK, CameraDirector.Lock.MOVE, CameraDirector.Lock.ATTACK, CameraDirector.Lock.USE));
        }
        if (own.isRemoved()) {
            release(mc);
            return;
        }
        stillTicks = own.falling() ? 0 : stillTicks + 1;
        if (stillTicks >= STILL_TICKS || deadTicks >= MAX_TICKS) release(mc);
    }

    /** The death screen may open now (the deferred vanilla one, when the server sent no survivor summary). */
    private static void release(Minecraft mc) {
        released = true;
        if (deferred != null && !SurvivorClient.hasSummary() && mc.screen == null) mc.setScreen(deferred);
        deferred = null;
    }

    private static void reset() {
        if (CameraDirector.isOpen(ID)) CameraDirector.close(ID);
        own = null;
        dead = released = false;
        deadTicks = stillTicks = 0;
        deferred = null;
    }

    @Nullable
    private static SurvivorCorpse find(Level level, Player player) {
        List<SurvivorCorpse> near = level.getEntitiesOfClass(SurvivorCorpse.class, player.getBoundingBox().inflate(3.0),
                c -> c.ownerPlayer().equals(player.getUUID()));
        // the newest body (a falling one first): older bodies of this account may lie here too
        return near.stream().max(Comparator.<SurvivorCorpse>comparingInt(c -> c.falling() ? 1 : 0)
                .thenComparingInt(c -> -c.tickCount)).orElse(null);
    }

    /** The view out of the own corpse's eyes. */
    @Nullable
    private static CameraPose shot(Camera camera, CameraPose current, float partial) {
        SurvivorCorpse corpse = own;
        if (corpse == null || corpse.isRemoved() || !CorpsePoses.pose(corpse, partial, POSE)) return null;
        Vec3[] eye = CorpsePoses.eye(corpse, POSE);
        if (eye == null) return null;
        Vec3 at = lift(corpse.level(), eye[0]);
        float[] angles = angles(eye[1], eye[2]);
        return new CameraPose(at, angles[0], angles[1], angles[2], Double.NaN);
    }

    /** Keeps the eye CLEARANCE above the collision top of the blocks it is over (lifts at most MAX_LIFT). */
    static Vec3 lift(Level level, Vec3 at) {
        double y = at.y;
        BlockPos base = BlockPos.containing(at);
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos pos = base.offset(0, dy, 0);
            for (AABB box : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                AABB b = box.move(pos);
                if (at.x < b.minX || at.x > b.maxX || at.z < b.minZ || at.z > b.maxZ) continue;
                if (b.minY <= at.y + CLEARANCE && b.maxY + CLEARANCE > y) y = b.maxY + CLEARANCE;
            }
        }
        return new Vec3(at.x, Math.min(y, at.y + MAX_LIFT), at.z);
    }

    /**
     * Minecraft's yaw and pitch (degrees) of a forward vector, and the roll that turns the view's up onto {@code up}:
     * the signed angle from the unrolled up to {@code up} about forward (GameRenderer turns the view by +roll about z).
     * Looking straight up or down, the yaw comes from {@code up} instead.
     */
    public static float[] angles(Vec3 forward, Vec3 up) {
        Vec3 f = forward.normalize();
        double pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, -f.y))));
        double yaw;
        if (Math.abs(f.y) > 0.9995) {
            // the top of the screen is the up vector's way (looking down) or the opposite (looking up)
            Vec3 h = f.y < 0 ? up : up.scale(-1);
            yaw = Math.toDegrees(Math.atan2(-h.x, h.z));
            return new float[]{(float) yaw, (float) pitch, 0F};
        }
        yaw = Math.toDegrees(Math.atan2(-f.x, f.z));
        Vec3 flat = new Vec3(0, 1, 0).subtract(f.scale(f.y)).normalize();
        double roll = Math.toDegrees(Math.atan2(f.dot(flat.cross(up)), flat.dot(up)));
        return new float[]{(float) yaw, (float) pitch, (float) roll};
    }

    /** A dead player with a corpse beside it is not drawn: the corpse is the body now. */
    @SubscribeEvent
    public static void hideDeadPlayer(RenderPlayerEvent.Pre event) {
        if (corpseBeside(event.getEntity())) event.setCanceled(true);
    }

    /**
     * A dead player with an own corpse within {@link #BESIDE} blocks (the falling body may have tumbled a few blocks away
     * from where the player died): not drawn, and no death poof (LivingEntityDeathPoofMixin).
     */
    public static boolean corpseBeside(Player player) {
        return player.isDeadOrDying() && !player.level().getEntitiesOfClass(SurvivorCorpse.class,
                player.getBoundingBox().inflate(BESIDE), c -> c.ownerPlayer().equals(player.getUUID())).isEmpty();
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
