package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.RectMultiblockBlock;
import com.antaurora.apofirstlight.blockentity.PriceSignBlockEntity;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the PRAIRIE price sign (docs/models/fuel_stop_a1_details_v1.md): how full its power buffer is. Every cell of a
 * rectangular multi-cell block (RectMultiblockBlock: the price sign, the rooftop unit) shows its master's tooltip.
 */
public enum PriceSignJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "price_sign");
    private static final String CHARGE = "AflPriceSignCharge";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof PriceSignBlockEntity sign) {
            int capacity = Math.max(1, MachineBalanceManager.priceSign().capacityFe());
            data.putInt(CHARGE, Math.min(100, Math.round(100.0F * sign.energyStored() / capacity)));
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.contains(CHARGE)) tooltip.add(Component.translatable("jade.apocalypse_firstlight.price_sign.charge", data.getInt(CHARGE)));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /** Any cell of a RectMultiblockBlock is shown as its master (where the block entity and the drop are). */
    public static void registerRedirect(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (!(accessor instanceof BlockAccessor block) || !(block.getBlock() instanceof RectMultiblockBlock<?> multi)
                    || multi.isMaster(block.getBlockState())) return accessor;
            Level level = block.getLevel();
            BlockPos master = multi.masterPosition(block.getPosition(), block.getBlockState());
            BlockState masterState = level.getBlockState(master);
            if (masterState.getBlock() != multi) return accessor;
            BlockEntity masterEntity = level.getBlockEntity(master);
            return registration.blockAccessor().from(block).hit(block.getHitResult().withPosition(master)).blockState(masterState).blockEntity(masterEntity).build();
        });
    }
}
