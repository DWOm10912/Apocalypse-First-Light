package com.antaurora.apofirstlight.weight;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.*;

/** Server-thread transient cache. Mutations only invalidate; computation happens once at tick END. */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class PlayerWeightRuntime {
    public static final int VERIFY_INTERVAL = 10, SYNC_INTERVAL = 5;
    private static final Map<ServerPlayer, Cache> CACHES = new HashMap<>();
    private static long sequence;
    private static final class Cache {
        boolean dirty = true, forceSync = true;
        long lastVerification = Long.MIN_VALUE, lastSync = Long.MIN_VALUE;
        int inventoryChanges = -1;
        AbstractContainerMenu menu;
        ContainerListener listener;
        ItemStack cursor = ItemStack.EMPTY;
        MassResult mass;
        EncumbranceState state, lastSent;
    }
    private PlayerWeightRuntime() {}
    public static void dirty(ServerPlayer player) { CACHES.computeIfAbsent(player, p -> new Cache()).dirty = true; }
    public static void force(ServerPlayer player) {
        var c = CACHES.computeIfAbsent(player, p -> new Cache()); c.dirty = true; c.forceSync = true;
    }
    public static void invalidateAll() { CACHES.values().forEach(c -> { c.dirty = true; c.forceSync = true; }); }
    public static EncumbranceState state(ServerPlayer player) {
        var c = CACHES.get(player); return c == null ? null : c.state;
    }
    public static MassResult breakdown(ServerPlayer player) {
        var c = CACHES.get(player); return c == null ? null : c.mass;
    }
    private static void remove(ServerPlayer player) {
        var c = CACHES.remove(player);
        if (c != null && c.menu != null) c.menu.removeSlotListener(c.listener);
    }
    private static void observeMenu(ServerPlayer player, Cache c) {
        if (c.menu == player.containerMenu) return;
        if (c.menu != null) c.menu.removeSlotListener(c.listener);
        c.menu = player.containerMenu;
        c.listener = new ContainerListener() {
            @Override public void slotChanged(AbstractContainerMenu menu, int index, ItemStack stack) {
                if (menu.getSlot(index).container == player.getInventory()
                        || (menu == player.inventoryMenu && index >= 1 && index <= 4)
                        || (menu instanceof CraftingMenu && index >= 1 && index <= 9)) c.dirty = true;
            }
            @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) {}
        };
        c.menu.addSlotListener(c.listener); c.dirty = true;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long tick = server.getTickCount();
        for (var player : server.getPlayerList().getPlayers()) {
            var c = CACHES.computeIfAbsent(player, p -> new Cache());
            observeMenu(player, c);
            int changes = player.getInventory().getTimesChanged();
            if (changes != c.inventoryChanges) { c.inventoryChanges = changes; c.dirty = true; }
            var cursor = player.containerMenu.getCarried();
            if (!ItemStack.matches(cursor, c.cursor)) { c.cursor = cursor.copy(); c.dirty = true; }
            var data = ItemMassData.snapshot();
            boolean enabled = !player.isCreative() && !player.isSpectator();
            if (c.state == null || c.state.penaltiesEnabled() != enabled || c.state.dataRevision() != data.revision()) {
                c.dirty = true; c.forceSync = true;
            }
            if ((c.dirty || tick - c.lastVerification >= VERIFY_INTERVAL) && c.lastVerification != tick) {
                c.mass = PlayerMassSources.calculate(player, data);
                c.state = EncumbranceState.calculate(c.mass, data, enabled);
                c.lastVerification = tick; c.dirty = false;
            }
            if (c.state != null && (c.forceSync || (!c.state.equals(c.lastSent) && tick - c.lastSync >= SYNC_INTERVAL))) {
                if (c.forceSync) WeightPackets.sendPolicy(player, data);
                WeightPackets.sendState(player, c.state, ++sequence);
                c.lastSent = c.state; c.lastSync = tick; c.forceSync = false;
            }
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) force(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) remove(p); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if (e.getEntity() instanceof ServerPlayer p) force(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) force(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        if (e.getOriginal() instanceof ServerPlayer p) remove(p);
        if (e.getEntity() instanceof ServerPlayer p) force(p);
    }
    @SubscribeEvent public static void death(LivingDeathEvent e) { if (e.getEntity() instanceof ServerPlayer p) force(p); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {
        for (var p : List.copyOf(CACHES.keySet())) remove(p);
        sequence = 0;
    }
}
