package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.world.biome.MainNationBiomeRegionPlan;
import com.antaurora.apofirstlight.worldgen.roads.RoadTerrainQuery;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainSource;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import java.util.*;
import java.util.function.IntFunction;

/** Observation adapter only. Uses existing noise TerrainQuery and Vanilla heightmaps, not a second surface finder. */
final class TerrainColumnInspection {
    static final String LOADED="LOADED_WORLD_BLOCKS",NOISE="NOISE_ESTIMATE";
    record Heights(Integer loaded,Integer noise) {}
    final ServerLevel level;
    final RoadTerrainQuery noise;
    final Set<Long> chunks=new HashSet<>();
    long blockChecks;int requestedColumns,extraNoiseColumns;
    TerrainColumnInspection(ServerLevel level){this.level=level;noise=new RoadTerrainQuery(level,4096);}
    Heights heights(int x,int z) {
        requestedColumns++;
        var n=noise.sample(x,z,TerrainSource.NOISE_PRE_DECORATION);
        var c=level.getChunkSource().getChunkNow(x>>4,z>>4);Integer loaded=null;
        if(c!=null){chunks.add(key(x>>4,z>>4));loaded=c.getHeight(Heightmap.Types.OCEAN_FLOOR,x&15,z&15)+1;}
        return new Heights(loaded,n.topSolidSurfaceY().isPresent()?n.topSolidSurfaceY().getAsInt():null);
    }
    JsonObject inspect(int x,int z,int depth) {
        JsonObject j=TerrainDiagnosticIO.identity(level,x,z);j.add("chunk",TerrainDiagnosticIO.GSON.toJsonTree(Map.of("x",x>>4,"z",z>>4)));
        j.add("macro",macro(x,z));j.addProperty("biome_region",MainNationBiomeRegionPlan.forSeed(level.getSeed()).regionAt(x,z).toString());
        var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
        JsonObject loaded=new JsonObject();
        if(chunk==null)loaded.addProperty("status","UNLOADED;NO_CHUNK_REQUESTED");
        else {
            chunks.add(key(x>>4,z>>4));loaded.addProperty("source",LOADED);
            int ws=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15)+1;
            int floor=chunk.getHeight(Heightmap.Types.OCEAN_FLOOR,x&15,z&15)+1;
            loaded.addProperty("world_surface_y",ws);loaded.addProperty("ocean_floor_y",floor);
            loaded.addProperty("motion_blocking_y",chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,x&15,z&15)+1);
            loaded.addProperty("top_solid_y",floor-1);loaded.addProperty("top_solid_definition","OCEAN_FLOOR_BLOCKS_MOTION;MAY_INCLUDE_TREES_OR_BUILDS");
            loaded.add("top_world_surface_block",block(chunk.getBlockState(new BlockPos(x,ws-1,z)),x,ws-1,z));
            loaded.add("top_solid_block",block(chunk.getBlockState(new BlockPos(x,floor-1,z)),x,floor-1,z));
            loaded.add("biome",biome(chunk.getNoiseBiome(x>>2,(floor-1)>>2,z>>2),new BlockPos(x,floor-1,z),"GENERATED_CHUNK_DATA_QUART"));
            loaded.add("subsurface",subsurface(y->chunk.getBlockState(new BlockPos(x,y,z)),x,z,floor,depth));
            loaded.addProperty("structure_context","CHUNK_REFERENCES_ONLY;NOT_A_PROOF_OF_CAUSATION");
            loaded.add("structure_starts",TerrainDiagnosticIO.GSON.toJsonTree(chunk.getAllStarts().entrySet().stream()
                    .filter(e->e.getValue().isValid()).map(e->level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                            .getKey(e.getKey()).toString()).sorted().toList()));
            loaded.addProperty("structure_reference_count",chunk.getAllReferences().values().stream().mapToInt(Set::size).sum());
        }
        j.add("loaded",loaded);
        var sample=noise.sample(x,z,TerrainSource.NOISE_PRE_DECORATION);JsonObject estimated=new JsonObject();
        estimated.addProperty("source",NOISE);estimated.addProperty("validity",sample.validity().name());
        if(sample.surfaceY().isPresent()) {
            int ws=sample.surfaceY().getAsInt();estimated.addProperty("world_surface_wg_y",ws);
            estimated.addProperty("motion_blocking_y","NOT_AVAILABLE_FROM_BASE_COLUMN_API");
            estimated.addProperty("ocean_floor_wg_y",level.getChunkSource().getGenerator().getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState()));
            estimated.addProperty("support_surface_y",sample.topSolidSurfaceY().isPresent()?sample.topSolidSurfaceY().getAsInt():null);
            var column=level.getChunkSource().getGenerator().getBaseColumn(x,z,level,level.getChunkSource().randomState());extraNoiseColumns++;
            estimated.add("top_block",block(column.getBlock(ws-1),x,ws-1,z));
            if(sample.topSolidSurfaceY().isPresent())estimated.add("subsurface",subsurface(column::getBlock,x,z,sample.topSolidSurfaceY().getAsInt(),depth));
            estimated.add("biome",biome(level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(x>>2,(ws-1)>>2,z>>2,
                    level.getChunkSource().randomState().sampler()),new BlockPos(x,ws-1,z),NOISE));
        }
        j.add("noise",estimated);return j;
    }
    JsonObject macro(int x,int z) {
        JsonObject j=TerrainDiagnosticIO.GSON.toJsonTree(MacroGeography.forSeed(level.getSeed()).sample(x,z)).getAsJsonObject();
        j.addProperty("source","MACRO_ESTIMATE");j.addProperty("coast_distance_semantics","SIGNED_ELLIPSE_FIELD;NOT_SURVEYED_SHORE_DISTANCE");
        j.addProperty("island_field","NOT_EXPOSED");j.addProperty("raw_land_mask","NOT_EXPOSED");return j;
    }
    private JsonObject biome(Holder<Biome> holder,BlockPos pos,String source) {
        var b=holder.value();JsonObject j=new JsonObject();
        j.addProperty("source",source);j.addProperty("key",holder.unwrapKey().map(k->k.location().toString()).orElse("UNREGISTERED"));
        j.addProperty("base_temperature",b.getBaseTemperature());j.addProperty("precipitation",b.getPrecipitationAt(pos).name());
        j.addProperty("downfall","NOT_EXPOSED_BY_PUBLIC_BIOME_GETTER");
        JsonArray carvers=new JsonArray();
        for(var stage:GenerationStep.Carving.values())for(var c:b.getGenerationSettings().getCarvers(stage)) {
            JsonObject v=new JsonObject();v.addProperty("stage",stage.name());v.addProperty("id",c.unwrapKey().map(k->k.location().toString()).orElse("INLINE"));carvers.add(v);
        }
        j.add("configured_carvers",carvers);j.addProperty("carver_context","BIOME_ELIGIBILITY_ONLY;NO_EXECUTION_OR_ORIGIN_PROOF");return j;
    }
    private JsonObject block(BlockState s,int x,int y,int z) {
        blockChecks++;JsonObject j=new JsonObject();j.addProperty("y",y);j.addProperty("block",BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        j.addProperty("state",s.toString());j.addProperty("air",s.isAir());
        j.addProperty("fluid",s.getFluidState().isEmpty()?"none":BuiltInRegistries.FLUID.getKey(s.getFluidState().getType()).toString());
        // EmptyBlockGetter deliberately prevents neighbor reads/chunk loads. Context-sensitive shapes are approximate.
        var shape=s.getCollisionShape(EmptyBlockGetter.INSTANCE,new BlockPos(x,y,z));
        j.addProperty("collision_empty",shape.isEmpty());j.addProperty("full_support",s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,new BlockPos(x,y,z)));
        j.addProperty("collision_context","ISOLATED_BLOCK_APPROXIMATION");return j;
    }
    private JsonObject subsurface(IntFunction<BlockState> read,int x,int z,int surfaceAbove,int depth) {
        JsonObject j=new JsonObject();JsonArray rows=new JsonArray(),runs=new JsonArray();int firstAir=-1,firstFluid=-1,run=-1;
        int actual=Math.min(depth,surfaceAbove-1-level.getMinBuildHeight());
        for(int d=0;d<=actual;d++) {
            int y=surfaceAbove-1-d;var state=read.apply(y);JsonObject row=block(state,x,y,z);row.addProperty("depth_from_surface_block",d);rows.add(row);
            if(state.isAir()){if(firstAir<0)firstAir=d;if(run<0)run=d;}
            else if(run>=0){runs.add(TerrainDiagnosticIO.GSON.toJsonTree(Map.of("start_depth",run,"end_depth",d-1)));run=-1;}
            if(!state.getFluidState().isEmpty()&&firstFluid<0)firstFluid=d;
        }
        if(run>=0)runs.add(TerrainDiagnosticIO.GSON.toJsonTree(Map.of("start_depth",run,"end_depth",actual)));
        j.addProperty("surface_above_y",surfaceAbove);j.addProperty("surface_block_y",surfaceAbove-1);j.addProperty("scanned_depth",actual);
        j.add("first_void_depth",firstAir<0?JsonNull.INSTANCE:new JsonPrimitive(firstAir));
        j.add("first_fluid_depth",firstFluid<0?JsonNull.INSTANCE:new JsonPrimitive(firstFluid));j.add("air_runs",runs);j.add("layers",rows);
        j.addProperty("origin","NOT_DETERMINABLE_POST_GENERATION");
        j.addProperty("shape_classification",firstAir<0?"NO_AIR_IN_SCAN":firstAir<=1?"UNSUPPORTED_COLUMN_LIKE":"CAVE_LIKE_VOID;LATERAL_OPENNESS_UNKNOWN");
        j.addProperty("surface_opening","NOT_DETERMINABLE_FROM_ONE_VERTICAL_COLUMN");
        for(int n:new int[]{2,4,6,8,12}) {
            boolean safe=actual>=n;for(int d=0;d<=Math.min(actual,n);d++) {
                JsonObject row=rows.get(d).getAsJsonObject();safe&=!row.get("air").getAsBoolean()&&row.get("fluid").getAsString().equals("none");
            }
            j.add("stable_"+n,actual<n?JsonNull.INSTANCE:new JsonPrimitive(safe));
        }
        return j;
    }
    static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
}
