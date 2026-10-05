package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.UndergroundFuelTankBlock;
import com.antaurora.apofirstlight.blockentity.UndergroundFuelTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the underground fuel tank (docs/models/underground_fuel_tank_v1.md): the fuel, the amount and a fluid bar, as
 * the vertical fluid tank shows them (FluidTankJadeComponentProvider). Only the port cell has the block entity, so a
 * ray-trace callback points Jade at the port cell whichever of the 63 cells is aimed at.
 */
public enum UndergroundFuelTankJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "underground_fuel_tank");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof UndergroundFuelTankBlockEntity tank)) return;
        FluidStack fluid = tank.tank().getFluid();
        int amount = fluid.isEmpty() ? 0 : Math.max(0, fluid.getAmount());
        if (!fluid.isEmpty()) data.put(FluidTankJadeServerDataProvider.FLUID, fluid.writeToNBT(new CompoundTag()));
        data.putInt(FluidTankJadeServerDataProvider.FLUID_AMOUNT, amount);
        data.putInt(FluidTankJadeServerDataProvider.FLUID_CAPACITY, tank.tank().getCapacity());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        FluidTankJadeComponentProvider.appendFluid(tooltip, accessor.getServerData());
    }

    /** Any cell of a tank: the accessor of its port cell (the master), so the server data and the tooltip come from there. */
    public static void registerRedirect(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (!(accessor instanceof BlockAccessor block) || !(block.getBlock() instanceof UndergroundFuelTankBlock)
                    || UndergroundFuelTankBlock.isMaster(block.getBlockState())) return accessor;
            Level level = block.getLevel();
            BlockPos master = UndergroundFuelTankBlock.masterPosition(block.getPosition(), block.getBlockState());
            BlockState masterState = level.getBlockState(master);
            BlockEntity masterEntity = level.getBlockEntity(master);
            if (!(masterState.getBlock() instanceof UndergroundFuelTankBlock) || masterEntity == null) return accessor;
            BlockHitResult moved = block.getHitResult().withPosition(master);
            Accessor<?> redirected = registration.blockAccessor().from(block).hit(moved).blockState(masterState).blockEntity(masterEntity).build();
            return redirected;
        });
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return 2000;
    }
}
