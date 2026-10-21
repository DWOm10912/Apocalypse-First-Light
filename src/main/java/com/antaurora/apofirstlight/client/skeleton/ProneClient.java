package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.health.ClientLightningInjury;
import com.antaurora.apofirstlight.health.LightningPackets;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.ProneCrawl;
import com.antaurora.apofirstlight.skeleton.SkeletonClips;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Lying on the front on land, drawn by the skeleton (docs/gameplay/lightning_injury_v1_plan.md 10; clips
 * prone.animation.json, tools/medical-actions-v1a/author_prone_v1.cjs). Every crawl pose out of water: vanilla's crawl
 * through a one-block gap, and lying after a lightning strike.
 * <ul>
 * <li>the clips on the skeleton's stance layer (PlayerSkeleton#solve): prone.out knocked out (the face on the ground),
 * prone.drag crawling on paralysed legs, prone.crawl any other crawl (a knee pushing with its elbow), prone.idle lying
 * still; why a player lies comes with LightningPackets.Lying (the own one from ClientLightningInjury);</li>
 * <li>the stride: the crawl clips' phase is the distance crawled along the body (ProneCrawl#phase), so a pulling palm
 * stays where it is on the ground; the own input follows the stride (× speed / mean at the phase), so the body goes in
 * jerks, nearly still while an arm reaches, fastest mid-pull, at vanilla's mean speed;</li>
 * <li>the body: its yaw follows the way it crawls; lying still the view turns first and the body follows on the elbows
 * once it is more than {@link #LAG} deg behind;</li>
 * <li>the hands: crawling, both are on the ground: the held item is put away and cannot be used ({@link #stowed});
 * lying still it can;</li>
 * <li>the own first-person camera on the posed eye (a CameraDirector shot easing in and out over {@link #CAMERA_EASE}),
 * kept within {@link #CAMERA_REACH} of the entity's eye across so it never looks through a wall in front.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ProneClient {
    public enum Kind { CRAWL, DRAG, OUT }

    private static final String IDLE = "prone.idle", CRAWL = "prone.crawl", DRAG = "prone.drag", OUT = "prone.out";
    /** Lying down and up again (s), crawling and still (s). */
    private static final float EASE = 0.35F, SETTLE = 0.4F;
    /** Lying still, the body follows the view once this far behind (deg), turning at TURN deg a tick down to TURN_TO. */
    private static final float LAG = 60F, TURN = 4.5F, TURN_TO = 15F;
    /** Crawling, the body turns toward the way it goes at most this much a tick (deg). */
    private static final float STEER = 8F;
    /** Turning on the spot, the elbows move as for this many blocks a degree (90 deg: about one pull). */
    private static final double TURN_BLOCKS = 0.006;
    /** Moved less than this a tick (blocks): lying still. */
    private static final double STILL = 0.004;
    /** The clips' eye is about 10 px ahead of the hips' pivot; the body goes back this much (model px) so it is ~3 px ahead. */
    private static final float BACK = 7F;
    private static final String CAMERA = "afl_prone";
    private static final double CAMERA_EASE = 0.35, CAMERA_REACH = 0.2;
    /**
     * The own first-person arms (#firstPersonReach): at most this far forward (model px); the upper arm's top is this far
     * above its pivot and this half wide (px, vanilla's arm box), its corners kept this far under the view's edge (deg).
     */
    private static final float REACH = 9F, SHOULDER_ABOVE = 2F, SHOULDER_HALF = 2F, SHOULDER_MARGIN = 4F,
            REACH_FADE_FROM = 40F, REACH_FADE_TO = 60F;

    private static final class Track {
        float weight, weightO, move, moveO, yaw, yawO;
        double blocks, blocksO;
        Kind kind = Kind.CRAWL;
        boolean lying, turning;

        float weight(float partial) {
            return smooth(Mth.lerp(partial, weightO, weight));
        }

        float move(float partial) {
            return smooth(Mth.lerp(partial, moveO, move));
        }
    }

    /** What this draw's lying does: its weight, and the crawl's share of it (0 still, 1 crawling). */
    public record Lie(float weight, float move, Kind kind) {}

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    /** Why the others lie (LightningPackets.Lying), by entity id. */
    private static final Map<Integer, Byte> LYING = new HashMap<>();
    private static Vec3 eye;
    private static long eyeFrame = -10, frame;

    private ProneClient() {
    }

    /** LightningPackets.Lying. */
    public static void lying(int entity, byte kind) {
        if (kind == LightningPackets.Lying.NONE) LYING.remove(entity);
        else LYING.put(entity, kind);
    }

    /** On the front out of water, drawn by the skeleton (PlayerSkeleton#supportedPose). */
    public static boolean prone(Player p) {
        return p.isVisuallyCrawling() && !p.isPassenger() && !p.isFallFlying() && !p.isAutoSpinAttack();
    }

    private static Kind kind(Player p) {
        if (p == Minecraft.getInstance().player)
            return ClientLightningInjury.fainted() ? Kind.OUT : ClientLightningInjury.paralysed() ? Kind.DRAG : Kind.CRAWL;
        Byte k = LYING.get(p.getId());
        return k == null ? Kind.CRAWL : k == LightningPackets.Lying.OUT ? Kind.OUT : k == LightningPackets.Lying.DRAG ? Kind.DRAG : Kind.CRAWL;
    }

    // ---------------------------------------------------------------- per tick

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRACKS.clear();
            LYING.clear();
            return;
        }
        if (mc.isPaused()) return;
        if (!PlayerSkeleton.enabled()) {
            TRACKS.clear();
            return;
        }
        for (AbstractClientPlayer p : mc.level.players()) {
            boolean lying = prone(p);
            Track t = TRACKS.get(p.getId());
            if (t == null) {
                if (!lying) continue;
                t = new Track();
                t.yaw = t.yawO = p.getYHeadRot();
                TRACKS.put(p.getId(), t);
            }
            tick(p, t, lying);
            if (!t.lying && t.weight <= 0F && t.weightO <= 0F) TRACKS.remove(p.getId());
        }
        TRACKS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private static void tick(AbstractClientPlayer p, Track t, boolean lying) {
        t.weightO = t.weight;
        t.moveO = t.move;
        t.yawO = t.yaw;
        t.blocksO = t.blocks;
        t.lying = lying;
        t.weight = Mth.clamp(t.weight + (lying ? 1F : -1F) / (EASE * 20F), 0F, 1F);
        if (!lying) {
            t.move = Math.max(0F, t.move - 1F / (SETTLE * 20F));
            return;
        }
        t.kind = kind(p);
        double dx = p.getX() - p.xo, dz = p.getZ() - p.zo, moved = Math.sqrt(dx * dx + dz * dz);
        boolean crawling = moved > STILL && t.kind != Kind.OUT;
        float turned = 0;
        if (crawling) {
            // the body turns toward the way it goes; backwards it keeps facing ahead
            float way = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = Mth.wrapDegrees(way - t.yaw);
            boolean back = Math.abs(rel) > 112.5F;
            if (back) rel = Mth.wrapDegrees(rel + 180);
            turned = Mth.clamp(rel, -STEER, STEER);
            t.blocks += back ? -moved : moved;   // backwards plays the stride back
            t.turning = false;
        } else if (t.kind != Kind.OUT) {
            // lying still: the view first, the body after it on the elbows once it is LAG behind, round to TURN_TO
            float lag = Mth.wrapDegrees(p.getYHeadRot() - t.yaw);
            if (Math.abs(lag) > LAG) t.turning = true;
            else if (Math.abs(lag) <= TURN_TO) t.turning = false;
            if (t.turning) {
                turned = Math.signum(lag) * Math.min(TURN, Math.abs(lag));
                t.blocks += Math.abs(turned) * TURN_BLOCKS;
            }
        }
        t.yaw += turned;
        boolean moving = crawling || Math.abs(turned) > 1e-3F;
        t.move = Mth.clamp(t.move + (moving ? 1F : -1F) / (SETTLE * 20F), 0F, 1F);
    }

    /** The body's yaw lying down (deg), or NaN: PlayerSkeleton's pelvis and chest keep to it. */
    static float bodyYaw(Player p, float partial) {
        Track t = TRACKS.get(p.getId());
        return t == null || t.weight <= 0F && t.weightO <= 0F ? Float.NaN : Mth.rotLerp(partial, t.yawO, t.yaw);
    }

    /** Crawling (or knocked out): the hands are on the ground, the held item put away and not used. */
    public static boolean stowed(Player p) {
        Track t = TRACKS.get(p.getId());
        return t != null && t.lying && (t.kind == Kind.OUT || t.move > 0F || t.moveO > 0F);
    }

    // ---------------------------------------------------------------- the own input

    /** The stride: the forward and sideways input × speed / mean at the phase (ProneCrawl). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        Track t = TRACKS.get(player.getId());
        if (t == null || !t.lying || t.kind == Kind.OUT) return;
        var input = event.getInput();
        float f = ProneCrawl.speed(ProneCrawl.phase(t.blocks)) / ProneCrawl.MEAN;
        input.forwardImpulse *= f;
        input.leftImpulse *= f;
    }

    /** Crawling: no using or swinging what is in the hand. */
    @SubscribeEvent
    public static void keys(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !stowed(player)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void stopUsing(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null && mc.player.isUsingItem() && stowed(mc.player)) mc.gameMode.releaseUsingItem(mc.player);
    }

    // ---------------------------------------------------------------- the body

    /**
     * PlayerSkeleton#solve, before the body solve: this player's lying clips on the stance layer, the body set back so
     * the eye is near the entity's, or null when it does not lie.
     */
    @Nullable
    static Lie stance(Player p, HumanoidPoser.Input in, SkeletonRig rig, float partial) {
        Track t = TRACKS.get(p.getId());
        if (t == null) return null;
        float w = t.weight(partial);
        if (w <= 0F) return null;
        float move = t.kind == Kind.OUT ? 0F : t.move(partial);
        float time = (p.tickCount + partial) / 20F;
        boolean[] mask = new boolean[rig.size()];
        java.util.Arrays.fill(mask, true);
        SkeletonClips.Clip still = SkeletonClips.byKey(t.kind == Kind.OUT ? OUT : IDLE);
        SkeletonClips.Clip crawl = SkeletonClips.byKey(t.kind == Kind.DRAG ? DRAG : CRAWL);
        if (still == null || crawl == null) return null;
        if (move < 1F) in.stance.add(new HumanoidPoser.Input.StanceClip(still, time, w * (1F - move), mask));
        if (move > 0F) {
            float phase = ProneCrawl.phase(Mth.lerp(partial, t.blocksO, t.blocks));
            in.stance.add(new HumanoidPoser.Input.StanceClip(crawl, phase * ProneCrawl.CYCLE_SECONDS, w * move, mask));
        }
        in.rootOffset.add(new Quaternionf().rotationY((float) Math.toRadians(in.rootYaw)).transform(new Vector3f(0, 0, BACK * w)));
        in.eyeTarget = null;
        in.chestYaw *= 1F - w;
        in.chestPitch *= 1F - w;
        if (t.kind == Kind.OUT) in.headClip = w;
        return new Lie(w, move, t.kind);
    }

    // ---------------------------------------------------------------- the own camera

    /** After the solve of the own player's level draw while lying: its posed eye (world). */
    static void solved(Player p, SkeletonPose pose, Vec3 origin, Matrix4f toModel) {
        Minecraft mc = Minecraft.getInstance();
        if (p != mc.player) return;
        Vector3f e = new Matrix4f(toModel).invert().transformPosition(ActionPose.cameraEye(pose).div(16F));
        eye = origin.add(e.x, e.y, e.z);
        eyeFrame = frame;
        Track t = TRACKS.get(p.getId());
        if (mc.options.getCameraType().isFirstPerson() && t != null && t.lying && !CameraDirector.holding(CAMERA))
            CameraDirector.open(new CameraDirector.Request(CAMERA, CameraDirector.PRIORITY_BODY_EYE, ProneClient::shot,
                    CameraDirector.Clock.GAME, CAMERA_EASE, CAMERA_EASE));
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        frame++;
        Minecraft mc = Minecraft.getInstance();
        Track t = mc.player == null ? null : TRACKS.get(mc.player.getId());
        if ((t == null || !t.lying) && CameraDirector.holding(CAMERA)) CameraDirector.close(CAMERA);
    }

    @Nullable
    private static CameraPose shot(Camera camera, CameraPose current, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || eye == null || frame - eyeFrame > 2) return null;
        // across, no further than CAMERA_REACH from the entity's eye: in a one-block gap the wall ahead stays out of view
        Vec3 at = mc.player.getEyePosition(partialTick);
        double dx = eye.x - at.x, dz = eye.z - at.z, d = Math.sqrt(dx * dx + dz * dz);
        double k = d > CAMERA_REACH ? CAMERA_REACH / d : 1;
        return new CameraPose(new Vec3(at.x + dx * k, eye.y, at.z + dz * k), current.yaw(), current.pitch());
    }

    /** FirstPersonBody#shown: the own camera is on the lying body's eye. */
    public static boolean cameraOnBody(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && camera.getEntity() == mc.player && CameraDirector.isOpen(CAMERA) && eye != null && frame - eyeFrame <= 2;
    }

    /**
     * FirstPersonBody#shown: the own player lies, so its body is drawn in first person from the first frame on; that draw
     * is what puts the eye and opens the camera (#solved), which #cameraOnBody waits for.
     */
    public static boolean ownLying(Player p) {
        Track t = TRACKS.get(p.getId());
        return t != null && t.lying;
    }

    /**
     * The own first-person lying body's arms come forward along the view by this much (model px), so the reaching palm is
     * in the bottom of the view looking ahead (the clips' palm is 2.4 px ahead of the eye and 5.9 px below it, 68 deg
     * down). The tops of the upper arms they come off (their corners) stay {@link #SHOULDER_MARGIN} deg under the view's
     * bottom edge, for wherever the view turns from the body: the reach is
     * the most up to {@link #REACH} that keeps both out. Looking down the hands are in view where they are, so the reach
     * is gone from {@link #REACH_FADE_FROM} to {@link #REACH_FADE_TO} deg.
     *
     * @param eye   the camera (model px)
     * @param ahead the view's level direction (model, unit, y 0)
     * @param pitch the camera's pitch (deg, down +)
     */
    static float firstPersonReach(Player p, float partial, SkeletonPose pose, Vector3f eye, Vector3f ahead, float pitch) {
        Track t = TRACKS.get(p.getId());
        if (t == null) return 0F;
        float w = t.weight(partial);
        if (w <= 0F) return 0F;
        float fade = 1F - smooth((pitch - REACH_FADE_FROM) / (REACH_FADE_TO - REACH_FADE_FROM));
        float most = REACH * fade * w;
        if (most <= 0F) return 0F;
        double half = com.antaurora.apofirstlight.client.ViewFov.world() / 2, cos = Math.cos(Math.toRadians(pitch)), sin = Math.sin(Math.toRadians(pitch));
        // the four corners of each upper arm's top (vanilla's 4 x 4 arm box, its top SHOULDER_ABOVE over the pivot)
        float[] along = new float[8], drop = new float[8];
        int n = 0;
        for (String bone : new String[]{"upper_arm_r", "upper_arm_l"}) {
            int b = pose.rig.find(bone);
            if (b < 0) continue;
            Vector3f pivot = pose.rig.pivot[b];
            for (int c = 0; c < 4; c++) {
                Vector3f corner = new Vector3f(pivot).add((c & 1) == 0 ? -SHOULDER_HALF : SHOULDER_HALF, -SHOULDER_ABOVE,
                        (c & 2) == 0 ? -SHOULDER_HALF : SHOULDER_HALF);
                Vector3f v = pose.point(b, corner, new Vector3f()).sub(eye);
                along[n] = v.dot(ahead);
                drop[n++] = v.y;   // model y is down
            }
        }
        for (float reach = most; reach > 0F; reach -= 0.25F) {
            boolean out = true;
            for (int i = 0; i < n && out; i++) {
                double a = along[i] + reach, d = drop[i];
                out = Math.toDegrees(Math.atan2(d * cos - a * sin, a * cos + d * sin)) >= half + SHOULDER_MARGIN;
            }
            if (out) return reach;
        }
        return 0F;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3 - 2 * x);
    }
}
