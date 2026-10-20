package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.FirstPersonBody;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The own first person into and out of an action the body draws (PlayerAction.FirstPerson#BODY, docs/rendering/
 * first_person_body_v1.md 3.5; user 2026-10-20: every extra first-person animation eases the camera, no pop):
 * <ul>
 * <li><b>Look</b>: an action with {@code first_person_pitch} opens a camera director shot ({@link #CAMERA}) that eases the
 * view down to at least that pitch over {@link #LOOK_IN} s (the mouse still looks lower); when the action stops it eases back
 * over {@link #LOOK_OUT} s to where the player looks. The camera alone turns: the player's own rotation stays, and the own
 * body is posed to the camera's pitch meanwhile (PlayerSkeleton#solve).</li>
 * <li><b>Hands</b>: the held item's first-person view model slides down out of view over {@link #HAND_OUT} s
 * (client/FirstPersonBody#handDrop) before the body's arms take over (PlayerSkeleton#firstPersonHands), so the two never
 * show at once; when the action has eased out of the body it slides back up the same way.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FirstPersonActionView {
    /** The camera director request's id. */
    public static final String CAMERA = "skeleton_action_look";
    /** The look's ease in and out (s, game time, smoothstep). */
    private static final double LOOK_IN = 0.45, LOOK_OUT = 0.45;
    /** The view model's slide out of view and back (s). */
    private static final float HAND_OUT = 0.15F;
    /** The view model's slide (0 in view .. 1 out of view). */
    private static float slide;
    /** The pitch the running action's look eases down to (deg, + = down); kept while it eases out. */
    private static float floor = Float.NaN;
    private static long nanos;

    private FirstPersonActionView() {
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        long now = Util.getNanos();
        float dt = nanos == 0L ? 0F : Mth.clamp((now - nanos) / 1.0E9F, 0F, 0.1F);
        nanos = now;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean own = player != null && mc.level != null && mc.options.getCameraType().isFirstPerson() && PlayerSkeleton.enabled()
                && SkeletonActions.drawsArms();
        PlayerAction running = own ? SkeletonActions.ownBodyAction(player) : null;
        if (!own) slide = 0F;
        else if (running != null && FirstPersonBody.itemOnBody()) slide = 1F;   // already the body's hand (looking down)
        else if (running != null) slide = Math.min(1F, slide + dt / HAND_OUT);
        else if (!SkeletonActions.drawsFirstPerson(player)) slide = Math.max(0F, slide - dt / HAND_OUT);   // after the ease out
        if (running != null && !Float.isNaN(running.firstPersonPitch)) {
            floor = running.firstPersonPitch;
            if (!CameraDirector.holding(CAMERA)) CameraDirector.open(new CameraDirector.Request(CAMERA, CameraDirector.PRIORITY_BODY_EYE,
                    FirstPersonActionView::shot, CameraDirector.Clock.GAME, LOOK_IN, LOOK_OUT));
        } else if (CameraDirector.holding(CAMERA)) {
            CameraDirector.close(CAMERA);
        }
    }

    /** The look: the view's pitch down to the floor, the rest as it is; nothing when already looking lower. */
    private static CameraPose shot(Camera camera, CameraPose current, float partialTick) {
        if (Float.isNaN(floor) || current.pitch() >= floor || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) return null;
        return new CameraPose(current.position(), current.yaw(), floor);
    }

    /** The look is on the camera (also while it eases out): the own body is posed to the camera's pitch. */
    public static boolean looking() {
        return CameraDirector.isOpen(CAMERA);
    }

    /** The view model has slid out of view: the body's arms do the action. */
    static boolean bodyHands() {
        return slide >= 1F;
    }

    /** How far the held item's view model has slid out of view (0..1, eased). */
    public static float viewModelSlide() {
        return slide * slide * (3F - 2F * slide);
    }
}
