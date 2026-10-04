package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.inventory.AflEquipmentSlot;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import top.theillusivec4.curios.api.type.ISlotType;

/**
 * The client side of AFL's equipment slots: their slot frames on the inventory page (the vanilla background has none
 * in that column), Curios' own panel button hidden while every slot the player has lives on AFL's page, and the
 * hearing protection drawn on the head from the ears slot (like vanilla's head item layer).
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEquipmentSlots {
    private static final String CURIOS_BUTTON = "top.theillusivec4.curios.client.gui.CuriosButton";
    private ClientEquipmentSlots() {}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.addListener(ClientEquipmentSlots::frames);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClientEquipmentSlots::hideCuriosButton);
        event.enqueueWork(() -> CuriosRendererRegistry.register(AflItems.SIMPLE_HEARING_PROTECTION.get(), HeadItemRenderer::new));
    }

    /** Vanilla-style slot frames (18 × 18: dark top / left, white bottom / right, slot grey inside). */
    private static void frames(ContainerScreenEvent.Render.Background event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen screen)) return;
        var g = event.getGuiGraphics();
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        for (var slot : screen.getMenu().slots) {
            if (!(slot instanceof AflEquipmentSlot) || !slot.isActive()) continue;
            int x = left + slot.x - 1, y = top + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
            g.fill(x, y, x + 17, y + 1, 0xFF373737);
            g.fill(x, y, x + 1, y + 17, 0xFF373737);
            g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
            g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        }
    }

    /** Curios' panel would be empty: AFL's slots are not on it (use_native_gui false). Keep it when another mod adds one. */
    private static void hideCuriosButton(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen) && !(event.getScreen() instanceof CreativeModeInventoryScreen)) return;
        var player = Minecraft.getInstance().player;
        if (player == null || CuriosApi.getPlayerSlots(player).values().stream().anyMatch(ISlotType::useNativeGui)) return;
        for (var listener : java.util.List.copyOf(event.getListenersList()))
            if (listener.getClass().getName().equals(CURIOS_BUTTON)) event.removeListener(listener);
    }

    /** Draws a worn curio on the head the way vanilla draws a non-armour head item (CustomHeadLayer). */
    private static final class HeadItemRenderer implements ICurioRenderer {
        @Override
        public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
                RenderLayerParent<T, M> parent, MultiBufferSource buffer, int light, float limbSwing, float limbSwingAmount,
                float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!(parent.getModel() instanceof HeadedModel headed)) return;
            var entity = slotContext.entity();
            poseStack.pushPose();
            if (entity.isBaby()) {
                poseStack.translate(0.0F, 0.03125F, 0.0F);
                poseStack.scale(0.7F, 0.7F, 0.7F);
                poseStack.translate(0.0F, 1.0F, 0.0F);
            }
            headed.getHead().translateAndRotate(poseStack);
            CustomHeadLayer.translateToHead(poseStack, false);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                    .renderItem(entity, stack, ItemDisplayContext.HEAD, false, poseStack, buffer, light);
            poseStack.popPose();
        }
    }
}
