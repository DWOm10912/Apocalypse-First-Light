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
public final class P901Actions {
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
    private static final Map<ServerPlayer, ItemStack> EQUIPPED = new WeakHashMap<>();
    public static final int DRY_FIRE_COOLDOWN = 6;

    private static final class Session {
        ItemStack stack;
        NativeGunItem item;
        long id;
        long start;
        long end;
        boolean reload;
        boolean operation;
        java.util.List<NativeGunAnimations.Cue> cues = new java.util.ArrayList<>();
        boolean reloadStartedEmpty;
        boolean rackPlayed;
        String clip;
        boolean outPlayed;
        boolean inPlayed;
        int slot;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    }

    private P901Actions() {}

    public static void clearForDataReload() {
        SESSIONS.forEach((p,s)->s.item.stopTriggeredAnim(p,s.id,P901Item.CONTROLLER,s.clip));
        SESSIONS.clear(); NEXT_FIRE.clear(); NEXT_DRY_FIRE.clear();
    }

    public static String animationName(boolean reload) { return reload ? "reload" : "fire"; }

    /** Read-only guard for atomic attachment changes, sharing the existing action lock. */
    public static boolean busy(ServerPlayer player) { return SESSIONS.containsKey(player); }

    /** Handling actions share the same server lock as fire/reload. */
    public static boolean operation(ServerPlayer player, String clip) {
        if (!player.isAlive() || player.isSpectator() || SESSIONS.containsKey(player)
                || !(player.getMainHandItem().getItem() instanceof ConfiguredNativeGunItem item)
                || !(clip.equals("inspect") || clip.equals("draw"))) return false;
        Session state = new Session();
        state.stack = player.getMainHandItem(); state.item = item;
        state.id = GeoItem.getOrAssignId(state.stack, player.serverLevel());
        state.start = player.server.getTickCount();
        state.end = state.start + NativeGunAnimations.ticks(item.animationAsset(), clip);
        state.clip = clip; state.operation = true; state.slot = player.getInventory().selected;
        state.dimension = player.level().dimension();
        state.cues.addAll(NativeGunAnimations.cues(item.animationAsset(), clip));
        SESSIONS.put(player, state); syncInventory(player);
        item.triggerAnim(player, state.id, P901Item.CONTROLLER, clip);
        return true;
    }

    public static void request(ServerPlayer player, boolean reload, int slot) {
        request(player,reload,slot,0);
    }
    public static void request(ServerPlayer player, boolean reload, int slot,long shotId) {
        if (!player.isAlive() || player.isSpectator() || slot < 0 || slot > 8
                || player.getInventory().selected != slot
                || !(player.getMainHandItem().getItem() instanceof NativeGunItem item)) return;
        long now = player.server.getTickCount();
        Session previous = SESSIONS.get(player);
        if (previous != null) return;
        NativeGunDefinition definition = item.definition();
        ItemStack held = player.getMainHandItem();
        if (reload) {
            if (NativeGunAmmo.read(held, definition) >= NativeGunAmmo.capacity(held,definition)
                    || NativeGunAmmo.reserve(player.getInventory(), definition) == 0) return;
        } else {
            if (now < NEXT_FIRE.getOrDefault(player, 0L)) return;
            if (NativeGunAmmo.read(held, definition) == 0) {
                if (now >= NEXT_DRY_FIRE.getOrDefault(player, 0L)) {
                    NEXT_DRY_FIRE.put(player, now + DRY_FIRE_COOLDOWN);
                    sound(player, AflSounds.P9_01_DRY_FIRE.get());
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
        state.end = now + (reload ? item.reloadTicks(state.reloadStartedEmpty) : definition.fireIntervalTicks());
        state.slot = slot;
        state.reload = reload;
        state.clip = reload ? item.reloadClip(state.reloadStartedEmpty)
                : item.fireClip(NativeGunAmmo.read(held, definition) == 0);
        if (reload && item.animationAsset() != null) state.cues.addAll(NativeGunAnimations.cues(item.animationAsset(), state.clip));
        state.dimension = player.level().dimension();
        SESSIONS.put(player, state);
        // Deliver the per-stack render identity before its animation trigger.
        syncInventory(player);
        item.triggerAnim(player, state.id, P901Item.CONTROLLER, state.clip);
        if (!reload) {
            sound(player, NativeGunNoise.resolve(player.getMainHandItem(),definition).fireSound(item));
            var hit = NativeGunShot.execute(player, definition);
            com.antaurora.apofirstlight.network.AflNetwork.sendNativeShot(player, slot, state.id, hit.point(),shotId);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ItemStack held = player.getMainHandItem();
        ItemStack old = EQUIPPED.put(player, held);
        if (old != held) {
            Session interrupted = SESSIONS.get(player);
            if (interrupted != null && interrupted.stack != held) {
                SESSIONS.remove(player);
                interrupted.item.stopTriggeredAnim(player, interrupted.id, P901Item.CONTROLLER, interrupted.clip);
            }
            if (old != null && old.getItem() instanceof ConfiguredNativeGunItem outgoing) {
                // Vanilla may stop rendering the old stack before the complete clip is visible.
                long id = GeoItem.getOrAssignId(old, player.serverLevel());
                outgoing.triggerAnim(player, id, P901Item.CONTROLLER, "put_away");
                for (var cue : NativeGunAnimations.cues(outgoing.animationAsset(), "put_away"))
                    if (cue.tick() <= 1) sound(player, java.util.Objects.requireNonNull(
                            net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(cue.sound())));
            }
            operation(player, "draw");
        }
        Session state = SESSIONS.get(player);
        if (state == null) return;
        if (!player.isAlive() || player.isSpectator() || state.stack.isEmpty() || player.getMainHandItem() != state.stack
                || player.getInventory().selected != state.slot
                || player.level().dimension() != state.dimension) {
            state.item.stopTriggeredAnim(player, state.id,
                    P901Item.CONTROLLER, state.clip);
            SESSIONS.remove(player);
            return;
        }
        long now = player.server.getTickCount();
        state.cues.removeIf(c -> {
            if (now < state.start + c.tick()) return false;
            sound(player, java.util.Objects.requireNonNull(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(c.sound()),
                    "Unregistered native sound " + c.sound()));
            return true;
        });
        if (state.reload && state.item.animationAsset() != null) {
            if (!state.inPlayed && now >= state.end) {
                NativeGunAmmo.transfer(player.getInventory(), state.stack, state.item.definition());
                syncInventory(player);
                state.inPlayed = true;
            }
        } else if (state.reload) {
            if (!state.outPlayed && now >= state.start + MAG_OUT_TICK) {
                sound(player, AflSounds.P9_01_MAGAZINE_OUT.get());
                state.outPlayed = true;
            }
            if (!state.inPlayed && now >= state.start + state.item.definition().magInTick()) {
                NativeGunAmmo.transfer(player.getInventory(), state.stack, state.item.definition());
                syncInventory(player);
                sound(player, AflSounds.P9_01_MAGAZINE_IN.get());
                state.inPlayed = true;
            }
            if (state.reloadStartedEmpty && !state.rackPlayed && now >= state.start + SLIDE_RACK_TICK) {
                sound(player, AflSounds.P9_01_SLIDE_ACTION.get());
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
            EQUIPPED.remove(player);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void preventMelee(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof NativeGunItem) event.setCanceled(true);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void preventBreaking(BlockEvent.BreakEvent event) {
        if (event.getPlayer().getMainHandItem().getItem() instanceof NativeGunItem
                && !BulletBlockInteraction.isBulletBreak(event)) event.setCanceled(true);
    }
}
