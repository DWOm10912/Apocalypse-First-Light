package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.cabin.BoxStove;
import com.antaurora.apofirstlight.cabin.BoxStoveBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for the box stove (docs/models/box_stove_v1.md): burning or out, the firewood left (in-game hours: 1000 ticks an
 * hour), what cooks on the top and how far along it is.
 */
public enum BoxStoveJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "box_stove");
    private static final String LIT = "AflStoveLit", FUEL = "AflStoveFuel", COOK = "AflStoveCook";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof BoxStoveBlockEntity stove)) return;
        data.putBoolean(LIT, accessor.getBlockState().getValue(BoxStove.Stove.LIT));
        data.putInt(FUEL, stove.fuel());
        int[] cook = new int[BoxStove.SPOTS.length];
        for (int k = 0; k < cook.length; k++) cook[k] = stove.item(k).isEmpty() ? -1 : Math.round(100 * stove.cooked(k));
        data.putIntArray(COOK, cook);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(LIT)) return;
        String k = "jade.apocalypse_firstlight.box_stove.";
        tooltip.add(Component.translatable(k + (data.getBoolean(LIT) ? "lit" : "out")));
        int fuel = data.getInt(FUEL);
        if (fuel > 0) tooltip.add(Component.translatable(k + "fuel", String.format(java.util.Locale.ROOT, "%.1f", fuel / 1000.0)));
        if (!(accessor.getBlockEntity() instanceof BoxStoveBlockEntity stove)) return;
        int[] cook = data.getIntArray(COOK);
        for (int i = 0; i < cook.length && i < BoxStove.SPOTS.length; i++) {
            ItemStack s = stove.item(i);
            if (cook[i] < 0 || s.isEmpty()) continue;
            tooltip.add(cook[i] > 0 ? Component.translatable(k + "cooking", s.getHoverName(), cook[i]) : s.getHoverName());
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
