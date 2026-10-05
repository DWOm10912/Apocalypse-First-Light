package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.fluid.AflFluidPortBlock;
import com.antaurora.apofirstlight.fluid.FluidTankStoredFluid;
import com.antaurora.apofirstlight.fluid.FluidTankStructures;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Fluid Tank V2 (2026-10-05, tools/build-fluid-tank-v2.mjs, docs/models/fluid_tank_v2.md). Each block is a tank; tanks
 * that fill a complete cuboid join into one (fluid/FluidTankStructures). The six joined flags say which neighbours belong
 * to the same tank: the baked model drops the glass and posts on those sides, and the fluid renderer runs on through
 * them. Ports: the AFL fluid port on every top face not joined upward (fill) and every bottom face not joined downward
 * (drain), as the old tank's top in / bottom out.
 */
public final class FluidTankBlock extends BaseEntityBlock implements AflFluidPortBlock {
    public static final Map<Direction, BooleanProperty> JOINED = PipeBlock.PROPERTY_BY_DIRECTION;

    /** The Heat-Resistant Fluid Tank (quartz glass, ceramic lining): holds hot liquids; the ordinary tank melts (FluidHeat). */
    private final boolean heatResistant;

    public FluidTankBlock(Properties properties) {
        this(properties, false);
    }

    public FluidTankBlock(Properties properties, boolean heatResistant) {
        super(properties.lightLevel(state -> state.getValue(com.antaurora.apofirstlight.fluid.FluidLighting.LIGHT)));
        BlockState state = stateDefinition.any().setValue(com.antaurora.apofirstlight.fluid.FluidLighting.LIGHT, 0);
        for (BooleanProperty property : JOINED.values()) state = state.setValue(property, false);
        registerDefaultState(state);
        this.heatResistant = heatResistant;
    }

    public boolean heatResistant() {
        return heatResistant;
    }

    @Override
    public boolean hasFluidPort(BlockState state, Direction face) {
        return face.getAxis().isVertical() && !state.getValue(JOINED.get(face));
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos position, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(position) instanceof FluidTankBlockEntity tank) {
            tank.preparePlayerBreakDrop(!player.isCreative());
        }
        super.playerWillDestroy(level, position, state, player);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FluidTankBlockEntity tank) {
            FluidStack preservedFluid = tank.getPreparedDropFluid();
            if (!preservedFluid.isEmpty()) {
                for (ItemStack drop : drops) if (drop.is(asItem())) FluidTankStoredFluid.write(drop, preservedFluid);
            }
        }
        return drops;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) FluidTankStructures.rebuild(serverLevel, position);
    }

    /** A neighbour changed: re-form the group on the next tick (cheap when nothing changes). */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos position, BlockPos neighborPosition) {
        if ((neighborState.getBlock() instanceof FluidTankBlock) != state.getValue(JOINED.get(direction))) level.scheduleTick(position, this, 1);
        return state;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos position, RandomSource random) {
        FluidTankStructures.rebuild(level, position);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(position) instanceof FluidTankBlockEntity tank) {
            FluidTankStructures.handleRemoved(serverLevel, position, state, tank);
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (!FluidUtil.getFluidHandler(heldItem).isPresent()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(position) instanceof FluidTankBlockEntity tank)) return InteractionResult.PASS;
        return tank.interactWithFluidContainer(player, hand) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** See-through: neighbours keep their faces (the glass shows what is behind it). */
    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(com.antaurora.apofirstlight.fluid.FluidLighting.LIGHT);
        JOINED.values().forEach(builder::add);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new FluidTankBlockEntity(position, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != AflBlockEntities.FLUID_TANK.get()) return null;
        return (tickerLevel, tickerPosition, tickerState, blockEntity) ->
                FluidTankBlockEntity.serverTick(tickerLevel, tickerPosition, tickerState, (FluidTankBlockEntity) blockEntity);
    }
}
