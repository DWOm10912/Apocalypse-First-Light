package com.antaurora.apofirstlight.contamination;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import com.antaurora.apofirstlight.radiation.RadiationManager;

/** Applies environmental contamination once when block or container loot is generated. */
public final class EnvironmentContaminationLootModifier extends LootModifier {
    public static final Codec<EnvironmentContaminationLootModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance).apply(instance, EnvironmentContaminationLootModifier::new));

    public EnvironmentContaminationLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
                                                           LootContext context) {
        if (generatedLoot.isEmpty()) return generatedLoot;
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null) return generatedLoot;

        boolean blockDrop = context.hasParam(LootContextParams.BLOCK_STATE);
        boolean containerLoot = isContainerLootTable(context.getQueriedLootTableId());
        if (!blockDrop && !containerLoot) return generatedLoot;

        BlockPos originPos = BlockPos.containing(origin);
        double ambient = RadiationManager.getAmbientRadiationForContamination(context.getLevel(), originPos);
        ItemContamination.Level level = ItemContamination.getTargetLevel(ambient);
        if (containerLoot && !blockDrop) {
            level = rollContainerLevel(level, context.getRandom());
        }

        for (ItemStack stack : generatedLoot) {
            ItemContamination.applyMinimumLevel(stack, level);
        }
        return generatedLoot;
    }

    private static boolean isContainerLootTable(ResourceLocation tableId) {
        return tableId != null && tableId.getPath().startsWith("chests/");
    }

    public static ItemContamination.Level rollContainerLevel(ItemContamination.Level baseLevel,
                                                              RandomSource random) {
        if (baseLevel == null || baseLevel == ItemContamination.Level.CLEAN) {
            return ItemContamination.Level.CLEAN;
        }
        return rollContainerLevel(baseLevel, random.nextFloat());
    }

    /** Pure overload used by deterministic probes. */
    public static ItemContamination.Level rollContainerLevel(ItemContamination.Level baseLevel, float roll) {
        int base = baseLevel == null ? 0 : baseLevel.value();
        if (base == 0) return ItemContamination.Level.CLEAN;
        int offset = roll < 0.15F ? -1 : roll < 0.85F ? 0 : 1;
        return ItemContamination.Level.fromValue(base + offset);
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
