package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.network.road.RoadWorksRef;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No vanilla water or lava spring in or beside a road's works (2026-10-19, docs/worldgen/federal_highways_c3.md): a
 * spring opens where a stone face has exactly one open side, and a road cut's face and its top edge are such faces, so
 * the cut at sh_hills 3463 -1679 had three springs running down its slope onto the shoulder. Springs deeper than the
 * works' rock (RoadWorksRef#springBlocked) and every world without the works are untouched.
 */
@Mixin(SpringFeature.class)
public abstract class SpringFeatureRoadMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void apocalypse$keepOffRoads(FeaturePlaceContext<SpringConfiguration> context, CallbackInfoReturnable<Boolean> cir) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) context.level().getLevel().getChunkSource().randomState();
        if (!access.apocalypse$hasTerrainPlan()) return;
        // U2: no lava springs in a deep world (design section 13.9)
        if (access.apocalypse$underground() != null && context.config().state.is(net.minecraft.tags.FluidTags.LAVA)) { cir.setReturnValue(false); return; }
        TerrainPlanSurface plan = access.apocalypse$terrainPlanSurface();
        RoadWorksRef works = access.apocalypse$roadWorksRef();
        if (plan == null || works == null) return;
        BlockPos o = context.origin();
        if (works.springBlocked(o.getX(), o.getY(), o.getZ(), plan)) cir.setReturnValue(false);
    }
}
