package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.tags.TagKey;

import java.util.HashSet;
import java.util.Set;

public class SteelDoorBlock extends DoorBlock {
    private static final TagKey<net.minecraft.world.item.Item> RECOVERY_TOOLS = TagKey.create(
            Registries.ITEM,
            new ResourceLocation("apocalypse_firstlight", "industrial_material_recovery_tools"));
    private static final Set<BlockPos> PLAYER_DESTROYING = new HashSet<>();
    private static final Set<BlockPos> EXPLOSION_DESTROYING = new HashSet<>();

    public SteelDoorBlock(Properties properties, BlockSetType type) {
        super(properties, type);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        BlockPos lowerPosition = canonicalPosition(position, state);
        boolean firstHalf = PLAYER_DESTROYING.add(lowerPosition);
        try {
            if (firstHalf && !player.isCreative() && player.getMainHandItem().is(RECOVERY_TOOLS)) {
                popResource(level, lowerPosition, new ItemStack(AflItems.STEEL_DOOR.get()));
            }
            super.playerWillDestroy(level, position, state, player);
        } finally {
            PLAYER_DESTROYING.remove(lowerPosition);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER
                && direction == Direction.DOWN
                && !neighborState.isFaceSturdy(level, neighborPos, Direction.UP)) {
            BlockPos lowerPosition = currentPos.immutable();
            if (!PLAYER_DESTROYING.contains(lowerPosition)
                    && !EXPLOSION_DESTROYING.remove(lowerPosition)
                    && level instanceof Level serverLevel
                    && !serverLevel.isClientSide()) {
                popResource(serverLevel, lowerPosition, new ItemStack(AflItems.STEEL_DOOR.get()));
            }
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    public static void markExplosion(BlockPos position, BlockState state) {
        EXPLOSION_DESTROYING.add(canonicalPosition(position, state));
    }

    public static void clearExplosionMarks() {
        EXPLOSION_DESTROYING.clear();
    }

    public static BlockPos canonicalPosition(BlockPos position, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? position.below() : position;
    }
}
