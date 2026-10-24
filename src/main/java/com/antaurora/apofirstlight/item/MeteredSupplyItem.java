package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.tooltip.AflEquipmentTooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A supply used by the amount (docs/gameplay/furniture_recipes_v1.md 1.3, 1.4): the can of walnut wood finish counted in
 * mL, the box of nails counted one by one. What is left rides in the stack's tag ({@link #LEFT_KEY}); none means full (a
 * can or box as found). Never stacks: two part-used cans would share one tag. The furniture recipes will take from it
 * ({@link #setLeft}; used up, the stack is gone); until then it is only found and carried. The bar shows what is left once
 * some has been used.
 */
public final class MeteredSupplyItem extends Item {
    public static final String LEFT_KEY = "AflLeft";

    private final int full;
    private final boolean litres;

    /** full: what a new one holds (mL when litres, else pieces). */
    public MeteredSupplyItem(int full, boolean litres) {
        super(new Properties().stacksTo(1));
        this.full = full;
        this.litres = litres;
    }

    public int full() {
        return full;
    }

    /** Kept in mL (true) or pieces (false): a recipe's litres or pieces line must match (WorkbenchRecipeData). */
    public boolean litres() {
        return litres;
    }

    /** What is left in this stack (mL or pieces); 0 for any other item. */
    public static int left(ItemStack stack) {
        if (!(stack.getItem() instanceof MeteredSupplyItem m)) return 0;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(LEFT_KEY, Tag.TAG_INT) ? Mth.clamp(tag.getInt(LEFT_KEY), 0, m.full) : m.full;
    }

    /** Sets what is left; nothing left uses the stack up. */
    public static void setLeft(ItemStack stack, int left) {
        if (!(stack.getItem() instanceof MeteredSupplyItem m)) return;
        if (left <= 0) stack.shrink(stack.getCount());
        else if (left >= m.full) stack.removeTagKey(LEFT_KEY);
        else stack.getOrCreateTag().putInt(LEFT_KEY, left);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return left(stack) < full;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * left(stack) / full);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xC8B48C;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        int left = left(stack);
        String id = getDescriptionId().substring("item.".length());
        lines.add(Component.translatable("tooltip." + id + ".left", litres ? AflEquipmentTooltip.number(left / 1000.0) : Integer.toString(left))
                .withStyle(ChatFormatting.GRAY));
        AflEquipmentTooltip.addDescription(lines, "tooltip." + id + ".description");
    }
}
