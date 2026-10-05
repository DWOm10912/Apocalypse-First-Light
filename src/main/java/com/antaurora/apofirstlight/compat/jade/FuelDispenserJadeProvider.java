package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.FuelDispenserBlock;
import com.antaurora.apofirstlight.block.FuelDispenserSumpBlock;
import com.antaurora.apofirstlight.blockentity.FuelDispenserBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the fuel dispenser's two line buffers (docs/models/fuel_station_sump_v1.md): gasoline and diesel, mB / capacity.
 * Only the master cell has the block entity, so a ray-trace callback points Jade at it from any cell. The dispenser sump
 * gets one line of its own: whether a dispenser stands on it ({@link Sump}).
 */
public enum FuelDispenserJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fuel_dispenser");
    private static final String GASOLINE = "AflLineGasoline", DIESEL = "AflLineDiesel";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof FuelDispenserBlockEntity dispenser)) return;
        data.putInt(GASOLINE, dispenser.line(FuelDispenserBlock.Grade.GASOLINE).getFluidAmount());
        data.putInt(DIESEL, dispenser.line(FuelDispenserBlock.Grade.DIESEL).getFluidAmount());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(GASOLINE)) return;
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.fuel_dispenser.gasoline", data.getInt(GASOLINE), FuelDispenserBlockEntity.LINE_MB)
                .withStyle(data.getInt(GASOLINE) > 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.fuel_dispenser.diesel", data.getInt(DIESEL), FuelDispenserBlockEntity.LINE_MB)
                .withStyle(data.getInt(DIESEL) > 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
    }

    /** Any other cell: the accessor of the master cell, so the server data and the tooltip come from there. */
    public static void registerRedirect(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (!(accessor instanceof BlockAccessor block) || !(block.getBlock() instanceof FuelDispenserBlock)
                    || block.getBlockState().getValue(FuelDispenserBlock.CELL) == FuelDispenserBlock.Cell.A0) return accessor;
            Level level = block.getLevel();
            BlockPos root = FuelDispenserBlock.rootPosition(block.getPosition(), block.getBlockState());
            BlockState rootState = level.getBlockState(root);
            BlockEntity rootEntity = level.getBlockEntity(root);
            if (!(rootState.getBlock() instanceof FuelDispenserBlock) || rootEntity == null) return accessor;
            BlockHitResult moved = block.getHitResult().withPosition(root);
            Accessor<?> redirected = registration.blockAccessor().from(block).hit(moved).blockState(rootState).blockEntity(rootEntity).build();
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

    /** The dispenser sump: connected to a dispenser above or not (client-side, from the block states). */
    public enum Sump implements IBlockComponentProvider {
        INSTANCE;

        public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fuel_dispenser_sump");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlock() instanceof FuelDispenserSumpBlock)) return;
            boolean connected = FuelDispenserSumpBlock.dispenserAbove(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState()) != null;
            tooltip.add(Component.translatable("jade.apocalypse_firstlight.fuel_dispenser_sump." + (connected ? "connected" : "none"))
                    .withStyle(connected ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
