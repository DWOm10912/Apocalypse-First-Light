package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.boulder.BoulderBlocks;
import com.antaurora.apofirstlight.boulder.BoulderStrike;
import com.antaurora.apofirstlight.boulder.PavementMining;
import com.antaurora.apofirstlight.boulder.PickPacket;
import com.antaurora.apofirstlight.earthwork.Earthwork;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The pick mattock's input and strikes on the client (Rock R1a, docs/gameplay/rock_r1_stones_boulders_design.md 3.4, 11.2),
 * as the shovel's (client/ShovelClient): aiming the pick at a boulder within reach, one press of the tool key (ToolKey:
 * the attack key since 2026-10-28, the use key before) is one whole strike; switching slots, a screen or dying cancels it before the pick lands. The server (boulder/BoulderStrike) runs
 * it and sends its phases to everyone tracking the player; this keeps them for the skeleton's poses
 * (client/skeleton/SkeletonActions: {@link #elapsed}, {@link #target}). Rock R2: bare rock ground in reach takes a strike
 * the same way (BoulderStrike#aim); so does a road's pavement (road Mesh-ification P0, PavementMining).
 */
@Mod.EventBusSubscriber(modid = "apocalypse_firstlight", value = Dist.CLIENT)
public final class PickClient {
    /** Ticks to wait for the server's answer to a start before asking again. */
    private static final int RETRY = BoulderStrike.INTERVAL;

    /** A strike as this client sees it: start clock, hit clock (-1 before), the rock point. */
    private record Strike(UUID action, int start, int hit, Vec3 target) {}

    private static final Map<UUID, Strike> STRIKES = new HashMap<>();
    private static int clock, asked = Integer.MIN_VALUE / 2, slot;
    /** the tool key was down last tick (a strike starts on the press, not while held) */
    private static boolean wasDown;

    static {
        ToolKey.register(new ToolKey.Tool() {
            @Override public boolean targets(Minecraft mc) { return holding(mc) && (onBoulder(mc) || onRock(mc) || onPavement(mc)); }

            @Override public boolean busy() { return active(); }
        });
    }

    private static Strike own() {
        var mc = Minecraft.getInstance();
        return mc.player == null ? null : STRIKES.get(mc.player.getUUID());
    }

    public static boolean active() { return own() != null; }

    /** A player's strike in ticks since it started, or -1 for none. */
    public static double elapsed(Player player, float partial) {
        Strike s = STRIKES.get(player.getUUID());
        if (s == null) return -1;
        // before the server's hit the strike waits just short of its landing (a slow server never shows the pick land early)
        double t = clock - s.start + partial;
        return s.hit < 0 ? Math.min(t, BoulderStrike.HIT_AT - 0.01) : Math.min(t, BoulderStrike.DURATION);
    }

    /** The rock point a player's strike lands on, or null. */
    public static Vec3 target(Player player) {
        Strike s = STRIKES.get(player.getUUID());
        return s == null ? null : s.target;
    }

    public static void receive(PickPacket p) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Strike s = STRIKES.get(p.player());
        switch (p.phase()) {
            case BoulderStrike.START -> {
                STRIKES.put(p.player(), new Strike(p.action(), clock, -1, p.target()));
                if (p.player().equals(mc.player.getUUID())) slot = mc.player.getInventory().selected;
            }
            case BoulderStrike.HIT -> {
                if (s != null && s.action.equals(p.action()))
                    // the server's hit is its tick HIT_AT: the strike's clock follows it from here
                    STRIKES.put(p.player(), new Strike(s.action, clock - BoulderStrike.HIT_AT, clock, s.target));
            }
            default -> {
                if (s != null && s.action.equals(p.action())) STRIKES.remove(p.player());
            }
        }
    }

    private static boolean holding(Minecraft mc) {
        return mc.player != null && mc.player.getMainHandItem().is(AflItems.PICK_MATTOCK.get());
    }

    /** Whether the crosshair is on a boulder block (any distance vanilla picks). */
    private static boolean onBoulder(Minecraft mc) {
        return mc.level != null && BoulderBlocks.ready() && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && BoulderBlocks.isBoulder(mc.level.getBlockState(hit.getBlockPos()));
    }

    /** Whether the crosshair is on bare rock ground (Rock R2) or a natural rock face (U3a-3) within a strike's reach, with the pick in hand. */
    private static boolean onRock(Minecraft mc) {
        if (mc.level == null || !holding(mc) || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !Earthwork.rock(mc.level.getBlockState(hit.getBlockPos()).getBlock())) return false;
        var aim = BoulderStrike.aim(mc.player);
        return aim != null && (aim.root() == null || aim.node() == com.antaurora.apofirstlight.boulder.RockMining.NODE);
    }

    /** Whether the crosshair is on a road's pavement within a strike's reach, with the pick in hand (road Mesh-ification P0). */
    private static boolean onPavement(Minecraft mc) {
        if (mc.level == null || !holding(mc) || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !PavementMining.pavement(mc.level.getBlockState(hit.getBlockPos()))) return false;
        var aim = BoulderStrike.aim(mc.player);
        return aim != null && aim.node() == PavementMining.NODE;
    }

    /** The prompt (WorldInteractionHint): strike with the pick at a boulder, bare rock or a road in reach; any other hand at a boulder: it needs a pick. */
    static Component hint(Minecraft mc) {
        if (active() || mc.player == null || mc.player.isSpectator()) return null;
        if (onRock(mc) || onPavement(mc)) return Component.translatable("hint.apocalypse_firstlight.pick.strike");
        if (!onBoulder(mc)) return null;
        if (!holding(mc)) return Component.translatable("hint.apocalypse_firstlight.pick.needed");
        return BoulderStrike.aim(mc.player) == null ? null : Component.translatable("hint.apocalypse_firstlight.pick.strike");
    }

    private static void cancel(Strike s) {
        PickPacket.cancel(s.action);
        STRIKES.remove(Minecraft.getInstance().player.getUUID());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            STRIKES.clear();
            wasDown = false;
            return;
        }
        if (mc.isPaused()) return;
        clock++;
        STRIKES.values().removeIf(s -> clock - s.start > BoulderStrike.DURATION + 100);   // a lost END
        var p = mc.player;
        Strike own = own();
        boolean down = ToolKey.down(mc), press = down && !wasDown;
        wasDown = down;
        if (own != null) {
            if (p.getInventory().selected != slot || !holding(mc) || mc.screen != null || !p.isAlive() || p.isSpectator()) {
                if (own.hit < 0) cancel(own);
            }
            return;
        }
        if (!press || p.isSpectator() || !p.isAlive() || !holding(mc) || ToolKey.onEntity(mc) || BoulderStrike.aim(p) == null) return;
        if (clock - asked < RETRY) return;
        asked = clock;
        PickPacket.start();
    }

    private PickClient() {}
}
