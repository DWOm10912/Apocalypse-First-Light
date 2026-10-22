package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.FallLanding;
import com.antaurora.apofirstlight.health.HealthPackets;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Landing and falling over V1 on the client (docs/gameplay/fall_landing_v1_plan.md; health/FallLanding decides): each
 * player's hurting landing as the server sent it, played as a player action (SkeletonActions#live: body/land_hard or
 * body/fall_over, mirrored for the right hand). While the own player falls over it does not move, jump, sprint, sneak, use
 * or hit, keeps the yaw it landed with, and in first person its view follows the action's first_person_view (eased in from
 * the pitch it landed with over {@link #VIEW_IN} s), set each frame after the mouse turned it (RenderTickEvent START, as
 * SkeletonActions#lookDown). The camera sits on the posed eye (the actions' first_person_eye). Getting up after lying
 * paralysed (lightning injury V1, body/prone_get_up) is played and held the same way.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FallLandingClient {
    /** A landing playing: the action, its start (s, the level clock), the right hand down. */
    public record Playing(String id, double started, boolean mirror) {}

    static final String HARD = "body/land_hard", OVER = "body/fall_over", GET_UP = "body/prone_get_up";
    /** The own view eases from the pitch it landed with into the action's over this long (s). */
    private static final double VIEW_IN = 0.1;
    private static final Map<Integer, Playing> PLAYING = new HashMap<>();
    /** The own player's yaw and pitch when it fell over. */
    private static float yaw, pitch;

    private FallLandingClient() {
    }

    public static void accept(HealthPackets.Landing packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean over = packet.kind() != FallLanding.HARD;
        String id = packet.kind() == FallLanding.GET_UP ? GET_UP : over ? OVER : HARD;
        PLAYING.put(packet.entity(), new Playing(id, mc.level.getGameTime() / 20.0, packet.mirror()));
        LocalPlayer own = mc.player;
        if (own != null && own.getId() == packet.entity() && over) {
            yaw = own.getYRot();
            pitch = own.getXRot();
            own.setSprinting(false);
            if (own.isUsingItem()) own.stopUsingItem();
        }
    }

    /** The landing this player plays at this clock (s), or null once it is over. */
    static Playing playing(Entity player, double now) {
        Playing p = PLAYING.get(player.getId());
        if (p == null) return null;
        double length = (p.id().equals(OVER) ? FallLanding.FALL_TICKS : p.id().equals(GET_UP) ? FallLanding.GET_UP_TICKS : FallLanding.HARD_TICKS) / 20.0;
        if (now - p.started() < length && now >= p.started() - 1) return p;
        PLAYING.remove(player.getId());
        return null;
    }

    /** Whether the own player is falling over (or getting up) now. */
    public static boolean fallingOver() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        Playing p = playing(mc.player, mc.level.getGameTime() / 20.0);
        return p != null && !p.id().equals(HARD);
    }

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer) || !fallingOver()) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void keys(InputEvent.InteractionKeyMappingTriggered event) {
        if (!fallingOver()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PLAYING.clear();
            return;
        }
        if (fallingOver() && mc.player.isSprinting()) mc.player.setSprinting(false);
    }

    @SubscribeEvent
    public static void view(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || !fallingOver()) return;
        player.setYRot(yaw);
        player.yRotO = yaw;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        Playing p = PLAYING.get(player.getId());
        PlayerAction action = p == null ? null : PlayerActions.get(p.id());
        if (action == null) return;
        float t = (float) ((mc.level.getGameTime() + event.renderTickTime) / 20.0 - p.started());
        float curve = action.viewAt(t);
        if (Float.isNaN(curve)) return;
        float in = (float) Mth.clamp(t / VIEW_IN, 0, 1);
        float x = Mth.lerp(in * in * (3 - 2 * in), pitch, curve);
        player.setXRot(x);
        player.xRotO = x;
    }
}
