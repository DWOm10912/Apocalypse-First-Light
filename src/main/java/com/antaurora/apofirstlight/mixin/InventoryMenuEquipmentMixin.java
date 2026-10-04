package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.AflEquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AFL equipment slots on the vanilla inventory page (docs/gameplay/equipment_slots_v1.md): back, wrist and ears in the
 * empty column right of the player model, above the off-hand slot (x 77, y 8 / 26 / 44), appended after the vanilla
 * 46 slots so every vanilla index stays where it was. Shift-click from the inventory puts a fitting item into an empty
 * one of them first.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuEquipmentMixin extends AbstractContainerMenu {
    private static final String[] AFL_ICONS = {"empty_slot_back", "empty_slot_wrist", "empty_slot_ears"};

    protected InventoryMenuEquipmentMixin(MenuType<?> type, int id) { super(type, id); }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void afl$addEquipmentSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        for (int i = 0; i < AflEquipmentSlots.INVENTORY_PAGE.size(); i++)
            this.addSlot(new AflEquipmentSlot(owner, AflEquipmentSlots.INVENTORY_PAGE.get(i), AFL_ICONS[i], 77, 8 + i * 18));
    }

    /** From the main inventory, hotbar or off-hand (9..45): one item into the first empty AFL slot that takes it. */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void afl$shiftIntoEquipment(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index < 9 || index > 45) return;
        var source = this.slots.get(index);
        if (!source.hasItem()) return;
        var stack = source.getItem();
        for (var slot : this.slots) {
            if (!(slot instanceof AflEquipmentSlot equipment) || equipment.hasItem() || !equipment.mayPlace(stack)) continue;
            var original = stack.copy();
            equipment.set(stack.split(1));
            if (stack.isEmpty()) source.setByPlayer(ItemStack.EMPTY);
            else source.setChanged();
            cir.setReturnValue(original);
            return;
        }
    }
}
