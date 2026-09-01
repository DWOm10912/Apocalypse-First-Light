package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.contamination.ItemContamination;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientContaminationTooltip {
    private static final ResourceLocation ICON = new ResourceLocation(ApocalypseFirstLight.MOD_ID,
            "textures/gui/radiation_symbol.png");
    private static final int ICON_SIZE = 18;
    private static final int TEXT_GAP = 4;

    private ClientContaminationTooltip() {
    }

    @SubscribeEvent
    public static void gatherTooltip(RenderTooltipEvent.GatherComponents event) {
        ItemContamination.Level level = ItemContamination.getLevel(event.getItemStack());
        if (level == ItemContamination.Level.CLEAN) return;

        MutableComponent text = Component.translatable(
                "tooltip.apocalypse_firstlight.contamination.label").withStyle(ChatFormatting.GRAY);
        text.append(Component.translatable(level.translationKey()).withStyle(color(level)));
        event.getTooltipElements().add(Either.right(new ContaminationRow(text)));
    }

    private static ChatFormatting color(ItemContamination.Level level) {
        return switch (level) {
            case TRACE -> ChatFormatting.GRAY;
            case LOW -> ChatFormatting.YELLOW;
            case MODERATE -> ChatFormatting.GOLD;
            case HIGH -> ChatFormatting.RED;
            case SEVERE -> ChatFormatting.DARK_RED;
            default -> ChatFormatting.WHITE;
        };
    }

    public record ContaminationRow(Component text) implements TooltipComponent {
    }

    private static final class ClientContaminationRow implements ClientTooltipComponent {
        private final Component text;

        private ClientContaminationRow(ContaminationRow row) {
            this.text = row.text();
        }

        @Override
        public int getHeight() {
            return ICON_SIZE;
        }

        @Override
        public int getWidth(Font font) {
            return ICON_SIZE + TEXT_GAP + font.width(text);
        }

        @Override
        public void renderText(Font font, int x, int y, Matrix4f matrix,
                               MultiBufferSource.BufferSource buffer) {
            int textY = y + (ICON_SIZE - font.lineHeight) / 2;
            font.drawInBatch(text.getVisualOrderText(), x + ICON_SIZE + TEXT_GAP, textY,
                    0xFFFFFFFF, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            graphics.blit(ICON, x, y, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }
    }

    @Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(ContaminationRow.class, ClientContaminationRow::new);
        }
    }
}
