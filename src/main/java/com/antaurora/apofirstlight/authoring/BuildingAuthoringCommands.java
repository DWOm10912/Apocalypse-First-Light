package com.antaurora.apofirstlight.authoring;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.*;
import static net.minecraft.commands.Commands.*;

@Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class BuildingAuthoringCommands {
    private static final Map<UUID,BuildingAuthoringSession> SESSIONS=new HashMap<>();
    private BuildingAuthoringCommands() {}
    /** Read-only reservation guard for development reference areas; call on the server thread. */
    public static boolean overlaps(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,net.minecraft.world.phys.AABB bounds) {
        return SESSIONS.values().stream().anyMatch(s->s.dimension.equals(dimension)&&s.bounds().intersects(bounds));
    }
    /** Permission-checked access for optional development-only draft tools. */
    public static BuildingAuthoringSession active(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if(!allowed(source))throw new IllegalArgumentException("Authoring disabled or insufficient permission");
        var player=source.getPlayerOrException();var session=SESSIONS.get(player.getUUID());
        if(session==null)throw new IllegalArgumentException("Create an authoring session first");
        if(!session.dimension.equals(player.serverLevel().dimension()))throw new IllegalArgumentException("Return to session dimension");
        return session;
    }
    public static boolean allowed(CommandSourceStack source) {
        return BuildingAuthoringConfig.ENABLED.get() && source.getEntity() instanceof ServerPlayer p
                && (p.isCreative()||source.hasPermission(2));
    }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var size=argument("height",IntegerArgumentType.integer(2,192))
                .executes(c->execute(c,"create"))
                .then(argument("surface_offset_y",IntegerArgumentType.integer(0,191)).executes(c->execute(c,"create_offset")));
        event.getDispatcher().register(literal("afl_author").requires(BuildingAuthoringCommands::allowed)
                .then(literal("create").then(argument("building_id",StringArgumentType.word())
                        .then(argument("width",IntegerArgumentType.integer(1,128))
                                .then(argument("depth",IntegerArgumentType.integer(1,128)).then(size)))))
                .then(literal("resume").then(argument("building_id",StringArgumentType.word())
                        .then(argument("origin",net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(argument("width",IntegerArgumentType.integer(1,128))
                                        .then(argument("depth",IntegerArgumentType.integer(1,128))
                                                .then(argument("height",IntegerArgumentType.integer(2,192))
                                                        .then(argument("surface_offset_y",IntegerArgumentType.integer(0,191))
                                                                .executes(c->execute(c,"resume")))))))))
                .then(literal("configure").then(argument("category",StringArgumentType.word())
                        .suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(BuildingMetadata.Category.values()).map(Enum::name),b))
                        .then(argument("zones",StringArgumentType.string())
                                .suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(BuildingMetadata.Zone.values()).map(Enum::name),b))
                                .then(argument("road_facing",BoolArgumentType.bool())
                                        .then(argument("damage_compatible",BoolArgumentType.bool()).executes(c->execute(c,"configure")))))))
                .then(literal("clear").executes(c->execute(c,"clear"))
                        .then(argument("token",StringArgumentType.word()).executes(c->execute(c,"clear_confirm"))))
                .then(literal("bounds").executes(c->execute(c,"bounds")))
                .then(literal("info").executes(c->execute(c,"info")))
                .then(literal("validate").executes(c->execute(c,"validate")))
                .then(literal("export").executes(c->execute(c,"export")))
                .then(literal("cancel").executes(c->execute(c,"cancel"))));
    }
    private static void say(CommandSourceStack src,String text){src.sendSuccess(()->Component.literal(text),false);}
    private static int execute(CommandContext<CommandSourceStack> c,String action) {
        var src=c.getSource();
        try {
            if(!allowed(src))throw new IllegalArgumentException("Authoring disabled or insufficient permission");
            ServerPlayer p=src.getPlayerOrException();var level=p.serverLevel();var key=p.getUUID();
            if(action.startsWith("create")||action.equals("resume")) {
                if(SESSIONS.containsKey(key))throw new IllegalArgumentException("Cancel current session first (blocks will remain)");
                int w=IntegerArgumentType.getInteger(c,"width"),d=IntegerArgumentType.getInteger(c,"depth"),h=IntegerArgumentType.getInteger(c,"height");
                int offset=!action.equals("create")?IntegerArgumentType.getInteger(c,"surface_offset_y"):1;
                var m=new BuildingMetadata(StringArgumentType.getString(c,"building_id"),w,d,h,offset,
                        BuildingMetadata.Category.FILLER,Set.of(BuildingMetadata.Zone.EDGE),true,true);
                // Air-space reservation, not terrain preparation. Never clear/flatten during create.
                BlockPos origin=action.equals("resume")?net.minecraft.commands.arguments.coordinates.BlockPosArgument.getBlockPos(c,"origin")
                        :new BlockPos(Math.floorDiv(p.getBlockX()+32,16)*16,p.getBlockY(),Math.floorDiv(p.getBlockZ(),16)*16);
                var s=new BuildingAuthoringSession(key,level.dimension(),origin,m);
                for(var other:SESSIONS.values())if(other.dimension.equals(s.dimension)&&other.bounds().intersects(s.bounds()))
                    throw new IllegalArgumentException("Plot overlaps another active reservation");
                if(action.equals("resume")){BuildingAuthoringService.accessible(level,s);s.changed();}
                else BuildingAuthoringService.vacant(level,s);
                SESSIONS.put(key,s);s.boundsUntil=level.getGameTime()+1200;
                say(src,"Created "+m.id()+" origin="+origin.toShortString()+" front=SOUTH surfaceY="+(origin.getY()+offset)+"; configure category/zones before export");return 1;
            }
            var s=SESSIONS.get(key);if(s==null)throw new IllegalArgumentException("No active authoring session");
            if(action.equals("cancel")){SESSIONS.remove(key);say(src,"Session cancelled; blocks preserved, nothing exported");return 1;}
            if(!s.dimension.equals(level.dimension()))throw new IllegalArgumentException("Return to session dimension");
            switch(action) {
                case "configure" -> {
                    var m=s.metadata;Set<BuildingMetadata.Zone> zones=EnumSet.noneOf(BuildingMetadata.Zone.class);
                    for(String z:StringArgumentType.getString(c,"zones").split(","))zones.add(BuildingMetadata.Zone.valueOf(z));
                    s.metadata=new BuildingMetadata(m.id(),m.width(),m.depth(),m.height(),m.surfaceOffset(),
                            BuildingMetadata.Category.valueOf(StringArgumentType.getString(c,"category")),zones,
                            BoolArgumentType.getBool(c,"road_facing"),BoolArgumentType.getBool(c,"damage_compatible"));s.changed();say(src,"Metadata configured; state=DRAFT");
                }
                case "bounds" -> {s.boundsUntil=s.boundsUntil>level.getGameTime()?0:level.getGameTime()+1200;say(src,"Bounds "+(s.boundsUntil==0?"OFF":"ON for 60 seconds"));}
                case "info" -> {var scan=BuildingAuthoringService.capture(level,s,false);var m=s.metadata;
                    say(src,m.id()+" origin="+s.origin.toShortString()+" size="+m.width()+"x"+m.depth()+"x"+m.height()+" front=SOUTH surface_offset_y="+m.surfaceOffset()+" state="+s.state+" blocks="+scan.blocks()+" category="+m.category()+" zones="+m.zones());}
                case "clear" -> {BuildingAuthoringService.accessible(level,s);s.clearToken=UUID.randomUUID().toString().substring(0,8);s.clearUntil=level.getGameTime()+600;
                    say(src,"DESTRUCTIVE: clear only "+s.origin.toShortString()+" through "+s.max().toShortString()+". Within 30s run /afl_author clear "+s.clearToken);}
                case "clear_confirm" -> {
                    if(s.clearToken==null||!s.clearToken.equals(StringArgumentType.getString(c,"token"))||level.getGameTime()>s.clearUntil)
                        throw new IllegalArgumentException("Invalid/expired confirmation; run /afl_author clear again");
                    BuildingAuthoringService.clear(level,s);say(src,"Capture volume cleared without drops; not automatically recoverable");
                }
                case "validate" -> {var result=BuildingAuthoringService.validate(level,s);say(src,"VALIDATED: "+result.blocks()+" non-air blocks; no entities/loot/debug blocks");}
                case "export" -> {var directory=net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("afl_authoring_exports");
                    say(src,"EXPORTED: "+BuildingAuthoringService.export(level,s,directory)+" + metadata JSON. Not in any city pool.");}
                default -> throw new IllegalArgumentException("Unknown action");
            }
            return 1;
        }catch(Exception e){src.sendFailure(Component.literal("Authoring: "+e.getMessage()));return 0;}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!BuildingAuthoringConfig.ENABLED.get())return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        for(var s:SESSIONS.values()) {
            var level=server.getLevel(s.dimension);var p=server.getPlayerList().getPlayer(s.owner);
            if(level==null||p==null||p.level()!=level||level.getGameTime()%20!=0||s.boundsUntil<=level.getGameTime()
                    ||!(p.isCreative()||p.hasPermissions(2)))continue;
            drawBounds(p,s);
        }
    }
    private static void dot(ServerPlayer p,double x,double y,double z,Vector3f color) {
        p.serverLevel().sendParticles(p,new DustParticleOptions(color,1),false,x,y,z,1,0,0,0,0);
    }
    private static void drawBounds(ServerPlayer p,BuildingAuthoringSession s) {
        var m=s.metadata;int x=s.origin.getX(),y=s.origin.getY(),z=s.origin.getZ();
        Vector3f white=new Vector3f(1,1,1),gray=new Vector3f(.65f,.65f,.65f),yellow=new Vector3f(1,1,0);
        // Fixed samples per edge, independent of volume. Particles have finite Vanilla lifetimes.
        for(int i=0;i<=8;i++) {double t=i/8.0;
            for(int side=0;side<=1;side++)for(int top=0;top<=1;top++) {
                dot(p,x+t*m.width(),y+top*m.height(),z+side*m.depth(),white);
                dot(p,x+side*m.width(),y+top*m.height(),z+t*m.depth(),white);
                dot(p,x+side*m.width(),y+t*m.height(),z+top*m.depth(),white);
            }
            for(int side=0;side<=1;side++) {
                dot(p,x+t*m.width(),y+m.surfaceOffset(),z+side*m.depth(),gray);
                dot(p,x+side*m.width(),y+m.surfaceOffset(),z+t*m.depth(),gray);
            }
            dot(p,x+m.width()/2.0,y+m.surfaceOffset(),z+m.depth()+t*3,yellow);
        }
        dot(p,x,y,z,new Vector3f(1,0,0));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){SESSIONS.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){SESSIONS.clear();}
    @SubscribeEvent public static void edited(net.minecraftforge.event.level.BlockEvent event) {
        if(event.isCanceled() || !(event instanceof net.minecraftforge.event.level.BlockEvent.BreakEvent
                || event instanceof net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent))return;
        if(!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;
        for(var s:SESSIONS.values())if(s.dimension.equals(level.dimension())&&s.bounds().contains(net.minecraft.world.phys.Vec3.atCenterOf(event.getPos())))s.changed();
    }
}
