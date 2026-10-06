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
 * or by itself.
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
    private static long lastFrame;

    private JerryCanPourView() {
    }

    /** The fill cover the local player is pouring into, or null. */
    private static BlockPos pouring(LocalPlayer player) {
        return player.isAlive() && !player.isSpectator() ? FuelCanItem.pourTarget(player.getMainHandItem()) : null;
    }

    /** Crouch and keep still. */
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || pouring(player) == null) return;
        var input = event.getInput();
        input.shiftKeyDown = true;
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
        // the opening's top middle, from the eyes
        Vec3 d = new Vec3(cover.getX() + 0.5, cover.getY() + 1.0, cover.getZ() + 0.5).subtract(player.getEyePosition(event.renderTickTime));
        double yawTo = Math.toDegrees(Math.atan2(-d.x, d.z)), pitchTo = -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        // where it should show on screen: just below and left of the pour pose's spout, moved from the hand's projection to the world's
        Vec3 spout = FuelCanItem.SPOUT_VIEW;
        double k = Math.tan(Math.toRadians(ViewFov.world()) / 2) / Math.tan(Math.toRadians(ViewFov.hand()) / 2);
        double sx = (spout.x / -spout.z + BELOW_SPOUT_X) * k, sy = (spout.y / -spout.z + BELOW_SPOUT_Y) * k;
        float yaw = (float) (yawTo - Math.toDegrees(Math.atan(sx)));
        float pitch = (float) Mth.clamp(pitchTo + Math.toDegrees(Math.atan(sy)), -90, 90);
        float blend = (float) (1 - Math.exp(-TURN_RATE * dt));
        float y = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * blend, p = player.getXRot() + (pitch - player.getXRot()) * blend;
        player.setYRot(y);
        player.setXRot(p);
        player.yRotO = y;
        player.xRotO = p;
        player.yHeadRot = player.yHeadRotO = y;
    }
}
