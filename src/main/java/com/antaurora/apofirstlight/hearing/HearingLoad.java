package com.antaurora.apofirstlight.hearing;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.HearingProtectionManager;
import com.antaurora.apofirstlight.network.AflNetwork;
import com.antaurora.apofirstlight.registry.AflAttributes;
import com.antaurora.apofirstlight.survivor.SurvivorStats;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tinnitus V2 (docs/gameplay/tinnitus_v2_plan.md): each player's hearing load. Loud events add doses (HearingExposure), × the
 * worn hearing protection and the survivor tinnitus attribute; the load sets the stage (TinnitusConfig#stage) and halves
 * every half_life_ticks (asleep faster), once a second, only while online. One dose of jolt_from or more rings at once
 * (HearingPackets.Jolt). Saved in the persistent data ({@code afl_tinnitus}: {@code v}, {@code load}); a death clears it.
 * The own player gets its load and stage (HearingPackets.State) when the stage changes, when the load moved by half a point,
 * and every 5 s while there is any.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class HearingLoad {
    static final String KEY = "afl_tinnitus";
    private static final int VERSION = 1, SYNC_SECONDS = 5;

    private static final class State {
        double load;
        int sentStage = -1, sinceSync;
        double sentLoad = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private HearingLoad() {
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> {
            State s = new State();
            CompoundTag t = player.getPersistentData().getCompound(KEY);
            s.load = Mth.clamp(t.getDouble("load"), 0, TinnitusConfig.get().maxLoad);
            return s;
        });
    }

    public static double load(ServerPlayer player) {
        return state(player).load;
    }

    /** A loud event's raw dose for this player (before its hearing protection and attribute). */
    public static void add(ServerPlayer player, double raw) {
        if (!(raw > 0) || player.isSpectator() || !player.isAlive()) return;
        var c = TinnitusConfig.get();
        double dose = raw * HearingProtectionManager.getImpulseProtectionMultiplier(player)
                * SurvivorStats.multiplier(player, AflAttributes.TINNITUS, 0);
        if (!(dose > 0)) return;
        State s = state(player);
        s.load = Math.min(c.maxLoad, s.load + dose);
        if (dose >= c.joltFrom)
            AflNetwork.hearing(player, new HearingPackets.Jolt((float) Mth.clamp(dose / c.joltFull, c.joltLeast, 1)));
        sync(player, s, false);
    }

    /** Dev: sets the load. */
    static void set(ServerPlayer player, double load) {
        State s = state(player);
        s.load = Mth.clamp(load, 0, TinnitusConfig.get().maxLoad);
        save(player, s);
        sync(player, s, true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        State s = STATES.get(player.getUUID());
        if (s == null) {
            if (!player.getPersistentData().contains(KEY)) return;
            s = state(player);
        }
        if (s.load <= 0 && s.sentStage == 0) return;
        var c = TinnitusConfig.get();
        double rate = player.isSleeping() ? c.sleep : 1;
        s.load *= Math.pow(0.5, 20 * rate / c.halfLifeTicks);
        if (s.load < 0.01) s.load = 0;
        save(player, s);
        sync(player, s, false);
    }

    private static void sync(ServerPlayer player, State s, boolean force) {
        int stage = TinnitusConfig.get().stage(s.load);
        s.sinceSync++;
        if (!force && stage == s.sentStage && Math.abs(s.load - s.sentLoad) < 0.5 && (s.load <= 0 || s.sinceSync < SYNC_SECONDS)) return;
        s.sentStage = stage;
        s.sentLoad = s.load;
        s.sinceSync = 0;
        AflNetwork.hearing(player, new HearingPackets.State((float) s.load, (byte) stage));
    }

    private static void save(ServerPlayer player, State s) {
        if (s.load <= 0) {
            player.getPersistentData().remove(KEY);
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("v", VERSION);
        t.putDouble("load", s.load);
        player.getPersistentData().put(KEY, t);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, state(player), true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State s = STATES.remove(player.getUUID());
        if (s != null) save(player, s);
    }

    /** Death: clear ears on the next life; back from the End: the same load. */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original) || !(event.getEntity() instanceof ServerPlayer player)) return;
        State s = STATES.remove(player.getUUID());
        if (event.isWasDeath()) {
            player.getPersistentData().remove(KEY);
        } else {
            if (s != null) save(original, s);
            CompoundTag t = original.getPersistentData().getCompound(KEY);
            if (!t.isEmpty()) player.getPersistentData().put(KEY, t.copy());
        }
        sync(player, state(player), true);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, state(player), true);
    }
}
