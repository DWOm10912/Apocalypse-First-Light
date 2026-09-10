package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.*;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/** Server-side safety and render-handoff math. This is NOT graphical or live HTTP acceptance. */
@GameTestHolder("apocalypse_firstlight") @PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class CameraGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){return List.of(
            new TestFunction("camera","afl_camera_tests:safety","afl_camera_tests:empty",200,0L,true,CameraGameTests::run),
            new TestFunction("camera","afl_camera_tests:frame_gate","afl_camera_tests:empty",100,0L,true,CameraGameTests::frameGate));}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void load(net.minecraftforge.event.level.LevelEvent.Load e){
        if(e.getLevel() instanceof ServerLevel l)l.getStructureManager().getOrCreate(new net.minecraft.resources.ResourceLocation("afl_camera_tests","empty"))
                .fillFromWorld(l,new BlockPos(0,300,0),new Vec3i(1,1,1),false,Blocks.STRUCTURE_VOID);
    }
    private interface Checked{void run()throws Exception;}
    private static void reject(GameTestHelper h,String error,Checked r){
        try{r.run();throw new AssertionError("Expected "+error);}
        catch(RuntimeException e){h.assertTrue(e.getMessage()!=null&&e.getMessage().contains(error),"Expected "+error+", got "+e);}
        catch(Exception e){throw new RuntimeException(e);}
    }
    private static JsonObject move(double x,double y,double z){return object("position",new double[]{x,y,z},"yaw",-180,"pitch",25);}
    private static void run(GameTestHelper h){
        var level=h.getLevel();
        var p=net.minecraftforge.common.util.FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"camera_test"));
        // Forge's default FakePlayerNetHandler no-ops teleport(). Keep the actual Vanilla
        // server teleport implementation, stubbing only outbound packets (no graphical client).
        p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),p){
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}
        };
        var router=new BridgeRouter();boolean enabled=BuildingAuthoringConfig.ENABLED.get();
        try{
            BuildingAuthoringConfig.ENABLED.set(true);p.setGameMode(GameType.CREATIVE);p.setPos(.5,200,.5);p.setYRot(37);p.setXRot(-12);
            p.getAbilities().flying=false;
            reject(h,"NO_ACTIVE_AUTHORING_SESSION",()->router.call("camera_move",move(40.5,205,8.5),p));
            for(int x=0;x<=64;x+=16)for(int z=0;z<=32;z+=16)level.getChunkAt(new BlockPos(x,200,z));
            router.call("authoring_create",object("building_id","camera_test","width",16,"height",16,"depth",16),p);
            var s=BuildingAuthoringCommands.active(p.createCommandSourceStack());var before=AuthoringCamera.pose(p);
            var preview=move(40.5,205,8.5);preview.addProperty("dry_run",true);
            router.call("camera_move",preview,p);
            h.assertTrue(AuthoringCamera.pose(p).equals(before)&&!p.getAbilities().flying,"dry run does not move/rotate/fly");
            h.assertTrue(!router.call("camera_status",object(),p).get("return_available").getAsBoolean()&&router.camera.frames.ready(),"dry run no anchor/frame wait");
            reject(h,"CAMERA_OUTSIDE_PLOT_MARGIN",()->router.call("camera_move",move(113,205,8.5),p));
            reject(h,"CAMERA_OUTSIDE_PLOT_MARGIN",()->router.call("camera_move",move(40.5,183,8.5),p));
            reject(h,"CAMERA_OUTSIDE_PLOT_MARGIN",()->router.call("camera_move",move(40.5,265,8.5),p));
            reject(h,"CAMERA_ANGLE_RANGE",()->router.call("camera_move",object("position",new int[]{40,205,8},"yaw",181,"pitch",0),p));
            reject(h,"CAMERA_ANGLE_RANGE",()->router.call("camera_move",object("position",new int[]{40,205,8},"yaw",0,"pitch",-91),p));
            reject(h,"CAMERA_FINITE_NUMBER_REQUIRED",()->router.call("camera_move",object("position",new int[]{40,205,8},"yaw","0","pitch",0),p));
            var infinity=move(40,205,8);infinity.getAsJsonArray("position").set(0,new com.google.gson.JsonPrimitive(Double.POSITIVE_INFINITY));
            reject(h,"CAMERA_FINITE_NUMBER_REQUIRED",()->router.call("camera_move",infinity,p));
            var nan=move(40,205,8);nan.addProperty("pitch",Double.NaN);
            reject(h,"CAMERA_FINITE_NUMBER_REQUIRED",()->router.call("camera_move",nan,p));
            reject(h,"CAMERA_POSITION_REQUIRED",()->router.call("camera_move",object("position",new int[]{1,2},"yaw",0,"pitch",0),p));
            var arbitrary=move(40,205,8);arbitrary.addProperty("command","gamemode spectator");
            reject(h,"CAMERA_UNKNOWN_ARGUMENT",()->router.call("camera_move",arbitrary,p));
            var badDry=move(40,205,8);badDry.addProperty("dry_run","false");
            reject(h,"CAMERA_INVALID_DRY_RUN",()->router.call("camera_move",badDry,p));
            reject(h,"CAMERA_NO_RETURN_POINT",()->router.call("camera_restore",object(),p));
            var obstacle=new BlockPos(40,205,8);level.setBlock(obstacle,Blocks.STONE.defaultBlockState(),2);
            reject(h,"CAMERA_DESTINATION_OBSTRUCTED",()->router.call("camera_move",move(40.5,205,8.5),p));
            h.assertTrue(level.getBlockState(obstacle).is(Blocks.STONE)&&AuthoringCamera.pose(p).equals(before),"rejection preserves player and block");
            level.setBlock(obstacle,Blocks.WATER.defaultBlockState(),2);
            reject(h,"CAMERA_DESTINATION_OBSTRUCTED",()->router.call("camera_move",move(40.5,205,8.5),p));
            level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
            level.setBlock(obstacle.below(),Blocks.MAGMA_BLOCK.defaultBlockState(),2);
            reject(h,"CAMERA_UNSAFE_DESTINATION",()->router.call("camera_move",move(40.5,205,8.5),p));
            level.setBlock(obstacle.below(),Blocks.AIR.defaultBlockState(),2);
            var entity=new net.minecraft.world.entity.item.ItemEntity(level,40.5,205,8.5,new net.minecraft.world.item.ItemStack(Blocks.STONE));
            level.addFreshEntity(entity);
            reject(h,"CAMERA_ENTITY_AT_DESTINATION",()->router.call("camera_move",move(40.5,205,8.5),p));entity.discard();
            p.setPos(-260,205,8.5);
            reject(h,"CAMERA_TRAVEL_LIMIT_256",()->router.call("camera_move",move(40.5,205,8.5),p));p.setPos(.5,200,.5);
            reject(h,"CAMERA_WORLD_BOUNDS",()->AuthoringCamera.checkDestination(p,new CameraFrameGate.Pose(40,321,8,0,0)));
            var border=level.getWorldBorder();double borderSize=border.getSize();border.setSize(16);
            try{reject(h,"CAMERA_WORLD_BOUNDS",()->router.call("camera_move",move(40.5,205,8.5),p));}finally{border.setSize(borderSize);}
            // A far-away ungenerated region. Direct checker isolates loaded-chunk guard from plot guard.
            p.setPos(20000.5,200,20000.5);var far=new BlockPos(20016,205,20016);
            h.assertTrue(!level.hasChunk(far.getX()>>4,far.getZ()>>4),"test destination starts unloaded");
            reject(h,"CAMERA_CHUNK_NOT_LOADED",()->AuthoringCamera.checkDestination(p,new CameraFrameGate.Pose(far.getX()+.5,205,far.getZ()+.5,0,0)));
            h.assertTrue(!level.hasChunk(far.getX()>>4,far.getZ()>>4),"rejected move did not load chunk");p.setPos(.5,200,.5);
            router.call("camera_move",move(40.5,205,8.5),p);
            h.assertTrue(p.getX()==40.5&&p.getY()==205&&p.getZ()==8.5&&p.getYRot()==-180&&p.getXRot()==25,"move sets exact pose");
            h.assertTrue(p.isCreative()&&p.getAbilities().flying&&!router.camera.frames.ready(),"flight enabled, gamemode unchanged, needs client frames");
            router.call("camera_move",move(44.5,208,8.5),p);
            var state=router.call("camera_status",object(),p);
            h.assertTrue(state.getAsJsonArray("return_position").get(0).getAsDouble()==.5,"second move preserves first anchor");
            var returnBlock=new BlockPos(0,200,0);level.setBlock(returnBlock,Blocks.STONE.defaultBlockState(),2);
            reject(h,"CAMERA_DESTINATION_OBSTRUCTED",()->router.call("camera_restore",object(),p));
            h.assertTrue(router.call("camera_status",object(),p).get("return_available").getAsBoolean(),"obstructed return retains anchor");
            level.setBlock(returnBlock,Blocks.AIR.defaultBlockState(),2);
            router.call("camera_restore",object("dry_run",true),p);h.assertTrue(p.getX()==44.5,"restore preview unchanged");
            router.call("camera_restore",object(),p);
            h.assertTrue(AuthoringCamera.pose(p).equals(before)&&!p.getAbilities().flying,"restore original pose and flight");
            reject(h,"CAMERA_NO_RETURN_POINT",()->router.call("camera_restore",object(),p));
            p.setGameMode(GameType.SURVIVAL);
            reject(h,"AUTHORING_DISABLED_OR_PERMISSION_DENIED",()->router.call("camera_move",move(40,205,8),p));p.setGameMode(GameType.CREATIVE);
            p.getAbilities().mayfly=false;
            reject(h,"CAMERA_CREATIVE_REQUIRED",()->router.call("camera_move",move(40,205,8),p));p.getAbilities().mayfly=true;
            p.containerMenu=net.minecraft.world.inventory.ChestMenu.threeRows(1,p.getInventory());
            reject(h,"CAMERA_PLAYER_BUSY",()->router.call("camera_move",move(40,205,8),p));p.containerMenu=p.inventoryMenu;
            var boat=new net.minecraft.world.entity.vehicle.Boat(level,.5,200,.5);level.addFreshEntity(boat);p.startRiding(boat,true);
            reject(h,"CAMERA_PLAYER_BUSY",()->router.call("camera_move",move(40,205,8),p));p.stopRiding();boat.discard();
            p.getAbilities().flying=true;router.call("camera_move",move(40.5,205,8.5),p);router.call("camera_restore",object(),p);
            h.assertTrue(p.getAbilities().flying,"original flying=true restored");
            router.call("camera_move",move(40.5,205,8.5),p);
            router.call("authoring_cancel",object(),p);h.assertTrue(router.camera.frames.ready(),"cancel invalidates pending frames");
            // Same coordinates and ID still create a new reservation; it must not inherit the anchor.
            p.setPos(.5,200,.5);router.call("authoring_create",object("building_id","camera_test","width",16,"height",16,"depth",16),p);
            reject(h,"CAMERA_NO_RETURN_POINT",()->router.call("camera_restore",object(),p));
            var other=level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);p.setServerLevel(other);
            try{reject(h,"DIMENSION_MISMATCH",()->router.call("camera_move",move(40,205,8),p));}
            finally{p.setServerLevel(level);}
            BuildingAuthoringConfig.ENABLED.set(false);
            reject(h,"AUTHORING_DISABLED_OR_PERMISSION_DENIED",()->router.call("camera_move",move(40,205,8),p));BuildingAuthoringConfig.ENABLED.set(true);
            h.assertTrue(s.origin.equals(new BlockPos(32,200,0))&&level.getBlockState(obstacle).isAir(),"camera never edits plot");
            com.mojang.logging.LogUtils.getLogger().info("[AFL CAMERA TEST] PASS safety, strict input, collision/liquid/entity, no chunk loading, move/restore, anchor and flight lifecycle");
            h.succeed();
        }catch(Exception e){throw new RuntimeException(e);}
        finally{
            BuildingAuthoringConfig.ENABLED.set(true);p.setGameMode(GameType.CREATIVE);
            try{router.call("authoring_cancel",object(),p);}catch(Exception ignored){}
            BuildingAuthoringConfig.ENABLED.set(enabled);
        }
    }
    private static void frameGate(GameTestHelper h){
        var gate=new CameraFrameGate();var target=new CameraFrameGate.Pose(1,2,3,-180,25);
        h.assertTrue(gate.ready(),"idle ready");gate.arm(target);
        h.assertTrue(!gate.ready(),"move not acknowledged by frame yet");gate.observe(target);
        h.assertTrue(!gate.ready(),"one frame insufficient");gate.observe(new CameraFrameGate.Pose(1.1,2,3,-180,25));
        gate.observe(target);h.assertTrue(!gate.ready(),"mismatch resets consecutive frames");
        gate.observe(new CameraFrameGate.Pose(1,2,3,-7380,25));h.assertTrue(gate.ready(),"wrapped yaw and two matching frames");
        var next=new CameraFrameGate.Pose(1,2,3,-180,25);gate.arm(next);
        h.assertTrue(!gate.ready(),"same pose new move still waits");gate.observe(next);gate.clear();
        h.assertTrue(gate.ready(),"cancel releases pending capture");h.succeed();
    }
}
