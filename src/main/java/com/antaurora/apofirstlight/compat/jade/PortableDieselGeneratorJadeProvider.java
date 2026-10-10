package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.PortableDieselGeneratorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade for the portable diesel generator (docs/machines/portable_diesel_generator_v1.md): the state word, the load, the fuel. */
public enum PortableDieselGeneratorJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "portable_diesel_generator");
    private static final String STATE = "AflPortableState", LOAD = "AflPortableLoad", FUEL = "AflPortableFuel";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof PortableDieselGeneratorBlockEntity generator)) return;
        data.putString(STATE, generator.running() ? generator.tripped() ? "tripped" : "running" : "off");
        data.putInt(LOAD, Math.round(generator.loadFe()));
        data.putInt(FUEL, generator.fuelLitres());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(STATE)) return;
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.portable_diesel_generator." + data.getString(STATE)));
        if (data.getString(STATE).equals("running"))
            tooltip.add(Component.translatable("jade.apocalypse_firstlight.portable_diesel_generator.load", data.getInt(LOAD), PortableDieselGeneratorBlockEntity.RATED));
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.portable_diesel_generator.fuel", data.getInt(FUEL), PortableDieselGeneratorBlockEntity.TANK));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
