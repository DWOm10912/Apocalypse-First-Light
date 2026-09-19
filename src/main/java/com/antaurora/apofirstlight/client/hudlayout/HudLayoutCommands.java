package com.antaurora.apofirstlight.client.hudlayout;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import com.google.gson.JsonObject;

@Mod.EventBusSubscriber(modid=ApocalypseFirstLight.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class HudLayoutCommands {
    private HudLayoutCommands(){}
    @SubscribeEvent public static void register(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("afl").then(Commands.literal("hudlayout")
                .then(Commands.literal("list").executes(c->run("list",null)))
                .then(Commands.literal("edit").then(Commands.argument("layout_id",StringArgumentType.word())
                        .suggests((c,b)->SharedSuggestionProvider.suggest(HudLayouts.REGISTRY.all().stream().map(HudLayoutDescriptor::id),b))
                        .executes(c->run("edit",StringArgumentType.getString(c,"layout_id")))))
                .then(Commands.literal("reload")
                        .then(Commands.literal("all").executes(c->run("reload","all")))
                        .then(Commands.argument("layout_id",StringArgumentType.word())
                                .suggests((c,b)->SharedSuggestionProvider.suggest(HudLayouts.REGISTRY.all().stream().map(HudLayoutDescriptor::id),b))
                                .executes(c->run("reload",StringArgumentType.getString(c,"layout_id")))))));
    }
    private static int run(String action,String id){
        var mc=Minecraft.getInstance();
        HudLayoutSourceStore store;
        try{store=new HudLayoutSourceStore(!FMLEnvironment.production,Path.of(""),mc.gameDirectory.toPath());}
        catch(Exception e){message("dev_only");return 0;}
        if(action.equals("list")){
            message("list",String.join(", ",HudLayouts.REGISTRY.all().stream().map(HudLayoutDescriptor::id).toList()));return 1;
        }
        var descriptor=HudLayouts.REGISTRY.get(id);
        if(descriptor==null && !(action.equals("reload") && "all".equals(id))){message("unknown",id);return 0;}
        try{
            if(action.equals("edit")){
                var screen=new HudLayoutEditorScreen((ClientHudLayout)descriptor,store,HudLayouts.REGISTRY);
                // Queue after ChatScreen closes; execute() on the client thread would run too early.
                mc.tell(()->{if(mc.player!=null && mc.level!=null)mc.setScreen(screen);});
            }else{
                var loaded=new LinkedHashMap<HudLayoutDescriptor,JsonObject>();
                for(var layout:HudLayouts.REGISTRY.all())if("all".equals(id)||layout==descriptor)
                    loaded.put(layout,store.read(layout).json());
                loaded.forEach(HudLayoutDescriptor::apply);message("reloaded",id);
            }
            return 1;
        }catch(Exception e){message("failed",e.getMessage());return 0;}
    }
    private static void message(String key,Object... args){
        var player=Minecraft.getInstance().player;
        if(player!=null)player.displayClientMessage(HudLayoutEditorScreen.text(key,args),false);
    }
    @SubscribeEvent public static void hideDuplicatePreview(RenderGuiOverlayEvent.Pre event){
        if(Minecraft.getInstance().screen instanceof HudLayoutEditorScreen editor
                && event.getOverlay().id().equals(editor.layout().overlayId()))event.setCanceled(true);
    }
}
