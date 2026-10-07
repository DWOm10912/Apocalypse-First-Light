package com.antaurora.apofirstlight.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Dev-only, creative OP/Overworld gate. Packaging excludes this entire package. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class TerrainDiagnosticCommand {
    private static final Map<ServerLevel,TerrainDiagnosticJob> RUNNING=new WeakHashMap<>();
    private static final Map<ServerLevel,Long> LAST=new WeakHashMap<>();
    private TerrainDiagnosticCommand() {}
    static boolean busy(ServerLevel level){return RUNNING.containsKey(level);}
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        var root=Commands.literal("terrain").requires(RoadConstructionCommand::allowed);
        for(String mode:List.of("inspect","subsurface")) {
            var branch=Commands.literal(mode).executes(c->here(c.getSource(),mode,0));
            branch.then(Commands.literal("here").executes(c->here(c.getSource(),mode,0)));
            var z=Commands.argument("z",IntegerArgumentType.integer(-29_990_000,29_990_000))
                    .executes(c->start(c.getSource(),mode,IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z"),0,16));
            if(mode.equals("subsurface"))z.then(Commands.argument("depth",IntegerArgumentType.integer(1,32))
                    .executes(c->start(c.getSource(),mode,IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"z"),0,IntegerArgumentType.getInteger(c,"depth"))));
            branch.then(Commands.argument("x",IntegerArgumentType.integer(-29_990_000,29_990_000)).then(z));root.then(branch);
        }
        for(String mode:List.of("survey","benchmark")) {
            var branch=Commands.literal(mode).executes(c->here(c.getSource(),mode,256));
            for(int radius:new int[]{128,256,512,1024})branch.then(Commands.literal(Integer.toString(radius)).executes(c->here(c.getSource(),mode,radius)));
            if(mode.equals("benchmark"))branch.then(Commands.literal("samples").executes(c->here(c.getSource(),"samples",0)));
            root.then(branch);
        }
        root.then(Commands.literal("status").executes(c->{var job=RUNNING.get(c.getSource().getLevel());say(c.getSource(),job==null?"NO_TERRAIN_DIAGNOSTIC":job.progress());return 1;}));
        root.then(Commands.literal("cancel").executes(c->{var job=RUNNING.get(c.getSource().getLevel());
            if(job==null)return 0;if(!job.owner.equals(c.getSource().getPlayerOrException().getUUID()))return 0;
            RUNNING.remove(job.level);say(c.getSource(),"地形诊断已取消，未写入世界；未完成报告不会覆盖上次基准。");return 1;}));return root;
    }
    private static int here(CommandSourceStack s,String mode,int radius) {
        var p=s.getPosition();return start(s,mode,(int)Math.floor(p.x),(int)Math.floor(p.z),radius,mode.equals("inspect")||mode.equals("subsurface")?16:12);
    }
    private static int start(CommandSourceStack s,String mode,int x,int z,int radius,int depth) {
        try {
            if(!RoadConstructionCommand.allowed(s))throw new IllegalArgumentException("DEV_CREATIVE_OP_OVERWORLD_REQUIRED");
            var level=s.getLevel();if(busy(level)||RoadConstructionCommand.busy(level)||RoadSurveyCommand.busy(level))throw new IllegalArgumentException("DIAGNOSTIC_ALREADY_RUNNING");
            if(Math.abs((long)x)>29_990_000||Math.abs((long)z)>29_990_000)throw new IllegalArgumentException("COORDINATE_BOUND");
            if(level.getGameTime()-LAST.getOrDefault(level,Long.MIN_VALUE/2)<100)throw new IllegalArgumentException("WAIT_5_SECONDS");
            var job=new TerrainDiagnosticJob(s.getPlayerOrException(),mode,x,z,radius,depth,RoadConstructionCommand.config());
            RUNNING.put(level,job);LAST.put(level,level.getGameTime());
            say(s,"地形只读诊断开始："+mode+"。不加载区块、不施工；/afl terrain status 或 /afl terrain cancel。预计数秒至数分钟，保持测试区域静止。");return 1;
        }catch(Exception e){s.sendFailure(Component.literal("地形诊断未开始："+e.getMessage()));return 0;}
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        var job=RUNNING.get(level);if(job==null)return;
        var player=level.getServer().getPlayerList().getPlayer(job.owner);
        if(player==null||player.serverLevel()!=level||!RoadConstructionCommand.allowed(player.createCommandSourceStack())){RUNNING.remove(level);return;}
        try {
            if(level.getGameTime()-job.started>=12000) {
                var file=TerrainDiagnosticIO.write("terrain_partial",job.finish("TIMEOUT_INCOMPLETE"));RUNNING.remove(level);
                player.sendSystemMessage(Component.literal("地形诊断超时，部分结果："+file+"；没有覆盖正式基准。"));return;
            }
            job.advance();if(!job.done())return;
            var result=job.finish("COMPLETE");String filename=switch(job.mode) {
                case "benchmark"->"terrain_v1_baseline";case "survey"->"terrain_region_survey";case "samples"->"terrain_regression_samples";
                default->"terrain_"+job.mode+"_"+job.x+"_"+job.z;
            };
            var file=TerrainDiagnosticIO.write(filename,result);RUNNING.remove(level);
            player.sendSystemMessage(Component.literal("地形诊断完成："+file+"；forced_chunk_loads=0。未加载部分为估计或 UNKNOWN，实机结论以 JSON 为准。"));
        }catch(Exception e){RUNNING.remove(level);player.sendSystemMessage(Component.literal("地形诊断停止："+e+"；未施工，未覆盖完整基准。"));}
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level){RUNNING.remove(level);LAST.remove(level);}
    }
    private static void say(CommandSourceStack s,String message){s.sendSuccess(()->Component.literal(message),false);}
}
