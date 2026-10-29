package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.interaction.ShovelAction;
import com.antaurora.apofirstlight.item.ShovelItem;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.network.ShovelPacket;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The shovel's input and strokes on the client (docs/worldgen/earthwork_a2.md 5): aiming at loose ground within reach
 * with the shovel in the main hand, the tool key (ToolKey: the attack key since 2026-10-28, the use key before) works the
 * ground. One press is one whole stroke (user 2026-10-21: no holding): sneaking it scrapes; else an empty blade digs, a
 * loaded one pours. Letting go
 * changes nothing; switching slots, a screen, dying cancel a stroke before its commit.
 * The server (interaction/ShovelAction) runs every stroke and sends its phases to everyone tracking the player; this
 * keeps them for the skeleton's poses (client/skeleton/SkeletonActions: {@link #elapsed}, {@link #mode}, {@link #target}).
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class ShovelClient {
    /** Ticks to wait for the server's answer to a start before asking again. */
    private static final int RETRY = ShovelAction.INTERVAL;

    /** A stroke as this client sees it: start clock, commit clock (-1 before), its mode and ground point. */
    private record Stroke(UUID action, int mode, int start, int commit, Vec3 target) {}

    private static final Map<UUID, Stroke> STROKES = new HashMap<>();
    private static int clock, asked = Integer.MIN_VALUE / 2, slot;
    /** the tool key was down last tick (a stroke starts on the press, not while held) */
    private static boolean wasDown;

    static {
        ToolKey.register(new ToolKey.Tool() {
            @Override public boolean targets(Minecraft mc) { return aim(mc) != null; }

            @Override public boolean busy() { return active(); }
        });
    }

    /** The own stroke, or null. */
    private static Stroke own() {
        var mc = Minecraft.getInstance();
        return mc.player == null ? null : STROKES.get(mc.player.getUUID());
    }

    public static boolean active() { return own() != null; }

    /** A player's stroke in ticks since it started, or -1 for none. */
    public static double elapsed(Player player, float partial) {
        Stroke s = STROKES.get(player.getUUID());
        if (s == null) return -1;
        // before the server's commit the stroke waits at its commit moment (a slow server never shows the scoop early)
        double t = clock - s.start + partial;
        return s.commit < 0 ? Math.min(t, ShovelAction.COMMIT_AT[s.mode] - 0.01) : Math.min(t, ShovelAction.DURATION[s.mode]);
    }

    /** A player's stroke mode (ShovelAction DIG, POUR, SCRAPE), or -1. */
    public static int mode(Player player) {
        Stroke s = STROKES.get(player.getUUID());
        return s == null ? -1 : s.mode;
    }

    /** The ground point a player's stroke works, or null. */
    public static Vec3 target(Player player) {
        Stroke s = STROKES.get(player.getUUID());
        return s == null ? null : s.target;
    }

    public static void receive(ShovelPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Stroke s = STROKES.get(p.player());
        switch (p.phase()) {
            case ShovelAction.START -> {
                STROKES.put(p.player(), new Stroke(p.action(), p.mode(), clock, -1, p.target()));
                if (p.player().equals(mc.player.getUUID())) slot = mc.player.getInventory().selected;
            }
            case ShovelAction.COMMIT -> {
                if (s != null && s.action.equals(p.action()))
                    // the server's commit is its tick COMMIT_AT: the stroke's clock follows it from here
                    STROKES.put(p.player(), new Stroke(s.action, s.mode, clock - ShovelAction.COMMIT_AT[s.mode], clock, s.target));
            }
            default -> {
                if (s != null && s.action.equals(p.action())) STROKES.remove(p.player());
            }
        }
    }

    /** The loose ground the own player aims the shovel at (ShovelAction#target on the client), or null. */
    static Vec3 aim(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || !p.getMainHandItem().is(AflItems.ROUND_POINT_SHOVEL.get())) return null;
        return ShovelAction.aimed(mc.level, p);
    }

    /** The prompt (WorldInteractionHint): scrape (sneaking), dig (an empty blade), else pour; none during a stroke. */
    static Component hint(Minecraft mc) {
        if (active() || mc.player == null || mc.player.isSpectator() || aim(mc) == null) return null;
        var load = ShovelItem.load(mc.player.getMainHandItem());
        String k = mc.player.isShiftKeyDown() ? "scrape" : load.stored() <= 0 ? "dig" : "pour";
        return Component.translatable("hint.apocalypse_firstlight.shovel." + k);
    }

    private static void start(int mode) {
        if (clock - asked < RETRY) return;
        asked = clock;
        AflNetwork.startShovel(mode);
    }

    private static void cancel(Stroke s) {
        AflNetwork.cancelShovel(s.action);
        STROKES.remove(Minecraft.getInstance().player.getUUID());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            STROKES.clear();
            wasDown = false;
            return;
        }
        if (mc.isPaused()) return;
        clock++;
        STROKES.values().removeIf(s -> clock - s.start > ShovelAction.DURATION[s.mode] + 100);   // a lost END
        var p = mc.player;
        Stroke own = own();
        boolean down = ToolKey.down(mc), press = down && !wasDown;
        wasDown = down;
        if (own != null) {
            if (p.getInventory().selected != slot || !p.getMainHandItem().is(AflItems.ROUND_POINT_SHOVEL.get()) || mc.screen != null
                    || !p.isAlive() || p.isSpectator()) {
                if (own.commit < 0) cancel(own);
            }
            return;
        }
        if (!press || p.isSpectator() || !p.isAlive() || ToolKey.onEntity(mc) || aim(mc) == null) return;
        var load = ShovelItem.load(p.getMainHandItem());
        start(p.isShiftKeyDown() ? ShovelAction.SCRAPE : load.stored() <= 0 ? ShovelAction.DIG : ShovelAction.POUR);
    }

    private ShovelClient() {}
}
