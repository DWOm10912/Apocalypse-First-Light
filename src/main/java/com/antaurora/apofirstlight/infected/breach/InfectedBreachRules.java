package com.antaurora.apofirstlight.infected.breach;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class InfectedBreachRules {
    public static final TagKey<Block> INFECTED_BREAKABLE = TagKey.create(
            Registries.BLOCK, new ResourceLocation(ApocalypseFirstLight.MOD_ID, "infected_breakable")
    );

    private InfectedBreachRules() {
    }

    public static boolean canBreak(BlockState state) {
        if (!state.is(INFECTED_BREAKABLE)) {
            return false;
        }
        Block block = state.getBlock();
        return state.is(BlockTags.LEAVES)
                || block instanceof GlassBlock
                || block == Blocks.GLASS_PANE
                || block instanceof StainedGlassPaneBlock
                || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.FENCE_GATES);
    }

    public static int breakTicks(BlockState state) {
        Block block = state.getBlock();
        if (state.is(BlockTags.LEAVES) || block == Blocks.GLASS_PANE || block instanceof StainedGlassPaneBlock) {
            return 20;
        }
        if (block instanceof GlassBlock) {
            return 30;
        }
        if (state.is(BlockTags.WOODEN_TRAPDOORS)) {
            return 40;
        }
        if (state.is(BlockTags.FENCE_GATES)) {
            return 45;
        }
        if (state.is(BlockTags.WOODEN_DOORS)) {
            return 50;
        }
        return 60; // Wooden fences are the remaining approved category.
    }
}
