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
    public static final int SLIDE_RACK_TICK = 25;
    // Legacy no-animation fallback only; P9/BR51 use their authored sound markers.
    public static final int MAG_OUT_TICK = 8;
    public static final int MAG_IN_TICK = 19;
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> NEXT_FIRE = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> NEXT_DRY_FIRE = new WeakHashMap<>();
    private static final Map<ServerPlayer, ItemStack> EQUIPPED = new WeakHashMap<>();
    public static final int DRY_FIRE_COOLDOWN = 6;
    /**
     * The unload's swap (docs/native_guns/magazine_system_v1.md 3.1): this many ticks into the unload clip the magazine,
     * caught in the support hand, becomes the held item (tools/author-p9-01-v3-native-animations.mjs UNLOAD.swap, 1.05 s;
     * the gun is off screen by then). Cut short before it, the magazine stays in the gun.
     */
    public static final int UNLOAD_SWAP_TICK = 21;
    /** Guns just unloaded into the hand: their clip already put them away, the switch plays no put_away of its own. */
    private static final java.util.Set<ItemStack> UNLOADED = java.util.Collections.newSetFromMap(new WeakHashMap<>());

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
        boolean unload;
        int magInTick;
        int slot;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    }

    private NativeGunActions() {}

    public static void clearForDataReload() {
        NativeFireControl.clear();
        SESSIONS.forEach((p,s)->{s.item.stopTriggeredAnim(p,s.id,NativeGunItem.ACTION_CONTROLLER,s.clip);if(s.reload)NativeMagazines.endSwap(s.stack);});
        SESSIONS.clear(); NEXT_FIRE.clear(); NEXT_DRY_FIRE.clear();
    }

    /** A reload clip's variant played while aiming down the sights. */
    public static final String ADS_SUFFIX = "_ads";
    /**
     * The draw that chambers a round (docs/native_guns/p9_first_draw_chamber_v1.md): played instead of draw when the gun
     * has the clip, rounds in the magazine and none chambered ({@link NativeGunAmmo#chambered}).
     */
    public static final String FIRST_DRAW = "first_draw";

    /** The draw clip for the held gun: first_draw when it chambers a round, else draw. */
    static String drawClip(ItemStack held, NativeGunItem item) {
        return item.supportsClip(FIRST_DRAW) && !NativeGunAmmo.chambered(held)
                && NativeGunAmmo.read(held, item.definition()) > 0 ? FIRST_DRAW : "draw";
    }

    public static String animationName(boolean reload) { return reload ? "reload_tactical" : "shoot"; }

    /** A reload of this stack is under way for the player (its going-out and coming-in magazines stay shown). */
    public static boolean reloading(ServerPlayer player, ItemStack stack) {
        Session s = SESSIONS.get(player);
        return s != null && s.reload && s.stack == stack;
    }

    /** Read-only guard for atomic attachment changes, sharing the existing action lock. */
    public static boolean busy(ServerPlayer player) { return SESSIONS.containsKey(player); }

    /** Handling actions share the same server lock as fire/reload. */
    public static boolean operation(ServerPlayer player, String clip) {
        if(FieldAttachmentOperation.pending(player))return false;
        if (!player.isAlive() || player.isSpectator() || SESSIONS.containsKey(player)
                || (clip.equals("inspect") && player.containerMenu != player.inventoryMenu)
                || !(player.getMainHandItem().getItem() instanceof NativeGunItem item)
                || !(clip.equals("inspect") ? item.inspectClip() != null
                    : clip.equals(FIRST_DRAW) ? item.supportsClip(FIRST_DRAW)
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
        state.cues.addAll(magazineCues(NativeGunAnimations.cues(item.animationAsset(), state.clip), state.stack, item, state.clip));
        SESSIONS.put(player, state); syncInventory(player);
        item.triggerAnim(player, state.id, NativeGunItem.ACTION_CONTROLLER, state.clip);
        return true;
    }

    /**
     * A reload request's {@code shotId} (unused by a reload otherwise): the reload key tapped (take the magazine out, or put
     * one in when none is fitted); 0, the default, is the reload proper (the key held: swap in another magazine).
     */
    public static final long RELOAD_TAP = 1;

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
        request(player, reload, slot, shotId, false);
    }
    public static void request(ServerPlayer player, boolean reload, int slot,long shotId, boolean aiming) {
        if(FieldAttachmentOperation.pending(player))return;
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
        // Shot cadence follows the stack's current fire mode (fire.mode_overrides.<mode>.interval_ticks).
        int interval = definition.fireIntervalTicks();
        if (reload) {
            // a gun that loads from magazine items (user 2026-10-22, docs/native_guns/magazine_system_v1.md 3): a tap takes
            // the fitted magazine out into the hand, to be loaded there; held, or a tap with none fitted, puts in the
            // fullest loaded one from the rig or the pockets (NativeMagazines)
            if (NativeMagazines.system(definition)) {
                boolean tap = shotId == RELOAD_TAP;
                if (tap && NativeMagazines.ejectable(held, definition)) {
                    unload(player, held, item, slot);
                    return;
                }
                if (!NativeMagazines.canReload(player, definition)) {
                    String why = NativeMagazines.inBackpack(player, definition) ? "magazine_in_backpack" : tap ? null : "no_spare_magazine";
                    if (why != null) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("hud.apocalypse_firstlight.gun." + why), true);
                    return;
                }
            } else if (NativeGunAmmo.read(held, definition) >= NativeGunAmmo.capacity(held,definition)
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
            interval = definition.fireIntervalTicks(NativeFireModes.current(held, definition));
            if (!NativeGunAmmo.consumeOne(held, definition)) return;
            int ammoAfter = NativeGunAmmo.read(held, definition);
            if (ammoAfter == 0) NativeGunAmmo.setChambered(held, definition, false);   // the last round leaves the chamber empty
            // the slide feeds the next round from the magazine: chambered while any are left (NativeMagazines: 16+1)
            else if (NativeMagazines.system(definition) && !NativeGunAmmo.chambered(held)) NativeGunAmmo.setChambered(held, definition, true);
            NEXT_FIRE.put(player, now + interval);
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
        // A fitted magazine may override the reload (clip, length, commit tick); fixed for this session.
        state.end = now + (reload ? item.reloadTicks(held, state.reloadStartedEmpty) : interval);
        state.magInTick = reload ? item.magInTick(held, state.reloadStartedEmpty) : 0;
        state.slot = slot;
        state.reload = reload;
        state.lastShot = lastShot;
        state.clip = reload ? item.reloadClip(held, state.reloadStartedEmpty)
                : item.fireClip(state.lastShot);
        // started while aiming: the clip's ADS variant (<clip>_ads, same length and beats; docs/dev/p9_reload_inspect_v1.md
        // 2.7) when the gun has one, and the client keeps the aim through it (NativeGunAds); from growth Lv 5 (user
        // 2026-10-18: "ADS换弹应该给到5级"), below it the hip clip plays and the aim drops for it.
        int gunLevel = reload ? com.antaurora.apofirstlight.survivor.SurvivorGrowth.gunLevel(player, definition.weaponClass()) : 0;
        boolean ads = reload && com.antaurora.apofirstlight.skeleton.SkeletonPackets.aiming(player) && item.animationAsset() != null
                && gunLevel >= com.antaurora.apofirstlight.survivor.SurvivorGrowth.ADS_RELOAD_LEVEL
                && NativeGunAnimations.hasClip(item.animationAsset(), state.clip + ADS_SUFFIX);
        // below the practised tier: the tier's own slower reload (an experienced ADS one while aiming), a novice sometimes
        // fumbling (NativeReloadTiers; docs/dev/p9_reload_inspect_v1.md 5.1); its clip sets the length and the moment the
        // rounds go in. Without a tier clip the practised one (or its ADS variant) plays.
        if (reload) {
            var tiered = NativeReloadTiers.choose(item.animationAsset(), state.clip, gunLevel, player.getRandom(), ads ? ADS_SUFFIX : "");
            if (tiered != null) {
                state.clip = tiered.clip();
                state.end = now + tiered.ticks();
                state.magInTick = tiered.magInTick();
            } else if (ads) state.clip += ADS_SUFFIX;
        }
        if (reload && item.animationAsset() != null)
            state.cues.addAll(magazineCues(NativeGunAnimations.cues(item.animationAsset(), state.clip), held, item, state.clip));
        state.dimension = player.level().dimension();
        SESSIONS.put(player, state);
        if (reload && NativeMagazines.system(definition)) NativeMagazines.beginSwap(player, held, definition);
        if (reload) com.antaurora.apofirstlight.stamina.PlayerStamina.reload(player, definition.id());
        // Deliver the per-stack render identity before its animation trigger.
        syncInventory(player);
        if (!reload) {
            // GeckoLib 4.7.4 does not reset a running controller when the same trigger name
            // arrives. Its standard stop + trigger pair makes every accepted shot start at 0s.
            item.stopTriggeredAnim(player, state.id, NativeGunItem.ACTION_CONTROLLER, state.clip);
        }
        item.triggerAnim(player, state.id, NativeGunItem.ACTION_CONTROLLER, state.clip);
        if (!reload) {
            // heard exactly as far as the shot's noise (NativeGunShot emits the same radius); a suppressed one 16 blocks; the
            // shooter hears it at the ear
            var shotNoise = NativeGunNoise.resolve(player.getMainHandItem(), definition);
            com.antaurora.apofirstlight.noise.RangedSound.play(player.serverLevel(), player.getEyePosition(), shotNoise.fireSound(item),
                    SoundSource.PLAYERS, shotNoise.soundRadius(), 1.0F, 1.0F, player);
            var shot = NativeGunShot.executeWithTrajectories(player, definition,
                    aiming && !player.isSprinting() && !player.isUsingItem());
            var hit = shot.representative();
            com.antaurora.apofirstlight.network.AflNetwork.sendNativeShot(player, slot, state.id, hit.point(),shotId,
                    definition.hitEffect().onHit(hit.entity()!=null), shot.endpoints());
            com.antaurora.apofirstlight.stamina.PlayerStamina.shot(player, definition.ammoType());
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
                if (interrupted.reload) NativeMagazines.endSwap(interrupted.stack);
                interrupted.item.stopTriggeredAnim(player, interrupted.id, NativeGunItem.ACTION_CONTROLLER, interrupted.clip);
            }
            if (old != null && old.getItem() instanceof NativeGunItem outgoing
                    && outgoing.animationAsset() != null && !UNLOADED.remove(old)) {
                // Vanilla may stop rendering the old stack before the complete clip is visible.
                long id = GeoItem.getOrAssignId(old, player.serverLevel());
                outgoing.triggerAnim(player, id, NativeGunItem.ACTION_CONTROLLER, "put_away");
                for (var cue : NativeGunAnimations.cues(outgoing.animationAsset(), "put_away"))
                    if (cue.tick() <= 1) sound(player, java.util.Objects.requireNonNull(
                            net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(cue.sound())));
            }
            if (held.getItem() instanceof NativeGunItem gun) operation(player, drawClip(held, gun));
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
            if (state.reload) NativeMagazines.endSwap(state.stack);
            NativeFireControl.cancel(player);
            return;
        }
        long now = player.server.getTickCount();
        if (state.lastShot && state.item.definition().actionType() != NativeActionType.BREAK_ACTION
                && !state.lockHandoffPlayed
                && now >= state.start + NativeShotAnimationPolicy.LAST_SHOT_HANDOFF_TICKS) {
            // The authored shot reaches its rearward mechanical pose during the first tick.
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
            // The authored magazine-in time is part of each gun's presentation definition (or the magazine's override).
            long ammoCommitTick = state.start + state.magInTick;
            if (!state.inPlayed && now >= ammoCommitTick) {
                if (load(player, state.stack, state.item.definition()))
                    com.antaurora.apofirstlight.survivor.SurvivorGrowth.gunReload(player, state.item.definition().weaponClass());
                syncInventory(player);
                state.inPlayed = true;
            }
        } else if (state.reload) {
            if (!state.outPlayed && now >= state.start + MAG_OUT_TICK) {
                sound(player, AflSounds.NATIVE_GUN_MAGAZINE_OUT.get());
                state.outPlayed = true;
            }
            if (!state.inPlayed && now >= state.start + state.item.definition().magInTick()) {
                if (load(player, state.stack, state.item.definition()))
                    com.antaurora.apofirstlight.survivor.SurvivorGrowth.gunReload(player, state.item.definition().weaponClass());
                syncInventory(player);
                sound(player, AflSounds.NATIVE_GUN_MAGAZINE_IN.get());
                state.inPlayed = true;
            }
            if (state.reloadStartedEmpty && !state.rackPlayed && now >= state.start + SLIDE_RACK_TICK) {
                sound(player, AflSounds.NATIVE_GUN_ACTION.get());
                state.rackPlayed = true;
            }
        }
        if (now >= state.end) {
            SESSIONS.remove(player);
            if (state.unload) {
                // the swap: the magazine leaves the gun for the hand (the gun to its weapon position, Loadout#toHand)
                NativeMagazines.eject(player, state.stack, state.item.definition());
                if (player.getMainHandItem() != state.stack) UNLOADED.add(state.stack);
                return;
            }
            if (state.reload) NativeMagazines.endSwap(state.stack);
            // completed only: a first draw or an empty reload cut short leaves the slide where it was, unchambered
            if (state.clip.equals(FIRST_DRAW) || state.reload && state.reloadStartedEmpty
                    && NativeGunAmmo.read(state.stack, state.item.definition()) > 0) {
                NativeGunAmmo.setChambered(state.stack, state.item.definition(), true);
                syncInventory(player);
            }
        }
        } finally { NativeFireControl.tick(player); }
    }

    /**
     * The reload key with no fuller magazine (docs/native_guns/magazine_system_v1.md 3.1): the gun's unload clip (unload_empty
     * with the slide locked back), its own sounds, the magazine into the hand at {@link #UNLOAD_SWAP_TICK}. A gun without the
     * clip hands it over at once, with its magazine-out sound.
     */
    private static void unload(ServerPlayer player, ItemStack held, NativeGunItem item, int slot) {
        NativeGunDefinition definition = item.definition();
        String clip = NativeGunAmmo.read(held, definition) == 0 ? "unload_empty" : "unload";
        if (item.animationAsset() == null || !item.supportsClip(clip) || !NativeGunAnimations.hasClip(item.animationAsset(), clip)) {
            if (!NativeMagazines.eject(player, held, definition)) return;
            var out = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation(
                    ApocalypseFirstLight.MOD_ID, definition.id().getPath() + "_magazine_out"));
            if (out != null) sound(player, out);
            return;
        }
        NativeFireControl.cancel(player);
        Session state = new Session();
        state.stack = held; state.item = item;
        state.id = GeoItem.getOrAssignId(held, player.serverLevel());
        state.start = player.server.getTickCount();
        state.end = state.start + UNLOAD_SWAP_TICK;
        state.clip = clip;
        state.operation = true; state.unload = true; state.slot = slot;
        state.dimension = player.level().dimension();
        state.cues.addAll(magazineCues(NativeGunAnimations.cues(item.animationAsset(), clip), held, item, clip));
        SESSIONS.put(player, state); syncInventory(player);
        item.triggerAnim(player, state.id, NativeGunItem.ACTION_CONTROLLER, clip);
    }

    /** The reload's magazine-in: a magazine swap (NativeMagazines) or loose rounds; whether anything went in. */
    private static boolean load(ServerPlayer player, ItemStack gun, NativeGunDefinition definition) {
        if (NativeMagazines.system(definition)) return NativeMagazines.swap(player, gun, definition);
        return NativeGunAmmo.transfer(player.getInventory(), gun, definition) > 0;
    }

    // Bound to the player entity (ClientboundSoundEntityPacket -> EntityBoundSoundInstance), not to the position the
    // action started at: a long mono action sound (HR55 reload_empty 3 s, inspect 6 s) follows the shooter instead of
    // staying behind when they turn and walk away. Same source, volume, pitch and audible range as before. Not the shot
    // itself: that one is heard as far as its noise (noise/RangedSound, 2026-10-09).
    private static void sound(ServerPlayer player, SoundEvent sound) {
        player.serverLevel().playSound(null, player, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * A clip's sounds without the magazine the gun does not have (magazine-item guns, NativeMagazines; user 2026-10-20: a
     * magazine pushed into an empty gun played the old one's release and pull-out): with no magazine fitted a reload has
     * no {@code <gun>_magazine_release} / {@code _magazine_out}, an inspect none of its four magazine sounds.
     */
    private static java.util.List<NativeGunAnimations.Cue> magazineCues(java.util.List<NativeGunAnimations.Cue> cues, ItemStack gun,
            NativeGunItem item, String clip) {
        var definition = item.definition();
        if (!NativeMagazines.system(definition) || !NativeMagazines.fitted(gun, definition).isEmpty()) return cues;
        boolean inspect = isInspect(clip);
        cues.removeIf(c -> {
            String path = c.sound().getPath();
            return path.endsWith("_magazine_release") || path.endsWith("_magazine_out")
                    || inspect && (path.endsWith("_magazine_in") || path.endsWith("_magazine_seat"));
        });
        return cues;
    }

    private static boolean isInspect(String clip) {
        return clip != null && clip.startsWith("inspect");   // inspect, inspect_empty and their _no_mag variants
    }

    private static void syncInventory(ServerPlayer player) {
        com.antaurora.apofirstlight.weight.PlayerWeightRuntime.dirty(player);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NativeFireControl.logout(player);
            Session s = SESSIONS.remove(player);
            if (s != null && s.reload) NativeMagazines.endSwap(s.stack);
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
