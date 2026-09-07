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
    public static final int EMPTY_RELOAD_TICKS = 33;
    public static final int SLIDE_RACK_TICK = 25;
    // Authored withdrawal at 0.42s; seated at 0.93s. Quantize to nearest server tick.
    public static final int MAG_OUT_TICK = 8;
    public static final int MAG_IN_TICK = 19;
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> NEXT_FIRE = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> NEXT_DRY_FIRE = new WeakHashMap<>();
    public static final int DRY_FIRE_COOLDOWN = 6;

    private static final class Session {
        ItemStack stack;
        ServicePistolItem item;
        long id;
        long start;
        long end;
        boolean reload;
        boolean reloadStartedEmpty;
        boolean rackPlayed;
        String clip;
        boolean outPlayed;
        boolean inPlayed;
        int slot;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    }

    private ServicePistolActions() {}

    public static String animationName(boolean reload) { return reload ? "reload" : "fire"; }

    public static void request(ServerPlayer player, boolean reload, int slot) {
        if (!player.isAlive() || player.isSpectator() || slot < 0 || slot > 8
                || player.getInventory().selected != slot
                || !(player.getMainHandItem().getItem() instanceof ServicePistolItem item)) return;
        long now = player.server.getTickCount();
        Session previous = SESSIONS.get(player);
        if (previous != null) return;
        NativeGunDefinition definition = item.definition();
        ItemStack held = player.getMainHandItem();
        if (reload) {
            if (NativeGunAmmo.read(held, definition) >= definition.magazineCapacity()
                    || NativeGunAmmo.reserve(player.getInventory(), definition) == 0) return;
        } else {
            if (now < NEXT_FIRE.getOrDefault(player, 0L)) return;
            if (NativeGunAmmo.read(held, definition) == 0) {
                if (now >= NEXT_DRY_FIRE.getOrDefault(player, 0L)) {
                    NEXT_DRY_FIRE.put(player, now + DRY_FIRE_COOLDOWN);
                    sound(player, AflSounds.SERVICE_PISTOL_DRY_FIRE.get());
                }
                return;
            }
            if (!NativeGunAmmo.consumeOne(held, definition)) return;
            NEXT_FIRE.put(player, now + definition.fireIntervalTicks());
        }
        Session state = new Session();
        state.stack = player.getMainHandItem();
        state.item = item;
        state.id = GeoItem.getOrAssignId(state.stack, player.serverLevel());
        state.start = now;
        state.reloadStartedEmpty = reload && NativeGunAmmo.read(held, definition) == 0;
        state.end = now + (reload ? (state.reloadStartedEmpty ? EMPTY_RELOAD_TICKS : definition.reloadDurationTicks()) : definition.fireIntervalTicks());
        state.slot = slot;
        state.reload = reload;
        state.clip = reload ? (state.reloadStartedEmpty ? "reload_empty" : "reload")
                : (NativeGunAmmo.read(held, definition) == 0 ? "fire_last_round" : "fire");
        state.dimension = player.level().dimension();
        SESSIONS.put(player, state);
        // Deliver the per-stack render identity before its animation trigger.
        syncInventory(player);
        item.triggerAnim(player, state.id, ServicePistolItem.CONTROLLER, state.clip);
        if (!reload) {
            sound(player, AflSounds.SERVICE_PISTOL_FIRE.get());
            var hit = NativeGunShot.execute(player, definition);
            com.antaurora.apofirstlight.network.AflNetwork.sendNativeShot(player, slot, state.id, hit.point());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Session state = SESSIONS.get(player);
        if (state == null) return;
        if (!player.isAlive() || player.isSpectator() || state.stack.isEmpty() || player.getMainHandItem() != state.stack
                || player.getInventory().selected != state.slot
                || player.level().dimension() != state.dimension) {
            state.item.stopTriggeredAnim(player, state.id,
                    ServicePistolItem.CONTROLLER, state.clip);
            SESSIONS.remove(player);
            return;
        }
        long now = player.server.getTickCount();
        if (state.reload) {
            if (!state.outPlayed && now >= state.start + MAG_OUT_TICK) {
                sound(player, AflSounds.SERVICE_PISTOL_MAGAZINE_OUT.get());
                state.outPlayed = true;
            }
            if (!state.inPlayed && now >= state.start + state.item.definition().magInTick()) {
                NativeGunAmmo.transfer(player.getInventory(), state.stack, state.item.definition());
                syncInventory(player);
                sound(player, AflSounds.SERVICE_PISTOL_MAGAZINE_IN.get());
                state.inPlayed = true;
            }
            if (state.reloadStartedEmpty && !state.rackPlayed && now >= state.start + SLIDE_RACK_TICK) {
                sound(player, AflSounds.SERVICE_PISTOL_SLIDE_ACTION.get());
                state.rackPlayed = true;
            }
        }
        if (now >= state.end) SESSIONS.remove(player);
    }

    private static void sound(ServerPlayer player, SoundEvent sound) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SESSIONS.remove(player);
            NEXT_FIRE.remove(player);
            NEXT_DRY_FIRE.remove(player);
        }
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
