package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.BuildingAuthoringSession;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.UUID;
import static com.antaurora.apofirstlight.dev.authoring.bridge.BridgeJson.*;

/** Dev bridge view positioning, not an arbitrary teleport/command execution API. Server thread only. */
final class AuthoringCamera {
    static final int HORIZONTAL_MARGIN=64, BELOW_MARGIN=16, ABOVE_MARGIN=48, MAX_TRAVEL=256;
    final CameraFrameGate frames=new CameraFrameGate();
    private BuildingAuthoringSession owner;
    private Saved saved;
    private record Saved(UUID player,CameraFrameGate.Pose pose,boolean flying) {}

    void clear(){owner=null;saved=null;frames.clear();}

    JsonObject call(String tool,JsonObject a,ServerPlayer p)throws Exception {
        BuildingAuthoringSession session;
        try{session=AuthoringAdapter.active(p);}catch(Exception e){clear();throw e;}
        if(owner!=session){clear();owner=session;}
        if(tool.equals("camera_status")){
            if(!a.keySet().isEmpty())throw new IllegalArgumentException("CAMERA_UNKNOWN_ARGUMENT");
            return status(p);
        }
        if(!p.isCreative()||!p.getAbilities().mayfly)throw new IllegalArgumentException("CAMERA_CREATIVE_REQUIRED");
        if(!p.isAlive()||p.isSleeping()||p.isPassenger()||p.isVehicle()||p.containerMenu!=p.inventoryMenu)
            throw new IllegalArgumentException("CAMERA_PLAYER_BUSY");
        boolean restore=tool.equals("camera_restore");
        if(!restore&&!tool.equals("camera_move"))throw new IllegalArgumentException("UNKNOWN_TOOL");
        Set<String> accepted=restore?Set.of("dry_run"):Set.of("position","yaw","pitch","dry_run");
        if(!accepted.containsAll(a.keySet()))throw new IllegalArgumentException("CAMERA_UNKNOWN_ARGUMENT");
        if(a.has("dry_run")&&(!a.get("dry_run").isJsonPrimitive()||!a.getAsJsonPrimitive("dry_run").isBoolean()))throw new IllegalArgumentException("CAMERA_INVALID_DRY_RUN");
        CameraFrameGate.Pose target;
        if(restore){
            if(saved==null||!saved.player.equals(p.getUUID()))throw new IllegalArgumentException("CAMERA_NO_RETURN_POINT");
            target=saved.pose;
        }else{
            if(!a.has("position")||!a.get("position").isJsonArray()||a.getAsJsonArray("position").size()!=3)throw new IllegalArgumentException("CAMERA_POSITION_REQUIRED");
            var v=a.getAsJsonArray("position");double x=number(v.get(0)),y=number(v.get(1)),z=number(v.get(2));
            double yaw=number(a.get("yaw")),pitch=number(a.get("pitch"));
            if(yaw< -180||yaw>180||pitch< -90||pitch>90)throw new IllegalArgumentException("CAMERA_ANGLE_RANGE");
            target=new CameraFrameGate.Pose(x,y,z,(float)yaw,(float)pitch);
            var scope=new AABB(session.origin,session.max().offset(1,1,1));
            if(x<scope.minX-HORIZONTAL_MARGIN||x>scope.maxX+HORIZONTAL_MARGIN||z<scope.minZ-HORIZONTAL_MARGIN||z>scope.maxZ+HORIZONTAL_MARGIN
                    ||y<scope.minY-BELOW_MARGIN||y>scope.maxY+ABOVE_MARGIN)throw new IllegalArgumentException("CAMERA_OUTSIDE_PLOT_MARGIN");
        }
        checkDestination(p,target);
        if(bool(a,"dry_run"))return object("dry_run",true,"position",xyz(target),"yaw",target.yaw(),"pitch",target.pitch(),"blocks_changed",0);
        var original=new Saved(p.getUUID(),pose(p),p.getAbilities().flying);
        // First successful move is the return anchor. Later moves never overwrite it.
        try{teleport(p,target,restore?saved.flying:true);}
        catch(RuntimeException e){if(saved==null)saved=original;throw e;}
        if(restore)saved=null;else if(saved==null)saved=original;
        frames.arm(target);
        var result=status(p);result.addProperty("moved",true);result.addProperty("restored",restore);result.addProperty("blocks_changed",0);
        return result;
    }
    private static double number(JsonElement e){
        if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("CAMERA_FINITE_NUMBER_REQUIRED");
        double n=e.getAsDouble();if(!Double.isFinite(n))throw new IllegalArgumentException("CAMERA_FINITE_NUMBER_REQUIRED");return n;
    }
    static CameraFrameGate.Pose pose(ServerPlayer p){return new CameraFrameGate.Pose(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot());}
    private static double[] xyz(CameraFrameGate.Pose p){return new double[]{p.x(),p.y(),p.z()};}
    private JsonObject status(ServerPlayer p){return object("position",xyz(pose(p)),"yaw",p.getYRot(),"pitch",p.getXRot(),"flying",p.getAbilities().flying,
            "return_available",saved!=null,"return_position",saved==null?null:xyz(saved.pose),"client_frame_ready",frames.ready(),"plot_id",owner.metadata.id());}
    static void checkDestination(ServerPlayer p,CameraFrameGate.Pose target){
        var level=p.serverLevel();var pos=new Vec3(target.x(),target.y(),target.z());
        if(p.position().distanceToSqr(pos)>MAX_TRAVEL*(double)MAX_TRAVEL)throw new IllegalArgumentException("CAMERA_TRAVEL_LIMIT_256");
        var box=p.getBoundingBox().move(pos.subtract(p.position())).inflate(.01);
        if(box.minY<level.getMinBuildHeight()||box.maxY>=level.getMaxBuildHeight()
                ||Math.abs(target.x())>29_999_980||Math.abs(target.z())>29_999_980)throw new IllegalArgumentException("CAMERA_WORLD_BOUNDS");
        var min=BlockPos.containing(box.minX,box.minY,box.minZ);var max=BlockPos.containing(box.maxX,box.maxY,box.maxZ);
        if(!level.getWorldBorder().isWithinBounds(min)||!level.getWorldBorder().isWithinBounds(max))throw new IllegalArgumentException("CAMERA_WORLD_BOUNDS");
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)if(!level.hasChunk(x,z))throw new IllegalArgumentException("CAMERA_CHUNK_NOT_LOADED");
        // Use a tiny positive foot offset so standing on a floor doesn't count as intersecting it.
        var body=new AABB(box.minX,target.y()+.001,box.minZ,box.maxX,box.maxY,box.maxZ);
        if(!level.noCollision(p,body)||level.containsAnyLiquid(body))throw new IllegalArgumentException("CAMERA_DESTINATION_OBSTRUCTED");
        if(!level.getEntities(p,body).isEmpty())throw new IllegalArgumentException("CAMERA_ENTITY_AT_DESTINATION");
        for(var block:BlockPos.betweenClosed(min,max)){
            String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(block).getBlock()).toString();
            if(id.matches(".*(portal|fire|cactus|magma_block|powder_snow|sweet_berry_bush|wither_rose).*$"))throw new IllegalArgumentException("CAMERA_UNSAFE_DESTINATION");
        }
    }
    private static void teleport(ServerPlayer p,CameraFrameGate.Pose to,boolean flying){
        // Same-dimension packet teleport after loaded-chunk preflight. ServerPlayer.teleportTo
        // would add a POST_TELEPORT chunk ticket, which this restricted tool must not request.
        p.connection.teleport(to.x(),to.y(),to.z(),to.yaw(),to.pitch(),Set.of());
        p.setYHeadRot(to.yaw());p.setYBodyRot(to.yaw());p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
        p.getAbilities().flying=flying;p.onUpdateAbilities();
    }
}
