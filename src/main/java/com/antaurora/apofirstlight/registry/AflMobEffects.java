package com.antaurora.apofirstlight.registry;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.radiation.RadiationSicknessEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AflMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ApocalypseFirstLight.MOD_ID);

    public static final RegistryObject<MobEffect> RADIATION_SICKNESS =
            MOB_EFFECTS.register("radiation_sickness", RadiationSicknessEffect::new);
    /** Thirst V1: stomach bug from raw water; the symptoms are applied by thirst/PlayerThirst. */
    public static final RegistryObject<MobEffect> GASTROENTERITIS =
            MOB_EFFECTS.register("gastroenteritis", com.antaurora.apofirstlight.thirst.GastroenteritisEffect::new);
    /** Gasoline- / diesel-soaked (2026-10-05): from standing on a fuel stain; drips; the burning bonus comes with ignition (fluid/FuelSpills). */
    public static final RegistryObject<MobEffect> GASOLINE_SOAKED =
            MOB_EFFECTS.register("gasoline_soaked", () -> new com.antaurora.apofirstlight.fluid.FuelSoakedEffect(false));
    public static final RegistryObject<MobEffect> DIESEL_SOAKED =
            MOB_EFFECTS.register("diesel_soaked", () -> new com.antaurora.apofirstlight.fluid.FuelSoakedEffect(true));

    private AflMobEffects() {
    }
}
