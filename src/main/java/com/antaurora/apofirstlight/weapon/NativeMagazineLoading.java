package com.antaurora.apofirstlight.weapon;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Loading a magazine by hand (docs/native_guns/magazine_system_v1.md 4): a click with an empty or part-loaded magazine
 * in the hand starts it, the next click stops it. One round every {@link #ROUND_TICKS}, pressed in at {@link #PRESS_TICK}
 * of each round's cycle (the action clip's thumb push), taken from anywhere the player carries loose rounds: the hotbar
 * and main inventory, the pockets, the rig and the backpack. Walking is allowed; it stops on sprinting, when the
 * magazine leaves the hand, when it is full or when no rounds are left. Creative loads without rounds.
 * <p>
 * The magazine stack carries the loading's start ({@code AflMagazine.loading}), synced with it, so every client plays
 * the action from the same clock (client/skeleton/SkeletonActions).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class NativeMagazineLoading {
    /** One round per second (user 2026-10-19: "默认1秒1发"). */
    public static final int ROUND_TICKS = 20;
    /** The tick of each round's cycle at which the round goes in (the clip's press). */
    public static final int PRESS_TICK = 13;
    /** The last rounds of a magazine go in against the stiff spring (their own press sound). */
    public static final int STIFF_ROUNDS = 3;
    /** A use packet this soon after the last one is the held button repeating, not a new click. */
    private static final int REPEAT_TICKS = 5;

    private static final class Session {
        ItemStack stack;
        int slot;
        long start;
        int loaded;
    }

    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_USE = new WeakHashMap<>();

    private NativeMagazineLoading() {}

    /** Server: a right-click with a magazine in the main hand (NativeMagazineItem#use). */
    static void click(ServerPlayer player, ItemStack stack) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_USE.put(player, now);
        if (last != null && now - last <= REPEAT_TICKS) return;   // the button held down
        Session running = SESSIONS.get(player);
        if (running != null) {
            stop(player);
            return;
        }
        if (!(stack.getItem() instanceof NativeMagazineItem item) || item.roundGun() == null) return;
        if (NativeMagazines.rounds(stack) >= item.capacity() || !hasRound(player, item.roundGun()) || player.isSprinting()) return;
        Session s = new Session();
        s.stack = stack;
        s.slot = player.getInventory().selected;
        s.start = now;
        SESSIONS.put(player, s);
        NativeMagazines.setLoading(stack, now);
        sync(player);
    }

    /** Whether the player is loading a magazine now. */
    public static boolean loading(ServerPlayer player) { return SESSIONS.containsKey(player); }

    /** Whether this stack is the one the player is loading. */
    static boolean loading(ServerPlayer player, ItemStack stack) {
        Session s = SESSIONS.get(player);
        return s != null && s.stack == stack;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Session s = SESSIONS.get(player);
        if (s == null) return;
        if (!player.isAlive() || player.isSpectator() || player.isSprinting() || player.getMainHandItem() != s.stack
                || player.getInventory().selected != s.slot || !(s.stack.getItem() instanceof NativeMagazineItem item)
                || item.roundGun() == null) {
            stop(player);
            return;
        }
        long now = player.serverLevel().getGameTime();
        long due = s.start + PRESS_TICK + (long) s.loaded * ROUND_TICKS;
        if (now < due) return;
        int rounds = NativeMagazines.rounds(s.stack);
        if (rounds >= item.capacity() || !takeRound(player, item.roundGun())) {
            stop(player);
            return;
        }
        NativeMagazines.setRounds(s.stack, rounds + 1);
        s.loaded++;
        pressSound(player, item, rounds + 1);
        sync(player);
        if (rounds + 1 >= item.capacity() || !hasRound(player, item.roundGun())) stop(player);
    }

    /**
     * The round snapping under the lips, as it goes in: {@code <gun>_mag_load} (random takes), the last
     * {@link #STIFF_ROUNDS} against the stiff spring {@code <gun>_mag_load_last}. Quiet handling foley, no infected noise.
     */
    private static void pressSound(ServerPlayer player, NativeMagazineItem item, int rounds) {
        String gun = item.compatibleGun().getPath();
        var id = new net.minecraft.resources.ResourceLocation(ApocalypseFirstLight.MOD_ID,
                gun + (rounds > item.capacity() - STIFF_ROUNDS ? "_mag_load_last" : "_mag_load"));
        var sound = ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (sound != null) player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    private static void stop(ServerPlayer player) {
        Session s = SESSIONS.remove(player);
        if (s == null) return;
        NativeMagazines.setLoading(s.stack, -1);
        sync(player);
    }

    private static boolean matches(ItemStack stack, NativeGunDefinition gun) {
        return !stack.isEmpty() && gun.ammoType().equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private static boolean hasRound(ServerPlayer player, NativeGunDefinition gun) {
        return NativeGunAmmo.reserve(player.getInventory(), gun) > 0;
    }

    /** One loose round out of the inventory: the hotbar and main inventory first, then the grids. Creative: free. */
    private static boolean takeRound(ServerPlayer player, NativeGunDefinition gun) {
        var inventory = player.getInventory();
        if (NativeGunAmmo.infiniteReserve(inventory)) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!matches(stack, gun)) continue;
            stack.shrink(1);
            inventory.setChanged();
            return true;
        }
        var grids = PlayerGrids.view(player);
        for (ItemStack stack : grids.stacks()) {
            if (!matches(stack, gun)) continue;
            stack.shrink(1);
            grids.commit();
            return true;
        }
        return false;
    }

    private static void sync(ServerPlayer player) {
        com.antaurora.apofirstlight.weight.PlayerWeightRuntime.dirty(player);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player);
            LAST_USE.remove(player);
        }
    }
}
