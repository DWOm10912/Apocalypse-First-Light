package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.contamination.ItemContamination;
import com.antaurora.apofirstlight.registry.AflItems;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientContaminationTooltip {
    private static final ResourceLocation ICON = new ResourceLocation(ApocalypseFirstLight.MOD_ID,
            "textures/gui/radiation_symbol.png");
    private static final int ICON_SOURCE_SIZE = 18;
    private static final int ICON_RENDER_SIZE = 14;
    private static final int TEXT_GAP = 3;
    private static final int SECOND_LINE_GAP = 1;
    private static final int TEXT_LINE_HEIGHT = 9;

    private ClientContaminationTooltip() {
    }

    @SubscribeEvent
    public static void gatherTooltip(RenderTooltipEvent.GatherComponents event) {
        ItemContamination.Level level = ItemContamination.getLevel(event.getItemStack());
        if (level == ItemContamination.Level.CLEAN) return;

        MutableComponent text = Component.translatable(
                "tooltip.apocalypse_firstlight.contamination.label").withStyle(ChatFormatting.GRAY);
        text.append(Component.translatable(level.translationKey()).withStyle(color(level)));
        Component emission = null;
        Player player = Minecraft.getInstance().player;
        if (hasDirectInventoryGeiger(player)) {
            emission = Component.translatable("tooltip.apocalypse_firstlight.contamination.emission",
                    String.format(Locale.ROOT, "%.2f", ItemContamination.getStackSourceRate(event.getItemStack())))
                    .withStyle(ChatFormatting.GRAY);
        }
        event.getTooltipElements().add(Either.right(new ContaminationRow(text, emission)));
    }

    private static boolean hasDirectInventoryGeiger(@Nullable Player player) {
        if (player == null) return false;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(AflItems.GEIGER_COUNTER.get())) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(AflItems.GEIGER_COUNTER.get())) return true;
        }
        return false;
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

    public record ContaminationRow(Component text, @Nullable Component emission) implements TooltipComponent {
    }

    private static final class ClientContaminationRow implements ClientTooltipComponent {
        private final Component text;
        private final Component emission;

        private ClientContaminationRow(ContaminationRow row) {
            this.text = row.text();
            this.emission = row.emission();
        }

        @Override
        public int getHeight() {
            return emission == null ? ICON_RENDER_SIZE
                    : ICON_RENDER_SIZE + SECOND_LINE_GAP + TEXT_LINE_HEIGHT;
        }

        @Override
        public int getWidth(Font font) {
            int textWidth = font.width(text);
            if (emission != null) textWidth = Math.max(textWidth, font.width(emission));
            return ICON_RENDER_SIZE + TEXT_GAP + textWidth;
        }

        @Override
        public void renderText(Font font, int x, int y, Matrix4f matrix,
                               MultiBufferSource.BufferSource buffer) {
            int textY = y + (ICON_RENDER_SIZE - font.lineHeight) / 2;
            font.drawInBatch(text.getVisualOrderText(), x + ICON_RENDER_SIZE + TEXT_GAP, textY,
                    0xFFFFFFFF, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
            if (emission != null) {
                font.drawInBatch(emission.getVisualOrderText(), x + ICON_RENDER_SIZE + TEXT_GAP,
                        y + ICON_RENDER_SIZE + SECOND_LINE_GAP, 0xFFFFFFFF, true, matrix, buffer,
                        Font.DisplayMode.NORMAL, 0, 15728880);
            }
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            graphics.blit(ICON, x, y, ICON_RENDER_SIZE, ICON_RENDER_SIZE,
                    0.0F, 0.0F, ICON_SOURCE_SIZE, ICON_SOURCE_SIZE,
                    ICON_SOURCE_SIZE, ICON_SOURCE_SIZE);
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
