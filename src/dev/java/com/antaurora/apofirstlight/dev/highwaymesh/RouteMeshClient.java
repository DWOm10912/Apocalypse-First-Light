package com.antaurora.apofirstlight.dev.highwaymesh;

import com.antaurora.apofirstlight.client.mesh.AflMeshRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import java.util.*;
import static com.antaurora.apofirstlight.dev.highwaymesh.ChunkMeshGeometry.Tile;

/** A bounded development scene. CPU tile cache follows real client chunk residency; no per-tile resource IDs. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT)
public final class RouteMeshClient {
    private static final ResourceLocation TEXTURE=new ResourceLocation("afl_highway_demo","prototype/placeholder.png");
    private static final RenderType TYPE=RenderType.entityCutoutNoCull(TEXTURE);
    private static RouteMeshNetwork.Snapshot scene;
    private static RoadMeshAsset asset;
    // At most one pure CPU job; new descriptors replace the pending scene instead of queuing jobs.
    private static java.util.concurrent.CompletableFuture<RoadMeshAsset> building;
    private static UUID buildingId;
    private static final Map<Tile,RoadMeshAsset.BakedTile> CACHE=new TreeMap<>();
    private static boolean failed,reportPending;
    private static long bakeNanos; private static int bakedTotal,evictedTotal,drawnTiles,submittedVertices,submittedTriangles,batches;
    public static void receive(RouteMeshNetwork.Snapshot next){
        if(FMLEnvironment.production)return;
        boolean same=scene!=null&&scene.instance().equals(next.instance())&&scene.origin().equals(next.origin())&&scene.dimension().equals(next.dimension())&&scene.version().equals(next.version());
        if(!same)clearAssets();scene=next.enabled()?next:null;reportPending=next.report();
        if(scene==null){clearAssets();if(next.report())message("No scene; cache=0. M1-B preview has no collision and is never persisted.");}
    }
    private static boolean active(){var level=Minecraft.getInstance().level;return scene!=null&&level!=null&&level.dimension().location().equals(scene.dimension());}
    private static boolean loaded(Tile t){var level=Minecraft.getInstance().level;return level!=null&&level.getChunkSource().hasChunk((scene.origin().getX()>>4)+t.x(),(scene.origin().getZ()>>4)+t.z());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||FMLEnvironment.production)return;
        if(!active()||failed){if(building!=null&&building.isDone()){building=null;buildingId=null;}return;}
        try {
            if(asset==null){
                if(building!=null&&building.isDone()){
                    if(scene.instance().equals(buildingId))asset=building.join();
                    building=null;buildingId=null;
                }
                if(asset==null&&building==null){
                    var requested=scene;
                    if(!RouteMeshNetwork.digest(requested.recipe()).equals(requested.version()))throw new IllegalArgumentException("RECIPE_DIGEST_MISMATCH");
                    buildingId=requested.instance();
                    building=java.util.concurrent.CompletableFuture.supplyAsync(()->new RouteRoadGeometry(RouteRoadCodec.decode(requested.recipe())).asset(requested.version()));
                }
                if(asset==null)return;
            }
            int before=CACHE.size();CACHE.keySet().removeIf(t->!loaded(t));evictedTotal+=before-CACHE.size();
            int remaining=2; // bounded work per tick, never build models inside the render callback
            for(var t:asset.tiles.keySet())if(loaded(t)&&!CACHE.containsKey(t)){
                long begin=System.nanoTime();CACHE.put(t,asset.bake(t));bakeNanos+=System.nanoTime()-begin;bakedTotal++;if(--remaining==0)break;
            }
            boolean ready=asset.tiles.keySet().stream().filter(RouteMeshClient::loaded).allMatch(CACHE::containsKey);
            if(reportPending&&ready){reportPending=false;report();}
        }catch(Exception e){failed=true;CACHE.clear();message("FAILED: "+e.getMessage()+"; no mesh rendered. Preview stopped; no blocks or saved data were written.");}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES||FMLEnvironment.production||!active()||failed||asset==null)return;
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        var metrics=new AflMeshRenderer.Metrics();Map<Long,Integer> lights=new HashMap<>();drawnTiles=0;
        for(var e:CACHE.entrySet()){
            if(!loaded(e.getKey()))continue; // unload takes effect immediately, before next cache prune
            var b=e.getValue();Vec3 origin=new Vec3(scene.origin().getX()+e.getKey().x()*16,scene.origin().getY(),scene.origin().getZ()+e.getKey().z()*16);
            AABB box=new AABB(origin.x,origin.y+b.minY(),origin.z,origin.x+16,origin.y+b.maxY()+.01,origin.z+16);
            if(!event.getFrustum().isVisible(box))continue;
            WorldChunkMeshRenderer.render(b.model().parts("road"),origin,camera,event.getPoseStack(),buffers.getBuffer(TYPE),mc.level,lights,metrics,true,b.sourceNormals());drawnTiles++;
        }
        batches=drawnTiles>0?1:0;if(batches!=0)buffers.endBatch(TYPE);
        submittedVertices=metrics.vertices;submittedTriangles=metrics.triangles;
    }
    private static void report(){
        long floats=CACHE.values().stream().mapToLong(t->(long)t.corners()*8*4).sum();
        message(String.format(Locale.ROOT,"%s asset=%s tiles=%d cached=%d drawn(last frame)=%d source vertices=%d triangles=%d; submit vertices=%d triangles=%d batches=%d (not GPU draw calls); corner float bytes=%d (not total heap); geometry clip/collision=%.1fms tile bake cumulative=%.1fms baked=%d evicted=%d",
                "PREVIEW / NO COLLISION",asset.version.substring(0,12),asset.tiles.size(),CACHE.size(),drawnTiles,asset.sourceStoredVertices,asset.sourceTriangleEquivalent,submittedVertices,submittedTriangles,batches,floats,asset.loadNanos/1e6,bakeNanos/1e6,bakedTotal,evictedTotal));
    }
    private static void message(String text){var p=Minecraft.getInstance().player;if(p!=null)p.displayClientMessage(Component.literal("Highway M1-B client: "+text),false);}
    private static void clearAssets(){CACHE.clear();asset=null;failed=false;bakeNanos=0;bakedTotal=evictedTotal=drawnTiles=submittedVertices=submittedTriangles=batches=0;}
    private static void clear(){scene=null;reportPending=false;clearAssets();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel().isClientSide())clear();}
    @Mod.EventBusSubscriber(modid="apocalypse_firstlight",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event){if(FMLEnvironment.production)return;
            event.registerReloadListener(new SimplePreparableReloadListener<Boolean>(){
                @Override protected Boolean prepare(ResourceManager manager,ProfilerFiller profiler){return true;}
                @Override protected void apply(Boolean ignored,ResourceManager manager,ProfilerFiller profiler){clearAssets();reportPending=scene!=null;}
            });
        }
    }
    private RouteMeshClient(){}
}
