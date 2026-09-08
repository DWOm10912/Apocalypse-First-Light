package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.block.StaticWorkstationBlock;
import com.antaurora.apofirstlight.blockentity.GunMaintenanceBenchBlockEntity;
import com.antaurora.apofirstlight.registry.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;

/** Test-only orchestration. All client actions still use normal Minecraft network packets. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class MaintenanceMultiplayerServer {
    private static final BlockPos ROOT=new BlockPos(0,100,0);
    private static int stage=-1,ticks,changed;private static boolean done;
    private static Path dir(){return Path.of(System.getProperty("afl.multiplayerControl"));}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e)throws Exception{
        if(!Boolean.getBoolean("afl.maintenanceMultiplayerServer")||done||e.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();ticks++;
        if(ticks%20!=0)return;
        if(stage==13&&ack("A")&&ack("B")){log("PASS REAL_TWO_CLIENT_NETWORK_UI_SYNC");done=true;server.saveEverything(false,true,true);server.halt(false);return;}
        if(ticks>18000){log("FAIL TIMEOUT stage="+stage);done=true;server.halt(false);return;}
        var a=server.getPlayerList().getPlayerByName("AFL_Test_A");var b=server.getPlayerList().getPlayerByName("AFL_Test_B");
        if(a==null||b==null)return;
        try{
            if(stage<0){
                Files.createDirectories(dir());var l=server.overworld();
                l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);l.setDayTime(1000);
                for(int x=-3;x<=4;x++)for(int z=-3;z<=3;z++)l.setBlock(ROOT.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
                for(var p:StaticWorkstationBlock.Part.values())l.setBlock(StaticWorkstationBlock.partPosition(ROOT,Direction.NORTH,p),AflBlocks.GUN_MAINTENANCE_BENCH.get().stateFor(Direction.NORTH,p),3);
                for(var p:new ServerPlayer[]{a,b}){p.teleportTo(l,.5,100,-1,java.util.Set.of(),0,20);p.getInventory().clearContent();open(p);}
                a.getInventory().setItem(4,new ItemStack(AflItems.P9_01.get()));a.containerMenu.broadcastChanges();
                log("BOOT_AFL_PASS A="+a.getUUID()+" B="+b.getUUID()+" BENCH="+ROOT);advance(0);return;
            }
            if(ticks-changed<60||!ack("A")||!ack("B"))return;
            var bench=(GunMaintenanceBenchBlockEntity)server.overworld().getBlockEntity(ROOT);
            if(stage==4||stage==8||stage==12){
                int n=bench.getItem(0).getCount();for(var p:new ServerPlayer[]{a,b})for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(AflItems.P9_01.get()))n+=p.getInventory().getItem(i).getCount();
                n+=server.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(ROOT).inflate(5),x->x.getItem().is(AflItems.P9_01.get())).stream().mapToInt(x->x.getItem().getCount()).sum();
                if(n!=1||!bench.isEmpty())throw new IllegalStateException("count="+n);
                log("CASE="+stage+" INITIAL_GUN_COUNT=1 FINAL_GUN_COUNT="+n+" BENCH=EMPTY A="+a.getInventory().getItem(4)+" B="+b.getInventory().getItem(0));
            }
            if(stage==4||stage==8){a.getInventory().clearContent();b.getInventory().clearContent();a.getInventory().setItem(4,new ItemStack(AflItems.P9_01.get()));a.containerMenu.broadcastChanges();b.containerMenu.broadcastChanges();}
            if(stage==13){log("PASS REAL_TWO_CLIENT_NETWORK_UI_SYNC");done=true;server.saveEverything(false,true,true);server.halt(false);return;}
            advance(stage+1);
        }catch(Exception ex){log("FAIL "+ex);done=true;Files.writeString(dir().resolve("failure"),ex.toString());server.halt(false);}
    }
    private static void open(ServerPlayer p){var pos=ROOT.east().above();p.serverLevel().getBlockState(pos).use(p.serverLevel(),p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));}
    private static boolean ack(String role)throws Exception{var p=dir().resolve("ack"+role);return Files.exists(p)&&Files.readString(p).equals(""+stage);}
    private static void advance(int s)throws Exception{stage=s;changed=ticks;Files.writeString(dir().resolve("stage"),""+s);log("STAGE="+s);}
    private static void log(String s){com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[MP LIVE SERVER] {}",s);}
}
