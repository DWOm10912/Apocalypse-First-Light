package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class ServicePistolActions {
    public static final int FIRE_TICKS = 3;
    public static final int RELOAD_TICKS = 26;
    // Authored withdrawal at 0.42s; seated at 0.93s. Quantize to nearest server tick.
    public static final int MAG_OUT_TICK = 8;
    public static final int MAG_IN_TICK = 19;
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();

    private static final class Session {
        ItemStack stack;
        ServicePistolItem item;
        long id;
        long start;
        long end;
        boolean reload;
        boolean outPlayed;
        boolean inPlayed;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    }

    private ServicePistolActions() {}

    public static String animationName(boolean reload) { return reload ? "reload" : "fire"; }

    public static void request(ServerPlayer player, boolean reload, int slot) {
        if (!player.isAlive() || player.isSpectator() || slot < 0 || slot > 8
                || player.getInventory().selected != slot
                || !(player.getMainHandItem().getItem() instanceof ServicePistolItem item)) return;
        long now = player.serverLevel().getGameTime();
        Session previous = SESSIONS.get(player);
        if (previous != null && now < previous.end) return;
        Session state = new Session();
        state.stack = player.getMainHandItem();
        state.item = item;
        state.id = GeoItem.getOrAssignId(state.stack, player.serverLevel());
        state.start = now;
        state.end = now + (reload ? RELOAD_TICKS : FIRE_TICKS);
        state.reload = reload;
        state.dimension = player.level().dimension();
        SESSIONS.put(player, state);
        // Deliver the per-stack render identity before its animation trigger.
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        item.triggerAnim(player, state.id, ServicePistolItem.CONTROLLER, animationName(reload));
        if (!reload) sound(player, AflSounds.SERVICE_PISTOL_FIRE.get());
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Session state = SESSIONS.get(player);
        if (state == null) return;
        if (!player.isAlive() || player.isSpectator() || player.getMainHandItem() != state.stack
                || player.level().dimension() != state.dimension) {
            state.item.stopTriggeredAnim(player, state.id,
                    ServicePistolItem.CONTROLLER, animationName(state.reload));
            SESSIONS.remove(player);
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (state.reload) {
            if (!state.outPlayed && now >= state.start + MAG_OUT_TICK) {
                sound(player, AflSounds.SERVICE_PISTOL_MAGAZINE_OUT.get());
                state.outPlayed = true;
            }
            if (!state.inPlayed && now >= state.start + MAG_IN_TICK) {
                sound(player, AflSounds.SERVICE_PISTOL_MAGAZINE_IN.get());
                state.inPlayed = true;
            }
        }
        if (now >= state.end) SESSIONS.remove(player);
    }

    private static void sound(ServerPlayer player, SoundEvent sound) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SESSIONS.remove(player);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void preventMelee(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof ServicePistolItem) event.setCanceled(true);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void preventBreaking(BlockEvent.BreakEvent event) {
        if (event.getPlayer().getMainHandItem().getItem() instanceof ServicePistolItem) event.setCanceled(true);
    }
}
