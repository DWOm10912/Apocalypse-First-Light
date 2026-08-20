package com.antaurora.apofirstlight.world.bunker;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Final-candidate-only cleanup performed immediately before structure placement. */
public final class BunkerPlacementHygiene {
    private static final int SURFACE_BAND_BELOW = 2;
    private static final int SURFACE_BAND_ABOVE = 6;
    private static final int ENTITY_MARGIN = 2;
    private static final int ENTITY_SEARCH_RADIUS = 10;
    private static final int SILENT_AIR_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
            | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
            | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS;

    private BunkerPlacementHygiene() {}

    public static Stats prepareForPlacement(ServerLevel level, BoundingBox box, int referenceSurfaceY) {
        int vegetation = clearNaturalDecorations(level, box, referenceSurfaceY);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, expanded(box));
        int moved = 0;
        int failed = 0;
        for (LivingEntity entity : entities) {
            if (entity.isRemoved() || entity instanceof net.minecraft.world.entity.player.Player) continue;
            if (entity.isPassenger() || entity.isVehicle()) {
                failed++;
                continue;
            }
            if (findSafeOutsidePosition(level, box, entity.position().x, entity.position().z)
                    .map(target -> {
                        entity.teleportTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
                        return true;
                    }).orElse(false)) {
                moved++;
            } else {
                failed++;
            }
        }
        return new Stats(vegetation, entities.size(), moved, failed);
    }

    private static int clearNaturalDecorations(ServerLevel level, BoundingBox box, int referenceSurfaceY) {
        int cleared = 0;
        int minY = Math.max(level.getMinBuildHeight(), referenceSurfaceY - SURFACE_BAND_BELOW);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, referenceSurfaceY + SURFACE_BAND_ABOVE);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (isNaturalDecoration(state)
                            && level.setBlock(pos, Blocks.AIR.defaultBlockState(), SILENT_AIR_FLAGS)) {
                        cleared++;
                    }
                }
            }
        }
        return cleared;
    }

    private static boolean isNaturalDecoration(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.SAPLINGS)
                || state.is(net.minecraft.tags.BlockTags.FLOWERS)
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.VINE)
                || state.is(Blocks.SNOW);
    }

    private static AABB expanded(BoundingBox box) {
        return new AABB(box.minX() - ENTITY_MARGIN, box.minY() - ENTITY_MARGIN, box.minZ() - ENTITY_MARGIN,
                box.maxX() + 1 + ENTITY_MARGIN, box.maxY() + 1 + ENTITY_MARGIN, box.maxZ() + 1 + ENTITY_MARGIN);
    }

    private static java.util.Optional<BlockPos> findSafeOutsidePosition(ServerLevel level, BoundingBox box,
                                                                          double entityX, double entityZ) {
        List<BlockPos> candidates = new ArrayList<>();
        int nearestX = entityX < (box.minX() + box.maxX()) / 2.0 ? box.minX() - ENTITY_MARGIN : box.maxX() + ENTITY_MARGIN;
        int nearestZ = entityZ < (box.minZ() + box.maxZ()) / 2.0 ? box.minZ() - ENTITY_MARGIN : box.maxZ() + ENTITY_MARGIN;
        for (int radius = 0; radius <= ENTITY_SEARCH_RADIUS; radius++) {
            candidates.add(new BlockPos(nearestX - radius, 0, (int) Math.floor(entityZ)));
            candidates.add(new BlockPos(nearestX + radius, 0, (int) Math.floor(entityZ)));
            candidates.add(new BlockPos((int) Math.floor(entityX), 0, nearestZ - radius));
            candidates.add(new BlockPos((int) Math.floor(entityX), 0, nearestZ + radius));
        }
        return candidates.stream()
                .filter(pos -> outside(box, pos.getX(), pos.getZ()))
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(new BlockPos((int) entityX, 0, (int) entityZ))))
                .map(pos -> safeSurface(level, pos.getX(), pos.getZ()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .findFirst();
    }

    private static boolean outside(BoundingBox box, int x, int z) {
        return x < box.minX() - ENTITY_MARGIN || x > box.maxX() + ENTITY_MARGIN
                || z < box.minZ() - ENTITY_MARGIN || z > box.maxZ() + ENTITY_MARGIN;
    }

    private static java.util.Optional<BlockPos> safeSurface(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos head = feet.above();
        BlockPos support = feet.below();
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty()
                || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(head).getCollisionShape(level, head).isEmpty()
                || level.getBlockState(support).getCollisionShape(level, support).isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(feet);
    }

    public record Stats(int vegetationCleared, int livingEntitiesFound,
                        int livingEntitiesMoved, int livingEntitiesMoveFailed) {}
}
