package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.entity.OfficeChairEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ApocalypseFirstLight.MOD_ID);

    /** An office chair someone sat on: rideable, rolls with W / S / A / D, stays where it was left (0.85 x 1.15 blocks). */
    public static final RegistryObject<EntityType<OfficeChairEntity>> MODERN_OFFICE_CHAIR = ENTITY_TYPES.register("modern_office_chair",
            () -> EntityType.Builder.<OfficeChairEntity>of(OfficeChairEntity::new, MobCategory.MISC)
                    .sized(0.85F, 1.15F).clientTrackingRange(10).build("modern_office_chair"));

    private AflEntities() {
    }
}
