package com.antaurora.apofirstlight.inventory.grid;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The gun sling (GunSling): fitted at a long gun's rear swivel on the Z page or the gun maintenance bench. */
public final class GunSlingItem extends Item implements com.antaurora.apofirstlight.weapon.NativeAttachment {
    public GunSlingItem(Properties properties) {
        super(properties);
    }

    @Override
    public Slot slot() {
        return Slot.SLING;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.gun_sling").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
