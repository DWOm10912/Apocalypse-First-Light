package com.antaurora.apofirstlight.containersearch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Opt-in contract for closed containers that use Progressive Container Search.
 *
 * <p>Implemented by the asset's own container block entity next to its existing superclass (for example
 * {@code RandomizableContainerBlockEntity}); the search system never requires a new base class. The block entity
 * already provides {@link #getLevel()}, {@link #getBlockPos()}, {@link #getBlockState()} and {@link #isRemoved()}.
 * The forwarding overrides the asset must add are listed in {@code docs/gameplay/progressive_container_search_v1.md}.
 */
public interface AflSearchableContainer extends Container {
    /** The container's own state object, created once per block entity and never shared. */
    AflContainerSearchState aflSearchState();

    /** Per-asset timing and noise configuration. */
    AflContainerSearchSettings aflSearchSettings();

    /**
     * Menu layout. Default: the chest grid for 9, 18, ... 54 slots (null for other sizes); a 9-slot container that
     * should look like a dispenser returns {@link AflContainerSearchLayout#GRID_3X3}.
     */
    @Nullable
    default AflContainerSearchLayout aflSearchLayout() {
        int size = getContainerSize();
        return size % 9 == 0 && size >= 9 && size <= 54 ? AflContainerSearchLayout.chest(size / 9) : null;
    }

    /**
     * Asked exactly once, when the search state is first initialized on the server and before any loot is
     * unpacked. The answer is persisted. Typical world-loot containers answer {@code lootTable != null}.
     */
    boolean aflSearchRequiredOnInit();

    @Nullable Level getLevel();

    BlockPos getBlockPos();

    BlockState getBlockState();

    boolean isRemoved();

    /**
     * The looping sound heard at the container while its search runs (AflContainerSearchState, client/
     * ContainerSearchSoundController). One shared rustle for every container for now (user 2026-10-02: no materials yet);
     * a container may return its own loop later, or null for silence.
     */
    @Nullable
    default SoundEvent aflSearchSound() {
        return com.antaurora.apofirstlight.registry.AflSounds.CONTAINER_SEARCH_RUMMAGE.get();
    }

    /** A search session began because at least one valid searcher is viewing. */
    default void onAflSearchStarted(ServerLevel level) {
    }

    /** The session ended before completion (last searcher left, or the container became invalid). */
    default void onAflSearchStopped(ServerLevel level) {
    }

    /** One slot changed from hidden to revealed. Its real content is only now sent to viewers. */
    default void onAflSearchSlotRevealed(ServerLevel level, int slot) {
    }

    /** Every slot is revealed; later openings use the ordinary container menu. */
    default void onAflSearchCompleted(ServerLevel level) {
    }
}
