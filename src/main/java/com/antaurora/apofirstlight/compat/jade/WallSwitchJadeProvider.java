package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.WallSwitchBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the wall switch (Building Wiring V1 phase 3, docs/gameplay/building_wiring_v1.md): 开 / 关, and 未接线 while it
 * works no light group (its WIRED mark, kept by the panel: the switch has no block entity for server data).
 */
public enum WallSwitchJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "wall_switch");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState s = accessor.getBlockState();
        if (!(s.getBlock() instanceof WallSwitchBlock)) return;
        String k = "jade.apocalypse_firstlight.wall_switch.";
        tooltip.add(Component.translatable(k + (s.getValue(WallSwitchBlock.ON) ? "on" : "off")));
        if (!s.getValue(WallSwitchBlock.WIRED)) tooltip.add(Component.translatable(k + "unwired"));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
