package com.antaurora.apofirstlight.worldgen.highway;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Bounded, per-world deterministic planning caches. Values never retain levels or chunks. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NaturalHighwayCacheManager {
    public static final int MAX_ENGINEERING_SEGMENTS = 24;
    public static final int MAX_HEIGHTS = 65_536;
    public static final int MAX_SURFACE_COLUMNS = 16_384;
    public static final int MAX_PROFILE_ANCHORS = 2_048;
    public static final int MAX_NODE_PLANS = 256;

    private static final AtomicLong NEXT_SESSION = new AtomicLong();
    private static final Map<MinecraftServer, Long> SERVER_SESSIONS = new WeakHashMap<>();
    private static final ConcurrentHashMap<WorldKey, WorldCache> CACHES = new ConcurrentHashMap<>();

    private NaturalHighwayCacheManager() {}

    public static WorldCache forLevel(ServerLevel level, ChunkGenerator generator, RandomState randomState) {
        long session = session(level.getServer());
        WorldKey key = new WorldKey(session, level.dimension().location().toString(), level.getSeed(),
                System.identityHashCode(generator), System.identityHashCode(randomState));
        return CACHES.computeIfAbsent(key, ignored -> new WorldCache());
    }

    private static long session(MinecraftServer server) {
        synchronized (SERVER_SESSIONS) {
            return SERVER_SESSIONS.computeIfAbsent(server, ignored -> NEXT_SESSION.incrementAndGet());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Long session;
        synchronized (SERVER_SESSIONS) {
            session = SERVER_SESSIONS.get(level.getServer());
        }
        if (session != null) {
            String dimension = level.dimension().location().toString();
            CACHES.keySet().removeIf(key -> key.session == session && key.dimension.equals(dimension));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Long session;
        synchronized (SERVER_SESSIONS) {
            session = SERVER_SESSIONS.remove(event.getServer());
        }
        if (session != null) CACHES.keySet().removeIf(key -> key.session == session);
    }

    public static final class WorldCache {
        private final BoundedSingleFlightCache<HeightKey, Integer> heights =
                new BoundedSingleFlightCache<>(MAX_HEIGHTS);
        private final BoundedSingleFlightCache<ColumnKey, Integer> columns =
                new BoundedSingleFlightCache<>(MAX_SURFACE_COLUMNS);
        private final BoundedSingleFlightCache<AnchorKey, Integer> anchors =
                new BoundedSingleFlightCache<>(MAX_PROFILE_ANCHORS);
        private final BoundedSingleFlightCache<NodeKey, InterstateInterchangeNode> nodes =
                new BoundedSingleFlightCache<>(MAX_NODE_PLANS);
        private final BoundedSingleFlightCache<SegmentKey, CorridorEngineeringSegment> segments =
                new BoundedSingleFlightCache<>(MAX_ENGINEERING_SEGMENTS);

        int height(HeightKey key, IntSupplier builder) {
            Lookup<Integer> lookup = heights.get(key, builder::getAsInt);
            if (lookup.miss) NaturalHighwayRuntimeStats.heightCacheMiss();
            else NaturalHighwayRuntimeStats.heightCacheHit();
            return lookup.value;
        }

        int surfaceFlags(ColumnKey key, IntSupplier builder) {
            return columns.get(key, builder::getAsInt).value;
        }

        int anchor(AnchorKey key, IntSupplier builder) {
            Lookup<Integer> lookup = anchors.get(key, builder::getAsInt);
            if (lookup.miss) NaturalHighwayRuntimeStats.profileAnchorCacheMiss();
            else NaturalHighwayRuntimeStats.profileAnchorCacheHit();
            return lookup.value;
        }

        InterstateInterchangeNode node(NodeKey key, Supplier<InterstateInterchangeNode> builder) {
            Lookup<InterstateInterchangeNode> lookup = nodes.get(key, builder);
            if (lookup.miss) NaturalHighwayRuntimeStats.nodePlanCacheMiss();
            else NaturalHighwayRuntimeStats.nodePlanCacheHit();
            return lookup.value;
        }

        CorridorEngineeringSegment segment(SegmentKey key, Supplier<CorridorEngineeringSegment> builder) {
            Lookup<CorridorEngineeringSegment> lookup = segments.get(key, builder);
            if (lookup.miss) {
                NaturalHighwayRuntimeStats.engineeringSegmentCacheMiss();
                validateNeighborIfReady(key, -1, lookup.value);
                validateNeighborIfReady(key, 1, lookup.value);
            } else {
                NaturalHighwayRuntimeStats.engineeringSegmentCacheHit();
            }
            return lookup.value;
        }

        private void validateNeighborIfReady(SegmentKey key, int offset,
                                             CorridorEngineeringSegment current) {
            SegmentKey neighborKey = new SegmentKey(key.orientation, key.corridorIndex,
                    key.segmentIndex + offset, key.engineeringVersion);
            CorridorEngineeringSegment neighbor = segments.peekCompleted(neighborKey);
            if (neighbor == null) return;
            if (offset < 0) NaturalHighwaySeamValidator.validate(neighbor, current);
            else NaturalHighwaySeamValidator.validate(current, neighbor);
        }
    }

    record HeightKey(int x, int z, HeightKind kind) {}
    record ColumnKey(int x, int z) {}
    record AnchorKey(PrimaryHighwayNetwork.Orientation orientation, int corridorIndex, long station) {}
    record NodeKey(int northSouthIndex, int eastWestIndex) {}
    record SegmentKey(PrimaryHighwayNetwork.Orientation orientation, int corridorIndex,
                      long segmentIndex, int engineeringVersion) {}
    enum HeightKind { SURFACE, OCEAN_FLOOR }

    private record WorldKey(long session, String dimension, long seed,
                            int generatorIdentity, int randomStateIdentity) {}

    private record Lookup<V>(V value, boolean miss) {}

    /** Access-order LRU plus a future value gives bounded single-flight construction. */
    private static final class BoundedSingleFlightCache<K, V> {
        private final int maximumSize;
        private final LinkedHashMap<K, CompletableFuture<V>> values = new LinkedHashMap<>(16, 0.75F, true);

        private BoundedSingleFlightCache(int maximumSize) {
            this.maximumSize = maximumSize;
        }

        private Lookup<V> get(K key, Supplier<V> builder) {
            CompletableFuture<V> future;
            boolean miss = false;
            synchronized (values) {
                future = values.get(key);
                if (future == null) {
                    miss = true;
                    future = new CompletableFuture<>();
                    values.put(key, future);
                    trimCompleted();
                }
            }
            if (miss) {
                try {
                    future.complete(builder.get());
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                    synchronized (values) {
                        values.remove(key, future);
                    }
                    throw failure;
                } finally {
                    synchronized (values) {
                        trimCompleted();
                    }
                }
            }
            try {
                return new Lookup<>(future.join(), miss);
            } catch (CompletionException failure) {
                throw failure;
            }
        }

        private V peekCompleted(K key) {
            synchronized (values) {
                CompletableFuture<V> future = values.get(key);
                if (future == null || !future.isDone() || future.isCompletedExceptionally()) return null;
                return future.getNow(null);
            }
        }

        private void trimCompleted() {
            if (values.size() <= maximumSize) return;
            Iterator<Map.Entry<K, CompletableFuture<V>>> iterator = values.entrySet().iterator();
            while (values.size() > maximumSize && iterator.hasNext()) {
                Map.Entry<K, CompletableFuture<V>> entry = iterator.next();
                if (entry.getValue().isDone()) iterator.remove();
            }
        }
    }
}
