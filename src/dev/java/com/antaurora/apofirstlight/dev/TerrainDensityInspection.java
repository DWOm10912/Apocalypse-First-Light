package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.geography.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.*;
import java.util.*;

/** Samples only the existing seed-bound RandomState router. Never constructs/installs another generator or router. */
final class TerrainDensityInspection {
    private TerrainDensityInspection() {}
    static JsonObject sample(ServerLevel level,int x,int z,int surfaceY) {
        JsonObject out=new JsonObject();var r=level.getChunkSource().randomState().router();
        Map<String,DensityFunction> nodes=new LinkedHashMap<>();
        nodes.put("router.continents",r.continents());nodes.put("router.erosion",r.erosion());nodes.put("router.ridges",r.ridges());
        nodes.put("router.temperature",r.temperature());nodes.put("router.vegetation",r.vegetation());nodes.put("router.depth",r.depth());
        nodes.put("router.initial_density_without_jaggedness",r.initialDensityWithoutJaggedness());nodes.put("router.final_density",r.finalDensity());
        Set<String> noises=new TreeSet<>(),splines=new HashSet<>();int[] visited={0};
        try {
            r.mapAll(new DensityFunction.Visitor() {
                @Override public DensityFunction apply(DensityFunction f) {
                    if(++visited[0]>30000)throw new IllegalStateException("GRAPH_VISIT_LIMIT");
                    if(f instanceof LandTerrainBias b) {
                        String name="afl.land_bias."+(b.continentalness()?"C":"E");nodes.putIfAbsent(name,b);
                        nodes.putIfAbsent(name+".input_continents",b.continents());nodes.putIfAbsent(name+".input_erosion",b.erosion());
                    } else if(f instanceof InlandElevationBias)nodes.putIfAbsent("afl.inland_offset",f);
                    else if(f instanceof MacroTerrainDensity m) {
                        String name="afl.macro_"+(m.initial()?"initial":"final");nodes.putIfAbsent(name,m);
                        nodes.putIfAbsent(name+".land",m.land());nodes.putIfAbsent(name+".terrain",m.terrain());
                        nodes.putIfAbsent(name+".underground_noise_cave_graph",m.underground());
                    } else if(f instanceof DensityFunctions.Spline&&splines.size()<8) {
                        String signature=f.minValue()+":"+f.maxValue();
                        if(splines.add(signature))nodes.put("spline_"+splines.size()+"_bounds_"+signature,f);
                    } else if(f.getClass().getSimpleName().equals("BlendedNoise"))nodes.putIfAbsent("base_3d_blended_noise",f);
                    return f;
                }
                @Override public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder holder) {
                    holder.noiseData().unwrapKey().ifPresent(k->{if(noises.size()<128)noises.add(k.location().toString());});return holder;
                }
            });
            out.addProperty("graph_traversal","COMPLETE");
        }catch(RuntimeException e){out.addProperty("graph_traversal","PARTIAL:"+e.getMessage());}
        out.addProperty("source","OBSERVED_SAMPLE_OF_SEED_BOUND_NODES");out.addProperty("visited_nodes",visited[0]);
        out.addProperty("interpretation","Point samples, NOT additive height contributions; NOT interpolated NoiseChunk output, aquifer or carver execution");
        out.addProperty("spline_labels","Distinct output-bound pairs; not registry names or proof of node uniqueness");
        out.add("referenced_noise_ids",TerrainDiagnosticIO.GSON.toJsonTree(noises));JsonArray samples=new JsonArray();
        for(int y:new int[]{surfaceY-1,surfaceY-5,surfaceY-9,surfaceY-13}) {
            if(y<level.getMinBuildHeight()||y>=level.getMaxBuildHeight())continue;
            var context=new DensityFunction.SinglePointContext(x,y,z);JsonObject row=new JsonObject();row.addProperty("y",y);
            for(var entry:nodes.entrySet()) {
                JsonObject n=new JsonObject();n.addProperty("node_class",entry.getValue().getClass().getName());
                try{double value=entry.getValue().compute(context);if(Double.isFinite(value))n.addProperty("value",value);else n.addProperty("status","NON_FINITE");}
                catch(RuntimeException e){n.addProperty("status","UNAVAILABLE:"+e.getClass().getSimpleName());}row.add(entry.getKey(),n);
            }samples.add(row);
        }
        out.add("samples",samples);out.addProperty("void_origin","NOT_DETERMINABLE_POST_GENERATION");return out;
    }
}
