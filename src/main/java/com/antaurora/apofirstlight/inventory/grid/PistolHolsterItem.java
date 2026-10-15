package com.antaurora.apofirstlight.inventory.grid;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.inventory.HotbarRoles;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * The pistol holster (docs/ui/afl_inventory_grid_v0_plan.md 10): worn in its own Curios slot ({@code holster}), it lets
 * the pistol be carried at key 2. It does not come off while a pistol sits in it. Right-click puts it on. Placeholder
 * icon; the pistol is drawn at the hip, the holster itself is not.
 */
public final class PistolHolsterItem extends Item implements ICurioItem {
    public PistolHolsterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (AflEquipmentSlots.equipFromHand(player, hand, AflEquipmentSlots.HOLSTER))
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return !(slotContext.entity() instanceof Player player) || player.isCreative() || player.isSpectator()
                || player.getInventory().getItem(HotbarRoles.PISTOL_SLOT).isEmpty();
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
    }
}
