package com.antaurora.apofirstlight.mixin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBiomes;
import com.antaurora.apofirstlight.worldgen.StartupEcologyState;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import terrablender.api.RegionType;

@Mixin(value=Climate.ParameterList.class,priority=900)
public abstract class ClimateParameterListMixin {
    @Unique private volatile StartupEcologyState apocalypse$ecology;

    @Dynamic("TerraBlender initializes each world's parameter list before biome resolution")
    @Inject(method="initializeForTerraBlender",at=@At("RETURN"),remap=false)
    private void apocalypse$initializeEcology(RegistryAccess registries,RegionType type,long seed,CallbackInfo ci) {
        if(type!=RegionType.OVERWORLD)return;
        StartupEcologyState existing=apocalypse$ecology;
        if(existing!=null) {
            if(existing.seed()!=seed)throw new IllegalStateException("Startup ParameterList reused across seeds");
            return;
        }
        var biomes=registries.registryOrThrow(Registries.BIOME);
        apocalypse$ecology=new StartupEcologyState(seed,biomes.getHolderOrThrow(Biomes.PLAINS),
                biomes.getHolderOrThrow(AflBiomes.IRRADIATED_WOODLAND));
        ApocalypseFirstLight.LOGGER.info("[AFL STARTUP ECOLOGY CONTEXT] binding=ParameterList seed={} identity={} region={} threadLocalRequired=false",
                seed,Integer.toHexString(System.identityHashCode(this)),type);
    }

    @Dynamic("TerraBlender's cancellable MultiNoise HEAD uses this positional lookup")
    @Inject(method="findValuePositional",at=@At("RETURN"),cancellable=true,remap=false)
    private void apocalypse$resolveEcology(Climate.TargetPoint target,int x,int y,int z,CallbackInfoReturnable<Object> ci) {
        StartupEcologyState state=apocalypse$ecology;
        if(state==null||!(ci.getReturnValue() instanceof Holder<?> raw))return;
        @SuppressWarnings("unchecked") Holder<Biome> original=(Holder<Biome>)raw;
        Holder<Biome> resolved=state.resolve(x,y,z,original);
        if(resolved!=original)ci.setReturnValue(resolved);
    }
}
