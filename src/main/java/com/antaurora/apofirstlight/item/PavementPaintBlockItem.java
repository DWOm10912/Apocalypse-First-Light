package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.PavementPaintBlock;
import com.antaurora.apofirstlight.block.PavementPaintBlock.Paint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Pavement Markings V1: the item of a tinted paint tile, in white, yellow or blue. The colour rides in the vanilla
 * BlockStateTag (BlockItem applies it on placement); no tag is white, the block's default. The creative tab lists all three,
 * the icon is tinted (client/PavementMarkingClient) and the name says the colour.
 */
public final class PavementPaintBlockItem extends BlockItem {
    public PavementPaintBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public ItemStack colored(Paint paint) {
        ItemStack stack = new ItemStack(this);
        if (paint != Paint.WHITE) stack.getOrCreateTagElement(BLOCK_STATE_TAG).putString(PavementPaintBlock.COLOR.getName(), paint.getSerializedName());
        return stack;
    }

    public List<ItemStack> variants() {
        return List.of(colored(Paint.WHITE), colored(Paint.YELLOW), colored(Paint.BLUE));
    }

    public static Paint paint(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(BLOCK_STATE_TAG);
        if (tag != null) for (Paint paint : Paint.values())
            if (paint.getSerializedName().equals(tag.getString(PavementPaintBlock.COLOR.getName()))) return paint;
        return Paint.WHITE;
    }

    @Override
    public Component getName(ItemStack stack) {
        Paint paint = paint(stack);
        if (paint == Paint.WHITE) return super.getName(stack);
        return Component.translatable("item.apocalypse_firstlight.pavement_paint.colored", super.getName(stack),
                Component.translatable("paint.apocalypse_firstlight." + paint.getSerializedName()));
    }
}
