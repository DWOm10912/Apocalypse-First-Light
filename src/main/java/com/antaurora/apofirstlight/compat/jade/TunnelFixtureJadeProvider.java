package com.antaurora.apofirstlight.compat.jade;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.block.TunnelFixtureBlock;
import com.antaurora.apofirstlight.blockentity.TunnelFixtureBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade for a tunnel fixture (block/TunnelFixtureBlock, Tunnel parts removable V1.1): its variant (the egress sign's way
 * and distance, the name plate's tunnel, a guide sign's legend) and what the crowbar does with it (takes it down as an item, or strips it for
 * materials) with the time; of the part aimed at in the cell (the block names its first part: another one's name is
 * added). Client only: every cell's block entity is synced.
 */
public enum TunnelFixtureJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    public static final ResourceLocation UID = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "tunnel_fixture");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlock() instanceof TunnelFixtureBlock block)) return;
        TunnelFixtureBlock.Kind kind = block.kind();
        String b = null;
        // the part aimed at in the cell (a cell may hold several)
        if (accessor.getBlockEntity() instanceof TunnelFixtureBlockEntity be) {
            TunnelFixtureBlockEntity.Entry e = be.nearest(accessor.getHitResult().getLocation());
            if (e != null) {
                kind = e.kind;
                b = e.placement.bone();
                // a small part's name comes from its item (getCloneItemStack); a large one has none, the block names the cell's first
                if (e.kind != block.kind() && !e.kind.item()) tooltip.add(Component.translatable("block.apocalypse_firstlight." + e.kind.id()));
            }
        }
        if (b != null) {
            if (kind == TunnelFixtureBlock.Kind.EGRESS_SIGN && b.length() > 1)
                tooltip.add(Component.translatable("tooltip.apocalypse_firstlight.tunnel_egress_sign." + (b.charAt(0) == 'l' ? "left" : "right"), b.substring(1)));
            else if (kind == TunnelFixtureBlock.Kind.NAME_PLATE) tooltip.add(Component.translatable("tooltip.apocalypse_firstlight.tunnel_name." + b));
            else if (kind == TunnelFixtureBlock.Kind.SIDE_ROAD_SIGN || kind == TunnelFixtureBlock.Kind.CURVE_SIGN)
                tooltip.add(Component.translatable("tooltip.apocalypse_firstlight." + kind.id() + "." + ("l".equals(b) ? "left" : "right")));
            // a guide sign's legend as it reads (English in every language, as the sign)
            else if (kind == TunnelFixtureBlock.Kind.GUIDE_SIGN)
                tooltip.add(Component.literal(com.antaurora.apofirstlight.worldgen.network.road.GuideSigns.describe(b)));
        }
        tooltip.add(Component.translatable("jade.apocalypse_firstlight.tunnel_fixture." + (kind.item() ? "item" : "materials"), kind.seconds));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
