package com.antaurora.apofirstlight.radiation;

import com.antaurora.apofirstlight.contamination.ItemContamination;
import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Derives nearby container radiation directly from inventories in already-loaded chunks. */
public final class ContainerRadiation {
    public static final int SEARCH_RADIUS_BLOCKS = 32;
    public static final double SEARCH_RADIUS_SQR = SEARCH_RADIUS_BLOCKS * SEARCH_RADIUS_BLOCKS;
    public static final double ORDINARY_CONTAINER_TRANSMISSION = 1.0D;
    public static final double LEAD_CHEST_TRANSMISSION = 0.50D;
    private static final int UPDATE_INTERVAL_TICKS = 20;
    private static final double MIN_POINT_TRANSMISSION = 0.01D;
    private static final double SOURCE_EXIT_EPSILON = 1.0E-6D;
    private static final Map<ServerPlayer, CachedScan> PLAYER_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ContainerRadiation() {
    }

    /** At most one inventory scan per player per 20 ticks; callers in the same interval share the result. */
    public static ScanResult scan(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long tick = level.getGameTime();
        CachedScan cached = PLAYER_CACHE.get(player);
        if (cached != null && cached.level == level && tick >= cached.tick
                && tick - cached.tick < UPDATE_INTERVAL_TICKS) {
            return cached.result;
        }
        ScanComputation computation = scanLoadedChunks(level, player.getBoundingBox().getCenter(), false);
        ScanResult result = new ScanResult(computation.totalRadiation, computation.contaminatedContainers);
        PLAYER_CACHE.put(player, new CachedScan(level, tick, result));
        return result;
    }

    /** Fresh, uncached command-only scan with exact per-source and per-shielding-block evidence. */
    public static DebugScanResult debugScan(ServerPlayer player) {
        Vec3 playerCenter = player.getBoundingBox().getCenter();
        ScanComputation computation = scanLoadedChunks(player.serverLevel(), playerCenter, true);
        return new DebugScanResult(playerCenter, computation.totalRadiation,
                computation.contaminatedContainers, computation.strongestSources);
    }

    private static ScanComputation scanLoadedChunks(ServerLevel level, Vec3 playerCenter,
                                                     boolean collectDebugDetails) {
        int minChunkX = SectionPos.blockToSectionCoord((int) Math.floor(playerCenter.x - SEARCH_RADIUS_BLOCKS));
        int maxChunkX = SectionPos.blockToSectionCoord((int) Math.floor(playerCenter.x + SEARCH_RADIUS_BLOCKS));
        int minChunkZ = SectionPos.blockToSectionCoord((int) Math.floor(playerCenter.z - SEARCH_RADIUS_BLOCKS));
        int maxChunkZ = SectionPos.blockToSectionCoord((int) Math.floor(playerCenter.z + SEARCH_RADIUS_BLOCKS));
        double totalRadiation = 0.0D;
        int contaminatedContainers = 0;
        List<SourceBreakdown> sourceBreakdowns = collectDebugDetails ? new ArrayList<>() : null;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity.isRemoved()) continue;
                    BlockPos sourcePos = blockEntity.getBlockPos();
                    Vec3 sourceCenter = Vec3.atCenterOf(sourcePos);
                    double distanceSquared = playerCenter.distanceToSqr(sourceCenter);
                    if (distanceSquared > SEARCH_RADIUS_SQR) continue;
                    double internalRadiation = internalRadiation(blockEntity);
                    if (internalRadiation <= ItemContamination.CLEAN_EPSILON) continue;

                    contaminatedContainers++;
                    double distanceAttenuation = 1.0D / Math.max(1.0D, distanceSquared);
                    PointTrace pointTrace = collectDebugDetails
                            ? pointTrace(level, sourcePos, sourceCenter, playerCenter)
                            : null;
                    double pointTransmission = pointTrace == null
                            ? pointTransmission(level, sourcePos, sourceCenter, playerCenter)
                            : pointTrace.transmission;
                    double contribution = internalRadiation * containerTransmission(blockEntity)
                            * distanceAttenuation * pointTransmission;
                    totalRadiation += contribution;
                    if (sourceBreakdowns != null) {
                        sourceBreakdowns.add(new SourceBreakdown(
                                BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()),
                                sourcePos.immutable(), internalRadiation, Math.sqrt(distanceSquared),
                                distanceAttenuation, pointTrace.shieldingBlocks.size(), pointTransmission,
                                contribution, pointTrace.shieldingBlocks));
                    }
                }
            }
        }
        List<SourceBreakdown> strongestSources = List.of();
        if (sourceBreakdowns != null) {
            sourceBreakdowns.sort(Comparator.comparingDouble(SourceBreakdown::contribution).reversed());
            strongestSources = List.copyOf(sourceBreakdowns.subList(0, Math.min(5, sourceBreakdowns.size())));
        }
        return new ScanComputation(totalRadiation, contaminatedContainers, strongestSources);
    }

    private static double internalRadiation(BlockEntity blockEntity) {
        if (blockEntity instanceof Container container) {
            // Reading RandomizableContainerBlockEntity#getItem generates pending loot. Until the loot table has
            // actually been unpacked there are no direct ItemStacks to irradiate the world, so skip it read-only.
            if (blockEntity instanceof RandomizableContainerBlockEntity
                    && blockEntity.saveWithoutMetadata().contains("LootTable", Tag.TAG_STRING)) {
                return 0.0D;
            }
            double total = 0.0D;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                total += ItemContamination.getStackSourceRate(container.getItem(slot));
            }
            return total;
        }

        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve()
                .map(ContainerRadiation::handlerRadiation)
                .orElse(0.0D);
    }

    private static double containerTransmission(BlockEntity blockEntity) {
        return blockEntity instanceof LeadChestBlockEntity
                ? LEAD_CHEST_TRANSMISSION
                : ORDINARY_CONTAINER_TRANSMISSION;
    }

    private static double handlerRadiation(IItemHandler handler) {
        double total = 0.0D;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            total += ItemContamination.getStackSourceRate(handler.getStackInSlot(slot));
        }
        return total;
    }

    private static double pointTransmission(ServerLevel level, BlockPos sourcePos,
                                            Vec3 sourceCenter, Vec3 playerCenter) {
        Vec3 rayStart = rayStartOutsideSource(sourceCenter, playerCenter);
        if (rayStart == null) return 1.0D;
        PointRay ray = new PointRay(level, sourcePos, BlockPos.containing(playerCenter), false);
        return traversePointRay(rayStart, playerCenter, ray);
    }

    private static PointTrace pointTrace(ServerLevel level, BlockPos sourcePos,
                                         Vec3 sourceCenter, Vec3 playerCenter) {
        Vec3 rayStart = rayStartOutsideSource(sourceCenter, playerCenter);
        if (rayStart == null) return new PointTrace(1.0D, List.of());
        PointRay ray = new PointRay(level, sourcePos, BlockPos.containing(playerCenter), true);
        double transmission = traversePointRay(rayStart, playerCenter, ray);
        return new PointTrace(transmission, List.copyOf(ray.shieldingBlocks));
    }

    private static Vec3 rayStartOutsideSource(Vec3 sourceCenter, Vec3 playerCenter) {
        Vec3 delta = playerCenter.subtract(sourceCenter);
        double maxComponent = Math.max(Math.abs(delta.x), Math.max(Math.abs(delta.y), Math.abs(delta.z)));
        if (maxComponent <= 0.5D) return null;
        return sourceCenter.add(delta.scale((0.5D + SOURCE_EXIT_EPSILON) / maxComponent));
    }

    private static double traversePointRay(Vec3 rayStart, Vec3 playerCenter, PointRay ray) {
        // Minecraft's DDA advances exactly one axis per step. Equality falls through deterministically,
        // so face/edge/corner ties select one voxel path instead of fanning out into adjacent voxels.
        return BlockGetter.traverseBlocks(rayStart, playerCenter, ray, (context, position) -> {
            if (position.equals(context.sourcePos)) return null;
            if (position.equals(context.playerPos)) return context.transmission;
            LevelChunk chunk = context.level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(position.getX()),
                    SectionPos.blockToSectionCoord(position.getZ()));
            if (chunk == null) return 0.0D;
            BlockState state = chunk.getBlockState(position);
            if (state.is(RadiationShielding.SHIELDING_BLOCKS)
                    && context.countShieldingBlock(position, state)) {
                context.transmission *= RadiationShielding.RC_TRANSMISSION;
                if (context.transmission <= MIN_POINT_TRANSMISSION) return context.transmission;
            }
            return null;
        }, context -> context.transmission);
    }

    public record ScanResult(double totalRadiation, int contaminatedContainers) {
    }

    public record DebugScanResult(Vec3 playerCenter, double totalRadiation, int contaminatedContainers,
                                  List<SourceBreakdown> strongestSources) {
    }

    public record SourceBreakdown(ResourceLocation blockId, BlockPos position, double internalRadiation,
                                  double distance, double distanceFactor, int shieldingHits,
                                  double shieldingTransmission, double contribution,
                                  List<ShieldHit> shieldingBlocks) {
    }

    public record ShieldHit(BlockPos position, ResourceLocation blockId) {
    }

    private record PointTrace(double transmission, List<ShieldHit> shieldingBlocks) {
    }

    private record ScanComputation(double totalRadiation, int contaminatedContainers,
                                   List<SourceBreakdown> strongestSources) {
    }

    private record CachedScan(ServerLevel level, long tick, ScanResult result) {
    }

    private static final class PointRay {
        private final ServerLevel level;
        private final BlockPos sourcePos;
        private final BlockPos playerPos;
        private final List<ShieldHit> shieldingBlocks;
        private LongOpenHashSet countedShieldingBlocks;
        private double transmission = 1.0D;

        private PointRay(ServerLevel level, BlockPos sourcePos, BlockPos playerPos, boolean collectDebugDetails) {
            this.level = level;
            this.sourcePos = sourcePos;
            this.playerPos = playerPos;
            this.shieldingBlocks = collectDebugDetails ? new ArrayList<>() : null;
        }

        private boolean countShieldingBlock(BlockPos position, BlockState state) {
            if (countedShieldingBlocks == null) countedShieldingBlocks = new LongOpenHashSet(4);
            if (!countedShieldingBlocks.add(position.asLong())) return false;
            if (shieldingBlocks != null) {
                shieldingBlocks.add(new ShieldHit(position.immutable(),
                        BuiltInRegistries.BLOCK.getKey(state.getBlock())));
            }
            return true;
        }
    }
}
