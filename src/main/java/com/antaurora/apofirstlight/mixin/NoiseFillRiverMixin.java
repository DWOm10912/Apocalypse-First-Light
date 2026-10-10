package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.worldgen.RandomStateSeedAccess;
import com.antaurora.apofirstlight.worldgen.terrain.v2.RiverWaterFill;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanStore;
import com.antaurora.apofirstlight.worldgen.terrain.v2.TerrainPlanSurface;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Terrain V2 Phase 2b (2026-10-10; docs/worldgen/terrain_v2_phase2b_rivers_v1.md): river water, graded banks and the
 * open water of the tidal marsh, written into the chunk at the end of its noise fill (RiverWaterFill), so the surface
 * rules dress the new beds and banks and the carvers, lakes and features see the river. Only in worlds generated
 * from a Terrain V2 plan; everything else is untouched.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseFillRiverMixin {
    @Inject(method = "doFill", at = @At("RETURN"))
    private void apocalypse$riverWater(Blender blender, StructureManager structureManager, RandomState randomState,
                                       ChunkAccess chunk, int cellNoiseMinY, int cellCountY,
                                       CallbackInfoReturnable<ChunkAccess> cir) {
        RandomStateSeedAccess access = (RandomStateSeedAccess) (Object) randomState;
        if (!access.apocalypse$hasTerrainPlan()) return;
        TerrainPlanSurface plan = TerrainPlanStore.peek(access.apocalypse$getSeed());
        if (plan == null || plan.rivers == null) return;
        RiverWaterFill.apply(plan, cir.getReturnValue(),
                ((NoiseBasedChunkGenerator) (Object) this).generatorSettings().value().defaultBlock());
    }
}
