package com.antaurora.apofirstlight.inventory.grid;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The gun sling (GunSling): fitted on the inventory page by clicking a long gun with it. */
public final class GunSlingItem extends Item {
    public GunSlingItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.apocalypse_firstlight.gun_sling").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
