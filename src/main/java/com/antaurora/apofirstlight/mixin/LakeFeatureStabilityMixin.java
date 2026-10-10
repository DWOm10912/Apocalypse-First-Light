package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanStore;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrain V2 stable layer for lava lakes (Phase 2b, 2026-10-10; docs/worldgen/terrain_v2_phase2_generation_v1.md):
 * the save audit of the Phase 2 acceptance world found vanilla lake_lava_underground lakes 3 to 7 blocks under the
 * plains (an air pocket over lava, right where a basement would be dug). A lake fills a 16 x 8 x 16 box from 4 below
 * its origin, lava in the lower half and air up to origin + 3; when that air would reach into the stable depth of the
 * planned surface (plains 12, foothills 6, fold belt 4) at the box's corners or centre, the lake is not placed. Deep
 * lava lakes in caves and every world without the plan are untouched.
 */
@Mixin(LakeFeature.class)
public abstract class LakeFeatureStabilityMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void apocalypse$keepStableLayer(FeaturePlaceContext<LakeFeature.Configuration> context,
                                            CallbackInfoReturnable<Boolean> cir) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) context.level().getLevel().getChunkSource().randomState();
        if (!access.apocalypse$hasTerrainPlan()) return;
        TerrainPlanSurface plan = TerrainPlanStore.peek(access.apocalypse$getSeed());
        if (plan == null) return;
        BlockPos o = context.origin();
        int airTop = o.getY() + 3;
        int[][] at = {{0, 0}, {15, 0}, {0, 15}, {15, 15}, {8, 8}};
        for (int[] d : at) {
            int x = o.getX() + d[0], z = o.getZ() + d[1];
            if (airTop >= plan.baseHeightAt(x, z) - plan.stableDepth(x, z) - 1) { cir.setReturnValue(false); return; }
        }
    }
}
