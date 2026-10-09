package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.block.ChannelLetterBlock;
import com.antaurora.apofirstlight.block.ChannelLetterBlock.Letter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.List;

/**
 * A channel letter (Fuel Stop A1 details V1): one item, the letter in the vanilla BlockStateTag (BlockItem applies it on
 * placement; no tag is A, the block's default). The creative tab lists A..Z, the name says the letter, the icon follows the
 * item property apocalypse_firstlight:letter (client/SignageClient), a broken letter drops its own (loot: copy_state).
 */
public final class ChannelLetterBlockItem extends BlockItem {
    public ChannelLetterBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public ItemStack lettered(Letter letter) {
        ItemStack stack = new ItemStack(this);
        if (letter != Letter.A) stack.getOrCreateTagElement(BLOCK_STATE_TAG).putString(ChannelLetterBlock.LETTER.getName(), letter.getSerializedName());
        return stack;
    }

    public List<ItemStack> variants() {
        return Arrays.stream(Letter.values()).map(this::lettered).toList();
    }

    public static Letter letter(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(BLOCK_STATE_TAG);
        if (tag != null) for (Letter letter : Letter.values())
            if (letter.getSerializedName().equals(tag.getString(ChannelLetterBlock.LETTER.getName()))) return letter;
        return Letter.A;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.apocalypse_firstlight.channel_letter.lettered", super.getName(stack), letter(stack).name());
    }
}
