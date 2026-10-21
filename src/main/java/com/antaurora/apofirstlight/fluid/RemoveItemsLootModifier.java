package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.world.VanillaRetirement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Takes the listed items, and the items of an optional tag, out of every loot roll (chests, mobs, fishing, blocks).
 * <ul>
 *   <li>2026-10-05: data/apocalypse_firstlight/loot_modifiers/remove_buckets.json, the vanilla buckets: AFL's fluids are
 *   1 mB = 1 L and carried in containers by real volume (docs/models/fuel_containers_v1.md).</li>
 *   <li>Vanilla Survival Rework V1: retired_vanilla.json, the tag apocalypse_firstlight:retired/unobtainable with
 *   "retirement": true, so it only applies while the retirement switch is on (world/VanillaRetirement).</li>
 * </ul>
 * Only freshly rolled loot is touched; stacks already in a container, on a mob or in an inventory never are.
 */
public final class RemoveItemsLootModifier extends LootModifier {
    public static final Codec<RemoveItemsLootModifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance)
            .and(ForgeRegistries.ITEMS.getCodec().listOf().optionalFieldOf("items", List.of()).forGetter(m -> m.items))
            .and(TagKey.codec(Registries.ITEM).optionalFieldOf("tag").forGetter(m -> m.tag))
            .and(Codec.BOOL.optionalFieldOf("retirement", false).forGetter(m -> m.retirement))
            .apply(instance, RemoveItemsLootModifier::new));

    private final List<Item> items;
    private final Optional<TagKey<Item>> tag;
    private final boolean retirement;

    public RemoveItemsLootModifier(LootItemCondition[] conditions, List<Item> items, Optional<TagKey<Item>> tag, boolean retirement) {
        super(conditions);
        this.items = items;
        this.tag = tag;
        this.retirement = retirement;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (retirement && !VanillaRetirement.enabled()) return generatedLoot;
        generatedLoot.removeIf(stack -> items.contains(stack.getItem()) || tag.isPresent() && stack.is(tag.get()));
        return generatedLoot;
    }

    @Override
    public Codec<RemoveItemsLootModifier> codec() {
        return CODEC;
    }
}
