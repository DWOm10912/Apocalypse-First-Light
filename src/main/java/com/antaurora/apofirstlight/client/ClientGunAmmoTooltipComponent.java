package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.tooltip.GunAmmoTooltipComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

public final class ClientGunAmmoTooltipComponent implements ClientTooltipComponent {
    private static final int ITEM_SIZE = 16;
    private static final int TEXT_OFFSET = 21;
    private static final int VERTICAL_PADDING = 3;
    private final GunAmmoTooltipComponent data;

    public ClientGunAmmoTooltipComponent(GunAmmoTooltipComponent data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        return ITEM_SIZE + VERTICAL_PADDING * 2;
    }

    @Override
    public int getWidth(Font font) {
        return textOffset() + font.width(data.ammoName());
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix,
                           MultiBufferSource.BufferSource buffer) {
        font.drawInBatch(data.ammoName().getVisualOrderText(), x + textOffset(),
                y + VERTICAL_PADDING + (ITEM_SIZE - font.lineHeight) / 2,
                0xFFFFFFFF, true, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 15728880);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (data.ammoStack().isEmpty()) return;
        graphics.renderItem(data.ammoStack(), x, y + VERTICAL_PADDING);
    }

    private int textOffset() {
        return data.ammoStack().isEmpty() ? 0 : TEXT_OFFSET;
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(GunAmmoTooltipComponent.class, ClientGunAmmoTooltipComponent::new);
        }
    }
}
