package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverCarver;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanStore;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * Terrain V2 stable layer for the carvers (Phase 2, 2026-10-10; docs/worldgen/terrain_v2_phase2_generation_v1.md):
 * the cave and canyon carvers run after the noise, so the density-side stable layer (PlanStabilityDensity) cannot stop
 * them. Every block a carver would remove is checked here: within the column's stable depth under the planned surface
 * (plains 12, foothills 6, fold belt 4, water floors 6) the carver leaves it. Deeper caves, ravines in the belt and
 * everything in worlds without the plan are untouched. Uses the plan grid surface without detail (+-0.5 block).
 * Phase 2b (2026-10-10): also nothing under a river bed, its graded banks or a marsh pool (8 below the lowest
 * water level in reach), so river water never hangs over a carved cave.
 */
@Mixin(WorldCarver.class)
public abstract class WorldCarverStabilityMixin<C extends CarverConfiguration> {
    @Inject(method = "carveBlock", at = @At("HEAD"), cancellable = true)
    private void apocalypse$keepStableLayer(CarvingContext context, C config, ChunkAccess chunk,
                                            Function<BlockPos, Holder<Biome>> biomes, CarvingMask mask,
                                            BlockPos.MutableBlockPos pos, BlockPos.MutableBlockPos below,
                                            Aquifer aquifer, MutableBoolean grass, CallbackInfoReturnable<Boolean> cir) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) context.randomState();
        if (!access.apocalypse$hasTerrainPlan()) return;
        TerrainPlanSurface plan = TerrainPlanStore.peek(access.apocalypse$getSeed());
        if (plan == null) return;
        int stable = plan.stableDepth(pos.getX(), pos.getZ());
        if (stable > 0) {
            double depth = plan.baseHeightAt(pos.getX(), pos.getZ()) - pos.getY();
            if (depth < stable + 1) { cir.setReturnValue(false); return; }
        }
        // Phase 2b: no carver under a river bed, its banks or a marsh pool (RiverCarver.carveCeiling)
        if (pos.getY() >= RiverCarver.carveCeiling(plan, pos.getX(), pos.getZ())) cir.setReturnValue(false);
    }
}
