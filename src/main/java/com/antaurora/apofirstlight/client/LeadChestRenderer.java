package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class LeadChestRenderer extends GeoBlockRenderer<LeadChestBlockEntity> {
    public LeadChestRenderer(BlockEntityRendererProvider.Context context) {
        super(new LeadChestGeoModel());
    }
}
