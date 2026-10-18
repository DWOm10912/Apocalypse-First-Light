package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.weapon.NativeGunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The weapons a player carries on the body, for the other players' clients (docs/ui/afl_inventory_grid_v0_plan.md 10):
 * their inventories are not synced, so the client's CarriedWeaponsLayer could only draw its own player. What is drawn is
 * worked out here, by the same rules as for the own player: the slung primary (key 1, a gun with a sling) on the back
 * and its sling across the body, the pistol (key 2) in the holster while one is worn; a gun in the hand is not on the
 * body (the sling still is), nothing for a spectator. Creative too, by the same rules and the guns' roles (user
 * 2026-10-17: in Creative a P9 put back into the holster vanished; Creative had been left out). Sent to the players tracking the carrier when it changes
 * (checked every tick, the stacks compared with their NBT; a gun in the hand is left out, so firing it sends nothing) and
 * to a player who starts tracking the carrier.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class CarriedWeaponsSync {
    /** On the back (empty: none or in the hand), the sling drawn, in the holster (empty: none or in the hand). */
    public record Shown(ItemStack back, boolean sling, ItemStack thigh) {
        public static final Shown NONE = new Shown(ItemStack.EMPTY, false, ItemStack.EMPTY);

        boolean same(Shown other) {
            return sling == other.sling && ItemStack.matches(back, other.back) && ItemStack.matches(thigh, other.thigh);
        }
    }

    private static final Map<UUID, Shown> SENT = new HashMap<>();
    /** Client: what the server last sent per entity id (other players only). */
    private static final Map<Integer, Shown> RECEIVED = new ConcurrentHashMap<>();

    private CarriedWeaponsSync() {}

    public static Shown of(Player player) {
        if (player.isSpectator()) return Shown.NONE;
        var inventory = player.getInventory();
        // by the weapon's role as well: Survival's hotbar only takes a primary at index 0 and a pistol at index 1
        // (HotbarRoles#accepts), Creative's takes anything
        var primary = inventory.items.get(HotbarRoles.PRIMARY_SLOT);
        boolean sling = primary.getItem() instanceof NativeGunItem && HotbarRoles.weaponRole(primary) == HotbarRoles.Role.PRIMARY
                && GunSling.has(primary);
        var back = sling && inventory.selected != HotbarRoles.PRIMARY_SLOT ? primary : ItemStack.EMPTY;
        var pistol = inventory.items.get(HotbarRoles.PISTOL_SLOT);
        var thigh = inventory.selected != HotbarRoles.PISTOL_SLOT && pistol.getItem() instanceof NativeGunItem
                && HotbarRoles.weaponRole(pistol) == HotbarRoles.Role.SECONDARY && HotbarRoles.holsterWorn(player) ? pistol : ItemStack.EMPTY;
        return new Shown(back, sling, thigh);
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var now = of(player);
        var sent = SENT.get(player.getUUID());
        if (sent != null && sent.same(now)) return;
        var copy = new Shown(now.back().copy(), now.sling(), now.thigh().copy());
        SENT.put(player.getUUID(), copy);
        GridPackets.sendCarried(player, null, copy);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer carrier && event.getEntity() instanceof ServerPlayer viewer)
            GridPackets.sendCarried(carrier, viewer, of(carrier));   // encoded at once: the live stacks are fine
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    /** Client. */
    public static void receive(int entity, Shown shown) {
        RECEIVED.put(entity, shown);
    }

    /** Client: the carried weapons of another player as last sent, or null. */
    public static Shown received(int entity) {
        return RECEIVED.get(entity);
    }

    /** Client: on leaving a world. */
    public static void clearReceived() {
        RECEIVED.clear();
    }
}
