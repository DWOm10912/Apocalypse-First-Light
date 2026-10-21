package com.antaurora.apofirstlight.inventory;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.grid.RigClips;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One AFL equipment slot on the vanilla inventory page: a view of the player's Curios slot of that type, read and written
 * every time, so it always matches what Curios holds (sync, death, keepInventory stay Curios'). One item; Curios' rules
 * decide what fits. A clip (id CLIP, index 0 / 1) is instead a view of one clip of the worn chest rig (RigClips): it exists
 * only while the rig has that clip, and takes what the clippable tag takes. Hidden and inert in Creative / Spectator (the
 * creative inventory lays the menu out on its own) and while the player has no slot of the type.
 */
public final class AflEquipmentSlot extends Slot {
    private final Player player;
    private final String id;
    private final int index;
    private final ResourceLocation icon;

    public AflEquipmentSlot(Player player, String id, String icon, int x, int y) {
        this(player, id, 0, icon, x, y);
    }

    public AflEquipmentSlot(Player player, String id, int index, String icon, int x, int y) {
        super(new SimpleContainer(1), 0, x, y);
        this.player = player;
        this.id = id;
        this.index = index;
        this.icon = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "item/" + icon);
    }

    public String slotType() { return id; }

    private boolean clip() { return AflEquipmentSlots.CLIP.equals(id); }
    private ItemStack rig() { return AflEquipmentSlots.get(player, AflEquipmentSlots.RIG); }

    @Override public ItemStack getItem() { return clip() ? AflEquipmentSlots.clip(player, index) : AflEquipmentSlots.get(player, id, index); }
    @Override public boolean hasItem() { return !getItem().isEmpty(); }
    @Override public void set(ItemStack stack) {
        if (clip()) RigClips.set(rig(), index, stack);
        else AflEquipmentSlots.stacks(player, id).filter(s -> index < s.getSlots()).ifPresent(s -> s.setStackInSlot(index, stack));
    }
    /** A clipped stack changed in place: write it back into the rig. */
    @Override public void setChanged() { if (clip()) RigClips.set(rig(), index, getItem()); }
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
    @Override public boolean mayPlace(ItemStack stack) {
        if (clip()) return isActive() && stack.is(RigClips.CLIPPABLE);
        return isActive() && AflEquipmentSlots.canHold(player, id, stack);
    }
    @Override public boolean mayPickup(Player who) { return isActive() && (clip() || AflEquipmentSlots.canRemove(player, id, getItem())); }
    @Override public boolean isActive() {
        if (player.isCreative() || player.isSpectator()) return false;
        if (clip()) return index < RigClips.count(rig());
        return AflEquipmentSlots.stacks(player, id).filter(s -> index < s.getSlots()).isPresent();
    }
    @Nullable @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
    }
}
