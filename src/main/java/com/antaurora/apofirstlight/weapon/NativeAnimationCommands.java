package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import software.bernie.geckolib.animatable.GeoItem;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Operator animation test entry for any configured animated weapon. Not an ammo/shot state machine. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class NativeAnimationCommands {
    private record Cue(double tick, ResourceLocation sound) {}
    private record Session(ServerPlayer player, net.minecraft.world.item.ItemStack stack, long start, List<Cue> cues) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    @SubscribeEvent public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("afl").then(Commands.literal("gun_animation")
                .requires(s -> s.hasPermission(2))
                .then(Commands.argument("clip", StringArgumentType.word())
                        .suggests((c,b) -> {
                            var p=c.getSource().getPlayer();
                            return SharedSuggestionProvider.suggest(p!=null && p.getMainHandItem().getItem() instanceof NativeAnimatedWeaponItem i
                                    ? i.profile.clips() : List.<String>of(), b);
                        }).executes(c -> {
                            var p=c.getSource().getPlayerOrException();
                            var stack=p.getMainHandItem();
                            String clip=StringArgumentType.getString(c,"clip");
                            if (!(stack.getItem() instanceof NativeAnimatedWeaponItem item) || !item.profile.clips().contains(clip)) {
                                c.getSource().sendFailure(Component.literal("Hold a supported animated weapon and choose its clip."));return 0;
                            }
                            long id=GeoItem.getOrAssignId(stack,p.serverLevel());
                            p.inventoryMenu.broadcastChanges();p.containerMenu.broadcastChanges();
                            item.triggerAnim(p,id,"action",clip);
                            // One authority: server reads the SAME exported sound_effects; no client keyframe handler.
                            List<Cue> cues=new ArrayList<>();
                            String path="/assets/apocalypse_firstlight/animations/"+item.profile.id()+".animation.json";
                            try(var in=NativeAnimationCommands.class.getResourceAsStream(path)) {
                                if(in==null)throw new IllegalStateException("Missing "+path);
                                var a=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject()
                                        .getAsJsonObject("animations").getAsJsonObject(clip);
                                if(a.has("sound_effects"))for(var entry:a.getAsJsonObject("sound_effects").entrySet())
                                    cues.add(new Cue(Double.parseDouble(entry.getKey())*20,
                                            new ResourceLocation(entry.getValue().getAsJsonObject().get("effect").getAsString())));
                            } catch(Exception ex) { throw new IllegalStateException("Cannot read animation cues",ex); }
                            sessions.put(p.getUUID(),new Session(p,stack,p.level().getGameTime(),cues));
                            return 1;
                        }))));
        e.getDispatcher().register(Commands.literal("afl").then(Commands.literal("gun_inspect")
                .executes(c -> P901Actions.operation(c.getSource().getPlayerOrException(), "inspect") ? 1 : 0)));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        sessions.values().removeIf(s -> {
            var p=s.player();
            if(p.hasDisconnected() || !p.isAlive() || p.getMainHandItem()!=s.stack())return true;
            long elapsed=p.level().getGameTime()-s.start();
            s.cues().removeIf(c -> {
                if(elapsed<c.tick())return false;
                var sound=ForgeRegistries.SOUND_EVENTS.getValue(c.sound());
                if(sound!=null)p.level().playSound(null,p.getX(),p.getY(),p.getZ(),sound,SoundSource.PLAYERS,1,1);
                return true;
            });
            return s.cues().isEmpty();
        });
    }
}
