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
