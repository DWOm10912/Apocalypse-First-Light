package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.roads.*;
import com.antaurora.apofirstlight.worldgen.roads.construction.*;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainSource;
import com.antaurora.apofirstlight.worldgen.terrain.TerrainValidity;
import com.antaurora.apofirstlight.worldgen.terrain.SurfaceType;
import com.google.gson.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only candidate search. No ledger registration, chunk tickets, or world edits. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class RoadSurveyCommand {
    private static final Map<ServerLevel,Survey> RUNNING=new WeakHashMap<>();
    private static final Map<ServerLevel,Long> LAST=new WeakHashMap<>();
    private static final int MAX_CANDIDATES=32, SAMPLES=15;
    private RoadSurveyCommand(){}
    static boolean busy(ServerLevel level){return RUNNING.containsKey(level);}
    static String progress(ServerLevel level){var s=RUNNING.get(level);return s==null?"NO_SURVEY":"SURVEY "+s.index+"/"+s.requests.size();}
    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        var survey=Commands.literal("survey").requires(RoadConstructionCommand::allowed).executes(c->start(c.getSource(),256));
        for(int radius:new int[]{128,256,512})survey.then(Commands.literal(Integer.toString(radius)).executes(c->start(c.getSource(),radius)));
        root.then(survey);
        root.then(Commands.literal("cancel_survey").requires(RoadConstructionCommand::allowed).executes(c->{
            var s=RUNNING.get(c.getSource().getLevel());
            if(s!=null&&s.owner.equals(c.getSource().getPlayerOrException().getUUID())) {
                RUNNING.remove(c.getSource().getLevel());c.getSource().sendSuccess(()->Component.literal("Survey已取消，没有施工或登记占地。"),false);return 1;
            }return 0;
        }));
    }
    private static int start(CommandSourceStack source,int radius) {
        try {
            if(!RoadConstructionCommand.allowed(source))throw new IllegalArgumentException("DEV_CREATIVE_OP_REQUIRED");
            var level=source.getLevel();
            if(busy(level)||RoadConstructionCommand.busy(level)||TerrainDiagnosticCommand.busy(level))throw new IllegalArgumentException("ROAD_DIAGNOSTIC_ALREADY_RUNNING");
            long now=level.getGameTime();
            if(now-LAST.getOrDefault(level,Long.MIN_VALUE/2)<200)throw new IllegalArgumentException("WAIT_10_SECONDS");
            var s=new Survey(source.getPlayerOrException(),radius,RoadConstructionCommand.config());
            RUNNING.put(level,s);LAST.put(level,now);
            source.sendSuccess(()->Component.literal("只读Survey开始：32个R12/32格候选；不会加载新区块、登记施工或修改方块。/afl roads status 查询。"),false);
            return 1;
        }catch(Exception e){source.sendFailure(Component.literal("Survey未开始："+e.getMessage()));return 0;}
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        Survey s=RUNNING.get(level);if(s==null)return;
        ServerPlayer player=level.getServer().getPlayerList().getPlayer(s.owner);
        if(player==null||player.serverLevel()!=level||!RoadConstructionCommand.allowed(player.createCommandSourceStack())) {
            RUNNING.remove(level);return;
        }
        try {
            if(level.getGameTime()>=s.expires) {s.timedOut=true;s.finish(level,player);RUNNING.remove(level);return;}
            try {s.advance(level);}
            catch(RuntimeException failure) {
                if(s.current==null)throw failure;
                s.current.add("terrain_rejections",strings("CANDIDATE_UNVERIFIED:"+failure.getClass().getSimpleName()+":"+failure.getMessage()));
                s.current.addProperty("validation_level","INCOMPLETE");s.end("UNKNOWN");
            }
            if(s.index>=s.requests.size()) {s.finish(level,player);RUNNING.remove(level);}
        }catch(Exception e){RUNNING.remove(level);player.sendSystemMessage(Component.literal("Survey停止，未授权施工："+e.getMessage()));}
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) {
        if(e.getLevel() instanceof ServerLevel level){RUNNING.remove(level);LAST.remove(level);}
    }
    private static final class Survey {
        final UUID owner;final int radius,originX,originZ;final long started,expires;
        final RoadConstructionConfig config;
        final RoadTerrainQuery noise;
        final List<RoadSegmentPreset.Request> requests=new ArrayList<>();
        final List<JsonObject> results=new ArrayList<>();
        int index,sampleIndex,totalSamples,totalActualColumns;
        boolean loaded,water,tree,voidRisk,nonSolid,timedOut;
        final List<Integer> heights=new ArrayList<>(),centerHeights=new ArrayList<>();
        JsonObject current;
        RoadPlan layout;
        RoadConstructionPlanner.Job preflight;
        Survey(ServerPlayer player,int radius,RoadConstructionConfig config) {
            this.owner=player.getUUID();this.radius=radius;this.config=config;
            noise=new RoadTerrainQuery(player.serverLevel(),MAX_CANDIDATES*SAMPLES);
            originX=player.blockPosition().getX();originZ=player.blockPosition().getZ();
            started=player.serverLevel().getGameTime();expires=started+12000;
            requests.addAll(TerrainRoadBenchmark.candidates(originX,originZ,radius));
            if(requests.size()>MAX_CANDIDATES)throw new IllegalStateException("SURVEY_CANDIDATE_BUDGET");
        }
        void advance(ServerLevel level) {
            if(current==null) {
                beginCandidate(level);
                return;
            }
            if(preflight!=null) {
                int before=preflight.processedColumns();
                preflight.advance(Math.min(32,config.columnsPerTick()));
                totalActualColumns+=preflight.processedColumns()-before;
                if(preflight.done()) {
                    var plan=preflight.result();
                    current.add("engineering_result",new Gson().toJsonTree(plan.summary()));
                    current.addProperty("expected_max_cut",plan.summary().maxCutDepth());
                    current.addProperty("expected_max_fill",plan.summary().maxFillHeight());
                    current.add("terrain_rejections",new Gson().toJsonTree(plan.issues()));
                    current.add("failure_details",new Gson().toJsonTree(preflight.failureDetails()));
                    int max=0,count=0;long sum=0;
                    for(var profile:plan.profiles())if(!profile.edgeId().startsWith("node:")) {
                        for(int i=1;i<profile.surfaceH16().size();i++) {
                            int delta=Math.abs(profile.surfaceH16().get(i)-profile.surfaceH16().get(i-1));
                            max=Math.max(max,delta);sum+=delta;count++;
                        }
                    }
                    current.addProperty("planned_max_grade",max/16.0);
                    current.addProperty("planned_mean_grade",count==0?0:sum/(16.0*count));
                    boolean ready=plan.status()==RoadConstructionPlan.Status.PREVIEW_READY;
                    current.addProperty("validation_level",ready?"ROAD_PREFLIGHT_VERIFIED":"ACTUAL_PREFLIGHT_INCOMPLETE_OR_REJECTED");
                    end(ready?"SUITABLE_VERIFIED":plan.status().name().equals("REJECTED")?"REJECTED":"UNKNOWN");
                }
                return;
            }
            // A deadline between units, not an unsafe interrupt of a generator/world call.
            long deadline=System.nanoTime()+4_000_000L;
            for(int work=0;work<2&&sampleIndex<SAMPLES;work++) {
                sample(level,requests.get(index),sampleIndex++);totalSamples++;
                if(System.nanoTime()>=deadline)break;
            }
            if(sampleIndex<SAMPLES)return;
            summarize();
            if(water||tree||voidRisk||nonSolid) {
                current.add("terrain_rejections",strings("WATER="+water,"TREE="+tree,"VOID="+voidRisk,"NON_SOLID="+nonSolid));
                end("REJECTED");return;
            }
            if(loaded) {
                var claims=RoadConstructionProtection.query(level,layout.candidateBounds());
                preflight=RoadConstructionPlanner.begin(level,layout,config,claims,true);
            } else {
                List<Integer> sorted=new ArrayList<>(heights);Collections.sort(sorted);int g=sorted.get(sorted.size()/2);
                int cut=sorted.get(sorted.size()-1)-g,fill=g-sorted.get(0);
                current.addProperty("expected_max_cut",cut);current.addProperty("expected_max_fill",fill);
                current.addProperty("planned_max_grade",0);
                current.addProperty("estimate_assumption","Sparse constant-G fit only; support, unsampled terrain, edits and engineering volume UNVERIFIED");
                current.addProperty("validation_level","SPARSE_BASE_NOISE_ONLY");
                if(cut>config.maxCutDepth()||fill>config.maxFillHeight()) {
                    current.add("terrain_rejections",strings("ESTIMATED_FLAT_CUT_FILL_EXCEEDS_BUDGET"));end("REJECTED");
                }else end("SUITABLE_ESTIMATED");
            }
        }
        void beginCandidate(ServerLevel level) {
            var request=requests.get(index);layout=RoadSegmentPreset.plan(level,request);
            current=new JsonObject();sampleIndex=0;preflight=null;heights.clear();centerHeights.clear();
            water=false;tree=false;voidRisk=false;nonSolid=false;
            current.addProperty("candidate_id",layout.candidateId());current.addProperty("road_type",request.type().name());
            current.addProperty("length",request.length());current.addProperty("direction",request.direction().getName());
            current.add("coordinates",new Gson().toJsonTree(Map.of("center_x",(request.x()+request.endX())/2,
                    "center_z",(request.z()+request.endZ())/2,"start_x",request.x(),"start_z",request.z())));
            current.add("bounds",new Gson().toJsonTree(layout.candidateBounds()));
            current.addProperty("construction_authorized",false);current.addProperty("prepare_required",true);
            current.addProperty("verified_scope","TERRAIN_PREFLIGHT_ONLY;NOT_PLAYER_POSITION_OR_PREPARE_AUTHORIZATION");
            var player=level.getServer().getPlayerList().getPlayer(owner);
            if(player!=null)current.add("player_position_gate",TerrainRoadBenchmark.playerGate(request,player.blockPosition()));
            current.addProperty("preview_command",request.command("preview"));current.addProperty("prepare_command",request.command("prepare"));
            current.add("terrain_rejections",new JsonArray());
            for(String field:List.of("sampled_min_height","sampled_max_height","sampled_relief",
                    "sampled_mean_ground_grade","sampled_max_ground_grade","expected_max_cut","expected_max_fill","planned_max_grade"))
                current.add(field,JsonNull.INSTANCE);
            current.addProperty("water_risk","UNVERIFIED");current.addProperty("tree_obstruction","UNVERIFIED");
            current.addProperty("cave_risk","UNVERIFIED");
            current.addProperty("validation_level","NOT_CHECKED");current.addProperty("terrain_source","NONE");
            current.addProperty("protection_status","KNOWN_CLAIMS_ONLY;PLAYER_NATURAL_BLOCK_PROVENANCE_UNVERIFIED;LEDGER_RECHECK_AT_PREPARE");
            try {
                var area=layout.candidateBounds();
                if(!level.getWorldBorder().isWithinBounds(new BlockPos(area.minX(),0,area.minZ()))
                        ||!level.getWorldBorder().isWithinBounds(new BlockPos(area.maxXExclusive()-1,0,area.maxZExclusive()-1))) {
                    current.add("terrain_rejections",strings("OUTSIDE_WORLD_BORDER"));end("REJECTED");return;
                }
                for(var claim:RoadConstructionProtection.query(level,area)) {
                    if(claim.boundsXZ().expand(claim.exclusionMargin()).intersects(area)) {
                        current.addProperty("protection_status","CONFLICT:"+claim.id());
                        current.add("terrain_rejections",strings("PROTECTED_STRUCTURE_CONFLICT"));end("REJECTED");return;
                    }
                }
                loaded=allLoaded(level,area);
                current.addProperty("terrain_source",loaded?"LOADED_WORLD_BLOCKS":"BASE_NOISE_ESTIMATE_UNLOADED_FOOTPRINT");
            }catch(RuntimeException e) {
                current.addProperty("protection_status","UNKNOWN");current.add("terrain_rejections",strings(e.getMessage()));end("UNKNOWN");
            }
        }
        void sample(ServerLevel level,RoadSegmentPreset.Request r,int sample) {
            int station=(sample/3)*(r.length()-1)/4;
            int side=switch(sample%3){case 0->-r.type().rightOfWayWidth()/2;case 1->0;default->r.type().rightOfWayWidth()/2-1;};
            int x=r.x()+r.direction().getStepX()*station-r.direction().getStepZ()*side;
            int z=r.z()+r.direction().getStepZ()*station+r.direction().getStepX()*side;
            int ground;
            if(loaded) {
                if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null)throw new IllegalStateException("SURVEY_CHUNK_UNLOADED;RETRY");
                ground=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
                for(int v=0;v<9&&ground>level.getMinBuildHeight();v++) {
                    var state=level.getBlockState(new BlockPos(x,ground-1,z));
                    if(state.getBlock() instanceof BushBlock||state.is(Blocks.SNOW)){ground--;continue;}break;
                }
                var pos=new BlockPos(x,ground-1,z);var top=level.getBlockState(pos);
                water|=!top.getFluidState().isEmpty();tree|=top.is(BlockTags.LOGS)||top.is(BlockTags.LEAVES);
                nonSolid|=top.isAir()||!top.isCollisionShapeFullBlock(level,pos);
                for(int depth=1;depth<=3&&ground-depth>=level.getMinBuildHeight();depth++) {
                    var under=level.getBlockState(new BlockPos(x,ground-depth,z));
                    voidRisk|=under.isAir();water|=!under.getFluidState().isEmpty();
                }
            }else {
                var terrain=noise.sample(x,z,TerrainSource.NOISE_PRE_DECORATION);
                if(terrain.validity()!=TerrainValidity.VALID||terrain.surfaceY().isEmpty())
                    throw new IllegalStateException("BASE_TERRAIN_UNKNOWN:"+noise.lastFailure());
                boolean fluid=!terrain.hasKnownNoFluid();water|=fluid;
                if(!fluid&&terrain.surfaceType()==SurfaceType.UNKNOWN)throw new IllegalStateException("BASE_SURFACE_UNKNOWN");
                ground=terrain.topSolidSurfaceY().orElse(terrain.surfaceY().getAsInt());
                nonSolid|=!fluid&&terrain.surfaceType()!=SurfaceType.SOLID;
            }
            heights.add(ground);if(sample%3==1)centerHeights.add(ground);
        }
        void summarize() {
            current.addProperty("sampled_min_height",Collections.min(heights));current.addProperty("sampled_max_height",Collections.max(heights));
            current.addProperty("sampled_relief",Collections.max(heights)-Collections.min(heights));
            double sum=0,max=0;
            for(int i=1;i<centerHeights.size();i++) {
                int length=requests.get(index).length();int distance=i*(length-1)/4-(i-1)*(length-1)/4;
                double slope=Math.abs(centerHeights.get(i)-centerHeights.get(i-1))/(double)distance;
                sum+=slope;max=Math.max(max,slope);
            }
            current.addProperty("sampled_mean_ground_grade",sum/Math.max(1,centerHeights.size()-1));current.addProperty("sampled_max_ground_grade",max);
            current.addProperty("water_risk",water?"DETECTED":"NOT_DETECTED_IN_SAMPLES");
            current.addProperty("tree_obstruction",loaded?(tree?"DETECTED":"NOT_DETECTED_IN_SAMPLES"):"UNVERIFIED");
            current.addProperty("cave_risk",loaded?(voidRisk?"DETECTED":"NOT_DETECTED_IN_SAMPLES"):"UNVERIFIED");
        }
        void end(String status){current.addProperty("status",status);results.add(current);current=null;preflight=null;layout=null;index++;}
        void finish(ServerLevel level,ServerPlayer player) throws java.io.IOException {
            Gson gson=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();JsonObject root=new JsonObject();
            root.addProperty("schema_version",1);root.addProperty("spec_version",RoadPlanner.VERSION);
            root.addProperty("construction_version",RoadConstructionPlanner.VERSION);root.addProperty("world_seed",level.getSeed());
            root.addProperty("dimension",level.dimension().location().toString());root.addProperty("radius",radius);
            root.add("coordinates",gson.toJsonTree(Map.of("x",originX,"z",originZ)));
            root.addProperty("construction_authorized",false);root.addProperty("timed_out",timedOut);
            root.addProperty("candidate_budget",MAX_CANDIDATES);root.addProperty("completed_candidates",results.size());
            root.addProperty("coarse_sample_count",totalSamples);root.addProperty("actual_preflight_columns",totalActualColumns);
            root.add("engineering_budget",gson.toJsonTree(config));root.addProperty("forced_chunk_loads",0);
            root.addProperty("validation_scope","VERIFIED=V1B actual preflight only, not site provenance or permission; PREPARE and explicit BUILD still required");
            root.add("candidates",gson.toJsonTree(results));
            var best=results.stream().filter(j->j.get("status").getAsString().startsWith("SUITABLE_"))
                    .sorted(Comparator.comparingInt((JsonObject j)->j.get("status").getAsString().equals("SUITABLE_VERIFIED")?0:1)
                            .thenComparingInt(j->j.get("expected_max_cut").getAsInt()+j.get("expected_max_fill").getAsInt()))
                    .limit(5).toList();
            root.add("recommended",gson.toJsonTree(best));
            Path dir=FMLPaths.GAMEDIR.get().resolve("afl_debug/roads");Files.createDirectories(dir);
            Path file=dir.resolve("road_survey.json"),temp=dir.resolve("road_survey.json.tmp");
            Files.writeString(temp,gson.toJson(root),StandardCharsets.UTF_8);
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            player.sendSystemMessage(Component.literal("Survey"+(timedOut?"达到时间上限，部分结果":"完成")+"："+results.size()+"/32；报告 "+file.toAbsolutePath()));
            for(var result:best)player.sendSystemMessage(Component.literal(result.get("status").getAsString()+" | "+result.get("preview_command").getAsString()));
            if(best.isEmpty())player.sendSystemMessage(Component.literal("当前采样未找到适宜候选，请查看拒绝/UNKNOWN原因；没有施工。"));
            player.sendSystemMessage(Component.literal("ESTIMATED未查实际方块；VERIFIED也不授权施工。靠近候选、加载区块、站在施工范围外，再Preview/Prepare。"));
        }
    }
    private static boolean allLoaded(ServerLevel level,BoundsXZ b) {
        for(int x=b.minX()>>4;x<=(b.maxXExclusive()-1)>>4;x++)for(int z=b.minZ()>>4;z<=(b.maxZExclusive()-1)>>4;z++)
            if(level.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
    private static JsonArray strings(String... values){JsonArray result=new JsonArray();for(String value:values)result.add(value);return result;}
}
