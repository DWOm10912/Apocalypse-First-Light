package com.antaurora.apofirstlight.vehicle;

import com.antaurora.apofirstlight.inventory.PlayerStorageCapacity;
import com.antaurora.apofirstlight.inventory.grid.PlayerGrids;
import com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A vehicle's key (docs/vehicles/t15_hotwire_v1_design.md section 9; 2026-10-21): one key per vehicle, bound to it by the
 * vehicle's UUID ({@link #TAG_VEHICLE}). Every vehicle model has its own key item with its maker's badge on the head, as in
 * real life (user 2026-10-21; the T15's is pickup_t15_key). A key only starts its own vehicle; one from the Creative tab
 * is bound to nothing and starts none. No recipe (user 2026-10-20: none before the recipe rework).
 */
public final class VehicleKeyItem extends Item {
    public static final String TAG_VEHICLE = "Vehicle";
    private static final Map<String, VehicleKeyItem> BY_MODEL = new HashMap<>();
    private final String model;

    /** model: the vehicle model this key belongs to (its tooltip key, tooltip.apocalypse_firstlight.vehicle_key.<model>). */
    public VehicleKeyItem(Properties properties, String model) {
        super(properties);
        this.model = model;
        BY_MODEL.put(model, this);
    }

    /** A new key for this vehicle: the model's own key item, bound to the vehicle. */
    public static ItemStack forVehicle(UUID vehicle, String model) {
        VehicleKeyItem item = BY_MODEL.get(model);
        if (item == null) throw new IllegalArgumentException("no key item for vehicle model " + model);
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putUUID(TAG_VEHICLE, vehicle);
        return stack;
    }

    /** Whether this stack is the key of that vehicle. */
    public static boolean fits(ItemStack stack, UUID vehicle) {
        if (!(stack.getItem() instanceof VehicleKeyItem)) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_VEHICLE) && tag.getUUID(TAG_VEHICLE).equals(vehicle);
    }

    /** Whether the player carries the vehicle's key: in the hands, the grids (pockets, rig, backpack) or the inventory. */
    public static boolean carries(Player player, UUID vehicle) {
        if (fits(player.getMainHandItem(), vehicle)) return true;
        for (ItemStack stack : PlayerGrids.stacks(player)) if (fits(stack, vehicle)) return true;
        for (ItemStack stack : player.getInventory().items) if (fits(stack, vehicle)) return true;
        return false;
    }

    /** Server: takes the vehicle's key from the player (hands first, then the grids, then the inventory); whether it had it. */
    public static boolean take(ServerPlayer player, UUID vehicle) {
        if (fits(player.getMainHandItem(), vehicle)) {
            player.getMainHandItem().shrink(1);
            return true;
        }
        PlayerGrids.View view = PlayerGrids.view(player);
        for (ItemStack stack : view.stacks()) {
            if (!fits(stack, vehicle)) continue;
            stack.shrink(1);
            view.commit();
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!fits(stack, vehicle)) continue;
            stack.shrink(1);
            return true;
        }
        return false;
    }

    /**
     * Server: puts the key with the player: the grids (pockets, rig, backpack), in Creative the inventory. Whether it went
     * in (a full grid leaves it where it was).
     */
    public static boolean give(ServerPlayer player, ItemStack key) {
        if (PlayerStorageCapacity.unrestricted(player)) return player.getInventory().add(key);
        return PlayerGrids.insert(player, key) && key.isEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        boolean bound = tag != null && tag.hasUUID(TAG_VEHICLE);
        AflEquipmentTooltip.addDescription(lines, bound
                ? "tooltip.apocalypse_firstlight.vehicle_key." + model
                : "tooltip.apocalypse_firstlight.vehicle_key.blank");
    }
}
