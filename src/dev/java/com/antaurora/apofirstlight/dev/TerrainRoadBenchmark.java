package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.geography.MacroGeography;
import com.antaurora.apofirstlight.worldgen.roads.RoadType;
import com.antaurora.apofirstlight.worldgen.roads.construction.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Calls the real planner once per candidate, discards edit snapshots, never touches the construction ledger. */
final class TerrainRoadBenchmark {
    private final ServerLevel level;
    private final RoadConstructionConfig config;
    private final List<RoadSegmentPreset.Request> requests;
    private final int regionalCount;
    final List<JsonObject> results=new ArrayList<>();
    final Set<Long> chunks=new HashSet<>();
    int index,columns;long checks;
    private RoadConstructionPlanner.Job job;
    private JsonObject current;
    private final BlockPos playerAtStart;
    TerrainRoadBenchmark(ServerLevel level,int x,int z,int radius,BlockPos player,RoadConstructionConfig config,boolean fixturesOnly) {
        this.level=level;this.config=config;playerAtStart=player.immutable();
        requests=new ArrayList<>(fixturesOnly?List.of():candidates(x,z,radius));regionalCount=requests.size();
        requests.add(new RoadSegmentPreset.Request(RoadType.R12,32,Direction.NORTH,331,-762));
        requests.add(new RoadSegmentPreset.Request(RoadType.R12,32,Direction.NORTH,322,-812));
    }
    static List<RoadSegmentPreset.Request> candidates(int x,int z,int radius) {
        List<RoadSegmentPreset.Request> list=new ArrayList<>();
        int[][] offsets={{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
        for(int ring:new int[]{radius/2,radius})for(int[] offset:offsets) {
            int step=offset[0]!=0&&offset[1]!=0?(int)Math.floor(ring/Math.sqrt(2)):ring;
            int cx=x+offset[0]*step,cz=z+offset[1]*step;
            for(Direction d:List.of(Direction.NORTH,Direction.EAST))list.add(new RoadSegmentPreset.Request(RoadType.R12,32,d,cx-d.getStepX()*16,cz-d.getStepZ()*16));
        }
        return List.copyOf(list);
    }
    boolean done(){return index>=requests.size();}
    String progress(){return "ROAD "+index+"/"+requests.size()+(job==null?"":" "+job.phase()+" "+job.processedColumns()+"/"+job.totalColumns());}
    void advance() {
        if(done())return;
        try {
            if(current==null) {
                var r=requests.get(index);current=new JsonObject();current.add("request",TerrainDiagnosticIO.GSON.toJsonTree(r));
                current.addProperty("sample_id",index<regionalCount?"regional_"+index:index==regionalCount?"ROAD_PREFLIGHT_CONSISTENCY_SAMPLE":"SUBSURFACE_VOID_SAMPLE");
                current.addProperty("fixture",index>=regionalCount);
                if(index>=regionalCount) {
                    current.addProperty("fixture_world_seed","UNKNOWN;USER_MUST_REUSE_ORIGINAL_WORLD");
                    current.addProperty("historical_observation",index==regionalCount?
                            "Survey SUITABLE_VERIFIED; Preview failed; earlier STEP_OUTSIDE_SEGMENT_AND_SHOULDERS":
                            "REJECTED:UNSAFE_SUBSURFACE_VOID");
                    if(index>regionalCount)current.add("historical_bounds",TerrainDiagnosticIO.GSON.toJsonTree(Map.of(
                            "minX",308,"minZ",-847,"maxXExclusive",336,"maxZExclusive",-809)));
                }
                current.addProperty("macro_land",MacroGeography.forSeed(level.getSeed()).sample(r.x(),r.z()).isLand());
                current.add("player_gate_at_benchmark_start",playerGate(r,playerAtStart));
                current.addProperty("preview_command",r.command("preview"));
                var layout=RoadSegmentPreset.plan(level,r);
                job=RoadConstructionPlanner.begin(level,layout,config,RoadConstructionProtection.query(level,layout.candidateBounds()),true);
                return;
            }
            job.advance(Math.min(16,config.columnsPerTick()));
            if(!job.done())return;
            var plan=job.result();current.addProperty("status",plan.status().name());
            current.addProperty("validation_level",plan.status()==RoadConstructionPlan.Status.PREVIEW_READY?"ROAD_PREFLIGHT_VERIFIED":"REJECTED_OR_INCOMPLETE");
            current.add("issues",TerrainDiagnosticIO.GSON.toJsonTree(plan.issues()));current.add("failure_details",TerrainDiagnosticIO.GSON.toJsonTree(job.failureDetails()));
            current.add("bounds",TerrainDiagnosticIO.GSON.toJsonTree(plan.bounds()));current.add("summary",TerrainDiagnosticIO.GSON.toJsonTree(plan.summary()));
            current.add("profiles",TerrainDiagnosticIO.GSON.toJsonTree(plan.profiles()));
            columns+=job.processedColumns();checks+=job.blockChecks();chunks.addAll(job.loadedChunksUsed());end();
        }catch(RuntimeException e) {
            if(current==null)current=new JsonObject();current.addProperty("status","UNKNOWN");current.addProperty("error",e.toString());
            if(job!=null){columns+=job.processedColumns();checks+=job.blockChecks();chunks.addAll(job.loadedChunksUsed());}end();
        }
    }
    private void end(){results.add(current);current=null;job=null;index++;}
    JsonObject report() {
        JsonObject j=new JsonObject();j.addProperty("selection","DETERMINISTIC_TWO_RINGS;NOT_RANDOM_NATIONAL_SAMPLE");
        j.addProperty("requested_regional_candidates",regionalCount);j.addProperty("completed_total_including_fixtures",results.size());
        j.addProperty("complete",done());j.add("engineering_policy",TerrainDiagnosticIO.GSON.toJsonTree(config));
        Map<String,Integer> counts=new TreeMap<>(),reasons=new TreeMap<>();int land=0,pass=0,rejected=0,unknown=0;
        for(int i=0;i<Math.min(regionalCount,results.size());i++) {
            var row=results.get(i);String status=row.get("status").getAsString();counts.merge(status,1,Integer::sum);
            if(row.has("macro_land")&&row.get("macro_land").getAsBoolean()) {
                land++;if(status.equals("PREVIEW_READY"))pass++;else if(status.equals("REJECTED"))rejected++;else unknown++;
            }
            if(!status.equals("PREVIEW_READY")&&row.has("issues"))for(var issue:row.getAsJsonArray("issues"))reasons.merge(issue.getAsString().split(":",2)[0],1,Integer::sum);
        }
        j.add("all_regional_statuses",TerrainDiagnosticMetrics.distribution(counts,Math.min(regionalCount,results.size())));
        j.addProperty("land_candidates_completed",land);j.addProperty("land_full_preflight_pass",pass);j.addProperty("land_rejected",rejected);j.addProperty("land_unknown",unknown);
        if(land>0)j.addProperty("land_pass_percent_including_unknown",100.0*pass/land);
        if(pass+rejected>0)j.addProperty("land_pass_percent_excluding_unknown",100.0*pass/(pass+rejected));
        j.add("rejection_reasons",TerrainDiagnosticIO.GSON.toJsonTree(reasons));j.add("candidates",TerrainDiagnosticIO.GSON.toJsonTree(results));
        j.addProperty("player_gate","Reported separately, not a terrain rejection or authorization to prepare/build");return j;
    }
    static JsonObject playerGate(RoadSegmentPreset.Request r,BlockPos p) {
        var area=r.corridor().expand(4);JsonObject j=new JsonObject();
        j.addProperty("status",area.expand(2).contains(p.getX(),p.getZ())?"STEP_OUTSIDE_SEGMENT_AND_SHOULDERS":
                !area.expand(32).contains(p.getX(),p.getZ())?"MOVE_NEAR_REQUESTED_SITE":"POSITION_GATE_PASSED_ONLY");
        j.addProperty("offending_x",p.getX());j.addProperty("offending_z",p.getZ());
        int dx=r.direction().getStepX(),dz=r.direction().getStepZ();
        j.addProperty("road_station",(p.getX()-r.x())*dx+(p.getZ()-r.z())*dz);
        j.addProperty("cross_offset",-(p.getX()-r.x())*dz+(p.getZ()-r.z())*dx);
        j.add("segment_corridor",TerrainDiagnosticIO.GSON.toJsonTree(r.corridor()));
        j.add("candidate_shoulder_envelope",TerrainDiagnosticIO.GSON.toJsonTree(area));
        j.add("player_exclusion_bounds",TerrainDiagnosticIO.GSON.toJsonTree(area.expand(2)));
        j.addProperty("note","Existing command guard envelope; actual rasterized shoulders are in preflight result bounds");return j;
    }
}
