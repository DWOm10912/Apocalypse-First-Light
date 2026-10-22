package com.antaurora.apofirstlight.building;

import com.antaurora.apofirstlight.inventory.CarriedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Where a building piece may go and what may come down (building hammer B0). The client asks the same questions to colour
 * the preview and say why it cannot go there; the server asks them again before it changes anything. A refusal is a key
 * suffix under message.apocalypse_firstlight.building.
 */
public final class BuildingRules {
    /**
     * Where and how a piece goes. B0: a whole block position and an index into the piece's quarter-turn states. Free
     * turns (any yaw) and offsets inside the block would be added here and in the packet later; nothing reads more yet.
     */
    public record Placement(BlockPos pos, int orientation) {}

    /** How far past the player's block reach (from the eyes to the block's centre) the hammer still works. */
    private static final double SLACK = 1.0;

    private BuildingRules() {}

    public static boolean inReach(Player player, BlockPos pos) {
        double reach = player.getBlockReach() + SLACK;
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach;
    }

    /** The state the piece takes at the placement: its orientation, waterlogged in still water. */
    @Nullable
    public static BlockState placed(Level level, BuildingCatalog.Piece piece, Placement p) {
        BlockState state = piece.state(p.orientation());
        if (state == null) return null;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED))
            state = state.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(p.pos()).isSourceOfType(Fluids.WATER));
        return state;
    }

    /** Why the piece cannot go there, or null when it can (the stock is checked unless the player is in Creative). */
    @Nullable
    public static String placeProblem(Player player, BuildingCatalog.Piece piece, Placement p) {
        Level level = player.level();
        BlockPos pos = p.pos();
        var item = piece.item();
        BlockState state = placed(level, piece, p);
        if (item == null || state == null) return "unknown";
        if (!level.isInWorldBounds(pos) || !player.mayBuild() || !level.mayInteract(player, pos)) return "protected";
        if (!inReach(player, pos)) return "too_far";
        BlockState there = level.getBlockState(pos);
        if (!there.canBeReplaced()) return "occupied";
        var fluid = level.getFluidState(pos);
        if (!fluid.isEmpty() && !(fluid.isSourceOfType(Fluids.WATER) && state.hasProperty(BlockStateProperties.WATERLOGGED))) return "liquid";
        if (!state.canSurvive(level, pos)) return "unsupported";
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) return "blocked";
        if (!player.getAbilities().instabuild && CarriedItems.count(player, item) < 1) return "none_carried";
        return null;
    }

    /** Why the block at pos cannot be taken down with the hammer, or null when it can. */
    @Nullable
    public static String removeProblem(Player player, BlockPos pos) {
        Level level = player.level();
        if (BuildingCatalog.byBlock(level.getBlockState(pos).getBlock()) == null) return "not_piece";
        if (!player.mayBuild() || !level.mayInteract(player, pos)) return "protected";
        if (!inReach(player, pos)) return "too_far";
        return null;
    }
}
