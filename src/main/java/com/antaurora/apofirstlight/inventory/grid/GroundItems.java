package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import com.antaurora.apofirstlight.mixin.ItemEntityAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/**
 * Ground items (docs/ui/afl_inventory_grid_v0_plan.md 12), Survival / Adventure: nothing is picked up by walking over it.
 * What lies near the player is listed on the inventory page (taken onto the cursor, or straight in with shift) and offered
 * by a prompt when looked at (client/GroundItemPick); both ask the server through {@link GridActions}, checked here.
 * Creative and spectators keep vanilla's pickup.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID)
public final class GroundItems {
    /** How far around the player's box ground items are listed and may be taken (blocks). */
    public static final double REACH = 2.5, REACH_UP = 1.0, REACH_DOWN = 1.5;
    /** The server's extra allowance over the client's list (movement between the click and the packet). */
    private static final double SLACK = 1.0;
    /** ItemEntity's "never" pickup delay (display items). */
    private static final int NEVER = 32767;
    /** Pickup modes of {@link #quick}: the normal pickup order (wear / weapon position / grids), or the grids only. */
    public static final int AUTO = 0, STOW = 1;

    private GroundItems() {}

    /** Walking over an item never picks it up in Survival / Adventure (vanilla and Forge's pickup hook). */
    @SubscribeEvent
    public static void noWalkOverPickup(EntityItemPickupEvent event) {
        if (!PlayerStorageCapacity.unrestricted(event.getEntity())) event.setCanceled(true);
    }

    /** The item may be taken by this player at all: not empty, not a display item, not reserved for someone else. */
    public static boolean pickable(Player player, ItemEntity item) {
        if (!item.isAlive() || item.getItem().isEmpty()) return false;
        var access = (ItemEntityAccessor) item;
        if (access.afl$pickupDelay() == NEVER) return false;
        var target = access.afl$target();
        return target == null || target.equals(player.getUUID());
    }

    /** The space ground items are listed in: around the player's box, more below than above (a slope, a step down). */
    public static AABB area(Player player, double slack) {
        var box = player.getBoundingBox();
        return new AABB(box.minX - REACH - slack, box.minY - REACH_DOWN - slack, box.minZ - REACH - slack,
                box.maxX + REACH + slack, box.maxY + REACH_UP + slack, box.maxZ + REACH + slack);
    }

    /** Not behind a wall: the eye sees the item's middle (or its top). */
    public static boolean inSight(Player player, ItemEntity item) {
        var eye = player.getEyePosition();
        var box = item.getBoundingBox();
        for (double y : new double[]{box.getCenter().y, box.maxY + 0.05}) {
            var to = new Vec3(item.getX(), y, item.getZ());
            var hit = player.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= to.distanceToSqr(eye) - 1e-4) return true;
        }
        return false;
    }

    /** The items the player can take now, nearest first (the inventory page's "nearby" list). */
    public static List<ItemEntity> nearby(Player player) {
        var eye = player.getEyePosition();
        return player.level().getEntitiesOfClass(ItemEntity.class, area(player, 0),
                        e -> pickable(player, e) && inSight(player, e)).stream()
                .sorted(Comparator.comparingDouble(e -> e.position().distanceToSqr(eye))).toList();
    }

    /** Server: the item with this id, if this player may take it from where they stand. */
    static ItemEntity reachable(ServerPlayer player, int id) {
        if (!(player.level().getEntity(id) instanceof ItemEntity item)) return null;
        if (!pickable(player, item) || !area(player, SLACK).intersects(item.getBoundingBox()) || !inSight(player, item)) return null;
        return item;
    }

    /** Server: the item onto the cursor, all of it ({@code half}: the larger half). Inventory page, empty cursor. */
    static boolean pick(ServerPlayer player, int id, boolean half) {
        var menu = player.inventoryMenu;
        if (!menu.getCarried().isEmpty()) return false;
        var item = reachable(player, id);
        if (item == null) return false;
        var stack = item.getItem().copy();
        int count = half && stack.getCount() > 1 ? (stack.getCount() + 1) / 2 : stack.getCount();
        menu.setCarried(stack.split(count));
        taken(player, item, stack, count);
        return true;
    }

    /**
     * Server: as much of the item as goes in ({@link #AUTO}: the normal pickup order, worn when its slot is free, a weapon
     * to its empty position, the rest pockets → rig → backpack, then the empty hand; {@link #STOW}: the grids only). Says
     * "no room" when nothing did.
     */
    public static boolean quick(ServerPlayer player, int id, int mode) {
        var item = reachable(player, id);
        if (item == null) return false;
        var stack = item.getItem().copy();
        int before = stack.getCount();
        if (mode == STOW) PlayerGrids.insert(player, stack);
        else {
            player.getInventory().add(stack);
            // what found no place goes into the empty hand (a long gun without a sling, a big item with full grids)
            var inventory = player.getInventory();
            if (!stack.isEmpty() && inventory.items.get(HotbarRoles.HAND_SLOT).isEmpty()
                    && !PlayerStorageCapacity.rejects(inventory, HotbarRoles.HAND_SLOT, stack))
                inventory.items.set(HotbarRoles.HAND_SLOT, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
        }
        int moved = before - stack.getCount();
        if (moved <= 0) {
            player.displayClientMessage(Component.translatable("hint.apocalypse_firstlight.loadout.no_room"), true);
            return false;
        }
        taken(player, item, stack, moved);
        return true;
    }

    /** Server: the cursor stack (button 1: one of it) onto the ground at the player's feet. */
    static boolean drop(ServerPlayer player, boolean one) {
        var menu = player.inventoryMenu;
        var carried = menu.getCarried();
        if (carried.isEmpty()) return false;
        var out = one ? carried.split(1) : carried.copyAndClear();
        menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        player.drop(out, true, false);
        return true;
    }

    /** What was taken: the pickup animation and statistics, and the rest left lying (or the entity gone). */
    private static void taken(ServerPlayer player, ItemEntity item, ItemStack rest, int count) {
        var kind = item.getItem().getItem();
        player.take(item, count);
        player.awardStat(Stats.ITEM_PICKED_UP.get(kind), count);
        player.onItemPickup(item);
        if (rest.isEmpty()) item.discard();
        else item.setItem(rest);
    }
}
