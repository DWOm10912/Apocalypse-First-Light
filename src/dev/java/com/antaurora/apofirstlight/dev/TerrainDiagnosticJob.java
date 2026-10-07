package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph;
import com.antaurora.apofirstlight.worldgen.roads.construction.RoadConstructionConfig;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Invocation-owned bounded state, advanced on the server thread. No tickets, block writes or saved-data mutation. */
final class TerrainDiagnosticJob {
    record Point(int x,int z) {long key(){return TerrainColumnInspection.key(x,z);}}
    record Corridor(String id,List<Point> points) {}
    final UUID owner;
    final ServerLevel level;
    final String mode;
    final int x,z,radius,depth;
    final long started;
    final TerrainColumnInspection reader;
    final RoadConstructionConfig config;
    final List<Point> centers=new ArrayList<>(), probes=new ArrayList<>(),details=new ArrayList<>();
    final List<Corridor> corridors=new ArrayList<>();
    final Map<Long,TerrainColumnInspection.Heights> heights=new HashMap<>();
    final Map<Long,JsonObject> observations=new LinkedHashMap<>();
    final JsonObject root;
    final TerrainRoadBenchmark roads;
    int probeIndex,detailIndex,phase;
    long maxAdvanceNanos;
    private boolean done;
    TerrainDiagnosticJob(ServerPlayer player,String mode,int x,int z,int radius,int depth,RoadConstructionConfig config) {
        this.owner=player.getUUID();this.level=player.serverLevel();this.mode=mode;this.x=x;this.z=z;
        this.radius=radius;this.depth=depth;this.config=config;started=level.getGameTime();reader=new TerrainColumnInspection(level);
        root=TerrainDiagnosticIO.identity(level,x,z);
        root.addProperty("mode",mode);root.addProperty("sample_radius",radius);root.addProperty("sample_spacing",radius==0?1:radius/4);
        root.addProperty("sample_footprint","SQUARE_HALF_EXTENT_RADIUS;AXIAL_PROBES_EXTEND_16_BEYOND_CENTERS");
        boolean region=mode.equals("survey")||mode.equals("benchmark");
        if(region)for(int ix=-4;ix<=4;ix++)for(int iz=-4;iz<=4;iz++)centers.add(new Point(x+ix*(radius/4),z+iz*(radius/4)));
        else if(!mode.equals("samples"))centers.add(new Point(x,z));
        LinkedHashSet<Point> unique=new LinkedHashSet<>();
        for(Point p:centers) {
            unique.add(p);
            if(region)for(int d:new int[]{-16,-8,-4,-1,1,4,8,16}){unique.add(new Point(p.x+d,p.z));unique.add(new Point(p.x,p.z+d));}
            else for(int d=-16;d<=16;d++){unique.add(new Point(p.x+d,p.z));unique.add(new Point(p.x,p.z+d));}
        }
        details.addAll(centers);
        roads=mode.equals("benchmark")||mode.equals("samples")?new TerrainRoadBenchmark(level,x,z,radius,player.blockPosition(),config,mode.equals("samples")):null;
        probes.addAll(unique);
    }
    boolean done(){return done;}
    String progress(){return mode+" columns="+probeIndex+"/"+probes.size()+" details="+detailIndex+"/"+details.size()
            +(roads==null?"":" "+roads.progress());}
    void advance() {
        long begin=System.nanoTime();
        try {
            if(phase==0) {
                root.add("runtime_fingerprint",TerrainDiagnosticIO.fingerprint(level));
                if(mode.equals("benchmark"))addHighways();phase=1;return;
            }
            if(probeIndex<probes.size()) {
                for(int n=0;n<2&&probeIndex<probes.size();n++) {
                    Point p=probes.get(probeIndex++);heights.put(p.key(),reader.heights(p.x,p.z));
                    if(System.nanoTime()-begin>=4_000_000)break;
                }return;
            }
            if(detailIndex<details.size()) {
                Point p=details.get(detailIndex++);observations.put(p.key(),reader.inspect(p.x,p.z,depth));return;
            }
            if(phase==1) {
                if(radius==0&&!mode.equals("samples")) {
                    JsonObject point=observations.get(TerrainColumnInspection.key(x,z));root.add("inspection",point);
                    var h=heights.get(TerrainColumnInspection.key(x,z));Integer y=h.loaded()!=null?h.loaded():h.noise();
                    if(y!=null)root.add("density",TerrainDensityInspection.sample(level,x,z,y));
                }phase=2;return;
            }
            if(roads!=null&&!roads.done()){roads.advance();return;}
            done=true;
        }finally{maxAdvanceNanos=Math.max(maxAdvanceNanos,System.nanoTime()-begin);}
    }
    private void addHighways() {
        var graph=HighwayRouteGraph.forSeed(level.getSeed());
        var nearest=graph.edges().stream().filter(e->e.orientation()!=HighwayRouteGraph.Orientation.POLYLINE)
                .sorted(Comparator.comparingDouble((HighwayRouteGraph.Edge e)->e.distanceTo(x,z)).thenComparing(HighwayRouteGraph.Edge::id)).limit(2).toList();
        Set<Point> unique=new LinkedHashSet<>(probes),detailSet=new LinkedHashSet<>(details);
        for(var edge:nearest) {
            int center=(int)edge.clampStation(edge.globalStation(x,z)),span=Math.min(512,radius);
            int from=Math.max(edge.startStation(),center-span),to=Math.min(edge.endStation(),center+span);List<Point> points=new ArrayList<>();
            for(int station=from;station<=to;station+=32) {
                Point p=edge.orientation()==HighwayRouteGraph.Orientation.NORTH_SOUTH?new Point(edge.fixedCoordinate(),station):new Point(station,edge.fixedCoordinate());
                points.add(p);unique.add(p);detailSet.add(p);
            }
            corridors.add(new Corridor(edge.id(),List.copyOf(points)));
        }
        probes.clear();probes.addAll(unique);details.clear();details.addAll(detailSet);
        if(probes.size()>2048)throw new IllegalStateException("COLUMN_BUDGET_EXCEEDED");
    }
    JsonObject finish(String status) {
        root.addProperty("status",status);root.addProperty("sample_count",centers.size());
        root.addProperty("completed_center_samples",centers.stream().filter(p->observations.containsKey(p.key())).count());
        root.addProperty("completed_at_game_tick",level.getGameTime());
        JsonObject datasets=new JsonObject();
        for(String source:List.of(TerrainColumnInspection.LOADED,TerrainColumnInspection.NOISE))datasets.add(source,summarize(source));
        root.add("datasets",datasets);
        if(radius==0&&!mode.equals("samples")) {
            JsonObject profiles=new JsonObject();for(String source:List.of(TerrainColumnInspection.LOADED,TerrainColumnInspection.NOISE)) {
                JsonObject axes=new JsonObject();for(boolean axisX:new boolean[]{true,false}) {
                    List<Integer> values=new ArrayList<>();for(int d=-16;d<=16;d++)values.add(height(new Point(x+(axisX?d:0),z+(axisX?0:d)),source));
                    axes.add(axisX?"x":"z",TerrainDiagnosticMetrics.profile(values,1));
                }profiles.add(source,axes);
            }root.add("local_profiles",profiles);
        }
        root.add("road_buildability",roads==null?new JsonPrimitive("NOT_REQUESTED"):roads.report());
        root.add("known_regression_samples",roads==null?new JsonPrimitive("RUN /afl terrain benchmark samples"):
                TerrainDiagnosticIO.GSON.toJsonTree(roads.results.stream().filter(r->r.has("fixture")&&r.get("fixture").getAsBoolean()).toList()));
        root.add("highway",highway());
        root.add("center_samples",TerrainDiagnosticIO.GSON.toJsonTree(centers.stream().map(p->observations.get(p.key())).filter(Objects::nonNull).toList()));
        Set<Long> chunks=new HashSet<>(reader.chunks);if(roads!=null)chunks.addAll(roads.chunks);
        JsonObject d=new JsonObject();d.addProperty("sampled_columns",probeIndex);d.addProperty("noise_query_columns",reader.noise.sampledColumns());
        d.addProperty("extra_noise_detail_columns",reader.extraNoiseColumns);d.addProperty("failed_noise_queries",reader.noise.failedColumns());
        d.addProperty("loaded_chunks_used",chunks.size());d.addProperty("forced_chunk_loads",0);
        d.addProperty("duration_ticks",level.getGameTime()-started);d.addProperty("max_work_unit_ms",maxAdvanceNanos/1_000_000.0);
        d.addProperty("road_successfully_sampled_columns",roads==null?0:roads.columns);d.addProperty("block_checks",reader.blockChecks+(roads==null?0:roads.checks));
        d.addProperty("budget","2 probe columns OR 1 detailed column OR 16 road columns per tick; 4ms soft probe deadline; 12000 tick timeout");
        d.addProperty("snapshot_consistency","MULTI_TICK_OBSERVATION;KEEP_WORLD_QUIET;NOT_AN_ATOMIC_WORLD_SNAPSHOT");
        d.addProperty("subsurface_reference","Surface block depth 0; scan inclusive through requested depth; NOISE support finder differs from loaded OCEAN_FLOOR heightmap");
        d.addProperty("road_scan_below_G",config.maxCutDepth()+config.supportDepth()+1);d.add("road_policy",TerrainDiagnosticIO.GSON.toJsonTree(config));
        root.add("diagnostics",d);return root;
    }
    private Integer height(Point p,String source) {
        var h=heights.get(p.key());return h==null?null:source.equals(TerrainColumnInspection.LOADED)?h.loaded():h.noise();
    }
    private JsonObject observation(Point p,String source) {
        var row=observations.get(p.key());return row==null?null:row.getAsJsonObject(source.equals(TerrainColumnInspection.LOADED)?"loaded":"noise");
    }
    private JsonObject summarize(String source) {
        JsonObject j=new JsonObject();List<Integer> elevation=new ArrayList<>(),voids=new ArrayList<>();
        Map<String,Integer> biomes=new TreeMap<>(),landWater=new TreeMap<>(),slopes=new TreeMap<>();
        biomes.put("apocalypse_firstlight:fallout_barrens",0);biomes.put("minecraft:plains",0);
        List<Double> grade=new ArrayList<>();Map<Integer,List<Integer>> relief=new TreeMap<>();
        for(int s:new int[]{8,16,32})relief.put(s,new ArrayList<>());
        int[] stable=new int[5],eligible=new int[5];int depthSamples=0;
        for(Point p:centers) {
            Integer h=height(p,source);if(h==null)continue;elevation.add(h);
            var obs=observation(p,source);if(obs!=null&&obs.has("biome")) {
                biomes.merge(obs.getAsJsonObject("biome").get("key").getAsString(),1,Integer::sum);
                var macro=observations.get(p.key()).getAsJsonObject("macro");landWater.merge(macro.get("surfaceClass").getAsString()+":"+macro.get("waterClass").getAsString(),1,Integer::sum);
            }
            double maxGrade=0;int pairs=0;
            for(Point q:List.of(new Point(p.x-1,p.z),new Point(p.x+1,p.z),new Point(p.x,p.z-1),new Point(p.x,p.z+1))) {
                Integer hy=height(q,source);if(hy!=null){double g=TerrainDiagnosticMetrics.grade(h,hy,1);grade.add(g);maxGrade=Math.max(maxGrade,g);pairs++;}
            }
            if(pairs==4)slopes.merge(TerrainDiagnosticMetrics.slopeClass(maxGrade),1,Integer::sum);
            for(int span:relief.keySet()) {
                int d=span/2;List<Integer> hs=new ArrayList<>();hs.add(h);
                for(Point q:List.of(new Point(p.x-d,p.z),new Point(p.x+d,p.z),new Point(p.x,p.z-d),new Point(p.x,p.z+d))) {
                    Integer hy=height(q,source);if(hy!=null)hs.add(hy);
                }if(hs.size()==5)relief.get(span).add(Collections.max(hs)-Collections.min(hs));
            }
            if(obs!=null&&obs.has("subsurface")) {
                JsonObject sub=obs.getAsJsonObject("subsurface");depthSamples++;
                if(!sub.get("first_void_depth").isJsonNull())voids.add(sub.get("first_void_depth").getAsInt());
                int i=0;for(int d:new int[]{2,4,6,8,12}){if(!sub.get("stable_"+d).isJsonNull()){eligible[i]++;if(sub.get("stable_"+d).getAsBoolean())stable[i]++;}i++;}
            }
        }
        j.add("elevation",TerrainDiagnosticMetrics.stats(elevation));j.add("adjacent_1_block_delta",TerrainDiagnosticMetrics.stats(grade));
        JsonObject rough=new JsonObject();relief.forEach((span,vals)->rough.add("axial_"+span+"_block_relief",TerrainDiagnosticMetrics.stats(vals)));j.add("roughness",rough);
        JsonObject slope=TerrainDiagnosticMetrics.distribution(slopes,slopes.values().stream().mapToInt(Integer::intValue).sum());
        slope.addProperty("thresholds","PHASE0_DIAGNOSTIC: max axial 1-block grade <=1/16 flat, <=1/8 gentle, <=1/4 moderate, else steep");j.add("slope",slope);
        JsonObject sub=new JsonObject();sub.addProperty("sample_count",depthSamples);int i=0;
        for(int d:new int[]{2,4,6,8,12}){JsonObject row=new JsonObject();row.addProperty("denominator",eligible[i]);row.addProperty("count",stable[i]);
            if(eligible[i]>0)row.addProperty("percent",stable[i]*100.0/eligible[i]);sub.add("stable_"+d,row);i++;}
        sub.add("first_void_distribution",TerrainDiagnosticMetrics.stats(voids));sub.addProperty("columns_with_no_observed_air",depthSamples-voids.size());j.add("subsurface",sub);
        j.add("biomes",TerrainDiagnosticMetrics.distribution(biomes,biomes.values().stream().mapToInt(Integer::intValue).sum()));
        j.add("land_water",TerrainDiagnosticMetrics.distribution(landWater,landWater.values().stream().mapToInt(Integer::intValue).sum()));return j;
    }
    private JsonElement highway() {
        if(!mode.equals("benchmark"))return new JsonPrimitive("NOT_REQUESTED");JsonObject j=new JsonObject();JsonArray array=new JsonArray();
        j.addProperty("scope","TWO_NEAREST_EXISTING_STRAIGHT_ROUTE_EDGES;NATURAL_TERRAIN_NOT_ENGINEERED_DECK;CENTERLINE_ONLY");
        for(Corridor c:corridors) {
            JsonObject row=new JsonObject();row.addProperty("edge_id",c.id);row.add("points",TerrainDiagnosticIO.GSON.toJsonTree(c.points));
            for(String source:List.of(TerrainColumnInspection.LOADED,TerrainColumnInspection.NOISE)) {
                List<Integer> ys=c.points.stream().map(p->height(p,source)).toList();JsonObject values=TerrainDiagnosticMetrics.profile(ys,32);
                List<Double> grades=new ArrayList<>();int rise=0,fall=0,water=0,known=0,stable=0;
                for(int i=0;i<c.points.size();i++) {
                    if(i>0&&ys.get(i)!=null&&ys.get(i-1)!=null){int delta=ys.get(i)-ys.get(i-1);rise+=Math.max(0,delta);fall+=Math.max(0,-delta);grades.add(TerrainDiagnosticMetrics.grade(ys.get(i-1),ys.get(i),32));}
                    var obs=observation(c.points.get(i),source);if(obs!=null&&obs.has("subsurface")) {
                        var sub=obs.getAsJsonObject("subsurface");known++;if(!sub.get("first_fluid_depth").isJsonNull())water++;
                        if(!sub.get("stable_6").isJsonNull()&&sub.get("stable_6").getAsBoolean())stable++;
                        String top=source.equals(TerrainColumnInspection.LOADED)?"top_world_surface_block":"top_block";
                        if(sub.get("first_fluid_depth").isJsonNull()&&obs.has(top)&&!obs.getAsJsonObject(top).get("fluid").getAsString().equals("none"))water++;
                    }
                }
                values.add("grade",TerrainDiagnosticMetrics.stats(grades));values.addProperty("cumulative_ascent",rise);values.addProperty("cumulative_descent",fall);
                values.addProperty("support_samples",known);values.addProperty("stable_6_samples",stable);values.addProperty("water_or_shallow_fluid_samples",water);row.add(source,values);
            }array.add(row);
        }j.add("corridors",array);return j;
    }
}
