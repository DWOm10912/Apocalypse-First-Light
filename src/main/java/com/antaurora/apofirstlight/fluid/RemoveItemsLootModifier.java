package com.antaurora.apofirstlight.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Takes the listed items out of every loot roll (2026-10-05: data/apocalypse_firstlight/loot_modifiers/remove_buckets.json,
 * the vanilla buckets: AFL's fluids are 1 mB = 1 L and carried in containers by real volume, docs/models/fuel_containers_v1.md).
 */
public final class RemoveItemsLootModifier extends LootModifier {
    public static final Codec<RemoveItemsLootModifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance)
            .and(ForgeRegistries.ITEMS.getCodec().listOf().fieldOf("items").forGetter(m -> m.items))
            .apply(instance, RemoveItemsLootModifier::new));

    private final List<Item> items;

    public RemoveItemsLootModifier(LootItemCondition[] conditions, List<Item> items) {
        super(conditions);
        this.items = items;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.removeIf(stack -> items.contains(stack.getItem()));
        return generatedLoot;
    }

    @Override
    public Codec<RemoveItemsLootModifier> codec() {
        return CODEC;
    }
}
