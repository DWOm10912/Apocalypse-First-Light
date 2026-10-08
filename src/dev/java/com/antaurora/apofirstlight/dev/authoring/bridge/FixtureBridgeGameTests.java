package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.*;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/** Bridge V2: whitelisted BlockEntity fixtures, multiblocks, shared history, reconciliation and audit. */
@GameTestHolder("apocalypse_firstlight") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class FixtureBridgeGameTests {
    private static final String NS="afl_bridge_fixture_tests";
    @GameTestGenerator public static Collection<TestFunction> cases(){return List.of(
        new TestFunction("bridge_fixtures",NS+":core",NS+":empty",400,0L,true,FixtureBridgeGameTests::run));}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void load(net.minecraftforge.event.level.LevelEvent.Load e){
        if(e.getLevel() instanceof ServerLevel l)l.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation(NS,"empty")).fillFromWorld(l,new BlockPos(0,300,0),new Vec3i(1,1,1),false,Blocks.STRUCTURE_VOID);
    }
    interface Checked{void run()throws Exception;}
    private static void expect(GameTestHelper h,Checked action,String code){
        try{action.run();}catch(Exception e){h.assertTrue(String.valueOf(e.getMessage()).contains(code),"expected "+code+" but got "+e.getMessage());return;}
        throw new AssertionError("Expected rejection "+code);
    }
    private static final String A="apocalypse_firstlight:";
    private static JsonObject place(String id,BlockPos pos,String facing){var a=object("block_id",id,"pos",xyz(pos));if(facing!=null)a.addProperty("facing",facing);return a;}
    private static JsonObject multi(String id,BlockPos anchor,String facing){return object("block_id",id,"anchor",xyz(anchor),"facing",facing);}
    private static int count(JsonObject audit,String issue){var c=audit.getAsJsonObject("counts");return c.has(issue)?c.get(issue).getAsInt():0;}

    private static void run(GameTestHelper h){try{
        var level=h.getLevel();var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"bridge_fixture_test"));
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.setPos(0,200,0);BuildingAuthoringConfig.ENABLED.set(true);
        for(int x=32;x<=64;x+=16)for(int z=0;z<=32;z+=16)level.getChunkAt(new BlockPos(x,200,z));
        var router=new BridgeRouter();
        router.call("authoring_create",object("building_id","mcp_fixture_test","width",16,"height",16,"depth",16),p);
        var o=BuildingAuthoringCommands.active(p.createCommandSourceStack()).origin;
        for(var pos:BlockPos.betweenClosed(o,o.offset(15,0,15)))level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);

        // Registry contract, description and catalog come from Java.
        var problems=AuthoringFixtureRegistry.problems();h.assertTrue(problems.isEmpty(),"fixture registry problems: "+problems);
        var cooler=router.call("describe_block",object("block_id",A+"beverage_cooler"),p);
        h.assertTrue(cooler.get("multiblock").getAsBoolean()&&cooler.getAsJsonArray("parts").size()==4&&cooler.get("master_part").getAsString().equals("lower_left")
                &&cooler.get("authoring_allowed").getAsBoolean()&&cooler.get("has_block_entity").getAsBoolean(),"cooler description");
        h.assertTrue(router.call("describe_block",object("block_id","minecraft:stone"),p).get("authoring_class").getAsString().equals("GENERIC_BLOCK"),"ordinary block description");
        h.assertTrue(!router.call("describe_block",object("block_id",A+"crusher"),p).get("authoring_allowed").getAsBoolean(),"machine not allowed");
        h.assertTrue(router.call("describe_block",object("block_id","minecraft:furnace"),p).get("authoring_class").getAsString().equals("UNSAFE"),"unregistered BE unsafe");
        var retail=router.call("list_authoring_fixtures",object("category","retail"),p).toString();
        h.assertTrue(retail.contains(A+"beverage_cooler")&&retail.contains(A+"retail_shelf_single")&&!retail.contains(A+"crusher"),"retail catalog");
        h.assertTrue(!router.call("list_authoring_fixtures",object("multiblock_only",true),p).toString().contains(A+"cash_register"),"multiblock filter");

        // 1/3: whitelisted inventory fixture, created empty; dry run changes nothing.
        var chestPos=o.offset(1,1,1);
        var dry=place(A+"lead_chest",o.offset(2,1,1),"north");dry.addProperty("dry_run",true);router.call("place_fixture",dry,p);
        h.assertTrue(level.getBlockState(o.offset(2,1,1)).isAir(),"fixture dry run");
        var placed=router.call("place_fixture",place(A+"lead_chest",chestPos,"north"),p);
        h.assertTrue(level.getBlockState(chestPos).is(com.antaurora.apofirstlight.registry.AflBlocks.LEAD_CHEST.get())&&level.getBlockEntity(chestPos)!=null,"lead chest with BlockEntity");
        h.assertTrue(placed.get("block_entity_created").getAsBoolean()&&placed.get("empty_inventory_confirmed").getAsBoolean(),"placement report");
        // 2/10/11: policy and validation rejections.
        expect(h,()->router.call("place_fixture",place(A+"crusher",o.offset(3,1,1),"north"),p),"FIXTURE_NOT_ALLOWED");
        expect(h,()->router.call("place_fixture",place("minecraft:furnace",o.offset(3,1,1),"north"),p),"FIXTURE_NOT_REGISTERED");
        expect(h,()->router.call("place_fixture",place(A+"cash_register",o.offset(3,1,1),"up"),p),"INVALID_FACING");
        var badHinge=multi(A+"restroom_stall_door",o.offset(3,1,1),"north");badHinge.add("properties",object("hinge","middle"));
        expect(h,()->router.call("place_multiblock",badHinge,p),"INVALID_PROPERTY_VALUE");
        var openDoor=multi(A+"beverage_cooler",o.offset(3,1,1),"north");openDoor.add("properties",object("left_open","true"));
        expect(h,()->router.call("place_multiblock",openDoor,p),"PROPERTY_NOT_ALLOWED");
        var raw=place(A+"lead_chest",o.offset(3,1,1),"north");raw.addProperty("nbt","{Items:[]}");
        expect(h,()->router.call("place_fixture",raw,p),"UNSUPPORTED_ARGUMENT");
        expect(h,()->router.call("place_fixture",place(A+"beverage_cooler",o.offset(3,1,1),"north"),p),"MULTIBLOCK_REQUIRES_PLACE_MULTIBLOCK");
        // 5: single fixture undo/redo removes and recreates the BlockEntity.
        router.call("we_undo",object(),p);h.assertTrue(level.getBlockState(chestPos).isAir()&&level.getBlockEntity(chestPos)==null,"fixture undo removes BlockEntity");
        router.call("we_redo",object(),p);var chest=level.getBlockEntity(chestPos);
        h.assertTrue(chest!=null&&AuthoringRegionGuard.inventoryProblem(chest)==null,"fixture redo recreates empty BlockEntity");
        // 4: a nonempty inventory blocks history and audits as unsafe.
        ((Container)chest).setItem(0,new ItemStack(Items.APPLE));
        expect(h,()->router.call("we_undo",object(),p),"UNSAFE_BLOCK_ENTITY_PRESENT");
        h.assertTrue(count(router.call("audit_support",object(),p),"NONEMPTY_INVENTORY")>=1,"audit nonempty inventory");
        if(BridgeRouter.hasWorldEdit())expect(h,()->router.call("we_set",object("min",xyz(chestPos),"max",xyz(chestPos),"block","minecraft:air"),p),"UNSAFE_BLOCK_ENTITY_PRESENT");
        ((Container)chest).setItem(0,ItemStack.EMPTY);router.call("we_undo",object(),p);h.assertTrue(level.getBlockState(chestPos).isAir(),"empty fixture undo");

        // 6/8: complete multiblock, BlockEntity only on the master, one undo step.
        var anchor=o.offset(4,1,4);router.call("place_multiblock",multi(A+"beverage_cooler",anchor,"south"),p);
        var parts=Map.of("lower_left",anchor,"lower_right",anchor.east(),"upper_left",anchor.above(),"upper_right",anchor.east().above());
        for(var e:parts.entrySet()){
            BlockState s=level.getBlockState(e.getValue());
            h.assertTrue(s.is(com.antaurora.apofirstlight.registry.AflBlocks.BEVERAGE_COOLER.get())&&AuthoringFixtureRegistry.value(s,"part").equals(e.getKey())
                    &&AuthoringFixtureRegistry.value(s,"facing").equals("south"),"cooler part "+e.getKey());
            h.assertTrue((level.getBlockEntity(e.getValue())!=null)==e.getKey().equals("lower_left"),"BlockEntity owner "+e.getKey());
        }
        router.call("we_undo",object(),p);
        for(var pos:parts.values())h.assertTrue(level.getBlockState(pos).isAir()&&level.getBlockEntity(pos)==null,"multiblock undo leaves no orphan BlockEntity");
        router.call("we_redo",object(),p);
        h.assertTrue(level.getBlockEntity(anchor)!=null&&level.getBlockState(anchor.east().above()).is(com.antaurora.apofirstlight.registry.AflBlocks.BEVERAGE_COOLER.get()),"multiblock redo");
        // 7: occupied part or missing floor fails before any write.
        var second=o.offset(8,1,4);level.setBlock(second.east().above(),Blocks.STONE.defaultBlockState(),2);
        expect(h,()->router.call("place_multiblock",multi(A+"beverage_cooler",second,"south"),p),"TARGET_OCCUPIED");
        h.assertTrue(level.getBlockState(second).isAir()&&level.getBlockState(second.east()).isAir()&&level.getBlockState(second.above()).isAir(),"atomic multiblock failure");
        level.setBlock(second.east().above(),Blocks.AIR.defaultBlockState(),2);level.setBlock(second.east().below(),Blocks.AIR.defaultBlockState(),2);
        expect(h,()->router.call("place_multiblock",multi(A+"beverage_cooler",second,"south"),p),"SUPPORT_MISSING");
        h.assertTrue(level.getBlockState(second).isAir(),"unsupported multiblock not placed");level.setBlock(second.east().below(),Blocks.STONE.defaultBlockState(),2);

        // 9: orphan detection. 12: support audit.
        var orphan=o.offset(12,5,12);
        level.setBlock(orphan,com.antaurora.apofirstlight.registry.AflBlocks.BEVERAGE_COOLER.get().defaultBlockState().setValue(com.antaurora.apofirstlight.block.BeverageCoolerBlock.PART,com.antaurora.apofirstlight.block.BeverageCoolerBlock.Part.UPPER_RIGHT),BridgeHistory.CONTROLLED_FLAGS);
        h.assertTrue(count(router.call("audit_support",object(),p),"ORPHAN_PART")>=1,"orphan part detected");
        level.setBlock(orphan,Blocks.AIR.defaultBlockState(),BridgeHistory.CONTROLLED_FLAGS);
        var light=o.offset(12,4,2);level.setBlock(light.above(),Blocks.STONE.defaultBlockState(),2);
        router.call("place_fixture",place(A+"industrial_utility_light",light,"down"),p);
        level.setBlock(light.above(),Blocks.AIR.defaultBlockState(),BridgeHistory.CONTROLLED_FLAGS);
        var lightAudit=router.call("audit_support",object("min",xyz(light),"max",xyz(light)),p);
        h.assertTrue(count(lightAudit,"UNSUPPORTED")==1,"unsupported ceiling light reported: "+lightAudit);
        level.setBlock(light.above(),Blocks.STONE.defaultBlockState(),2);

        // 13: connection reconciliation, undoable, plus neighbour door_support on partitions.
        var row=List.of(o.offset(1,1,10),o.offset(2,1,10),o.offset(3,1,10));
        for(var pos:row)level.setBlock(pos,Blocks.GLASS_PANE.defaultBlockState(),BridgeHistory.CONTROLLED_FLAGS);
        router.call("reconcile_shapes",object("min",xyz(row.get(0)),"max",xyz(row.get(2))),p);
        var middle=level.getBlockState(row.get(1));
        h.assertTrue(AuthoringFixtureRegistry.value(middle,"east").equals("true")&&AuthoringFixtureRegistry.value(middle,"west").equals("true"),"pane reconciled");
        router.call("we_undo",object(),p);h.assertTrue(level.getBlockState(row.get(1)).equals(Blocks.GLASS_PANE.defaultBlockState()),"reconcile undo");
        router.call("we_redo",object(),p);
        router.call("place_fixture",place(A+"restroom_partition",o.offset(6,1,10),null),p);
        router.call("place_fixture",place(A+"restroom_partition",o.offset(8,1,10),null),p);
        var stall=multi(A+"restroom_stall_door",o.offset(7,1,10),"north");stall.add("properties",object("hinge","left"));
        router.call("place_multiblock",stall,p);
        h.assertTrue(AuthoringFixtureRegistry.value(level.getBlockState(o.offset(6,1,10)),"door_support").equals("4")
                &&AuthoringFixtureRegistry.value(level.getBlockState(o.offset(8,1,10)),"door_support").equals("8"),"partitions reconciled to stall door");

        if(BridgeRouter.hasWorldEdit()){
            // 14: ordinary AFL non-BE blocks unchanged. 15: generic WorldEdit never writes block entities or multiblock parts.
            var concrete=o.offset(14,1,14);router.call("we_set",object("min",xyz(concrete),"max",xyz(concrete),"block",A+"reinforced_concrete"),p);
            h.assertTrue(level.getBlockState(concrete).is(com.antaurora.apofirstlight.registry.AflBlocks.REINFORCED_CONCRETE.get()),"ordinary AFL block");
            var probe=object("min",xyz(o.offset(14,1,12)),"max",xyz(o.offset(14,1,12)),"dry_run",true);
            probe.addProperty("block","minecraft:chest");expect(h,()->router.call("we_set",probe,p),"UNSAFE_OR_DYNAMIC_BLOCK");
            probe.addProperty("block",A+"beverage_cooler[facing=north,part=lower_left,left_open=false,right_open=false]");expect(h,()->router.call("we_set",probe,p),"UNSAFE_OR_DYNAMIC_BLOCK");
            probe.addProperty("block",A+"water_dispenser[facing=north,half=lower]");expect(h,()->router.call("we_set",probe,p),"UNSAFE_OR_DYNAMIC_BLOCK");   // V2 (2026-10-01) has a block entity
            probe.addProperty("block",A+"steel_door[facing=north,half=lower]");expect(h,()->router.call("we_set",probe,p),"UNSAFE_OR_DYNAMIC_BLOCK");   // Steel-frame doors V1 (2026-10-07) have a block entity
            probe.addProperty("block",A+"office_multifunction_printer[facing=north,half=lower]");expect(h,()->router.call("we_set",probe,p),"MULTIBLOCK_REQUIRES_PLACE_MULTIBLOCK");
            // Whitelisted empty fixtures may be copied/removed as whole multiblocks, never split.
            expect(h,()->router.call("we_set",object("min",xyz(anchor),"max",xyz(anchor.above()),"block","minecraft:air"),p),"MULTIBLOCK_SPLIT");
            router.call("we_copy",object("min",xyz(anchor),"max",xyz(anchor.east().above())),p);
            router.call("we_set",object("min",xyz(anchor),"max",xyz(anchor.east().above()),"block","minecraft:air"),p);
            for(var pos:parts.values())h.assertTrue(level.getBlockState(pos).isAir()&&level.getBlockEntity(pos)==null,"whole multiblock removed");
            router.call("we_undo",object(),p);
            h.assertTrue(level.getBlockEntity(anchor)!=null&&count(router.call("audit_support",object("min",xyz(anchor),"max",xyz(anchor.east().above())),p),"ORPHAN_PART")==0,"WorldEdit undo restores complete multiblock");
        }
        router.call("authoring_cancel",object(),p);
        com.mojang.logging.LogUtils.getLogger().info("[AFL BRIDGE TEST] PASS fixtures: registry, describe/list, whitelisted BE placement, empty inventories, multiblock atomicity, shared undo/redo, orphan/support audit, reconciliation, WorldEdit BE/multiblock guards");
        h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}finally{BuildingAuthoringConfig.ENABLED.set(false);}}
}
