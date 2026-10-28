package com.antaurora.apofirstlight.worldgen.lock;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Set;

/**
 * The per-world switches of the gated generation systems ({@link WorldgenGates}) as the running world has them: bound
 * to the overworld's RandomState by the version lock at load (WorldgenLockEvents), before the first chunk. Generation
 * code asks {@link #enabled}; a noise state the lock never bound (another dimension, a world without the AFL plan) has
 * every gated system off.
 */
public final class WorldgenGateState {
    private WorldgenGateState() {}

    static void bind(RandomState state, Set<String> on) {
        ((RandomStateSeedAccess) (Object) state).apocalypse$setWorldgenGates(on);
        // C3.2: the road network and its terrain works follow their gates (both off: the planned ground unchanged)
        com.antaurora.apofirstlight.worldgen.network.RoadNetworkRuntime.bind(state, on);
        // river / lake R2: the wider river channels, narrowing at the road works just bound
        com.antaurora.apofirstlight.worldgen.terrain.v2.RiverChannels.bind(state, on);
        // U2: a deep world's planned caves, planned round the roads, towns and home just bound
        com.antaurora.apofirstlight.worldgen.underground.UndergroundRuntime.bind(state);
    }

    public static Set<String> on(RandomState state) {
        return ((RandomStateSeedAccess) (Object) state).apocalypse$worldgenGates();
    }

    public static boolean enabled(RandomState state, String id) {
        return on(state).contains(id);
    }

    public static boolean enabled(ServerLevel level, String id) {
        return enabled(level.getChunkSource().randomState(), id);
    }
}
