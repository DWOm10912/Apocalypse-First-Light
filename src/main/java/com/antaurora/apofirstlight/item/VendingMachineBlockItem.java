package com.antaurora.apofirstlight.item;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.AflStaticMeshItemRenderer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import java.util.function.Consumer;

/**
 * Vending Machine item: remembers broken glass (AflBrokenGlass) when a broken machine is mined. Drawn from the block's
 * V2 mesh and atlas (tools/build-vending-machine-v2.mjs; the item model's views are fitted by the generator), lights off;
 * a broken-glass stack without the translucent layer, i.e. without the glass.
 */
public final class VendingMachineBlockItem extends BlockItem {
    private static final String BROKEN_GLASS_TAG = "AflBrokenGlass";
    public VendingMachineBlockItem(Block block,Properties properties) { super(block,properties); }
    public static boolean hasBrokenGlass(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(BROKEN_GLASS_TAG);
    }
    public static void setBrokenGlass(ItemStack stack, boolean broken) {
        if (broken) stack.getOrCreateTag().putBoolean(BROKEN_GLASS_TAG,true);
    }
    @Override public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new AflStaticMeshItemRenderer(
                        new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/vending_machine.geo.json"),
                        new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/vending_machine.png"), 0.0, 0.0)
                        .opaqueOnly(VendingMachineBlockItem::hasBrokenGlass);
                return renderer;
            }
        });
    }
}
