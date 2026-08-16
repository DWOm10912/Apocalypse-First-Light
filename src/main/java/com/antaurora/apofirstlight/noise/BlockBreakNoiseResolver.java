package com.antaurora.apofirstlight.noise;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockBreakNoiseResolver {
    public enum Category { METAL, GLASS, STONE, WOOD, SOFT, FALLBACK }
    private static final TagKey<Block> METAL = tag("noise_metal_blocks"), GLASS = tag("noise_glass_blocks"), STONE = tag("noise_stone_blocks"), WOOD = tag("noise_wood_blocks"), SOFT = tag("noise_soft_blocks");
    private BlockBreakNoiseResolver() {}
    public static Result resolve(BlockState state) {
        if (state.is(METAL)) return new Result(Category.METAL,12); if(state.is(GLASS)) return new Result(Category.GLASS,10); if(state.is(STONE)) return new Result(Category.STONE,8); if(state.is(WOOD)) return new Result(Category.WOOD,6); if(state.is(SOFT)) return new Result(Category.SOFT,4); return new Result(Category.FALLBACK,6);
    }
    private static TagKey<Block> tag(String path){return TagKey.create(Registries.BLOCK,new ResourceLocation(ApocalypseFirstLight.MOD_ID,path));}
    public record Result(Category category,double radius) {}
}
