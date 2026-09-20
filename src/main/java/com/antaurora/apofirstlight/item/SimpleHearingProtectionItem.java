package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.equipment.HearingProtection;
import com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Non-armor HEAD item: vanilla equipment swapping and CustomHeadLayer rendering. */
public final class SimpleHearingProtectionItem extends Item implements Equipable, HearingProtection {
    public SimpleHearingProtectionItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
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
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.simple_hearing_protection.world_sound")
                .withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false)));
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.simple_hearing_protection.hearing_impact")
                .withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false)));
    }
}
