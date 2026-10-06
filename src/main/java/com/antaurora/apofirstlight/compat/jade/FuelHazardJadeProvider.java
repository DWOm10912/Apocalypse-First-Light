package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.fluid.FuelContainers;
import com.antaurora.apofirstlight.fluid.FuelLeaks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for shot and burning fuel containers (docs/gameplay/fuel_fire_v1.md "第二阶段"): the bullet holes in it (how many
 * leak) and whether it burns. Shown on the dispenser, fluid tanks holding fuel and the underground tank; nothing while it
 * is whole and cold. The dispenser and the underground tank point Jade at their master cell (their own redirects).
 */
public enum FuelHazardJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "fuel_hazard");
    private static final String HOLES = "AflFuelHoles", LEAKING = "AflFuelLeaking", BURNING = "AflFuelBurning";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) return;
        FuelContainers.Container c = FuelContainers.at(level, accessor.getPosition());
        if (c == null) return;
        FuelLeaks.Status status = FuelLeaks.get(level).status(level, c.master());
        if (status.holes() > 0) {
            data.putInt(HOLES, status.holes());
            data.putInt(LEAKING, status.leaking());
        }
        if (status.burning()) data.putBoolean(BURNING, true);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data.getBoolean(BURNING)) tooltip.add(Component.translatable("jade.apocalypse_firstlight.fuel_hazard.burning").withStyle(ChatFormatting.RED));
        if (data.getInt(HOLES) > 0) {
            tooltip.add(Component.translatable("jade.apocalypse_firstlight.fuel_hazard.holes", data.getInt(HOLES), data.getInt(LEAKING))
                    .withStyle(data.getInt(LEAKING) > 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return 2100;
    }
}
