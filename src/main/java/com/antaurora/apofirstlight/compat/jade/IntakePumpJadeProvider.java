package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.IntakePumpBlock;
import com.antaurora.apofirstlight.blockentity.IntakePumpBlockEntity;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the intake pump (docs/models/intake_pump_v1.md): its status (running, or why not), the liquid under the front
 * cell, the FE buffer and the discharge buffer. Only the bank cell has the block entity, so a ray-trace callback points
 * Jade at the bank cell when the front cell is aimed at.
 */
public enum IntakePumpJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "intake_pump");
    private static final String STATUS = "AflPumpStatus";
    private static final String SOURCE = "AflPumpSource";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof IntakePumpBlockEntity pump)) return;
        data.putString(STATUS, pump.status().key());
        FluidStack source = pump.sourceFluid();
        ResourceLocation id = source.isEmpty() ? null : ForgeRegistries.FLUIDS.getKey(source.getFluid());
        if (id != null) data.putString(SOURCE, id.toString());
        data.putInt(MachineJadeServerDataProvider.ENERGY_STORED, pump.energyStored());
        data.putInt(MachineJadeServerDataProvider.ENERGY_CAPACITY, MachineBalanceManager.intakePump().capacityFe());
        FluidStack fluid = pump.buffer().getFluid();
        if (!fluid.isEmpty()) data.put(FluidTankJadeServerDataProvider.FLUID, fluid.writeToNBT(new CompoundTag()));
        data.putInt(FluidTankJadeServerDataProvider.FLUID_AMOUNT, fluid.isEmpty() ? 0 : fluid.getAmount());
        data.putInt(FluidTankJadeServerDataProvider.FLUID_CAPACITY, pump.buffer().getCapacity());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(STATUS, Tag.TAG_STRING)) return;
        tooltip.remove(Identifiers.UNIVERSAL_ENERGY_STORAGE);
        String status = data.getString(STATUS);
        ChatFormatting colour = switch (status) {
            case "running" -> ChatFormatting.GREEN;
            case "off" -> ChatFormatting.GRAY;
            default -> ChatFormatting.GOLD;
        };
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.intake_pump.status",
                Component.translatable("jade.apocalypse_firstlight.intake_pump.status." + status).withStyle(colour)));
        Fluid source = data.contains(SOURCE, Tag.TAG_STRING) ? ForgeRegistries.FLUIDS.getValue(new ResourceLocation(data.getString(SOURCE))) : null;
        Component sourceName = source == null ? Component.translatable("jade.apocalypse_firstlight.intake_pump.source.none")
                : new FluidStack(source, 1).getDisplayName();
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.intake_pump.source", sourceName));
        MachineJadeComponentProvider.addEnergy(tooltip, data);
        FluidTankJadeComponentProvider.appendFluid(tooltip, data);
    }

    /** The front cell: the accessor of the bank cell, so the server data and the tooltip come from there. */
    public static void registerRedirect(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (!(accessor instanceof BlockAccessor block) || !(block.getBlock() instanceof IntakePumpBlock)
                    || IntakePumpBlock.isBank(block.getBlockState())) return accessor;
            Level level = block.getLevel();
            BlockPos bank = IntakePumpBlock.bankPosition(block.getPosition(), block.getBlockState());
            BlockState bankState = level.getBlockState(bank);
            BlockEntity bankEntity = level.getBlockEntity(bank);
            if (!(bankState.getBlock() instanceof IntakePumpBlock) || bankEntity == null) return accessor;
            BlockHitResult moved = block.getHitResult().withPosition(bank);
            Accessor<?> redirected = registration.blockAccessor().from(block).hit(moved).blockState(bankState).blockEntity(bankEntity).build();
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
