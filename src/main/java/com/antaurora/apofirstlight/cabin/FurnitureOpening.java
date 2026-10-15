package com.antaurora.apofirstlight.cabin;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.meshshape.AflMeshInteractionBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The wood furniture's storage opens in the steps every AFL searchable container has (user 2026-10-15; the industrial
 * locker's, the lead chest's): aim at the doors / drawer / lid and they open (no screen), aim into the open interior and
 * the screen opens (search while hidden slots are left, else view), aim at the open doors / drawer / lid and they close.
 * The aimed region comes from the block's Mesh Shape profile (data/apocalypse_firstlight/mesh_shapes/<id>.json, written by
 * tools/build-wood-furniture-v1.mjs and -v2.mjs: regions door*, drawer, lid, interior), on the player's own view ray, the
 * same for the prompt (client/WorldInteractionHint) and use().
 */
public final class FurnitureOpening {
    private FurnitureOpening() {
    }

    public enum Action {
        OPEN, CLOSE, SEARCH, VIEW, NONE;

        @Nullable
        public String hintKey() { return this == NONE ? null : "hint.apocalypse_firstlight.cabinet." + name().toLowerCase(Locale.ROOT); }
    }

    public static ResourceLocation profile(String id) { return new ResourceLocation(ApocalypseFirstLight.MOD_ID, id); }

    /** door* / drawer / lid: open or close; interior while open: search or view; anything else nothing. */
    public static Action action(boolean open, String region, @Nullable WoodCounterBlockEntity storage) {
        if (region.startsWith("door") || region.equals("drawer") || region.equals("lid")) return open ? Action.CLOSE : Action.OPEN;
        if (region.equals("interior") && open && storage != null) return storage.isSearchCompleteForPrompt() ? Action.VIEW : Action.SEARCH;
        return Action.NONE;
    }

    @Nullable
    static String hintKey(Level level, BlockState state, BlockPos master, String region) {
        WoodCounterBlockEntity storage = level.getBlockEntity(master) instanceof WoodCounterBlockEntity s ? s : null;
        return storage == null ? null : action(state.getValue(BlockStateProperties.OPEN), region, storage).hintKey();
    }

    /** The shared use(): PASS where nothing is aimed at (an item can still be placed on the top). */
    static InteractionResult use(AflMeshInteractionBlock block, BlockState state, Level level, BlockPos pos, BlockPos master, Player player) {
        if (player.isSpectator()) return InteractionResult.PASS;
        var region = block.meshInteraction(state, pos, player);
        WoodCounterBlockEntity storage = level.getBlockEntity(master) instanceof WoodCounterBlockEntity s ? s : null;
        Action action = region == null || storage == null ? Action.NONE : action(state.getValue(BlockStateProperties.OPEN), region.region(), storage);
        if (action == Action.NONE) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        switch (action) {
            case OPEN -> storage.setDoors(true, player);
            case CLOSE -> storage.setDoors(false, player);
            default -> player.openMenu(storage);
        }
        return InteractionResult.CONSUME;
    }
}
