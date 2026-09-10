package com.antaurora.apofirstlight.dev.authoring.bridge;

import com.antaurora.apofirstlight.authoring.BuildingAuthoringCommands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
import static net.minecraft.commands.Commands.*;

/** Explicit in-game user target, never an autonomous MCP area setter. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="apocalypse_firstlight")
public final class ReferenceAreaCommands {
    record Area(BridgeBounds bounds,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension){}
    private static final Map<UUID,Area> AREAS=new HashMap<>();
    static Area get(ServerPlayer p){var a=AREAS.get(p.getUUID());if(a==null)throw new IllegalArgumentException("REFERENCE_AREA_NOT_SET: use /afl_reference_area set in game");if(!a.dimension.equals(p.serverLevel().dimension()))throw new IllegalArgumentException("DIMENSION_MISMATCH");if(BuildingAuthoringCommands.overlaps(a.dimension,a.bounds.aabb()))throw new IllegalArgumentException("PASTE_SCOPE_REJECTED: authoring overlap");return a;}
    static void set(ServerPlayer p,BridgeBounds b){b.check(p.serverLevel());if(BuildingAuthoringCommands.overlaps(p.serverLevel().dimension(),b.aabb()))throw new IllegalArgumentException("PASTE_SCOPE_REJECTED: authoring overlap");if(!p.serverLevel().getEntities(null,b.aabb()).isEmpty())throw new IllegalArgumentException("ENTITY_IN_REFERENCE_AREA");for(var pos:net.minecraft.core.BlockPos.betweenClosed(b.min(),b.max()))if(!p.serverLevel().getBlockState(pos).isAir())throw new IllegalArgumentException("REFERENCE_AREA_NOT_EMPTY");AREAS.put(p.getUUID(),new Area(b,p.serverLevel().dimension()));}
    @SubscribeEvent public static void register(net.minecraftforge.event.RegisterCommandsEvent e){
        if(net.minecraftforge.fml.loading.FMLEnvironment.production)return;
        e.getDispatcher().register(literal("afl_reference_area").requires(BuildingAuthoringCommands::allowed).then(literal("set").then(argument("origin",BlockPosArgument.blockPos()).then(argument("width",IntegerArgumentType.integer(1,256)).then(argument("height",IntegerArgumentType.integer(1,384)).then(argument("depth",IntegerArgumentType.integer(1,256)).executes(c->{try{
            var o=BlockPosArgument.getBlockPos(c,"origin");var b=new BridgeBounds(o,o.offset(IntegerArgumentType.getInteger(c,"width")-1,IntegerArgumentType.getInteger(c,"height")-1,IntegerArgumentType.getInteger(c,"depth")-1));set(c.getSource().getPlayerOrException(),b);c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("REFERENCE AREA reserved; no blocks pasted. "+b.json()),false);return 1;
        }catch(Exception ex){c.getSource().sendFailure(net.minecraft.network.chat.Component.literal(ex.getMessage()));return 0;}})))))));
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){AREAS.clear();}
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){AREAS.remove(e.getEntity().getUUID());}
}
