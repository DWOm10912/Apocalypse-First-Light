package com.antaurora.apofirstlight.inventory;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One AFL equipment slot on the vanilla inventory page: a view of the player's Curios slot of that type (index 0),
 * read and written every time, so it always matches what Curios holds (sync, death, keepInventory stay Curios').
 * One item; Curios' rules decide what fits. Hidden and inert in Creative / Spectator (the creative inventory lays the
 * menu out on its own) and while the player has no slot of the type.
 */
public final class AflEquipmentSlot extends Slot {
    private final Player player;
    private final String id;
    private final ResourceLocation icon;

    public AflEquipmentSlot(Player player, String id, String icon, int x, int y) {
        super(new SimpleContainer(1), 0, x, y);
        this.player = player;
        this.id = id;
        this.icon = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "item/" + icon);
    }

    public String slotType() { return id; }

    @Override public ItemStack getItem() { return AflEquipmentSlots.get(player, id); }
    @Override public boolean hasItem() { return !getItem().isEmpty(); }
    @Override public void set(ItemStack stack) {
        AflEquipmentSlots.stacks(player, id).ifPresent(s -> s.setStackInSlot(0, stack));
        setChanged();
    }
    @Override public void setChanged() {}
    @Override public ItemStack remove(int amount) {
        var current = getItem();
        if (current.isEmpty()) return ItemStack.EMPTY;
        var copy = current.copy();
        var taken = copy.split(amount);
        set(copy.isEmpty() ? ItemStack.EMPTY : copy);
        return taken;
    }
    @Override public int getMaxStackSize() { return 1; }
    @Override public int getMaxStackSize(ItemStack stack) { return 1; }
    @Override public boolean mayPlace(ItemStack stack) { return isActive() && AflEquipmentSlots.canHold(player, id, stack); }
    @Override public boolean mayPickup(Player who) { return isActive() && AflEquipmentSlots.canRemove(player, id, getItem()); }
    @Override public boolean isActive() {
        return !player.isCreative() && !player.isSpectator() && AflEquipmentSlots.stacks(player, id).isPresent();
    }
    @Nullable @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
    }
}
