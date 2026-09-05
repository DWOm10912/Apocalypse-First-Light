package com.antaurora.apofirstlight.fluid;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

public final class IndustrialWasteFluidType extends FluidType {
    public IndustrialWasteFluidType() {
        // Forge 47.4.22 WATER_TYPE uses these overrides on Properties.create().
        // Retain its shared defaults (movement, swimming, drowning, density, viscosity, temperature).
        super(Properties.create().descriptionId("fluid.apocalypse_firstlight.industrial_waste")
                .fallDistanceModifier(0.0F).canExtinguish(true).canConvertToSource(false)
                .supportsBoating(true).canHydrate(true)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH));
    }

    @Override
    @Nullable
    public BlockPathTypes getBlockPathType(FluidState state, BlockGetter level, BlockPos pos,
                                           @Nullable Mob mob, boolean canFluidLog) {
        return canFluidLog ? super.getBlockPathType(state, level, pos, mob, true) : null;
    }

    @Override
    public boolean canRideVehicleUnder(Entity vehicle, Entity rider) {
        return ForgeMod.WATER_TYPE.get().canRideVehicleUnder(vehicle, rider);
    }

    @Override
    public boolean isVaporizedOnPlacement(Level level, BlockPos pos, FluidStack stack) {
        return ForgeMod.WATER_TYPE.get().isVaporizedOnPlacement(level, pos, stack);
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            private final ResourceLocation still = new ResourceLocation(ApocalypseFirstLight.MOD_ID,
                    "fluid/industrial_waste_still");
            private final ResourceLocation flowing = new ResourceLocation(ApocalypseFirstLight.MOD_ID,
                    "fluid/industrial_waste_flow");

            @Override
            public ResourceLocation getStillTexture() {
                return still;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flowing;
            }

            @Override
            public int getTintColor() {
                return 0xFFFFFFFF;
            }
        });
    }
}
