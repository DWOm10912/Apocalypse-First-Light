package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.SteelDoorBlockEntity;
import com.antaurora.apofirstlight.registry.AflItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Steel Door (`steel_door`, zh "工业门"). Steel-frame doors V1 (2026-10-07, docs/models/steel_frame_doors_v1.md): a charcoal
 * hollow-metal frame and leaf drawn by the AFL Animated Block Mesh Runtime (SteelDoorBlockEntity on the lower half; the block
 * model is particle-only). Still a vanilla door: the states (facing / half / hinge / open / powered), placement, redstone,
 * sounds and the recovery-tool / explosion drops below are unchanged, so existing worlds and templates (bunker.nbt, the A1
 * store) keep their doors. New: the leaf hangs at the face toward the placer and swings out toward the placer (out of the
 * cell, as an exit door placed from outside); the shapes follow the mesh (SteelFrameDoorShapes).
 */
public class SteelDoorBlock extends DoorBlock implements EntityBlock {
    private static final TagKey<net.minecraft.world.item.Item> RECOVERY_TOOLS = TagKey.create(
            Registries.ITEM,
            new ResourceLocation("apocalypse_firstlight", "industrial_material_recovery_tools"));
    private static final Set<BlockPos> PLAYER_DESTROYING = new HashSet<>();
    private static final Set<BlockPos> EXPLOSION_DESTROYING = new HashSet<>();

    public SteelDoorBlock(Properties properties, BlockSetType type) {
        super(properties, type);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SteelFrameDoorShapes.shape(state, false);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** The lower half draws the whole door. */
    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new SteelDoorBlockEntity(pos, state) : null;
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
