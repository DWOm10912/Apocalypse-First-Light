package com.antaurora.apofirstlight.dev.highwaymesh;

import com.antaurora.apofirstlight.worldgen.highway.HighwayRouteGraph;
import com.antaurora.apofirstlight.worldgen.spatial.BoundsXZ;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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

/** One transient, relocated RouteGraph preview per creative OP. No create, clear, force-load or save operation. */
@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class RouteMeshCommands {
    private record Session(RouteRoadGeometry geometry,RouteMeshNetwork.Snapshot snapshot){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static boolean allowed(CommandSourceStack s){return !FMLEnvironment.production&&s.hasPermission(2)&&s.getEntity() instanceof ServerPlayer p&&p.isCreative()&&s.getLevel().dimension().equals(Level.OVERWORLD);}
    @SubscribeEvent public static void register(RegisterCommandsEvent e){
        if(FMLEnvironment.production)return;
        var root=Commands.literal("highway_route_mesh").requires(RouteMeshCommands::allowed);
        root.then(Commands.literal("list").executes(c->list(c.getSource())));
        for(String action:List.of("status","verify","remove"))root.then(Commands.literal(action).executes(c->action(c.getSource(),action)));
        var origin=Commands.argument("origin",BlockPosArgument.blockPos()).executes(c->preview(c.getSource(),
            StringArgumentType.getString(c,"edge"),DoubleArgumentType.getDouble(c,"offset"),IntegerArgumentType.getInteger(c,"length"),
            StringArgumentType.getString(c,"section"),BlockPosArgument.getBlockPos(c,"origin")));
        var section=Commands.argument("section",StringArgumentType.word())
            .suggests((c,b)->SharedSuggestionProvider.suggest(List.of("rural4","suburban6","widen_4_to_6"),b)).then(origin);
        var length=Commands.argument("length",IntegerArgumentType.integer(512,1024)).then(section);
        var offset=Commands.argument("offset",DoubleArgumentType.doubleArg(32)).then(length);
        var edge=Commands.argument("edge",StringArgumentType.string()).suggests((c,b)->SharedSuggestionProvider.suggest(
            HighwayRouteGraph.forSeed(c.getSource().getLevel().getSeed()).edges().stream().map(item->"\"" + item.id() + "\""),b)).then(offset);
        root.then(Commands.literal("preview").then(edge));
        root.then(Commands.literal("sample").then(Commands.argument("x",DoubleArgumentType.doubleArg()).then(Commands.argument("z",DoubleArgumentType.doubleArg())
            .executes(c->sample(c.getSource(),DoubleArgumentType.getDouble(c,"x"),DoubleArgumentType.getDouble(c,"z"))))));
        e.getDispatcher().register(Commands.literal("afl").then(Commands.literal("dev").then(root)));
    }
    private static int list(CommandSourceStack s){
        try{var g=HighwayRouteGraph.forSeed(s.getLevel().getSeed());reply(s,"seed="+g.seed()+" graph="+HighwayRouteGraph.VERSION+" trunks="+g.getNationalTrunks().size()+" crossings="+g.seaCrossings().size()+" reserved="+g.reservedZones().size());
            for(var e:g.edges())reply(s,e.id()+" "+e.routeType()+" "+e.orientation()+" authorityStation=["+e.startStation()+","+e.endStation()+"]; preview offset measured from finite edge start, never crosses reserved edge gaps");
            return 1;
        }catch(Exception ex){return failure(s,ex);}
    }
    private static Map<String,RoadSection> sections() throws IOException {
        var in=RouteMeshCommands.class.getClassLoader().getResourceAsStream("assets/afl_highway_demo/route/sections.json");
        if(in==null)throw new IOException("MISSING_DEV_SECTION_RESOURCE: use this worktree's development run");
        try(var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){return RoadSection.read(reader);}
    }
    private static int preview(CommandSourceStack s,String edge,double start,int length,String section,BlockPos origin){
        try{
            if((origin.getX()&15)!=0||(origin.getZ()&15)!=0)throw new IllegalArgumentException("ORIGIN_XZ_MULTIPLES_OF_16_REQUIRED");
            var configs=sections();RoadSection from=configs.get(section.equals("widen_4_to_6")?"rural4":section),to=configs.get(section.equals("widen_4_to_6")?"suburban6":section);
            if(from==null||to==null)throw new IllegalArgumentException("SECTION: rural4/suburban6/widen_4_to_6");
            var graph=HighwayRouteGraph.forSeed(s.getLevel().getSeed());
            var plan=RouteGraphMeshAdapter.select(graph,edge,start,length,from,to);
            var geometry=new RouteRoadGeometry(plan);
            double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=-minX,maxZ=-minX,minY=minX,maxY=-minX;
            // Preview AABB conservatively covers the entire path using the maximum half width plus two metres.
            double half=Math.max(from.protection(),to.protection())/2+2;
            for(double at=start;at<=start+length;at+=1){
                var p=geometry.alignment.frame(at).point();minX=Math.min(minX,p.x()-geometry.originX-half);maxX=Math.max(maxX,p.x()-geometry.originX+half);
                minZ=Math.min(minZ,p.z()-geometry.originZ-half);maxZ=Math.max(maxZ,p.z()-geometry.originZ+half);
                minY=Math.min(minY,geometry.elevation(at)-geometry.originY-4);maxY=Math.max(maxY,geometry.elevation(at)-geometry.originY+4);
            }
            var box=new AABB(origin.getX()+minX,origin.getY()+minY,origin.getZ()+minZ,origin.getX()+maxX,origin.getY()+maxY,origin.getZ()+maxZ);
            if(box.minY<s.getLevel().getMinBuildHeight()||box.maxY>=s.getLevel().getMaxBuildHeight()
                ||!s.getLevel().getWorldBorder().isWithinBounds(BlockPos.containing(box.minX,box.minY,box.minZ))
                ||!s.getLevel().getWorldBorder().isWithinBounds(BlockPos.containing(box.maxX,box.maxY,box.maxZ)))throw new IllegalArgumentException("WORLD_BOUNDS");
            var area=new BoundsXZ((int)Math.floor(box.minX),(int)Math.floor(box.minZ),(int)Math.ceil(box.maxX),(int)Math.ceil(box.maxZ));
            if(!com.antaurora.apofirstlight.worldgen.highway.HighwaySpatialClaimProvider.query(graph,s.getLevel().dimension(),area).isEmpty())throw new IllegalArgumentException("DISPLAY_PLOT_INTERSECTS_EXISTING_GRAPH: choose an independent plot");
            if(com.antaurora.apofirstlight.authoring.BuildingAuthoringCommands.overlaps(s.getLevel().dimension(),box))throw new IllegalArgumentException("AUTHORING_RESERVATION");
            String recipe=RouteMeshNetwork.GSON.toJson(plan);if(recipe.length()>32768)throw new IllegalArgumentException("RECIPE_BUDGET");
            var packet=new RouteMeshNetwork.Snapshot(s.getLevel().dimension().location(),true,true,origin.immutable(),UUID.randomUUID(),RouteMeshNetwork.digest(recipe),recipe);
            SESSIONS.put(s.getPlayerOrException().getUUID(),new Session(geometry,packet));RouteMeshNetwork.send(s.getPlayerOrException(),packet);
            reply(s,"M1-B REAL_GRAPH / RELOCATED / VISUAL_ONLY / writes=0 / collision=0; "+plan.edgeId()+" arc=["+start+","+(start+length)+"] sourceStartXZ="+geometry.originX+","+geometry.originZ+" display="+origin.toShortString());
            reply(s,"width="+from.width()+" -> "+to.width()+" construction="+from.construction()+" -> "+to.construction()+" protection="+from.protection()+" -> "+to.protection()+"; DESIGN 3% PROFILE, ENGINEERING/PROTECTION UNKNOWN; fly along +"+(plan.controls().get(1).x()==plan.controls().get(0).x()?"Z":"X")+". No chunk forced.");
            return 1;
        }catch(Exception ex){return failure(s,ex);}
    }
    private static int action(CommandSourceStack s,String action){
        try{
            var player=s.getPlayerOrException();var session=SESSIONS.get(player.getUUID());
            if(action.equals("remove")){
                SESSIONS.remove(player.getUUID());RouteMeshNetwork.send(player,new RouteMeshNetwork.Snapshot(s.getLevel().dimension().location(),false,true,BlockPos.ZERO,UUID.randomUUID(),"",""));
                reply(s,"M1-B preview removed, writes=0; M1-A fixed scene unchanged.");return 1;
            }
            if(session==null){reply(s,"NO_PREVIEW; reconnect/dimension change clears preview. Repeat preview command.");return 1;}
            if(!session.snapshot.dimension().equals(s.getLevel().dimension().location()))throw new IllegalArgumentException("DIMENSION_CHANGED");
            RouteMeshNetwork.send(player,session.snapshot);
            reply(s,"recipe="+session.snapshot.version()+" controls="+session.geometry.plan.controls().size()+" curvatureMax="+session.geometry.alignment.maxCurvature+"; "+session.geometry.plan.limitations());
            return 1;
        }catch(Exception ex){return failure(s,ex);}
    }
    private static int sample(CommandSourceStack s,double x,double z){
        try{
            var session=SESSIONS.get(s.getPlayerOrException().getUUID());if(session==null)throw new IllegalArgumentException("NO_PREVIEW");
            var g=session.geometry;var o=session.snapshot.origin();var surface=g.query(x-o.getX()+g.originX,z-o.getZ()+g.originZ);
            if(surface.isEmpty())throw new IllegalArgumentException("OUTSIDE_PREVIEW");
            var p=surface.get();reply(s,"arc="+p.station()+" lateral="+p.lateral()+" displayY="+(o.getY()+p.y()-g.originY)+" normal="+p.nx()+","+p.ny()+","+p.nz()
                +" width="+p.section().width()+" median="+p.section().medianRange()+" shoulders="+p.section().leftInner()+","+p.section().leftOuter()+","+p.section().rightInner()+","+p.section().rightOuter()+" (NO PHYSICAL SUPPORT)");
            return 1;
        }catch(Exception ex){return failure(s,ex);}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){SESSIONS.clear();}
    private static int failure(CommandSourceStack s,Exception e){s.sendFailure(Component.literal("Highway M1-B: "+e.getMessage()));return 0;}
    private static void reply(CommandSourceStack s,String text){s.sendSuccess(()->Component.literal("Highway M1-B: "+text),false);}
    private RouteMeshCommands(){}
}