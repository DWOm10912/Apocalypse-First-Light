package com.antaurora.apofirstlight.dev.worldgen;

import com.antaurora.apofirstlight.block.RoadStructureBlock;
import com.antaurora.apofirstlight.meshhit.MeshBlockHitResult;
import com.antaurora.apofirstlight.meshhit.MeshHitClip;
import com.antaurora.apofirstlight.meshhit.MeshHitModels;
import com.antaurora.apofirstlight.registry.AflBlocks;
import com.antaurora.apofirstlight.worldgen.network.road.BridgeMesh;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bridge hit meshes and ground in a test world (C3, 2026-10-15; docs/worldgen/federal_highways_c3.md 10.8): around a
 * point, every road_structure block's hit mesh (meshhit/RoadStructureHits) and vertical rays through MeshHitClip on 8 x 8
 * points of each structure column, compared with the bridge's solid (BridgeMesh.solid): where it has a top in a
 * structure block the ray must stop there on the mesh; where it has nothing the ray must not stop on road_structure.
 * Also the block under each structure column's foot (grass kept, or the dirt of a replaced top).
 */
final class C3HitCheck {
    private C3HitCheck() {}

    static String run(Level level, int cx, int cz, int r, ClipContext.Block shape) {
        RoadWorks works = RoadStructureBlock.boundWorks();
        if (works == null) return "no road network bound";
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int blocks = 0, meshed = 0, triangles = 0, edges = 0;
        int topRays = 0, topOnMesh = 0, throughRays = 0, stoppedOutside = 0, columns = 0, grass = 0, dirt = 0, other = 0;
        double worst = 0;
        List<String> examples = new ArrayList<>();
        int[] edgeCount = new int[1];
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
            int top = Integer.MIN_VALUE, foot = Integer.MAX_VALUE;
            for (int y = level.getMinBuildHeight() + 1; y < level.getMaxBuildHeight(); y++) {
                BlockState s = level.getBlockState(p.set(x, y, z));
                if (!s.is(AflBlocks.ROAD_STRUCTURE.get())) continue;
                blocks++;
                top = Math.max(top, y);
                foot = Math.min(foot, y);
                MeshHitModels.Shape m = MeshHitModels.shape(level, p.immutable(), s);
                if (m == null) continue;
                meshed++;
                triangles += m.triangles();
                edgeCount[0] = 0;
                m.forEachEdge((x0, y0, z0, x1, y1, z1) -> edgeCount[0]++);
                edges += edgeCount[0];
            }
            if (top == Integer.MIN_VALUE) continue;
            columns++;
            BlockState under = level.getBlockState(p.set(x, foot - 1, z));
            if (under.is(Blocks.GRASS_BLOCK)) grass++;
            else if (under.is(Blocks.DIRT)) dirt++;
            else other++;
            for (int j = 0; j < 8; j++) for (int i = 0; i < 8; i++) {
                double px = x + (i + 0.5) / 8, pz = z + (j + 0.5) / 8;
                double[] solid = BridgeMesh.solid(works, px, pz);
                Vec3 from = new Vec3(px, top + 3, pz), to = new Vec3(px, foot - 3, pz);
                BlockHitResult hit = MeshHitClip.clip(level, new ClipContext(from, to, shape, ClipContext.Fluid.NONE, null));
                boolean onStructure = hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).is(AflBlocks.ROAD_STRUCTURE.get());
                if (solid == null) {
                    throughRays++;
                    if (onStructure) {
                        stoppedOutside++;
                        if (examples.size() < 4) examples.add(String.format(Locale.ROOT, "stopped where nothing is drawn %.2f %.2f %.2f", px, hit.getLocation().y, pz));
                    }
                    continue;
                }
                // a top inside a structure block (not the deck's road_surface)
                BlockState topBlock = level.getBlockState(p.set(x, (int) Math.floor(solid[1] - 1e-6), z));
                if (!topBlock.is(AflBlocks.ROAD_STRUCTURE.get())) continue;
                topRays++;
                if (onStructure && hit instanceof MeshBlockHitResult) {
                    double d = Math.abs(hit.getLocation().y - solid[1]);
                    if (d <= 0.01) topOnMesh++;
                    else if (examples.size() < 4) examples.add(String.format(Locale.ROOT, "top %.3f vs mesh %.3f at %.2f %.2f", solid[1], hit.getLocation().y, px, pz));
                    worst = Math.max(worst, d);
                } else if (examples.size() < 4) examples.add(String.format(Locale.ROOT, "no mesh hit at %.2f %.2f (%s)", px, pz, hit.getClass().getSimpleName()));
            }
        }
        return String.format(Locale.ROOT, "%d structure blocks in %d columns, %d with a hit mesh (%d triangles, %d outline edges); "
                        + "vertical rays: %d/%d stop on the mesh at its top (worst %.3f m), %d of %d rays where nothing is drawn stopped on structure; "
                        + "under the feet: grass %d, dirt %d, other %d%s",
                blocks, columns, meshed, triangles, edges, topOnMesh, topRays, worst, stoppedOutside, throughRays, grass, dirt, other,
                examples.isEmpty() ? "" : "; examples " + examples);
    }
}
