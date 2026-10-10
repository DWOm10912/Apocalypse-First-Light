package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.HandFuelPumpBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The hand fuel pump (2026-10-05, docs/models/fuel_containers_v1.md, tools/build-fuel-containers-v1.mjs): a rotary barrel
 * pump, for fuel without power. It stands in the cell over what it draws from ({@link Mount}):
 * <ul>
 *   <li>{@link Mount#FILL}: an open fuel fill cover, its column down the fill riser into the underground tank (how fuel is
 *   got out of a station's tanks when the power is gone). Its discharge points away from the cover's raised lid, toward
 *   where the cover was opened from.</li>
 *   <li>{@link Mount#DRUM} / {@link Mount#SMALL_DRUM}: on a drum's 2" bung, its discharge out over the drum's front.</li>
 * </ul>
 * The hose from the discharge goes into the nearest fuel container standing next to the pump ({@link #target}: its own
 * level or one lower, beside the drum it stands on). Hold use on the pump (or on the drum under it) with an empty hand to turn the
 * crank: 2 L a second (HandFuelPumpBlockEntity); sneak + use takes it off. It falls off when what it stands on goes (or the
 * fill cover is shut). Its outline is the part inside its own cell; the drum under it answers for the rest.
 */
public final class HandFuelPumpBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public enum Mount implements StringRepresentable {
        FILL("fill"), DRUM("drum"), SMALL_DRUM("small_drum");

        private final String name;

        Mount(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Mount> MOUNT = EnumProperty.create("mount", Mount.class);
    /** Outline boxes (px, discharge toward north): the parts inside the pump's own cell (tools/build-fuel-containers-v1.mjs). */
    private static final double[] FILL_BOX = {6.92, 0.0, 4.01, 10.88, 5.63, 10.82}, DRUM_BOX = {6.92, 0.0, 0.21, 10.88, 6.76, 7.03}, SMALL_DRUM_BOX = {6.92, 0.0, 1.41, 10.88, 2.8, 8.22};
    /** Per mount (Mount order): the hose barb's lower end, px about the pump cell's centre, discharge toward north (generator). */
    static final double[][] BARB = {{0.0, -4.64, -3.56}, {0.0, -3.5, -7.36}, {0.0, -7.46, -6.16}};
    /** How far the hose reaches from the barb to a container's opening (blocks). */
    public static final double REACH = 1.8;

    public HandFuelPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.FILL));
    }

    /** How a pump would stand on {@code below}: its mount and discharge side, or null where it cannot. */
    @Nullable
    public static BlockState placed(BlockState pump, BlockState below) {
        if (below.getBlock() instanceof FuelSumpCoverBlock cover && cover.kind() == FuelSumpCoverBlock.Kind.FILL && below.getValue(FuelSumpCoverBlock.OPEN)) {
            return pump.setValue(MOUNT, Mount.FILL).setValue(FACING, below.getValue(FuelSumpCoverBlock.FACING).getOpposite());
        }
        if (below.getBlock() instanceof FuelCanBlock can && can.size().drum() && below.getValue(FuelCanBlock.OPEN)) {   // the bung open
            return pump.setValue(MOUNT, can.size() == FuelCanBlock.Size.DRUM ? Mount.DRUM : Mount.SMALL_DRUM).setValue(FACING, below.getValue(FACING));
        }
        return null;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return placed(defaultBlockState(), context.getLevel().getBlockState(pos.below()));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState should = placed(state, level.getBlockState(pos.below()));
        return should != null && should.getValue(MOUNT) == state.getValue(MOUNT) && should.getValue(FACING) == state.getValue(FACING);
    }

    /** What it stands on changed: it stays only as it was (a shut cover, a turned or broken drum drops it). */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** The hose barb's lower end, in the world. */
    public static net.minecraft.world.phys.Vec3 barb(BlockPos pump, BlockState state) {
        double[] b = BARB[state.getValue(MOUNT).ordinal()];
        return FuelCanBlock.point(pump, state.getValue(FACING), b[0], b[1], b[2]);
    }

    /**
     * Where the hose goes (V1.1, 2026-10-05: one fixed cell in front was hard to find, user): the fuel container standing
     * next to the pump, at its own level or one lower (beside the drum it stands on), whose opening is nearest the barb and
     * within {@link #REACH}; null when there is none.
     */
    @Nullable
    public static BlockPos target(BlockGetter level, BlockPos pump, BlockState state) {
        net.minecraft.world.phys.Vec3 from = barb(pump, state);
        BlockPos best = null;
        double nearest = REACH * REACH;
        for (int dy = 0; dy >= -1; dy--) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;   // the pump's own cell, and what it stands on
            BlockPos at = pump.offset(dx, dy, dz);
            BlockState there = level.getBlockState(at);
            if (!(there.getBlock() instanceof FuelCanBlock) || !there.getValue(FuelCanBlock.OPEN)) continue;   // the cap off
            double d = FuelCanBlock.opening(at, there).distanceToSqr(from);
            if (d < nearest) {
                nearest = d;
                best = at;
            }
        }
        return best;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        return useOn(level, pos, player);
    }

    /** An empty hand on the pump (or the drum under it): turn the crank; sneaking, take the pump off. */
    public static InteractionResult useOn(Level level, BlockPos pump, Player player) {
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            BlockState state = level.getBlockState(pump);
            level.removeBlock(pump, false);
            ItemStack item = new ItemStack(state.getBlock());
            if (!player.getInventory().add(item)) player.drop(item, false);
            level.playSound(null, pump, net.minecraft.sounds.SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.3F);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pump) instanceof HandFuelPumpBlockEntity entity) entity.crank(player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(MOUNT)) {
            case FILL -> FuelCanBlock.turned(FILL_BOX, state.getValue(FACING));
            case DRUM -> FuelCanBlock.turned(DRUM_BOX, state.getValue(FACING));
            case SMALL_DRUM -> FuelCanBlock.turned(SMALL_DRUM_BOX, state.getValue(FACING));
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return true;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HandFuelPumpBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AflBlockEntities.HAND_FUEL_PUMP.get()) return null;
        return level.isClientSide
                ? (l, p, s, e) -> ((HandFuelPumpBlockEntity) e).clientTick()
                : (l, p, s, e) -> ((HandFuelPumpBlockEntity) e).serverTick();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }
}
