package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.DieselGeneratorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the diesel standby generator (docs/machines/diesel_standby_generator_v1.md): the state word, the load and the
 * fuel. Other cells reach the master through the RectMultiblockBlock redirect (PriceSignJadeProvider#registerRedirect).
 */
public enum DieselGeneratorJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "diesel_generator");
    private static final String STATE = "AflGensetState", LOAD = "AflGensetLoad", FUEL = "AflGensetFuel";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof DieselGeneratorBlockEntity generator)) return;
        data.putString(STATE, switch (generator.mode()) { case RUNNING -> "running"; case CRANKING -> "cranking"; default -> generator.fault() ? "fault" : "off"; });
        data.putInt(LOAD, Math.round(generator.loadFe()));
        data.putInt(FUEL, generator.fuelLitres());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(STATE)) return;
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.diesel_generator." + data.getString(STATE)));
        if (data.getString(STATE).equals("running"))
            tooltip.add(Component.translatable("jade.apocalypse_firstlight.diesel_generator.load", data.getInt(LOAD), DieselGeneratorBlockEntity.RATED));
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.diesel_generator.fuel", data.getInt(FUEL), DieselGeneratorBlockEntity.TANK));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
