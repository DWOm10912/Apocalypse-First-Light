package com.antaurora.apofirstlight.tree;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * The axe swings at trees players are in, on the client (2026-10-20, docs/gameplay/felling_axe_v1.md 5): the own one starts
 * here when FellingClient sends it (no wait for the server), another player's when the server says so (TreeNetwork SWING),
 * both on this client's game clock. One swing is FellingRules#CHOP_TICKS long and lands at #IMPACT_TICKS (the server's
 * chop). The felling axe plays it (client/skeleton/SkeletonActions: tool/felling_axe_chop); the own first-person camera
 * follows it a little ({@link #cameraShake}).
 */
public final class FellingSwingClient {
    /** Entity id -> the game tick its swing started. */
    private static final Map<Integer, Long> STARTS = new HashMap<>();
    private static Level level;

    private FellingSwingClient() {}

    private static long clock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            STARTS.clear();
            level = mc.level;
        }
        return level == null ? 0 : level.getGameTime();
    }

    /** The own player is in a swing (FellingClient starts no other). */
    static boolean busy(Player p) {
        Long s = STARTS.get(p.getId());
        long now = clock();
        return s != null && now - s < FellingRules.CHOP_TICKS && now >= s;
    }

    /** The own player starts a swing (FellingClient, as it sends it). */
    static void start(Player p) {
        STARTS.put(p.getId(), clock());
    }

    /** Another player started one (the server's SWING; client thread). */
    static void remote(int entityId) {
        STARTS.put(entityId, clock());
    }

    /**
     * Seconds into a player's swing (0..CHOP_TICKS / 20), or -1 for none. A swing counts a tick past its end, so the next
     * one, started on that tick while the key is held, follows it without a frame of the hold between.
     */
    public static double seconds(Player p, float partial) {
        Long s = STARTS.get(p.getId());
        if (s == null) return -1;
        double age = clock() - s + partial;
        if (age < 0 || age > FellingRules.CHOP_TICKS + 1) {
            if (age > FellingRules.CHOP_TICKS + 40 || age < 0) STARTS.remove(p.getId());
            return -1;
        }
        return Math.min(age, FellingRules.CHOP_TICKS) / 20.0;
    }

    /** Seconds since a player's last swing ended (negative while it runs), or a large number for none lately. */
    public static double sinceEnd(Player p, float partial) {
        Long s = STARTS.get(p.getId());
        return s == null ? 1e9 : (clock() - s + partial - FellingRules.CHOP_TICKS) / 20.0;
    }

    /**
     * Camera director shake source (client/camera/CameraSources): the own first-person view in a swing comes up a little
     * with the wind-up, dips with the downswing into the cut and takes the knock of the impact (3 ticks, as the crowbar's),
     * back level as the axe returns to the hold.
     */
    public static void cameraShake(Camera camera, float partial, com.antaurora.apofirstlight.client.camera.CameraShake out) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || camera.getEntity() != mc.player || !mc.options.getCameraType().isFirstPerson()) return;
        double s = seconds(mc.player, partial);
        if (s < 0) return;
        double impact = FellingRules.IMPACT_TICKS / 20.0;
        double up = smooth(s / 0.4) * (1 - smooth((s - 0.4) / (impact - 0.4)));
        double dip = smooth((s - 0.4) / (impact - 0.4)) * (1 - smooth((s - 0.65) / 0.3));
        float kick = com.antaurora.apofirstlight.client.CrowbarFirstPerson.kick((s - impact) * 20);
        out.add(-1.0 * up + 1.8 * dip + 0.8 * kick, 0, 0.25 * kick);
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
