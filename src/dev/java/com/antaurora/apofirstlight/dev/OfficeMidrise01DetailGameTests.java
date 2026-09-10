package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.authoring.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class OfficeMidrise01DetailGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return List.of(new TestFunction("office_details","afl_office_detail_tests:upgrade","afl_office_detail_tests:empty",400,0L,true,OfficeMidrise01DetailGameTests::run));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void load(net.minecraftforge.event.level.LevelEvent.Load e) {
        if(e.getLevel() instanceof ServerLevel l)
            l.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_office_detail_tests","empty"))
                    .fillFromWorld(l,new BlockPos(0,300,0),new Vec3i(1,1,1),false,Blocks.STRUCTURE_VOID);
    }
    private record Walk(double feet,Direction stairs) {}
    /** Static collision connectivity, not a substitute for player-controlled movement acceptance. */
    private static Set<BlockPos> walkable(ServerLevel level,BlockPos origin) {
        var nodes=new HashMap<BlockPos,Walk>();
        for(int x=1;x<27;x++)for(int z=1;z<33;z++)for(int y=1;y<38;y++) {
            BlockPos key=new BlockPos(x,y,z),pos=origin.offset(key);var state=level.getBlockState(pos);
            var shape=state.getCollisionShape(level,pos);if(shape.isEmpty())continue;
            double height=0;
            for(var box:shape.toAabbs())if(box.maxX>.2&&box.minX<.8&&box.maxZ>.2&&box.minZ<.8)height=Math.max(height,box.maxY);
            if(height<=0)continue;
            double feet=pos.getY()+height;
            if(level.noCollision((net.minecraft.world.entity.Entity)null,new AABB(pos.getX()+.2,feet+.00001,pos.getZ()+.2,pos.getX()+.8,feet+1.79999,pos.getZ()+.8)))
                nodes.put(key,new Walk(feet,state.getBlock() instanceof StairBlock?state.getValue(StairBlock.FACING):null));
        }
        BlockPos start=new BlockPos(13,1,30);if(!nodes.containsKey(start))throw new IllegalStateException("Lobby start blocked");
        var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();seen.add(start);queue.add(start);
        while(!queue.isEmpty()) {
            var key=queue.remove();var a=nodes.get(key);
            for(Direction d:Direction.Plane.HORIZONTAL)for(int dy=-1;dy<=1;dy++) {
                var next=key.relative(d).offset(0,dy,0);var b=nodes.get(next);if(b==null||seen.contains(next))continue;
                double rise=b.feet-a.feet;
                if(Math.abs(rise)>1.00001)continue;
                if(rise>.60001&&b.stairs!=d)continue;
                if(rise<-.60001&&a.stairs!=d.getOpposite())continue;
                seen.add(next);queue.add(next);
            }
        }
        return seen;
    }
    private static void run(GameTestHelper h) {
        var level=h.getLevel();
        try {
            var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"office_upgrade"));
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.setPos(0,180,0);
            var src=p.createCommandSourceStack().withPermission(0);var cmds=level.getServer().getCommands().getDispatcher();
            BuildingAuthoringConfig.ENABLED.set(true);
            for(int x=32;x<64;x+=16)for(int z=0;z<48;z+=16)level.getChunkAt(new BlockPos(x,180,z));
            h.assertTrue(cmds.execute("afl_author create office_midrise_01 28 34 40 1",src)==1,"create");
            var s=BuildingAuthoringCommands.active(src);var o=s.origin;
            h.assertTrue(cmds.execute("afl_author build office_midrise_01",src)==1,"baseline");
            var conflict=o.offset(3,2,5);level.setBlock(conflict,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
            h.assertTrue(cmds.execute("afl_author upgrade office_midrise_01",src)==0,"refuse conflicting manual edit");
            h.assertTrue(level.getBlockState(o.offset(8,3,20)).isAir(),"atomic preflight leaves interior unchanged");
            level.setBlock(conflict,Blocks.AIR.defaultBlockState(),2);
            var preserved=o.offset(11,2,22);level.setBlock(preserved,Blocks.LAPIS_BLOCK.defaultBlockState(),2);
            level.setBlock(o.west(),Blocks.GOLD_BLOCK.defaultBlockState(),2);
            h.assertTrue(cmds.execute("afl_author upgrade office_midrise_01",src)==1,"upgrade command");
            h.assertTrue(s.state==BuildingAuthoringSession.State.DRAFT,"not validated/exported");
            h.assertTrue(level.getBlockState(preserved).is(Blocks.LAPIS_BLOCK)&&level.getBlockState(o.west()).is(Blocks.GOLD_BLOCK),"unrelated edits preserved");
            h.assertTrue(OfficeMidrise01DetailBuilder.upgrade(level,s)==0,"repeat is no-op");
            Set<BlockPos> reachable=walkable(level,o);
            for(int f:new int[]{1,6,11,16,21,26}) {
                h.assertTrue(reachable.contains(new BlockPos(10,f,15)),"corridor reachable floor="+f+" nodes="+reachable.size());
                h.assertTrue(reachable.contains(new BlockPos(6,f,20)),"west office reachable floor="+f);
                if(f>1)h.assertTrue(reachable.contains(new BlockPos(24,f,24)),"east office reachable floor="+f);
                h.assertTrue(level.getBlockState(o.offset(10,f+4,26)).is(Blocks.SEA_LANTERN),"stable office lighting");
            }
            h.assertTrue(reachable.contains(new BlockPos(10,32,4)),"roof reachable from lobby");
            int lamps=0,steps=0;
            for(var pos:BlockPos.betweenClosed(o,s.max())) {
                var state=level.getBlockState(pos);if(state.is(Blocks.SEA_LANTERN))lamps++;
                if(state.is(Blocks.POLISHED_ANDESITE_STAIRS))steps++;
                h.assertTrue(level.getBlockEntity(pos)==null,"no loot/BE/machine/debug");
                h.assertTrue(!state.is(Blocks.COMMAND_BLOCK)&&!state.is(Blocks.STRUCTURE_BLOCK)&&!state.is(Blocks.BARRIER)&&!state.is(Blocks.JIGSAW),"no debug blocks");
            }
            h.assertTrue(steps==62&&lamps>50,"stair and lighting counts");
            h.assertTrue(level.getEntities((net.minecraft.world.entity.Entity)null,s.bounds(),e->true).isEmpty(),"no entities");
            var exports=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("afl_authoring_exports");
            h.assertTrue(!java.nio.file.Files.exists(exports.resolve("office_midrise_01.nbt"))&&!java.nio.file.Files.exists(exports.resolve("office_midrise_01.json")),"no export");
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[OFFICE DETAIL TEST] PASS conflict atomicity, preserved edits, no-op repeat, lobby-to-six-floors-and-roof static collision connectivity, stairs={} lamps={} reachableNodes={} NO_EXPORT",steps,lamps,reachable.size());
            cmds.execute("afl_author cancel",src);h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}
        finally {BuildingAuthoringConfig.ENABLED.set(false);}
    }
}
