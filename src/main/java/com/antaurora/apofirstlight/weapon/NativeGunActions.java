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
public final class NativeGunActions {
    public static final int FIRE_TICKS = 3;
    public static final int RELOAD_TICKS = 48;
    public static final int EMPTY_RELOAD_TICKS = 63;
    private static final int P9_EMPTY_RELOAD_SYNC_LEAD_TICKS = 2;
    public static final int SLIDE_RACK_TICK = 25;
    // Legacy no-animation fallback only; P9/BR51 use their authored sound markers.
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
        boolean lastShot;
        boolean lockHandoffPlayed;
        int slot;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    }

    private NativeGunActions() {}

    public static void clearForDataReload() {
        NativeFireControl.clear();
        SESSIONS.forEach((p,s)->s.item.stopTriggeredAnim(p,s.id,NativeGunItem.ACTION_CONTROLLER,s.clip));
        SESSIONS.clear(); NEXT_FIRE.clear(); NEXT_DRY_FIRE.clear();
    }

    public static String animationName(boolean reload) { return reload ? "reload_tactical" : "shoot"; }

    /** Read-only guard for atomic attachment changes, sharing the existing action lock. */
    public static boolean busy(ServerPlayer player) { return SESSIONS.containsKey(player); }

    /** Handling actions share the same server lock as fire/reload. */
    public static boolean operation(ServerPlayer player, String clip) {
        if (!player.isAlive() || player.isSpectator() || SESSIONS.containsKey(player)
                || (clip.equals("inspect") && player.containerMenu != player.inventoryMenu)
                || !(player.getMainHandItem().getItem() instanceof NativeGunItem item)
                || !(clip.equals("inspect") ? item.inspectClip() != null
                    : clip.equals("draw") && item.animationAsset() != null)) return false;
        Session state = new Session();
        NativeFireControl.cancel(player);
        state.stack = player.getMainHandItem(); state.item = item;
        state.id = GeoItem.getOrAssignId(state.stack, player.serverLevel());
        state.start = player.server.getTickCount();
        state.clip = clip.equals("inspect") ? item.inspectClip(state.stack) : clip;
        state.end = state.start + NativeGunAnimations.ticks(item.animationAsset(), state.clip);
        state.operation = true; state.slot = player.getInventory().selected;
        state.dimension = player.level().dimension();
        state.cues.addAll(NativeGunAnimations.cues(item.animationAsset(), state.clip));
        SESSIONS.put(player, state); syncInventory(player);
        item.triggerAnim(player, state.id, NativeGunItem.ACTION_CONTROLLER, state.clip);
        return true;
    }

    public static void request(ServerPlayer player, boolean reload, int slot) {
        request(player,reload,slot,0);
    }
    public static void cancelInspect(ServerPlayer player, long expectedId) {
        Session state = SESSIONS.get(player);
        if (state != null && state.operation && isInspect(state.clip) && state.id == expectedId) {
            state.item.stopTriggeredAnim(player, state.id, NativeGunItem.ACTION_CONTROLLER, state.clip);
            SESSIONS.remove(player);
        }
    }
    public static void request(ServerPlayer player, boolean reload, int slot,long shotId) {
        if(reload)NativeFireControl.cancel(player);
        if (!player.isAlive() || player.isSpectator() || slot < 0 || slot > 8
                || player.getInventory().selected != slot
                || !(player.getMainHandItem().getItem() instanceof NativeGunItem item)) return;
        long now = player.server.getTickCount();
        Session previous = SESSIONS.get(player);
        if (previous != null) {
            if (NativeShotAnimationPolicy.completedShotCanBeReplaced(
                    previous.reload, previous.operation, now, previous.end)) {
                SESSIONS.remove(player);
            } else if (previous.operation && isInspect(previous.clip)) {
                cancelInspect(player, previous.id); // Same click proceeds to normal combat validation.
            } else return;
        }
        NativeGunDefinition definition = item.definition();
        ItemStack held = player.getMainHandItem();
        boolean lastShot = false;
        if (reload) {
            if (NativeGunAmmo.read(held, definition) >= NativeGunAmmo.capacity(held,definition)
                    || NativeGunAmmo.reserve(player.getInventory(), definition) == 0) return;
        } else {
            if (now < NEXT_FIRE.getOrDefault(player, 0L)) return;
            if (NativeGunAmmo.read(held, definition) == 0) {
                if (now >= NEXT_DRY_FIRE.getOrDefault(player, 0L)) {
                    NEXT_DRY_FIRE.put(player, now + DRY_FIRE_COOLDOWN);
                    sound(player, item.dryFireSound());
                }
                return;
            }
            int ammoBefore = NativeGunAmmo.read(held, definition);
            if (!NativeGunAmmo.consumeOne(held, definition)) return;
            int ammoAfter = NativeGunAmmo.read(held, definition);
            NEXT_FIRE.put(player, now + definition.fireIntervalTicks());
            // This transition is authoritative because it is observed immediately around consumeOne.
            // Store it on the accepted shot session; clients never infer the last round themselves.
            lastShot = NativeShotAnimationPolicy.isLastShot(ammoBefore, ammoAfter);
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
        state.lastShot = lastShot;
        state.clip = reload ? item.reloadClip(state.reloadStartedEmpty)
                : item.fireClip(state.lastShot);
        if (reload && item.animationAsset() != null) state.cues.addAll(NativeGunAnimations.cues(item.animationAsset(), state.clip));
        state.dimension = player.level().dimension();
        SESSIONS.put(player, state);
        // Deliver the per-stack render identity before its animation trigger.
        syncInventory(player);
        if (!reload) {
            // GeckoLib 4.7.4 does not reset a running controller when the same trigger name
            // arrives. Its standard stop + trigger pair makes every accepted shot start at 0s.
            item.stopTriggeredAnim(player, state.id, NativeGunItem.ACTION_CONTROLLER, state.clip);
        }
        item.triggerAnim(player, state.id, NativeGunItem.ACTION_CONTROLLER, state.clip);
        if (!reload) {
            sound(player, NativeGunNoise.resolve(player.getMainHandItem(),definition).fireSound(item));
            var hit = NativeGunShot.execute(player, definition);
            com.antaurora.apofirstlight.network.AflNetwork.sendNativeShot(player, slot, state.id, hit.point(),shotId,
                    definition.hitEffect().onHit(hit.entity()!=null));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        try {
        ItemStack held = player.getMainHandItem();
        ItemStack old = EQUIPPED.put(player, held);
        if (old != held) {
            NativeFireControl.cancel(player);
            Session interrupted = SESSIONS.get(player);
            if (interrupted != null && interrupted.stack != held) {
                SESSIONS.remove(player);
                interrupted.item.stopTriggeredAnim(player, interrupted.id, NativeGunItem.ACTION_CONTROLLER, interrupted.clip);
            }
            if (old != null && old.getItem() instanceof NativeGunItem outgoing
                    && outgoing.animationAsset() != null) {
                // Vanilla may stop rendering the old stack before the complete clip is visible.
                long id = GeoItem.getOrAssignId(old, player.serverLevel());
                outgoing.triggerAnim(player, id, NativeGunItem.ACTION_CONTROLLER, "put_away");
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
                || player.level().dimension() != state.dimension
                || (isInspect(state.clip) && player.containerMenu != player.inventoryMenu)) {
            state.item.stopTriggeredAnim(player, state.id,
                    NativeGunItem.ACTION_CONTROLLER, state.clip);
            SESSIONS.remove(player);
            NativeFireControl.cancel(player);
            return;
        }
        long now = player.server.getTickCount();
        if (state.lastShot && !state.lockHandoffPlayed
                && now >= state.start + NativeShotAnimationPolicy.LAST_SHOT_HANDOFF_TICKS) {
            // Both formal assets reach their rearward mechanical pose during the first tick.
            // Stop the one-shot here so the ammo-driven empty baseline holds that pose instead
            // of allowing the ordinary shoot clip to close the action again.
            state.item.stopTriggeredAnim(player, state.id, NativeGunItem.ACTION_CONTROLLER, state.clip);
            state.lockHandoffPlayed = true;
        }
        state.cues.removeIf(c -> {
            if (now < state.start + c.tick()) return false;
            sound(player, java.util.Objects.requireNonNull(net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(c.sound()),
                    "Unregistered native sound " + c.sound()));
            return true;
        });
        if (state.reload && state.item.animationAsset() != null) {
            // P9's authored empty reload is 3.12 s (62.4 ticks), while its action lock is 63 ticks.
            // Sync the loaded magazine just before the trigger ends so the controller transitions
            // directly to static_idle instead of showing empty_idle's locked-back slide for a frame.
            // The session still remains locked until state.end, so this does not shorten the reload.
            long ammoCommitTick = state.start + (state.reloadStartedEmpty
                    ? state.item.definition().emptyMagInTick() : state.item.definition().magInTick());
            if (state.reloadStartedEmpty && state.item instanceof P901Item)
                ammoCommitTick -= P9_EMPTY_RELOAD_SYNC_LEAD_TICKS;
            if (!state.inPlayed && now >= ammoCommitTick) {
                NativeGunAmmo.transfer(player.getInventory(), state.stack, state.item.definition());
                syncInventory(player);
                state.inPlayed = true;
            }
        } else if (state.reload) {
            if (!state.outPlayed && now >= state.start + MAG_OUT_TICK) {
                sound(player, AflSounds.NATIVE_GUN_MAGAZINE_OUT.get());
                state.outPlayed = true;
            }
            if (!state.inPlayed && now >= state.start + state.item.definition().magInTick()) {
                NativeGunAmmo.transfer(player.getInventory(), state.stack, state.item.definition());
                syncInventory(player);
                sound(player, AflSounds.NATIVE_GUN_MAGAZINE_IN.get());
                state.inPlayed = true;
            }
            if (state.reloadStartedEmpty && !state.rackPlayed && now >= state.start + SLIDE_RACK_TICK) {
                sound(player, AflSounds.NATIVE_GUN_ACTION.get());
                state.rackPlayed = true;
            }
        }
        if (now >= state.end) SESSIONS.remove(player);
        } finally { NativeFireControl.tick(player); }
    }

    private static void sound(ServerPlayer player, SoundEvent sound) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static boolean isInspect(String clip) {
        return "inspect".equals(clip) || "inspect_empty".equals(clip);
    }

    private static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NativeFireControl.logout(player);
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
