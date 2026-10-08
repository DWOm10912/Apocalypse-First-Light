package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.EmergencyLightBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade for the emergency light (docs/models/building_lights_v1.md): its battery charge. */
public enum EmergencyLightJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "emergency_light");
    private static final String BATTERY = "AflEmergencyBattery";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof EmergencyLightBlockEntity light) data.putInt(BATTERY, light.percent());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.contains(BATTERY)) tooltip.add(Component.translatable("jade.apocalypse_firstlight.emergency_light.battery", data.getInt(BATTERY)));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
