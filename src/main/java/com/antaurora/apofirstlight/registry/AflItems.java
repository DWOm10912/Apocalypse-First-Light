package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<Item> REINFORCED_CONCRETE = ITEMS.register("reinforced_concrete",
            () -> new BlockItem(AflBlocks.REINFORCED_CONCRETE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_BLOCK = ITEMS.register("steel_block",
            () -> new BlockItem(AflBlocks.STEEL_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_GRATE = ITEMS.register("steel_grate",
            () -> new BlockItem(AflBlocks.STEEL_GRATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_PLATE = ITEMS.register("steel_plate",
            () -> new BlockItem(AflBlocks.STEEL_PLATE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STEEL_SCRAP = ITEMS.register("steel_scrap",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CONCRETE_RUBBLE = ITEMS.register("concrete_rubble",
            () -> new Item(new Item.Properties()));

    private AflItems() {
    }
}
