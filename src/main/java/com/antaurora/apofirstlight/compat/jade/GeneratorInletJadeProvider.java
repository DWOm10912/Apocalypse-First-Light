package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.GeneratorInletBoxBlockEntity;
import com.antaurora.apofirstlight.energy.PlugCord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Jade for the generator inlet box (docs/models/generator_inlet_v1.md): where the cord's plug is, the feed, no panel. */
public enum GeneratorInletJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "generator_inlet_box");
    private static final String CORD = "AflInletCord", FEED = "AflInletFeed", PANEL = "AflInletPanel";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof GeneratorInletBoxBlockEntity inlet)) return;
        PlugCord cord = inlet.plugCord();
        data.putString(CORD, cord.host() != null ? "plugged" : cord.carrier() != null ? "carried" : "coiled");
        data.putFloat(FEED, inlet.feed());
        data.putBoolean(PANEL, inlet.panel() != null);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(CORD)) return;
        String k = "jade.apocalypse_firstlight.generator_inlet_box.";
        tooltip.add(Component.translatable(k + data.getString(CORD)));
        if (!data.getBoolean(PANEL)) tooltip.add(Component.translatable(k + "no_panel"));
        else if (data.getString(CORD).equals("plugged")) tooltip.add(Component.translatable(k + "feed", String.format("%.1f", data.getFloat(FEED))));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
