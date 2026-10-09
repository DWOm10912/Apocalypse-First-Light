package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.LightPoleBaseBlockEntity;
import com.antaurora.apofirstlight.energy.MachineBalanceManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade for a parking-lot light pole's base (docs/models/site_lighting_v1.md): how full its power buffer is. */
public enum LightPoleJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "light_pole_base");
    private static final String CHARGE = "AflLightPoleCharge";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof LightPoleBaseBlockEntity base) {
            int capacity = Math.max(1, MachineBalanceManager.siteLight().capacityFe());
            data.putInt(CHARGE, Math.min(100, Math.round(100.0F * base.energyStored() / capacity)));
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.contains(CHARGE)) tooltip.add(Component.translatable("jade.apocalypse_firstlight.light_pole_base.charge", data.getInt(CHARGE)));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
