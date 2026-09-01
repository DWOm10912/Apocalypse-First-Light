package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.contamination.EnvironmentContaminationLootModifier;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                    ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<Codec<? extends IGlobalLootModifier>> ENVIRONMENT_CONTAMINATION =
            SERIALIZERS.register("environment_contamination",
                    () -> EnvironmentContaminationLootModifier.CODEC);

    private AflLootModifiers() {
    }
}
