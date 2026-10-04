package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.equipment.HearingProtection;
import com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Worn in the ears slot (AflEquipmentSlots.EARS, a Curios slot shown on the inventory page), so it goes on together with
 * a helmet; drawn on the head like a head item. Right-click equips it (swapping out what is there). One worn on the HEAD
 * slot by an older save still protects (HearingProtectionManager).
 */
public final class SimpleHearingProtectionItem extends Item implements ICurioItem, HearingProtection {
    public SimpleHearingProtectionItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (AflEquipmentSlots.equipFromHand(player, hand, AflEquipmentSlots.EARS))
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
    }

    @Override
    public float worldSoundMultiplier(ItemStack stack) {
        return 0.50F;
    }

    @Override
    public float impulseProtectionMultiplier(ItemStack stack) {
        return 0.50F;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        AflEquipmentTooltip.addDescription(lines, "tooltip.apocalypse_firstlight.simple_hearing_protection.description");
    }
}
