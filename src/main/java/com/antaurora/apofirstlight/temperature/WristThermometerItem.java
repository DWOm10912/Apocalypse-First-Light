package com.antaurora.apofirstlight.temperature;

import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
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
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import java.util.List;

/**
 * Wrist thermometer (Temperature V1): worn in the wrist slot (AflEquipmentSlots.WRIST), it shows the air temperature
 * around the temperature dial (TemperatureReadout); drawn on the left wrist. Right-click equips it.
 */
public final class WristThermometerItem extends Item implements ICurioItem {
    public WristThermometerItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (AflEquipmentSlots.equipFromHand(player, hand, AflEquipmentSlots.WRIST))
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.1F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        AflEquipmentTooltip.addDescription(lines, "tooltip.apocalypse_firstlight.wrist_thermometer.description");
    }
}
