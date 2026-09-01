package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.ChestType;

public class LeadChestRenderer extends ChestRenderer<LeadChestBlockEntity> {
    private static final Material SINGLE = material("lead_chest");
    private static final Material LEFT = material("lead_chest_left");
    private static final Material RIGHT = material("lead_chest_right");

    public LeadChestRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected Material getMaterial(LeadChestBlockEntity blockEntity, ChestType chestType) {
        return switch (chestType) {
            case LEFT -> LEFT;
            case RIGHT -> RIGHT;
            case SINGLE -> SINGLE;
        };
    }

    private static Material material(String textureName) {
        return new Material(Sheets.CHEST_SHEET, new ResourceLocation(
                ApocalypseFirstLight.MOD_ID, "entity/chest/" + textureName));
    }
}
