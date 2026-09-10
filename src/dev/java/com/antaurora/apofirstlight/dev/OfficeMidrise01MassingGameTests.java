package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.authoring.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class OfficeMidrise01MassingGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return List.of(new TestFunction("office_massing","afl_office_tests:massing","afl_office_tests:empty",200,0L,true,OfficeMidrise01MassingGameTests::run));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void load(net.minecraftforge.event.level.LevelEvent.Load e) {
        if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel l)
            l.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_office_tests","empty"))
                    .fillFromWorld(l,new BlockPos(0,300,0),new Vec3i(1,1,1),false,Blocks.STRUCTURE_VOID);
    }
    private static void run(GameTestHelper h) {
        var level=h.getLevel();
        try {
            var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"office_review"));
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.setPos(0,180,0);
            var src=p.createCommandSourceStack().withPermission(0);var commands=level.getServer().getCommands().getDispatcher();
            BuildingAuthoringConfig.ENABLED.set(true);
            for(int x=32;x<64;x+=16)for(int z=0;z<48;z+=16)level.getChunkAt(new BlockPos(x,180,z));
            h.assertTrue(commands.execute("afl_author create office_midrise_01 28 34 40 1",src)==1,"create");
            var s=BuildingAuthoringCommands.active(src);BlockPos o=s.origin;
            level.setBlock(o.west(),Blocks.GOLD_BLOCK.defaultBlockState(),2);
            h.assertTrue(commands.execute("afl_author build office_midrise_01",src)==1,"one-command draft");
            h.assertTrue(s.state==BuildingAuthoringSession.State.DRAFT,"only DRAFT, not validated/exported");
            h.assertTrue(s.metadata.category()==BuildingMetadata.Category.MIDRISE_OFFICE&&s.metadata.zones().equals(Set.of(BuildingMetadata.Zone.CORE,BuildingMetadata.Zone.MIXED)),"metadata");
            for(int floor:new int[]{1,6,11,16,21,26}) {
                h.assertTrue(level.getBlockState(o.offset(13,floor,16)).is(Blocks.SMOOTH_STONE),"floor "+floor);
                for(int y=1;y<=4;y++)h.assertTrue(level.isEmptyBlock(o.offset(13,floor+y,16)),"four clear interior blocks");
            }
            for(int floor:new int[]{6,11,16,21,26})for(int x:new int[]{4,13,23})
                h.assertTrue(level.getBlockState(o.offset(x,floor+2,33)).is(Blocks.BLACK_STAINED_GLASS),"three front bays");
            for(int y=2;y<=4;y++)h.assertTrue(level.isEmptyBlock(o.offset(13,y,33)),"walkable entrance");
            h.assertTrue(level.getBlockState(o).is(Blocks.STONE_BRICKS),"embedded foundation");
            h.assertTrue(level.getBlockState(o.offset(13,31,16)).is(Blocks.SMOOTH_STONE),"roof slab");
            h.assertTrue(level.getBlockState(o.west()).is(Blocks.GOLD_BLOCK),"outside unchanged");
            h.assertTrue(commands.execute("afl_author build office_midrise_01",src)==0,"no overwrite of draft");
            int occupied=0;
            for(var pos:BlockPos.betweenClosed(o,s.max())) {
                if(!level.isEmptyBlock(pos))occupied++;
                h.assertTrue(level.getBlockEntity(pos)==null,"no BE/loot/debug containers");
                var b=level.getBlockState(pos);
                h.assertTrue(!b.is(Blocks.COMMAND_BLOCK)&&!b.is(Blocks.STRUCTURE_BLOCK)&&!b.is(Blocks.JIGSAW)&&!b.is(Blocks.BARRIER),"no debug blocks");
            }
            h.assertTrue(level.getEntities((net.minecraft.world.entity.Entity)null,s.bounds(),e->true).isEmpty(),"no entities");
            var exports=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("afl_authoring_exports");
            h.assertTrue(!java.nio.file.Files.exists(exports.resolve("office_midrise_01.nbt"))&&!java.nio.file.Files.exists(exports.resolve("office_midrise_01.json")),"no asset export");
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[OFFICE MASSING TEST] PASS occupied={} bounds=28x34x40 floors=6 DRAFT_ONLY NO_EXPORT; user-world placement and visual approval still required",occupied);
            commands.execute("afl_author cancel",src);h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}
        finally {BuildingAuthoringConfig.ENABLED.set(false);}
    }
}
