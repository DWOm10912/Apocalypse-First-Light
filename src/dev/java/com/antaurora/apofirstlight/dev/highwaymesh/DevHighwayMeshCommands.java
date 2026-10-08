package com.antaurora.apofirstlight.dev.highwaymesh;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit preview -> dry_run -> create. No natural generation, forced chunks, replacement or regeneration. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class DevHighwayMeshCommands {
    private record Pending(UUID player,BlockPos origin,String version,long expires,boolean dry){}
    private static final Map<ServerLevel,Pending> PENDING=new WeakHashMap<>();
    private static RoadMeshAsset asset;
    private static RoadMeshAsset asset() throws IOException {
        if(asset==null)asset=RoadMeshAsset.load(name->{var stream=DevHighwayMeshCommands.class.getClassLoader().getResourceAsStream(RoadMeshAsset.PREFIX+name);
            if(stream==null)throw new IOException("Missing dev resource "+name+"; launch the updated development run");return new InputStreamReader(stream,StandardCharsets.UTF_8);});
        return asset;
    }
    private static boolean allowed(CommandSourceStack s){return !FMLEnvironment.production&&s.hasPermission(2)&&s.getEntity() instanceof ServerPlayer p&&p.isCreative()&&s.getLevel().dimension().equals(Level.OVERWORLD);}
    @SubscribeEvent public static void register(RegisterCommandsEvent event){
        if(FMLEnvironment.production)return;
        var command=Commands.literal("highway_mesh").requires(DevHighwayMeshCommands::allowed);
        for(String action:List.of("preview","dry_run","create"))command.then(Commands.literal(action).then(Commands.argument("origin",BlockPosArgument.blockPos()).executes(c->run(c.getSource(),action,BlockPosArgument.getBlockPos(c,"origin")))));
        for(String action:List.of("status","verify","remove","hide"))command.then(Commands.literal(action).executes(c->run(c.getSource(),action,null)));
        command.then(Commands.literal("sample").then(Commands.argument("x",DoubleArgumentType.doubleArg()).then(Commands.argument("z",DoubleArgumentType.doubleArg()).executes(c->sample(c.getSource(),DoubleArgumentType.getDouble(c,"x"),DoubleArgumentType.getDouble(c,"z"))))));
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(command)));
    }
    private static DevHighwayMeshData read(ServerLevel level){
        var data=level.getDataStorage().get(DevHighwayMeshData::load,DevHighwayMeshData.ID);
        // Vanilla catches deserialization exceptions and returns null. Never treat a rejected/corrupt file as an empty plot.
        var file=DimensionType.getStorageFolder(level.dimension(),level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data").resolve(DevHighwayMeshData.ID+".dat");
        if(data==null&&java.nio.file.Files.exists(file))throw new IllegalStateException("SAVED_DATA_UNREADABLE; refusing to replace the existing ledger");
        return data;
    }
    private static AABB bounds(RoadMeshAsset a,BlockPos o){return new AABB(o.getX()+Math.floor(a.minX)-1,o.getY()+Math.floor(a.minY)-1,o.getZ()+Math.floor(a.minZ)-1,o.getX()+Math.ceil(a.maxX)+1,o.getY()+Math.ceil(a.maxY)+4,o.getZ()+Math.ceil(a.maxZ)+1);}
    private static void origin(ServerLevel level,BlockPos o,RoadMeshAsset a){
        if((o.getX()&15)!=0||(o.getZ()&15)!=0)throw new IllegalArgumentException("ORIGIN_XZ_MUST_BE_MULTIPLES_OF_16");
        AABB box=bounds(a,o);BlockPos low=BlockPos.containing(box.minX,box.minY,box.minZ),high=BlockPos.containing(box.maxX-1,box.maxY-1,box.maxZ-1);
        if(low.getY()<level.getMinBuildHeight()||high.getY()>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(low)||!level.getWorldBorder().isWithinBounds(high))throw new IllegalArgumentException("WORLD_BOUNDS");
    }
    private static void loaded(ServerLevel level,AABB box){
        for(int x=(int)Math.floor(box.minX/16);x<=(int)Math.floor((box.maxX-1)/16);x++)for(int z=(int)Math.floor(box.minZ/16);z<=(int)Math.floor((box.maxZ-1)/16);z++)
            if(!level.getChunkSource().hasChunk(x,z))throw new IllegalArgumentException("UNLOADED_CHUNK "+x+","+z+"; fly above the scene centre and wait; no chunks were forced");
    }
    private static int preflight(ServerLevel level,BlockPos o,RoadMeshAsset a){
        origin(level,o,a);AABB box=bounds(a,o);loaded(level,box);int reads=0;
        if(com.antaurora.apofirstlight.authoring.BuildingAuthoringCommands.overlaps(level.dimension(),box))throw new IllegalArgumentException("AUTHORING_RESERVATION; choose an independent test plot");
        for(var p:BlockPos.betweenClosed((int)box.minX,(int)box.minY,(int)box.minZ,(int)box.maxX-1,(int)box.maxY-1,(int)box.maxZ-1)){
            if(++reads>500000)throw new IllegalArgumentException("SCAN_BUDGET");
            if(!level.getBlockState(p).isAir()||!level.getFluidState(p).isEmpty()||level.getBlockEntity(p)!=null)throw new IllegalArgumentException("NOT_EMPTY "+p.toShortString()+"; refusing every block write");
        }
        if(!level.getEntities(null,box).isEmpty())throw new IllegalArgumentException("ENTITY_IN_ENVELOPE; fly above the road before creating it");
        return reads;
    }
    private static void pending(CommandSourceStack s,BlockPos o,RoadMeshAsset a,boolean dry){
        Pending p=PENDING.get(s.getLevel());if(p==null||!p.player.equals(s.getEntity().getUUID())||!p.origin.equals(o)||!p.version.equals(a.version)||p.expires<s.getLevel().getGameTime()||dry&&!p.dry)
            throw new IllegalArgumentException(dry?"REQUIRES_MATCHING_RECENT_DRY_RUN":"REQUIRES_MATCHING_RECENT_PREVIEW");
    }
    private static int run(CommandSourceStack s,String action,BlockPos o){
        try{
            ServerLevel level=s.getLevel();ServerPlayer player=s.getPlayerOrException();DevHighwayMeshData data=read(level);
            if(action.equals("hide")){PENDING.remove(level);sync(player,data,false);reply(s,"Preview cleared; persisted scene, if any, restored. Use remove to remove owned collision.");return 1;}
            if(action.equals("remove")){remove(s,data);return 1;}
            if(action.equals("status")||action.equals("verify")){
                sync(player,data,true);if(data==null||!data.active){reply(s,"EMPTY; preview is transient and never saved");return 1;}
                int present=0,changed=0,unloaded=0;for(var e:data.owned.entrySet()){if(!level.getChunkSource().hasChunk(e.getKey().getX()>>4,e.getKey().getZ()>>4)){unloaded++;continue;}var state=level.getBlockState(e.getKey());if(state.is(DevHighwayMeshRegistration.block())&&state.getValue(DevRoadSurfaceBlock.LAYERS).equals(e.getValue()))present++;else changed++;}
                reply(s,"phase="+data.phase+" origin="+data.origin.toShortString()+" id="+data.instance+" expected="+data.owned.size()+" present="+present+" changed="+changed+" unloaded="+unloaded+" (no repair); asset="+data.assetVersion);return 1;
            }
            if(data!=null&&data.active)throw new IllegalArgumentException("SCENE_EXISTS; remove it first, never overwrite it");
            RoadMeshAsset a=asset();origin(level,o,a);
            if(action.equals("preview")){
                PENDING.put(level,new Pending(player.getUUID(),o.immutable(),a.version,level.getGameTime()+6000,false));
                DevHighwayMeshNetwork.send(player,new DevHighwayMeshNetwork.Snapshot(level.dimension().location(),true,true,true,o,UUID.randomUUID(),a.version));
                reply(s,"PREVIEW_ONLY / NO_COLLISION / NO_WRITES; bounds="+bounds(a,o)+" columns="+a.collision.size()+"; next dry_run with same origin");return 1;
            }
            pending(s,o,a,action.equals("create"));long begin=System.nanoTime();int reads=preflight(level,o,a);
            if(action.equals("dry_run")){PENDING.put(level,new Pending(player.getUUID(),o.immutable(),a.version,level.getGameTime()+1200,true));reply(s,"DRY_RUN_READY / writes=0 / air reads="+reads+" / ms="+(System.nanoTime()-begin)/1e6+"; create within 60 s; preflight repeats");return 1;}
            data=level.getDataStorage().computeIfAbsent(DevHighwayMeshData::load,DevHighwayMeshData::new,DevHighwayMeshData.ID);data.begin(o,a.version);
            try{
                for(var cell:a.collision){BlockPos pos=o.offset(cell.x(),cell.y(),cell.z());
                    if(!level.getBlockState(pos).isAir()||!level.getFluidState(pos).isEmpty())throw new IllegalStateException("STATE_CHANGED "+pos.toShortString());
                    if(!level.setBlock(pos,DevHighwayMeshRegistration.block().defaultBlockState().setValue(DevRoadSurfaceBlock.LAYERS,cell.layers()),2))throw new IllegalStateException("WRITE_FAILED "+pos.toShortString());
                    data.owned.put(pos,cell.layers());
                }
                data.phase="COMPLETE";
            }catch(Exception e){data.phase="PARTIAL_USE_REMOVE";throw e;}
            finally{data.setDirty();PENDING.remove(level);broadcast(level,data);}
            reply(s,"CREATED / collision="+data.owned.size()+" / ms="+(System.nanoTime()-begin)/1e6+"; axis-aligned steps, not triangle physics; use status and sample");return 1;
        }catch(Exception e){s.sendFailure(Component.literal("Highway M1-A: "+e.getMessage()));return 0;}
    }
    private static void remove(CommandSourceStack s,DevHighwayMeshData data){
        if(data==null||!data.active){PENDING.remove(s.getLevel());broadcast(s.getLevel(),data);reply(s,"EMPTY; nothing removed");return;}
        ServerLevel level=s.getLevel();for(BlockPos p:data.owned.keySet())if(!level.getChunkSource().hasChunk(p.getX()>>4,p.getZ()>>4))throw new IllegalArgumentException("UNLOADED_OWNED_CHUNK; move to centre; removal writes=0");
        int removed=0,preserved=0;
        for(var iterator=data.owned.entrySet().iterator();iterator.hasNext();){var e=iterator.next();var state=level.getBlockState(e.getKey());
            if(state.is(DevHighwayMeshRegistration.block())&&state.getValue(DevRoadSurfaceBlock.LAYERS).equals(e.getValue())&&level.getBlockEntity(e.getKey())==null){
                if(!level.setBlock(e.getKey(),Blocks.AIR.defaultBlockState(),2)){data.phase="PARTIAL_REMOVE";data.setDirty();broadcast(level,data);throw new IllegalStateException("REMOVE_WRITE_FAILED; retry; ledger retained");}removed++;
            }else preserved++;
            iterator.remove();
        }
        data.clear();PENDING.remove(level);broadcast(level,data);reply(s,"REMOVED owned="+removed+" preserved changed/absent="+preserved+"; no terrain or player blocks restored/cleared");
    }
    private static int sample(CommandSourceStack s,double x,double z){try{
        var d=read(s.getLevel());if(d==null||!d.active)throw new IllegalArgumentException("NO_PERSISTED_SCENE");var a=asset();if(!a.version.equals(d.assetVersion))throw new IllegalArgumentException("ASSET_VERSION_MISMATCH");
        if(!s.getLevel().getChunkSource().hasChunk((int)Math.floor(x/16),(int)Math.floor(z/16)))throw new IllegalArgumentException("UNLOADED_QUERY");
        var q=a.sample(x-d.origin.getX(),z-d.origin.getZ());if(q.isEmpty()){reply(s,"OUTSIDE_ROAD");return 0;}var v=q.get();
        reply(s,String.format(Locale.ROOT,"VISUAL_SURFACE height=%.6f normal=[%.6f,%.6f,%.6f] s=%.3f material=%s; piecewise triangle surface, not vehicle dynamics",d.origin.getY()+v.height(),v.nx(),v.ny(),v.nz(),v.station(),v.material()));return 1;
    }catch(Exception e){s.sendFailure(Component.literal("Highway query: "+e.getMessage()));return 0;}}
    private static void reply(CommandSourceStack s,String text){s.sendSuccess(()->Component.literal(text),false);}
    private static void sync(ServerPlayer p,DevHighwayMeshData d,boolean report){DevHighwayMeshNetwork.send(p,new DevHighwayMeshNetwork.Snapshot(p.serverLevel().dimension().location(),d!=null&&d.active&&d.phase.equals("COMPLETE"),false,report,d==null?BlockPos.ZERO:d.origin,d==null?new UUID(0,0):d.instance,d==null?"":d.assetVersion));}
    private static void broadcast(ServerLevel level,DevHighwayMeshData d){for(var player:level.players())sync(player,d,false);}
    private static void syncOnJoin(ServerPlayer p){try{sync(p,read(p.serverLevel()),false);}catch(Exception e){sync(p,null,false);p.sendSystemMessage(Component.literal("Highway M1-A: "+e.getMessage()));}}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(!FMLEnvironment.production&&e.getEntity() instanceof ServerPlayer p)syncOnJoin(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(!FMLEnvironment.production&&e.getEntity() instanceof ServerPlayer p)syncOnJoin(p);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(!FMLEnvironment.production&&e.getEntity() instanceof ServerPlayer p)syncOnJoin(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){PENDING.clear();asset=null;}
    private DevHighwayMeshCommands(){}
}
