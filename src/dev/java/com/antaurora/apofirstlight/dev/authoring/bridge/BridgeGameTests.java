package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

@GameTestHolder("apocalypse_firstlight") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class BridgeGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){return List.of(new TestFunction("bridge","afl_bridge_tests:core","afl_bridge_tests:empty",400,0L,true,BridgeGameTests::run));}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void load(net.minecraftforge.event.level.LevelEvent.Load e){if(e.getLevel() instanceof ServerLevel l)l.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_bridge_tests","empty")).fillFromWorld(l,new BlockPos(0,300,0),new Vec3i(1,1,1),false,Blocks.STRUCTURE_VOID);}
    interface Checked{void run()throws Exception;}
    static void reject(GameTestHelper h,Checked r,String message){try{r.run();throw new AssertionError("Expected rejection: "+message);}catch(AssertionError e){throw e;}catch(Exception expected){h.assertTrue(true,message);}}
    private static void run(GameTestHelper h){try{
        var level=h.getLevel();var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"bridge_test"));p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.setPos(0,200,0);BuildingAuthoringConfig.ENABLED.set(true);
        for(int x=32;x<=64;x+=16)for(int z=0;z<=32;z+=16)level.getChunkAt(new BlockPos(x,200,z));
        var router=new BridgeRouter();reject(h,()->router.call("we_set",object("block","minecraft:stone"),p),"no session");
        router.call("authoring_create",object("building_id","mcp_test_build","width",16,"height",16,"depth",16),p);var s=BuildingAuthoringCommands.active(p.createCommandSourceStack());var o=s.origin;
        var registry=router.call("export_target_registry",new com.google.gson.JsonObject(),p);h.assertTrue(registry.get("block_count").getAsInt()>900,"actual registry");
        if(!BridgeRouter.hasWorldEdit()){reject(h,()->router.call("we_set",object("block","minecraft:stone"),p),"missing WE safe");router.call("authoring_cancel",new com.google.gson.JsonObject(),p);h.succeed();return;}
        WithWorldEdit.run(h,router,p,s,o);
        ReferenceBridgeTests.run(h,router,p);
        router.call("authoring_cancel",object(),p);reject(h,()->router.call("we_undo",object(),p),"history unavailable after cancel");
        com.mojang.logging.LogUtils.getLogger().info("[AFL BRIDGE TEST] PASS WorldEdit API edits, history, conflicts, scope, slices, registry; screenshot/HTTP client acceptance separate");h.succeed();
    }catch(Exception e){throw new RuntimeException(e);}finally{BuildingAuthoringConfig.ENABLED.set(false);}}
    private static final class WithWorldEdit {
      static void run(GameTestHelper h,BridgeRouter router,net.minecraft.server.level.ServerPlayer p,BuildingAuthoringSession s,BlockPos o)throws Exception {
        var level=h.getLevel();
        var operations=new com.google.gson.JsonArray();
        operations.add(object("min",xyz(o),"max",xyz(o),"block","minecraft:stone"));
        operations.add(object("min",xyz(o.east()),"max",xyz(o.east()),"block","minecraft:bricks"));
        var batch=object();batch.add("operations",operations);
        var preview=batch.deepCopy();preview.addProperty("dry_run",true);router.call("we_batch_set",preview,p);h.assertTrue(level.getBlockState(o).isAir(),"batch preview unchanged");
        var invalid=batch.deepCopy();invalid.getAsJsonArray("operations").add(object("min",xyz(o.west()),"max",xyz(o.west()),"block","minecraft:stone"));
        reject(h,()->router.call("we_batch_set",invalid,p),"late invalid batch");h.assertTrue(level.getBlockState(o).isAir(),"batch preflight atomic");
        router.call("we_batch_set",batch,p);h.assertTrue(level.getBlockState(o).is(Blocks.STONE)&&level.getBlockState(o.east()).is(Blocks.BRICKS),"batch applied");
        router.call("we_undo",object(),p);h.assertTrue(level.getBlockState(o).isAir()&&level.getBlockState(o.east()).isAir(),"single batch undo");
        router.call("we_redo",object(),p);h.assertTrue(level.getBlockState(o.east()).is(Blocks.BRICKS),"batch redo");router.call("we_undo",object(),p);
        var floor=object("min",xyz(o),"max",xyz(o.offset(15,0,15)),"block","minecraft:stone");
        var dry=floor.deepCopy();dry.addProperty("dry_run",true);router.call("we_set",dry,p);h.assertTrue(level.getBlockState(o).isAir(),"dry run");
        router.call("we_set",floor,p);int floorCount=0;for(var pos:BlockPos.betweenClosed(o,o.offset(15,0,15)))if(level.getBlockState(pos).is(Blocks.STONE))floorCount++;h.assertTrue(floorCount==256,"actual floor blocks (response counter tracked separately)");
        router.call("we_walls",object("min",xyz(o.above()),"max",xyz(o.offset(15,5,15)),"block","minecraft:bricks"),p);
        router.call("we_replace",object("min",xyz(o.offset(2,2,0)),"max",xyz(o.offset(5,3,0)),"from","minecraft:bricks","block","minecraft:glass"),p);h.assertTrue(level.getBlockState(o.offset(2,2,0)).is(Blocks.GLASS),"window");
        router.call("we_undo",object(),p);h.assertTrue(level.getBlockState(o.offset(2,2,0)).is(Blocks.BRICKS),"undo");router.call("we_redo",object(),p);h.assertTrue(level.getBlockState(o.offset(2,2,0)).is(Blocks.GLASS),"redo");
        level.setBlock(o.offset(2,2,0),Blocks.GOLD_BLOCK.defaultBlockState(),2);reject(h,()->router.call("we_undo",object(),p),"history conflict");level.setBlock(o.offset(2,2,0),Blocks.GLASS.defaultBlockState(),2);
        router.call("we_copy",object("min",xyz(o.offset(2,2,0)),"max",xyz(o.offset(5,3,0))),p);
        router.call("we_paste",object("min",xyz(o.offset(2,2,2)),"max",xyz(o.offset(5,3,2)),"to",xyz(o.offset(2,2,2))),p);h.assertTrue(level.getBlockState(o.offset(2,2,2)).is(Blocks.GLASS),"paste");
        router.call("we_stack",object("min",xyz(o.offset(2,2,2)),"max",xyz(o.offset(5,3,2)),"offset",new int[]{0,0,2},"count",2),p);h.assertTrue(level.getBlockState(o.offset(2,2,6)).is(Blocks.GLASS),"stack");
        router.call("we_move",object("min",xyz(o.offset(2,2,6)),"max",xyz(o.offset(5,3,6)),"offset",new int[]{0,0,1}),p);h.assertTrue(level.getBlockState(o.offset(2,2,7)).is(Blocks.GLASS)&&level.getBlockState(o.offset(2,2,6)).isAir(),"move");
        reject(h,()->router.call("we_set",object("min",xyz(o.west()),"max",xyz(o),"block","minecraft:stone"),p),"scope");
        reject(h,()->router.call("we_set",object("block","minecraft:not_a_block"),p),"unknown block");
        reject(h,()->router.call("we_set",object("block","minecraft:command_block"),p),"dangerous block");
        reject(h,()->router.call("we_copy",object("target","REFERENCE_SELECTION"),p),"reference read only");
        reject(h,()->new BridgeBounds(o,o.offset(127,191,127)).check(level),"volume");
        var actor=com.sk89q.worldedit.forge.ForgeAdapter.adaptPlayer(p);var weWorld=com.sk89q.worldedit.forge.ForgeAdapter.adapt(level);var selector=new com.sk89q.worldedit.regions.selector.CuboidRegionSelector(weWorld,com.sk89q.worldedit.forge.ForgeAdapter.adapt(o),com.sk89q.worldedit.forge.ForgeAdapter.adapt(s.max()));com.sk89q.worldedit.WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(weWorld,selector);
        h.assertTrue(router.call("get_worldedit_selection",object(),p).get("volume").getAsLong()==4096,"selection bounds");
        h.assertTrue(router.call("inspect_selection",object(),p).get("non_air").getAsInt()>256,"inspect");
        h.assertTrue(router.call("get_horizontal_slice",object("coordinate",200),p).getAsJsonArray("rows").size()==16,"horizontal");
        h.assertTrue(router.call("get_vertical_slice",object("axis","X","coordinate",o.getX()),p).getAsJsonArray("rows").size()==16,"vertical");
        router.call("inspect_facade",object("side","SOUTH"),p);
        router.call("authoring_clear",object(),p);h.assertTrue(level.getBlockState(o).isAir(),"clear");router.call("we_undo",object(),p);h.assertTrue(level.getBlockState(o).is(Blocks.STONE),"clear undo");
      }
    }
}
