package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.DistributionPanelBlockEntity;
import com.antaurora.apofirstlight.wiring.WiringNode;
import com.antaurora.apofirstlight.wiring.WiringSupply;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the Distribution Panel and the load center (Building Wiring V1 phase 2, docs/gameplay/building_wiring_v1.md):
 * 范围供电 for a LEGACY_AREA panel; for a WIRED one only what is wrong: a circuit joined to another panel, wire running into
 * unloaded chunks.
 */
public enum DistributionPanelJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "distribution_panel");
    private static final String LEGACY = "AflPanelLegacy", CONFLICT = "AflPanelConflict", PARTIAL = "AflPanelPartial";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof DistributionPanelBlockEntity panel)) return;
        data.putBoolean(LEGACY, !panel.wired());
        data.putInt(CONFLICT, panel.conflictMask());
        WiringSupply.Result r = panel.wiring();
        data.putBoolean(PARTIAL, r != null && r.partial);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(LEGACY)) return;
        String k = "jade.apocalypse_firstlight.distribution_panel.";
        if (data.getBoolean(LEGACY)) { tooltip.add(Component.translatable(k + "legacy")); return; }
        int conflict = data.getInt(CONFLICT);
        for (int c = 1; c <= WiringNode.BREAKERS; c++) if ((conflict & 1 << c) != 0) tooltip.add(Component.translatable(k + "conflict", c));
        if (data.getBoolean(PARTIAL)) tooltip.add(Component.translatable(k + "partial"));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
