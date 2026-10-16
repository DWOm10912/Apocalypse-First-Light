package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.item.FuelCanItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The local player pouring a jerry can into a fill cover (item/FuelCanItem; 2026-10-06, the user: open the cap, hold the
 * spout over the fill cover and let it run, crouching to reach it): while the pour lasts the player crouches and keeps
 * still (the forced sneak goes to the server as any sneak: the eyes come down, and a click stops the pour rather than
 * setting the can down, FuelCanItem#useOn), and the view turns, easing, until the cover's opening shows left of and a
 * little below the spout of the first-person pour pose (FuelCanItem.SPOUT_VIEW: the can held across, its spout end tipped
 * down to the left), so the stream (client/FuelCanPourJets) runs from the spout into it. The view is set each frame after
 * the mouse has turned it (RenderTickEvent START), so the mouse cannot pull it away; the pour stops with another click,
 * or by itself. 2026-10-16, the player skeleton on (player actions S2 round 3, docs/dev/player_actions_v1_plan.md 12):
 * the body steps to the opening and holds the spout over it; the view locks on the opening itself from the camera on the
 * stepped body, at most 50 deg down, and the player crouches only for a low opening.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class JerryCanPourView {
    /**
     * Where the opening shows, from the spout, on the held items' projection (x right, y up, as tangents): to the left,
     * where the tipped spout points (straight under the spout is the can's own end).
     */
    private static final double BELOW_SPOUT_X = -0.15, BELOW_SPOUT_Y = -0.08;
    /** How fast the view turns to it (per second, exponential). */
    private static final double TURN_RATE = 5.0;
    /**
     * The player skeleton on (player action item/jerry_can holds the spout over the opening): the view locks on this far
     * over the opening (blocks), from the camera on the stepped body, looking down at most {@link #PITCH_MAX} deg (a
     * steeper look shows the own arms from above; the opening then shows low on the screen).
     */
    private static final double LOOK_OVER = 0.1, PITCH_MAX = 50;
    private static long lastFrame;

    private JerryCanPourView() {
    }

    /** The fill cover the local player is pouring into, or null. */
    private static BlockPos pouring(LocalPlayer player) {
        return player.isAlive() && !player.isSpectator() ? FuelCanItem.pourTarget(player.getMainHandItem()) : null;
    }

    /**
     * Crouch (the skeleton on: only for a low opening, client/skeleton/SkeletonActions#pourCrouched; a high one is poured
     * standing) and keep still.
     */
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        BlockPos cover = pouring(player);
        if (cover == null) return;
        var input = event.getInput();
        input.shiftKeyDown = !com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton.enabled()
                || com.antaurora.apofirstlight.client.skeleton.SkeletonActions.pourCrouched(FuelCanItem.pourOpening(player.level(), cover).y - player.getY());
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = input.jumping = false;
    }

    /** Turn the view toward the cover's opening. */
    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;
        BlockPos cover = pouring(player);
        if (cover == null) return;
        float yaw, pitch;
        if (com.antaurora.apofirstlight.client.skeleton.PlayerSkeleton.enabled()) {
            // the player skeleton holds the can's spout over the opening (player action item/jerry_can): the view locks on
            // the opening from the camera on the stepped body (the head looks with it), not past the pour pose's spout
            Vec3 eye = com.antaurora.apofirstlight.client.skeleton.SkeletonActions.cameraEye();
            if (eye == null) eye = player.getEyePosition(event.renderTickTime);
            Vec3 d = FuelCanItem.pourOpening(mc.level, cover).add(0, LOOK_OVER, 0).subtract(eye);
            yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            pitch = (float) Math.min(PITCH_MAX, -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
        } else {
            // the opening's top middle, from the eyes
            Vec3 d = FuelCanItem.pourOpening(mc.level, cover).add(0, 0.55, 0).subtract(player.getEyePosition(event.renderTickTime));
            double yawTo = Math.toDegrees(Math.atan2(-d.x, d.z)), pitchTo = -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
            // where it should show on screen: just below and left of the pour pose's spout, moved from the hand's projection to the world's
            Vec3 spout = FuelCanItem.SPOUT_VIEW;
            double k = Math.tan(Math.toRadians(ViewFov.world()) / 2) / Math.tan(Math.toRadians(ViewFov.hand()) / 2);
            double sx = (spout.x / -spout.z + BELOW_SPOUT_X) * k, sy = (spout.y / -spout.z + BELOW_SPOUT_Y) * k;
            yaw = (float) (yawTo - Math.toDegrees(Math.atan(sx)));
            pitch = (float) Mth.clamp(pitchTo + Math.toDegrees(Math.atan(sy)), -90, 90);
        }
        float blend = (float) (1 - Math.exp(-TURN_RATE * dt));
        float y = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * blend, p = player.getXRot() + (pitch - player.getXRot()) * blend;
        player.setYRot(y);
        player.setXRot(p);
        player.yRotO = y;
        player.xRotO = p;
        player.yHeadRot = player.yHeadRotO = y;
    }
}
