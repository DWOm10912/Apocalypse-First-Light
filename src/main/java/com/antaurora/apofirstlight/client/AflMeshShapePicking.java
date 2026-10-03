package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.meshshape.AflMeshShapeBlock;
import com.antaurora.apofirstlight.meshshape.AflMeshShapes;
import com.antaurora.apofirstlight.meshshape.AflOverhangPickBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Crosshair picking for Mesh Shape blocks whose selection leaves their own cell (an open locker door), and for blocks with
 * parts standing above their cell (AflOverhangPickBlock: an open dumpster lid). Vanilla only tests a block while the view
 * ray passes through that block's cell; this adds the horizontal neighbours of the cells the ray crosses, and the cells
 * below them, and keeps the nearer of the two results (so walls and entities in front still win). Same approach as the
 * restroom stall door's supplemental pick, driven by the shape profile instead of a hard-coded leaf.
 */
public final class AflMeshShapePicking {
    private AflMeshShapePicking() {
    }

    public static void refine(float partialTick) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null || mc.getCameraEntity() != mc.player) return;
        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 end = eye.add(mc.player.getViewVector(partialTick).scale(mc.gameMode.getPickRange()));
        double best = mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS
                ? eye.distanceToSqr(end) : eye.distanceToSqr(mc.hitResult.getLocation());
        List<BlockPos> path = new ArrayList<>();
        BlockGetter.traverseBlocks(eye, end, path, (cells, pos) -> { cells.add(pos.immutable()); return null; }, cells -> null);
        Set<BlockPos> seen = new HashSet<>(path);
        BlockHitResult found = null;
        int radius = AflMeshShapes.pickRadius();
        for (BlockPos cell : path) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            BlockPos pos = cell.offset(dx, 0, dz);
            if (!seen.add(pos)) continue;
            if (!mc.level.hasChunkAt(pos)) continue;
            var state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof AflMeshShapeBlock block)) continue;
            var shape = block.meshShape(state);
            if (!shape.selectionOutOfCell()) continue;
            var hit = shape.selection().clip(eye, end, pos);
            if (hit != null && eye.distanceToSqr(hit.getLocation()) < best) {
                best = eye.distanceToSqr(hit.getLocation());
                found = hit;
            }
        }
        // parts standing above their block's cell (AflOverhangPickBlock, e.g. an open dumpster lid): the blocks below the
        // cells the ray crosses
        Set<BlockPos> below = new HashSet<>();
        for (BlockPos cell : path) for (int dy = 1; dy <= AflOverhangPickBlock.MAX_CELLS_BELOW; dy++) {
            BlockPos pos = cell.below(dy);
            if (!below.add(pos) || !mc.level.hasChunkAt(pos)) continue;
            var state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof AflOverhangPickBlock block)) continue;
            List<AABB> boxes = block.overhangPickBoxes(mc.level, state, pos).stream().map(b -> b.move(-pos.getX(), -pos.getY(), -pos.getZ())).toList();
            if (boxes.isEmpty()) continue;
            BlockHitResult hit = AABB.clip(boxes, eye, end, pos);
            if (hit != null && eye.distanceToSqr(hit.getLocation()) < best) {
                best = eye.distanceToSqr(hit.getLocation());
                found = hit;
            }
        }
        if (found != null) {
            mc.hitResult = clampToCell(found);
            mc.crosshairPickEntity = null;
        } else if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof AflMeshShapeBlock) {
            mc.hitResult = clampToCell(hit);
        }
    }

    /**
     * The server rejects use packets whose hit point lies more than 1 block from the block centre on any axis; the
     * interaction region is decided from the player's view ray (AflMeshShapeBlock#meshInteraction), not this point.
     */
    private static BlockHitResult clampToCell(BlockHitResult hit) {
        Vec3 c = Vec3.atCenterOf(hit.getBlockPos()), p = hit.getLocation();
        Vec3 q = new Vec3(clamp(p.x, c.x), clamp(p.y, c.y), clamp(p.z, c.z));
        return q.equals(p) ? hit : new BlockHitResult(q, hit.getDirection(), hit.getBlockPos(), hit.isInside());
    }

    private static double clamp(double v, double centre) {
        return Math.max(centre - 0.999, Math.min(centre + 0.999, v));
    }
}
