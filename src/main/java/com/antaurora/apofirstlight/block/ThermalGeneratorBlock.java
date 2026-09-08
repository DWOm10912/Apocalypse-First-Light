package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.ThermalGeneratorBlockEntity;
import com.antaurora.apofirstlight.energy.MachineStoredEnergy;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.EnumMap;

public final class ThermalGeneratorBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final Map<Direction, VoxelShape> COLLISION_SHAPES = createShapes(true);
    private static final Map<Direction, VoxelShape> OUTLINE_SHAPES = createShapes(false);

    public ThermalGeneratorBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPES.get(state.getValue(FACING));
    }

    private static Map<Direction, VoxelShape> createShapes(boolean collision) {
        // Simplified closed machine envelope, in the source model's NORTH-facing coordinates.
        // Glass is solid; tiny vents and moving-part clearances are not walkable holes.
        double[][] boxes = {
                {.1,2.1,1.3,15.9,14.4,16}, {1.65,2.15,.14,14.3,13.7,1.3},
                {.5,.65,.8,15.5,1.5,15.5}, {.3,1.5,.5,15.7,2.1,15.6},
                {.2,0,.7,1.8,2.1,2.3}, {14.2,0,.7,15.8,2.1,2.3},
                {.2,0,13.7,1.8,2.1,15.3}, {14.2,0,13.7,15.8,2.1,15.3},
                {.2,14.4,.5,15.8,14.75,1.25}, {.2,14.4,14.95,15.8,14.75,15.7},
                {.2,14.4,1.25,.95,14.75,14.95}, {15.05,14.4,1.25,15.8,14.75,14.95},
                {8.1,14.4,7.8,13.95,15.55,13.5}, {2.2,14.4,10,6.8,15,14.4},
                {2.65,15,10.45,6.35,16.85,13.95}, {2.6,16.85,10.4,6.4,17.2,14},
                {0,6,6,.1,10,10}, {15.9,6,6,16,10,10},
                {6.55,11.55,0,14.1,13.7,.14}
        };
        // Only the targeting outline is simplified; physical collision above is unchanged.
        if (!collision) boxes = new double[][] {
                {0,0,0,16,15.55,16}, {2.6,15.55,10.4,6.4,17.2,14}
        };
        Map<Direction, VoxelShape> result = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int steps = switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
            VoxelShape shape = Shapes.empty();
            for (double[] box : boxes) {
                double x0=box[0], z0=box[2], x1=box[3], z1=box[5];
                for (int i=0; i<steps; i++) {
                    double oldX0=x0, oldX1=x1;
                    x0=16-z1; x1=16-z0; z0=oldX0; z1=oldX1;
                }
                shape = Shapes.or(shape, Block.box(x0,box[1],z0,x1,box[4],z1));
            }
            result.put(facing, shape.optimize());
        }
        return Map.copyOf(result);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(position) instanceof ThermalGeneratorBlockEntity generator) {
            NetworkHooks.openScreen(serverPlayer, generator, position);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos position, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }

        double centerX = position.getX() + 0.5D;
        double centerZ = position.getZ() + 0.5D;
        if (random.nextDouble() < 0.1D) {
            float pitch = 0.95F + random.nextFloat() * 0.1F;
            level.playLocalSound(centerX, position.getY() + 0.5D, centerZ,
                    SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS,
                    0.5F, pitch, false);
        }

        if (random.nextDouble() < 0.1D) {
            double particleX = centerX + (random.nextDouble() - 0.5D) * 0.16D;
            double particleZ = centerZ + (random.nextDouble() - 0.5D) * 0.16D;
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    particleX, position.getY() + 1.05D, particleZ,
                    0.0D, 0.03D, 0.0D);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(position) instanceof ThermalGeneratorBlockEntity generator) {
            Containers.dropContents(level, position, generator);
        }
        super.onRemove(state, level, position, newState, movedByPiston);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof ThermalGeneratorBlockEntity generator) {
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    MachineStoredEnergy.write(drop, AflBlockEntities.THERMAL_GENERATOR.get(),
                            generator.getStoredEnergy());
                }
            }
        }
        return drops;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new ThermalGeneratorBlockEntity(position, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != AflBlockEntities.THERMAL_GENERATOR.get()) {
            return null;
        }
        return (tickerLevel, tickerPosition, tickerState, blockEntity) ->
                ThermalGeneratorBlockEntity.serverTick(tickerLevel, tickerPosition, tickerState,
                        (ThermalGeneratorBlockEntity) blockEntity);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }
}
