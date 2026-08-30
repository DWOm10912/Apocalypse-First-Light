package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.UUID;

public final class RadiationHeartOverlay {
    private static final int HEART_SIZE = 9;
    private static final int HEART_SPACING = 8;
    private static final int HEARTS_PER_ROW = 10;

    private static final ResourceLocation MILD_FULL = texture("heart_mild_full");
    private static final ResourceLocation MILD_HALF = texture("heart_mild_half");
    private static final ResourceLocation SEVERE_FULL = texture("heart_severe_full");
    private static final ResourceLocation SEVERE_HALF = texture("heart_severe_half");
    private static final ResourceLocation CRITICAL_FULL = texture("heart_critical_full");
    private static final ResourceLocation CRITICAL_HALF = texture("heart_critical_half");

    private static final RandomSource JITTER_RANDOM = RandomSource.create();

    private RadiationHeartOverlay() {
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !gui.shouldDrawSurvivalElements()) return;

        MobEffectInstance sickness = player.getEffect(AflMobEffects.RADIATION_SICKNESS.get());
        if (sickness == null) return;

        int stage = sickness.getAmplifier() + 1;
        if (stage < 2) return;

        int health = Mth.ceil(player.getHealth());
        if (health <= 0) return;

        int filledHeartSlots = Mth.ceil(health / 2.0F);
        int targetCount = targetCount(stage, filledHeartSlots);
        if (targetCount <= 0) return;

        AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute == null) return;

        float maxHealth = Math.max((float) maxHealthAttribute.getValue(), health);
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int maxHealthHeartSlots = Mth.ceil(maxHealth / 2.0F);
        int absorptionHeartSlots = Mth.ceil(absorption / 2.0F);
        int healthRows = Mth.ceil((maxHealth + absorption) / 2.0F / HEARTS_PER_ROW);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        int healthHudHeight = healthRows * rowHeight;
        if (rowHeight != 10) healthHudHeight += 10 - rowHeight;

        int left = screenWidth / 2 - 91;
        int top = screenHeight - (gui.leftHeight - healthHudHeight);
        int regenerationHeart = player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)
                ? gui.getGuiTicks() % Mth.ceil(maxHealth + 5.0F)
                : -1;

        JITTER_RANDOM.setSeed((long) (gui.getGuiTicks() * 312871));
        gui.setupOverlayRenderState(true, false);

        UUID playerId = player.getUUID();
        for (int heartIndex = maxHealthHeartSlots + absorptionHeartSlots - 1; heartIndex >= 0; heartIndex--) {
            int row = heartIndex / HEARTS_PER_ROW;
            int column = heartIndex % HEARTS_PER_ROW;
            int x = left + column * HEART_SPACING;
            int y = top - row * rowHeight;

            if (health + absorption <= 4) y += JITTER_RANDOM.nextInt(2);
            if (heartIndex < maxHealthHeartSlots && heartIndex == regenerationHeart) y -= 2;

            if (heartIndex >= filledHeartSlots
                    || !isSelected(playerId, heartIndex, filledHeartSlots, targetCount)) {
                continue;
            }

            boolean halfHeart = heartIndex * 2 + 1 == health;
            ResourceLocation texture = textureFor(stage, halfHeart);
            graphics.blit(texture, x, y, 0, 0.0F, 0.0F,
                    HEART_SIZE, HEART_SIZE, HEART_SIZE, HEART_SIZE);
        }
    }

    private static int targetCount(int stage, int filledHeartSlots) {
        return switch (stage) {
            case 2 -> (filledHeartSlots * 3 + 9) / 10;
            case 3 -> (filledHeartSlots * 6 + 9) / 10;
            default -> filledHeartSlots;
        };
    }

    private static boolean isSelected(UUID playerId, int heartIndex, int filledHeartSlots, int targetCount) {
        if (targetCount >= filledHeartSlots) return true;

        long score = heartScore(playerId, heartIndex);
        int rank = 0;
        for (int candidate = 0; candidate < filledHeartSlots; candidate++) {
            if (candidate == heartIndex) continue;

            long candidateScore = heartScore(playerId, candidate);
            int comparison = Long.compareUnsigned(candidateScore, score);
            if (comparison < 0 || (comparison == 0 && candidate < heartIndex)) rank++;
        }
        return rank < targetCount;
    }

    private static long heartScore(UUID playerId, int heartIndex) {
        long value = playerId.getMostSignificantBits()
                ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17)
                ^ (0x9E3779B97F4A7C15L * (heartIndex + 1L));
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static ResourceLocation textureFor(int stage, boolean halfHeart) {
        if (stage == 2) return halfHeart ? MILD_HALF : MILD_FULL;
        if (stage == 3) return halfHeart ? SEVERE_HALF : SEVERE_FULL;
        return halfHeart ? CRITICAL_HALF : CRITICAL_FULL;
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID,
                "textures/gui/radiation/" + name + ".png");
    }
}
