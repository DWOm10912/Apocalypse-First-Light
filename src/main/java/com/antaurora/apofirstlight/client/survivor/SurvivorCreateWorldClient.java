package com.antaurora.apofirstlight.client.survivor;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.survivor.SurvivorMode;
import com.antaurora.apofirstlight.survivor.Survivors;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.Difficulty;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * The survivor mode on the vanilla Create World screen (docs/gameplay/survivor_death_succession_v0.md 2, 11): the
 * difficulty button of the Game tab is hidden (AFL keeps one fixed difficulty, survivor/AflDifficulty) and a
 * vanilla-style "Survivor mode: Normal / Hard" button takes its place; its tooltip states the death and item rules.
 * Vanilla hardcore pins it to Hard. The pick goes to the new world when it is created (Survivors.applyCreateScreenChoice)
 * and is dropped when the player leaves for the world list or the title screen. Without the difficulty button (another
 * mod's screen) nothing is added and the host chooses on first join as before. Placeholder until the modern UI pass.
 */
@Mod.EventBusSubscriber(modid = ApocalypseFirstLight.MOD_ID, value = Dist.CLIENT)
public final class SurvivorCreateWorldClient {
    private static WeakReference<CreateWorldScreen> screen = new WeakReference<>(null);
    @Nullable
    private static AbstractWidget difficultyButton;
    @Nullable
    private static CycleButton<SurvivorMode> modeButton;
    private static SurvivorMode chosen = SurvivorMode.NORMAL;
    private static boolean wasHardcore;

    private SurvivorCreateWorldClient() {
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof SelectWorldScreen || event.getScreen() instanceof TitleScreen) {
            Survivors.setCreateScreenChoice(null);
            return;
        }
        if (!(event.getScreen() instanceof CreateWorldScreen create)) return;
        // a new screen starts on Normal; a resize (init again) keeps the pick
        if (screen.get() != create) chosen = SurvivorMode.NORMAL;
        screen = new WeakReference<>(create);
        difficultyButton = null;
        modeButton = null;
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof CycleButton<?> button && button.getValue() instanceof Difficulty) difficultyButton = button;
        }
        if (difficultyButton == null) return;
        difficultyButton.visible = false;
        wasHardcore = create.getUiState().isHardcore();
        modeButton = CycleButton.<SurvivorMode>builder(mode -> Component.translatable("survivor.apocalypse_firstlight.mode." + mode.id()))
                .withValues(SurvivorMode.NORMAL, SurvivorMode.HARD)
                .withInitialValue(wasHardcore ? SurvivorMode.HARD : chosen)
                .withTooltip(SurvivorCreateWorldClient::tooltip)
                .create(difficultyButton.getX(), difficultyButton.getY(), difficultyButton.getWidth(), difficultyButton.getHeight(),
                        Component.translatable("survivor.apocalypse_firstlight.mode.title"), (button, mode) -> chosen = mode);
        event.addListener(modeButton);
    }

    /**
     * Each frame before the screen draws: the button follows the hidden difficulty button (only on the Game tab, at its
     * place), stays on Hard while vanilla hardcore is selected, and the pick is handed over.
     */
    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof CreateWorldScreen create) || create != screen.get() || modeButton == null
                || difficultyButton == null) {
            return;
        }
        difficultyButton.visible = false;
        modeButton.visible = create.children().contains(difficultyButton);
        modeButton.setPosition(difficultyButton.getX(), difficultyButton.getY());
        boolean hardcore = create.getUiState().isHardcore();
        modeButton.active = !hardcore;
        SurvivorMode shown = hardcore ? SurvivorMode.HARD : chosen;
        if (modeButton.getValue() != shown || hardcore != wasHardcore) {
            wasHardcore = hardcore;
            modeButton.setValue(shown);
        }
        Survivors.setCreateScreenChoice(shown);
    }

    private static Tooltip tooltip(SurvivorMode mode) {
        MutableComponent text = Component.translatable("survivor.apocalypse_firstlight.mode." + mode.id() + ".line");
        CreateWorldScreen create = screen.get();
        if (create != null && create.getUiState().isHardcore()) {
            text.append("\n").append(Component.translatable("survivor.apocalypse_firstlight.mode.hardcore"));
        }
        return Tooltip.create(text.append("\n").append(Component.translatable("survivor.apocalypse_firstlight.mode.rule_items"))
                .append("\n").append(Component.translatable("survivor.apocalypse_firstlight.mode.locked")));
    }
}
