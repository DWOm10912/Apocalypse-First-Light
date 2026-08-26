package com.antaurora.apofirstlight.worldgen.rural;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/** Structure shell. The bounded piece performs the deterministic Rural planning and replay. */
public final class RuralNaturalStructure extends Structure {
    public static final Codec<RuralNaturalStructure> CODEC = Structure.simpleCodec(RuralNaturalStructure::new);
    public RuralNaturalStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        BlockPos center = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        long fastStart = System.nanoTime();
        if (!RuralNaturalGenerator.fastSiteCheck(context, center)) {
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL RURAL NATURAL][FIND_GENERATION_POINT] REJECT_FAST candidateChunk={} center={} fastSiteCheckMs={}",
                    chunk, center, (System.nanoTime() - fastStart) / 1_000_000.0D);
            return Optional.empty();
        }
        double fastSiteCheckMs = (System.nanoTime() - fastStart) / 1_000_000.0D;
        RuralPlan plan = RuralNaturalGenerator.plan(context, center, fastSiteCheckMs);
        if (!plan.valid()) {
            ApocalypseFirstLight.LOGGER.debug(
                    "[AFL RURAL NATURAL][FIND_GENERATION_POINT] REJECT candidateChunk={} center={} tier={} seed={} reason={} targetBuildings={} finalBuildings={} farmPlotTarget={} farmPlotCount={} roadLayout={} bounds={}",
                    chunk, center, plan.scaleTier(), plan.deterministicSeed(), plan.failureReason(),
                    plan.targetBuildings(), plan.lots().size(), plan.farmPlotTarget(), plan.farmPlotCount(),
                    plan.roadLayout(), plan.reservation());
            return Optional.empty();
        }
        BoundingBox bounds = new BoundingBox(plan.reservation().minX(), context.heightAccessor().getMinBuildHeight(),
                plan.reservation().minZ(), plan.reservation().maxX(), context.heightAccessor().getMaxBuildHeight() - 1,
                plan.reservation().maxZ());
        BlockPos stubPosition = new BlockPos(center.getX(), plan.site().medianY(), center.getZ());
        Holder<Biome> stubBiome = context.chunkGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(stubPosition.getX()), QuartPos.fromBlock(stubPosition.getY()),
                QuartPos.fromBlock(stubPosition.getZ()), context.randomState().sampler());
        boolean biomeAllowed = context.validBiome().test(stubBiome);
        String biomeId = stubBiome.unwrapKey().map(key -> key.location().toString()).orElse("unbound");
        ApocalypseFirstLight.LOGGER.info(
                "[AFL RURAL NATURAL][PLANNER_ACCEPT] candidateChunk={} center={} tier={} seed={} targetBuildings={} finalBuildings={} farmPlotTarget={} farmPlotCount={} roadLayout={} bounds={} stubPosition={}",
                chunk, center, plan.scaleTier(), plan.deterministicSeed(), plan.targetBuildings(), plan.lots().size(),
                plan.farmPlotTarget(), plan.farmPlotCount(), plan.roadLayout(), bounds, stubPosition);
        if (biomeAllowed) {
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL RURAL NATURAL][BIOME_CHECK] candidateChunk={} stubPosition={} biome={} configuredBiomeSet={} allowed=true",
                    chunk, stubPosition, biomeId, biomes());
        } else {
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL RURAL NATURAL][BIOME_CHECK] candidateChunk={} stubPosition={} biome={} configuredBiomeSet={} allowed=false; vanilla Structure.findValidGenerationPoint will reject this Planner-valid candidate",
                    chunk, stubPosition, biomeId, biomes());
        }
        return Optional.of(new GenerationStub(stubPosition, (StructurePiecesBuilder builder) -> {
            boolean emptyBefore = builder.isEmpty();
            RuralNaturalPiece piece = new RuralNaturalPiece(center, bounds, plan);
            builder.addPiece(piece);
            ApocalypseFirstLight.LOGGER.info(
                    "[AFL RURAL NATURAL][GENERATION_STUB_RESULT] returnedStub=true biomeGatePassed=true center={} stubPosition={} tier={} pieceCount=1 builderEmptyBefore={} builderEmptyAfter={} pieceType={} pieceBounds={} planValid={} planIdentity={}",
                    center, stubPosition, plan.scaleTier(), emptyBefore, builder.isEmpty(),
                    RuralNaturalWorldgen.RURAL_PIECE.getId(), bounds, plan.valid(), System.identityHashCode(plan));
        }));
    }

    @Override
    public StructureType<?> type() {
        return RuralNaturalWorldgen.RURAL_STRUCTURE.get();
    }
}
