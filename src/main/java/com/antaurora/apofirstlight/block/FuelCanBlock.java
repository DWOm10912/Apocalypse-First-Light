package com.antaurora.apofirstlight.block;

import com.antaurora.apofirstlight.blockentity.FuelCanBlockEntity;
import com.antaurora.apofirstlight.item.FuelCanItem;
import com.antaurora.apofirstlight.item.FuelNozzleItem;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Fuel Containers V1 (2026-10-05, docs/models/fuel_containers_v1.md, tools/build-fuel-containers-v1.mjs): the portable
 * steel containers fuel is carried and kept in, standing on the ground as blocks (user: the dispenser and the hand pump
 * fill them where they stand). Modelled at real size (1 block = 1 m) and drawn larger than life (V1.1, 2026-10-05: they
 * looked too small, user; jerry can 1.4 times, 60 L drum 1.3 times, 200 L drum one block tall):
 * <ul>
 *   <li>{@link Size#JERRY_CAN}: NATO 20 L can; carried in the hand to pour (item/FuelCanItem), set down with sneak + use,
 *   picked up again with sneak + use on an empty hand;</li>
 *   <li>{@link Size#SMALL_DRUM}: 60 L drum; {@link Size#DRUM}: 200 L tight-head drum. A hand pump (HandFuelPumpBlock) goes
 *   on their 2" bung, which faces the player who set the drum down.</li>
 * </ul>
 * Gasoline or diesel only, one at a time (FuelCanBlockEntity). Broken, they keep their fuel (the loot tables copy it into
 * the item). Steel: bullets hole them, fire sets them off (fluid/FuelContainers, fluid/FuelLeaks).
 */
public final class FuelCanBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public enum Size {
        /** Body box (px, facing north), fuel box (the body's inside, px), the opening (px, cell-centred, facing north). */
        // tools/build-fuel-containers-v1.mjs prints these lines and --check finds them (V1.1: drawn larger than life)
        JERRY_CAN(20, new double[]{4.04, 0.0, 6.01, 11.96, 10.55, 9.99}, new double[]{4.29, 0.15, 6.3, 11.71, 9.07, 9.7}, new Vec3(2.45, 2.5, 0.0)),
        SMALL_DRUM(60, new double[]{3.75, 0.0, 3.75, 12.25, 12.27, 12.25}, new double[]{4.24, 0.4, 4.24, 11.76, 11.6, 11.76}, new Vec3(0.0, 4.27, -2.6)),
        DRUM(200, new double[]{2.22, 0.0, 2.22, 13.78, 16.0, 13.78}, new double[]{2.7, 0.4, 2.7, 13.3, 15.56, 13.3}, new Vec3(0.0, 8.19, -3.8));

        public final int capacity;
        private final double[] body, fuel;
        private final Vec3 opening;
        private final VoxelShape[] shapes = new VoxelShape[4];

        Size(int capacity, double[] body, double[] fuel, Vec3 opening) {
            this.capacity = capacity;
            this.body = body;
            this.fuel = fuel;
            this.opening = opening;
            for (Direction d : Direction.Plane.HORIZONTAL) shapes[d.get2DDataValue()] = turned(body, d);
        }

        public boolean drum() {
            return this != JERRY_CAN;
        }
    }

    private final Size size;

    public FuelCanBlock(Size size, Properties properties) {
        super(properties);
        this.size = size;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Size size() {
        return size;
    }

    /** A drum's 2" bung (and its pump) faces the player who sets it down; the can's broad face runs across their view. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** A box (px, facing north) turned to {@code facing} about the cell's vertical axis. */
    public static VoxelShape turned(double[] b, Direction facing) {
        double[] t = turnedBox(b, facing);
        return Block.box(t[0], t[1], t[2], t[3], t[4], t[5]);
    }

    public static double[] turnedBox(double[] b, Direction facing) {
        double x0 = b[0] - 8, z0 = b[2] - 8, x1 = b[3] - 8, z1 = b[5] - 8;
        double[] p = turnXZ(x0, z0, facing), q = turnXZ(x1, z1, facing);
        return new double[]{Math.min(p[0], q[0]) + 8, b[1], Math.min(p[1], q[1]) + 8, Math.max(p[0], q[0]) + 8, b[4], Math.max(p[1], q[1]) + 8};
    }

    /** (x, z) relative to the cell's centre, facing north, turned as the blockstate turns the model to {@code facing}. */
    public static double[] turnXZ(double x, double z, Direction facing) {
        return switch (facing) {
            case EAST -> new double[]{-z, x};
            case SOUTH -> new double[]{-x, -z};
            case WEST -> new double[]{z, -x};
            default -> new double[]{x, z};
        };
    }

    /** A point given in px about the cell's centre (facing north), in the world for a block at {@code pos} facing {@code facing}. */
    public static Vec3 point(BlockPos pos, Direction facing, double x, double y, double z) {
        double[] t = turnXZ(x, z, facing);
        return new Vec3(pos.getX() + 0.5 + t[0] / 16, pos.getY() + 0.5 + y / 16, pos.getZ() + 0.5 + t[1] / 16);
    }

    /** Where fuel goes in (the can's spout, a drum's 2" bung), in the world. */
    public static Vec3 opening(BlockPos pos, BlockState state) {
        Size s = ((FuelCanBlock) state.getBlock()).size;
        return point(pos, state.getValue(FACING), s.opening.x, s.opening.y, s.opening.z);
    }

    /** The space the fuel fills from the bottom up (FuelContainers). */
    public static AABB fuelBox(BlockPos pos, BlockState state) {
        Size s = ((FuelCanBlock) state.getBlock()).size;
        double[] t = turnedBox(s.fuel, state.getValue(FACING));
        return new AABB(pos.getX() + t[0] / 16, pos.getY() + t[1] / 16, pos.getZ() + t[2] / 16, pos.getX() + t[3] / 16, pos.getY() + t[4] / 16, pos.getZ() + t[5] / 16);
    }

    /**
     * Use on the container itself: a held nozzle, can or pump does its own thing (they PASS here); on a drum with a hand pump
     * on it, an empty hand turns the crank (sneaking: takes the pump off), as on the pump; a can is picked up by sneaking
     * with an empty hand, its fuel kept.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return InteractionResult.PASS;
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) return InteractionResult.PASS;
        BlockPos above = pos.above();
        if (size.drum() && level.getBlockState(above).getBlock() instanceof HandFuelPumpBlock) {
            return HandFuelPumpBlock.useOn(level, above, player);
        }
        if (size == Size.JERRY_CAN && player.isSecondaryUseActive()) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            ItemStack can = new ItemStack(this);
            if (level.getBlockEntity(pos) instanceof FuelCanBlockEntity entity) FuelCanItem.setFluid(can, entity.tank().getFluid());
            level.removeBlock(pos, false);
            player.setItemInHand(InteractionHand.MAIN_HAND, can);
            level.playSound(null, pos, getSoundType(state, level, pos, player).getHitSound(), net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.2F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /** True for an item that acts on a container on its own (dispenser nozzle, a can that pours, the hand pump). */
    public static boolean actsOn(ItemStack stack) {
        return stack.getItem() instanceof FuelNozzleItem || stack.getItem() instanceof FuelCanItem can && can.pours()
                || stack.is(AflBlocks.HAND_FUEL_PUMP.get().asItem());
    }

    /**
     * What a click on this block would do with the fuel containers (client/WorldInteractionHint, key suffix under
     * hint.apocalypse_firstlight.fuel_container), or null: on a container, fill it (nozzle), set a pump on a drum, crank or
     * take off a drum's pump, pick a can up; on the pump the same; on an open fill cover, set a pump on it, pour a can into
     * its tank, or stop that pour.
     */
    @Nullable
    public static String hint(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        ItemStack held = player.getMainHandItem();
        boolean empty = held.isEmpty(), sneak = player.isSecondaryUseActive(), pump = held.is(AflBlocks.HAND_FUEL_PUMP.get().asItem());
        boolean pouring = held.getItem() instanceof FuelCanItem can && can.pours() && !FuelCanItem.fluid(held).isEmpty();
        boolean free = level.getBlockState(pos.above()).canBeReplaced();
        if (state.getBlock() instanceof HandFuelPumpBlock) return empty ? sneak ? "take_pump" : HandFuelPumpBlock.target(level, pos, state) == null ? "needs_container" : "crank" : null;
        if (state.getBlock() instanceof FuelCanBlock can) {
            if (held.getItem() instanceof FuelNozzleItem) return "fill";
            if (pump && can.size.drum() && free) return "mount_pump";
            BlockState above = level.getBlockState(pos.above());
            if (empty && can.size.drum() && above.getBlock() instanceof HandFuelPumpBlock)
                return sneak ? "take_pump" : HandFuelPumpBlock.target(level, pos.above(), above) == null ? "needs_container" : "crank";
            if (empty && can.size == Size.JERRY_CAN) return "pick_up";
            return null;
        }
        if (state.getBlock() instanceof FuelSumpCoverBlock cover && cover.kind() == FuelSumpCoverBlock.Kind.FILL && state.getValue(FuelSumpCoverBlock.OPEN)) {
            if (pump && free) return "mount_pump";
            if (FuelCanItem.isPouring(held)) return "pour_stop";
            if (pouring && !sneak) return "pour_tank";
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return size.shapes[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelCanBlockEntity(pos, state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
