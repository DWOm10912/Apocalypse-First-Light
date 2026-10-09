package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.CurbBlock;
import com.antaurora.apofirstlight.block.CurbBlock.Paint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Curbs V1: the item of a curb block, bare, red or yellow. The paint rides in the vanilla BlockStateTag (BlockItem applies it
 * on placement, the loot table copies it back); no tag is bare concrete. The creative tab lists all three, the icon is tinted
 * (client/CurbModel) and the name says the paint.
 */
public final class CurbBlockItem extends BlockItem {
    public CurbBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public ItemStack painted(Paint paint) {
        ItemStack stack = new ItemStack(this);
        if (paint != Paint.NONE) stack.getOrCreateTagElement(BLOCK_STATE_TAG).putString(CurbBlock.PAINT.getName(), paint.getSerializedName());
        return stack;
    }

    public List<ItemStack> variants() {
        return List.of(painted(Paint.NONE), painted(Paint.RED), painted(Paint.YELLOW));
    }

    public static Paint paint(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(BLOCK_STATE_TAG);
        if (tag != null) for (Paint paint : Paint.values())
            if (paint.getSerializedName().equals(tag.getString(CurbBlock.PAINT.getName()))) return paint;
        return Paint.NONE;
    }

    @Override
    public Component getName(ItemStack stack) {
        Paint paint = paint(stack);
        if (paint == Paint.NONE) return super.getName(stack);
        return Component.translatable("item.apocalypse_firstlight.pavement_paint.colored", super.getName(stack),
                Component.translatable("paint.apocalypse_firstlight." + paint.getSerializedName()));
    }
}
