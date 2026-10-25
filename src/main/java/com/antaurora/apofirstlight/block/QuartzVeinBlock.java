package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.worldgen.underground.RockType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The mineralized quartz vein (U3b, design 6.2): what metal it carries is its district's (GeologyPass#veinMetal), a block
 * state, so each looks like its ore (gold: rusty, with a few flecks of visible gold in rich ore; copper: chalcopyrite;
 * lead: galena) and gives that raw ore.
 */
public class QuartzVeinBlock extends OreRockBlock {
    public enum Metal implements StringRepresentable {
        GOLD("gold", "gold_quartz", 400, 1500), COPPER("copper", "raw_chalcopyrite", 250, 1000), LEAD("lead", "raw_galena", 250, 1000);

        private final String id, raw;
        /** litres a piece of raw ore comes out of, {lean, rich} */
        final double[] litres;

        Metal(String id, String raw, double rich, double lean) {
            this.id = id;
            this.raw = raw;
            this.litres = new double[]{lean, rich};
        }

        @Override
        public String getSerializedName() { return id; }
    }

    public static final EnumProperty<Metal> METAL = EnumProperty.create("metal", Metal.class);
    private final Item[] items = new Item[Metal.values().length];

    public QuartzVeinBlock(Properties properties) {
        super(RockType.GRANITE, 2650, 45, "granite", "field_stone", false, 1000, 250, new String[]{"gold_quartz"}, new double[]{1}, properties);
        registerDefaultState(defaultBlockState().setValue(METAL, Metal.GOLD));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(METAL);
    }

    @Override
    public Item raw(BlockState state, RandomSource random) {
        Metal m = state.getValue(METAL);
        Item i = items[m.ordinal()];
        if (i == null) items[m.ordinal()] = i = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ApocalypseFirstLight.MOD_ID, m.raw));
        return i;
    }

    @Override
    public double litresPerPiece(BlockState state) { return state.getValue(METAL).litres[state.getValue(GRADE).ordinal()]; }
}
