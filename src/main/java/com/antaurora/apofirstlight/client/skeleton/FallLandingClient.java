package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.health.ClientKnockdown;
import com.antaurora.apofirstlight.health.ClientLightningInjury;
import com.antaurora.apofirstlight.health.FallLanding;
import com.antaurora.apofirstlight.health.HealthPackets;
import com.antaurora.apofirstlight.skeleton.action.PlayerAction;
import com.antaurora.apofirstlight.skeleton.action.PlayerActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
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
 * paralysed (lightning injury V1, body/prone_get_up) is played and held the same way, and so are injury knockdown V1's
 * (health/Knockdown: body/knock_front, knock_back, knock_side, mirrored onto the right side, and body/collapse, after which
 * the own player stays collapsed, ClientKnockdown, crawling with no jumping until it gets up).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FallLandingClient {
    /** A landing playing: the action, its start (s, the level clock), the right hand down. */
    public record Playing(String id, double started, boolean mirror) {}

    static final String HARD = "body/land_hard", OVER = "body/fall_over", GET_UP = "body/prone_get_up";
    /** Each landing kind's action and length (ticks), by FallLanding's kind. */
    private static final String[] IDS = {HARD, OVER, GET_UP, "body/knock_front", "body/knock_back", "body/knock_side", "body/collapse"};
    private static final int[] TICKS = {FallLanding.HARD_TICKS, FallLanding.FALL_TICKS, FallLanding.GET_UP_TICKS, FallLanding.KNOCK_FRONT_TICKS,
            FallLanding.KNOCK_BACK_TICKS, FallLanding.KNOCK_SIDE_TICKS, FallLanding.COLLAPSE_TICKS};
    private static final Map<String, Integer> LENGTH = new HashMap<>();

    static {
        for (int i = 0; i < IDS.length; i++) LENGTH.put(IDS[i], TICKS[i]);
    }
    /** The own view eases from the pitch it landed with into the action's over this long (s). */
    private static final double VIEW_IN = 0.1;
    private static final Map<Integer, Playing> PLAYING = new HashMap<>();
    /** The own player's yaw and pitch when it fell over. */
    private static float yaw, pitch;
    /** The own player's crawl was forced here for its collapse. */
    private static boolean collapsePosed;

    private FallLandingClient() {
    }

    public static void accept(HealthPackets.Landing packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (packet.kind() < 0 || packet.kind() >= IDS.length) return;
        boolean over = packet.kind() != FallLanding.HARD;
        String id = IDS[packet.kind()];
        PLAYING.put(packet.entity(), new Playing(id, mc.level.getGameTime() / 20.0, packet.mirror()));
        LocalPlayer own = mc.player;
        if (own != null && own.getId() == packet.entity()) {
            if (packet.kind() == FallLanding.COLLAPSE) ClientKnockdown.set(true);
            else if (packet.kind() == FallLanding.GET_UP) ClientKnockdown.set(false);
        }
        if (own != null && own.getId() == packet.entity() && over) {
            yaw = own.getYRot();
            pitch = own.getXRot();
            own.setSprinting(false);
            if (own.isUsingItem()) own.stopUsingItem();
        }
    }

    /**
     * A living ragdoll came to rest (client/survivor/LiveRagdollClient): the knockdown {@code kind}'s clip, already
     * {@code fromTicks} into it (lying), no mirror. The own player keeps the yaw the server put it at.
     */
    public static void getUpFrom(int entity, byte kind, int fromTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || kind < 0 || kind >= IDS.length) return;
        PLAYING.put(entity, new Playing(IDS[kind], (mc.level.getGameTime() - fromTicks) / 20.0, false));
        LocalPlayer own = mc.player;
        if (own != null && own.getId() == entity) {
            yaw = own.getYRot();
            pitch = own.getXRot();
            own.setSprinting(false);
            if (own.isUsingItem()) own.stopUsingItem();
        }
    }

    /** Whether this player plays a landing (or a get-up) now. */
    public static boolean plays(Entity player) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && playing(player, mc.level.getGameTime() / 20.0) != null;
    }

    /** The landing this player plays at this clock (s), or null once it is over. */
    static Playing playing(Entity player, double now) {
        Playing p = PLAYING.get(player.getId());
        if (p == null) return null;
        double length = LENGTH.getOrDefault(p.id(), FallLanding.HARD_TICKS) / 20.0;
        if (now - p.started() < length && now >= p.started() - 1) return p;
        PLAYING.remove(player.getId());
        return null;
    }

    /** Whether the own player is falling over (or getting up) now. */
    public static boolean fallingOver() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (com.antaurora.apofirstlight.client.survivor.LiveRagdollClient.ownFalling()) return true;   // thrown, still falling
        Playing p = playing(mc.player, mc.level.getGameTime() / 20.0);
        return p != null && !p.id().equals(HARD);
    }

    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer)) return;
        if (ClientKnockdown.collapsed()) event.getInput().jumping = false;
        if (!fallingOver()) return;
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
            ClientKnockdown.set(false);
            return;
        }
        if (mc.player != null && !mc.player.isAlive()) ClientKnockdown.set(false);
        LocalPlayer own = mc.player;
        if (own != null) {
            // collapsed: the server forces the crawl (Knockdown); the own client computes its own pose, so it forces it too
            // (as client/LightningInjuryClient), or it stands here while the others see it crawl (user 2026-10-22 「好像客户端的动画不同步」)
            if (ClientKnockdown.collapsed()) {
                if (own.getForcedPose() != Pose.SWIMMING) own.setForcedPose(Pose.SWIMMING);
                collapsePosed = true;
            } else if (collapsePosed) {
                collapsePosed = false;
                if (own.getForcedPose() == Pose.SWIMMING && !ClientLightningInjury.down()) own.setForcedPose(null);
            }
        }
        if (fallingOver() && mc.player.isSprinting()) mc.player.setSprinting(false);
    }

    /** A new own player (respawned, another dimension): the server dropped its collapse (Knockdown). */
    @SubscribeEvent
    public static void cloned(ClientPlayerNetworkEvent.Clone event) {
        ClientKnockdown.set(false);
        collapsePosed = false;
    }

    @SubscribeEvent
    public static void view(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.isPaused() || !fallingOver()) return;
        // thrown: the camera rides the ragdoll's head (LiveRagdollClient), the view is not held
        if (com.antaurora.apofirstlight.client.survivor.LiveRagdollClient.ownFalling()) return;
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
