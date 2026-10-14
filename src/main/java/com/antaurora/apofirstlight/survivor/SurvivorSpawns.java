package com.antaurora.apofirstlight.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.radiation.RadiationManager;
import com.antaurora.apofirstlight.radiation.RadiationZone;
import com.antaurora.apofirstlight.tree.TreeBlocks;
import com.antaurora.apofirstlight.tree.TreeLogBlock;
import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanStore;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Successor starts (docs/gameplay/survivor_death_succession_v0.md 5), always in the overworld.
 * <ol>
 * <li>At the death ({@link #plan}): providers propose columns, each is screened without touching chunks (world border,
 * 192 blocks from the death, 128 from the old bed, natural radiation not heavy fallout or extreme, Terrain V2 plan: dry
 * land away from the shore and not steep, biome: no ocean, river or beach). The first column that passes is stored with
 * the pending successor; a relog or restart reads it back instead of rolling again.</li>
 * <li>At the takeover ({@link #resolve}): the planned column is checked on the real ground (its chunk loaded or
 * generated): solid, dry, safe, not a tree, free head room. If it fails, further candidates follow, at most
 * {@value #MAX_GROUND_CHECKS} ground checks in all.</li>
 * <li>Fallback: none passed; the player stays where vanilla's respawn put it (the world spawn area), source
 * "fallback". Never the death point by design, but the fallback does not check the distance.</li>
 * </ol>
 */
public final class SurvivorSpawns {
    public static final int MIN_FROM_DEATH = 192;
    public static final int MIN_FROM_BED = 128;
    static final int PLAN_ATTEMPTS = 96;
    static final int MAX_GROUND_CHECKS = 8;
    private static final int BORDER_MARGIN = 32;

    private static final List<SurvivorSpawnProvider> PROVIDERS = new CopyOnWriteArrayList<>(List.of(new WildernessSpawnProvider()));

    record Start(ServerLevel level, BlockPos feet, String source) {
    }

    private SurvivorSpawns() {
    }

    /** Adds a provider in front of the existing ones (later: states, towns, POI). */
    public static void register(SurvivorSpawnProvider provider) {
        PROVIDERS.add(0, provider);
    }

    /** At the death: stores the first screened column with the pending successor, or nothing (takeover falls back). */
    static void plan(MinecraftServer server, SurvivorLineage lineage, @Nullable SurvivorRecord dead, SurvivorRecord next) {
        SurvivorSpawnProvider.Context context = context(server, lineage, dead, next);
        for (SurvivorSpawnProvider provider : PROVIDERS) {
            for (int attempt = 0; attempt < PLAN_ATTEMPTS; attempt++) {
                BlockPos column = provider.propose(context, attempt);
                if (column == null || !screen(context, column)) continue;
                next.hasSpawnPlan = true;
                next.spawnPlanX = column.getX();
                next.spawnPlanZ = column.getZ();
                next.spawnSource = provider.id();
                return;
            }
        }
        ApocalypseFirstLight.LOGGER.warn("[AFL Survivor] no successor start column passed the screen for {}; the takeover will fall back",
                lineage.accountName);
    }

    /** At the takeover: the planned column on the real ground, then further candidates; null = fallback. */
    @Nullable
    static Start resolve(MinecraftServer server, SurvivorLineage lineage, @Nullable SurvivorRecord dead, SurvivorRecord next) {
        SurvivorSpawnProvider.Context context = context(server, lineage, dead, next);
        ServerLevel level = context.level();
        int checks = 0;
        if (next.hasSpawnPlan) {
            checks++;
            BlockPos feet = ground(level, next.spawnPlanX, next.spawnPlanZ);
            if (feet != null) return new Start(level, feet, next.spawnSource != null ? next.spawnSource : WildernessSpawnProvider.ID);
        }
        for (SurvivorSpawnProvider provider : PROVIDERS) {
            for (int attempt = 0; attempt < PLAN_ATTEMPTS && checks < MAX_GROUND_CHECKS; attempt++) {
                BlockPos column = provider.propose(context, attempt);
                if (column == null || next.hasSpawnPlan && column.getX() == next.spawnPlanX && column.getZ() == next.spawnPlanZ
                        || !screen(context, column)) {
                    continue;
                }
                checks++;
                BlockPos feet = ground(level, column.getX(), column.getZ());
                if (feet != null) return new Start(level, feet, provider.id());
            }
        }
        return null;
    }

    private static SurvivorSpawnProvider.Context context(MinecraftServer server, SurvivorLineage lineage,
                                                         @Nullable SurvivorRecord dead, SurvivorRecord next) {
        ServerLevel level = server.overworld();
        String overworld = Level.OVERWORLD.location().toString();
        BlockPos death = dead != null && overworld.equals(dead.deathDimension) ? dead.deathPos : null;
        BlockPos bed = overworld.equals(lineage.lastBedDimension) ? lineage.lastBed : null;
        int ringMin = SurvivorConfig.SPAWN_RING_MIN.get();
        int ringMax = Math.max(ringMin + 64, SurvivorConfig.SPAWN_RING_MAX.get());
        return new SurvivorSpawnProvider.Context(level, lineage.playerId, next.lifeIndex, next.rollSeed,
                level.getSharedSpawnPos(), death, bed, ringMin, ringMax);
    }

    /** Cheap column screen: no chunk is loaded or generated here. */
    static boolean screen(SurvivorSpawnProvider.Context context, BlockPos column) {
        ServerLevel level = context.level();
        int x = column.getX(), z = column.getZ();
        if (!level.getWorldBorder().isWithinBounds(x, z) || level.getWorldBorder().getDistanceToBorder(x, z) < BORDER_MARGIN) {
            return false;
        }
        if (farther(context.deathPos(), x, z, MIN_FROM_DEATH) || farther(context.lastBed(), x, z, MIN_FROM_BED)) return false;
        RadiationZone zone = RadiationManager.getNaturalZone(level, column);
        if (zone == RadiationZone.HEAVY_FALLOUT || zone == RadiationZone.EXTREME) return false;
        TerrainPlanSurface plan = terrainPlan(level);
        if (plan != null && (plan.waterClass(x, z) != 0 || plan.shoreDistance(x, z) < 24 || plan.slopeAt(x, z) > 0.25)) {
            return false;
        }
        Holder<Biome> biome = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),
                QuartPos.fromBlock(level.getSeaLevel()), QuartPos.fromBlock(z), level.getChunkSource().randomState().sampler());
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER)
                && !biome.is(BiomeTags.IS_BEACH);
    }

    /** True when {@code from} exists and the column is closer to it than {@code min} (horizontal). */
    private static boolean farther(@Nullable BlockPos from, int x, int z, int min) {
        if (from == null) return false;
        long dx = x - from.getX(), dz = z - from.getZ();
        return dx * dx + dz * dz < (long) min * min;
    }

    @Nullable
    private static TerrainPlanSurface terrainPlan(ServerLevel level) {
        Object state = level.getChunkSource().randomState();
        if (!(state instanceof RandomStateSeedAccess access) || !access.apocalypse$hasTerrainPlan()) return null;
        return TerrainPlanStore.peek(access.apocalypse$getSeed());
    }

    /**
     * The real ground check (loads or generates the column's chunk): the top solid block under open sky (leaves ignored),
     * dry and safe, not part of a tree, with room for a standing player and no fluid around the body. Returns the feet
     * position, or null.
     */
    @Nullable
    static BlockPos ground(ServerLevel level, int x, int z) {
        if (!level.getWorldBorder().isWithinBounds(x, z)) return null;
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 3) return null;
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.getFluidState().isEmpty() || unsafe(ground) || ground.getCollisionShape(level, below).isEmpty()) return null;
        AABB body = EntityType.PLAYER.getDimensions().makeBoundingBox(x + 0.5, y, z + 0.5);
        if (!level.noCollision(body) || level.containsAnyLiquid(body.inflate(0.5, 0.0, 0.5))) return null;
        return feet;
    }

    private static boolean unsafe(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(BlockTags.FIRE)
                || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.LAVA)
                || state.getBlock() instanceof TreeLogBlock || TreeBlocks.isTree(state);
    }
}
