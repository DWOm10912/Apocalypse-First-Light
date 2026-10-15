package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.EmergencyRadio;
import com.antaurora.apofirstlight.cabin.EmergencyRadioBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the emergency radio (docs/models/emergency_radio_v1.md): on (playing), on without charge, or off; the charge
 * (percent of a full radio's 20 minutes).
 */
public enum EmergencyRadioJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "emergency_radio");
    private static final String CHARGE = "AflRadioCharge";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof EmergencyRadioBlockEntity radio) data.putInt(CHARGE, radio.charge());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(CHARGE)) return;
        var s = accessor.getBlockState();
        String k = "jade.apocalypse_firstlight.emergency_radio.";
        tooltip.add(Component.translatable(k + (!s.getValue(EmergencyRadio.Radio.ON) ? "off" : s.getValue(EmergencyRadio.Radio.LIT) ? "on" : "empty")));
        int pct = (int) Math.ceil(100.0 * data.getInt(CHARGE) / EmergencyRadio.CAPACITY);
        tooltip.add(Component.translatable(k + "charge", pct));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
