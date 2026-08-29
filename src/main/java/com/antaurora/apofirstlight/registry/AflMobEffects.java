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

    private AflMobEffects() {
    }
}
