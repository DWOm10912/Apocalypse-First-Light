package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.health.BodyPart;
import com.antaurora.apofirstlight.health.ClientHealth;
import com.antaurora.apofirstlight.health.SplintItem;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.sit.GroundSeat;
import com.antaurora.apofirstlight.sit.GroundSit;
import com.antaurora.apofirstlight.skeleton.HumanoidPoser;
import com.antaurora.apofirstlight.skeleton.SkeletonClips;
import com.antaurora.apofirstlight.skeleton.SkeletonPose;
import com.antaurora.apofirstlight.skeleton.SkeletonRig;
import com.antaurora.apofirstlight.skeleton.action.ActionPose;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Sitting on the ground on the client (docs/gameplay/splint_animation_v2_plan.md 3; server: sit/GroundSit):
 * <ul>
 * <li>the sit key (N): sit down / get up; seated, jump or sneak get up too, and the movement keys do nothing (not while
 * the hands splint a leg);</li>
 * <li>the body's clips on the skeleton's stance layer under any action (PlayerSkeleton#solve): body.sit_down as it sits
 * down, body.sit while it sits, body.stand_up as it gets up (all as this client first saw them), mirrored when the right
 * leg is the broken one (the clips keep the left leg out straight); the legs stay the sitting ones under an item's action
 * (only the leg splint's own clip poses them);</li>
 * <li>the own first-person camera on the posed eye while seated and getting up or down (a CameraDirector shot, eased off
 * over {@link #CAMERA_OUT} once standing), the first-person body drawn meanwhile ({@link #cameraOnBody}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GroundSitClient {
    public static final KeyMapping SIT = new KeyMapping("key.apocalypse_firstlight.sit", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.apocalypse_firstlight");
    private static final String SIT_DOWN = "body.sit_down", SITTING = "body.sit", STAND_UP = "body.stand_up";
    private static final float SIT_SECONDS = GroundSit.SIT_TICKS / 20F, STAND_SECONDS = GroundSit.STAND_TICKS / 20F;
    /** Off the seat without getting up (the ground went, water): the sitting pose eases off over this (s). */
    private static final float DROP_OFF = 0.3F;
    /** A leg's splint this far in (ticks, past its action's 0.3 s blend-in) sets which leg the sitting keeps out. */
    private static final int MIRROR_UNDER_TICKS = 8;
    private static final String CAMERA = "afl_ground_sit";
    /** The item actions done sat that pose the whole body themselves (the leg's splint and dressings). */
    private static final java.util.Set<String> FULL_BODY = java.util.Set.of("item/splint_leg", "item/bandage_leg", "item/alcohol_leg");
    private static final double CAMERA_OUT = 0.35;

    private static final class Track {
        final int seat;
        boolean mirror;
        final double sat;
        double rising = Double.NaN, left = Double.NaN;
        boolean hinted;

        Track(int seat, boolean mirror, double sat) {
            this.seat = seat;
            this.mirror = mirror;
            this.sat = sat;
        }
    }

    /** What the sitting does to this draw: the stance's weight and whether the body is getting up or down. */
    public record Sit(float weight, boolean moving) {}

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static boolean wasUp;
    private static Vec3 eye;
    private static long eyeFrame = -10, frame;

    private GroundSitClient() {
    }

    // ---------------------------------------------------------------- keys

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            TRACKS.clear();
            return;
        }
        while (SIT.consumeClick()) {
            if (mc.screen != null || player.isSpectator()) continue;
            if (GroundSit.seat(player) == null) AflNetwork.sitToServer(new GroundSit.Request(true));
            else if (!GroundSit.treatingLeg(player)) AflNetwork.sitToServer(new GroundSit.Request(false));
        }
        GroundSeat seat = GroundSit.seat(player);
        Track t = TRACKS.get(player.getId());
        if (seat != null && t != null && !t.hinted && seat.state() == GroundSeat.State.SAT) {
            t.hinted = true;
            player.displayClientMessage(Component.translatable("hint.apocalypse_firstlight.sit.stand", SIT.getTranslatedKeyMessage()), true);
        }
        TRACKS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    /** Seated: no walking; jump or sneak get up (not while splinting a leg), and never reach the server as a dismount. */
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        var input = event.getInput();
        if (GroundSit.seat(player) == null) {
            wasUp = input.jumping || input.shiftKeyDown;
            return;
        }
        boolean up = input.jumping || input.shiftKeyDown;
        if (up && !wasUp && !GroundSit.treatingLeg(player)) AflNetwork.sitToServer(new GroundSit.Request(false));
        wasUp = up;
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
    }

    // ---------------------------------------------------------------- the body

    /**
     * PlayerSkeleton#solve, before the body solve of a level draw: this player's sitting clips on the stance layer, or null
     * when it neither sits nor gets up or down.
     */
    @Nullable
    static Sit stance(Player p, HumanoidPoser.Input in, SkeletonRig rig, float partial) {
        double now = now(partial);
        GroundSeat seat = GroundSit.seat(p);
        Track t = TRACKS.get(p.getId());
        if (seat != null) {
            if (t == null || t.seat != seat.getId()) {
                // first seen: sitting down now, or already sat (it came into view seated)
                t = new Track(seat.getId(), mirror(p), now - (seat.state() == GroundSeat.State.SITTING_DOWN ? 0 : SIT_SECONDS));
                TRACKS.put(p.getId(), t);
            }
            if (seat.state() == GroundSeat.State.GETTING_UP && Double.isNaN(t.rising)) t.rising = now;
            // a leg's splint or dressing (both legs: the second one) keeps that leg out after it: switched under the action once
            // it has blended in (the action poses the whole body), so the swap does not show
            BodyPart leg = GroundSit.treatedLeg(p);
            if (leg != null && p.getTicksUsingItem() >= MIRROR_UNDER_TICKS) t.mirror = leg.right();
        } else if (t != null && Double.isNaN(t.left)) {
            t.left = now;
        }
        if (t == null) return null;
        String clip;
        float time, weight = 1F;
        if (!Double.isNaN(t.rising)) {
            clip = STAND_UP;
            time = (float) (now - t.rising);
            if (seat == null && time >= STAND_SECONDS) {
                TRACKS.remove(p.getId());
                return null;
            }
        } else if (seat == null) {
            clip = SITTING;
            time = 0F;
            weight = 1F - (float) ((now - t.left) / DROP_OFF);
            if (weight <= 0F) {
                TRACKS.remove(p.getId());
                return null;
            }
        } else {
            time = (float) (now - t.sat);
            clip = time < SIT_SECONDS ? SIT_DOWN : SITTING;
            if (clip.equals(SITTING)) time -= SIT_SECONDS;
        }
        SkeletonClips.Clip c = SkeletonClips.byKey(clip);
        if (c == null) return null;
        boolean[] mask = new boolean[rig.size()];
        java.util.Arrays.fill(mask, true);
        in.stance.add(new HumanoidPoser.Input.StanceClip(c, Math.max(0F, time), weight, mask, t.mirror));
        return new Sit(weight, !clip.equals(SITTING));
    }

    /**
     * Sat, the look does not bend the chest (HumanoidPoser#chestPitch standing): the clips' chest is keyed with the arms on
     * the knee and thigh, the head alone looks down.
     */
    static float chestPitch(Sit sit, float standing) {
        return Mth.lerp(sit.weight(), standing, 0F);
    }

    /**
     * After ActionPose#body while sitting: an item's action keeps to the upper body (the legs and hips stay the sitting
     * ones); only the leg's splint and dressings (seated themselves) pose the whole body.
     */
    static void keepLegs(HumanoidPoser.Input in, SkeletonRig rig, PlayerAction action) {
        if (in.actionMask == null || FULL_BODY.contains(action.id)) return;
        boolean[] m = in.actionMask.clone();
        for (int b : new int[]{rig.root, rig.pelvis, rig.thighR, rig.shinR, rig.footR, rig.thighL, rig.shinL, rig.footL}) m[b] = false;
        in.actionMask = m;
    }

    /**
     * The legs' clips keep the left leg out: the right one's when it is the broken one (the one a splint goes on), decided
     * as the player sits down; a leg's splint later sets it to its own leg ({@link #stance}).
     */
    private static boolean mirror(Player p) {
        BodyPart target = SplintItem.target(p);
        if (target == BodyPart.RIGHT_LEG) return true;
        if (target == BodyPart.LEFT_LEG) return false;
        int broken = ClientHealth.marks(p.getUUID()).broken();
        return (broken & BodyPart.RIGHT_LEG.bit()) != 0 && (broken & BodyPart.LEFT_LEG.bit()) == 0;
    }

    /** The legs' clips keep the right leg out for this player now (the leg splint's side; BandageWrapRenderer). */
    public static boolean rightLegOut(Player p) {
        Track t = TRACKS.get(p.getId());
        return t != null && t.mirror;
    }

    /** The way the body sits (deg), or NaN: PlayerSkeleton's pelvis keeps to it. */
    static float seatYaw(Player p) {
        GroundSeat seat = GroundSit.seat(p);
        return seat == null ? Float.NaN : seat.getYRot();
    }

    // ---------------------------------------------------------------- the own camera

    /** After the solve of the own player's level draw while sitting: its posed eye (world). */
    static void solved(Player p, SkeletonPose pose, Vec3 origin, Matrix4f toModel) {
        Minecraft mc = Minecraft.getInstance();
        if (p != mc.player) return;
        Vector3f e = new Matrix4f(toModel).invert().transformPosition(ActionPose.cameraEye(pose).div(16F));
        eye = origin.add(e.x, e.y, e.z);
        eyeFrame = frame;
        if (mc.options.getCameraType().isFirstPerson() && !CameraDirector.holding(CAMERA))
            CameraDirector.open(new CameraDirector.Request(CAMERA, CameraDirector.PRIORITY_BODY_EYE, GroundSitClient::shot,
                    CameraDirector.Clock.GAME, 0, CAMERA_OUT));
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        frame++;
        Minecraft mc = Minecraft.getInstance();
        boolean sitting = mc.player != null && TRACKS.containsKey(mc.player.getId());
        if (!sitting && CameraDirector.holding(CAMERA)) CameraDirector.close(CAMERA);
    }

    @Nullable
    private static CameraPose shot(Camera camera, CameraPose current, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || eye == null || frame - eyeFrame > 2) return null;
        return new CameraPose(eye, current.yaw(), current.pitch());
    }

    /** FirstPersonBody#shown: the own camera is on the sitting body's eye. */
    public static boolean cameraOnBody(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && camera.getEntity() == mc.player && CameraDirector.isOpen(CAMERA) && eye != null && frame - eyeFrame <= 2;
    }

    private static double now(float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : (level.getGameTime() + partial) / 20.0;
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(SIT);
        }
    }
}
