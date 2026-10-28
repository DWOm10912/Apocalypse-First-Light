package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.camera.CameraDirector;
import com.antaurora.apofirstlight.client.camera.CameraPose;
import com.antaurora.apofirstlight.client.light.DynamicLights;
import com.antaurora.apofirstlight.client.light.LightCone;
import com.antaurora.apofirstlight.client.skeleton.SkeletonActions;
import com.antaurora.apofirstlight.item.FlipLighterItem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Flip lighter V1 on the client (docs/gameplay/flip_lighter_v1.md 4-5):
 * <ul>
 *   <li><b>The camera dip</b> (the camera director, client/camera/CameraDirector): in the own first person, while the hand
 *   flicks the lid open or strikes the wheel, the view glances down to the hands (at least {@link #DIP_PITCH} deg) and
 *   draws back a little so both hands are in it ({@link #PULL_BACK}, {@link #LIFT}: within the distance at which the
 *   first-person body is still drawn), eased in and out; it lingers {@link #LINGER} s after an open or a failed strike for
 *   the next strike and comes up as the lit lighter does. The mouse is locked only while the hand moves
 *   ({@link #lockUntil}).</li>
 *   <li><b>Sparks</b> off the wheel as it is struck (FireFx#flintSparks), for every player.</li>
 *   <li><b>The light</b> of a lit lighter (client/light/DynamicLights): one round light at the flame (two halves, LightCone#round), warm, about
 *   6 blocks, flickering with it.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FlipLighterClient {
    private static final String CAMERA = "afl_flip_lighter", LOCK = "afl_flip_lighter_lock";
    /** The dip: the view at least this far down (deg), drawn back and up this far (blocks). */
    private static final float DIP_PITCH = 25F;
    private static final double PULL_BACK = 0.1, LIFT = 0.06;
    private static final double DIP_IN = 0.2, DIP_OUT = 0.35;
    /** After an open or a failed strike the view stays down this long for the next strike (s). */
    private static final double LINGER = 1.2;
    /** The flame's light, the same all round (LightCone#round): the lower and upper half. */
    private static final LightCone GLOW = LightCone.round(6.0, 9.0, 2.0, 0.25);
    private static final Vec3 DOWN = new Vec3(0, -1, 0), UP = new Vec3(0, 1, 0), EAST = new Vec3(1, 0, 0);
    private static final double LIGHT_RANGE = 48;
    /** Per player: the act start whose sparks were thrown. */
    private static final Map<Integer, Long> SPARKED = new HashMap<>();
    private static boolean lightsRegistered;

    private FlipLighterClient() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (!lightsRegistered) {
            DynamicLights.register(FlipLighterClient::lights);
            lightsRegistered = true;
        }
        if (mc.level == null || mc.player == null) {
            SPARKED.clear();
            return;
        }
        camera(mc);
        sparks(mc.level);
    }

    // ---------------------------------------------------------------- the camera dip

    private static void camera(Minecraft mc) {
        ItemStack stack = mc.player.getMainHandItem();
        String act = stack.getItem() instanceof FlipLighterItem ? FlipLighterItem.act(stack) : null;
        boolean fp = mc.options.getCameraType().isFirstPerson() && SkeletonActions.drawsArms() && !mc.player.isSpectator();
        double t = act == null ? 1e6 : FlipLighterItem.actSeconds(stack, mc.level, 0F);
        boolean holding = CameraDirector.holding(CAMERA);
        boolean dip = fp && act != null && t < dipUntil(act, holding);
        if (dip && !holding) CameraDirector.open(new CameraDirector.Request(CAMERA, CameraDirector.PRIORITY_MACHINE, FlipLighterClient::shot,
                CameraDirector.Clock.GAME, DIP_IN, DIP_OUT));
        else if (!dip && holding) CameraDirector.close(CAMERA);
        boolean lock = dip && t < lockUntil(act);
        boolean locking = CameraDirector.holding(LOCK);
        if (lock && !locking) CameraDirector.open(new CameraDirector.Request(LOCK, CameraDirector.PRIORITY_MACHINE, (c, cur, pt) -> null,
                CameraDirector.Clock.GAME, 0, 0).locks(CameraDirector.Lock.LOOK));
        else if (!lock && locking) CameraDirector.close(LOCK);
    }

    /** How long into an act the view stays down (s); a close only keeps a dip that is open. */
    private static double dipUntil(String act, boolean holding) {
        return switch (act) {
            case "open" -> 0.45 + LINGER;
            case "strike" -> 0.35;   // up with the lit lighter
            case "spark" -> 0.3 + LINGER;
            case "close" -> holding ? 0.2 : 0;
            default -> 0;
        };
    }

    /** How long into an act the mouse is locked (s): while the hand moves to the lid or the wheel. */
    private static double lockUntil(String act) {
        return switch (act) {
            case "open" -> 0.45;
            case "strike" -> 0.3;
            case "spark" -> 0.25;
            default -> 0;
        };
    }

    /** At least DIP_PITCH down, drawn back along the view and lifted. */
    private static CameraPose shot(Camera camera, CameraPose current, float partial) {
        float yaw = current.yaw() * Mth.DEG_TO_RAD;
        Vec3 back = new Vec3(Mth.sin(yaw), 0, -Mth.cos(yaw)).scale(PULL_BACK);   // the view's ahead is (-sin, 0, cos)
        return new CameraPose(current.position().add(back).add(0, LIFT, 0), current.yaw(), Math.max(current.pitch(), DIP_PITCH));
    }

    // ---------------------------------------------------------------- sparks

    private static void sparks(ClientLevel level) {
        long now = level.getGameTime();
        SPARKED.keySet().removeIf(id -> !(level.getEntity(id) instanceof Player));
        for (Player p : level.players()) {
            ItemStack stack = p.getMainHandItem();
            if (!(stack.getItem() instanceof FlipLighterItem)) continue;
            String act = FlipLighterItem.act(stack);
            if (!"strike".equals(act) && !"spark".equals(act)) continue;
            long at = FlipLighterItem.actAt(stack);
            double t = (now - at) / 20.0;
            if (t < FlipLighterItem.WHEEL_FROM || t > FlipLighterItem.WHEEL_TO + 0.1 || Long.valueOf(at).equals(SPARKED.get(p.getId()))) continue;
            SPARKED.put(p.getId(), at);
            Vec3 wheel = SkeletonActions.propPoint(p, "wheel");
            if (wheel == null) wheel = FlipLighterItem.flamePoint(p).add(0, -0.03, 0);
            Vec3 look = p.getViewVector(1F);
            FireFx.flintSparks(level, wheel, new Vec3(-look.x, 0, -look.z));
        }
    }

    // ---------------------------------------------------------------- light

    private static void lights(DynamicLights.Lights out, ClientLevel level, Vec3 camera, float partial) {
        float ticks = level.getGameTime() + partial;
        for (Player p : level.players()) {
            if (p.distanceToSqr(camera) > LIGHT_RANGE * LIGHT_RANGE) continue;
            ItemStack stack = p.getMainHandItem();
            if (!(stack.getItem() instanceof FlipLighterItem)) continue;
            String act = FlipLighterItem.act(stack);
            float t = (float) FlipLighterItem.actSeconds(stack, level, partial);
            float size = FlipLighterRenderer.flame(stack, act, t, ticks);
            if (size < 0.05F) continue;
            Vec3 at = SkeletonActions.propPoint(p, "flame");
            if (at == null) at = FlipLighterItem.flamePoint(p);
            at = at.add(0, 0.04, 0);   // the flame's middle, over the wick
            float k = Math.min(1.1F, size);
            int occ = out.occluder(at.x, at.y, at.z);
            out.spot(GLOW, at.x, at.y, at.z, DOWN, EAST, 1.0F * k, 0.68F * k, 0.38F * k, occ);
            out.spot(GLOW, at.x, at.y, at.z, UP, EAST, 1.0F * k, 0.68F * k, 0.38F * k, occ);
        }
    }
}
