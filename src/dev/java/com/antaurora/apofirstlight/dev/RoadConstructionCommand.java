package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.worldgen.roads.*;
import com.antaurora.apofirstlight.worldgen.roads.construction.*;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.antaurora.apofirstlight.worldgen.terrain.*;
import com.google.gson.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit development adapter. The reusable construction core never depends on this command. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class RoadConstructionCommand {
    private static final Map<ServerLevel, Pending> PENDING = new WeakHashMap<>();
    private static final Map<ServerLevel, Long> LAST_PREVIEW = new WeakHashMap<>();
    private record Pending(UUID owner, RoadPlan layout, RoadConstructionConfig config,
                           RoadConstructionPlanner.Job job, boolean prepare, long expires, RoadSegmentPreset.Request segment) {}
    private RoadConstructionCommand() {}

    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(mode("preview",false));
        root.then(mode("prepare",true));
        RoadSurveyCommand.attach(root);
        root.then(Commands.literal("build").requires(RoadConstructionCommand::allowed)
                .then(id().then(Commands.literal("confirm").executes(c->action(c.getSource(),
                        StringArgumentType.getString(c,"plan_id"),"start")))));
        root.then(Commands.literal("resume").requires(RoadConstructionCommand::allowed)
                .then(id().then(Commands.literal("confirm").executes(c->action(c.getSource(),
                        StringArgumentType.getString(c,"plan_id"),"resume")))));
        root.then(Commands.literal("pause").requires(RoadConstructionCommand::allowed)
                .then(id().executes(c->action(c.getSource(),StringArgumentType.getString(c,"plan_id"),"pause"))));
        root.then(Commands.literal("status").requires(RoadConstructionCommand::allowed)
                .executes(c->action(c.getSource(),null,"list"))
                .then(id().executes(c->action(c.getSource(),StringArgumentType.getString(c,"plan_id"),"status"))));
        root.then(Commands.literal("cancel_preview").requires(RoadConstructionCommand::allowed).executes(c->{
            Pending p=PENDING.get(c.getSource().getLevel());
            if(p!=null && p.owner().equals(c.getSource().getPlayerOrException().getUUID())) {
                PENDING.remove(c.getSource().getLevel()); say(c.getSource(),"道路预检已取消，没有修改世界方块。"); return 1;
            }
            say(c.getSource(),"没有属于你的进行中预检。"); return 0;
        }));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,String> id() {
        return Commands.argument("plan_id",StringArgumentType.string());
    }
    static boolean allowed(CommandSourceStack s) {
        return !FMLEnvironment.production && s.hasPermission(2) && s.getEntity() instanceof ServerPlayer p
                && p.isCreative() && Level.OVERWORLD.equals(s.getLevel().dimension());
    }
    private static LiteralArgumentBuilder<CommandSourceStack> mode(String name,boolean prepare) {
        var mode=Commands.literal(name).requires(RoadConstructionCommand::allowed);
        mode.then(segmentMode(prepare));
        for(var layout:RoadPlan.Layout.values()) {
            if(layout==RoadPlan.Layout.SEGMENT)continue;
            var z=Commands.argument("z",IntegerArgumentType.integer(-29_999_000,29_999_000));
            if(prepare) z.then(Commands.literal("confirm_unbuilt").executes(c->begin(c.getSource(),layout,
                    IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z"),true)));
            else z.executes(c->begin(c.getSource(),layout,IntegerArgumentType.getInteger(c,"x"),
                    IntegerArgumentType.getInteger(c,"z"),false));
            mode.then(Commands.literal(layout.name().toLowerCase(Locale.ROOT))
                    .then(Commands.argument("x",IntegerArgumentType.integer(-29_999_000,29_999_000)).then(z)));
        }
        return mode;
    }
    static boolean busy(ServerLevel level){return PENDING.containsKey(level);}
    static RoadConstructionConfig config() throws java.io.IOException {
        return RoadConstructionConfig.load(FMLPaths.CONFIGDIR.get().resolve("apocalypse_firstlight/road_construction_v1.json"));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> segmentMode(boolean prepare) {
        var root=Commands.literal("segment");
        for(RoadType type:RoadType.values()) {
            var branch=Commands.literal(type.name().toLowerCase(Locale.ROOT));
            var length=Commands.argument("length",IntegerArgumentType.integer(16,64));
            for(Direction direction:Direction.Plane.HORIZONTAL) {
                branch.then(segmentDirection(type,direction,prepare,false));
                length.then(segmentDirection(type,direction,prepare,true));
            }
            root.then(branch.then(length));
        }
        return root;
    }
    private static LiteralArgumentBuilder<CommandSourceStack> segmentDirection(RoadType type,Direction direction,
                                                                              boolean prepare,boolean lengthGiven) {
        var branch=Commands.literal(direction.getName());
        com.mojang.brigadier.Command<CommandSourceStack> nearby=c->beginSegment(c.getSource(),type,
                lengthGiven?IntegerArgumentType.getInteger(c,"length"):32,direction,null,null,prepare);
        if(prepare)branch.then(Commands.literal("confirm_unbuilt").executes(nearby));else branch.executes(nearby);
        var z=Commands.argument("z",IntegerArgumentType.integer(-29_998_000,29_998_000));
        com.mojang.brigadier.Command<CommandSourceStack> positioned=c->beginSegment(c.getSource(),type,
                lengthGiven?IntegerArgumentType.getInteger(c,"length"):32,direction,
                IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z"),prepare);
        if(prepare)z.then(Commands.literal("confirm_unbuilt").executes(positioned));else z.executes(positioned);
        branch.then(Commands.literal("at").then(Commands.argument("x",IntegerArgumentType.integer(-29_998_000,29_998_000)).then(z)));
        return branch;
    }
    private static int beginSegment(CommandSourceStack source,RoadType type,int length,Direction direction,
                                    Integer x,Integer z,boolean prepare) {
        try {
            if(!allowed(source))throw new IllegalArgumentException("DEV_CREATIVE_OP_REQUIRED");
            ServerLevel level=source.getLevel();
            if(busy(level)||RoadSurveyCommand.busy(level)||TerrainDiagnosticCommand.busy(level))throw new IllegalArgumentException("ROAD_DIAGNOSTIC_ALREADY_RUNNING");
            long now=level.getGameTime();
            if(now-LAST_PREVIEW.getOrDefault(level,Long.MIN_VALUE/2)<100)throw new IllegalArgumentException("WAIT_5_SECONDS");
            var player=source.getPlayerOrException();var position=player.blockPosition();
            var request=new RoadSegmentPreset.Request(type,length,direction,
                    x==null?position.getX()+direction.getStepX()*12:x,
                    z==null?position.getZ()+direction.getStepZ()*12:z);
            var area=request.corridor().expand(4);
            var gate=TerrainRoadBenchmark.playerGate(request,position);
            if(!gate.get("status").getAsString().equals("POSITION_GATE_PASSED_ONLY")) {
                var report=TerrainDiagnosticIO.identity(level,request.x(),request.z());report.add("player_gate",gate);
                report.addProperty("road_preflight","NOT_STARTED");report.addProperty("preview_command",request.command("preview"));
                var file=TerrainDiagnosticIO.write("road_preview_position_gate",report);
                throw new IllegalArgumentException(gate.get("status").getAsString()+"; evidence="+file);
            }
            var config=config();var blockers=RoadConstructionProtection.query(level,area);
            var layout=RoadSegmentPreset.plan(level,request);
            var job=RoadConstructionPlanner.begin(level,layout,config,blockers,true);
            LAST_PREVIEW.put(level,now);
            PENDING.put(level,new Pending(player.getUUID(),layout,config,job,prepare,now+12000,request));
            say(source,"单路段预检已排队；固定坐标命令："+request.command("preview"));
            say(source,"准备命令："+request.command("prepare")+"；完整预检后仍需 plan_id 确认。请先备份无人建设测试存档。");
            return 1;
        }catch(Exception e){source.sendFailure(Component.literal("单路段预检拒绝："+e.getMessage()));return 0;}
    }
    private static int begin(CommandSourceStack source,RoadPlan.Layout layout,int x,int z,boolean prepare) {
        try {
            if(!allowed(source))throw new IllegalArgumentException("DEV_CREATIVE_OP_REQUIRED");
            ServerLevel level=source.getLevel();
            if(PENDING.containsKey(level)||RoadSurveyCommand.busy(level)||TerrainDiagnosticCommand.busy(level))throw new IllegalArgumentException("ROAD_DIAGNOSTIC_ALREADY_RUNNING");
            long now=level.getGameTime();
            if(now-LAST_PREVIEW.getOrDefault(level,Long.MIN_VALUE/2)<100)
                throw new IllegalArgumentException("WAIT_5_SECONDS");
            LAST_PREVIEW.put(level,now);
            var config=RoadConstructionConfig.load(FMLPaths.CONFIGDIR.get().resolve("apocalypse_firstlight")
                    .resolve("road_construction_v1.json"));
            BoundsXZ area=RoadPlanner.candidateBounds(x,z);
            if(!area.expand(32).contains(source.getPlayerOrException().blockPosition().getX(),
                    source.getPlayerOrException().blockPosition().getZ()))
                throw new IllegalArgumentException("MOVE_NEAR_REQUESTED_SITE");
            var blockers=RoadConstructionProtection.query(level,area);
            // Freeze exactly the existing synthetic 2D layout, then independently validate real world columns.
            // This sample authorizes NO writes and is never used as the actual construction elevation.
            TerrainQuery flat=(sx,sz,ts)->new TerrainSample(TerrainValidity.VALID,ts,OptionalInt.of(64),
                    OptionalInt.of(64),OptionalInt.of(64),OptionalInt.empty(),FluidCategory.NONE,
                    Optional.empty(),SurfaceType.SOLID,ProtectionKnowledge.UNKNOWN);
            String candidate="diagnostic_synthetic:"+layout.name().toLowerCase(Locale.ROOT)+":"+x+":"+z;
            RoadPlan plan=RoadPlanner.plan(level.getSeed(),candidate,x,z,layout,flat,
                    TerrainSource.STRUCTURE_PLANNING,blockers);
            if(!plan.successful())throw new IllegalArgumentException("LAYOUT_REJECTED:"+String.join(";",plan.diagnostics()));
            var job=RoadConstructionPlanner.begin(level,plan,config,blockers,true);
            PENDING.put(level,new Pending(source.getPlayerOrException().getUUID(),plan,config,job,prepare,now+12000,null));
            say(source,"V1-B 实际方块预检已排队，分 tick 读取；不会强载区块，也不会施工。/afl roads status 查询。");
            if(prepare) say(source,"已记录你对无人建设开发区域的声明；该声明不会绕过未知方块、结构、流体或保护检查。施工前请备份存档。");
            return 1;
        } catch(Exception e) { source.sendFailure(Component.literal("道路预检未开始："+e.getMessage()));return 0; }
    }

    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level))return;
        Pending p=PENDING.get(level); if(p==null)return;
        ServerPlayer player=level.getServer().getPlayerList().getPlayer(p.owner());
        if(player==null || player.serverLevel()!=level || !allowed(player.createCommandSourceStack())
                || level.getGameTime()>p.expires()) { PENDING.remove(level);return; }
        try {
            p.job().advance(p.config().columnsPerTick());
            if(!p.job().done())return;
            PENDING.remove(level);
            RoadConstructionPlan plan=p.job().result();
            if(p.segment()!=null && plan.bounds().expand(2).contains(player.blockPosition().getX(),player.blockPosition().getZ())) {
                var report=TerrainDiagnosticIO.identity(level,p.segment().x(),p.segment().z());
                report.add("player_gate",TerrainRoadBenchmark.playerGate(p.segment(),player.blockPosition()));
                report.addProperty("status","PLAYER_ENTERED_SEGMENT");report.addProperty("terrain_status",plan.status().name());
                report.add("actual_exclusion_bounds",TerrainDiagnosticIO.GSON.toJsonTree(plan.bounds().expand(2)));
                report.add("failure_details",TerrainDiagnosticIO.GSON.toJsonTree(p.job().failureDetails()));
                var file=TerrainDiagnosticIO.write("road_preview_position_gate",report);
                throw new IllegalArgumentException("PLAYER_ENTERED_SEGMENT;MOVE_OUTSIDE_AND_REPEAT_PREVIEW; evidence="+file);
            }
            // Reports may say can_confirm only after the ledger accepted the exact snapshot.
            // Preparing saves metadata only; a later explicit build command is still mandatory.
            if(p.prepare() && plan.status()==RoadConstructionPlan.Status.PREVIEW_READY)
                RoadConstructionJobs.prepare(level,p.owner(),plan,p.config());
            Path file=writeReport(level,p,plan);
            var summary=plan.summary();
            player.sendSystemMessage(Component.literal("V1-B "+plan.status()+" | id="+plan.planId()
                    +" | 道路="+summary.roads()+" 路口="+summary.junctions()+" 区块="+summary.chunks()
                    +" CUT="+summary.cutBlocks()+" FILL="+summary.fillBlocks()+" 修改="+summary.expectedEdits()));
            player.sendSystemMessage(Component.literal("报告："+file.toAbsolutePath()+"；"+String.join("; ",plan.issues())));
            if(plan.status()==RoadConstructionPlan.Status.PREVIEW_READY)
                player.sendSystemMessage(Component.literal(p.prepare()
                        ? "审核报告并备份后执行 /afl roads build \""+plan.planId()+"\" confirm；不支持事务回滚。"
                        : "此结果仅预览，不可施工。确认无人建设区域后使用 prepare ... confirm_unbuilt 重新预检。"));
        } catch(Exception e) {
            PENDING.remove(level);player.sendSystemMessage(Component.literal("道路预检/报告未完成（未施工）："+e.getMessage()
                    +"；/afl roads status 可检查是否已有 PREPARED 记录。"));
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level) {PENDING.remove(level);LAST_PREVIEW.remove(level);}
    }

    private static int action(CommandSourceStack s,String id,String action) {
        try {
            if(!allowed(s))throw new IllegalArgumentException("DEV_CREATIVE_OP_REQUIRED");
            UUID owner=s.getPlayerOrException().getUUID();ServerLevel level=s.getLevel();
            if(action.equals("list")) {
                Pending p=PENDING.get(level);if(p!=null)say(s,"PRECHECK_RUNNING; "+p.job().phase()+" "+p.job().processedColumns()+"/"+p.job().totalColumns());
                if(RoadSurveyCommand.busy(level))say(s,RoadSurveyCommand.progress(level));
                var all=RoadConstructionJobs.list(level,owner);
                for(var view:all)say(s,format(view));
                if(all.isEmpty() && p==null && !RoadSurveyCommand.busy(level))say(s,"没有施工记录。");return 1;
            }
            var old=RoadConstructionJobs.status(level,owner,id);
            if((action.equals("start")||action.equals("resume")) && !old.bounds().expand(32)
                    .contains(s.getPlayerOrException().blockPosition().getX(),s.getPlayerOrException().blockPosition().getZ()))
                throw new IllegalArgumentException("MOVE_NEAR_PREPARED_SITE");
            var view=switch(action) {
                case "start" -> RoadConstructionJobs.start(level,owner,id);
                case "pause" -> RoadConstructionJobs.pause(level,owner,id);
                case "resume" -> RoadConstructionJobs.resume(level,owner,id);
                default -> old;
            };
            say(s,format(view));return 1;
        }catch(Exception e){s.sendFailure(Component.literal("道路施工命令拒绝："+e.getMessage()));return 0;}
    }
    private static String format(RoadConstructionJobs.StatusView v) {
        return v.planId()+" | "+v.state()+" / "+v.phase()+" | 修改 "+v.completedEdits()+"/"+v.totalEdits()
                +" | 复核 "+v.checkedGuards()+"/"+v.totalGuards()+" | 完成区块 "+v.completedChunks()+"/"+v.totalChunks()
                +" | "+v.reason();
    }
    private static void say(CommandSourceStack s,String text){s.sendSuccess(()->Component.literal(text),false);}

    private static Path writeReport(ServerLevel level,Pending p,RoadConstructionPlan plan) throws java.io.IOException {
        Gson gson=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        JsonObject j=new JsonObject();
        j.addProperty("schema_version",1);j.addProperty("status",plan.status().name());
        j.addProperty("plan_id",plan.planId());j.addProperty("source_plan_id",plan.sourcePlanId());
        j.addProperty("version",plan.version());j.addProperty("dimension",plan.dimension().toString());
        j.addProperty("seed",level.getSeed());j.addProperty("owner",p.owner().toString());
        j.addProperty("spec_version",RoadPlanner.VERSION);j.addProperty("world_seed",level.getSeed());
        j.addProperty("candidate_id",p.layout().candidateId());
        j.addProperty("terrain_source","LOADED_WORLD_BLOCKS_WITH_SPARSE_NOISE_COMPARISON");
        j.addProperty("validation_level",plan.status()==RoadConstructionPlan.Status.PREVIEW_READY?"ROAD_PREFLIGHT_VERIFIED":"INCOMPLETE_OR_REJECTED");
        j.addProperty("construction_authorized",false);
        j.add("terrain_rejections",gson.toJsonTree(plan.issues()));
        j.add("failure_details",gson.toJsonTree(p.job().failureDetails()));
        if(p.segment()!=null) {
            var owner=level.getServer().getPlayerList().getPlayer(p.owner());
            if(owner!=null)j.add("player_position_gate_at_completion",TerrainRoadBenchmark.playerGate(p.segment(),owner.blockPosition()));
        }
        j.add("engineering_budget",gson.toJsonTree(p.config()));
        j.addProperty("protection_status","KNOWN_CHECKS_ONLY;UNIVERSAL_PLAYER_MOD_PROVENANCE_UNVERIFIED");
        if(p.segment()!=null) {
            var r=p.segment();j.addProperty("road_type",r.type().name());j.addProperty("length",r.length());
            j.addProperty("direction",r.direction().getName());
            j.add("coordinates",gson.toJsonTree(Map.of("start_x",r.x(),"start_z",r.z(),"end_x",r.endX(),"end_z",r.endZ())));
            j.addProperty("preview_command",r.command("preview"));j.addProperty("prepare_command",r.command("prepare"));
        }
        j.addProperty("reported_at_game_tick",level.getGameTime());
        j.addProperty("unstarted_plan_lifetime_ticks",p.config().planLifetimeTicks());
        j.addProperty("unbuilt_dev_site_attested",p.prepare());
        j.addProperty("can_confirm",p.prepare() && plan.status()==RoadConstructionPlan.Status.PREVIEW_READY);
        j.addProperty("protection_limit","Known exclusions + loaded block/structure checks; natural player blocks indistinguishable; dev-site attestation required; no universal mod-protection guarantee");
        j.addProperty("rollback","NOT_IMPLEMENTED; back up world before confirm; pause/reconcile only");
        j.add("bounds",gson.toJsonTree(plan.bounds()));j.add("summary",gson.toJsonTree(plan.summary()));
        j.add("config",gson.toJsonTree(p.config()));j.add("profiles",gson.toJsonTree(plan.profiles()));
        j.add("lot_ground_y",gson.toJsonTree(plan.lotGroundY()));j.add("issues",gson.toJsonTree(plan.issues()));
        j.addProperty("source_layout_height_authority","XZ_ONLY: source_layout uses synthetic G=64 for topology; actual heights are profiles and effective_lots");
        j.add("source_layout",JsonParser.parseString(RoadPlanJson.write(p.layout())));
        JsonArray effectiveLots=new JsonArray();
        for(var lot:p.layout().lots()) {
            Integer g=plan.lotGroundY().get(lot.id());if(g==null)continue;
            JsonObject item=new JsonObject();item.addProperty("id",lot.id());item.addProperty("G",g);
            item.addProperty("surfaceH16",16*g-3);item.addProperty("nbt_bound",false);
            var body=lot.building();JsonArray origin=new JsonArray();
            origin.add(body.placementOrigin().getX());origin.add(g-body.groundAnchorOffsetY());
            origin.add(body.placementOrigin().getZ());item.add("planned_building_origin",origin);
            item.add("bounds",gson.toJsonTree(lot.fullBounds()));
            item.addProperty("terrain_validated_scope","Entrance strips only; parcel interior reserved, neither leveled nor approved for building placement");
            effectiveLots.add(item);
        }
        j.add("effective_lots",effectiveLots);
        JsonArray edits=new JsonArray();
        for(var e:plan.edits()) {
            JsonObject item=new JsonObject();item.addProperty("x",e.pos().getX());item.addProperty("y",e.pos().getY());
            item.addProperty("z",e.pos().getZ());item.addProperty("before",e.before().toString());
            item.addProperty("after",e.after().toString());item.addProperty("unit",e.unit());
            item.addProperty("kind",e.kind().name());edits.add(item);
        }
        j.add("edits",edits);
        Path directory=FMLPaths.GAMEDIR.get().resolve("afl_debug/roads");Files.createDirectories(directory);
        Path target=directory.resolve("construction_"+p.layout().layout().name().toLowerCase(Locale.ROOT)+".json");
        Path temp=directory.resolve(target.getFileName()+".tmp");
        Files.writeString(temp,gson.toJson(j),StandardCharsets.UTF_8);
        try {Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException ignored){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        return target;
    }
}
