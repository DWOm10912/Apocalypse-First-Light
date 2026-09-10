package com.antaurora.apofirstlight.dev;

import com.antaurora.apofirstlight.authoring.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraftforge.gametest.*;
import java.nio.file.*;
import java.util.*;

@GameTestHolder("apocalypse_firstlight")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class BuildingAuthoringGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return List.of(new TestFunction("authoring","afl_authoring_tests:workflow","afl_authoring_tests:empty",400,0L,true,BuildingAuthoringGameTests::workflow));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void load(net.minecraftforge.event.level.LevelEvent.Load e) {
        if(e.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            level.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_authoring_tests","empty"))
                    .fillFromWorld(level,new BlockPos(0,300,0),new Vec3i(1,1,1),false,Blocks.STRUCTURE_VOID);
    }
    @SuppressWarnings("unchecked") private static BuildingAuthoringSession session(UUID owner) throws Exception {
        var f=BuildingAuthoringCommands.class.getDeclaredField("SESSIONS");f.setAccessible(true);
        return ((Map<UUID,BuildingAuthoringSession>)f.get(null)).get(owner);
    }
    private static void denied(Runnable action,GameTestHelper h,String name) {
        boolean rejected=false;try{action.run();}catch(IllegalArgumentException e){rejected=true;}h.assertTrue(rejected,name);
    }
    private static void workflow(GameTestHelper h) {
        var level=h.getLevel();
        try {
            h.assertTrue(!BuildingAuthoringConfig.ENABLED.get(),"Dedicated authoring default false");
            var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"authoring_test"));
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.setPos(0,200,0);
            var src=p.createCommandSourceStack().withPermission(0);
            h.assertTrue(!BuildingAuthoringCommands.allowed(src),"Creative cannot bypass disabled config");
            BuildingAuthoringConfig.ENABLED.set(true);
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            h.assertTrue(!BuildingAuthoringCommands.allowed(src),"Survival non-op denied");
            h.assertTrue(BuildingAuthoringCommands.allowed(src.withPermission(2)),"Operator allowed");
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            // Isolated test world only: load fixture chunks explicitly, never production force tickets.
            for(int x=-32;x<160;x+=16)for(int z=-48;z<144;z+=16)level.getChunkAt(new BlockPos(x,200,z));
            var dispatcher=level.getServer().getCommands().getDispatcher();
            String id="authoring_test_box";
            h.assertTrue(dispatcher.execute("afl_author create "+id+" 16 20 12",src)==1,"create");
            var s=session(p.getUUID());h.assertTrue(s.state==BuildingAuthoringSession.State.EMPTY,"EMPTY");
            h.assertTrue(s.origin.equals(new BlockPos(32,200,0)),"grid origin");
            h.assertTrue(dispatcher.execute("afl_author bounds",src)==1&&s.boundsUntil==0,"bounds off");
            h.assertTrue(dispatcher.execute("afl_author bounds",src)==1&&s.boundsUntil>0,"bounds on");
            h.assertTrue(dispatcher.execute("afl_author info",src)==1,"info");
            h.assertTrue(dispatcher.execute("afl_author configure UTILITY \"EDGE,MIXED\" true true",src)==1,"metadata enums");
            level.setBlock(s.origin,Blocks.STONE.defaultBlockState(),2);
            BlockPos outside=s.origin.west();level.setBlock(outside,Blocks.GOLD_BLOCK.defaultBlockState(),2);
            h.assertTrue(dispatcher.execute("afl_author clear wrong",src)==0&&!level.isEmptyBlock(s.origin),"clear requires token");
            h.assertTrue(dispatcher.execute("afl_author clear",src)==1,"clear request");
            h.assertTrue(dispatcher.execute("afl_author clear "+s.clearToken,src)==1,"clear confirmed");
            h.assertTrue(level.isEmptyBlock(s.origin)&&level.getBlockState(outside).is(Blocks.GOLD_BLOCK),"clear exact bounds");
            // Standard intact fixture: foundation, walls, glazed window, SOUTH double-height door, second floor.
            for(var pos:BlockPos.betweenClosed(s.origin,s.max())) {
                int x=pos.getX()-s.origin.getX(),y=pos.getY()-s.origin.getY(),z=pos.getZ()-s.origin.getZ();
                if(y==0||y==5||y==10)level.setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),2);
                else if(y<10&&(x==0||x==15||z==0||z==19))level.setBlock(pos,Blocks.BRICKS.defaultBlockState(),2);
            }
            BlockPos door=s.origin.offset(7,1,19);
            level.setBlock(door,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH),2);
            level.setBlock(door.above(),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
            level.setBlock(s.origin.offset(0,3,5),Blocks.GLASS.defaultBlockState(),2);
            BlockPos inner=s.origin.offset(3,1,3);level.setBlock(inner,Blocks.BARREL.defaultBlockState(),2);
            h.assertTrue(dispatcher.execute("afl_author validate",src)==1&&s.state==BuildingAuthoringSession.State.VALIDATED,"validate fixture");
            level.setBlock(inner.above(),Blocks.STONE.defaultBlockState(),2);
            dispatcher.execute("afl_author info",src);h.assertTrue(s.state==BuildingAuthoringSession.State.DRAFT,"post-validation edits invalidate on audit");
            for(var forbidden:List.of(Blocks.COMMAND_BLOCK,Blocks.STRUCTURE_BLOCK,Blocks.JIGSAW,Blocks.BARRIER,Blocks.SPONGE)) {
                level.setBlock(inner.above(),forbidden.defaultBlockState(),2);
                h.assertTrue(dispatcher.execute("afl_author validate",src)==0,"reject "+forbidden);
            }
            level.setBlock(inner.above(),Blocks.AIR.defaultBlockState(),2);
            var barrel=(net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(inner);
            barrel.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
            h.assertTrue(dispatcher.execute("afl_author validate",src)==0,"reject inventory");barrel.clearContent();
            barrel.setLootTable(new net.minecraft.resources.ResourceLocation("minecraft","chests/simple_dungeon"),1L);
            h.assertTrue(dispatcher.execute("afl_author validate",src)==0,"reject loot without unpacking");
            level.removeBlockEntity(inner);level.setBlock(inner,Blocks.AIR.defaultBlockState(),2);level.setBlock(inner,Blocks.BARREL.defaultBlockState(),2);
            var item=new net.minecraft.world.entity.item.ItemEntity(level,inner.getX()+.5,inner.getY()+.5,inner.getZ()+.5,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));level.addFreshEntity(item);
            h.assertTrue(dispatcher.execute("afl_author validate",src)==0,"reject entities");item.discard();
            h.assertTrue(dispatcher.execute("afl_author export",src)==1&&s.state==BuildingAuthoringSession.State.EXPORTED,"export command");
            Path dir=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("afl_authoring_exports");
            Path nbt=dir.resolve(id+".nbt");h.assertTrue(Files.exists(nbt)&&Files.exists(dir.resolve(id+".json")),"export pair exists");
            h.assertTrue(dispatcher.execute("afl_author export",src)==0,"never overwrite");
            var tag=NbtIo.readCompressed(nbt.toFile());
            h.assertTrue(tag.getList("entities",10).isEmpty(),"no entities captured");
            var template=level.getStructureManager().readStructure(tag);
            h.assertTrue(template.getSize().equals(new Vec3i(16,12,20)),"standard NBT size");
            var json=com.google.gson.JsonParser.parseString(Files.readString(dir.resolve(id+".json"))).getAsJsonObject();
            h.assertTrue(json.get("surface_offset_y").getAsInt()==1&&json.get("front").getAsString().equals("SOUTH")&&!json.get("loot_ready").getAsBoolean(),"metadata contract");
            int i=0;
            for(Rotation rot:Rotation.values()) {
                BlockPos target=new BlockPos(80,220,30+i++*30);
                var settings=new StructurePlaceSettings().setRotation(rot).setIgnoreEntities(true);
                var bounds=template.getBoundingBox(settings,target);
                for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++)level.getChunk(x,z);
                h.assertTrue(template.placeInWorld(level,target,target,settings,net.minecraft.util.RandomSource.create(1),2),"place "+rot);
                BlockPos rotatedDoor=StructureTemplate.transform(new BlockPos(7,1,19),Mirror.NONE,rot,BlockPos.ZERO).offset(target);
                h.assertTrue(level.getBlockState(rotatedDoor).is(Blocks.OAK_DOOR)&&level.getBlockState(rotatedDoor).getValue(DoorBlock.FACING)==rot.rotate(Direction.SOUTH),"front "+rot);
                h.assertTrue(rotatedDoor.getY()==target.getY()+1,"surface offset "+rot);
                h.assertTrue(level.getBlockState(target).is(Blocks.STONE_BRICKS),"foundation "+rot);
                var re=new StructureTemplate();re.fillFromWorld(level,new BlockPos(bounds.minX(),bounds.minY(),bounds.minZ()),template.getSize(rot),false,null);
                h.assertTrue(re.save(new net.minecraft.nbt.CompoundTag()).getList("entities",10).isEmpty(),"reimport entities");
            }
            h.assertTrue(dispatcher.execute("afl_author cancel",src)==1&&session(p.getUUID())==null,"cancel");
            h.assertTrue(level.getBlockState(door).is(Blocks.OAK_DOOR),"cancel preserves draft");
            h.assertTrue(dispatcher.execute("afl_author resume authoring_test_box 32 200 0 16 20 12 1",src)==1,"resume existing draft");
            h.assertTrue(dispatcher.execute("afl_author validate",src)==1,"resumed draft validation");
            var resumed=session(p.getUUID());
            var visual=BuildingAuthoringCommands.class.getDeclaredMethod("drawBounds",net.minecraft.server.level.ServerPlayer.class,BuildingAuthoringSession.class);
            visual.setAccessible(true);visual.invoke(null,p,resumed);
            var check=BuildingAuthoringService.capture(level,resumed,true);
            for(var palette:check.nbt().getList("palette",10)) {
                String block=((net.minecraft.nbt.CompoundTag)palette).getString("Name");
                h.assertTrue(!block.contains("concrete")&&!block.contains("wool")&&!block.contains("structure")&&!block.contains("barrier"),"particles did not contaminate export");
            }
            h.assertTrue(dispatcher.execute("afl_author cancel",src)==1,"cancel resumed");
            denied(()->new BuildingMetadata("../escape",16,20,12,1,BuildingMetadata.Category.FILLER,Set.of(BuildingMetadata.Zone.EDGE),true,true),h,"id traversal denied");
            denied(()->new BuildingMetadata("huge",129,20,12,1,BuildingMetadata.Category.FILLER,Set.of(BuildingMetadata.Zone.EDGE),true,true),h,"size bound");
            com.antaurora.apofirstlight.ApocalypseFirstLight.LOGGER.info("[AUTHORING TEST] PASS commands, permissions, clear guard, metadata, loot/entities/debug rejection, export, manager reload, rotations; export={}",nbt.toAbsolutePath());
            h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}
        finally {BuildingAuthoringConfig.ENABLED.set(false);}
    }
}
